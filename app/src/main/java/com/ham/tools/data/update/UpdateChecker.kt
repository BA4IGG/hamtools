package com.ham.tools.data.update

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * 更新检测里所有「可以脱离 Android 跑」的部分：GitHub Release JSON / Atom 解析、
 * 通道筛选、版本号比较、更新日志排版。保持纯 JVM，配 JVM 单测覆盖真实响应与各种脏数据。
 *
 * 设计约束（与项目一贯的「数据诚实」一致）：
 * - 解析失败一律返回 null / 空列表，绝不猜测出一个版本号或伪造更新日志；
 * - 无法比较版本号时不报「有更新」，避免把 tag 格式变化误判成新版本；
 * - 正式通道只认带 `prerelease=false` 的 Release；历史 beta 与已归入测试通道的
 *   旧版本由 GitHub 的 Pre-release 标记区分，不在客户端硬编码任何版本黑名单；
 * - Atom 备用源没有 `prerelease` 字段，只能从 tag 里明显的 beta/rc 后缀判断，
 *   其余一律标记 `channelUnverified`，界面必须提示用户自行核对通道。
 */
object UpdateChecker {

    private const val MAX_NOTES_LINES = 200

    private val VERSION_NUMBER = Regex("""(\d+(?:\.\d+)*)""")
    private val DIGITS = Regex("""\d+""")
    private val HEADING = Regex("""^\s{0,3}(#{1,6})\s+(.*)$""")
    private val LIST_PREFIX = Regex("""^\s*(?:[-*•]|\d+[.)])\s+""")
    private val INLINE_LINK = Regex("""\[([^\]]+)\]\([^)]+\)""")
    private val INLINE_BOLD = Regex("""\*\*(.+?)\*\*""")
    private val INLINE_CODE = Regex("""`([^`]+)`""")
    private val HTML_TAG = Regex("""<[^>]+>""")
    private val ENTRY = Regex("(?s)<entry[^>]*>(.*?)</entry>")
    private val LINK_HREF = Regex("<link[^>]*href=[\"']([^\"']+)[\"'][^>]*>")
    private val TAG_PATH = Regex("""/tag/([^/?#]+)""")
    /** 只有发布者自己在 tag 里写明的预发布后缀才算，不从版本号大小推测。 */
    private val BETA_TAG = Regex("""(?i)(?:^|[-_.])(?:beta|alpha|rc\d*|preview|dev|test)(?:$|[-_.])""")

    // ------------------------------------------------------------------ 解析

    /** 解析单个 GitHub Release 对象；只挑 .apk 资源，忽略服务端补丁包等其它附件。 */
    private fun parseReleaseObject(root: JSONObject, source: String): AppRelease? {
        val tag = root.optString("tag_name", "").trim()
        if (tag.isEmpty()) return null

        var apkUrl: String? = null
        var apkName: String? = null
        var apkSize = 0L
        val assets = root.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                apkUrl = asset.optString("browser_download_url", "").takeIf { it.isNotBlank() }
                apkName = name
                apkSize = asset.optLong("size", 0L)
                break
            }
        }

        return AppRelease(
            tag = tag,
            version = normalizeVersion(tag),
            name = root.optString("name", tag).trim(),
            notes = root.optString("body", ""),
            publishedAt = root.optString("published_at", root.optString("created_at", "")),
            htmlUrl = root.optString("html_url", ""),
            apkUrl = apkUrl,
            apkName = apkName,
            apkSize = apkSize,
            prerelease = root.optBoolean("prerelease", false) || root.optBoolean("draft", false),
            source = source
        )
    }

    /** 解析 GitHub REST `releases/latest` 的单对象响应（保留兼容，列表接口也复用同一解析）。 */
    fun parseRelease(json: String?, source: String): AppRelease? {
        if (json.isNullOrBlank()) return null
        return runCatching { parseReleaseObject(JSONObject(json), source) }.getOrNull()
    }

    /**
     * 解析 GitHub REST `releases` 列表响应。
     * GitHub 按创建时间倒序返回；单条脏数据跳过而不是让整次检测失败。
     */
    fun parseReleaseList(json: String?, source: String): List<AppRelease> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val release = runCatching { parseReleaseObject(item, source) }.getOrNull()
                    // 没有可比较版本号的对象不能参与通道筛选，否则会误报「有更新」
                    if (release != null && release.version.isNotEmpty()) add(release)
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * 按通道挑选一条 Release。
     * - 正式通道：第一条非预发布（与 GitHub `/releases/latest` 的语义一致）；
     * - 测试通道：第一条即可，包含预发布，让用户能尝鲜。
     *
     * 列表里一条都没有或没有匹配的通道时返回 null，由调用方归因为「暂无该通道 Release」。
     */
    fun pickForChannel(releases: List<AppRelease>, channel: UpdateChannel): AppRelease? =
        when (channel) {
            UpdateChannel.BETA -> releases.firstOrNull()
            UpdateChannel.STABLE -> releases.firstOrNull { !it.prerelease }
        }

    /**
     * 解析 GitHub 官方 `releases.atom`（REST API 被限流或不可达时的备用源）。
     * Atom 的 `<content type="html">` 是实体转义后的 HTML，先解码再转成分行纯文本。
     * 该源不含附件信息，所以 apkUrl 恒为 null，界面据此不显示「直接下载」。
     * 它也没有 `prerelease` 字段：只有 tag 里明确写了 beta/rc 等后缀才标为预发布，
     * 其余一律 `channelUnverified = true`，提示用户自行核对，不冒充已确认的正式版。
     */
    fun parseAtomFeed(
        xml: String?,
        source: String,
        channel: UpdateChannel = UpdateChannel.STABLE
    ): AppRelease? {
        if (xml.isNullOrBlank()) return null
        return runCatching {
            val entry = ENTRY.find(xml)?.groupValues?.get(1) ?: return@runCatching null
            fun tag(name: String): String =
                Regex("(?s)<$name[^>]*>(.*?)</$name>").find(entry)?.groupValues?.get(1)?.trim() ?: ""

            val links = LINK_HREF.findAll(entry).map { it.groupValues[1] }.toList()
            val htmlUrl = links.firstOrNull { it.contains("/releases/tag/") }
                ?: links.firstOrNull().orEmpty()
            val tag = TAG_PATH.find(htmlUrl)?.groupValues?.get(1)
                ?: tag("title").takeIf { DIGITS.containsMatchIn(it) }
                ?: return@runCatching null

            AppRelease(
                tag = tag,
                version = normalizeVersion(tag),
                name = cleanInline(tag("title")).ifBlank { tag },
                notes = htmlToNotes(decodeEntities(tag("content"))),
                publishedAt = tag("updated").ifBlank { tag("published") },
                htmlUrl = htmlUrl,
                apkUrl = null,
                apkName = null,
                apkSize = 0L,
                prerelease = BETA_TAG.containsMatchIn(tag),
                source = source,
                // 测试通道本来就接受任意版本，不必额外提示；正式通道必须提示无法确认
                channelUnverified = channel == UpdateChannel.STABLE
            )
        }.getOrNull()
    }

    // ------------------------------------------------------------------ 版本

    /** `v1.2` → `1.2`，`1.3.0-beta.2` → `1.3.0`；取不到数字串时返回空串。 */
    fun normalizeVersion(raw: String): String =
        VERSION_NUMBER.find(raw.trim())?.groupValues?.get(1).orEmpty()

    /** 逐段数字比较，短版本缺位补 0：`1.2` == `1.2.0`，`1.10` > `1.9`。无法比较返回 0。 */
    fun compareVersions(left: String, right: String): Int {
        val a = DIGITS.findAll(left).map { it.value.toIntOrNull() ?: 0 }.toList()
        val b = DIGITS.findAll(right).map { it.value.toIntOrNull() ?: 0 }.toList()
        if (a.isEmpty() || b.isEmpty()) return 0
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /** 默认按正式通道判断，保留两参重载以兼容既有调用与单测。 */
    fun isUpdateAvailable(localVersion: String, release: AppRelease): Boolean =
        isUpdateAvailable(localVersion, release, UpdateChannel.STABLE)

    /**
     * 只有版本号确实高于本机才算有更新。
     * - 正式通道额外排除预发布（历史 beta 与归入测试通道的版本都在 GitHub 上标了 Pre-release）；
     * - 测试通道允许预发布，方便尝鲜，但界面必须标注「预发布」。
     *
     * `channelUnverified` 不在这里拦截：备用源仍可提示有新版本，
     * 但弹窗会明确写出「无法确认通道，请到发布页核对」，不冒充确定信息。
     */
    fun isUpdateAvailable(
        localVersion: String,
        release: AppRelease,
        channel: UpdateChannel
    ): Boolean {
        if (release.version.isEmpty()) return false
        if (channel == UpdateChannel.STABLE && release.prerelease) return false
        return compareVersions(localVersion, release.version) < 0
    }

    // ------------------------------------------------------------------ 更新日志

    /** 把 Markdown / 简单 HTML 更新日志压成可排版的行，去掉内联标记但保留文字信息。 */
    fun formatNotes(notes: String): List<ReleaseLine> {
        if (notes.isBlank()) return emptyList()
        val result = mutableListOf<ReleaseLine>()
        for (raw in notes.replace("\r\n", "\n").split("\n")) {
            if (result.size >= MAX_NOTES_LINES) break
            val line = raw.trimEnd()
            if (line.isBlank()) continue
            val heading = HEADING.find(line)
            val isBullet = LIST_PREFIX.containsMatchIn(line)
            val plain = cleanInline(
                when {
                    heading != null -> heading.groupValues[2]
                    isBullet -> LIST_PREFIX.replace(line, "")
                    else -> line
                }
            )
            if (plain.isEmpty()) continue
            val kind = when {
                heading != null -> ReleaseLineKind.HEADING
                isBullet -> ReleaseLineKind.BULLET
                else -> ReleaseLineKind.TEXT
            }
            result += ReleaseLine(kind, plain)
        }
        return result
    }

    /** 去掉 `**粗体**`、反引号、`[文字](链接)` 与残留标签，保留可读文字。 */
    fun cleanInline(raw: String): String = INLINE_LINK.replace(raw, "\$1")
        .let { INLINE_BOLD.replace(it, "\$1") }
        .let { INLINE_CODE.replace(it, "\$1") }
        .let { HTML_TAG.replace(it, "") }
        .let { decodeEntities(it) }
        .replace(Regex("""\s+"""), " ")
        .trim()

    /** Atom content 的 HTML → 带换行的纯文本，交给 [formatNotes] 继续排版。 */
    private fun htmlToNotes(html: String): String {
        if (html.isBlank()) return ""
        return html
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|li|h[1-6]|div|ul|ol|pre)>"), "\n")
            .replace(Regex("(?i)<li[^>]*>"), "- ")
            .replace(Regex("(?i)<h[1-6][^>]*>"), "### ")
            .let { HTML_TAG.replace(it, "") }
    }

    /** &amp; 必须最后替换，否则 `&amp;lt;` 会被二次解码成 `<`。 */
    private fun decodeEntities(raw: String): String = raw
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")

    // ------------------------------------------------------------------ 展示

    /** ISO 时间戳只取日期部分，避免界面出现一串用户看不懂的 UTC 时间。 */
    fun formatDate(iso: String): String = iso.trim().take(10).ifEmpty { "—" }

    fun formatSize(bytes: Long): String = when {
        bytes <= 0L -> "—"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

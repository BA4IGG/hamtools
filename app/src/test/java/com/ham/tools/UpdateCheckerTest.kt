package com.ham.tools

import com.ham.tools.data.update.AppRelease
import com.ham.tools.data.update.ReleaseLineKind
import com.ham.tools.data.update.UpdateChannel
import com.ham.tools.data.update.UpdateChecker
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 更新检测的纯 JVM 层：真实响应结构、脏数据、版本比较与更新日志排版。 */
class UpdateCheckerTest {

    /** 按 GitHub releases/latest 的真实字段结构构造一份精简响应。 */
    private fun releaseJson(
        tag: String = "v1.3",
        prerelease: Boolean = false,
        body: String = "### 本次更新\n- **新增**检测更新\n- 修复若干问题"
    ): String {
        // JSON 字符串里不能有裸换行，先按 JSON 规则转义
        val escapedBody = body.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        return """
        {
          "tag_name": "$tag",
          "name": "HAM 工具合集 $tag",
          "body": "$escapedBody",
          "published_at": "2026-11-20T09:30:00Z",
          "html_url": "https://github.com/BA4IGG/hamtools/releases/tag/$tag",
          "prerelease": $prerelease,
          "draft": false,
          "assets": [
            {"name": "server-qsl-deploy-patch.zip", "size": 41497,
             "browser_download_url": "https://example.invalid/patch.zip"},
            {"name": "HAM-Tools-$tag.apk", "size": 16548231,
             "browser_download_url": "https://github.com/BA4IGG/hamtools/releases/download/$tag/HAM-Tools-$tag.apk"}
          ]
        }
    """.trimIndent()
    }

    private fun atomXml(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Release notes from hamtools</title>
          <entry>
            <id>tag:github.com,2008:Repository/97004279/v1.3</id>
            <updated>2026-11-20T09:30:00Z</updated>
            <link rel="alternate" type="text/html" href="https://github.com/BA4IGG/hamtools/releases/tag/v1.3" />
            <title>HAM 工具合集 v1.3</title>
            <content type="html">&lt;h3&gt;本次更新&lt;/h3&gt;&lt;ul&gt;&lt;li&gt;新增检测更新&lt;/li&gt;&lt;li&gt;修复若干问题&lt;/li&gt;&lt;/ul&gt;</content>
          </entry>
        </feed>
    """.trimIndent()

    @Test
    fun `解析 GitHub latest 响应并只挑 apk 附件`() {
        val release = UpdateChecker.parseRelease(releaseJson(), "GitHub Releases API")
        checkNotNull(release)
        assertEquals("v1.3", release.tag)
        assertEquals("1.3", release.version)
        assertEquals("2026-11-20T09:30:00Z", release.publishedAt)
        assertEquals("HAM-Tools-v1.3.apk", release.apkName)
        assertEquals(16_548_231L, release.apkSize)
        assertTrue(release.apkUrl.orEmpty().endsWith("HAM-Tools-v1.3.apk"))
        assertFalse(release.prerelease)
        assertEquals("GitHub Releases API", release.source)
    }

    @Test
    fun `脏数据与缺字段不猜版本号`() {
        assertNull(UpdateChecker.parseRelease(null, "x"))
        assertNull(UpdateChecker.parseRelease("", "x"))
        assertNull(UpdateChecker.parseRelease("不是 JSON", "x"))
        assertNull(UpdateChecker.parseRelease("""{"name":"无 tag"}""", "x"))
        val noAssets = UpdateChecker.parseRelease("""{"tag_name":"v1.3"}""", "x")
        checkNotNull(noAssets)
        assertNull(noAssets.apkUrl)
        assertEquals("", noAssets.notes)
        assertEquals("v1.3", noAssets.name)
    }

    @Test
    fun `预发布不触发更新提示`() {
        val release = checkNotNull(UpdateChecker.parseRelease(releaseJson(prerelease = true), "x"))
        assertTrue(release.prerelease)
        assertFalse(UpdateChecker.isUpdateAvailable("1.2", release))
    }

    @Test
    fun `版本号归一化覆盖历史 tag 形式`() {
        assertEquals("1.2", UpdateChecker.normalizeVersion("v1.2"))
        assertEquals("1.3", UpdateChecker.normalizeVersion("  v1.3  "))
        assertEquals("1.3.0", UpdateChecker.normalizeVersion("1.3.0-beta.2"))
        assertEquals("1.1", UpdateChecker.normalizeVersion("beta-1.1"))
        assertEquals("", UpdateChecker.normalizeVersion("release-candidate"))
    }

    @Test
    fun `版本比较逐段进行且短版本补零`() {
        assertTrue(UpdateChecker.compareVersions("1.2", "1.3") < 0)
        assertTrue(UpdateChecker.compareVersions("1.10", "1.9") > 0)
        assertEquals(0, UpdateChecker.compareVersions("1.2", "1.2.0"))
        assertEquals(0, UpdateChecker.compareVersions("v1.2", "1.2"))
        assertEquals(0, UpdateChecker.compareVersions("", "1.2"))
        assertTrue(UpdateChecker.compareVersions("2.0", "1.9.9") > 0)
    }

    @Test
    fun `只有正式且更高的版本才算有更新`() {
        val newer = checkNotNull(UpdateChecker.parseRelease(releaseJson("v1.3"), "x"))
        val same = checkNotNull(UpdateChecker.parseRelease(releaseJson("v1.2"), "x"))
        val older = checkNotNull(UpdateChecker.parseRelease(releaseJson("v1.1"), "x"))
        assertTrue(UpdateChecker.isUpdateAvailable("1.2", newer))
        assertFalse(UpdateChecker.isUpdateAvailable("1.2", same))
        assertFalse(UpdateChecker.isUpdateAvailable("1.2", older))
        assertFalse(UpdateChecker.isUpdateAvailable("", newer))
    }

    @Test
    fun `更新日志排版保留标题与列表并清掉内联标记`() {
        val notes = """
            ## HAM 工具合集 v1.3

            **版本**：1.3（versionCode 4）
            ### 本次更新
            - **新增**「检测更新」，见 [GitHub](https://github.com/BA4IGG/hamtools)
            - 修复 `NullPointerException`
            1. 顺序项也应识别为列表

            普通说明文字。
        """.trimIndent()
        val lines = UpdateChecker.formatNotes(notes)
        assertTrue(lines.size >= 6)
        assertEquals(ReleaseLineKind.HEADING, lines[0].kind)
        assertEquals("HAM 工具合集 v1.3", lines[0].text)
        assertTrue(lines.any { it.kind == ReleaseLineKind.BULLET && it.text == "新增「检测更新」，见 GitHub" })
        assertTrue(lines.any { it.kind == ReleaseLineKind.BULLET && it.text == "修复 NullPointerException" })
        assertTrue(lines.any { it.text == "版本：1.3（versionCode 4）" })
        assertTrue(lines.none { it.text.contains("**") || it.text.contains("](") })
    }

    @Test
    fun `空日志与超长日志都不崩且有上限`() {
        assertTrue(UpdateChecker.formatNotes("").isEmpty())
        assertTrue(UpdateChecker.formatNotes("   \n\n  ").isEmpty())
        val long = (1..300).joinToString("\n") { "- 第 $it 行" }
        assertEquals(200, UpdateChecker.formatNotes(long).size)
    }

    @Test
    fun `Atom 备用源解析出 tag 与更新日志且标明没有附件`() {
        val release = UpdateChecker.parseAtomFeed(atomXml(), "GitHub Releases Atom（备用源）")
        checkNotNull(release)
        assertEquals("v1.3", release.tag)
        assertEquals("1.3", release.version)
        assertEquals("https://github.com/BA4IGG/hamtools/releases/tag/v1.3", release.htmlUrl)
        assertEquals("2026-11-20T09:30:00Z", release.publishedAt)
        assertNull(release.apkUrl)
        assertFalse(release.prerelease)
        val lines = UpdateChecker.formatNotes(release.notes)
        assertTrue(lines.any { it.text == "本次更新" })
        assertTrue(lines.any { it.kind == ReleaseLineKind.BULLET && it.text == "新增检测更新" })
    }

    @Test
    fun `Atom 无 entry 或无 tag 时返回 null`() {
        assertNull(UpdateChecker.parseAtomFeed(null, "x"))
        assertNull(UpdateChecker.parseAtomFeed("<feed><title>空</title></feed>", "x"))
        assertNull(
            UpdateChecker.parseAtomFeed(
                "<feed><entry><title>无版本号</title><link href=\"https://example.invalid/\"/></entry></feed>",
                "x"
            )
        )
    }

    @Test
    fun `展示辅助函数不伪造数据`() {
        assertEquals("2026-11-20", UpdateChecker.formatDate("2026-11-20T09:30:00Z"))
        assertEquals("—", UpdateChecker.formatDate(""))
        assertEquals("—", UpdateChecker.formatSize(0L))
        assertEquals("41 KB", UpdateChecker.formatSize(41_497L))
        assertEquals("15.8 MB", UpdateChecker.formatSize(16_548_231L))
    }

    @Test
    fun `cleanInline 处理 HTML 实体且 amp 不被二次解码`() {
        assertEquals("A & B", UpdateChecker.cleanInline("A &amp; B"))
        assertEquals("&lt;", UpdateChecker.cleanInline("&amp;lt;"))
        assertEquals("纯文本", UpdateChecker.cleanInline("**纯文本**"))
        assertEquals("看这里", UpdateChecker.cleanInline("[看这里](https://example.invalid)"))
        assertArrayEquals(
            arrayOf("去掉标签后"),
            arrayOf(UpdateChecker.cleanInline("<b>去掉标签后</b>"))
        )
    }

    // ------------------------------------------------------------ 双通道

    private fun releaseListJson(vararg items: String): String =
        items.joinToString(prefix = "[", postfix = "]")

    @Test
    fun `正式通道从列表中筛掉预发布并保留 APK 附件`() {
        val list = UpdateChecker.parseReleaseList(
            releaseListJson(
                releaseJson("v2.1-beta", prerelease = true),
                releaseJson("v2.0"),
                releaseJson("v1.2", prerelease = true)
            ),
            "GitHub Releases API"
        )
        assertEquals(3, list.size)
        val picked = checkNotNull(UpdateChecker.pickForChannel(list, UpdateChannel.STABLE))
        assertEquals("v2.0", picked.tag)
        assertFalse(picked.prerelease)
        assertEquals("HAM-Tools-v2.0.apk", picked.apkName)
        assertTrue(picked.apkUrl.orEmpty().endsWith("HAM-Tools-v2.0.apk"))
    }

    @Test
    fun `测试通道取列表第一条包含预发布`() {
        val list = UpdateChecker.parseReleaseList(
            releaseListJson(releaseJson("v2.1-beta", prerelease = true), releaseJson("v2.0")),
            "x"
        )
        val picked = checkNotNull(UpdateChecker.pickForChannel(list, UpdateChannel.BETA))
        assertEquals("v2.1-beta", picked.tag)
        assertTrue(picked.prerelease)
        assertTrue(UpdateChecker.isUpdateAvailable("2.0", picked, UpdateChannel.BETA))
        assertFalse(UpdateChecker.isUpdateAvailable("2.0", picked, UpdateChannel.STABLE))
    }

    @Test
    fun `列表脏数据不猜版本且全预发布时正式通道无候选`() {
        assertEquals(emptyList<AppRelease>(), UpdateChecker.parseReleaseList(null, "x"))
        assertEquals(emptyList<AppRelease>(), UpdateChecker.parseReleaseList("不是 JSON", "x"))
        assertEquals(emptyList<AppRelease>(), UpdateChecker.parseReleaseList("{}", "x"))
        val dirty = """[{"tag_name":"v2.0"},{"name":"无 tag"},"字符串",{"tag_name":"release-candidate"}]"""
        val list = UpdateChecker.parseReleaseList(dirty, "x")
        assertEquals(1, list.size)
        assertEquals("v2.0", list[0].tag)

        val allBeta = UpdateChecker.parseReleaseList(
            releaseListJson(releaseJson("v3.0-beta", prerelease = true)), "x"
        )
        assertNull(UpdateChecker.pickForChannel(allBeta, UpdateChannel.STABLE))
        assertNotNull(UpdateChecker.pickForChannel(allBeta, UpdateChannel.BETA))
    }

    @Test
    fun `预发布只在测试通道提示且正式通道仍按版本号比较`() {
        val beta = checkNotNull(UpdateChecker.parseRelease(releaseJson("v3.0-beta", true), "x"))
        val stable = checkNotNull(UpdateChecker.parseRelease(releaseJson("v2.0"), "x"))
        assertTrue(UpdateChecker.isUpdateAvailable("2.0", beta, UpdateChannel.BETA))
        assertFalse(UpdateChecker.isUpdateAvailable("2.0", beta, UpdateChannel.STABLE))
        assertFalse(UpdateChecker.isUpdateAvailable("2.0", stable, UpdateChannel.BETA))
        assertTrue(UpdateChecker.isUpdateAvailable("1.2", stable, UpdateChannel.STABLE))
        assertFalse(UpdateChecker.isUpdateAvailable("2.0", stable, UpdateChannel.STABLE))
        // 两参重载默认走正式通道，行为不能因为加了通道而悄悄改变
        assertFalse(UpdateChecker.isUpdateAvailable("2.0", beta))
    }

    @Test
    fun `Atom 备用源标注通道未确认并从 tag 识别明显预发布`() {
        val stable = checkNotNull(
            UpdateChecker.parseAtomFeed(atomXml(), "x", UpdateChannel.STABLE)
        )
        assertTrue(stable.channelUnverified)
        assertFalse(stable.prerelease)

        val betaXml = atomXml().replace("v1.3", "v1.4-beta")
        val beta = checkNotNull(
            UpdateChecker.parseAtomFeed(betaXml, "x", UpdateChannel.STABLE)
        )
        assertTrue(beta.prerelease)
        assertTrue(beta.channelUnverified)
        assertFalse(UpdateChecker.isUpdateAvailable("1.2", beta, UpdateChannel.STABLE))
        assertTrue(UpdateChecker.isUpdateAvailable("1.2", beta, UpdateChannel.BETA))

        // 测试通道本来就接受任意版本，无需再提示「通道未确认」
        val betaChannel = checkNotNull(
            UpdateChecker.parseAtomFeed(atomXml(), "x", UpdateChannel.BETA)
        )
        assertFalse(betaChannel.channelUnverified)
    }

    @Test
    fun `通道枚举从持久化键回落且不认识的值一律回到正式通道`() {
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromKey(null))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromKey(""))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromKey("unknown"))
        assertEquals(UpdateChannel.BETA, UpdateChannel.fromKey("beta"))
        assertEquals("beta", UpdateChannel.BETA.key)
    }
}

package com.ham.tools.data.remote

import com.ham.tools.data.AppConfig
import com.ham.tools.data.reference.DxSpot
import com.ham.tools.data.reference.SolarModel
import com.ham.tools.data.reference.SolarSnapshot
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 服务端聚合接口返回的 DX spots 数据。
 * liveSpots 表示是否为真实在线数据，避免客户端把内置样例当成实时数据展示。
 */
data class HamOverview(
    val spots: List<DxSpot>,
    val source: String,
    val updatedAt: String,
    val liveSpots: Boolean = false,
    val spotsSource: String = source
)

/** 一次 spots 取回结果。source 如实描述来源，live 表示是否为真实在线数据。 */
data class SpotFeed(val spots: List<DxSpot>, val source: String, val live: Boolean)

/** 在线呼号资料（执照登记信息，不是实时位置）。 */
data class OnlineCallsign(
    val callsign: String,
    val name: String?,
    val country: String?,
    val grid: String?,
    val licenseClass: String?,
    val source: String
)

/**
 * 联网板块的数据访问层。
 *
 * 设计原则是**离线优先 + 数据诚实**：所有在线请求都是「可选增强」，任何一个失败都不影响
 * 工具打开使用；界面必须如实标注来源，绝不把内置样例伪装成实时值。
 *
 * 数据源分工（按顺序尝试，都失败就返回 null，由调用方保留内置数据）：
 * - 呼号资料：HamDB 直连（免费无密钥）→ 服务端代理 → 内置离线前缀表
 * - DX spots：服务端聚合（须 live）→ DXWatch 实时页面 → 内置 EXAMPLE 样例
 *
 * 本类保持纯 JVM（不引用任何 android.*），因此解析函数可以用本地单元测试覆盖。
 */
class HamDataClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** 拉取实时太阳活动；失败返回 null，由调用方降级。 */
    fun fetchSolar(): SolarSnapshot? {
        val xml = get(AppConfig.SOLAR_XML_URL)
        if (xml != null) {
            parseSolarXml(xml)?.let { return it }
        }
        val noaa = get(AppConfig.SOLAR_NOAA_URL)
        if (noaa != null) {
            val kp = parseNoaaKp(noaa)
            if (kp != null) {
                return SolarSnapshot(
                    sfi = SolarModel.FALLBACK.sfi,
                    kp = kp,
                    aIndex = apFromKp(kp),
                    sunspots = SolarModel.FALLBACK.sunspots,
                    source = "NOAA SWPC 实时 Kp · 太阳通量为内置值",
                    updatedAt = "实时"
                )
            }
        }
        return null
    }

    /** 拉取自部署服务端的聚合数据；未部署返回 null。live 字段决定能否当作实时值展示。 */
    fun fetchOverview(): HamOverview? {
        val body = get(AppConfig.HAM_DATA_URL) ?: return null
        return runCatching {
            val root = JSONObject(body)
            val spotsArray = root.optJSONArray("spots") ?: JSONArray()
            val spots = (0 until spotsArray.length()).mapNotNull { i ->
                val item = spotsArray.optJSONObject(i) ?: return@mapNotNull null
                DxSpot(
                    time = item.optString("time", "—"),
                    dxCallsign = item.optString("callsign", "—"),
                    freqKHz = item.optDouble("freqKHz", 0.0),
                    spotter = item.optString("spotter", "—"),
                    comment = item.optString("comment", "")
                )
            }
            val source = root.optString("source", "自部署服务器")
            HamOverview(
                spots = spots,
                source = source,
                updatedAt = root.optString("updatedAt", "—"),
                liveSpots = root.optBoolean("liveSpots", false),
                spotsSource = root.optString("spotsSource", source)
            )
        }.getOrNull()
    }

    /** 在线呼号资料：HamDB 直连 → 服务端代理 → null。 */
    fun fetchCallsign(raw: String): OnlineCallsign? {
        val call = normalizeCallsign(raw)
        if (call.isEmpty()) return null
        val direct = get(AppConfig.HAMDB_BASE_URL + call + "/json/" + AppConfig.HAMDB_APP_NAME)
        parseHamDb(direct, call, "HamDB 在线呼号库（hamdb.org）")?.let { return it }
        val proxied = get(
            AppConfig.CALLSIGN_LOOKUP_URL + "?q=" + URLEncoder.encode(call, "UTF-8")
        )
        parseHamDb(proxied, call, "服务端代理 · HamDB")?.let { return it }
        return null
    }

    /** DX spots：服务端实时聚合 → DXWatch 实时页面 → null（调用方保留内置样例）。 */
    fun fetchSpots(): SpotFeed? {
        val overview = fetchOverview()
        if (overview != null && overview.liveSpots && overview.spots.isNotEmpty()) {
            return SpotFeed(overview.spots, overview.spotsSource, true)
        }
        val spots = parseDxWatch(get(AppConfig.DXWATCH_SPOTS_URL))
        if (spots.isNotEmpty()) {
            return SpotFeed(spots, "DXWatch 实时 DX cluster（dxwatch.com）", true)
        }
        return null
    }

    private fun parseSolarXml(xml: String): SolarSnapshot? {
        fun tag(name: String): Double? =
            Regex("<$name>\\s*([^<]+)\\s*</$name>")
                .find(xml)?.groupValues?.get(1)?.trim()?.toDoubleOrNull()
        val sfi = tag("solarflux") ?: return null
        val kp = tag("kindex") ?: return null
        return SolarSnapshot(
            sfi = sfi.toInt(),
            kp = kp,
            aIndex = (tag("aindex")?.toInt() ?: apFromKp(kp)),
            sunspots = tag("sunspots")?.toInt() ?: SolarModel.FALLBACK.sunspots,
            source = "hamqsl.com 实时观测",
            updatedAt = "实时"
        )
    }

    private fun parseNoaaKp(body: String): Double? = runCatching {
        val array = JSONArray(body)
        if (array.length() == 0) return null
        val last = array.getJSONObject(array.length() - 1)
        val primary = last.optDouble("k_index", Double.NaN)
        val fallback = last.optDouble("estimated_kp", Double.NaN)
        when {
            !primary.isNaN() && primary >= 0.0 -> primary
            !fallback.isNaN() && fallback >= 0.0 -> fallback
            else -> null
        }
    }.getOrNull()

    /** Kp 与 Ap 的粗略换算，仅用于备份数据源缺失 Ap 时补全显示。 */
    private fun apFromKp(kp: Double): Int = (kp * kp * 3.5).toInt().coerceIn(0, 400)

    private fun get(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            // 部分站点（DXWatch）对非常规 UA 会拒绝或返回挑战页
            .header("User-Agent", "Mozilla/5.0 (compatible; HAM-Tools/1.2; BA4IGG)")
            .header("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }.getOrNull()

    companion object {

        private val TR = Regex("(?is)<tr[^>]*>(.*?)</tr>")
        private val TD = Regex("(?is)<td[^>]*>(.*?)</td>")
        private val TAGS = Regex("(?s)<[^>]*>")
        private val QRZ_LINK = Regex("(?i)/qrz/[^\"'/?\\s]+")
        private val FREQ = Regex("^\\d{3,6}(?:\\.\\d+)?$")
        private val TIME = Regex("(?i)\\d{4}z\\s+\\d{1,2}\\s+[a-z]{3}")
        private val TIME_DIGITS = Regex("(\\d{4})z")
        private val NON_CALL = Regex("[^A-Z0-9]")

        /** 去掉斜杠后缀与 SSID，只留基础呼号；非法输入返回空串。 */
        fun normalizeCallsign(raw: String): String {
            val call = raw.trim().uppercase()
                .substringBefore("/")
                .substringBefore("-")
                .replace(NON_CALL, "")
            return if (call.length in 3..10) call else ""
        }

        /** 解析 HamDB v1 JSON。NOT_FOUND / 字段缺失都返回 null，由调用方降级。 */
        fun parseHamDb(body: String?, fallbackCall: String, source: String): OnlineCallsign? {
            if (body.isNullOrBlank()) return null
            return runCatching {
                val root = JSONObject(body).optJSONObject("hamdb") ?: return@runCatching null
                val status = root.optJSONObject("messages")?.optString("status")
                    ?: return@runCatching null
                if (!status.equals("OK", ignoreCase = true)) return@runCatching null
                val c = root.optJSONObject("callsign") ?: return@runCatching null
                fun field(key: String): String? = c.optString(key, "").takeIf { it.isNotBlank() }
                OnlineCallsign(
                    callsign = field("call") ?: fallbackCall,
                    name = field("name"),
                    country = field("country"),
                    grid = field("grid"),
                    licenseClass = field("class"),
                    source = source
                )
            }.getOrNull()
        }

        /**
         * 解析 DXWatch 的 spots 表格。
         *
         * 该站没有公开 JSON API，只有 HTML 表格；这里按「含 /qrz/ 链接的单元格」识别
         * spotter 与 DX 呼号，再按单元格内容找频率与时间，结构变化时返回空列表，
         * 由调用方降级——绝不会因为解析失败而把样例伪装成实时数据。
         */
        fun parseDxWatch(html: String?): List<DxSpot> {
            if (html.isNullOrBlank()) return emptyList()
            val result = mutableListOf<DxSpot>()
            for (rowMatch in TR.findAll(html)) {
                if (result.size >= 60) break
                val row = rowMatch.groupValues[1]
                val cellMatches = TD.findAll(row).toList()
                if (cellMatches.size < 4) continue
                val cells = cellMatches.map { plainText(it.groupValues[1]) }
                val linkIndexes = cellMatches.mapIndexedNotNull { index, cell ->
                    index.takeIf { QRZ_LINK.containsMatchIn(cell.groupValues[1]) }
                }
                if (linkIndexes.size < 2) continue
                val spotter = cells[linkIndexes[0]]
                val dx = cells[linkIndexes[1]]
                val freqIndex = cells.indexOfFirst { FREQ.matches(it) }
                val timeIndex = cells.indexOfFirst { TIME.containsMatchIn(it) }
                if (dx.isEmpty() || freqIndex < 0 || timeIndex < 0) continue
                val freq = cells[freqIndex].toDoubleOrNull() ?: continue
                if (freq <= 0.0) continue
                val comment = if (timeIndex > freqIndex + 1) {
                    cells.subList(freqIndex + 1, timeIndex).joinToString(" ").trim()
                } else ""
                result += DxSpot(
                    time = formatTime(cells[timeIndex]),
                    dxCallsign = dx,
                    freqKHz = freq,
                    spotter = spotter,
                    comment = comment
                )
            }
            return result
        }

        /** "0958z 11 Nov" → "09:58"，与内置样例的时间格式保持一致。 */
        fun formatTime(raw: String): String {
            val match = TIME_DIGITS.find(raw) ?: return raw.take(16)
            val v = match.groupValues[1]
            return v.substring(0, 2) + ":" + v.substring(2)
        }

        /** 去标签 + 常见 HTML 实体，压缩空白。不用 android.text.Html，保持纯 JVM 可测。 */
        fun plainText(raw: String): String = TAGS.replace(raw, " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

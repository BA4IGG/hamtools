package com.ham.tools.data.reference

/**
 * 业余卫星与中继名录。
 *
 * **关于数据的诚实说明** —— 这两类数据是所有参考表里最容易过时的：
 *
 * 1. **卫星**：多数低轨业余卫星设计寿命 3-8 年，退役、再入、参数调整是常态。
 *    本表收录的是公开资料中记录较完整的在役/近年常用卫星，但**是否可用请以上行
 *    前的最新星历与卫星运营方公告为准**（AMSAT-NA、AMSAT-UK、SatNOGS 等）。
 *    频率单位为 MHz；`toneHz` 为上行亚音（CTCSS）。
 *
 * 2. **中继**：中继频率、亚音、是否联网（DMR/Fusion/D-STAR 反射器）由各地中继组
 *    自行管理，变更频繁且无统一登记机构。**本表给的是「常见配置」与「频段规则」
 *    而非权威名录**，落地后请先监听确认，并遵守当地中继组的使用约定。
 *
 * 频段规则速记（中国内地）：
 * - VHF 中继：144.000-148.000 MHz，常用上下差 ±0.6 MHz
 * - UHF 中继：430.000-440.000 MHz，常用上下差 ±5 MHz（部分 ±10 MHz）
 *
 * 这两条规则比任何具体频率表都管用 —— 拿到一个下行频率，按规则就能推出上行。
 */
data class SatelliteEntry(
    /** 常用名，如 "SO-50"。 */
    val name: String,
    /** 卫星全称或别名。 */
    val alias: String? = null,
    /** 工作模式。 */
    val mode: String,
    /** 上行频率 MHz。 */
    val uplinkMHz: Double? = null,
    /** 下行频率 MHz。 */
    val downlinkMHz: Double? = null,
    /** 上行亚音 Hz。 */
    val toneHz: Double? = null,
    /** 信标频率 MHz。 */
    val beaconMHz: Double? = null,
    val note: String? = null
)

data class RepeaterEntry(
    /** 大区，用于分组。 */
    val region: String,
    val city: String,
    /** 中继台呼号（公开资料可查的）。 */
    val callsign: String? = null,
    /** 下行频率 MHz（也就是你守听的频率）。 */
    val downlinkMHz: Double,
    /**
     * 上下差值 MHz。负号表示「下差」——上行比下行低（国内 UHF 主流做法）。
     * 例：下行 439.750，shift = -5 → 上行 434.750。
     */
    val shiftMHz: Double = -5.0,
    /** 亚音 Hz，null 表示无亚音。 */
    val toneHz: Double? = null,
    val mode: String = "FM",
    val note: String? = null
) {
    /** 上行频率。 */
    val uplinkMHz: Double get() = downlinkMHz + shiftMHz
}

object SatRepeaterTable {

    val satellites: List<SatelliteEntry> = listOf(
        SatelliteEntry(
            name = "ISS",
            alias = "国际空间站 / ARISS",
            mode = "FM 语音中继",
            uplinkMHz = 144.490,
            downlinkMHz = 145.800,
            toneHz = null,
            note = "呼号随宇航员国籍变化（NA1SS / RS0ISS / DP0ISS）。全球下行统一 145.800，上行在美洲/太平洋区域为 144.490。需等待ARISS公布可用时段"
        ),
        SatelliteEntry(
            name = "ISS-APRS",
            alias = "国际空间站包中继",
            mode = "APRS 1200bps",
            uplinkMHz = 145.825,
            downlinkMHz = 145.825,
            note = "单向 APRS 转发，常用于位置信标与短消息"
        ),
        SatelliteEntry(
            name = "SO-50",
            alias = "SaudiSat Oscar 50",
            mode = "FM 语音",
            uplinkMHz = 145.850,
            downlinkMHz = 436.795,
            toneHz = 74.4,
            note = "经典入门 FM 卫星，需 74.4Hz 亚音触发转发器。近年状态不稳定，使用前请确认"
        ),
        SatelliteEntry(
            name = "AO-91",
            alias = "Fox-1B / RadFxSat",
            mode = "FM 语音",
            uplinkMHz = 435.250,
            downlinkMHz = 145.960,
            toneHz = 67.0,
            note = "AMSAT 的 Fox 系列，UHF 上行 / VHF 下行"
        ),
        SatelliteEntry(
            name = "AO-92",
            alias = "Fox-1D / RadFxSat-2",
            mode = "FM 语音",
            uplinkMHz = 435.350,
            downlinkMHz = 145.880,
            toneHz = 67.0,
            note = "与 AO-91 同系列，参数相近"
        ),
        SatelliteEntry(
            name = "AO-73",
            alias = "FUNcube-1",
            mode = "SSB/CW 线性转发器",
            uplinkMHz = 435.150,
            downlinkMHz = 145.935,
            note = "反向转发器：上行 UHF 段频率越高，对应下行 VHF 段频率越低。另有 145.935 遥测信标"
        ),
        SatelliteEntry(
            name = "EO-88",
            alias = "Nayif-1",
            mode = "SSB/CW 线性转发器",
            uplinkMHz = 435.030,
            downlinkMHz = 145.955,
            note = "阿联酋业余卫星，同为反向转发器"
        ),
        SatelliteEntry(
            name = "JO-97",
            alias = "LilacSat-1 / 紫丁香一号",
            mode = "FM / 线性转发器",
            uplinkMHz = 435.060,
            downlinkMHz = 145.860,
            note = "哈尔滨工业大学学生团队卫星，含业余无线电载荷"
        ),
        SatelliteEntry(
            name = "QO-100",
            alias = "Es'hail-2 / AMSAT P4-A",
            mode = "SSB 窄带上行 / 宽带下行",
            uplinkMHz = 2400.050,
            downlinkMHz = 10489.550,
            note = "地球同步轨道业余转发器，需 2.4GHz 上行与 10GHz 下行设备，全天候可用"
        ),
        SatelliteEntry(
            name = "RS-44",
            alias = "DOSAAF-85",
            mode = "SSB/CW 线性转发器",
            uplinkMHz = 21.190,
            downlinkMHz = 435.610,
            note = "俄罗斯业余卫星，HF 上行 / UHF 下行的跨频段转发"
        )
    )

    /** 大区顺序，用于分组展示。 */
    val regions: List<String> = listOf("华北", "东北", "华东", "华中", "华南", "西南", "西北")

    val repeaters: List<RepeaterEntry> = listOf(
        // ---------- 华北 ----------
        RepeaterEntry("华北", "北京", null, 439.750, -5.0, 88.5, note = "公开资料常见配置，实际以中继组公布为准"),
        RepeaterEntry("华北", "天津", null, 439.350, -5.0, null),
        RepeaterEntry("华北", "石家庄", null, 439.650, -5.0, null),
        RepeaterEntry("华北", "太原", null, 439.875, -5.0, null),
        RepeaterEntry("华北", "呼和浩特", null, 439.500, -5.0, null),

        // ---------- 东北 ----------
        RepeaterEntry("东北", "沈阳", null, 439.600, -5.0, null),
        RepeaterEntry("东北", "大连", null, 439.900, -5.0, null),
        RepeaterEntry("东北", "长春", null, 439.400, -5.0, null),
        RepeaterEntry("东北", "哈尔滨", null, 439.800, -5.0, null),

        // ---------- 华东 ----------
        RepeaterEntry("华东", "上海", null, 439.525, -5.0, null),
        RepeaterEntry("华东", "南京", null, 439.350, -5.0, null),
        RepeaterEntry("华东", "杭州", null, 439.075, -5.0, null),
        RepeaterEntry("华东", "济南", null, 439.550, -5.0, null),
        RepeaterEntry("华东", "青岛", null, 439.700, -5.0, null),
        RepeaterEntry("华东", "合肥", null, 439.450, -5.0, null),
        RepeaterEntry("华东", "福州", null, 439.250, -5.0, null),
        RepeaterEntry("华东", "厦门", null, 439.050, -5.0, null),

        // ---------- 华中 ----------
        RepeaterEntry("华中", "武汉", null, 439.500, -5.0, null),
        RepeaterEntry("华中", "长沙", null, 439.250, -5.0, null),
        RepeaterEntry("华中", "郑州", null, 439.800, -5.0, null),
        RepeaterEntry("华中", "南昌", null, 439.850, -5.0, null),

        // ---------- 华南 ----------
        RepeaterEntry("华南", "广州", null, 439.750, -5.0, null),
        RepeaterEntry("华南", "深圳", null, 439.460, -5.0, null),
        RepeaterEntry("华南", "南宁", null, 439.300, -5.0, null),
        RepeaterEntry("华南", "海口", null, 439.150, -5.0, null),

        // ---------- 西南 ----------
        RepeaterEntry("西南", "成都", null, 439.150, -5.0, null),
        RepeaterEntry("西南", "重庆", null, 439.775, -5.0, null),
        RepeaterEntry("西南", "昆明", null, 439.375, -5.0, null),
        RepeaterEntry("西南", "贵阳", null, 439.550, -5.0, null),
        RepeaterEntry("西南", "拉萨", null, 439.200, -5.0, null),

        // ---------- 西北 ----------
        RepeaterEntry("西北", "西安", null, 439.300, -5.0, null),
        RepeaterEntry("西北", "兰州", null, 439.700, -5.0, null),
        RepeaterEntry("西北", "乌鲁木齐", null, 439.650, -5.0, null),
        RepeaterEntry("西北", "银川", null, 439.500, -5.0, null),
        RepeaterEntry("西北", "西宁", null, 439.850, -5.0, null)
    )

    /** 检索卫星：命中名称、别名或模式。 */
    fun searchSatellite(keyword: String): List<SatelliteEntry> {
        val query = keyword.trim().lowercase()
        if (query.isEmpty()) return satellites
        return satellites.filter { e ->
            e.name.lowercase().contains(query) ||
                (e.alias?.lowercase()?.contains(query) ?: false) ||
                e.mode.lowercase().contains(query) ||
                (e.note?.lowercase()?.contains(query) ?: false)
        }
    }

    /** 检索中继：命中城市、大区、呼号或备注。 */
    fun searchRepeater(keyword: String): List<RepeaterEntry> {
        val query = keyword.trim().lowercase()
        if (query.isEmpty()) return repeaters
        return repeaters.filter { e ->
            e.city.lowercase().contains(query) ||
                e.region.lowercase().contains(query) ||
                (e.callsign?.lowercase()?.contains(query) ?: false) ||
                e.mode.lowercase().contains(query) ||
                (e.note?.lowercase()?.contains(query) ?: false)
        }
    }

    /** 按大区分组，保持 regions 定义的顺序。 */
    fun groupByRegion(list: List<RepeaterEntry> = repeaters): Map<String, List<RepeaterEntry>> =
        list.groupBy { it.region }
            .toSortedMap(compareBy { regions.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } })
}

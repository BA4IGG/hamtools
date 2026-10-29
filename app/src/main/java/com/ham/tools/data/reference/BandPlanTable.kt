package com.ham.tools.data.reference

/**
 * 业余业务频段划分表。
 *
 * 数据按中国现行划分与 IARU 三个区的常见惯例整理。需要注意：
 * - 多数频段上业余业务是**次要业务**，必须避让同频段的主要业务（如 40m 的广播、
 *   30m/17m/12m 的固定与移动业务），这是法定义务而非建议
 * - 60m 各国差异极大，且不少国家按「频道」而非「频段」指配，务必查本国规定
 * - 4m 仅部分国家开放，中国未指配给业余业务
 * - 2200m / 630m 受等效全向辐射功率与带宽限制，且需不与授时等业务冲突
 *
 * 本表为工程速查，实际发射前应以本国现行频率划分规定为准。
 */
data class BandPlanEntry(
    /** 频段俗称，如 "20m"。 */
    val band: String,
    val startMHz: Double,
    val endMHz: Double,
    /** 该频段常见操作模式。 */
    val modes: String,
    /** 典型用途或传播特点。 */
    val usage: String,
    val note: String? = null
)

/** 划分区域。中国单列，其余按 IARU 三个区。 */
enum class BandRegion(val labelKey: String) {
    CN("region_cn"),
    R1("region_r1"),
    R2("region_r2"),
    R3("region_r3")
}

object BandPlanTable {

    val entries: Map<BandRegion, List<BandPlanEntry>> = mapOf(
        BandRegion.CN to listOf(
            BandPlanEntry("2200m", 0.1357, 0.1378, "CW / 窄带数字", "地波传播，需严格控制辐射功率与带宽"),
            BandPlanEntry("630m", 0.472, 0.479, "CW / 窄带数字", "中波低频段，地波为主"),
            BandPlanEntry("160m", 1.800, 2.000, "CW / SSB / 数字", "夜间为主，冬季 DX 效果好", "以当地划分规定为准"),
            BandPlanEntry("80m", 3.500, 3.900, "CW / SSB / 数字", "夜间区域通信，白天近距离"),
            BandPlanEntry("60m", 5.3515, 5.3665, "USB / CW / 数字", "近年逐步开放，按指配频道工作", "各国差异极大，请以本国规定为准"),
            BandPlanEntry("40m", 7.000, 7.200, "CW / SSB / 数字", "昼夜通用，本地与区域通信主力"),
            BandPlanEntry("30m", 10.100, 10.150, "CW / 窄带数字", "昼夜通用，带宽受限仅窄带", "禁止 SSB"),
            BandPlanEntry("20m", 14.000, 14.350, "CW / SSB / 数字", "全球 DX 主力频段"),
            BandPlanEntry("17m", 18.068, 18.168, "CW / SSB / 数字", "传播特性接近 20m，干扰较少"),
            BandPlanEntry("15m", 21.000, 21.450, "CW / SSB / 数字", "太阳活动高年 DX 优秀"),
            BandPlanEntry("12m", 24.890, 24.990, "CW / SSB / 数字", "传播接近 10m，带宽小", "禁止 SSB"),
            BandPlanEntry("10m", 28.000, 29.700, "CW / SSB / FM / 数字", "太阳活动高年可全球通联"),
            BandPlanEntry("6m", 50.000, 54.000, "CW / SSB / FM / 数字", "「魔法频段」， sporadic-E 时有惊喜"),
            BandPlanEntry("2m", 144.000, 148.000, "FM / SSB / CW / 数字", "本地中继与卫星通信主力"),
            BandPlanEntry("70cm", 430.000, 440.000, "FM / 数字 / 卫星", "城市中中继与数字模式常用"),
            BandPlanEntry("23cm", 1240.000, 1300.000, "FM / SSB / 数字", "业余电视与高速数据试验"),
            BandPlanEntry("13cm", 2300.000, 2450.000, "SSB / 数字 / 宽带", "宽带与高速实验，需备案", "业余为次要业务")
        ),

        BandRegion.R1 to listOf(
            BandPlanEntry("2200m", 0.1357, 0.1378, "CW / 窄带数字", "地波传播"),
            BandPlanEntry("630m", 0.472, 0.479, "CW / 窄带数字", "中波低频段"),
            BandPlanEntry("160m", 1.810, 2.000, "CW / SSB / 数字", "夜间为主"),
            BandPlanEntry("80m", 3.500, 3.800, "CW / SSB / 数字", "区域通信"),
            BandPlanEntry("60m", 5.3515, 5.3665, "USB / CW / 数字", "按本国指配"),
            BandPlanEntry("40m", 7.000, 7.200, "CW / SSB / 数字", "昼夜通用"),
            BandPlanEntry("30m", 10.100, 10.150, "CW / 窄带数字", "窄带专用"),
            BandPlanEntry("20m", 14.000, 14.350, "CW / SSB / 数字", "全球 DX"),
            BandPlanEntry("17m", 18.068, 18.168, "CW / SSB / 数字", "低干扰 DX"),
            BandPlanEntry("15m", 21.000, 21.450, "CW / SSB / 数字", "高年 DX"),
            BandPlanEntry("12m", 24.890, 24.990, "CW / 窄带数字", "窄带为主"),
            BandPlanEntry("10m", 28.000, 29.700, "CW / SSB / FM", "高年全球通联"),
            BandPlanEntry("6m", 50.000, 52.000, "CW / SSB / FM", "sporadic-E"),
            BandPlanEntry("4m", 70.000, 70.500, "CW / SSB / FM", "仅部分国家开放"),
            BandPlanEntry("2m", 144.000, 146.000, "FM / SSB / CW", "本地中继"),
            BandPlanEntry("70cm", 430.000, 440.000, "FM / 数字", "城市中数字模式"),
            BandPlanEntry("23cm", 1240.000, 1300.000, "FM / SSB / 数字", "宽带实验")
        ),

        BandRegion.R2 to listOf(
            BandPlanEntry("2200m", 0.1357, 0.1378, "CW / 窄带数字", "地波传播"),
            BandPlanEntry("630m", 0.472, 0.479, "CW / 窄带数字", "中波低频段"),
            BandPlanEntry("160m", 1.800, 2.000, "CW / SSB / 数字", "夜间为主"),
            BandPlanEntry("80m", 3.500, 4.000, "CW / SSB / 数字", "区域通信，上限更宽"),
            BandPlanEntry("60m", 5.3305, 5.4035, "USB / CW / 数字", "按本国指配频道"),
            BandPlanEntry("40m", 7.000, 7.300, "CW / SSB / 数字", "上限比 1/3 区高"),
            BandPlanEntry("30m", 10.100, 10.150, "CW / 窄带数字", "窄带专用"),
            BandPlanEntry("20m", 14.000, 14.350, "CW / SSB / 数字", "全球 DX"),
            BandPlanEntry("17m", 18.068, 18.168, "CW / SSB / 数字", "低干扰 DX"),
            BandPlanEntry("15m", 21.000, 21.450, "CW / SSB / 数字", "高年 DX"),
            BandPlanEntry("12m", 24.890, 24.990, "CW / 窄带数字", "窄带为主"),
            BandPlanEntry("10m", 28.000, 29.700, "CW / SSB / FM", "高年全球通联"),
            BandPlanEntry("6m", 50.000, 54.000, "CW / SSB / FM", "sporadic-E"),
            BandPlanEntry("2m", 144.000, 148.000, "FM / SSB / CW", "本地中继"),
            BandPlanEntry("70cm", 420.000, 450.000, "FM / 数字", "部分地区更宽"),
            BandPlanEntry("23cm", 1240.000, 1300.000, "FM / SSB / 数字", "宽带实验")
        ),

        BandRegion.R3 to listOf(
            BandPlanEntry("2200m", 0.1357, 0.1378, "CW / 窄带数字", "地波传播"),
            BandPlanEntry("630m", 0.472, 0.479, "CW / 窄带数字", "中波低频段"),
            BandPlanEntry("160m", 1.800, 2.000, "CW / SSB / 数字", "夜间为主"),
            BandPlanEntry("80m", 3.500, 3.900, "CW / SSB / 数字", "区域通信"),
            BandPlanEntry("60m", 5.3515, 5.3665, "USB / CW / 数字", "按本国指配"),
            BandPlanEntry("40m", 7.000, 7.200, "CW / SSB / 数字", "昼夜通用"),
            BandPlanEntry("30m", 10.100, 10.150, "CW / 窄带数字", "窄带专用"),
            BandPlanEntry("20m", 14.000, 14.350, "CW / SSB / 数字", "全球 DX"),
            BandPlanEntry("17m", 18.068, 18.168, "CW / SSB / 数字", "低干扰 DX"),
            BandPlanEntry("15m", 21.000, 21.450, "CW / SSB / 数字", "高年 DX"),
            BandPlanEntry("12m", 24.890, 24.990, "CW / 窄带数字", "窄带为主"),
            BandPlanEntry("10m", 28.000, 29.700, "CW / SSB / FM", "高年全球通联"),
            BandPlanEntry("6m", 50.000, 54.000, "CW / SSB / FM", "sporadic-E"),
            BandPlanEntry("2m", 144.000, 148.000, "FM / SSB / CW", "本地中继"),
            BandPlanEntry("70cm", 430.000, 440.000, "FM / 数字", "城市中数字模式"),
            BandPlanEntry("23cm", 1240.000, 1300.000, "FM / SSB / 数字", "宽带实验")
        )
    )

    fun entriesOf(region: BandRegion): List<BandPlanEntry> = entries[region].orEmpty()

    /**
     * 按关键词过滤：命中频段名、模式或用途说明。
     * 空关键词返回完整列表。
     */
    fun search(region: BandRegion, keyword: String): List<BandPlanEntry> {
        val query = keyword.trim().lowercase()
        if (query.isEmpty()) return entriesOf(region)
        return entriesOf(region).filter { entry ->
            entry.band.lowercase().contains(query) ||
                entry.modes.lowercase().contains(query) ||
                entry.usage.lowercase().contains(query) ||
                (entry.note?.lowercase()?.contains(query) ?: false)
        }
    }
}

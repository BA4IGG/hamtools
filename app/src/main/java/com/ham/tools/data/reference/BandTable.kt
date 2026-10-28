package com.ham.tools.data.reference

/**
 * 业余业务常用频段划分（MHz）。
 *
 * 数据按 IARU / ITU 常见惯例整理，各国（尤其 60m 与 4m）差异较大，
 * 实际使用请以本国现行频率划分规定为准。类型列的 names(x) 仅作提示。
 */
data class HamBand(
    val name: String,
    val startMHz: Double,
    val endMHz: Double,
    val note: String? = null
)

object BandTable {

    val bands: List<HamBand> = listOf(
        HamBand("2200m", 0.1357, 0.1378),
        HamBand("630m", 0.472, 0.479),
        HamBand("160m", 1.810, 2.000),
        HamBand("80m", 3.500, 4.000, "3 区上限 3.900，2 区到 4.000"),
        HamBand("60m", 5.3515, 5.3665, "各国差异极大，请以本国规定为准"),
        HamBand("40m", 7.000, 7.300, "1/3 区上限 7.200"),
        HamBand("30m", 10.100, 10.150),
        HamBand("20m", 14.000, 14.350),
        HamBand("17m", 18.068, 18.168),
        HamBand("15m", 21.000, 21.450),
        HamBand("12m", 24.890, 24.990),
        HamBand("10m", 28.000, 29.700),
        HamBand("6m", 50.000, 54.000),
        HamBand("4m", 70.000, 70.500, "仅部分国家开放"),
        HamBand("2m", 144.000, 148.000, "1 区上限 146.000"),
        HamBand("70cm", 430.000, 440.000, "1 区（含中国）为 430–440"),
        HamBand("23cm", 1240.0, 1300.0)
    )

    fun bandFor(frequencyMHz: Double): HamBand? =
        bands.firstOrNull { frequencyMHz >= it.startMHz && frequencyMHz <= it.endMHz }
}

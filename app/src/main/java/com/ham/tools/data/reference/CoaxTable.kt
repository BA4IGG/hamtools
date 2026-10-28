package com.ham.tools.data.reference

/**
 * 常用同轴馈线。
 *
 * lossDbPer100Feet 取自公开的同轴电缆衰减对照表，
 * 依次对应 CoaxTable.REFERENCE_FREQUENCIES_MHZ 中的频点，
 * 缺失频点由双对数插值估算（见 HamMath.coaxLossDbPer100Feet）。
 */
data class CoaxCable(
    val id: String,
    val name: String,
    val impedanceOhm: Int,
    val lossDbPer100Feet: List<Double>,
    val note: String? = null
)

object CoaxTable {

    /** 衰减对照频点：10 / 30 / 50 / 100 / 150 / 220 / 450 MHz。 */
    val REFERENCE_FREQUENCIES_MHZ: List<Double> =
        listOf(10.0, 30.0, 50.0, 100.0, 150.0, 220.0, 450.0)

    val cables: List<CoaxCable> = listOf(
        CoaxCable(
            id = "rg174",
            name = "RG-174",
            impedanceOhm = 50,
            lossDbPer100Feet = listOf(3.3, 5.5, 6.6, 8.9, 13.0, 17.3, 25.0),
            note = "极细跳线，仅适合机内短接"
        ),
        CoaxCable(
            id = "rg316",
            name = "RG-316",
            impedanceOhm = 50,
            lossDbPer100Feet = listOf(2.1, 4.2, 5.6, 7.9, 9.6, 11.9, 17.0),
            note = "耐高温细缆，短跳线常用"
        ),
        CoaxCable(
            id = "rg58",
            name = "RG-58A/U",
            impedanceOhm = 50,
            lossDbPer100Feet = listOf(1.4, 2.5, 3.1, 4.9, 6.2, 7.4, 10.6),
            note = "HF 短距可用，不建议 UHF 长馈"
        ),
        CoaxCable(
            id = "rg8x",
            name = "RG-8X",
            impedanceOhm = 50,
            lossDbPer100Feet = listOf(1.0, 2.0, 2.5, 3.6, 4.7, 6.0, 8.6),
            note = "轻量馈线，便携台常用"
        ),
        CoaxCable(
            id = "rg213",
            name = "RG-213 / RG-8",
            impedanceOhm = 50,
            lossDbPer100Feet = listOf(0.6, 1.2, 1.6, 2.2, 2.8, 3.5, 5.2),
            note = "经典 HF 馈线，短波固定台首选"
        ),
        CoaxCable(
            id = "b9913",
            name = "Belden 9913",
            impedanceOhm = 50,
            lossDbPer100Feet = listOf(0.4, 0.8, 1.1, 1.3, 1.7, 2.1, 3.1),
            note = "低损耗半刚性馈线"
        ),
        CoaxCable(
            id = "lmr400",
            name = "LMR-400",
            impedanceOhm = 50,
            lossDbPer100Feet = listOf(0.4, 0.8, 0.9, 1.2, 1.5, 1.8, 2.7),
            note = "VHF/UHF 基地台标准配置"
        ),
        CoaxCable(
            id = "half_superflex",
            name = "1/2\" Superflex",
            impedanceOhm = 50,
            lossDbPer100Feet = listOf(0.3, 0.56, 0.73, 1.04, 1.3, 1.6, 2.3),
            note = "半英寸皱纹铜管馈线"
        )
    )

    fun find(id: String?): CoaxCable? = cables.firstOrNull { it.id == id }
}

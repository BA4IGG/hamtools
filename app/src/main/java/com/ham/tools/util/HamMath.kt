package com.ham.tools.util

import com.ham.tools.data.reference.CoaxCable
import com.ham.tools.data.reference.CoaxTable
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * HAM 工具合集的核心射频计算。
 *
 * 全部为无状态纯函数，不依赖 Android 框架，方便用 JVM 单元测试直接验证。
 */
object HamMath {

    const val SPEED_OF_LIGHT_MPS = 299_792_458.0
    const val FEET_PER_METER = 3.280_839_895
    const val METERS_PER_FOOT = 0.3048

    /** 短波惯例：S9 对应接收机输入端 -73 dBm，每档相差 6 dB。 */
    const val S9_DBM = -73.0
    const val DB_PER_S_UNIT = 6.0

    // ------------------------------------------------------------------
    // 频率 / 波长
    // ------------------------------------------------------------------

    fun wavelengthMeters(frequencyHz: Double): Double =
        if (frequencyHz > 0.0) SPEED_OF_LIGHT_MPS / frequencyHz else Double.NaN

    fun frequencyHz(wavelengthMeters: Double): Double =
        if (wavelengthMeters > 0.0) SPEED_OF_LIGHT_MPS / wavelengthMeters else Double.NaN

    fun metersToFeet(meters: Double): Double = meters * FEET_PER_METER

    fun feetToMeters(feet: Double): Double = feet * METERS_PER_FOOT

    // ------------------------------------------------------------------
    // 功率 / 分贝
    // ------------------------------------------------------------------

    fun wattToDbm(watt: Double): Double =
        if (watt > 0.0) 10.0 * log10(watt * 1000.0) else Double.NaN

    fun dbmToWatt(dbm: Double): Double = 10.0.pow(dbm / 10.0) / 1000.0

    fun dbToRatio(db: Double): Double = 10.0.pow(db / 10.0)

    fun ratioToDb(ratio: Double): Double =
        if (ratio > 0.0) 10.0 * log10(ratio) else Double.NaN

    // ------------------------------------------------------------------
    // 驻波 / 失配
    // ------------------------------------------------------------------

    /** 由驻波比求反射系数模值 |Γ| = (S-1)/(S+1)。 */
    fun reflectionCoefficientFromVswr(vswr: Double): Double =
        if (vswr >= 1.0) (vswr - 1.0) / (vswr + 1.0) else Double.NaN

    /** 由反射系数模值反算驻波比 S = (1+Γ)/(1-Γ)。 */
    fun vswrFromReflectionCoefficient(gamma: Double): Double =
        if (gamma >= 0.0 && gamma < 1.0) (1.0 + gamma) / (1.0 - gamma) else Double.NaN

    /** 回波损耗 RL = -20·log10(Γ)，完全匹配时为 +∞。 */
    fun returnLossDb(gamma: Double): Double =
        if (gamma > 0.0 && gamma < 1.0) -20.0 * log10(gamma) else Double.POSITIVE_INFINITY

    /** 失配损耗 ML = -10·log10(1-Γ²)。 */
    fun mismatchLossDb(gamma: Double): Double =
        if (gamma >= 0.0 && gamma < 1.0) -10.0 * log10(1.0 - gamma * gamma)
        else Double.POSITIVE_INFINITY

    /** 反射功率占总功率的比例 Γ²。 */
    fun reflectedPowerFraction(gamma: Double): Double = gamma * gamma

    // ------------------------------------------------------------------
    // 天线尺寸
    // ------------------------------------------------------------------

    /**
     * 振子长度 = 波长 × 波长系数 × 缩短系数。
     *
     * @param wavelengthFraction 0.5 表示半波、0.25 表示四分之一波长。
     * @param velocityFactor 实际天线中的传播速率因子，导线振子通常取 0.95 左右。
     */
    fun antennaLengthMeters(
        frequencyHz: Double,
        wavelengthFraction: Double,
        velocityFactor: Double = 1.0
    ): Double {
        val wave = wavelengthMeters(frequencyHz)
        return if (wave.isNaN()) Double.NaN else wave * wavelengthFraction * velocityFactor
    }

    // ------------------------------------------------------------------
    // 馈线损耗
    // ------------------------------------------------------------------

    /** 按对数（双对数）插值求每 100 英尺损耗，单位 dB。 */
    fun coaxLossDbPer100Feet(cable: CoaxCable, frequencyMHz: Double): Double {
        if (frequencyMHz <= 0.0) return Double.NaN

        val freqs = CoaxTable.REFERENCE_FREQUENCIES_MHZ
        val losses = cable.lossDbPer100Feet
        check(freqs.size == losses.size) { "馈线数据与参考频率点数量不一致" }

        return when {
            frequencyMHz <= freqs.first() -> {
                val k = slope(freqs[0], freqs[1], losses[0], losses[1])
                interpolate(freqs[0], losses[0], frequencyMHz, k)
            }
            frequencyMHz >= freqs.last() -> {
                val last = freqs.lastIndex
                val k = slope(freqs[last - 1], freqs[last], losses[last - 1], losses[last])
                interpolate(freqs[last], losses[last], frequencyMHz, k)
            }
            else -> {
                val upper = freqs.indexOfFirst { it >= frequencyMHz }
                val k = slope(freqs[upper - 1], freqs[upper], losses[upper - 1], losses[upper])
                interpolate(freqs[upper - 1], losses[upper - 1], frequencyMHz, k)
            }
        }
    }

    fun coaxLossDbPer100Meters(cable: CoaxCable, frequencyMHz: Double): Double =
        coaxLossDbPer100Feet(cable, frequencyMHz) * FEET_PER_METER

    /** 匹配状态下的线缆总损耗 dB。 */
    fun matchedLineLossDb(
        cable: CoaxCable,
        frequencyMHz: Double,
        lengthMeters: Double
    ): Double = coaxLossDbPer100Meters(cable, frequencyMHz) * lengthMeters / 100.0

    /**
     * ARRL 公式：考虑驻波引起的往返多次穿越后的实际总损耗。
     *
     * 当 SWR = 1 时结果退化为 [matchedLossDb]。
     */
    fun totalLineLossDb(matchedLossDb: Double, vswr: Double): Double {
        val gamma = reflectionCoefficientFromVswr(vswr)
        if (gamma.isNaN() || gamma >= 1.0) return Double.NaN

        val a = dbToRatio(matchedLossDb)
        val numerator = a * a - gamma * gamma
        val denominator = a * (1.0 - gamma * gamma)
        if (numerator <= 0.0 || denominator <= 0.0) return Double.NaN

        return ratioToDb(numerator / denominator)
    }

    /** 经过损耗后剩余功率 W。 */
    fun powerAfterLossWatt(inputWatt: Double, lossDb: Double): Double =
        if (lossDb.isNaN()) Double.NaN else inputWatt / dbToRatio(lossDb)

    // ------------------------------------------------------------------
    // 链路预算
    // ------------------------------------------------------------------

    /** 自由空间路径损耗 FSPL(dB) = 32.44 + 20log10(d/km) + 20log10(f/MHz)。 */
    fun freeSpacePathLossDb(distanceKm: Double, frequencyMHz: Double): Double =
        if (distanceKm > 0.0 && frequencyMHz > 0.0) {
            32.44 + 20.0 * log10(distanceKm) + 20.0 * log10(frequencyMHz)
        } else {
            Double.NaN
        }

    fun receivedPowerDbm(
        txPowerDbm: Double,
        txGainDbi: Double,
        rxGainDbi: Double,
        pathLossDb: Double,
        extraLossDb: Double = 0.0
    ): Double = txPowerDbm + txGainDbi + rxGainDbi - pathLossDb - extraLossDb

    /** 折算到指定阻抗上的接收电平（有效值），返回 µV。 */
    fun dbmToMicrovolt(dbm: Double, impedanceOhm: Double = 50.0): Double =
        sqrt(dbmToWatt(dbm) * impedanceOhm) * 1_000_000.0

    /** S 表读数，返回 S 单位数（9 表示 S9，>9 表示超 S9）。 */
    fun sMeterUnits(dbm: Double): Double = 9.0 + (dbm - S9_DBM) / DB_PER_S_UNIT

    // ------------------------------------------------------------------

    private fun slope(f0: Double, f1: Double, loss0: Double, loss1: Double): Double =
        ln(loss1 / loss0) / ln(f1 / f0)

    private fun interpolate(
        baseFrequency: Double,
        baseLoss: Double,
        targetFrequency: Double,
        k: Double
    ): Double = baseLoss * (targetFrequency / baseFrequency).pow(k)
}

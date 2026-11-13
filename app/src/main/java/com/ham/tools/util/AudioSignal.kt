package com.ham.tools.util

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** 音调发生器支持的四种基本波形。 */
enum class Waveform(val label: String) {
    SINE("正弦波"),
    SQUARE("方波"),
    TRIANGLE("三角波"),
    SAWTOOTH("锯齿波")
}

/**
 * 纯 Kotlin 的 PCM 信号合成，无 Android 依赖，便于 JVM 单元测试。
 *
 * 所有波形都带 5ms 线性渐入渐出：直接从 0 幅度跳到满幅会让扬声器「啪」一声，
 * 连续按键（CW 电键、拨号盘）时尤其明显。
 *
 * 采用 16bit 单声道 PCM，与 AudioTrack 的 ENCODING_PCM_16BIT 直接对接。
 */
object AudioSignal {

    const val SAMPLE_RATE = 44100
    private const val RAMP_MS = 5
    private const val TWO_PI = 2.0 * PI
    private const val FULL_SCALE = 32767.0

    /** 单音，可指定波形。 */
    fun tone(
        freqHz: Double,
        durationMs: Int,
        waveform: Waveform = Waveform.SINE,
        amplitude: Double = 0.6,
        sampleRate: Int = SAMPLE_RATE
    ): ShortArray {
        val total = samples(durationMs, sampleRate)
        val out = ShortArray(total)
        val ramp = rampSamples(sampleRate)
        val step = TWO_PI * freqHz / sampleRate
        var phase = 0.0
        for (i in 0 until total) {
            val env = envelope(i, total, ramp)
            out[i] = toShort(amplitude * env * shape(phase, waveform))
            phase = (phase + step) % TWO_PI
        }
        return out
    }

    /** 双音叠加，用于 DTMF。两个正弦各占一半幅度，避免叠加后削顶。 */
    fun dualTone(
        lowHz: Double,
        highHz: Double,
        durationMs: Int,
        amplitude: Double = 0.6,
        sampleRate: Int = SAMPLE_RATE
    ): ShortArray {
        val total = samples(durationMs, sampleRate)
        val out = ShortArray(total)
        val ramp = rampSamples(sampleRate)
        val stepLow = TWO_PI * lowHz / sampleRate
        val stepHigh = TWO_PI * highHz / sampleRate
        var pLow = 0.0
        var pHigh = 0.0
        for (i in 0 until total) {
            val env = envelope(i, total, ramp)
            out[i] = toShort(amplitude * env * 0.5 * (sin(pLow) + sin(pHigh)))
            pLow = (pLow + stepLow) % TWO_PI
            pHigh = (pHigh + stepHigh) % TWO_PI
        }
        return out
    }

    /** 线性扫频：瞬时频率从 fromHz 匀速变到 toHz。 */
    fun sweep(
        fromHz: Double,
        toHz: Double,
        durationMs: Int,
        amplitude: Double = 0.6,
        sampleRate: Int = SAMPLE_RATE
    ): ShortArray {
        val total = samples(durationMs, sampleRate)
        val out = ShortArray(total)
        val ramp = rampSamples(sampleRate)
        var phase = 0.0
        for (i in 0 until total) {
            val t = i.toDouble() / total
            val f = fromHz + (toHz - fromHz) * t
            val env = envelope(i, total, ramp)
            out[i] = toShort(amplitude * env * sin(phase))
            phase = (phase + TWO_PI * f / sampleRate) % TWO_PI
        }
        return out
    }

    /** 静音片段，用于拼接拨号串时留间隔。 */
    fun silence(durationMs: Int, sampleRate: Int = SAMPLE_RATE): ShortArray =
        ShortArray(samples(durationMs, sampleRate))

    /** 按顺序拼接多段 PCM。 */
    fun concat(parts: List<ShortArray>): ShortArray {
        val out = ShortArray(parts.sumOf { it.size })
        var offset = 0
        for (part in parts) {
            part.copyInto(out, offset)
            offset += part.size
        }
        return out
    }

    private fun samples(durationMs: Int, sampleRate: Int): Int =
        (durationMs.coerceAtLeast(0) * sampleRate / 1000).coerceAtLeast(1)

    private fun rampSamples(sampleRate: Int): Int =
        (sampleRate * RAMP_MS / 1000).coerceAtLeast(1)

    private fun envelope(i: Int, total: Int, ramp: Int): Double {
        val edge = minOf(i, total - 1 - i).coerceAtLeast(0)
        return (edge.toDouble() / ramp).coerceAtMost(1.0)
    }

    /** 波形在给定相位上的取值，范围 [-1, 1]。持续音合成器也会复用它。 */
    fun shape(phase: Double, waveform: Waveform): Double = when (waveform) {
        Waveform.SINE -> sin(phase)
        Waveform.SQUARE -> if (sin(phase) >= 0.0) 1.0 else -1.0
        Waveform.TRIANGLE -> 2.0 / PI * asin(sin(phase))
        Waveform.SAWTOOTH -> {
            val n = phase / TWO_PI
            2.0 * (n - floor(n)) - 1.0
        }
    }

    private fun toShort(value: Double): Short =
        (value * FULL_SCALE).toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
}

/**
 * DTMF 编解码。
 *
 * 频率组遵循 ITU-T Q.23：低群 697/770/852/941 Hz，高群 1209/1336/1477/1633 Hz，
 * 4x4 矩阵覆盖 0-9、*、# 与 A-D（A-D 为军用/专网扩展键）。
 *
 * 解码用 Goertzel 算法：只需对 8 个已知频率各做一次二阶递推，
 * 比完整 FFT 更省算力，是 DTMF 接收机的标准做法。
 */
object DtmfCodec {

    val LOW_FREQ = doubleArrayOf(697.0, 770.0, 852.0, 941.0)
    val HIGH_FREQ = doubleArrayOf(1209.0, 1336.0, 1477.0, 1633.0)

    /** 行优先：低频下标 * 4 + 高频下标。 */
    const val KEYS = "123A456B789C*0#D"

    fun freqsOf(key: Char): Pair<Double, Double>? {
        val index = KEYS.indexOf(key.uppercaseChar())
        if (index < 0) return null
        return LOW_FREQ[index / 4] to HIGH_FREQ[index % 4]
    }

    fun keyOf(lowIndex: Int, highIndex: Int): Char? =
        KEYS.getOrNull(lowIndex * 4 + highIndex)

    /** 合成单个按键的信号，默认 100ms 键音 + 默认幅度。 */
    fun encode(key: Char, durationMs: Int = 100, sampleRate: Int = AudioSignal.SAMPLE_RATE): ShortArray? {
        val (low, high) = freqsOf(key) ?: return null
        return AudioSignal.dualTone(low, high, durationMs, sampleRate = sampleRate)
    }

    /**
     * 从一段 PCM 中识别按键。
     *
     * 判据有三层，避免把语音或单音误判成 DTMF：
     * 1. 低群、高群各取能量最大的频点；
     * 2. 这两个频点的能量之和要占 8 个频点总能量的 55% 以上；
     * 3. 峰值能量要显著高于同群的次大值（1.4 倍）。
     */
    fun decode(
        pcm: ShortArray,
        sampleRate: Int = AudioSignal.SAMPLE_RATE,
        offset: Int = 0,
        length: Int = pcm.size - offset
    ): Char? {
        if (length < 256 || offset < 0 || offset + length > pcm.size) return null
        val lowPowers = LOW_FREQ.map { goertzel(pcm, sampleRate, it, offset, length) }
        val highPowers = HIGH_FREQ.map { goertzel(pcm, sampleRate, it, offset, length) }
        val li = lowPowers.indices.maxByOrNull { lowPowers[it] } ?: return null
        val hi = highPowers.indices.maxByOrNull { highPowers[it] } ?: return null
        val peak = lowPowers[li] + highPowers[hi]
        val total = lowPowers.sum() + highPowers.sum()
        if (total <= 0.0 || peak < total * 0.55) return null
        // DTMF 双音的两组幅度相近；单音或语音会在这里被挡下
        if (highPowers[hi] < lowPowers[li] * 0.15) return null
        val secondLow = lowPowers.filterIndexed { i, _ -> i != li }.maxOrNull() ?: 0.0
        val secondHigh = highPowers.filterIndexed { i, _ -> i != hi }.maxOrNull() ?: 0.0
        if (lowPowers[li] < secondLow * 1.4 || highPowers[hi] < secondHigh * 1.4) return null
        return keyOf(li, hi)
    }

    /** 解码整段拨号串：按 toneMs + gapMs 的固定节奏切片。 */
    fun decodeSequence(
        pcm: ShortArray,
        toneMs: Int = 100,
        gapMs: Int = 60,
        sampleRate: Int = AudioSignal.SAMPLE_RATE
    ): String {
        val toneLen = toneMs * sampleRate / 1000
        val stride = (toneMs + gapMs) * sampleRate / 1000
        if (toneLen <= 0 || stride <= 0) return ""
        val out = StringBuilder()
        var offset = 0
        while (offset + toneLen <= pcm.size) {
            // 只在音的中段取窗，避开渐入渐出造成的能量偏低
            val skip = toneLen / 5
            val key = decode(pcm, sampleRate, offset + skip, toneLen - skip * 2)
            if (key != null) out.append(key)
            offset += stride
        }
        return out.toString()
    }

    /** Goertzel：返回目标频率上的相对能量（未归一化，仅用于横向比较）。 */
    private fun goertzel(
        pcm: ShortArray,
        sampleRate: Int,
        freqHz: Double,
        offset: Int,
        length: Int
    ): Double {
        val coeff = 2.0 * cos(2.0 * PI * freqHz / sampleRate)
        var s1 = 0.0
        var s2 = 0.0
        for (i in 0 until length) {
            val sample = pcm[offset + i].toDouble() / Short.MAX_VALUE.toDouble()
            val s0 = sample + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        return s1 * s1 + s2 * s2 - coeff * s1 * s2
    }
}

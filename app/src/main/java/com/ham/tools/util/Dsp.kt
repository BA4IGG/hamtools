package com.ham.tools.util

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 编解码共用的纯 JVM 数字信号处理工具：重采样、FFT、功率谱、Goertzel。
 *
 * 不依赖 Android，也不申请任何权限；所有麦克风采集都在上层完成后把 PCM 传进来。
 */
object Dsp {

    fun toFloat(pcm: ShortArray, offset: Int = 0, length: Int = pcm.size - offset): FloatArray {
        val n = length.coerceAtLeast(0).coerceAtMost(pcm.size - offset)
        return FloatArray(n) { pcm[offset + it] / 32768.0f }
    }

    /**
     * 任意采样率重采样。降采样时先做盒式低通（等价于简易抗混叠），再线性插值；
     * 升采样时直接线性插值。对 FT8 的窄带 8-FSK 与 SSTV 的 FM 信号足够，
     * 但不追求高保真音频品质。
     */
    fun resample(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (srcRate == dstRate || input.isEmpty()) return input.copyOf()
        val ratio = srcRate.toDouble() / dstRate.toDouble()
        val outSize = ceil(input.size / ratio).toInt().coerceAtLeast(1)
        val out = FloatArray(outSize)
        val half = (ratio / 2.0).toInt().coerceIn(1, 32)
        for (i in 0 until outSize) {
            val pos = i * ratio
            val c = pos.toInt()
            val frac = (pos - c).toFloat()
            val a = boxMean(input, c, half)
            val b = boxMean(input, c + 1, half)
            out[i] = a * (1f - frac) + b * frac
        }
        return out
    }

    private fun boxMean(input: FloatArray, center: Int, half: Int): Float {
        val from = (center - half).coerceAtLeast(0)
        val to = (center + half).coerceAtMost(input.size - 1)
        if (to < from) return 0f
        var sum = 0f
        for (i in from..to) sum += input[i]
        return sum / (to - from + 1)
    }

    /** 原地 radix-2 复数 FFT，n 必须是 2 的幂。 */
    fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        require(n == im.size && n > 0 && (n and (n - 1)) == 0) { "FFT 长度必须是 2 的幂" }

        // 位反转置换
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wLenR = cos(ang).toFloat()
            val wLenI = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var wr = 1f
                var wi = 0f
                val half = len / 2
                for (k in 0 until half) {
                    val uR = re[i + k]
                    val uI = im[i + k]
                    val vR = re[i + k + half] * wr - im[i + k + half] * wi
                    val vI = re[i + k + half] * wi + im[i + k + half] * wr
                    re[i + k] = uR + vR
                    im[i + k] = uI + vI
                    re[i + k + half] = uR - vR
                    im[i + k + half] = uI - vI
                    val nextR = wr * wLenR - wi * wLenI
                    wi = wr * wLenI + wi * wLenR
                    wr = nextR
                }
                i += len
            }
            len = len shl 1
        }
    }

    fun hann(n: Int): FloatArray = FloatArray(n) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * i / (n - 1))).toFloat()
    }

    /**
     * 短时傅里叶功率谱。返回 [帧][频点]，频点 i 对应频率 i * sampleRate / fftSize。
     * 窗长取一个符号左右，窗后补零到 fftSize 提高频率读数精度。
     */
    fun spectrogram(
        signal: FloatArray,
        sampleRate: Int,
        fftSize: Int,
        hop: Int,
        windowSize: Int
    ): Array<FloatArray> {
        val window = hann(windowSize)
        val bins = fftSize / 2 + 1
        val frames = ((signal.size - windowSize) / hop + 1).coerceAtLeast(0)
        return Array(frames) { f ->
            val re = FloatArray(fftSize)
            val im = FloatArray(fftSize)
            val start = f * hop
            for (k in 0 until windowSize) re[k] = signal[start + k] * window[k]
            fft(re, im)
            FloatArray(bins) { b -> re[b] * re[b] + im[b] * im[b] }
        }
    }

    /** 在谱图上取某个频率附近的功率，带线性插值与相邻频点补偿，抵抗基频不落在整数 bin 上。 */
    fun specPower(
        spec: Array<FloatArray>,
        frame: Int,
        fftSize: Int,
        sampleRate: Int,
        freqHz: Double
    ): Float {
        if (spec.isEmpty()) return 0f
        val f = frame.coerceIn(0, spec.lastIndex)
        val row = spec[f]
        val bin = freqHz * fftSize / sampleRate
        val b0 = bin.toInt().coerceIn(0, row.size - 2)
        val frac = (bin - b0).toFloat()
        val p: (Int) -> Float = { b -> if (b in 0 until row.size) row[b] else 0f }
        val center = p(b0) * (1f - frac) + p(b0 + 1) * frac
        return center + 0.4f * (p(b0 - 1) + p(b0 + 2))
    }

    /** Goertzel：返回目标频率上的相对功率，用于窄带频点检测（VIS、同步脉冲）。 */
    fun goertzelPower(
        pcm: FloatArray,
        sampleRate: Int,
        freqHz: Double,
        offset: Int = 0,
        length: Int = pcm.size - offset
    ): Double {
        if (length <= 0) return 0.0
        val coeff = 2.0 * cos(2.0 * PI * freqHz / sampleRate)
        var s1 = 0.0
        var s2 = 0.0
        for (i in 0 until length) {
            val s0 = pcm[offset + i].toDouble() + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        return (s1 * s1 + s2 * s2 - coeff * s1 * s2) / (length * length)
    }

    /** 均方根电平，用于界面电平表。 */
    fun rms(pcm: ShortArray, offset: Int = 0, length: Int = pcm.size - offset): Float {
        if (length <= 0) return 0f
        var sum = 0.0
        for (i in 0 until length) {
            val v = pcm[offset + i] / 32768.0
            sum += v * v
        }
        return sqrt(sum / length).toFloat()
    }
}

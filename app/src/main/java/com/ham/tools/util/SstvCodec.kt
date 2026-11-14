package com.ham.tools.util

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 一条扫描线里的一个区段。频率由图像内容决定时 fixedHz 为 null。 */
data class SstvSegment(
    val kind: SstvChannel,
    val startMs: Double,
    val durationMs: Double,
    val fixedHz: Double? = null
)

/** 图像通道 / 固定电平区段。 */
enum class SstvChannel {
    SYNC, PORCH, SEPARATOR,
    Y, R, G, B, R_MINUS_Y, B_MINUS_Y
}

/** 解码出的一幅图：ARGB 像素、识别到的模式与实际解出的行数。 */
data class SstvImage(
    val mode: SstvMode,
    val width: Int,
    val height: Int,
    val argb: IntArray,
    val linesDecoded: Int,
    val note: String
)

/**
 * 支持的 SSTV 模式与公开的 VIS 码、扫描时序。
 *
 * 频率约定：同步 1200 Hz，黑 1500 Hz，白 2300 Hz，VIS 1100(0)/1300(1) Hz，
 * 引导音 1900 Hz。每行由「同步脉冲 + 消隐 + 若干颜色通道」组成。
 * Robot36 的两行共用一组色差信号（偶数行 R-Y、奇数行 B-Y）。
 */
enum class SstvMode(
    val label: String,
    val visCode: Int,
    val width: Int,
    val height: Int,
    val lineMs: Double,
    val syncMs: Double,
    val segments: List<SstvSegment>
) {
    ROBOT36(
        label = "Robot 36", visCode = 8, width = 320, height = 240,
        lineMs = 150.0, syncMs = 9.0,
        segments = listOf(
            SstvSegment(SstvChannel.SYNC, 0.0, 9.0, 1200.0),
            SstvSegment(SstvChannel.PORCH, 9.0, 3.0, 1500.0),
            SstvSegment(SstvChannel.Y, 12.0, 88.0),
            SstvSegment(SstvChannel.SEPARATOR, 100.0, 4.5, 1500.0),
            SstvSegment(SstvChannel.R_MINUS_Y, 104.5, 44.0),
            SstvSegment(SstvChannel.PORCH, 148.5, 1.5, 1500.0)
        )
    ),
    MARTIN_M1(
        label = "Martin M1", visCode = 44, width = 320, height = 240,
        lineMs = 446.446, syncMs = 4.862,
        segments = listOf(
            SstvSegment(SstvChannel.SYNC, 0.0, 4.862, 1200.0),
            SstvSegment(SstvChannel.PORCH, 4.862, 0.572, 1500.0),
            SstvSegment(SstvChannel.G, 5.434, 146.432),
            SstvSegment(SstvChannel.SEPARATOR, 151.866, 0.572, 1500.0),
            SstvSegment(SstvChannel.B, 152.438, 146.432),
            SstvSegment(SstvChannel.SEPARATOR, 298.870, 0.572, 1500.0),
            SstvSegment(SstvChannel.R, 299.442, 146.432),
            SstvSegment(SstvChannel.PORCH, 445.874, 0.572, 1500.0)
        )
    ),
    SCOTTIE_S1(
        label = "Scottie S1", visCode = 60, width = 320, height = 240,
        lineMs = 428.22, syncMs = 9.0,
        segments = listOf(
            SstvSegment(SstvChannel.SYNC, 0.0, 9.0, 1200.0),
            SstvSegment(SstvChannel.PORCH, 9.0, 1.5, 1500.0),
            SstvSegment(SstvChannel.R, 10.5, 138.24),
            SstvSegment(SstvChannel.SEPARATOR, 148.74, 1.5, 1500.0),
            SstvSegment(SstvChannel.G, 150.24, 138.24),
            SstvSegment(SstvChannel.SEPARATOR, 288.48, 1.5, 1500.0),
            SstvSegment(SstvChannel.B, 289.98, 138.24)
        )
    );

    /** 单张图的音频时长（毫秒），用于界面显示与采集长度建议。 */
    val imageMs: Double get() = VIS_HEADER_MS + height * lineMs

    companion object {
        const val VIS_HEADER_MS = 940.0
        val ALL: List<SstvMode> = entries
        fun fromVis(code: Int): SstvMode? = ALL.firstOrNull { it.visCode == code }
        fun fromLabel(label: String): SstvMode = ALL.firstOrNull { it.label == label } ?: ROBOT36
    }
}

/**
 * SSTV 编码 / 解码（纯 JVM、完全离线）。
 *
 * 编码：把任意尺寸图片缩放到 320×240，按 VIS 头 + 逐行逐通道调频生成 16bit PCM。
 * 解码：I/Q 正交鉴频得到瞬时频率 → 识别 VIS 头 → 找同步脉冲 → 按逐像素频率还原图像。
 *
 * **能力边界（界面必须如实标注）**：
 * - 只支持 Robot36 / Martin M1 / Scottie S1，其它 VIS 码会明确提示「不支持的模式」。
 * - 不做斜率/倾斜校正、色差插值与自动增益，要求信号较强、频偏小、无明显噪声；
 *   实际无线电接收效果通常不如 QSSTV / MMSSTV 等专业软件。
 * - 解码是本机的数字信号处理，不录文件、不联网、不上传图像。
 */
object SstvCodec {

    const val SAMPLE_RATE = 44100
    const val SYNC_HZ = 1200.0
    const val BLACK_HZ = 1500.0
    const val WHITE_HZ = 2300.0
    const val VIS_LEADER_HZ = 1900.0
    const val VIS_ZERO_HZ = 1100.0
    const val VIS_ONE_HZ = 1300.0

    private const val FULL_SCALE = 32767.0

    /** 把 ARGB 图片编码成 SSTV 基带音频。 */
    fun encode(
        pixels: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        mode: SstvMode,
        sampleRate: Int = SAMPLE_RATE,
        lines: Int = mode.height
    ): ShortArray {
        require(sourceWidth > 0 && sourceHeight > 0 && pixels.size >= sourceWidth * sourceHeight)
        val rgb = scale(pixels, sourceWidth, sourceHeight, mode.width, mode.height)

        val chunks = ArrayList<DoubleArray>(mode.height * mode.segments.size + 16)
        // VIS 头：300ms 引导 / 10ms 间隔 / 300ms 引导 / 30ms 起始位 / 8 数据位 / 1 偶校验位 / 30ms 停止位
        chunks.add(fixed(300.0, VIS_LEADER_HZ, sampleRate))
        chunks.add(fixed(10.0, SYNC_HZ, sampleRate))
        chunks.add(fixed(300.0, VIS_LEADER_HZ, sampleRate))
        chunks.add(fixed(30.0, SYNC_HZ, sampleRate))
        var ones = 0
        for (bit in 0 until 8) {
            val isOne = ((mode.visCode shr bit) and 1) == 1
            if (isOne) ones++
            chunks.add(fixed(30.0, if (isOne) VIS_ONE_HZ else VIS_ZERO_HZ, sampleRate))
        }
        chunks.add(fixed(30.0, if (ones % 2 == 1) VIS_ONE_HZ else VIS_ZERO_HZ, sampleRate))
        chunks.add(fixed(30.0, SYNC_HZ, sampleRate))

        for (line in 0 until lines) {
            for (segment in mode.segments) {
                if (segment.fixedHz != null) {
                    chunks.add(fixed(segment.durationMs, segment.fixedHz, sampleRate))
                    continue
                }
                val levels = IntArray(mode.width) { x ->
                    val index = line * mode.width + x
                    val color = rgb[index]
                    val r = (color shr 16) and 0xFF
                    val g = (color shr 8) and 0xFF
                    val b = color and 0xFF
                    when (segment.kind) {
                        SstvChannel.Y -> luma(r, g, b)
                        SstvChannel.R -> r
                        SstvChannel.G -> g
                        SstvChannel.B -> b
                        SstvChannel.R_MINUS_Y -> chroma(rgb, line, mode.width, true, x)
                        SstvChannel.B_MINUS_Y -> chroma(rgb, line, mode.width, false, x)
                        else -> 0
                    }
                }
                chunks.add(scanned(segment.durationMs, levels, sampleRate))
            }
        }
        return render(chunks, sampleRate)
    }

    /** 从 PCM 解码 SSTV，识别不了 VIS 或模式不支持时返回 null（界面如实提示，不猜测）。 */
    fun decode(pcm: ShortArray, sampleRate: Int): SstvImage? {
        if (pcm.size < sampleRate / 2) return null
        val freq = fmDemod(pcm, sampleRate)
        val headerEnd = findVisHeader(freq, sampleRate) ?: return null
        val code = headerEnd.code
        val mode = SstvMode.fromVis(code) ?: return null

        val syncSamples = (mode.syncMs * sampleRate / 1000.0).roundToInt()
        val lineStarts = findSyncPulses(freq, headerEnd.endIndex, syncSamples)
        if (lineStarts.isEmpty()) return null

        val lines = lineStarts.take(mode.height)
        val luma = Array(lines.size) { IntArray(mode.width) }
        val red = Array(lines.size) { IntArray(mode.width) { 128 } }
        val green = Array(lines.size) { IntArray(mode.width) { 128 } }
        val blue = Array(lines.size) { IntArray(mode.width) { 128 } }
        val chromaRy = Array(lines.size) { IntArray(mode.width) { 128 } }
        val chromaBy = Array(lines.size) { IntArray(mode.width) { 128 } }

        for ((lineIndex, start) in lines.withIndex()) {
            for (segment in mode.segments) {
                if (segment.fixedHz != null || segment.kind in listOf(
                        SstvChannel.SYNC, SstvChannel.PORCH, SstvChannel.SEPARATOR
                    )) continue
                val levels = readScan(freq, sampleRate, start, segment, mode.width)
                when (segment.kind) {
                    SstvChannel.Y -> luma[lineIndex] = levels
                    SstvChannel.R -> red[lineIndex] = levels
                    SstvChannel.G -> green[lineIndex] = levels
                    SstvChannel.B -> blue[lineIndex] = levels
                    // Robot36 偶数行发 R-Y、奇数行发 B-Y；编码端按行交替，解码端按行号还原
                    SstvChannel.R_MINUS_Y, SstvChannel.B_MINUS_Y ->
                        if (lineIndex % 2 == 0) chromaRy[lineIndex] = levels
                        else chromaBy[lineIndex] = levels
                    else -> Unit
                }
            }
        }

        val argb = IntArray(mode.width * mode.height)
        for (lineIndex in 0 until mode.height) {
            if (lineIndex >= lines.size) continue
            val yLine = luma[lineIndex]
            var rySource = lineIndex
            var bySource = lineIndex
            if (mode == SstvMode.ROBOT36) {
                rySource = if (lineIndex % 2 == 0) lineIndex else (lineIndex - 1).coerceAtLeast(0)
                bySource = if (lineIndex % 2 == 1) lineIndex else (lineIndex + 1).coerceAtMost(lines.size - 1)
            }
            for (x in 0 until mode.width) {
                val r: Int
                val g: Int
                val b: Int
                if (mode == SstvMode.ROBOT36) {
                    val y = yLine[x].toDouble()
                    val ry = chromaRy[rySource][x] - 128.0
                    val by = chromaBy[bySource][x] - 128.0
                    val rr = y + ry
                    val bb = y + by
                    val gg = (y - 0.299 * rr - 0.114 * bb) / 0.587
                    r = rr.roundToInt().coerceIn(0, 255)
                    g = gg.roundToInt().coerceIn(0, 255)
                    b = bb.roundToInt().coerceIn(0, 255)
                } else {
                    r = red[lineIndex][x]
                    g = green[lineIndex][x]
                    b = blue[lineIndex][x]
                }
                argb[lineIndex * mode.width + x] =
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val note = "VIS $code · ${mode.label} · 解出 ${lines.size}/${mode.height} 行" +
            (if (headerEnd.parityOk) "" else " · 校验位不一致")
        return SstvImage(mode, mode.width, mode.height, argb, lines.size, note)
    }

    // ---------------------------------------------------------------- 编码内部

    private data class VisHeader(val code: Int, val endIndex: Int, val parityOk: Boolean)

    private fun scale(
        pixels: IntArray, srcW: Int, srcH: Int, dstW: Int, dstH: Int
    ): IntArray {
        if (srcW == dstW && srcH == dstH) return pixels.copyOf(dstW * dstH)
        val out = IntArray(dstW * dstH)
        for (y in 0 until dstH) {
            val sy = (y * srcH / dstH).coerceAtMost(srcH - 1)
            for (x in 0 until dstW) {
                val sx = (x * srcW / dstW).coerceAtMost(srcW - 1)
                out[y * dstW + x] = pixels[sy * srcW + sx]
            }
        }
        return out
    }

    private fun luma(r: Int, g: Int, b: Int): Int =
        (0.299 * r + 0.587 * g + 0.114 * b).roundToInt().coerceIn(0, 255)

    /** Robot36 色差：两行取平均后算 R-Y 或 B-Y，再偏移到 0..255。 */
    private fun chroma(rgb: IntArray, line: Int, width: Int, redDifference: Boolean, x: Int): Int {
        val partner = if (line % 2 == 0) (line + 1).coerceAtMost(rgb.size / width - 1) else line - 1
        val a = rgb[line * width + x]
        val b = rgb[partner * width + x]
        val r = (((a shr 16) and 0xFF) + ((b shr 16) and 0xFF)) / 2.0
        val g = (((a shr 8) and 0xFF) + ((b shr 8) and 0xFF)) / 2.0
        val bl = ((a and 0xFF) + (b and 0xFF)) / 2.0
        val y = 0.299 * r + 0.587 * g + 0.114 * bl
        val value = if (redDifference) r - y else bl - y
        return (128 + value).roundToInt().coerceIn(0, 255)
    }

    /** 生成一段恒定频率的 PCM（DoubleArray 便于拼接时保持连续相位）。 */
    private fun fixed(durationMs: Double, hz: Double, sampleRate: Int): DoubleArray {
        val n = (durationMs * sampleRate / 1000.0).roundToInt().coerceAtLeast(1)
        return DoubleArray(n) { hz }
    }

    /** 生成一段逐像素调频的频率序列。 */
    private fun scanned(durationMs: Double, levels: IntArray, sampleRate: Int): DoubleArray {
        val n = (durationMs * sampleRate / 1000.0).roundToInt().coerceAtLeast(levels.size)
        return DoubleArray(n) { i ->
            val x = ((i.toLong() * levels.size) / n).toInt().coerceAtMost(levels.size - 1)
            BLACK_HZ + (WHITE_HZ - BLACK_HZ) * levels[x] / 255.0
        }
    }

    /** 把频率序列渲染成连续相位的 16bit PCM，首尾 5 ms 淡入淡出。 */
    private fun render(chunks: List<DoubleArray>, sampleRate: Int): ShortArray {
        val total = chunks.sumOf { it.size }
        val out = ShortArray(total)
        val ramp = (sampleRate * 0.005).toInt().coerceAtLeast(1)
        var phase = 0.0
        var index = 0
        for (chunk in chunks) {
            for (hz in chunk) {
                phase += 2.0 * PI * hz / sampleRate
                if (phase > 2.0 * PI) phase -= 2.0 * PI
                val edge = minOf(index, total - 1 - index).coerceAtLeast(0)
                val env = (edge.toDouble() / ramp).coerceAtMost(1.0)
                out[index++] = (0.55 * env * sin(phase) * FULL_SCALE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return out
    }

    // ---------------------------------------------------------------- 解码内部

    /**
     * I/Q 正交鉴频：以 1900 Hz 为中心把信号搬到基带，盒式低通滤掉 2 倍频分量，
     * 再由相邻相位差求瞬时频率。比过零检测更适合 SSTV 的短像素窗口。
     */
    private fun fmDemod(pcm: ShortArray, sampleRate: Int): FloatArray {
        val n = pcm.size
        val center = VIS_LEADER_HZ
        val smooth = (sampleRate / 3800.0).roundToInt().coerceIn(4, 64)
        val iBuf = FloatArray(n)
        val qBuf = FloatArray(n)
        var phase = 0.0
        val step = 2.0 * PI * center / sampleRate
        for (i in 0 until n) {
            val x = pcm[i] / 32768.0f
            iBuf[i] = x * cos(phase).toFloat()
            // Q 路取负：cos(A)·sin(B) 的低通分量是 -sin((f-1900)t)，
            // 不取负会把频率算成 3800 - f（1200Hz 被读成 2600Hz）。
            qBuf[i] = -x * sin(phase).toFloat()
            phase += step
        }
        // 两级盒式低通：单级抑制不了 2 倍频（约 3800 Hz）残留，
        // 瞬时频率会在 ±120 Hz 内抖动，导致 VIS/同步状态机被频繁打断。
        //
        // 只复用 I/Q/临时三个数组：Martin M1 整幅约 108 秒、麦克风 48000Hz
        // 约 520 万采样，若每级滤波都新建数组，峰值会超过 160 MB，低配机容易 OOM。
        val tmp = FloatArray(n)
        boxcarInto(iBuf, tmp, smooth)
        boxcarInto(tmp, iBuf, smooth)
        boxcarInto(qBuf, tmp, smooth)
        boxcarInto(tmp, qBuf, smooth)

        var previous = 0.0
        for (i in 0 until n) {
            val angle = atan2(qBuf[i].toDouble(), iBuf[i].toDouble())
            var delta = angle - previous
            while (delta > PI) delta -= 2.0 * PI
            while (delta < -PI) delta += 2.0 * PI
            previous = angle
            // 同一索引先读后写，后续迭代不会再用这一格的 I 值，可安全原地覆盖
            iBuf[i] = (center + delta * sampleRate / (2.0 * PI)).toFloat()
        }
        // 两级滤波带来约 smooth 个采样的群延迟，整体前移补偿，
        // 让 freq[i] 对应原始信号的第 i 个采样（像素采样才不会整体偏移一行像素）。
        val delay = smooth
        val last = iBuf[n - 1]
        for (i in 0 until n - delay) iBuf[i] = iBuf[i + delay]
        for (i in (n - delay).coerceAtLeast(0) until n) iBuf[i] = last
        return iBuf
    }

    /** 长度 w 的滑动平均（盒式低通），src 与 dst 必须是不同数组。 */
    private fun boxcarInto(src: FloatArray, dst: FloatArray, w: Int) {
        var sum = 0.0
        for (i in src.indices) {
            sum += src[i]
            if (i >= w) sum -= src[i - w]
            dst[i] = (sum / w).toFloat()
        }
    }

    private fun meanFreq(freq: FloatArray, from: Int, to: Int): Double {
        val a = from.coerceIn(0, freq.size - 1)
        val b = to.coerceIn(a + 1, freq.size)
        var sum = 0.0
        for (i in a until b) sum += freq[i]
        return sum / (b - a)
    }

    /** VIS 头状态机：1900(300ms) → 1200(10ms) → 1900(300ms) → 1200(30ms) → 8 数据位 → 校验 → 停止位。 */
    private fun findVisHeader(freq: FloatArray, sampleRate: Int): VisHeader? {
        fun ms(value: Double) = (value * sampleRate / 1000.0).roundToInt().coerceAtLeast(1)
        val leader = ms(300.0)
        val bit = ms(30.0)
        var i = 0
        var state = 0
        var run = 0
        while (i < freq.size) {
            val f = freq[i].toDouble()
            when (state) {
                0 -> if (f in 1800.0..2000.0) run++ else run = 0
                1 -> if (f < 1350.0) run++ else run = 0
                2 -> if (f in 1800.0..2000.0) run++ else run = 0
                3 -> if (f < 1350.0) run++ else run = 0
            }
            if ((state == 0 || state == 2) && run >= leader * 0.8) {
                state++
                run = 0
                i += ms(20.0)
                continue
            }
            if (state == 1 && run >= ms(7.0)) { state = 2; run = 0; continue }
            if (state == 3 && run >= ms(24.0)) break
            i++
        }
        if (state != 3) return null
        // 起始位（30ms 1200Hz）的起点 = 当前位置 - 已累计的连续长度；
        // 加一个 bit 即为第一个 VIS 数据位的起点。
        val pos = i - run + bit
        if (pos + bit * 10 >= freq.size) return null
        var code = 0
        var ones = 0
        for (b in 0 until 8) {
            val center = pos + b * bit + bit / 2
            val f = meanFreq(freq, center - bit / 4, center + bit / 4)
            if (f > 1200.0) { code = code or (1 shl b); ones++ }
        }
        val parityCenter = pos + 8 * bit + bit / 2
        val parityBit = meanFreq(freq, parityCenter - bit / 4, parityCenter + bit / 4) > 1200.0
        val parityOk = (ones % 2 == 1) == parityBit
        val end = pos + 10 * bit
        return VisHeader(code, end, parityOk)
    }

    /** 在 VIS 头之后找 1200Hz 同步脉冲，返回每行的起始采样点。 */
    private fun findSyncPulses(freq: FloatArray, from: Int, syncSamples: Int): List<Int> {
        val starts = mutableListOf<Int>()
        val threshold = 1360.0
        val minRun = (syncSamples * 0.45).toInt().coerceAtLeast(2)
        var i = from.coerceAtLeast(0)
        while (i < freq.size) {
            if (freq[i] < threshold) {
                var j = i
                while (j < freq.size && freq[j] < threshold) j++
                if (j - i >= minRun && (starts.isEmpty() || i - starts.last() > syncSamples / 2)) {
                    starts += i
                }
                i = j + 1
            } else i++
        }
        return starts
    }

    /** 读取一个扫描区段的逐像素电平。取像素窗口中间一半求均值，避开频率切换边缘。 */
    private fun readScan(
        freq: FloatArray,
        sampleRate: Int,
        lineStart: Int,
        segment: SstvSegment,
        width: Int
    ): IntArray {
        val start = lineStart + (segment.startMs * sampleRate / 1000.0).roundToInt()
        val total = (segment.durationMs * sampleRate / 1000.0).roundToInt().coerceAtLeast(width)
        val out = IntArray(width)
        for (x in 0 until width) {
            val a = start + (x.toLong() * total / width).toInt()
            val b = start + ((x + 1).toLong() * total / width).toInt()
            val pad = ((b - a) / 4).coerceAtLeast(0)
            val f = meanFreq(freq, a + pad, (b - pad).coerceAtLeast(a + pad + 1))
            out[x] = ((f - BLACK_HZ) / (WHITE_HZ - BLACK_HZ) * 255.0)
                .roundToInt().coerceIn(0, 255)
        }
        return out
    }
}

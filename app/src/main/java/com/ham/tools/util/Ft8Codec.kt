package com.ham.tools.util

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import java.util.concurrent.ConcurrentHashMap

/** 一次成功解码的结果。frequencyHz 是 8 个音调中第 0 个音调的估计频率。 */
data class Ft8Decode(
    val text: String,
    val frequencyHz: Double,
    val offsetMs: Int,
    val score: Double
)

/**
 * FT8 编解码（离线、纯 JVM、不含任何网络请求）。
 *
 * 实现范围（依据 K1JT 等《The FT4 and FT8 Communication Protocols》与公有领域矩阵数据）：
 * - 发送侧：77bit 消息打包 → CRC14 → LDPC(174,91) 系统编码 → Gray 映射 →
 *   S7/D29/S7/D29/S7 共 79 个符号 → 连续相位 8-FSK 音频（6.25 Hz 音调间隔、0.16 s/符号）。
 * - 接收侧：重采样到 12 kHz → STFT 功率谱 → Costas 同步搜索 → 软判决 LLR →
 *   LDPC 归一化最小和置信传播 → 稀疏校验 → CRC14 → 解包成文本。
 *
 * **能力边界（界面必须如实标注）**：
 * 1. 生成的是音频基带信号，不是射频发射；真正通联需要电台、合法执照与合规频率。
 * 2. 调制采用简化连续相位 FSK，未实现 WSJT-X 的精确高斯频率成形；
 *    解码不含 OSD 深搜、多信号逐次消除与分集，弱信号灵敏度明显低于 WSJT-X / FT8CN。
 * 3. 消息类型只覆盖标准 QSO 消息、常用 token、自由文本与遥测十六进制；
 *    竞赛/特殊呼号等扩展类型解包时会明确显示「不支持」，不猜测内容。
 */
object Ft8Codec {

    const val SAMPLE_RATE = 44100
    const val BASE_HZ = 1500.0
    const val CAPTURE_SECONDS = 15
    const val SIGNAL_MS = (79 * 0.16 * 1000).toInt()   // 12.64 s
    const val ANALYSIS_RATE = 12000

    // 22bit / 12bit 呼号哈希只保存在内存里：本次收到的标准消息里出现过完整呼号后，
    // 后续只带哈希的消息才能还原成文字。不写磁盘、不上传。
    private val callsByHash22 = ConcurrentHashMap<Int, String>()
    private val callsByHash12 = ConcurrentHashMap<Int, String>()

    // ---------------------------------------------------------------- 发送侧

    /** 把一条消息文本编码成 12.64 秒的 16bit PCM。不支持的消息返回 null。 */
    fun encode(
        text: String,
        baseHz: Double = BASE_HZ,
        sampleRate: Int = SAMPLE_RATE
    ): ShortArray? {
        val tones = encodeTones(text) ?: return null
        return modulate(tones, baseHz, sampleRate)
    }

    /** 文本 -> 79 个音调（0..7），便于单测直接检查帧结构。 */
    fun encodeTones(text: String): IntArray? {
        val payload = pack77(text) ?: return null
        val a91 = addCrc(payload)
        val codeword = ldpcEncode(a91)
        return frameSymbols(codeword)
    }

    /** 77bit 消息打包：先试标准 QSO 消息，失败再试自由文本。 */
    fun pack77(text: String): BooleanArray? {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return null
        return packStandard(normalized) ?: packFreeText(normalized)
    }

    /** 77bit -> 可读文本；无法识别的类型如实返回占位说明。 */
    fun unpack77(payload: BooleanArray): String {
        require(payload.size >= 77) { "payload 必须是 77 位" }
        val i3 = getBits(payload, 74, 3).toInt()
        val n3 = getBits(payload, 71, 3).toInt()
        return when (i3) {
            1, 2 -> unpackStandard(payload, i3)
            0 -> when (n3) {
                0 -> unpackFreeText(payload)
                5 -> "遥测 " + unpackTelemetryHex(payload)
                else -> "〈扩展自由文本 n3=$n3〉"
            }
            4 -> unpackNonStandard(payload)
            else -> "〈未支持的消息类型 i3=$i3〉"
        }
    }

    /** 77bit -> 91bit（追加 14bit CRC）。 */
    fun addCrc(payload: BooleanArray): BooleanArray {
        require(payload.size >= 77) { "payload 必须是 77 位" }
        val bytes = bitsToBytes(payload, 12)
        bytes[9] = (bytes[9].toInt() and 0xF8).toByte()
        bytes[10] = 0
        bytes[11] = 0
        val checksum = computeCrc14(bytes, 82)
        bytes[9] = (bytes[9].toInt() or (checksum shr 11)).toByte()
        bytes[10] = ((checksum shr 3) and 0xFF).toByte()
        bytes[11] = ((checksum shl 5) and 0xFF).toByte()
        return bytesToBits(bytes, Ft8Constants.LDPC_K)
    }

    /** 校验 91bit 消息里的 CRC14。 */
    fun checkCrc(a91: BooleanArray): Boolean {
        if (a91.size < 91) return false
        val bytes = bitsToBytes(a91, 12)
        val extracted = ((bytes[9].toInt() and 0x07) shl 11) or
            ((bytes[10].toInt() and 0xFF) shl 3) or
            ((bytes[11].toInt() and 0xFF) shr 5)
        bytes[9] = (bytes[9].toInt() and 0xF8).toByte()
        bytes[10] = 0
        bytes[11] = 0
        return computeCrc14(bytes, 82) == extracted
    }

    /** LDPC(174,91) 系统编码：前 91 位原样，后 83 位由生成矩阵逐行点乘得到。 */
    fun ldpcEncode(a91: BooleanArray): BooleanArray {
        require(a91.size >= Ft8Constants.LDPC_K) { "消息必须是 91 位" }
        val out = BooleanArray(Ft8Constants.LDPC_N)
        for (i in 0 until Ft8Constants.LDPC_K) out[i] = a91[i]
        val generator = Ft8Constants.GENERATOR
        for (i in 0 until Ft8Constants.LDPC_M) {
            var parity = 0
            val row = generator[i]
            for (b in 0 until Ft8Constants.LDPC_K) {
                if (a91[b] && (row[b / 8].toInt() and (0x80 shr (b % 8))) != 0) parity = parity xor 1
            }
            out[Ft8Constants.LDPC_K + i] = parity == 1
        }
        return out
    }

    /** 返回未满足的稀疏校验方程个数，0 表示是合法码字。 */
    fun ldpcCheck(codeword: BooleanArray): Int {
        if (codeword.size < Ft8Constants.LDPC_N) return Int.MAX_VALUE
        var bad = 0
        for (row in Ft8Constants.CHECKS) {
            var parity = 0
            for (bit in row) if (codeword[bit]) parity = parity xor 1
            if (parity != 0) bad++
        }
        return bad
    }

    /** 174bit 码字 -> 79 个音调：S7 D29 S7 D29 S7。 */
    fun frameSymbols(codeword: BooleanArray): IntArray {
        require(codeword.size >= Ft8Constants.LDPC_N) { "码字必须是 174 位" }
        val tones = IntArray(Ft8Constants.FRAME_SYMBOLS)
        var dataIndex = 0
        for (i in 0 until Ft8Constants.FRAME_SYMBOLS) {
            val costasBlock = when (i) {
                in 0..6 -> i
                in 36..42 -> i - 36
                in 72..78 -> i - 72
                else -> -1
            }
            if (costasBlock >= 0) {
                tones[i] = Ft8Constants.COSTAS[costasBlock]
            } else {
                val value = (if (codeword[dataIndex * 3]) 4 else 0) +
                    (if (codeword[dataIndex * 3 + 1]) 2 else 0) +
                    (if (codeword[dataIndex * 3 + 2]) 1 else 0)
                tones[i] = Ft8Constants.GRAY[value]
                dataIndex++
            }
        }
        return tones
    }

    /** 连续相位 8-FSK 调制。音调切换处做升余弦平滑，首尾 5 ms 淡入淡出防爆音。 */
    fun modulate(
        tones: IntArray,
        baseHz: Double = BASE_HZ,
        sampleRate: Int = SAMPLE_RATE
    ): ShortArray {
        val sps = (sampleRate * Ft8Constants.SYMBOL_SECONDS).roundToInt().coerceAtLeast(1)
        val total = tones.size * sps
        val out = ShortArray(total)
        val ramp = (sampleRate * 0.005).toInt().coerceAtLeast(1)
        var phase = 0.0
        var previousTone = tones.firstOrNull()?.toDouble() ?: 0.0
        var index = 0
        for (tone in tones) {
            val target = tone.toDouble()
            for (i in 0 until sps) {
                val frac = i.toDouble() / sps
                val blend = if (frac < 0.12) {
                    val x = frac / 0.12
                    x * x * (3.0 - 2.0 * x)
                } else 1.0
                val current = previousTone + (target - previousTone) * blend
                val freq = baseHz + current * Ft8Constants.TONE_SPACING_HZ
                phase += 2.0 * PI * freq / sampleRate
                if (phase > 2.0 * PI) phase -= 2.0 * PI
                val edge = minOf(index, total - 1 - index).coerceAtLeast(0)
                val env = (edge.toDouble() / ramp).coerceAtMost(1.0)
                val value = (0.55 * env * sin(phase) * 32767.0).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                out[index++] = value.toShort()
            }
            previousTone = target
        }
        return out
    }

    // ---------------------------------------------------------------- 接收侧

    /**
     * 从一段 PCM 中解码 FT8。输入最好是 15 秒窗口；采样率任意，内部会重采样到 12 kHz。
     * 返回按同步得分排序、通过 LDPC 校验与 CRC 的结果。
     */
    fun decode(pcm: ShortArray, sampleRate: Int, maxResults: Int = 12): List<Ft8Decode> {
        val signal = Dsp.resample(Dsp.toFloat(pcm), sampleRate, ANALYSIS_RATE)
        val fftSize = 4096
        val windowSize = 2048
        val hop = 960                      // 半个符号
        val spec = Dsp.spectrogram(signal, ANALYSIS_RATE, fftSize, hop, windowSize)
        if (spec.isEmpty()) return emptyList()
        val binHz = ANALYSIS_RATE.toDouble() / fftSize
        val results = mutableListOf<Ft8Decode>()

        data class Candidate(val bin: Int, val startFrame: Int, val score: Double)
        val candidates = ArrayList<Candidate>(4096)

        val minBin = (180.0 / binHz).toInt().coerceAtLeast(0)
        val maxBin = ((3200.0 - 7 * Ft8Constants.TONE_SPACING_HZ) / binHz).toInt()
            .coerceAtMost(fftSize / 2 - 16)
        val maxStartFrame = (2.4 * ANALYSIS_RATE / hop).toInt().coerceAtMost(spec.size / 4)

        for (bin in minBin..maxBin) {
            val base = bin * binHz
            var startFrame = 0
            while (startFrame <= maxStartFrame) {
                var score = 0.0
                var valid = true
                for (block in intArrayOf(0, 36, 72)) {
                    for (k in 0 until 7) {
                        val frame = startFrame + (block + k) * 2
                        if (frame >= spec.size) { valid = false; break }
                        val expectedTone = Ft8Constants.COSTAS[k]
                        val expected = power(spec, frame, fftSize, ANALYSIS_RATE, base + expectedTone * 6.25)
                        var others = 0.0
                        for (t in 0..7) {
                            if (t != expectedTone) {
                                others += power(spec, frame, fftSize, ANALYSIS_RATE, base + t * 6.25).toDouble()
                            }
                        }
                        val mean = others / 7.0
                        score += (expected - mean) / (expected + mean + 1e-9)
                    }
                    if (!valid) break
                }
                if (valid && score > 2.0) candidates += Candidate(bin, startFrame, score)
                startFrame += 2                 // 每次挪一个符号
            }
        }
        candidates.sortByDescending { it.score }

        val triedPositions = mutableListOf<Pair<Int, Int>>()
        for (cand in candidates) {
            if (results.size >= maxResults) break
            // 跳过几乎相同的时频位置，避免同一条信号被反复解码
            if (triedPositions.any {
                    abs(it.first - cand.bin) <= 1 && abs(it.second - cand.startFrame) <= 2
                }) continue
            triedPositions += cand.bin to cand.startFrame

            val base = cand.bin * binHz
            val llr = DoubleArray(Ft8Constants.LDPC_N)
            for (dataIndex in 0 until Ft8Constants.DATA_SYMBOLS) {
                val symbol = if (dataIndex < 29) 7 + dataIndex else 43 + (dataIndex - 29)
                val frame = cand.startFrame + symbol * 2
                if (frame >= spec.size) break
                val energies = DoubleArray(8) { t ->
                    power(spec, frame, fftSize, ANALYSIS_RATE, base + t * 6.25).toDouble()
                }
                for (bit in 0 until 3) {
                    var sum0 = 0.0
                    var sum1 = 0.0
                    for (tone in 0..7) {
                        val value = Ft8Constants.GRAY_INV[tone]
                        if ((value shr (2 - bit)) and 1 == 1) sum1 += energies[tone] else sum0 += energies[tone]
                    }
                    // 约定：正数表示这一位更可能是 0
                    llr[dataIndex * 3 + bit] = (ln(sum0 + 1e-9) - ln(sum1 + 1e-9))
                        .coerceIn(-9.0, 9.0)
                }
            }
            val codeword = ldpcDecode(llr) ?: continue
            if (ldpcCheck(codeword) != 0) continue
            val a91 = codeword.copyOfRange(0, Ft8Constants.LDPC_K)
            if (!checkCrc(a91)) continue
            val text = unpack77(a91.copyOfRange(0, Ft8Constants.PAYLOAD_BITS))
            val offsetMs = cand.startFrame * hop * 1000 / ANALYSIS_RATE
            if (results.none { it.text == text && abs(it.frequencyHz - base) < 20.0 }) {
                results += Ft8Decode(text, base, offsetMs, cand.score)
            }
        }
        return results
    }

    private fun power(
        spec: Array<FloatArray>, frame: Int, fftSize: Int, rate: Int, hz: Double
    ): Float = Dsp.specPower(spec, frame, fftSize, rate, hz)

    /**
     * 归一化最小和置信传播（min-sum BP）LDPC 译码。
     * llr 的约定：正值表示 bit=0 更可能。全部 83 个校验方程满足时返回硬判决码字。
     */
    fun ldpcDecode(channel: DoubleArray, maxIterations: Int = 30): BooleanArray? {
        require(channel.size >= Ft8Constants.LDPC_N) { "需要 174 个软比特" }
        val checks = Ft8Constants.CHECKS
        val bitChecks = Ft8Constants.BIT_CHECKS
        // 每个码字位在其参与的 3 个校验方程中的位置
        val positions = Array(Ft8Constants.LDPC_N) { bit ->
            IntArray(3) { k -> checks[bitChecks[bit][k]].indexOf(bit) }
        }
        val q = Array(Ft8Constants.LDPC_M) { DoubleArray(checks[it].size) }
        val r = Array(Ft8Constants.LDPC_M) { DoubleArray(checks[it].size) }
        val hard = BooleanArray(Ft8Constants.LDPC_N)

        repeat(maxIterations) {
            // 变量节点 -> 校验节点
            for (c in 0 until Ft8Constants.LDPC_M) {
                val row = checks[c]
                for (k in row.indices) {
                    val bit = row[k]
                    var sum = channel[bit]
                    for (p in 0 until 3) {
                        val cc = bitChecks[bit][p]
                        if (cc != c) sum += r[cc][positions[bit][p]]
                    }
                    q[c][k] = sum.coerceIn(-40.0, 40.0)
                }
            }
            // 校验节点 -> 变量节点（归一化最小和）
            for (c in 0 until Ft8Constants.LDPC_M) {
                val row = checks[c]
                var signProduct = 1.0
                var min1 = Double.MAX_VALUE
                var min2 = Double.MAX_VALUE
                var argMin = 0
                for (k in row.indices) {
                    val v = q[c][k]
                    val a = abs(v)
                    if (v < 0) signProduct = -signProduct
                    if (a < min1) {
                        min2 = min1; min1 = a; argMin = k
                    } else if (a < min2) min2 = a
                }
                val second = if (min2 == Double.MAX_VALUE) min1 else min2
                for (k in row.indices) {
                    val v = q[c][k]
                    val sign = signProduct * if (v < 0) -1.0 else 1.0
                    val magnitude = if (k == argMin) second else min1
                    r[c][k] = (sign * magnitude * 0.75).coerceIn(-30.0, 30.0)
                }
            }
            // 后验概率与硬判决
            for (bit in 0 until Ft8Constants.LDPC_N) {
                var sum = channel[bit]
                for (p in 0 until 3) sum += r[bitChecks[bit][p]][positions[bit][p]]
                hard[bit] = sum < 0.0        // LLR 为负 -> bit = 1
            }
            if (ldpcCheck(hard) == 0) return hard
        }
        return null
    }

    // ---------------------------------------------------------------- 77bit 打包

    private enum class Table {
        ALPHANUM_SPACE, ALPHANUM, NUMERIC, LETTERS_SPACE, FULL, ALPHANUM_SPACE_SLASH
    }

    private fun nchar(c: Char, table: Table): Int {
        var n = 0
        if (table != Table.ALPHANUM && table != Table.NUMERIC) {
            if (c == ' ') return n
            n += 1
        }
        if (table != Table.LETTERS_SPACE) {
            if (c in '0'..'9') return n + (c - '0')
            n += 10
        }
        if (table != Table.NUMERIC) {
            if (c in 'A'..'Z') return n + (c - 'A')
            n += 26
        }
        if (table == Table.FULL) {
            when (c) {
                '+' -> return n
                '-' -> return n + 1
                '.' -> return n + 2
                '/' -> return n + 3
                '?' -> return n + 4
            }
        } else if (table == Table.ALPHANUM_SPACE_SLASH) {
            if (c == '/') return n
        }
        return -1
    }

    private fun charn(value: Int, table: Table): Char {
        var c = value
        if (table != Table.ALPHANUM && table != Table.NUMERIC) {
            if (c == 0) return ' '
            c -= 1
        }
        if (table != Table.LETTERS_SPACE) {
            if (c < 10) return '0' + c
            c -= 10
        }
        if (table != Table.NUMERIC) {
            if (c < 26) return 'A' + c
            c -= 26
        }
        if (table == Table.FULL) {
            if (c in 0..4) return "+-./?"[c]
        } else if (table == Table.ALPHANUM_SPACE_SLASH) {
            if (c == 0) return '/'
        }
        return '_'
    }

    private fun normalize(text: String): String = buildString {
        var lastSpace = false
        for (ch in text.uppercase()) {
            if (ch == ' ' || ch == '\t') {
                if (!lastSpace && isNotEmpty()) append(' ')
                lastSpace = true
            } else {
                append(ch)
                lastSpace = false
            }
        }
    }.trim()

    /** 22bit 呼号哈希；同时登记 12bit 短哈希，便于解出非标准消息。 */
    private fun callsignHash(callsign: String): Int {
        var n58 = 0uL
        for (i in 0 until 11) {
            val c = callsign.getOrNull(i) ?: ' '
            val j = nchar(c, Table.ALPHANUM_SPACE_SLASH)
            if (j < 0) return -1
            n58 = n58 * 38uL + j.toULong()
        }
        return (((47055833459uL * n58) shr 42) and 0x3FFFFFuL).toInt()
    }

    private fun rememberCallsign(callsign: String) {
        val hash = callsignHash(callsign)
        if (hash < 0) return
        callsByHash22[hash] = callsign
        callsByHash12[hash shr 10] = callsign
    }

    /** 标准 6 字符呼号 -> 28bit 整数，失败返回 -1。 */
    private fun packBasecall(call: String): Long {
        if (call.length <= 2) return -1
        val c6 = CharArray(6) { ' ' }
        when {
            call[2].isDigit() && call.length <= 6 -> call.forEachIndexed { i, c -> c6[i] = c }
            call[1].isDigit() && call.length <= 5 -> call.forEachIndexed { i, c -> c6[i + 1] = c }
            else -> return -1
        }
        val i0 = nchar(c6[0], Table.ALPHANUM_SPACE)
        val i1 = nchar(c6[1], Table.ALPHANUM)
        val i2 = nchar(c6[2], Table.NUMERIC)
        val i3 = nchar(c6[3], Table.LETTERS_SPACE)
        val i4 = nchar(c6[4], Table.LETTERS_SPACE)
        val i5 = nchar(c6[5], Table.LETTERS_SPACE)
        if (minOf(i0, i1, i2, i3, i4, i5) < 0) return -1
        var n = i0.toLong()
        n = n * 36 + i1
        n = n * 10 + i2
        n = n * 27 + i3
        n = n * 27 + i4
        n = n * 27 + i5
        return n
    }

    /** 呼号/token -> (28bit 值, /P 或 /R 后缀标志)。 */
    private fun pack28(value: String): Pair<Long, Int>? {
        when (value) {
            "DE" -> return 0L to 0
            "QRZ" -> return 1L to 0
            "CQ" -> return 2L to 0
        }
        if (value.startsWith("CQ ") && value.length < 8) {
            val modifier = parseCqModifier(value)
            if (modifier >= 0) return (3L + modifier) to 0
        }
        var ip = 0
        val base = if (value.endsWith("/P") || value.endsWith("/R")) {
            ip = 1
            value.dropLast(2)
        } else value

        val encoded = packBasecall(base)
        if (encoded >= 0) {
            rememberCallsign(base)
            return (Ft8Constants.NTOKENS + Ft8Constants.MAX22 + encoded) to ip
        }
        if (base.length in 3..11) {
            val hash = callsignHash(base)
            if (hash < 0) return null
            rememberCallsign(base)
            return (Ft8Constants.NTOKENS + hash) to 0
        }
        return null
    }

    private fun parseCqModifier(value: String): Int {
        val body = value.substring(3)
        if (body.isEmpty()) return -1
        if (body.all { it.isDigit() } && body.length <= 3) return body.toIntOrNull() ?: -1
        if (body.length <= 4 && body.all { it in 'A'..'Z' }) {
            var m = 0
            for (c in body) m = 27 * m + (c - 'A' + 1)
            return 1000 + m
        }
        return -1
    }

    /** 网格 / 信号报告 / RRR 等 -> 16bit（最高位为 R 标志）。 */
    private fun packGrid(extra: String): Int {
        if (extra.isEmpty()) return Ft8Constants.MAXGRID4 + 1
        when (extra) {
            "RRR" -> return Ft8Constants.MAXGRID4 + 2
            "RR73" -> return Ft8Constants.MAXGRID4 + 3
            "73" -> return Ft8Constants.MAXGRID4 + 4
        }
        if (extra.length == 4 && extra[0] in 'A'..'R' && extra[1] in 'A'..'R' &&
            extra[2].isDigit() && extra[3].isDigit()
        ) {
            var n = extra[0] - 'A'
            n = n * 18 + (extra[1] - 'A')
            n = n * 10 + (extra[2] - '0')
            n = n * 10 + (extra[3] - '0')
            return n
        }
        var ir = 0
        var report = extra
        if (report.startsWith("R") && report.length > 1) {
            ir = 1
            report = report.drop(1)
        }
        val dd = report.toIntOrNull() ?: return Ft8Constants.MAXGRID4 + 1
        val value = Ft8Constants.MAXGRID4 + 35 + dd
        return value or (ir shl 15)
    }

    private fun packStandard(normalized: String): BooleanArray? {
        val tokens = normalized.split(' ').filter { it.isNotEmpty() }
        if (tokens.size !in 2..4) return null
        val cq = tokens[0] == "CQ"
        val callTo: String
        val callDe: String
        val extra: String
        if (cq) {
            val hasModifier = tokens.size >= 3 && tokens[1].let {
                (it.all { ch -> ch.isDigit() } && it.length <= 3) ||
                    (it.length <= 4 && it.all { ch -> ch in 'A'..'Z' })
            }
            if (hasModifier) {
                callTo = "CQ " + tokens[1]
                callDe = tokens[2]
                extra = tokens.getOrElse(3) { "" }
            } else {
                callTo = "CQ"
                callDe = tokens.getOrElse(1) { "" }
                extra = tokens.getOrElse(2) { "" }
            }
        } else {
            callTo = tokens[0]
            callDe = tokens[1]
            extra = tokens.getOrElse(2) { "" }
        }
        if (callDe.isEmpty()) return null
        val (n28a, ipa) = pack28(callTo) ?: return null
        val (n28b, ipb) = pack28(callDe) ?: return null
        val grid = packGrid(extra)
        val i3 = if (callTo.endsWith("/P") || callDe.endsWith("/P")) 2 else 1
        val bits = BooleanArray(Ft8Constants.PAYLOAD_BITS)
        setBits(bits, 0, 29, (n28a shl 1) or ipa.toLong())
        setBits(bits, 29, 29, (n28b shl 1) or ipb.toLong())
        setBits(bits, 58, 1, ((grid shr 15) and 1).toLong())
        setBits(bits, 59, 15, (grid and 0x7FFF).toLong())
        setBits(bits, 74, 3, i3.toLong())
        return bits
    }

    /** 自由文本：13 字符 base-42 整数（71bit），末尾 i3=0 / n3=0。 */
    private fun packFreeText(normalized: String): BooleanArray? {
        if (normalized.length > 13) return null
        val b71 = ByteArray(9)
        for (idx in 0 until 13) {
            val c = normalized.getOrElse(idx) { ' ' }
            val id = nchar(c, Table.FULL)
            if (id < 0) return null
            var rem = id
            for (i in 8 downTo 0) {
                val cur = (b71[i].toInt() and 0xFF) * 42 + rem
                b71[i] = (cur and 0xFF).toByte()
                rem = cur shr 8
            }
        }
        val bytes = ByteArray(10)
        var carry = 0
        for (i in 8 downTo 0) {
            val v = b71[i].toInt() and 0xFF
            bytes[i] = ((v shl 1) or (carry shr 7)).toByte()
            carry = v and 0x80
        }
        return bytesToBits(bytes, Ft8Constants.PAYLOAD_BITS)
    }

    // ---------------------------------------------------------------- 77bit 解包

    private fun unpackStandard(payload: BooleanArray, i3: Int): String {
        val n29a = getBits(payload, 0, 29)
        val n29b = getBits(payload, 29, 29)
        val ir = getBits(payload, 58, 1).toInt()
        val grid = getBits(payload, 59, 15).toInt()
        val parts = listOf(
            unpack28(n29a shr 1, (n29a and 1L).toInt(), i3),
            unpack28(n29b shr 1, (n29b and 1L).toInt(), i3),
            unpackGrid(grid, ir)
        ).filter { it.isNotEmpty() }
        return parts.joinToString(" ")
    }

    private fun unpack28(n28: Long, ip: Int, i3: Int): String {
        if (n28 < Ft8Constants.NTOKENS) {
            return when {
                n28 == 0L -> "DE"
                n28 == 1L -> "QRZ"
                n28 == 2L -> "CQ"
                n28 <= 1002L -> "CQ " + (n28 - 3).toString().padStart(3, '0')
                n28 <= 532443L -> {
                    var n = n28 - 1003
                    val chars = CharArray(4)
                    for (i in 3 downTo 0) {
                        chars[i] = charn((n % 27).toInt(), Table.LETTERS_SPACE)
                        n /= 27
                    }
                    "CQ " + String(chars).trimStart()
                }
                else -> "〈token $n28〉"
            }
        }
        val rest = n28 - Ft8Constants.NTOKENS
        if (rest < Ft8Constants.MAX22) {
            return callsByHash22[rest.toInt()]?.let { "<$it>" } ?: "<...>"
        }
        var n = rest - Ft8Constants.MAX22
        val chars = CharArray(6)
        chars[5] = charn((n % 27).toInt(), Table.LETTERS_SPACE); n /= 27
        chars[4] = charn((n % 27).toInt(), Table.LETTERS_SPACE); n /= 27
        chars[3] = charn((n % 27).toInt(), Table.LETTERS_SPACE); n /= 27
        chars[2] = charn((n % 10).toInt(), Table.NUMERIC); n /= 10
        chars[1] = charn((n % 36).toInt(), Table.ALPHANUM); n /= 36
        chars[0] = charn((n % 37).toInt(), Table.ALPHANUM_SPACE)
        var call = String(chars).trim()
        if (call.startsWith("3D0") && call.length > 3) call = "3DA0" + call.substring(3)
        else if (call.length > 1 && call[0] == 'Q' && call[1] in 'A'..'Z') call = "3X" + call.substring(1)
        if (ip != 0) call += if (i3 == 2) "/P" else "/R"
        rememberCallsign(call)
        return call
    }

    private fun unpackGrid(grid: Int, ir: Int): String {
        if (grid <= Ft8Constants.MAXGRID4) {
            var n = grid
            val chars = CharArray(4)
            chars[3] = '0' + (n % 10); n /= 10
            chars[2] = '0' + (n % 10); n /= 10
            chars[1] = 'A' + (n % 18); n /= 18
            chars[0] = 'A' + (n % 18)
            return (if (ir != 0) "R " else "") + String(chars)
        }
        return when (val irpt = grid - Ft8Constants.MAXGRID4) {
            1 -> ""
            2 -> "RRR"
            3 -> "RR73"
            4 -> "73"
            else -> {
                val dd = irpt - 35
                val sign = if (dd >= 0) "+" else "-"
                (if (ir != 0) "R" else "") + sign + abs(dd).toString().padStart(2, '0')
            }
        }
    }

    private fun unpackFreeText(payload: BooleanArray): String {
        val bytes = bitsToBytes(payload, 10)
        val b71 = ByteArray(9)
        var carry = 0
        for (i in 0..8) {
            val v = bytes[i].toInt() and 0xFF
            b71[i] = ((carry shl 7) or (v shr 1)).toByte()
            carry = v and 1
        }
        val chars = CharArray(13)
        for (idx in 12 downTo 0) {
            var rem = 0
            for (i in 0..8) {
                val cur = (rem shl 8) or (b71[i].toInt() and 0xFF)
                b71[i] = (cur / 42).toByte()
                rem = cur % 42
            }
            chars[idx] = charn(rem, Table.FULL)
        }
        return String(chars).trim()
    }

    private fun unpackTelemetryHex(payload: BooleanArray): String {
        val bytes = bitsToBytes(payload, 10)
        val b71 = ByteArray(9)
        var carry = 0
        for (i in 0..8) {
            val v = bytes[i].toInt() and 0xFF
            b71[i] = ((carry shl 7) or (v shr 1)).toByte()
            carry = v and 1
        }
        return buildString {
            for (b in b71) {
                val v = b.toInt() and 0xFF
                append("%02X".format(v))
            }
        }.take(18)
    }

    private fun unpackNonStandard(payload: BooleanArray): String {
        val hash12 = getBits(payload, 0, 12).toInt()
        val n58 = getBits(payload, 12, 58)
        val iflip = getBits(payload, 70, 1).toInt()
        val nrpt = getBits(payload, 71, 2).toInt()
        val icq = getBits(payload, 73, 1).toInt()
        var n = n58
        val chars = CharArray(11)
        for (i in 10 downTo 0) {
            chars[i] = charn((n % 38).toInt(), Table.ALPHANUM_SPACE_SLASH)
            n /= 38
        }
        val plain = String(chars).trim()
        rememberCallsign(plain)
        val hashed = callsByHash12[hash12]?.let { "<$it>" } ?: "<...>"
        val first: String
        val second: String
        if (iflip != 0) { first = plain; second = hashed } else { first = hashed; second = plain }
        val extra = when (nrpt) { 1 -> "RRR"; 2 -> "RR73"; 3 -> "73"; else -> "" }
        return if (icq != 0) "CQ $second" else listOf(first, second, extra)
            .filter { it.isNotEmpty() }.joinToString(" ")
    }

    // ---------------------------------------------------------------- 位与 CRC 工具

    private fun setBits(bits: BooleanArray, start: Int, count: Int, value: Long) {
        for (i in 0 until count) bits[start + i] = ((value shr (count - 1 - i)) and 1L) == 1L
    }

    private fun getBits(bits: BooleanArray, start: Int, count: Int): Long {
        var v = 0L
        for (i in 0 until count) v = (v shl 1) or if (bits[start + i]) 1L else 0L
        return v
    }

    private fun bitsToBytes(bits: BooleanArray, byteCount: Int): ByteArray {
        val out = ByteArray(byteCount)
        for (i in bits.indices) {
            val b = i / 8
            if (b >= byteCount) break
            if (bits[i]) out[b] = (out[b].toInt() or (0x80 shr (i % 8))).toByte()
        }
        return out
    }

    private fun bytesToBits(bytes: ByteArray, count: Int): BooleanArray {
        val out = BooleanArray(count)
        for (i in 0 until count) {
            out[i] = (bytes[i / 8].toInt() and (0x80 shr (i % 8))) != 0
        }
        return out
    }

    /** CRC-14，多项式 0x6757（x^14 项隐含），输入取 82 位。与 WSJT-X / ft8_lib 一致。 */
    private fun computeCrc14(message: ByteArray, numBits: Int): Int {
        var remainder = 0
        var byteIndex = 0
        for (bit in 0 until numBits) {
            if (bit % 8 == 0) {
                remainder = remainder xor ((message[byteIndex].toInt() and 0xFF) shl 6)
                byteIndex++
            }
            remainder = if (remainder and 0x2000 != 0) {
                ((remainder shl 1) xor 0x6757) and 0x7FFF
            } else {
                (remainder shl 1) and 0x7FFF
            }
        }
        return remainder and 0x3FFF
    }
}

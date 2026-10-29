package com.ham.tools.data.reference

import kotlin.math.roundToInt

/**
 * 摩尔斯电码对照表与编解码。
 *
 * 电码表取国际摩尔斯码（ITU-R M.1677-1），覆盖字母、数字、常用标点与简语。
 * 简语（prosign）没有独立的字母，是两个字母连发不留间隔，这里用 `…` 单独一张表。
 *
 * **时序**采用 PARIS 标准：以「点」为单位长度 T，
 * - 点 = 1T，划 = 3T
 * - 点划之间 = 1T
 * - 字符之间 = 3T
 * - 单词之间 = 7T
 *
 * 速度换算：T(ms) = 1200 / WPM。20 WPM 对应 T=60ms，这是人耳最舒服的区间；
 * 初学者建议 13-15 WPM，科赫法训练常用 15 WPM 起步。
 */
data class MorseEntry(
    val char: Char,
    /** 电码，点为 `.`，划为 `-`。 */
    val code: String,
    val note: String? = null
)

/** 发声时序的一个片段。 */
data class MorseSymbol(
    /** true 表示有声音（键按下），false 表示静默。 */
    val on: Boolean,
    val durationMs: Int
)

object MorseTable {

    val entries: List<MorseEntry> = listOf(
        // ---------- 字母 ----------
        MorseEntry('A', ".-"),
        MorseEntry('B', "-..."),
        MorseEntry('C', "-.-."),
        MorseEntry('D', "-.."),
        MorseEntry('E', "."),
        MorseEntry('F', "..-."),
        MorseEntry('G', "--."),
        MorseEntry('H', "...."),
        MorseEntry('I', ".."),
        MorseEntry('J', ".---"),
        MorseEntry('K', "-.-"),
        MorseEntry('L', ".-.."),
        MorseEntry('M', "--"),
        MorseEntry('N', "-."),
        MorseEntry('O', "---"),
        MorseEntry('P', ".--."),
        MorseEntry('Q', "--.-"),
        MorseEntry('R', ".-."),
        MorseEntry('S', "..."),
        MorseEntry('T', "-"),
        MorseEntry('U', "..-"),
        MorseEntry('V', "...-"),
        MorseEntry('W', ".--"),
        MorseEntry('X', "-..-"),
        MorseEntry('Y', "-.--"),
        MorseEntry('Z', "--.."),

        // ---------- 数字 ----------
        MorseEntry('0', "-----"),
        MorseEntry('1', ".----"),
        MorseEntry('2', "..---"),
        MorseEntry('3', "...--"),
        MorseEntry('4', "....-"),
        MorseEntry('5', "....."),
        MorseEntry('6', "-...."),
        MorseEntry('7', "--..."),
        MorseEntry('8', "---.."),
        MorseEntry('9', "----."),

        // ---------- 标点 ----------
        MorseEntry('.', ".-.-.-"),
        MorseEntry(',', "--..--"),
        MorseEntry('?', "..--.."),
        MorseEntry('\'', ".----."),
        MorseEntry('!', "-.-.--"),
        MorseEntry('/', "-..-."),
        MorseEntry('(', "-.--."),
        MorseEntry(')', "-.--.-"),
        MorseEntry('&', ".-..."),
        MorseEntry(':', "---..."),
        MorseEntry(';', "-.-.-."),
        MorseEntry('=', "-...-"),
        MorseEntry('+', ".-.-."),
        MorseEntry('-', "-....-"),
        MorseEntry('_', "..--.-"),
        MorseEntry('"', ".-..-."),
        MorseEntry('$', "...-..-"),
        MorseEntry('@', ".--.-.")
    )

    /**
     * 简语：由两个字符连发而成，中间不停顿。
     * 单独列一张表，避免与正常字母序列混淆（如 `… ...`）。
     */
    val prosigns: List<MorseEntry> = listOf(
        MorseEntry('+', ".-.-.", note = "AR，报文结束"),
        MorseEntry('&', ".-...", note = "AS，请稍候"),
        MorseEntry(':', "-...-.-", note = "BK，请求插入"),
        MorseEntry('=', "-...-", note = "BT，另起一段"),
        MorseEntry('~', "...-.-", note = "SK，通联结束"),
        MorseEntry('%', "...-.", note = "SN，明白了（同 VE）"),
        MorseEntry('^', "........", note = "HH，发错了，请忽略"),
        MorseEntry('*', "-.-.-", note = "KA，起始信号")
    )

    private val charToCode: Map<Char, String> =
        (entries + prosigns).associate { it.char to it.code }

    private val codeToChar: Map<String, Char> =
        (entries + prosigns).associate { it.code to it.char }

    /** 字母条目（A-Z）。 */
    val letters: List<MorseEntry> = entries.filter { it.char.isLetter() }

    /** 数字条目（0-9）。 */
    val digits: List<MorseEntry> = entries.filter { it.char.isDigit() }

    /** 标点符号条目。 */
    val punctuations: List<MorseEntry> = entries.filter { !it.char.isLetterOrDigit() }

    /** 常用呼号片段，供练习与对照。 */
    val commonCallsigns: List<String> = listOf(
        "CQ", "DE", "QTH", "QSL", "QRZ", "RST", "BK", "TU", "73", "88", "OM", "YL", "DX"
    )

    /**
     * 文字转电码。
     * 字母间以空格分隔，词间以 ` / ` 分隔；无法编码的字符忽略。
     */
    fun encode(text: String): String {
        val upper = text.uppercase()
        val letters = mutableListOf<String>()
        val words = mutableListOf<String>()
        for (ch in upper) {
            when {
                ch == ' ' || ch == '\n' || ch == '\t' -> {
                    if (letters.isNotEmpty()) {
                        words.add(letters.joinToString(" "))
                        letters.clear()
                    }
                }
                charToCode.containsKey(ch) -> letters.add(charToCode.getValue(ch))
            }
        }
        if (letters.isNotEmpty()) words.add(letters.joinToString(" "))
        return words.joinToString(" / ")
    }

    /**
     * 电码转文字。
     * 支持空格分隔的字母与 `/` 分隔的单词；无法识别的组用 `?` 占位。
     */
    fun decode(morse: String): String {
        val normalized = morse.trim()
        if (normalized.isEmpty()) return ""
        return normalized
            .split("/")
            .joinToString(" ") { word ->
                word.trim()
                    .split(Regex("\\s+"))
                    .filter { it.isNotEmpty() }
                    .joinToString("") { token ->
                        codeToChar[token]?.toString() ?: "?"
                    }
            }.trim()
    }

    /**
     * 生成发声时序。
     *
     * @param text 待播报文本
     * @param wpm 速度，词/分钟，建议 5-40
     * @return 交替的「有声/静默」片段序列，单位毫秒
     */
    fun toSymbols(text: String, wpm: Int): List<MorseSymbol> {
        val unit = (1200.0 / wpm.coerceIn(5, 40)).roundToInt().coerceAtLeast(20)
        val out = mutableListOf<MorseSymbol>()

        fun emitWordGap() {
            // 前一个字符已产出 3T，此处只需补足到 7T
            out.add(MorseSymbol(false, unit * 4))
        }

        var pendingWordGap = false
        var anyEmitted = false

        for (ch in text.uppercase()) {
            when {
                ch == ' ' || ch == '\n' || ch == '\t' -> {
                    if (anyEmitted) pendingWordGap = true
                }
                else -> {
                    val code = charToCode[ch] ?: continue
                    if (pendingWordGap) {
                        emitWordGap()
                        pendingWordGap = false
                    } else if (anyEmitted) {
                        out.add(MorseSymbol(false, unit * 3)) // 字符间隔
                    }
                    code.forEachIndexed { index, symbol ->
                        if (index > 0) out.add(MorseSymbol(false, unit)) // 点划间隔
                        out.add(MorseSymbol(true, if (symbol == '.') unit else unit * 3))
                    }
                    anyEmitted = true
                }
            }
        }
        // 尾部补一个字符间隔，让最后一个音完整收尾
        if (anyEmitted) out.add(MorseSymbol(false, unit * 3))
        return out
    }

    /** 估算整段文本的发声总时长（毫秒），用于进度展示。 */
    fun totalDurationMs(text: String, wpm: Int): Int =
        toSymbols(text, wpm).sumOf { it.durationMs }
}

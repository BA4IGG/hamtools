package com.ham.tools.data.reference

/**
 * 通联用语速查表：Q 简语 + 字母解释法 + 常用缩略语。
 *
 * Q 简语起源于商用无线电报时代，用 QAA-QNZ 三字母码替代高频整句，
 * 现在 CW 与数字模式仍在大量使用。注意它的一个特性：
 * **同一代码后加问号是提问，不加是陈述**。例如 `QTH?` 问「你的位置？」，
 * `QTH SHANGHAI` 答「我在上海」。本表的 question / statement 两栏即对应这两面。
 *
 * 字母解释法采用国际民航组织（ICAO）标准，另附业余界口耳相传的变体，
 * 方便在信号不佳时交叉确认。
 */
data class QCodeEntry(
    val code: String,
    /** 作提问时的含义。 */
    val question: String,
    /** 作陈述/回答时的含义。 */
    val statement: String
)

/** 字母解释法条目。 */
data class PhoneticEntry(
    val letter: Char,
    /** ICAO 标准词。 */
    val word: String,
    /** 中文谐音提示。 */
    val hint: String,
    /** 业余界常见变体，无则为空。 */
    val variant: String? = null
)

object QCodeTable {

    val qCodes: List<QCodeEntry> = listOf(
        QCodeEntry("QRA", "你的电台名称是什么？", "我的电台名称是……"),
        QCodeEntry("QRB", "你我之间距离多远？", "我们之间距离约……公里"),
        QCodeEntry("QRG", "我的准确频率是多少？", "你的准确频率是……kHz/MHz"),
        QCodeEntry("QRH", "我的频率稳定吗？", "你的频率不稳定，有漂移"),
        QCodeEntry("QRI", "我的音调如何？", "你的音调偏颤/偏闷/正常"),
        QCodeEntry("QRJ", "你能收到我吗？", "我收不到你，信号太弱"),
        QCodeEntry("QRK", "我的信号清晰度如何？", "你的信号清晰度是……级"),
        QCodeEntry("QRL", "频率忙吗？", "频率正在使用中，请稍等"),
        QCodeEntry("QRM", "你受他台干扰吗？", "我受到……级人为干扰"),
        QCodeEntry("QRN", "你受天电干扰吗？", "我受到……级天电/噪声干扰"),
        QCodeEntry("QRO", "要我加大功率吗？", "请加大发射功率"),
        QCodeEntry("QRP", "要我减小功率吗？", "请减小发射功率（小功率通联）"),
        QCodeEntry("QRQ", "要我发快点吗？", "请加快发送速度"),
        QCodeEntry("QRS", "要我发慢点吗？", "请放慢发送速度"),
        QCodeEntry("QRT", "要我停止发送吗？", "我停止发送（关机/收工）"),
        QCodeEntry("QRU", "你有事要告诉我吗？", "我没有事情要告诉你"),
        QCodeEntry("QRV", "你准备好了吗？", "我已准备好，可以开始"),
        QCodeEntry("QRW", "要我转告……吗？", "请转告……我在……频率呼叫"),
        QCodeEntry("QRX", "我什么时候再呼叫你？", "请在……时（频率）再呼叫我"),
        QCodeEntry("QRY", "轮到我了第几位？", "你的次序是第……位"),
        QCodeEntry("QRZ", "谁在呼叫我？", "……正在呼叫你"),
        QCodeEntry("QSA", "我的信号强度如何？", "你的信号强度是……级（1-5）"),
        QCodeEntry("QSB", "我的信号有衰落吗？", "你的信号有衰落，时强时弱"),
        QCodeEntry("QSD", "我的键控有毛病吗？", "你的键控有缺陷"),
        QCodeEntry("QSG", "要我一次发若干份电报吗？", "请一次发送……份电报"),
        QCodeEntry("QSK", "能在我的信号中插入吗？", "你可以（我不能）在发送中插入"),
        QCodeEntry("QSL", "你能确认收到吗？", "我确认收到（亦指 QSL 卡片）"),
        QCodeEntry("QSM", "要我重发上份电报吗？", "请重发上一份或第……份电报"),
        QCodeEntry("QSN", "你听到我在……频率上发吗？", "我在……频率上听到你"),
        QCodeEntry("QSO", "你能直接和我通信吗？", "我能和你直接通信（一次通联）"),
        QCodeEntry("QSP", "能转发给……吗？", "我可以免费转发给……"),
        QCodeEntry("QST", "", "全体台站注意（通播）"),
        QCodeEntry("QSU", "要我用这个频率回复吗？", "请用这个频率或……频率回复"),
        QCodeEntry("QSV", "要我发一串 V 字吗？", "请发一串 V 字以便调校"),
        QCodeEntry("QSW", "你要用这个频率发吗？", "我将用……频率发射"),
        QCodeEntry("QSX", "你在哪个频率守听？", "我在……频率守听"),
        QCodeEntry("QSY", "要我转到别的频率吗？", "请转到……频率"),
        QCodeEntry("QSZ", "要我每个字发两遍吗？", "请把每个字/每组发两遍"),
        QCodeEntry("QTC", "你有电报要发吗？", "我有……份电报要发给你"),
        QCodeEntry("QTH", "你的位置在哪里？", "我的位置是……"),
        QCodeEntry("QTR", "现在准确时间是多少？", "现在的准确时间是……UTC"),
        QCodeEntry("QTV", "要我在……频率上替你守听吗？", "请在……频率上替我守听"),
        QCodeEntry("QTX", "你要继续和我保持联系吗？", "我会保持开着直到你呼叫"),
        QCodeEntry("QUA", "你有……的消息吗？", "有关于……的消息"),
        QCodeEntry("QRL?", "这个频率有人用吗？", "这个频率已占用（常用提问式）")
    )

    /** 字母解释法（ICAO 标准 + 业余变体）。 */
    val phonetics: List<PhoneticEntry> = listOf(
        PhoneticEntry('A', "Alpha", "阿尔法"),
        PhoneticEntry('B', "Bravo", "布拉沃"),
        PhoneticEntry('C', "Charlie", "查理"),
        PhoneticEntry('D', "Delta", "德尔塔"),
        PhoneticEntry('E', "Echo", "埃科"),
        PhoneticEntry('F', "Foxtrot", "福克斯罗特"),
        PhoneticEntry('G', "Golf", "高尔夫"),
        PhoneticEntry('H', "Hotel", "霍特尔"),
        PhoneticEntry('I', "India", "印第亚"),
        PhoneticEntry('J', "Juliett", "朱丽叶"),
        PhoneticEntry('K', "Kilo", "基洛"),
        PhoneticEntry('L', "Lima", "利马"),
        PhoneticEntry('M', "Mike", "麦克"),
        PhoneticEntry('N', "November", "诺温伯"),
        PhoneticEntry('O', "Oscar", "奥斯卡"),
        PhoneticEntry('P', "Papa", "帕帕"),
        PhoneticEntry('Q', "Quebec", "魁北克"),
        PhoneticEntry('R', "Romeo", "罗密欧"),
        PhoneticEntry('S', "Sierra", "塞拉"),
        PhoneticEntry('T', "Tango", "探戈"),
        PhoneticEntry('U', "Uniform", "尤尼佛姆"),
        PhoneticEntry('V', "Victor", "维克托"),
        PhoneticEntry('W', "Whiskey", "威士忌"),
        PhoneticEntry('X', "X-ray", "埃克斯雷"),
        PhoneticEntry('Y', "Yankee", "扬基"),
        PhoneticEntry('Z', "Zulu", "祖鲁"),
        // 数字的解释法（业余界习惯读法）
        PhoneticEntry('0', "Zero", "齐洛", variant = "也常读作 Nadazero"),
        PhoneticEntry('1', "One", "万", variant = "也常读作 Unaone"),
        PhoneticEntry('2', "Two", "图", variant = "也常读作 Bissotwo"),
        PhoneticEntry('3', "Three", "斯里", variant = "也常读作 Terrathree"),
        PhoneticEntry('4', "Four", "佛", variant = "也常读作 Kartefour"),
        PhoneticEntry('5', "Five", "法伊夫", variant = "也常读作 Pantafive"),
        PhoneticEntry('6', "Six", "西克斯", variant = "也常读作 Soxisix"),
        PhoneticEntry('7', "Seven", "赛文", variant = "也常读作 Setteseven"),
        PhoneticEntry('8', "Eight", "诶特", variant = "也常读作 Oktoeight"),
        PhoneticEntry('9', "Niner", "奈纳", variant = "避免与 five 混淆，不读 nine")
    )

    /**
     * 检索 Q 简语：命中代码或中英文释义。
     * 空关键词返回完整列表。
     */
    fun searchQCode(keyword: String): List<QCodeEntry> {
        val query = keyword.trim().uppercase()
        if (query.isEmpty()) return qCodes
        val lower = query.lowercase()
        return qCodes.filter { entry ->
            entry.code.contains(query) ||
                entry.question.lowercase().contains(lower) ||
                entry.statement.lowercase().contains(lower)
        }
    }

    /** 检索字母解释法。空关键词返回完整列表。 */
    fun searchPhonetic(keyword: String): List<PhoneticEntry> {
        val query = keyword.trim().lowercase()
        if (query.isEmpty()) return phonetics
        return phonetics.filter { entry ->
            entry.letter.lowercase() == query ||
                entry.word.lowercase().contains(query) ||
                entry.hint.contains(query) ||
                (entry.variant?.lowercase()?.contains(query) ?: false)
        }
    }

    /** 把一段文字转成解释法单词序列（非字母数字原样保留）。 */
    fun toPhoneticWords(text: String): List<String> =
        text.uppercase().mapNotNull { ch ->
            if (ch.isLetterOrDigit()) phonetics.firstOrNull { it.letter == ch }?.word else null
        }
}

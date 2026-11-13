package com.ham.tools.util

import com.ham.tools.data.reference.MorseTable
import kotlin.random.Random

/**
 * 科赫法（Koch method）CW 听抄训练的课程生成。
 *
 * 科赫法的核心是**一上来就用目标速度**，不靠放慢速度来迁就初学者：
 * 第 2 级只有 K、M 两个字符，听辨压力小；每掌握一级就加一个新字符，
 * 直到 40 级覆盖全部字母、数字与常用标点。字符顺序沿用经典 Koch 序列。
 *
 * 纯函数 + 可注入随机种子，便于单元测试复现。
 */
object CwLesson {

    /** 经典 Koch 字符加入顺序，共 40 级。全部字符都能被 MorseTable 编码。 */
    const val KOCH_ORDER = "KMURESNAPTLWI.JZFOY,VG5/Q92H38B74C1D60X?"

    const val MIN_LEVEL = 2
    val MAX_LEVEL: Int = KOCH_ORDER.length

    /** 某一级可用字符集（含之前所有级）。 */
    fun charsForLevel(level: Int): List<Char> =
        KOCH_ORDER.take(level.coerceIn(MIN_LEVEL, MAX_LEVEL)).toList()

    /** 新增的那个字符，用于界面提示「本课新增」。 */
    fun newCharOf(level: Int): Char =
        KOCH_ORDER[level.coerceIn(MIN_LEVEL, MAX_LEVEL) - 1]

    /** 生成一组随机码，字符间不加空格，播放器会按字符间隔（3T）连续拍发。 */
    fun generateGroup(level: Int, size: Int, seed: Long): String {
        val chars = charsForLevel(level)
        val random = Random(seed)
        val count = size.coerceIn(1, 60)
        return buildString {
            repeat(count) { append(chars[random.nextInt(chars.size)]) }
        }
    }

    /** PARIS 标准：点长 T(ms) = 1200 / WPM。 */
    fun unitMs(wpm: Int): Int = (1200.0 / wpm.coerceIn(5, 40)).toInt().coerceAtLeast(20)

    /** 估算一组码的播放时长，含尾部字符间隔。 */
    fun groupDurationMs(group: String, wpm: Int): Int =
        MorseTable.totalDurationMs(group, wpm)
}

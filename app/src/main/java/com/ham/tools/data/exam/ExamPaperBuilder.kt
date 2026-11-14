package com.ham.tools.data.exam

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 可打印的模拟试卷。组卷规则与 App 内模拟考试完全一致（同一份 [ExamEngine.buildPaper]），
 * 因此可以打印出来做纸笔练习，答案单独放在最后一页。
 *
 * 本文件不依赖 Android：试卷结构、答案表与折行都能跑本地单测；
 * 真正的 PDF 绘制在 ui/exam/ExamPdf.kt。
 */
data class ExamPaper(
    val level: ExamLevel,
    val seed: Long,
    val createdAt: Long,
    val questions: List<ExamQuestion>
) {
    val serial: String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date(createdAt))
    val title: String = "业余无线电操作证书考试 · ${level.label}模拟试卷"
    val answers: List<String> = questions.map { it.answer }
    val singleCount: Int get() = questions.count { !it.multi }
    val multiCount: Int get() = questions.count { it.multi }

    val intro: List<String> = listOf(
        "试卷编号 $serial · 题库版本 v20250728 · 本卷由 HAM 工具合集按官方配额随机生成",
        "共 ${questions.size} 题（单选 $singleCount 题、多选 $multiCount 题）｜建议用时 ${level.minutes} 分钟｜答对 ${level.passScore} 题合格",
        "多选题漏选、少选、多选均不得分；题目按估算难度由易到难排列，该难度不代表官方分级。",
        "答案与评分说明见本卷最后一页。"
    )
}

object ExamPaperBuilder {
    fun build(
        level: ExamLevel,
        bank: List<ExamQuestion>,
        seed: Long = System.currentTimeMillis(),
        now: Long = System.currentTimeMillis()
    ): ExamPaper = ExamPaper(
        level = level,
        seed = seed,
        createdAt = now,
        questions = ExamEngine.buildPaper(level, bank, seed)
    )

    /** 安全文件名：无空格、无中文路径问题，SAF 里默认落在「下载」目录。 */
    fun fileName(paper: ExamPaper): String =
        "HAM-Tools-${paper.level.name}-${paper.serial}.pdf"

    /** 答案表按 5 列排布，返回可直接绘制的文本行。 */
    fun answerLines(paper: ExamPaper): List<String> {
        val cells = paper.answers.mapIndexed { index, answer ->
            "%2d. %-4s".format(index + 1, answer)
        }
        return cells.chunked(5).map { row -> row.joinToString("  ").trimEnd() }
    }

    /**
     * 按「半角单位」折行：中文/全角占 2，ASCII 占 1。
     * PDF 绘制用同一套单位估算行宽，因此中文不会在词中间被切断，也不会超出版心。
     */
    fun wrap(text: String, maxUnits: Int): List<String> {
        if (text.isEmpty()) return listOf("")
        val width = maxUnits.coerceAtLeast(8)
        val lines = ArrayList<String>()
        var current = StringBuilder()
        var units = 0
        text.forEach { ch ->
            val cost = charUnits(ch)
            if (units + cost > width && current.isNotEmpty()) {
                lines += current.toString().trimEnd()
                current = StringBuilder()
                units = 0
            }
            current.append(ch)
            units += cost
        }
        if (current.isNotEmpty()) lines += current.toString().trimEnd()
        return lines
    }

    /** CJK 与常见全角标点按双宽计算，其余按半宽。 */
    fun charUnits(ch: Char): Int = when (ch.code) {
        in 0x1100..0x115F, in 0x2E80..0xA4CF, in 0xAC00..0xD7A3,
        in 0xF900..0xFAFF, in 0xFE10..0xFE19, in 0xFE30..0xFE6F,
        in 0xFF00..0xFF60, in 0xFFE0..0xFFE6 -> 2
        else -> 1
    }
}

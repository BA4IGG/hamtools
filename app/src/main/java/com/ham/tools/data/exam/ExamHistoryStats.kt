package com.ham.tools.data.exam

/**
 * 考试历史的统计计算。纯 JVM，图表只负责把这里算好的数字画出来。
 *
 * 只按「答对题数」与「得分率」两种口径统计：前者贴合官方合格线，
 * 后者让 A(40 题)/B(60 题)/C(90 题) 三类成绩能画在同一张趋势图里。
 */
data class ExamHistoryStats(
    val count: Int = 0,
    val passed: Int = 0,
    val passRate: Float = 0f,
    val averageScore: Float = 0f,
    val bestScore: Int = 0,
    val latestScore: Int = 0,
    val averageRatio: Float = 0f,
    val averageElapsedMillis: Long = 0L,
    val singleCorrect: Int = 0,
    val singleTotal: Int = 0,
    val multiCorrect: Int = 0,
    val multiTotal: Int = 0,
    /** 按时间正序，供折线图使用（最多 limit 条）。 */
    val trend: List<ExamRecord> = emptyList()
) {
    val singleAccuracy: Float get() = ratio(singleCorrect, singleTotal)
    val multiAccuracy: Float get() = ratio(multiCorrect, multiTotal)
    val averageMinutes: Float get() = averageElapsedMillis / 60_000f

    private fun ratio(a: Int, b: Int): Float = if (b <= 0) 0f else a.toFloat() / b
}

/**
 * @param onlyExam 只看模拟考试（默认）；false 时把练习、复习、错题重练也纳入。
 * @param level 非空时只统计该类别。
 */
fun examHistoryStats(
    history: List<ExamRecord>,
    level: ExamLevel? = null,
    onlyExam: Boolean = true,
    limit: Int = 20
): ExamHistoryStats {
    val filtered = history.filter { record ->
        (!onlyExam || record.mode == ExamMode.EXAM.name) &&
            (level == null || record.level == level.name)
    }
    if (filtered.isEmpty()) return ExamHistoryStats()

    val chronological = filtered.sortedWith(
        compareBy<ExamRecord> { it.at }.thenBy { it.id }
    )
    val trend = chronological.takeLast(limit.coerceAtLeast(1))
    val elapsedRecords = filtered.filter { it.elapsedMillis > 0L }
    return ExamHistoryStats(
        count = filtered.size,
        passed = filtered.count { it.passed },
        passRate = filtered.count { it.passed }.toFloat() / filtered.size,
        averageScore = filtered.map { it.score }.average().toFloat(),
        bestScore = filtered.maxOf { it.score },
        latestScore = chronological.last().score,
        averageRatio = filtered.map { it.ratio }.average().toFloat(),
        averageElapsedMillis = if (elapsedRecords.isEmpty()) {
            0L
        } else {
            elapsedRecords.map { it.elapsedMillis }.average().toLong()
        },
        singleCorrect = filtered.sumOf { it.singleCorrect },
        singleTotal = filtered.sumOf { it.singleTotal },
        multiCorrect = filtered.sumOf { it.multiCorrect },
        multiTotal = filtered.sumOf { it.multiTotal },
        trend = trend
    )
}

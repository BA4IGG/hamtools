package com.ham.tools.data.exam

import kotlin.math.max
import kotlin.random.Random

/** 顺序练习的全量计划：题目、起始序号、类别总题数、是否已开启新一轮。 */
data class SequentialPlan(
    val questions: List<ExamQuestion>,
    val startIndex: Int,
    val totalInLevel: Int,
    val restarted: Boolean
)

/**
 * 艾宾浩斯遗忘曲线复习调度 + 组卷与判分。纯 JVM，无 Android 依赖。
 *
 * 记忆曲线不是「背完就结束」：答对后按 5 分钟 → 30 分钟 → 12 小时 → 1 天 → 2 天 →
 * 4 天 → 7 天 → 15 天 → 30 天的间隔逐级拉长复习；一旦答错立即回退到第一档重新巩固。
 * 练习时优先推送到期题，组内始终按难度由易到难排列。
 */
object Ebbinghaus {
    /** 各阶段的复习间隔（分钟）。stage 0 对应首次答对后的 5 分钟。 */
    val INTERVAL_MINUTES = longArrayOf(5L, 30L, 720L, 1_440L, 2_880L, 5_760L, 10_080L, 21_600L, 43_200L)

    const val NEW_STAGE = -1
    val MAX_STAGE: Int get() = INTERVAL_MINUTES.lastIndex

    fun intervalMinutes(stage: Int): Long =
        INTERVAL_MINUTES[stage.coerceIn(0, MAX_STAGE)]

    fun nextReviewAt(stage: Int, now: Long): Long =
        now + intervalMinutes(stage) * 60_000L

    /** 一次作答后更新单题记忆状态。 */
    fun applyAnswer(
        id: String,
        previous: QuestionProgress?,
        correct: Boolean,
        now: Long
    ): QuestionProgress {
        val base = previous ?: QuestionProgress(id = id)
        return if (correct) {
            val stage = (base.stage + 1).coerceAtMost(MAX_STAGE)
            base.copy(
                stage = stage,
                lastAt = now,
                nextAt = nextReviewAt(stage, now),
                correctCount = base.correctCount + 1,
                lastWrong = false
            )
        } else {
            // 错题回到最短间隔，重新走完整条曲线
            base.copy(
                stage = 0,
                lastAt = now,
                nextAt = nextReviewAt(0, now),
                wrongCount = base.wrongCount + 1,
                lapses = base.lapses + 1,
                lastWrong = true
            )
        }
    }

    fun isDue(progress: QuestionProgress?, now: Long): Boolean =
        progress != null && progress.isDue(now)

    /**
     * 复习队列：到期题（越早到期越靠前）+ 尚未学的新题，各自内部按难度升序。
     * 这既保证「先复习将要遗忘的」，又满足「由易到难」。
     */
    fun reviewQueue(
        pool: List<ExamQuestion>,
        progress: Map<String, QuestionProgress>,
        now: Long,
        limit: Int
    ): List<ExamQuestion> = buildReviewQueue(pool, progress, now, limit)

    private fun buildReviewQueue(
        pool: List<ExamQuestion>,
        progress: Map<String, QuestionProgress>,
        now: Long,
        limit: Int
    ): List<ExamQuestion> {
        if (limit <= 0) return emptyList()
        val byDifficulty = compareBy<ExamQuestion> { it.difficulty }.thenBy { it.id }
        val due = pool.filter { progress[it.id]?.isDue(now) == true }
            .sortedWith(compareBy<ExamQuestion> { progress.getValue(it.id).nextAt }.then(byDifficulty))
        val fresh = pool.filter { progress[it.id] == null }
            .sortedWith(byDifficulty)
        return (due + fresh).take(limit)
    }
}

/** 组卷、判分与成绩统计。 */
object ExamEngine {
    /**
     * 按官方配额抽卷：单选 / 多选分别按难度分层抽样，再整卷按难度升序排列，
     * 所以试卷从第一题到最后一题是由易到难，且不会全抽到同一个难度段。
     */
    fun buildPaper(level: ExamLevel, bank: List<ExamQuestion>, seed: Long): List<ExamQuestion> {
        val pool = bank.filter { it.belongsTo(level) }
        val singles = pool.filter { !it.multi }
        val multis = pool.filter { it.multi }
        require(singles.size >= level.singleCount) { "${level.label} 单选题不足" }
        require(multis.size >= level.multiCount) { "${level.label} 多选题不足" }
        val random = Random(seed)
        val picked = stratified(singles, level.singleCount, random) +
            stratified(multis, level.multiCount, random)
        return picked.sortedWith(compareBy<ExamQuestion> { it.difficulty }.thenBy { it.id })
    }

    /** 把按难度排好序的候选切成 count 个桶，每桶随机取一题。 */
    private fun stratified(
        candidates: List<ExamQuestion>,
        count: Int,
        random: Random
    ): List<ExamQuestion> {
        val sorted = candidates.sortedWith(compareBy<ExamQuestion> { it.difficulty }.thenBy { it.id })
        val width = sorted.size.toDouble() / count
        return List(count) { bucket ->
            val from = (bucket * width).toInt().coerceIn(0, sorted.lastIndex)
            val to = ((bucket + 1) * width).toInt().coerceIn(from + 1, sorted.size)
            sorted.subList(from, to)[random.nextInt(to - from)]
        }
    }

    /**
     * 旧版的「一轮 20 题」队列，仍供按量练习使用。
     * 正式的「顺序练习」已改为 [sequentialPractice]：从头做到尾并支持断点续做。
     */
    fun practiceQueue(
        level: ExamLevel,
        bank: List<ExamQuestion>,
        progress: Map<String, QuestionProgress> = emptyMap(),
        limit: Int
    ): List<ExamQuestion> {
        val byDifficulty = compareBy<ExamQuestion> { it.difficulty }.thenBy { it.id }
        val sorted = bank.filter { it.belongsTo(level) }.sortedWith(byDifficulty)
        val fresh = sorted.filter { progress[it.id] == null }
        val reviewed = sorted.filter { progress[it.id] != null }
            .sortedWith(compareBy<ExamQuestion> { progress.getValue(it.id).lastAt }.then(byDifficulty))
        return (fresh + reviewed).take(max(0, limit))
    }

    /**
     * 一次「从头到尾」顺序练习的题目清单。
     *
     * [cursor] 是全量题库里下一题的 0-based 索引：中途退出后从断点继续，
     * 做满一轮（cursor >= 总题数）再从第一题重新开始。
     */
    fun sequentialPractice(
        level: ExamLevel,
        bank: List<ExamQuestion>,
        cursor: Int = 0
    ): SequentialPlan {
        val byDifficulty = compareBy<ExamQuestion> { it.difficulty }.thenBy { it.id }
        val sorted = bank.filter { it.belongsTo(level) }.sortedWith(byDifficulty)
        if (sorted.isEmpty()) return SequentialPlan(emptyList(), 0, 0, false)
        val normalized = cursor.coerceIn(0, sorted.size)
        val finishedRound = normalized >= sorted.size
        val from = if (finishedRound) 0 else normalized
        return SequentialPlan(
            questions = sorted.subList(from, sorted.size).toList(),
            startIndex = from,
            totalInLevel = sorted.size,
            restarted = finishedRound
        )
    }

    /** 错题重练：按错误次数降序、难度升序；limit 传 Int.MAX_VALUE 即为全量。 */
    fun wrongQueue(
        level: ExamLevel,
        bank: List<ExamQuestion>,
        progress: Map<String, QuestionProgress>,
        limit: Int
    ): List<ExamQuestion> = bank.filter { q ->
        q.belongsTo(level) && (progress[q.id]?.wrongCount ?: 0) > 0
    }.sortedWith(
        compareByDescending<ExamQuestion> { progress.getValue(it.id).wrongCount }
            .thenBy { it.difficulty }
            .thenBy { it.id }
    ).take(max(0, limit))

    /**
     * 判分：多选题必须与答案集合**完全一致**，漏选、少选、多选一律算错。
     * 单选也走同一套集合比较，避免两套逻辑出现分歧。
     */
    fun isCorrect(question: ExamQuestion, selected: Set<Int>): Boolean =
        selected == question.answerIndices

    fun score(questions: List<ExamQuestion>, answers: Map<String, Set<Int>>): Int =
        questions.count { isCorrect(it, answers[it.id].orEmpty()) }

    fun passed(level: ExamLevel, correct: Int): Boolean = correct >= level.passScore

    /** 距离下一次复习的分钟数，用于首页展示。 */
    fun minutesUntil(nextAt: Long, now: Long): Long =
        if (nextAt <= now) 0 else (nextAt - now + 59_999L) / 60_000L
}

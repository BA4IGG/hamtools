package com.ham.tools.data.exam

/**
 * 本地学习记录与账号云端记录的合并规则。纯 JVM，便于单测。
 *
 * 不同设备可能各自离线练了一段时间，因此**不能整块覆盖**：
 * - 单题进度：以最后作答时间为准，同一时刻取累计作答次数更多的那份；
 * - 考试历史：按稳定 id 去重，按时间倒序保留最近若干条；
 * - 顺序练习断点：取较大的值（顺序练习只会向前推进）。
 *
 * 云端只存题号、数字与成绩摘要，不存题目正文、不存用户所选答案，也不存任何个人资料。
 */
object ExamProgressMerge {

    /** 历史记录的保留上限；本地与云端一致，避免两端反复裁剪导致同步抖动。 */
    const val MAX_HISTORY = 200

    fun merge(local: ExamProgressData, remote: ExamProgressData): ExamProgressData {
        val progress = HashMap<String, QuestionProgress>(remote.progress)
        local.progress.forEach { (id, mine) ->
            val theirs = progress[id]
            progress[id] = if (theirs == null || newer(mine, theirs)) mine else theirs
        }

        val history = LinkedHashMap<String, ExamRecord>()
        // 先放远端，再用本地同 id 记录补全（本地通常字段更全），最后按时间倒序
        remote.history.forEach { history[it.id] = it }
        local.history.forEach { history[it.id] = it }
        val sortedHistory = history.values
            .sortedWith(compareByDescending<ExamRecord> { it.at }.thenByDescending { it.id })
            .take(MAX_HISTORY)

        val cursors = HashMap(remote.practiceCursor)
        local.practiceCursor.forEach { (level, index) ->
            cursors[level] = maxOf(cursors[level] ?: 0, index)
        }

        return ExamProgressData(
            progress = progress,
            history = sortedHistory,
            practiceCursor = cursors
        )
    }

    private fun newer(mine: QuestionProgress, theirs: QuestionProgress): Boolean {
        if (mine.lastAt != theirs.lastAt) return mine.lastAt > theirs.lastAt
        val mineAnswers = mine.correctCount + mine.wrongCount
        val theirAnswers = theirs.correctCount + theirs.wrongCount
        return mineAnswers >= theirAnswers
    }

    /** 两个方向合并都应收敛到同一份数据，用于自检与单测。 */
    fun converged(local: ExamProgressData, remote: ExamProgressData): Boolean =
        normalize(merge(local, remote)) == normalize(merge(remote, local))

    private fun normalize(data: ExamProgressData) = data.copy(
        progress = data.progress.toSortedMap(),
        practiceCursor = data.practiceCursor.toSortedMap(),
        history = data.history.sortedByDescending { it.id }
    )
}

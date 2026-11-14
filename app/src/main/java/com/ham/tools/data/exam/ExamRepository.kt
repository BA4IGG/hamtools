package com.ham.tools.data.exam

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * 题库与学习记录的本地门面。
 *
 * 题库、附图始终在设备本地；学习记录默认写在应用私有目录，不登录也能完整使用。
 * 登录账号后可由 ViewModel 把这份摘要同步到账号（仅题号进度与成绩摘要），
 * 但本地副本始终是唯一的工作副本，服务端不可用不影响任何练习功能。
 */
class ExamRepository(private val context: Context) {

    @Volatile
    private var cachedBank: ExamBankData? = null

    private val progressFile: File get() = File(context.filesDir, "exam_progress.json")

    suspend fun bank(): ExamBankData = withContext(Dispatchers.IO) {
        cachedBank ?: run {
            val text = context.assets.open(BANK_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
            ExamBankParser.parse(JSONObject(text)).also { cachedBank = it }
        }
    }

    /** 同步读取，供 ViewModel 初始化时使用；文件损坏时退回空记录而不是崩溃。 */
    fun readProgress(): ExamProgressData = synchronized(this) {
        if (!progressFile.exists()) return ExamProgressData()
        runCatching {
            ExamProgressCodec.decode(JSONObject(progressFile.readText(Charsets.UTF_8)))
        }.getOrDefault(ExamProgressData())
    }

    fun saveProgress(data: ExamProgressData): Unit = synchronized(this) {
        runCatching {
            progressFile.writeText(ExamProgressCodec.encode(data).toString(), Charsets.UTF_8)
        }
    }

    fun clearProgress() {
        synchronized(this) { progressFile.delete() }
    }

    /** 记录一次作答，返回更新后的完整状态。 */
    fun recordAnswer(
        current: ExamProgressData,
        questionId: String,
        correct: Boolean,
        now: Long = System.currentTimeMillis()
    ): ExamProgressData {
        val updated = current.progress.toMutableMap()
        updated[questionId] = Ebbinghaus.applyAnswer(questionId, updated[questionId], correct, now)
        return current.copy(progress = updated)
    }

    /** 交卷后追加一条可回看的成绩记录，最多保留最近 200 次。 */
    fun appendRecord(
        current: ExamProgressData,
        level: ExamLevel,
        mode: ExamMode,
        score: Int,
        total: Int,
        now: Long = System.currentTimeMillis(),
        elapsedMillis: Long = 0L,
        singleCorrect: Int = 0,
        singleTotal: Int = 0,
        multiCorrect: Int = 0,
        multiTotal: Int = 0,
        wrongIds: List<String> = emptyList(),
        timedOut: Boolean = false
    ): ExamProgressData {
        val record = ExamRecord(
            level = level.name,
            mode = mode.name,
            score = score,
            total = total,
            passed = if (mode == ExamMode.EXAM) ExamEngine.passed(level, score) else score == total,
            at = now,
            elapsedMillis = elapsedMillis,
            singleCorrect = singleCorrect,
            singleTotal = singleTotal,
            multiCorrect = multiCorrect,
            multiTotal = multiTotal,
            wrongIds = wrongIds,
            timedOut = timedOut
        )
        return current.copy(
            history = (listOf(record) + current.history).take(ExamProgressMerge.MAX_HISTORY)
        )
    }

    /** 顺序练习答完一题就推进断点；做满一轮归零，下次从头开始。 */
    fun advanceCursor(current: ExamProgressData, level: ExamLevel, nextIndex: Int): ExamProgressData {
        val cursors = current.practiceCursor.toMutableMap()
        cursors[level.name] = nextIndex.coerceAtLeast(0)
        return current.copy(practiceCursor = cursors)
    }

    fun resetCursor(current: ExamProgressData, level: ExamLevel): ExamProgressData {
        val cursors = current.practiceCursor.toMutableMap()
        cursors[level.name] = 0
        return current.copy(practiceCursor = cursors)
    }

    companion object {
        const val BANK_ASSET = "exam/questions.json"
        const val FIGURE_ASSET_PREFIX = "exam/figures/page-"
    }
}

/** 首页概览统计，纯计算便于单测与预览。 */
data class ExamOverview(
    val learned: Int = 0,
    val due: Int = 0,
    val wrong: Int = 0,
    val mastered: Int = 0,
    val totalAnswered: Int = 0,
    val totalCorrect: Int = 0,
    val examPassed: Int = 0
) {
    val accuracy: Float get() = if (totalAnswered == 0) 0f else totalCorrect.toFloat() / totalAnswered
}

fun buildOverview(data: ExamProgressData, now: Long = System.currentTimeMillis()): ExamOverview {
    val values = data.progress.values
    return ExamOverview(
        learned = values.count { !it.isNew },
        due = values.count { it.isDue(now) },
        wrong = values.count { it.wrongCount > 0 },
        mastered = values.count { it.stage >= 5 },
        totalAnswered = values.sumOf { it.correctCount + it.wrongCount },
        totalCorrect = values.sumOf { it.correctCount },
        examPassed = data.history.count { it.mode == ExamMode.EXAM.name && it.passed }
    )
}

package com.ham.tools.data.exam

import org.json.JSONArray
import org.json.JSONObject

/**
 * 业余无线电操作证书考试练习：题库模型与规则。
 *
 * 本文件全部是纯 JVM 代码（除 org.json 外不依赖 Android），因此 A/B/C 组卷规则、
 * 艾宾浩斯调度、判分与进度编解码都能跑本地单测。assets 读取与界面在别的层。
 *
 * 题库来源：《业余电台操作证书考试题库（v20250728）》A/B/C 三类 PDF，
 * 按 [I] 全局题号去重合并，共 1375 题；[T] 含多个字母即为多选题。
 */

/** 附图裁切框，单位为 PDF points；[page] 从 0 开始，对应 assets 的 page-(page+1).png。 */
data class ExamFigure(
    val page: Int,
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float
)

data class ExamQuestion(
    val id: String,
    /** 官方题库 [J] 编号，用于溯源与附图关联。 */
    val sourceId: String,
    /** 该题出现在哪几类题库中，例如 "ABC" / "BC"。 */
    val levels: String,
    /** [P] 章节号，例如 4.2.2。 */
    val chapter: String,
    val text: String,
    val options: List<String>,
    /** 正确选项字母，例如 "A" 或 "ABD"。 */
    val answer: String,
    val multi: Boolean,
    /** 静态难度 1~100，由题号顺序、题型、篇幅、数值与附图综合估算。 */
    val difficulty: Int,
    val figure: ExamFigure? = null
) {
    fun belongsTo(level: ExamLevel): Boolean = level.name in levels

    /** 正确选项的 0-based 索引集合。 */
    val answerIndices: Set<Int> by lazy {
        answer.map { it - 'A' }.toSet()
    }
}

/**
 * 考试类别与出题规则。数值来自用户给定的正式考试要求：
 * A 40 题（32 单 + 8 多）/ 40 分钟 / 答对 30 题合格；
 * B 60 题（45 单 + 15 多）/ 60 分钟 / 45 题合格；
 * C 90 题（70 单 + 20 多）/ 90 分钟 / 70 题合格。
 */
enum class ExamLevel(
    val label: String,
    val singleCount: Int,
    val multiCount: Int,
    val minutes: Int,
    val passScore: Int
) {
    A("A 类", 32, 8, 40, 30),
    B("B 类", 45, 15, 60, 45),
    C("C 类", 70, 20, 90, 70);

    val totalCount: Int get() = singleCount + multiCount
    val durationMillis: Long get() = minutes * 60_000L
}

/** 四种练习形态。 */
enum class ExamMode(val label: String) {
    EXAM("模拟考试"),
    PRACTICE("顺序练习"),
    REVIEW("记忆复习"),
    WRONG("错题重练")
}

/** 单题的记忆状态，stage = -1 表示从未作答。 */
data class QuestionProgress(
    val id: String,
    val stage: Int = -1,
    val lastAt: Long = 0L,
    val nextAt: Long = 0L,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val lapses: Int = 0,
    val lastWrong: Boolean = false
) {
    val isNew: Boolean get() = stage < 0
    fun isDue(now: Long): Boolean = !isNew && nextAt <= now
}

/**
 * 一次交卷的记录。只存摘要与错题题号，不存逐题选项答案（隐私最小化）。
 * 新增字段一律带默认值，保证旧版 exam_progress.json 能被无损读入。
 */
data class ExamRecord(
    val level: String,
    val mode: String,
    val score: Int,
    val total: Int,
    val passed: Boolean,
    val at: Long,
    /** 稳定主键，用于跨设备合并去重；旧数据按时间+类别+模式推导。 */
    val id: String = "$at-$level-$mode-$score",
    val elapsedMillis: Long = 0L,
    val singleCorrect: Int = 0,
    val singleTotal: Int = 0,
    val multiCorrect: Int = 0,
    val multiTotal: Int = 0,
    /** 本轮答错的题号，便于历史详情回看；不记录用户选了什么。 */
    val wrongIds: List<String> = emptyList(),
    val timedOut: Boolean = false
) {
    /** 百分比成绩，用于图表（总分不同也能画在同一条轴上）。 */
    val ratio: Float get() = if (total <= 0) 0f else score.toFloat() / total
}

data class ExamProgressData(
    val progress: Map<String, QuestionProgress> = emptyMap(),
    val history: List<ExamRecord> = emptyList(),
    /**
     * 顺序练习断点：类别名 -> 全量题库里「下一题」的 0-based 索引。
     * 从头做到尾，中途退出后能接着做，做满一轮后归零重新开始。
     */
    val practiceCursor: Map<String, Int> = emptyMap()
)

/** 题库 assets 的解析结果。 */
data class ExamBankData(
    val version: String,
    /** JSON 中声明的题数，解析后可与实际条数核对，避免题库被截断。 */
    val total: Int,
    val figureDpi: Int,
    val figurePages: Int,
    val questions: List<ExamQuestion>
)

object ExamBankParser {
    fun parse(json: JSONObject): ExamBankData {
        val array = json.getJSONArray("questions")
        val questions = ArrayList<ExamQuestion>(array.length())
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val optionsJson = item.getJSONArray("o")
            val options = List(optionsJson.length()) { optionsJson.getString(it) }
            require(options.size == 4) { "${item.getString("i")} 选项数量不是 4" }
            val answer = item.getString("t")
            require(answer.isNotEmpty() && answer.all { it in 'A'..'D' }) {
                "${item.getString("i")} 答案非法：$answer"
            }
            val figure = item.optJSONArray("f")?.let { box ->
                ExamFigure(
                    page = box.getInt(0),
                    x0 = box.getDouble(1).toFloat(),
                    y0 = box.getDouble(2).toFloat(),
                    x1 = box.getDouble(3).toFloat(),
                    y1 = box.getDouble(4).toFloat()
                )
            }
            questions += ExamQuestion(
                id = item.getString("i"),
                sourceId = item.getString("j"),
                levels = item.getString("lv"),
                chapter = item.getString("p"),
                text = item.getString("q"),
                options = options,
                answer = answer,
                multi = item.optInt("m", if (answer.length > 1) 1 else 0) == 1,
                difficulty = item.getInt("d"),
                figure = figure
            )
        }
        return ExamBankData(
            version = json.optString("version", ""),
            total = json.optInt("total", questions.size),
            figureDpi = json.optInt("figureDpi", 180),
            figurePages = json.optInt("figurePages", 0),
            questions = questions
        )
    }
}

/** 本地学习进度的 JSON 编解码；用短键名控制文件体积。 */
object ExamProgressCodec {
    /** v2 起增加顺序练习断点与考试详情；decode 仍完整兼容 v1 文件。 */
    private const val VERSION = 2

    fun encode(data: ExamProgressData): JSONObject = JSONObject().apply {
        put("v", VERSION)
        put("p", JSONObject().apply {
            data.progress.values.forEach { p ->
                put(p.id, JSONObject().apply {
                    put("s", p.stage)
                    put("l", p.lastAt)
                    put("n", p.nextAt)
                    put("c", p.correctCount)
                    put("w", p.wrongCount)
                    put("x", p.lapses)
                    if (p.lastWrong) put("e", 1)
                })
            }
        })
        put("h", JSONArray().apply {
            data.history.forEach { r ->
                put(JSONObject().apply {
                    put("lv", r.level)
                    put("m", r.mode)
                    put("s", r.score)
                    put("t", r.total)
                    if (r.passed) put("p", 1)
                    put("a", r.at)
                    put("id", r.id)
                    put("el", r.elapsedMillis)
                    put("sc", r.singleCorrect)
                    put("st", r.singleTotal)
                    put("mc", r.multiCorrect)
                    put("mt", r.multiTotal)
                    if (r.timedOut) put("to", 1)
                    if (r.wrongIds.isNotEmpty()) {
                        put("wi", JSONArray().apply { r.wrongIds.forEach { put(it) } })
                    }
                })
            }
        })
        put("c", JSONObject().apply {
            data.practiceCursor.forEach { (level, index) -> put(level, index) }
        })
    }

    fun decode(json: JSONObject): ExamProgressData {
        val progress = LinkedHashMap<String, QuestionProgress>()
        val p = json.optJSONObject("p")
        if (p != null) {
            val keys = p.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val o = p.getJSONObject(id)
                progress[id] = QuestionProgress(
                    id = id,
                    stage = o.optInt("s", -1),
                    lastAt = o.optLong("l", 0L),
                    nextAt = o.optLong("n", 0L),
                    correctCount = o.optInt("c", 0),
                    wrongCount = o.optInt("w", 0),
                    lapses = o.optInt("x", 0),
                    lastWrong = o.optInt("e", 0) == 1
                )
            }
        }
        val history = ArrayList<ExamRecord>()
        val h = json.optJSONArray("h")
        if (h != null) {
            for (i in 0 until h.length()) {
                val o = h.getJSONObject(i)
                val level = o.optString("lv", "")
                val mode = o.optString("m", "")
                val score = o.optInt("s", 0)
                val at = o.optLong("a", 0L)
                val wrongIds = ArrayList<String>()
                o.optJSONArray("wi")?.let { array ->
                    for (k in 0 until array.length()) wrongIds += array.optString(k, "")
                }
                history += ExamRecord(
                    level = level,
                    mode = mode,
                    score = score,
                    total = o.optInt("t", 0),
                    passed = o.optInt("p", 0) == 1,
                    at = at,
                    id = o.optString("id", "$at-$level-$mode-$score").ifBlank { "$at-$level-$mode-$score" },
                    elapsedMillis = o.optLong("el", 0L),
                    singleCorrect = o.optInt("sc", 0),
                    singleTotal = o.optInt("st", 0),
                    multiCorrect = o.optInt("mc", 0),
                    multiTotal = o.optInt("mt", 0),
                    wrongIds = wrongIds.filter { it.isNotBlank() },
                    timedOut = o.optInt("to", 0) == 1
                )
            }
        }
        val cursors = LinkedHashMap<String, Int>()
        json.optJSONObject("c")?.let { c ->
            val keys = c.keys()
            while (keys.hasNext()) {
                val level = keys.next()
                cursors[level] = c.optInt(level, 0).coerceAtLeast(0)
            }
        }
        return ExamProgressData(progress, history, cursors)
    }
}

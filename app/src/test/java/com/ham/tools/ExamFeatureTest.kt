package com.ham.tools

import com.ham.tools.data.exam.Ebbinghaus
import com.ham.tools.data.exam.ExamBankParser
import com.ham.tools.data.exam.ExamEngine
import com.ham.tools.data.exam.ExamLevel
import com.ham.tools.data.exam.ExamMode
import com.ham.tools.data.exam.ExamPaperBuilder
import com.ham.tools.data.exam.ExamProgressCodec
import com.ham.tools.data.exam.ExamProgressData
import com.ham.tools.data.exam.ExamProgressMerge
import com.ham.tools.data.exam.ExamQuestion
import com.ham.tools.data.exam.ExamRecord
import com.ham.tools.data.exam.QuestionProgress
import com.ham.tools.data.exam.examHistoryStats
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 本次迭代新增能力：全量顺序练习、断点、云同步合并、历史统计、纸质组卷。 */
class ExamFeatureTest {

    private fun question(
        serial: Int,
        multi: Boolean = serial % 5 == 0,
        levels: String = "ABC",
        difficulty: Int = serial
    ) = ExamQuestion(
        id = "MC${if (multi) 2 else 1}-${serial.toString().padStart(4, '0')}",
        sourceId = "LK$serial",
        levels = levels,
        chapter = "1.1.1",
        text = "题目 $serial",
        options = listOf("选项A", "选项B", "选项C", "选项D"),
        answer = if (multi) "AC" else "B",
        multi = multi,
        difficulty = difficulty
    )

    private fun bank(): List<ExamQuestion> = (1..120).map { question(it) }

    private fun realQuestions(): List<ExamQuestion> {
        val candidates = listOf(
            File("src/main/assets/exam/questions.json"),
            File("app/src/main/assets/exam/questions.json")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("找不到题库 assets：${candidates.map { it.absolutePath }}")
        return ExamBankParser.parse(JSONObject(file.readText(Charsets.UTF_8))).questions
    }

    // ---- 顺序练习：从头到尾 + 断点续做 ----

    @Test
    fun `顺序练习一次给出全量题目且由易到难`() {
        val all = bank()
        val plan = ExamEngine.sequentialPractice(ExamLevel.A, all, 0)
        assertEquals(all.size, plan.questions.size)
        assertEquals(all.size, plan.totalInLevel)
        assertEquals(0, plan.startIndex)
        assertFalse(plan.restarted)
        assertEquals(plan.questions.sortedBy { it.difficulty }, plan.questions)
        assertEquals(plan.questions.size, plan.questions.distinctBy { it.id }.size)
    }

    @Test
    fun `顺序练习从断点继续而不是回到第一题`() {
        val all = bank()
        val first = ExamEngine.sequentialPractice(ExamLevel.B, all, 0).questions
        val resumed = ExamEngine.sequentialPractice(ExamLevel.B, all, 47)
        assertEquals(47, resumed.startIndex)
        assertEquals(all.size - 47, resumed.questions.size)
        assertEquals(first.subList(47, first.size), resumed.questions)
        assertTrue(resumed.questions.none { it.id in first.take(47).map { q -> q.id } })
    }

    @Test
    fun `做满一轮后顺序练习自动回到第一题`() {
        val all = bank()
        val done = ExamEngine.sequentialPractice(ExamLevel.C, all, all.size)
        assertTrue(done.restarted)
        assertEquals(0, done.startIndex)
        assertEquals(all.size, done.questions.size)
        // 越界的断点也不能让队列变空或崩溃
        assertEquals(all.size, ExamEngine.sequentialPractice(ExamLevel.C, all, 9999).questions.size)
    }

    @Test
    fun `真实题库三类顺序练习都能覆盖全部题目`() {
        val all = realQuestions()
        for (level in ExamLevel.entries) {
            val expected = all.count { it.belongsTo(level) }
            val plan = ExamEngine.sequentialPractice(level, all, 0)
            assertEquals("$level 全量题数", expected, plan.questions.size)
            assertEquals(expected, plan.totalInLevel)
            assertTrue("$level 混入其它类别", plan.questions.all { it.belongsTo(level) })
            val again = ExamEngine.sequentialPractice(level, all, expected / 2)
            assertEquals(expected - expected / 2, again.questions.size)
        }
        assertEquals(683, all.count { it.belongsTo(ExamLevel.A) })
        assertEquals(1143, all.count { it.belongsTo(ExamLevel.B) })
        assertEquals(1282, all.count { it.belongsTo(ExamLevel.C) })
    }

    @Test
    fun `错题重练传全量上限时给出所有错题`() {
        val all = bank().take(37)
        val now = 1_700_000_000_000L
        val progress = all.take(31).associate {
            it.id to Ebbinghaus.applyAnswer(it.id, null, false, now)
        }
        val queue = ExamEngine.wrongQueue(ExamLevel.A, all, progress, Int.MAX_VALUE)
        assertEquals(31, queue.size)
        assertEquals(queue.sortedByDescending { progress.getValue(it.id).wrongCount }, queue)
    }

    // ---- 学习记录编解码（含断点与成绩详情） ----

    @Test
    fun `v2 进度文件完整往返保留断点与成绩详情`() {
        val data = ExamProgressData(
            progress = mapOf(
                "MC1-0001" to QuestionProgress("MC1-0001", 4, 100L, 200L, 6, 2, 1, true)
            ),
            history = listOf(
                ExamRecord(
                    level = "C",
                    mode = ExamMode.EXAM.name,
                    score = 71,
                    total = 90,
                    passed = true,
                    at = 1_700_000_000_000L,
                    elapsedMillis = 3_600_000L,
                    singleCorrect = 56,
                    singleTotal = 70,
                    multiCorrect = 15,
                    multiTotal = 20,
                    wrongIds = listOf("MC1-0003", "MC2-0015"),
                    timedOut = true
                )
            ),
            practiceCursor = mapOf("A" to 137, "C" to 0)
        )
        val decoded = ExamProgressCodec.decode(ExamProgressCodec.encode(data))
        assertEquals(data, decoded)
        assertEquals(2, ExamProgressCodec.encode(data).getInt("v"))
    }

    @Test
    fun `旧版 v1 进度文件仍能无损读入`() {
        val legacy = JSONObject(
            """{"v":1,"p":{"MC1-0001":{"s":2,"l":10,"n":20,"c":3,"w":1,"x":0}},"h":[{"lv":"A","m":"EXAM","s":33,"t":40,"p":1,"a":12345}]}"""
        )
        val decoded = ExamProgressCodec.decode(legacy)
        assertEquals(2, decoded.progress["MC1-0001"]?.stage)
        assertEquals(1, decoded.history.size)
        assertEquals("12345-A-EXAM-33", decoded.history[0].id)
        assertEquals(0L, decoded.history[0].elapsedMillis)
        assertEquals(emptyList<String>(), decoded.history[0].wrongIds)
        assertTrue(decoded.practiceCursor.isEmpty())
    }

    // ---- 账号云同步合并 ----

    @Test
    fun `云同步按最后作答时间合并且收敛`() {
        val older = QuestionProgress("Q1", stage = 2, lastAt = 100L, nextAt = 200L, correctCount = 3)
        val newer = QuestionProgress("Q1", stage = 4, lastAt = 900L, nextAt = 1_000L, correctCount = 7)
        val onlyRemote = QuestionProgress("Q2", stage = 1, lastAt = 500L, nextAt = 600L, correctCount = 2)
        val record = ExamRecord("A", ExamMode.EXAM.name, 31, 40, true, 1_000L)
        val another = ExamRecord("B", ExamMode.EXAM.name, 46, 60, true, 2_000L)
        val local = ExamProgressData(
            progress = mapOf("Q1" to older),
            history = listOf(record),
            practiceCursor = mapOf("A" to 37)
        )
        val remote = ExamProgressData(
            progress = mapOf("Q1" to newer, "Q2" to onlyRemote),
            history = listOf(another),
            practiceCursor = mapOf("A" to 12, "B" to 5)
        )
        val merged = ExamProgressMerge.merge(local, remote)
        assertEquals(newer, merged.progress["Q1"])
        assertEquals(onlyRemote, merged.progress["Q2"])
        assertEquals(setOf(record.id, another.id), merged.history.map { it.id }.toSet())
        assertEquals(another.id, merged.history.first().id) // 时间倒序
        assertEquals(37, merged.practiceCursor["A"])
        assertEquals(5, merged.practiceCursor["B"])
        assertTrue(ExamProgressMerge.converged(local, remote))
    }

    @Test
    fun `历史记录按稳定 id 去重并裁剪到上限`() {
        val many = (1..250).map { index ->
            ExamRecord("A", ExamMode.EXAM.name, 30, 40, true, index.toLong(), id = "r$index")
        }
        val merged = ExamProgressMerge.merge(
            ExamProgressData(history = many),
            ExamProgressData(history = many.reversed())
        )
        assertEquals(ExamProgressMerge.MAX_HISTORY, merged.history.size)
        assertEquals(merged.history.size, merged.history.distinctBy { it.id }.size)
        assertEquals(250L, merged.history.first().at)
        assertEquals(200, ExamProgressMerge.MAX_HISTORY)
    }

    // ---- 考试历史统计 ----

    @Test
    fun `历史统计给出合格律平均分与正确率明细`() {
        val records = listOf(
            ExamRecord(
                "A", ExamMode.EXAM.name, 30, 40, true, 1_000L,
                elapsedMillis = 60_000L,
                singleCorrect = 25, singleTotal = 32, multiCorrect = 5, multiTotal = 8
            ),
            ExamRecord(
                "B", ExamMode.EXAM.name, 40, 60, false, 2_000L,
                elapsedMillis = 120_000L,
                singleCorrect = 30, singleTotal = 45, multiCorrect = 10, multiTotal = 15
            ),
            ExamRecord("A", ExamMode.PRACTICE.name, 18, 20, false, 3_000L)
        )
        val stats = examHistoryStats(records)
        assertEquals(2, stats.count)
        assertEquals(1, stats.passed)
        assertEquals(0.5f, stats.passRate, 0.0001f)
        assertEquals(35f, stats.averageScore, 0.0001f)
        assertEquals(40, stats.bestScore)
        assertEquals(40, stats.latestScore)
        assertEquals(2, stats.trend.size)
        assertTrue(stats.trend[0].at < stats.trend[1].at)
        assertEquals(55f / 77f, stats.singleAccuracy, 0.0001f)
        assertEquals(15f / 23f, stats.multiAccuracy, 0.0001f)
        assertEquals(1.5f, stats.averageMinutes, 0.0001f)

        assertEquals(3, examHistoryStats(records, onlyExam = false).count)
        assertEquals(1, examHistoryStats(records, level = ExamLevel.B).count)
        assertTrue(examHistoryStats(emptyList()).trend.isEmpty())
    }

    // ---- 纸质组卷 PDF 的数据层 ----

    @Test
    fun `组卷 PDF 与 App 模拟考试同配额且答案表完整`() {
        val all = bank()
        val paper = ExamPaperBuilder.build(ExamLevel.B, all, seed = 9L, now = 1_700_000_000_000L)
        assertEquals(60, paper.questions.size)
        assertEquals(45, paper.singleCount)
        assertEquals(15, paper.multiCount)
        assertEquals(paper.questions.map { it.answer }, paper.answers)
        assertEquals(ExamEngine.buildPaper(ExamLevel.B, all, 9L), paper.questions)
        assertEquals(12, ExamPaperBuilder.answerLines(paper).size)
        assertTrue(ExamPaperBuilder.fileName(paper).endsWith(".pdf"))
        assertTrue(ExamPaperBuilder.fileName(paper).startsWith("HAM-Tools-B-"))
        assertTrue(paper.intro.any { it.contains("60 题") })
        assertTrue(paper.title.contains("B 类"))
    }

    @Test
    fun `中文按全角折行不会截断或超宽`() {
        assertEquals(
            listOf("一二三四", "五六七八", "九十"),
            ExamPaperBuilder.wrap("一二三四五六七八九十", 8)
        )
        assertEquals(2, ExamPaperBuilder.charUnits('中'))
        assertEquals(1, ExamPaperBuilder.charUnits('A'))
        assertEquals(listOf(""), ExamPaperBuilder.wrap("", 20))
        val long = "A".repeat(30)
        assertEquals(listOf("A".repeat(10), "A".repeat(10), "A".repeat(10)), ExamPaperBuilder.wrap(long, 10))
    }
}

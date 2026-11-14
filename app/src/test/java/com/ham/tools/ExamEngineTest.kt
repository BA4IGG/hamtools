package com.ham.tools

import com.ham.tools.data.exam.Ebbinghaus
import com.ham.tools.data.exam.ExamBankData
import com.ham.tools.data.exam.ExamBankParser
import com.ham.tools.data.exam.ExamEngine
import com.ham.tools.data.exam.ExamLevel
import com.ham.tools.data.exam.ExamMode
import com.ham.tools.data.exam.ExamOverview
import com.ham.tools.data.exam.ExamProgressCodec
import com.ham.tools.data.exam.ExamProgressData
import com.ham.tools.data.exam.ExamQuestion
import com.ham.tools.data.exam.ExamRecord
import com.ham.tools.data.exam.QuestionProgress
import com.ham.tools.data.exam.buildOverview
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExamEngineTest {

    private fun fakeQuestion(
        serial: Int,
        multi: Boolean = false,
        levels: String = "ABC",
        difficulty: Int = serial % 100
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

    private fun fakeBank(): List<ExamQuestion> =
        (1..200).map { fakeQuestion(it, multi = false, difficulty = it) } +
            (201..300).map { fakeQuestion(it, multi = true, difficulty = it - 200) }

    // ---- 官方出题规则 ----

    @Test
    fun `ABC 三类的题量时长与合格线符合官方要求`() {
        assertEquals(40, ExamLevel.A.totalCount)
        assertEquals(32, ExamLevel.A.singleCount)
        assertEquals(8, ExamLevel.A.multiCount)
        assertEquals(40, ExamLevel.A.minutes)
        assertEquals(30, ExamLevel.A.passScore)
        assertEquals(2_400_000L, ExamLevel.A.durationMillis)

        assertEquals(60, ExamLevel.B.totalCount)
        assertEquals(45, ExamLevel.B.singleCount)
        assertEquals(15, ExamLevel.B.multiCount)
        assertEquals(60, ExamLevel.B.minutes)
        assertEquals(45, ExamLevel.B.passScore)

        assertEquals(90, ExamLevel.C.totalCount)
        assertEquals(70, ExamLevel.C.singleCount)
        assertEquals(20, ExamLevel.C.multiCount)
        assertEquals(90, ExamLevel.C.minutes)
        assertEquals(70, ExamLevel.C.passScore)
        assertEquals(5_400_000L, ExamLevel.C.durationMillis)
    }

    @Test
    fun `组卷单选多选配额正确且整卷由易到难`() {
        val bank = fakeBank()
        for (level in ExamLevel.entries) {
            val paper = ExamEngine.buildPaper(level, bank, 2025L)
            assertEquals(level.totalCount, paper.size)
            assertEquals(level.singleCount, paper.count { !it.multi })
            assertEquals(level.multiCount, paper.count { it.multi })
            assertEquals(paper.size, paper.distinctBy { it.id }.size)
            assertEquals(paper.sortedBy { it.difficulty }, paper)
        }
    }

    @Test
    fun `不同随机种子给出不同试卷但结构不变`() {
        val bank = fakeBank()
        val first = ExamEngine.buildPaper(ExamLevel.C, bank, 1L)
        val second = ExamEngine.buildPaper(ExamLevel.C, bank, 2L)
        assertNotEquals(first.map { it.id }, second.map { it.id })
        assertEquals(first.size, second.size)
        assertEquals(first.count { it.multi }, second.count { it.multi })
    }

    @Test
    fun `题库按类别筛选且共有题不重复`() {
        // fakeBank 共 300 题（200 单选 + 100 多选），全部标记为 ABC 共有
        val bank = fakeBank() + fakeQuestion(999, levels = "A")
        assertEquals(301, bank.count { it.belongsTo(ExamLevel.A) })
        assertEquals(300, bank.count { it.belongsTo(ExamLevel.B) })
        assertEquals(300, bank.count { it.belongsTo(ExamLevel.C) })
        // 只属于 A 的题不会出现在 B 卷里；同一 [I] 题号也只会被抽一次
        val paper = ExamEngine.buildPaper(ExamLevel.B, bank, 3L)
        assertTrue(paper.all { it.belongsTo(ExamLevel.B) })
        assertEquals(paper.size, paper.distinctBy { it.id }.size)
    }

    // ---- 判分 ----

    @Test
    fun `多选题漏选少选多选一律算错`() {
        val q = fakeQuestion(1, multi = true) // 答案 AC
        assertTrue(ExamEngine.isCorrect(q, setOf(0, 2)))
        assertTrue(ExamEngine.isCorrect(q, setOf(2, 0))) // 选择顺序无关
        assertFalse(ExamEngine.isCorrect(q, setOf(0)))      // 漏选
        assertFalse(ExamEngine.isCorrect(q, setOf(2)))      // 漏选
        assertFalse(ExamEngine.isCorrect(q, setOf(0, 1, 2))) // 多选
        assertFalse(ExamEngine.isCorrect(q, setOf(0, 1)))    // 选错
        assertFalse(ExamEngine.isCorrect(q, emptySet()))     // 未作答
    }

    @Test
    fun `单选题按集合比较判分`() {
        val q = fakeQuestion(2) // 答案 B
        assertTrue(ExamEngine.isCorrect(q, setOf(1)))
        assertFalse(ExamEngine.isCorrect(q, setOf(0, 1)))
        assertFalse(ExamEngine.isCorrect(q, emptySet()))
    }

    @Test
    fun `成绩统计与合格线`() {
        val bank = fakeBank()
        val paper = ExamEngine.buildPaper(ExamLevel.A, bank, 7L)
        val allCorrect = paper.associate { it.id to it.answerIndices.toSet() }
        assertEquals(paper.size, ExamEngine.score(paper, allCorrect))
        assertTrue(ExamEngine.passed(ExamLevel.A, 30))
        assertFalse(ExamEngine.passed(ExamLevel.A, 29))
        assertTrue(ExamEngine.passed(ExamLevel.C, 70))
        assertFalse(ExamEngine.passed(ExamLevel.C, 69))
        assertEquals(0, ExamEngine.score(paper, emptyMap()))
    }

    // ---- 艾宾浩斯记忆曲线 ----

    @Test
    fun `答对逐级拉长复习间隔`() {
        val now = 1_700_000_000_000L
        var progress: QuestionProgress? = null
        val gaps = mutableListOf<Long>()
        repeat(Ebbinghaus.INTERVAL_MINUTES.size + 2) {
            progress = Ebbinghaus.applyAnswer("MC1-0001", progress, true, now)
            gaps += (progress!!.nextAt - now) / 60_000L
        }
        assertEquals(Ebbinghaus.INTERVAL_MINUTES.take(9).toList() + listOf(43_200L, 43_200L), gaps)
        assertEquals(Ebbinghaus.MAX_STAGE, progress!!.stage)
        assertEquals(11, progress!!.correctCount)
    }

    @Test
    fun `答错立即回到五分钟档并计入失误`() {
        val now = 1_700_000_000_000L
        var p = Ebbinghaus.applyAnswer("MC1-0001", null, true, now)
        p = Ebbinghaus.applyAnswer("MC1-0001", p, true, now)
        assertEquals(1, p.stage)
        val wrong = Ebbinghaus.applyAnswer("MC1-0001", p, false, now)
        assertEquals(0, wrong.stage)
        assertEquals(5L * 60_000L, wrong.nextAt - now)
        assertEquals(1, wrong.lapses)
        assertEquals(1, wrong.wrongCount)
        assertTrue(wrong.lastWrong)
        assertTrue(wrong.isDue(now + 5L * 60_000L))
        assertFalse(wrong.isDue(now + 1_000L))
    }

    @Test
    fun `复习队列优先到期题且组内由易到难`() {
        val now = 1_700_000_000_000L
        val easy = fakeQuestion(1, difficulty = 10)
        val hard = fakeQuestion(2, difficulty = 90)
        val fresh = fakeQuestion(3, difficulty = 5)
        val progress = mapOf(
            easy.id to QuestionProgress(easy.id, stage = 1, nextAt = now - 1_000L),
            hard.id to QuestionProgress(hard.id, stage = 0, nextAt = now - 10L)
        )
        val queue = Ebbinghaus.reviewQueue(listOf(fresh, hard, easy), progress, now, 3)
        // 到期题在前（越早到期越靠前），新题补在最后
        assertEquals(listOf(easy.id, hard.id, fresh.id), queue.map { it.id })
        assertEquals(listOf(easy.id), Ebbinghaus.reviewQueue(listOf(fresh, easy), progress, now, 1).map { it.id })
    }

    @Test
    fun `顺序练习优先未学新题且不会永远重复最易的二十题`() {
        val bank = fakeBank()
        val first = ExamEngine.practiceQueue(ExamLevel.A, bank, emptyMap(), 20)
        assertEquals(20, first.size)
        assertEquals(first.sortedBy { it.difficulty }, first)

        // 假设前 20 题已作答，下一轮应从未做过的第 21 题之后继续，而不是重复前 20 题
        val now = 1_700_000_000_000L
        val learned = first.associate {
            it.id to Ebbinghaus.applyAnswer(it.id, null, true, now)
        }
        val second = ExamEngine.practiceQueue(ExamLevel.A, bank, learned, 20)
        assertTrue(second.none { it.id in learned })
        assertTrue(second.first().difficulty >= first.last().difficulty)

        // 全部做完后按最久未复习的顺序循环
        val all = bank.associate { it.id to QuestionProgress(it.id, 1, 100L, 200L) }
        val repeat = ExamEngine.practiceQueue(ExamLevel.A, bank, all, 20)
        assertEquals(20, repeat.size)
        assertTrue(repeat.all { it.id in all })
    }

    @Test
    fun `错题重练按错误次数降序`() {
        val now = 1_700_000_000_000L
        val a = fakeQuestion(1, difficulty = 60)
        val b = fakeQuestion(2, difficulty = 20)
        val progress = mapOf(
            a.id to Ebbinghaus.applyAnswer(a.id, null, false, now),
            b.id to Ebbinghaus.applyAnswer(
                b.id,
                Ebbinghaus.applyAnswer(b.id, null, false, now),
                false,
                now
            )
        )
        val queue = ExamEngine.wrongQueue(ExamLevel.A, listOf(a, b), progress, 2)
        assertEquals(listOf(b.id, a.id), queue.map { it.id })
    }

    // ---- 进度编解码与统计 ----

    @Test
    fun `学习进度与历史记录可以完整往返`() {
        val data = ExamProgressData(
            progress = mapOf(
                "MC1-0001" to QuestionProgress("MC1-0001", 3, 100L, 200L, 5, 2, 2, true)
            ),
            history = listOf(
                ExamRecord("A", ExamMode.EXAM.name, 33, 40, true, 300L),
                ExamRecord("B", ExamMode.PRACTICE.name, 18, 20, false, 400L)
            )
        )
        val decoded = ExamProgressCodec.decode(ExamProgressCodec.encode(data))
        assertEquals(data.progress, decoded.progress)
        assertEquals(data.history, decoded.history)
    }

    @Test
    fun `概览统计正确`() {
        val now = 1_700_000_000_000L
        val data = ExamProgressData(
            progress = mapOf(
                "a" to QuestionProgress("a", 6, 0, now - 1, 10, 1, 1),
                "b" to QuestionProgress("b", 0, 0, now + 10_000, 1, 3, 2),
                "c" to QuestionProgress("c", -1, 0, 0, 0, 0, 0)
            ),
            history = listOf(ExamRecord("A", ExamMode.EXAM.name, 30, 40, true, now))
        )
        val overview = buildOverview(data, now)
        assertEquals(2, overview.learned)
        assertEquals(1, overview.due)
        assertEquals(2, overview.wrong)
        assertEquals(1, overview.mastered)
        assertEquals(15, overview.totalAnswered)
        assertEquals(11, overview.totalCorrect)
        assertEquals(1, overview.examPassed)
        assertEquals(11f / 15f, overview.accuracy)
        assertEquals(0, ExamOverview().accuracy.toInt())
    }

    @Test
    fun `距离下次复习的分钟数向上取整`() {
        assertEquals(0L, ExamEngine.minutesUntil(1_000L, 2_000L))
        assertEquals(1L, ExamEngine.minutesUntil(61_000L, 1_000L))
        assertEquals(2L, ExamEngine.minutesUntil(120_001L, 1_000L))
    }

    // ---- 真实离线题库 ----

    private fun realBank(): ExamBankData {
        val candidates = listOf(
            File("src/main/assets/exam/questions.json"),
            File("app/src/main/assets/exam/questions.json")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("找不到题库 assets：${candidates.map { it.absolutePath }}")
        return ExamBankParser.parse(JSONObject(file.readText(Charsets.UTF_8)))
    }

    @Test
    fun `真实题库按题号去重后为 1375 题且字段合法`() {
        val bank = realBank()
        assertEquals("v20250728", bank.version)
        assertEquals(1375, bank.questions.size)
        assertEquals(bank.total, bank.questions.size)
        assertEquals(bank.questions.size, bank.questions.distinctBy { it.id }.size)
        bank.questions.forEach { q ->
            assertTrue("${q.id} 选项数", q.options.size == 4)
            assertTrue("${q.id} 答案", q.answer.all { it in 'A'..'D' })
            assertTrue("${q.id} 难度", q.difficulty in 1..100)
            assertEquals("${q.id} 多选标记", q.answer.length > 1, q.multi)
            assertTrue("${q.id} 类别", q.levels.isNotEmpty())
        }
    }

    @Test
    fun `三类题库的组卷池都满足官方配额且 AB 共有题不重复抽`() {
        val bank = realBank()
        for (level in ExamLevel.entries) {
            val pool = bank.questions.filter { it.belongsTo(level) }
            assertTrue("$level 单选不足", pool.count { !it.multi } >= level.singleCount)
            assertTrue("$level 多选不足", pool.count { it.multi } >= level.multiCount)
            val paper = ExamEngine.buildPaper(level, bank.questions, 42L)
            assertEquals(level.totalCount, paper.size)
            assertEquals(paper.size, paper.distinctBy { it.id }.size)
            assertTrue("$level 不属于本类", paper.all { it.belongsTo(level) })
        }
        assertEquals(683, bank.questions.count { it.belongsTo(ExamLevel.A) })
        assertEquals(1143, bank.questions.count { it.belongsTo(ExamLevel.B) })
        assertEquals(1282, bank.questions.count { it.belongsTo(ExamLevel.C) })
    }

    @Test
    fun `附图题与附图页编号一一对应`() {
        val bank = realBank()
        val figures = bank.questions.filter { it.figure != null }
        assertEquals(53, figures.size)
        assertEquals(53, figures.distinctBy { it.sourceId }.size)
        assertEquals(3, bank.figurePages)
        assertEquals(180, bank.figureDpi)
        figures.forEach { q ->
            val f = q.figure!!
            assertTrue("${q.id} 页码越界", f.page in 0 until bank.figurePages)
            assertTrue("${q.id} 裁切框非法", f.x1 > f.x0 && f.y1 > f.y0)
        }
    }
}

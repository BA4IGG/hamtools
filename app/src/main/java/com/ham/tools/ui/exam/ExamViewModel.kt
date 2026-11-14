package com.ham.tools.ui.exam

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ham.tools.data.exam.Ebbinghaus
import com.ham.tools.data.exam.ExamBankData
import com.ham.tools.data.exam.ExamCloudApi
import com.ham.tools.data.exam.ExamCloudResult
import com.ham.tools.data.exam.ExamEngine
import com.ham.tools.data.exam.ExamLevel
import com.ham.tools.data.exam.ExamMode
import com.ham.tools.data.exam.ExamOverview
import com.ham.tools.data.exam.ExamPaperBuilder
import com.ham.tools.data.exam.ExamProgressData
import com.ham.tools.data.exam.ExamProgressMerge
import com.ham.tools.data.exam.ExamQuestion
import com.ham.tools.data.exam.ExamRepository
import com.ham.tools.data.exam.ExamSyncError
import com.ham.tools.data.exam.buildOverview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/** 一次答题会话。考试模式有倒计时；顺序练习额外记录全量题库里的断点。 */
data class ExamSession(
    val level: ExamLevel,
    val mode: ExamMode,
    val questions: List<ExamQuestion>,
    val answers: Map<String, Set<Int>> = emptyMap(),
    val index: Int = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val durationMillis: Long? = null,
    /** 顺序练习：这一轮从全量题库的第几题开始、该类共有多少题。其它模式为 0。 */
    val startIndex: Int = 0,
    val totalInLevel: Int = 0
) {
    val current: ExamQuestion get() = questions[index]
    val answeredCount: Int get() = answers.count { it.value.isNotEmpty() }
    /** 展示给用户的全局进度，例如「第 137 / 683 题」。 */
    val globalIndex: Int get() = if (totalInLevel > 0) startIndex + index + 1 else index + 1
    val globalTotal: Int get() = if (totalInLevel > 0) totalInLevel else questions.size
}

data class ExamResult(
    val level: ExamLevel,
    val mode: ExamMode,
    val questions: List<ExamQuestion>,
    val answers: Map<String, Set<Int>>,
    val score: Int,
    val passed: Boolean,
    val elapsedMillis: Long,
    val timedOut: Boolean = false
)

sealed interface ExamScreenState {
    data object Home : ExamScreenState
    data class Quiz(val session: ExamSession, val revealed: Set<String> = emptySet()) : ExamScreenState
    data class Result(val result: ExamResult) : ExamScreenState
    data object WrongBook : ExamScreenState
    data object Stats : ExamScreenState
    data object History : ExamScreenState
}

/** 账号同步的真实状态；界面照实显示，绝不把本机记录说成已上云。 */
enum class ExamSyncPhase {
    /** 未登录，或服务端还没确认可用：仅本机保存 */
    LOCAL_ONLY,
    CHECKING,
    SYNCING,
    SYNCED,
    NOT_DEPLOYED,
    FAILED
}

data class ExamSyncUi(
    val phase: ExamSyncPhase = ExamSyncPhase.LOCAL_ONLY,
    val signedIn: Boolean = false,
    val detail: String = "",
    val lastSyncedAt: Long = 0L
)

data class ExamUiState(
    val loading: Boolean = true,
    val bank: ExamBankData? = null,
    val level: ExamLevel = ExamLevel.A,
    val progressData: ExamProgressData = ExamProgressData(),
    val overview: ExamOverview = ExamOverview(),
    val screen: ExamScreenState = ExamScreenState.Home,
    val sync: ExamSyncUi = ExamSyncUi(),
    val paperGenerating: Boolean = false,
    /** 生成好的 PDF 暂存到用户通过系统文件选择器保存为止。 */
    val paperFileName: String? = null,
    val paperBytes: ByteArray? = null,
    val message: String? = null
)

class ExamViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ExamRepository(application)
    private val _state = MutableStateFlow(ExamUiState())
    val state: StateFlow<ExamUiState> = _state.asStateFlow()

    private var boundToken: String? = null
    private var cloudApi: ExamCloudApi? = null
    private var syncReady = false
    private var syncJob: Job? = null
    private var pushJob: Job? = null
    private var initialized = false

    init {
        viewModelScope.launch {
            val bank = runCatching { repo.bank() }.getOrNull()
            val data = withContext(Dispatchers.IO) { repo.readProgress() }
            initialized = true
            _state.value = _state.value.copy(
                loading = false,
                bank = bank,
                progressData = data,
                overview = buildOverview(data),
                message = if (bank == null) "本地题库读取失败，请重新安装 App" else null
            )
            // 登录态可能在题库加载完成前就到达，这里补一次同步
            if (cloudApi != null) startSync()
        }
    }

    // ------------------------------------------------------------------ 导航与类别

    fun selectLevel(level: ExamLevel) {
        _state.value = _state.value.copy(level = level)
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun goHome() {
        _state.value = _state.value.copy(screen = ExamScreenState.Home)
    }

    fun openWrongBook() {
        _state.value = _state.value.copy(screen = ExamScreenState.WrongBook)
    }

    fun openStats() {
        _state.value = _state.value.copy(
            screen = ExamScreenState.Stats,
            overview = buildOverview(_state.value.progressData)
        )
    }

    fun openHistory() {
        _state.value = _state.value.copy(screen = ExamScreenState.History)
    }

    /** 考试界面里的「确认退出」：丢弃本次会话，不记成绩、不写复习计划。 */
    fun abandonSession() {
        _state.value = _state.value.copy(screen = ExamScreenState.Home, message = "已退出，本次作答未计入成绩")
    }

    fun resetSequentialCursor(level: ExamLevel = _state.value.level) {
        val data = repo.resetCursor(_state.value.progressData, level)
        persist(data)
        _state.value = _state.value.copy(message = "${level.label}顺序练习已回到第 1 题")
    }

    // ------------------------------------------------------------------ 开始答题

    fun start(mode: ExamMode, level: ExamLevel = _state.value.level) {
        val bank = _state.value.bank?.questions ?: return
        val now = System.currentTimeMillis()
        val progress = _state.value.progressData.progress
        var startIndex = 0
        var totalInLevel = 0
        val questions = when (mode) {
            ExamMode.EXAM -> ExamEngine.buildPaper(level, bank, now)
            ExamMode.PRACTICE -> {
                val plan = ExamEngine.sequentialPractice(
                    level, bank, _state.value.progressData.practiceCursor[level.name] ?: 0
                )
                startIndex = plan.startIndex
                totalInLevel = plan.totalInLevel
                if (plan.restarted) {
                    _state.value = _state.value.copy(message = "上一轮已做完，本轮从第 1 题重新开始")
                }
                plan.questions
            }
            ExamMode.REVIEW -> Ebbinghaus.reviewQueue(
                bank.filter { it.belongsTo(level) }, progress, now, PRACTICE_LIMIT
            )
            // 错题重练不再截断成 20 题：错题本里的题一次全部重练
            ExamMode.WRONG -> ExamEngine.wrongQueue(level, bank, progress, Int.MAX_VALUE)
        }
        if (questions.isEmpty()) {
            _state.value = _state.value.copy(message = emptyHint(mode))
            return
        }
        _state.value = _state.value.copy(
            level = level,
            screen = ExamScreenState.Quiz(
                ExamSession(
                    level = level,
                    mode = mode,
                    questions = questions,
                    startedAt = now,
                    durationMillis = if (mode == ExamMode.EXAM) level.durationMillis else null,
                    startIndex = startIndex,
                    totalInLevel = totalInLevel
                )
            )
        )
    }

    private fun emptyHint(mode: ExamMode): String = when (mode) {
        ExamMode.REVIEW -> "当前没有到期需要复习的题目，先做一轮顺序练习吧"
        ExamMode.WRONG -> "错题本还是空的，先去练习或模拟考试"
        ExamMode.PRACTICE -> "该类别题库暂时不可用"
        else -> "题库暂时不可用"
    }

    fun toggleOption(optionIndex: Int) {
        val quiz = _state.value.screen as? ExamScreenState.Quiz ?: return
        if (quiz.session.current.id in quiz.revealed) return
        val question = quiz.session.current
        val current = quiz.session.answers[question.id].orEmpty()
        val next = if (!question.multi) {
            setOf(optionIndex)
        } else if (optionIndex in current) {
            current - optionIndex
        } else {
            current + optionIndex
        }
        val session = quiz.session.copy(
            answers = quiz.session.answers + (question.id to next)
        )
        _state.value = _state.value.copy(screen = quiz.copy(session = session))
        // 练习类的单选题点一下即判，多选题需要点「确认答案」
        if (session.mode != ExamMode.EXAM && !question.multi) {
            confirmCurrent()
        }
    }

    /** 即时反馈：判题、写入记忆曲线、推进顺序练习断点、显示答案。 */
    fun confirmCurrent() {
        val quiz = _state.value.screen as? ExamScreenState.Quiz ?: return
        if (quiz.session.mode == ExamMode.EXAM) return
        val question = quiz.session.current
        if (question.id in quiz.revealed) return
        val selected = quiz.session.answers[question.id].orEmpty()
        if (selected.isEmpty()) {
            _state.value = _state.value.copy(message = "请先选择答案")
            return
        }
        val correct = ExamEngine.isCorrect(question, selected)
        var data = repo.recordAnswer(_state.value.progressData, question.id, correct)
        data = advanceSequentialCursor(data, quiz.session)
        persist(data)
        _state.value = _state.value.copy(
            message = null,
            screen = quiz.copy(revealed = quiz.revealed + question.id)
        )
    }

    /** 顺序练习做一题就把断点往前推一格；做满一轮归零，下次从头开始。 */
    private fun advanceSequentialCursor(
        data: ExamProgressData,
        session: ExamSession
    ): ExamProgressData {
        if (session.mode != ExamMode.PRACTICE || session.totalInLevel <= 0) return data
        val reachedEnd = session.startIndex + session.index + 1 >= session.totalInLevel
        if (reachedEnd) return repo.advanceCursor(data, session.level, 0)
        val next = session.startIndex + session.index + 1
        val current = data.practiceCursor[session.level.name] ?: 0
        return repo.advanceCursor(data, session.level, max(current, next))
    }

    fun moveTo(index: Int) {
        val quiz = _state.value.screen as? ExamScreenState.Quiz ?: return
        if (index !in quiz.session.questions.indices) return
        _state.value = _state.value.copy(screen = quiz.copy(session = quiz.session.copy(index = index)))
    }

    fun next() {
        val quiz = _state.value.screen as? ExamScreenState.Quiz ?: return
        val session = quiz.session
        if (session.index == session.questions.lastIndex) {
            if (session.mode == ExamMode.EXAM) {
                _state.value = _state.value.copy(message = "已经是最后一题，点「交卷」查看成绩")
            } else {
                finish(false)
            }
            return
        }
        moveTo(session.index + 1)
    }

    fun previous() {
        val quiz = _state.value.screen as? ExamScreenState.Quiz ?: return
        if (quiz.session.index > 0) moveTo(quiz.session.index - 1)
    }

    /** UI 每秒调用一次：模拟考试到点自动交卷。 */
    fun tick(now: Long = System.currentTimeMillis()) {
        val quiz = _state.value.screen as? ExamScreenState.Quiz ?: return
        val duration = quiz.session.durationMillis ?: return
        if (now - quiz.session.startedAt >= duration) finish(timedOut = true)
    }

    fun submit() = finish(false)

    private fun finish(timedOut: Boolean) {
        val quiz = _state.value.screen as? ExamScreenState.Quiz ?: return
        val session = quiz.session
        val score = ExamEngine.score(session.questions, session.answers)
        val wrongQuestions = session.questions.filter {
            !ExamEngine.isCorrect(it, session.answers[it.id].orEmpty())
        }
        val singleTotal = session.questions.count { !it.multi }
        val multiTotal = session.questions.size - singleTotal
        val singleCorrect = session.questions.count {
            !it.multi && ExamEngine.isCorrect(it, session.answers[it.id].orEmpty())
        }
        val multiCorrect = session.questions.count {
            it.multi && ExamEngine.isCorrect(it, session.answers[it.id].orEmpty())
        }
        var data = _state.value.progressData
        // 模拟考试不即时反馈，交卷时统一把每道题的结果写入记忆曲线
        if (session.mode == ExamMode.EXAM) {
            val now = System.currentTimeMillis()
            session.questions.forEach { q ->
                val correct = ExamEngine.isCorrect(q, session.answers[q.id].orEmpty())
                data = repo.recordAnswer(data, q.id, correct, now)
            }
        }
        data = repo.appendRecord(
            current = data,
            level = session.level,
            mode = session.mode,
            score = score,
            total = session.questions.size,
            elapsedMillis = (System.currentTimeMillis() - session.startedAt).coerceAtLeast(0),
            singleCorrect = singleCorrect,
            singleTotal = singleTotal,
            multiCorrect = multiCorrect,
            multiTotal = multiTotal,
            wrongIds = wrongQuestions.map { it.id },
            timedOut = timedOut
        )
        persist(data)
        _state.value = _state.value.copy(
            message = null,
            screen = ExamScreenState.Result(
                ExamResult(
                    level = session.level,
                    mode = session.mode,
                    questions = session.questions,
                    answers = session.answers,
                    score = score,
                    passed = if (session.mode == ExamMode.EXAM) {
                        ExamEngine.passed(session.level, score)
                    } else {
                        score == session.questions.size
                    },
                    elapsedMillis = (System.currentTimeMillis() - session.startedAt).coerceAtLeast(0),
                    timedOut = timedOut
                )
            )
        )
    }

    /** 错题本里一键重练全部错题。 */
    fun retryWrong(level: ExamLevel) {
        val bank = _state.value.bank?.questions ?: return
        val questions = ExamEngine.wrongQueue(
            level, bank, _state.value.progressData.progress, Int.MAX_VALUE
        )
        if (questions.isEmpty()) {
            _state.value = _state.value.copy(message = "错题本还是空的")
            return
        }
        _state.value = _state.value.copy(
            level = level,
            message = null,
            screen = ExamScreenState.Quiz(
                ExamSession(
                    level = level,
                    mode = ExamMode.WRONG,
                    questions = questions,
                    startedAt = System.currentTimeMillis()
                )
            )
        )
    }

    // ------------------------------------------------------------------ 纸质组卷 PDF

    fun preparePaper(level: ExamLevel = _state.value.level) {
        val bank = _state.value.bank?.questions ?: return
        if (_state.value.paperGenerating) return
        _state.value = _state.value.copy(paperGenerating = true, message = null)
        val context = getApplication<Application>()
        viewModelScope.launch {
            val generated = runCatching {
                withContext(Dispatchers.IO) {
                    val paper = ExamPaperBuilder.build(level, bank)
                    ExamPaperBuilder.fileName(paper) to ExamPdf.render(context, paper, 180)
                }
            }
            generated.onSuccess { (name, bytes) ->
                _state.value = _state.value.copy(
                    paperGenerating = false,
                    paperFileName = name,
                    paperBytes = bytes,
                    message = "试卷已生成（${level.totalCount} 题 + 答案页），请选择保存位置"
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    paperGenerating = false,
                    message = "试卷生成失败：${it.message.orEmpty()}"
                )
            }
        }
    }

    /** 系统文件选择器已打开，清掉触发标志避免重复弹窗；字节留到写入回调再用。 */
    fun paperLaunched() {
        _state.value = _state.value.copy(paperFileName = null)
    }

    fun clearPaper(saved: Boolean) {
        _state.value = _state.value.copy(
            paperBytes = null,
            paperFileName = null,
            message = if (saved) "试卷已保存，可在「下载」中查看或打印" else "已取消保存"
        )
    }

    // ------------------------------------------------------------------ 账号云同步

    fun bindSession(token: String?) {
        if (token == boundToken) return
        boundToken = token
        syncJob?.cancel()
        pushJob?.cancel()
        syncReady = false
        cloudApi = token?.takeIf { it.isNotBlank() }?.let { ExamCloudApi(it) }
        val sync = if (cloudApi == null) {
            ExamSyncUi(ExamSyncPhase.LOCAL_ONLY, signedIn = false, detail = "未登录 · 记录仅保存在本机")
        } else {
            ExamSyncUi(ExamSyncPhase.CHECKING, signedIn = true, detail = "正在连接账号…")
        }
        _state.value = _state.value.copy(sync = sync)
        if (cloudApi != null && initialized) startSync()
    }

    /** 手动「立即同步」：重新探测 + 拉取 + 合并 + 回写。 */
    fun syncNow() {
        if (cloudApi == null) {
            _state.value = _state.value.copy(message = "请先登录账号")
            return
        }
        startSync()
    }

    private fun startSync() {
        val api = cloudApi ?: return
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            setSync(ExamSyncPhase.SYNCING, "正在与账号同步…")
            when (val ping = api.ping()) {
                is ExamCloudResult.Success -> Unit
                is ExamCloudResult.Failure -> {
                    val failure = ping as ExamCloudResult.Failure
                    syncReady = false
                    setSync(
                        if (failure.code == ExamSyncError.NOT_DEPLOYED) {
                            ExamSyncPhase.NOT_DEPLOYED
                        } else {
                            ExamSyncPhase.FAILED
                        },
                        syncDetail(failure.code, failure.message)
                    )
                    return@launch
                }
            }
            when (val fetched = api.fetch()) {
                is ExamCloudResult.Success -> {
                    val remote = fetched.value
                    // 拉取期间用户可能又答了几题，用此刻最新的本地状态再合并一次，
                    // 避免把同步过程中产生的新进度回退掉
                    val merged = ExamProgressMerge.merge(_state.value.progressData, remote)
                    // 先写本地，再回写云端；两步都失败也不影响本机练习
                    persist(merged, push = false)
                    when (val pushed = api.push(merged)) {
                        is ExamCloudResult.Success -> {
                            syncReady = true
                            setSync(
                                ExamSyncPhase.SYNCED,
                                "已与账号同步 · 复习进度 / 错题 / 考试历史",
                                System.currentTimeMillis()
                            )
                        }
                        is ExamCloudResult.Failure -> {
                            val failure = pushed as ExamCloudResult.Failure
                            syncReady = false
                            setSync(ExamSyncPhase.FAILED, syncDetail(failure.code, failure.message))
                        }
                    }
                }
                is ExamCloudResult.Failure -> {
                    val failure = fetched as ExamCloudResult.Failure
                    syncReady = false
                    setSync(ExamSyncPhase.FAILED, syncDetail(failure.code, failure.message))
                }
            }
        }
    }

    private fun syncDetail(code: ExamSyncError, message: String): String = when (code) {
        ExamSyncError.NOT_DEPLOYED -> "服务端尚未部署学习记录接口 · 当前仅保存在本机"
        ExamSyncError.NETWORK -> "网络不通，记录仍保存在本机，稍后可重试"
        ExamSyncError.AUTH -> "登录状态已失效，请重新登录"
        ExamSyncError.BAD_RESPONSE -> "服务器返回内容异常，记录仍保存在本机"
        ExamSyncError.SERVER -> message.ifBlank { "服务器暂时不可用，记录仍保存在本机" }
    }

    private fun setSync(phase: ExamSyncPhase, detail: String, at: Long = _state.value.sync.lastSyncedAt) {
        _state.value = _state.value.copy(
            sync = _state.value.sync.copy(phase = phase, detail = detail, lastSyncedAt = at)
        )
    }

    /** 本地记录变化后延迟 1.5 秒合并上传，避免每答一题都发一次请求。 */
    private fun schedulePush() {
        val api = cloudApi ?: return
        if (!syncReady) return
        pushJob?.cancel()
        pushJob = viewModelScope.launch {
            delay(1_500L)
            when (val result = api.push(_state.value.progressData)) {
                is ExamCloudResult.Success -> setSync(
                    ExamSyncPhase.SYNCED,
                    "已与账号同步 · 复习进度 / 错题 / 考试历史",
                    System.currentTimeMillis()
                )
                is ExamCloudResult.Failure -> {
                    val failure = result as ExamCloudResult.Failure
                    setSync(ExamSyncPhase.FAILED, syncDetail(failure.code, failure.message))
                }
            }
        }
    }

    fun clearRecords() {
        repo.clearProgress()
        persist(ExamProgressData(), push = false)
        cloudApi?.let { api ->
            viewModelScope.launch {
                when (val result = api.clear()) {
                    is ExamCloudResult.Success -> Unit
                    is ExamCloudResult.Failure -> {
                        _state.value = _state.value.copy(
                            message = "本机记录已清空，但云端删除失败，下次同步可能恢复旧记录"
                        )
                    }
                }
            }
        }
        _state.value = _state.value.copy(
            screen = ExamScreenState.Home,
            message = "学习记录已清空"
        )
    }

    // ------------------------------------------------------------------ 本地持久化

    private fun persist(data: ExamProgressData, push: Boolean = true) {
        _state.value = _state.value.copy(progressData = data, overview = buildOverview(data))
        viewModelScope.launch(Dispatchers.IO) { repo.saveProgress(_state.value.progressData) }
        if (push) schedulePush()
    }

    companion object {
        /** 记忆复习每轮的题量；顺序练习与错题重练已改为全量，不受此限制。 */
        const val PRACTICE_LIMIT = 20
    }
}

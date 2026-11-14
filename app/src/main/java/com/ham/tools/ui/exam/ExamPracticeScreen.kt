package com.ham.tools.ui.exam

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ham.tools.R
import com.ham.tools.data.exam.ExamLock
import com.ham.tools.data.exam.ExamMode
import com.ham.tools.data.remote.UserSession
import com.ham.tools.ui.components.HamTopBar
import com.ham.tools.ui.theme.AppShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「业余无线电考试练习」板块入口，挂在底部 Dock 正中央的顶层页。
 *
 * 题库与附图始终离线；复习进度、错题与考试历史默认存本机，
 * 登录账号后才可选同步到账号（服务端未部署时如实标注）。
 * 模拟考试一旦开始，本页会置位全局锁定：隐藏 Dock、禁用 Pager 滑动、
 * 接管系统返回键与侧滑手势，退出必须走确认对话框。
 */
@Composable
fun ExamPracticeScreen(
    modifier: Modifier = Modifier,
    session: UserSession? = null,
    onLoginClick: () -> Unit = {},
    viewModel: ExamViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showExitConfirm by remember { mutableStateOf(false) }

    val quiz = state.screen as? ExamScreenState.Quiz
    val examRunning = quiz?.session?.mode == ExamMode.EXAM

    LaunchedEffect(session?.token) { viewModel.bindSession(session?.token) }
    LaunchedEffect(examRunning) { ExamLock.set(examRunning) }
    DisposableEffect(Unit) { onDispose { ExamLock.set(false) } }

    // 考试期间接管系统返回键与 Android 10+ 的侧滑返回手势
    BackHandler(enabled = examRunning) { showExitConfirm = true }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(3_500L)
            viewModel.clearMessage()
        }
    }

    // 系统文件选择器（SAF）：不申请存储权限，用户自己决定把 PDF 存到哪儿
    val savePdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val bytes = viewModel.state.value.paperBytes
        if (uri == null || bytes == null) {
            viewModel.clearPaper(false)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                val written = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(bytes)
                        stream.flush()
                    } ?: error("无法写入所选位置")
                }.isSuccess
                if (written) {
                    // 部分文件提供者不支持持久授权，失败不影响文件已经写好
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
                written
            }
            viewModel.clearPaper(saved)
        }
    }

    LaunchedEffect(state.paperFileName) {
        val name = state.paperFileName ?: return@LaunchedEffect
        viewModel.paperLaunched()
        savePdf.launch(name)
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            // 五个 Dock 页共用同一条 64dp 顶栏。答题页自带「返回 + 倒计时」状态条，
            // 高度与顶栏一致，这里不再叠加第二条，避免挤占题干与附图的显示空间。
            if (state.screen !is ExamScreenState.Quiz) {
                HamTopBar(
                    title = examHeaderTitle(state.screen),
                    subtitle = stringResource(R.string.header_subtitle_exam)
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.message != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = AppShapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = state.message.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.clearMessage() }) { Text(text = "关闭") }
                        }
                    }
                }

                if (state.loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    when (val screen = state.screen) {
                        ExamScreenState.Home -> ExamHome(
                            state = state,
                            session = session,
                            onLevel = viewModel::selectLevel,
                            onStart = { mode, level -> viewModel.start(mode, level) },
                            onWrongBook = viewModel::openWrongBook,
                            onStats = viewModel::openStats,
                            onHistory = viewModel::openHistory,
                            onExportPdf = viewModel::preparePaper,
                            onLogin = onLoginClick,
                            onSync = viewModel::syncNow,
                            onResetCursor = viewModel::resetSequentialCursor,
                            onClear = viewModel::clearRecords,
                            modifier = Modifier.fillMaxSize()
                        )

                        is ExamScreenState.Quiz -> ExamQuiz(
                            state = state,
                            session = screen.session,
                            revealed = screen.revealed,
                            viewModel = viewModel,
                            onExitRequest = { showExitConfirm = true },
                            modifier = Modifier.fillMaxSize()
                        )

                        is ExamScreenState.Result -> ExamResultView(
                            result = screen.result,
                            onRetry = {
                                viewModel.goHome()
                                viewModel.start(screen.result.mode, screen.result.level)
                            },
                            onWrong = { viewModel.retryWrong(screen.result.level) },
                            onHistory = viewModel::openHistory,
                            onHome = viewModel::goHome,
                            modifier = Modifier.fillMaxSize()
                        )

                        ExamScreenState.WrongBook -> ExamWrongBook(
                            state = state,
                            onRetry = { viewModel.retryWrong(state.level) },
                            onHome = viewModel::goHome,
                            modifier = Modifier.fillMaxSize()
                        )

                        ExamScreenState.Stats -> ExamStats(
                            state = state,
                            onHome = viewModel::goHome,
                            onHistory = viewModel::openHistory,
                            modifier = Modifier.fillMaxSize()
                        )

                        ExamScreenState.History -> ExamHistoryView(
                            state = state,
                            onHome = viewModel::goHome,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            if (state.paperGenerating) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(shape = AppShapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(text = "正在排版试卷…", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = {
                Text(text = if (examRunning) "确定放弃本次模拟考试？" else "退出本次练习？")
            },
            text = {
                Text(
                    text = if (examRunning) {
                        "考试期间不能离开答题界面：底部导航、左右滑动、系统返回键与侧滑手势都已锁定。" +
                            "确认退出后本次作答不计入成绩，也不会写入复习计划；倒计时不会暂停。\n" +
                            "说明：Android 不允许应用拦截 Home 键与最近任务键；若切到后台，倒计时仍按真实时间继续，回来可接着答。"
                    } else {
                        "当前作答不会计入成绩与复习计划，已答过的题会保留在复习计划里。"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirm = false
                        viewModel.abandonSession()
                    }
                ) {
                    Text(
                        text = if (examRunning) "放弃考试" else "退出",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text(text = "继续答题") }
            }
        )
    }
}

/** 统一顶栏按子页切换标题；答题状态由 ExamQuiz 自带状态条承担，不会走到这里。 */
@Composable
private fun examHeaderTitle(screen: ExamScreenState): String = when (screen) {
    ExamScreenState.Home -> stringResource(R.string.dock_exam)
    is ExamScreenState.Quiz -> stringResource(R.string.dock_exam)
    is ExamScreenState.Result -> stringResource(R.string.exam_header_result)
    ExamScreenState.WrongBook -> stringResource(R.string.exam_header_wrong)
    ExamScreenState.Stats -> stringResource(R.string.exam_header_stats)
    ExamScreenState.History -> stringResource(R.string.exam_header_history)
}

package com.ham.tools.ui.exam

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ham.tools.data.exam.ExamEngine
import com.ham.tools.data.exam.ExamMode
import com.ham.tools.ui.theme.AppShapes
import kotlinx.coroutines.delay

@Composable
fun ExamQuiz(
    state: ExamUiState,
    session: ExamSession,
    revealed: Set<String>,
    viewModel: ExamViewModel,
    onExitRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showSubmit by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            now = System.currentTimeMillis()
            viewModel.tick(now)
        }
    }

    val question = session.current
    val selected = session.answers[question.id].orEmpty()
    val isRevealed = question.id in revealed
    val remainingMillis = session.durationMillis?.let { (it - (now - session.startedAt)).coerceAtLeast(0L) }
    val unanswered = session.questions.count { session.answers[it.id].isNullOrEmpty() }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            // 答题页不叠加统一顶栏，这条状态条就承担头部角色，高度与 64dp 顶栏对齐，
            // 从练习首页进入答题时顶部不会跳动。
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onExitRequest) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (session.mode == ExamMode.EXAM) "退出考试" else "退出练习"
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = "${session.level.label} · ${session.mode.label}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "第 ${session.globalIndex} / ${session.globalTotal} 题 · 由易到难" +
                        if (session.mode == ExamMode.EXAM) " · 界面已锁定" else " · 可随时退出续做",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (remainingMillis != null) {
                val totalSeconds = (remainingMillis + 999L) / 1_000L
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                val urgent = remainingMillis < 5 * 60_000L
                Surface(
                    shape = AppShapes.small,
                    color = if (urgent) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (urgent) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = "%02d:%02d".format(minutes, seconds),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp),
                            color = if (urgent) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }

        LinearProgressIndicator(
            progress = { (session.index + 1).toFloat() / session.questions.size },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ExamSectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = AppShapes.extraSmall,
                            color = if (question.multi) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        ) {
                            Text(
                                text = if (question.multi) "多选" else "单选",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = if (question.multi) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        }
                        Text(
                            text = "题号 ${question.id} · 难度 ${question.difficulty}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Text(
                        text = question.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    if (question.multi) {
                        Text(
                            text = "多选题：漏选、少选、多选均算错，必须与答案完全一致。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    val bitmap = rememberExamFigure(question.figure, state.bank?.figureDpi ?: 180)
                    if (bitmap != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            shape = AppShapes.small,
                            color = Color.White
                        ) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "题号 ${question.sourceId} 的附图",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 260.dp)
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }

            items(question.options.size) { index ->
                ExamOptionRow(
                    index = index,
                    text = question.options[index],
                    selected = index in selected,
                    multi = question.multi,
                    correct = if (isRevealed) index in question.answerIndices else null,
                    enabled = !isRevealed,
                    onClick = { viewModel.toggleOption(index) }
                )
            }

            if (isRevealed) {
                item {
                    val correct = ExamEngine.isCorrect(question, selected)
                    Surface(
                        shape = AppShapes.medium,
                        color = if (correct) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (correct) Icons.Filled.Check else Icons.Filled.Clear,
                                    contentDescription = null
                                )
                                Text(
                                    text = if (correct) "回答正确" else "回答错误",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                            Text(
                                text = "正确答案：${question.answer}　你的选择：${
                                    if (selected.isEmpty()) "未选" else selected.sorted().map { ('A' + it) }.joinToString("")
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Text(
                                text = "章节 ${question.chapter} · 原编号 ${question.sourceId} · 已计入艾宾浩斯复习计划",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (session.mode == ExamMode.EXAM) {
                            "已作答 ${session.questions.size - unanswered} / ${session.questions.size}，交卷后统一判分"
                        } else {
                            "练习模式即时判题，答题结果会更新复习计划"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { viewModel.previous() },
                    enabled = session.index > 0,
                    shape = AppShapes.small
                ) { Text(text = "上一题") }

                if (session.mode == ExamMode.EXAM) {
                    Button(
                        onClick = { showSubmit = true },
                        modifier = Modifier.weight(1f),
                        shape = AppShapes.small
                    ) { Text(text = "交卷") }
                } else if (!isRevealed && question.multi) {
                    Button(
                        onClick = { viewModel.confirmCurrent() },
                        modifier = Modifier.weight(1f),
                        shape = AppShapes.small,
                        enabled = selected.isNotEmpty()
                    ) { Text(text = "确认答案") }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                FilledTonalButton(
                    onClick = { viewModel.next() },
                    shape = AppShapes.small
                ) {
                    Text(text = if (session.index == session.questions.lastIndex) "完成" else "下一题")
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(start = 2.dp)
                    )
                }
            }
        }
    }

    if (showSubmit) {
        AlertDialog(
            onDismissRequest = { showSubmit = false },
            title = { Text(text = "确认交卷？") },
            text = {
                Text(
                    text = if (unanswered > 0) {
                        "还有 $unanswered 题未作答，交卷后不能修改，这些题将按错误处理。"
                    } else {
                        "全部题目已作答，交卷后统一判分。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showSubmit = false; viewModel.submit() }) { Text(text = "交卷") }
            },
            dismissButton = {
                TextButton(onClick = { showSubmit = false }) { Text(text = "再检查一下") }
            }
        )
    }
}

@Composable
private fun ExamOptionRow(
    index: Int,
    text: String,
    selected: Boolean,
    multi: Boolean,
    correct: Boolean?,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val container = when {
        correct == true -> MaterialTheme.colorScheme.tertiaryContainer
        correct == false && selected -> MaterialTheme.colorScheme.errorContainer
        selected -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = if (multi) AppShapes.small else AppShapes.medium,
        color = container,
        onClick = onClick,
        enabled = enabled
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(if (multi) AppShapes.extraSmall else CircleShape)
                    .background(
                        when {
                            correct == true -> MaterialTheme.colorScheme.tertiary
                            selected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected || correct == true) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        text = ('A' + index).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = "${('A' + index)}. $text",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (correct == true) {
                    Text(
                        text = "正确选项",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

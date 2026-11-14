package com.ham.tools.ui.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ham.tools.data.exam.Ebbinghaus
import com.ham.tools.data.exam.ExamLevel
import com.ham.tools.data.exam.ExamMode
import com.ham.tools.data.remote.UserSession
import com.ham.tools.ui.theme.AppShapes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExamHome(
    state: ExamUiState,
    onLevel: (ExamLevel) -> Unit,
    onStart: (ExamMode, ExamLevel) -> Unit,
    onWrongBook: () -> Unit,
    onStats: () -> Unit,
    onHistory: () -> Unit,
    onExportPdf: (ExamLevel) -> Unit,
    onLogin: () -> Unit,
    onSync: () -> Unit,
    onResetCursor: (ExamLevel) -> Unit,
    onClear: () -> Unit,
    session: UserSession? = null,
    modifier: Modifier = Modifier
) {
    var showClear by remember { mutableStateOf(false) }
    val level = state.level
    val bank = state.bank
    val poolCount = bank?.questions?.count { it.belongsTo(level) } ?: 0
    val cursor = (state.progressData.practiceCursor[level.name] ?: 0).coerceIn(0, poolCount)
    val examCount = state.progressData.history.count { it.mode == ExamMode.EXAM.name }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ExamSectionCard(title = "选择报考类别", subtitle = "题库按官方 [I] 题号去重，A/B 共有题不会重复计算") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExamLevel.entries.forEach { item ->
                        val selected = item == level
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = AppShapes.medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            onClick = { onLevel(item) }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 14.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = "${item.totalCount} 题 / ${item.minutes} 分",
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Text(
                                    text = "合格 ${item.passScore}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "${level.label}题库共 $poolCount 题｜正式考试抽 ${level.singleCount} 单 + ${level.multiCount} 多｜多选题漏选、少选、多选均不得分",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }

        item {
            ExamSectionCard(title = "模拟考试", subtitle = "严格按正式考试配额组卷，题目由易到难，考试期间锁定界面") {
                Button(
                    onClick = { onStart(ExamMode.EXAM, level) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.medium,
                    enabled = !state.loading && bank != null
                ) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                    Text(text = "开始 ${level.label} 模拟考试", modifier = Modifier.padding(start = 8.dp))
                }
                Text(
                    text = "${level.singleCount} 道单选 + ${level.multiCount} 道多选｜${level.minutes} 分钟｜答对 ${level.passScore} 题合格\n" +
                        "考试期间隐藏底部导航、禁用左右滑动与系统返回；确需退出必须二次确认。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        item {
            ExamSectionCard(title = "纸质组卷 · PDF 导出", subtitle = "按同一套官方配额随机生成可打印试卷") {
                Button(
                    onClick = { onExportPdf(level) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.medium,
                    enabled = bank != null && !state.paperGenerating
                ) {
                    Icon(imageVector = Icons.Filled.Description, contentDescription = null)
                    Text(
                        text = "生成 ${level.label}试卷 PDF（${level.totalCount} 题）",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Text(
                    text = "包含题目、四个选项、原题附图，以及单独一页参考答案。" +
                        "通过系统文件选择器保存到「下载」等位置，可打印或分享；离线生成，不需要存储权限。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        item {
            ExamSectionCard(title = "艾宾浩斯记忆练习", subtitle = "答对后逐级拉长复习间隔，答错立即回到 5 分钟重新巩固") {
                val intervals = Ebbinghaus.INTERVAL_MINUTES.map { minutes ->
                    when {
                        minutes < 60 -> "${minutes} 分钟"
                        minutes < 1_440 -> "${minutes / 60} 小时"
                        else -> "${minutes / 1_440} 天"
                    }
                }
                Text(
                    text = intervals.joinToString(" → "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onStart(ExamMode.PRACTICE, level) },
                        modifier = Modifier.weight(1f),
                        shape = AppShapes.small,
                        enabled = bank != null
                    ) { Text(text = "顺序练习") }
                    FilledTonalButton(
                        onClick = { onStart(ExamMode.REVIEW, level) },
                        modifier = Modifier.weight(1f),
                        shape = AppShapes.small,
                        enabled = bank != null
                    ) { Text(text = "到期复习 ${state.overview.due}") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onStart(ExamMode.WRONG, level) },
                        modifier = Modifier.weight(1f),
                        shape = AppShapes.small,
                        enabled = bank != null
                    ) { Text(text = "错题重练 ${state.overview.wrong}") }
                    FilledTonalButton(
                        onClick = onWrongBook,
                        modifier = Modifier.weight(1f),
                        shape = AppShapes.small
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Quiz,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text(text = "错题本")
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = AppShapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "顺序练习进度：$cursor / $poolCount 题",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "从第 ${cursor + 1} 题开始，一路做到最后一题；中途退出会从断点继续。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (cursor > 0) {
                            TextButton(onClick = { onResetCursor(level) }) { Text(text = "从头开始") }
                        }
                    }
                }
                Text(
                    text = "顺序练习与错题重练均为全量题目（不再限制 20 题）；到期复习每轮 ${ExamViewModel.PRACTICE_LIMIT} 题。题目由易到难，练习与复习即时显示答案。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }

        item {
            ExamSectionCard(title = "账号同步（可选）", subtitle = "复习进度、错题本、顺序练习断点与考试历史") {
                if (session == null) {
                    Text(
                        text = "未登录：所有学习记录完整保存在本机，功能不受影响。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "登录账号后可把上述记录同步到账号，换设备或重装 App 时能继续接着练。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    TextButton(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                        Text(text = "登录账号以开启同步")
                    }
                } else {
                    val phase = state.sync.phase
                    val label = when (phase) {
                        ExamSyncPhase.LOCAL_ONLY -> "本机记录"
                        ExamSyncPhase.CHECKING -> "正在连接"
                        ExamSyncPhase.SYNCING -> "同步中"
                        ExamSyncPhase.SYNCED -> "已同步"
                        ExamSyncPhase.NOT_DEPLOYED -> "仅本机"
                        ExamSyncPhase.FAILED -> "未同步"
                    }
                    ExamInfoLine("当前账号", session.profile.callsign)
                    ExamInfoLine("同步状态", label)
                    if (state.sync.lastSyncedAt > 0L && phase == ExamSyncPhase.SYNCED) {
                        ExamInfoLine(
                            "最近同步",
                            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                                .format(Date(state.sync.lastSyncedAt))
                        )
                    }
                    Text(
                        text = state.sync.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (phase == ExamSyncPhase.FAILED || phase == ExamSyncPhase.NOT_DEPLOYED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    FilledTonalButton(
                        onClick = onSync,
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.small,
                        enabled = phase != ExamSyncPhase.CHECKING && phase != ExamSyncPhase.SYNCING
                    ) { Text(text = "立即同步") }
                }
                Text(
                    text = "同步只上传题号进度、复习数字与成绩摘要，不上传题目正文、你所选的答案或任何个人资料；" +
                        "服务端未部署或网络不可用时界面会如实标注，本机功能始终可用。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        item {
            ExamSectionCard(title = "学习概览") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExamStatChip("已学题", "${state.overview.learned}", Modifier.weight(1f))
                    ExamStatChip("待复习", "${state.overview.due}", Modifier.weight(1f), state.overview.due > 0)
                    ExamStatChip("错题", "${state.overview.wrong}", Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExamStatChip("已掌握", "${state.overview.mastered}", Modifier.weight(1f))
                    ExamStatChip(
                        "正确率",
                        "${(state.overview.accuracy * 100).toInt()}%",
                        Modifier.weight(1f)
                    )
                    ExamStatChip("考试合格", "${state.overview.examPassed} 次", Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onHistory,
                        modifier = Modifier.weight(1f),
                        shape = AppShapes.small
                    ) { Text(text = "考试历史 · $examCount") }
                    FilledTonalButton(
                        onClick = onStats,
                        modifier = Modifier.weight(1f),
                        shape = AppShapes.small
                    ) { Text(text = "学习统计") }
                }
                TextButton(
                    onClick = { showClear = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "清空学习记录",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        item {
            Text(
                text = "题库、附图与组卷都在本机，无需联网即可完成全部练习与考试。\n" +
                    "登录后的账号同步只是可选增强；未登录、服务端未部署或断网时，记录仍完整保存在本机。\n" +
                    "题目难度为按篇幅、题型与章节顺序的估算值，仅用于「由易到难」排序，不代表官方难度分级。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text(text = "清空全部学习记录？") },
            text = {
                Text(
                    text = "会删除复习进度、错题本、顺序练习断点与考试历史，此操作不可撤销。" +
                        if (session != null) "登录状态下也会一并删除该账号的云端记录。" else "题库本身不受影响。"
                )
            },
            confirmButton = {
                TextButton(onClick = { showClear = false; onClear() }) {
                    Text(text = "确认清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) { Text(text = "取消") }
            }
        )
    }
}

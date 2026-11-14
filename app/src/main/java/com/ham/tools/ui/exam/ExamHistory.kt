package com.ham.tools.ui.exam

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ham.tools.data.exam.ExamLevel
import com.ham.tools.data.exam.ExamMode
import com.ham.tools.data.exam.ExamRecord
import com.ham.tools.data.exam.examHistoryStats
import com.ham.tools.ui.theme.AppShapes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun dayTime(at: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(at))

private fun shortDay(at: Long): String =
    SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(at))

private fun modeLabel(mode: String): String = runCatching {
    ExamMode.valueOf(mode).label
}.getOrDefault("练习")

/**
 * 模拟考试历史：分数趋势折线图、最近成绩柱状图、关键指标与逐次详情。
 * 全部数据来自本机 exam_progress.json（登录时可与账号同步），不需要联网。
 */
@Composable
fun ExamHistoryView(
    state: ExamUiState,
    onHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    var levelFilter by remember { mutableStateOf<ExamLevel?>(null) }
    var onlyExam by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<ExamRecord?>(null) }

    val stats = remember(state.progressData.history, levelFilter, onlyExam) {
        examHistoryStats(state.progressData.history, levelFilter, onlyExam, limit = 20)
    }
    val records = remember(state.progressData.history, levelFilter, onlyExam) {
        state.progressData.history.filter { record ->
            (!onlyExam || record.mode == ExamMode.EXAM.name) &&
                (levelFilter == null || record.level == levelFilter!!.name)
        }.sortedWith(compareByDescending<ExamRecord> { it.at }.thenByDescending { it.id })
    }
    val passRatio = if (levelFilter != null) {
        levelFilter!!.passScore.toFloat() / levelFilter!!.totalCount
    } else {
        0.75f
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "每次交卷自动记录成绩、用时与错题题号；图表按得分率绘制，A/B/C 可放在同一张图上比较。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            ExamSectionCard(title = "筛选") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf<ExamLevel?>(null, ExamLevel.A, ExamLevel.B, ExamLevel.C).forEach { level ->
                        val chosen = levelFilter == level
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = AppShapes.small,
                            color = if (chosen) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            onClick = { levelFilter = level }
                        ) {
                            Text(
                                text = level?.name ?: "全部",
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = if (chosen) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
                TextButton(onClick = { onlyExam = !onlyExam }) {
                    Text(text = if (onlyExam) "仅模拟考试（点此切换为全部练习）" else "全部练习记录（点此只看模拟考试）")
                }
            }
        }

        item {
            ExamSectionCard(title = "关键指标", subtitle = "基于当前筛选的 ${stats.count} 次记录") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExamStatChip("合格率", "${(stats.passRate * 100).toInt()}%", Modifier.weight(1f))
                    ExamStatChip("平均得分", "%.1f".format(stats.averageScore), Modifier.weight(1f))
                    ExamStatChip("最高得分", "${stats.bestScore}", Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExamStatChip("最近得分", "${stats.latestScore}", Modifier.weight(1f))
                    ExamStatChip("平均得分率", "${(stats.averageRatio * 100).toInt()}%", Modifier.weight(1f))
                    ExamStatChip(
                        "平均用时",
                        if (stats.averageElapsedMillis > 0L) "%.1f 分".format(stats.averageMinutes) else "—",
                        Modifier.weight(1f)
                    )
                }
                ExamInfoLine("单选正确率", "${(stats.singleAccuracy * 100).toInt()}%（${stats.singleCorrect}/${stats.singleTotal}）")
                ExamInfoLine("多选正确率", "${(stats.multiAccuracy * 100).toInt()}%（${stats.multiCorrect}/${stats.multiTotal}）")
            }
        }

        item {
            ExamSectionCard(
                title = "分数趋势",
                subtitle = "按时间正序的最近 ${stats.trend.size} 次 · 虚线为合格线"
            ) {
                if (stats.trend.isEmpty()) {
                    Text(
                        text = "当前筛选下还没有记录，先去做一次模拟考试吧。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ExamTrendChart(records = stats.trend, passRatio = passRatio)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = shortDay(stats.trend.first().at),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "合格线 ${(passRatio * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = shortDay(stats.trend.last().at),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            ExamSectionCard(title = "最近成绩", subtitle = "柱高为得分率，柱上数字为答对题数") {
                if (records.isEmpty()) {
                    Text(
                        text = "暂无成绩记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ExamBarChart(records = records.take(10).reversed())
                }
            }
        }

        item {
            Text(
                text = "逐次记录（${records.size}）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (records.isEmpty()) {
            item {
                Text(
                    text = "还没有可查看的历史成绩。",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                )
            }
        } else {
            // 旧版记录推导出的 id 理论上可能碰撞，键里带上序号保证 LazyColumn 稳定
            itemsIndexed(records, key = { index, record -> "${record.id}#$index" }) { _, record ->
                ExamHistoryRow(record = record, onClick = { selected = record })
            }
        }

        item {
            FilledTonalButton(
                onClick = onHome,
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.small
            ) { Text(text = "返回练习首页") }
        }
    }

    selected?.let { record ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(text = "${record.level} 类 · ${modeLabel(record.mode)}") },
            text = {
                Column {
                    Text(
                        text = "${record.score} / ${record.total} 题 · ${if (record.passed) "合格" else "未合格"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "时间：${dayTime(record.at)}")
                    Text(text = "用时：${formatElapsed(record.elapsedMillis)}")
                    Text(text = "单选：${record.singleCorrect} / ${record.singleTotal}")
                    Text(text = "多选：${record.multiCorrect} / ${record.multiTotal}")
                    Text(text = "错题：${record.wrongIds.size} 题")
                    if (record.timedOut) Text(text = "考试时间到，系统自动交卷")
                    if (record.wrongIds.isNotEmpty()) {
                        Text(
                            text = "错题题号：${record.wrongIds.take(20).joinToString("、")}" +
                                if (record.wrongIds.size > 20) " 等" else "",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    Text(
                        text = "只保存成绩摘要与错题题号，不保存你所选的答案。",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null }) { Text(text = "关闭") }
            }
        )
    }
}

private fun formatElapsed(millis: Long): String {
    if (millis <= 0L) return "—"
    val seconds = millis / 1_000L
    return "%d 分 %02d 秒".format(seconds / 60, seconds % 60)
}

@Composable
private fun ExamTrendChart(records: List<ExamRecord>, passRatio: Float) {
    val primary = MaterialTheme.colorScheme.primary
    val passColor = MaterialTheme.colorScheme.tertiary
    val failColor = MaterialTheme.colorScheme.error
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerLow

    Row(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(end = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "100%", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
            Text(text = "50%", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
            Text(text = "0%", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
        }
        Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val padTop = 10.dp.toPx()
            val padBottom = 10.dp.toPx()
            val chartHeight = size.height - padTop - padBottom
            val chartWidth = size.width

            for (line in 0..4) {
                val y = padTop + chartHeight * line / 4f
                drawLine(
                    color = grid,
                    start = Offset(0f, y),
                    end = Offset(chartWidth, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            val passY = padTop + chartHeight * (1f - passRatio.coerceIn(0f, 1f))
            drawLine(
                color = passColor,
                start = Offset(0f, passY),
                end = Offset(chartWidth, passY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(8.dp.toPx(), 6.dp.toPx())
                )
            )
            val points = records.mapIndexed { index, record ->
                val x = if (records.size == 1) {
                    chartWidth / 2f
                } else {
                    chartWidth * index / (records.size - 1)
                }
                val y = padTop + chartHeight * (1f - record.ratio.coerceIn(0f, 1f))
                Offset(x, y)
            }
            for (index in 1 until points.size) {
                drawLine(
                    color = primary,
                    start = points[index - 1],
                    end = points[index],
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            points.forEachIndexed { index, point ->
                drawCircle(
                    color = if (records[index].passed) passColor else failColor,
                    radius = 4.5.dp.toPx(),
                    center = point
                )
                drawCircle(color = surface, radius = 1.6.dp.toPx(), center = point)
            }
        }
    }
}

@Composable
private fun ExamBarChart(records: List<ExamRecord>) {
    val bars = records.take(10)
    Row(
        modifier = Modifier.fillMaxWidth().height(172.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEach { record ->
            val color = if (record.passed) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.error
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = "${record.score}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp
                )
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .width(22.dp)
                        .height((108.dp * record.ratio.coerceIn(0.02f, 1f)).coerceAtLeast(4.dp))
                        .background(color, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                )
                Text(
                    text = shortDay(record.at),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ExamHistoryRow(record: ExamRecord, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = AppShapes.small,
                color = if (record.passed) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            ) {
                Text(
                    text = "${record.score}/${record.total}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = "${record.level} 类 · ${modeLabel(record.mode)}" +
                        if (record.timedOut) " · 自动交卷" else "",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${dayTime(record.at)} · 用时 ${formatElapsed(record.elapsedMillis)} · 错题 ${record.wrongIds.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (record.passed) "合格" else "未合格",
                style = MaterialTheme.typography.labelSmall,
                color = if (record.passed) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

package com.ham.tools.ui.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ham.tools.data.exam.Ebbinghaus
import com.ham.tools.data.exam.ExamEngine
import com.ham.tools.data.exam.ExamMode
import com.ham.tools.data.exam.ExamProgressMerge
import com.ham.tools.data.exam.ExamQuestion
import com.ham.tools.ui.theme.AppShapes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1_000L
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatTime(at: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(at))

@Composable
fun ExamResultView(
    result: ExamResult,
    onRetry: () -> Unit,
    onWrong: () -> Unit,
    onHistory: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wrongQuestions = result.questions.filter {
        !ExamEngine.isCorrect(it, result.answers[it.id].orEmpty())
    }
    val singleTotal = result.questions.count { !it.multi }
    val singleCorrect = result.questions.count {
        !it.multi && ExamEngine.isCorrect(it, result.answers[it.id].orEmpty())
    }
    val multiTotal = result.questions.size - singleTotal
    val multiCorrect = result.questions.count {
        it.multi && ExamEngine.isCorrect(it, result.answers[it.id].orEmpty())
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.large,
                color = if (result.passed) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (result.passed) Icons.Filled.Check else Icons.Filled.Clear,
                        contentDescription = null
                    )
                    Text(
                        text = "${result.score} / ${result.questions.size}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            result.mode != ExamMode.EXAM -> "本轮练习完成"
                            result.timedOut -> "考试时间到 · ${if (result.passed) "合格" else "未合格"}"
                            result.passed -> "恭喜，达到 ${result.level.passScore} 题合格线"
                            else -> "未达到 ${result.level.passScore} 题合格线"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "用时 ${formatDuration(result.elapsedMillis)}　·　${result.level.label} ${result.mode.label}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        item {
            ExamSectionCard(title = "成绩明细") {
                ExamInfoLine("单选题", "$singleCorrect / $singleTotal")
                ExamInfoLine("多选题", "$multiCorrect / $multiTotal")
                ExamInfoLine("错题", "${wrongQuestions.size} 题")
                if (result.mode == ExamMode.EXAM) {
                    ExamInfoLine("合格线", "答对 ${result.level.passScore} 题（限时 ${result.level.minutes} 分钟）")
                }
                Text(
                    text = "多选题必须与答案完全一致，漏选、少选、多选均不计分。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    shape = AppShapes.small
                ) {
                    Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                    Text(text = "再来一轮", modifier = Modifier.padding(start = 4.dp))
                }
                FilledTonalButton(
                    onClick = onWrong,
                    modifier = Modifier.weight(1f),
                    shape = AppShapes.small
                ) { Text(text = "重练错题") }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onHistory,
                    modifier = Modifier.weight(1f),
                    shape = AppShapes.small
                ) { Text(text = "历史成绩图表") }
                FilledTonalButton(
                    onClick = onHome,
                    modifier = Modifier.weight(1f),
                    shape = AppShapes.small
                ) { Text(text = "返回练习首页") }
            }
        }

        if (wrongQuestions.isNotEmpty()) {
            item {
                Text(
                    text = "本轮错题（${wrongQuestions.size}）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(wrongQuestions, key = { it.id }) { question ->
                ExamWrongCard(question = question, progress = null)
            }
        }
    }
}

@Composable
fun ExamWrongBook(
    state: ExamUiState,
    onRetry: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bank = state.bank?.questions.orEmpty()
    val wrong = bank.filter { (state.progressData.progress[it.id]?.wrongCount ?: 0) > 0 }
        .sortedWith(
            compareByDescending<ExamQuestion> {
                state.progressData.progress[it.id]?.wrongCount ?: 0
            }.thenBy { it.difficulty }
        )
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "共 ${wrong.size} 题曾答错。答对后会留在记忆曲线里继续复习，不再自动移出。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    shape = AppShapes.small,
                    enabled = wrong.isNotEmpty()
                ) { Text(text = "重练错题") }
                FilledTonalButton(
                    onClick = onHome,
                    modifier = Modifier.weight(1f),
                    shape = AppShapes.small
                ) { Text(text = "返回首页") }
            }
        }
        if (wrong.isEmpty()) {
            item {
                Text(
                    text = "还没有错题，先去做一轮练习或模拟考试吧。",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                )
            }
        } else {
            items(wrong, key = { it.id }) { question ->
                ExamWrongCard(
                    question = question,
                    progress = state.progressData.progress[question.id]
                )
            }
        }
    }
}

@Composable
private fun ExamWrongCard(
    question: ExamQuestion,
    progress: com.ham.tools.data.exam.QuestionProgress?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = question.id,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (progress != null) {
                    Text(
                        text = "答错 ${progress.wrongCount} 次",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            Text(
                text = question.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                text = "正确答案 ${question.answer}（${if (question.multi) "多选" else "单选"}）· ${question.levels} 类 · 章节 ${question.chapter}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
fun ExamStats(
    state: ExamUiState,
    onHome: () -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val data = state.progressData
    val stageCount = (0..Ebbinghaus.MAX_STAGE).associateWith { stage ->
        data.progress.values.count { it.stage == stage }
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ExamSectionCard(title = "总体") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ExamStatChip("已学题", "${state.overview.learned}", Modifier.weight(1f))
                    ExamStatChip("待复习", "${state.overview.due}", Modifier.weight(1f), state.overview.due > 0)
                    ExamStatChip("已掌握", "${state.overview.mastered}", Modifier.weight(1f))
                }
                ExamInfoLine("累计作答", "${state.overview.totalAnswered} 次")
                ExamInfoLine("累计答对", "${state.overview.totalCorrect} 次")
                ExamInfoLine("正确率", "${(state.overview.accuracy * 100).toInt()}%")
                ExamInfoLine("模拟考试合格", "${state.overview.examPassed} 次")
            }
        }
        item {
            ExamSectionCard(title = "记忆曲线分布", subtitle = "答对晋级，答错回到第 1 档重新巩固") {
                stageCount.forEach { (stage, count) ->
                    ExamInfoLine(
                        label = "第 ${stage + 1} 档 · ${formatInterval(Ebbinghaus.intervalMinutes(stage))} 后复习",
                        value = "$count 题"
                    )
                }
            }
        }
        item {
            ExamSectionCard(
                title = "最近记录",
                subtitle = "最多保留最近 ${ExamProgressMerge.MAX_HISTORY} 次，只存摘要与错题题号，不存逐题答案"
            ) {
                if (data.history.isEmpty()) {
                    Text(
                        text = "还没有考试或练习记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    data.history.take(12).forEach { record ->
                        val mode = runCatching { ExamMode.valueOf(record.mode) }
                            .getOrDefault(ExamMode.PRACTICE)
                        ExamInfoLine(
                            label = "${formatTime(record.at)} · ${record.level} 类 ${mode.label}",
                            value = "${record.score}/${record.total}${if (record.passed) " ✓" else ""}"
                        )
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onHistory,
                    modifier = Modifier.weight(1f),
                    shape = AppShapes.small
                ) { Text(text = "考试历史与图表") }
                FilledTonalButton(
                    onClick = onHome,
                    modifier = Modifier.weight(1f),
                    shape = AppShapes.small
                ) { Text(text = "返回首页") }
            }
        }
    }
}

private fun formatInterval(minutes: Long): String = when {
    minutes < 60 -> "${minutes} 分钟"
    minutes < 1_440 -> "${minutes / 60} 小时"
    else -> "${minutes / 1_440} 天"
}

package com.ham.tools.ui.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ham.tools.data.reference.MorseTable
import com.ham.tools.ui.components.ActionButton
import com.ham.tools.ui.components.InfoSurface
import com.ham.tools.ui.components.KeyValueRow
import com.ham.tools.ui.components.NoteCard
import com.ham.tools.ui.components.SectionLabel
import com.ham.tools.ui.components.SettingSlider
import com.ham.tools.ui.components.ToolScaffold
import com.ham.tools.ui.components.ToolScrollColumn
import com.ham.tools.ui.theme.AppShapes
import com.ham.tools.util.CwLesson
import com.ham.tools.util.MorsePlayer

/**
 * CW 听抄训练（科赫法）。
 *
 * 关键取舍：**速度始终用目标速度**，不放慢来迁就初学者；难度靠逐级增加字符数控制。
 * 一组码播放完才允许看答案，避免边看边听，保证训练的是听觉辨识而不是阅读。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CwTrainerScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var level by rememberSaveable { mutableStateOf(2) }
    var wpm by rememberSaveable { mutableStateOf(15) }
    var groupSize by rememberSaveable { mutableStateOf(5) }
    var group by rememberSaveable { mutableStateOf("") }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var playedCount by rememberSaveable { mutableStateOf(0) }

    val player = remember { MorsePlayer() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    fun playGroup(next: Boolean) {
        if (next || group.isEmpty()) {
            group = CwLesson.generateGroup(level, groupSize, System.nanoTime())
            revealed = false
            playedCount += 1
        }
        player.play(MorseTable.toSymbols(group, wpm))
    }

    ToolScaffold(title = "CW 听抄训练", onBack = onBack, modifier = modifier) { padding ->
        ToolScrollColumn(padding) {
            SectionLabel(text = "课程设置")
            SettingSlider(
                label = "科赫等级",
                valueLabel = "第 $level 级 / ${CwLesson.MAX_LEVEL}",
                value = level.toFloat(),
                valueRange = CwLesson.MIN_LEVEL.toFloat()..CwLesson.MAX_LEVEL.toFloat(),
                steps = CwLesson.MAX_LEVEL - CwLesson.MIN_LEVEL - 1,
                onValueChange = { level = it.toInt() }
            )
            Text(
                text = "本课新增字符：${CwLesson.newCharOf(level)}　已学字符集：${CwLesson.charsForLevel(level).joinToString("")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SettingSlider(
                label = "码速",
                valueLabel = "$wpm WPM",
                value = wpm.toFloat(),
                valueRange = 5f..40f,
                steps = 34,
                onValueChange = { wpm = it.toInt() }
            )
            SettingSlider(
                label = "每组字符数",
                valueLabel = "$groupSize 个",
                value = groupSize.toFloat(),
                valueRange = 3f..12f,
                steps = 8,
                onValueChange = { groupSize = it.toInt() }
            )

            SectionLabel(text = "听抄")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = when {
                        group.isEmpty() -> "点击下方按钮开始第一组"
                        revealed -> group
                        else -> "● ● ● ● ●"
                    },
                    modifier = Modifier.padding(vertical = 34.dp, horizontal = 16.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (revealed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionButton(
                    text = "下一组",
                    onClick = { playGroup(true) },
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    text = "重播",
                    onClick = { playGroup(false) },
                    modifier = Modifier.weight(1f),
                    enabled = group.isNotEmpty(),
                    tonal = true
                )
            }
            ActionButton(
                text = if (revealed) "隐藏答案" else "显示答案",
                onClick = { revealed = !revealed },
                modifier = Modifier.fillMaxWidth(),
                enabled = group.isNotEmpty(),
                tonal = true
            )

            InfoSurface(title = "本轮统计") {
                KeyValueRow(label = "已播放组数", value = "$playedCount 组")
                KeyValueRow(label = "当前时长", value = "${MorseTable.totalDurationMs(group, wpm) / 1000.0} 秒")
                KeyValueRow(label = "点长", value = "${CwLesson.unitMs(wpm)} ms")
            }

            NoteCard(
                text = "科赫法从 2 个字符起步、始终保持目标码速，逐课加入新字母，比「放慢速度」更容易建立整体听辨能力。建议每级能连续抄对 90% 再升下一级。"
            )
        }
    }
}

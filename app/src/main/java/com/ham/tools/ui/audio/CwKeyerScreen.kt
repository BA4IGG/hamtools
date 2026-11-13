package com.ham.tools.ui.audio

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ham.tools.data.reference.MorseTable
import com.ham.tools.ui.components.ActionButton
import com.ham.tools.ui.components.NoteCard
import com.ham.tools.ui.components.SectionLabel
import com.ham.tools.ui.components.SettingSlider
import com.ham.tools.ui.components.ToolScaffold
import com.ham.tools.ui.components.ToolScrollColumn
import com.ham.tools.ui.theme.AppShapes
import com.ham.tools.util.CwLesson
import com.ham.tools.util.MorsePlayer
import com.ham.tools.util.ToneEngine
import com.ham.tools.util.Waveform

/**
 * CW 电键：按住大按钮即发声，抬手即停，模拟真实手键。
 *
 * 与「摩尔斯对照」页的整段播放不同，这里走 ToneEngine 的 MODE_STREAM 持续音通路，
 * 按下/抬起几乎没有延迟；点划时长由 PARIS 标准的点长提示，供练习时对照节奏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CwKeyerScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var wpm by rememberSaveable { mutableStateOf(15) }
    var toneHz by rememberSaveable { mutableStateOf(600) }
    var autoText by rememberSaveable { mutableStateOf("CQ CQ DE BA4IGG") }
    var keyDown by remember { mutableStateOf(false) }

    val engine = remember { ToneEngine() }
    val player = remember { MorsePlayer() }
    DisposableEffect(Unit) {
        onDispose {
            engine.release()
            player.release()
        }
    }

    ToolScaffold(title = "CW 电键", onBack = onBack, modifier = modifier) { padding ->
        ToolScrollColumn(padding) {
            SectionLabel(text = "键控参数")
            SettingSlider(
                label = "速度",
                valueLabel = "$wpm WPM",
                value = wpm.toFloat(),
                valueRange = 5f..40f,
                steps = 34,
                onValueChange = { wpm = it.toInt() }
            )
            SettingSlider(
                label = "侧音音调",
                valueLabel = "$toneHz Hz",
                value = toneHz.toFloat(),
                valueRange = 300f..1200f,
                onValueChange = { toneHz = it.toInt() }
            )
            Text(
                text = "点 ${CwLesson.unitMs(wpm)} ms · 划 ${CwLesson.unitMs(wpm) * 3} ms · 字符间隔 ${CwLesson.unitMs(wpm) * 3} ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionLabel(text = "手动电键")
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                keyDown = true
                                engine.start(toneHz.toDouble(), Waveform.SINE)
                                tryAwaitRelease()
                                engine.stop()
                                keyDown = false
                            }
                        )
                    },
                shape = AppShapes.large,
                color = if (keyDown) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (keyDown) "● 正在发报" else "按住此处发报",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (keyDown) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }
            }
            Text(
                text = "按住时持续发声，抬手停止；侧音只从本机扬声器输出，不发射射频信号。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionLabel(text = "自动拍发")
            OutlinedTextField(
                value = autoText,
                onValueChange = { autoText = it.uppercase() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "待发文本") },
                singleLine = true,
                shape = AppShapes.small
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionButton(
                    text = "播放",
                    onClick = {
                        player.play(MorseTable.toSymbols(autoText, wpm))
                    },
                    modifier = Modifier.weight(1f),
                    enabled = autoText.isNotBlank()
                )
                ActionButton(
                    text = "停止",
                    onClick = { player.stop() },
                    modifier = Modifier.weight(1f),
                    tonal = true
                )
            }

            NoteCard(
                text = "电键练习只产生音频侧音，不涉及无线电发射。实际发报请遵守所在国家/地区的法规与执照要求，并接入合规的发信设备。"
            )
        }
    }
}

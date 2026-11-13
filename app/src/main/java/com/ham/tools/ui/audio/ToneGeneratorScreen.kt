package com.ham.tools.ui.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ham.tools.ui.components.ActionButton
import com.ham.tools.ui.components.InfoSurface
import com.ham.tools.ui.components.KeyValueRow
import com.ham.tools.ui.components.NoteCard
import com.ham.tools.ui.components.SectionLabel
import com.ham.tools.ui.components.SettingSlider
import com.ham.tools.ui.components.ToolScaffold
import com.ham.tools.ui.components.ToolScrollColumn
import com.ham.tools.ui.components.StatusBanner
import com.ham.tools.util.AudioSignal
import com.ham.tools.util.ToneEngine
import com.ham.tools.util.Waveform

/**
 * 音调发生器：单音持续输出 + 线性扫频。
 *
 * 持续音用 MODE_STREAM，拖动频率滑块时通过 ToneEngine.update 实时改参数，
 * 不重建 AudioTrack，因此滑动过程中不会有断续或重启的咔哒声。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToneGeneratorScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var freq by rememberSaveable { mutableStateOf(600f) }
    var waveIndex by rememberSaveable { mutableStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var sweepFrom by rememberSaveable { mutableStateOf(300f) }
    var sweepTo by rememberSaveable { mutableStateOf(3000f) }
    var sweepMs by rememberSaveable { mutableStateOf(2000f) }
    var status by rememberSaveable { mutableStateOf("待机") }

    val waves = Waveform.entries
    val engine = remember { ToneEngine() }
    DisposableEffect(Unit) { onDispose { engine.release() } }

    ToolScaffold(title = "音调发生器", onBack = onBack, modifier = modifier) { padding ->
        ToolScrollColumn(padding) {
            SectionLabel(text = "波形")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                waves.forEachIndexed { index, wave ->
                    if (index == waveIndex) {
                        Button(
                            onClick = { waveIndex = index },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = wave.label, style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        FilledTonalButton(
                            onClick = {
                                waveIndex = index
                                if (playing) engine.update(freq.toDouble(), wave)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = wave.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            SectionLabel(text = "单音")
            SettingSlider(
                label = "频率",
                valueLabel = "${freq.toInt()} Hz",
                value = freq,
                valueRange = 20f..5000f,
                onValueChange = {
                    freq = it
                    if (playing) engine.update(it.toDouble(), waves[waveIndex])
                }
            )
            ActionButton(
                text = if (playing) "停止输出" else "开始输出",
                onClick = {
                    if (playing) {
                        engine.stop()
                        playing = false
                        status = "已停止"
                    } else {
                        engine.start(freq.toDouble(), waves[waveIndex])
                        playing = true
                        status = "持续输出 ${waves[waveIndex].label} ${freq.toInt()} Hz"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                tonal = playing
            )

            SectionLabel(text = "扫频")
            SettingSlider(
                label = "起始频率",
                valueLabel = "${sweepFrom.toInt()} Hz",
                value = sweepFrom,
                valueRange = 20f..5000f,
                onValueChange = { sweepFrom = it }
            )
            SettingSlider(
                label = "结束频率",
                valueLabel = "${sweepTo.toInt()} Hz",
                value = sweepTo,
                valueRange = 20f..5000f,
                onValueChange = { sweepTo = it }
            )
            SettingSlider(
                label = "扫频时长",
                valueLabel = "${sweepMs.toInt() / 1000.0} 秒",
                value = sweepMs,
                valueRange = 500f..8000f,
                onValueChange = { sweepMs = it }
            )
            ActionButton(
                text = "播放扫频",
                onClick = {
                    if (playing) {
                        engine.stop()
                        playing = false
                    }
                    engine.play(
                        AudioSignal.sweep(
                            sweepFrom.toDouble(),
                            sweepTo.toDouble(),
                            sweepMs.toInt()
                        )
                    )
                    status = "扫频 ${sweepFrom.toInt()} → ${sweepTo.toInt()} Hz"
                },
                modifier = Modifier.fillMaxWidth(),
                tonal = true
            )

            StatusBanner(text = "状态：$status")
            InfoSurface(title = "输出参数") {
                KeyValueRow(label = "采样率", value = "${AudioSignal.SAMPLE_RATE} Hz / 16bit 单声道")
                KeyValueRow(label = "波形", value = waves[waveIndex].label)
                KeyValueRow(label = "渐入渐出", value = "5 ms，避免爆音")
            }

            NoteCard(
                text = "请用合适音量试听，长时间贴近耳机听高频单音可能损伤听力。扫频常用于检查音频链路与滤波器响应；本工具输出的是本机音频，不产生射频信号。"
            )
        }
    }
}

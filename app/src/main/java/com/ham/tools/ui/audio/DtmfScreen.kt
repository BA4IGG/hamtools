package com.ham.tools.ui.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.unit.dp
import com.ham.tools.ui.components.ActionButton
import com.ham.tools.ui.components.InfoSurface
import com.ham.tools.ui.components.KeyValueRow
import com.ham.tools.ui.components.NoteCard
import com.ham.tools.ui.components.SectionLabel
import com.ham.tools.ui.components.ToolScaffold
import com.ham.tools.ui.components.ToolScrollColumn
import com.ham.tools.ui.theme.AppShapes
import com.ham.tools.util.AudioSignal
import com.ham.tools.util.DtmfCodec
import com.ham.tools.util.ToneEngine

/**
 * DTMF 双音多频：生成、拨号串播放与本机解码自检。
 *
 * 解码走 Goertzel 算法，对「本机刚合成的 PCM」做识别，用来验证频率对和解码器；
 * 不给麦克风权限、不录音，因此完全离线且不触碰隐私数据。
 */
@Composable
fun DtmfScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var sequence by rememberSaveable { mutableStateOf("") }
    var decoded by rememberSaveable { mutableStateOf("") }
    var lastKeyInfo by rememberSaveable { mutableStateOf("—") }

    val engine = remember { ToneEngine() }
    DisposableEffect(Unit) { onDispose { engine.release() } }

    fun buildPcm(): ShortArray {
        val parts = mutableListOf<ShortArray>()
        for (key in sequence) {
            DtmfCodec.encode(key, 100)?.let {
                parts.add(it)
                parts.add(AudioSignal.silence(60))
            }
        }
        return AudioSignal.concat(parts)
    }

    ToolScaffold(title = "DTMF", onBack = onBack, modifier = modifier) { padding ->
        ToolScrollColumn(padding) {
            SectionLabel(text = "拨号盘")
            DtmfCodec.KEYS.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { key ->
                        FilledTonalButton(
                            onClick = {
                                sequence += key
                                DtmfCodec.encode(key, 100)?.let { engine.play(it) }
                                val (low, high) = DtmfCodec.freqsOf(key)!!
                                lastKeyInfo = "$key → ${low.toInt()} Hz + ${high.toInt()} Hz"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = key.toString(), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            InfoSurface(title = "当前按键") {
                KeyValueRow(label = "频率对", value = lastKeyInfo)
            }

            SectionLabel(text = "拨号串")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = sequence.ifEmpty { "（尚未输入）" },
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionButton(
                    text = "播放整串",
                    onClick = {
                        val pcm = buildPcm()
                        engine.play(pcm)
                        decoded = DtmfCodec.decodeSequence(pcm)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = sequence.isNotEmpty()
                )
                ActionButton(
                    text = "清空",
                    onClick = {
                        sequence = ""
                        decoded = ""
                        lastKeyInfo = "—"
                    },
                    modifier = Modifier.weight(1f),
                    tonal = true
                )
            }

            InfoSurface(title = "解码自检") {
                KeyValueRow(
                    label = "识别结果",
                    value = decoded.ifEmpty { "—" }
                )
                KeyValueRow(label = "与输入一致", value = if (decoded == sequence) "是" else "否")
            }

            NoteCard(
                text = "频率遵循 ITU-T Q.23：低群 697/770/852/941 Hz，高群 1209/1336/1477/1633 Hz。此处解码针对本机合成的 PCM 做自检，不申请麦克风权限、不采集外部声音；A-D 为专网扩展键。"
            )
        }
    }
}

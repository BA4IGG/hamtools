package com.ham.tools.ui.audio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ham.tools.data.audio.MicCapture
import com.ham.tools.ui.components.InfoSurface
import com.ham.tools.ui.components.KeyValueRow
import com.ham.tools.ui.components.SectionLabel
import com.ham.tools.ui.components.ToolScaffold
import com.ham.tools.ui.components.ToolScrollColumn
import com.ham.tools.util.Ft8Codec
import com.ham.tools.util.Ft8Decode
import com.ham.tools.util.ToneEngine
import com.ham.tools.util.WavIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FT8 编解码工具页。
 *
 * 编码、播放、保存 WAV、本机自测都不需要任何权限；只有「开始监听」需要麦克风。
 * 所有信号都在本机处理，界面明确区分「本机自测」与「实际接收」，不把自测结果伪装成通联。
 */
@Composable
fun Ft8Screen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val permission = rememberMicPermission()
    val scope = rememberCoroutineScope()
    val engine = remember { ToneEngine() }
    val mic = remember { MicCapture(context) }
    DisposableEffect(Unit) { onDispose { engine.release(); mic.release() } }

    var message by rememberSaveable { mutableStateOf("CQ BA4IGG OM89") }
    var baseHz by rememberSaveable { mutableStateOf("1500") }
    var status by rememberSaveable { mutableStateOf("尚未生成信号") }
    var results by remember { mutableStateOf(emptyList<Ft8Decode>()) }
    var busy by remember { mutableStateOf(false) }
    var pendingWav by remember { mutableStateOf<ByteArray?>(null) }

    val level by mic.level.collectAsState()
    val seconds by mic.seconds.collectAsState()
    val recording by mic.recording.collectAsState()
    val micError by mic.error.collectAsState()

    fun buildPcm(): ShortArray? {
        val hz = baseHz.toDoubleOrNull()?.coerceIn(500.0, 3000.0) ?: Ft8Codec.BASE_HZ
        val pcm = Ft8Codec.encode(message, hz, Ft8Codec.SAMPLE_RATE)
        if (pcm == null) {
            status = "消息格式不支持：可用「CQ 呼号 网格」「呼号 呼号 报告」或 13 字符内自由文本"
        }
        return pcm
    }

    fun decode(data: ShortArray, rate: Int) {
        if (busy) return
        busy = true
        status = "正在解码 ${"%.1f".format(data.size.toDouble() / rate)} 秒音频…"
        scope.launch {
            val decoded = withContext(Dispatchers.Default) {
                runCatching { Ft8Codec.decode(data, rate) }.getOrDefault(emptyList())
            }
            results = decoded
            status = if (decoded.isEmpty()) {
                "未解出信号。请确认处在 15 秒时隙内、信号较强且频率在 200–3000 Hz；" +
                    "本机自测可用来验证解码器本身。"
            } else {
                "解出 ${decoded.size} 条消息（轻量化解码，无深搜）"
            }
            busy = false
        }
    }

    val saveWav = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri: Uri? ->
        val bytes = pendingWav
        if (uri != null && bytes != null) {
            status = if (writeBytesToUri(context, uri, bytes)) "WAV 已保存到所选位置" else "保存失败：无法写入所选位置"
        }
        pendingWav = null
    }

    val openWav = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val wav = runCatching {
            context.contentResolver.openInputStream(uri)?.use { WavIo.decode(it.readBytes()) }
        }.getOrNull()
        if (wav == null) {
            status = "导入失败：只支持 16bit PCM 单声道/立体声 WAV"
        } else {
            status = "已导入 WAV：${"%.1f".format(wav.pcm.size.toDouble() / wav.sampleRate)} 秒 / ${wav.sampleRate} Hz"
            decode(wav.pcm, wav.sampleRate)
        }
    }

    // 收满一个 15 秒时隙后自动停止并解码；中途点「停止并解码」也会走同一路径
    LaunchedEffect(recording) {
        if (recording) {
            while (mic.recording.value && mic.seconds.value < Ft8Codec.CAPTURE_SECONDS.toDouble()) {
                delay(200)
            }
            if (mic.recording.value) {
                val captured = mic.stop()
                decode(captured, MicCapture.SAMPLE_RATE)
            }
        }
    }

    ToolScaffold(title = "FT8 编解码", onBack = onBack, modifier = modifier) { padding ->
        ToolScrollColumn(padding) {
            SectionLabel(text = "消息编码")
            OutlinedTextField(
                value = message,
                onValueChange = { message = it.uppercase() },
                label = { Text(text = "消息文本") },
                supportingText = { Text(text = "例：CQ BA4IGG OM89 ／ BA4IGG K1ABC -07 ／ HELLO WORLD?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = baseHz,
                onValueChange = { baseHz = it.filter { ch -> ch.isDigit() } },
                label = { Text(text = "基带起始频率（Hz）") },
                supportingText = { Text(text = "8 个音调依次为 起始频率 + 0…7 × 6.25 Hz，默认 1500 Hz") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            CodecActionRow(
                primary = "生成并播放",
                onPrimary = {
                    val pcm = buildPcm()
                    if (pcm != null) {
                        engine.play(pcm)
                        status = "已生成 12.64 秒 FT8 音频（79 符号 / 6.25 Hz 间隔）并播放"
                    }
                },
                secondary = "保存 WAV",
                onSecondary = {
                    val pcm = buildPcm()
                    if (pcm != null) {
                        pendingWav = WavIo.encode(pcm, Ft8Codec.SAMPLE_RATE)
                        saveWav.launch("FT8_${System.currentTimeMillis()}.wav")
                    }
                }
            )
            InfoSurface(title = "编码状态") {
                KeyValueRow(label = "状态", value = status)
                KeyValueRow(label = "时长 / 采样率", value = "12.64 s / ${Ft8Codec.SAMPLE_RATE} Hz")
            }
            CodecActionRow(
                primary = "本机自测解码",
                onPrimary = {
                    val pcm = buildPcm()
                    if (pcm != null) {
                        val window = ShortArray(Ft8Codec.CAPTURE_SECONDS * Ft8Codec.SAMPLE_RATE)
                        pcm.copyInto(window)
                        decode(window, Ft8Codec.SAMPLE_RATE)
                    }
                },
                secondary = "导入 WAV 解码",
                onSecondary = { openWav.launch("audio/*") }
            )

            SectionLabel(text = "麦克风解码")
            MicPermissionCard(state = permission)
            LevelMeter(
                level = level,
                seconds = seconds,
                running = recording,
                targetSeconds = Ft8Codec.CAPTURE_SECONDS
            )
            if (micError != null) {
                Text(
                    text = micError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
            CodecActionRow(
                primary = if (recording) "停止并解码" else "开始监听 15 秒",
                onPrimary = {
                    if (recording) {
                        val captured = mic.stop()
                        decode(captured, MicCapture.SAMPLE_RATE)
                    } else {
                        mic.clearError()
                        if (!mic.start()) status = mic.error.value ?: "无法启动麦克风"
                    }
                },
                primaryEnabled = permission.granted && !busy,
                secondary = "清空结果",
                onSecondary = {
                    results = emptyList()
                    status = "结果已清空"
                },
                secondaryEnabled = results.isNotEmpty()
            )
            InfoSurface(title = "解码结果") {
                if (results.isEmpty()) {
                    Text(
                        text = "（暂无结果）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    results.forEachIndexed { index, item ->
                        KeyValueRow(label = "#${index + 1} 消息", value = item.text)
                        KeyValueRow(
                            label = "基频 / 起点 / 同步得分",
                            value = "${"%.1f".format(item.frequencyHz)} Hz · ${item.offsetMs} ms · " +
                                "%.1f".format(item.score)
                        )
                    }
                }
            }

            CodecDisclaimer(
                extra = "FT8 采用 LDPC(174,91) + CRC14 + Costas 同步的完整编解码，但不含 OSD 深搜、" +
                    "多信号消除与高斯成形，调制为简化连续相位 FSK；只适合信号较强时研究与自测。"
            )
        }
    }
}

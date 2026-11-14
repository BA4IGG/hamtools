package com.ham.tools.ui.audio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ham.tools.data.audio.MicCapture
import com.ham.tools.ui.components.ActionButton
import com.ham.tools.ui.components.InfoSurface
import com.ham.tools.ui.components.KeyValueRow
import com.ham.tools.ui.components.SectionLabel
import com.ham.tools.ui.components.ToolScaffold
import com.ham.tools.ui.components.ToolScrollColumn
import com.ham.tools.util.SstvCodec
import com.ham.tools.util.SstvImage
import com.ham.tools.util.SstvMode
import com.ham.tools.util.ToneEngine
import com.ham.tools.util.WavIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

/**
 * SSTV 编解码工具页：选图 → 生成基带音频 → 播放/保存 WAV；
 * 解码可用麦克风、导入 WAV，或直接用本机生成的音频做闭环自测。
 */
@Composable
fun SstvScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val permission = rememberMicPermission()
    val scope = rememberCoroutineScope()
    val engine = remember { ToneEngine() }
    val mic = remember { MicCapture(context) }
    DisposableEffect(Unit) { onDispose { engine.release(); mic.release() } }

    var modeLabel by rememberSaveable { mutableStateOf(SstvMode.ROBOT36.label) }
    val mode = SstvMode.fromLabel(modeLabel)
    val targetSeconds = ceil(mode.imageMs / 1000.0).toInt() + 3

    var imageName by rememberSaveable { mutableStateOf("内置测试图") }
    var pixels by remember { mutableStateOf<IntArray?>(null) }
    var status by rememberSaveable { mutableStateOf("尚未生成信号") }
    var busy by remember { mutableStateOf(false) }
    var decodedImage by remember { mutableStateOf<SstvImage?>(null) }
    var pendingWav by remember { mutableStateOf<ByteArray?>(null) }
    var pendingPng by remember { mutableStateOf<ByteArray?>(null) }

    val level by mic.level.collectAsState()
    val seconds by mic.seconds.collectAsState()
    val recording by mic.recording.collectAsState()
    val micError by mic.error.collectAsState()

    fun sourcePixels(): IntArray = pixels ?: builtInTestImage(mode.width, mode.height)

    fun decode(data: ShortArray, rate: Int) {
        if (busy) return
        busy = true
        status = "正在解码 ${"%.1f".format(data.size.toDouble() / rate)} 秒音频…"
        scope.launch {
            val image = withContext(Dispatchers.Default) {
                runCatching { SstvCodec.decode(data, rate) }.getOrNull()
            }
            decodedImage = image
            status = if (image == null) {
                "未识别到支持的 SSTV 信号。请确认 VIS 头完整、信号较强、无明显噪声；" +
                    "仅支持 Robot36 / Martin M1 / Scottie S1。"
            } else image.note
            busy = false
        }
    }

    /** 编码耗时随模式增长（Martin M1 约 107 秒音频），放到后台线程避免卡界面。 */
    fun generate(onReady: (ShortArray) -> Unit) {
        if (busy) return
        busy = true
        status = "正在生成 ${mode.label} 音频…"
        // 在主线程取一次快照，避免后台协程里读 Compose 状态
        val source = sourcePixels()
        val selectedMode = mode
        scope.launch {
            val pcm = withContext(Dispatchers.Default) {
                SstvCodec.encode(
                    source, selectedMode.width, selectedMode.height,
                    selectedMode, SstvCodec.SAMPLE_RATE
                )
            }
            status = "已生成 ${"%.1f".format(pcm.size.toDouble() / SstvCodec.SAMPLE_RATE)} 秒 " +
                "${selectedMode.label} 音频（VIS ${selectedMode.visCode}）"
            busy = false
            onReady(pcm)
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

    val savePng = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? ->
        val bytes = pendingPng
        if (uri != null && bytes != null) {
            status = if (writeBytesToUri(context, uri, bytes)) "PNG 已保存到所选位置" else "保存失败：无法写入所选位置"
        }
        pendingPng = null
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
            status = "已导入 WAV：${"%.1f".format(wav.pcm.size.toDouble() / wav.sampleRate)} 秒"
            decode(wav.pcm, wav.sampleRate)
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        // 在进入后台协程前取一次模式快照：不同模式的分辨率不同，缩放必须按当时的模式做
        val selectedMode = mode
        scope.launch {
            val loaded = withContext(Dispatchers.Default) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                            ?: return@runCatching null
                        val w = selectedMode.width
                        val h = selectedMode.height
                        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
                        val arr = IntArray(w * h)
                        scaled.getPixels(arr, 0, w, 0, 0, w, h)
                        arr
                    }
                }.getOrNull()
            }
            if (loaded == null) {
                status = "读取图片失败，仍可使用内置测试图"
            } else {
                pixels = loaded
                imageName = "所选图片（已缩放到 ${selectedMode.width}×${selectedMode.height}）"
                status = "已载入图片，可生成 ${selectedMode.label} 音频"
            }
        }
    }

    fun exportPng(image: SstvImage) {
        val bytes = runCatching {
            val bitmap = Bitmap.createBitmap(
                image.argb, image.width, image.height, Bitmap.Config.ARGB_8888
            )
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        }.getOrNull()
        if (bytes == null) {
            status = "生成 PNG 失败"
        } else {
            pendingPng = bytes
            savePng.launch("SSTV_${System.currentTimeMillis()}.png")
        }
    }

    LaunchedEffect(recording, targetSeconds) {
        if (recording) {
            while (mic.recording.value && mic.seconds.value < targetSeconds.toDouble()) {
                delay(300)
            }
            if (mic.recording.value) {
                val captured = mic.stop()
                decode(captured, MicCapture.SAMPLE_RATE)
            }
        }
    }

    ToolScaffold(title = "SSTV 编解码", onBack = onBack, modifier = modifier) { padding ->
        ToolScrollColumn(padding) {
            SectionLabel(text = "编码模式")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SstvMode.ALL.forEach { item ->
                    val selected = item == mode
                    val onClick = { modeLabel = item.label }
                    if (selected) {
                        Button(onClick = onClick, modifier = Modifier.weight(1f)) { Text(text = item.label) }
                    } else {
                        FilledTonalButton(onClick = onClick, modifier = Modifier.weight(1f)) {
                            Text(text = item.label)
                        }
                    }
                }
            }
            InfoSurface(title = "模式参数") {
                KeyValueRow(label = "VIS 码 / 分辨率", value = "${mode.visCode} / ${mode.width}×${mode.height}")
                KeyValueRow(label = "每行时长", value = "%.3f ms".format(mode.lineMs))
                KeyValueRow(label = "整幅音频", value = "%.1f 秒".format(mode.imageMs / 1000.0))
                KeyValueRow(label = "待编码图像", value = imageName)
            }
            CodecActionRow(
                primary = "选择图片",
                onPrimary = { pickImage.launch("image/*") },
                secondary = "内置测试图",
                onSecondary = {
                    pixels = null
                    imageName = "内置测试图"
                    status = "已切换为内置测试图"
                }
            )

            SectionLabel(text = "生成与播放")
            CodecActionRow(
                primary = "生成并播放",
                onPrimary = { generate { pcm -> engine.play(pcm) } },
                secondary = "保存 WAV",
                onSecondary = {
                    generate { pcm ->
                        pendingWav = WavIo.encode(pcm, SstvCodec.SAMPLE_RATE)
                        saveWav.launch("SSTV_${mode.name}_${System.currentTimeMillis()}.wav")
                    }
                },
                primaryEnabled = !busy,
                secondaryEnabled = !busy
            )
            CodecActionRow(
                primary = "本机自测解码",
                onPrimary = { generate { pcm -> decode(pcm, SstvCodec.SAMPLE_RATE) } },
                secondary = "导入 WAV 解码",
                onSecondary = { openWav.launch("audio/*") },
                primaryEnabled = !busy
            )
            InfoSurface(title = "编码状态") {
                KeyValueRow(label = "状态", value = status)
                KeyValueRow(label = "频率", value = "同步 1200 Hz · 黑 1500 Hz · 白 2300 Hz")
            }

            SectionLabel(text = "麦克风解码")
            MicPermissionCard(state = permission)
            LevelMeter(
                level = level,
                seconds = seconds,
                running = recording,
                targetSeconds = targetSeconds
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
                primary = if (recording) "停止并解码" else "开始监听（约 $targetSeconds 秒）",
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
                secondary = "清空图像",
                onSecondary = { decodedImage = null },
                secondaryEnabled = decodedImage != null
            )

            decodedImage?.let { image ->
                SectionLabel(text = "解码图像")
                val bitmap = remember(image) {
                    Bitmap.createBitmap(
                        image.argb, image.width, image.height, Bitmap.Config.ARGB_8888
                    ).asImageBitmap()
                }
                Image(
                    bitmap = bitmap,
                    contentDescription = "解码得到的 SSTV 图像",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(image.width.toFloat() / image.height)
                        .padding(vertical = 8.dp)
                )
                InfoSurface(title = "识别信息") {
                    KeyValueRow(label = "结果", value = image.note)
                }
                ActionButton(
                    text = "保存 PNG",
                    onClick = { exportPng(image) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            CodecDisclaimer(
                extra = "SSTV 只支持 Robot36 / Martin M1 / Scottie S1，解码用 I/Q 鉴频 + VIS 识别 + 行同步，" +
                    "不做斜率与倾斜校正、自动增益，弱信号或有频偏时效果不如专业软件。"
            )
        }
    }
}

/** 内置测试图：横向红渐变、纵向绿渐变、对角线蓝渐变，便于一眼看出解码是否完整。 */
private fun builtInTestImage(width: Int, height: Int): IntArray = IntArray(width * height) { i ->
    val x = i % width
    val y = i / width
    val r = x * 255 / (width - 1)
    val g = y * 255 / (height - 1)
    val b = (x + y) * 255 / (width + height - 2)
    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

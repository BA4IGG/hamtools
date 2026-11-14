package com.ham.tools.ui.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ham.tools.data.audio.MicCapture
import com.ham.tools.ui.components.ActionButton
import com.ham.tools.ui.components.InfoSurface
import com.ham.tools.ui.components.KeyValueRow
import com.ham.tools.ui.components.NoteCard

/** 麦克风权限的当前状态与两个动作。 */
data class MicPermissionState(
    val granted: Boolean,
    val request: () -> Unit,
    val openSettings: () -> Unit
)

/**
 * 运行时麦克风权限：不自动弹窗，用户点「申请权限」后先展示用途说明，
 * 确认后才调用系统权限对话框；被拒绝也不影响编码、播放、本机自测和导入 WAV。
 */
@Composable
fun rememberMicPermission(): MicPermissionState {
    val context = LocalContext.current
    var granted by rememberSaveable { mutableStateOf(MicCapture.isGranted(context)) }
    var showRationale by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        granted = ok
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text(text = "需要麦克风权限") },
            text = {
                Text(
                    text = "解码 FT8 / SSTV 信号时需要实时读取麦克风。音频只在手机内存里做本机信号处理：" +
                        "不保存录音文件、不上传、不联网，也不会用于其它用途。" +
                        "拒绝后仍可使用编码、播放、本机自测和导入 WAV 解码。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text(text = "继续申请") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text(text = "暂不") }
            }
        )
    }

    val openSettings: () -> Unit = {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        runCatching { context.startActivity(intent) }
    }
    return MicPermissionState(
        granted = granted,
        request = { showRationale = true },
        openSettings = openSettings
    )
}

@Composable
fun MicPermissionCard(state: MicPermissionState, modifier: Modifier = Modifier) {
    InfoSurface(title = "麦克风权限（仅解码需要）", modifier = modifier) {
        KeyValueRow(
            label = "当前状态",
            value = if (state.granted) "已授予，可开始监听" else "未授予，实时监听不可用"
        )
        Text(
            text = "采集到的声音只在内存里做本机解码，不保存录音、不上传、不联网。" +
                "离开页面即释放麦克风。编码、播放与本机自测不需要此权限。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)
        )
        if (!state.granted) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = state.request, modifier = Modifier.weight(1f)) {
                    Text(text = "申请权限")
                }
                OutlinedButton(onClick = state.openSettings, modifier = Modifier.weight(1f)) {
                    Text(text = "系统设置")
                }
            }
        }
    }
}

/** 实时电平与采集时长。电平只是相对值，不显示为 dB，避免伪装成校准过的仪表。 */
@Composable
fun LevelMeter(level: Float, seconds: Double, running: Boolean, targetSeconds: Int) {
    val scaled = (level * 4f).coerceIn(0f, 1f)
    InfoSurface(title = if (running) "正在监听" else "监听已停止") {
        KeyValueRow(label = "输入电平", value = if (running) "${(scaled * 100).toInt()}%" else "—")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(scaled)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        KeyValueRow(
            label = "已采集",
            value = "%.1f 秒 / 建议 %d 秒".format(seconds, targetSeconds)
        )
    }
}

/** 两个编解码工具共用的合规与能力边界说明。 */
@Composable
fun CodecDisclaimer(extra: String) {
    NoteCard(
        text = "本工具生成/解析的是音频基带信号，不是射频发射。实际通联需要电台、合法执照并遵守" +
            "《中华人民共和国无线电管理条例》等规定，且应把音频接到电台的音频输入、按合规频率发射。" +
            "解码为轻量化实现，不含深搜与多信号消除，弱信号灵敏度低于 WSJT-X / 专业 SSTV 软件。" + extra
    )
}

/** 统一把字节写到用户通过系统文件选择器选中的位置。 */
fun writeBytesToUri(context: Context, uri: Uri, bytes: ByteArray): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { stream ->
        stream.write(bytes)
        stream.flush()
    } ?: error("无法写入所选位置")
    true
}.getOrElse { false }

/** 供界面拼按钮文字用，避免各处重复写毫秒换算。 */
fun formatSeconds(seconds: Double): String = "%.1f s".format(seconds)

/** 统一的操作按钮，保持两个工具页的视觉一致。 */
@Composable
fun CodecActionRow(
    primary: String,
    onPrimary: () -> Unit,
    secondary: String,
    onSecondary: () -> Unit,
    primaryEnabled: Boolean = true,
    secondaryEnabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ActionButton(
            text = primary,
            onClick = onPrimary,
            modifier = Modifier.weight(1f),
            enabled = primaryEnabled
        )
        ActionButton(
            text = secondary,
            onClick = onSecondary,
            modifier = Modifier.weight(1f),
            enabled = secondaryEnabled,
            tonal = true
        )
    }
}

package com.ham.tools.data.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import com.ham.tools.util.Dsp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 解码用的麦克风采集器。
 *
 * 隐私约定（与界面文案、隐私政策一致）：
 * - 只在用户主动点「开始监听」且已授予 RECORD_AUDIO 时才打开麦克风；
 * - PCM 只放在内存里，交给本机编解码器处理，**不写录音文件、不上传、不联网**；
 * - 停止或离开页面即释放 AudioRecord，最多保留 MAX_SECONDS 秒的滚动缓冲。
 *
 * 采集用 48000 Hz（Android 广泛支持），编解码器内部自行重采样；
 * 优先用 UNPROCESSED 音源避免 AGC/降噪破坏 FT8/SSTV 的幅度与频率信息。
 */
class MicCapture(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var recorder: AudioRecord? = null

    // 每秒一块，避免一次性申请十几 MB 的连续数组；只在 IO 线程与 stop() 之间加锁
    private val chunks = ArrayList<ShortArray>()
    private var totalSamples = 0

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level

    private val _seconds = MutableStateFlow(0.0)
    val seconds: StateFlow<Double> = _seconds

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** 启动采集。权限缺失或硬件初始化失败时返回 false 并写入可诊断的错误信息。 */
    fun start(): Boolean {
        if (_recording.value) return true
        if (!isGranted(context)) {
            _error.value = "未获得麦克风权限，无法监听；编码、播放与本机自测仍可使用。"
            return false
        }
        _error.value = null
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            _error.value = "设备不支持 ${SAMPLE_RATE}Hz 单声道采集。"
            return false
        }
        val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.MIC
        }
        val record = try {
            AudioRecord(
                source, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                (minBuffer * 2).coerceAtLeast(8192)
            )
        } catch (t: Throwable) {
            _error.value = "无法初始化麦克风：${t.message ?: "未知错误"}"
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { record.release() }
            _error.value = "麦克风初始化失败，可能被其它应用占用。"
            return false
        }
        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                runCatching { record.release() }
                _error.value = "麦克风未能进入采集状态，可能被其它应用占用。"
                return false
            }
        } catch (t: SecurityException) {
            runCatching { record.release() }
            _error.value = "麦克风权限被系统拒绝，请到系统设置里开启。"
            return false
        } catch (t: Throwable) {
            // 部分 ROM 会抛 IllegalStateException / RuntimeException，不能让它冒到界面造成崩溃
            runCatching { record.release() }
            _error.value = "无法启动麦克风采集：${t.message ?: "未知错误"}"
            return false
        }
        synchronized(chunks) {
            chunks.clear()
            totalSamples = 0
        }
        _seconds.value = 0.0
        _level.value = 0f
        _recording.value = true
        recorder = record

        job = scope.launch {
            val buffer = ShortArray(4096)
            try {
                while (isActive && _recording.value) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        synchronized(chunks) {
                            chunks += buffer.copyOf(read)
                            totalSamples += read
                            while (totalSamples > MAX_SAMPLES && chunks.size > 1) {
                                totalSamples -= chunks.removeAt(0).size
                            }
                        }
                        _seconds.value = totalSamples.toDouble() / SAMPLE_RATE
                        _level.value = Dsp.rms(buffer, 0, read)
                    } else if (read < 0) {
                        _error.value = "采集返回错误码 $read，已停止监听。"
                        break
                    }
                }
            } catch (t: Throwable) {
                _error.value = "采集中断：${t.message ?: "未知错误"}"
            } finally {
                _recording.value = false
                _level.value = 0f
            }
        }
        return true
    }

    /** 停止采集并返回本次采集到的全部 PCM（已按时间顺序拼接）。 */
    fun stop(): ShortArray {
        _recording.value = false
        job?.cancel()
        job = null
        val active = recorder
        recorder = null
        runCatching {
            active?.stop()
            active?.release()
        }
        val snapshot = synchronized(chunks) { chunks.toList() }
        val out = ShortArray(snapshot.sumOf { it.size })
        var offset = 0
        for (chunk in snapshot) {
            chunk.copyInto(out, offset)
            offset += chunk.size
        }
        _level.value = 0f
        return out
    }

    fun clearError() { _error.value = null }

    /** 页面销毁时必须调用，确保 AudioRecord 与协程都被释放。 */
    fun release() {
        stop()
        scope.cancel()
    }

    companion object {
        const val SAMPLE_RATE = 48000
        const val MAX_SECONDS = 125
        const val MAX_SAMPLES = SAMPLE_RATE * MAX_SECONDS

        fun isGranted(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }
}

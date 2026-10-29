package com.ham.tools.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.ham.tools.data.reference.MorseSymbol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * 摩尔斯电码发声器。
 *
 * 用 AudioTrack 直接合成正弦波，不依赖任何音频资源文件，因此完全离线可用。
 *
 * 两个容易被忽略但很关键的处理：
 * - **渐入渐出（ramp）**：正弦波若从 0 幅度瞬间跳到满幅，扬声器会「啪」一声爆音，
 *   连续发送时听感极差。这里每个音的起止各留 5ms 线性斜坡来消除它
 * - **相位连续**：整段文本用一个累加相位，而不是每个音重新从 0 开始，
 *   避免音与音之间出现额外的宽频分量
 *
 * 采用 MODE_STATIC：先一次性写入 PCM 再播放，避免 MODE_STREAM 在
 * 低端机上因缓冲区欠载而产生的断续。代价是需要一次性占用内存，
 * 故在 [play] 处对总时长设了上限。
 */
class MorsePlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var track: AudioTrack? = null
    private var timerJob: Job? = null

    /**
     * 播放一段时序。
     *
     * @param symbols 由 [com.ham.tools.data.reference.MorseTable.toSymbols] 生成的时序
     * @param freqHz 音调频率，人耳对 600-700Hz 最敏感，收发报常用 600Hz
     * @param onDone 播放自然结束（非被打断）时回调
     * @return 是否真的开始播放
     */
    fun play(
        symbols: List<MorseSymbol>,
        freqHz: Int = DEFAULT_TONE_HZ,
        onDone: () -> Unit = {}
    ): Boolean {
        stop()
        val totalMs = symbols.sumOf { it.durationMs }
        if (totalMs <= 0 || totalMs > MAX_DURATION_MS) return false

        val pcm = render(symbols, freqHz)
        if (pcm.isEmpty()) return false

        val built = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
        } catch (t: Throwable) {
            return false
        }

        return try {
            built.write(pcm, 0, pcm.size)
            built.play()
            track = built
            timerJob = scope.launch {
                delay(totalMs.toLong() + 60) // 留一点尾部余量
                if (track === built) {
                    stopInternal()
                    onDone()
                }
            }
            true
        } catch (t: Throwable) {
            runCatching { built.release() }
            false
        }
    }

    /** 停止播放。重复调用安全。 */
    fun stop() {
        timerJob?.cancel()
        timerJob = null
        stopInternal()
    }

    private fun stopInternal() {
        track?.let { t ->
            runCatching {
                if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.pause()
                t.flush()
            }
        }
        track = null
    }

    /** 释放资源，页面销毁时必须调用。 */
    fun release() {
        stop()
        scope.cancel()
    }

    private fun render(symbols: List<MorseSymbol>, freqHz: Int): ShortArray {
        if (symbols.isEmpty()) return ShortArray(0)
        val counts = IntArray(symbols.size) { i ->
            symbols[i].durationMs * SAMPLE_RATE / 1000
        }
        val total = counts.sum()
        if (total <= 0) return ShortArray(0)

        val out = ShortArray(total)
        val step = 2.0 * PI * freqHz / SAMPLE_RATE
        val ramp = (SAMPLE_RATE * RAMP_MS / 1000).coerceAtLeast(1)
        var idx = 0
        var phase = 0.0

        for (i in symbols.indices) {
            val on = symbols[i].on
            val n = counts[i]
            for (k in 0 until n) {
                if (on) {
                    val edge = min(k, n - 1 - k).coerceAtLeast(0)
                    val env = (edge.toDouble() / ramp).coerceAtMost(1.0)
                    out[idx] = (env * AMPLITUDE * sin(phase) * Short.MAX_VALUE)
                        .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                idx++
                phase += step
            }
        }
        return out
    }

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val RAMP_MS = 5
        private const val AMPLITUDE = 0.6
        const val DEFAULT_TONE_HZ = 600

        /** 单次播放的时长上限，防止超长文本一次性吃太多内存。 */
        const val MAX_DURATION_MS = 120_000
    }
}

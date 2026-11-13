package com.ham.tools.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI

/**
 * 音频输出引擎：给 CW 电键、DTMF 拨号、音调发生器共用。
 *
 * 两条通路各司其职：
 * - **短音**（play）：MODE_STATIC，一次性写入 PCM 再播放。拨号音、扫频、
 *   CW 自动拍发都走这里，低端机上不会因缓冲欠载而断续。
 * - **持续音**（start/stop）：MODE_STREAM，协程循环生成 PCM 块并阻塞写入，
 *   支持按住发报和实时改频率/波形。参数用 volatile 字段，改参数不必重建 AudioTrack。
 *
 * 停止时做逐块淡出而不是直接 pause，避免「啪」的爆音；页面销毁必须调用 release()。
 */
class ToneEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var staticTrack: AudioTrack? = null
    private var streamTrack: AudioTrack? = null
    private var streamJob: Job? = null

    @Volatile private var freqHz = 600.0
    @Volatile private var waveform = Waveform.SINE
    @Volatile private var amplitude = 0.6
    @Volatile private var running = false
    @Volatile private var stopping = false

    /** 播放一段已合成好的 PCM。重复调用会先停掉上一段。 */
    fun play(pcm: ShortArray): Boolean {
        releaseStatic()
        if (pcm.isEmpty()) return false
        val track = try {
            buildTrack(AudioTrack.MODE_STATIC, pcm.size * 2)
        } catch (t: Throwable) {
            return false
        }
        return try {
            track.write(pcm, 0, pcm.size)
            track.play()
            staticTrack = track
            true
        } catch (t: Throwable) {
            runCatching { track.release() }
            false
        }
    }

    /** 开始持续音；已在播放时只更新参数，不重建轨道。 */
    fun start(freq: Double, wave: Waveform, amp: Double = 0.6) {
        freqHz = freq.coerceIn(20.0, 20000.0)
        waveform = wave
        amplitude = amp.coerceIn(0.0, 1.0)
        if (running && !stopping) return
        if (running) stopImmediate()

        val track = try {
            buildTrack(AudioTrack.MODE_STREAM, streamBufferBytes())
        } catch (t: Throwable) {
            return
        }
        running = true
        stopping = false
        try {
            track.play()
        } catch (t: Throwable) {
            runCatching { track.release() }
            running = false
            return
        }
        streamTrack = track
        streamJob = scope.launch { loop(track) }
    }

    /** 运行中实时改参数（音调发生器拖动滑块时用）。 */
    fun update(freq: Double, wave: Waveform, amp: Double = 0.6) {
        freqHz = freq.coerceIn(20.0, 20000.0)
        waveform = wave
        amplitude = amp.coerceIn(0.0, 1.0)
    }

    /** 停止持续音，做短渐出后释放轨道。 */
    fun stop() {
        stopping = true
    }

    /** 立即停止，不等渐出，用于快速连按电键。 */
    fun stopImmediate() {
        running = false
        stopping = false
        streamJob?.cancel()
        streamJob = null
        streamTrack?.let { t ->
            runCatching {
                if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.pause()
                t.flush()
                t.release()
            }
        }
        streamTrack = null
    }

    /** 页面销毁时释放全部资源。 */
    fun release() {
        stopImmediate()
        releaseStatic()
        scope.cancel()
    }

    private suspend fun loop(track: AudioTrack) {
        val chunk = ShortArray(CHUNK_SAMPLES)
        val sampleRate = AudioSignal.SAMPLE_RATE
        var phase = 0.0
        var fadeIn = 0.0
        var fadeOut = 1.0
        try {
            while (running && currentCoroutineContext().isActive) {
                if (stopping) {
                    fadeOut *= 0.45
                    if (fadeOut < 0.02) break
                } else {
                    fadeIn = (fadeIn + 0.25).coerceAtMost(1.0)
                }
                val f = freqHz
                val w = waveform
                val gain = amplitude * fadeIn * fadeOut
                val step = 2.0 * PI * f / sampleRate
                for (i in 0 until CHUNK_SAMPLES) {
                    chunk[i] = (gain * AudioSignal.shape(phase, w) * FULL_SCALE)
                        .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    phase = (phase + step) % (2.0 * PI)
                }
                track.write(chunk, 0, chunk.size)
            }
        } catch (t: Throwable) {
            // 轨道被提前释放时 write 会抛异常，静默收尾即可
        } finally {
            running = false
            stopping = false
            if (streamTrack === track) streamTrack = null
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
                track.flush()
                track.release()
            }
        }
    }

    private fun releaseStatic() {
        staticTrack?.let { t ->
            runCatching {
                if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.pause()
                t.flush()
                t.release()
            }
        }
        staticTrack = null
    }

    private fun buildTrack(mode: Int, bufferBytes: Int): AudioTrack =
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
                    .setSampleRate(AudioSignal.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(mode)
            .setBufferSizeInBytes(bufferBytes)
            .build()

    private fun streamBufferBytes(): Int {
        val min = AudioTrack.getMinBufferSize(
            AudioSignal.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        return (min * 2).coerceAtLeast(4096)
    }

    private companion object {
        const val CHUNK_SAMPLES = 2048
        const val FULL_SCALE = 32767.0
    }
}

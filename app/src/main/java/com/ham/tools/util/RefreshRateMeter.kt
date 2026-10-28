package com.ham.tools.util

import android.view.Choreographer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * 用 Choreographer 采样真实帧间隔，算出界面实际跑在多少赫兹。
 *
 * 注意它拿到的是「UI 线程被唤醒的频率」，也就是 Compose 实际能跑到的帧率上限。
 * 屏幕显示模式即使写着 120Hz，如果每帧绘制超时，这里也只会显示更低的值。
 *
 * 只在需要的页面（关于页）启动、离开即停止，避免常驻回调把 CPU 叫醒白耗电。
 */
class RefreshRateMeter {

    private companion object {
        const val SAMPLE_WINDOW = 40
        const val MIN_INTERVAL_NS = 1_000_000L     // 1ms，低于此值是异常跳变
        const val MAX_INTERVAL_NS = 100_000_000L   // 100ms，高于此值说明中间断帧了
        const val MIN_SAMPLES = 4
    }

    private val choreographer by lazy { Choreographer.getInstance() }
    private val intervalsNs = LongArray(SAMPLE_WINDOW)
    private var writeIndex = 0
    private var sampleCount = 0
    private var lastFrameNs = 0L
    private var frameCallback: Choreographer.FrameCallback? = null

    private var _hz by mutableFloatStateOf(0f)

    /** 实测刷新率，样本不足时为 0，单位 Hz。 */
    val hz: Float get() = _hz

    fun start() {
        if (frameCallback != null) return
        lastFrameNs = 0L
        writeIndex = 0
        sampleCount = 0

        val callback = Choreographer.FrameCallback { frameTimeNanos ->
            if (lastFrameNs != 0L) {
                val delta = frameTimeNanos - lastFrameNs
                if (delta in MIN_INTERVAL_NS..MAX_INTERVAL_NS) {
                    intervalsNs[writeIndex] = delta
                    writeIndex = (writeIndex + 1) % SAMPLE_WINDOW
                    sampleCount = minOf(sampleCount + 1, SAMPLE_WINDOW)
                }
            }
            lastFrameNs = frameTimeNanos
            _hz = estimateHz()
            frameCallback?.let { choreographer.postFrameCallback(it) }
        }
        frameCallback = callback
        choreographer.postFrameCallback(callback)
    }

    fun stop() {
        frameCallback?.let { choreographer.removeFrameCallback(it) }
        frameCallback = null
    }

    /** 取中位数而不是平均数：偶发的超长帧不会把结果拖偏。 */
    private fun estimateHz(): Float {
        if (sampleCount < MIN_SAMPLES) return 0f
        val sorted = intervalsNs.take(sampleCount).sorted()
        val medianNs = sorted[sorted.size / 2]
        if (medianNs <= 0L) return 0f
        return 1_000_000_000f / medianNs.toFloat()
    }
}

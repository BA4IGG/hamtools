package com.ham.tools.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.Display
import android.view.WindowManager
import kotlin.math.abs

private const val TAG = "FrameRate"

/** 声明给系统的可接受下限：允许系统在发热/省电时往下走，但不锁死在低帧。 */
private const val MIN_ACCEPTABLE_HZ = 60f

/** 判定「当前模式已达目标」的容差，部分屏幕报的是 119.88Hz 这类非整数值。 */
private const val RATE_TOLERANCE_HZ = 0.5f

/**
 * 一块屏幕的刷新率画像。
 *
 * @property currentHz 当前显示模式的刷新率（刚开机/刚进 App 时的快照）
 * @property maxHz 屏幕硬件支持的最高刷新率
 * @property targetHz 本 App 实际请求的刷新率，取 [TARGET_HZ] 与 [maxHz] 的较小值
 * @property requested 本次是否真的向系统发出了请求
 * @property supportedHz 屏幕支持的全部刷新率，用于排查问题
 */
data class RefreshProfile(
    val currentHz: Float,
    val maxHz: Float,
    val targetHz: Float,
    val requested: Boolean,
    val supportedHz: List<Float>
)

/**
 * 让本 App 跑到屏幕支持的最高刷新率。
 *
 * Android 把刷新率的最终决定权握在系统手里（省电模式、温度、厂商白名单都能覆盖应用请求），
 * 所以这里做的是三条路径层层兜底，任何一条被系统拒绝都不会崩溃，只是退回默认：
 *
 * 1. `preferredDisplayModeId`（API 23+，公开）：直接指定显示模式，允许非无缝的完整切换，
 *    覆盖面最广。只对分辨率与当前完全一致的模式生效，避免为了高刷把画面分辨率也改掉。
 * 2. `preferredRefreshRate`（API 21+，公开）：声明期望的刷新率。注意它与上一条互斥——
 *    同时设置时 `preferredDisplayModeId` 生效、另一个被忽略，所以两者只挑一个写。
 * 3. `preferredMin/MaxDisplayRefreshRate`（隐藏字段）：部分厂商 ROM 正是写这两个字段把第三方
 *    应用限到 60/90Hz，反向写回去可以解除限制。反射失败只是跳过，不影响前两条。
 *
 * 设备保护：只请求硬件支持范围内的值，60Hz 屏幕直接放弃请求。
 */
object FrameRateController {

    /** 只读探测，不修改任何窗口属性，用于展示当前状态。 */
    fun probe(context: Context): RefreshProfile {
        return runCatching { profileOf(displayOf(context)) }
            .getOrElse { RefreshProfile(0f, 0f, 0f, false, emptyList()) }
    }

    /** 向系统请求高刷新率，返回本次使用的画像。调用失败返回 null。 */
    fun apply(activity: Activity): RefreshProfile? {
        return runCatching { applyInternal(activity) }
            .onFailure { Log.w(TAG, "请求高刷新率失败", it) }
            .getOrNull()
    }

    private fun applyInternal(activity: Activity): RefreshProfile {
        val window = activity.window ?: return profileOf(null)
        val display = activity.windowManager?.defaultDisplay
        val profile = profileOf(display)
        if (profile.targetHz <= 0f) {
            return profile.copy(requested = false)
        }

        val params = window.attributes
        val modeId = pickModeId(display, profile.targetHz)
        if (modeId != 0) {
            params.preferredDisplayModeId = modeId
            // 两者互斥，用前者时把后者清零，避免残留旧值混淆系统判断
            params.preferredRefreshRate = 0f
        } else {
            params.preferredDisplayModeId = 0
            params.preferredRefreshRate = profile.targetHz
        }
        writeCompatRefreshRateRange(params, profile.maxHz)
        window.attributes = params

        Log.d(
            TAG,
            "已请求 ${profile.targetHz}Hz（modeId=$modeId，" +
                "当前 ${profile.currentHz}Hz，上限 ${profile.maxHz}Hz）"
        )
        return profile.copy(requested = true)
    }

    /** 挑一个刷新率达标、且分辨率与当前一致的显示模式；挑不到返回 0。 */
    private fun pickModeId(display: Display?, targetHz: Float): Int {
        val modes = display?.supportedModes ?: return 0
        val current = runCatching { display.mode }.getOrNull() ?: return 0
        if (targetHz <= 0f) return 0
        // 当前模式已经达标就不必再切换，避免触发一次无意义的完整模式切换（会黑屏闪一下）
        if (current.refreshRate >= targetHz - RATE_TOLERANCE_HZ) return 0
        val sameResolution = modes.filter { mode ->
            mode.physicalWidth == current.physicalWidth &&
                mode.physicalHeight == current.physicalHeight
        }
        if (sameResolution.isEmpty()) return 0
        val picked = RefreshTargets.closestTo(sameResolution.map { it.refreshRate }, targetHz)
            ?: return 0
        return sameResolution
            .filter { abs(it.refreshRate - picked) < 0.1f }
            .minByOrNull { it.modeId }
            ?.modeId
            ?: 0
    }

    private fun profileOf(display: Display?): RefreshProfile {
        if (display == null) return RefreshProfile(0f, 0f, 0f, false, emptyList())
        val modes = runCatching { display.supportedModes?.toList() }.getOrNull().orEmpty()
        val currentHz = runCatching { display.mode?.refreshRate }.getOrNull() ?: display.refreshRate
        val maxHz = modes.maxOfOrNull { it.refreshRate } ?: display.refreshRate
        val supportedHz = modes.map { it.refreshRate }.distinct().sorted()
        // 与策略层共用同一套目标值计算，避免两处请求的数值不一致
        val targetHz = RefreshTargets.targetOf(supportedHz)
        return RefreshProfile(
            currentHz = currentHz,
            maxHz = maxHz,
            targetHz = targetHz,
            requested = false,
            supportedHz = supportedHz
        )
    }

    /**
     * 写隐藏字段，用于解除部分 ROM 对第三方应用的帧率封顶。
     *
     * 这些字段不在公开 SDK 里，Android 9 之后受 hidden API 限制，
     * 被挡住时会抛异常，这里吞掉并忽略，退回到公开 API 的效果。
     */
    @SuppressLint("PrivateApi", "SoonBlockedPrivateApi", "DiscouragedPrivateApi")
    private fun writeCompatRefreshRateRange(params: WindowManager.LayoutParams, maxHz: Float) {
        runCatching {
            WindowManager.LayoutParams::class.java
                .getDeclaredField("preferredMinDisplayRefreshRate")
                .apply { isAccessible = true }
                .setFloat(params, MIN_ACCEPTABLE_HZ)
        }.onFailure { Log.d(TAG, "preferredMinDisplayRefreshRate 不可用，已跳过") }

        runCatching {
            WindowManager.LayoutParams::class.java
                .getDeclaredField("preferredMaxDisplayRefreshRate")
                .apply { isAccessible = true }
                .setFloat(params, maxHz)
        }.onFailure { Log.d(TAG, "preferredMaxDisplayRefreshRate 不可用，已跳过") }
    }

    private fun displayOf(context: Context): Display? =
        (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
}

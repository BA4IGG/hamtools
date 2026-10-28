package com.ham.tools.util

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "FrameRateBoost"

/** 最近一次写入的结果。Activity 写完之后放到这里，UI 据此向用户解释为什么没上去。 */
object BoostStatus {
    private val _lastOutcome = MutableStateFlow<WriteOutcome?>(null)
    val lastOutcome: Flow<WriteOutcome?> = _lastOutcome.asStateFlow()

    fun report(outcome: WriteOutcome) {
        _lastOutcome.value = outcome
    }
}

/**
 * 刷新率提升的编排器：策略层写入 + 前台防回落守护。
 *
 * 需要「守护」的原因：不少 ROM 会在熄屏、换主题、温度变化时把
 * min/peak_refresh_rate 改回自家默认值，一改回去 App 立刻掉到 90Hz。
 * 这里盯住这两个键，被改走就补写回来。
 *
 * 守护只在前台运行，后台不做任何事，不会白白耗电。
 */
class FrameRateBoost(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val handler = Handler(Looper.getMainLooper())

    private var observer: ContentObserver? = null

    /** 当前守护的目标刷新率，0 表示未启用。 */
    @Volatile
    var targetHz: Float = 0f
        private set

    /** 自己刚写入的时刻，用于忽略由此引发的通知回环。 */
    @Volatile
    private var lastWriteMs = 0L

    /**
     * 启用提升：记下目标值、开始守护、写入系统策略。
     *
     * 幂等——已经在这个目标值时只做轻量的重登记，不会反复写磁盘。
     */
    fun setTarget(hz: Float): WriteOutcome {
        if (hz <= 0f) {
            return WriteOutcome(false, WriteMethod.NONE, null, null, "目标刷新率无效")
        }
        targetHz = hz
        if (!shouldWriteNow()) return RefreshPolicy.read(appContext).let { snapshot ->
            WriteOutcome(
                applied = RefreshPolicy.isOnTarget(snapshot, hz),
                method = WriteMethod.SETTINGS,
                minHz = snapshot.minHz,
                peakHz = snapshot.peakHz,
                detail = "策略已在目标值上"
            )
        }
        val outcome = write(hz)
        BoostStatus.report(outcome)
        return outcome
    }

    /** 关闭提升：停止守护并删掉写入的策略键，让 ROM 恢复自己的自适应策略。 */
    fun clearTarget() {
        if (targetHz == 0f && observer == null) return
        stopGuard()
        targetHz = 0f
        RefreshPolicy.reset(appContext)
        Log.i(TAG, "已关闭提升并恢复系统默认策略")
    }

    /** 回到前台：重新挂上守护，顺带补写一次（此时常常刚好被 ROM 重置过）。 */
    fun onForeground() {
        if (targetHz <= 0f) return
        startGuard()
        BoostStatus.report(write(targetHz))
    }

    /** 退到后台：摘掉守护，避免在后台被 ROM 的改动反复唤醒。 */
    fun onBackground() {
        stopGuard()
    }

    private fun write(hz: Float): WriteOutcome {
        lastWriteMs = android.os.SystemClock.elapsedRealtime()
        val outcome = RefreshPolicy.boost(appContext, hz)
        Log.i(TAG, "提升 ${hz}Hz -> applied=${outcome.applied} via=${outcome.method} (${outcome.detail})")
        return outcome
    }

    /** 距上次写入太近时不重复写，防止「写→通知→再写」的忙循环。 */
    private fun shouldWriteNow(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        return now - lastWriteMs > MIN_WRITE_INTERVAL_MS || observer == null
    }

    private fun startGuard() {
        if (observer != null) return
        val callback = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                onPolicyChanged()
            }
        }
        observer = callback
        runCatching {
            resolver.registerContentObserver(
                Settings.System.getUriFor(KEY_MIN_REFRESH_RATE), false, callback
            )
            resolver.registerContentObserver(
                Settings.System.getUriFor(KEY_PEAK_REFRESH_RATE), false, callback
            )
        }.onFailure { Log.w(TAG, "注册策略监听失败，退化为每次回前台补写", it) }
    }

    private fun stopGuard() {
        observer?.let { runCatching { resolver.unregisterContentObserver(it) } }
        observer = null
    }

    private fun onPolicyChanged() {
        val target = targetHz
        if (target <= 0f) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastWriteMs < SELF_WRITE_QUIET_MS) return

        val snapshot = RefreshPolicy.read(appContext)
        if (RefreshPolicy.isOnTarget(snapshot, target)) return
        Log.i(TAG, "检测到策略被改为 peak=${snapshot.peakHz}，补写回 ${target}Hz")
        BoostStatus.report(write(target))
    }

    private companion object {
        /** 自己写入后的静默期，期间的回调忽略，避免自触发回环。 */
        const val SELF_WRITE_QUIET_MS = 1_200L

        /** 两次真实写入之间的最小间隔。 */
        const val MIN_WRITE_INTERVAL_MS = 800L
    }
}

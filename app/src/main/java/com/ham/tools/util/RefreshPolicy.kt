package com.ham.tools.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.getSystemService
import java.util.Locale

private const val TAG = "RefreshPolicy"

/**
 * AOSP 的刷新率策略键（在 SDK 里是隐藏常量，所以这里写死字符串）。
 *
 * 它们的作用层级比 `preferredDisplayModeId` 更高：DisplayManager 决定策略时，
 * 会先按 `peak_refresh_rate` 划定允许的上限、按 `min_refresh_rate` 划定允许的下限，
 * 应用自己的窗口请求只能在这个区间里生效。
 * 换句话说——ROM 只要在策略层把上限设成 90，应用无论怎么请求都上不去。
 * 这正是「只有 90Hz」最常见的成因，也是本轮要攻的地方。
 */
const val KEY_MIN_REFRESH_RATE = "min_refresh_rate"
const val KEY_PEAK_REFRESH_RATE = "peak_refresh_rate"

/** 部分 ROM（三星、部分国产）额外用这个键记录用户在显示设置里选的档位。 */
const val KEY_USER_REFRESH_RATE = "user_refresh_rate"

/** 策略键是从 Android 12 开始被引用的，更早的版本写了也没人读。 */
private const val MIN_SUPPORTED_API = Build.VERSION_CODES.S

/** 写入通道。 */
enum class WriteMethod {
    /** 通过 WRITE_SETTINGS 权限直接改 Settings.System，覆盖绝大多数机型。 */
    SETTINGS,

    /** 设备已 root，走 su 改写（能绕过 WRITE_SETTINGS 未授权的情况）。 */
    ROOT,

    /** 没写成。 */
    NONE
}

/** 一次读取到的平面快照，用于展示与判断是否被系统改回去。 */
data class PolicySnapshot(
    val minHz: Float?,
    val peakHz: Float?,
    val userHz: Float?,
    val canWrite: Boolean,
    val powerSaveMode: Boolean
)

/** 一次写入操作的结果。 */
data class WriteOutcome(
    val applied: Boolean,
    val method: WriteMethod,
    val minHz: Float?,
    val peakHz: Float?,
    val detail: String
)

/**
 * 系统级刷新率策略的读写。
 *
 * 与 [FrameRateController] 是互补的两件事：
 * - [FrameRateController] 是「应用层」：告诉系统本 App 想跑多少帧，只有这一层时会被策略层压住
 * - [RefreshPolicy] 是「策略层」：直接抬系统允许的上限，影响所有应用
 *
 * 策略层需要 `WRITE_SETTINGS` 权限，这个权限不能用运行时弹窗申请，
 * 只能引导用户到系统页手动打开（[openWriteSettingsPage]）。
 */
object RefreshPolicy {

    /** 是否已有权限改写 Settings.System。Android 6 之前是安装时授予的。 */
    fun canWrite(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else {
            true
        }
    }

    /** 跳到系统的「修改系统设置」授权页。返回 false 表示这台设备没有这个页面。 */
    fun openWriteSettingsPage(context: Context): Boolean {
        return runCatching {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrElse { Log.w(TAG, "无法打开授权页", it); false }
    }

    /** 读取当前策略值。未设置时对应字段为 null（系统此时会用出厂默认值）。 */
    fun read(context: Context): PolicySnapshot {
        return PolicySnapshot(
            minHz = readFloat(context, KEY_MIN_REFRESH_RATE),
            peakHz = readFloat(context, KEY_PEAK_REFRESH_RATE),
            userHz = readFloat(context, KEY_USER_REFRESH_RATE),
            canWrite = canWrite(context),
            powerSaveMode = isPowerSaveMode(context)
        )
    }

    /**
     * 把系统允许的下限与上限都抬到 [targetHz]，强迫 Refreh 不再回落到低帧。
     *
     * 三个失败分支都有明确语义，方便上层给用户对症的建议：
     * - 系统低于 Android 12：策略键不存在，只能靠应用层请求
     * - 未授权 WRITE_SETTINGS：引导去系统页打开
     * - 写成功但读回来不对：ROM 忽略了这个键，尝试 root 通道
     */
    fun boost(context: Context, targetHz: Float): WriteOutcome {
        val rejected = WriteOutcome(false, WriteMethod.NONE, null, null, "")
        if (targetHz <= 0f) {
            return rejected.copy(detail = "目标刷新率无效")
        }
        if (Build.VERSION.SDK_INT < MIN_SUPPORTED_API) {
            return rejected.copy(detail = "Android ${Build.VERSION.RELEASE} 不支持全局策略键")
        }
        if (!canWrite(context)) {
            return rejected.copy(detail = "未授权「修改系统设置」")
        }

        val resolver = context.contentResolver
        val okMin = runCatching {
            Settings.System.putFloat(resolver, KEY_MIN_REFRESH_RATE, targetHz)
        }.getOrElse { Log.w(TAG, "写入 min_refresh_rate 失败", it); false }
        val okPeak = runCatching {
            Settings.System.putFloat(resolver, KEY_PEAK_REFRESH_RATE, targetHz)
        }.getOrElse { Log.w(TAG, "写入 peak_refresh_rate 失败", it); false }

        var snapshot = read(context)
        if (isOnTarget(snapshot.minHz, targetHz) && isOnTarget(snapshot.peakHz, targetHz)) {
            return WriteOutcome(true, WriteMethod.SETTINGS, snapshot.minHz, snapshot.peakHz, "已写入并读回校验通过")
        }

        // ROM 忽略了 SettingsProvider 的写入：设备若已 root 就走 su 兜底
        if (rootAvailable()) {
            val value = formatValue(targetHz)
            runRoot("settings put system $KEY_MIN_REFRESH_RATE $value")
            runRoot("settings put system $KEY_PEAK_REFRESH_RATE $value")
            snapshot = read(context)
            return if (isOnTarget(snapshot.minHz, targetHz) && isOnTarget(snapshot.peakHz, targetHz)) {
                WriteOutcome(true, WriteMethod.ROOT, snapshot.minHz, snapshot.peakHz, "已通过 root 写入")
            } else {
                WriteOutcome(false, WriteMethod.NONE, snapshot.minHz, snapshot.peakHz, "root 写入后仍未生效，厂商可能忽略该键")
            }
        }

        if (!okMin && !okPeak) {
            return rejected.copy(minHz = snapshot.minHz, peakHz = snapshot.peakHz, detail = "系统拒绝写入")
        }
        return rejected.copy(
            minHz = snapshot.minHz,
            peakHz = snapshot.peakHz,
            detail = "已写入但读回为 ${formatValue(snapshot.peakHz ?: 0f)}Hz，厂商 ROM 可能忽略该键"
        )
    }

    /**
     * 恢复系统默认：删掉这两个键，让 ROM 回到自家的自适应策略。
     *
     * 用「删除」而不是写回 60 —— 不少 ROM 的出厂默认值并不是 60，
     * 写死一个值反而可能比原来更差。
     */
    fun reset(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < MIN_SUPPORTED_API) return false
        val resolver = context.contentResolver
        val okMin = runCatching { clearKey(resolver, KEY_MIN_REFRESH_RATE) }
            .getOrElse { Log.w(TAG, "清除 min_refresh_rate 失败", it); false }
        val okPeak = runCatching { clearKey(resolver, KEY_PEAK_REFRESH_RATE) }
            .getOrElse { Log.w(TAG, "清除 peak_refresh_rate 失败", it); false }
        return okMin && okPeak
    }

    /**
     * 删掉一个策略键，让 ROM 回到自家的自适应策略。
     *
     * 优先用 putString(null)——等价于 adb 的 `settings delete`；
     * 少数 ROM 的 Provider 不接受 null 值，此时退回 ContentResolver.delete(rowUri)。
     */
    private fun clearKey(resolver: android.content.ContentResolver, key: String): Boolean {
        if (runCatching { Settings.System.putString(resolver, key, null) }.getOrDefault(false)) {
            return true
        }
        return runCatching { resolver.delete(Settings.System.getUriFor(key), null, null) > 0 }
            .getOrDefault(false)
    }

    /** 判定「已达标」的容差：有些屏的实际模式是 119.88Hz 而不是 120，别误判成失败。 */
    private const val TARGET_TOLERANCE_HZ = 1.5f

    /** 当前策略是否已经在目标值上（用于判断要不要重新施加）。 */
    fun isOnTarget(snapshot: PolicySnapshot, targetHz: Float): Boolean =
        targetHz <= 0f || (isOnTarget(snapshot.minHz, targetHz) && isOnTarget(snapshot.peakHz, targetHz))

    private fun isOnTarget(value: Float?, targetHz: Float): Boolean =
        value != null && value >= targetHz - TARGET_TOLERANCE_HZ

    /** 省电模式会把刷新率强制压到 60Hz，这是 AOSP 的硬策略，无法被覆盖。 */
    fun isPowerSaveMode(context: Context): Boolean {
        return runCatching {
            context.getSystemService<PowerManager>()?.isPowerSaveMode ?: false
        }.getOrDefault(false)
    }

    private fun readFloat(context: Context, key: String): Float? {
        if (Build.VERSION.SDK_INT < MIN_SUPPORTED_API) return null
        return runCatching {
            Settings.System.getFloat(context.contentResolver, key)
        }.onFailure { Log.d(TAG, "$key 未设置（$it）") }.getOrNull()
    }

    private fun formatValue(hz: Float): String =
        if (hz == hz.toLong().toFloat()) {
            hz.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", hz)
        }

    /** root 是否可用。结果就地缓存，探测一次就够了，避免每次 UI 刷新都 fork su。 */
    fun rootAvailable(): Boolean = cachedRoot ?: probeRoot().also { cachedRoot = it }

    @Volatile
    private var cachedRoot: Boolean? = null

    private fun probeRoot(): Boolean =
        runRoot("id -u").let { it.exitCode == 0 && it.output.trim() == "0" }

    /** 单条 su 命令，带 3 秒超时。只读 stdout，失败一律返回 exitCode=-1，不抛异常。 */
    private fun runRoot(command: String): ShellResult {
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = StringBuilder()
            val reader = Thread {
                runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { output.appendLine(it) }
                    }
                }
            }
            reader.start()
            reader.join(3_000L)
            val exitCode = if (reader.isAlive) {
                process.destroy()
                -1
            } else {
                try {
                    process.exitValue()
                } catch (ignored: IllegalThreadStateException) {
                    process.destroy()
                    -1
                }
            }
            ShellResult(exitCode, output.toString().trim())
        }.getOrElse { ShellResult(-1, it.message.orEmpty()) }
    }

    private data class ShellResult(val exitCode: Int, val output: String)
}

package com.ham.tools.data.update

import android.content.Context
import com.ham.tools.data.preferences.UserPreferences
import com.ham.tools.data.preferences.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 应用级更新检测状态（与 ExamLock / QslNoticeController 同样的单例 + StateFlow 约定）。
 *
 * - 自动检测：启动动画结束后触发一次，12 小时节流；失败静默，只在关于页卡片里如实显示，
 *   绝不因为 GitHub 连不上就弹窗打扰用户。
 * - 手动检测：用户点「检测更新」时忽略节流与「跳过此版本」，一定要给出明确结果。
 * - 发布通道：正式通道（默认）/ 测试通道写进 DataStore；切换后立刻按新通道重新检测，
 *   不沿用上一次通道查到的 Release 与更新日志。
 * - 跳过：只对自动检测生效，写入 DataStore；换一个新 tag 后仍会正常提醒。
 *
 * 只持有 applicationContext，不持有 Activity，避免生命周期泄漏。
 */
object UpdateController {

    /** 自动检测的最小间隔；失败也记时间，避免网络不通时每次冷启动都重试。 */
    const val AUTO_CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = UpdateClient()

    private val _status = MutableStateFlow(UpdateStatus())
    val status: StateFlow<UpdateStatus> = _status

    private val _channel = MutableStateFlow(UpdateChannel.STABLE)
    val channel: StateFlow<UpdateChannel> = _channel

    @Volatile
    private var inFlight = false

    @Volatile
    private var channelLoaded = false

    fun check(context: Context, localVersion: String, manual: Boolean) {
        if (inFlight) return
        val app = context.applicationContext
        inFlight = true
        _status.value = _status.value.copy(
            phase = UpdatePhase.CHECKING,
            failure = null,
            channel = _channel.value
        )
        scope.launch {
            try {
                val preferences = UserPreferences(app.settingsDataStore)
                // 首次检测前把用户上次选的通道读回来；老版本没有这个键会自然回落到正式通道
                if (!channelLoaded) {
                    _channel.value = preferences.updateChannel.first()
                    channelLoaded = true
                }
                val channel = _channel.value
                val now = System.currentTimeMillis()
                if (!manual) {
                    val last = preferences.updateLastCheckedAt.first()
                    if (now - last < AUTO_CHECK_INTERVAL_MS) {
                        _status.value = _status.value.copy(
                            phase = UpdatePhase.IDLE,
                            lastCheckedAt = last,
                            latestVersion = preferences.updateLatestVersion.first(),
                            channel = channel
                        )
                        return@launch
                    }
                }
                val result = client.fetch(channel)
                // 成功与失败都记录本次检测时间：自动检测靠它节流
                preferences.setUpdateLastCheckedAt(now)
                _status.value = when (result) {
                    is UpdateFetchResult.Success -> {
                        val release = result.release
                        preferences.setUpdateLatestVersion(release.version)
                        val skipped = if (manual) "" else preferences.updateSkippedTag.first()
                        val available = UpdateChecker.isUpdateAvailable(localVersion, release, channel)
                        val phase = when {
                            !available -> UpdatePhase.UP_TO_DATE
                            skipped == release.tag -> UpdatePhase.SKIPPED
                            else -> UpdatePhase.AVAILABLE
                        }
                        UpdateStatus(
                            phase = phase,
                            release = release,
                            latestVersion = release.version,
                            lastCheckedAt = now,
                            channel = channel
                        )
                    }
                    is UpdateFetchResult.Failure -> UpdateStatus(
                        phase = UpdatePhase.FAILED,
                        failure = result.reason,
                        latestVersion = preferences.updateLatestVersion.first(),
                        lastCheckedAt = now,
                        channel = channel
                    )
                }
            } catch (t: Throwable) {
                // 偏好读写异常也不能让检测卡死在「正在检查」
                _status.value = _status.value.copy(
                    phase = UpdatePhase.FAILED,
                    failure = UpdateFailure.BAD_DATA
                )
            } finally {
                inFlight = false
            }
        }
    }

    /** 用户在关于页切换发布通道：持久化 + 立刻按新通道重新检测（忽略 12 小时节流）。 */
    fun setChannel(context: Context, channel: UpdateChannel, localVersion: String) {
        _channel.value = channel
        channelLoaded = true
        // 清掉旧通道查到的 Release，避免「查看发布说明」展示另一个通道的内容
        _status.value = UpdateStatus(phase = UpdatePhase.IDLE, channel = channel)
        val app = context.applicationContext
        scope.launch {
            runCatching { UserPreferences(app.settingsDataStore).setUpdateChannel(channel) }
        }
        check(context, localVersion, manual = true)
    }

    /**
     * 「查看发布说明」：即使本机已是最新版，也能弹出同一个弹窗看该 Release 的更新日志。
     * 不重新联网，也不改变版本判断；标题与按钮会明确说明这不是一次升级提醒。
     */
    fun showNotes() {
        val current = _status.value
        if (current.release != null) {
            _status.value = current.copy(phase = UpdatePhase.AVAILABLE, notesOnly = true)
        }
    }

    /** 「稍后」：收起弹窗但保留本次 release 信息，关于页仍可再次查看。 */
    fun dismiss() {
        _status.value = _status.value.copy(phase = UpdatePhase.IDLE)
    }

    /** 「跳过此版本」：写入 tag，只抑制自动提醒，不影响手动检测。 */
    fun skip(context: Context, tag: String) {
        val app = context.applicationContext
        scope.launch {
            runCatching { UserPreferences(app.settingsDataStore).setUpdateSkippedTag(tag) }
        }
        dismiss()
    }
}

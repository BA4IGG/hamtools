package com.ham.tools.data.update

/**
 * 发布通道。
 *
 * - [STABLE]（默认，正式通道）：只跟踪 GitHub 的正式 Release；历史 beta 与后来
 *   归入测试通道的版本都不会触发升级提示。
 * - [BETA]（测试通道）：连同 Pre-release 一起跟踪，用于尝鲜，界面会明确标注预发布。
 *
 * 通道是用户可切换的偏好，写进 DataStore；切换后立刻按新通道重新检测一次。
 */
enum class UpdateChannel(val label: String, val key: String) {
    STABLE("正式通道", "stable"),
    BETA("测试通道", "beta");

    companion object {
        fun fromKey(raw: String?): UpdateChannel =
            entries.firstOrNull { it.key == raw } ?: STABLE
    }
}

/**
 * 一次 GitHub Release 的元数据。
 *
 * `version` 是从 tag 归一化出来的可比较版本号（`v1.3` → `1.3`）；
 * `notes` 保留 release body 原文（Markdown / HTML），由 [UpdateChecker.formatNotes] 排版。
 * `source` 记录真实来源，界面必须如实标注，不能把备用源的结果说成 API 直连。
 */
data class AppRelease(
    val tag: String,
    val version: String,
    val name: String,
    val notes: String,
    val publishedAt: String,
    val htmlUrl: String,
    val apkUrl: String?,
    val apkName: String?,
    val apkSize: Long,
    val prerelease: Boolean,
    val source: String,
    /**
     * true 表示数据来自不带 `prerelease` 字段的备用源（Atom），无法确认该版本
     * 属于正式还是测试通道。界面必须提示用户自行核对，不能冒充已确认的正式版。
     */
    val channelUnverified: Boolean = false
)

/** 更新日志的轻量排版单元：纯数据，Compose 层只按类型挑样式。 */
enum class ReleaseLineKind { HEADING, BULLET, TEXT }

data class ReleaseLine(val kind: ReleaseLineKind, val text: String)

enum class UpdatePhase { IDLE, CHECKING, AVAILABLE, UP_TO_DATE, SKIPPED, FAILED }

/**
 * 检测失败的原因分类。
 *
 * 铁律：界面必须区分「网络不通」与「GitHub 上游错误」，
 * 不能把限流、无 Release、解析失败混成一句「检测失败」。
 */
enum class UpdateFailure { OFFLINE, RATE_LIMITED, NO_RELEASE, UPSTREAM, BAD_DATA }

/** 全局更新检测状态。自动检测失败不弹窗，只在关于页卡片里如实显示。 */
data class UpdateStatus(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val release: AppRelease? = null,
    val latestVersion: String = "",
    val failure: UpdateFailure? = null,
    val lastCheckedAt: Long = 0L,
    /** true 表示这次弹窗只是用户主动「查看发布说明」，并没有检测到新版本。 */
    val notesOnly: Boolean = false,
    /** 本次检测所用的通道，界面据此显示通道说明与预发布标识。 */
    val channel: UpdateChannel = UpdateChannel.STABLE
)

/** 网络层结果；[Failure] 带 HTTP 状态码，便于归因与日志诊断。 */
sealed interface UpdateFetchResult {
    data class Success(val release: AppRelease) : UpdateFetchResult
    data class Failure(val reason: UpdateFailure, val httpCode: Int? = null) : UpdateFetchResult
}

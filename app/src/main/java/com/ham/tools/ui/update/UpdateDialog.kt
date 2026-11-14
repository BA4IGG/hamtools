package com.ham.tools.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ham.tools.R
import com.ham.tools.data.AppConfig
import com.ham.tools.data.update.AppRelease
import com.ham.tools.data.update.ReleaseLineKind
import com.ham.tools.data.update.UpdateChecker

/**
 * 有新版本时的弹窗：列出 GitHub Release 的更新日志与来源。
 *
 * 数据诚实：
 * - 明确标出数据来自 REST API 还是 Atom 备用源、发布日期、附件体积；
 * - 备用源没有 APK 附件信息时就不显示「安装包」这一行，不编造；
 * - 只做检测与跳转，不静默下载 / 不自动安装，提示用户自行核对签名。
 */
@Composable
fun UpdateDialog(
    release: AppRelease,
    localVersion: String,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    notesOnly: Boolean = false
) {
    val context = LocalContext.current
    val lines = remember(release.tag, release.notes) { UpdateChecker.formatNotes(release.notes) }
    val apkName = release.apkName

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (notesOnly) {
                    stringResource(R.string.update_dialog_title_notes, release.version)
                } else {
                    stringResource(R.string.update_dialog_title, release.version)
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (notesOnly) {
                        stringResource(R.string.update_dialog_same_version, release.version)
                    } else {
                        stringResource(
                            R.string.update_dialog_versions, localVersion, release.version
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(
                        R.string.update_dialog_source,
                        release.source,
                        UpdateChecker.formatDate(release.publishedAt)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (apkName != null) {
                    Text(
                        text = stringResource(
                            R.string.update_dialog_apk,
                            apkName,
                            UpdateChecker.formatSize(release.apkSize)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 测试通道的预发布、以及 Atom 备用源无法确认通道，都必须如实标出来，
                // 不能把一个「可能是 beta」的版本当成确定无疑的正式版推荐安装。
                if (release.prerelease) {
                    Text(
                        text = stringResource(R.string.update_dialog_prerelease),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (release.channelUnverified) {
                    Text(
                        text = stringResource(R.string.update_dialog_channel_unverified),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Text(
                    text = stringResource(R.string.update_dialog_notes),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (lines.isEmpty()) {
                        Text(
                            text = stringResource(R.string.update_dialog_no_notes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        lines.forEach { line ->
                            val heading = line.kind == ReleaseLineKind.HEADING
                            Text(
                                text = if (line.kind == ReleaseLineKind.BULLET) {
                                    "•  ${line.text}"
                                } else line.text,
                                style = if (heading) {
                                    MaterialTheme.typography.titleSmall
                                } else {
                                    MaterialTheme.typography.bodySmall
                                },
                                color = if (heading) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = if (heading) Modifier.padding(top = 4.dp) else Modifier
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.update_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 只是查看当前版本发布说明时，没有「跳过此版本」这个动作
                if (!notesOnly) {
                    TextButton(onClick = onSkip) {
                        Text(text = stringResource(R.string.update_action_skip))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                openReleasePage(context, release)
                onDismiss()
            }) {
                Text(
                    text = stringResource(
                        if (notesOnly) R.string.update_action_open_release
                        else R.string.update_action_download
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.update_action_later))
            }
        }
    )
}

/**
 * 优先打开发布页而不是直接下载 APK：国内网络下 Release 页面更稳，
 * 也便于用户自己核对签名、附件与更新日志。没有发布页链接时退回仓库主页。
 */
private fun openReleasePage(context: Context, release: AppRelease) {
    val url = release.htmlUrl.takeIf { it.isNotBlank() }
        ?: release.apkUrl?.takeIf { it.isNotBlank() }
        ?: AppConfig.GITHUB_REPO_URL
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

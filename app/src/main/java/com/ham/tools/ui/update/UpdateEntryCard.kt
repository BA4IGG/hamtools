package com.ham.tools.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ham.tools.R
import com.ham.tools.data.update.UpdateChannel
import com.ham.tools.data.update.UpdateFailure
import com.ham.tools.data.update.UpdatePhase
import com.ham.tools.data.update.UpdateStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 关于页的「检测更新」入口，也是检测结果的展示位与发布通道切换处。
 *
 * 自动检测失败只在这里以一行文字如实说明原因，不弹窗、不打扰用户；
 * 手动点击则忽略节流与「跳过此版本」，一定能得到明确结果。
 * 通道切换会立刻按新通道重新检测，界面同时写清两个通道各自的含义。
 */
@Composable
fun UpdateEntryCard(
    status: UpdateStatus,
    localVersion: String,
    channel: UpdateChannel,
    onChannelChange: (UpdateChannel) -> Unit,
    onClick: () -> Unit,
    onShowNotes: () -> Unit,
    modifier: Modifier = Modifier
) {
    val failed = status.phase == UpdatePhase.FAILED
    val available = status.phase == UpdatePhase.AVAILABLE
    val checking = status.phase == UpdatePhase.CHECKING
    val valueColor = when {
        failed -> MaterialTheme.colorScheme.error
        available -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        enabled = !checking,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.update_entry_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = statusText(status, localVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = valueColor
                    )
                }
                if (checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
            Text(
                text = if (status.lastCheckedAt <= 0L) {
                    stringResource(R.string.update_never_checked)
                } else {
                    stringResource(
                        R.string.update_last_checked,
                        formatCheckTime(status.lastCheckedAt)
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                text = stringResource(R.string.update_auto_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            // 发布通道：正式通道（默认）只看稳定版，测试通道包含 Pre-release
            Text(
                text = stringResource(R.string.update_channel_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 14.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                UpdateChannel.entries.forEach { item ->
                    val selected = channel == item
                    val label = stringResource(
                        if (item == UpdateChannel.STABLE) R.string.update_channel_stable
                        else R.string.update_channel_beta
                    )
                    val buttonModifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                    if (selected) {
                        Button(
                            onClick = { onChannelChange(item) },
                            enabled = !checking,
                            modifier = buttonModifier
                        ) { Text(text = label) }
                    } else {
                        OutlinedButton(
                            onClick = { onChannelChange(item) },
                            enabled = !checking,
                            modifier = buttonModifier
                        ) { Text(text = label) }
                    }
                }
            }
            Text(
                text = stringResource(
                    if (channel == UpdateChannel.STABLE) R.string.update_channel_stable_note
                    else R.string.update_channel_beta_note
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                text = stringResource(R.string.update_channel_switch_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            // 即使本机已是最新版，也能查看该 Release 的更新日志；不重新联网
            if (status.release != null && !checking) {
                TextButton(
                    onClick = onShowNotes,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.update_action_view_notes,
                            status.latestVersion.ifBlank { localVersion }
                        ),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun statusText(status: UpdateStatus, localVersion: String): String = when (status.phase) {
    UpdatePhase.IDLE -> stringResource(R.string.update_entry_desc)
    UpdatePhase.CHECKING -> stringResource(R.string.update_state_checking)
    UpdatePhase.UP_TO_DATE -> stringResource(
        R.string.update_state_latest,
        status.latestVersion.ifBlank { localVersion }
    )
    UpdatePhase.AVAILABLE -> stringResource(
        R.string.update_state_available,
        status.release?.version?.takeIf { it.isNotBlank() } ?: status.latestVersion
    )
    UpdatePhase.SKIPPED -> stringResource(
        R.string.update_state_skipped,
        status.release?.version?.takeIf { it.isNotBlank() } ?: status.latestVersion
    )
    UpdatePhase.FAILED -> stringResource(
        R.string.update_state_failed,
        failureText(status.failure)
    )
}

@Composable
private fun failureText(failure: UpdateFailure?): String = stringResource(
    when (failure) {
        UpdateFailure.OFFLINE -> R.string.update_failure_offline
        UpdateFailure.RATE_LIMITED -> R.string.update_failure_rate_limited
        UpdateFailure.NO_RELEASE -> R.string.update_failure_no_release
        UpdateFailure.UPSTREAM -> R.string.update_failure_upstream
        UpdateFailure.BAD_DATA -> R.string.update_failure_bad_data
        null -> R.string.update_failure_bad_data
    }
)

private fun formatCheckTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

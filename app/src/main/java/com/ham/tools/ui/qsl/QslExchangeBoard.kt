package com.ham.tools.ui.qsl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ham.tools.data.qsl.QslContact
import com.ham.tools.data.qsl.QslDirection
import com.ham.tools.data.qsl.QslExchange
import com.ham.tools.data.qsl.QslExchangeDraft
import com.ham.tools.data.qsl.QslMode
import com.ham.tools.data.qsl.QslResult
import com.ham.tools.data.qsl.QslStateMachine
import com.ham.tools.data.qsl.QslStatus
import com.ham.tools.ui.theme.AppShapes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 「发起交换」子页：填写一次通联，向对方发出卡片交换申请。 */
@Composable
fun QslNewExchange(
    viewModel: QslViewModel,
    mode: QslMode,
    prefill: String,
    onPrefillConsumed: () -> Unit,
    onSubmitted: () -> Unit,
    modifier: Modifier = Modifier
) {
    var peer by remember { mutableStateOf("") }
    var qsoDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var band by remember { mutableStateOf("") }
    var radioMode by remember { mutableStateOf("") }
    var rstSent by remember { mutableStateOf("59") }
    var rstRcvd by remember { mutableStateOf("59") }
    var note by remember { mutableStateOf("") }
    var recordOnly by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(prefill) {
        if (prefill.isNotBlank()) {
            peer = prefill
            onPrefillConsumed()
        }
    }
    LaunchedEffect(mode) { localError = null }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!viewModel.state.profile.configured) {
            Surface(
                shape = AppShapes.medium,
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    text = "建议先在「我的资料」填好名片信息并保存，对方接受交换后才能看到你的邮寄地址 / 卡片。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        QslSectionCard(
            title = "发起${mode.label}交换",
            subtitle = "填写与对方的那次通联，申请会以你的登录呼号发出"
        ) {
            QslField(
                value = peer,
                onValueChange = { peer = it.uppercase() },
                label = "对方呼号 *",
                placeholder = "例如 JA1ABC / W1AW",
                maxLength = 16
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QslField(
                    value = qsoDate,
                    onValueChange = { qsoDate = it },
                    label = "通联日期",
                    placeholder = "YYYY-MM-DD",
                    maxLength = 16,
                    modifier = Modifier.weight(1f)
                )
                QslField(
                    value = band,
                    onValueChange = { band = it },
                    label = "波段",
                    placeholder = "20m",
                    maxLength = 16,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QslField(
                    value = radioMode,
                    onValueChange = { radioMode = it },
                    label = "模式",
                    placeholder = "FT8 / SSB / CW",
                    maxLength = 16,
                    modifier = Modifier.weight(1f)
                )
                QslField(
                    value = rstSent,
                    onValueChange = { rstSent = it },
                    label = "RST 发出",
                    maxLength = 8,
                    modifier = Modifier.weight(1f)
                )
                QslField(
                    value = rstRcvd,
                    onValueChange = { rstRcvd = it },
                    label = "RST 收到",
                    maxLength = 8,
                    modifier = Modifier.weight(1f)
                )
            }
            QslField(
                value = note,
                onValueChange = { note = it },
                label = "给对方的附言",
                placeholder = "例如：确认通联，期待你的卡片！73",
                singleLine = false,
                minLines = 2,
                maxLength = 255,
                imeAction = ImeAction.Done
            )
            QslToggle(
                label = "仅记录到我的交换历史",
                description = "对方还没注册本服务、或只想自己留档时勾选；不会向对方发送申请，也不产生通知",
                checked = recordOnly,
                onCheckedChange = { recordOnly = it }
            )
            localError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = {
                    if (peer.trim().isEmpty()) {
                        localError = "请填写对方呼号"
                        return@Button
                    }
                    localError = null
                    viewModel.createExchange(
                        QslExchangeDraft(
                            mode = mode,
                            peerCallsign = peer,
                            qsoDate = qsoDate,
                            qsoBand = band,
                            qsoMode = radioMode,
                            rstSent = rstSent,
                            rstRcvd = rstRcvd,
                            note = note,
                            recordOnly = recordOnly
                        )
                    ) { ok ->
                        if (ok) {
                            peer = ""
                            note = ""
                            recordOnly = false
                            onSubmitted()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.medium
            ) {
                Text(text = if (recordOnly) "仅记录到历史" else "发送交换申请")
            }
        }

        QslSectionCard(title = "交换流程说明") {
            Text(
                text = if (mode == QslMode.PAPER) {
                    "① 你发出申请 → ② 对方接受（此后双方才能互看邮寄地址）→ ③ 标记已寄出 → ④ 双方确认收到后完成。" +
                        "纸质卡片需要邮寄，请只在愿意公开收件地址时使用本功能。"
                } else {
                    "① 你发出申请 → ② 对方接受后立即完成，双方互换已上传的电子卡片图。" +
                        "eQSL 不需要邮寄地址，全程在 App 内完成。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "对方如果开启通知权限且 App 在前台轮询或后台周期检查时在线，会收到一条本机生成的提醒；这不是服务器主动推送。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 「交换记录」子页：进行中的流转操作 + 完整的交换历史。 */
@Composable
fun QslExchangeList(
    viewModel: QslViewModel,
    mode: QslMode,
    modifier: Modifier = Modifier
) {
    var filter by remember { mutableIntStateOf(0) }
    var contactTarget by remember { mutableStateOf<Long?>(null) }
    var contact by remember { mutableStateOf<QslContact?>(null) }
    var contactError by remember { mutableStateOf<String?>(null) }
    var viewingCardId by remember { mutableStateOf<Long?>(null) }

    val all = viewModel.state.exchanges.filter { it.mode == mode }.sortedByDescending { it.id }
    val shown = when (filter) {
        0 -> all.filter { it.status.isOpen }
        1 -> all.filter { it.status == QslStatus.PENDING }
        else -> all
    }

    LaunchedEffect(contactTarget) {
        val id = contactTarget ?: return@LaunchedEffect
        contact = null
        contactError = null
        when (val result = viewModel.contact(id)) {
            is QslResult.Success -> contact = result.value
            is QslResult.Failure -> contactError = result.code.humanMessage(result.message)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("进行中", "待我处理", "全部历史").forEachIndexed { index, label ->
                FilledTonalButton(
                    onClick = { filter = index },
                    modifier = Modifier.weight(1f),
                    shape = AppShapes.small
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (filter == index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }
            }
        }
        Text(
            text = "共 ${shown.size} 条${mode.label}记录${if (viewModel.state.demo) "（演示）" else ""}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (shown.isEmpty()) {
            QslEmptyHint(
                text = when (filter) {
                    0 -> "当前没有进行中的交换，去「发起交换」试一试"
                    1 -> "没有待处理的申请"
                    else -> "还没有交换记录"
                }
            )
        } else {
            shown.forEach { exchange ->
                QslExchangeCard(
                    exchange = exchange,
                    onAction = { action -> viewModel.actOnExchange(exchange.id, action) },
                    onContact = { contactTarget = exchange.id },
                    onViewCard = { id -> viewingCardId = id }
                )
            }
        }
    }

    val target = contactTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { contactTarget = null },
            title = { Text(text = "对方的联系信息") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        contact != null -> {
                            val c = contact!!
                            ContactLine("呼号", c.callsign)
                            if (c.displayName.isNotBlank()) ContactLine("名称", c.displayName)
                            if (c.address.isNotBlank()) ContactLine("邮寄地址", c.address)
                            if (c.email.isNotBlank()) ContactLine("邮箱", c.email)
                            if (c.qth.isNotBlank()) ContactLine("QTH", c.qth)
                            if (c.grid.isNotBlank()) ContactLine("网格", c.grid)
                            Text(
                                text = "这些信息只在你接受交换后可见，请勿对外转发。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        contactError != null -> Text(
                            text = contactError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        else -> Text(text = "正在读取…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { contactTarget = null }) { Text(text = "关闭") }
            }
        )
    }

    val cardId = viewingCardId
    if (cardId != null) {
        AlertDialog(
            onDismissRequest = { viewingCardId = null },
            title = { Text(text = "对方上传的 QSL 卡片") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QslCardImage(
                        cardId = cardId,
                        load = { id -> viewModel.cardImage(id) },
                        imageHeight = 220.dp,
                        emptyText = if (viewModel.state.demo) {
                            "演示模式没有真实卡片文件，可在「我的资料」上传一张体验"
                        } else {
                            "图片暂时无法显示，请稍后重试"
                        }
                    )
                    Text(
                        text = "卡片图片只对本人与本次交换的对方可见，请勿对外转发。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingCardId = null }) { Text(text = "关闭") }
            }
        )
    }
}

@Composable
private fun ContactLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun QslExchangeCard(
    exchange: QslExchange,
    onAction: (String) -> Unit,
    onContact: () -> Unit,
    onViewCard: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val actions = QslStateMachine.availableActions(
        exchange.mode, exchange.status, exchange.direction
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exchange.peerCallsign,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${if (exchange.direction == QslDirection.INCOMING) "对方发起" else "我发起的"} · ${exchange.mode.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                QslStatusChip(status = exchange.status)
            }
            val qso = listOf(
                exchange.qsoDate, exchange.qsoBand, exchange.qsoMode,
                if (exchange.rstSent.isNotBlank()) "${exchange.rstSent}/${exchange.rstRcvd}" else ""
            ).filter { it.isNotBlank() }.joinToString(" · ")
            if (qso.isNotBlank()) {
                Text(text = qso, style = MaterialTheme.typography.bodySmall)
            }
            if (exchange.note.isNotBlank()) {
                Text(
                    text = exchange.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
            if (actions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    actions.forEach { (action, label) ->
                        FilledTonalButton(
                            onClick = { onAction(action) },
                            modifier = Modifier.weight(1f),
                            shape = AppShapes.small
                        ) {
                            Text(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
            if (exchange.status in listOf(
                    QslStatus.ACCEPTED, QslStatus.SENT, QslStatus.COMPLETED
                )
            ) {
                // fromCardId / toCardId 分别对应发起方与接收方的卡片，按方向取对方的
                val peerCardId = if (exchange.direction == QslDirection.OUTGOING) {
                    exchange.toCardId
                } else {
                    exchange.fromCardId
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (peerCardId != null) {
                        TextButton(onClick = { onViewCard(peerCardId) }) {
                            Text(text = "查看卡片")
                        }
                    }
                    TextButton(onClick = onContact) { Text(text = "查看邮寄信息") }
                }
            }
            Text(
                text = "更新于 ${exchange.updatedAt.take(16).replace('T', ' ')}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!exchange.registeredPeer) {
                Text(
                    text = "对方未注册，本条仅作为你自己的通联留档",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 「台站名录」子页：按呼号搜索已开放交换的台站，发起申请。 */
@Composable
fun QslDirectory(
    viewModel: QslViewModel,
    mode: QslMode,
    onPeerSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val users = viewModel.state.directory.filter {
        if (mode == QslMode.PAPER) it.paperEnabled else it.eqslEnabled
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QslSectionCard(
            title = "查找台站",
            subtitle = "只显示已登记 QSL 名片、且开放${mode.label}交换的账号"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QslField(
                    value = query,
                    onValueChange = { query = it.uppercase() },
                    label = "呼号",
                    placeholder = "留空显示全部",
                    maxLength = 16,
                    imeAction = ImeAction.Search,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { viewModel.searchDirectory(query) },
                    shape = AppShapes.medium
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "搜索"
                    )
                }
            }
            Text(
                text = "名录已脱敏：不含邮寄地址与邮箱，也不含任何位置轨迹。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (users.isEmpty()) {
            QslEmptyHint(text = "没有找到符合条件的台站${if (viewModel.state.demo) "（当前为演示名录）" else ""}")
        } else {
            users.forEach { user ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user.callsign,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = listOf(user.displayName, user.grid, user.qth)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · ")
                                        .ifBlank { "未填写 QTH" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (user.hasCard) {
                                Text(
                                    text = "有卡片",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (user.note.isNotBlank()) {
                            Text(
                                text = user.note,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        Text(
                            text = "开放：${listOfNotNull(
                                if (user.paperEnabled) "纸质" else null,
                                if (user.eqslEnabled) "eQSL" else null
                            ).joinToString(" / ").ifBlank { "暂未开放" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        TextButton(onClick = { onPeerSelected(user.callsign) }) {
                            Text(text = "向 TA 发起交换")
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

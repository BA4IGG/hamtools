package com.ham.tools.ui.qsl

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ham.tools.data.qsl.QslCard
import com.ham.tools.data.qsl.QslImageSniffer
import com.ham.tools.data.qsl.QslJson
import com.ham.tools.data.qsl.QslMode
import com.ham.tools.ui.theme.AppShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「我的资料」子页：填写 QSL 名片信息、上传当前板块的卡片图片。
 *
 * 纸质与 eQSL 共用一份个人信息（邮寄地址、邮箱不分模式），
 * 但卡片图片按板块分别管理：纸质板块上传的是纸卡扫描/照片，
 * eQSL 板块上传的是电子卡片图。
 */
@Composable
fun QslProfileEditor(
    viewModel: QslViewModel,
    mode: QslMode,
    modifier: Modifier = Modifier
) {
    val profile = viewModel.state.profile
    val cards = viewModel.state.cards.filter { it.mode == mode }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 表单是「草稿」：只在服务端资料版本真正变化时同步，
    // 否则用户正在打字时一次后台刷新就会把输入清空。
    var draft by remember { mutableStateOf(profile) }
    var syncedKey by remember { mutableStateOf("") }
    val versionKey = listOf(
        profile.callsign, profile.updatedAt, profile.configured.toString(),
        profile.cardId?.toString().orEmpty()
    ).joinToString("|")
    LaunchedEffect(versionKey) {
        if (syncedKey != versionKey) {
            draft = profile
            syncedKey = versionKey
        }
    }

    var localError by remember { mutableStateOf<String?>(null) }
    var profileError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) {
            picking = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            picking = false
            if (bytes == null) {
                localError = "读取图片失败，请换一张试试"
                return@launch
            }
            val check = QslImageSniffer.check(bytes)
            if (!check.ok) {
                localError = check.error ?: "图片格式不支持"
                return@launch
            }
            localError = null
            viewModel.uploadCard(mode, bytes, check.mime!!)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        QslSectionCard(
            title = "我的 QSL 名片",
            subtitle = "呼号取自登录账号，不可修改；其余信息保存在你的账号下"
        ) {
            QslField(
                value = profile.callsign.ifBlank { "—" },
                onValueChange = {},
                label = "呼号（登录账号）",
                enabled = false,
                imeAction = ImeAction.Next
            )
            QslField(
                value = draft.displayName,
                onValueChange = { draft = draft.copy(displayName = it) },
                label = "显示名称",
                placeholder = "例如：BA4IGG / 张三",
                maxLength = 64
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QslField(
                    value = draft.grid,
                    onValueChange = { draft = draft.copy(grid = it.uppercase()) },
                    label = "网格",
                    placeholder = "OM89",
                    maxLength = 8,
                    modifier = Modifier.weight(1f)
                )
                QslField(
                    value = draft.qth,
                    onValueChange = { draft = draft.copy(qth = it) },
                    label = "QTH / 所在地",
                    placeholder = "城市即可，无需详细地址",
                    maxLength = 128,
                    modifier = Modifier.weight(1f)
                )
            }
            QslField(
                value = draft.email,
                onValueChange = { draft = draft.copy(email = it) },
                label = "联系邮箱",
                placeholder = "用于 eQSL 与交换沟通",
                maxLength = 128
            )
            QslField(
                value = draft.address,
                onValueChange = { draft = draft.copy(address = it) },
                label = "邮寄地址（仅纸质交换需要）",
                placeholder = "对方接受交换后才会看到",
                singleLine = false,
                minLines = 3,
                maxLength = 500,
                imeAction = ImeAction.Done
            )
            QslField(
                value = draft.note,
                onValueChange = { draft = draft.copy(note = it) },
                label = "给对方的留言",
                placeholder = "例如：请回寄纸卡，谢谢！73",
                singleLine = false,
                minLines = 2,
                maxLength = 255,
                imeAction = ImeAction.Done
            )

            QslToggle(
                label = "接受纸质卡片交换",
                checked = draft.paperEnabled,
                onCheckedChange = { draft = draft.copy(paperEnabled = it) }
            )
            QslToggle(
                label = "接受 eQSL 电子卡片交换",
                checked = draft.eqslEnabled,
                onCheckedChange = { draft = draft.copy(eqslEnabled = it) }
            )
            QslToggle(
                label = "出现在台站名录",
                description = "关闭后别人无法主动找到你，但仍可通过呼号向你发起交换",
                checked = draft.discoverable,
                onCheckedChange = { draft = draft.copy(discoverable = it) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val issue = QslJson.validateProfile(draft)
                        if (issue != null) {
                            profileError = issue
                        } else {
                            profileError = null
                            viewModel.saveProfile(draft)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = AppShapes.medium
                ) {
                    Text(text = "保存资料")
                }
                FilledTonalButton(
                    onClick = {
                        draft = profile
                        profileError = null
                    },
                    shape = AppShapes.medium
                ) {
                    Text(text = "撤销修改")
                }
            }
            profileError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = "隐私：邮寄地址与邮箱不会出现在台站名录里，只有你接受某次交换后，对方才能在该条记录中查看。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        QslSectionCard(
            title = if (mode == QslMode.PAPER) "纸质卡片图片" else "eQSL 电子卡片图片",
            subtitle = "JPG / PNG / WebP，最大 5 MB；图片仅对本人与已建立交换的对方可见",
            action = {
                FilledTonalButton(
                    onClick = {
                        localError = null
                        picking = true
                        pickImage.launch("image/*")
                    },
                    enabled = !picking
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(text = "上传", modifier = Modifier.padding(start = 6.dp))
                }
            }
        ) {
            if (picking) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        text = "正在读取图片…",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
            localError?.let {
                Surface(
                    shape = AppShapes.small,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            if (cards.isEmpty()) {
                QslEmptyHint(text = "还没有上传${if (mode == QslMode.PAPER) "纸卡" else "电子卡片"}图片")
            } else {
                cards.forEach { card ->
                    QslCardRow(
                        card = card,
                        load = { id -> viewModel.cardImage(id) },
                        onDelete = { viewModel.deleteCard(card.id) }
                    )
                }
            }
        }

        QslSectionCard(title = "数据管理", subtitle = "删除后不可恢复") {
            Text(
                text = "删除会一并清除你在服务端的 QSL 名片、已上传卡片图片和全部交换记录；不会删除登录账号本身。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = { confirmDelete = true },
                shape = AppShapes.medium
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(text = "删除我的 QSL 资料", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(text = "删除全部 QSL 资料？") },
            text = { Text(text = "名片信息、卡片图片与交换历史都会被永久删除，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteProfile()
                }) { Text(text = "确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(text = "取消") }
            }
        )
    }
}

@Composable
private fun QslCardRow(
    card: QslCard,
    load: suspend (Long) -> ByteArray?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            QslCardImage(
                cardId = card.id,
                load = load,
                emptyText = "图片暂时无法显示，请下拉刷新重试"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "%.1f KB · %s".format(card.sizeBytes / 1024.0, card.createdAt.take(10)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                TextButton(onClick = onDelete) { Text(text = "删除") }
            }
        }
    }
}



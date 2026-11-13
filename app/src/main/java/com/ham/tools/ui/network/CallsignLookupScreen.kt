package com.ham.tools.ui.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ham.tools.data.reference.CallsignLookup
import com.ham.tools.data.remote.HamDataClient
import com.ham.tools.data.remote.OnlineCallsign
import com.ham.tools.ui.components.ActionButton
import com.ham.tools.ui.components.InfoSurface
import com.ham.tools.ui.components.KeyValueRow
import com.ham.tools.ui.components.NoteCard
import com.ham.tools.ui.components.SectionLabel
import com.ham.tools.ui.components.StatusBanner
import com.ham.tools.ui.components.ToolScaffold
import com.ham.tools.ui.components.ToolScrollColumn
import com.ham.tools.ui.theme.AppShapes
import com.ham.tools.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 呼号查询：离线前缀表解析 + HamDB 在线执照资料。
 *
 * 「归属信息」「后缀与中国分区」始终来自内置离线前缀表，断网也能用；
 * 在线查询是可选增强，成功才额外展示执照登记资料，失败如实标注，
 * 不会把离线结果伪装成在线数据。
 */
@Composable
fun CallsignLookupScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var input by rememberSaveable { mutableStateOf("BA4IGG") }
    val profile = remember(input) { CallsignLookup.parse(input) }

    var online by remember { mutableStateOf<OnlineCallsign?>(null) }
    var onlineStatus by rememberSaveable { mutableStateOf("尚未发起在线查询，下方为内置离线前缀表结果") }
    var onlineLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val client = remember { HamDataClient() }

    // 输入变化时清掉上一次的在线结果，避免把旧呼号的资料显示在新呼号下面
    LaunchedEffect(input) {
        online = null
        onlineStatus = "尚未发起在线查询，下方为内置离线前缀表结果"
    }

    fun lookupOnline() {
        if (onlineLoading) return
        val requested = input
        onlineLoading = true
        online = null
        onlineStatus = "正在查询 HamDB 在线呼号库…"
        scope.launch {
            val result = withContext(Dispatchers.IO) { client.fetchCallsign(requested) }
            // 查询期间用户改了呼号，就丢弃这次结果
            if (input != requested) {
                onlineLoading = false
                return@launch
            }
            online = result
            onlineStatus = if (result != null) {
                "在线查询成功 · ${result.source}"
            } else {
                "在线源不可用或该呼号无记录，下方仍为内置离线前缀表结果"
            }
            onlineLoading = false
        }
    }

    ToolScaffold(title = "呼号查询", onBack = onBack, modifier = modifier) { padding ->
        ToolScrollColumn(padding) {
            SectionLabel(text = "输入呼号")
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.uppercase() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "呼号，可带 /P、/M、/KH6 等后缀") },
                singleLine = true,
                shape = AppShapes.small
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("BA4IGG", "W1AW", "JA1ABC", "G3ABC").forEach { sample ->
                    FilledTonalButton(
                        onClick = { input = sample },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = sample, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (!profile.isValid) {
                StatusBanner(text = "未能识别该呼号的前缀，请检查拼写或换一个呼号", warning = true)
            }

            SectionLabel(text = "在线呼号资料（HamDB）")
            StatusBanner(
                text = onlineStatus,
                warning = onlineStatus.contains("不可用")
            )
            ActionButton(
                text = if (onlineLoading) "查询中…" else "在线查询呼号资料",
                onClick = { lookupOnline() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !onlineLoading,
                tonal = true
            )
            online?.let { info ->
                InfoSurface(title = info.callsign) {
                    KeyValueRow(label = "登记名称", value = info.name ?: "—")
                    KeyValueRow(label = "国家", value = info.country ?: "—")
                    KeyValueRow(label = "执照网格", value = info.grid ?: "—")
                    KeyValueRow(label = "执照等级", value = info.licenseClass ?: "—")
                    KeyValueRow(label = "数据来源", value = info.source)
                }
                StatusBanner(text = "在线资料是执照登记信息，不是电台的实时位置。")
            }

            SectionLabel(text = "归属信息（离线前缀表）")
            InfoSurface(title = profile.normalized.ifEmpty { "—" }) {
                KeyValueRow(label = "国家 / 实体", value = profile.entity)
                KeyValueRow(label = "英文名称", value = profile.entityEn)
                KeyValueRow(label = "大洲", value = profile.continent)
                KeyValueRow(label = "CQ 分区", value = profile.cqZone?.toString() ?: Format.PLACEHOLDER)
                KeyValueRow(label = "ITU 分区", value = profile.ituZone?.toString() ?: Format.PLACEHOLDER)
            }

            SectionLabel(text = "后缀与中国分区")
            InfoSurface {
                KeyValueRow(label = "斜杠后缀", value = profile.suffix ?: "无")
                KeyValueRow(label = "后缀含义", value = profile.suffixMeaning ?: "—")
                KeyValueRow(label = "中国呼号分区", value = profile.chinaRegion ?: "非中国呼号或不适用")
                KeyValueRow(label = "备注", value = profile.prefixNote ?: "—")
            }

            NoteCard(
                text = "归属信息来自内置离线前缀表（ITU 呼号序列划分与常用 DXCC 列表），按最长前缀优先匹配，因此 W1AW/KH6 会落到夏威夷而不是美国本土，断网也能用。在线资料来自 HamDB 免费公开 API，属执照登记信息，可能缺失或过期（对美国 FCC 执照覆盖最好），不能作为竞赛计分或奖状申请依据；这类用途请以 DXCC 官方最新列表为准。"
            )
        }
    }
}

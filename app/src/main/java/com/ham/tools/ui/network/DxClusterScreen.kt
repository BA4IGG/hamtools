package com.ham.tools.ui.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ham.tools.data.reference.DxSpot
import com.ham.tools.data.reference.DxSpotTable
import com.ham.tools.data.remote.HamDataClient
import com.ham.tools.ui.components.ActionButton
import com.ham.tools.ui.reference.EmptyResultHint
import com.ham.tools.ui.reference.EntryCard
import com.ham.tools.ui.reference.InfoRow
import com.ham.tools.ui.reference.ReferenceListScaffold
import com.ham.tools.ui.reference.SearchField
import com.ham.tools.ui.components.StatusBanner
import com.ham.tools.ui.components.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DX 集群：spots 列表 + 频段筛选 + 在线同步。
 *
 * 在线源按顺序尝试：自部署服务端聚合（国内可达）→ DXWatch 实时 DX cluster 页面。
 * 两者都不可用时保留内置 EXAMPLE 样例，并明确标注「非实时通联」，
 * 绝不把内置样例伪装成实时 spots。
 */
@Composable
fun DxClusterScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var band by rememberSaveable { mutableStateOf("全部") }
    var spots by remember { mutableStateOf(DxSpotTable.spots) }
    var loading by remember { mutableStateOf(false) }
    var source by rememberSaveable {
        mutableStateOf("内置示例数据，非实时通联信息")
    }
    val scope = rememberCoroutineScope()
    val client = remember { HamDataClient() }

    fun refresh() {
        if (loading) return
        loading = true
        scope.launch {
            val feed = withContext(Dispatchers.IO) { client.fetchSpots() }
            if (feed != null && feed.live && feed.spots.isNotEmpty()) {
                spots = feed.spots
                source = "实时数据 · ${feed.source}"
            } else {
                source = "在线源不可用，当前仍为内置 EXAMPLE 样例（非实时）"
            }
            loading = false
        }
    }

    val keyword = query.trim().uppercase()
    val filtered = remember(spots, keyword, band) {
        spots.filter { spot ->
            val matchesBand = band == "全部" || spot.bandLabel == band
            val matchesKeyword = keyword.isEmpty() ||
                spot.dxCallsign.contains(keyword) ||
                spot.spotter.contains(keyword) ||
                spot.comment.uppercase().contains(keyword)
            matchesBand && matchesKeyword
        }
    }
    val bands = listOf("全部", "160m", "80m", "40m", "30m", "20m", "17m", "15m", "12m", "10m")

    ToolScaffold(title = "DX 集群", onBack = onBack, modifier = modifier) { padding ->
        ReferenceListScaffold(
            innerPadding = padding,
            countLabel = "共 ${filtered.size} 条 spot",
            header = {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    label = "搜索呼号 / spotter / 备注"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    bands.take(5).forEach { item ->
                        FilledTonalButton(
                            onClick = { band = item },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = item,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (band == item) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    bands.drop(5).forEach { item ->
                        FilledTonalButton(
                            onClick = { band = item },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = item,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (band == item) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                        }
                    }
                }
                StatusBanner(text = "数据来源：$source", warning = source.contains("不可用"))
                ActionButton(
                    text = if (loading) "获取实时 spots…" else "刷新实时 spots",
                    onClick = { refresh() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                    tonal = true
                )
            },
            listContent = {
                if (filtered.isEmpty()) {
                    item { EmptyResultHint(text = "没有匹配的 spot，换个频段或关键词试试") }
                } else {
                    itemsIndexed(filtered) { index, spot ->
                        SpotCard(index = index, spot = spot)
                    }
                }
            }
        )
    }
}

@Composable
private fun SpotCard(index: Int, spot: DxSpot) {
    EntryCard(index = index) {
        InfoRow(label = "时间", value = spot.time, emphasize = true)
        InfoRow(label = "DX 呼号", value = spot.dxCallsign, emphasize = true)
        InfoRow(label = "频率", value = "%.1f kHz · %s".format(spot.freqKHz, spot.bandLabel))
        InfoRow(label = "Spotter", value = spot.spotter)
        InfoRow(label = "备注", value = spot.comment)
    }
}

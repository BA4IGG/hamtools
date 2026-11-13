package com.ham.tools.ui.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ham.tools.data.reference.BandQuality
import com.ham.tools.data.reference.SolarModel
import com.ham.tools.data.reference.SolarSnapshot
import com.ham.tools.data.remote.HamDataClient
import com.ham.tools.ui.components.ActionButton
import com.ham.tools.ui.components.InfoSurface
import com.ham.tools.ui.components.KeyValueRow
import com.ham.tools.ui.components.NoteCard
import com.ham.tools.ui.components.SectionLabel
import com.ham.tools.ui.components.StatusBanner
import com.ham.tools.ui.components.ToolScaffold
import com.ham.tools.ui.components.ToolScrollColumn
import com.ham.tools.ui.theme.AppShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 传播指数：太阳活动快照 + HF 波段经验评估。
 *
 * 在线数据为可选增强：hamqsl.com 与 NOAA SWPC 都是公开免密钥源，
 * 任一不可达就退回内置典型值，并在状态条里如实写明，绝不把离线值标成实时。
 */
@Composable
fun PropagationScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var snapshot by remember { mutableStateOf(SolarModel.FALLBACK) }
    var loading by remember { mutableStateOf(false) }
    var status by rememberSaveable {
        mutableStateOf("当前为内置典型值（非实时观测），可点击刷新尝试获取公开实时数据")
    }
    val scope = rememberCoroutineScope()
    val client = remember { HamDataClient() }

    fun refresh() {
        if (loading) return
        loading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { client.fetchSolar() }
            if (result != null) {
                snapshot = result
                status = "已获取实时数据 · ${result.source}"
            } else {
                status = "在线数据源暂不可用（可能需要联网或数据源维护），已保留内置典型值"
            }
            loading = false
        }
    }

    val forecast = remember(snapshot) { SolarModel.forecast(snapshot.sfi, snapshot.kp) }

    ToolScaffold(title = "传播指数", onBack = onBack, modifier = modifier) { padding ->
        ToolScrollColumn(padding) {
            StatusBanner(text = status, warning = status.contains("不可用"))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionButton(
                    text = if (loading) "获取中…" else "刷新实时数据",
                    onClick = { refresh() },
                    modifier = Modifier.weight(1f),
                    enabled = !loading
                )
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                }
            }

            SectionLabel(text = "太阳与地磁活动")
            InfoSurface(title = snapshot.source) {
                KeyValueRow(label = "太阳通量 SFI", value = "${snapshot.sfi} sfu")
                KeyValueRow(label = "行星 K 指数", value = "${snapshot.kp} · ${SolarModel.kpDescription(snapshot.kp)}")
                KeyValueRow(label = "A 指数", value = "${snapshot.aIndex}")
                KeyValueRow(label = "太阳黑子数", value = "${snapshot.sunspots}")
                KeyValueRow(label = "数据时间", value = snapshot.updatedAt)
            }

            SectionLabel(text = "HF 波段评估")
            forecast.forEach { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.medium,
                    color = when (item.quality) {
                        BandQuality.GOOD -> MaterialTheme.colorScheme.primaryContainer
                        BandQuality.FAIR -> MaterialTheme.colorScheme.secondaryContainer
                        BandQuality.POOR -> MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = item.band, style = MaterialTheme.typography.titleSmall)
                        Text(text = item.quality.label, style = MaterialTheme.typography.titleSmall)
                    }
                }
                Text(
                    text = item.advice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            NoteCard(
                text = "波段评估是基于 SFI 与 Kp 的经验估算，不是电离层实测预报。真实开通还取决于昼夜、季节、路径、太阳高度角与天线条件，请结合实测通联判断。"
            )
        }
    }
}

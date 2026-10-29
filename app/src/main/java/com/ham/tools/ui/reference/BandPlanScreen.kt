package com.ham.tools.ui.reference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ham.tools.R
import com.ham.tools.data.reference.BandPlanEntry
import com.ham.tools.data.reference.BandPlanTable
import com.ham.tools.data.reference.BandRegion
import com.ham.tools.ui.components.NoteCard
import com.ham.tools.ui.components.SectionLabel
import com.ham.tools.ui.components.ToolScaffold
import com.ham.tools.ui.components.UnitSelector
import com.ham.tools.ui.components.rememberDebounced
import com.ham.tools.util.Format

/**
 * 业余频段划分速查。
 *
 * 页面要点在「先按区域切，再按关键词收窄」：多数人查频段时关心的是
 * *自己所在区域*能不能用某段，所以区域是主筛选、搜索是辅筛选，
 * 而非反过来。默认落在中国（[BandRegion.CN]）。
 */
@Composable
fun BandPlanScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var regionIndex by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val keyword = rememberDebounced(query)

    val region = BandRegion.entries[regionIndex]
    val results = remember(region, keyword) { BandPlanTable.search(region, keyword) }

    ToolScaffold(
        title = stringResource(R.string.tool_band_plan),
        onBack = onBack,
        modifier = modifier
    ) { padding ->
        ReferenceListScaffold(
            innerPadding = padding,
            countLabel = stringResource(R.string.ref_result_count, results.size),
            header = {
                SectionLabel(text = stringResource(R.string.group_input))
                UnitSelector(
                    units = BandRegion.entries.map { regionLabel(it) },
                    selectedIndex = regionIndex,
                    onSelected = { regionIndex = it }
                )
                SearchField(value = query, onValueChange = { query = it })
            }
        ) {
            if (results.isEmpty()) {
                item { EmptyResultHint() }
            } else {
                itemsIndexed(results, key = { _, item -> "${region.name}_${item.band}" }) { index, entry ->
                    BandPlanCard(index = index, entry = entry)
                }
            }
            item {
                NoteCard(
                    text = stringResource(R.string.note_band_plan),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun BandPlanCard(index: Int, entry: BandPlanEntry, modifier: Modifier = Modifier) {
    EntryCard(index = index, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.band,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = bandRange(entry),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(modifier = Modifier.padding(top = 8.dp)) {
            InfoRow(
                label = stringResource(R.string.band_plan_modes),
                value = entry.modes,
                emphasize = true
            )
            InfoRow(
                label = stringResource(R.string.band_plan_usage),
                value = entry.usage
            )
            entry.note?.let {
                InfoRow(
                    label = stringResource(R.string.band_plan_note),
                    value = it
                )
            }
        }
    }
}

/**
 * 频率范围格式化。
 *
 * 小数位按量级自适应：2200m 的 0.1357–0.1378 需要 4 位才不丢精度，
 * 而 23cm 的 1240–1300 带小数纯属噪音。统一固定位数会让一头失真、
 * 一头冗余，所以这里分段取。
 */
private fun bandRange(entry: BandPlanEntry): String {
    val digits = when {
        entry.endMHz >= 1000.0 -> 0
        entry.endMHz >= 100.0 -> 3
        entry.endMHz >= 10.0 -> 3
        entry.endMHz >= 1.0 -> 3
        else -> 4
    }
    return "${Format.number(entry.startMHz, digits)} – ${Format.number(entry.endMHz, digits)} MHz"
}

@Composable
private fun regionLabel(region: BandRegion): String = when (region) {
    BandRegion.CN -> stringResource(R.string.region_cn)
    BandRegion.R1 -> stringResource(R.string.region_r1)
    BandRegion.R2 -> stringResource(R.string.region_r2)
    BandRegion.R3 -> stringResource(R.string.region_r3)
}

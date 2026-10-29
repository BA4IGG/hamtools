package com.ham.tools.ui.reference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import com.ham.tools.data.reference.RepeaterEntry
import com.ham.tools.data.reference.SatRepeaterTable
import com.ham.tools.data.reference.SatelliteEntry
import com.ham.tools.ui.components.NoteCard
import com.ham.tools.ui.components.SectionLabel
import com.ham.tools.ui.components.ToolScaffold
import com.ham.tools.ui.components.rememberDebounced
import com.ham.tools.ui.theme.AppShapes
import com.ham.tools.util.Format

/**
 * 卫星与中继名录。
 *
 * 两个 Tab 的数据性质差别很大，所以呈现方式也不同：
 * - **卫星**：条目少但每条信息多，用大卡片把上下行、亚音、信标一次给全
 * - **中继**：条目多而信息少，按大区分组 + 紧凑行
 *
 * 两处都保留了免责入口（[NoteCard]）：卫星会退役、中继会改频，
 * 与其假装数据永远正确，不如把「请核实」写在明面上。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatRepeaterScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(0) }

    ToolScaffold(
        title = stringResource(R.string.tool_sat_repeater),
        onBack = onBack,
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(text = stringResource(R.string.sat_tab_satellite)) }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(text = stringResource(R.string.sat_tab_repeater)) }
                )
            }
            when (tab) {
                0 -> SatellitePane(modifier = Modifier.weight(1f))
                else -> RepeaterPane(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SatellitePane(modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyword = rememberDebounced(query)
    val results = remember(keyword) { SatRepeaterTable.searchSatellite(keyword) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionLabel(text = stringResource(R.string.group_input))
            SearchField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.sat_search_label)
            )
        }

        SectionLabel(
            text = stringResource(R.string.ref_result_count, results.size),
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (results.isEmpty()) {
                item { EmptyResultHint() }
            } else {
                itemsIndexed(results, key = { _, item -> item.name }) { index, entry ->
                    SatelliteCard(index = index, entry = entry)
                }
            }
            item { NoteCard(text = stringResource(R.string.note_satellite)) }
        }
    }
}

@Composable
private fun SatelliteCard(index: Int, entry: SatelliteEntry, modifier: Modifier = Modifier) {
    EntryCard(index = index, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = entry.mode,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        entry.alias?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(modifier = Modifier.padding(top = 6.dp)) {
            InfoRow(
                label = stringResource(R.string.sat_uplink),
                value = entry.uplinkMHz?.let { "${Format.number(it, 3)} MHz" }.orEmpty(),
                emphasize = true
            )
            InfoRow(
                label = stringResource(R.string.sat_downlink),
                value = entry.downlinkMHz?.let { "${Format.number(it, 3)} MHz" }.orEmpty(),
                emphasize = true
            )
            entry.toneHz?.let {
                InfoRow(
                    label = stringResource(R.string.sat_tone),
                    value = "${Format.number(it, 1)} Hz"
                )
            }
            entry.beaconMHz?.let {
                InfoRow(
                    label = stringResource(R.string.sat_beacon),
                    value = "${Format.number(it, 3)} MHz"
                )
            }
            entry.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun RepeaterPane(modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyword = rememberDebounced(query)

    val total = remember(keyword) { SatRepeaterTable.searchRepeater(keyword).size }
    val grouped = remember(keyword) {
        SatRepeaterTable.groupByRegion(SatRepeaterTable.searchRepeater(keyword))
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionLabel(text = stringResource(R.string.group_input))
            SearchField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.rpt_search_label)
            )
        }

        SectionLabel(
            text = stringResource(R.string.ref_result_count, total),
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (grouped.isEmpty()) {
                item { EmptyResultHint() }
            } else {
                grouped.forEach { (region, list) ->
                    item {
                        SectionLabel(
                            text = region,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                    items(list, key = { "${it.city}_${it.downlinkMHz}" }) { entry ->
                        RepeaterRow(entry = entry)
                    }
                }
            }
            item { NoteCard(text = stringResource(R.string.note_repeater)) }
        }
    }
}

@Composable
private fun RepeaterRow(entry: RepeaterEntry, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.city,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = entry.mode,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(modifier = Modifier.padding(top = 6.dp)) {
                Text(
                    text = stringResource(
                        R.string.rpt_freq_pair,
                        Format.number(entry.downlinkMHz, 3),
                        Format.number(entry.uplinkMHz, 3)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = entry.toneHz?.let { "${Format.number(it, 1)} Hz" }
                        ?: stringResource(R.string.rpt_no_tone),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            entry.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

package com.ham.tools.ui.reference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ham.tools.R
import com.ham.tools.data.reference.PrefixEntry
import com.ham.tools.data.reference.PrefixTable
import com.ham.tools.ui.components.NoteCard
import com.ham.tools.ui.components.ResultCard
import com.ham.tools.ui.components.ResultItem
import com.ham.tools.ui.components.SectionLabel
import com.ham.tools.ui.components.ToolScaffold
import com.ham.tools.ui.components.UnitSelector
import com.ham.tools.ui.components.contentEnter
import com.ham.tools.ui.components.rememberDebounced
import com.ham.tools.ui.theme.AppShapes
import com.ham.tools.util.Format

/** 呼号输入框允许的最大长度，够放下最长呼号加斜杠后缀。 */
private const val MAX_CALLSIGN_LENGTH = 14

/**
 * 呼号前缀检索。
 *
 * 用两个 Tab 而不是上下分区，因为这两块各自都需要完整高度：
 * - **解析**：给一个呼号，告诉我它属于哪个实体
 * - **名录**：按大洲与关键词翻找前缀
 *
 * 若压成一屏，解析结果卡片会吃掉大半屏，名录只剩两三行可滚区域，两边都难受。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrefixScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(0) }

    ToolScaffold(
        title = stringResource(R.string.tool_callsign_prefix),
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
                    text = { Text(text = stringResource(R.string.prefix_tab_resolve)) }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(text = stringResource(R.string.prefix_tab_directory)) }
                )
            }
            when (tab) {
                0 -> ResolvePane(modifier = Modifier.weight(1f))
                else -> DirectoryPane(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 解析页：输入呼号，直接给出归属实体。 */
@Composable
private fun ResolvePane(modifier: Modifier = Modifier) {
    var callsignText by rememberSaveable { mutableStateOf("") }
    val callsignInput = rememberDebounced(callsignText)
    val resolved = remember(callsignInput) { PrefixTable.lookup(callsignInput) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionLabel(text = stringResource(R.string.group_input))
        OutlinedTextField(
            value = callsignText,
            onValueChange = { callsignText = it.uppercase().take(MAX_CALLSIGN_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.prefix_input_label)) },
            placeholder = { Text(text = "BA4IGG") },
            singleLine = true,
            shape = AppShapes.small,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done
            )
        )

        if (resolved != null) {
            SectionLabel(text = stringResource(R.string.group_result))
            ResultCard(
                items = listOf(
                    ResultItem(
                        label = stringResource(R.string.prefix_entity),
                        value = resolved.entity,
                        highlight = true
                    ),
                    ResultItem(
                        label = stringResource(R.string.prefix_entity_en),
                        value = resolved.entityEn
                    ),
                    ResultItem(
                        label = stringResource(R.string.prefix_continent),
                        value = continentLabel(resolved.continent)
                    ),
                    ResultItem(
                        label = stringResource(R.string.prefix_cq),
                        value = resolved.cqZone?.toString() ?: Format.PLACEHOLDER
                    ),
                    ResultItem(
                        label = stringResource(R.string.prefix_itu),
                        value = resolved.ituZone?.toString() ?: Format.PLACEHOLDER
                    )
                )
            )
            resolved.note?.let { NoteCard(text = it) }
        } else if (callsignInput.isNotBlank()) {
            EmptyResultHint(text = stringResource(R.string.prefix_unresolved))
        } else {
            NoteCard(text = stringResource(R.string.note_prefix))
        }
    }
}

/** 名录页：大洲筛选 + 搜索 + 长列表。 */
@Composable
private fun DirectoryPane(modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var continentIndex by rememberSaveable { mutableStateOf(0) }
    val keyword = rememberDebounced(query)

    val results = remember(keyword, continentIndex) {
        val code = CONTINENT_CODES[continentIndex]
        val searched = PrefixTable.search(keyword)
        if (code == null) searched else searched.filter { it.continent == code }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionLabel(text = stringResource(R.string.group_input))
            UnitSelector(
                units = CONTINENT_CODES.map { continentLabel(it) },
                selectedIndex = continentIndex,
                onSelected = { continentIndex = it }
            )
            SearchField(value = query, onValueChange = { query = it })
        }

        SectionLabel(
            text = stringResource(R.string.ref_result_count, results.size),
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (results.isEmpty()) {
                item { EmptyResultHint() }
            } else {
                items(results, key = { it.prefix }) { entry ->
                    PrefixRow(entry = entry)
                }
            }
        }
    }
}

/** 名录里的一行。 */
@Composable
private fun PrefixRow(entry: PrefixEntry, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = entry.prefix,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(PREFIX_WIDTH)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.entity,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = entry.entityEn,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${continentLabel(entry.continent)} · CQ ${entry.cqZone ?: "—"} · ITU ${entry.ituZone ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 大洲筛选顺序；null 表示「全部」。 */
private val CONTINENT_CODES: List<String?> = listOf(
    null, "AS", "EU", "AF", "NA", "SA", "OC", "AN"
)

@Composable
private fun continentLabel(code: String?): String = when (code) {
    "AS" -> stringResource(R.string.continent_as)
    "EU" -> stringResource(R.string.continent_eu)
    "AF" -> stringResource(R.string.continent_af)
    "NA" -> stringResource(R.string.continent_na)
    "SA" -> stringResource(R.string.continent_sa)
    "OC" -> stringResource(R.string.continent_oc)
    "AN" -> stringResource(R.string.continent_an)
    else -> stringResource(R.string.continent_all)
}

private val PREFIX_WIDTH = 52.dp

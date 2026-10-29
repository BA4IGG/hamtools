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
import com.ham.tools.data.reference.PhoneticEntry
import com.ham.tools.data.reference.QCodeEntry
import com.ham.tools.data.reference.QCodeTable
import com.ham.tools.ui.components.NoteCard
import com.ham.tools.ui.components.SectionLabel
import com.ham.tools.ui.components.ToolScaffold
import com.ham.tools.ui.components.rememberDebounced
import com.ham.tools.ui.theme.AppShapes

/**
 * Q 简语与字母解释法。
 *
 * 这两样东西的共同点是「在信号差、口音杂时把关键信息说清楚」，
 * 因此放在一起。展示时把 Q 简语的**提问 / 陈述两面都列出来** ——
 * 只背一半，收到对方 `QTH?` 时未必反应得过来。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QCodeScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(0) }

    ToolScaffold(
        title = stringResource(R.string.tool_qcode),
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
                    text = { Text(text = stringResource(R.string.qcode_tab_q)) }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(text = stringResource(R.string.qcode_tab_phonetic)) }
                )
            }
            when (tab) {
                0 -> QCodePane(modifier = Modifier.weight(1f))
                else -> PhoneticPane(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QCodePane(modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyword = rememberDebounced(query)
    val results = remember(keyword) { QCodeTable.searchQCode(keyword) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionLabel(text = stringResource(R.string.group_input))
            SearchField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.qcode_search_label)
            )
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
                items(results, key = { it.code + it.question }) { entry ->
                    QCodeCard(entry = entry)
                }
            }
            item { NoteCard(text = stringResource(R.string.note_qcode)) }
        }
    }
}

@Composable
private fun QCodeCard(entry: QCodeEntry, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Text(
                text = entry.code,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (entry.question.isNotBlank()) {
                InfoRow(
                    label = stringResource(R.string.qcode_question),
                    value = entry.question,
                    emphasize = true
                )
            }
            InfoRow(
                label = stringResource(R.string.qcode_statement),
                value = entry.statement,
                emphasize = true
            )
        }
    }
}

@Composable
private fun PhoneticPane(modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyword = rememberDebounced(query)
    val results = remember(keyword) { QCodeTable.searchPhonetic(keyword) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionLabel(text = stringResource(R.string.group_input))
            SearchField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.qcode_phonetic_search_label)
            )
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
                items(results, key = { "phonetic_${it.letter}" }) { entry ->
                    PhoneticRow(entry = entry)
                }
            }
            item { NoteCard(text = stringResource(R.string.note_phonetic)) }
        }
    }
}

@Composable
private fun PhoneticRow(entry: PhoneticEntry, modifier: Modifier = Modifier) {
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
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = entry.letter.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(LETTER_WIDTH)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.word,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = entry.hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            entry.variant?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private val LETTER_WIDTH = 32.dp

package com.ham.tools.ui.reference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.ham.tools.data.reference.MorseEntry
import com.ham.tools.data.reference.MorseTable
import com.ham.tools.ui.components.NoteCard
import com.ham.tools.ui.components.SectionLabel
import com.ham.tools.ui.components.ToolScaffold
import com.ham.tools.ui.components.UnitSelector
import com.ham.tools.ui.components.contentEnter
import com.ham.tools.ui.components.rememberDebounced
import com.ham.tools.ui.theme.AppShapes
import com.ham.tools.util.MorsePlayer

/** 电码输入框里允许出现的字符，用来过滤掉无法编码的输入。 */
private val MORSE_INPUT_FILTER = Regex("[^.\\-\\s/]")

/**
 * 摩尔斯电码对照与发声。
 *
 * 这一页是查询手册里唯一带硬件输出的：**把文本真的发出声音**。
 * 之所以值得做，是因为摩尔斯的学习效率高度依赖「听」——
 * 对照表只能帮记忆，听抄能力得靠耳朵。
 *
 * 速度默认 15 WPM，这是科赫法训练的常用起点：慢到能分辨点划，
 * 又不至于慢到让人按「点划」逐个翻译而非整体识别。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorseScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(0) }

    ToolScaffold(
        title = stringResource(R.string.tool_morse_code),
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
                    text = { Text(text = stringResource(R.string.morse_tab_convert)) }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(text = stringResource(R.string.morse_tab_table)) }
                )
            }
            when (tab) {
                0 -> ConvertPane(modifier = Modifier.weight(1f))
                else -> TablePane(modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConvertPane(modifier: Modifier = Modifier) {
    var direction by rememberSaveable { mutableStateOf(0) }
    var textInput by rememberSaveable { mutableStateOf("") }
    var codeInput by rememberSaveable { mutableStateOf("") }
    var wpm by rememberSaveable { mutableStateOf(15) }

    val textValue = rememberDebounced(textInput)
    val codeValue = rememberDebounced(codeInput)

    val encoded = remember(textValue) { MorseTable.encode(textValue) }
    val decoded = remember(codeValue) { MorseTable.decode(codeValue) }

    // 两种模式都要能发声：文字模式直接拼时序，电码模式先解出文字再拼
    val playSource = if (direction == 0) textValue else decoded
    val symbols = remember(playSource, wpm) { MorseTable.toSymbols(playSource, wpm) }
    val durationMs = remember(symbols) { symbols.sumOf { it.durationMs } }

    val player = remember { MorsePlayer() }
    var playing by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(player) {
        onDispose {
            player.release()
            playing = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionLabel(text = stringResource(R.string.group_input))
        UnitSelector(
            units = listOf(
                stringResource(R.string.morse_dir_to_code),
                stringResource(R.string.morse_dir_to_text)
            ),
            selectedIndex = direction,
            onSelected = {
                direction = it
                player.stop()
                playing = false
            }
        )

        if (direction == 0) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.morse_input_text)) },
                placeholder = { Text(text = "CQ CQ DE BA4IGG") },
                singleLine = true,
                shape = AppShapes.small,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                )
            )
        } else {
            OutlinedTextField(
                value = codeInput,
                onValueChange = { codeInput = it.replace(MORSE_INPUT_FILTER, "") },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.morse_input_code)) },
                placeholder = { Text(text = "-.-. --.- / -.. .") },
                singleLine = true,
                shape = AppShapes.small,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                )
            )
        }

        SectionLabel(text = stringResource(R.string.morse_speed))
        Text(
            text = stringResource(R.string.morse_speed_value, wpm),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Slider(
            value = wpm.toFloat(),
            onValueChange = { wpm = it.toInt() },
            valueRange = 5f..40f,
            steps = 34
        )
        Text(
            text = stringResource(R.string.morse_duration, durationMs / 1000.0),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = {
                if (playing) {
                    player.stop()
                    playing = false
                } else {
                    playing = player.play(symbols) { playing = false }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = symbols.isNotEmpty()
        ) {
            Icon(
                imageVector = if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null
            )
            Text(
                text = stringResource(
                    if (playing) R.string.morse_action_stop else R.string.morse_action_play
                ),
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        SectionLabel(text = stringResource(R.string.group_result))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(contentEnter(delayMillis = 60)),
            shape = AppShapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = (if (direction == 0) encoded else decoded).ifBlank { "—" },
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium
            )
        }

        SectionLabel(text = stringResource(R.string.morse_quick_words))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MorseTable.commonCallsigns.take(6).forEach { word ->
                FilledTonalButton(
                    onClick = {
                        textInput = word
                        codeInput = MorseTable.encode(word).replace(" / ", "/")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = word, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        NoteCard(text = stringResource(R.string.note_morse))
    }
}

@Composable
private fun TablePane(modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyword = rememberDebounced(query)

    // 分组标题要在 Composable 上下文里取；remember 的计算块不是 Composable 作用域
    val lettersTitle = stringResource(R.string.morse_group_letter)
    val digitsTitle = stringResource(R.string.morse_group_digit)
    val punctTitle = stringResource(R.string.morse_group_punct)
    val prosignsTitle = stringResource(R.string.morse_group_prosign)

    val groups = remember(keyword, lettersTitle, digitsTitle, punctTitle, prosignsTitle) {
        val k = keyword.trim().uppercase()
        fun filter(list: List<MorseEntry>) =
            if (k.isEmpty()) list else list.filter { it.char == k.firstOrNull() || it.code.contains(k) }

        listOf(
            lettersTitle to filter(MorseTable.letters),
            digitsTitle to filter(MorseTable.digits),
            punctTitle to filter(MorseTable.punctuations),
            prosignsTitle to filter(MorseTable.prosigns)
        ).filter { it.second.isNotEmpty() }
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
                label = stringResource(R.string.morse_search_label)
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (groups.isEmpty()) {
                item { EmptyResultHint() }
            } else {
                groups.forEach { (title, list) ->
                    item {
                        SectionLabel(
                            text = title,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                    // 每行排 2 条，短条目挤在整行太浪费横向空间
                    items(list.chunked(2)) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            row.forEach { entry ->
                                MorseCell(entry = entry, modifier = Modifier.weight(1f))
                            }
                            repeat(2 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MorseCell(entry: MorseEntry, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = AppShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = entry.char.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(CELL_CHAR_WIDTH)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.code,
                    style = MaterialTheme.typography.titleSmall
                )
                entry.note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private val CELL_CHAR_WIDTH = 22.dp

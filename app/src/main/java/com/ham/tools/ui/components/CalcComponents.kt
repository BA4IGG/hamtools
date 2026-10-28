package com.ham.tools.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ham.tools.R
import com.ham.tools.ui.theme.AppShapes
import com.ham.tools.util.Format

/** 所有工具页统一的脚手架：返回栏 + 可滚动内容区。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = title, style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        content = content
    )
}

/** 工具页统一滚动容器，自动消费 Scaffold 留下的内边距。 */
@Composable
fun ToolScrollColumn(
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = { content() }
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(start = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

/** 数值输入框，自动过滤非法字符。 */
@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(Format.sanitizeNumeric(it)) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = label) },
        suffix = unit?.let { { Text(text = it) } },
        singleLine = true,
        isError = isError,
        supportingText = {
            if (isError) {
                Text(text = stringResource(R.string.hint_invalid_input))
            }
        },
        shape = AppShapes.small,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

/** 单位切换用的分段按钮。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitSelector(
    units: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        units.forEachIndexed { index, unit ->
            SegmentedButton(
                selected = selectedIndex == index,
                onClick = { onSelected(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = units.size)
            ) {
                Text(text = unit)
            }
        }
    }
}

/** 单选列表，支持副标题。 */
@Composable
fun RadioOptions(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    supporting: @Composable (Int) -> String? = { null }
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            options.forEachIndexed { index, option ->
                val selectedBg by animateColorAsState(
                    targetValue = if (selectedIndex == index) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(200),
                    label = "radioBackground"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .clip(AppShapes.small)
                        .background(selectedBg)
                        .selectable(
                            selected = selectedIndex == index,
                            onClick = { onSelected(index) }
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedIndex == index,
                        onClick = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                        Text(text = option, style = MaterialTheme.typography.bodyLarge)
                        supporting(index)?.let { sub ->
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

data class ResultItem(
    val label: String,
    val value: String,
    val unit: String? = null,
    val highlight: Boolean = false
)

@Composable
fun ResultCard(items: List<ResultItem>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(contentEnter(delayMillis = 80)),
        shape = AppShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                ResultRow(item = item)
            }
        }
    }
}

@Composable
private fun ResultRow(item: ResultItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedContent(
                targetState = item.value,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(Motion.VALUE_SWAP_MS)) +
                        slideInVertically(animationSpec = tween(Motion.VALUE_SWAP_MS)) { it / 4 })
                        .togetherWith(
                            fadeOut(animationSpec = tween(Motion.VALUE_SWAP_MS)) +
                                slideOutVertically(animationSpec = tween(Motion.VALUE_SWAP_MS)) { -it / 4 }
                        )
                },
                label = "resultValueSwap"
            ) { value ->
                Text(
                    text = value,
                    style = if (item.highlight) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    color = if (item.highlight) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            item.unit?.let { unit ->
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 1.dp)
                )
            }
        }
    }
}

@Composable
fun NoteCard(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(contentEnter(delayMillis = 140)),
        shape = AppShapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

package com.ham.tools.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ham.tools.BuildConfig
import com.ham.tools.R
import com.ham.tools.data.AppConfig
import com.ham.tools.data.tools.ToolRegistry
import com.ham.tools.model.ToolMeta
import com.ham.tools.ui.components.Motion
import com.ham.tools.ui.components.staggerEnter
import com.ham.tools.ui.theme.HamToolsTheme
import com.ham.tools.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onToolClick: (String) -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    // 只在“是否滚动过”这个布尔值翻转时才触发重组，避免滚动全程重绘
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 4
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            HomeTopAppBar(
                scrolled = scrolled,
                onAboutClick = onAboutClick
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            var itemIndex = 0

            ToolRegistry.categories.forEach { category ->
                val titleIndex = itemIndex++
                item(key = "title_${category.name}") {
                    CategoryTitle(
                        text = category.label,
                        modifier = staggerEnter(titleIndex)
                    )
                }

                ToolRegistry.toolsOf(category).chunked(2).forEach { rowTools ->
                    val rowIndex = itemIndex++
                    val rowKey = rowTools.joinToString(separator = "_") { it.id }
                    item(key = rowKey) {
                        ToolRow(
                            tools = rowTools,
                            onToolClick = onToolClick,
                            modifier = staggerEnter(rowIndex)
                        )
                    }
                }
            }

            item(key = "bottom_gap") {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopAppBar(
    scrolled: Boolean,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (scrolled) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "topBarContainer"
    )
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (scrolled) 0f else 1f,
        animationSpec = Motion.standard(200),
        label = "subtitleAlpha"
    )

    TopAppBar(
        modifier = modifier,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = stringResource(R.string.home_subtitle),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { alpha = subtitleAlpha }
                    )
                }
            }
        },
        actions = {
            VersionAuthorBadge(onClick = onAboutClick)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor
        )
    )
}

/** 右上角署名：软件版本号 + 作者呼号，点击跳到「关于」页。 */
@Composable
private fun VersionAuthorBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(end = 12.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Badge,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = stringResource(
                        R.string.home_version_badge,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.home_author_label, AppConfig.AUTHOR_CALLSIGN),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CategoryTitle(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(MaterialTheme.shapes.extraSmall),
            color = MaterialTheme.colorScheme.primary
        ) {}
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

@Composable
private fun ToolRow(
    tools: List<ToolMeta>,
    onToolClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        tools.forEach { tool ->
            ToolCard(
                tool = tool,
                onClick = { onToolClick(tool.id) },
                modifier = Modifier.weight(1f)
            )
        }
        repeat(2 - tools.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HamToolsTheme(themeMode = ThemeMode.DARK) {
        HomeScreen(onToolClick = {}, onAboutClick = {})
    }
}

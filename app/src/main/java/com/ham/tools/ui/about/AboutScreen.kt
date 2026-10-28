package com.ham.tools.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ham.tools.BuildConfig
import com.ham.tools.R
import com.ham.tools.data.AppConfig
import com.ham.tools.data.tools.ToolRegistry
import com.ham.tools.ui.components.staggerEnter
import com.ham.tools.ui.theme.HamToolsTheme
import com.ham.tools.ui.theme.ThemeMode

/** 「关于」页：应用介绍、进度统计、技术信息与数据来源。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.about_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeroCard(modifier = staggerEnter(0))
            SettingsEntryCard(onClick = onSettingsClick, modifier = staggerEnter(1))
            TechInfoCard(modifier = staggerEnter(2))
            InfoCard(
                title = stringResource(R.string.about_section_data),
                body = stringResource(R.string.about_data_body),
                modifier = staggerEnter(3)
            )
            InfoCard(
                title = stringResource(R.string.about_section_disclaimer),
                body = stringResource(R.string.about_disclaimer_body),
                modifier = staggerEnter(4)
            )
            Text(
                text = stringResource(R.string.about_footer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HeroCard(modifier: Modifier = Modifier) {
    val implemented = ToolRegistry.tools.count { it.implemented }
    val total = ToolRegistry.tools.size

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Radio,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.primaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.home_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatItem(
                    value = "$implemented",
                    label = stringResource(R.string.about_stats_implemented)
                )
                StatItem(
                    value = "$total",
                    label = stringResource(R.string.about_stats_total)
                )
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun SettingsEntryCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    text = stringResource(R.string.about_appearance_settings),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.about_appearance_settings_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TechInfoCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            InfoRow(
                label = stringResource(R.string.settings_version),
                value = BuildConfig.VERSION_NAME
            )
            InfoRow(
                label = stringResource(R.string.settings_package),
                value = BuildConfig.APPLICATION_ID
            )
            InfoRow(
                label = stringResource(R.string.about_author),
                value = AppConfig.AUTHOR_CALLSIGN
            )
            InfoRow(
                label = stringResource(R.string.about_build_type),
                value = BuildConfig.BUILD_TYPE
            )
            InfoRow(
                label = stringResource(R.string.about_min_sdk),
                value = stringResource(R.string.about_min_sdk_value)
            )
        }
    }
}

/**
 * 帧率控制面板。
 *
 * 它把「应用层请求」和「系统策略层」两条路径摆在同一个地方：
 * 上面几行是设备现状（屏幕能到多少、系统允许到多少、实测跑多少），
 * 下面是唯一需要用户操作的开关——改系统策略要 WRITE_SETTINGS 权限，
 * 这个权限必须用户去系统页亲手打开，App 不能代劳。
 */
@Composable
private fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoCard(title: String, body: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AboutScreenPreview() {
    HamToolsTheme(themeMode = ThemeMode.DARK) {
        AboutScreen(onSettingsClick = {})
    }
}

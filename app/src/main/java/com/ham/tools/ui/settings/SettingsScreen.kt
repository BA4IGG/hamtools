package com.ham.tools.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ham.tools.BuildConfig
import com.ham.tools.R
import com.ham.tools.ui.components.rememberDebouncedCallback
import com.ham.tools.ui.theme.HamToolsTheme
import com.ham.tools.ui.theme.M3Variant
import com.ham.tools.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    accentSeed: Int,
    accentVariant: M3Variant,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onAccentChange: (Int, M3Variant) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicColorActive = dynamicColor && supportsDynamicColor
    // 主题相关改动会让整棵树重新着色，连点时只应用最后一次，避免掉帧堆积
    val debouncedThemeChange = rememberDebouncedCallback<ThemeMode>(delayMs = 180L) {
        onThemeModeChange(it)
    }
    val debouncedDynamicColorChange = rememberDebouncedCallback<Boolean>(delayMs = 180L) {
        onDynamicColorChange(it)
    }
    var pickerVisible by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            SectionTitle(text = stringResource(R.string.settings_section_appearance))

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_theme_mode),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.settings_theme_mode_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ThemeModeSelector(
                    themeMode = themeMode,
                    onThemeModeChange = debouncedThemeChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_accent),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = if (dynamicColorActive) {
                        stringResource(R.string.settings_accent_disabled_desc)
                    } else {
                        stringResource(R.string.settings_accent_desc)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AccentEntry(
                    seed = accentSeed,
                    variant = accentVariant,
                    enabled = !dynamicColorActive,
                    onClick = { pickerVisible = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_dynamic_color),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.settings_dynamic_color_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = dynamicColor && supportsDynamicColor,
                    onCheckedChange = debouncedDynamicColorChange,
                    enabled = supportsDynamicColor
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionTitle(text = stringResource(R.string.settings_section_about))
            AboutRow(
                label = stringResource(R.string.settings_version),
                value = BuildConfig.VERSION_NAME
            )
            AboutRow(
                label = stringResource(R.string.settings_package),
                value = BuildConfig.APPLICATION_ID
            )
        }
    }

    if (pickerVisible) {
        M3ColorPickerDialog(
            initialSeed = accentSeed,
            initialVariant = accentVariant,
            darkTheme = themeMode == ThemeMode.DARK,
            onConfirm = { seed, variant ->
                pickerVisible = false
                onAccentChange(seed, variant)
            },
            onDismiss = { pickerVisible = false }
        )
    }
}

/** 主题色入口：显示当前种子色与方案变体，点击打开取色器。 */
@Composable
private fun AccentEntry(
    seed: Int,
    variant: M3Variant,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha = if (enabled) 1f else 0.38f
    Surface(
        modifier = modifier.then(
            if (enabled) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(seed))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape
                    )
            )
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    text = "#" + (seed and 0xFFFFFF)
                        .toString(16).uppercase().padStart(6, '0'),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = variantLabel(variant),
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
private fun variantLabel(variant: M3Variant): String = stringResource(
    when (variant) {
        M3Variant.TONAL_SPOT -> R.string.variant_tonal_spot
        M3Variant.VIBRANT -> R.string.variant_vibrant
        M3Variant.EXPRESSIVE -> R.string.variant_expressive
        M3Variant.NEUTRAL -> R.string.variant_neutral
        M3Variant.MONOCHROME -> R.string.variant_monochrome
        M3Variant.FIDELITY -> R.string.variant_fidelity
        M3Variant.CONTENT -> R.string.variant_content
        M3Variant.RAINBOW -> R.string.variant_rainbow
        M3Variant.FRUIT_SALAD -> R.string.variant_fruit_salad
    }
)

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        ThemeMode.SYSTEM to R.string.theme_mode_system,
        ThemeMode.LIGHT to R.string.theme_mode_light,
        ThemeMode.DARK to R.string.theme_mode_dark
    )

    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, (mode, labelRes) ->
            SegmentedButton(
                selected = themeMode == mode,
                onClick = { onThemeModeChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(text = stringResource(labelRes))
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String, modifier: Modifier = Modifier) {
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

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    HamToolsTheme(themeMode = ThemeMode.DARK) {
        SettingsScreen(
            themeMode = ThemeMode.DARK,
            dynamicColor = false,
            accentSeed = 0xFF006C5B.toInt(),
            accentVariant = M3Variant.TONAL_SPOT,
            onThemeModeChange = {},
            onDynamicColorChange = {},
            onAccentChange = { _, _ -> },
            onBack = {}
        )
    }
}

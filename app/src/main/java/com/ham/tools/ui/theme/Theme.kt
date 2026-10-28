package com.ham.tools.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** 用户可选的三态主题模式。 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * 应用主题。
 *
 * 自定义主题色走 MD3 的完整流程：种子色 → HCT → 色调调色板 → 颜色角色
 * （见 [m3ColorScheme]），因此任意取色都能得到官方一致的、满足对比度要求的配色。
 * 开启动态取色时由系统壁纸决定，此时忽略自定义种子色。
 */
@Composable
fun HamToolsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    accentSeed: Int = DEFAULT_ACCENT_SEED,
    accentVariant: M3Variant = M3Variant.TONAL_SPOT,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic && !darkTheme -> dynamicLightColorScheme(context)
        else -> remember(accentSeed, accentVariant, darkTheme) {
            m3ColorScheme(
                seedArgb = accentSeed,
                dark = darkTheme,
                variant = accentVariant
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

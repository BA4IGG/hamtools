package com.ham.tools.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.ham.tools.materialcolor.utilities.DynamicScheme
import com.ham.tools.materialcolor.utilities.Hct
import com.ham.tools.materialcolor.utilities.MaterialDynamicColors
import com.ham.tools.materialcolor.utilities.SchemeContent
import com.ham.tools.materialcolor.utilities.SchemeExpressive
import com.ham.tools.materialcolor.utilities.SchemeFidelity
import com.ham.tools.materialcolor.utilities.SchemeFruitSalad
import com.ham.tools.materialcolor.utilities.SchemeMonochrome
import com.ham.tools.materialcolor.utilities.SchemeNeutral
import com.ham.tools.materialcolor.utilities.SchemeRainbow
import com.ham.tools.materialcolor.utilities.SchemeTonalSpot
import com.ham.tools.materialcolor.utilities.SchemeVibrant
import com.ham.tools.materialcolor.utilities.TonalPalette
import com.ham.tools.materialcolor.utilities.Variant

/**
 * Material Design 3 动态配色方案的生成入口。
 *
 * 这里没有用「HSL 拍脑袋派生几个主色」的做法，而是完整走 MD3 的定义：
 * 种子色 → HCT（Hue / Chroma / Tone）→ 五组色调调色板（primary / secondary /
 * tertiary / neutral / neutral-variant）→ 由对比度曲线决定的全部颜色角色。
 * 因此任意用户自选色都能得到与官方 Material Theme Builder 一致、且满足
 * 对比度要求（on 色与主色/容器色之间）的配色。
 *
 * 颜色数学来自内置的 material-color-utilities（Apache-2.0，
 * 见 com.ham.tools.materialcolor.PROVENANCE.txt）。
 */

/** MD3 的九种配色方案变体。 */
enum class M3Variant {
    /** 强调点：默认，从一个主色铺开。 */
    TONAL_SPOT,

    /** 鲜艳：主色饱和度更高。 */
    VIBRANT,

    /** 表现力：色相偏移更大，对比更强。 */
    EXPRESSIVE,

    /** 中性：低饱和，克制。 */
    NEUTRAL,

    /** 单色：全灰阶。 */
    MONOCHROME,

    /** 忠实：尽量保留种子色本身。 */
    FIDELITY,

    /** 内容：更贴近内容色的取色策略。 */
    CONTENT,

    /** 彩虹：色相丰富。 */
    RAINBOW,

    /** 水果色：主色与次色色相拉开。 */
    FRUIT_SALAD
}

/** 默认品牌色（青绿），以 ARGB 形式存储。 */
val DEFAULT_ACCENT_SEED: Int = 0xFF006C5B.toInt()

private fun M3Variant.toVariant(): Variant = when (this) {
    M3Variant.TONAL_SPOT -> Variant.TONAL_SPOT
    M3Variant.VIBRANT -> Variant.VIBRANT
    M3Variant.EXPRESSIVE -> Variant.EXPRESSIVE
    M3Variant.NEUTRAL -> Variant.NEUTRAL
    M3Variant.MONOCHROME -> Variant.MONOCHROME
    M3Variant.FIDELITY -> Variant.FIDELITY
    M3Variant.CONTENT -> Variant.CONTENT
    M3Variant.RAINBOW -> Variant.RAINBOW
    M3Variant.FRUIT_SALAD -> Variant.FRUIT_SALAD
}

private fun dynamicSchemeFor(
    seedArgb: Int,
    dark: Boolean,
    variant: M3Variant,
    contrastLevel: Double
): DynamicScheme {
    val source = Hct.fromInt(seedArgb)
    return when (variant) {
        M3Variant.TONAL_SPOT -> SchemeTonalSpot(source, dark, contrastLevel)
        M3Variant.VIBRANT -> SchemeVibrant(source, dark, contrastLevel)
        M3Variant.EXPRESSIVE -> SchemeExpressive(source, dark, contrastLevel)
        M3Variant.NEUTRAL -> SchemeNeutral(source, dark, contrastLevel)
        M3Variant.MONOCHROME -> SchemeMonochrome(source, dark, contrastLevel)
        M3Variant.FIDELITY -> SchemeFidelity(source, dark, contrastLevel)
        M3Variant.CONTENT -> SchemeContent(source, dark, contrastLevel)
        M3Variant.RAINBOW -> SchemeRainbow(source, dark, contrastLevel)
        M3Variant.FRUIT_SALAD -> SchemeFruitSalad(source, dark, contrastLevel)
    }
}

/**
 * 由种子色生成一套完整的 MD3 [ColorScheme]。
 *
 * @param seedArgb 用户选择的 ARGB 种子色。
 * @param dark 是否为深色模式。
 * @param variant 配色方案变体。
 * @param contrastLevel 对比度档位，0 为标准，取值 -1.0 ~ 1.0。
 */
fun m3ColorScheme(
    seedArgb: Int = DEFAULT_ACCENT_SEED,
    dark: Boolean,
    variant: M3Variant = M3Variant.TONAL_SPOT,
    contrastLevel: Double = 0.0
): ColorScheme {
    val scheme = dynamicSchemeFor(seedArgb, dark, variant, contrastLevel)
    val roles = MaterialDynamicColors()

    val primary = Color(roles.primary().getArgb(scheme))
    val onPrimary = Color(roles.onPrimary().getArgb(scheme))
    val primaryContainer = Color(roles.primaryContainer().getArgb(scheme))
    val onPrimaryContainer = Color(roles.onPrimaryContainer().getArgb(scheme))
    val inversePrimary = Color(roles.inversePrimary().getArgb(scheme))

    val secondary = Color(roles.secondary().getArgb(scheme))
    val onSecondary = Color(roles.onSecondary().getArgb(scheme))
    val secondaryContainer = Color(roles.secondaryContainer().getArgb(scheme))
    val onSecondaryContainer = Color(roles.onSecondaryContainer().getArgb(scheme))

    val tertiary = Color(roles.tertiary().getArgb(scheme))
    val onTertiary = Color(roles.onTertiary().getArgb(scheme))
    val tertiaryContainer = Color(roles.tertiaryContainer().getArgb(scheme))
    val onTertiaryContainer = Color(roles.onTertiaryContainer().getArgb(scheme))

    val background = Color(roles.background().getArgb(scheme))
    val onBackground = Color(roles.onBackground().getArgb(scheme))
    val surface = Color(roles.surface().getArgb(scheme))
    val onSurface = Color(roles.onSurface().getArgb(scheme))
    val surfaceVariant = Color(roles.surfaceVariant().getArgb(scheme))
    val onSurfaceVariant = Color(roles.onSurfaceVariant().getArgb(scheme))
    val surfaceTint = Color(roles.surfaceTint().getArgb(scheme))
    val inverseSurface = Color(roles.inverseSurface().getArgb(scheme))
    val inverseOnSurface = Color(roles.inverseOnSurface().getArgb(scheme))

    val error = Color(roles.error().getArgb(scheme))
    val onError = Color(roles.onError().getArgb(scheme))
    val errorContainer = Color(roles.errorContainer().getArgb(scheme))
    val onErrorContainer = Color(roles.onErrorContainer().getArgb(scheme))

    val outline = Color(roles.outline().getArgb(scheme))
    val outlineVariant = Color(roles.outlineVariant().getArgb(scheme))
    val scrim = Color(roles.scrim().getArgb(scheme))

    val surfaceDim = Color(roles.surfaceDim().getArgb(scheme))
    val surfaceBright = Color(roles.surfaceBright().getArgb(scheme))
    val surfaceContainerLowest = Color(roles.surfaceContainerLowest().getArgb(scheme))
    val surfaceContainerLow = Color(roles.surfaceContainerLow().getArgb(scheme))
    val surfaceContainer = Color(roles.surfaceContainer().getArgb(scheme))
    val surfaceContainerHigh = Color(roles.surfaceContainerHigh().getArgb(scheme))
    val surfaceContainerHighest = Color(roles.surfaceContainerHighest().getArgb(scheme))

    val primaryFixed = Color(roles.primaryFixed().getArgb(scheme))
    val primaryFixedDim = Color(roles.primaryFixedDim().getArgb(scheme))
    val onPrimaryFixed = Color(roles.onPrimaryFixed().getArgb(scheme))
    val onPrimaryFixedVariant = Color(roles.onPrimaryFixedVariant().getArgb(scheme))
    val secondaryFixed = Color(roles.secondaryFixed().getArgb(scheme))
    val secondaryFixedDim = Color(roles.secondaryFixedDim().getArgb(scheme))
    val onSecondaryFixed = Color(roles.onSecondaryFixed().getArgb(scheme))
    val onSecondaryFixedVariant = Color(roles.onSecondaryFixedVariant().getArgb(scheme))
    val tertiaryFixed = Color(roles.tertiaryFixed().getArgb(scheme))
    val tertiaryFixedDim = Color(roles.tertiaryFixedDim().getArgb(scheme))
    val onTertiaryFixed = Color(roles.onTertiaryFixed().getArgb(scheme))
    val onTertiaryFixedVariant = Color(roles.onTertiaryFixedVariant().getArgb(scheme))

    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = surfaceTint,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = scrim,
            surfaceDim = surfaceDim,
            surfaceBright = surfaceBright,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            primaryFixed = primaryFixed,
            primaryFixedDim = primaryFixedDim,
            onPrimaryFixed = onPrimaryFixed,
            onPrimaryFixedVariant = onPrimaryFixedVariant,
            secondaryFixed = secondaryFixed,
            secondaryFixedDim = secondaryFixedDim,
            onSecondaryFixed = onSecondaryFixed,
            onSecondaryFixedVariant = onSecondaryFixedVariant,
            tertiaryFixed = tertiaryFixed,
            tertiaryFixedDim = tertiaryFixedDim,
            onTertiaryFixed = onTertiaryFixed,
            onTertiaryFixedVariant = onTertiaryFixedVariant
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = surfaceTint,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = scrim,
            surfaceDim = surfaceDim,
            surfaceBright = surfaceBright,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            primaryFixed = primaryFixed,
            primaryFixedDim = primaryFixedDim,
            onPrimaryFixed = onPrimaryFixed,
            onPrimaryFixedVariant = onPrimaryFixedVariant,
            secondaryFixed = secondaryFixed,
            secondaryFixedDim = secondaryFixedDim,
            onSecondaryFixed = onSecondaryFixed,
            onSecondaryFixedVariant = onSecondaryFixedVariant,
            tertiaryFixed = tertiaryFixed,
            tertiaryFixedDim = tertiaryFixedDim,
            onTertiaryFixed = onTertiaryFixed,
            onTertiaryFixedVariant = onTertiaryFixedVariant
        )
    }
}

/**
 * 生成主色的色调调色板（tone 0–100），用于取色器里的色阶预览与快捷取色。
 * 每隔 10 取一档，保证任意种子色下都能看到完整明度梯度。
 */
fun m3TonalSwatches(seedArgb: Int, variant: M3Variant = M3Variant.TONAL_SPOT): List<Int> {
    val scheme = dynamicSchemeFor(seedArgb, false, variant, 0.0)
    val palette: TonalPalette = scheme.primaryPalette
    return listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 95).map { palette.tone(it) }
}

/** 取色器预览用的关键角色：只算这六个，拖拽调色时开销最小。 */
data class M3PreviewRoles(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val tertiary: Color,
    val onTertiary: Color
)

fun m3PreviewRoles(
    seedArgb: Int,
    dark: Boolean,
    variant: M3Variant = M3Variant.TONAL_SPOT
): M3PreviewRoles {
    val scheme = dynamicSchemeFor(seedArgb, dark, variant, 0.0)
    val roles = MaterialDynamicColors()
    return M3PreviewRoles(
        primary = Color(roles.primary().getArgb(scheme)),
        onPrimary = Color(roles.onPrimary().getArgb(scheme)),
        primaryContainer = Color(roles.primaryContainer().getArgb(scheme)),
        onPrimaryContainer = Color(roles.onPrimaryContainer().getArgb(scheme)),
        secondary = Color(roles.secondary().getArgb(scheme)),
        onSecondary = Color(roles.onSecondary().getArgb(scheme)),
        tertiary = Color(roles.tertiary().getArgb(scheme)),
        onTertiary = Color(roles.onTertiary().getArgb(scheme))
    )
}

/** 取色器预设色：绕色相环一周、色度与明度按 MD3 常用区间固定，保证观感一致。 */
val M3PresetSeeds: List<Int> = (0 until 360 step 30).map { hue ->
    Hct.from(hue.toDouble(), 48.0, 50.0).toInt()
}

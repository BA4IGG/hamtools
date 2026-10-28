package com.ham.tools

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ham.tools.materialcolor.utilities.Hct
import com.ham.tools.ui.theme.DEFAULT_ACCENT_SEED
import com.ham.tools.ui.theme.M3Variant
import com.ham.tools.ui.theme.m3ColorScheme
import com.ham.tools.ui.theme.m3PreviewRoles
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MD3 配色生成的回归测试。
 *
 * 期望值取自 Google material-components-android 1.14.0 官方实现
 * （SchemeTonalSpot + MaterialDynamicColors）的实测输出，
 * 以此保证内置的调色算法与官方结果逐位一致。
 */
class M3ColorSchemeTest {

    private fun scheme(seed: Long, dark: Boolean) =
        m3ColorScheme(seedArgb = seed.toInt(), dark = dark)

    @Test
    fun tonalSpot_baselineSeed_lightMatchesOfficial() {
        val s = scheme(0xFF6750A4, dark = false)
        assertEquals(0xFF65558F.toInt(), s.primary.toArgb())
        assertEquals(0xFFFFFFFF.toInt(), s.onPrimary.toArgb())
        assertEquals(0xFFE9DDFF.toInt(), s.primaryContainer.toArgb())
        assertEquals(0xFF4D3D75.toInt(), s.onPrimaryContainer.toArgb())
        assertEquals(0xFF625B71.toInt(), s.secondary.toArgb())
        assertEquals(0xFFE8DEF8.toInt(), s.secondaryContainer.toArgb())
        assertEquals(0xFF7E5260.toInt(), s.tertiary.toArgb())
        assertEquals(0xFFFDF7FF.toInt(), s.surface.toArgb())
        assertEquals(0xFF1D1B20.toInt(), s.onSurface.toArgb())
        assertEquals(0xFFBA1A1A.toInt(), s.error.toArgb())
        assertEquals(0xFF7A757F.toInt(), s.outline.toArgb())
        assertEquals(0xFFECE6EE.toInt(), s.surfaceContainerHigh.toArgb())
    }

    @Test
    fun tonalSpot_baselineSeed_darkMatchesOfficial() {
        val s = scheme(0xFF6750A4, dark = true)
        assertEquals(0xFFCFBDFE.toInt(), s.primary.toArgb())
        assertEquals(0xFF36275D.toInt(), s.onPrimary.toArgb())
        assertEquals(0xFF4D3D75.toInt(), s.primaryContainer.toArgb())
        assertEquals(0xFFE9DDFF.toInt(), s.onPrimaryContainer.toArgb())
        assertEquals(0xFFCBC2DB.toInt(), s.secondary.toArgb())
        assertEquals(0xFFEFB8C8.toInt(), s.tertiary.toArgb())
        assertEquals(0xFF141218.toInt(), s.surface.toArgb())
        assertEquals(0xFFFFB4AB.toInt(), s.error.toArgb())
    }

    @Test
    fun defaultSeed_producesTealFamily() {
        val light = scheme(DEFAULT_ACCENT_SEED.toLong() and 0xFFFFFFFFL, dark = false)
        assertEquals(0xFF086B5A.toInt(), light.primary.toArgb())
        assertEquals(0xFFA1F2DD.toInt(), light.primaryContainer.toArgb())
        val dark = scheme(DEFAULT_ACCENT_SEED.toLong() and 0xFFFFFFFFL, dark = true)
        assertEquals(0xFF85D6C1.toInt(), dark.primary.toArgb())
        assertEquals(0xFF0E1513.toInt(), dark.surface.toArgb())
    }

    @Test
    fun everyHue_keepsTextContrastAboveAaThreshold() {
        for (hue in 0 until 360 step 30) {
            val seed = Hct.from(hue.toDouble(), 48.0, 50.0).toInt()
            for (dark in listOf(false, true)) {
                val s = m3ColorScheme(seedArgb = seed, dark = dark)
                val label = "hue=$hue dark=$dark"
                assertTrue("$label onPrimary/primary", contrast(s.onPrimary, s.primary) >= 4.5)
                assertTrue(
                    "$label onPrimaryContainer/primaryContainer",
                    contrast(s.onPrimaryContainer, s.primaryContainer) >= 4.5
                )
                assertTrue("$label onSurface/surface", contrast(s.onSurface, s.surface) >= 4.5)
                assertTrue(
                    "$label onSurfaceVariant/surface",
                    contrast(s.onSurfaceVariant, s.surface) >= 4.5
                )
            }
        }
    }

    @Test
    fun monochromeVariant_collapsesChroma() {
        val seed = 0xFFFF0000.toInt()
        val colored = m3ColorScheme(seedArgb = seed, dark = false, variant = M3Variant.TONAL_SPOT)
        val mono = m3ColorScheme(seedArgb = seed, dark = false, variant = M3Variant.MONOCHROME)
        // 单色方案下主色的色相不再决定观感：红绿通道应基本相等
        val spread = maxOf(mono.primary.red, mono.primary.green, mono.primary.blue) -
            minOf(mono.primary.red, mono.primary.green, mono.primary.blue)
        assertTrue("monochrome spread=$spread", spread < 0.05f)
        assertTrue(colored.primary != mono.primary)
    }

    @Test
    fun previewRolesMatchFullScheme() {
        val seed = 0xFF006C5B.toInt()
        for (dark in listOf(false, true)) {
            val full = m3ColorScheme(seedArgb = seed, dark = dark)
            val preview = m3PreviewRoles(seedArgb = seed, dark = dark)
            assertEquals(full.primary.toArgb(), preview.primary.toArgb())
            assertEquals(full.onPrimary.toArgb(), preview.onPrimary.toArgb())
            assertEquals(full.secondary.toArgb(), preview.secondary.toArgb())
            assertEquals(full.tertiary.toArgb(), preview.tertiary.toArgb())
        }
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrast(foreground: Color, background: Color): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }
}

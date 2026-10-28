package com.ham.tools.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ham.tools.R
import com.ham.tools.ui.theme.DEFAULT_ACCENT_SEED
import com.ham.tools.ui.theme.M3PresetSeeds
import com.ham.tools.ui.theme.M3Variant
import com.ham.tools.ui.theme.m3PreviewRoles

/**
 * MD3 风格取色器。
 *
 * 取色流程遵循 Material 的做法：先定色相，再在色彩空间里定饱和度与明度，
 * 也可以直接输入 HEX；选中的种子色会交给 MD3 的 HCT 流程派生整套配色。
 *
 * 交互期间只在本地维护种子色，点「确定」后才写入偏好，避免拖动时反复持久化。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3ColorPickerDialog(
    initialSeed: Int,
    initialVariant: M3Variant,
    darkTheme: Boolean,
    onConfirm: (seed: Int, variant: M3Variant) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var seed by remember { mutableIntStateOf(initialSeed) }
    var variant by remember { mutableStateOf(initialVariant) }

    // 色相 / 饱和度 / 明度：从当前种子色反解，拖动时再回写种子色
    val initialHsv = remember(initialSeed) { colorToHsv(Color(seed)) }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    var hexText by remember { mutableStateOf(hexOf(seed)) }
    var hexError by remember { mutableStateOf(false) }

    fun applyHsv(newHue: Float = hue, newSat: Float = saturation, newValue: Float = value) {
        hue = newHue
        saturation = newSat
        value = newValue
        seed = Color.hsv(newHue, newSat, newValue).toArgb()
        hexText = hexOf(seed)
        hexError = false
    }

    /** @param syncHex 是否回写输入框；用户正在手动输入时不要回写，否则光标会被顶掉。 */
    fun applySeed(newSeed: Int, syncHex: Boolean = true) {
        seed = newSeed
        val hsv = colorToHsv(Color(newSeed))
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        if (syncHex) hexText = hexOf(newSeed)
        hexError = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.color_picker_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                SaturationValueArea(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onChange = { newSat, newValue -> applyHsv(newSat = newSat, newValue = newValue) },
                    modifier = Modifier.fillMaxWidth()
                )

                HueSlider(
                    hue = hue,
                    onHueChange = { newHue -> applyHsv(newHue = newHue) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = hexText,
                    onValueChange = { input ->
                        val clean = input.filter { it.isLetterOrDigit() }.take(6).uppercase()
                        hexText = clean
                        val parsed = parseHex(clean)
                        if (parsed != null) {
                            // 输入过程中不改写文本框，避免光标跳到末尾
                            applySeed(parsed, syncHex = false)
                        } else {
                            hexError = clean.length == 6
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.color_picker_hex)) },
                    prefix = { Text(text = "#") },
                    isError = hexError,
                    supportingText = {
                        if (hexError) {
                            Text(text = stringResource(R.string.color_picker_hex_invalid))
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                SectionLabel(text = stringResource(R.string.color_picker_presets))
                PresetSwatches(
                    currentSeed = seed,
                    onSelect = { applySeed(it) },
                    modifier = Modifier.fillMaxWidth()
                )

                SectionLabel(text = stringResource(R.string.color_picker_variant))
                VariantChips(
                    variant = variant,
                    onVariantChange = { variant = it },
                    modifier = Modifier.fillMaxWidth()
                )

                SectionLabel(text = stringResource(R.string.color_picker_preview))
                SchemePreview(
                    seed = seed,
                    variant = variant,
                    darkTheme = darkTheme,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { applySeed(DEFAULT_ACCENT_SEED) }) {
                        Text(text = stringResource(R.string.color_picker_reset))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.action_cancel))
                    }
                    TextButton(onClick = { onConfirm(seed, variant) }) {
                        Text(text = stringResource(R.string.action_confirm))
                    }
                }
            }
        }
    }
}

/** 饱和度（横轴）× 明度（纵轴）取色区。 */
@Composable
private fun SaturationValueArea(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (saturation: Float, value: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val pure = Color.hsv(hue, 1f, 1f)
    val thumbRadius = 12.dp
    val innerRadius = 8.dp

    Box(
        modifier = modifier
            .aspectRatio(1.4f)
            .clip(shape)
            .background(Color.White)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onChange(
                        (offset.x / size.width).coerceIn(0f, 1f),
                        (1f - offset.y / size.height).coerceIn(0f, 1f)
                    )
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onChange(
                        (change.position.x / size.width).coerceIn(0f, 1f),
                        (1f - change.position.y / size.height).coerceIn(0f, 1f)
                    )
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.White, pure)),
                size = size
            )
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
                size = size
            )
            val center = Offset(saturation * size.width, (1f - value) * size.height)
            drawCircle(color = Color.White, radius = thumbRadius.toPx(), center = center)
            drawCircle(
                color = Color.hsv(hue, saturation, value),
                radius = innerRadius.toPx(),
                center = center
            )
        }
    }
}

/** 色相滑动条：轨道为完整色相环，手柄为圆形。 */
@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val rainbow = listOf(
        Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
        Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000)
    )
    val trackHeight = 16.dp
    val thumbRadius = 12.dp
    val innerRadius = 8.dp

    Box(
        modifier = modifier
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val usable = (size.width - 2 * thumbRadius.toPx()).coerceAtLeast(1f)
                    val ratio = ((offset.x - thumbRadius.toPx()) / usable).coerceIn(0f, 1f)
                    onHueChange(ratio * 360f)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val usable = (size.width - 2 * thumbRadius.toPx()).coerceAtLeast(1f)
                    val ratio = ((change.position.x - thumbRadius.toPx()) / usable).coerceIn(0f, 1f)
                    onHueChange(ratio * 360f)
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val trackTop = (size.height - trackHeight.toPx()) / 2f
            drawRoundRect(
                brush = Brush.horizontalGradient(rainbow),
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, trackHeight.toPx()),
                cornerRadius = CornerRadius(trackHeight.toPx() / 2f, trackHeight.toPx() / 2f)
            )
            val usable = (size.width - 2 * thumbRadius.toPx()).coerceAtLeast(1f)
            val cx = thumbRadius.toPx() + (hue / 360f).coerceIn(0f, 1f) * usable
            val center = Offset(cx, size.height / 2f)
            drawCircle(color = Color.White, radius = thumbRadius.toPx(), center = center)
            drawCircle(color = Color.hsv(hue, 1f, 1f), radius = innerRadius.toPx(), center = center)
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = thumbRadius.toPx(),
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}

/** 预设种子色：色相环上等距取 12 个色。 */
@Composable
private fun PresetSwatches(
    currentSeed: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        M3PresetSeeds.chunked(6).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { preset ->
                    val selected = preset == currentSeed
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(Color(preset))
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        width = 3.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape
                                    )
                                } else {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                                }
                            )
                            .clickable { onSelect(preset) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = onColorFor(Color(preset))
                            )
                        }
                    }
                }
                repeat(6 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** MD3 配色方案变体选择。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VariantChips(
    variant: M3Variant,
    onVariantChange: (M3Variant) -> Unit,
    modifier: Modifier = Modifier
) {
    val labels = listOf(
        M3Variant.TONAL_SPOT to R.string.variant_tonal_spot,
        M3Variant.VIBRANT to R.string.variant_vibrant,
        M3Variant.EXPRESSIVE to R.string.variant_expressive,
        M3Variant.NEUTRAL to R.string.variant_neutral,
        M3Variant.MONOCHROME to R.string.variant_monochrome,
        M3Variant.FIDELITY to R.string.variant_fidelity,
        M3Variant.CONTENT to R.string.variant_content,
        M3Variant.RAINBOW to R.string.variant_rainbow,
        M3Variant.FRUIT_SALAD to R.string.variant_fruit_salad
    )

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEach { (item, labelRes) ->
            FilterChip(
                selected = variant == item,
                onClick = { onVariantChange(item) },
                label = { Text(text = stringResource(labelRes)) },
                shape = RoundedCornerShape(8.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    }
}

/** 当前种子色派生出的主要角色预览。 */
@Composable
private fun SchemePreview(
    seed: Int,
    variant: M3Variant,
    darkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val roles = remember(seed, variant, darkTheme) {
        m3PreviewRoles(seedArgb = seed, dark = darkTheme, variant = variant)
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PreviewChip(
            label = stringResource(R.string.color_role_primary),
            container = roles.primary,
            content = roles.onPrimary,
            modifier = Modifier.weight(1f)
        )
        PreviewChip(
            label = stringResource(R.string.color_role_secondary),
            container = roles.secondary,
            content = roles.onSecondary,
            modifier = Modifier.weight(1f)
        )
        PreviewChip(
            label = stringResource(R.string.color_role_tertiary),
            container = roles.tertiary,
            content = roles.onTertiary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PreviewChip(
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.aspectRatio(1.1f),
        shape = MaterialTheme.shapes.medium,
        color = container
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** ARGB → 6 位十六进制字符串（不含 #）。 */
private fun hexOf(argb: Int): String =
    (argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

/** 解析 3 位或 6 位十六进制颜色。 */
private fun parseHex(input: String): Int? {
    val clean = input.trim().removePrefix("#")
    val value = when (clean.length) {
        6 -> clean.toLongOrNull(16)
        3 -> clean.map { "$it$it" }.joinToString("").toLongOrNull(16)
        else -> null
    } ?: return null
    return (0xFF000000 or value).toInt()
}

/** RGB → HSV（色相 0–360，饱和度/明度 0–1）。 */
private fun colorToHsv(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }
    val hueNormalized = ((hue % 360f) + 360f) % 360f
    return floatArrayOf(hueNormalized, if (max == 0f) 0f else delta / max, max)
}

/** 在给定底色上选一个可读的前景（黑或白），用于预设色上的勾号。 */
private fun onColorFor(background: Color): Color {
    val luminance = 0.2126f * background.red + 0.7152f * background.green + 0.0722f * background.blue
    return if (luminance > 0.5f) Color.Black else Color.White
}

package com.ham.tools.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

private val DockHeight = 60.dp

/**
 * 底部 Dock：主页 / 关于。
 *
 * 指示块的位置直接由 [selectedFraction]（翻页器当前页 + 滑动偏移）驱动，
 * 所以手指滑动时它是连续跟随的，而不是等滑动结束才跳过去。
 */
@Composable
fun HamDock(
    selectedFraction: Float,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    selectedIcons: List<ImageVector> = emptyList(),
    unselectedIcons: List<ImageVector> = emptyList()
) {
    val count = labels.size
    if (count == 0) return

    var containerWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val clamped = selectedFraction.coerceIn(0f, (count - 1).toFloat())
    val itemWidthPx = if (containerWidthPx == 0) 0f else containerWidthPx.toFloat() / count
    val indicatorXPx = clamped * itemWidthPx

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .height(DockHeight)
        ) {
            if (itemWidthPx > 0f) {
                Box(
                    modifier = Modifier
                        .offsetPx(indicatorXPx)
                        .widthPx(with(density) { itemWidthPx.toDp() })
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DockHeight)
                    .onWidthChanged { containerWidthPx = it }
            ) {
                repeat(count) { index ->
                    val progress = (1f - abs(clamped - index)).coerceIn(0f, 1f)
                    DockItem(
                        label = labels[index],
                        selectedIcon = selectedIcons.getOrNull(index) ?: Icons.Filled.Info,
                        unselectedIcon = unselectedIcons.getOrNull(index) ?: Icons.Outlined.Info,
                        progress = progress,
                        onClick = { onPageSelected(index) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DockItem(
    label: String,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedColor = MaterialTheme.colorScheme.onSecondaryContainer
    val tint = lerp(unselectedColor, selectedColor, progress)
    val scale = 1f + 0.16f * progress

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (progress > 0.5f) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 预置的主页 / 关于两项配置，避免调用处重复写图标。 */
object HamDockItems {
    fun defaultLabels(home: String, about: String) = listOf(home, about)
    val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Info)
    val unselectedIcons = listOf(Icons.Outlined.Home, Icons.Outlined.Info)
}

private fun Modifier.offsetPx(x: Float): Modifier =
    this.offset { IntOffset(x.roundToInt(), 0) }

private fun Modifier.widthPx(width: Dp): Modifier = this.width(width)

private fun Modifier.onWidthChanged(onWidth: (Int) -> Unit): Modifier =
    this.onSizeChanged { onWidth(it.width) }

package com.ham.tools.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

private val DockHeight = 64.dp

/**
 * 底部 Dock：主页 / QSL 交换 / 考试练习 / 我的 / 关于。
 *
 * 指示块的位置直接由 [selectedFraction]（翻页器当前页 + 滑动偏移）驱动，
 * 所以手指滑动时它是连续跟随的，而不是等滑动结束才跳过去。
 * [centerIndex] 指向正中央的「考试练习」，给它一个凸起的圆形底座以示强调。
 *
 * 标签使用短名（见 strings.xml 的 dock_label_*）：五个按钮平分宽度，
 * 「QSL 交换」「考试练习」这类长名字在窄屏或系统字体放大后会被省略号截断；
 * 完整名称通过 [contentDescriptions] 提供给无障碍朗读，页面顶栏仍显示全称。
 */
@Composable
fun HamDock(
    selectedFraction: Float,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    contentDescriptions: List<String> = emptyList(),
    selectedIcons: List<ImageVector> = emptyList(),
    unselectedIcons: List<ImageVector> = emptyList(),
    /** 每项右上角的待处理数量，0 表示不显示；用于 QSL 待处理申请角标。 */
    badges: List<Int> = emptyList(),
    centerIndex: Int = -1
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .height(DockHeight)
        ) {
            if (itemWidthPx > 0f) {
                Box(
                    modifier = Modifier
                        .offsetPx(indicatorXPx)
                        .widthPx(with(density) { itemWidthPx.toDp() })
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(22.dp))
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
                        contentDescription = contentDescriptions.getOrNull(index) ?: labels[index],
                        selectedIcon = selectedIcons.getOrNull(index) ?: Icons.Filled.Info,
                        unselectedIcon = unselectedIcons.getOrNull(index) ?: Icons.Outlined.Info,
                        progress = progress,
                        badge = badges.getOrNull(index) ?: 0,
                        isCenter = index == centerIndex,
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
    contentDescription: String,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    progress: Float,
    badge: Int,
    isCenter: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedColor = MaterialTheme.colorScheme.onSecondaryContainer
    val tint = lerp(unselectedColor, selectedColor, progress)
    val iconScale = 1f + 0.14f * progress
    // 中央按钮的配色在选中/未选中之间是硬阈值，用颜色动画抹掉临界点的突变
    val centerBackground by animateColorAsState(
        targetValue = if (progress > 0.5f) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = Motion.standard(180),
        label = "dockCenterBackground"
    )
    val centerIconTint by animateColorAsState(
        targetValue = if (progress > 0.5f) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = Motion.standard(180),
        label = "dockCenterIconTint"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(if (isCenter) 36.dp else 26.dp)
                    .offset(y = if (isCenter) (-1).dp else 0.dp)
                    .clip(CircleShape)
                    .background(if (isCenter) centerBackground else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (progress > 0.5f) selectedIcon else unselectedIcon,
                    contentDescription = contentDescription,
                    tint = if (isCenter) centerIconTint else tint,
                    modifier = Modifier.graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
                )
                if (badge > 0) {
                    Badge(
                        modifier = Modifier.offset(x = 12.dp, y = (-10).dp),
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) {
                        Text(text = if (badge > 9) "9+" else badge.toString())
                    }
                }
            }
            // 固定高度让五个按钮的文字基线严格对齐；短名保证单行完整显示
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCenter) MaterialTheme.colorScheme.primary else tint,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.graphicsLayer {
                        alpha = 0.78f + 0.22f * progress
                        scaleX = 0.96f + 0.04f * progress
                        scaleY = 0.96f + 0.04f * progress
                    }
                )
            }
        }
    }
}

private fun Modifier.offsetPx(x: Float): Modifier =
    this.offset { IntOffset(x.roundToInt(), 0) }

private fun Modifier.widthPx(width: Dp): Modifier = this.width(width)

private fun Modifier.onWidthChanged(onWidth: (Int) -> Unit): Modifier =
    this.onSizeChanged { onWidth(it.width) }

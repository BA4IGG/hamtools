package com.ham.tools.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow

/**
 * 五个顶层页共用的顶部栏。
 *
 * 之所以单独抽出来：各页早期各自写了 TopAppBar（主页还是两行标题 + 署名徽章，
 * 考试页干脆没有顶栏），左右滑动切换时顶部会明显跳动。这里统一成
 * 「一行标题 + 一行副标题 + 可选操作区」的 M3 小顶栏，高度恒为 64dp，
 * 视觉重量一致；滚动时底色与副标题透明度一起平滑过渡。
 *
 * @param scrolled 内容是否已滚动。由各页的列表状态提供，没有列表状态的页传 false。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HamTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    scrolled: Boolean = false,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val containerColor by animateColorAsState(
        targetValue = if (scrolled) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = Motion.standard(220),
        label = "topBarContainer"
    )
    // 滚动后不把副标题直接抹掉，只降低存在感，信息仍然可读
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (scrolled) 0.6f else 1f,
        animationSpec = Motion.standard(200),
        label = "topBarSubtitleAlpha"
    )

    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer { alpha = subtitleAlpha }
                    )
                }
            }
        },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor)
    )
}

package com.ham.tools.ui.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ham.tools.R
import com.ham.tools.ui.components.Motion
import kotlinx.coroutines.delay
import kotlin.math.min

/** 启动动画总时长。 */
const val SPLASH_DURATION_MS = 1500L

// 品牌色固定为图标底色，与 windowBackground 一致，冷启动时不会出现白屏闪烁。
private val SplashTop = Color(0xFF006C5B)
private val SplashBottom = Color(0xFF00231D)
// 图标底比背景略亮一档，圆形裁切后才看得出是一枚圆盘
private val SplashIconBase = Color(0xFF00796B)
private val OnSplash = Color(0xFFF1FFFA)

/**
 * 启动加载界面：图标弹性入场 + 电波脉冲环 + 应用名 + MD3 进度指示器。
 *
 * 只做视觉展示，应用内容在其下层照常加载，动画结束后淡出即可进入。
 */
@Composable
fun SplashBrand(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "splashPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing)
        ),
        label = "pulse"
    )

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val iconScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.55f,
        animationSpec = Motion.bouncy(),
        label = "iconScale"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(260),
        label = "iconAlpha"
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(320, delayMillis = 160),
        label = "titleAlpha"
    )
    val titleOffset by animateFloatAsState(
        targetValue = if (entered) 0f else 24f,
        animationSpec = Motion.bouncy(),
        label = "titleOffset"
    )

    var progressVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(480)
        progressVisible = true
    }
    val progressAlpha by animateFloatAsState(
        targetValue = if (progressVisible) 1f else 0f,
        animationSpec = tween(240),
        label = "progressAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SplashTop, SplashBottom))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { alpha = titleAlpha }
        ) {
            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                // 电波脉冲环：三个环依次向外扩散并淡出
                Canvas(modifier = Modifier.matchParentSize()) {
                    val baseRadius = min(size.width, size.height) / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    repeat(3) { index ->
                        val phase = (pulse + index / 3f) % 1f
                        val radius = baseRadius * (0.42f + phase * 0.62f)
                        val alpha = (1f - phase) * 0.34f
                        drawCircle(
                            color = OnSplash,
                            radius = radius,
                            center = center,
                            alpha = alpha
                        )
                    }
                }

                // 脉冲环与图标之间留一圈描边，圆形图标看起来像一枚“天线振子”
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawCircle(
                        color = OnSplash,
                        radius = min(size.width, size.height) / 2f * 0.60f,
                        center = Offset(size.width / 2f, size.height / 2f),
                        alpha = 0.16f
                    )
                }

                Surface(
                    modifier = Modifier
                        .size(112.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                            this.alpha = iconAlpha
                        },
                    shape = CircleShape,
                    color = SplashIconBase,
                    tonalElevation = 6.dp,
                    shadowElevation = 10.dp
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = OnSplash,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { translationY = titleOffset }
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSplash.copy(alpha = 0.72f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            CircularProgressIndicator(
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer { alpha = progressAlpha },
                color = OnSplash,
                trackColor = OnSplash.copy(alpha = 0.24f),
                strokeWidth = 3.dp
            )
        }
    }
}

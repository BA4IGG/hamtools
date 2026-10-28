package com.ham.tools.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 统一的动效参数与可复用动画修饰器。
 * 集中在这里，方便后续整体调节手感。
 */
object Motion {

    /** 温和弹性：用于按压反馈、图标强调，带一点点回弹但不夸张。 */
    fun <T> bouncy() = spring<T>(
        dampingRatio = 0.68f,
        stiffness = 340f
    )

    /** 标准位移：用于入场、显隐，收尾干脆。 */
    fun <T> standard(durationMillis: Int = 260) = tween<T>(
        durationMillis = durationMillis,
        delayMillis = 0
    )

    /** 数值/文本切换：短促，避免快速输入时看上去在闪。 */
    const val VALUE_SWAP_MS = 180

    /** 列表项交错入场的单步延迟。 */
    const val STAGGER_STEP_MS = 40
    const val STAGGER_MAX_DELAY_MS = 320
}

/**
 * 列表项交错入场：先轻微下移并透明，再弹回原位。
 *
 * @param index 项序号，越靠后延迟越久（有上限，避免长列表等太久）。
 */
@Composable
fun staggerEnter(index: Int): Modifier {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index * Motion.STAGGER_STEP_MS).coerceAtMost(Motion.STAGGER_MAX_DELAY_MS).toLong())
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = Motion.standard(240),
        label = "staggerAlpha"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = Motion.bouncy(),
        label = "staggerOffset"
    )
    return Modifier.graphicsLayer {
        this.alpha = alpha
        translationY = offsetY
    }
}

/**
 * 完整的入场修饰器（透明度 + 位移），用于非列表场景，
 * 例如工具详情页的结果卡与说明卡。
 */
@Composable
fun contentEnter(delayMillis: Int = 0): Modifier {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = Motion.standard(280),
        label = "contentAlpha"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 22f,
        animationSpec = Motion.bouncy(),
        label = "contentOffset"
    )
    return Modifier.graphicsLayer {
        this.alpha = alpha
        translationY = offsetY
    }
}

/**
 * 回调防抖：连续点击时只在停手后触发最后一次。
 * 用于主题切换这类会导致整棵 UI 树重新着色的昂贵操作。
 */
@Composable
fun <T> rememberDebouncedCallback(
    delayMs: Long = 200L,
    onEvent: (T) -> Unit
): (T) -> Unit {
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    return remember(onEvent, delayMs) {
        { value: T ->
            job?.cancel()
            job = scope.launch {
                delay(delayMs)
                onEvent(value)
            }
        }
    }
}

/**
 * 输入防抖：文本每敲一个字符只重组输入框本身，
 * 计算与结果区等输入停顿后再更新，避免连续快速输入造成掉帧。
 */
@Composable
fun rememberDebounced(value: String, delayMs: Long = 120L): String {
    var debounced by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value == debounced) return@LaunchedEffect
        delay(delayMs)
        debounced = value
    }
    return debounced
}

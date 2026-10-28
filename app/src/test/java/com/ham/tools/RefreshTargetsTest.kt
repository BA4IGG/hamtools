package com.ham.tools

import com.ham.tools.util.RefreshTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 刷新率目标值计算。
 *
 * 这几个分支决定了「请求什么」——写错会直接导致两种极端后果：
 * 向 60Hz 屏请求 120 让整次窗口属性设置被判为非法，或者对着 144Hz 屏还保守地请求 120。
 */
class RefreshTargetsTest {

    @Test
    fun `120Hz 屏请求 120`() {
        val supported = listOf(60f, 90f, 120f)
        assertEquals(120f, RefreshTargets.targetOf(supported), EPSILON)
        assertEquals(120f, RefreshTargets.maxOf(supported), EPSILON)
    }

    @Test
    fun `144Hz 屏封顶在 120`() {
        assertEquals(120f, RefreshTargets.targetOf(listOf(60f, 120f, 144f)), EPSILON)
    }

    @Test
    fun `90Hz 屏退到 90`() {
        assertEquals(90f, RefreshTargets.targetOf(listOf(60f, 90f)), EPSILON)
    }

    @Test
    fun `60Hz 屏不做请求`() {
        assertEquals(0f, RefreshTargets.targetOf(listOf(60f)), EPSILON)
        assertEquals(0f, RefreshTargets.targetOf(emptyList()), EPSILON)
    }

    @Test
    fun `只有 60Hz 屏不值得提升`() {
        assertEquals(false, RefreshTargets.worthBoosting(listOf(60f)))
        assertEquals(false, RefreshTargets.worthBoosting(emptyList()))
        assertEquals(true, RefreshTargets.worthBoosting(listOf(60f, 90f)))
    }

    @Test
    fun `挑选最接近目标的档位，宁可略高也不被降额`() {
        val supported = listOf(60f, 90f, 120f)
        // 目标 90 时应当挑 90，而不是 120（多一分都是白耗电）
        assertEquals(90f, RefreshTargets.closestTo(supported, 90f)!!, EPSILON)
        // 目标 120 时挑 120
        assertEquals(120f, RefreshTargets.closestTo(supported, 120f)!!, EPSILON)
        // 目标 119.9 这种非整数值要落到 120 这一档，而不是被降到 90
        assertEquals(120f, RefreshTargets.closestTo(supported, 119.9f)!!, EPSILON)
        // 目标刚好在两个档位中间（100），应当取不低于它的最小值 120
        assertEquals(120f, RefreshTargets.closestTo(supported, 100f)!!, EPSILON)
    }

    @Test
    fun `目标 130 时若最高只有 120 就退到 120`() {
        assertEquals(120f, RefreshTargets.closestTo(listOf(60f, 90f, 120f), 130f)!!, EPSILON)
    }

    @Test
    fun `只有 60 和 144 时选最接近的 144`() {
        assertEquals(144f, RefreshTargets.closestTo(listOf(60f, 144f), 120f)!!, EPSILON)
    }

    @Test
    fun `空列表与非法目标值不挑档位`() {
        assertNull(RefreshTargets.closestTo(emptyList(), 120f))
        assertNull(RefreshTargets.closestTo(listOf(60f, 120f), 0f))
    }

    private companion object {
        const val EPSILON = 0.001f
    }
}

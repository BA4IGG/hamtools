package com.ham.tools.util

import kotlin.math.abs

/**
 * 刷新率目标值计算——纯逻辑，不碰任何 Android API，方便单元测试。
 *
 * 之所以单独拆一层：刷新率是「设备能力 + 省电取舍」共同决定的，
 * 一刀切写死 120 会在 60/90Hz 屏上变成无意义的请求，
 * 甚至会让部分 ROM 判定我们不合法而直接忽略整次窗口属性设置。
 */
object RefreshTargets {

    /** 期望达到的刷新率上限。超过这个值的屏幕（144Hz 及以上）不再往上追，省电。 */
    const val PREFERRED_HZ = 120f

    /** 屏幕连这个值都达不到就不折腾了，免得干扰设备原本的省电策略。 */
    const val SKIP_BELOW_HZ = 61f

    /** 请求值与实际支持值允许的最大误差，用于匹配显示模式（有些屏标 119.88Hz 而非 120）。 */
    private const val TOLERANCE_HZ = 0.5f

    /** 屏幕支持的最高刷新率；拿不到（空列表）时返回 0。 */
    fun maxOf(supportedHz: List<Float>): Float = supportedHz.maxOrNull() ?: 0f

    /**
     * 本 App 应当请求的刷新率。
     *
     * @return 目标刷新率；返回 0 表示这块屏幕不值得请求（60Hz 屏或能力未知）
     */
    fun targetOf(supportedHz: List<Float>): Float {
        val maxHz = maxOf(supportedHz)
        if (maxHz < SKIP_BELOW_HZ) return 0f
        return maxHz.coerceAtMost(PREFERRED_HZ)
    }

    /** 这块屏幕是否值得做全局提升（至少是 90Hz 屏）。 */
    fun worthBoosting(supportedHz: List<Float>): Boolean = targetOf(supportedHz) > 0f

    /**
     * 在 [available] 中挑出最接近 [targetHz] 的刷新率。
     *
     * 优先取「不低于目标」里最小的那一档（宁可略高也不要被降额），
     * 全部都低于目标时才退回最接近的一档。
     */
    fun closestTo(available: List<Float>, targetHz: Float): Float? {
        if (available.isEmpty() || targetHz <= 0f) return null
        val sorted = available.map { it }.distinct().sorted()
        val notLower = sorted.firstOrNull { it >= targetHz - TOLERANCE_HZ }
        if (notLower != null) return notLower
        return sorted.minByOrNull { abs(it - targetHz) }
    }
}

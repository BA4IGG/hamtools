package com.ham.tools.navigation

import android.os.SystemClock
import androidx.navigation.NavHostController

/**
 * 带节流的导航器。
 *
 * 连点会把同一目的地反复压进回退栈，返回时要按很多次才能回到首页，
 * 表现上就像“卡死”；过渡动画期间再次导航还可能触发状态异常。
 * 这里统一做：固定间隔内只认第一次操作 + 栈顶复用。
 */
class SafeNavigator(
    private val navController: NavHostController,
    private val intervalMs: Long = 350L
) {
    private var lastActionAt = 0L

    private fun claimSlot(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastActionAt < intervalMs) return false
        lastActionAt = now
        return true
    }

    fun navigate(route: String) {
        if (!claimSlot()) return
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
        }
    }

    fun back() {
        if (!claimSlot()) return
        navController.popBackStack()
    }
}

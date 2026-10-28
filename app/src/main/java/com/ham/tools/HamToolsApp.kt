package com.ham.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.ham.tools.data.remote.UserSession
import com.ham.tools.navigation.HamNavHost
import com.ham.tools.ui.splash.SPLASH_DURATION_MS
import com.ham.tools.ui.splash.SplashBrand
import com.ham.tools.ui.theme.M3Variant
import com.ham.tools.ui.theme.ThemeMode
import kotlinx.coroutines.delay

/**
 * 应用根组合：持有全局 navController 与 Snackbar 宿主，承载启动动画。
 * 主题由外层 HamToolsTheme 提供，各页面自行处理系统栏留白。
 */
@Composable
fun HamToolsApp(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    accentSeed: Int,
    accentVariant: M3Variant,
    session: UserSession?,
    authState: AuthUiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onAccentChange: (Int, M3Variant) -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onLogout: () -> Unit,
    onClearAuthError: () -> Unit,
    onSessionRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var splashVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(SPLASH_DURATION_MS)
        splashVisible = false
    }
    // 冷启动时用本地令牌向服务器校验一次，令牌失效就退回未登录
    LaunchedEffect(Unit) { onSessionRefresh() }

    Box(modifier = modifier) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) {
            HamNavHost(
                navController = navController,
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                accentSeed = accentSeed,
                accentVariant = accentVariant,
                session = session,
                authState = authState,
                onThemeModeChange = onThemeModeChange,
                onDynamicColorChange = onDynamicColorChange,
                onAccentChange = onAccentChange,
                onLogin = onLogin,
                onRegister = onRegister,
                onLogout = onLogout,
                onClearAuthError = onClearAuthError
            )
        }

        // 启动画面盖在内容之上，内容在其下层完成首帧与入场动画
        AnimatedVisibility(
            visible = splashVisible,
            exit = fadeOut(tween(320)) + scaleOut(
                targetScale = 1.08f,
                animationSpec = tween(320)
            )
        ) {
            SplashBrand()
        }
    }
}

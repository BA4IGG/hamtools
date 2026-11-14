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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.ham.tools.data.exam.ExamLock
import com.ham.tools.data.remote.UserSession
import com.ham.tools.data.update.UpdateController
import com.ham.tools.data.update.UpdatePhase
import com.ham.tools.navigation.HamNavHost
import com.ham.tools.ui.splash.SPLASH_DURATION_MS
import com.ham.tools.ui.splash.SplashBrand
import com.ham.tools.ui.theme.M3Variant
import com.ham.tools.ui.update.UpdateDialog
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
    openQsl: Boolean = false,
    onOpenQslConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var splashVisible by remember { mutableStateOf(true) }
    val updateStatus by UpdateController.status.collectAsStateWithLifecycle()
    val examLocked by ExamLock.locked.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        delay(SPLASH_DURATION_MS)
        splashVisible = false
    }
    // 冷启动时用本地令牌向服务器校验一次，令牌失效就退回未登录
    LaunchedEffect(Unit) { onSessionRefresh() }
    // 启动动画结束后自动检测一次更新；12 小时节流与失败静默都由 UpdateController 负责，
    // 弹窗只挂在最外层，工具详情、考试练习等页面不会被各自重复弹一次。
    LaunchedEffect(splashVisible) {
        if (!splashVisible) {
            UpdateController.check(context, BuildConfig.VERSION_NAME, manual = false)
        }
    }

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
                onClearAuthError = onClearAuthError,
                openQsl = openQsl,
                onOpenQslConsumed = onOpenQslConsumed
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

        // 有更新才弹窗；自动检测失败与「已是最新」都只在关于页卡片里显示，不打扰用户。
        // 模拟考试锁定期间绝不弹窗遮挡试卷，解锁后状态还在，届时再提示。
        if (updateStatus.phase == UpdatePhase.AVAILABLE && !examLocked) {
            updateStatus.release?.let { release ->
                UpdateDialog(
                    release = release,
                    localVersion = BuildConfig.VERSION_NAME,
                    notesOnly = updateStatus.notesOnly,
                    onDismiss = { UpdateController.dismiss() },
                    onSkip = { UpdateController.skip(context, release.tag) }
                )
            }
        }
    }
}

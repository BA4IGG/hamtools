package com.ham.tools.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ham.tools.AuthUiState
import com.ham.tools.data.remote.UserSession
import com.ham.tools.data.tools.ToolIds
import com.ham.tools.data.tools.ToolRegistry
import com.ham.tools.ui.auth.LoginScreen
import com.ham.tools.ui.calc.AntennaLengthScreen
import com.ham.tools.ui.calc.FeedlineLossScreen
import com.ham.tools.ui.calc.LinkBudgetScreen
import com.ham.tools.ui.calc.VswrScreen
import com.ham.tools.ui.calc.WavelengthScreen
import com.ham.tools.ui.pager.TOP_LEVEL_PAGE_COUNT
import com.ham.tools.ui.pager.TopLevelPager
import com.ham.tools.ui.settings.SettingsScreen
import com.ham.tools.ui.theme.M3Variant
import com.ham.tools.ui.theme.ThemeMode
import com.ham.tools.ui.tool.ToolPlaceholderScreen

/** 详情页：从右侧推入，返回时推回右侧。 */
private val detailEnter = fadeIn(tween(240)) + slideInHorizontally(tween(280)) { it / 5 }
private val detailExit = fadeOut(tween(160))
private val detailPopEnter = fadeIn(tween(220))
private val detailPopExit = fadeOut(tween(240)) + slideOutHorizontally(tween(260)) { it / 5 }

@Composable
fun HamNavHost(
    navController: NavHostController,
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
    modifier: Modifier = Modifier
) {
    val navigator = remember(navController) { SafeNavigator(navController) }
    // 提升到 NavHost 之外：从首页进工具详情再返回时，仍停在原来那一页
    val pagerState = rememberPagerState(pageCount = { TOP_LEVEL_PAGE_COUNT })

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {
        composable(
            route = Routes.HOME,
            enterTransition = { detailPopEnter },
            exitTransition = { detailExit },
            popEnterTransition = { detailPopEnter },
            popExitTransition = { detailPopExit }
        ) {
            TopLevelPager(
                pagerState = pagerState,
                session = session,
                onToolClick = { toolId -> navigator.navigate(Routes.tool(toolId)) },
                onSettingsClick = { navigator.navigate(Routes.SETTINGS) },
                onLoginClick = { navigator.navigate(Routes.LOGIN) },
                onLogout = onLogout
            )
        }

        composable(
            route = Routes.SETTINGS,
            enterTransition = { detailEnter },
            exitTransition = { detailExit },
            popEnterTransition = { detailPopEnter },
            popExitTransition = { detailPopExit }
        ) {
            SettingsScreen(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                accentSeed = accentSeed,
                accentVariant = accentVariant,
                onThemeModeChange = onThemeModeChange,
                onDynamicColorChange = onDynamicColorChange,
                onAccentChange = onAccentChange,
                onBack = { navigator.back() }
            )
        }

        composable(
            route = Routes.LOGIN,
            enterTransition = { detailEnter },
            exitTransition = { detailExit },
            popEnterTransition = { detailPopEnter },
            popExitTransition = { detailPopExit }
        ) {
            LoginScreen(
                uiState = authState,
                session = session,
                onLogin = onLogin,
                onRegister = onRegister,
                onClearError = onClearAuthError,
                onLoggedIn = { navigator.back() },
                onBack = { navigator.back() }
            )
        }

        composable(
            route = Routes.TOOL,
            arguments = listOf(navArgument(Routes.ARG_TOOL_ID) { type = NavType.StringType }),
            enterTransition = { detailEnter },
            exitTransition = { detailExit },
            popEnterTransition = { detailPopEnter },
            popExitTransition = { detailPopExit }
        ) { backStackEntry ->
            val toolId = backStackEntry.arguments?.getString(Routes.ARG_TOOL_ID)
            val tool = ToolRegistry.find(toolId)
            val onBack: () -> Unit = { navigator.back() }

            when (tool?.id) {
                ToolIds.WAVELENGTH -> WavelengthScreen(onBack = onBack)
                ToolIds.ANTENNA_LENGTH -> AntennaLengthScreen(onBack = onBack)
                ToolIds.VSWR -> VswrScreen(onBack = onBack)
                ToolIds.FEEDLINE_LOSS -> FeedlineLossScreen(onBack = onBack)
                ToolIds.LINK_BUDGET -> LinkBudgetScreen(onBack = onBack)
                else -> ToolPlaceholderScreen(
                    toolName = tool?.name,
                    toolDescription = tool?.description,
                    onBack = onBack
                )
            }
        }
    }
}

package com.ham.tools.ui.pager

import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ham.tools.R
import com.ham.tools.data.remote.UserSession
import com.ham.tools.ui.about.AboutScreen
import com.ham.tools.ui.components.HamDock
import com.ham.tools.ui.home.HomeScreen
import com.ham.tools.ui.profile.ProfileScreen
import com.ham.tools.ui.qsl.QslScreen
import kotlinx.coroutines.launch

/** 底部 Dock 对应的四个顶层页面。 */
const val PAGE_HOME = 0
const val PAGE_PROFILE = 1
const val PAGE_ABOUT = 2
const val PAGE_QSL = 3
const val TOP_LEVEL_PAGE_COUNT = 4

/**
 * 顶层页面容器：左右滑动或点 Dock 都能在「主页 / 我的 / 关于 / QSL 交换」之间切换。
 *
 * 用 Pager 而不是导航路由，好处是：切换完全发生在同一个目的地内，
 * 不会被回退栈影响（连点、快速来回切都不会堆积页面），
 * 而且滑动过程中 Dock 指示器可以连续跟随。
 *
 * QSL 交换是独立板块，不在工具集里注册，因此只作为 Dock 的第四个顶层页出现。
 */
@Composable
fun TopLevelPager(
    session: UserSession?,
    onToolClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onLoginClick: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    openQsl: Boolean = false,
    onOpenQslConsumed: () -> Unit = {},
    qslBadge: Int = 0,
    pagerState: PagerState = rememberPagerState(pageCount = { TOP_LEVEL_PAGE_COUNT })
) {
    val scope = rememberCoroutineScope()
    val labels = listOf(
        stringResource(R.string.dock_home),
        stringResource(R.string.dock_profile),
        stringResource(R.string.dock_about),
        stringResource(R.string.dock_qsl)
    )
    val selectedIcons = listOf(
        Icons.Filled.Home, Icons.Filled.Person, Icons.Filled.Info, Icons.Filled.Email
    )
    val unselectedIcons = listOf(
        Icons.Outlined.Home,
        Icons.Outlined.PersonOutline,
        Icons.Outlined.Info,
        Icons.Outlined.Email
    )
    val badges = listOf(0, 0, 0, qslBadge)

    // 从通知点进来时切到 QSL 板块；用状态而非一次性事件，重组后也不会丢
    LaunchedEffect(openQsl) {
        if (openQsl) {
            runCatching { pagerState.animateScrollToPage(PAGE_QSL) }
            onOpenQslConsumed()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            HamDock(
                selectedFraction = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                onPageSelected = { page ->
                    scope.launch {
                        pagerState.animateScrollToPage(
                            page = page,
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)
                        )
                    }
                },
                labels = labels,
                selectedIcons = selectedIcons,
                unselectedIcons = unselectedIcons,
                badges = badges
            )
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(innerPadding)
        ) { page ->
            when (page) {
                PAGE_HOME -> HomeScreen(
                    onToolClick = onToolClick,
                    onAboutClick = {
                        scope.launch { pagerState.animateScrollToPage(PAGE_ABOUT) }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                PAGE_PROFILE -> ProfileScreen(
                    session = session,
                    onLoginClick = onLoginClick,
                    onSettingsClick = onSettingsClick,
                    onLogout = onLogout,
                    modifier = Modifier.fillMaxSize()
                )
                PAGE_ABOUT -> AboutScreen(
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.fillMaxSize()
                )
                PAGE_QSL -> QslScreen(
                    session = session,
                    onLoginClick = onLoginClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

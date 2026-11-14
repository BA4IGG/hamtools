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
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.ham.tools.R
import com.ham.tools.data.exam.ExamLock
import com.ham.tools.data.remote.UserSession
import com.ham.tools.ui.about.AboutScreen
import com.ham.tools.ui.components.HamDock
import com.ham.tools.ui.exam.ExamPracticeScreen
import com.ham.tools.ui.home.HomeScreen
import com.ham.tools.ui.profile.ProfileScreen
import com.ham.tools.ui.qsl.QslScreen
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 底部 Dock 对应的五个顶层页面，顺序即 Dock 从左到右的顺序：
 * 主页 / QSL 交换 / 考试练习 / 我的 / 关于。
 * 「考试练习」固定在正中央（index 2），保持凸起按钮与首页相邻的动线。
 */
const val PAGE_HOME = 0
const val PAGE_QSL = 1
const val PAGE_EXAM = 2
const val PAGE_PROFILE = 3
const val PAGE_ABOUT = 4
const val TOP_LEVEL_PAGE_COUNT = 5

/**
 * 顶层页面容器：左右滑动或点 Dock 都能在「主页 / QSL 交换 / 考试练习 / 我的 / 关于」之间切换。
 *
 * 用 Pager 而不是导航路由，好处是：切换完全发生在同一个目的地内，
 * 不会被回退栈影响（连点、快速来回切都不会堆积页面），
 * 而且滑动过程中 Dock 指示器可以连续跟随。
 *
 * 「考试练习」与「QSL 交换」都是独立板块，不在工具集里注册；
 * 按用户要求，考试练习固定在 Dock 正中央，QSL 交换紧邻主页。
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
    val examLocked by ExamLock.locked.collectAsStateWithLifecycle()
    // 短标签：五个按钮平分宽度，长名字会在窄屏或大字体下被截断
    val labels = listOf(
        stringResource(R.string.dock_label_home),
        stringResource(R.string.dock_label_qsl),
        stringResource(R.string.dock_label_exam),
        stringResource(R.string.dock_label_profile),
        stringResource(R.string.dock_label_about)
    )
    // 无障碍朗读仍使用完整板块名
    val contentDescriptions = listOf(
        stringResource(R.string.dock_home),
        stringResource(R.string.dock_qsl),
        stringResource(R.string.dock_exam),
        stringResource(R.string.dock_profile),
        stringResource(R.string.dock_about)
    )
    val selectedIcons = listOf(
        Icons.Filled.Home, Icons.Filled.Email, Icons.Filled.Quiz, Icons.Filled.Person, Icons.Filled.Info
    )
    val unselectedIcons = listOf(
        Icons.Outlined.Home,
        Icons.Outlined.Email,
        Icons.Filled.Quiz,
        Icons.Outlined.PersonOutline,
        Icons.Outlined.Info
    )
    val badges = listOf(0, qslBadge, 0, 0, 0)

    // 从通知点进来时切到 QSL 板块；用状态而非一次性事件，重组后也不会丢。
    // 模拟考试锁定期间不切页，等解锁后再处理，避免通知把用户带离考试界面。
    LaunchedEffect(openQsl, examLocked) {
        if (openQsl && !examLocked) {
            runCatching { pagerState.animateScrollToPage(PAGE_QSL) }
            onOpenQslConsumed()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            // 模拟考试期间彻底隐藏 Dock：不给任何「点一下就离开考试」的入口
            if (!examLocked) {
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
                    contentDescriptions = contentDescriptions,
                    selectedIcons = selectedIcons,
                    unselectedIcons = unselectedIcons,
                    badges = badges,
                    centerIndex = PAGE_EXAM
                )
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            // 考试期间禁用左右滑动切页；系统返回键由考试页的 BackHandler 接管
            userScrollEnabled = !examLocked,
            // QSL 现在是第 2 页、紧邻主页，可能被预组合；副作用由 QslScreen 的
            // active 参数控制，预组合时不会绑定账号，也不会弹出窗口级的权限对话框。
            modifier = Modifier.padding(innerPadding)
        ) { page ->
            // 切换时未选中的页轻微淡出缩小，让横向滑动更有层次；
            // 停稳后当前页恒为不透明、原始尺寸，不影响阅读与点击。
            // 状态读取放在 graphicsLayer 里，只触发重绘、不触发整页重组。
            val pageModifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val distance = abs(
                        (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    ).coerceIn(0f, 1f)
                    alpha = 1f - 0.16f * distance
                    val scale = 1f - 0.018f * distance
                    scaleX = scale
                    scaleY = scale
                }
            when (page) {
                PAGE_HOME -> HomeScreen(
                    onToolClick = onToolClick,
                    onAboutClick = {
                        scope.launch { pagerState.animateScrollToPage(PAGE_ABOUT) }
                    },
                    modifier = pageModifier
                )
                PAGE_PROFILE -> ProfileScreen(
                    session = session,
                    onLoginClick = onLoginClick,
                    onSettingsClick = onSettingsClick,
                    onLogout = onLogout,
                    modifier = pageModifier
                )
                PAGE_EXAM -> ExamPracticeScreen(
                    session = session,
                    onLoginClick = onLoginClick,
                    modifier = pageModifier
                )
                PAGE_ABOUT -> AboutScreen(
                    onSettingsClick = onSettingsClick,
                    modifier = pageModifier
                )
                PAGE_QSL -> QslScreen(
                    session = session,
                    onLoginClick = onLoginClick,
                    active = pagerState.currentPage == page,
                    modifier = pageModifier
                )
            }
        }
    }
}

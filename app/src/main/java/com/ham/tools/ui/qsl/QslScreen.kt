package com.ham.tools.ui.qsl

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ham.tools.R
import com.ham.tools.data.qsl.QslMode
import com.ham.tools.data.qsl.QslNotifier
import com.ham.tools.data.remote.UserSession
import com.ham.tools.ui.components.HamTopBar
import com.ham.tools.ui.components.StatusBanner
import com.ham.tools.ui.theme.AppShapes

private val QSL_MODES = listOf(QslMode.PAPER, QslMode.EQSL)
private val QSL_SECTIONS = listOf("我的资料", "发起交换", "交换记录", "台站名录")

/**
 * QSL 卡片交换板块（底部 Dock 第 2 页，紧邻主页，不在工具集里注册）。
 *
 * 三层结构：登录门禁 → 纸质 / eQSL 两大板块 → 每个板块内四个功能子页。
 * 铁律：未登录一律不给用；服务端没部署 QSL 接口时醒目显示「本地演示」，
 * 绝不把 DEMO 的 EXAMPLE 呼号当成真实用户。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QslScreen(
    session: UserSession?,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 当前页是否真的切到了 QSL。HorizontalPager 会预组合相邻页，
     * 若不区分可见性，冷启动时就会在这里弹出通知权限说明、提前绑定账号。
     */
    active: Boolean = true
) {
    val viewModel: QslViewModel = viewModel()
    val state = viewModel.state
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 4
        }
    }

    var modeIndex by rememberSaveable { mutableIntStateOf(0) }
    var sectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var prefill by rememberSaveable { mutableStateOf("") }

    // 通知权限：首次进入时先讲清楚用途再申请，被拒绝后不反复弹窗
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }
    var permissionVersion by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> permissionVersion++ }

    LaunchedEffect(session?.token, active) {
        val current = session
        if (active && current != null) {
            viewModel.bind(current)
            if (!permissionAsked &&
                QslNotifier.needsRuntimePermission() &&
                !QslNotifier.canNotify(context)
            ) {
                showRationale = true
            }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // permissionVersion 变化触发重组，重新读取系统里的授权状态
    val notificationsEnabled = remember(permissionVersion, session?.token) {
        QslNotifier.canNotify(context)
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            HamTopBar(
                title = stringResource(R.string.dock_qsl),
                subtitle = stringResource(R.string.header_subtitle_qsl),
                scrolled = scrolled,
                actions = {
                    if (session != null) {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "刷新"
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (session == null) {
            QslLoginGate(
                onLoginClick = onLoginClick,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            )
        } else {
            // 整页只保留一个滚动容器：概览、通知卡、当前子页、底部说明一起滚动。
            // 只有两个 Tab 固定，避免上半部分被钉死后内容区过窄、键盘弹出时点不到按钮。
            LaunchedEffect(modeIndex, sectionIndex) {
                listState.scrollToItem(0)
            }

            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .imePadding()
            ) {
                if (state.loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                PrimaryTabRow(
                    selectedTabIndex = modeIndex,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    QSL_MODES.forEachIndexed { index, mode ->
                        Tab(
                            selected = modeIndex == index,
                            onClick = { modeIndex = index },
                            text = { Text(text = mode.label) }
                        )
                    }
                }
                SecondaryTabRow(
                    selectedTabIndex = sectionIndex,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    QSL_SECTIONS.forEachIndexed { index, label ->
                        Tab(
                            selected = sectionIndex == index,
                            onClick = { sectionIndex = index },
                            text = { Text(text = label, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 32.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (state.demo) {
                                StatusBanner(
                                    text = "本地演示模式：服务端尚未部署 QSL 接口，下面的 EXAMPLE 呼号与交换记录都只是演示数据，" +
                                        "不会发送给任何其他用户，退出 App 后不保存。",
                                    warning = true
                                )
                            } else {
                                StatusBanner(
                                    text = "已连接 ${session.profile.callsign} 的账号 · 交换记录保存在你自己的服务器账号下"
                                )
                            }
                            QslOverview(state.summary)
                            QslNotificationCard(
                                enabled = notificationsEnabled,
                                demo = state.demo,
                                onEnable = { showRationale = true },
                                onTest = { QslNotifier.notifyDemoTest(context) },
                                onOpenSettings = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        runCatching { context.startActivity(intent) }
                                    }
                                }
                            )
                        }
                    }

                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val mode = QSL_MODES[modeIndex]
                            when (sectionIndex) {
                                0 -> QslProfileEditor(
                                    viewModel = viewModel,
                                    mode = mode,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                1 -> QslNewExchange(
                                    viewModel = viewModel,
                                    mode = mode,
                                    prefill = prefill,
                                    onPrefillConsumed = { prefill = "" },
                                    onSubmitted = { sectionIndex = 2 },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                2 -> QslExchangeList(
                                    viewModel = viewModel,
                                    mode = mode,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                else -> QslDirectory(
                                    viewModel = viewModel,
                                    mode = mode,
                                    onPeerSelected = { peer ->
                                        prefill = peer
                                        sectionIndex = 1
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = "QSL 交换需要联网；纸质交换会涉及双方邮寄地址，请自行判断风险。" +
                                "本 App 不集成第三方推送，新申请由前台轮询 / 后台周期检查后在本机发出通知。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }

    // Dialog 是窗口级的：即使页面被滑出视口，一旦组合就会浮在整个 App 上，
    // 所以必须同时要求当前页确实是 QSL。
    if (active && showRationale) {
        AlertDialog(
            onDismissRequest = {
                showRationale = false
                permissionAsked = true
            },
            title = { Text(text = "开启交换申请通知？") },
            text = {
                Text(
                    text = "当别人向你发起 QSL 卡片交换申请时，App 会在本机发一条通知提醒你及时处理。" +
                        "演示模式或服务端未部署时不会发任何交换通知。你也可以稍后在这一页手动开启或关闭。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionAsked = true
                    if (QslNotifier.needsRuntimePermission()) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        QslNotifier.notifyDemoTest(context)
                    }
                }) { Text(text = "开启通知") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionAsked = true
                }) { Text(text = "暂不开启") }
            }
        )
    }
}

@Composable
private fun QslLoginGate(onLoginClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.padding(24.dp),
            shape = AppShapes.large,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "QSL 交换需要登录",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "卡片交换会记录双方呼号、通联信息与邮寄地址，" +
                        "必须使用账号才能确认身份、保存你的交换历史。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onLoginClick, shape = AppShapes.medium) {
                    Text(text = "登录 / 注册")
                }
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "登录后可以使用：",
                style = MaterialTheme.typography.titleSmall
            )
            listOf(
                "纸质卡片交换：申请 → 接受 → 寄出 → 确认收到",
                "eQSL 电子卡片交换：接受即互换卡片图，无需邮寄",
                "填写 QSL 名片、上传卡片图片、查找可交换台站",
                "完整的交换历史与统计，以及新申请通知"
            ).forEach { item ->
                Text(
                    text = "· $item",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QslOverview(summary: com.ham.tools.data.qsl.QslSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "交换概览",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QslStatTile("待处理", summary.incoming, Modifier.weight(1f), highlight = summary.incoming > 0)
                QslStatTile("我发出", summary.outgoing, Modifier.weight(1f))
                QslStatTile("已完成", summary.completed, Modifier.weight(1f))
                QslStatTile("纸质", summary.paper, Modifier.weight(1f))
                QslStatTile("eQSL", summary.eqsl, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QslNotificationCard(
    enabled: Boolean,
    demo: Boolean,
    onEnable: () -> Unit,
    onTest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = if (enabled) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            ) {
                Text(
                    text = if (enabled) "交换申请通知已开启" else "通知未开启",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
                Text(
                    text = if (demo) {
                        "演示模式不会发送交换通知，可先发一条测试通知看看效果"
                    } else {
                        "有新申请时在本机提醒；不是服务器主动推送"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }
                if (!enabled) {
                    TextButton(onClick = onEnable) { Text(text = "开启") }
                } else {
                    FilledTonalButton(onClick = onTest, shape = AppShapes.small) {
                        Text(text = "测试", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (!enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(text = "去系统设置开启", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

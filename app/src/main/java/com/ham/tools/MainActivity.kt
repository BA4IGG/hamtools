package com.ham.tools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ham.tools.data.preferences.UserPreferences
import com.ham.tools.data.preferences.settingsDataStore
import com.ham.tools.ui.theme.HamToolsTheme
import com.ham.tools.util.FrameRateBoost
import com.ham.tools.util.FrameRateController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val preferences by lazy { UserPreferences(settingsDataStore) }
    private val boost by lazy { FrameRateBoost(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 应用层：告诉系统本 App 想跑多少帧（60Hz 屏会自动跳过）
        FrameRateController.apply(this)
        // 部分 ROM 会在窗口真正显示后重置窗口属性，首帧之后再补一次
        window.decorView.post { FrameRateController.apply(this) }
        watchRefreshBoost()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
            val accentSeed by viewModel.accentSeed.collectAsStateWithLifecycle()
            val accentVariant by viewModel.accentVariant.collectAsStateWithLifecycle()
            val session by viewModel.session.collectAsStateWithLifecycle()
            val authState by viewModel.authState.collectAsStateWithLifecycle()

            HamToolsTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                accentSeed = accentSeed,
                accentVariant = accentVariant
            ) {
                HamToolsApp(
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    accentSeed = accentSeed,
                    accentVariant = accentVariant,
                    onThemeModeChange = viewModel::setThemeMode,
                    onDynamicColorChange = viewModel::setDynamicColor,
                    onAccentChange = viewModel::setAccent,
                    session = session,
                    authState = authState,
                    onLogin = viewModel::login,
                    onRegister = viewModel::register,
                    onLogout = viewModel::logout,
                    onClearAuthError = viewModel::clearAuthError,
                    onSessionRefresh = viewModel::refreshSession
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        boost.onBackground()
    }

    override fun onResume() {
        super.onResume()
        // 回到前台时重申一次偏好，防止系统在切后台期间把刷新率收回去
        FrameRateController.apply(this)
        boost.onForeground()
    }

    /**
     * 监听用户的「提升到最高帧率」开关。
     *
     * 用 repeatOnLifecycle 而不是在 onResume 里读一次：开关拨动时 Activity 仍在前台，
     * 不触发生命周期回调，只有数据驱动才能立刻生效。
     */
    private fun watchRefreshBoost() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                preferences.refreshBoost.collect { enabled ->
                    applyBoost(enabled)
                }
            }
        }
    }

    /** 写系统设置是阻塞 IPC，放到 IO 线程，别拖慢首帧。 */
    private suspend fun applyBoost(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            val targetHz = FrameRateController.probe(this@MainActivity).targetHz
            if (enabled && targetHz > 0f) {
                boost.setTarget(targetHz)
            } else {
                boost.clearTarget()
            }
        }
    }
}

package com.ham.tools

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ham.tools.data.preferences.UserPreferences
import com.ham.tools.data.preferences.settingsDataStore
import com.ham.tools.data.remote.AuthErrorCode
import com.ham.tools.data.remote.AuthRepository
import com.ham.tools.data.remote.AuthResult
import com.ham.tools.data.remote.UserSession
import com.ham.tools.ui.theme.DEFAULT_ACCENT_SEED
import com.ham.tools.ui.theme.M3Variant
import com.ham.tools.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 登录/注册这类异步操作的界面状态。 */
data class AuthUiState(
    val inFlight: Boolean = false,
    val error: AuthErrorCode? = null
)

/** 应用级界面状态：主题偏好与账号登录态，均持久化在本地。 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val userPreferences = UserPreferences(application.settingsDataStore)
    private val authRepository = AuthRepository()

    val themeMode: StateFlow<ThemeMode> = userPreferences.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM
        )

    val dynamicColor: StateFlow<Boolean> = userPreferences.dynamicColor
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true
        )

    val accentSeed: StateFlow<Int> = userPreferences.accentSeed
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DEFAULT_ACCENT_SEED
        )

    val accentVariant: StateFlow<M3Variant> = userPreferences.accentVariant
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = M3Variant.TONAL_SPOT
        )

    val session: StateFlow<UserSession?> = userPreferences.session
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private val _authState = MutableStateFlow(AuthUiState())
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { userPreferences.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setDynamicColor(enabled) }
    }

    fun setAccent(seed: Int, variant: M3Variant) {
        viewModelScope.launch { userPreferences.setAccent(seed, variant) }
    }

    fun clearAuthError() {
        _authState.value = _authState.value.copy(error = null)
    }

    /** 呼号 + 密码登录，成功后本地保存令牌。 */
    fun login(callsign: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthUiState(inFlight = true)
            val result = authRepository.login(callsign, password)
            _authState.value = when (result) {
                is AuthResult.Success -> {
                    userPreferences.saveSession(result.value)
                    AuthUiState()
                }
                is AuthResult.Failure -> AuthUiState(error = result.code)
            }
        }
    }

    /** 呼号注册，成功后直接登录。 */
    fun register(callsign: String, password: String, nickname: String) {
        viewModelScope.launch {
            _authState.value = AuthUiState(inFlight = true)
            val result = authRepository.register(callsign, password, nickname)
            _authState.value = when (result) {
                is AuthResult.Success -> {
                    userPreferences.saveSession(result.value)
                    AuthUiState()
                }
                is AuthResult.Failure -> AuthUiState(error = result.code)
            }
        }
    }

    fun logout() {
        viewModelScope.launch { userPreferences.clearSession() }
    }

    /** 冷启动校验：令牌失效就清掉本地登录态。 */
    fun refreshSession() {
        val current = session.value ?: return
        viewModelScope.launch {
            when (val result = authRepository.fetchProfile(current.token)) {
                is AuthResult.Success -> userPreferences.saveSession(
                    current.copy(profile = result.value)
                )
                is AuthResult.Failure -> {
                    if (result.code in INVALID_TOKEN_CODES) {
                        userPreferences.clearSession()
                    }
                }
            }
        }
    }

    companion object {
        private val INVALID_TOKEN_CODES = setOf(
            AuthErrorCode.NO_TOKEN,
            AuthErrorCode.TOKEN_EXPIRED,
            AuthErrorCode.NO_USER
        )
    }
}

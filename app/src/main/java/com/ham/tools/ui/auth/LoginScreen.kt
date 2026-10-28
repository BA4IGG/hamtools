package com.ham.tools.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ham.tools.AuthUiState
import com.ham.tools.R
import com.ham.tools.data.remote.UserSession
import com.ham.tools.ui.components.staggerEnter
import com.ham.tools.ui.theme.HamToolsTheme
import com.ham.tools.ui.theme.ThemeMode
import com.ham.tools.util.Callsign

private const val TAB_LOGIN = 0
private const val TAB_REGISTER = 1
private const val MIN_PASSWORD = 6

/**
 * 登录 / 注册页：呼号 + 密码。
 *
 * 呼号按工信部规则校验，密码规则与服务端一致（6~64 位）。
 * 服务器地址固定为 AppConfig.SERVER_BASE_URL，界面上不再提供修改入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    uiState: AuthUiState,
    session: UserSession?,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onClearError: () -> Unit,
    onLoggedIn: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tabIndex by remember { mutableIntStateOf(TAB_LOGIN) }
    var callsign by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var localErrorRes by remember { mutableStateOf<Int?>(null) }

    // 登录/注册成功后由上层负责返回，这里只负责感知登录态变化
    LaunchedEffect(session?.token) {
        if (session != null) onLoggedIn()
    }

    val isRegister = tabIndex == TAB_REGISTER
    val normalizedCallsign = remember(callsign) { Callsign.normalize(callsign) }

    fun submit() {
        onClearError()
        val normalized = Callsign.normalize(callsign)
        val problem = when {
            normalized == null -> R.string.login_callsign_invalid
            password.length < MIN_PASSWORD -> R.string.login_password_too_short
            isRegister && password != confirmPassword -> R.string.login_password_mismatch
            else -> null
        }
        localErrorRes = problem
        if (problem != null) return
        val safeCallsign = normalized ?: return
        if (isRegister) {
            onRegister(safeCallsign, password, nickname.trim())
        } else {
            onLogin(safeCallsign, password)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.login_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Radio,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            PrimaryTabRow(
                selectedTabIndex = tabIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = !isRegister,
                    onClick = { tabIndex = TAB_LOGIN; localErrorRes = null; onClearError() },
                    text = { Text(text = stringResource(R.string.login_tab_login)) }
                )
                Tab(
                    selected = isRegister,
                    onClick = { tabIndex = TAB_REGISTER; localErrorRes = null; onClearError() },
                    text = { Text(text = stringResource(R.string.login_tab_register)) }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedTextField(
                value = callsign,
                onValueChange = { callsign = it; localErrorRes = null },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(text = stringResource(R.string.login_callsign_label)) },
                placeholder = { Text(text = stringResource(R.string.login_callsign_placeholder)) },
                visualTransformation = remember { UppercaseTransformation() },
                isError = callsign.isNotBlank() && normalizedCallsign == null,
                supportingText = {
                    if (callsign.isNotBlank() && normalizedCallsign == null) {
                        Text(text = stringResource(R.string.login_callsign_invalid))
                    }
                },
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; localErrorRes = null },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(text = stringResource(R.string.login_password_label)) },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Filled.Visibility
                            } else {
                                Icons.Filled.VisibilityOff
                            },
                            contentDescription = null
                        )
                    }
                },
                isError = password.isNotBlank() && password.length < MIN_PASSWORD,
                supportingText = {
                    if (password.isNotBlank() && password.length < MIN_PASSWORD) {
                        Text(text = stringResource(R.string.login_password_too_short))
                    }
                },
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            if (isRegister) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; localErrorRes = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.login_password_confirm_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = confirmPassword.isNotBlank() && confirmPassword != password,
                    supportingText = {
                        if (confirmPassword.isNotBlank() && confirmPassword != password) {
                            Text(text = stringResource(R.string.login_password_mismatch))
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.login_nickname_label)) },
                    shape = MaterialTheme.shapes.medium
                )
            }

            val remoteError = uiState.error
            val combinedError = when {
                localErrorRes != null -> stringResource(localErrorRes!!)
                remoteError != null -> authErrorMessage(remoteError)
                else -> null
            }
            if (combinedError != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = combinedError,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val canSubmit = normalizedCallsign != null &&
                password.length >= MIN_PASSWORD &&
                (!isRegister || confirmPassword == password) &&
                !uiState.inFlight

            Button(
                onClick = { submit() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = canSubmit,
                shape = MaterialTheme.shapes.large
            ) {
                if (uiState.inFlight) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(
                            if (isRegister) R.string.login_action_register else R.string.login_action_login
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** 呼号输入自动显示成大写，实际提交时也用规范化后的大写值。 */
private class UppercaseTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(AnnotatedString(text.text.uppercase()), OffsetMapping.Identity)
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    HamToolsTheme(themeMode = ThemeMode.DARK) {
        LoginScreen(
            uiState = AuthUiState(),
            session = null,
            onLogin = { _, _ -> },
            onRegister = { _, _, _ -> },
            onClearError = {},
            onLoggedIn = {},
            onBack = {}
        )
    }
}

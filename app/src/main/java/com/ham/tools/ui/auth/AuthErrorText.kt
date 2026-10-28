package com.ham.tools.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ham.tools.R
import com.ham.tools.data.remote.AuthErrorCode

/** 把服务端/网络层返回的错误码翻译成界面文案。 */
@Composable
fun authErrorMessage(code: AuthErrorCode): String = stringResource(
    when (code) {
        AuthErrorCode.NO_SERVER -> R.string.auth_error_NO_SERVER
        AuthErrorCode.NETWORK -> R.string.auth_error_NETWORK
        AuthErrorCode.BAD_RESPONSE -> R.string.auth_error_BAD_RESPONSE
        AuthErrorCode.INVALID_CALLSIGN -> R.string.auth_error_INVALID_CALLSIGN
        AuthErrorCode.WEAK_PASSWORD -> R.string.auth_error_WEAK_PASSWORD
        AuthErrorCode.INVALID_NICKNAME -> R.string.auth_error_INVALID_NICKNAME
        AuthErrorCode.CALLSIGN_TAKEN -> R.string.auth_error_CALLSIGN_TAKEN
        AuthErrorCode.BAD_CREDENTIALS -> R.string.auth_error_BAD_CREDENTIALS
        AuthErrorCode.NO_TOKEN -> R.string.auth_error_NO_TOKEN
        AuthErrorCode.TOKEN_EXPIRED -> R.string.auth_error_TOKEN_EXPIRED
        AuthErrorCode.NO_USER -> R.string.auth_error_NO_USER
        AuthErrorCode.NOT_FOUND -> R.string.auth_error_NOT_FOUND
        AuthErrorCode.SERVER_ERROR -> R.string.auth_error_SERVER_ERROR
    }
)

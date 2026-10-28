package com.ham.tools.data.remote

/** 服务端返回的账号资料。 */
data class UserProfile(
    val callsign: String,
    val nickname: String = "",
    val region: String? = null,
    val createdAt: String = ""
)

/** 登录态：令牌 + 资料，令牌持久化在本地，App 冷启动时用 /me 校验。 */
data class UserSession(
    val token: String,
    val profile: UserProfile
)

/** 账号操作的结果：成功带数据，失败带错误码（界面负责翻译成中文）。 */
sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>
    data class Failure(val code: AuthErrorCode, val message: String = "") : AuthResult<Nothing>
}

/**
 * 服务端错误码。与 server/src/routes/auth.js 一一对应，
 * 网络层与解析层的失败也归到这里，界面只需要处理这一个枚举。
 */
enum class AuthErrorCode {
    /** 服务器地址为空或格式不合法 */
    NO_SERVER,

    /** 连不上服务器 / 超时 / 读写出错 */
    NETWORK,

    /** 服务器返回了无法解析的内容 */
    BAD_RESPONSE,

    INVALID_CALLSIGN,
    WEAK_PASSWORD,
    INVALID_NICKNAME,
    CALLSIGN_TAKEN,
    BAD_CREDENTIALS,
    NO_TOKEN,
    TOKEN_EXPIRED,
    NO_USER,
    NOT_FOUND,
    SERVER_ERROR;

    companion object {
        fun fromCode(code: String?): AuthErrorCode {
            if (code.isNullOrBlank()) return SERVER_ERROR
            return entries.firstOrNull { it.name == code } ?: SERVER_ERROR
        }
    }
}

package com.ham.tools.data.remote

import com.ham.tools.data.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 账号服务的数据访问层：只负责发请求、解 JSON、把失败翻译成 [AuthErrorCode]。
 *
 * 服务端地址固定在 [AppConfig.SERVER_BASE_URL]，不提供运行时修改入口；
 * 换服务器时改 AppConfig 里的那一行常量即可。
 */
class AuthRepository(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = AppConfig.SERVER_BASE_URL
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** 呼号注册。 */
    suspend fun register(
        callsign: String,
        password: String,
        nickname: String
    ): AuthResult<UserSession> = postSession("/api/auth/register") {
        put("callsign", callsign)
        put("password", password)
        put("nickname", nickname)
    }

    /** 呼号登录。 */
    suspend fun login(
        callsign: String,
        password: String
    ): AuthResult<UserSession> = postSession("/api/auth/login") {
        put("callsign", callsign)
        put("password", password)
    }

    /** 用本地令牌换取最新资料，用于冷启动校验登录态。 */
    suspend fun fetchProfile(token: String): AuthResult<UserProfile> {
        val request = Request.Builder()
            .url(buildUrl("/api/auth/me"))
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return call(request) { body ->
            AuthResult.Success(parseProfile(JSONObject(body).optJSONObject("user")))
        }
    }

    /** 修改昵称。 */
    suspend fun updateNickname(
        token: String,
        nickname: String
    ): AuthResult<UserProfile> {
        val body = JSONObject().apply { put("nickname", nickname) }.toString()
        val request = Request.Builder()
            .url(buildUrl("/api/auth/profile"))
            .header("Authorization", "Bearer $token")
            .patch(body.toRequestBody(jsonMediaType))
            .build()
        return call(request) { responseBody ->
            AuthResult.Success(parseProfile(JSONObject(responseBody).optJSONObject("user")))
        }
    }

    // ---------------------------------------------------------------- 内部实现

    private suspend fun postSession(
        path: String,
        fill: JSONObject.() -> Unit
    ): AuthResult<UserSession> {
        val body = JSONObject().apply(fill).toString()
        val request = Request.Builder()
            .url(buildUrl(path))
            .post(body.toRequestBody(jsonMediaType))
            .build()
        return call(request) { responseBody ->
            val json = JSONObject(responseBody)
            AuthResult.Success(
                UserSession(
                    token = json.optString("token"),
                    profile = parseProfile(json.optJSONObject("user"))
                )
            )
        }
    }

    private suspend fun <T> call(
        request: Request,
        parse: (String) -> AuthResult<T>
    ): AuthResult<T> = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            return@withContext AuthResult.Failure(AuthErrorCode.NETWORK, error.message.orEmpty())
        } catch (error: IllegalArgumentException) {
            return@withContext AuthResult.Failure(AuthErrorCode.BAD_RESPONSE, error.message.orEmpty())
        }

        val payload = response.use { it.body?.string().orEmpty() }
        if (!response.isSuccessful) {
            val error = runCatching { JSONObject(payload).optJSONObject("error") }.getOrNull()
            return@withContext AuthResult.Failure(
                code = AuthErrorCode.fromCode(error?.optString("code")),
                message = error?.optString("message").orEmpty()
            )
        }

        try {
            parse(payload)
        } catch (error: Exception) {
            AuthResult.Failure(AuthErrorCode.BAD_RESPONSE, error.message.orEmpty())
        }
    }

    private fun buildUrl(path: String): String {
        // 兜底：万一传进来空地址，退回常量，避免构造 URL 失败导致崩溃
        val root = baseUrl.trim().trimEnd('/').ifBlank { AppConfig.SERVER_BASE_URL }
        return "$root$path"
    }

    private fun parseProfile(json: JSONObject?): UserProfile {
        if (json == null) throw IllegalArgumentException("missing user")
        return UserProfile(
            callsign = json.optString("callsign"),
            nickname = json.optString("nickname"),
            region = json.optString("region").takeIf { it.isNotBlank() },
            createdAt = json.optString("createdAt")
        )
    }

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

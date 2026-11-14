package com.ham.tools.data.exam

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

/** 云同步的失败原因；界面必须照实显示，不能把「没部署」说成「网络不通」。 */
enum class ExamSyncError {
    NOT_DEPLOYED,
    NETWORK,
    AUTH,
    SERVER,
    BAD_RESPONSE
}

sealed interface ExamCloudResult<out T> {
    data class Success<T>(val value: T) : ExamCloudResult<T>
    data class Failure(val code: ExamSyncError, val message: String = "") : ExamCloudResult<Nothing>
}

/**
 * 考试练习的账号云同步 HTTP 层（可选增强）。
 *
 * 只在已登录时被调用；服务端是旧版（404）时明确返回 NOT_DEPLOYED，
 * App 继续用本机记录并在界面标注「仅保存在本机」。
 */
class ExamCloudApi(
    private val token: String,
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = AppConfig.SERVER_BASE_URL
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun ping(): ExamCloudResult<Boolean> = execute(
        Request.Builder().url(url("/api/exam/ping")).get().build()
    ) { true }

    /** 云端没有记录时返回一份空数据，合并逻辑因此不需要处理 null。 */
    suspend fun fetch(): ExamCloudResult<ExamProgressData> = execute(
        authed("/api/exam/progress").get().build()
    ) { body ->
        val data = JSONObject(body).optJSONObject("data")
            ?: return@execute ExamProgressData()
        ExamProgressCodec.decode(data)
    }

    suspend fun push(data: ExamProgressData): ExamCloudResult<Unit> {
        val payload = JSONObject().put("data", ExamProgressCodec.encode(data))
        return execute(
            authed("/api/exam/progress")
                .put(payload.toString().toRequestBody(jsonType))
                .build()
        ) { Unit }
    }

    suspend fun clear(): ExamCloudResult<Unit> = execute(
        authed("/api/exam/progress").delete().build()
    ) { Unit }

    private suspend fun <T> execute(
        request: Request,
        parse: (String) -> T
    ): ExamCloudResult<T> = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(request).execute()
        } catch (_: IOException) {
            return@withContext ExamCloudResult.Failure(ExamSyncError.NETWORK, "连不上服务器或请求超时")
        }
        val body = response.use { it.body?.string().orEmpty() }
        // 旧版服务端 / Nginx 可能返回 HTML 或纯文本 404，一律按「未部署」处理
        if (response.code == 404) {
            return@withContext ExamCloudResult.Failure(ExamSyncError.NOT_DEPLOYED, "服务端尚未部署学习记录接口")
        }
        if (response.code == 401) {
            return@withContext ExamCloudResult.Failure(ExamSyncError.AUTH, "登录状态已失效，请重新登录")
        }
        if (!response.isSuccessful) {
            val message = runCatching {
                JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
            }.getOrDefault("")
            return@withContext ExamCloudResult.Failure(
                ExamSyncError.SERVER,
                message.ifBlank { "服务器返回 ${response.code}" }
            )
        }
        val value = try {
            parse(body)
        } catch (error: Exception) {
            return@withContext ExamCloudResult.Failure(
                ExamSyncError.BAD_RESPONSE,
                error.message.orEmpty()
            )
        }
        ExamCloudResult.Success(value)
    }

    private fun authed(path: String): Request.Builder = Request.Builder()
        .url(url(path))
        .header("Authorization", "Bearer $token")

    private fun url(path: String): String =
        baseUrl.trim().trimEnd('/').ifBlank { AppConfig.SERVER_BASE_URL } + path

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

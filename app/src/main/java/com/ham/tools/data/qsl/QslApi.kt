package com.ham.tools.data.qsl

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
 * QSL 交换服务端的 HTTP 访问层：只负责发请求、解 JSON、翻译错误码。
 *
 * 所有业务接口都带 Bearer 令牌；[ping] 是唯一的例外，用来判断
 * 线上服务端有没有部署 /api/qsl（旧版会返回 404）。
 */
class QslApi(
    private val token: String,
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = AppConfig.SERVER_BASE_URL
) {

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** 能力探测：true=已部署；404 NOT_FOUND=未部署（进演示模式）；其余=网络/服务器故障。 */
    suspend fun ping(): QslResult<Boolean> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url("/api/qsl/ping")).get().build()
        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            return@withContext QslResult.Failure(QslErrorCode.NETWORK, error.message.orEmpty())
        }
        val body = response.use { it.body?.string().orEmpty() }
        // 404 一律视为「线上还是旧版服务端，没有 /api/qsl」。
        // 不能只认 JSON 里的 NOT_FOUND：老版本 Express / Nginx 可能直接返回
        // 一段 HTML 或纯文本 404，那样 parseError 会得出 BAD_RESPONSE，
        // 导致 App 误以为「网络/服务器故障」而不肯进入演示模式。
        if (response.code == 404) {
            return@withContext QslResult.Failure(
                QslErrorCode.NOT_DEPLOYED,
                "服务端尚未部署 QSL 接口"
            )
        }
        if (!response.isSuccessful) {
            val (code, message) = QslJson.parseError(body)
            return@withContext QslResult.Failure(code, message)
        }
        QslResult.Success(true)
    }

    /** 我的资料 + 我上传过的卡片列表。 */
    suspend fun getMe(): QslResult<Pair<QslProfile, List<QslCard>>> =
        call(authed("/api/qsl/me").get().build()) { body ->
            val root = JSONObject(body)
            Pair(
                QslJson.parseProfile(root.optJSONObject("profile")),
                QslJson.parseCards(root.optJSONArray("cards"))
            )
        }

    suspend fun saveProfile(profile: QslProfile): QslResult<QslProfile> =
        call(
            authed("/api/qsl/me")
                .put(QslJson.profileBody(profile).toString().toRequestBody(jsonType))
                .build()
        ) { body -> QslJson.parseProfile(JSONObject(body).optJSONObject("profile")) }

    suspend fun deleteMe(): QslResult<Unit> =
        call(authed("/api/qsl/me").delete().build()) { Unit }

    /** 卡片图片以二进制请求体上传，模式走 X-Qsl-Mode 头。 */
    suspend fun uploadCard(mode: QslMode, bytes: ByteArray, mime: String): QslResult<QslCard> =
        call(
            authed("/api/qsl/cards")
                .header("X-Qsl-Mode", mode.key)
                .post(bytes.toRequestBody(mime.toMediaType()))
                .build()
        ) { body ->
            val card = JSONObject(body).optJSONObject("card")
                ?: throw IllegalArgumentException("missing card")
            QslJson.parseCard(card)
        }

    suspend fun deleteCard(id: Long): QslResult<Unit> =
        call(authed("/api/qsl/cards/$id").delete().build()) { Unit }

    /** 卡片图片必须带令牌下载，服务端会校验可见范围。 */
    suspend fun cardImage(id: Long): ByteArray? = withContext(Dispatchers.IO) {
        val request = authed("/api/qsl/cards/$id/image").get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.bytes()
            }
        } catch (_: IOException) {
            null
        }
    }

    suspend fun directory(query: String): QslResult<List<QslDirectoryUser>> =
        call(authed("/api/qsl/directory?q=${encode(query)}").get().build()) { body ->
            QslJson.parseDirectory(JSONObject(body).optJSONArray("users"))
        }

    suspend fun exchanges(): QslResult<List<QslExchange>> =
        call(authed("/api/qsl/exchanges").get().build()) { body ->
            QslJson.parseExchanges(JSONObject(body).optJSONArray("exchanges"))
        }

    suspend fun createExchange(draft: QslExchangeDraft): QslResult<QslExchange> =
        call(
            authed("/api/qsl/exchanges")
                .post(QslJson.draftBody(draft).toString().toRequestBody(jsonType))
                .build()
        ) { body ->
            QslJson.parseExchange(
                JSONObject(body).optJSONObject("exchange")
                    ?: throw IllegalArgumentException("missing exchange")
            )
        }

    suspend fun actOnExchange(id: Long, action: String): QslResult<QslExchange> =
        call(
            authed("/api/qsl/exchanges/$id")
                .patch(JSONObject().put("action", action).toString().toRequestBody(jsonType))
                .build()
        ) { body ->
            QslJson.parseExchange(
                JSONObject(body).optJSONObject("exchange")
                    ?: throw IllegalArgumentException("missing exchange")
            )
        }

    suspend fun contact(id: Long): QslResult<QslContact> =
        call(authed("/api/qsl/exchanges/$id/contact").get().build()) { body ->
            QslJson.parseContact(JSONObject(body).optJSONObject("contact"))
        }

    suspend fun summary(): QslResult<QslSummary> =
        call(authed("/api/qsl/summary").get().build()) { body ->
            QslJson.parseSummary(JSONObject(body))
        }

    // ---------------------------------------------------------------- 内部

    private fun authed(path: String): Request.Builder = Request.Builder()
        .url(url(path))
        .header("Authorization", "Bearer $token")

    private fun url(path: String): String =
        baseUrl.trim().trimEnd('/').ifBlank { AppConfig.SERVER_BASE_URL } + path

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private suspend fun <T> call(
        request: Request,
        parse: (String) -> T
    ): QslResult<T> = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            return@withContext QslResult.Failure(QslErrorCode.NETWORK, error.message.orEmpty())
        } catch (error: IllegalArgumentException) {
            return@withContext QslResult.Failure(QslErrorCode.BAD_RESPONSE, error.message.orEmpty())
        }
        val body = response.use { it.body?.string().orEmpty() }
        if (!response.isSuccessful) {
            val (code, message) = QslJson.parseError(body)
            return@withContext QslResult.Failure(code, message)
        }
        try {
            QslResult.Success(parse(body))
        } catch (error: Exception) {
            QslResult.Failure(QslErrorCode.BAD_RESPONSE, error.message.orEmpty())
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

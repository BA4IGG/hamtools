package com.ham.tools.data.update

import com.ham.tools.data.AppConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

private sealed interface HttpResult {
    data class Ok(val body: String) : HttpResult
    data class HttpError(val code: Int) : HttpResult
    data object NetworkError : HttpResult
}

/**
 * 通过 GitHub 官方接口检测更新，纯只读 GET，不带任何账号 / 设备信息。
 *
 * 两个源都在 GitHub 官方域名下：
 * 1. REST `releases` 列表：字段最全（含 `prerelease`、更新日志、附件 APK 直链），
 *    客户端按通道筛选，正式通道只取非预发布；
 * 2. `releases.atom`：无未认证限流，REST 被限流 / 不可达时接管，
 *    但没有附件信息、也没有 `prerelease` 字段，界面必须如实标注来源与通道不确定性。
 *
 * 失败按原因分类返回，界面据此给出可诊断的提示，不把「网络不通」和「上游错误」混成一句。
 */
class UpdateClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun fetch(channel: UpdateChannel): UpdateFetchResult {
        val api = request(AppConfig.GITHUB_RELEASE_API_URL, json = true)
        if (api is HttpResult.Ok) {
            val releases = UpdateChecker.parseReleaseList(api.body, SOURCE_API)
            if (releases.isNotEmpty()) {
                val picked = UpdateChecker.pickForChannel(releases, channel)
                return if (picked != null) {
                    UpdateFetchResult.Success(picked)
                } else {
                    // 列表非空却全是预发布：正式通道确实「暂无正式 Release」，
                    // 不能退到 Atom 把某个 beta 当成正式版报给用户。
                    UpdateFetchResult.Failure(UpdateFailure.NO_RELEASE)
                }
            }
        }
        val atom = request(AppConfig.GITHUB_RELEASE_ATOM_URL, json = false)
        if (atom is HttpResult.Ok) {
            UpdateChecker.parseAtomFeed(atom.body, SOURCE_ATOM, channel)?.let {
                return UpdateFetchResult.Success(it)
            }
        }
        return UpdateFetchResult.Failure(
            reason = classify(api, atom),
            httpCode = httpCode(api, atom)
        )
    }

    private fun request(url: String, json: Boolean): HttpResult {
        return try {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", "HAM-Tools (+https://github.com/BA4IGG/hamtools)")
                .header(
                    "Accept",
                    if (json) "application/vnd.github+json"
                    else "application/atom+xml, application/xml;q=0.9, */*;q=0.8"
                )
            if (json) builder.header("X-GitHub-Api-Version", "2022-11-28")
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) return HttpResult.HttpError(response.code)
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) HttpResult.HttpError(response.code) else HttpResult.Ok(body)
            }
        } catch (e: IOException) {
            HttpResult.NetworkError
        } catch (e: Throwable) {
            // OkHttp 在部分 ROM / 代理环境下会抛非 IOException，不能让它冒到界面
            HttpResult.NetworkError
        }
    }

    /** 两个源都失败时才归因；只要有一个返回 200 但解析不出来，就是「数据格式异常」。 */
    private fun classify(api: HttpResult, atom: HttpResult): UpdateFailure {
        val codes = listOf(api, atom).filterIsInstance<HttpResult.HttpError>().map { it.code }
        return when {
            codes.contains(404) -> UpdateFailure.NO_RELEASE
            codes.any { it == 403 || it == 429 } -> UpdateFailure.RATE_LIMITED
            codes.any { it >= 500 } -> UpdateFailure.UPSTREAM
            api is HttpResult.Ok || atom is HttpResult.Ok -> UpdateFailure.BAD_DATA
            api is HttpResult.NetworkError && atom is HttpResult.NetworkError -> UpdateFailure.OFFLINE
            else -> UpdateFailure.UPSTREAM
        }
    }

    private fun httpCode(api: HttpResult, atom: HttpResult): Int? =
        (api as? HttpResult.HttpError)?.code ?: (atom as? HttpResult.HttpError)?.code

    companion object {
        const val SOURCE_API = "GitHub Releases API"
        const val SOURCE_ATOM = "GitHub Releases Atom（备用源 · 通道未标注）"
    }
}

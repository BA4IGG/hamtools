package com.ham.tools.ui.qsl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ham.tools.data.qsl.QslCard
import com.ham.tools.data.qsl.QslContact
import com.ham.tools.data.qsl.QslDirectoryUser
import com.ham.tools.data.qsl.QslErrorCode
import com.ham.tools.data.qsl.QslExchange
import com.ham.tools.data.qsl.QslExchangeDraft
import com.ham.tools.data.qsl.QslProfile
import com.ham.tools.data.qsl.QslRepository
import com.ham.tools.data.qsl.QslResult
import com.ham.tools.data.qsl.QslSummary
import com.ham.tools.data.remote.UserSession
import kotlinx.coroutines.launch

data class QslUiState(
    val loading: Boolean = true,
    val demo: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val profile: QslProfile = QslProfile(),
    val cards: List<QslCard> = emptyList(),
    val exchanges: List<QslExchange> = emptyList(),
    val directory: List<QslDirectoryUser> = emptyList(),
    val summary: QslSummary = QslSummary()
)

/** 把服务端错误码翻成能直接展示的中文，优先用服务端给的具体说明。 */
fun QslErrorCode.humanMessage(detail: String): String {
    if (detail.isNotBlank()) return detail
    return when (this) {
        QslErrorCode.NO_TOKEN, QslErrorCode.TOKEN_EXPIRED -> "登录状态已失效，请重新登录"
        QslErrorCode.NO_USER -> "账号不存在，请重新登录"
        QslErrorCode.NETWORK -> "连不上服务器，请检查网络后重试"
        QslErrorCode.BAD_RESPONSE -> "服务器返回了无法识别的数据"
        QslErrorCode.NOT_DEPLOYED -> "服务端尚未部署 QSL 接口，当前为本地演示"
        QslErrorCode.INVALID_FIELD -> "填写内容格式不正确"
        QslErrorCode.INVALID_ID -> "交换编号不正确，请刷新后重试"
        QslErrorCode.INVALID_ACTION -> "不支持该操作，请升级 App 后重试"
        QslErrorCode.INVALID_EMAIL -> "邮箱格式不正确"
        QslErrorCode.INVALID_GRID -> "网格格式不正确，应形如 OM89"
        QslErrorCode.INVALID_CALLSIGN -> "对方呼号格式不正确"
        QslErrorCode.INVALID_MODE -> "交换方式不正确"
        QslErrorCode.INVALID_IMAGE -> "仅支持 JPG / PNG / WebP 图片"
        QslErrorCode.IMAGE_TOO_LARGE -> "图片不能超过 5 MB"
        QslErrorCode.SELF_EXCHANGE -> "不能和自己交换卡片"
        QslErrorCode.DUPLICATE_EXCHANGE -> "这次通联已有一条交换记录"
        QslErrorCode.PEER_NOT_REGISTERED -> "对方尚未注册，可选择「仅记录到历史」"
        QslErrorCode.PEER_NOT_AVAILABLE -> "对方未开放该交换方式，可选择「仅记录到历史」"
        QslErrorCode.UNREGISTERED_PEER -> "对方未注册，没有可查询的邮寄信息"
        QslErrorCode.FORBIDDEN -> "无权执行该操作"
        QslErrorCode.NOT_ACCEPTED -> "对方接受交换后才能查看邮寄信息"
        QslErrorCode.INVALID_TRANSITION -> "当前状态不支持该操作，请刷新后重试"
        QslErrorCode.NOT_FOUND, QslErrorCode.CARD_NOT_FOUND -> "图片不存在，请刷新后重试"
        QslErrorCode.EXCHANGE_NOT_FOUND -> "交换记录不存在，请刷新后重试"
        QslErrorCode.SERVER_ERROR -> "服务器开小差了，请稍后重试"
    }
}

/**
 * QSL 板块的界面状态。用 ViewModel 而不是页面内 remember，
 * 是因为它在 HorizontalPager 里，切到别的顶层页时页面可能被回收，
 * 资料和交换列表不应该每次滑回来都重新加载一遍。
 */
class QslViewModel : ViewModel() {

    var state by mutableStateOf(QslUiState())
        private set

    private var repository: QslRepository? = null
    private var boundToken: String = ""

    fun bind(session: UserSession) {
        if (repository != null && boundToken == session.token) return
        boundToken = session.token
        repository = QslRepository(session.token, session.profile.callsign)
        refresh()
    }

    fun refresh() {
        val repo = repository ?: return
        viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            val me = repo.getMe()
            if (me is QslResult.Failure) {
                state = state.copy(loading = false, error = me.code.humanMessage(me.message))
                return@launch
            }
            // sealed interface 在这里不会自动 smart cast 到 Success，显式取一次
            val mine = me as? QslResult.Success<Pair<QslProfile, List<QslCard>>>
                ?: return@launch
            val exchanges = repo.exchanges()
            val summary = repo.summary()
            val directory = repo.directory("")
            state = state.copy(
                loading = false,
                demo = mine.demo,
                profile = mine.value.first,
                cards = mine.value.second,
                exchanges = (exchanges as? QslResult.Success)?.value.orEmpty(),
                summary = (summary as? QslResult.Success)?.value ?: QslSummary(),
                directory = (directory as? QslResult.Success)?.value.orEmpty(),
                error = listOf(exchanges, summary)
                    .filterIsInstance<QslResult.Failure>()
                    .firstOrNull()
                    ?.let { it.code.humanMessage(it.message) }
            )
        }
    }

    private fun <T> onResult(
        result: QslResult<T>,
        message: String,
        onSuccess: (T) -> Unit = {}
    ) {
        when (result) {
            is QslResult.Success -> {
                onSuccess(result.value)
                state = state.copy(error = null, message = if (result.demo) "$message（演示）" else message)
                refresh()
            }
            is QslResult.Failure -> state = state.copy(error = result.code.humanMessage(result.message))
        }
    }

    fun saveProfile(profile: QslProfile) {
        val repo = repository ?: return
        viewModelScope.launch {
            state = state.copy(error = null)
            onResult(repo.saveProfile(profile), "资料已保存") {
                state = state.copy(profile = it)
            }
        }
    }

    fun deleteProfile() {
        val repo = repository ?: return
        viewModelScope.launch {
            onResult(repo.deleteMe(), "QSL 资料与交换记录已删除")
        }
    }

    fun uploadCard(mode: com.ham.tools.data.qsl.QslMode, bytes: ByteArray, mime: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            onResult(repo.uploadCard(mode, bytes, mime), "卡片图片已上传")
        }
    }

    fun deleteCard(id: Long) {
        val repo = repository ?: return
        viewModelScope.launch {
            onResult(repo.deleteCard(id), "卡片已删除")
        }
    }

    fun createExchange(draft: QslExchangeDraft, onDone: (Boolean) -> Unit) {
        val repo = repository ?: return
        viewModelScope.launch {
            when (val result = repo.createExchange(draft)) {
                is QslResult.Success -> {
                    onDone(true)
                    state = state.copy(
                        error = null,
                        message = if (result.demo) "已记录（演示）" else "交换申请已发送"
                    )
                    refresh()
                }
                is QslResult.Failure -> {
                    onDone(false)
                    state = state.copy(error = result.code.humanMessage(result.message))
                }
            }
        }
    }

    fun actOnExchange(id: Long, action: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            onResult(repo.actOnExchange(id, action), "操作已完成")
        }
    }

    suspend fun contact(id: Long): QslResult<QslContact> =
        repository?.contact(id) ?: QslResult.Failure(QslErrorCode.NO_TOKEN)

    suspend fun cardImage(id: Long): ByteArray? = repository?.cardImage(id)

    fun searchDirectory(query: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            when (val result = repo.directory(query)) {
                is QslResult.Success -> state = state.copy(directory = result.value)
                is QslResult.Failure -> state = state.copy(error = result.code.humanMessage(result.message))
            }
        }
    }

    fun clearMessage() {
        state = state.copy(message = null)
    }

    fun clearError() {
        state = state.copy(error = null)
    }
}

package com.ham.tools.data.qsl

import org.json.JSONArray
import org.json.JSONObject

/** QSL 交换的两大板块。 */
enum class QslMode(val key: String, val label: String) {
    PAPER("paper", "纸质卡片"),
    EQSL("eqsl", "eQSL 电子卡片");

    companion object {
        fun from(key: String?): QslMode = entries.firstOrNull { it.key == key } ?: PAPER
    }
}

/** 交换状态机，与服务端 qsl_exchanges.status 一一对应。 */
enum class QslStatus(val key: String, val label: String) {
    PENDING("pending", "待对方确认"),
    ACCEPTED("accepted", "已接受 · 待寄出"),
    SENT("sent", "已寄出 · 待对方确认"),
    COMPLETED("completed", "交换完成"),
    DECLINED("declined", "已拒绝"),
    CANCELLED("cancelled", "已取消"),
    RECORDED("recorded", "仅记录 · 对方未注册");

    val isOpen: Boolean get() = this == PENDING || this == ACCEPTED || this == SENT
    val isDone: Boolean get() = this == COMPLETED || this == RECORDED

    companion object {
        fun from(key: String?): QslStatus = entries.firstOrNull { it.key == key } ?: PENDING
    }
}

enum class QslDirection(val key: String) {
    OUTGOING("outgoing"),
    INCOMING("incoming");

    companion object {
        fun from(key: String?): QslDirection =
            entries.firstOrNull { it.key == key } ?: OUTGOING
    }
}

/** 我的 QSL 资料。address 属敏感字段，只在本机编辑框与本人接口中出现。 */
data class QslProfile(
    val callsign: String = "",
    val displayName: String = "",
    val grid: String = "",
    val qth: String = "",
    val email: String = "",
    val address: String = "",
    val note: String = "",
    val paperEnabled: Boolean = true,
    val eqslEnabled: Boolean = true,
    val discoverable: Boolean = true,
    val cardId: Long? = null,
    val hasAddress: Boolean = false,
    val configured: Boolean = false,
    val updatedAt: String = ""
)

/** 我上传的 QSL 卡片图片（图片本体需带令牌单独下载）。 */
data class QslCard(
    val id: Long,
    val mode: QslMode,
    val mime: String,
    val sizeBytes: Long,
    val createdAt: String = ""
)

/** 一条交换记录，同时也是交换历史的数据源。 */
data class QslExchange(
    val id: Long,
    val mode: QslMode,
    val peerCallsign: String,
    val direction: QslDirection,
    val qsoDate: String = "",
    val qsoBand: String = "",
    val qsoMode: String = "",
    val rstSent: String = "",
    val rstRcvd: String = "",
    val note: String = "",
    val status: QslStatus = QslStatus.PENDING,
    val registeredPeer: Boolean = false,
    val fromCardId: Long? = null,
    val toCardId: Long? = null,
    val createdAt: String = "",
    val updatedAt: String = ""
)

/** 顶部统计与角标；latestIncomingId 用于通知去重。 */
data class QslSummary(
    val incoming: Int = 0,
    val outgoing: Int = 0,
    val completed: Int = 0,
    val paper: Int = 0,
    val eqsl: Int = 0,
    val latestIncomingId: Long = 0,
    val serverTime: String = ""
)

/** 可交换名录中的用户：服务端已脱敏，不含地址与邮箱。 */
data class QslDirectoryUser(
    val callsign: String,
    val displayName: String = "",
    val grid: String = "",
    val qth: String = "",
    val note: String = "",
    val paperEnabled: Boolean = true,
    val eqslEnabled: Boolean = true,
    val hasCard: Boolean = false
)

/** 交换被接受后，双方才能取到的邮寄信息。 */
data class QslContact(
    val callsign: String,
    val displayName: String = "",
    val address: String = "",
    val email: String = "",
    val qth: String = "",
    val grid: String = "",
    val hasCard: Boolean = false
)

/** 统一结果类型；demo = true 时界面必须醒目标注「本地演示，非真实交换」。 */
sealed interface QslResult<out T> {
    data class Success<T>(val value: T, val demo: Boolean = false) : QslResult<T>
    data class Failure(val code: QslErrorCode, val message: String = "") : QslResult<Nothing>
}

enum class QslErrorCode {
    NO_TOKEN,
    NO_USER,
    TOKEN_EXPIRED,
    NETWORK,
    BAD_RESPONSE,
    NOT_DEPLOYED,
    INVALID_FIELD,
    INVALID_ID,
    INVALID_ACTION,
    INVALID_EMAIL,
    INVALID_GRID,
    INVALID_CALLSIGN,
    INVALID_MODE,
    INVALID_IMAGE,
    IMAGE_TOO_LARGE,
    SELF_EXCHANGE,
    DUPLICATE_EXCHANGE,
    PEER_NOT_REGISTERED,
    PEER_NOT_AVAILABLE,
    FORBIDDEN,
    NOT_ACCEPTED,
    INVALID_TRANSITION,
    NOT_FOUND,
    EXCHANGE_NOT_FOUND,
    CARD_NOT_FOUND,
    UNREGISTERED_PEER,
    SERVER_ERROR;

    companion object {
        fun from(code: String?): QslErrorCode =
            entries.firstOrNull { it.name == code } ?: SERVER_ERROR
    }
}

/** 发起交换时填写的 QSO 信息。 */
data class QslExchangeDraft(
    val mode: QslMode,
    val peerCallsign: String,
    val qsoDate: String = "",
    val qsoBand: String = "",
    val qsoMode: String = "",
    val rstSent: String = "",
    val rstRcvd: String = "",
    val note: String = "",
    val recordOnly: Boolean = false
)

enum class QslActor { SENDER, RECEIVER, BOTH }

/**
 * 交换状态机的客户端镜像，必须与服务端 nextStatus / ACTION_ACTOR 保持完全一致。
 *
 * 放在纯 Kotlin 层是为了：
 * 1. 演示模式与真实模式共用同一套流转规则，不会出现「演示能点、线上被拒」；
 * 2. 可以直接在本地单测里穷举所有状态 × 动作 × 模式组合。
 */
object QslStateMachine {

    fun next(action: String, mode: QslMode, status: QslStatus): QslStatus? = when (action) {
        "accept" -> if (status == QslStatus.PENDING) {
            if (mode == QslMode.EQSL) QslStatus.COMPLETED else QslStatus.ACCEPTED
        } else null

        "decline" -> if (status == QslStatus.PENDING) QslStatus.DECLINED else null
        "sent" -> if (mode == QslMode.PAPER && status == QslStatus.ACCEPTED) QslStatus.SENT else null
        "complete" -> if (status == QslStatus.ACCEPTED || status == QslStatus.SENT) {
            QslStatus.COMPLETED
        } else null

        "cancel" -> if (status in listOf(
                QslStatus.PENDING, QslStatus.ACCEPTED, QslStatus.SENT
            )
        ) QslStatus.CANCELLED else null

        else -> null
    }

    fun actor(action: String): QslActor? = when (action) {
        "accept", "decline" -> QslActor.RECEIVER
        "sent", "complete" -> QslActor.BOTH
        "cancel" -> QslActor.SENDER
        else -> null
    }

    /** 当前用户在某条记录上可执行的动作，返回 (action, 按钮文案)。 */
    fun availableActions(
        mode: QslMode,
        status: QslStatus,
        direction: QslDirection
    ): List<Pair<String, String>> {
        val candidates = when (status) {
            QslStatus.PENDING -> listOf("accept" to "接受申请", "decline" to "婉拒", "cancel" to "撤回申请")
            QslStatus.ACCEPTED -> listOf("sent" to "标记已寄出", "complete" to "确认完成", "cancel" to "取消")
            QslStatus.SENT -> listOf("complete" to "确认收到 / 完成", "cancel" to "取消")
            else -> emptyList()
        }
        return candidates.filter { (action, _) ->
            next(action, mode, status) != null && when (actor(action)) {
                QslActor.RECEIVER -> direction == QslDirection.INCOMING
                QslActor.SENDER -> direction == QslDirection.OUTGOING
                QslActor.BOTH -> true
                null -> false
            }
        }
    }
}

/**
 * QSL 接口的 JSON 解析与序列化。
 *
 * 全部是纯 JVM 函数（只依赖 org.json），不碰 Android API，
 * 因此可以在本地单元测试里直接用真实响应样本做回归。
 */
object QslJson {

    fun parseProfile(json: JSONObject?): QslProfile {
        if (json == null) return QslProfile()
        return QslProfile(
            callsign = json.optString("callsign"),
            displayName = json.optString("displayName"),
            grid = json.optString("grid"),
            qth = json.optString("qth"),
            email = json.optString("email"),
            address = json.optString("address"),
            note = json.optString("note"),
            paperEnabled = json.optBoolean("paperEnabled", true),
            eqslEnabled = json.optBoolean("eqslEnabled", true),
            discoverable = json.optBoolean("discoverable", true),
            cardId = json.optLong("cardId", 0L).takeIf { it > 0L },
            hasAddress = json.optBoolean("hasAddress", false),
            configured = json.optBoolean("configured", false),
            updatedAt = json.optString("updatedAt")
        )
    }

    /**
     * 保存资料前的即时校验（纯 JVM）。与服务端 PUT /me 的规则保持一致，
     * 但只是提前给出友好提示，不能替代服务端校验。
     */
    fun validateProfile(profile: QslProfile): String? {
        if (profile.email.isNotBlank() && !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(profile.email)) {
            return "邮箱格式不正确"
        }
        if (profile.grid.isNotBlank() &&
            !Regex("^[A-Ra-r]{2}\\d{2}([A-Xa-x]{2})?(\\d{2})?$").matches(profile.grid)
        ) {
            return "网格格式不正确，应形如 OM89"
        }
        return null
    }

    fun profileBody(profile: QslProfile): JSONObject = JSONObject().apply {
        put("displayName", profile.displayName)
        put("grid", profile.grid)
        put("qth", profile.qth)
        put("email", profile.email)
        put("address", profile.address)
        put("note", profile.note)
        put("paperEnabled", profile.paperEnabled)
        put("eqslEnabled", profile.eqslEnabled)
        put("discoverable", profile.discoverable)
    }

    fun parseCard(json: JSONObject): QslCard = QslCard(
        id = json.optLong("id", 0L),
        mode = QslMode.from(json.optString("mode")),
        mime = json.optString("mime"),
        sizeBytes = json.optLong("sizeBytes", 0L),
        createdAt = json.optString("createdAt")
    )

    fun parseCards(array: JSONArray?): List<QslCard> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let(::parseCard)
        }
    }

    fun parseExchange(json: JSONObject): QslExchange = QslExchange(
        id = json.optLong("id", 0L),
        mode = QslMode.from(json.optString("mode")),
        peerCallsign = json.optString("peerCallsign"),
        direction = QslDirection.from(json.optString("direction")),
        qsoDate = json.optString("qsoDate"),
        qsoBand = json.optString("qsoBand"),
        qsoMode = json.optString("qsoMode"),
        rstSent = json.optString("rstSent"),
        rstRcvd = json.optString("rstRcvd"),
        note = json.optString("note"),
        status = QslStatus.from(json.optString("status")),
        registeredPeer = json.optBoolean("registeredPeer", false),
        fromCardId = json.optLong("fromCardId", 0L).takeIf { it > 0L },
        toCardId = json.optLong("toCardId", 0L).takeIf { it > 0L },
        createdAt = json.optString("createdAt"),
        updatedAt = json.optString("updatedAt")
    )

    fun parseExchanges(array: JSONArray?): List<QslExchange> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let(::parseExchange)
        }
    }

    fun draftBody(draft: QslExchangeDraft): JSONObject = JSONObject().apply {
        put("mode", draft.mode.key)
        put("peerCallsign", draft.peerCallsign.trim().uppercase())
        put("qsoDate", draft.qsoDate)
        put("qsoBand", draft.qsoBand)
        put("qsoMode", draft.qsoMode)
        put("rstSent", draft.rstSent)
        put("rstRcvd", draft.rstRcvd)
        put("note", draft.note)
        put("recordOnly", draft.recordOnly)
    }

    fun parseSummary(json: JSONObject): QslSummary = QslSummary(
        incoming = json.optInt("incoming", 0),
        outgoing = json.optInt("outgoing", 0),
        completed = json.optInt("completed", 0),
        paper = json.optInt("paper", 0),
        eqsl = json.optInt("eqsl", 0),
        latestIncomingId = json.optLong("latestIncomingId", 0L),
        serverTime = json.optString("serverTime")
    )

    fun parseDirectoryUser(json: JSONObject): QslDirectoryUser = QslDirectoryUser(
        callsign = json.optString("callsign"),
        displayName = json.optString("displayName"),
        grid = json.optString("grid"),
        qth = json.optString("qth"),
        note = json.optString("note"),
        paperEnabled = json.optBoolean("paperEnabled", true),
        eqslEnabled = json.optBoolean("eqslEnabled", true),
        hasCard = json.optBoolean("hasCard", false)
    )

    fun parseDirectory(array: JSONArray?): List<QslDirectoryUser> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let(::parseDirectoryUser)
        }
    }

    fun parseContact(json: JSONObject?): QslContact {
        if (json == null) return QslContact(callsign = "")
        return QslContact(
            callsign = json.optString("callsign"),
            displayName = json.optString("displayName"),
            address = json.optString("address"),
            email = json.optString("email"),
            qth = json.optString("qth"),
            grid = json.optString("grid"),
            hasCard = json.optBoolean("hasCard", false)
        )
    }

    fun parseError(body: String): Pair<QslErrorCode, String> = try {
        val error = JSONObject(body).optJSONObject("error")
        Pair(
            QslErrorCode.from(error?.optString("code")),
            error?.optString("message").orEmpty()
        )
    } catch (_: Exception) {
        Pair(QslErrorCode.BAD_RESPONSE, "")
    }
}

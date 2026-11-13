package com.ham.tools.data.qsl

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 服务端未部署 QSL 接口时的本地演示数据。
 *
 * 铁律：这里的每一条数据都必须能被界面标注成「演示」，绝不允许冒充真实交换。
 * 所有呼号都用 EXAMPLE* 前缀；数据在进程内保存，退出 App 即清空，不写磁盘、不上传。
 */
object QslDemoStore {

    @Volatile
    private var profile: QslProfile = QslProfile()

    @Volatile
    private var cards: List<QslCard> = emptyList()

    @Volatile
    private var exchanges: List<QslExchange> = emptyList()

    @Volatile
    private var images: Map<Long, ByteArray> = emptyMap()

    @Volatile
    private var nextId: Long = 1L

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun now(): String = timeFormat.format(Date(System.currentTimeMillis()))

    @Synchronized
    fun ensureSeed(callsign: String) {
        if (exchanges.isNotEmpty() || profile.configured) return
        profile = QslProfile(callsign = callsign, displayName = "", configured = false)
        exchanges = listOf(
            demoExchange(QslMode.PAPER, "EXAMPLE1", QslDirection.INCOMING, QslStatus.PENDING, "2026-11-09", "20m", "FT8"),
            demoExchange(QslMode.EQSL, "EXAMPLE2", QslDirection.INCOMING, QslStatus.PENDING, "2026-11-10", "40m", "CW"),
            demoExchange(QslMode.PAPER, "EXAMPLE3", QslDirection.OUTGOING, QslStatus.ACCEPTED, "2026-11-05", "15m", "SSB"),
            demoExchange(QslMode.PAPER, "EXAMPLE4", QslDirection.OUTGOING, QslStatus.COMPLETED, "2026-10-22", "20m", "FT8"),
            demoExchange(QslMode.EQSL, "EXAMPLE5", QslDirection.INCOMING, QslStatus.COMPLETED, "2026-10-18", "10m", "FT4"),
            demoExchange(QslMode.PAPER, "EXAMPLE6", QslDirection.OUTGOING, QslStatus.RECORDED, "2026-09-30", "80m", "CW")
        )
    }

    private fun demoExchange(
        mode: QslMode,
        peer: String,
        direction: QslDirection,
        status: QslStatus,
        date: String,
        band: String,
        radioMode: String
    ): QslExchange = QslExchange(
        id = nextId++,
        mode = mode,
        peerCallsign = peer,
        direction = direction,
        qsoDate = date,
        qsoBand = band,
        qsoMode = radioMode,
        rstSent = "59",
        rstRcvd = "57",
        note = "演示记录 · 非真实通联",
        status = status,
        registeredPeer = status != QslStatus.RECORDED,
        fromCardId = if (status == QslStatus.COMPLETED && mode == QslMode.EQSL) 901L else null,
        toCardId = if (status == QslStatus.COMPLETED && mode == QslMode.EQSL) 902L else null,
        createdAt = now(),
        updatedAt = now()
    )

    @Synchronized
    fun getMe(): Pair<QslProfile, List<QslCard>> = Pair(profile, cards)

    @Synchronized
    fun saveProfile(value: QslProfile): QslProfile {
        profile = value.copy(configured = true, hasAddress = value.address.isNotBlank(), updatedAt = now())
        return profile
    }

    @Synchronized
    fun deleteMe() {
        profile = QslProfile(callsign = profile.callsign)
        cards = emptyList()
        images = emptyMap()
        exchanges = emptyList()
    }

    @Synchronized
    fun uploadCard(mode: QslMode, bytes: ByteArray, mime: String): QslCard {
        val card = QslCard(
            id = nextId++,
            mode = mode,
            mime = mime,
            sizeBytes = bytes.size.toLong(),
            createdAt = now()
        )
        cards = listOf(card) + cards
        images = images + (card.id to bytes)
        profile = profile.copy(cardId = card.id, configured = true)
        return card
    }

    @Synchronized
    fun deleteCard(id: Long) {
        cards = cards.filterNot { it.id == id }
        images = images - id
        if (profile.cardId == id) profile = profile.copy(cardId = null)
    }

    @Synchronized
    fun cardImage(id: Long): ByteArray? = images[id]

    @Synchronized
    fun directory(query: String): List<QslDirectoryUser> = listOf(
        QslDirectoryUser("EXAMPLE1", "演示台站 · 华北", "OM89", "演示 QTH", "仅用于体验界面", true, true, false),
        QslDirectoryUser("EXAMPLE2", "演示台站 · 华东", "PM01", "演示 QTH", "仅用于体验界面", false, true, false),
        QslDirectoryUser("EXAMPLE3", "演示台站 · 华南", "OL72", "演示 QTH", "仅用于体验界面", true, false, false)
    ).filter { query.isBlank() || it.callsign.contains(query.trim().uppercase(), ignoreCase = true) }

    @Synchronized
    fun exchanges(): List<QslExchange> = exchanges.sortedByDescending { it.id }

    @Synchronized
    fun createExchange(draft: QslExchangeDraft): QslResult<QslExchange> {
        val peer = draft.peerCallsign.trim().uppercase()
        if (peer.isBlank()) {
            return QslResult.Failure(QslErrorCode.INVALID_CALLSIGN, "请填写对方呼号")
        }
        val duplicate = exchanges.any {
            it.peerCallsign == peer && it.mode == draft.mode &&
                it.qsoDate == draft.qsoDate &&
                (it.status.isOpen || it.status.isDone)
        }
        if (duplicate) {
            return QslResult.Failure(QslErrorCode.DUPLICATE_EXCHANGE, "这次通联已有一条交换记录")
        }
        val status = if (draft.recordOnly) QslStatus.RECORDED else QslStatus.PENDING
        val created = QslExchange(
            id = nextId++,
            mode = draft.mode,
            peerCallsign = peer,
            direction = QslDirection.OUTGOING,
            qsoDate = draft.qsoDate,
            qsoBand = draft.qsoBand,
            qsoMode = draft.qsoMode,
            rstSent = draft.rstSent,
            rstRcvd = draft.rstRcvd,
            note = if (draft.note.isBlank()) "演示记录 · 非真实通联" else draft.note,
            status = status,
            registeredPeer = !draft.recordOnly,
            createdAt = now(),
            updatedAt = now()
        )
        exchanges = listOf(created) + exchanges
        return QslResult.Success(created, demo = true)
    }

    @Synchronized
    fun act(id: Long, action: String): QslResult<QslExchange> {
        val current = exchanges.firstOrNull { it.id == id }
            ?: return QslResult.Failure(QslErrorCode.NOT_FOUND, "交换记录不存在")
        if (action == "accept" && current.direction != QslDirection.INCOMING) {
            return QslResult.Failure(QslErrorCode.FORBIDDEN, "只有接收方能处理申请")
        }
        if (action == "cancel" && current.direction != QslDirection.OUTGOING) {
            return QslResult.Failure(QslErrorCode.FORBIDDEN, "只有发起方能取消申请")
        }
        val next = QslStateMachine.next(action, current.mode, current.status)
            ?: return QslResult.Failure(QslErrorCode.INVALID_TRANSITION, "当前状态不支持该操作")
        val updated = current.copy(
            status = next,
            updatedAt = now(),
            fromCardId = if (next == QslStatus.COMPLETED) current.fromCardId ?: 901L else current.fromCardId,
            toCardId = if (next == QslStatus.COMPLETED) current.toCardId ?: 902L else current.toCardId
        )
        exchanges = exchanges.map { if (it.id == id) updated else it }
        return QslResult.Success(updated, demo = true)
    }

    @Synchronized
    fun contact(id: Long): QslResult<QslContact> {
        val current = exchanges.firstOrNull { it.id == id }
            ?: return QslResult.Failure(QslErrorCode.NOT_FOUND, "交换记录不存在")
        if (current.status !in listOf(QslStatus.ACCEPTED, QslStatus.SENT, QslStatus.COMPLETED)) {
            return QslResult.Failure(QslErrorCode.NOT_ACCEPTED, "对方接受交换后才能查看邮寄信息")
        }
        return QslResult.Success(
            QslContact(
                callsign = current.peerCallsign,
                displayName = "演示台站",
                address = "【演示数据】这里不会展示真实邮寄地址",
                email = "【演示数据】",
                qth = "演示 QTH",
                grid = "OM89"
            ),
            demo = true
        )
    }

    @Synchronized
    fun summary(): QslSummary {
        val list = exchanges
        val done = list.filter { it.status.isDone }
        return QslSummary(
            incoming = list.count { it.status == QslStatus.PENDING && it.direction == QslDirection.INCOMING },
            outgoing = list.count { it.status == QslStatus.PENDING && it.direction == QslDirection.OUTGOING },
            completed = done.size,
            paper = done.count { it.mode == QslMode.PAPER },
            eqsl = done.count { it.mode == QslMode.EQSL },
            latestIncomingId = list.filter {
                it.status == QslStatus.PENDING && it.direction == QslDirection.INCOMING
            }.maxOfOrNull { it.id } ?: 0L,
            serverTime = now()
        )
    }
}

/**
 * QSL 功能的统一数据入口。
 *
 * [probe] 通过 /api/qsl/ping 判断线上是否已部署：
 * - 已部署 → 全部走真实服务端；
 * - 明确 404（旧版服务端）→ 进入演示模式，所有结果都带 demo=true，界面必须标注；
 * - 网络故障 / 5xx → **不**切演示模式，如实报错，避免把「暂时连不上」误判成「没这功能」。
 */
class QslRepository(
    private val token: String,
    private val callsign: String,
    private val api: QslApi = QslApi(token)
) {

    @Volatile
    private var demo: Boolean? = null

    fun isDemo(): Boolean = demo == true

    suspend fun probe(): QslResult<Boolean> = ensureMode()

    private suspend fun ensureMode(): QslResult<Boolean> {
        demo?.let { return QslResult.Success(it) }
        return when (val result = api.ping()) {
            is QslResult.Success -> {
                demo = false
                QslResult.Success(false)
            }
            is QslResult.Failure -> if (result.code == QslErrorCode.NOT_DEPLOYED) {
                demo = true
                QslDemoStore.ensureSeed(callsign)
                QslResult.Success(true)
            } else {
                result
            }
        }
    }

    private suspend fun <T> dispatch(
        demoBlock: () -> QslResult<T>,
        liveCall: suspend () -> QslResult<T>
    ): QslResult<T> = when (val mode = ensureMode()) {
        is QslResult.Failure -> mode
        is QslResult.Success -> if (mode.value) demoBlock() else liveCall()
    }

    private fun <T> demo(value: T): QslResult<T> = QslResult.Success(value, demo = true)

    suspend fun getMe(): QslResult<Pair<QslProfile, List<QslCard>>> =
        dispatch({ demo(QslDemoStore.getMe()) }, { api.getMe() })

    suspend fun saveProfile(profile: QslProfile): QslResult<QslProfile> = dispatch(
        { demo(QslDemoStore.saveProfile(profile)) },
        { api.saveProfile(profile) }
    )

    suspend fun deleteMe(): QslResult<Unit> = dispatch(
        { QslDemoStore.deleteMe(); demo(Unit) },
        { api.deleteMe() }
    )

    suspend fun uploadCard(mode: QslMode, bytes: ByteArray, mime: String): QslResult<QslCard> =
        dispatch(
            { demo(QslDemoStore.uploadCard(mode, bytes, mime)) },
            { api.uploadCard(mode, bytes, mime) }
        )

    suspend fun deleteCard(id: Long): QslResult<Unit> = dispatch(
        { QslDemoStore.deleteCard(id); demo(Unit) },
        { api.deleteCard(id) }
    )

    suspend fun cardImage(id: Long): ByteArray? = when (val mode = ensureMode()) {
        is QslResult.Success -> if (mode.value) QslDemoStore.cardImage(id) else api.cardImage(id)
        is QslResult.Failure -> null
    }

    suspend fun directory(query: String): QslResult<List<QslDirectoryUser>> =
        dispatch({ demo(QslDemoStore.directory(query)) }, { api.directory(query) })

    suspend fun exchanges(): QslResult<List<QslExchange>> =
        dispatch({ demo(QslDemoStore.exchanges()) }, { api.exchanges() })

    suspend fun createExchange(draft: QslExchangeDraft): QslResult<QslExchange> =
        dispatch({ QslDemoStore.createExchange(draft) }, { api.createExchange(draft) })

    suspend fun actOnExchange(id: Long, action: String): QslResult<QslExchange> =
        dispatch({ QslDemoStore.act(id, action) }, { api.actOnExchange(id, action) })

    suspend fun contact(id: Long): QslResult<QslContact> =
        dispatch({ QslDemoStore.contact(id) }, { api.contact(id) })

    suspend fun summary(): QslResult<QslSummary> =
        dispatch({ demo(QslDemoStore.summary()) }, { api.summary() })
}

package com.ham.tools

import com.ham.tools.data.qsl.QslDemoStore
import com.ham.tools.data.qsl.QslDirection
import com.ham.tools.data.qsl.QslErrorCode
import com.ham.tools.data.qsl.QslExchangeDraft
import com.ham.tools.data.qsl.QslImageSniffer
import com.ham.tools.data.qsl.QslJson
import com.ham.tools.data.qsl.QslMode
import com.ham.tools.data.qsl.QslProfile
import com.ham.tools.data.qsl.QslResult
import com.ham.tools.data.qsl.QslStateMachine
import com.ham.tools.data.qsl.QslStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QSL 纯逻辑回归：JSON 解析、状态机、演示数据、图片格式白名单、错误码。
 *
 * 这些代码都不碰 Android API，所以能直接在本地 JVM 上跑；
 * 尤其是状态机，必须与服务端 qsl.js 的 nextStatus / ACTION_ACTOR 保持一致。
 */
class QslModelsTest {

    @Test
    fun parseProfile_readsAllFieldsAndDefaults() {
        val json = JSONObject(
            """
            {
              "callsign":"BA4IGG","displayName":"IGG","grid":"OM89","qth":"Jinan",
              "email":"a@b.cn","address":"保密地址","note":"73","paperEnabled":false,
              "eqslEnabled":true,"discoverable":false,"cardId":17,
              "hasAddress":true,"configured":true,"updatedAt":"2026-11-11T00:00:00Z"
            }
            """.trimIndent()
        )
        val p = QslJson.parseProfile(json)
        assertEquals("BA4IGG", p.callsign)
        assertEquals("保密地址", p.address)
        assertEquals(17L, p.cardId!!)
        assertFalse(p.paperEnabled)
        assertTrue(p.eqslEnabled)
        assertFalse(p.discoverable)
        assertTrue(p.hasAddress)
        assertTrue(p.configured)
        assertNull(QslJson.parseProfile(null).cardId)
        assertEquals("", QslJson.parseProfile(JSONObject()).callsign)
    }

    @Test
    fun profileBody_omitsCallsignAndAddressNeverLeaksIntoLists() {
        val body = QslJson.profileBody(
            QslProfile(callsign = "BA4IGG", address = "某街 1 号", grid = "OM89")
        )
        // 呼号由服务端按登录账号决定，客户端不能覆盖
        assertFalse(body.has("callsign"))
        assertEquals("某街 1 号", body.getString("address"))
        assertEquals("OM89", body.getString("grid"))
    }

    @Test
    fun validateProfile_matchesServerRules() {
        assertNull(QslJson.validateProfile(QslProfile(email = "a@b.cn", grid = "OM89ab")))
        assertNull(QslJson.validateProfile(QslProfile(email = "", grid = "")))
        assertNotNull(QslJson.validateProfile(QslProfile(email = "not-an-email")))
        assertNotNull(QslJson.validateProfile(QslProfile(grid = "ZZ99")))
        assertNotNull(QslJson.validateProfile(QslProfile(grid = "OM8")))
    }

    @Test
    fun parseExchange_andParseSummary_useServerShapes() {
        val ex = QslJson.parseExchange(
            JSONObject(
                """
                {
                  "id":42,"mode":"eqsl","peerCallsign":"JA1ABC","direction":"incoming",
                  "qsoDate":"2026-11-10","qsoBand":"40m","qsoMode":"CW",
                  "rstSent":"599","rstRcvd":"579","note":"TNX","status":"pending",
                  "registeredPeer":true,"fromCardId":0,"toCardId":9,
                  "createdAt":"2026-11-10T01:00:00Z","updatedAt":"2026-11-11T01:00:00Z"
                }
                """.trimIndent()
            )
        )
        assertEquals(42L, ex.id)
        assertEquals(QslMode.EQSL, ex.mode)
        assertEquals(QslDirection.INCOMING, ex.direction)
        assertEquals(QslStatus.PENDING, ex.status)
        assertEquals(9L, ex.toCardId!!)
        assertNull(ex.fromCardId)

        val summary = QslJson.parseSummary(
            JSONObject(
                """{"incoming":2,"outgoing":1,"completed":7,"paper":3,"eqsl":4,
                    "latestIncomingId":88,"serverTime":"2026-11-11T00:00:00Z"}""".trimIndent()
            )
        )
        assertEquals(2, summary.incoming)
        assertEquals(7, summary.completed)
        assertEquals(88L, summary.latestIncomingId)
    }

    @Test
    fun parseError_mapsEveryServerCode() {
        val (code, message) = QslJson.parseError(
            """{"error":{"code":"PEER_NOT_REGISTERED","message":"对方尚未注册"}}"""
        )
        assertEquals(QslErrorCode.PEER_NOT_REGISTERED, code)
        assertEquals("对方尚未注册", message)
        assertEquals(
            QslErrorCode.SERVER_ERROR,
            QslJson.parseError("""{"error":{"code":"SOMETHING_NEW"}}""").first
        )
        assertEquals(QslErrorCode.BAD_RESPONSE, QslJson.parseError("<html>404</html>").first)
    }

    @Test
    fun allServerErrorCodesAreKnownToClient() {
        val serverCodes = listOf(
            "CARD_NOT_FOUND", "DUPLICATE_EXCHANGE", "EXCHANGE_NOT_FOUND", "FORBIDDEN",
            "IMAGE_TOO_LARGE", "INVALID_ACTION", "INVALID_CALLSIGN", "INVALID_EMAIL",
            "INVALID_FIELD", "INVALID_GRID", "INVALID_ID", "INVALID_IMAGE", "INVALID_MODE",
            "INVALID_TRANSITION", "NOT_ACCEPTED", "NO_USER", "PEER_NOT_AVAILABLE",
            "PEER_NOT_REGISTERED", "SELF_EXCHANGE", "SERVER_ERROR", "UNREGISTERED_PEER",
            "NO_TOKEN", "TOKEN_EXPIRED"
        )
        serverCodes.forEach { raw ->
            assertFalse(
                "服务端错误码 $raw 在客户端被吞成了 SERVER_ERROR",
                QslErrorCode.from(raw) == QslErrorCode.SERVER_ERROR && raw != "SERVER_ERROR"
            )
        }
    }

    @Test
    fun stateMachine_paperNeedsMailing_eqslAcceptCompletes() {
        assertEquals(QslStatus.ACCEPTED, QslStateMachine.next("accept", QslMode.PAPER, QslStatus.PENDING))
        assertEquals(QslStatus.COMPLETED, QslStateMachine.next("accept", QslMode.EQSL, QslStatus.PENDING))
        assertEquals(QslStatus.SENT, QslStateMachine.next("sent", QslMode.PAPER, QslStatus.ACCEPTED))
        assertNull(QslStateMachine.next("sent", QslMode.EQSL, QslStatus.ACCEPTED))
        assertNull(QslStateMachine.next("sent", QslMode.PAPER, QslStatus.PENDING))
        assertEquals(QslStatus.COMPLETED, QslStateMachine.next("complete", QslMode.PAPER, QslStatus.SENT))
        assertNull(QslStateMachine.next("complete", QslMode.PAPER, QslStatus.PENDING))
        assertEquals(QslStatus.DECLINED, QslStateMachine.next("decline", QslMode.PAPER, QslStatus.PENDING))
        assertEquals(QslStatus.CANCELLED, QslStateMachine.next("cancel", QslMode.EQSL, QslStatus.ACCEPTED))
        assertNull(QslStateMachine.next("cancel", QslMode.PAPER, QslStatus.COMPLETED))
        assertNull(QslStateMachine.next("unknown", QslMode.PAPER, QslStatus.PENDING))
    }

    @Test
    fun availableActions_respectDirection() {
        val incoming = QslStateMachine.availableActions(
            QslMode.PAPER, QslStatus.PENDING, QslDirection.INCOMING
        ).map { it.first }
        assertEquals(listOf("accept", "decline"), incoming)

        val outgoing = QslStateMachine.availableActions(
            QslMode.PAPER, QslStatus.PENDING, QslDirection.OUTGOING
        ).map { it.first }
        assertEquals(listOf("cancel"), outgoing)

        val accepted = QslStateMachine.availableActions(
            QslMode.PAPER, QslStatus.ACCEPTED, QslDirection.INCOMING
        ).map { it.first }
        assertEquals(listOf("sent", "complete"), accepted)
        assertTrue(
            QslStateMachine.availableActions(
                QslMode.EQSL, QslStatus.COMPLETED, QslDirection.INCOMING
            ).isEmpty()
        )
    }

    @Test
    fun demoStore_createRecordOnlyAndRejectDuplicates() {
        QslDemoStore.deleteMe()
        QslDemoStore.ensureSeed("BA4IGG")
        val draft = QslExchangeDraft(
            mode = QslMode.PAPER,
            peerCallsign = "ja1abc",
            qsoDate = "2026-11-11",
            recordOnly = true
        )
        val created = QslDemoStore.createExchange(draft) as QslResult.Success
        assertTrue(created.demo)
        assertEquals("JA1ABC", created.value.peerCallsign)
        assertEquals(QslStatus.RECORDED, created.value.status)
        assertFalse(created.value.registeredPeer)

        val duplicate = QslDemoStore.createExchange(draft)
        assertTrue(duplicate is QslResult.Failure)
        assertEquals(QslErrorCode.DUPLICATE_EXCHANGE, (duplicate as QslResult.Failure).code)

        val blank = QslDemoStore.createExchange(draft.copy(peerCallsign = " "))
        assertEquals(QslErrorCode.INVALID_CALLSIGN, (blank as QslResult.Failure).code)
    }

    @Test
    fun demoStore_actAndContactOnlyAfterAcceptance() {
        QslDemoStore.deleteMe()
        QslDemoStore.ensureSeed("BA4IGG")
        val pending = QslDemoStore.exchanges().first {
            it.status == QslStatus.PENDING && it.direction == QslDirection.INCOMING
        }
        // 演示种子里的 pending 都是 incoming，接受应当成功
        val accepted = QslDemoStore.act(pending.id, "accept") as QslResult.Success
        assertEquals(
            if (pending.mode == QslMode.EQSL) QslStatus.COMPLETED else QslStatus.ACCEPTED,
            accepted.value.status
        )
        assertTrue(QslDemoStore.contact(pending.id) is QslResult.Success)

        val recorded = QslDemoStore.exchanges().first { it.status == QslStatus.RECORDED }
        assertTrue(QslDemoStore.contact(recorded.id) is QslResult.Failure)
        // 只有发起方能取消
        assertEquals(
            QslErrorCode.FORBIDDEN,
            (QslDemoStore.act(pending.id, "cancel") as QslResult.Failure).code
        )
        assertEquals(
            QslErrorCode.NOT_FOUND,
            (QslDemoStore.act(999999L, "accept") as QslResult.Failure).code
        )
    }

    @Test
    fun imageSniffer_acceptsOnlyRealHeaders() {
        val jpeg = ByteArray(16).also {
            it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = 0xFF.toByte()
        }
        val png = ByteArray(16).also {
            val magic = byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
            )
            magic.copyInto(it)
        }
        val webp = ByteArray(16).also {
            "RIFF".toByteArray().copyInto(it, 0)
            "WEBP".toByteArray().copyInto(it, 8)
        }
        assertEquals("image/jpeg", QslImageSniffer.mimeOf(jpeg))
        assertEquals("image/png", QslImageSniffer.mimeOf(png))
        assertEquals("image/webp", QslImageSniffer.mimeOf(webp))
        assertNull(QslImageSniffer.mimeOf(ByteArray(16)))
        assertNull(QslImageSniffer.mimeOf(ByteArray(4)))
        assertTrue(QslImageSniffer.check(jpeg).ok)
        assertEquals("image/webp", QslImageSniffer.check(webp).mime)
        assertFalse(QslImageSniffer.check(ByteArray(16)).ok)
    }

    @Test
    fun imageSniffer_rejectsOversizedUpload() {
        val big = ByteArray(QslImageSniffer.MAX_BYTES + 1)
        assertTrue(QslImageSniffer.isTooLarge(big))
        val result = QslImageSniffer.check(big)
        assertFalse(result.ok)
        assertNotNull(result.error)
    }

    @Test
    fun modeAndStatusFromUnknownKeysFallBackSafely() {
        assertEquals(QslMode.PAPER, QslMode.from(null))
        assertEquals(QslMode.EQSL, QslMode.from("eqsl"))
        assertEquals(QslStatus.PENDING, QslStatus.from("???"))
        assertEquals(QslDirection.OUTGOING, QslDirection.from(null))
    }
}

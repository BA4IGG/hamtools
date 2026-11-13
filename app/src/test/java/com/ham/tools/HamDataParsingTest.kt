package com.ham.tools

import com.ham.tools.data.remote.HamDataClient
import com.ham.tools.util.GridMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 在线数据源的解析纯函数回归测试（不发起真实网络请求）。 */
class HamDataParsingTest {

    @Test
    fun normalize_callsign_strips_suffix_and_ssid() {
        assertEquals("BA4IGG", HamDataClient.normalizeCallsign("ba4igg"))
        assertEquals("BA4IGG", HamDataClient.normalizeCallsign("BA4IGG/P"))
        assertEquals("BA1GM", HamDataClient.normalizeCallsign("BA1GM-11"))
        assertEquals("W1AW", HamDataClient.normalizeCallsign(" W1AW/KH6 "))
        assertEquals("", HamDataClient.normalizeCallsign(""))
        assertEquals("", HamDataClient.normalizeCallsign("!!"))
        assertEquals("", HamDataClient.normalizeCallsign("AB"))
    }

    @Test
    fun parse_hamdb_success_payload() {
        val json = """
            {"hamdb":{"version":"1","callsign":{
              "call":"W1AW","class":"","expires":"02/26/2031","status":"A",
              "grid":"FN31pr","lat":"41.7146626","lon":"-72.7284655",
              "name":"ARRL HQ OPERATORS CLUB","country":"United States"},
              "messages":{"status":"OK"}}}
        """.trimIndent()
        val result = HamDataClient.parseHamDb(json, "W1AW", "HamDB")
        assertNotNull(result)
        assertEquals("W1AW", result!!.callsign)
        assertEquals("ARRL HQ OPERATORS CLUB", result.name)
        assertEquals("United States", result.country)
        assertEquals("FN31pr", result.grid)
        assertEquals("HamDB", result.source)
        // 空白字段应转成 null，而不是把空字符串显示给用户
        assertNull(result.licenseClass)
    }

    @Test
    fun parse_hamdb_not_found_or_garbage_returns_null() {
        assertNull(
            HamDataClient.parseHamDb(
                """{"hamdb":{"messages":{"status":"NOT_FOUND"}}}""", "X", "s"
            )
        )
        assertNull(HamDataClient.parseHamDb("not json", "X", "s"))
        assertNull(HamDataClient.parseHamDb(null, "X", "s"))
        assertNull(HamDataClient.parseHamDb("", "X", "s"))
        assertNull(HamDataClient.parseHamDb("{}", "X", "s"))
    }

    @Test
    fun parse_dxwatch_table() {
        val html = """
            <table>
            <tr><td><a href="/qrz/G0API">G0API</a></td><td><img src="/img/f/england.gif"></td><td><a href="/qrz/GB3LEU/B">GB3LEU/B</a></td><td>432430</td><td>IO80XS(TR)IO92IQ 529</td><td><a href="/">0958z 11 Nov</a></td></tr>
            <tr><td><a href="/qrz/SP7ROM">SP7ROM</a></td><td><img src="/img/f/it.gif"></td><td><a href="/qrz/IW2EYR">IW2EYR</a></td><td>18100</td><td>[LoTW] FT8 +17dB 901Hz</td><td><a href="/">0957z 11 Nov</a></td></tr>
            </table>
        """.trimIndent()
        val spots = HamDataClient.parseDxWatch(html)
        assertEquals(2, spots.size)
        assertEquals("G0API", spots[0].spotter)
        assertEquals("GB3LEU/B", spots[0].dxCallsign)
        assertEquals(432430.0, spots[0].freqKHz, 0.001)
        assertEquals("09:58", spots[0].time)
        assertEquals("IO80XS(TR)IO92IQ 529", spots[0].comment)
        assertEquals("IW2EYR", spots[1].dxCallsign)
        assertEquals(18100.0, spots[1].freqKHz, 0.001)
    }

    @Test
    fun parse_dxwatch_degrades_on_broken_markup() {
        assertTrue(HamDataClient.parseDxWatch(null).isEmpty())
        assertTrue(HamDataClient.parseDxWatch("").isEmpty())
        assertTrue(
            HamDataClient.parseDxWatch("<html><body>Cloudflare challenge</body></html>").isEmpty()
        )
        assertTrue(HamDataClient.parseDxWatch("<tr><td>no links</td><td>14085</td></tr>").isEmpty())
        assertTrue(
            HamDataClient.parseDxWatch(
                """<tr><td><a href="/qrz/W1AW">W1AW</a></td><td><a href="/qrz/KH8A">KH8A</a></td><td>abc</td><td><a href="/">0958z 11 Nov</a></td></tr>"""
            ).isEmpty()
        )
    }

    @Test
    fun html_helpers() {
        assertEquals("09:58", HamDataClient.formatTime("0958z 11 Nov"))
        assertEquals(
            "W1AW test",
            HamDataClient.plainText("<a href=\"/qrz/W1AW\">W1AW</a>&nbsp;test")
        )
        assertEquals("A & B", HamDataClient.plainText("A &amp; B"))
    }

    @Test
    fun app_grid_math_matches_server_maidenhead4() {
        // 与 server/src/routes/ham.js 的 maidenhead4 自检值保持一致，
        // 保证服务端下发与 App 端解析的粗粒度网格是同一套编码
        assertEquals("OM89", GridMath.encode(39.90, 116.40, 2))
        assertEquals("PM01", GridMath.encode(31.23, 121.47, 2))
        assertNotNull(GridMath.decode(GridMath.encode(39.90, 116.40, 2)))
    }
}

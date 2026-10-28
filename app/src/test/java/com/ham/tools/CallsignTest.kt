package com.ham.tools

import com.ham.tools.util.Callsign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 呼号校验与分区换算：与 server/src/callsign.js 保持一致。 */
class CallsignTest {

    @Test
    fun normalize_uppercasesAndTrims() {
        assertEquals("BA4IGG", Callsign.normalize(" ba4igg "))
        assertEquals("BG5XYZ", Callsign.normalize("bg5xyz"))
        assertEquals("BI1ABC", Callsign.normalize("BI1ABC"))
    }

    @Test
    fun normalize_rejectsInvalid() {
        assertNull(Callsign.normalize("X1ABC"))       // 不是 B 开头
        assertNull(Callsign.normalize("B4IGG"))       // 缺种类字母
        assertNull(Callsign.normalize("BAIGG"))       // 缺分区数字
        assertNull(Callsign.normalize("BA4"))         // 缺后缀
        assertNull(Callsign.normalize("BA4IGGXX"))    // 后缀 5 位
        assertNull(Callsign.normalize(""))
        assertNull(Callsign.normalize(null))
    }

    @Test
    fun normalize_rejectsReservedSuffix() {
        assertNull(Callsign.normalize("BA4QOA"))      // QOA~QUZ 保留
        assertNull(Callsign.normalize("BA4SOS"))      // 遇险信号
        assertNull(Callsign.normalize("BA4XXX"))
        assertNull(Callsign.normalize("BA4TTT"))
    }

    @Test
    fun region_matchesOfficialTable() {
        assertEquals("北京", Callsign.regionOf("BI1ABC"))
        assertEquals("黑龙江、吉林、辽宁", Callsign.regionOf("BG2ABC"))
        assertEquals("天津、内蒙古、河北、山西", Callsign.regionOf("BH3ABC"))
        assertEquals("上海、山东、江苏", Callsign.regionOf("BA4IGG"))
        assertEquals("浙江、江西、福建", Callsign.regionOf("BG5XYZ"))
        assertEquals("河南、湖北、安徽", Callsign.regionOf("BD6ABC"))
        assertEquals("湖南、广东、广西、海南", Callsign.regionOf("BA7ABC"))
        assertEquals("四川、重庆、贵州、云南", Callsign.regionOf("BA8ABC"))
        assertEquals("陕西、甘肃、宁夏、青海", Callsign.regionOf("BA9ABC"))
        assertEquals("新疆、西藏", Callsign.regionOf("BA0ABC"))
    }

    @Test
    fun region_returnsNullForInvalid() {
        assertNull(Callsign.regionOf("X1ABC"))
        assertNull(Callsign.regionOf(""))
    }

    @Test
    fun isValid_agreesWithNormalize() {
        assertTrue(Callsign.isValid("ba4igg"))
        assertTrue(!Callsign.isValid("BA4IGGXX"))
    }
}

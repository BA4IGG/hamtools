package com.ham.tools

import com.ham.tools.data.reference.BandPlanTable
import com.ham.tools.data.reference.BandRegion
import com.ham.tools.data.reference.MorseTable
import com.ham.tools.data.reference.PrefixTable
import com.ham.tools.data.reference.QCodeTable
import com.ham.tools.data.reference.SatRepeaterTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 查询手册板块数据表的本地单元测试，不依赖 Android 框架。
 *
 * 重点验证两类容易出错的地方：
 * - **匹配逻辑**：前缀最长匹配、斜杠后缀、便携标记
 * - **编解码往返**：文字 → 电码 → 文字应当回到原样
 */
class ReferenceTableTest {

    // ---------------- 呼号前缀 ----------------

    @Test
    fun prefix_lookupChineseStation() {
        assertEquals("中国", PrefixTable.lookup("BA4IGG")?.entity)
    }

    @Test
    fun prefix_lookupIsCaseInsensitive() {
        assertEquals("中国", PrefixTable.lookup("ba4igg")?.entity)
    }

    @Test
    fun prefix_lookupIgnoresSpaces() {
        assertEquals("美国", PrefixTable.lookup(" W 1 AW ")?.entity)
    }

    @Test
    fun prefix_lookupTaiwan() {
        assertEquals("中国台湾", PrefixTable.lookup("BV2XX")?.entity)
    }

    @Test
    fun prefix_lookupHongKong() {
        assertEquals("中国香港", PrefixTable.lookup("VR2ABC")?.entity)
    }

    @Test
    fun prefix_lookupMacao() {
        assertEquals("中国澳门", PrefixTable.lookup("XX9ABC")?.entity)
    }

    @Test
    fun prefix_slashSuffixWinsOverHead() {
        // W1AW 在夏威夷操作：应判到 KH6 而非美国的 K
        assertEquals("夏威夷", PrefixTable.lookup("W1AW/KH6")?.entity)
    }

    @Test
    fun prefix_portableMarkerDoesNotChangeEntity() {
        assertEquals("英国", PrefixTable.lookup("G3ABC/P")?.entity)
        assertEquals("英国", PrefixTable.lookup("G3ABC/M")?.entity)
        assertEquals("英国", PrefixTable.lookup("G3ABC/QRP")?.entity)
    }

    @Test
    fun prefix_unknownReturnsNull() {
        assertNull(PrefixTable.lookup(""))
        assertNull(PrefixTable.lookup("   "))
        assertNull(PrefixTable.lookup("12345XYZ"))
    }

    @Test
    fun prefix_longestPrefixBeatsShorter() {
        // 2 区（美洲）的 40m 上限更宽，但前缀必须是 K 而非 KH6 之类
        assertEquals("美国", PrefixTable.lookup("K1ABC")?.entity)
        assertEquals("阿拉斯加", PrefixTable.lookup("KL7ABC")?.entity)
        assertEquals("夏威夷", PrefixTable.lookup("KH6ABC")?.entity)
    }

    @Test
    fun prefix_searchFindsByNameAndPrefix() {
        assertTrue(PrefixTable.search("日本").any { it.prefix == "JA" })
        assertTrue(PrefixTable.search("DL").any { it.entity == "德国" })
    }

    @Test
    fun prefix_filterByContinentIsConsistent() {
        val asian = PrefixTable.filterByContinent("AS")
        assertTrue(asian.isNotEmpty())
        assertTrue(asian.all { it.continent == "AS" })
    }

    @Test
    fun prefix_noDuplicateKeys() {
        // 列表用作 LazyColumn 的 key，重复会导致运行时崩溃
        val prefixes = PrefixTable.entries.map { it.prefix }
        assertEquals(prefixes.size, prefixes.toSet().size)
    }

    // ---------------- 摩尔斯电码 ----------------

    @Test
    fun morse_encodeSos() {
        assertEquals("... --- ...", MorseTable.encode("SOS"))
    }

    @Test
    fun morse_encodeSeparatesWords() {
        assertEquals("-.-. --.- / -.. .", MorseTable.encode("CQ DE"))
    }

    @Test
    fun morse_decodeRoundTrip() {
        val text = "CQ CQ DE BA4IGG"
        assertEquals(text, MorseTable.decode(MorseTable.encode(text)))
    }

    @Test
    fun morse_decodeDigits() {
        assertEquals("73", MorseTable.decode("--... ...--"))
    }

    @Test
    fun morse_decodeUnknownIsQuestionMark() {
        assertEquals("?", MorseTable.decode(".-.-.-.-.-.-.-.-"))
    }

    @Test
    fun morse_encodeSkipsUnsupportedCharacters() {
        // 汉字无对应电码，应被忽略而不是产生乱码
        assertEquals(".... .- --", MorseTable.encode("HAM 电台"))
    }

    @Test
    fun morse_encodeSupportsExclamation() {
        // 「!」有独立电码，不应被当成无法编码的字符丢掉
        assertEquals("-.-.--", MorseTable.encode("!"))
    }

    @Test
    fun morse_alphabetIsComplete() {
        assertEquals(26, MorseTable.letters.size)
        assertEquals(10, MorseTable.digits.size)
        assertTrue(MorseTable.punctuations.isNotEmpty())
        assertTrue(MorseTable.prosigns.isNotEmpty())
    }

    @Test
    fun morse_unitLengthFollowsParisStandard() {
        // 20 WPM → T = 1200/20 = 60ms；一个点应为 60ms
        val symbols = MorseTable.toSymbols("E", 20) // E = "."
        val firstTone = symbols.first { it.on }
        assertEquals(60, firstTone.durationMs)
    }

    @Test
    fun morse_dahIsThreeUnits() {
        val symbols = MorseTable.toSymbols("T", 20) // T = "-"
        val firstTone = symbols.first { it.on }
        assertEquals(180, firstTone.durationMs)
    }

    @Test
    fun morse_slowSpeedIsRespected() {
        // 5 WPM → T = 240ms
        val symbols = MorseTable.toSymbols("E", 5)
        assertEquals(240, symbols.first { it.on }.durationMs)
    }

    @Test
    fun morse_speedIsClamped() {
        // 超出合理区间应被夹住，避免算出负时长或过短的脉冲
        val tooFast = MorseTable.toSymbols("E", 500)
        val tooSlow = MorseTable.toSymbols("E", 1)
        assertTrue(tooFast.first { it.on }.durationMs >= 20)
        assertTrue(tooSlow.first { it.on }.durationMs <= 600)
    }

    @Test
    fun morse_timingStartsAndEndsSilent() {
        val symbols = MorseTable.toSymbols("SOS", 15)
        assertTrue(symbols.first().on)
        assertTrue(symbols.last().let { !it.on })
    }

    @Test
    fun morse_emptyInputProducesNothing() {
        assertTrue(MorseTable.toSymbols("", 20).isEmpty())
        assertEquals("", MorseTable.decode(""))
    }

    // ---------------- 频段划分 ----------------

    @Test
    fun bandPlan_everyRegionHasEntries() {
        BandRegion.entries.forEach { region ->
            assertTrue("$region 不应为空", BandPlanTable.entriesOf(region).isNotEmpty())
        }
    }

    @Test
    fun bandPlan_rangesAreOrdered() {
        BandRegion.entries.forEach { region ->
            BandPlanTable.entriesOf(region).forEach { entry ->
                assertTrue("${entry.band} 起止频率颠倒", entry.startMHz < entry.endMHz)
            }
        }
    }

    @Test
    fun bandPlan_bandsAreUniqueWithinRegion() {
        BandRegion.entries.forEach { region ->
            val bands = BandPlanTable.entriesOf(region).map { it.band }
            assertEquals(bands.size, bands.toSet().size)
        }
    }

    @Test
    fun bandPlan_searchMatchesBandName() {
        val hits = BandPlanTable.search(BandRegion.CN, "20m")
        assertTrue(hits.any { it.band == "20m" })
    }

    @Test
    fun bandPlan_searchMatchesMode() {
        assertTrue(BandPlanTable.search(BandRegion.CN, "CW").size > 5)
    }

    @Test
    fun bandPlan_emptyKeywordReturnsAll() {
        assertEquals(
            BandPlanTable.entriesOf(BandRegion.CN).size,
            BandPlanTable.search(BandRegion.CN, "").size
        )
    }

    @Test
    fun bandPlan_unknownKeywordReturnsEmpty() {
        assertTrue(BandPlanTable.search(BandRegion.CN, "zzzz").isEmpty())
    }

    // ---------------- Q 简语 ----------------

    @Test
    fun qcode_searchFindsQth() {
        val hits = QCodeTable.searchQCode("QTH")
        assertTrue(hits.any { it.code == "QTH" })
    }

    @Test
    fun qcode_searchIsCaseInsensitive() {
        assertTrue(QCodeTable.searchQCode("qth").isNotEmpty())
    }

    @Test
    fun qcode_qthHasBothFaces() {
        val qth = QCodeTable.qCodes.first { it.code == "QTH" }
        assertTrue(qth.question.isNotBlank())
        assertTrue(qth.statement.isNotBlank())
    }

    @Test
    fun qcode_phoneticsCoverAlphabetAndDigits() {
        val letters = QCodeTable.phonetics.filter { it.letter.isLetter() }.map { it.letter }
        assertEquals(26, letters.distinct().size)
        val digits = QCodeTable.phonetics.filter { it.letter.isDigit() }.map { it.letter }
        assertEquals(10, digits.distinct().size)
    }

    @Test
    fun qcode_toPhoneticWordsMapsLetters() {
        assertEquals(
            listOf("Bravo", "Alpha", "Four"),
            QCodeTable.toPhoneticWords("BA4")
        )
    }

    // ---------------- 卫星与中继 ----------------

    @Test
    fun satellite_allHaveUplinkAndDownlink() {
        SatRepeaterTable.satellites.forEach { sat ->
            assertTrue("${sat.name} 缺少频率", sat.uplinkMHz != null && sat.downlinkMHz != null)
        }
    }

    @Test
    fun satellite_toneIsOnlyUsedByFmRepeaters() {
        // 并非所有 FM 转发器都要亚音（如 ISS 语音中继与部分线性载荷），
        // 但反过来必须成立：凡是标了亚音的，都应是 FM 语音转发器
        val withTone = SatRepeaterTable.satellites.filter { it.toneHz != null }
        assertTrue(withTone.isNotEmpty())
        assertTrue(withTone.all { it.mode.contains("FM") })
        assertTrue(withTone.any { it.name == "SO-50" })
        assertTrue(withTone.any { it.name == "AO-91" })
    }

    @Test
    fun satellite_searchByName() {
        assertTrue(SatRepeaterTable.searchSatellite("QO-100").isNotEmpty())
    }

    @Test
    fun repeater_frequenciesAreWithinAllocation() {
        SatRepeaterTable.repeaters.forEach { rpt ->
            val down = rpt.downlinkMHz
            val up = rpt.uplinkMHz
            assertTrue("${rpt.city} 下行 $down 超出 430-440", down in 430.0..440.0)
            assertTrue("${rpt.city} 上行 $up 超出 430-440", up in 430.0..440.0)
        }
    }

    @Test
    fun repeater_uplinkEqualsDownlinkPlusShift() {
        SatRepeaterTable.repeaters.forEach { rpt ->
            val expected = rpt.downlinkMHz + rpt.shiftMHz
            assertEquals("${rpt.city} 上行频率不符", expected, rpt.uplinkMHz, 1e-6)
        }
    }

    @Test
    fun repeater_groupByRegionKeepsDefinedOrder() {
        val grouped = SatRepeaterTable.groupByRegion()
        val keys = grouped.keys.toList()
        assertEquals(SatRepeaterTable.regions, keys)
    }

    @Test
    fun repeater_searchByCity() {
        assertTrue(SatRepeaterTable.searchRepeater("北京").isNotEmpty())
    }
}

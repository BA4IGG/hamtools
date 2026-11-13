package com.ham.tools

import com.ham.tools.data.reference.BandQuality
import com.ham.tools.data.reference.CallsignLookup
import com.ham.tools.data.reference.DxSpotTable
import com.ham.tools.data.reference.MorseTable
import com.ham.tools.data.reference.SolarModel
import com.ham.tools.util.CwLesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HamDataTest {

    @Test
    fun koch_levels_grow_monotonically() {
        assertEquals(40, CwLesson.MAX_LEVEL)
        assertEquals(listOf('K', 'M'), CwLesson.charsForLevel(2))
        assertEquals('M', CwLesson.newCharOf(2))
        assertTrue(CwLesson.charsForLevel(10).containsAll(CwLesson.charsForLevel(3)))
        assertEquals(40, CwLesson.charsForLevel(99).size)
        assertEquals(2, CwLesson.charsForLevel(0).size)
    }

    @Test
    fun koch_group_is_reproducible_and_only_uses_learned_chars() {
        val first = CwLesson.generateGroup(10, 5, 12345L)
        val second = CwLesson.generateGroup(10, 5, 12345L)
        assertEquals(first, second)
        assertEquals(5, first.length)
        assertTrue(first.all { it in CwLesson.charsForLevel(10) })
        assertTrue(CwLesson.unitMs(20) == 60)
        assertTrue(CwLesson.groupDurationMs(first, 15) > 0)
    }

    @Test
    fun every_koch_character_is_morse_encodable() {
        for (ch in CwLesson.KOCH_ORDER) {
            val symbols = MorseTable.toSymbols(ch.toString(), 15)
            assertTrue("字符 $ch 无法用摩尔斯编码", symbols.any { it.on })
        }
    }

    @Test
    fun solar_forecast_degrades_as_geomagnetic_activity_rises() {
        val calm = SolarModel.forecast(150, 1.0)
        val storm = SolarModel.forecast(150, 8.0)
        assertEquals(6, calm.size)
        val calmPoor = calm.count { it.quality == BandQuality.POOR }
        val stormPoor = storm.count { it.quality == BandQuality.POOR }
        assertTrue("磁暴时差波段不应更少 ($calmPoor -> $stormPoor)", stormPoor >= calmPoor)
        assertTrue(SolarModel.kpDescription(1.0).contains("平静"))
        assertTrue(SolarModel.kpDescription(7.0).contains("磁暴"))
    }

    @Test
    fun builtin_tables_are_well_formed() {
        assertEquals(12, DxSpotTable.spots.size)
        assertTrue(DxSpotTable.spots.all { it.bandLabel.isNotBlank() && it.freqKHz > 0 })
    }

    @Test
    fun callsign_lookup_resolves_prefix_and_china_region() {
        val own = CallsignLookup.parse("ba4igg")
        assertTrue(own.isValid)
        assertEquals("BA4IGG", own.normalized)
        assertEquals("上海、山东、江苏", own.chinaRegion)
        assertTrue(own.entity.isNotBlank())

        val portable = CallsignLookup.parse("BA4IGG/P")
        assertEquals("P", portable.suffix)
        assertEquals("便携 / 临时台", portable.suffixMeaning)

        val abroad = CallsignLookup.parse("JA1ABC")
        assertTrue(abroad.isValid)
        assertTrue(abroad.entity.isNotBlank())
    }
}

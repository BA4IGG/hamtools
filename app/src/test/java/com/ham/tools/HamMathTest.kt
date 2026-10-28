package com.ham.tools

import com.ham.tools.data.reference.BandTable
import com.ham.tools.data.reference.CoaxTable
import com.ham.tools.util.HamMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 射频计算核心逻辑的本地单元测试，不依赖 Android 框架。 */
class HamMathTest {

    private companion object {
        const val DELTA = 1e-3
        const val TIGHT_DELTA = 1e-6
    }

    // ---------------- 频率 / 波长 ----------------

    @Test
    fun wavelength_matchesSpeedOfLight() {
        val wave = HamMath.wavelengthMeters(14_174_000.0)
        assertEquals(21.1509, wave, DELTA)

        val free = HamMath.wavelengthMeters(300_000_000.0)
        assertEquals(0.99931, free, DELTA)
    }

    @Test
    fun frequency_isInverseOfWavelength() {
        val wave = HamMath.wavelengthMeters(144_000_000.0)
        val back = HamMath.frequencyHz(wave)
        assertEquals(144_000_000.0, back, 1.0)
    }

    @Test
    fun invalidInput_returnsNaN() {
        assertTrue(HamMath.wavelengthMeters(0.0).isNaN())
        assertTrue(HamMath.wavelengthMeters(-5.0).isNaN())
    }

    @Test
    fun unitConversion_isConsistent() {
        assertEquals(3.28084, HamMath.metersToFeet(1.0), 1e-4)
        assertEquals(1.0, HamMath.feetToMeters(HamMath.metersToFeet(1.0)), TIGHT_DELTA)
    }

    // ---------------- 功率 / 分贝 ----------------

    @Test
    fun powerConversion_roundTrip() {
        assertEquals(50.0, HamMath.wattToDbm(100.0), DELTA)
        assertEquals(30.0, HamMath.wattToDbm(1.0), DELTA)
        assertEquals(100.0, HamMath.dbmToWatt(HamMath.wattToDbm(100.0)), DELTA)
    }

    @Test
    fun dbRatio_conversion() {
        assertEquals(2.0, HamMath.dbToRatio(3.0103), DELTA)
        assertEquals(10.0, HamMath.ratioToDb(10.0), DELTA)
    }

    // ---------------- 驻波 / 失配 ----------------

    @Test
    fun vswr2_reflectionCoefficient() {
        val gamma = HamMath.reflectionCoefficientFromVswr(2.0)
        assertEquals(0.3333, gamma, 1e-4)
    }

    @Test
    fun vswr2_returnLossAndMismatchLoss() {
        val gamma = HamMath.reflectionCoefficientFromVswr(2.0)
        assertEquals(9.5424, HamMath.returnLossDb(gamma), 1e-3)
        assertEquals(0.5115, HamMath.mismatchLossDb(gamma), 1e-3)
        assertEquals(11.1111, HamMath.reflectedPowerFraction(gamma) * 100.0, 1e-3)
    }

    @Test
    fun perfectMatch_meansInfiniteReturnLoss() {
        val gamma = HamMath.reflectionCoefficientFromVswr(1.0)
        assertEquals(0.0, gamma, TIGHT_DELTA)
        assertTrue(HamMath.returnLossDb(gamma).isInfinite())
        assertEquals(0.0, HamMath.mismatchLossDb(gamma), TIGHT_DELTA)
    }

    @Test
    fun vswr_roundTrip() {
        val gamma = HamMath.reflectionCoefficientFromVswr(3.0)
        assertEquals(3.0, HamMath.vswrFromReflectionCoefficient(gamma), TIGHT_DELTA)
    }

    @Test
    fun invalidVswr_returnsNaN() {
        assertTrue(HamMath.reflectionCoefficientFromVswr(0.5).isNaN())
        assertTrue(HamMath.reflectionCoefficientFromVswr(-1.0).isNaN())
    }

    // ---------------- 天线 ----------------

    @Test
    fun halfWaveDipole_matchesRuleOfThumb() {
        val totalMeters = HamMath.antennaLengthMeters(
            frequencyHz = 14_174_000.0,
            wavelengthFraction = 0.5,
            velocityFactor = 0.95
        )
        assertEquals(10.0467, totalMeters, DELTA)
        // 经典经验式 143 / f(MHz) ≈ 10.09 m
        assertTrue(totalMeters in 9.8..10.3)
    }

    @Test
    fun twentyMeterDipole_saneLegLength() {
        val leg = HamMath.antennaLengthMeters(
            frequencyHz = 7_050_000.0,
            wavelengthFraction = 0.25,
            velocityFactor = 0.95
        )
        assertEquals(10.0994, leg, DELTA)
    }

    // ---------------- 馈线 ----------------

    @Test
    fun coaxLoss_hitsTableAnchorPoints() {
        val rg58 = CoaxTable.find("rg58")!!
        assertEquals(4.9, HamMath.coaxLossDbPer100Feet(rg58, 100.0), DELTA)
        assertEquals(1.4, HamMath.coaxLossDbPer100Feet(rg58, 10.0), DELTA)
        assertEquals(10.6, HamMath.coaxLossDbPer100Feet(rg58, 450.0), DELTA)
    }

    @Test
    fun coaxLoss_interpolatesBetweenAnchors() {
        val rg58 = CoaxTable.find("rg58")!!
        val at146 = HamMath.coaxLossDbPer100Feet(rg58, 146.0)
        assertTrue("146MHz 应落在 100MHz 与 150MHz 之间", at146 in 4.9..6.2)
    }

    @Test
    fun coaxLoss_increasesWithFrequency() {
        val lm = CoaxTable.find("lmr400")!!
        assertTrue(HamMath.coaxLossDbPer100Feet(lm, 450.0) > HamMath.coaxLossDbPer100Feet(lm, 30.0))
    }

    @Test
    fun matchedLineLoss_scalesWithLength() {
        val rg213 = CoaxTable.find("rg213")!!
        val perMeter = HamMath.coaxLossDbPer100Meters(rg213, 14.174)
        assertTrue(perMeter > 0.0)

        val long = HamMath.matchedLineLossDb(rg213, 14.174, 100.0)
        val half = HamMath.matchedLineLossDb(rg213, 14.174, 50.0)
        assertEquals(long, half * 2.0, TIGHT_DELTA)
    }

    @Test
    fun totalLoss_degeneratesToMatchedLossWhenPerfectSWR() {
        val rg213 = CoaxTable.find("rg213")!!
        val matched = HamMath.matchedLineLossDb(rg213, 144.0, 30.0)
        val withPerfectSWR = HamMath.totalLineLossDb(matched, 1.0)
        assertEquals(matched, withPerfectSWR, DELTA)
    }

    @Test
    fun totalLoss_isGreaterWithMismatch() {
        val rg213 = CoaxTable.find("rg213")!!
        val matched = HamMath.matchedLineLossDb(rg213, 144.0, 30.0)
        val withMismatch = HamMath.totalLineLossDb(matched, 3.0)
        assertTrue(withMismatch > matched)
    }

    @Test
    fun powerAfterLoss_halvesAt3dB() {
        assertEquals(50.0, HamMath.powerAfterLossWatt(100.0, 3.0103), DELTA)
    }

    // ---------------- 链路预算 ----------------

    @Test
    fun freeSpacePathLoss_matchesClassicFormula() {
        // 145 MHz, 100 km → 32.44 + 40 + 43.23 ≈ 115.67 dB
        assertEquals(115.667, HamMath.freeSpacePathLossDb(100.0, 145.0), 1e-2)
    }

    @Test
    fun receivedPower_matchesFriis() {
        val fspl = HamMath.freeSpacePathLossDb(100.0, 145.0)
        val rx = HamMath.receivedPowerDbm(
            txPowerDbm = HamMath.wattToDbm(100.0),
            txGainDbi = 6.0,
            rxGainDbi = 6.0,
            pathLossDb = fspl
        )
        // 50 dBm + 12 dBi - 115.67 dB ≈ -53.67 dBm
        assertEquals(-53.667, rx, 1e-2)
    }

    @Test
    fun sMeter_pinsS9AtMinus73dBm() {
        assertEquals(9.0, HamMath.sMeterUnits(-73.0), TIGHT_DELTA)
        assertEquals(8.0, HamMath.sMeterUnits(-79.0), TIGHT_DELTA)
        assertEquals(1.0, HamMath.sMeterUnits(-121.0), TIGHT_DELTA)
    }

    @Test
    fun dbmToMicrovolt_saneValues() {
        // 0 dBm 折算到 50Ω 约 223.6 mV → 223607 µV
        assertEquals(223_606.8, HamMath.dbmToMicrovolt(0.0), 1.0)
        assertTrue(HamMath.dbmToMicrovolt(-73.0) < HamMath.dbmToMicrovolt(-53.0))
    }

    // ---------------- 频段表 ----------------

    @Test
    fun bandLookup_identifiesCommonBands() {
        assertEquals("20m", BandTable.bandFor(14.174)?.name)
        assertEquals("40m", BandTable.bandFor(7.050)?.name)
        assertEquals("70cm", BandTable.bandFor(438.500)?.name)
        assertNull(BandTable.bandFor(101.0))
    }

    @Test
    fun cableTable_hasExpectedDefaults() {
        assertNotNull(CoaxTable.find("lmr400"))
        assertTrue(CoaxTable.cables.size >= 8)
    }
}

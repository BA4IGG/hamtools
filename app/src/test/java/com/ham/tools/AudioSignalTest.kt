package com.ham.tools

import com.ham.tools.util.AudioSignal
import com.ham.tools.util.DtmfCodec
import com.ham.tools.util.Waveform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSignalTest {

    @Test
    fun dtmf_keys_all_have_valid_frequency_pairs() {
        assertEquals(16, DtmfCodec.KEYS.length)
        for (key in DtmfCodec.KEYS) {
            val pair = DtmfCodec.freqsOf(key)
            assertNotNull("键 $key 缺少频率", pair)
            val (low, high) = pair!!
            assertTrue(DtmfCodec.LOW_FREQ.any { it == low })
            assertTrue(DtmfCodec.HIGH_FREQ.any { it == high })
        }
        assertNull(DtmfCodec.freqsOf('Z'))
    }

    @Test
    fun dtmf_encode_decode_roundtrip_for_all_keys() {
        for (key in DtmfCodec.KEYS) {
            val pcm = DtmfCodec.encode(key, 100)
            assertNotNull("键 $key 无法合成", pcm)
            assertEquals("键 $key 解码错误", key, DtmfCodec.decode(pcm!!))
        }
    }

    @Test
    fun dtmf_sequence_roundtrip() {
        val sequence = "123A456B789C*0#D"
        val parts = mutableListOf<ShortArray>()
        for (key in sequence) {
            parts.add(DtmfCodec.encode(key, 100)!!)
            parts.add(AudioSignal.silence(60))
        }
        val pcm = AudioSignal.concat(parts)
        assertEquals(sequence, DtmfCodec.decodeSequence(pcm))
    }

    @Test
    fun waveforms_have_expected_length_and_ramped_edges() {
        for (wave in Waveform.entries) {
            val pcm = AudioSignal.tone(600.0, 100, wave)
            assertEquals(4410, pcm.size)
            assertEquals(0, pcm.first().toInt())
            assertEquals(0, pcm.last().toInt())
            assertTrue("$wave 全是静音", pcm.any { it.toInt() != 0 })
        }
        assertEquals(4410, AudioSignal.dualTone(697.0, 1209.0, 100).size)
        assertEquals(4410, AudioSignal.sweep(300.0, 3000.0, 100).size)
    }

    @Test
    fun single_tone_and_silence_are_not_decoded_as_dtmf() {
        assertNull(DtmfCodec.decode(AudioSignal.tone(697.0, 100)))
        assertNull(DtmfCodec.decode(AudioSignal.silence(100)))
        assertNull(DtmfCodec.decode(ShortArray(100)))
    }
}

package com.ham.tools

import com.ham.tools.util.SstvCodec
import com.ham.tools.util.SstvMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SstvCodecTest {

    private fun gradient(width: Int, height: Int): IntArray = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        val r = (x * 255 / (width - 1))
        val g = (y * 255 / (height - 1))
        val b = ((x + y) * 255 / (width + height - 2))
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    @Test
    fun `三种模式的 VIS 码与时长参数`() {
        assertEquals(8, SstvMode.ROBOT36.visCode)
        assertEquals(44, SstvMode.MARTIN_M1.visCode)
        assertEquals(60, SstvMode.SCOTTIE_S1.visCode)
        assertEquals(320, SstvMode.ROBOT36.width)
        assertEquals(240, SstvMode.ROBOT36.height)
        assertTrue(SstvMode.MARTIN_M1.imageMs > SstvMode.ROBOT36.imageMs)
    }

    @Test
    fun `每个模式的 VIS 头都能被正确识别`() {
        val src = gradient(320, 240)
        for (mode in SstvMode.ALL) {
            val pcm = SstvCodec.encode(src, 320, 240, mode, lines = 2)
            val image = SstvCodec.decode(pcm, SstvCodec.SAMPLE_RATE)
            assertNotNull("${mode.label} 的 VIS 头没被识别", image)
            assertEquals("${mode.label} 模式识别错误", mode, image!!.mode)
            assertTrue("${mode.label} 至少应解出 2 行，实际 ${image.linesDecoded}",
                image.linesDecoded >= 2)
        }
    }

    @Test
    fun `Robot36 整幅图编码后能解码还原`() {
        val src = gradient(320, 240)
        val pcm = SstvCodec.encode(src, 320, 240, SstvMode.ROBOT36)
        val expectedSamples = ((940.0 + 240 * 150.0) / 1000.0 * SstvCodec.SAMPLE_RATE).toInt()
        assertTrue("音频时长异常: ${pcm.size} vs $expectedSamples",
            abs(pcm.size - expectedSamples) < 2000)

        val image = SstvCodec.decode(pcm, SstvCodec.SAMPLE_RATE)
        assertNotNull("未能解码 VIS 头或同步脉冲", image)
        assertEquals(SstvMode.ROBOT36, image!!.mode)
        assertEquals("应解出全部扫描线", 240, image.linesDecoded)

        for (point in listOf(20 to 20, 160 to 120, 300 to 200)) {
            val (x, y) = point
            val expected = src[y * 320 + x]
            val actual = image.argb[y * 320 + x]
            for (shift in intArrayOf(16, 8, 0)) {
                val e = (expected shr shift) and 0xFF
                val a = (actual shr shift) and 0xFF
                assertTrue("像素 ($x,$y) 通道 $shift 偏差过大: $e -> $a", abs(e - a) <= 32)
            }
        }
    }

    @Test
    fun `静音或过短音频不会被误判成图像`() {
        assertNull(SstvCodec.decode(ShortArray(1000), SstvCodec.SAMPLE_RATE))
        assertNull(SstvCodec.decode(ShortArray(SstvCodec.SAMPLE_RATE * 2), SstvCodec.SAMPLE_RATE))
    }
}

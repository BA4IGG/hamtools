package com.ham.tools

import com.ham.tools.util.Ft8Codec
import com.ham.tools.util.Ft8Constants
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FT8 编解码的 JVM 单测。
 *
 * 最重要的是「两张矩阵交叉校验」：生成矩阵编码出的码字必须满足稀疏校验矩阵的
 * 全部 83 个方程。任何一行常量抄错，这条断言都会失败。
 */
class Ft8CodecTest {

    @Test
    fun `LDPC 常量表完整且两张矩阵互相一致`() {
        assertEquals(83, Ft8Constants.GENERATOR.size)
        assertEquals(83, Ft8Constants.CHECKS.size)
        assertEquals(174, Ft8Constants.BIT_CHECKS.size)
        assertTrue("每个码字位应恰好参与 3 个校验方程",
            Ft8Constants.BIT_CHECKS.all { it.size == 3 })
        assertTrue(Ft8Constants.GENERATOR.all { it.size == 12 })

        val payload = Ft8Codec.pack77("CQ BA4IGG OM89")!!
        val codeword = Ft8Codec.ldpcEncode(Ft8Codec.addCrc(payload))
        assertEquals("生成矩阵与稀疏校验矩阵不一致，常量数据有误",
            0, Ft8Codec.ldpcCheck(codeword))
    }

    @Test
    fun `77bit 消息打包解包往返`() {
        val messages = listOf(
            "CQ BA4IGG OM89",
            "BA4IGG K1ABC -07",
            "BA4IGG K1ABC R+05",
            "BA4IGG K1ABC RRR",
            "BA4IGG K1ABC RR73",
            "BA4IGG K1ABC 73",
            "CQ 123 BA4IGG OM89",
            "CQ DX BA4IGG",
            "QRZ BA4IGG OM89",
            "HELLO WORLD?"
        )
        for (message in messages) {
            val payload = Ft8Codec.pack77(message)
            assertNotNull("$message 打包失败", payload)
            assertEquals(message, Ft8Codec.unpack77(payload!!))
        }
    }

    @Test
    fun `CRC14 与 LDPC 纠错`() {
        val payload = Ft8Codec.pack77("BA4IGG K1ABC -07")!!
        val a91 = Ft8Codec.addCrc(payload)
        assertTrue("CRC 自检失败", Ft8Codec.checkCrc(a91))

        val codeword = Ft8Codec.ldpcEncode(a91)
        assertEquals(0, Ft8Codec.ldpcCheck(codeword))

        // 构造软判决 LLR：正确位给 ±6，人为翻转 3 个码字位，BP 应能纠正回来
        val llr = DoubleArray(174) { i -> if (codeword[i]) -6.0 else 6.0 }
        for (bit in intArrayOf(5, 60, 170)) llr[bit] = -llr[bit]
        val recovered = Ft8Codec.ldpcDecode(llr)
        assertNotNull("BP 译码未能纠正 3 位错误", recovered)
        assertArrayEquals(codeword, recovered!!)
    }

    @Test
    fun `帧结构是 S7 D29 S7 D29 S7`() {
        val tones = Ft8Codec.encodeTones("CQ BA4IGG OM89")!!
        assertEquals(79, tones.size)
        assertArrayEquals(Ft8Constants.COSTAS, tones.copyOfRange(0, 7))
        assertArrayEquals(Ft8Constants.COSTAS, tones.copyOfRange(36, 43))
        assertArrayEquals(Ft8Constants.COSTAS, tones.copyOfRange(72, 79))
        assertTrue(tones.all { it in 0..7 })
        assertEquals(58, tones.size - 21)
    }

    @Test
    fun `编码音频能被本机解码器还原`() {
        val message = "CQ BA4IGG OM89"
        val pcm = Ft8Codec.encode(message)
        assertNotNull(pcm)
        // 12.64 秒信号 + 尾部静音，模拟一个完整的 15 秒接收窗口
        val window = ShortArray(15 * Ft8Codec.SAMPLE_RATE)
        pcm!!.copyInto(window)

        val decoded = Ft8Codec.decode(window, Ft8Codec.SAMPLE_RATE)
        assertTrue(
            "解码结果里应包含原始消息，实际为 $decoded",
            decoded.any { it.text == message }
        )
    }

    @Test
    fun `不支持的消息明确报错而不是猜测`() {
        // 超过 13 字符且不是合法 QSO 结构时返回 null，交由界面提示
        assertEquals(null, Ft8Codec.pack77("THIS MESSAGE IS WAY TOO LONG"))
        assertEquals(null, Ft8Codec.pack77(""))
    }
}

package com.ham.tools

import com.ham.tools.util.AprsPasscode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * APRS-IS 验证码算法的本地单元测试。
 *
 * 标杆值不来自本项目的实现，而是取自公开的 APRS-IS passcode 生成器文档：
 * - N0CALL = 13023（多个生成器与 CLI 文档一致）
 * - 9M2PJU = 12970（公开手算示例的最终结果；其列出的中间值不可靠，见下方注释）
 */
class AprsPasscodeTest {

    @Test
    fun known_reference_values_match_public_generators() {
        assertEquals(13023, AprsPasscode.calculate("N0CALL")!!.passcode)
        assertEquals(12970, AprsPasscode.calculate("9M2PJU")!!.passcode)
    }

    @Test
    fun steps_record_each_pair_and_are_consistent_with_final_value() {
        // 注意：网上某些「手算示例」列出的中间哈希（42863 / 55583 / 36586）与其最终结果
        // 12970 自相矛盾（0x73E2 ^ (57<<8) 实为 0x4AE2 而非 42914），不能当作标杆。
        // 这里用另一种独立写法（把两字符打包成一个 16 位字再异或）复核逐对记账。
        val base = "9M2PJU"
        var independent = 0x73E2
        val expectedAfter = mutableListOf<Int>()
        var i = 0
        while (i < base.length) {
            val word = (base[i].code shl 8) or (base.getOrNull(i + 1)?.code ?: 0)
            independent = independent xor word
            expectedAfter += independent
            i += 2
        }

        val result = AprsPasscode.calculate(base)!!
        assertEquals(3, result.steps.size)
        assertEquals(expectedAfter, result.steps.map { it.hashAfter })
        assertEquals(listOf(Pair('9', 'M'), Pair('2', 'P'), Pair('J', 'U')),
            result.steps.map { Pair(it.first, it.second) })
        assertEquals(12970, result.passcode)
        assertEquals(independent and 0x7FFF, result.passcode)
        assertEquals(0x73E2, result.steps.first().hashBefore)
    }

    @Test
    fun case_ssid_and_slash_suffix_do_not_change_passcode() {
        val expected = 13023
        listOf("N0CALL", "n0call", " n0call ", "N0CALL-9", "N0CALL-15", "N0CALL/P", "n0call-7/p")
            .forEach { input ->
                assertEquals("输入 $input", expected, AprsPasscode.calculate(input)!!.passcode)
            }
    }

    @Test
    fun ssid_is_kept_in_login_callsign_but_not_in_hashed_base() {
        val result = AprsPasscode.calculate("BA4IGG-9")!!
        assertEquals("BA4IGG", result.baseCallsign)
        assertEquals("BA4IGG-9", result.loginCallsign)
        assertEquals(AprsPasscode.calculate("BA4IGG")!!.passcode, result.passcode)
        assertEquals("user BA4IGG-9 pass ${result.passcode} vers HAM-Tools 1.2",
            AprsPasscode.loginLine(result))
    }

    @Test
    fun odd_length_pads_missing_low_byte_with_zero() {
        // 独立手算："ABC" = ((0x73E2 ^ ('A'<<8) ^ 'B') ^ ('C'<<8) ^ 0) & 0x7FFF
        val manually = (((0x73E2 xor ('A'.code shl 8) xor 'B'.code)
            xor ('C'.code shl 8)) xor 0) and 0x7FFF
        val result = AprsPasscode.calculate("ABC")!!
        assertEquals(29088, manually)
        assertEquals(manually, result.passcode)
        assertEquals(2, result.steps.size)
        assertEquals('C', result.steps.last().first)
        assertNull(result.steps.last().second)
    }

    @Test
    fun result_is_always_a_non_negative_15bit_integer() {
        listOf("A", "N0CALL", "9M2PJU", "W1AW", "BA4IGG", "G3ABC", "JA1ABC", "ABCDEFGHIJ")
            .forEach { input ->
                val code = AprsPasscode.calculate(input)!!.passcode
                assertTrue("输入 $input 得到 $code", code in 0..32767)
            }
    }

    @Test
    fun invalid_inputs_are_rejected_instead_of_silently_filtered() {
        listOf(null, "", "   ", "-9", "/P", "N0CALL@", "中文", "CALL SIGN!!", "ABCDEFGHIJK")
            .forEach { input ->
                assertNull("输入 $input 应判为非法", AprsPasscode.calculate(input))
            }
    }

    @Test
    fun normalize_strips_suffixes_and_uppercases() {
        assertEquals("BA4IGG", AprsPasscode.normalize(" ba4igg-9 "))
        assertEquals("BA4IGG", AprsPasscode.normalize("ba4igg/p"))
        assertEquals("N0CALL", AprsPasscode.normalize("N0CALL-15/M"))
        assertNull(AprsPasscode.normalize("-9"))
    }

    @Test
    fun constants_match_the_published_algorithm() {
        assertEquals(29666, AprsPasscode.SEED)
        assertEquals(32767, AprsPasscode.MASK)
        assertEquals(10, AprsPasscode.MAX_BASE_LENGTH)
    }
}

package com.ham.tools.util

/**
 * APRS-IS 登录验证码（passcode / callpass）计算。
 *
 * APRS-IS 互联网网关在客户端登录时要求提交一个由呼号算出的 15 位十进制数，
 * 用来校验「登录者确实持有该呼号」。通行算法（源自 Bob Bruninga WB4APR）：
 *
 * 1. 取呼号基础部分：去掉 -SSID 与 /后缀，去掉空白，转大写；
 * 2. 哈希初值 `0x73E2`（29666，"73" 是业余无线电里的「致意」简语）；
 * 3. 每两个字符一组：`hash ^= first.code shl 8`，再 `hash ^= second.code`；
 *    奇数长度时末组缺少的低字节按 0 处理（等价于不异或）；
 * 4. `hash & 0x7FFF` 得到 0..32767 的验证码。
 *
 * **这不是密码，也不是加密**：算法完全公开，任何人对任何呼号都能算出同一个值，
 * 它只用于校验登录呼号，不具备身份保密或防冒充能力。请只计算自己持有执照的呼号。
 *
 * 本工具为纯本地计算，不联网、不上传、不保存输入的呼号；不涉及地图或位置数据。
 * 纯 Kotlin，无 Android 依赖，便于 JVM 单元测试。
 *
 * 标杆值来自多个公开的 APRS-IS passcode 生成器与 CLI 文档：
 * `N0CALL = 13023`、`9M2PJU = 12970`。
 * 注意：网上流传的 9M2PJU「手算过程」中间值与最终结果自相矛盾，只采用其最终结果作为标杆。
 */
object AprsPasscode {

    /** 哈希初值 0x73E2 = 29666。 */
    const val SEED = 0x73E2

    /** 结果掩码 0x7FFF，保证输出为 15 位非负整数。 */
    const val MASK = 0x7FFF

    /** 参与计算的基础呼号最大长度（超出视为非法输入，不静默截断）。 */
    const val MAX_BASE_LENGTH = 10

    private val BASE_PATTERN = Regex("^[A-Z0-9]{1,$MAX_BASE_LENGTH}$")
    private val SSID_PATTERN = Regex("^\\d{1,2}$")
    private val WHITESPACE = Regex("\\s+")

    /** 一组异或运算的中间结果，用于界面展示计算过程。 */
    data class Step(
        val index: Int,
        val first: Char,
        val second: Char?,
        val hashBefore: Int,
        val hashAfter: Int
    )

    data class Result(
        /** 去掉 SSID / 斜杠后缀、转大写后真正参与运算的呼号。 */
        val baseCallsign: String,
        /** 登录 APRS-IS 时写在 `user` 后面的完整呼号（保留数字 SSID）。 */
        val loginCallsign: String,
        val passcode: Int,
        val steps: List<Step>
    )

    /**
     * 规范化呼号：去空白、转大写、剥离 `-SSID` 与 `/后缀`，只保留字母数字。
     * 空串、含非法字符或基础部分超过 [MAX_BASE_LENGTH] 位时返回 null。
     */
    fun normalize(raw: String?): String? {
        if (raw == null) return null
        val value = raw.trim().replace(WHITESPACE, "").uppercase()
        if (value.isEmpty()) return null
        val base = value.substringBefore('-').substringBefore('/')
        return if (BASE_PATTERN.matches(base)) base else null
    }

    /** 计算验证码；输入不合法时返回 null。 */
    fun calculate(raw: String?): Result? {
        if (raw == null) return null
        val base = normalize(raw) ?: return null
        val value = raw.trim().replace(WHITESPACE, "").uppercase()
        val ssid = value.substringAfter('-', "").substringBefore('/')
            .takeIf { SSID_PATTERN.matches(it) }
        val loginCallsign = base + (ssid?.let { "-$it" } ?: "")

        var hash = SEED
        val steps = mutableListOf<Step>()
        var i = 0
        while (i < base.length) {
            val first = base[i]
            val second = base.getOrNull(i + 1)
            val before = hash
            hash = hash xor (first.code shl 8)
            hash = hash xor (second?.code ?: 0)
            steps += Step(index = steps.size + 1, first, second, before, hash)
            i += 2
        }
        return Result(
            baseCallsign = base,
            loginCallsign = loginCallsign,
            passcode = hash and MASK,
            steps = steps
        )
    }

    /** APRS-IS 登录行示例；`pass` 恒为基础呼号算出的验证码，与 SSID 无关。 */
    fun loginLine(result: Result, software: String = "HAM-Tools", version: String = "1.2"): String =
        "user ${result.loginCallsign} pass ${result.passcode} vers $software $version"
}

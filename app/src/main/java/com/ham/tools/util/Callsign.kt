package com.ham.tools.util

/**
 * 中国业余电台呼号校验与分区换算。
 *
 * 规则依据工信部《业余无线电台呼号管理办法》：
 * 呼号 = B + 电台种类字母 + 分区数字 + 1~4 位后缀（字母或字母数字组合），
 * 其中 QOA~QUZ 与 SOS / XXX / TTT / BPV / BPM 不作后缀。
 * 纯函数，无 Android 依赖，便于单元测试，并与服务端 src/callsign.js 保持一致。
 */
object Callsign {

    private val PATTERN = Regex("^B[A-Z]\\d[A-Z0-9]{1,4}$")
    private val RESERVED_SUFFIX = Regex("^Q[O-U][A-Z0-9]$")
    private val FORBIDDEN_SUFFIX = setOf("SOS", "XXX", "TTT", "BPV", "BPM")

    /** 业余电台分区表，下标即呼号第 3 位的数字。 */
    private val REGIONS = arrayOf(
        "新疆、西藏",
        "北京",
        "黑龙江、吉林、辽宁",
        "天津、内蒙古、河北、山西",
        "上海、山东、江苏",
        "浙江、江西、福建",
        "河南、湖北、安徽",
        "湖南、广东、广西、海南",
        "四川、重庆、贵州、云南",
        "陕西、甘肃、宁夏、青海"
    )

    /** 规范化：去空白、转大写；格式非法返回 null。 */
    fun normalize(raw: String?): String? {
        if (raw == null) return null
        val value = raw.trim().uppercase().replace(Regex("\\s+"), "")
        if (!PATTERN.matches(value)) return null
        val suffix = value.substring(3)
        if (RESERVED_SUFFIX.matches(suffix) || suffix in FORBIDDEN_SUFFIX) return null
        return value
    }

    /** 是否为合法呼号。 */
    fun isValid(raw: String?): Boolean = normalize(raw) != null

    /** 呼号所属分区地区名，非法呼号返回 null。 */
    fun regionOf(raw: String?): String? {
        val value = normalize(raw) ?: return null
        val digit = value[2].digitToIntOrNull() ?: return null
        return REGIONS.getOrNull(digit)
    }
}

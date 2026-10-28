package com.ham.tools.util

import java.util.Locale

/** 统一的数值格式化，避免 NaN / 无穷大打进界面。 */
object Format {

    fun number(value: Double, digits: Int = 3): String = when {
        value.isNaN() -> PLACEHOLDER
        value.isInfinite() -> INFINITY
        else -> String.format(Locale.US, "%.${digits}f", value)
    }

    fun integer(value: Double): String = when {
        value.isNaN() || value.isInfinite() -> PLACEHOLDER
        else -> String.format(Locale.US, "%,.0f", value)
    }

    /** 自动在小数值上提升精度，适合跨度极大的功率/电平结果。 */
    fun auto(value: Double): String = when {
        value.isNaN() -> PLACEHOLDER
        value.isInfinite() -> INFINITY
        else -> {
            val magnitude = kotlin.math.abs(value)
            val digits = when {
                magnitude == 0.0 -> 2
                magnitude < 0.001 -> 6
                magnitude < 0.1 -> 5
                magnitude < 10.0 -> 4
                magnitude < 1000.0 -> 3
                magnitude < 1_000_000.0 -> 2
                else -> 0
            }
            String.format(Locale.US, "%.${digits}f", value)
        }
    }

    /** 把用户输入清洗成合法数值字符串（允许小数点和负号）。 */
    fun sanitizeNumeric(input: String): String {
        var seenDot = false
        val builder = StringBuilder()
        for (char in input) {
            when {
                char.isDigit() -> builder.append(char)
                char == '.' || char == ',' -> if (!seenDot) {
                    builder.append('.')
                    seenDot = true
                }
                char == '-' && builder.isEmpty() -> builder.append('-')
            }
        }
        return builder.toString()
    }

    const val PLACEHOLDER = "—"
    const val INFINITY = "∞"
}

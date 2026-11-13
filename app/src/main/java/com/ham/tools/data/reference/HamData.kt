package com.ham.tools.data.reference

import com.ham.tools.util.Callsign

/** 太阳/地磁活动快照。source 标明数据出处，UI 必须如实展示，避免把内置值当实测值。 */
data class SolarSnapshot(
    val sfi: Int,
    val kp: Double,
    val aIndex: Int,
    val sunspots: Int,
    val source: String,
    val updatedAt: String = "—"
)

enum class BandQuality(val label: String) { GOOD("良好"), FAIR("一般"), POOR("较差") }

data class BandForecast(val band: String, val quality: BandQuality, val advice: String)

/**
 * HF 波段的经验传播评估。
 *
 * 不是电离层物理模型，而是把两条公认趋势量化，供野外快速判断：
 * - 太阳通量（SFI）越高，F 层电离越充分，高波段（15/10m）越容易开通；
 * - Kp 越大代表地磁扰动越强，所有波段变差，且高波段衰减更快。
 *
 * 结果只作参考，真实开通还取决于昼夜、季节、路径与天线。
 */
object SolarModel {

    val FALLBACK = SolarSnapshot(
        sfi = 120, kp = 2.0, aIndex = 8, sunspots = 60,
        source = "内置典型值（非实时观测）"
    )

    private val BANDS = arrayOf("160m", "80m", "40m", "20m", "15m", "10m")
    private val SFI_FACTOR = doubleArrayOf(-0.15, -0.08, 0.08, 0.35, 0.70, 1.00)
    private val KP_PENALTY = doubleArrayOf(4.0, 6.0, 8.0, 11.0, 15.0, 19.0)

    fun forecast(sfi: Int, kp: Double): List<BandForecast> {
        val disturbance = (kp - 1.5).coerceAtLeast(0.0)
        return BANDS.mapIndexed { i, band ->
            val score = 55.0 + (sfi - 100) * SFI_FACTOR[i] - disturbance * KP_PENALTY[i]
            val quality = when {
                score >= 60.0 -> BandQuality.GOOD
                score >= 34.0 -> BandQuality.FAIR
                else -> BandQuality.POOR
            }
            val advice = when (i) {
                0, 1 -> "夜间为主，白天 D 层吸收强"
                2 -> "昼夜过渡波段，近距离与远距离都可用"
                3 -> "DX 主力波段，白天通常最稳定"
                4, 5 -> "依赖太阳通量，太阳活动低年开通机会少"
                else -> ""
            }
            BandForecast(band, quality, advice)
        }
    }

    /** Kp 的文字描述，阈值按 NOAA G 级地磁暴划分简化。 */
    fun kpDescription(kp: Double): String = when {
        kp < 3.0 -> "平静"
        kp < 4.0 -> "轻度扰动"
        kp < 5.0 -> "活跃（G0）"
        kp < 6.0 -> "小磁暴（G1）"
        kp < 7.0 -> "中等磁暴（G2）"
        kp < 8.0 -> "强磁暴（G3）"
        else -> "强烈磁暴（G4+）"
    }
}

/** 一条 DX spot。内置数据为演示格式的样例，不是实时通联信息。 */
data class DxSpot(
    val time: String,
    val dxCallsign: String,
    val freqKHz: Double,
    val spotter: String,
    val comment: String
) {
    val bandLabel: String
        get() = when {
            freqKHz < 2000 -> "160m"
            freqKHz < 4000 -> "80m"
            freqKHz < 8000 -> "40m"
            freqKHz < 11000 -> "30m"
            freqKHz < 15000 -> "20m"
            freqKHz < 19000 -> "17m"
            freqKHz < 22000 -> "15m"
            freqKHz < 26000 -> "12m"
            else -> "10m"
        }
}

object DxSpotTable {
    val spots: List<DxSpot> = listOf(
        DxSpot("08:12", "EXAMPLE-1", 14085.0, "DEMO-2", "FT8 · 示例数据"),
        DxSpot("08:24", "EXAMPLE-3", 21074.0, "DEMO-4", "FT8 · 示例数据"),
        DxSpot("08:47", "EXAMPLE-5", 7032.0, "DEMO-6", "CW · 示例数据"),
        DxSpot("09:05", "EXAMPLE-7", 28495.0, "DEMO-8", "SSB · 示例数据"),
        DxSpot("09:31", "EXAMPLE-9", 14205.0, "DEMO-1", "SSB · 示例数据"),
        DxSpot("09:58", "EXAMPLE-2", 18130.0, "DEMO-3", "CW · 示例数据"),
        DxSpot("10:16", "EXAMPLE-4", 24910.0, "DEMO-5", "FT8 · 示例数据"),
        DxSpot("10:42", "EXAMPLE-6", 3555.0, "DEMO-7", "CW · 示例数据"),
        DxSpot("11:03", "EXAMPLE-8", 10136.0, "DEMO-9", "FT8 · 示例数据"),
        DxSpot("11:27", "EXAMPLE-1", 21225.0, "DEMO-2", "SSB · 示例数据"),
        DxSpot("11:50", "EXAMPLE-3", 7074.0, "DEMO-4", "FT8 · 示例数据"),
        DxSpot("12:14", "EXAMPLE-5", 14260.0, "DEMO-6", "SSB · 示例数据")
    )
}

/** 呼号解析结果。 */
data class CallsignProfile(
    val input: String,
    val normalized: String,
    val isValid: Boolean,
    val entity: String,
    val entityEn: String,
    val continent: String,
    val cqZone: Int?,
    val ituZone: Int?,
    val suffix: String?,
    val suffixMeaning: String?,
    val chinaRegion: String?,
    val prefixNote: String?
)

object CallsignLookup {

    private val SUFFIX_MEANINGS = mapOf(
        "P" to "便携 / 临时台",
        "M" to "陆地移动台",
        "MM" to "海上移动台",
        "AM" to "航空移动台",
        "QRP" to "小功率操作（通常 ≤5W）",
        "PORTABLE" to "便携台",
        "MOBILE" to "移动台"
    )

    private val CONTINENT_NAMES = mapOf(
        "AS" to "亚洲", "EU" to "欧洲", "AF" to "非洲", "NA" to "北美洲",
        "SA" to "南美洲", "OC" to "大洋洲", "AN" to "南极洲"
    )

    /** 解析呼号：先按最长前缀匹配 DXCC 实体，再解析斜杠后缀与中国分区。 */
    fun parse(raw: String): CallsignProfile {
        val input = raw.trim()
        val normalized = input.uppercase().replace(Regex("\\s+"), "")
        val parts = normalized.split("/")
        val base = parts.firstOrNull().orEmpty()
        val suffix = parts.getOrNull(1)
        val entry = PrefixTable.lookup(normalized)
        val chinaRegion = Callsign.regionOf(base)

        return CallsignProfile(
            input = input,
            normalized = normalized,
            isValid = base.isNotEmpty() && entry != null,
            entity = entry?.entity ?: "未识别",
            entityEn = entry?.entityEn ?: "—",
            continent = CONTINENT_NAMES[entry?.continent] ?: entry?.continent ?: "—",
            cqZone = entry?.cqZone,
            ituZone = entry?.ituZone,
            suffix = suffix,
            suffixMeaning = suffix?.let { SUFFIX_MEANINGS[it] },
            chinaRegion = chinaRegion,
            prefixNote = entry?.note
        )
    }
}

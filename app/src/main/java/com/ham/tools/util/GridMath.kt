package com.ham.tools.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 经纬度点，纬度在前。 */
data class GeoPoint(val lat: Double, val lon: Double)

/** 两点间的大圆路径结果。 */
data class PathResult(
    val distanceKm: Double,
    /** 从起点看向终点的初始方位角，正北为 0°，顺时针。 */
    val bearingDeg: Double,
    /** 从终点回看起点的初始方位角，按大圆重新计算，不是简单 +180°。 */
    val backBearingDeg: Double
)

/**
 * 梅登海德网格（Maidenhead Locator）与大圆计算。
 *
 * 纯函数、无 Android 依赖。网格层级：
 * - 2 字符：字段（Field），经度 20° x 纬度 10°，A-R
 * - 4 字符：方格里（Square），经度 2° x 纬度 1°，0-9
 * - 6 字符：子方格（Subsquare），经度 5' x 纬度 2.5'，A-X
 * - 8 字符：扩展方格，再各分 10 份，0-9
 *
 * 顺序是「经度字符、纬度字符」交替，例如 PM01 表示东经 120-122°、北纬 30-31°。
 * decode 返回的是该格子的**中心点**，这样同级网格之间可复现地互算距离。
 */
object GridMath {

    private const val EARTH_RADIUS_KM = 6371.0088

    private val COMPASS = arrayOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")

    /** 生成网格，level 为 1~4（对应 2/4/6/8 字符）。 */
    fun encode(lat: Double, lon: Double, level: Int = 3): String {
        val lv = level.coerceIn(1, 4)
        var x = (lon + 180.0).coerceIn(0.0, 359.999999)
        var y = (lat + 90.0).coerceIn(0.0, 179.999999)
        val sb = StringBuilder()

        val fieldX = (x / 20.0).toInt().coerceIn(0, 17)
        val fieldY = (y / 10.0).toInt().coerceIn(0, 17)
        sb.append(fieldChar(fieldX)).append(fieldChar(fieldY))
        x -= fieldX * 20.0
        y -= fieldY * 10.0

        if (lv >= 2) {
            val squareX = (x / 2.0).toInt().coerceIn(0, 9)
            val squareY = y.toInt().coerceIn(0, 9)
            sb.append(squareX).append(squareY)
            x -= squareX * 2.0
            y -= squareY
        }
        if (lv >= 3) {
            val subX = (x / (2.0 / 24.0)).toInt().coerceIn(0, 23)
            val subY = (y / (1.0 / 24.0)).toInt().coerceIn(0, 23)
            sb.append(subChar(subX)).append(subChar(subY))
            x -= subX * (2.0 / 24.0)
            y -= subY * (1.0 / 24.0)
        }
        if (lv >= 4) {
            val extX = (x / (2.0 / 24.0 / 10.0)).toInt().coerceIn(0, 9)
            val extY = (y / (1.0 / 24.0 / 10.0)).toInt().coerceIn(0, 9)
            sb.append(extX).append(extY)
        }
        return sb.toString()
    }

    /** 解析网格，返回格子中心点；格式非法返回 null。大小写不敏感。 */
    fun decode(grid: String): GeoPoint? {
        val g = grid.trim()
        if (g.length < 2 || g.length > 8 || g.length % 2 != 0) return null

        val fieldX = letterIndex(g[0], 18) ?: return null
        val fieldY = letterIndex(g[1], 18) ?: return null
        var lon = -180.0 + fieldX * 20.0
        var lat = -90.0 + fieldY * 10.0
        var lonSpan = 20.0
        var latSpan = 10.0

        if (g.length >= 4) {
            val sx = g[2].digitToIntOrNull() ?: return null
            val sy = g[3].digitToIntOrNull() ?: return null
            lon += sx * 2.0
            lat += sy * 1.0
            lonSpan = 2.0
            latSpan = 1.0
        }
        if (g.length >= 6) {
            val ux = letterIndex(g[4], 24) ?: return null
            val uy = letterIndex(g[5], 24) ?: return null
            lon += ux * (lonSpan / 24.0)
            lat += uy * (latSpan / 24.0)
            lonSpan /= 24.0
            latSpan /= 24.0
        }
        if (g.length >= 8) {
            val ex = g[6].digitToIntOrNull() ?: return null
            val ey = g[7].digitToIntOrNull() ?: return null
            lon += ex * (lonSpan / 10.0)
            lat += ey * (latSpan / 10.0)
            lonSpan /= 10.0
            latSpan /= 10.0
        }
        return GeoPoint(lat + latSpan / 2.0, lon + lonSpan / 2.0)
    }

    /** Haversine 大圆距离，单位 km。 */
    fun distanceKm(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(to.lon - from.lon)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2.0 * EARTH_RADIUS_KM * atan2(sqrt(a), sqrt(1.0 - a))
    }

    /** 初始大圆方位角（度，正北 0°，顺时针）。 */
    fun bearingDeg(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLon = Math.toRadians(to.lon - from.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun path(from: GeoPoint, to: GeoPoint): PathResult = PathResult(
        distanceKm = distanceKm(from, to),
        bearingDeg = bearingDeg(from, to),
        backBearingDeg = bearingDeg(to, from)
    )

    /** 八方位中文名。 */
    fun compass(deg: Double): String =
        COMPASS[((((deg % 360.0) + 360.0) % 360.0 + 22.5) / 45.0).toInt() % 8]

    private fun fieldChar(index: Int): Char = ('A'.code + index).toChar()
    private fun subChar(index: Int): Char = ('a'.code + index).toChar()

    private fun letterIndex(c: Char, count: Int): Int? {
        val v = c.uppercaseChar().code - 'A'.code
        return if (v in 0 until count) v else null
    }
}

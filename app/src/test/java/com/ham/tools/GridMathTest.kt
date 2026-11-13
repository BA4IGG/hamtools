package com.ham.tools

import com.ham.tools.util.GeoPoint
import com.ham.tools.util.GridMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridMathTest {

    @Test
    fun known_cities_encode_to_expected_squares() {
        assertEquals("OM89", GridMath.encode(39.90, 116.40, 2))
        assertEquals("PM01", GridMath.encode(31.23, 121.47, 2))
        assertEquals("JJ00", GridMath.encode(0.0, 0.0, 2))
    }

    @Test
    fun encode_decode_roundtrip_stays_inside_cell() {
        val points = listOf(
            GeoPoint(39.90, 116.40),
            GeoPoint(31.23, 121.47),
            GeoPoint(-33.86, 151.21),
            GeoPoint(64.10, -21.90),
            GeoPoint(0.0, 0.0)
        )
        for (point in points) {
            for (level in 1..4) {
                val grid = GridMath.encode(point.lat, point.lon, level)
                assertEquals(level * 2, grid.length)
                val center = GridMath.decode(grid)
                assertNotNull("$grid 无法解析", center)
                val deviation = GridMath.distanceKm(point, center!!)
                val tolerance = when (level) {
                    1 -> 1600.0
                    2 -> 180.0
                    3 -> 12.0
                    else -> 2.0
                }
                assertTrue("$grid 中心偏差 $deviation km 超过 $tolerance", deviation < tolerance)
            }
        }
    }

    @Test
    fun decode_is_case_insensitive_and_validates_input() {
        val upper = GridMath.decode("OM89")
        val lower = GridMath.decode("om89")
        assertEquals(upper, lower)
        assertNull(GridMath.decode("Z9"))
        assertNull(GridMath.decode("OM8"))
        assertNull(GridMath.decode(""))
        assertNull(GridMath.decode("OM8Z"))
    }

    @Test
    fun distance_and_bearing_match_geodesic_landmarks() {
        val origin = GeoPoint(0.0, 0.0)
        assertEquals(111.19, GridMath.distanceKm(origin, GeoPoint(0.0, 1.0)), 0.5)
        assertEquals(90.0, GridMath.bearingDeg(origin, GeoPoint(0.0, 1.0)), 0.1)
        assertEquals(0.0, GridMath.bearingDeg(origin, GeoPoint(1.0, 0.0)), 0.1)
        assertEquals(270.0, GridMath.bearingDeg(origin, GeoPoint(0.0, -1.0)), 0.1)
        val path = GridMath.path(GeoPoint(39.90, 116.40), GeoPoint(31.23, 121.47))
        assertTrue(path.distanceKm > 1000.0)
        assertTrue(path.bearingDeg in 0.0..360.0)
        assertTrue(path.backBearingDeg in 0.0..360.0)
    }

    @Test
    fun compass_directions() {
        assertEquals("北", GridMath.compass(0.0))
        assertEquals("东", GridMath.compass(90.0))
        assertEquals("南", GridMath.compass(180.0))
        assertEquals("西", GridMath.compass(270.0))
        assertEquals("北", GridMath.compass(360.0))
    }
}

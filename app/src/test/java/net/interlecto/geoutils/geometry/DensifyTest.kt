package net.interlecto.geoutils.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DensifyTest {

    @Test
    fun stepsAreAtMostOneDegree() {
        val pts = densify(listOf(GeoPoint(0.0, 0.0), GeoPoint(10.0, 0.0)), closed = false)
        assertEquals(11, pts.size)
        for (i in 1 until pts.size) {
            assertTrue(pts[i - 1].angleTo(pts[i]) <= DEFAULT_MAX_STEP_RAD + 1e-12)
        }
    }

    @Test
    fun interpolatedPointsFollowTheGreatCircle() {
        // New York to Madrid, both near 40° N: the great circle bulges north.
        val pts = densify(listOf(GeoPoint(-74.0, 40.7), GeoPoint(-3.7, 40.4)), closed = false)
        val maxLat = pts.maxOf { it.toGeoPoint().lat }
        // Vertex latitude: atan(tan 40.5° / cos 35°) ≈ 46.2°.
        assertEquals(46.2, maxLat, 0.2)
    }

    @Test
    fun closedRingDropsRepeatedPointAndDensifiesClosingEdge() {
        val ring = listOf(GeoPoint(0.0, 0.0), GeoPoint(2.0, 0.0), GeoPoint(2.0, 2.0), GeoPoint(0.0, 0.0))
        val pts = densify(ring, closed = true)
        assertTrue(pts.first() != pts.last())
        assertTrue(pts.last().angleTo(pts.first()) <= DEFAULT_MAX_STEP_RAD + 1e-12)
    }
}

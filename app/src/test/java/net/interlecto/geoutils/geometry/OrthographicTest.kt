package net.interlecto.geoutils.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

class OrthographicTest {

    private val eps = 1e-9

    @Test
    fun centerProjectsToOrigin() {
        val p = Orthographic(GeoPoint(-74.0, 4.6)).forward(GeoPoint(-74.0, 4.6))!!
        assertEquals(0.0, p.x, eps)
        assertEquals(0.0, p.y, eps)
    }

    @Test
    fun cardinalPointsFromNullIsland() {
        val ortho = Orthographic(GeoPoint(0.0, 0.0))
        assertVec(Vec2(1.0, 0.0), ortho.forward(GeoPoint(90.0, 0.0))!!)
        assertVec(Vec2(-1.0, 0.0), ortho.forward(GeoPoint(-90.0, 0.0))!!)
        assertVec(Vec2(0.0, 1.0), ortho.forward(GeoPoint(0.0, 90.0))!!)
        assertVec(Vec2(sqrt(0.5), 0.0), ortho.forward(GeoPoint(45.0, 0.0))!!)
    }

    @Test
    fun northPoleCenterLooksDownOnParallels() {
        val ortho = Orthographic(GeoPoint(0.0, 90.0))
        // Every point on the 60th parallel is at distance cos(60°) from the center.
        for (lon in -180 until 180 step 15) {
            val p = ortho.forward(GeoPoint(lon.toDouble(), 60.0))!!
            assertEquals(0.5, hypot(p.x, p.y), eps)
        }
        // Greenwich points straight down.
        assertVec(Vec2(0.0, -0.5), ortho.forward(GeoPoint(0.0, 60.0))!!)
    }

    @Test
    fun farSideIsHidden() {
        val ortho = Orthographic(GeoPoint(10.0, 20.0))
        assertNull(ortho.forward(GeoPoint(-170.0, -20.0)))
        assertNull(ortho.forward(GeoPoint(110.0, -60.0)))
    }

    @Test
    fun inverseOutsideDiskFails() {
        assertFalse(Orthographic(GeoPoint(0.0, 0.0)).inverse(0.8, 0.8, DoubleArray(2)))
    }

    @Test
    fun forwardThenInverseRoundTrips() {
        val random = Random(42)
        repeat(20) {
            val center = GeoPoint(random.nextDouble(-180.0, 180.0), random.nextDouble(-90.0, 90.0))
            val ortho = Orthographic(center)
            repeat(200) {
                val g = GeoPoint(random.nextDouble(-180.0, 180.0), random.nextDouble(-89.0, 89.0))
                val p = ortho.forward(g) ?: return@repeat
                val back = ortho.inverse(p)!!
                assertEquals(g.lat, back.lat, 1e-6)
                assertEquals(0.0, angleDiff(g.lon, back.lon), 1e-6)
            }
        }
    }

    @Test
    fun lineCrossingHorizonIsCutOnTheCircle() {
        val ortho = Orthographic(GeoPoint(0.0, 0.0))
        // Along the equator from 60° E to 150° E: visible up to 90° E.
        val pieces = ortho.projectLine(listOf(GeoPoint(60.0, 0.0), GeoPoint(150.0, 0.0)))
        assertEquals(1, pieces.size)
        val end = pieces[0].last()
        assertEquals(1.0, end.x, 1e-9)
        assertEquals(0.0, end.y, 1e-9)
    }

    @Test
    fun lineLeavingAndReturningGivesTwoPieces() {
        val ortho = Orthographic(GeoPoint(0.0, 0.0))
        val pieces = ortho.projectLine(
            listOf(GeoPoint(45.0, 10.0), GeoPoint(135.0, 10.0), GeoPoint(-135.0, 10.0), GeoPoint(-45.0, 10.0)),
        )
        assertEquals(2, pieces.size)
    }

    @Test
    fun hiddenLineGivesNothing() {
        val ortho = Orthographic(GeoPoint(0.0, 0.0))
        assertTrue(ortho.projectLine(listOf(GeoPoint(120.0, 0.0), GeoPoint(170.0, 10.0))).isEmpty())
    }

    @Test
    fun visibleRingIsDensifiedButUnclipped() {
        val ortho = Orthographic(GeoPoint(0.0, 0.0))
        val ring = square(-10.0, -10.0, 10.0, 10.0)
        val projected = ortho.projectRing(ring)!!
        assertTrue(projected.size > 4)
        assertTrue(projected.all { hypot(it.x, it.y) < 1.0 })
    }

    @Test
    fun hiddenRingIsDropped() {
        val ortho = Orthographic(GeoPoint(0.0, 0.0))
        assertNull(ortho.projectRing(square(150.0, -10.0, 170.0, 10.0)))
    }

    @Test
    fun ringCrossingHorizonFollowsTheShortArc() {
        val ortho = Orthographic(GeoPoint(0.0, 0.0))
        // Straddles the eastern horizon (90° E) between 20° S and 20° N.
        val projected = ortho.projectRing(square(60.0, -20.0, 120.0, 20.0))!!
        assertTrue(projected.all { hypot(it.x, it.y) <= 1.0 + 1e-9 })
        val onHorizon = projected.filter { abs(hypot(it.x, it.y) - 1.0) < 1e-9 }
        assertTrue(onHorizon.size >= 2)
        // The horizon part stays on the east side. The 20° edges are great circles,
        // which bend poleward to atan(tan 20° / cos 30°) ≈ 22.8° at the horizon.
        for (p in onHorizon) {
            assertTrue(p.x > 0.9)
            assertTrue(abs(Math.toDegrees(atan2(p.y, p.x))) <= 22.8)
        }
    }

    @Test
    fun polarCapSeenFromTheSideIsClosedAlongTheHorizon() {
        // An Arctic cap above 60° N seen from the equator: the hidden part
        // behind the pole must be replaced by the top of the horizon.
        val ortho = Orthographic(GeoPoint(0.0, 0.0))
        val cap = (-180 until 180 step 10).map { GeoPoint(it.toDouble(), 60.0) }
        val projected = ortho.projectRing(cap)!!
        assertNotNull(projected.find { abs(it.x) < 0.02 && it.y > 0.999 })
        assertTrue(projected.all { it.y >= 0.5 - 1e-9 })
    }

    private fun square(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) = listOf(
        GeoPoint(minLon, minLat),
        GeoPoint(maxLon, minLat),
        GeoPoint(maxLon, maxLat),
        GeoPoint(minLon, maxLat),
        GeoPoint(minLon, minLat),
    )

    private fun assertVec(expected: Vec2, actual: Vec2) {
        assertEquals(expected.x, actual.x, eps)
        assertEquals(expected.y, actual.y, eps)
    }

    private fun angleDiff(a: Double, b: Double): Double = GeoPoint.normalizeLongitude(a - b + 0.0).let {
        if (abs(it) > 180 - 1e-9) 0.0 else it
    }
}

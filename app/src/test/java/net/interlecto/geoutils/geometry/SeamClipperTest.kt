package net.interlecto.geoutils.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class SeamClipperTest {

    private val plate = Equirectangular()
    private val top = PI / 2
    private val eps = 1e-6

    private fun box(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) = listOf(
        GeoPoint(minLon, minLat), GeoPoint(maxLon, minLat), GeoPoint(maxLon, maxLat), GeoPoint(minLon, maxLat),
        GeoPoint(minLon, minLat),
    )

    @Test
    fun ringAwayFromTheSeamIsKeptWhole() {
        val rings = plate.projectRing(box(-10.0, -10.0, 10.0, 10.0))
        assertEquals(1, rings.size)
        assertTrue(rings[0].all { abs(it.x) <= Math.toRadians(10.0) + eps })
    }

    @Test
    fun ringAcrossTheAntimeridianSplitsInTwo() {
        // A box from 170° E to 170° W (crossing 180°).
        val rings = plate.projectRing(box(170.0, -10.0, -170.0, 10.0))
        assertEquals(2, rings.size)
        val (east, west) = rings.partition { r -> r.all { it.x > 0 } }
        assertEquals(1, east.size)
        assertEquals(1, west.size)
        assertTrue(east[0].any { abs(it.x - PI) < eps })
        assertTrue(west[0].any { abs(it.x + PI) < eps })
    }

    @Test
    fun ringOrientationDoesNotMatter() {
        val forward = plate.projectRing(box(170.0, -10.0, -170.0, 10.0))
        val backward = plate.projectRing(box(170.0, -10.0, -170.0, 10.0).reversed())
        assertEquals(forward.map { r -> r.maxOf { it.y } }.sorted(), backward.map { r -> r.maxOf { it.y } }.sorted())
        // The ±10° edges are great circles, bulging poleward to about 10.15°.
        assertTrue(backward.all { r -> r.all { abs(it.y) <= Math.toRadians(10.2) } })
    }

    @Test
    fun arcticCapIsClosedAlongTheTopEdge() {
        val cap = (-180 until 180 step 10).map { GeoPoint(it.toDouble(), 60.0) } + GeoPoint(-180.0, 60.0)
        val ring = plate.projectRing(cap).single()
        assertTrue(ring.any { abs(it.y - top) < eps })
        assertTrue(ring.none { it.y < Math.toRadians(60.0) - 1e-3 })
    }

    @Test
    fun antarcticaLikeRingThroughThePoleCoversTheBottom() {
        // Natural Earth style: the coast, then down the antimeridian to the
        // pole and back up, with ±180 both normalized to -180.
        val coast = (-180..180 step 10).map { GeoPoint(it.toDouble(), -70.0) }
        val ring = coast + listOf(GeoPoint(180.0, -90.0), GeoPoint(-180.0, -90.0), GeoPoint(-180.0, -70.0))
        val projected = plate.projectRing(ring)
        assertEquals(1, projected.size)
        val r = projected[0]
        assertTrue(r.any { abs(it.y + top) < eps })
        assertTrue(r.none { it.y > Math.toRadians(-70.0) + 1e-3 })
    }

    @Test
    fun lineAcrossTheAntimeridianSplits() {
        val pieces = plate.projectLine(listOf(GeoPoint(170.0, 0.0), GeoPoint(-170.0, 0.0)))
        assertEquals(2, pieces.size)
        assertEquals(PI, pieces[0].last().x, eps)
        assertEquals(-PI, pieces[1].first().x, eps)
    }

    @Test
    fun goodeCutsRingsAtItsInterruptions() {
        val goode = ProjectionCatalog.GOODE.create(GeoPoint(0.0, 0.0))
        // Straddles the southern interruption at 20° W.
        val rings = goode.projectRing(box(-30.0, -50.0, -10.0, -20.0))
        assertEquals(2, rings.size)
        // One piece in each lobe: their x ranges don't overlap.
        val (a, b) = rings.sortedBy { r -> r.minOf { it.x } }
        assertTrue(a.maxOf { it.x } < b.minOf { it.x })
    }

    @Test
    fun outlinesAreClosedLoopsInsideTheBounds() {
        for (type in ProjectionCatalog.all) {
            val projection = type.create(GeoPoint(0.0, 0.0))
            val outline = projection.outline()
            assertTrue(type.id, outline.size > 4)
            val b = projection.bounds
            assertTrue(type.id, outline.all { it.x >= b.minX - eps && it.x <= b.maxX + eps })
            assertTrue(type.id, outline.all { it.y >= b.minY - eps && it.y <= b.maxY + eps })
        }
    }
}

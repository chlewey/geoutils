package net.interlecto.geoutils.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class HemisphericTest {

    private fun fwd(p: Projection, lon: Double, lat: Double) = p.forward(GeoPoint(lon, lat))!!

    private fun nicolosi(lambda: Double, phi: Double): Vec2 {
        val out = DoubleArray(2)
        NicolosiNormal.forward(lambda, phi, out)
        return Vec2(out[0], out[1])
    }

    @Test
    fun hemispheresStopAtTheRim() {
        for (type in listOf(
            ProjectionCatalog.ORTHOGRAPHIC,
            ProjectionCatalog.AZIMUTHAL_EQUIDISTANT_HEMI,
            ProjectionCatalog.LAMBERT_AZIMUTHAL_HEMI,
            ProjectionCatalog.MOLLWEIDE_HEMI,
            ProjectionCatalog.NICOLOSI,
        )) {
            val map = type.create(GeoPoint(20.0, 30.0))
            val radius = map.bounds.maxX
            assertNull(type.id, map.forward(GeoPoint(-160.0, -30.0))) // The antipode.
            // A point on the rim, 90° from the center, is on the edge of the disk.
            val rim = fwd(map, 20.0, -60.0)
            assertEquals(type.id, radius, hypot(rim.x, rim.y), 1e-9)
        }
        assertEquals(PI / 2, ProjectionCatalog.AZIMUTHAL_EQUIDISTANT_HEMI.create(GeoPoint(0.0, 0.0)).bounds.maxX, 1e-12)
        assertEquals(sqrt(2.0), ProjectionCatalog.MOLLWEIDE_HEMI.create(GeoPoint(0.0, 0.0)).bounds.maxX, 1e-12)
    }

    @Test
    fun nicolosiAxesAndRimAreEvenlyDivided() {
        for (deg in listOf(-89.0, -60.0, -15.0, 0.1, 30.0, 75.0)) {
            val a = Math.toRadians(deg)
            assertEquals(a, nicolosi(a, 0.0).x, 1e-12) // Equator.
            assertEquals(0.0, nicolosi(a, 0.0).y, 1e-12)
            assertEquals(0.0, nicolosi(0.0, a).x, 1e-12) // Central meridian.
            assertEquals(a, nicolosi(0.0, a).y, 1e-9)
            val rim = nicolosi(PI / 2, a) // Rim: evenly divided in latitude.
            assertEquals(PI / 2 * cos(a), rim.x, 1e-9)
            assertEquals(PI / 2 * sin(a), rim.y, 1e-9)
        }
    }

    @Test
    fun nicolosiMeridiansAndParallelsAreCircularArcs() {
        val p2 = PI * PI / 4
        for (lonDeg in listOf(-80.0, -45.0, 10.0, 60.0)) {
            for (latDeg in listOf(-70.0, -20.0, 5.0, 50.0, 85.0)) {
                val l = Math.toRadians(lonDeg)
                val f = Math.toRadians(latDeg)
                val p = nicolosi(l, f)
                // Meridian: the circle through both poles and (λ, 0).
                val h = (l * l - p2) / (2 * l)
                assertEquals(hypot(l - h, 0.0), hypot(p.x - h, p.y), 1e-9)
                // Parallel: the circle through (0, φ) and the rim point at φ.
                val k = (p2 - f * f) / (2 * (PI / 2 * sin(f) - f))
                assertEquals(hypot(0.0, f - k), hypot(p.x, p.y - k), 1e-9)
            }
        }
        // Close to the straight axes the arcs flatten out without blowing up.
        val near = nicolosi(1e-9, 0.5)
        assertEquals(0.0, near.x, 1e-8)
        assertEquals(0.5, near.y, 1e-8)
    }

    @Test
    fun splitLayoutTouchesAtTheTangentPoint() {
        val map = ProjectionCatalog.DOUBLE_ORTHOGRAPHIC.create(GeoPoint(-30.0, 0.0))
        assertEquals(4.0, map.bounds.width, 1e-12)
        assertEquals(2.0, map.bounds.height, 1e-12)
        val tangent = fwd(map, -30.0, 0.0)
        assertEquals(0.0, tangent.x, 1e-9)
        assertEquals(0.0, tangent.y, 1e-9)
        // Disk centers 90° east and west of the split, north up.
        val east = fwd(map, 60.0, 0.0)
        assertEquals(1.0, east.x, 1e-9)
        assertEquals(0.0, east.y, 1e-9)
        val west = fwd(map, -120.0, 0.0)
        assertEquals(-1.0, west.x, 1e-9)
        assertTrue(fwd(map, 60.0, 45.0).y > 0)
        // The split meridian is the shared rim: x = 0 only where the disks touch.
        assertFalse(map.inverse(0.0, 0.5, DoubleArray(2)))
    }

    @Test
    fun polarLayoutPutsThePolesAtTheCenters() {
        val map = ProjectionCatalog.DOUBLE_ORTHOGRAPHIC.create(GeoPoint(-30.0, 0.0), polar = true)
        val north = fwd(map, 0.0, 90.0)
        assertEquals(-1.0, north.x, 1e-9)
        assertEquals(0.0, north.y, 1e-9)
        assertEquals(1.0, fwd(map, 0.0, -90.0).x, 1e-9)
        // The shared meridian runs through both centers.
        assertEquals(0.0, fwd(map, -30.0, 45.0).y, 1e-9)
        assertEquals(0.0, fwd(map, -30.0, -45.0).y, 1e-9)

        // Azimuth turns the map clockwise: at 90° the north disk is on top.
        val turned = ProjectionCatalog.DOUBLE_ORTHOGRAPHIC.create(GeoPoint(-30.0, 0.0), azimuthDeg = 90.0, polar = true)
        val top = fwd(turned, 0.0, 90.0)
        assertEquals(0.0, top.x, 1e-9)
        assertEquals(1.0, top.y, 1e-9)
        assertEquals(2.0, turned.bounds.width, 1e-9)
        assertEquals(4.0, turned.bounds.height, 1e-9)
    }

    @Test
    fun doubleHemispheresRoundTrip() {
        val random = Random(11)
        val types = ProjectionCatalog.all.filter { it.family == ProjectionFamily.DOUBLE_HEMISPHERIC }
        for (type in types) {
            for (polar in listOf(false, true)) {
                repeat(5) {
                    val map = type.create(
                        GeoPoint(random.nextDouble(-180.0, 180.0), random.nextDouble(-60.0, 60.0)),
                        random.nextDouble(-180.0, 180.0),
                        polar = polar,
                    )
                    repeat(200) {
                        val g = GeoPoint(random.nextDouble(-180.0, 180.0), random.nextDouble(-89.0, 89.0))
                        val p = map.forward(g)
                        assertTrue("${type.id} lost $g", p != null) // Two hemispheres cover the sphere.
                        val back = map.inverse(p!!)!!
                        assertTrue("${type.id}: $g came back as $back", g.toVec3().angleTo(back.toVec3()) < 1e-6)
                    }
                }
            }
        }
    }
}

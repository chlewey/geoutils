package net.interlecto.geoutils.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ConicTest {

    private fun fwd(p: NormalProjection, lonDeg: Double, latDeg: Double): Vec2 {
        val out = DoubleArray(2)
        p.forward(Math.toRadians(lonDeg), Math.toRadians(latDeg), out)
        return Vec2(out[0], out[1])
    }

    /** Snyder's examples put the origin at (λ0, φ0); here the apex is at the origin. */
    private fun fromOrigin(p: NormalProjection, lonDeg: Double, latDeg: Double, lat0Deg: Double): Vec2 {
        val q = fwd(p, lonDeg, latDeg)
        val o = fwd(p, 0.0, lat0Deg)
        return Vec2(q.x - o.x, q.y - o.y)
    }

    @Test
    fun albersMatchesSnyder() {
        // Snyder, Map Projections — A Working Manual, p. 292: φ1 = 29.5°, φ2 = 45.5°,
        // φ0 = 23°, λ0 = 96° W; the point 35° N, 75° W on the unit sphere.
        val p = fromOrigin(AlbersNormal(29.5, 45.5), 21.0, 35.0, 23.0)
        assertEquals(0.2952720, p.x, 1e-6)
        assertEquals(0.2416774, p.y, 1e-6)
    }

    @Test
    fun lambertConformalConicMatchesSnyder() {
        // Snyder p. 295: φ1 = 33°, φ2 = 45°, φ0 = 23°, λ0 = 96° W; 35° N, 75° W.
        val p = fromOrigin(LambertConformalConicNormal(33.0, 45.0), 21.0, 35.0, 23.0)
        assertEquals(0.2966785, p.x, 1e-6)
        assertEquals(0.2462112, p.y, 1e-6)
    }

    @Test
    fun equidistantConicKeepsMeridianDistances() {
        val p = EquidistantConicNormal(20.0, 60.0)
        for (lon in listOf(0.0, 75.0, -150.0)) {
            val a = fwd(p, lon, -40.0)
            val b = fwd(p, lon, 70.0)
            assertEquals(Math.toRadians(110.0), hypot(a.x - b.x, a.y - b.y), 1e-12)
        }
    }

    @Test
    fun parallelsAreArcsAroundTheApex() {
        for (p in listOf(AlbersNormal(20.0, 50.0), LambertConformalConicNormal(30.0, 60.0), EquidistantConicNormal(-50.0, -20.0))) {
            val r0 = fwd(p, 0.0, 10.0).let { hypot(it.x, it.y) }
            for (lon in listOf(-170.0, -60.0, 45.0, 179.0)) {
                assertEquals(r0, fwd(p, lon, 10.0).let { hypot(it.x, it.y) }, 1e-12)
            }
        }
    }

    @Test
    fun theGapBetweenTheCutsIsOffTheMap() {
        val albers = AlbersNormal(20.0, 50.0)
        // Straight down from the apex is the central meridian; straight up is the gap.
        val out = DoubleArray(2)
        assertTrue(albers.inverse(0.0, -1.5, out))
        assertEquals(0.0, out[0], 1e-12)
        assertFalse(albers.inverse(0.0, 1.5, out))
    }

    @Test
    fun southernConesWork() {
        val p = LambertConformalConicNormal(-60.0, -30.0)
        val out = DoubleArray(2)
        val q = fwd(p, 40.0, -45.0)
        assertTrue(p.inverse(q.x, q.y, out))
        assertEquals(Math.toRadians(40.0), out[0], 1e-9)
        assertEquals(Math.toRadians(-45.0), out[1], 1e-9)
        // The north is clamped at the limit instead of running off to infinity.
        assertTrue(hypot(fwd(p, 0.0, 89.0).x, fwd(p, 0.0, 89.0).y) < 10.0)
    }

    @Test
    fun opposedParallelsDoNotDegenerate() {
        val p = AlbersNormal(-30.0, 30.0)
        val b = p.bounds
        assertTrue(b.width.isFinite() && b.height.isFinite())
        assertTrue(b.width < 200 && b.height < 200)
    }
}

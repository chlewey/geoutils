package net.interlecto.geoutils.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class GeodesyTest {

    private val london = GeoPoint(-0.1278, 51.5074)
    private val newYork = GeoPoint(-74.006, 40.7128)

    @Test
    fun greatCircleLondonToNewYork() {
        // About 5570 km on the mean-radius sphere.
        assertEquals(5570.0, Geodesy.greatCircleDistance(london, newYork) * Sphere.EARTH_RADIUS_KM, 5.0)
    }

    @Test
    fun rhumbLineIsNeverShorterThanTheGreatCircle() {
        val gc = Geodesy.greatCircleDistance(london, newYork)
        val rhumb = Geodesy.rhumbDistance(london, newYork)
        assertTrue(rhumb > gc)
        // About 5794 km.
        assertEquals(5794.0, rhumb * Sphere.EARTH_RADIUS_KM, 10.0)
    }

    @Test
    fun alongAParallelTheRhumbLineIsThatParallel() {
        val a = GeoPoint(0.0, 60.0)
        val b = GeoPoint(30.0, 60.0)
        assertEquals(Math.toRadians(30.0) * cos(Math.toRadians(60.0)), Geodesy.rhumbDistance(a, b), 1e-12)
        assertTrue(Geodesy.rhumbPath(a, b).all { kotlin.math.abs(it.lat - 60.0) < 1e-9 })
        assertEquals(90.0, Geodesy.rhumbBearing(a, b), 1e-9)
    }

    @Test
    fun alongAMeridianBothRoutesAgree() {
        val a = GeoPoint(10.0, -20.0)
        val b = GeoPoint(10.0, 50.0)
        assertEquals(Geodesy.greatCircleDistance(a, b), Geodesy.rhumbDistance(a, b), 1e-12)
    }

    @Test
    fun rhumbLineTakesTheShortWayAroundTheAntimeridian() {
        val d = Geodesy.rhumbDistance(GeoPoint(170.0, 0.0), GeoPoint(-170.0, 0.0))
        assertEquals(Math.toRadians(20.0), d, 1e-12)
    }

    @Test
    fun rhumbPathIsStraightOnMercator() {
        val mercator = ProjectionCatalog.MERCATOR.create(GeoPoint(0.0, 0.0))
        val points = Geodesy.rhumbPath(london, newYork).map { mercator.forward(it)!! }
        val first = points.first()
        val last = points.last()
        for (p in points) {
            val cross = (last.x - first.x) * (p.y - first.y) - (last.y - first.y) * (p.x - first.x)
            assertEquals(0.0, cross, 1e-9)
        }
    }

    @Test
    fun initialBearingDueEastOnTheEquator() {
        assertEquals(90.0, Geodesy.initialBearing(GeoPoint(0.0, 0.0), GeoPoint(30.0, 0.0)), 1e-9)
    }

    @Test
    fun equatorThroughTwoPointsPutsThemOnTheMiddleLine() {
        val (center, azimuth) = Geodesy.equatorThrough(london, newYork)!!
        val map = ProjectionCatalog.MERCATOR.create(center, azimuth)
        val a = map.forward(london)!!
        val b = map.forward(newYork)!!
        assertEquals(0.0, a.y, 1e-9)
        assertEquals(0.0, b.y, 1e-9)
        assertTrue(b.x > a.x) // The second point is to the right.
        assertEquals(-b.x, a.x, 1e-9) // Centered between them.
    }

    @Test
    fun equatorThroughIsUndefinedForEqualOrAntipodalPoints() {
        assertNull(Geodesy.equatorThrough(london, london))
        assertNull(Geodesy.equatorThrough(GeoPoint(0.0, 0.0), GeoPoint(180.0, 0.0)))
    }
}

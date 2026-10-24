package net.interlecto.geoutils.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoodeVariantsTest {

    /** A rough Greenland outline, spanning both sides of 40° W. */
    private val greenland = listOf(
        GeoPoint(-73.0, 78.0), GeoPoint(-60.0, 82.0), GeoPoint(-30.0, 83.5), GeoPoint(-12.0, 81.5),
        GeoPoint(-20.0, 72.0), GeoPoint(-25.0, 68.0), GeoPoint(-38.0, 65.5), GeoPoint(-43.5, 60.0),
        GeoPoint(-50.0, 63.0), GeoPoint(-54.0, 68.0), GeoPoint(-58.0, 75.0), GeoPoint(-73.0, 78.0),
    )

    private fun goode(greenland: String, lon: Double = 0.0, azimuth: Double = 0.0) =
        ProjectionCatalog.GOODE.create(GeoPoint(lon, 0.0), azimuth, mapOf("greenland" to greenland))

    @Test
    fun dividedSplitsGreenlandAtFortyWest() {
        assertEquals(2, goode("divided").projectRing(greenland).size)
    }

    @Test
    fun extendedKeepsGreenlandWhole() {
        assertEquals(1, goode("extended").projectRing(greenland).size)
    }

    @Test
    fun bothShowsItSplitAndWhole() {
        val rings = goode("both").projectRing(greenland)
        // Two halves in their usual lobes, plus the eastern half again next to the western one.
        assertEquals(3, rings.size)
    }

    @Test
    fun extendedMovesTheEastOfGreenlandToTheAmericanLobe() {
        val p = GeoPoint(-25.0, 72.0)
        val divided = goode("divided").forward(p)!!
        val extended = goode("extended").forward(p)!!
        // In the American (western) lobe it lands further left.
        assertTrue(extended.x < divided.x)
    }

    @Test
    fun extendedLeavesWestAfricaAndIrelandAlone() {
        for (p in listOf(GeoPoint(-17.0, 14.7), GeoPoint(-10.0, 53.0))) {
            assertEquals(goode("divided").forward(p)!!.x, goode("extended").forward(p)!!.x, 1e-12)
        }
    }

    @Test
    fun bothRoundTripsInTheRepeatedRegion() {
        val map = goode("both")
        val extended = goode("extended").forward(GeoPoint(-25.0, 72.0))!!
        // That spot is only on the map in the "both" layout's extra part.
        val back = map.inverse(extended)
        assertNotNull(back)
        assertEquals(-25.0, back!!.lon, 1e-6)
        assertEquals(72.0, back.lat, 1e-6)
    }

    @Test
    fun centralMeridianShiftsTheWholeMap() {
        val shifted = goode("divided", lon = 30.0)
        assertEquals(0.0, shifted.forward(GeoPoint(30.0, 0.0))!!.x, 1e-12)
    }

    @Test
    fun lobesTravelWithTheirContinents() {
        // Every point moves by the same amount: the map slides, cuts and all.
        val plain = goode("divided")
        val shifted = goode("divided", lon = 30.0)
        for (p in listOf(GeoPoint(-45.0, 50.0), GeoPoint(-35.0, 50.0), GeoPoint(-25.0, -30.0), GeoPoint(100.0, 60.0))) {
            val a = plain.forward(p)!!
            val b = shifted.forward(p)!!
            assertEquals(a.x - Math.toRadians(30.0), b.x, 1e-12)
            assertEquals(a.y, b.y, 1e-12)
        }
        // The cuts stay on the same longitudes, so Greenland is still split
        // at 40° W into two pieces, not cut somewhere else.
        assertEquals(2, shifted.projectRing(greenland).size)
    }

    @Test
    fun aShapeAcrossTheMapEdgeIsSplitBetweenBothSides() {
        // Africa-sized box around 20° E; with the central meridian at 170° W
        // the map edge (10° E) runs through it.
        val box = listOf(
            GeoPoint(0.0, -10.0), GeoPoint(30.0, -10.0), GeoPoint(30.0, 10.0), GeoPoint(0.0, 10.0), GeoPoint(0.0, -10.0),
        )
        val rings = goode("divided", lon = -170.0).projectRing(box)
        assertEquals(2, rings.size)
        assertTrue(rings.any { r -> r.all { it.x > 0 } })
        assertTrue(rings.any { r -> r.all { it.x < 0 } })
    }

    @Test
    fun shiftedMapRoundTrips() {
        val map = goode("both", lon = 77.0)
        for (p in listOf(GeoPoint(-25.0, 72.0), GeoPoint(-100.0, 40.0), GeoPoint(140.0, -30.0), GeoPoint(-110.0, 5.0))) {
            val back = map.inverse(map.forward(p)!!)!!
            assertEquals(p.lon, back.lon, 1e-6)
            assertEquals(p.lat, back.lat, 1e-6)
        }
    }

    @Test
    fun southUpTurnsTheMapWithoutMovingLobes() {
        val north = goode("divided").forward(GeoPoint(-25.0, 50.0))!!
        val south = goode("divided", azimuth = 180.0).forward(GeoPoint(-25.0, 50.0))!!
        assertEquals(-north.x, south.x, 1e-12)
        assertEquals(-north.y, south.y, 1e-12)
    }

    @Test
    fun latitudeAndOtherAzimuthsAreIgnored() {
        val plain = goode("divided").forward(GeoPoint(-25.0, 50.0))!!
        val asked = ProjectionCatalog.GOODE.create(GeoPoint(0.0, 45.0), 37.0).forward(GeoPoint(-25.0, 50.0))!!
        assertEquals(plain, asked)
    }
}

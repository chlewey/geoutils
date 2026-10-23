package net.interlecto.geoutils.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.math.PI

class EquirectangularTest {

    private val projection = Equirectangular()

    @Test
    fun forwardIsLonLatInRadians() {
        val p = projection.forward(GeoPoint(90.0, -45.0))!!
        assertEquals(PI / 2, p.x, 1e-12)
        assertEquals(-PI / 4, p.y, 1e-12)
    }

    @Test
    fun inverseRoundTrips() {
        val g = projection.inverse(projection.forward(GeoPoint(-74.08, 4.6))!!)!!
        assertEquals(-74.08, g.lon, 1e-9)
        assertEquals(4.6, g.lat, 1e-9)
    }

    @Test
    fun inverseOutsideBoundsFails() {
        assertFalse(projection.inverse(4.0, 0.0, DoubleArray(2)))
    }

    @Test
    fun lineIsSplitAtTheAntimeridian() {
        val pieces = projection.projectLine(listOf(GeoPoint(170.0, 0.0), GeoPoint(-170.0, 0.0)))
        assertEquals(2, pieces.size)
    }
}

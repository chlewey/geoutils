package net.interlecto.geoutils.geometry

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoPointTest {

    @Test
    fun longitudeInRangeIsUnchanged() {
        assertEquals(-74.08, GeoPoint(-74.08, 4.6).lon, 1e-12)
    }

    @Test
    fun longitudeWrapsAround() {
        assertEquals(-170.0, GeoPoint(190.0, 0.0).lon, 1e-12)
        assertEquals(170.0, GeoPoint(-190.0, 0.0).lon, 1e-12)
        assertEquals(10.0, GeoPoint(730.0, 0.0).lon, 1e-12)
    }

    @Test
    fun antimeridianIsMinus180() {
        assertEquals(-180.0, GeoPoint(180.0, 0.0).lon, 0.0)
        assertEquals(-180.0, GeoPoint(-180.0, 0.0).lon, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun latitudeOutOfRangeIsRejected() {
        GeoPoint(0.0, 90.5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteLongitudeIsRejected() {
        GeoPoint(Double.NaN, 0.0)
    }
}

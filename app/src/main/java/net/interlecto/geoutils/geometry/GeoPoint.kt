package net.interlecto.geoutils.geometry

/**
 * A point on the sphere in degrees, in (longitude, latitude) order as in KML.
 *
 * Longitude is normalized to [-180, 180) and latitude must be in [-90, 90].
 * This package must not depend on Android so it can be tested on the JVM.
 */
class GeoPoint(lon: Double, val lat: Double) {

    val lon: Double = normalizeLongitude(lon)

    init {
        require(lat in -90.0..90.0) { "Latitude out of range: $lat" }
    }

    override fun equals(other: Any?): Boolean =
        other is GeoPoint && lon == other.lon && lat == other.lat

    override fun hashCode(): Int = 31 * lon.hashCode() + lat.hashCode()

    override fun toString(): String = "GeoPoint(lon=$lon, lat=$lat)"

    companion object {
        /** Wraps any longitude into [-180, 180). */
        fun normalizeLongitude(lon: Double): Double {
            require(lon.isFinite()) { "Longitude must be finite: $lon" }
            val wrapped = (lon + 180.0).mod(360.0) - 180.0
            return if (wrapped == 180.0) -180.0 else wrapped
        }
    }
}

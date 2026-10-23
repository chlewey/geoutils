package net.interlecto.geoutils.ui.map

import net.interlecto.geoutils.geometry.GeoPoint
import net.interlecto.geoutils.geometry.ProjectionFamily
import net.interlecto.geoutils.geometry.ProjectionType

/** How the azimuth slider works: off (fixed at 0), or ranging over -180..180 or 0..360. */
enum class AzimuthMode(val range: ClosedFloatingPointRange<Double>) {
    OFF(-180.0..180.0),
    SIGNED(-180.0..180.0),
    FULL(0.0..360.0);

    /** Brings [azimuth] into this mode's range; OFF always gives 0. */
    fun normalize(azimuth: Double): Double = when (this) {
        OFF -> 0.0
        SIGNED -> (azimuth + 180.0).mod(360.0) - 180.0
        FULL -> azimuth.mod(360.0)
    }
}

/**
 * Where one projection is aimed. Latitude applies only while
 * [latitudeEnabled] and the azimuth only while [azimuthMode] isn't OFF;
 * otherwise both are 0, which is the normal aspect for whole-sphere maps.
 */
data class ViewSettings(
    val longitude: Double,
    val latitude: Double = 0.0,
    val latitudeEnabled: Boolean = true,
    val azimuthMode: AzimuthMode = AzimuthMode.OFF,
    val azimuth: Double = 0.0,
) {
    val center: GeoPoint get() = GeoPoint(longitude, if (latitudeEnabled) latitude else 0.0)

    val effectiveAzimuth: Double get() = azimuthMode.normalize(azimuth)

    companion object {
        /** Used for azimuthal maps until the device location is known. */
        val FALLBACK_CENTER = GeoPoint(-30.0, 20.0)

        /**
         * Azimuthal maps start on the device location with latitude active;
         * cylindrical and pseudocylindrical ones start in normal aspect on
         * the projection's default longitude, with latitude and azimuth off.
         */
        fun defaultFor(type: ProjectionType, deviceLocation: GeoPoint?): ViewSettings =
            if (type.family == ProjectionFamily.AZIMUTHAL) {
                val center = deviceLocation ?: FALLBACK_CENTER
                ViewSettings(center.lon, center.lat, latitudeEnabled = true)
            } else {
                ViewSettings(type.defaultLongitude, 0.0, latitudeEnabled = false)
            }
    }
}

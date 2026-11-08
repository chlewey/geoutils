package net.interlecto.geoutils.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Routes and frames on the sphere. Distances are angles in radians; multiply by a radius for length. */
object Geodesy {

    /** Great-circle (shortest) distance between [a] and [b]. */
    fun greatCircleDistance(a: GeoPoint, b: GeoPoint): Double = a.toVec3().angleTo(b.toVec3())

    /** The great-circle route from [a] to [b], sampled every degree. */
    fun greatCirclePath(a: GeoPoint, b: GeoPoint): List<GeoPoint> =
        densify(listOf(a, b), closed = false).map { it.toGeoPoint() }

    /** Initial bearing of the great-circle route from [a] to [b], in degrees clockwise from north. */
    fun initialBearing(a: GeoPoint, b: GeoPoint): Double {
        val phi1 = Math.toRadians(a.lat)
        val phi2 = Math.toRadians(b.lat)
        val dLambda = Math.toRadians(b.lon - a.lon)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return Math.toDegrees(atan2(y, x)).mod(360.0)
    }

    /**
     * The point reached from [from] by going [distance] (an angle) along the
     * great circle that leaves it at [bearingDeg], clockwise from north.
     */
    fun destination(from: GeoPoint, distance: Double, bearingDeg: Double): GeoPoint {
        val lambda = Math.toRadians(from.lon)
        val phi = Math.toRadians(from.lat)
        val east = Vec3(-sin(lambda), cos(lambda), 0.0)
        val north = Vec3(-sin(phi) * cos(lambda), -sin(phi) * sin(lambda), cos(phi))
        val b = Math.toRadians(bearingDeg)
        val heading = north * cos(b) + east * sin(b)
        return (from.toVec3() * cos(distance) + heading * sin(distance)).toGeoPoint()
    }

    /**
     * The small circle of all points at [radius] (an angle) from [center], as
     * a closed ring sampled every [stepDeg] of bearing, counterclockwise seen
     * from outside the sphere (so the center is inside).
     */
    fun circle(center: GeoPoint, radius: Double, stepDeg: Double = 2.0): List<GeoPoint> {
        val n = maxOf(8, ceil(360.0 / stepDeg).toInt())
        return List(n) { i -> destination(center, radius, -360.0 * i / n) }
    }

    /**
     * Circles around [center] every [spacing] (an angle) up to, but not
     * including, the antipode.
     */
    fun rings(center: GeoPoint, spacing: Double): List<List<GeoPoint>> {
        require(spacing > 0)
        return generateSequence(spacing) { it + spacing }.takeWhile { it < PI - 1e-9 }
            .map { circle(center, it) }.toList()
    }

    /**
     * Great circles leaving [center] every [stepDeg] of bearing, from the
     * center to its antipode, sampled every 10°.
     */
    fun rays(center: GeoPoint, stepDeg: Double = 15.0): List<List<GeoPoint>> {
        val n = maxOf(1, ceil(360.0 / stepDeg).toInt())
        return List(n) { i ->
            val bearing = 360.0 * i / n
            List(19) { k -> destination(center, Math.toRadians(10.0 * k), bearing) }
        }
    }

    /**
     * Rhumb line (loxodrome) distance from [a] to [b]: the route at a
     * constant bearing, going the short way around in longitude.
     */
    fun rhumbDistance(a: GeoPoint, b: GeoPoint): Double {
        val (dPhi, dLambda, q) = rhumbTerms(a, b)
        return sqrt(dPhi * dPhi + q * q * dLambda * dLambda)
    }

    /** Constant bearing of the rhumb line from [a] to [b], in degrees clockwise from north. */
    fun rhumbBearing(a: GeoPoint, b: GeoPoint): Double {
        val dPsi = mercatorY(b.lat) - mercatorY(a.lat)
        val dLambda = shortLongitude(b.lon - a.lon)
        return Math.toDegrees(atan2(dLambda, dPsi)).mod(360.0)
    }

    /**
     * The rhumb line from [a] to [b], sampled at most every degree. It
     * crosses each meridian at the same angle, so it is a straight line on
     * a Mercator map.
     */
    fun rhumbPath(a: GeoPoint, b: GeoPoint): List<GeoPoint> {
        val dLambda = shortLongitude(b.lon - a.lon)
        val dLat = b.lat - a.lat
        val psi1 = mercatorY(a.lat)
        val dPsi = mercatorY(b.lat) - psi1
        val steps = maxOf(1, ceil(maxOf(abs(dLat), abs(Math.toDegrees(dLambda)))).toInt())
        return List(steps + 1) { i ->
            val f = i.toDouble() / steps
            val lat = a.lat + dLat * f
            // Longitude grows linearly with the Mercator y (isometric latitude).
            val g = if (abs(dPsi) < 1e-12) f else (mercatorY(lat) - psi1) / dPsi
            GeoPoint(a.lon + Math.toDegrees(dLambda) * g, lat)
        }
    }

    /**
     * Center and azimuth that make the great circle through [a] and [b] the
     * map's equator: the center is halfway between them, and [b] lies to the
     * right of [a]. Null when they coincide or are antipodal.
     */
    fun equatorThrough(a: GeoPoint, b: GeoPoint): Pair<GeoPoint, Double>? {
        val va = a.toVec3()
        val vb = b.toVec3()
        val pole = va cross vb
        val mid = va + vb
        if (pole.length() < 1e-9 || mid.length() < 1e-9) return null
        val up = pole.normalized()
        val center = mid.normalized().toGeoPoint()
        // Bearing of the "up" direction at the center.
        val lambda0 = Math.toRadians(center.lon)
        val phi0 = Math.toRadians(center.lat)
        val east = Vec3(-sin(lambda0), cos(lambda0), 0.0)
        val north = Vec3(-sin(phi0) * cos(lambda0), -sin(phi0) * sin(lambda0), cos(phi0))
        val azimuth = Math.toDegrees(atan2(up dot east, up dot north))
        return center to azimuth
    }

    private fun mercatorY(latDeg: Double): Double {
        val phi = Math.toRadians(latDeg.coerceIn(-89.999999, 89.999999))
        return ln(tan(PI / 4 + phi / 2))
    }

    /** Longitude difference in radians, wrapped into (-π, π]. */
    private fun shortLongitude(dLonDeg: Double): Double {
        val d = (dLonDeg + 180.0).mod(360.0) - 180.0
        return Math.toRadians(if (d == -180.0) 180.0 else d)
    }

    /** Δφ, Δλ (short way) and q = Δφ / Δψ, the stretched-parallel factor. */
    private fun rhumbTerms(a: GeoPoint, b: GeoPoint): Triple<Double, Double, Double> {
        val dPhi = Math.toRadians(b.lat - a.lat)
        val dLambda = shortLongitude(b.lon - a.lon)
        val dPsi = mercatorY(b.lat) - mercatorY(a.lat)
        val q = if (abs(dPsi) > 1e-12) dPhi / dPsi else cos(Math.toRadians(a.lat))
        return Triple(dPhi, dLambda, q)
    }
}

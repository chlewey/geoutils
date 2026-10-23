package net.interlecto.geoutils.geometry

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A point on a projected (planar) map. */
data class Vec2(val x: Double, val y: Double)

/**
 * A vector in Earth-centered 3D space. Points on the unit sphere use
 * x toward (0°, 0°), y toward (90° E, 0°) and z toward the North Pole.
 */
data class Vec3(val x: Double, val y: Double, val z: Double) {

    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = Vec3(x * k, y * k, z * k)

    infix fun dot(o: Vec3): Double = x * o.x + y * o.y + z * o.z

    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)

    fun length(): Double = sqrt(this dot this)

    fun normalized(): Vec3 = this * (1.0 / length())

    /** Angle in radians between two unit vectors, accurate for small and large angles. */
    fun angleTo(o: Vec3): Double = atan2((this cross o).length(), this dot o)

    fun toGeoPoint(): GeoPoint = GeoPoint(
        lon = Math.toDegrees(atan2(y, x)),
        lat = Math.toDegrees(asin(z.coerceIn(-1.0, 1.0))),
    )

    companion object {
        /**
         * Spherical linear interpolation between unit vectors [a] and [b];
         * the result stays on the great circle through them.
         */
        fun slerp(a: Vec3, b: Vec3, t: Double): Vec3 {
            val omega = a.angleTo(b)
            if (omega < 1e-12) return a
            val s = sin(omega)
            return a * (sin((1 - t) * omega) / s) + b * (sin(t * omega) / s)
        }
    }
}

fun GeoPoint.toVec3(): Vec3 {
    val lambda = Math.toRadians(lon)
    val phi = Math.toRadians(lat)
    val cosPhi = cos(phi)
    return Vec3(cosPhi * cos(lambda), cosPhi * sin(lambda), sin(phi))
}

/** Axis-aligned extent of a projected map, in projection units. */
data class Bounds(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    val width: Double get() = maxX - minX
    val height: Double get() = maxY - minY
    val centerX: Double get() = (minX + maxX) / 2
    val centerY: Double get() = (minY + maxY) / 2
}

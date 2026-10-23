package net.interlecto.geoutils.geometry

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Azimuthal projections in normal aspect, centered on (0°, 0°). With the
 * local unit vector q = (cos φ cos λ, cos φ sin λ, sin φ), the center is q =
 * (1, 0, 0) and the map is (k·q.y, k·q.z) for a radial scale k that depends
 * only on the angular distance c = acos(q.x) from the center.
 */

private inline fun azimuthalForward(lambda: Double, phi: Double, out: DoubleArray, k: (cosC: Double) -> Double) {
    val cosPhi = cos(phi)
    val qx = cosPhi * cos(lambda)
    val scale = k(qx.coerceIn(-1.0, 1.0))
    out[0] = scale * cosPhi * sin(lambda)
    out[1] = scale * sin(phi)
}

/** Writes (λ, φ) for a point at angular distance [c] from the center in the map direction ([x], [y]). */
private fun azimuthalInverse(x: Double, y: Double, c: Double, out: DoubleArray) {
    val rho = sqrt(x * x + y * y)
    val sinC = sin(c)
    val qx = cos(c)
    val qy = if (rho == 0.0) 0.0 else sinC * x / rho
    val qz = if (rho == 0.0) 0.0 else sinC * y / rho
    out[0] = atan2(qy, qx)
    out[1] = asin(qz.coerceIn(-1.0, 1.0))
}

/** The globe seen from infinitely far away: the facing hemisphere on the unit disk. */
object OrthographicNormal : NormalProjection {
    override val bounds = Bounds(-1.0, -1.0, 1.0, 1.0)
    override val clip = Clip.Cap(0.0)

    override fun forward(lambda: Double, phi: Double, out: DoubleArray) =
        azimuthalForward(lambda, phi, out) { 1.0 }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean {
        val rho2 = x * x + y * y
        if (rho2 > 1.0) return false
        azimuthalInverse(x, y, asin(sqrt(rho2)), out)
        return true
    }
}

/** Distances from the center are true: a point at angular distance c is at radius c. */
object AzimuthalEquidistantNormal : NormalProjection {
    override val bounds = Bounds(-PI, -PI, PI, PI)

    /** Everything but a 1° hole around the antipode, where the map wraps onto its rim. */
    override val clip = Clip.Cap(cos(Math.toRadians(179.0)))

    override fun forward(lambda: Double, phi: Double, out: DoubleArray) =
        azimuthalForward(lambda, phi, out) { cosC ->
            val c = acos(cosC)
            if (c < 1e-12) 1.0 else c / sin(c)
        }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean {
        val rho = sqrt(x * x + y * y)
        if (rho > PI) return false
        azimuthalInverse(x, y, rho, out)
        return true
    }
}

/** Lambert azimuthal equal-area: areas are true; the whole sphere fits in a disk of radius 2. */
object LambertAzimuthalEqualAreaNormal : NormalProjection {
    override val bounds = Bounds(-2.0, -2.0, 2.0, 2.0)
    override val clip = Clip.Cap(cos(Math.toRadians(179.0)))

    override fun forward(lambda: Double, phi: Double, out: DoubleArray) =
        azimuthalForward(lambda, phi, out) { cosC -> sqrt(2 / (1 + cosC)) }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean {
        val rho = sqrt(x * x + y * y)
        if (rho > 2.0) return false
        azimuthalInverse(x, y, 2 * asin(rho / 2), out)
        return true
    }
}

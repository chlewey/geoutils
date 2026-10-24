package net.interlecto.geoutils.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A conic projection in normal aspect, with standard parallels [phi1Deg] and
 * [phi2Deg] (equal for a tangent cone). Parallels are arcs of radius ρ(φ)
 * around the apex and meridians are rays from it at angle n·λ; the map is a
 * sector of an annulus, cut along the antimeridian. The apex is at the
 * origin, above the map for a cone over the north (n > 0).
 *
 * Standard parallels at equal distances on either side of the equator give
 * a cylinder (n = 0); n is kept at least [MIN_N] away from 0.
 */
abstract class ConicNormal(phi1Deg: Double, phi2Deg: Double) : NormalProjection {
    protected val phi1 = Math.toRadians(phi1Deg)
    protected val phi2 = Math.toRadians(phi2Deg)

    /** The cone constant: how much of a full turn the map's sector spans. */
    protected abstract val n: Double

    /** Radius of the parallel at [phi], signed like [n]. */
    protected abstract fun rho(phi: Double): Double

    /** Latitude of the parallel of radius [rho], or NaN if there is none. */
    protected abstract fun phiAt(rho: Double): Double

    /** Latitudes beyond these are drawn on the limit (for maps that would be infinite). */
    protected open val minPhi = -PI / 2
    protected open val maxPhi = PI / 2

    override val clip = Clip.Seams()

    /** Found by walking the map's edge: the pole lines and both sides of the cut. */
    override val bounds: Bounds by lazy {
        val out = DoubleArray(2)
        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        fun add(lambda: Double, phi: Double) {
            forward(lambda, phi, out)
            minX = minOf(minX, out[0])
            maxX = maxOf(maxX, out[0])
            minY = minOf(minY, out[1])
            maxY = maxOf(maxY, out[1])
        }
        val steps = 720
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            add(-PI + 2 * PI * t, minPhi)
            add(-PI + 2 * PI * t, maxPhi)
            add(-PI, minPhi + (maxPhi - minPhi) * t)
            add(PI, minPhi + (maxPhi - minPhi) * t)
        }
        Bounds(minX, minY, maxX, maxY)
    }

    override fun forward(lambda: Double, phi: Double, out: DoubleArray) {
        val r = rho(phi.coerceIn(minPhi, maxPhi))
        val theta = n * lambda
        out[0] = r * sin(theta)
        out[1] = -r * cos(theta)
    }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean {
        val s = n.sign
        val r = s * hypot(x, y)
        val lambda = atan2(s * x, -s * y) / n
        if (abs(lambda) > PI + 1e-12) return false
        val phi = phiAt(r)
        if (phi.isNaN() || phi < minPhi - 1e-12 || phi > maxPhi + 1e-12) return false
        out[0] = lambda.coerceIn(-PI, PI)
        out[1] = phi
        return true
    }

    /** Keeps [n] away from 0, where the cone opens into a cylinder. */
    protected fun limited(n: Double): Double = if (abs(n) >= MIN_N) n else if (n < 0) -MIN_N else MIN_N

    companion object {
        const val MIN_N = 0.05
    }
}

/** Albers equal-area conic: areas are true; scale is true along the standard parallels. */
class AlbersNormal(phi1Deg: Double, phi2Deg: Double) : ConicNormal(phi1Deg, phi2Deg) {
    override val n = limited((sin(phi1) + sin(phi2)) / 2)
    private val c = cos(phi1) * cos(phi1) + 2 * n * sin(phi1)

    override fun rho(phi: Double): Double = sqrt(maxOf(c - 2 * n * sin(phi), 0.0)) / n

    override fun phiAt(rho: Double): Double {
        val s = (c - rho * rho * n * n) / (2 * n)
        return if (abs(s) > 1 + 1e-12) Double.NaN else asin(s.coerceIn(-1.0, 1.0))
    }
}

/** Equidistant conic: distances along the meridians are true. */
class EquidistantConicNormal(phi1Deg: Double, phi2Deg: Double) : ConicNormal(phi1Deg, phi2Deg) {
    override val n = limited(
        if (abs(phi1 - phi2) < 1e-10) sin(phi1) else (cos(phi1) - cos(phi2)) / (phi2 - phi1),
    )
    private val g = cos(phi1) / n + phi1

    override fun rho(phi: Double): Double = g - phi

    override fun phiAt(rho: Double): Double = g - rho
}

/**
 * Lambert conformal conic: angles are true. The pole away from the cone is
 * infinitely far, so the map stops at [LIMIT_DEG] on that side.
 */
class LambertConformalConicNormal(phi1Deg: Double, phi2Deg: Double) : ConicNormal(phi1Deg, phi2Deg) {
    override val n = limited(
        if (abs(phi1 - phi2) < 1e-10) {
            sin(phi1)
        } else {
            ln(cos(phi1) / cos(phi2)) / ln(tan(PI / 4 + phi2 / 2) / tan(PI / 4 + phi1 / 2))
        },
    )
    private val f = cos(phi1) * tan(PI / 4 + phi1 / 2).pow(n) / n
    override val minPhi = if (n > 0) -Math.toRadians(LIMIT_DEG) else -PI / 2
    override val maxPhi = if (n > 0) PI / 2 else Math.toRadians(LIMIT_DEG)

    override fun rho(phi: Double): Double =
        if (abs(phi) >= PI / 2 - 1e-12 && phi.sign == n.sign) 0.0 else f / tan(PI / 4 + phi / 2).pow(n)

    override fun phiAt(rho: Double): Double = when {
        rho == 0.0 -> n.sign * PI / 2
        rho.sign != f.sign -> Double.NaN
        else -> 2 * atan((f / rho).pow(1 / n)) - PI / 2
    }

    companion object {
        const val LIMIT_DEG = 60.0
    }
}

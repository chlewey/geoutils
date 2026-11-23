package net.interlecto.geoutils.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.tan

/*
 * Cylindrical projections in normal aspect: meridians are equally spaced
 * vertical lines (x = λ·k) and parallels are horizontal lines. All are cut
 * at the antimeridian.
 */

private fun Bounds.contains(x: Double, y: Double) = x in minX..maxX && y in minY..maxY

/** x = λ cos φ₁, y = φ: true scale along the standard parallel φ₁. */
class EquirectangularNormal(standardParallelDeg: Double = 0.0) : NormalProjection {
    private val k = cos(Math.toRadians(standardParallelDeg))
    override val bounds = Bounds(-PI * k, -PI / 2, PI * k, PI / 2)
    override val clip = Clip.Seams()

    override fun forward(lambda: Double, phi: Double, out: DoubleArray) {
        out[0] = lambda * k
        out[1] = phi
    }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean {
        if (!bounds.contains(x, y)) return false
        out[0] = x / k
        out[1] = y
        return true
    }
}

/**
 * Conformal Mercator, cut at ±[maxLatDeg] (by default the web Mercator limit,
 * which makes the map square). On a sphere, web and spherical Mercator are
 * the same projection.
 */
class MercatorNormal(maxLatDeg: Double = WEB_MERCATOR_MAX_LAT) : NormalProjection {
    private val maxPhi = Math.toRadians(maxLatDeg)
    private val maxY = ln(tan(PI / 4 + maxPhi / 2))
    override val bounds = Bounds(-PI, -maxY, PI, maxY)
    override val clip = Clip.Seams()

    override fun forward(lambda: Double, phi: Double, out: DoubleArray) {
        out[0] = lambda
        out[1] = ln(tan(PI / 4 + phi.coerceIn(-maxPhi, maxPhi) / 2))
    }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean {
        if (!bounds.contains(x, y)) return false
        out[0] = x
        out[1] = 2 * atan(exp(y)) - PI / 2
        return true
    }

    companion object {
        const val WEB_MERCATOR_MAX_LAT = 85.0511287798066
    }
}

/**
 * Cylindrical equal-area with standard parallel φₛ: x = λ cos φₛ,
 * y = sin φ / cos φₛ. Lambert (0°), Behrmann (30°), Hobo–Dyer (37.5°) and
 * Gall–Peters (45°) differ only in φₛ.
 */
class CylindricalEqualAreaNormal(standardParallelDeg: Double) : NormalProjection {
    private val k = cos(Math.toRadians(standardParallelDeg))
    override val bounds = Bounds(-PI * k, -1 / k, PI * k, 1 / k)
    override val clip = Clip.Seams()

    override fun forward(lambda: Double, phi: Double, out: DoubleArray) {
        out[0] = lambda * k
        out[1] = sin(phi) / k
    }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean {
        if (!bounds.contains(x, y)) return false
        out[0] = x / k
        out[1] = asin((y * k).coerceIn(-1.0, 1.0))
        return true
    }
}

/**
 * Central cylindrical: the sphere projected from its center onto a tangent
 * cylinder, y = tan φ. Unbounded toward the poles, so cut at ±[maxLatDeg].
 */
class CentralCylindricalNormal(maxLatDeg: Double = 75.0) : NormalProjection {
    private val maxPhi = Math.toRadians(maxLatDeg)
    private val maxY = tan(maxPhi)
    override val bounds = Bounds(-PI, -maxY, PI, maxY)
    override val clip = Clip.Seams()

    override fun forward(lambda: Double, phi: Double, out: DoubleArray) {
        out[0] = lambda
        out[1] = tan(phi.coerceIn(-maxPhi, maxPhi))
    }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean {
        if (!bounds.contains(x, y) || abs(y) > maxY) return false
        out[0] = x
        out[1] = atan(y)
        return true
    }
}

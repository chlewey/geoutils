package net.interlecto.geoutils.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Pseudocylindrical projections in normal aspect: parallels are horizontal
 * lines, meridians curve toward the poles.
 */

private val SQRT2 = sqrt(2.0)

/** Sinusoidal (Sanson–Flamsteed): x = λ cos φ, y = φ. Equal-area. */
object SinusoidalNormal : NormalProjection {
    override val bounds = Bounds(-PI, -PI / 2, PI, PI / 2)
    override val clip = Clip.Seams()

    override fun forward(lambda: Double, phi: Double, out: DoubleArray) {
        out[0] = lambda * cos(phi)
        out[1] = phi
    }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean {
        if (abs(y) > PI / 2) return false
        val lambda = x / cos(y)
        if (abs(lambda) > PI) return false
        out[0] = lambda
        out[1] = y
        return true
    }
}

/** Mollweide's auxiliary angle θ: 2θ + sin 2θ = π sin φ, by Newton's method. */
internal fun mollweideTheta(phi: Double): Double {
    if (abs(phi) > PI / 2 - 1e-9) return PI / 2 * sign(phi)
    val target = PI * sin(phi)
    var theta = phi
    repeat(50) {
        val delta = (2 * theta + sin(2 * theta) - target) / (2 + 2 * cos(2 * theta))
        theta -= delta
        if (abs(delta) < 1e-12) return theta
    }
    return theta
}

/** Mollweide: equal-area, the whole sphere in a 2:1 ellipse. */
object MollweideNormal : NormalProjection {
    private val xScale = 2 * SQRT2 / PI
    override val bounds = Bounds(-2 * SQRT2, -SQRT2, 2 * SQRT2, SQRT2)
    override val clip = Clip.Seams()

    override fun forward(lambda: Double, phi: Double, out: DoubleArray) {
        val theta = mollweideTheta(phi)
        out[0] = xScale * lambda * cos(theta)
        out[1] = SQRT2 * sin(theta)
    }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean {
        if (abs(y) > SQRT2) return false
        val theta = asin(y / SQRT2)
        val cosTheta = cos(theta)
        val lambda = if (cosTheta < 1e-12) 0.0 else x / (xScale * cosTheta)
        if (abs(lambda) > PI) return false
        out[0] = lambda
        out[1] = asin(((2 * theta + sin(2 * theta)) / PI).coerceIn(-1.0, 1.0))
        return true
    }
}

/** Equal Earth (Šavrič, Patterson and Jenny, 2018): equal-area, with straight parallels. */
object EqualEarthNormal : NormalProjection {
    private const val A1 = 1.340264
    private const val A2 = -0.081106
    private const val A3 = 0.000893
    private const val A4 = 0.003796
    private val M = sqrt(3.0) / 2

    override val bounds: Bounds
    override val clip = Clip.Seams()

    init {
        val out = DoubleArray(2)
        forward(PI, 0.0, out)
        val maxX = out[0]
        forward(0.0, PI / 2, out)
        bounds = Bounds(-maxX, -out[1], maxX, out[1])
    }

    private fun y(theta: Double): Double {
        val t2 = theta * theta
        val t6 = t2 * t2 * t2
        return theta * (A1 + A2 * t2 + t6 * (A3 + A4 * t2))
    }

    private fun dy(theta: Double): Double {
        val t2 = theta * theta
        val t6 = t2 * t2 * t2
        return A1 + 3 * A2 * t2 + t6 * (7 * A3 + 9 * A4 * t2)
    }

    override fun forward(lambda: Double, phi: Double, out: DoubleArray) {
        val theta = asin(M * sin(phi))
        out[0] = lambda * cos(theta) / (M * dy(theta))
        out[1] = y(theta)
    }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean {
        if (abs(y) > bounds.maxY) return false
        var theta = y / A1
        repeat(20) {
            val delta = (y(theta) - y) / dy(theta)
            theta -= delta
            if (abs(delta) < 1e-12) return@repeat
        }
        val lambda = x * M * dy(theta) / cos(theta)
        if (abs(lambda) > PI) return false
        out[0] = lambda
        out[1] = asin((sin(theta) / M).coerceIn(-1.0, 1.0))
        return true
    }
}

/**
 * Goode homolosine, interrupted: sinusoidal between ±40°44′ and Mollweide
 * beyond, in six lobes (two north, four south) arranged to keep the
 * continents whole.
 */
object GoodeHomolosineNormal : NormalProjection {

    /** Latitude where the sinusoidal and Mollweide parts meet, and Mollweide's vertical shift there. */
    private const val PHI_T = 0.7109889596207567
    private const val Y_SHIFT = 0.0528035274542

    private class Lobe(minDeg: Double, centerDeg: Double, maxDeg: Double) {
        val min = Math.toRadians(minDeg)
        val center = Math.toRadians(centerDeg)
        val max = Math.toRadians(maxDeg)
    }

    private val north = listOf(Lobe(-180.0, -100.0, -40.0), Lobe(-40.0, 30.0, 180.0))
    private val south = listOf(
        Lobe(-180.0, -160.0, -100.0),
        Lobe(-100.0, -60.0, -20.0),
        Lobe(-20.0, 20.0, 80.0),
        Lobe(80.0, 140.0, 180.0),
    )

    override val bounds = Bounds(-PI, -(SQRT2 - Y_SHIFT), PI, SQRT2 - Y_SHIFT)

    override val clip = Clip.Seams(
        listOf(Seam.ANTIMERIDIAN, Seam(Math.toRadians(-40.0), 0.0, PI / 2)) +
            listOf(-100.0, -20.0, 80.0).map { Seam(Math.toRadians(it), -PI / 2, 0.0) },
    )

    override fun forward(lambda: Double, phi: Double, out: DoubleArray) {
        val lobes = if (phi >= 0) north else south
        val lobe = lobes.firstOrNull { lambda <= it.max } ?: lobes.last()
        homolosine(lambda - lobe.center, phi, out)
        out[0] += lobe.center
    }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean {
        if (abs(y) > bounds.maxY) return false
        for (lobe in if (y >= 0) north else south) {
            if (!inverseHomolosine(x - lobe.center, y, out)) continue
            val lambda = out[0] + lobe.center
            if (lambda >= lobe.min - 1e-12 && lambda <= lobe.max + 1e-12) {
                out[0] = lambda
                return true
            }
        }
        return false
    }

    private fun homolosine(lambda: Double, phi: Double, out: DoubleArray) {
        if (abs(phi) <= PHI_T) {
            SinusoidalNormal.forward(lambda, phi, out)
        } else {
            MollweideNormal.forward(lambda, phi, out)
            out[1] -= Y_SHIFT * sign(phi)
        }
    }

    private fun inverseHomolosine(x: Double, y: Double, out: DoubleArray): Boolean =
        if (abs(y) <= PHI_T) {
            SinusoidalNormal.inverse(x, y, out)
        } else {
            MollweideNormal.inverse(x, y + Y_SHIFT * sign(y), out)
        }
}

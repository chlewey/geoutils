package net.interlecto.geoutils.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Hemispheric maps: one hemisphere on a disk, centered on (0°, 0°) in normal
 * aspect. The rim of the disk is the great circle 90° from the center
 * (meridians ±90° in normal aspect).
 */

/**
 * Only the hemisphere around the center of [base], which must draw that
 * hemisphere as a disk of [radius] (true of the azimuthals, and of Mollweide
 * between ±90°).
 */
class HemisphereNormal(private val base: NormalProjection, val radius: Double) : NormalProjection {
    override val bounds = Bounds(-radius, -radius, radius, radius)
    override val clip = Clip.Cap(0.0)

    override fun forward(lambda: Double, phi: Double, out: DoubleArray) = base.forward(lambda, phi, out)

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean =
        x * x + y * y <= radius * radius && base.inverse(x, y, out)
}

/**
 * Nicolosi globular: the hemisphere on a disk of radius π/2. The equator and
 * the central meridian are straight and evenly divided; the other meridians
 * are circular arcs through the poles and evenly spaced points of the
 * equator, and the parallels are circular arcs through evenly spaced points
 * of the central meridian and of the rim.
 */
object NicolosiNormal : NormalProjection {
    private const val P = PI / 2
    override val bounds = Bounds(-P, -P, P, P)
    override val clip = Clip.Cap(0.0)

    /**
     * The point is where the meridian's circle (center (h, 0)) meets the
     * parallel's (center (0, k)). Written with 1/h and 1/k, which go to 0 on
     * the straight central meridian and equator, so it stays accurate near them.
     */
    override fun forward(lambda: Double, phi: Double, out: DoubleArray) {
        val l = lambda.coerceIn(-P, P)
        when {
            abs(phi) >= P - 1e-12 -> {
                out[0] = 0.0
                out[1] = phi.sign * P
            }

            P * P - l * l < 1e-12 -> { // The rim, evenly divided.
                out[0] = l.sign * P * cos(phi)
                out[1] = P * sin(phi)
            }

            else -> {
                val ih = 2 * l / (l * l - P * P)
                val ik = 2 * (P * sin(phi) - phi) / (P * P - phi * phi)
                val y0 = P * sin(phi) // Where the line through both intersections crosses x = 0.
                val cos2 = cos(phi) * cos(phi)
                val b = 2 * (ik * y0 - 1)
                val ac = -(ih * ih + ik * ik) * P * P * cos2
                val den = -b + sqrt(b * b - 4 * ac)
                out[0] = -2 * ih * P * P * cos2 / den
                out[1] = y0 - 2 * ik * P * P * cos2 / den
            }
        }
    }

    /** No closed form: Newton's method from a first guess that is exact on the axes. */
    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean {
        if (x * x + y * y > P * P * (1 + 1e-12)) return false
        val yc = y.coerceIn(-P, P)
        var phi = yc
        var lambda = (P * x / sqrt(maxOf(P * P - yc * yc, 1e-24))).coerceIn(-P, P)
        val f = DoubleArray(2)
        val fl = DoubleArray(2)
        val fp = DoubleArray(2)
        for (i in 0 until 40) {
            forward(lambda, phi, f)
            val ex = f[0] - x
            val ey = f[1] - y
            if (abs(ex) + abs(ey) < 1e-13) break
            // One-sided differences, stepping inwards so they stay on the hemisphere.
            val dl = if (lambda > 0) -1e-7 else 1e-7
            val dp = if (phi > 0) -1e-7 else 1e-7
            forward(lambda + dl, phi, fl)
            forward(lambda, phi + dp, fp)
            val a = (fl[0] - f[0]) / dl
            val c = (fl[1] - f[1]) / dl
            val b = (fp[0] - f[0]) / dp
            val d = (fp[1] - f[1]) / dp
            val det = a * d - b * c
            if (abs(det) < 1e-18) break
            lambda = (lambda - (d * ex - b * ey) / det).coerceIn(-P, P)
            phi = (phi - (-c * ex + a * ey) / det).coerceIn(-P, P)
        }
        out[0] = lambda
        out[1] = phi
        return true
    }
}

/**
 * Two hemispheres on disks that touch at one point, as one map.
 *
 * In the split layout, [tangent] is the point where the disks touch and
 * [azimuthDeg] the bearing there of the great circle that splits the two
 * hemispheres (0° runs north–south, so the disks sit side by side with north
 * up). The disk centers are 90° to either side of the split.
 *
 * In the [polar] layout the hemispheres are the northern and southern ones:
 * the disks touch on the equator at the longitude of [tangent], so that
 * meridian runs through both centers, and [azimuthDeg] turns the finished
 * map in the plane (0° puts the disks side by side, north on the left).
 *
 * @param hemisphere draws one hemisphere, centered, on a disk filling its bounds.
 */
fun doubleHemisphere(hemisphere: NormalProjection, tangent: GeoPoint, azimuthDeg: Double, polar: Boolean): Projection {
    val radius = hemisphere.bounds.maxX
    // The split circle as the "meridian" of a frame at the tangent point: in
    // the polar layout it is the equator, so up is east.
    val frame = if (polar) SphereFrame(GeoPoint(tangent.lon, 0.0), 90.0) else SphereFrame(tangent, azimuthDeg)
    val left = SphereFrame(c = frame.r * -1.0, r = frame.c, u = frame.u)
    val right = SphereFrame(c = frame.r, r = frame.c * -1.0, u = frame.u)
    val turn = if (polar) azimuthDeg else 0.0
    val a = Math.toRadians(turn)
    val halfWidth = radius + radius * abs(cos(a))
    val halfHeight = radius + radius * abs(sin(a))
    return CompositeProjection(
        listOf(
            PlacedProjection(RotatedProjection(hemisphere, left), Vec2(-radius, 0.0), turn),
            PlacedProjection(RotatedProjection(hemisphere, right), Vec2(radius, 0.0), turn),
        ),
        Bounds(-halfWidth, -halfHeight, halfWidth, halfHeight),
    )
}

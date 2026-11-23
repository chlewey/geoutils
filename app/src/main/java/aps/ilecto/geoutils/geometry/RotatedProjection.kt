package net.interlecto.geoutils.geometry

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * An orthonormal frame on the sphere: [c] points at the center of the map,
 * [u] is "up" (north on the map) and [r] = [u] × [c] points to the right. In
 * local coordinates q = (p·c, p·r, p·u), the center is at (0°, 0°) and [u]
 * points north.
 */
class SphereFrame(val c: Vec3, val r: Vec3, val u: Vec3) {

    fun toLocal(p: Vec3) = Vec3(p dot c, p dot r, p dot u)

    fun toGlobal(q: Vec3) = c * q.x + r * q.y + u * q.z

    companion object {
        /** The frame at [center] with "up" along the bearing [azimuthDeg] (clockwise from north). */
        operator fun invoke(center: GeoPoint, azimuthDeg: Double): SphereFrame {
            val lambda0 = Math.toRadians(center.lon)
            val phi0 = Math.toRadians(center.lat)
            val east = Vec3(-sin(lambda0), cos(lambda0), 0.0)
            val north = Vec3(-sin(phi0) * cos(lambda0), -sin(phi0) * sin(lambda0), cos(phi0))
            val a = Math.toRadians(azimuthDeg)
            return SphereFrame(
                c = center.toVec3(),
                r = east * cos(a) - north * sin(a),
                u = north * cos(a) + east * sin(a),
            )
        }
    }
}

/**
 * A [NormalProjection] aimed by a [SphereFrame], usually built from a center
 * and an azimuth: the sphere is rotated so that the center lands on (0°, 0°)
 * with the azimuth bearing pointing north, then projected with the normal
 * formulas. Clipping
 * and cutting happen in the rotated system.
 *
 * With a [region], only that box (in rotated coordinates) is drawn, instead
 * of the base's own clipping. A [CompositeProjection] uses this to repeat
 * part of the sphere elsewhere on the map.
 */
class RotatedProjection(
    val base: NormalProjection,
    private val frame: SphereFrame,
    region: LonLatBox? = null,
) : Projection {

    constructor(base: NormalProjection, center: GeoPoint, azimuthDeg: Double = 0.0, region: LonLatBox? = null) :
        this(base, SphereFrame(center, azimuthDeg), region)
    private val clipper: Clipper = when {
        region != null -> BoxClipper(base, region)
        else -> when (val clip = base.clip) {
            is Clip.Cap -> CapClipper(base, clip.minCos)
            is Clip.Seams -> SeamClipper(base, clip.seams)
        }
    }

    override val bounds: Bounds get() = base.bounds

    override fun forward(lon: Double, lat: Double, out: DoubleArray): Boolean {
        val lambda = Math.toRadians(lon)
        val phi = Math.toRadians(lat)
        val cosPhi = cos(phi)
        val px = cosPhi * cos(lambda)
        val py = cosPhi * sin(lambda)
        val pz = sin(phi)
        val c = frame.c
        val r = frame.r
        val u = frame.u
        val qx = px * c.x + py * c.y + pz * c.z
        val qy = px * r.x + py * r.y + pz * r.z
        val qz = px * u.x + py * u.y + pz * u.z
        if (!clipper.isVisible(qx, qy, qz)) return false
        base.forward(localLambda(qx, qy), asin(qz.coerceIn(-1.0, 1.0)), out)
        return true
    }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean {
        if (!base.inverse(x, y, out)) return false
        val cosPhi = cos(out[1])
        val qx = cosPhi * cos(out[0])
        val qy = cosPhi * sin(out[0])
        val qz = sin(out[1])
        if (!clipper.isVisible(qx, qy, qz)) return false
        val c = frame.c
        val r = frame.r
        val u = frame.u
        val px = qx * c.x + qy * r.x + qz * u.x
        val py = qx * c.y + qy * r.y + qz * u.y
        val pz = qx * c.z + qy * r.z + qz * u.z
        out[0] = Math.toDegrees(atan2(py, px))
        out[1] = Math.toDegrees(asin(pz.coerceIn(-1.0, 1.0)))
        return true
    }

    override fun outline(): List<List<Vec2>> = clipper.outline()

    override fun projectLine(points: List<GeoPoint>): List<List<Vec2>> =
        clipper.line(densify(points, closed = false).map(frame::toLocal))

    override fun projectRing(ring: List<GeoPoint>): List<List<Vec2>> =
        clipper.ring(densify(ring, closed = true).map(frame::toLocal))
}

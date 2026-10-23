package net.interlecto.geoutils.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Orthographic projection: the globe as seen from infinitely far away, looking
 * at [center]. Only the hemisphere facing the viewer is drawn; the map is the
 * unit disk.
 *
 * [azimuthDeg] turns the view around its axis: it is the bearing, clockwise
 * from north, that points up on the map. At 0 north is up; at 90 east is up
 * and north points left.
 *
 * Internally a point p on the sphere maps to (p·right, p·up), where right, up
 * and the view axis form an orthonormal frame at [center]. A point is visible
 * when p·axis ≥ 0.
 */
class Orthographic(val center: GeoPoint, val azimuthDeg: Double = 0.0) : Projection {

    private val axis = center.toVec3()
    private val right: Vec3
    private val up: Vec3

    init {
        val lambda0 = Math.toRadians(center.lon)
        val phi0 = Math.toRadians(center.lat)
        val east = Vec3(-sin(lambda0), cos(lambda0), 0.0)
        val north = Vec3(-sin(phi0) * cos(lambda0), -sin(phi0) * sin(lambda0), cos(phi0))
        val a = Math.toRadians(azimuthDeg)
        up = north * cos(a) + east * sin(a)
        right = east * cos(a) - north * sin(a)
    }

    override val bounds = Bounds(-1.0, -1.0, 1.0, 1.0)

    override fun forward(lon: Double, lat: Double, out: DoubleArray): Boolean {
        val lambda = Math.toRadians(lon)
        val phi = Math.toRadians(lat)
        val cosPhi = cos(phi)
        val px = cosPhi * cos(lambda)
        val py = cosPhi * sin(lambda)
        val pz = sin(phi)
        if (px * axis.x + py * axis.y + pz * axis.z < 0) return false
        out[0] = px * right.x + py * right.y + pz * right.z
        out[1] = px * up.x + py * up.y + pz * up.z
        return true
    }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean {
        val rho2 = x * x + y * y
        if (rho2 > 1.0) return false
        val z = sqrt(1.0 - rho2)
        // p = x·right + y·up + z·axis, then back to lon/lat.
        val px = x * right.x + y * up.x + z * axis.x
        val py = x * right.y + y * up.y + z * axis.y
        val pz = x * right.z + y * up.z + z * axis.z
        out[0] = Math.toDegrees(atan2(py, px))
        out[1] = Math.toDegrees(asin(pz.coerceIn(-1.0, 1.0)))
        return true
    }

    override fun outline(): List<Vec2> = List(OUTLINE_STEPS) { i ->
        val a = 2 * PI * i / OUTLINE_STEPS
        Vec2(cos(a), sin(a))
    }

    override fun projectLine(points: List<GeoPoint>): List<List<Vec2>> {
        val pieces = mutableListOf<MutableList<Vec2>>()
        var current: MutableList<Vec2>? = null
        var prev: Vec3? = null
        for (p in densify(points, closed = false)) {
            if (isVisible(p)) {
                if (current == null) {
                    current = mutableListOf()
                    if (prev != null) current.add(toPlane(horizonCrossing(prev, p)))
                    pieces.add(current)
                }
                current.add(toPlane(p))
            } else if (current != null) {
                current.add(toPlane(horizonCrossing(prev!!, p)))
                current = null
            }
            prev = p
        }
        return pieces.filter { it.size >= 2 }
    }

    /**
     * Each hidden stretch of the ring is replaced by an arc of the horizon
     * from where the ring leaves the visible hemisphere to where it comes
     * back. The arc turns around the disk center by the same angle the hidden
     * stretch does, which tells which way (and how far) to go around.
     */
    override fun projectRing(ring: List<GeoPoint>): List<Vec2>? {
        val pts = densify(ring, closed = true)
        val n = pts.size
        if (n < 3) return null
        val start = pts.indexOfFirst(::isVisible)
        if (start < 0) return null

        val out = mutableListOf(toPlane(pts[start]))
        var hidden = false
        var exitAngle = 0.0
        var sweep = 0.0
        var last = out[0]
        for (k in 1..n) {
            val p = pts[(start + k) % n]
            val prev = pts[(start + k - 1) % n]
            if (isVisible(p)) {
                if (hidden) {
                    val entry = toPlane(horizonCrossing(prev, p))
                    sweep += turn(last, entry)
                    appendArc(out, exitAngle, sweep)
                    out.add(entry)
                    hidden = false
                }
                if (k < n) out.add(toPlane(p))
            } else {
                if (!hidden) {
                    val exit = toPlane(horizonCrossing(prev, p))
                    out.add(exit)
                    exitAngle = atan2(exit.y, exit.x)
                    sweep = 0.0
                    last = exit
                    hidden = true
                }
                val q = toPlane(p)
                sweep += turn(last, q)
                last = q
            }
        }
        return out
    }

    private fun isVisible(p: Vec3): Boolean = (p dot axis) >= 0

    private fun toPlane(p: Vec3) = Vec2(p dot right, p dot up)

    /** Point where the short great-circle arc from [a] (visible) to [b] (hidden), or back, meets the horizon. */
    private fun horizonCrossing(a: Vec3, b: Vec3): Vec3 {
        val da = a dot axis
        val db = b dot axis
        val t = da / (da - db)
        return (a + (b - a) * t).normalized()
    }

    /** Signed angle, around the disk center, from [a] to [b]. */
    private fun turn(a: Vec2, b: Vec2): Double =
        atan2(a.x * b.y - a.y * b.x, a.x * b.x + a.y * b.y)

    /** Appends horizon points strictly between [from] and [from] + [sweep]. */
    private fun appendArc(out: MutableList<Vec2>, from: Double, sweep: Double) {
        val steps = ceil(abs(sweep) / ARC_STEP_RAD).toInt()
        for (s in 1 until steps) {
            val a = from + sweep * s / steps
            out.add(Vec2(cos(a), sin(a)))
        }
    }

    private companion object {
        const val OUTLINE_STEPS = 360
        const val ARC_STEP_RAD = PI / 180
    }
}

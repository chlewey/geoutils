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
 * Internally a point p on the sphere maps to (p·east, p·north), where east,
 * north and the view axis form an orthonormal frame at [center]. A point is
 * visible when p·axis ≥ 0.
 */
class Orthographic(val center: GeoPoint) : Projection {

    private val lambda0 = Math.toRadians(center.lon)
    private val phi0 = Math.toRadians(center.lat)
    private val sinPhi0 = sin(phi0)
    private val cosPhi0 = cos(phi0)

    private val axis = center.toVec3()
    private val east = Vec3(-sin(lambda0), cos(lambda0), 0.0)
    private val north = Vec3(-sinPhi0 * cos(lambda0), -sinPhi0 * sin(lambda0), cosPhi0)

    override val bounds = Bounds(-1.0, -1.0, 1.0, 1.0)

    override fun forward(lon: Double, lat: Double, out: DoubleArray): Boolean {
        val dLambda = Math.toRadians(lon) - lambda0
        val phi = Math.toRadians(lat)
        val sinPhi = sin(phi)
        val cosPhi = cos(phi)
        val cosDLambda = cos(dLambda)
        val cosC = sinPhi0 * sinPhi + cosPhi0 * cosPhi * cosDLambda
        if (cosC < 0) return false
        out[0] = cosPhi * sin(dLambda)
        out[1] = cosPhi0 * sinPhi - sinPhi0 * cosPhi * cosDLambda
        return true
    }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean {
        val rho2 = x * x + y * y
        if (rho2 > 1.0) return false
        val z = sqrt(1.0 - rho2)
        // p = x·east + y·north + z·axis, then back to lon/lat.
        val px = x * east.x + y * north.x + z * axis.x
        val py = x * east.y + y * north.y + z * axis.y
        val pz = x * east.z + y * north.z + z * axis.z
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

    private fun toPlane(p: Vec3) = Vec2(p dot east, p dot north)

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

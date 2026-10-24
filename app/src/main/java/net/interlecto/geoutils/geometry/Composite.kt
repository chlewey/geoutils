package net.interlecto.geoutils.geometry

import kotlin.math.PI

/**
 * The map turned 180° in the plane: south up. Unlike an azimuth of 180°, this
 * doesn't rotate the sphere, so interrupted maps keep their lobes on the
 * same continents.
 */
class FlippedProjection(private val inner: Projection) : Projection {

    override val bounds: Bounds
        get() = inner.bounds.let { Bounds(-it.maxX, -it.maxY, -it.minX, -it.minY) }

    override fun forward(lon: Double, lat: Double, out: DoubleArray): Boolean {
        if (!inner.forward(lon, lat, out)) return false
        out[0] = -out[0]
        out[1] = -out[1]
        return true
    }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean = inner.inverse(-x, -y, out)

    override fun outline(): List<List<Vec2>> = inner.outline().map(::flip)

    override fun projectLine(points: List<GeoPoint>): List<List<Vec2>> = inner.projectLine(points).map(::flip)

    override fun projectRing(ring: List<GeoPoint>): List<List<Vec2>> = inner.projectRing(ring).map(::flip)

    private fun flip(path: List<Vec2>) = path.map { Vec2(-it.x, -it.y) }
}

/**
 * A map drawn in several parts, all within the first part's bounds. Some of
 * the sphere may appear in more than one part (Goode with Greenland both
 * whole and divided). [forward] and [inverse] use the first part that
 * accepts the point; paths are drawn in every part.
 */
class CompositeProjection(private val parts: List<Projection>) : Projection {

    init {
        require(parts.isNotEmpty())
    }

    override val bounds: Bounds get() = parts.first().bounds

    override fun forward(lon: Double, lat: Double, out: DoubleArray): Boolean =
        parts.any { it.forward(lon, lat, out) }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean = parts.any { it.inverse(x, y, out) }

    override fun outline(): List<List<Vec2>> = parts.flatMap { it.outline() }

    override fun projectLine(points: List<GeoPoint>): List<List<Vec2>> = parts.flatMap { it.projectLine(points) }

    override fun projectRing(ring: List<GeoPoint>): List<List<Vec2>> = parts.flatMap { it.projectRing(ring) }
}

/**
 * The map slid sideways by [shiftDeg] and wrapped around at its left and
 * right edges, like a cylinder turned about its axis. Used for interrupted
 * maps: the lobes travel with their continents, and a lobe that crosses an
 * edge is split, part on each side.
 *
 * The inner map must span x ∈ [-π, π], with x = λ along the equator
 * (as Goode homolosine does), so the point at longitude [shiftDeg] on the
 * equator lands in the middle.
 */
class ShiftedProjection(private val inner: Projection, shiftDeg: Double) : Projection {

    private val shift = Math.toRadians(GeoPoint.normalizeLongitude(shiftDeg))

    override val bounds: Bounds get() = inner.bounds

    override fun forward(lon: Double, lat: Double, out: DoubleArray): Boolean {
        if (!inner.forward(lon, lat, out)) return false
        out[0] = wrap(out[0] - shift)
        return true
    }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean = inner.inverse(wrap(x + shift), y, out)

    override fun outline(): List<List<Vec2>> = inner.outline().flatMap(::splitPolygon)

    override fun projectLine(points: List<GeoPoint>): List<List<Vec2>> = inner.projectLine(points).flatMap(::splitLine)

    override fun projectRing(ring: List<GeoPoint>): List<List<Vec2>> = inner.projectRing(ring).flatMap(::splitPolygon)

    private fun wrap(x: Double): Double = when {
        x < -PI -> x + 2 * PI
        x > PI -> x - 2 * PI
        else -> x
    }

    /** Shifts a polyline, cutting it where it crosses the left or right edge. */
    private fun splitLine(path: List<Vec2>): List<List<Vec2>> {
        val pieces = mutableListOf(mutableListOf<Vec2>())
        var prev: Vec2? = null
        for (p in path) {
            val u = Vec2(p.x - shift, p.y)
            if (prev != null) {
                for (edge in listOf(-PI, PI)) {
                    if ((prev.x - edge) * (u.x - edge) < 0) {
                        val y = prev.y + (u.y - prev.y) * (edge - prev.x) / (u.x - prev.x)
                        pieces.last().add(Vec2(edgeSeenFrom(edge, prev.x), y))
                        pieces.add(mutableListOf(Vec2(edgeSeenFrom(edge, u.x), y)))
                    }
                }
            }
            pieces.last().add(Vec2(wrap(u.x), u.y))
            prev = u
        }
        return pieces.filter { it.size >= 2 }
    }

    /**
     * Where the crossing at [edge] ends up on the map, seen from a point at
     * (unwrapped) [x]: the part beyond the edge wraps to the opposite side.
     */
    private fun edgeSeenFrom(edge: Double, x: Double): Double = when {
        edge < 0 && x < edge -> PI
        edge > 0 && x > edge -> -PI
        else -> edge
    }

    /** Shifts a closed polygon, splitting off the part that wraps to the other side. */
    private fun splitPolygon(ring: List<Vec2>): List<List<Vec2>> {
        val shifted = ring.map { Vec2(it.x - shift, it.y) }
        val inside = clip(clip(shifted, -PI, keepAbove = true), PI, keepAbove = false)
        val left = clip(shifted, -PI, keepAbove = false).map { Vec2(it.x + 2 * PI, it.y) }
        val right = clip(shifted, PI, keepAbove = true).map { Vec2(it.x - 2 * PI, it.y) }
        return listOf(inside, left, right).filter { it.size >= 3 }
    }

    /** Sutherland–Hodgman against the vertical line x = [edge]. */
    private fun clip(poly: List<Vec2>, edge: Double, keepAbove: Boolean): List<Vec2> {
        if (poly.isEmpty()) return poly
        fun inside(p: Vec2) = if (keepAbove) p.x >= edge else p.x <= edge
        val out = mutableListOf<Vec2>()
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            if (inside(a)) out += a
            if (inside(a) != inside(b)) {
                out += Vec2(edge, a.y + (b.y - a.y) * (edge - a.x) / (b.x - a.x))
            }
        }
        return out
    }
}

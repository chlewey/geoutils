package net.interlecto.geoutils.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Clips and cuts densified paths given in local (rotated) unit vectors, and
 * projects them with a [NormalProjection].
 */
internal interface Clipper {
    fun isVisible(qx: Double, qy: Double, qz: Double): Boolean
    fun outline(): List<Vec2>
    fun line(points: List<Vec3>): List<List<Vec2>>
    fun ring(points: List<Vec3>): List<List<Vec2>>
}

/** Longitude of a local vector; a point exactly on the antimeridian counts as -π (its east side). */
internal fun localLambda(x: Double, y: Double): Double =
    if (y == 0.0 && x < 0) -PI else atan2(y, x)

private fun NormalProjection.project(lambda: Double, phi: Double): Vec2 {
    val out = DoubleArray(2)
    forward(lambda, phi, out)
    return Vec2(out[0], out[1])
}

private fun NormalProjection.project(q: Vec3): Vec2 =
    project(localLambda(q.x, q.y), asin(q.z.coerceIn(-1.0, 1.0)))

private const val STEP_RAD = PI / 180

/**
 * Keeps the cap q.x ≥ [minCos] around the center (a hemisphere when 0).
 * Each hidden stretch of a ring is replaced by an arc of the cap's rim from
 * where the ring leaves to where it comes back. The arc turns around the
 * center by the same angle as the hidden stretch, which tells which way, and
 * how far, to go around.
 */
internal class CapClipper(private val base: NormalProjection, private val minCos: Double) : Clipper {

    private val rimRadius = sqrt(1 - minCos * minCos)

    override fun isVisible(qx: Double, qy: Double, qz: Double) = qx >= minCos

    override fun outline(): List<Vec2> = List(OUTLINE_STEPS) { i -> base.project(rim(2 * PI * i / OUTLINE_STEPS)) }

    override fun line(points: List<Vec3>): List<List<Vec2>> {
        val pieces = mutableListOf<MutableList<Vec2>>()
        var current: MutableList<Vec2>? = null
        var prev: Vec3? = null
        for (p in points) {
            if (visible(p)) {
                if (current == null) {
                    current = mutableListOf()
                    if (prev != null) current.add(base.project(rimCrossing(prev, p)))
                    pieces.add(current)
                }
                current.add(base.project(p))
            } else if (current != null) {
                current.add(base.project(rimCrossing(prev!!, p)))
                current = null
            }
            prev = p
        }
        return pieces.filter { it.size >= 2 }
    }

    override fun ring(points: List<Vec3>): List<List<Vec2>> {
        val n = points.size
        if (n < 3) return emptyList()
        val start = points.indexOfFirst(::visible)
        if (start < 0) return emptyList()

        val out = mutableListOf(base.project(points[start]))
        var hidden = false
        var exitAngle = 0.0
        var sweep = 0.0
        var last = points[start]
        for (k in 1..n) {
            val p = points[(start + k) % n]
            val prev = points[(start + k - 1) % n]
            if (visible(p)) {
                if (hidden) {
                    val entry = rimCrossing(prev, p)
                    sweep += turn(last, entry)
                    appendRim(out, exitAngle, sweep)
                    out.add(base.project(entry))
                    hidden = false
                }
                if (k < n) out.add(base.project(p))
            } else {
                if (!hidden) {
                    val exit = rimCrossing(prev, p)
                    out.add(base.project(exit))
                    exitAngle = atan2(exit.z, exit.y)
                    sweep = 0.0
                    last = exit
                    hidden = true
                }
                sweep += turn(last, p)
                last = p
            }
        }
        return listOf(out)
    }

    private fun visible(q: Vec3) = q.x >= minCos

    private fun rim(angle: Double) = Vec3(minCos, rimRadius * cos(angle), rimRadius * sin(angle))

    /** Where the short arc between [a] and [b] (one visible, one hidden) meets the rim. */
    private fun rimCrossing(a: Vec3, b: Vec3): Vec3 {
        val t = (a.x - minCos) / (a.x - b.x)
        return (a + (b - a) * t).normalized()
    }

    /** Signed angle around the center (the x axis) from [a] to [b]. */
    private fun turn(a: Vec3, b: Vec3): Double = atan2(a.y * b.z - a.z * b.y, a.y * b.y + a.z * b.z)

    /** Appends rim points strictly between [from] and [from] + [sweep]. */
    private fun appendRim(out: MutableList<Vec2>, from: Double, sweep: Double) {
        val steps = ceil(abs(sweep) / STEP_RAD).toInt()
        for (s in 1 until steps) out.add(base.project(rim(from + sweep * s / steps)))
    }

    private companion object {
        const val OUTLINE_STEPS = 360
    }
}

/**
 * Cuts paths along [seams] (the antimeridian, plus any interruptions) for
 * maps of the whole sphere.
 *
 * The map's edge is a closed loop in (λ, φ): the bottom (south pole) line,
 * the west side of the antimeridian, the top (north pole) line and its east
 * side, detouring along both sides of every interruption. It runs
 * counterclockwise, with the map on its left.
 *
 * A polygon ring is first oriented so its inside (taken as the smaller of
 * the two regions it bounds on the sphere) is on its left. It is then cut
 * where it crosses seams, and the pieces are closed up again by following
 * the edge loop forward from each piece's exit to the next entry, as in
 * Weiler–Atherton clipping.
 */
internal class SeamClipper(private val base: NormalProjection, private val seams: List<Seam>) : Clipper {

    /** A straight run of the edge loop in (λ, φ). */
    private class Edge(val from: Vec2, val to: Vec2, val seam: Int, val eastSide: Boolean) {
        val length = abs(to.x - from.x) + abs(to.y - from.y)
        var start = 0.0

        fun at(d: Double): Vec2 {
            val f = if (length == 0.0) 0.0 else d / length
            return Vec2(from.x + (to.x - from.x) * f, from.y + (to.y - from.y) * f)
        }
    }

    private sealed interface Event

    private class Point(val lambda: Double, val phi: Double, val atPole: Boolean) : Event

    /** A seam crossing: the path leaves the map at [exitLambda] and continues at [entryLambda]. */
    private class Crossing(
        val t: Double,
        val seam: Int,
        val westToEast: Boolean,
        val phi: Double,
        val exitLambda: Double,
        val entryLambda: Double,
    ) : Event

    private class Piece(val entry: Double, val exit: Double, val points: List<Vec2>)

    /** Points east, normal to each seam's meridian plane. */
    private val normals = seams.map { Vec3(-sin(it.lambda), cos(it.lambda), 0.0) }
    private val directions = seams.map { Vec3(cos(it.lambda), sin(it.lambda), 0.0) }
    private val edges: List<Edge> = buildEdgeLoop()
    private val perimeter: Double

    init {
        var s = 0.0
        for (e in edges) {
            e.start = s
            s += e.length
        }
        perimeter = s
    }

    override fun isVisible(qx: Double, qy: Double, qz: Double) = true

    override fun outline(): List<Vec2> = (listOf(pointAt(0.0)) + walk(0.0, perimeter)).map(::project)

    override fun line(points: List<Vec3>): List<List<Vec2>> {
        val pieces = mutableListOf(mutableListOf<Vec2>())
        for (event in events(points, closed = false)) when (event) {
            is Point -> pieces.last().add(Vec2(event.lambda, event.phi))
            is Crossing -> {
                pieces.last().add(Vec2(event.exitLambda, event.phi))
                pieces.add(mutableListOf(Vec2(event.entryLambda, event.phi)))
            }
        }
        return pieces.filter { it.size >= 2 }.map { piece -> piece.map(::project) }
    }

    override fun ring(points: List<Vec3>): List<List<Vec2>> {
        var pts = dedupe(points, closed = true)
        if (pts.size < 3) return emptyList()
        if (signedArea(pts) < 0) pts = pts.reversed()
        val events = events(pts, closed = true)

        val first = events.indexOfFirst { it is Crossing }
        if (first < 0) {
            return listOf(events.map { (it as Point).let { p -> project(Vec2(p.lambda, p.phi)) } })
        }

        // Split into pieces, each running from one crossing to the next.
        val n = events.size
        val pieces = mutableListOf<Piece>()
        var start = events[first] as Crossing
        var current = mutableListOf(Vec2(start.entryLambda, start.phi))
        for (k in 1..n) {
            when (val event = events[(first + k) % n]) {
                is Point -> current.add(Vec2(event.lambda, event.phi))
                is Crossing -> {
                    current.add(Vec2(event.exitLambda, event.phi))
                    pieces += Piece(
                        entry = position(start.seam, eastSide = start.westToEast, start.phi),
                        exit = position(event.seam, eastSide = !event.westToEast, event.phi),
                        points = current,
                    )
                    start = event
                    current = mutableListOf(Vec2(event.entryLambda, event.phi))
                }
            }
        }

        // Join them by following the edge loop from each exit to the next entry.
        val rings = mutableListOf<List<Vec2>>()
        val used = BooleanArray(pieces.size)
        for (i in pieces.indices) {
            if (used[i]) continue
            val ring = mutableListOf<Vec2>()
            var cur = i
            while (!used[cur]) {
                used[cur] = true
                val piece = pieces[cur]
                ring += piece.points
                val next = pieces.indices.minBy { (pieces[it].entry - piece.exit).mod(perimeter) }
                ring += walk(piece.exit, piece.exit + (pieces[next].entry - piece.exit).mod(perimeter))
                cur = next
            }
            rings += ring.map(::project)
        }
        return rings
    }

    private fun project(p: Vec2): Vec2 = base.project(p.x, p.y)

    /** Longitude just east or west of a seam, wrapped into [-π, π]. */
    private fun sideLambda(seam: Int, eastSide: Boolean): Double {
        val l = seams[seam].lambda + if (eastSide) EPS else -EPS
        return when {
            l > PI -> l - 2 * PI
            l < -PI -> l + 2 * PI
            else -> l
        }
    }

    private fun buildEdgeLoop(): List<Edge> {
        val south = -PI / 2
        val north = PI / 2
        val west = sideLambda(0, eastSide = true)
        val east = sideLambda(0, eastSide = false)
        val loop = mutableListOf<Edge>()

        // Bottom: west to east, around interruptions that start at the south pole.
        var lambda = west
        val southern = seams.indices.drop(1).filter { seams[it].phiMin <= south }.sortedBy { seams[it].lambda }
        for (k in southern) {
            val westSide = sideLambda(k, eastSide = false)
            val eastSide = sideLambda(k, eastSide = true)
            val tip = seams[k].phiMax
            loop += Edge(Vec2(lambda, south), Vec2(westSide, south), -1, false)
            loop += Edge(Vec2(westSide, south), Vec2(westSide, tip), k, false)
            loop += Edge(Vec2(eastSide, tip), Vec2(eastSide, south), k, true)
            lambda = eastSide
        }
        loop += Edge(Vec2(lambda, south), Vec2(east, south), -1, false)
        // Right: west side of the antimeridian, south to north.
        loop += Edge(Vec2(east, south), Vec2(east, north), 0, false)

        // Top: east to west, around interruptions that start at the north pole.
        lambda = east
        val northern = seams.indices.drop(1).filter { seams[it].phiMax >= north }.sortedByDescending { seams[it].lambda }
        for (k in northern) {
            val eastSide = sideLambda(k, eastSide = true)
            val westSide = sideLambda(k, eastSide = false)
            val tip = seams[k].phiMin
            loop += Edge(Vec2(lambda, north), Vec2(eastSide, north), -1, false)
            loop += Edge(Vec2(eastSide, north), Vec2(eastSide, tip), k, true)
            loop += Edge(Vec2(westSide, tip), Vec2(westSide, north), k, false)
            lambda = westSide
        }
        loop += Edge(Vec2(lambda, north), Vec2(west, north), -1, false)
        // Left: east side of the antimeridian, north to south.
        loop += Edge(Vec2(west, north), Vec2(west, south), 0, true)
        return loop
    }

    /** Position along the edge loop of a point on one side of a seam. */
    private fun position(seam: Int, eastSide: Boolean, phi: Double): Double {
        val edge = edges
            .filter { it.seam == seam && it.eastSide == eastSide }
            .minBy { e ->
                val lo = min(e.from.y, e.to.y)
                val hi = maxOf(e.from.y, e.to.y)
                if (phi < lo) lo - phi else if (phi > hi) phi - hi else 0.0
            }
        val lo = min(edge.from.y, edge.to.y)
        val hi = maxOf(edge.from.y, edge.to.y)
        return edge.start + abs(phi.coerceIn(lo, hi) - edge.from.y)
    }

    private fun edgeAt(pos: Double): Edge {
        val p = pos.mod(perimeter)
        return edges.last { it.start <= p }
    }

    private fun pointAt(pos: Double): Vec2 {
        val e = edgeAt(pos)
        return e.at(pos.mod(perimeter) - e.start)
    }

    /** Points along the edge loop strictly between [from] and [to] (which may exceed the perimeter). */
    private fun walk(from: Double, to: Double): List<Vec2> {
        val out = mutableListOf<Vec2>()
        var pos = from
        while (true) {
            val e = edgeAt(pos)
            val corner = pos - pos.mod(perimeter) + e.start + e.length
            val next = if (corner > pos) min(pos + STEP_RAD, corner) else pos + STEP_RAD
            if (next >= to - 1e-12) break
            out += pointAt(next)
            pos = next
        }
        return out
    }

    /** Vertices and seam crossings, with points at a pole split to follow the pole line. */
    private fun events(points: List<Vec3>, closed: Boolean): List<Event> {
        val pts = dedupe(points, closed)
        val raw = mutableListOf<Event>()
        val segments = if (closed) pts.size else pts.size - 1
        for (i in pts.indices) {
            val a = pts[i]
            raw += Point(localLambda(a.x, a.y), asin(a.z.coerceIn(-1.0, 1.0)), abs(a.z) > 1 - POLE_EPS)
            if (i < segments) raw += crossings(a, pts[(i + 1) % pts.size])
        }
        return splitPoles(raw, closed)
    }

    private fun crossings(a: Vec3, b: Vec3): List<Crossing> {
        val found = mutableListOf<Crossing>()
        for (k in seams.indices) {
            val sa = a dot normals[k]
            val sb = b dot normals[k]
            if ((sa < 0) == (sb < 0)) continue
            val t = sa / (sa - sb)
            val m = (a + (b - a) * t).normalized()
            if ((m dot directions[k]) <= 0) continue // The other half of the great circle.
            val phi = asin(m.z.coerceIn(-1.0, 1.0))
            if (phi < seams[k].phiMin || phi > seams[k].phiMax) continue
            val westToEast = sa < 0
            found += Crossing(
                t = t,
                seam = k,
                westToEast = westToEast,
                phi = phi,
                exitLambda = sideLambda(k, eastSide = !westToEast),
                entryLambda = sideLambda(k, eastSide = westToEast),
            )
        }
        return found.sortedBy { it.t }
    }

    /**
     * A vertex at a pole has no longitude; on a whole-sphere map the pole is
     * a line. Replace it with two points on that line, at the longitudes of
     * the path arriving and leaving.
     */
    private fun splitPoles(events: List<Event>, closed: Boolean): List<Event> {
        if (events.none { it is Point && it.atPole }) return events
        fun neighborLambda(from: Int, step: Int): Double? {
            var i = from + step
            while (true) {
                if (i !in events.indices) {
                    if (!closed) return null
                    i = i.mod(events.size)
                }
                if (i == from) return null
                when (val e = events[i]) {
                    is Point -> if (!e.atPole) return e.lambda
                    is Crossing -> return if (step < 0) e.entryLambda else e.exitLambda
                }
                i += step
            }
        }
        val out = mutableListOf<Event>()
        for ((i, e) in events.withIndex()) {
            if (e is Point && e.atPole) {
                val before = neighborLambda(i, -1)
                val after = neighborLambda(i, +1)
                if (before != null) out += Point(before, e.phi, false)
                if (after != null) out += Point(after, e.phi, false)
                if (before == null && after == null) out += e
            } else {
                out += e
            }
        }
        return out
    }

    /**
     * Signed area of the ring, in (-2π, 2π]: positive when its smaller side is
     * on the left (counterclockwise seen from outside the sphere). Sum of the
     * signed triangles of a fan, with Van Oosterom and Strackee's formula.
     */
    private fun signedArea(p: List<Vec3>): Double {
        val a = p[0]
        var sum = 0.0
        for (i in 1 until p.size - 1) {
            val b = p[i]
            val c = p[i + 1]
            sum += 2 * atan2(a dot (b cross c), 1 + (a dot b) + (b dot c) + (c dot a))
        }
        val s = sum.mod(4 * PI)
        return if (s > 2 * PI) s - 4 * PI else s
    }

    private companion object {
        const val EPS = 1e-9
        const val POLE_EPS = 1e-12
    }
}

/** Drops consecutive repeated points (and, for rings, a last point equal to the first). */
private fun dedupe(points: List<Vec3>, closed: Boolean): List<Vec3> {
    val out = ArrayList<Vec3>(points.size)
    for (p in points) {
        if (out.isEmpty() || out.last().angleTo(p) > 1e-10) out.add(p)
    }
    if (closed) {
        while (out.size > 1 && out.last().angleTo(out.first()) <= 1e-10) out.removeAt(out.lastIndex)
    }
    return out
}

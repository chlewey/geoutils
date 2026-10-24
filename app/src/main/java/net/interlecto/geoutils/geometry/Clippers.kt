package net.interlecto.geoutils.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Clips and cuts densified paths given in local (rotated) unit vectors, and
 * projects them with a [NormalProjection].
 */
internal interface Clipper {
    fun isVisible(qx: Double, qy: Double, qz: Double): Boolean
    fun outline(): List<List<Vec2>>
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

    override fun outline(): List<List<Vec2>> =
        listOf(List(OUTLINE_STEPS) { i -> base.project(rim(2 * PI * i / OUTLINE_STEPS)) })

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
 * side, detouring down and back up both sides of every interruption. It runs
 * counterclockwise, with the map on its left.
 *
 * A polygon ring is first oriented so its inside (taken as the smaller of
 * the two regions it bounds on the sphere) is on its left. It is then cut
 * where it crosses seams, and the pieces are closed up again by following
 * the edge loop forward from each piece's exit to the next entry, as in
 * Weiler–Atherton clipping.
 */
internal class SeamClipper(private val base: NormalProjection, seams: List<Seam>) : Clipper {

    /**
     * One straight segment of a seam, from [a] to [b] (pole to tip). Its
     * left and right sides are relative to that direction.
     */
    private class Segment(val a: Vec2, val b: Vec2) {
        val meridian = a.x == b.x
        private val dLambda = sign(b.x - a.x)
        private val dPhi = sign(b.y - a.y)
        // Exact on the antimeridian: sin(π) is not quite 0 in floating point, and
        // the side test must agree with localLambda for points right on the seam.
        private val normal = if (abs(a.x) == PI) Vec3(0.0, -1.0, 0.0) else Vec3(-sin(a.x), cos(a.x), 0.0)
        private val direction = if (abs(a.x) == PI) Vec3(-1.0, 0.0, 0.0) else Vec3(cos(a.x), sin(a.x), 0.0)
        private val sinPhi = sin(a.y)
        val lo = if (meridian) min(a.y, b.y) else min(a.x, b.x)
        val hi = if (meridian) maxOf(a.y, b.y) else maxOf(a.x, b.x)

        /** Positive east of a meridian segment, north of a parallel one. */
        fun side(q: Vec3): Double = if (meridian) q dot normal else q.z - sinPhi

        /** Whether positive [side] values are on the left. */
        val positiveIsLeft = if (meridian) dPhi < 0 else dLambda > 0

        /** Where the chord from [p] to [q] crosses this segment, or null. */
        fun crossing(p: Vec3, q: Vec3, sp: Double, sq: Double): Vec2? {
            val t = sp / (sp - sq)
            val m = (p + (q - p) * t).normalized()
            return if (meridian) {
                if ((m dot direction) <= 0) return null // The other half of the great circle.
                val phi = asin(m.z.coerceIn(-1.0, 1.0))
                if (phi in lo..hi) Vec2(a.x, phi) else null
            } else {
                val lambda = localLambda(m.x, m.y)
                if (lambda in lo..hi) Vec2(lambda, a.y) else null
            }
        }

        /**
         * A vertex lying right on this segment, moved just off it to the side
         * [side] puts it on, the same test that decides crossings; null if the
         * vertex isn't on the segment. Otherwise a vertex on an interruption
         * would be drawn on whichever lobe the projection picks for that exact
         * longitude, maybe across the gap from the rest of its path.
         */
        fun nudge(lambda: Double, phi: Double, q: Vec3): Vec2? {
            val s = if (side(q) >= 0) EPS else -EPS
            return if (meridian) {
                if (abs(lambda - a.x) < ON_SEAM && phi in lo..hi) Vec2(wrapLambda(a.x + s), phi) else null
            } else {
                if (abs(phi - a.y) < ON_SEAM && lambda in lo..hi) Vec2(lambda, a.y + s) else null
            }
        }

        /** [p] moved just off the segment to its left or right side. */
        fun offset(p: Vec2, left: Boolean): Vec2 {
            val s = if (left) EPS else -EPS
            return Vec2(wrapLambda(p.x - dPhi * s), p.y + dLambda * s)
        }
    }

    /** A straight run of the edge loop in (λ, φ); along one side of [segment], if any. */
    private class Edge(val from: Vec2, val to: Vec2, val segment: Segment?, val left: Boolean) {
        val length = abs(to.x - from.x) + abs(to.y - from.y)
        var start = 0.0

        fun at(d: Double): Vec2 {
            val f = if (length == 0.0) 0.0 else d / length
            return Vec2(from.x + (to.x - from.x) * f, from.y + (to.y - from.y) * f)
        }
    }

    private sealed interface Event

    private class Point(val lambda: Double, val phi: Double, val atPole: Boolean) : Event

    /** A seam crossing: the path leaves the map at [exit] and continues at [entry]. */
    private class Crossing(
        val t: Double,
        val exit: Vec2,
        val entry: Vec2,
        val exitPos: Double,
        val entryPos: Double,
    ) : Event

    private class Piece(val entry: Double, val exit: Double, val points: List<Vec2>)

    private val segments: List<List<Segment>> = seams.map { seam -> seam.path.zipWithNext { a, b -> Segment(a, b) } }
    private val fromNorth = seams.map { it.fromNorth }
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

    override fun outline(): List<List<Vec2>> = listOf((listOf(pointAt(0.0)) + walk(0.0, perimeter)).map(::project))

    override fun line(points: List<Vec3>): List<List<Vec2>> {
        val pieces = mutableListOf(mutableListOf<Vec2>())
        for (event in events(points, closed = false)) when (event) {
            is Point -> pieces.last().add(Vec2(event.lambda, event.phi))
            is Crossing -> {
                pieces.last().add(event.exit)
                pieces.add(mutableListOf(event.entry))
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
        var current = mutableListOf(start.entry)
        for (k in 1..n) {
            when (val event = events[(first + k) % n]) {
                is Point -> current.add(Vec2(event.lambda, event.phi))
                is Crossing -> {
                    current.add(event.exit)
                    pieces += Piece(entry = start.entryPos, exit = event.exitPos, points = current)
                    start = event
                    current = mutableListOf(event.entry)
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

    /** Down the left side of seam [k], pole to tip. */
    private fun descend(k: Int) = segments[k].mapIndexed { j, s ->
        Edge(sideVertex(k, j, true), sideVertex(k, j + 1, true), s, true)
    }

    /** Back up the right side of seam [k], tip to pole. */
    private fun ascend(k: Int) = segments[k].indices.reversed().map { j ->
        Edge(sideVertex(k, j + 1, false), sideVertex(k, j, false), segments[k][j], false)
    }

    /**
     * Vertex [i] of seam [k], moved just off the seam to one side. At a
     * corner it moves off both segments, so it doesn't sit on the other
     * segment's line (where it could fall on the wrong side, in another lobe).
     */
    private fun sideVertex(k: Int, i: Int, left: Boolean): Vec2 {
        val seam = segments[k]
        var p = if (i < seam.size) seam[i].a else seam[i - 1].b
        if (i > 0) p = seam[i - 1].offset(p, left)
        if (i < seam.size) p = seam[i].offset(p, left)
        return p
    }

    private fun buildEdgeLoop(): List<Edge> {
        val south = -PI / 2
        val north = PI / 2
        val loop = mutableListOf<Edge>()
        val leftEdge = descend(0) // East side of the antimeridian, north to south.
        val rightEdge = ascend(0) // West side, south to north.

        // Bottom: west to east, around interruptions that start at the south pole.
        var lambda = leftEdge.last().to.x
        for (k in segments.indices.drop(1).filter { !fromNorth[it] }.sortedBy { segments[it][0].a.x }) {
            val down = descend(k)
            val up = ascend(k)
            loop += Edge(Vec2(lambda, south), down.first().from, null, false)
            loop += down
            loop += up
            lambda = up.last().to.x
        }
        loop += Edge(Vec2(lambda, south), rightEdge.first().from, null, false)
        loop += rightEdge

        // Top: east to west, around interruptions that start at the north pole.
        lambda = rightEdge.last().to.x
        for (k in segments.indices.drop(1).filter { fromNorth[it] }.sortedByDescending { segments[it][0].a.x }) {
            val down = descend(k)
            val up = ascend(k)
            loop += Edge(Vec2(lambda, north), down.first().from, null, false)
            loop += down
            loop += up
            lambda = up.last().to.x
        }
        loop += Edge(Vec2(lambda, north), leftEdge.first().from, null, false)
        loop += leftEdge
        return loop
    }

    /** Position along the edge loop of [point], on one side of [segment]. */
    private fun position(segment: Segment, left: Boolean, point: Vec2): Double {
        val edge = edges.first { it.segment === segment && it.left == left }
        val d = if (segment.meridian) abs(point.y - edge.from.y) else abs(point.x - edge.from.x)
        return edge.start + d.coerceIn(0.0, edge.length)
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
        val count = if (closed) pts.size else pts.size - 1
        for (i in pts.indices) {
            val a = pts[i]
            val atPole = abs(a.z) > 1 - POLE_EPS
            var lambda = localLambda(a.x, a.y)
            var phi = asin(a.z.coerceIn(-1.0, 1.0))
            if (!atPole) {
                for (seam in segments) for (segment in seam) {
                    segment.nudge(lambda, phi, a)?.let { lambda = it.x; phi = it.y }
                }
            }
            raw += Point(lambda, phi, atPole)
            if (i < count) raw += crossings(a, pts[(i + 1) % pts.size])
        }
        return splitPoles(raw, closed)
    }

    private fun crossings(a: Vec3, b: Vec3): List<Crossing> {
        val found = mutableListOf<Crossing>()
        for (seam in segments) for (segment in seam) {
            val sa = segment.side(a)
            val sb = segment.side(b)
            if ((sa < 0) == (sb < 0)) continue
            val at = segment.crossing(a, b, sa, sb) ?: continue
            val exitLeft = (sa >= 0) == segment.positiveIsLeft
            found += Crossing(
                t = sa / (sa - sb),
                exit = segment.offset(at, exitLeft),
                entry = segment.offset(at, !exitLeft),
                exitPos = position(segment, exitLeft, at),
                entryPos = position(segment, !exitLeft, at),
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
                    is Crossing -> return if (step < 0) e.entry.x else e.exit.x
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

        /** How close to a seam a vertex counts as lying on it. */
        const val ON_SEAM = 1e-12
        const val POLE_EPS = 1e-12
    }
}

/** An area of the sphere bounded by two meridians and two parallels, in radians (normal aspect). */
data class LonLatBox(val minLambda: Double, val maxLambda: Double, val minPhi: Double, val maxPhi: Double) {
    init {
        require(maxLambda - minLambda in 0.0..PI) { "A box spans at most 180° of longitude, without wrapping" }
    }
}

/**
 * Keeps only what lies inside [box], clipping in 3D against its four sides
 * as half-spaces: two meridian planes and two parallel (latitude) planes.
 * The box spans less than 180° of longitude, so the two meridian half-spaces
 * meet in a single lune and no wrapping is involved.
 */
internal class BoxClipper(private val base: NormalProjection, private val box: LonLatBox) : Clipper {

    /** A side as a half-space: inside when q·normal ≥ offset. */
    private class Side(val normal: Vec3, val offset: Double) {
        fun value(q: Vec3) = (q dot normal) - offset
        fun value(qx: Double, qy: Double, qz: Double) = qx * normal.x + qy * normal.y + qz * normal.z - offset
    }

    private val sides = listOf(
        Side(Vec3(-sin(box.minLambda), cos(box.minLambda), 0.0), 0.0), // East of minLambda.
        Side(Vec3(sin(box.maxLambda), -cos(box.maxLambda), 0.0), 0.0), // West of maxLambda.
        Side(Vec3(0.0, 0.0, 1.0), sin(box.minPhi)), // North of minPhi.
        Side(Vec3(0.0, 0.0, -1.0), -sin(box.maxPhi)), // South of maxPhi.
    )

    override fun isVisible(qx: Double, qy: Double, qz: Double): Boolean = sides.all { it.value(qx, qy, qz) >= 0 }

    override fun outline(): List<List<Vec2>> {
        val corners = listOf(
            Vec2(box.minLambda, box.minPhi),
            Vec2(box.maxLambda, box.minPhi),
            Vec2(box.maxLambda, box.maxPhi),
            Vec2(box.minLambda, box.maxPhi),
        )
        return listOf(densifyFlat(corners + corners.first()).dropLast(1).map { base.project(it.x, it.y) })
    }

    override fun line(points: List<Vec3>): List<List<Vec2>> {
        val pieces = mutableListOf<MutableList<Vec3>>()
        var current: MutableList<Vec3>? = null
        for (i in 0 until points.size - 1) {
            val clipped = clipSegment(points[i], points[i + 1])
            if (clipped == null) {
                current = null
                continue
            }
            val (a, b) = clipped
            val piece = current?.takeIf { it.last() == a } ?: mutableListOf(a).also { pieces += it }
            piece.add(b)
            // When the far end was clipped, the path leaves the box here.
            current = if (b == points[i + 1]) piece else null
        }
        return pieces.filter { it.size >= 2 }.map { piece ->
            densifyFlat(piece.map(::toFlat)).map { base.project(it.x, it.y) }
        }
    }

    override fun ring(points: List<Vec3>): List<List<Vec2>> {
        var poly = points
        for (side in sides) {
            if (poly.isEmpty()) break
            poly = clipPolygon(poly, side)
        }
        if (poly.size < 3) return emptyList()
        val flat = poly.map(::toFlat)
        return listOf(densifyFlat(flat + flat.first()).dropLast(1).map { base.project(it.x, it.y) })
    }

    /** Sutherland–Hodgman against one side. */
    private fun clipPolygon(poly: List<Vec3>, side: Side): List<Vec3> {
        val out = mutableListOf<Vec3>()
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            val va = side.value(a)
            val vb = side.value(b)
            if (va >= 0) out += a
            if ((va >= 0) != (vb >= 0)) out += (a + (b - a) * (va / (va - vb))).normalized()
        }
        return out
    }

    /** The part of segment [a]–[b] inside the box (Liang–Barsky on the chord), or null. */
    private fun clipSegment(a: Vec3, b: Vec3): Pair<Vec3, Vec3>? {
        var t0 = 0.0
        var t1 = 1.0
        for (side in sides) {
            val va = side.value(a)
            val vb = side.value(b)
            if (va < 0 && vb < 0) return null
            if (va >= 0 && vb >= 0) continue
            val t = va / (va - vb)
            if (va < 0) t0 = maxOf(t0, t) else t1 = min(t1, t)
        }
        if (t0 > t1) return null
        val start = if (t0 == 0.0) a else (a + (b - a) * t0).normalized()
        val end = if (t1 == 1.0) b else (a + (b - a) * t1).normalized()
        return start to end
    }

    private fun toFlat(q: Vec3) = Vec2(localLambda(q.x, q.y), asin(q.z.coerceIn(-1.0, 1.0)))

    /** Adds points every degree in (λ, φ), so cuts along the box's parallels follow them. */
    private fun densifyFlat(points: List<Vec2>): List<Vec2> {
        val out = mutableListOf<Vec2>()
        for (i in points.indices) {
            val a = points[i]
            out += a
            if (i == points.lastIndex) break
            val b = points[i + 1]
            val steps = ceil(maxOf(abs(b.x - a.x), abs(b.y - a.y)) / STEP_RAD).toInt()
            for (s in 1 until steps) {
                val f = s.toDouble() / steps
                out += Vec2(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
            }
        }
        return out
    }
}

private fun wrapLambda(l: Double): Double = when {
    l > PI -> l - 2 * PI
    l < -PI -> l + 2 * PI
    else -> l
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

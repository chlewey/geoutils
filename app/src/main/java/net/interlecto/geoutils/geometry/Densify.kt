package net.interlecto.geoutils.geometry

import kotlin.math.ceil

/** Default spacing for densified paths: one degree of arc. */
const val DEFAULT_MAX_STEP_RAD = Math.PI / 180

/**
 * Converts [points] to unit vectors, inserting points along great circles so
 * that no step is longer than [maxStepRad]. With [closed], the edge from the
 * last point back to the first is densified too; a repeated closing point (as
 * KML rings have) is dropped, and the result does not repeat the first point.
 */
fun densify(
    points: List<GeoPoint>,
    closed: Boolean,
    maxStepRad: Double = DEFAULT_MAX_STEP_RAD,
): List<Vec3> {
    val vectors = points.map { it.toVec3() }.toMutableList()
    if (closed && vectors.size > 1 && vectors.first() == vectors.last()) {
        vectors.removeAt(vectors.lastIndex)
    }
    val result = ArrayList<Vec3>(vectors.size * 2)
    for (i in vectors.indices) {
        val a = vectors[i]
        result.add(a)
        val b = when {
            i < vectors.lastIndex -> vectors[i + 1]
            closed && vectors.size > 2 -> vectors[0]
            else -> continue
        }
        val steps = ceil(a.angleTo(b) / maxStepRad).toInt()
        for (s in 1 until steps) {
            result.add(Vec3.slerp(a, b, s.toDouble() / steps))
        }
    }
    return result
}

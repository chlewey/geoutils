package net.interlecto.geoutils.geometry

import kotlin.math.PI
import kotlin.math.abs

/**
 * The formulas of a projection in its normal aspect: centered on (0°, 0°)
 * with north up. Angles are in radians. [RotatedProjection] turns this into a
 * [Projection] aimed at any center and azimuth.
 */
interface NormalProjection {

    /** Extent of the map, in projection units (unit sphere). */
    val bounds: Bounds

    /** How the sphere is cut or clipped to fit the map. */
    val clip: Clip

    /** Projects ([lambda], [phi]) into `out[0]` (x) and `out[1]` (y). */
    fun forward(lambda: Double, phi: Double, out: DoubleArray)

    /**
     * Writes the ([lambda], [phi]) of map point ([x], [y]) into [out].
     * Returns false when the point is outside the map.
     */
    fun inverse(x: Double, y: Double, out: DoubleArray): Boolean
}

/**
 * A cut in the map, as (λ, φ) vertices in radians (normal aspect) starting
 * at a pole. Each segment runs along a meridian or a parallel, so a cut can
 * step sideways, as Goode's "extended Greenland" interruption does.
 */
data class Seam(val path: List<Vec2>) {
    init {
        require(path.size >= 2) { "A seam needs at least two points" }
        require(abs(abs(path[0].y) - PI / 2) < 1e-12) { "A seam starts at a pole" }
        for (i in 1 until path.size) {
            require(path[i].x == path[i - 1].x || path[i].y == path[i - 1].y) {
                "Seam segments run along meridians or parallels"
            }
        }
    }

    val fromNorth: Boolean get() = path[0].y > 0

    companion object {
        val ANTIMERIDIAN = Seam(listOf(Vec2(PI, PI / 2), Vec2(PI, -PI / 2)))

        /** A cut along meridian [lambda] from the north or south pole to latitude [tipPhi]. */
        fun meridian(lambda: Double, fromNorth: Boolean, tipPhi: Double) =
            Seam(listOf(Vec2(lambda, if (fromNorth) PI / 2 else -PI / 2), Vec2(lambda, tipPhi)))
    }
}

sealed interface Clip {

    /**
     * Only the cap around the center is drawn: points whose cosine of the
     * angular distance from the center is at least [minCos]. 0 is a
     * hemisphere (orthographic); just above -1 is the whole sphere minus a
     * small hole at the antipode (azimuthal equidistant, equal-area).
     */
    data class Cap(val minCos: Double) : Clip

    /**
     * The whole sphere is drawn, cut along [seams]. The first seam must be
     * the antimeridian; others (interruptions) start at a pole and must not
     * cross the antimeridian.
     */
    data class Seams(val seams: List<Seam> = listOf(Seam.ANTIMERIDIAN)) : Clip {
        init {
            require(seams.firstOrNull() == Seam.ANTIMERIDIAN) { "The first seam must be the antimeridian" }
        }
    }
}

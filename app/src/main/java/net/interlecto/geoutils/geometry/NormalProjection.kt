package net.interlecto.geoutils.geometry

import kotlin.math.PI

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

/** A cut along the meridian [lambda] from [phiMin] to [phiMax], in radians, normal aspect. */
data class Seam(val lambda: Double, val phiMin: Double, val phiMax: Double) {
    companion object {
        val ANTIMERIDIAN = Seam(PI, -PI / 2, PI / 2)
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
     * the antimeridian; others (interruptions) must touch a pole.
     */
    data class Seams(val seams: List<Seam> = listOf(Seam.ANTIMERIDIAN)) : Clip {
        init {
            require(seams.firstOrNull() == Seam.ANTIMERIDIAN) { "The first seam must be the antimeridian" }
        }
    }
}

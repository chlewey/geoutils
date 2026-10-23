package net.interlecto.geoutils.geometry

import kotlin.math.PI
import kotlin.math.abs

/**
 * Equirectangular (plate carrée) projection: x = λ, y = φ in radians.
 *
 * Used as the identity/reference projection: it is the layout of the input
 * bitmaps. Lines are split where they cross the antimeridian; rings are not
 * cut yet (general boundary cutting comes with milestone 2).
 */
class Equirectangular : Projection {

    override val bounds = Bounds(-PI, -PI / 2, PI, PI / 2)

    override fun forward(lon: Double, lat: Double, out: DoubleArray): Boolean {
        out[0] = Math.toRadians(GeoPoint.normalizeLongitude(lon))
        out[1] = Math.toRadians(lat)
        return true
    }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean {
        if (x < bounds.minX || x > bounds.maxX || y < bounds.minY || y > bounds.maxY) return false
        out[0] = Math.toDegrees(x)
        out[1] = Math.toDegrees(y)
        return true
    }

    override fun outline(): List<Vec2> = listOf(
        Vec2(bounds.minX, bounds.minY),
        Vec2(bounds.maxX, bounds.minY),
        Vec2(bounds.maxX, bounds.maxY),
        Vec2(bounds.minX, bounds.maxY),
    )

    override fun projectLine(points: List<GeoPoint>): List<List<Vec2>> {
        val pieces = mutableListOf<MutableList<Vec2>>()
        var current = mutableListOf<Vec2>()
        for (v in densify(points, closed = false)) {
            val p = project(v)
            if (current.isNotEmpty() && abs(p.x - current.last().x) > PI) {
                pieces.add(current)
                current = mutableListOf()
            }
            current.add(p)
        }
        pieces.add(current)
        return pieces.filter { it.size >= 2 }
    }

    override fun projectRing(ring: List<GeoPoint>): List<Vec2>? =
        densify(ring, closed = true).map(::project).takeIf { it.size >= 3 }

    private fun project(v: Vec3): Vec2 {
        val g = v.toGeoPoint()
        return Vec2(Math.toRadians(g.lon), Math.toRadians(g.lat))
    }
}

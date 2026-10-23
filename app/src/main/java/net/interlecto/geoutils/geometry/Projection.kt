package net.interlecto.geoutils.geometry

/**
 * A map projection of the unit sphere onto the plane.
 *
 * Projection units are those of the unit sphere (Earth radius = 1), with y
 * pointing north. Hot paths ([forward], [inverse]) write into an output array
 * instead of allocating, since they run once per pixel.
 */
interface Projection {

    /** Extent of the whole map, in projection units. */
    val bounds: Bounds

    /**
     * Projects ([lon], [lat]) in degrees into `out[0]` (x) and `out[1]` (y).
     * Returns false, leaving [out] undefined, when the point isn't drawn
     * (for example on the far side of an orthographic globe).
     */
    fun forward(lon: Double, lat: Double, out: DoubleArray): Boolean

    /**
     * Inverse-projects ([x], [y]) into `out[0]` (longitude) and `out[1]`
     * (latitude), in degrees. Returns false when the point is outside the map.
     */
    fun inverse(x: Double, y: Double, out: DoubleArray): Boolean

    /** Closed boundary of the map, in projection units. */
    fun outline(): List<Vec2>

    /**
     * Projects a polyline, following great circles between its vertices.
     * Returns the visible pieces; empty when nothing is visible.
     */
    fun projectLine(points: List<GeoPoint>): List<List<Vec2>>

    /**
     * Projects a closed ring (polygon boundary), clipped to the map. The
     * result is one closed path, or null when the ring is entirely hidden.
     */
    fun projectRing(ring: List<GeoPoint>): List<Vec2>?
}

fun Projection.forward(point: GeoPoint): Vec2? {
    val out = DoubleArray(2)
    return if (forward(point.lon, point.lat, out)) Vec2(out[0], out[1]) else null
}

fun Projection.inverse(point: Vec2): GeoPoint? {
    val out = DoubleArray(2)
    return if (inverse(point.x, point.y, out)) GeoPoint(out[0], out[1]) else null
}

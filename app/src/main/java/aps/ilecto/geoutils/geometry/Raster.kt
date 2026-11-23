package net.interlecto.geoutils.geometry

import java.util.stream.IntStream
import kotlin.math.floor

/** A bitmap as packed ARGB pixels, row by row from the top-left corner. */
class RasterImage(val width: Int, val height: Int, val pixels: IntArray = IntArray(width * height)) {
    init {
        require(pixels.size == width * height) { "Expected ${width * height} pixels, got ${pixels.size}" }
    }
}

/** Reprojects equirectangular bitmaps by inverse mapping. */
object RasterReprojector {

    /**
     * For each pixel of a [viewport]-sized image, inverse-projects its center
     * and samples [source] (an equirectangular image covering the whole
     * sphere) there. Pixels outside the map stay transparent. Rows are
     * processed in parallel.
     */
    fun reproject(source: RasterImage, projection: Projection, viewport: Viewport): RasterImage {
        val out = RasterImage(viewport.width, viewport.height)
        IntStream.range(0, viewport.height).parallel().forEach { row ->
            val lonLat = DoubleArray(2)
            val y = viewport.toProjectedY(row + 0.5)
            val offset = row * viewport.width
            for (col in 0 until viewport.width) {
                if (projection.inverse(viewport.toProjectedX(col + 0.5), y, lonLat)) {
                    out.pixels[offset + col] = sample(source, lonLat[0], lonLat[1])
                }
            }
        }
        return out
    }

    /**
     * Bilinear sample of an equirectangular [source] at ([lon], [lat]) in
     * degrees. Longitude wraps around the antimeridian; latitude is clamped
     * at the poles.
     */
    fun sample(source: RasterImage, lon: Double, lat: Double): Int {
        val w = source.width
        val h = source.height
        val u = (lon + 180.0) / 360.0 * w - 0.5
        val v = (90.0 - lat) / 180.0 * h - 0.5
        val x0 = floor(u)
        val y0 = floor(v)
        val fx = u - x0
        val fy = v - y0
        val xa = Math.floorMod(x0.toInt(), w)
        val xb = Math.floorMod(x0.toInt() + 1, w)
        val ya = y0.toInt().coerceIn(0, h - 1)
        val yb = (y0.toInt() + 1).coerceIn(0, h - 1)
        val p = source.pixels
        return blend(
            blend(p[ya * w + xa], p[ya * w + xb], fx),
            blend(p[yb * w + xa], p[yb * w + xb], fx),
            fy,
        )
    }

    private fun blend(c0: Int, c1: Int, t: Double): Int {
        if (c0 == c1) return c0
        var result = 0
        for (shift in 0..24 step 8) {
            val a = (c0 ushr shift) and 0xFF
            val b = (c1 ushr shift) and 0xFF
            result = result or ((a + (b - a) * t + 0.5).toInt() shl shift)
        }
        return result
    }
}

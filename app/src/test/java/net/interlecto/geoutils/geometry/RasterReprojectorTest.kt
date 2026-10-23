package net.interlecto.geoutils.geometry

import org.junit.Assert.assertEquals
import org.junit.Test

class RasterReprojectorTest {

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    /** Western hemisphere red, eastern hemisphere blue. */
    private val source = RasterImage(36, 18).apply {
        for (row in 0 until height) for (col in 0 until width) {
            pixels[row * width + col] = if (col < width / 2) red else blue
        }
    }

    @Test
    fun orthographicFromNullIslandShowsWestOnTheLeft() {
        val viewport = Viewport(Orthographic(GeoPoint(0.0, 0.0)).bounds, 101, 101)
        val out = RasterReprojector.reproject(source, Orthographic(GeoPoint(0.0, 0.0)), viewport)
        assertEquals(red, out.pixels[50 * 101 + 20])
        assertEquals(blue, out.pixels[50 * 101 + 80])
        assertEquals(0, out.pixels[0]) // corner: outside the globe
    }

    @Test
    fun samplingWrapsAtTheAntimeridian() {
        // Halfway between the last (blue) and first (red) columns.
        val c = RasterReprojector.sample(source, 180.0, 0.0)
        assertEquals(0xFF, c ushr 24)
        assertEquals(0x80, (c shr 16) and 0xFF)
        assertEquals(0x80, c and 0xFF)
    }

    @Test
    fun equirectangularIsTheIdentity() {
        val viewport = Viewport(Equirectangular().bounds, 36, 18)
        val out = RasterReprojector.reproject(source, Equirectangular(), viewport)
        assertEquals(source.pixels.toList(), out.pixels.toList())
    }
}

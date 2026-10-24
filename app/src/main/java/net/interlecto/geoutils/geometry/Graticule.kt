package net.interlecto.geoutils.geometry

/** Meridians and parallels every [stepDeg] degrees, as polylines. */
object Graticule {

    private const val SAMPLE_DEG = 2

    fun lines(stepDeg: Int = 30): List<List<GeoPoint>> {
        val meridians = (-180 until 180 step stepDeg).map { lon ->
            (-90..90 step SAMPLE_DEG).map { lat -> GeoPoint(lon.toDouble(), lat.toDouble()) }
        }
        val parallels = (-90 + stepDeg until 90 step stepDeg).map { lat ->
            // Sample to 180 inclusive so the parallel closes on itself.
            (-180..180 step SAMPLE_DEG).map { lon -> GeoPoint(lon.toDouble(), lat.toDouble()) }
        }
        return meridians + parallels
    }

    /** Tilt of the Earth's axis, which places the tropics and the polar circles. */
    const val OBLIQUITY_DEG = 23.436

    /** The tropics of Cancer and Capricorn and the Arctic and Antarctic circles. */
    fun specialParallels(): List<List<GeoPoint>> =
        listOf(OBLIQUITY_DEG, -OBLIQUITY_DEG, 90 - OBLIQUITY_DEG, OBLIQUITY_DEG - 90).map { lat ->
            (-180..180 step SAMPLE_DEG).map { lon -> GeoPoint(lon.toDouble(), lat) }
        }
}

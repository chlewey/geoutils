package net.interlecto.geoutils.domain.model

import net.interlecto.geoutils.geometry.GeoPoint

/** The parts of a KML file the app uses: placemarks with geometry and style. */
data class KmlDocument(
    val name: String?,
    val placemarks: List<Placemark>,
)

data class Placemark(
    val name: String?,
    val description: String?,
    val geometry: Geometry,
    val style: FeatureStyle = FeatureStyle.DEFAULT,
)

sealed interface Geometry {
    data class Point(val position: GeoPoint) : Geometry

    /** A polyline; KML `LinearRing`s outside a polygon are read as closed lines. */
    data class LineString(val points: List<GeoPoint>) : Geometry

    data class Polygon(
        val outer: List<GeoPoint>,
        val holes: List<List<GeoPoint>> = emptyList(),
    ) : Geometry

    data class Multi(val parts: List<Geometry>) : Geometry
}

/**
 * Drawing style. Colors are packed ARGB; widths are in pixels for a
 * 1024-pixel map and scale with the output size.
 */
data class FeatureStyle(
    val lineColor: Int = 0xFFFFCC00.toInt(),
    val lineWidth: Float = 1.5f,
    val fillColor: Int = 0x40FFCC00,
    val fill: Boolean = true,
    val outline: Boolean = true,
    val iconColor: Int = 0xFFFF3B30.toInt(),
    val iconScale: Float = 1f,
) {
    companion object {
        val DEFAULT = FeatureStyle()
    }
}

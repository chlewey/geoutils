package net.interlecto.geoutils.domain.model

import net.interlecto.geoutils.geometry.RasterImage
import net.interlecto.geoutils.geometry.Vec2

/**
 * A rendered map, ready to draw or export. All coordinates are in pixels of a
 * [width] × [height] image, y down. The same scene is drawn on screen,
 * exported to PNG and written as SVG.
 */
data class MapScene(
    val width: Int,
    val height: Int,
    /** Reprojected base map, [width] × [height], transparent outside the map. */
    val raster: RasterImage?,
    /** Closed boundary of the map: one loop, or several for maps in parts. */
    val outlines: List<List<Vec2>>,
    val graticule: List<List<Vec2>>,
    val shapes: List<SceneShape>,
    /** Multiplier for [FeatureStyle] widths at this output size. */
    val strokeScale: Float,
    val attribution: String?,
)

sealed interface SceneShape {
    val name: String?
    val style: FeatureStyle

    /** One or more open or closed paths. A polygon's holes are extra closed parts, filled even-odd. */
    data class Path(
        override val name: String?,
        override val style: FeatureStyle,
        val parts: List<List<Vec2>>,
        val closed: Boolean,
    ) : SceneShape

    data class Marker(
        override val name: String?,
        override val style: FeatureStyle,
        val at: Vec2,
    ) : SceneShape
}

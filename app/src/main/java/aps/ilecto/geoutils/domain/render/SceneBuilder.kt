package aps.ilecto.geoutils.domain.render

import aps.ilecto.geoutils.domain.model.Geometry
import aps.ilecto.geoutils.domain.model.KmlDocument
import aps.ilecto.geoutils.domain.model.MapScene
import aps.ilecto.geoutils.domain.model.Placemark
import aps.ilecto.geoutils.domain.model.SceneShape
import aps.ilecto.geoutils.geometry.Graticule
import aps.ilecto.geoutils.geometry.Projection
import aps.ilecto.geoutils.geometry.RasterImage
import aps.ilecto.geoutils.geometry.RasterReprojector
import aps.ilecto.geoutils.geometry.Vec2
import aps.ilecto.geoutils.geometry.Viewport
import aps.ilecto.geoutils.geometry.forward
import kotlin.math.roundToInt

/** Projects a base map and KML features into a [MapScene]. */
object SceneBuilder {

    /** Width at which [aps.ilecto.geoutils.domain.model.FeatureStyle] widths apply as is. */
    private const val REFERENCE_SIZE = 1024f

    /**
     * @param size width of the output in pixels; the height follows the
     *   projection's aspect ratio.
     * @param baseMap equirectangular source image, if any.
     */
    fun build(
        projection: Projection,
        size: Int,
        baseMap: RasterImage?,
        kml: KmlDocument?,
        showGraticule: Boolean,
        attribution: String? = null,
        showSpecialParallels: Boolean = false,
    ): MapScene {
        val bounds = projection.bounds
        val height = (size * bounds.height / bounds.width).roundToInt()
        val viewport = Viewport(bounds, size, height, padding = size * 0.01)
        val toPixels = { path: List<Vec2> -> path.map(viewport::toPixel) }

        return MapScene(
            width = size,
            height = height,
            raster = baseMap?.let { RasterReprojector.reproject(it, projection, viewport) },
            outlines = projection.outline().map(toPixels),
            graticule = if (showGraticule) {
                Graticule.lines().flatMap(projection::projectLine).map(toPixels)
            } else {
                emptyList()
            },
            specialParallels = if (showSpecialParallels) {
                Graticule.specialParallels().flatMap(projection::projectLine).map(toPixels)
            } else {
                emptyList()
            },
            shapes = kml?.placemarks.orEmpty().flatMap { placemark ->
                shapesOf(placemark, placemark.geometry, projection, viewport)
            },
            strokeScale = size / REFERENCE_SIZE,
            attribution = attribution,
        )
    }

    private fun shapesOf(
        placemark: Placemark,
        geometry: Geometry,
        projection: Projection,
        viewport: Viewport,
    ): List<SceneShape> = when (geometry) {
        is Geometry.Point -> listOfNotNull(
            projection.forward(geometry.position)?.let {
                SceneShape.Marker(placemark.name, placemark.style, viewport.toPixel(it))
            },
        )

        is Geometry.LineString -> projection.projectLine(geometry.points)
            .takeIf { it.isNotEmpty() }
            ?.let { pieces ->
                SceneShape.Path(placemark.name, placemark.style, pieces.map { it.map(viewport::toPixel) }, closed = false)
            }
            .let(::listOfNotNull)

        is Geometry.Polygon -> {
            val outer = projection.projectRing(geometry.outer)
            if (outer.isEmpty()) {
                emptyList()
            } else {
                val rings = outer + geometry.holes.flatMap(projection::projectRing)
                listOf(
                    SceneShape.Path(placemark.name, placemark.style, rings.map { it.map(viewport::toPixel) }, closed = true),
                )
            }
        }

        is Geometry.Multi -> geometry.parts.flatMap { shapesOf(placemark, it, projection, viewport) }
    }
}

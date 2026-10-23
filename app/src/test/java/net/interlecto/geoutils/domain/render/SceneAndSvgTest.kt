package net.interlecto.geoutils.domain.render

import net.interlecto.geoutils.domain.model.Geometry
import net.interlecto.geoutils.domain.model.KmlDocument
import net.interlecto.geoutils.domain.model.Placemark
import net.interlecto.geoutils.domain.model.SceneShape
import net.interlecto.geoutils.geometry.GeoPoint
import net.interlecto.geoutils.geometry.Orthographic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SceneAndSvgTest {

    private val kml = KmlDocument(
        name = "Test",
        placemarks = listOf(
            Placemark("Near <side> & visible", null, Geometry.Point(GeoPoint(10.0, 10.0))),
            Placemark("Far side", null, Geometry.Point(GeoPoint(180.0, 0.0))),
            Placemark(
                "Box",
                null,
                Geometry.Polygon(
                    listOf(GeoPoint(-10.0, -10.0), GeoPoint(10.0, -10.0), GeoPoint(10.0, 10.0), GeoPoint(-10.0, -10.0)),
                ),
            ),
        ),
    )

    private val scene = SceneBuilder.build(
        projection = Orthographic(GeoPoint(0.0, 0.0)),
        size = 512,
        baseMap = null,
        kml = kml,
        showGraticule = true,
        attribution = "Test data",
    )

    @Test
    fun sceneIsSquareAndDropsHiddenFeatures() {
        assertEquals(512, scene.width)
        assertEquals(512, scene.height)
        assertEquals(listOf("Near <side> & visible", "Box"), scene.shapes.map { it.name })
        assertTrue(scene.graticule.isNotEmpty())
    }

    @Test
    fun visiblePointLandsUpAndRightOfCenter() {
        val marker = scene.shapes[0] as SceneShape.Marker
        assertTrue(marker.at.x > 256)
        assertTrue(marker.at.y < 256)
    }

    @Test
    fun svgIsEscapedAndLocaleIndependent() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("es-CO"))
        try {
            val svg = SvgWriter.write(scene, "data:image/jpeg;base64,AAAA", title = "A & B")
            assertTrue(svg.contains("""viewBox="0 0 512 512""""))
            assertTrue(svg.contains("<title>A &amp; B</title>"))
            assertTrue(svg.contains("Near &lt;side&gt; &amp; visible"))
            assertTrue(svg.contains("""href="data:image/jpeg;base64,AAAA""""))
            assertTrue(svg.contains("fill-rule=\"evenodd\""))
            assertFalse(Regex("""\d,\d+,\d""").containsMatchIn(svg.substringAfter("<g id=\"features\"")))
        } finally {
            Locale.setDefault(previous)
        }
    }
}

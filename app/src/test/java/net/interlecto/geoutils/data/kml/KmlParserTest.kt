package net.interlecto.geoutils.data.kml

import net.interlecto.geoutils.domain.model.FeatureStyle
import net.interlecto.geoutils.domain.model.Geometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KmlParserTest {

    private val sample = """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Document>
            <name>Sample</name>
            <Style id="red-line">
              <LineStyle><color>ff0000ff</color><width>3</width></LineStyle>
            </Style>
            <Style id="hollow">
              <PolyStyle><color>7f00ff00</color><fill>0</fill></PolyStyle>
            </Style>
            <StyleMap id="hollow-map">
              <Pair><key>normal</key><styleUrl>#hollow</styleUrl></Pair>
              <Pair><key>highlight</key><styleUrl>#red-line</styleUrl></Pair>
            </StyleMap>
            <Folder>
              <Placemark>
                <name>Bogotá</name>
                <Point><coordinates>-74.08,4.6,2600</coordinates></Point>
              </Placemark>
              <Placemark>
                <name>Route</name>
                <styleUrl>#red-line</styleUrl>
                <LineString><coordinates>
                  -74.08,4.6 -3.7,40.4
                </coordinates></LineString>
              </Placemark>
            </Folder>
            <Placemark>
              <name>Square with hole</name>
              <styleUrl>#hollow-map</styleUrl>
              <Polygon>
                <outerBoundaryIs><LinearRing><coordinates>
                  0,0 10,0 10,10 0,10 0,0
                </coordinates></LinearRing></outerBoundaryIs>
                <innerBoundaryIs><LinearRing><coordinates>
                  2,2 4,2 4,4 2,2
                </coordinates></LinearRing></innerBoundaryIs>
              </Polygon>
            </Placemark>
            <Placemark>
              <name>Inline style</name>
              <styleUrl>#red-line</styleUrl>
              <Style><LineStyle><width>5</width></LineStyle></Style>
              <MultiGeometry>
                <Point><coordinates>1,1</coordinates></Point>
                <LineString><coordinates>1,1 2,2</coordinates></LineString>
              </MultiGeometry>
            </Placemark>
            <Placemark>
              <name>No geometry</name>
            </Placemark>
          </Document>
        </kml>
    """.trimIndent()

    private val doc = KmlParser.parse(sample.byteInputStream())

    @Test
    fun readsDocumentNameAndPlacemarksWithGeometry() {
        assertEquals("Sample", doc.name)
        assertEquals(listOf("Bogotá", "Route", "Square with hole", "Inline style"), doc.placemarks.map { it.name })
    }

    @Test
    fun readsPointsLinesAndPolygonsWithHoles() {
        val point = doc.placemarks[0].geometry as Geometry.Point
        assertEquals(-74.08, point.position.lon, 1e-12)
        assertEquals(4.6, point.position.lat, 1e-12)

        val line = doc.placemarks[1].geometry as Geometry.LineString
        assertEquals(2, line.points.size)

        val polygon = doc.placemarks[2].geometry as Geometry.Polygon
        assertEquals(5, polygon.outer.size)
        assertEquals(1, polygon.holes.size)

        val multi = doc.placemarks[3].geometry as Geometry.Multi
        assertEquals(2, multi.parts.size)
    }

    @Test
    fun resolvesSharedStylesStyleMapsAndInlineOverrides() {
        assertEquals(FeatureStyle.DEFAULT, doc.placemarks[0].style)

        val route = doc.placemarks[1].style
        assertEquals(0xFFFF0000.toInt(), route.lineColor)
        assertEquals(3f, route.lineWidth)

        val square = doc.placemarks[2].style
        assertEquals(0x7F00FF00, square.fillColor)
        assertFalse(square.fill)

        val inline = doc.placemarks[3].style
        assertEquals(0xFFFF0000.toInt(), inline.lineColor)
        assertEquals(5f, inline.lineWidth)
    }

    @Test
    fun convertsKmlColorsFromAbgr() {
        assertEquals(0x80112233.toInt(), KmlParser.parseColor("80332211"))
    }

    @Test
    fun worksWithoutNamespace() {
        val doc = KmlParser.parse(
            "<kml><Placemark><Point><coordinates>5,6</coordinates></Point></Placemark></kml>".byteInputStream(),
        )
        assertEquals(1, doc.placemarks.size)
    }

    @Test
    fun skipsInvalidCoordinates() {
        val doc = KmlParser.parse(
            """<kml><Placemark><LineString><coordinates>
                0,0 nonsense 1,95 2,2 3,3
            </coordinates></LineString></Placemark></kml>""".byteInputStream(),
        )
        assertEquals(3, (doc.placemarks.single().geometry as Geometry.LineString).points.size)
    }

    @Test(expected = KmlParser.KmlException::class)
    fun rejectsNonKml() {
        KmlParser.parse("<svg/>".byteInputStream())
    }

    @Test
    fun rejectsDoctypeEntities() {
        val xxe = """<?xml version="1.0"?>
            <!DOCTYPE kml [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <kml><Placemark><name>&x;</name><Point><coordinates>0,0</coordinates></Point></Placemark></kml>"""
        val result = runCatching { KmlParser.parse(xxe.byteInputStream()) }
        assertTrue(result.isFailure || result.getOrNull()?.placemarks?.single()?.name.isNullOrEmpty())
    }
}

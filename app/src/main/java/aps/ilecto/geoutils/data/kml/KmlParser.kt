package net.interlecto.geoutils.data.kml

import net.interlecto.geoutils.domain.model.FeatureStyle
import net.interlecto.geoutils.domain.model.Geometry
import net.interlecto.geoutils.domain.model.KmlDocument
import net.interlecto.geoutils.domain.model.Placemark
import net.interlecto.geoutils.geometry.GeoPoint
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads placemarks (Point, LineString, LinearRing, Polygon, MultiGeometry)
 * and basic styles (LineStyle, PolyStyle, IconStyle color/scale; shared
 * styles via styleUrl and StyleMap) from KML. Unknown elements are ignored.
 *
 * Elements are matched by local name, so KML 2.0–2.2 namespaces (or none)
 * all work.
 */
object KmlParser {

    class KmlException(message: String, cause: Throwable? = null) : Exception(message, cause)

    fun parse(input: InputStream): KmlDocument {
        val root = try {
            newDocumentBuilderFactory().newDocumentBuilder().parse(input).documentElement
        } catch (e: Exception) {
            throw KmlException("Not a valid KML/XML file", e)
        }
        if (root.localName != "kml") throw KmlException("Not a KML file (root element is <${root.localName}>)")

        val styles = root.descendants("Style")
            .filter { it.getAttribute("id").isNotEmpty() }
            .associate { it.getAttribute("id") to parseStyle(it, FeatureStyle.DEFAULT) }
        val styleMaps = root.descendants("StyleMap")
            .filter { it.getAttribute("id").isNotEmpty() }
            .associate { map ->
                val normal = map.children("Pair").firstOrNull { it.childText("key") == "normal" }
                map.getAttribute("id") to normal?.childText("styleUrl")
            }

        fun resolve(url: String?): FeatureStyle? {
            val id = url?.substringAfter('#') ?: return null
            return styles[id] ?: styleMaps[id]?.let { styles[it.substringAfter('#')] }
        }

        val placemarks = root.descendants("Placemark").mapNotNull { element ->
            val geometry = element.childElements().firstNotNullOfOrNull(::parseGeometry) ?: return@mapNotNull null
            val shared = resolve(element.childText("styleUrl")) ?: FeatureStyle.DEFAULT
            val style = element.children("Style").firstOrNull()?.let { parseStyle(it, shared) } ?: shared
            Placemark(
                name = element.childText("name"),
                description = element.childText("description"),
                geometry = geometry,
                style = style,
            )
        }
        val name = root.children("Document").firstOrNull()?.childText("name") ?: root.childText("name")
        return KmlDocument(name, placemarks)
    }

    /** Converts KML's `aabbggrr` hex color to packed ARGB. */
    fun parseColor(text: String): Int? {
        val abgr = text.trim().removePrefix("#").toLongOrNull(16)?.toInt() ?: return null
        val a = abgr ushr 24
        val b = (abgr shr 16) and 0xFF
        val g = (abgr shr 8) and 0xFF
        val r = abgr and 0xFF
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun parseStyle(element: Element, base: FeatureStyle): FeatureStyle {
        var style = base
        element.children("LineStyle").firstOrNull()?.let { line ->
            line.childText("color")?.let(::parseColor)?.let { style = style.copy(lineColor = it) }
            line.childText("width")?.toFloatOrNull()?.let { style = style.copy(lineWidth = it) }
        }
        element.children("PolyStyle").firstOrNull()?.let { poly ->
            poly.childText("color")?.let(::parseColor)?.let { style = style.copy(fillColor = it) }
            poly.childText("fill")?.let { style = style.copy(fill = it.trim() != "0") }
            poly.childText("outline")?.let { style = style.copy(outline = it.trim() != "0") }
        }
        element.children("IconStyle").firstOrNull()?.let { icon ->
            icon.childText("color")?.let(::parseColor)?.let { style = style.copy(iconColor = it) }
            icon.childText("scale")?.toFloatOrNull()?.let { style = style.copy(iconScale = it) }
        }
        return style
    }

    private fun parseGeometry(element: Element): Geometry? = when (element.localName) {
        "Point" -> coordinates(element).firstOrNull()?.let(Geometry::Point)
        "LineString" -> coordinates(element).takeIf { it.size >= 2 }?.let(Geometry::LineString)
        "LinearRing" -> coordinates(element).takeIf { it.size >= 3 }?.let(Geometry::LineString)
        "Polygon" -> {
            val outer = element.children("outerBoundaryIs").firstOrNull()?.let(::ring)
            outer?.let {
                Geometry.Polygon(it, element.children("innerBoundaryIs").mapNotNull(::ring))
            }
        }
        "MultiGeometry" -> element.childElements().mapNotNull(::parseGeometry)
            .takeIf { it.isNotEmpty() }
            ?.let(Geometry::Multi)
        else -> null
    }

    private fun ring(boundary: Element): List<GeoPoint>? =
        boundary.children("LinearRing").firstOrNull()?.let(::coordinates)?.takeIf { it.size >= 3 }

    /** Parses `lon,lat[,alt]` tuples separated by whitespace, skipping invalid ones. */
    private fun coordinates(element: Element): List<GeoPoint> {
        val text = element.childText("coordinates") ?: return emptyList()
        return text.split(WHITESPACE).mapNotNull { tuple ->
            val parts = tuple.split(',')
            if (parts.size < 2) return@mapNotNull null
            val lon = parts[0].toDoubleOrNull() ?: return@mapNotNull null
            val lat = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            if (!lon.isFinite() || lat !in -90.0..90.0) null else GeoPoint(lon, lat)
        }
    }

    private val WHITESPACE = Regex("\\s+")

    private fun newDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // Refuse DTDs and external entities (XXE). Not every parser
            // supports every feature, so each is best-effort.
            for ((feature, value) in listOf(
                "http://apache.org/xml/features/disallow-doctype-decl" to true,
                "http://xml.org/sax/features/external-general-entities" to false,
                "http://xml.org/sax/features/external-parameter-entities" to false,
                "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
            )) {
                runCatching { setFeature(feature, value) }
            }
        }

    private fun Element.descendants(localName: String): List<Element> {
        val nodes = getElementsByTagNameNS("*", localName)
        return List(nodes.length) { nodes.item(it) as Element }
    }

    private fun Element.childElements(): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).map(nodes::item).filter { it.nodeType == Node.ELEMENT_NODE }.map { it as Element }
    }

    private fun Element.children(localName: String): List<Element> =
        childElements().filter { it.localName == localName }

    private fun Element.childText(localName: String): String? =
        children(localName).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }
}

package net.interlecto.geoutils.domain.render

import net.interlecto.geoutils.domain.model.MapScene
import net.interlecto.geoutils.domain.model.SceneShape
import net.interlecto.geoutils.geometry.Vec2
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Writes a [MapScene] as a standalone SVG document. The base map is embedded
 * as an `<image>` (given as a data URI, since encoding it is platform work)
 * clipped to the map outline; features are vector paths.
 */
object SvgWriter {

    const val GRATICULE_COLOR = 0x59FFFFFF
    const val OUTLINE_COLOR = 0xFF9FB3C8.toInt()

    fun write(scene: MapScene, rasterDataUri: String?, title: String? = null): String = buildString {
        val s = scene.strokeScale
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" """)
        append("""width="${scene.width}" height="${scene.height}" viewBox="0 0 ${scene.width} ${scene.height}">""")
        append('\n')
        if (title != null) append("  <title>").append(escape(title)).append("</title>\n")

        append("  <defs>\n    <clipPath id=\"map-clip\"><path d=\"")
        for (loop in scene.outlines) appendPath(loop, closed = true)
        append("\"/></clipPath>\n  </defs>\n")

        if (rasterDataUri != null) {
            append("""  <image x="0" y="0" width="${scene.width}" height="${scene.height}" """)
            append("""preserveAspectRatio="none" clip-path="url(#map-clip)" """)
            append("href=\"").append(rasterDataUri).append("\" xlink:href=\"").append(rasterDataUri).append("\"/>\n")
        }

        if (scene.graticule.isNotEmpty()) {
            append("  <g id=\"graticule\" fill=\"none\"")
            appendColor("stroke", GRATICULE_COLOR)
            append(" stroke-width=\"").appendNumber(0.75 * s).append("\">\n")
            for (line in scene.graticule) {
                append("    <path d=\"")
                appendPath(line, closed = false)
                append("\"/>\n")
            }
            append("  </g>\n")
        }

        append("  <g id=\"features\" stroke-linejoin=\"round\" stroke-linecap=\"round\">\n")
        for (shape in scene.shapes) appendShape(shape, s)
        append("  </g>\n")

        append("  <path id=\"outline\" fill=\"none\"")
        appendColor("stroke", OUTLINE_COLOR)
        append(" stroke-width=\"").appendNumber(1.5 * s).append("\" d=\"")
        for (loop in scene.outlines) appendPath(loop, closed = true)
        append("\"/>\n")

        if (scene.attribution != null) {
            append("  <text x=\"").appendNumber(scene.width - 8.0 * s)
            append("\" y=\"").appendNumber(scene.height - 8.0 * s)
            append("\" text-anchor=\"end\" font-family=\"sans-serif\" font-size=\"").appendNumber(11.0 * s)
            append("\" fill=\"#888888\">").append(escape(scene.attribution)).append("</text>\n")
        }
        append("</svg>\n")
    }

    private fun StringBuilder.appendShape(shape: SceneShape, s: Float) {
        val style = shape.style
        when (shape) {
            is SceneShape.Path -> {
                append("    <path")
                if (shape.closed && style.fill) {
                    appendColor("fill", style.fillColor)
                    append(" fill-rule=\"evenodd\"")
                } else {
                    append(" fill=\"none\"")
                }
                if (!shape.closed || style.outline) {
                    appendColor("stroke", style.lineColor)
                    append(" stroke-width=\"").appendNumber(style.lineWidth * s.toDouble()).append('"')
                }
                append(" d=\"")
                for (part in shape.parts) appendPath(part, shape.closed)
                append('"')
            }

            is SceneShape.Marker -> {
                append("    <circle cx=\"").appendNumber(shape.at.x)
                append("\" cy=\"").appendNumber(shape.at.y)
                append("\" r=\"").appendNumber(4.0 * style.iconScale * s).append('"')
                appendColor("fill", style.iconColor)
                append(" stroke=\"#ffffff\" stroke-width=\"").appendNumber(1.0 * s).append('"')
            }
        }
        val name = shape.name
        if (name != null) {
            append("><title>").append(escape(name)).append("</title></")
            append(if (shape is SceneShape.Path) "path" else "circle").append(">\n")
        } else {
            append("/>\n")
        }
    }

    private fun StringBuilder.appendPath(points: List<Vec2>, closed: Boolean) {
        points.forEachIndexed { i, p ->
            append(if (i == 0) 'M' else 'L').appendNumber(p.x).append(',').appendNumber(p.y)
        }
        if (closed && points.isNotEmpty()) append('Z')
    }

    /** Writes `name="#rrggbb"` plus an opacity attribute when the color isn't opaque. */
    private fun StringBuilder.appendColor(name: String, argb: Int) {
        append(' ').append(name).append("=\"#").append(String.format(Locale.ROOT, "%06x", argb and 0xFFFFFF)).append('"')
        val alpha = argb ushr 24
        if (alpha != 0xFF) {
            append(' ').append(name).append("-opacity=\"").appendNumber(alpha / 255.0).append('"')
        }
    }

    /** Two decimals at most, always with a dot; locale-independent. */
    private fun StringBuilder.appendNumber(v: Double): StringBuilder {
        val hundredths = (v * 100).roundToLong()
        if (hundredths < 0) append('-')
        val magnitude = abs(hundredths)
        append(magnitude / 100)
        val frac = (magnitude % 100).toInt()
        if (frac != 0) {
            append('.').append(frac / 10)
            if (frac % 10 != 0) append(frac % 10)
        }
        return this
    }

    private fun escape(text: String): String = buildString(text.length) {
        for (c in text) when (c) {
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '&' -> append("&amp;")
            '"' -> append("&quot;")
            else -> append(c)
        }
    }
}

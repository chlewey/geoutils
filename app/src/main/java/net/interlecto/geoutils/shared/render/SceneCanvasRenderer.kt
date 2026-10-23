package net.interlecto.geoutils.shared.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import net.interlecto.geoutils.domain.model.MapScene
import net.interlecto.geoutils.domain.model.SceneShape
import net.interlecto.geoutils.domain.render.SvgWriter
import net.interlecto.geoutils.geometry.RasterImage
import net.interlecto.geoutils.geometry.Vec2

fun RasterImage.toBitmap(): Bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)

/**
 * Draws a [MapScene] on an Android [Canvas] in scene pixels. Used both by the
 * on-screen viewer and for PNG export, and kept visually in line with
 * [SvgWriter].
 */
object SceneCanvasRenderer {

    /** @param raster the scene's raster as a bitmap (see [toBitmap]), converted once by the caller. */
    fun draw(canvas: Canvas, scene: MapScene, raster: Bitmap?) {
        val s = scene.strokeScale
        if (raster != null) {
            canvas.drawBitmap(raster, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        if (scene.graticule.isNotEmpty()) {
            val paint = strokePaint(SvgWriter.GRATICULE_COLOR, 0.75f * s)
            val path = Path()
            for (line in scene.graticule) path.addPolyline(line, closed = false)
            canvas.drawPath(path, paint)
        }

        for (shape in scene.shapes) {
            val style = shape.style
            when (shape) {
                is SceneShape.Path -> {
                    val path = Path().apply { fillType = Path.FillType.EVEN_ODD }
                    for (part in shape.parts) path.addPolyline(part, shape.closed)
                    if (shape.closed && style.fill) {
                        canvas.drawPath(path, fillPaint(style.fillColor))
                    }
                    if (!shape.closed || style.outline) {
                        canvas.drawPath(path, strokePaint(style.lineColor, style.lineWidth * s))
                    }
                }

                is SceneShape.Marker -> {
                    val r = 4f * style.iconScale * s
                    val x = shape.at.x.toFloat()
                    val y = shape.at.y.toFloat()
                    canvas.drawCircle(x, y, r, fillPaint(style.iconColor))
                    canvas.drawCircle(x, y, r, strokePaint(0xFFFFFFFF.toInt(), 1f * s))
                }
            }
        }

        val outline = Path().apply { addPolyline(scene.outline, closed = true) }
        canvas.drawPath(outline, strokePaint(SvgWriter.OUTLINE_COLOR, 1.5f * s))

        scene.attribution?.let { text ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF888888.toInt()
                textSize = 11f * s
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(text, scene.width - 8f * s, scene.height - 8f * s, paint)
        }
    }

    private fun Path.addPolyline(points: List<Vec2>, closed: Boolean) {
        points.forEachIndexed { i, p ->
            if (i == 0) moveTo(p.x.toFloat(), p.y.toFloat()) else lineTo(p.x.toFloat(), p.y.toFloat())
        }
        if (closed && points.isNotEmpty()) close()
    }

    private fun strokePaint(argb: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = argb
        strokeWidth = width
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private fun fillPaint(argb: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = argb
    }
}

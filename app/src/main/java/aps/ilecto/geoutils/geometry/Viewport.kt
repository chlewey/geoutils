package net.interlecto.geoutils.geometry

import kotlin.math.min

/**
 * Maps projection units to pixels: [bounds] is scaled uniformly to fit a
 * [width] × [height] image with [padding] pixels on each side, centered, with
 * y flipped so north is up.
 */
class Viewport(
    val bounds: Bounds,
    val width: Int,
    val height: Int,
    padding: Double = 0.0,
) {
    /** Pixels per projection unit. */
    val scale: Double = min((width - 2 * padding) / bounds.width, (height - 2 * padding) / bounds.height)

    private val cx = width / 2.0
    private val cy = height / 2.0

    fun toPixel(p: Vec2) = Vec2(cx + (p.x - bounds.centerX) * scale, cy - (p.y - bounds.centerY) * scale)

    fun toProjectedX(px: Double): Double = bounds.centerX + (px - cx) / scale

    fun toProjectedY(py: Double): Double = bounds.centerY - (py - cy) / scale
}

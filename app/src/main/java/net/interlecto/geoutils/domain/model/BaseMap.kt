package net.interlecto.geoutils.domain.model

/**
 * An equirectangular bitmap covering the whole sphere (360° × 180°), used as
 * the source image for projections.
 *
 * @property uri where the bitmap is read from: an `asset://`, `file://` or
 *   `content://` URI.
 */
data class BaseMap(
    val id: Long = 0,
    val name: String,
    val uri: String,
    val attribution: String? = null,
    val widthPx: Int,
    val heightPx: Int,
)

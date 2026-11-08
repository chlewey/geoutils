package net.interlecto.geoutils.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import net.interlecto.geoutils.domain.model.MapScene
import net.interlecto.geoutils.shared.render.SceneCanvasRenderer
import net.interlecto.geoutils.shared.render.toBitmap
import kotlin.math.min

private const val MAX_ZOOM = 16f

/**
 * Shows a [MapScene] fitted to the available space, with pinch zoom, drag
 * and double-tap to reset. Zooming scales the rendered image; it doesn't
 * re-render the map. With [fitWidth] the map fills the width and may run
 * past the top and bottom (pan to see them); otherwise it fits whole.
 */
@Composable
fun MapViewer(scene: MapScene?, modifier: Modifier = Modifier, fitWidth: Boolean = false) {
    // Offset of the scene center from the view center, in screen pixels.
    // Both reset when the map's shape changes (another projection).
    val shape = scene?.let { it.width to it.height }
    var offset by remember(shape) { mutableStateOf(Offset.Zero) }
    var zoom by remember(shape) { mutableFloatStateOf(1f) }
    val raster = remember(scene) { scene?.raster?.toBitmap() }

    Box(
        modifier
            .clipToBounds()
            .pointerInput(shape) {
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                    val newZoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                    val factor = newZoom / zoom
                    // Keep the point under the fingers fixed while zooming.
                    val c = centroid - Offset(size.width / 2f, size.height / 2f)
                    offset = (offset - c) * factor + c + pan
                    zoom = newZoom
                }
            }
            .pointerInput(shape) {
                detectTapGestures(onDoubleTap = {
                    zoom = 1f
                    offset = Offset.Zero
                })
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (scene == null) return@Canvas
            val widthFit = size.width / scene.width
            val fit = if (fitWidth) widthFit else min(widthFit, size.height / scene.height)
            val k = fit * zoom
            withTransform({
                translate(size.width / 2 + offset.x, size.height / 2 + offset.y)
                scale(k, k, pivot = Offset.Zero)
                translate(-scene.width / 2f, -scene.height / 2f)
            }) {
                drawIntoCanvas { SceneCanvasRenderer.draw(it.nativeCanvas, scene, raster) }
            }
        }
    }
}

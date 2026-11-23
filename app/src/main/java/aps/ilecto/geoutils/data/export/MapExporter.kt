package net.interlecto.geoutils.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.interlecto.geoutils.domain.model.MapScene
import net.interlecto.geoutils.domain.render.SvgWriter
import net.interlecto.geoutils.geometry.RasterImage
import net.interlecto.geoutils.shared.di.DefaultDispatcher
import net.interlecto.geoutils.shared.render.SceneCanvasRenderer
import net.interlecto.geoutils.shared.render.toBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** Writes rendered maps as SVG or PNG, to a user-chosen location or a shareable cache file. */
@Singleton
class MapExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {
    enum class Format(val extension: String, val mimeType: String) {
        SVG("svg", "image/svg+xml"),
        PNG("png", "image/png"),
    }

    /** Writes [scene] to [target], a URI from the Storage Access Framework. */
    suspend fun save(scene: MapScene, format: Format, target: Uri, title: String) = withContext(dispatcher) {
        val stream = context.contentResolver.openOutputStream(target, "wt")
            ?: throw FileNotFoundException(target.toString())
        stream.use { write(scene, format, title, it) }
    }

    /** Writes [scene] to a cache file and returns a content URI other apps can read. */
    suspend fun shareable(scene: MapScene, format: Format, title: String): Uri = withContext(dispatcher) {
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val safeName = title.replace(Regex("[^A-Za-z0-9._-]+"), "_").ifEmpty { "map" }
        val file = File(dir, "$safeName.${format.extension}")
        file.outputStream().use { write(scene, format, title, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun write(scene: MapScene, format: Format, title: String, out: OutputStream) {
        when (format) {
            Format.SVG -> {
                val raster = scene.raster?.let(::jpegDataUri)
                out.write(SvgWriter.write(scene, raster, title).toByteArray(Charsets.UTF_8))
            }

            Format.PNG -> {
                val bitmap = createBitmap(scene.width, scene.height)
                val raster = scene.raster?.toBitmap()
                SceneCanvasRenderer.draw(Canvas(bitmap), scene, raster)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                raster?.recycle()
                bitmap.recycle()
            }
        }
    }

    /**
     * JPEG keeps the embedded image several times smaller than PNG. It has no
     * transparency, but the SVG clips the image to the map outline anyway.
     */
    private fun jpegDataUri(raster: RasterImage): String {
        val bitmap = raster.toBitmap()
        val bytes = ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
            it.toByteArray()
        }
        bitmap.recycle()
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes)
    }

    private companion object {
        const val EXPORT_DIR = "exports"
        const val JPEG_QUALITY = 90
    }
}

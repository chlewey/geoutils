package net.interlecto.geoutils.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.interlecto.geoutils.geometry.RasterImage
import net.interlecto.geoutils.shared.di.IoDispatcher
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Decodes base map images (PNG, JPEG, WebP) from app assets or content URIs
 * into [RasterImage]s, downsampling anything wider than [MAX_WIDTH].
 */
@Singleton
class RasterLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun load(uri: String, maxWidth: Int = MAX_WIDTH): RasterImage = withContext(io) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not a readable image: $uri")

        // Power-of-two subsampling while decoding keeps huge images from
        // exhausting memory; the final resize is done on the smaller bitmap.
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= maxWidth) sampleSize *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = open(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Could not decode image: $uri")
        if (bitmap.width > maxWidth) {
            val height = (bitmap.height.toDouble() * maxWidth / bitmap.width).roundToInt()
            val scaled = bitmap.scale(maxWidth, height)
            bitmap.recycle()
            bitmap = scaled
        }
        val raster = RasterImage(bitmap.width, bitmap.height)
        bitmap.getPixels(raster.pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
        raster
    }

    private fun open(uri: String): InputStream =
        if (uri.startsWith(ASSET_URI_PREFIX)) {
            context.assets.open(uri.removePrefix(ASSET_URI_PREFIX))
        } else {
            context.contentResolver.openInputStream(uri.toUri()) ?: throw FileNotFoundException(uri)
        }

    companion object {
        /** Prefix for base maps bundled in `assets/`. */
        const val ASSET_URI_PREFIX = "asset:///"
        const val MAX_WIDTH = 4096
    }
}

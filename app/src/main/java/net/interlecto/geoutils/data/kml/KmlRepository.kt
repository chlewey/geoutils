package net.interlecto.geoutils.data.kml

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.interlecto.geoutils.data.displayName
import net.interlecto.geoutils.domain.model.KmlDocument
import net.interlecto.geoutils.shared.di.IoDispatcher
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KmlRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    data class LoadedKml(val fileName: String, val document: KmlDocument)

    /** @throws KmlParser.KmlException if the file isn't valid KML. */
    suspend fun load(uri: Uri): LoadedKml = withContext(io) {
        val document = context.contentResolver.openInputStream(uri)?.use(KmlParser::parse)
            ?: throw FileNotFoundException(uri.toString())
        LoadedKml(context.displayName(uri), document)
    }
}

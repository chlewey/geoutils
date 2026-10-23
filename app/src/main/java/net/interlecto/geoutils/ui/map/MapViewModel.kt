package net.interlecto.geoutils.ui.map

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.interlecto.geoutils.R
import net.interlecto.geoutils.data.displayName
import net.interlecto.geoutils.data.export.MapExporter
import net.interlecto.geoutils.data.image.RasterLoader
import net.interlecto.geoutils.data.kml.KmlRepository
import net.interlecto.geoutils.data.repository.BaseMapRepository
import net.interlecto.geoutils.domain.model.KmlDocument
import net.interlecto.geoutils.domain.model.MapScene
import net.interlecto.geoutils.domain.render.SceneBuilder
import net.interlecto.geoutils.geometry.GeoPoint
import net.interlecto.geoutils.geometry.Orthographic
import net.interlecto.geoutils.geometry.RasterImage
import net.interlecto.geoutils.shared.di.DefaultDispatcher
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

/** An equirectangular image to project: from the library or picked by the user. */
data class MapSource(val name: String, val uri: String, val attribution: String?)

data class MapUiState(
    val library: List<MapSource> = emptyList(),
    val source: MapSource? = null,
    val kmlFileName: String? = null,
    val kml: KmlDocument? = null,
    val center: GeoPoint = GeoPoint(-30.0, 20.0),
    val showGraticule: Boolean = true,
    val scene: MapScene? = null,
    val rendering: Boolean = false,
    val exporting: Boolean = false,
) {
    val renderInput get() = RenderInput(source, kml, center, showGraticule)
}

data class RenderInput(
    val source: MapSource?,
    val kml: KmlDocument?,
    val center: GeoPoint,
    val showGraticule: Boolean,
)

sealed interface MapEvent {
    data class Message(@StringRes val text: Int, val args: List<Any> = emptyList()) : MapEvent
    data class Share(val uri: Uri, val mimeType: String) : MapEvent
}

@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    baseMapRepository: BaseMapRepository,
    private val rasterLoader: RasterLoader,
    private val kmlRepository: KmlRepository,
    private val exporter: MapExporter,
    @DefaultDispatcher private val cpu: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private val _events = Channel<MapEvent>(Channel.BUFFERED)
    val events: Flow<MapEvent> = _events.receiveAsFlow()

    /** The decoded source image is large; keep only the current one. */
    private var cachedRaster: Pair<String, RasterImage>? = null
    private val rasterMutex = Mutex()

    init {
        viewModelScope.launch {
            baseMapRepository.observeAll().collect { baseMaps ->
                val library = baseMaps.map { MapSource(it.name, it.uri, it.attribution) }
                _state.update { it.copy(library = library, source = it.source ?: library.firstOrNull()) }
            }
        }
        viewModelScope.launch {
            state.map { it.renderInput }.distinctUntilChanged().collectLatest(::render)
        }
    }

    fun selectSource(source: MapSource?) = _state.update { it.copy(source = source) }

    fun openImage(uri: Uri) {
        val source = MapSource(context.displayName(uri), uri.toString(), attribution = null)
        viewModelScope.launch {
            val raster = try {
                raster(source.uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message(R.string.msg_image_failed, e.localizedMessage ?: e.javaClass.simpleName)
                return@launch
            }
            if (abs(raster.width.toDouble() / raster.height - 2.0) > 0.02) {
                message(R.string.msg_not_equirectangular, raster.width, raster.height)
            }
            _state.update { it.copy(source = source) }
        }
    }

    fun openKml(uri: Uri) {
        viewModelScope.launch {
            try {
                val loaded = kmlRepository.load(uri)
                _state.update { it.copy(kml = loaded.document, kmlFileName = loaded.fileName) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message(R.string.msg_kml_failed, e.localizedMessage ?: e.javaClass.simpleName)
            }
        }
    }

    fun removeKml() = _state.update { it.copy(kml = null, kmlFileName = null) }

    fun setCenter(lon: Double, lat: Double) = _state.update { it.copy(center = GeoPoint(lon, lat)) }

    fun setGraticule(show: Boolean) = _state.update { it.copy(showGraticule = show) }

    fun save(format: MapExporter.Format, target: Uri) = export {
        exporter.save(buildScene(state.value.renderInput, EXPORT_SIZE), format, target, exportTitle())
        message(R.string.msg_saved)
    }

    fun share(format: MapExporter.Format) = export {
        val uri = exporter.shareable(buildScene(state.value.renderInput, EXPORT_SIZE), format, exportTitle())
        _events.send(MapEvent.Share(uri, format.mimeType))
    }

    /** Suggested file name, without extension. */
    fun exportTitle(): String {
        val s = state.value
        val base = s.kmlFileName?.substringBeforeLast('.') ?: s.source?.name ?: "map"
        return "$base-ortho-%.0f_%.0f".format(Locale.ROOT, s.center.lon, s.center.lat)
    }

    private suspend fun render(input: RenderInput) {
        _state.update { it.copy(rendering = true) }
        try {
            val scene = buildScene(input, PREVIEW_SIZE)
            _state.update { it.copy(scene = scene) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message(R.string.msg_render_failed, e.localizedMessage ?: e.javaClass.simpleName)
        } finally {
            _state.update { it.copy(rendering = false) }
        }
    }

    private suspend fun buildScene(input: RenderInput, size: Int): MapScene {
        val raster = input.source?.let { raster(it.uri) }
        return withContext(cpu) {
            SceneBuilder.build(
                projection = Orthographic(input.center),
                size = size,
                baseMap = raster,
                kml = input.kml,
                showGraticule = input.showGraticule,
                attribution = input.source?.attribution,
            )
        }
    }

    private suspend fun raster(uri: String): RasterImage = rasterMutex.withLock {
        cachedRaster?.takeIf { it.first == uri }?.second
            ?: rasterLoader.load(uri).also { cachedRaster = uri to it }
    }

    private fun export(block: suspend () -> Unit) {
        if (state.value.exporting) return
        viewModelScope.launch {
            _state.update { it.copy(exporting = true) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message(R.string.msg_export_failed, e.localizedMessage ?: e.javaClass.simpleName)
            } finally {
                _state.update { it.copy(exporting = false) }
            }
        }
    }

    private suspend fun message(@StringRes text: Int, vararg args: Any) {
        _events.send(MapEvent.Message(text, args.toList()))
    }

    private companion object {
        const val PREVIEW_SIZE = 1024
        const val EXPORT_SIZE = 2048
    }
}

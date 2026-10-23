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
import net.interlecto.geoutils.data.location.LocationProvider
import net.interlecto.geoutils.data.repository.BaseMapRepository
import net.interlecto.geoutils.domain.model.KmlDocument
import net.interlecto.geoutils.domain.model.MapScene
import net.interlecto.geoutils.domain.render.SceneBuilder
import net.interlecto.geoutils.geometry.GeoPoint
import net.interlecto.geoutils.geometry.ProjectionCatalog
import net.interlecto.geoutils.geometry.ProjectionType
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
    val projectionId: String = ProjectionCatalog.ORTHOGRAPHIC.id,
    /** Views the user has changed, by projection id; others use [ViewSettings.defaultFor]. */
    val views: Map<String, ViewSettings> = emptyMap(),
    val deviceLocation: GeoPoint? = null,
    val showGraticule: Boolean = true,
    val scene: MapScene? = null,
    val rendering: Boolean = false,
    val exporting: Boolean = false,
) {
    val projectionType: ProjectionType get() = ProjectionCatalog.byId(projectionId)

    val view: ViewSettings get() = views[projectionId] ?: ViewSettings.defaultFor(projectionType, deviceLocation)

    val renderInput
        get() = RenderInput(source, kml, projectionType, view.center, view.effectiveAzimuth, showGraticule)
}

data class RenderInput(
    val source: MapSource?,
    val kml: KmlDocument?,
    val projection: ProjectionType,
    val center: GeoPoint,
    val azimuth: Double,
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
    private val locationProvider: LocationProvider,
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

    fun selectProjection(id: String) = _state.update { it.copy(projectionId = id) }

    fun setCenter(lon: Double, lat: Double) = updateView { it.copy(longitude = lon, latitude = lat) }

    fun setLongitude(lon: Double) = updateView { it.copy(longitude = lon) }

    /** Turning latitude off returns it to 0 (normal aspect). */
    fun setLatitudeEnabled(enabled: Boolean) =
        updateView { it.copy(latitudeEnabled = enabled, latitude = if (enabled) it.latitude else 0.0) }

    fun setAzimuth(degrees: Double) = updateView { it.copy(azimuth = it.azimuthMode.normalize(degrees)) }

    fun setAzimuthMode(mode: AzimuthMode) =
        updateView { it.copy(azimuthMode = mode, azimuth = mode.normalize(it.azimuth)) }

    /**
     * Looks up the device location. Views the user hasn't changed (azimuthal
     * ones start there) follow it; changed views keep their center.
     */
    fun centerOnDeviceLocation() {
        viewModelScope.launch {
            val here = locationProvider.current() ?: return@launch
            _state.update { it.copy(deviceLocation = here) }
        }
    }

    /** Changes the current projection's view, which from then on is kept as the user left it. */
    private fun updateView(change: (ViewSettings) -> ViewSettings) =
        _state.update { it.copy(views = it.views + (it.projectionId to change(it.view))) }

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
        val view = s.view
        val azimuth = if (view.effectiveAzimuth != 0.0) "_az%.0f".format(Locale.ROOT, view.effectiveAzimuth) else ""
        return "$base-${s.projectionId}-%.0f_%.0f".format(Locale.ROOT, view.center.lon, view.center.lat) + azimuth
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
                projection = input.projection.create(input.center, input.azimuth),
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

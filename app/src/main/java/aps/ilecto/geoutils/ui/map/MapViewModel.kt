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
import net.interlecto.geoutils.data.settings.PointPair
import net.interlecto.geoutils.data.settings.SavedPoints
import net.interlecto.geoutils.data.settings.SavedPointsRepository
import net.interlecto.geoutils.domain.model.FeatureStyle
import net.interlecto.geoutils.domain.model.Geometry
import net.interlecto.geoutils.domain.model.KmlDocument
import net.interlecto.geoutils.domain.model.Placemark
import net.interlecto.geoutils.domain.model.MapScene
import net.interlecto.geoutils.domain.render.SceneBuilder
import net.interlecto.geoutils.geometry.AspectControls
import net.interlecto.geoutils.geometry.GeoPoint
import net.interlecto.geoutils.geometry.Geodesy
import net.interlecto.geoutils.geometry.ProjectionCatalog
import net.interlecto.geoutils.geometry.ProjectionType
import net.interlecto.geoutils.geometry.RasterImage
import net.interlecto.geoutils.geometry.Sphere
import net.interlecto.geoutils.shared.di.DefaultDispatcher
import java.text.NumberFormat
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
    val route: Route? = null,
    val distances: Distances? = null,
    /** Last points used, to fill in the point fields (also after a restart). */
    val savedPoints: SavedPoints = SavedPoints(),
    val showGraticule: Boolean = true,
    val showSpecialParallels: Boolean = false,
    val scene: MapScene? = null,
    val rendering: Boolean = false,
    val exporting: Boolean = false,
) {
    val projectionType: ProjectionType get() = ProjectionCatalog.byId(projectionId)

    val view: ViewSettings get() = views[projectionId] ?: ViewSettings.defaultFor(projectionType, deviceLocation)

    val renderInput: RenderInput
        get() {
            val (center, azimuth) = view.aspectFor(projectionType)
            return RenderInput(
                source = source,
                kml = kml,
                projection = projectionType,
                center = center,
                azimuth = azimuth,
                polar = view.polar,
                params = view.params,
                route = route,
                distances = distances,
                showGraticule = showGraticule,
                showSpecialParallels = showSpecialParallels,
            )
        }
}

data class RenderInput(
    val source: MapSource?,
    val kml: KmlDocument?,
    val projection: ProjectionType,
    val center: GeoPoint,
    val azimuth: Double,
    val polar: Boolean,
    val params: Map<String, String>,
    val route: Route?,
    val distances: Distances?,
    val showGraticule: Boolean,
    val showSpecialParallels: Boolean,
)

/**
 * Distances from one point: rings every [spacingKm], great circles leaving it
 * every 15° of bearing, and a filled circle of [radiusKm] (everything within
 * that distance), each optional.
 */
data class Distances(
    val center: GeoPoint,
    val spacingKm: Double = 1000.0,
    val rings: Boolean = true,
    val rays: Boolean = true,
    val radiusKm: Double? = null,
)

/** A route drawn on the map between two points, as a great circle, a rhumb line or both. */
data class Route(
    val from: GeoPoint,
    val to: GeoPoint,
    val greatCircle: Boolean = true,
    val rhumb: Boolean = false,
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
    private val savedPointsRepository: SavedPointsRepository,
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
            savedPointsRepository.points.collect { saved -> _state.update { it.copy(savedPoints = saved) } }
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

    fun setLatitude(lat: Double) = updateView { it.copy(latitude = lat) }

    /** Turning latitude off returns it to 0 (normal aspect). */
    fun setLatitudeEnabled(enabled: Boolean) =
        updateView { it.copy(latitudeEnabled = enabled, latitude = if (enabled) it.latitude else 0.0) }

    fun setAzimuth(degrees: Double) = updateView { it.copy(azimuth = it.azimuthMode.normalize(degrees)) }

    fun setAzimuthMode(mode: AzimuthMode) =
        updateView { it.copy(azimuthMode = mode, azimuth = mode.normalize(it.azimuth)) }

    fun setSouthUp(southUp: Boolean) = updateView { it.copy(southUp = southUp) }

    fun setPolar(polar: Boolean) = updateView { it.copy(polar = polar) }

    fun setParam(key: String, value: String) = updateView { it.copy(params = it.params + (key to value)) }

    /**
     * Aims the map so the great circle through [a] and [b] is its equator,
     * centered between them with [b] to the right. This needs latitude and
     * azimuth, so it turns them on.
     */
    fun setEquatorThrough(a: GeoPoint, b: GeoPoint) {
        val aim = Geodesy.equatorThrough(a, b)
        if (aim == null) {
            viewModelScope.launch { message(R.string.msg_equator_undefined) }
            return
        }
        val (center, azimuth) = aim
        viewModelScope.launch { savedPointsRepository.saveEquator(PointPair(a, b)) }
        updateView {
            val mode = if (it.azimuthMode == AzimuthMode.OFF) AzimuthMode.SIGNED else it.azimuthMode
            it.copy(
                longitude = center.lon,
                latitude = center.lat,
                latitudeEnabled = true,
                azimuthMode = mode,
                azimuth = mode.normalize(azimuth),
            )
        }
    }

    fun setRoute(route: Route?) {
        if (route != null) viewModelScope.launch { savedPointsRepository.saveRoute(PointPair(route.from, route.to)) }
        _state.update { it.copy(route = route) }
    }

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

    fun setSpecialParallels(show: Boolean) = _state.update { it.copy(showSpecialParallels = show) }

    fun setDistances(distances: Distances?) = _state.update { it.copy(distances = distances) }

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
        val (center, azimuth) = s.view.aspectFor(s.projectionType)
        val turn = if (azimuth != 0.0) "_az%.0f".format(Locale.ROOT, azimuth) else ""
        val polar = if (s.view.polar && s.projectionType.aspect == AspectControls.TANGENT) "_polar" else ""
        return "$base-${s.projectionId}-%.0f_%.0f".format(Locale.ROOT, center.lon, center.lat) + turn + polar
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
                projection = input.projection.create(input.center, input.azimuth, input.params, input.polar),
                size = size,
                baseMap = raster,
                kml = withOverlays(input.kml, input.route, input.distances),
                showGraticule = input.showGraticule,
                attribution = input.source?.attribution,
                showSpecialParallels = input.showSpecialParallels,
            )
        }
    }

    /** Adds the route and distances, if any, to the KML features so they're drawn and exported with them. */
    private fun withOverlays(kml: KmlDocument?, route: Route?, distances: Distances?): KmlDocument? {
        val features = routeFeatures(route) + distanceFeatures(distances)
        if (features.isEmpty()) return kml
        return KmlDocument(kml?.name, kml?.placemarks.orEmpty() + features)
    }

    private fun distanceFeatures(distances: Distances?): List<Placemark> {
        if (distances == null) return emptyList()
        val center = distances.center
        val km = NumberFormat.getIntegerInstance(Locale.ROOT)
        return buildList {
            distances.radiusKm?.let { radius ->
                val ring = Geodesy.circle(center, radius / Sphere.EARTH_RADIUS_KM)
                add(Placemark("Within ${km.format(radius)} km", null, Geometry.Polygon(ring), CIRCLE_STYLE))
            }
            if (distances.rings) {
                val spacing = distances.spacingKm / Sphere.EARTH_RADIUS_KM
                Geodesy.rings(center, spacing).forEachIndexed { i, ring ->
                    val name = "${km.format(distances.spacingKm * (i + 1))} km"
                    add(Placemark(name, null, Geometry.LineString(ring + ring.first()), RING_STYLE))
                }
            }
            if (distances.rays) {
                Geodesy.rays(center).forEachIndexed { i, ray ->
                    add(Placemark("${15 * i}°", null, Geometry.LineString(ray), RAY_STYLE))
                }
            }
            add(Placemark("Center", null, Geometry.Point(center), FeatureStyle(iconColor = ROUTE_ENDS, iconScale = 1.25f)))
        }
    }

    private fun routeFeatures(route: Route?): List<Placemark> {
        if (route == null || (!route.greatCircle && !route.rhumb)) return emptyList()
        val ends = FeatureStyle(iconColor = ROUTE_ENDS, iconScale = 1.25f)
        return buildList {
            if (route.rhumb) {
                add(Placemark("Rhumb line", null, Geometry.LineString(Geodesy.rhumbPath(route.from, route.to)), RHUMB_STYLE))
            }
            if (route.greatCircle) {
                add(Placemark("Great circle", null, Geometry.LineString(listOf(route.from, route.to)), GREAT_CIRCLE_STYLE))
            }
            add(Placemark("A", null, Geometry.Point(route.from), ends))
            add(Placemark("B", null, Geometry.Point(route.to), ends))
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
        const val ROUTE_ENDS = 0xFFFFFFFF.toInt()
        val GREAT_CIRCLE_STYLE = FeatureStyle(lineColor = 0xFFFF4FD8.toInt(), lineWidth = 2.5f)
        val RHUMB_STYLE = FeatureStyle(lineColor = 0xFF39D0FF.toInt(), lineWidth = 2.5f)
        val RING_STYLE = FeatureStyle(lineColor = 0xB3FFFFFF.toInt(), lineWidth = 1.25f)
        val RAY_STYLE = FeatureStyle(lineColor = 0x80FFFFFF.toInt(), lineWidth = 1f)
        val CIRCLE_STYLE = FeatureStyle(lineColor = 0xFFFFD60A.toInt(), lineWidth = 2f, fillColor = 0x33FFD60A)
    }
}

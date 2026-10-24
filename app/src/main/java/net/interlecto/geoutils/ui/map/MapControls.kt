package net.interlecto.geoutils.ui.map

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.interlecto.geoutils.R
import net.interlecto.geoutils.geometry.AspectControls
import net.interlecto.geoutils.geometry.GeoPoint
import net.interlecto.geoutils.geometry.Geodesy
import net.interlecto.geoutils.geometry.ProjectionCatalog
import net.interlecto.geoutils.geometry.ProjectionFamily
import net.interlecto.geoutils.geometry.ProjectionParam
import net.interlecto.geoutils.geometry.ProjectionType
import net.interlecto.geoutils.geometry.Sphere
import java.text.NumberFormat

private enum class ControlTab(@StringRes val label: Int) {
    MAP(R.string.tab_map),
    VIEW(R.string.tab_view),
    TOOLS(R.string.tab_tools),
}

/** The controls under the viewer, in three tabs: what to draw, where to aim, and tools. */
@Composable
fun ControlPanel(
    state: MapUiState,
    viewModel: MapViewModel,
    onOpenImage: () -> Unit,
    onOpenKml: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(ControlTab.MAP) }
    Column(modifier) {
        PrimaryTabRow(selectedTabIndex = tab.ordinal) {
            for (t in ControlTab.entries) {
                Tab(selected = t == tab, onClick = { tab = t }, text = { Text(stringResource(t.label)) })
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (tab) {
                ControlTab.MAP -> MapTab(state, viewModel, onOpenImage, onOpenKml)
                ControlTab.VIEW -> ViewTab(state, viewModel)
                ControlTab.TOOLS -> ToolsTab(state, viewModel)
            }
        }
    }
}

@Composable
private fun MapTab(state: MapUiState, viewModel: MapViewModel, onOpenImage: () -> Unit, onOpenKml: () -> Unit) {
    Label(stringResource(R.string.base_map))
    SourcePicker(state, viewModel::selectSource, onOpenImage)
    state.source?.attribution?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Spacer(Modifier.padding(top = 4.dp))
    Label(stringResource(R.string.kml))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onOpenKml) { Text(stringResource(R.string.kml_open)) }
        if (state.kml != null) {
            TextButton(onClick = viewModel::removeKml) { Text(stringResource(R.string.kml_remove)) }
        }
    }
    Text(
        text = state.kml?.let {
            pluralStringResource(R.plurals.kml_summary, it.placemarks.size, state.kmlFileName.orEmpty(), it.placemarks.size)
        } ?: stringResource(R.string.kml_none),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )

    Spacer(Modifier.padding(top = 4.dp))
    Label(stringResource(R.string.projection))
    ProjectionPicker(state.projectionType, viewModel::selectProjection)
    ProjectionParams(state.projectionType, state.view.params, viewModel::setParam)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.graticule), Modifier.weight(1f))
        Switch(checked = state.showGraticule, onCheckedChange = viewModel::setGraticule)
    }
    Hint(stringResource(R.string.viewer_hint))
}

/**
 * Center and orientation. Projections that rotate the sphere get center,
 * latitude and azimuth, with pole shortcuts for azimuthal and hemispheric
 * maps; interrupted ones (Goode) only the central meridian and north-up /
 * south-up; two-hemisphere maps the tangent point and a polar layout.
 */
@Composable
private fun ViewTab(state: MapUiState, viewModel: MapViewModel) {
    val type = state.projectionType
    val view = state.view
    val wholeSphere = !view.latitudeAlwaysOn(type)
    when (type.aspect) {
        AspectControls.ROTATE -> {
            LongitudeControl(view.longitude, wholeSphere, state.deviceLocation, R.string.center_longitude, viewModel::setLongitude)
            LatitudeControl(view, switchable = wholeSphere, viewModel::setLatitude, viewModel::setLatitudeEnabled)
            when (type.family) {
                ProjectionFamily.AZIMUTHAL -> PoleShortcuts(view.latitude, listOf(90.0, -90.0), viewModel::setLatitude)
                ProjectionFamily.HEMISPHERIC -> PoleShortcuts(view.latitude, listOf(90.0, 0.0, -90.0), viewModel::setLatitude)
                else -> Unit
            }
            AzimuthControl(view.azimuthMode, view.azimuth, viewModel::setAzimuthMode, viewModel::setAzimuth)

            Spacer(Modifier.padding(top = 8.dp))
            Label(stringResource(R.string.equator_title))
            Hint(stringResource(R.string.equator_hint))
            PointPairEditor(
                initialA = state.savedPoints.equator?.a,
                initialB = state.savedPoints.equator?.b,
                center = view.aspectFor(type).first,
                actionLabel = stringResource(R.string.apply),
                onApply = viewModel::setEquatorThrough,
            )
        }

        AspectControls.MERIDIAN_AND_FLIP -> {
            LongitudeControl(view.longitude, quickPicks = true, state.deviceLocation, R.string.central_meridian, viewModel::setLongitude)
            Text(stringResource(R.string.orientation), style = MaterialTheme.typography.bodyMedium)
            SegmentedChoice(
                options = listOf(false to stringResource(R.string.north_up), true to stringResource(R.string.south_up)),
                selected = view.southUp,
                onSelect = viewModel::setSouthUp,
            )
            Hint(stringResource(R.string.interrupted_note))
        }

        AspectControls.TANGENT -> {
            Text(stringResource(R.string.layout), style = MaterialTheme.typography.bodyMedium)
            SegmentedChoice(
                options = listOf(false to stringResource(R.string.layout_split), true to stringResource(R.string.layout_polar)),
                selected = view.polar,
                onSelect = viewModel::setPolar,
            )
            LongitudeControl(
                view.longitude,
                quickPicks = false,
                state.deviceLocation,
                if (view.polar) R.string.shared_meridian else R.string.tangent_longitude,
                viewModel::setLongitude,
            )
            if (!view.polar) LatitudeControl(view, switchable = true, viewModel::setLatitude, viewModel::setLatitudeEnabled)
            AzimuthControl(view.azimuthMode, view.azimuth, viewModel::setAzimuthMode, viewModel::setAzimuth)
            Hint(stringResource(if (view.polar) R.string.polar_note else R.string.split_note))
        }
    }
}

/** One-tap latitudes: the poles, and for hemispheric maps the equator. */
@Composable
private fun PoleShortcuts(latitude: Double, latitudes: List<Double>, onSelect: (Double) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (lat in latitudes) {
            val label = when {
                lat > 0 -> R.string.pole_north
                lat < 0 -> R.string.pole_south
                else -> R.string.pole_equator
            }
            FilterChip(selected = latitude == lat, onClick = { onSelect(lat) }, label = { Text(stringResource(label)) })
        }
    }
}

/** A route between two points: great circle, rhumb line, or both, with distances and bearings. */
@Composable
private fun ToolsTab(state: MapUiState, viewModel: MapViewModel) {
    val route = state.route
    Label(stringResource(R.string.route_title))
    PointPairEditor(
        initialA = route?.from ?: state.savedPoints.route?.a,
        initialB = route?.to ?: state.savedPoints.route?.b,
        center = state.view.aspectFor(state.projectionType).first,
        actionLabel = stringResource(R.string.route_show),
        onApply = { a, b ->
            viewModel.setRoute(Route(a, b, route?.greatCircle ?: true, route?.rhumb ?: false))
        },
    )
    if (route == null) return

    val km = NumberFormat.getIntegerInstance()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = route.greatCircle, onCheckedChange = { viewModel.setRoute(route.copy(greatCircle = it)) })
        Text(
            stringResource(
                R.string.route_great_circle,
                km.format(Geodesy.greatCircleDistance(route.from, route.to) * Sphere.EARTH_RADIUS_KM),
                Geodesy.initialBearing(route.from, route.to),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = route.rhumb, onCheckedChange = { viewModel.setRoute(route.copy(rhumb = it)) })
        Text(
            stringResource(
                R.string.route_rhumb,
                km.format(Geodesy.rhumbDistance(route.from, route.to) * Sphere.EARTH_RADIUS_KM),
                Geodesy.rhumbBearing(route.from, route.to),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    TextButton(onClick = { viewModel.setRoute(null) }) { Text(stringResource(R.string.route_clear)) }
}

@Composable
private fun SourcePicker(state: MapUiState, onSelect: (MapSource?) -> Unit, onOpenImage: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(
                state.source?.name ?: stringResource(R.string.base_map_none),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (source in state.library) {
                DropdownMenuItem(text = { Text(source.name) }, onClick = {
                    expanded = false
                    onSelect(source)
                })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.base_map_none)) }, onClick = {
                expanded = false
                onSelect(null)
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.base_map_open_image)) }, onClick = {
                expanded = false
                onOpenImage()
            })
        }
    }
}

@Composable
private fun ProjectionPicker(current: ProjectionType, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(current.displayName) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for ((family, types) in ProjectionCatalog.all.groupBy { it.family }) {
                Text(
                    stringResource(familyLabel(family)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
                for (type in types) {
                    DropdownMenuItem(text = { Text(type.displayName) }, onClick = {
                        expanded = false
                        onSelect(type.id)
                    })
                }
            }
        }
    }
}

private fun familyLabel(family: ProjectionFamily) = when (family) {
    ProjectionFamily.HEMISPHERIC -> R.string.family_hemispheric
    ProjectionFamily.AZIMUTHAL -> R.string.family_azimuthal
    ProjectionFamily.CYLINDRICAL -> R.string.family_cylindrical
    ProjectionFamily.PSEUDOCYLINDRICAL -> R.string.family_pseudocylindrical
    ProjectionFamily.DOUBLE_HEMISPHERIC -> R.string.family_double_hemispheric
}

/** Controls generated from the projection's own parameters. */
@Composable
private fun ProjectionParams(type: ProjectionType, values: Map<String, String>, onChange: (String, String) -> Unit) {
    for (param in type.params) {
        val value = values[param.key] ?: param.default
        when (param) {
            is ProjectionParam.Angle -> {
                var drag by remember(value) { mutableFloatStateOf(value.toFloat()) }
                Text(stringResource(R.string.param_angle, param.label, drag), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = drag,
                    onValueChange = { drag = it },
                    onValueChangeFinished = { onChange(param.key, drag.toDouble().toString()) },
                    valueRange = param.min.toFloat()..param.max.toFloat(),
                )
            }

            is ProjectionParam.Choice -> {
                Text(param.label, style = MaterialTheme.typography.bodyMedium)
                SegmentedChoice(
                    options = param.options.map { it.value to it.label },
                    selected = value,
                    onSelect = { onChange(param.key, it) },
                )
            }
        }
    }
}

/**
 * Longitude slider; it shows the value while dragging and only re-renders
 * when released. [quickPicks] adds 10° E, 0° and the device's longitude.
 */
@Composable
private fun LongitudeControl(
    longitude: Double,
    quickPicks: Boolean,
    deviceLocation: GeoPoint?,
    @StringRes label: Int,
    onChange: (Double) -> Unit,
) {
    var drag by remember(longitude) { mutableFloatStateOf(longitude.toFloat()) }
    Text(stringResource(label, drag), style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = drag,
        onValueChange = { drag = it },
        onValueChangeFinished = { onChange(drag.toDouble()) },
        valueRange = -180f..180f,
    )
    if (quickPicks) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = longitude == 10.0,
                onClick = { onChange(10.0) },
                label = { Text(stringResource(R.string.longitude_10e)) },
            )
            FilterChip(
                selected = longitude == 0.0,
                onClick = { onChange(0.0) },
                label = { Text(stringResource(R.string.longitude_0)) },
            )
            FilterChip(
                selected = deviceLocation != null && longitude == deviceLocation.lon,
                onClick = { deviceLocation?.let { onChange(it.lon) } },
                enabled = deviceLocation != null,
                label = { Text(stringResource(R.string.longitude_here)) },
            )
        }
    }
}

/**
 * Latitude slider. For whole-sphere maps ([switchable]) it starts off,
 * fixed at 0 (the normal aspect), with a switch to turn it on.
 */
@Composable
private fun LatitudeControl(
    view: ViewSettings,
    switchable: Boolean,
    onChange: (Double) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    val enabled = !switchable || view.latitudeEnabled
    var drag by remember(view.latitude) { mutableFloatStateOf(view.latitude.toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.center_latitude, if (enabled) drag else 0f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA),
            modifier = Modifier.weight(1f),
        )
        if (switchable) Switch(checked = view.latitudeEnabled, onCheckedChange = onEnabledChange)
    }
    Slider(
        value = if (enabled) drag else 0f,
        onValueChange = { drag = it },
        onValueChangeFinished = { onChange(drag.toDouble()) },
        valueRange = -90f..90f,
        enabled = enabled,
    )
}

/**
 * Azimuth slider with a mode selector: Off grays the slider out and fixes the
 * azimuth at 0; the other modes set the slider's range.
 */
@Composable
private fun AzimuthControl(
    mode: AzimuthMode,
    azimuth: Double,
    onModeChange: (AzimuthMode) -> Unit,
    onAzimuthChange: (Double) -> Unit,
) {
    val enabled = mode != AzimuthMode.OFF
    var drag by remember(azimuth, mode) { mutableFloatStateOf(azimuth.toFloat()) }
    Text(
        stringResource(R.string.azimuth, drag),
        style = MaterialTheme.typography.bodyMedium,
        color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA),
    )
    SegmentedChoice(
        options = listOf(
            AzimuthMode.OFF to stringResource(R.string.azimuth_off),
            AzimuthMode.SIGNED to stringResource(R.string.azimuth_signed),
            AzimuthMode.FULL to stringResource(R.string.azimuth_full),
        ),
        selected = mode,
        onSelect = onModeChange,
    )
    Slider(
        value = drag,
        onValueChange = { drag = it },
        onValueChangeFinished = { onAzimuthChange(drag.toDouble()) },
        valueRange = mode.range.start.toFloat()..mode.range.endInclusive.toFloat(),
        enabled = enabled,
    )
}

/**
 * Two points typed as longitude/latitude, each of which can be filled from
 * the current center, and a button to use them.
 */
@Composable
private fun PointPairEditor(
    initialA: GeoPoint?,
    initialB: GeoPoint?,
    center: GeoPoint,
    actionLabel: String,
    onApply: (GeoPoint, GeoPoint) -> Unit,
) {
    val a = pointFields(initialA)
    val b = pointFields(initialB)
    PointRow(stringResource(R.string.point_a), a, center)
    PointRow(stringResource(R.string.point_b), b, center)
    val pa = a.parse()
    val pb = b.parse()
    Button(onClick = { if (pa != null && pb != null) onApply(pa, pb) }, enabled = pa != null && pb != null) {
        Text(actionLabel)
    }
}

/** Text state for one point's longitude and latitude fields. */
private class PointFields(lon: String, lat: String) {
    var lon by mutableStateOf(lon)
    var lat by mutableStateOf(lat)

    fun set(p: GeoPoint) {
        lon = formatDegrees(p.lon)
        lat = formatDegrees(p.lat)
    }

    /** The point, or null unless both fields hold valid degrees. Accepts a comma as decimal separator. */
    fun parse(): GeoPoint? {
        val x = lon.trim().replace(',', '.').toDoubleOrNull() ?: return null
        val y = lat.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return if (x in -180.0..180.0 && y in -90.0..90.0) GeoPoint(x, y) else null
    }
}

@Composable
private fun pointFields(initial: GeoPoint?): PointFields = remember(initial) {
    PointFields(initial?.lon?.let(::formatDegrees).orEmpty(), initial?.lat?.let(::formatDegrees).orEmpty())
}

private fun formatDegrees(v: Double) = "%.4f".format(java.util.Locale.ROOT, v).trimEnd('0').trimEnd('.')

@Composable
private fun PointRow(label: String, fields: PointFields, center: GeoPoint) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(16.dp))
        OutlinedTextField(
            value = fields.lon,
            onValueChange = { fields.lon = it },
            label = { Text(stringResource(R.string.longitude_short)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = fields.lat,
            onValueChange = { fields.lat = it },
            label = { Text(stringResource(R.string.latitude_short)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { fields.set(center) }) { Text(stringResource(R.string.use_center)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SegmentedChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(label, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val DISABLED_ALPHA = 0.38f

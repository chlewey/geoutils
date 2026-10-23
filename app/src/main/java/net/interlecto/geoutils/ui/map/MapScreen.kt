package net.interlecto.geoutils.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.interlecto.geoutils.R
import net.interlecto.geoutils.data.export.MapExporter.Format
import net.interlecto.geoutils.geometry.GeoPoint
import net.interlecto.geoutils.geometry.ProjectionCatalog
import net.interlecto.geoutils.geometry.ProjectionFamily
import net.interlecto.geoutils.geometry.ProjectionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MapEvent.Message -> snackbar.showSnackbar(
                    resources.getString(event.text, *event.args.toTypedArray()),
                    duration = SnackbarDuration.Long,
                )

                is MapEvent.Share -> {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType(event.mimeType)
                        .putExtra(Intent.EXTRA_STREAM, event.uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, resources.getString(R.string.share_title)))
                }
            }
        }
    }

    // Ask for location once per screen instance (not again after rotation);
    // when granted, the globe starts centered on the device.
    var locationAsked by rememberSaveable { mutableStateOf(false) }
    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.centerOnDeviceLocation()
    }
    LaunchedEffect(Unit) {
        if (locationAsked) return@LaunchedEffect
        locationAsked = true
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.centerOnDeviceLocation()
        } else {
            requestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    val openImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::openImage)
    }
    // KML files often come without a proper MIME type, so accept anything and let the parser decide.
    val openKml = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::openKml)
    }
    val saveSvg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Format.SVG.mimeType)) { uri ->
        uri?.let { viewModel.save(Format.SVG, it) }
    }
    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Format.PNG.mimeType)) { uri ->
        uri?.let { viewModel.save(Format.PNG, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    ExportMenu(
                        enabled = state.scene != null && !state.exporting,
                        onSaveSvg = { saveSvg.launch("${viewModel.exportTitle()}.svg") },
                        onSavePng = { savePng.launch("${viewModel.exportTitle()}.png") },
                        onShareSvg = { viewModel.share(Format.SVG) },
                        onSharePng = { viewModel.share(Format.PNG) },
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.exporting) LinearProgressIndicator(Modifier.fillMaxWidth())
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF000814)),
                contentAlignment = Alignment.Center,
            ) {
                MapViewer(state.scene, Modifier.fillMaxSize())
                if (state.rendering) CircularProgressIndicator()
            }
            HorizontalDivider()
            Controls(
                state = state,
                onSelectSource = viewModel::selectSource,
                onOpenImage = { openImage.launch(arrayOf("image/*")) },
                onOpenKml = { openKml.launch(arrayOf("*/*")) },
                onRemoveKml = viewModel::removeKml,
                onSelectProjection = viewModel::selectProjection,
                onCenterChange = viewModel::setCenter,
                onLongitudeChange = viewModel::setLongitude,
                onLatitudeEnabledChange = viewModel::setLatitudeEnabled,
                onAzimuthModeChange = viewModel::setAzimuthMode,
                onAzimuthChange = viewModel::setAzimuth,
                onGraticuleChange = viewModel::setGraticule,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ExportMenu(
    enabled: Boolean,
    onSaveSvg: () -> Unit,
    onSavePng: () -> Unit,
    onShareSvg: () -> Unit,
    onSharePng: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) {
            Text(stringResource(R.string.export))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for ((label, action) in listOf(
                R.string.export_save_svg to onSaveSvg,
                R.string.export_save_png to onSavePng,
                R.string.export_share_svg to onShareSvg,
                R.string.export_share_png to onSharePng,
            )) {
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
        }
    }
}

@Composable
private fun Controls(
    state: MapUiState,
    onSelectSource: (MapSource?) -> Unit,
    onOpenImage: () -> Unit,
    onOpenKml: () -> Unit,
    onRemoveKml: () -> Unit,
    onSelectProjection: (String) -> Unit,
    onCenterChange: (Double, Double) -> Unit,
    onLongitudeChange: (Double) -> Unit,
    onLatitudeEnabledChange: (Boolean) -> Unit,
    onAzimuthModeChange: (AzimuthMode) -> Unit,
    onAzimuthChange: (Double) -> Unit,
    onGraticuleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label(stringResource(R.string.base_map))
        SourcePicker(state, onSelectSource, onOpenImage)
        state.source?.attribution?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.padding(top = 4.dp))
        Label(stringResource(R.string.kml))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onOpenKml) { Text(stringResource(R.string.kml_open)) }
            if (state.kml != null) {
                TextButton(onClick = onRemoveKml) { Text(stringResource(R.string.kml_remove)) }
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
        ProjectionPicker(state.projectionType, onSelectProjection)
        CenterControls(
            view = state.view,
            wholeSphere = state.projectionType.family != ProjectionFamily.AZIMUTHAL,
            deviceLocation = state.deviceLocation,
            onCenterChange = onCenterChange,
            onLongitudeChange = onLongitudeChange,
            onLatitudeEnabledChange = onLatitudeEnabledChange,
        )
        AzimuthControl(state.view.azimuthMode, state.view.azimuth, onAzimuthModeChange, onAzimuthChange)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.graticule), Modifier.weight(1f))
            Switch(checked = state.showGraticule, onCheckedChange = onGraticuleChange)
        }
        Text(
            stringResource(R.string.viewer_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
    ProjectionFamily.AZIMUTHAL -> R.string.family_azimuthal
    ProjectionFamily.CYLINDRICAL -> R.string.family_cylindrical
    ProjectionFamily.PSEUDOCYLINDRICAL -> R.string.family_pseudocylindrical
}

/**
 * Center sliders. They show the value while dragging and only re-render when
 * released. For whole-sphere maps ([wholeSphere]) latitude starts off (fixed
 * at 0, the normal aspect) and has a switch, and longitude has quick picks.
 */
@Composable
private fun CenterControls(
    view: ViewSettings,
    wholeSphere: Boolean,
    deviceLocation: GeoPoint?,
    onCenterChange: (Double, Double) -> Unit,
    onLongitudeChange: (Double) -> Unit,
    onLatitudeEnabledChange: (Boolean) -> Unit,
) {
    var dragLon by remember(view.longitude) { mutableFloatStateOf(view.longitude.toFloat()) }
    var dragLat by remember(view.latitude) { mutableFloatStateOf(view.latitude.toFloat()) }
    val latitudeEnabled = !wholeSphere || view.latitudeEnabled

    Text(stringResource(R.string.center_longitude, dragLon), style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = dragLon,
        onValueChange = { dragLon = it },
        onValueChangeFinished = { onCenterChange(dragLon.toDouble(), dragLat.toDouble()) },
        valueRange = -180f..180f,
    )
    if (wholeSphere) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = view.longitude == 10.0,
                onClick = { onLongitudeChange(10.0) },
                label = { Text(stringResource(R.string.longitude_10e)) },
            )
            FilterChip(
                selected = view.longitude == 0.0,
                onClick = { onLongitudeChange(0.0) },
                label = { Text(stringResource(R.string.longitude_0)) },
            )
            FilterChip(
                selected = deviceLocation != null && view.longitude == deviceLocation.lon,
                onClick = { deviceLocation?.let { onLongitudeChange(it.lon) } },
                enabled = deviceLocation != null,
                label = { Text(stringResource(R.string.longitude_here)) },
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.center_latitude, if (latitudeEnabled) dragLat else 0f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (latitudeEnabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.weight(1f),
        )
        if (wholeSphere) Switch(checked = view.latitudeEnabled, onCheckedChange = onLatitudeEnabledChange)
    }
    Slider(
        value = if (latitudeEnabled) dragLat else 0f,
        onValueChange = { dragLat = it },
        onValueChangeFinished = { onCenterChange(dragLon.toDouble(), dragLat.toDouble()) },
        valueRange = -90f..90f,
        enabled = latitudeEnabled,
    )
}

/**
 * Azimuth slider with a mode selector: Off grays the slider out and fixes the
 * azimuth at 0; the other modes set the slider's range.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
        color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val labels = mapOf(
            AzimuthMode.OFF to R.string.azimuth_off,
            AzimuthMode.SIGNED to R.string.azimuth_signed,
            AzimuthMode.FULL to R.string.azimuth_full,
        )
        AzimuthMode.entries.forEachIndexed { index, m ->
            SegmentedButton(
                selected = m == mode,
                onClick = { onModeChange(m) },
                shape = SegmentedButtonDefaults.itemShape(index, AzimuthMode.entries.size),
            ) {
                Text(stringResource(labels.getValue(m)))
            }
        }
    }
    Slider(
        value = drag,
        onValueChange = { drag = it },
        onValueChangeFinished = { onAzimuthChange(drag.toDouble()) },
        valueRange = mode.range.start.toFloat()..mode.range.endInclusive.toFloat(),
        enabled = enabled,
    )
}

@Composable
private fun Label(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(8.dp))
    }
}

package net.interlecto.geoutils.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.interlecto.geoutils.R
import net.interlecto.geoutils.data.export.MapExporter.Format
import net.interlecto.geoutils.geometry.ProjectionFamily

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
                // Cylindrical maps are cut at an arbitrary latitude (Mercator's
                // square, central cylindrical's 75°), so fill the width instead.
                MapViewer(
                    state.scene,
                    Modifier.fillMaxSize(),
                    fitWidth = state.projectionType.family == ProjectionFamily.CYLINDRICAL,
                )
                if (state.rendering) CircularProgressIndicator()
            }
            HorizontalDivider()
            ControlPanel(
                state = state,
                viewModel = viewModel,
                onOpenImage = { openImage.launch(arrayOf("image/*")) },
                onOpenKml = { openKml.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
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

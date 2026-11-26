package net.interlecto.geoutils.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.interlecto.geoutils.R
import net.interlecto.geoutils.data.settings.AppSettings
import net.interlecto.geoutils.data.settings.PointPair
import net.interlecto.geoutils.data.settings.ThemeMode
import net.interlecto.geoutils.ui.map.PointPairEditor
import net.interlecto.geoutils.ui.map.SegmentedChoice

/**
 * The settings page: a list of sections. To add a setting, add its field to
 * [AppSettings], its storage to `SettingsRepository`, and a row to a section here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        val current = settings ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppearanceSection(current.themeMode, viewModel::setThemeMode)
            DefaultPointsSection(current.defaultPoints, viewModel::setDefaultPoints)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun AppearanceSection(themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit) {
    SectionTitle(stringResource(R.string.settings_appearance))
    Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
    SegmentedChoice(
        options = listOf(
            ThemeMode.SYSTEM to stringResource(R.string.theme_system),
            ThemeMode.LIGHT to stringResource(R.string.theme_light),
            ThemeMode.DARK to stringResource(R.string.theme_dark),
        ),
        selected = themeMode,
        onSelect = onThemeMode,
    )
}

@Composable
private fun DefaultPointsSection(points: PointPair, onSave: (PointPair) -> Unit) {
    SectionTitle(stringResource(R.string.settings_default_points))
    Text(
        stringResource(R.string.settings_default_points_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PointPairEditor(
        initialA = points.a,
        initialB = points.b,
        center = null,
        actionLabel = stringResource(R.string.save),
        onApply = { a, b -> onSave(PointPair(a, b)) },
    )
}

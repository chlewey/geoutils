package net.interlecto.geoutils.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.interlecto.geoutils.R
import net.interlecto.geoutils.ui.theme.GeoUtilsTheme

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val baseMapCount by viewModel.baseMapCount.collectAsStateWithLifecycle()
    HomeContent(baseMapCount = baseMapCount)
}

@Composable
private fun HomeContent(baseMapCount: Int) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.home_base_maps, baseMapCount),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    GeoUtilsTheme {
        HomeContent(baseMapCount = 0)
    }
}

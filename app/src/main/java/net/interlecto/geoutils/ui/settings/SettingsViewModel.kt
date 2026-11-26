package net.interlecto.geoutils.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.interlecto.geoutils.data.settings.AppSettings
import net.interlecto.geoutils.data.settings.PointPair
import net.interlecto.geoutils.data.settings.SettingsRepository
import net.interlecto.geoutils.data.settings.ThemeMode
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    /** The saved settings; null only until they are first read. */
    val settings: StateFlow<AppSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setDefaultPoints(points: PointPair) {
        viewModelScope.launch { repository.setDefaultPoints(points) }
    }
}

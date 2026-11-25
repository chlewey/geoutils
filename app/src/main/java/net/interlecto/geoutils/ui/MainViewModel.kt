package net.interlecto.geoutils.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.interlecto.geoutils.data.repository.BaseMapRepository
import net.interlecto.geoutils.data.settings.AppSettings
import net.interlecto.geoutils.data.settings.SettingsRepository
import javax.inject.Inject

/** Runs startup work while the splash screen is shown, and holds the app-wide settings. */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val baseMapRepository: BaseMapRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /** The saved settings; null until they are read, so the first frame already has the right theme. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val bundledReady = MutableStateFlow(false)

    /** True once the bundled data is in place and the settings are loaded; the splash screen waits for it. */
    val isReady: StateFlow<Boolean> = combine(bundledReady, settings) { ready, s -> ready && s != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            baseMapRepository.ensureBundled()
            bundledReady.value = true
        }
    }
}

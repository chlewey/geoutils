package net.interlecto.geoutils.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.interlecto.geoutils.data.repository.BaseMapRepository
import javax.inject.Inject

/** Runs startup work while the splash screen is shown. */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val baseMapRepository: BaseMapRepository,
) : ViewModel() {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        viewModelScope.launch {
            // Opening the database here keeps the first screen from waiting on it.
            baseMapRepository.count()
            _isReady.value = true
        }
    }
}

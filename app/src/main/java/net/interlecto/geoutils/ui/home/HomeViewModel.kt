package net.interlecto.geoutils.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.interlecto.geoutils.data.repository.BaseMapRepository
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    baseMapRepository: BaseMapRepository,
) : ViewModel() {

    val baseMapCount: StateFlow<Int> = baseMapRepository.observeAll()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

package com.evcockpit.presentation.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evcockpit.data.radio.RadioRepository
import com.evcockpit.data.radio.RadioStation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RadioUiState(
    val stations: List<RadioStation> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false
)

@HiltViewModel
class RadioViewModel @Inject constructor(private val repository: RadioRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(RadioUiState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null
    init { refresh() }

    fun refresh() {
        request?.cancel()
        request = viewModelScope.launch {
            mutableState.value = RadioUiState()
            try {
                mutableState.value = RadioUiState(repository.stations(), loading = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = RadioUiState(loading = false, failed = true)
            }
        }
    }
}

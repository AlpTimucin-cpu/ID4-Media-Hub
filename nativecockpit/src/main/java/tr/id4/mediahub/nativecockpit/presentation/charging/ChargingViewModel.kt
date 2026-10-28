package tr.id4.mediahub.nativecockpit.presentation.charging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tr.id4.mediahub.nativecockpit.domain.model.*
import tr.id4.mediahub.nativecockpit.domain.repository.ChargingRepository
import tr.id4.mediahub.nativecockpit.domain.repository.RouteRepository
import tr.id4.mediahub.nativecockpit.domain.usecase.StationCorridor

data class ChargingUiState(
    val visible: List<ChargingStation> = emptyList(),
    val selectedFilter: StationFilter = StationFilter.ALL,
    val total: Int = 0,
    val corridorCount: Int? = null,
    val routeProvided: Boolean = false,
    val verifiedAtUtc: String? = null,
    val loading: Boolean = true,
    val fromCache: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ChargingViewModel @Inject constructor(
    private val chargingRepository: ChargingRepository,
    routeRepository: RouteRepository
): ViewModel() {
    private val viewport=MutableStateFlow<GeoViewport?>(null)
    private val filter=MutableStateFlow(StationFilter.ALL)
    private val route=routeRepository.currentRoute
    init { refresh() }
    fun refresh() { viewModelScope.launch { chargingRepository.refresh() } }
    fun changeFilter(value: StationFilter) { filter.value=value }
    fun viewportChanged(bounds: GeoViewport) { if(bounds.valid())viewport.value=bounds }

    val uiState: StateFlow<ChargingUiState> = combine(
        chargingRepository.catalog,filter,route
    ) { catalog,selected,actualRoute ->
        Triple(catalog,selected,actualRoute?.routePoints.orEmpty())
    }.mapLatest { (catalog,selected,routePoints) ->
        withContext(Dispatchers.Default) {
            // Bound displayed feature count to avoid unresponsive map on low-end devices.
            val visible=catalog.stations.asSequence()
                .filter { when(selected){
                    StationFilter.ALL -> true
                    StationFilter.DC -> it.hasDc
                    StationFilter.ROUTE_CCS -> routePoints.size>=2 &&
                        StationCorridor.isAlongRoute(it,routePoints)
                }}.toList()
            ChargingUiState(visible,selected,catalog.stations.size,
                if(routePoints.size>=2) visible.count { StationCorridor.isAlongRoute(it,routePoints) }
                else null,routePoints.size>=2,catalog.verifiedAtUtc,
                catalog.loading,catalog.staleCache,catalog.error)
        }
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),ChargingUiState())
}

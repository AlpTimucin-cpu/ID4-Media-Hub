package tr.id4.mediahub.nativecockpit.presentation.cockpit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import tr.id4.mediahub.nativecockpit.domain.model.RouteSnapshot
import tr.id4.mediahub.nativecockpit.domain.model.GpsFix
import tr.id4.mediahub.nativecockpit.domain.repository.LocationRepository
import tr.id4.mediahub.nativecockpit.domain.repository.RouteRepository
import tr.id4.mediahub.nativecockpit.domain.repository.VehicleRepository
import tr.id4.mediahub.nativecockpit.domain.usecase.RouteProgressTracker

/** UI reflects actual source states. No hardcoded speed/SoC/range. */
data class CockpitUiState(
    val socPercent: Double? = null,
    val speedKmh: Double? = null,
    val voltageV: Double? = null,
    val batteryTemperatureC: Double? = null,
    val powerKw: Double? = null,
    // Future EnergyUseCase (model), not a sensor or a dummy constant.
    val estimatedRangeKm: Double? = null,
    val route: RouteSnapshot? = null,
    val gpsFix: GpsFix? = null
)

@HiltViewModel
class CockpitViewModel @Inject constructor(
    vehicleRepository: VehicleRepository,
    routeRepository: RouteRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {
    private val routeProgress=RouteProgressTracker()

    // Refresh once per second so old OBD values automatically disappear.
    private val clock: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    val uiState: StateFlow<CockpitUiState> = combine(
        vehicleRepository.telemetry,
        routeRepository.currentRoute,
        locationRepository.gpsFix,
        clock
    ) { telemetry, route, gps, now ->
        CockpitUiState(
            socPercent = telemetry.socPercent.current(now),
            speedKmh = telemetry.speedKmh.current(now),
            voltageV = telemetry.voltageV.current(now),
            batteryTemperatureC = telemetry.batteryTemperatureC.current(now),
            powerKw = telemetry.powerKw.current(now),
            route = routeProgress.enrich(route,gps,now),
            gpsFix = gps?.takeIf { it.usable(now) }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = CockpitUiState()
    )

    fun startGps() = locationRepository.start()
    fun stopGps() = locationRepository.stop()

    override fun onCleared() {
        locationRepository.stop()
        super.onCleared()
    }
}

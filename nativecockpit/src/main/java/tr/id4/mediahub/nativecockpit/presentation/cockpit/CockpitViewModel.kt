package tr.id4.mediahub.nativecockpit.presentation.cockpit

import tr.id4.mediahub.nativecockpit.domain.model.GpsFix
import tr.id4.mediahub.nativecockpit.domain.model.RouteSnapshot

/** Main MapLibre cockpit values originate from the central NavigationViewModel. */
data class CockpitUiState(
    val socPercent: Double? = null,
    val speedKmh: Double? = null,
    val voltageV: Double? = null,
    val batteryTemperatureC: Double? = null,
    val powerKw: Double? = null,
    val estimatedRangeKm: Double? = null,
    val route: RouteSnapshot? = null,
    val gpsFix: GpsFix? = null
)

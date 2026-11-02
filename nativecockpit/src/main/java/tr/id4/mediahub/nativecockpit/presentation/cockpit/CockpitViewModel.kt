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
    // MODEL/TAHMİN only; never present a missing OBD field as live.
    val chargingNeededEstimate: Boolean? = null,
    val chargingEnergyKwh: Double? = null,
    val energyEstimateStatus: String? = null,
    val socSourceLabel: String? = null,
    val simulationEnabled: Boolean = false,
    val gpsWarning: String? = null,
    val route: RouteSnapshot? = null,
    val gpsFix: GpsFix? = null
)

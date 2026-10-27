package tr.id4.mediahub.nativecockpit.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tr.id4.mediahub.nativecockpit.domain.model.VehicleTelemetry
import tr.id4.mediahub.nativecockpit.domain.repository.VehicleRepository

// Production-safe default: no connection means all measurements are unknown.
class DisconnectedVehicleRepository @Inject constructor() : VehicleRepository {
    private val _telemetry = MutableStateFlow(VehicleTelemetry())
    override val telemetry: StateFlow<VehicleTelemetry> = _telemetry.asStateFlow()
}

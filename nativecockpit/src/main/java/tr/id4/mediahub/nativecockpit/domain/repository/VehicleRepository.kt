package tr.id4.mediahub.nativecockpit.domain.repository

import kotlinx.coroutines.flow.StateFlow
import tr.id4.mediahub.nativecockpit.domain.model.VehicleTelemetry

// Later, a read-only Bluetooth ELM327 adapter will implement this contract.
interface VehicleRepository {
    val telemetry: StateFlow<VehicleTelemetry>
}

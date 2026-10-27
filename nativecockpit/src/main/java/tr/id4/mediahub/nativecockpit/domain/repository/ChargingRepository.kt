package tr.id4.mediahub.nativecockpit.domain.repository

import kotlinx.coroutines.flow.StateFlow
import tr.id4.mediahub.nativecockpit.domain.model.ChargingCatalog

interface ChargingRepository {
    val catalog: StateFlow<ChargingCatalog>
    suspend fun refresh()
}

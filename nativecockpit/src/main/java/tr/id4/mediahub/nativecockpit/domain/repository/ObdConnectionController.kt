package tr.id4.mediahub.nativecockpit.domain.repository

import kotlinx.coroutines.flow.StateFlow
import tr.id4.mediahub.nativecockpit.domain.model.ObdConnectionState

interface ObdConnectionController {
    val connection: StateFlow<ObdConnectionState>
    /** Lists bonded adapters only; never automatically scans or connects. */
    fun refreshPaired()
    fun connect(address: String)
    fun disconnect()
    fun permissionDenied()
}

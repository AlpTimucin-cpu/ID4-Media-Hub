package com.evcockpit.domain.repository

import kotlinx.coroutines.flow.StateFlow
import com.evcockpit.domain.model.ObdConnectionState

interface ObdConnectionController {
    val connection: StateFlow<ObdConnectionState>
    fun refreshPaired()
    fun connect(address: String)
    fun disconnect()
    fun permissionDenied()
    /** Only three allowlisted, single-shot UDS ReadDataByIdentifier requests. */
    fun requestMebDiagnostic(command: String)
    fun clearMebDiagnostic()
}

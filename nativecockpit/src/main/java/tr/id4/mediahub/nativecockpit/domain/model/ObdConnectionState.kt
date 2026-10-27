package tr.id4.mediahub.nativecockpit.domain.model

/** A driver must explicitly choose an already-paired, personally owned adapter. */
data class PairedObdDevice(val name: String, val address: String)

enum class ObdPhase { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class ObdConnectionState(
    val phase: ObdPhase = ObdPhase.DISCONNECTED,
    val devices: List<PairedObdDevice> = emptyList(),
    val selectedDevice: String? = null,
    val error: String? = null,
    val lastSpeedAtMs: Long? = null,
    val lastPollSucceeded: Boolean = false
)

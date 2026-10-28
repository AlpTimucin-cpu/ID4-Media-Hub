package tr.id4.mediahub.nativecockpit.domain.repository

import kotlinx.coroutines.flow.StateFlow
import tr.id4.mediahub.nativecockpit.domain.model.GpsFix

interface LocationRepository {
    val gpsFix: StateFlow<GpsFix?>
    /** Foreground-only GPS after explicit Android permission. */
    fun start()
    fun stop()
    fun acceptFix(fix: GpsFix)
}

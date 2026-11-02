package tr.id4.mediahub.nativecockpit.car

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-process voice ownership: avoid duplicate phone and Android Auto announcements. */
object CarNavigationOwnership {
    private val mutableNavigating = MutableStateFlow(false)
    val navigating: StateFlow<Boolean> = mutableNavigating.asStateFlow()
    fun setNavigating(value: Boolean) { mutableNavigating.value = value }
}

package tr.id4.mediahub.nativecockpit.presentation.charging

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import tr.id4.mediahub.nativecockpit.domain.model.*
import tr.id4.mediahub.nativecockpit.domain.repository.ChargingRepository
import tr.id4.mediahub.nativecockpit.domain.repository.RouteRepository
import tr.id4.mediahub.nativecockpit.domain.repository.VehicleRepository
import tr.id4.mediahub.nativecockpit.domain.usecase.*

/** One immutable projection for the original RoutePanel and MapLibre overlay. */
@HiltViewModel
class AutomaticChargePlanViewModel @Inject constructor(
    @ApplicationContext context: Context,
    routes: RouteRepository,
    charging: ChargingRepository,
    vehicle: VehicleRepository
) : ViewModel() {
    private val preferences = context.applicationContext.getSharedPreferences(
        "eya_energy_calibration", Context.MODE_PRIVATE
    )

    private data class Inputs(
        val route: RouteSnapshot?,
        val stations: List<ChargingStation>,
        val telemetry: VehicleTelemetry
    )

    private fun preference(key: String): Double? = runCatching {
        preferences.getFloat(key, Float.NaN).toDouble()
            .takeIf { it.isFinite() }
    }.getOrNull()

    val uiState: StateFlow<AutomaticChargePlan> = combine(
        routes.currentRoute,
        charging.catalog,
        vehicle.telemetry
    ) { route, catalog, telemetry ->
        Inputs(route, catalog.stations, telemetry)
    }.mapLatest { input ->
        kotlinx.coroutines.withContext(Dispatchers.Default) {
            val route = input.route
                ?: return@withContext AutomaticChargePlan.Unavailable("Rota oluşturulmadı")
            val capacity = preference("usableBatteryKwh")
            val consumption = preference("referenceWhPerKm")
            val reserve = preference("arrivalReservePct")
            if (capacity == null || consumption == null || reserve == null) {
                return@withContext AutomaticChargePlan.Unavailable(
                    "Batarya kapasitesi, tüketim ve varış rezervi kalibrasyonu gerekli"
                )
            }
            val now = System.currentTimeMillis()
            val soc = input.telemetry.socPercent.current(now)
            val origin = if (soc != null) {
                val measuredAt = input.telemetry.socPercent.measuredAtMillis
                    ?: return@withContext AutomaticChargePlan.Unavailable("OBD zamanı yok")
                DepartureSoc.FreshObd(soc, measuredAt)
            } else {
                DepartureSoc.ExplicitAssumption(
                    preference("explicitAssumedSocPct") ?: 80.0
                )
            }
            try {
                AutomaticChargePlanner.plan(
                    route, input.stations, origin,
                    capacity, consumption, reserve, now
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                AutomaticChargePlan.Unavailable("Otomatik şarj hesabı yapılamadı")
            }
        }
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000L),
        AutomaticChargePlan.Unavailable("Rota bekleniyor")
    )
}

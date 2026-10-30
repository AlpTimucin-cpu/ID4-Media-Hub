package tr.id4.mediahub.nativecockpit.presentation.charging

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import tr.id4.mediahub.nativecockpit.domain.model.*
import tr.id4.mediahub.nativecockpit.domain.repository.*
import tr.id4.mediahub.nativecockpit.domain.usecase.*

data class RouteChargingUiState(
    val routeId: String? = null,
    val plan: AutomaticChargePlan = AutomaticChargePlan.Empty,
    val socSource: String = "MODEL/TAHMİN",
    val catalogIsStale: Boolean = false
)

/** Separate use case owner: no changes to the media, OBD or navigation UI. */
@HiltViewModel
class RouteChargePlanViewModel @Inject constructor(
    @ApplicationContext context: Context,
    routes: RouteRepository,
    charging: ChargingRepository,
    vehicle: VehicleRepository
) : ViewModel() {
    private val preferences = context.getSharedPreferences(
        "eya_energy_calibration", Context.MODE_PRIVATE
    )

    private data class Prepared(
        val route: RouteSnapshot? = null,
        val candidates: List<DCCorridorCandidate> = emptyList(),
        val cacheStale: Boolean = false
    )

    // Index and geodesic projection only when route or EPDK catalog changes,
    // never every GPS/OBD heartbeat.
    private val prepared = combine(routes.currentRoute, charging.catalog) { route, catalog ->
        withContext(Dispatchers.Default) {
            Prepared(
                route,
                if (route == null) emptyList()
                else AutomaticDcChargingPlanner.candidates(route, catalog.stations),
                catalog.staleCache
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), Prepared())

    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(10_000L)
        }
    }

    private fun config(key: String): Double? = runCatching {
        preferences.getFloat(key, Float.NaN).toDouble()
            .takeIf { it.isFinite() }
    }.getOrNull()

    val uiState: StateFlow<RouteChargingUiState> = combine(
        prepared, vehicle.telemetry, clock
    ) { selected, telemetry, now ->
        val route = selected.route
        if (route?.routeId == null)
            return@combine RouteChargingUiState()

        val capacity = config("usableBatteryKwh")
        val consumption = config("referenceWhPerKm")
        val reserve = config("arrivalReservePct")
        if (capacity == null || capacity <= 0 || consumption == null ||
            consumption <= 0 || reserve == null || reserve !in 0.0..100.0
        ) return@combine RouteChargingUiState(
            routeId = route.routeId,
            plan = AutomaticChargePlan.Unavailable(
                "Onaylı kapasite, tüketim ve varış rezervi kalibrasyonu gerekli"
            ),
            catalogIsStale = selected.cacheStale
        )

        val freshObd = telemetry.socPercent.current(now)
        val measuredAt = telemetry.socPercent.measuredAtMillis
        val stored = config("explicitAssumedSocPct")
        val soc = freshObd ?: stored ?: 80.0
        val source = if (freshObd != null && measuredAt != null)
            DepartureSoc.FreshObd(freshObd, measuredAt)
        else DepartureSoc.ExplicitAssumption(soc)

        val baseline = EvBaselineEnergyEstimator.estimate(
            BaselineEnergyInputs(
                route = route,
                departureSoc = source,
                usableBatteryKwh = capacity,
                consumptionWhPerKm = consumption,
                consumptionBasis = ConsumptionBasis.REFERENCE_MODEL,
                requiredArrivalSocPercent = reserve,
                nowMs = now,
                speedKmh = telemetry.speedKmh.current(now)
            )
        )
        val plan = AutomaticDcChargingPlanner.plan(
            route, selected.candidates, baseline, capacity, soc, reserve
        )
        RouteChargingUiState(
            routeId = route.routeId,
            plan = plan,
            socSource = if (source is DepartureSoc.FreshObd) "CANLI OBD · MODEL/TAHMİN"
                else if (stored == null) "Varsayılan Başlangıç %80 · MODEL/TAHMİN"
                else "Seçilmiş Başlangıç SoC · MODEL/TAHMİN",
            catalogIsStale = selected.cacheStale
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), RouteChargingUiState())
}

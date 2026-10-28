package tr.id4.mediahub.nativecockpit.domain.usecase

import tr.id4.mediahub.nativecockpit.domain.model.RouteSnapshot
import tr.id4.mediahub.nativecockpit.domain.model.validRouteGeometry

/** Real OBD readings and explicitly assumed SoC must never be conflated. */
sealed interface DepartureSoc {
    data class FreshObd(val percent: Double, val measuredAtMs: Long) : DepartureSoc
    data class ExplicitAssumption(val percent: Double) : DepartureSoc
}

enum class ConsumptionBasis { CALIBRATED, REFERENCE_MODEL }

/** All inputs explicit: do not invent ID.4 battery capacity or consumption. */
data class BaselineEnergyInputs(
    val route: RouteSnapshot,
    val departureSoc: DepartureSoc,
    val usableBatteryKwh: Double,
    val consumptionWhPerKm: Double,
    val consumptionBasis: ConsumptionBasis,
    val requiredArrivalSocPercent: Double,
    val nowMs: Long
)

sealed interface BaselineEnergyResult {
    data class Unavailable(val reason: String) : BaselineEnergyResult
    data class Estimate(
        val routeKm: Double,
        val driveEnergyKwh: Double,
        val arrivalSocPercent: Double,
        val requiredChargingEnergyKwh: Double,
        val chargingStopNeeded: Boolean,
        val usedLiveObd: Boolean,
        val consumptionBasis: ConsumptionBasis
    ) : BaselineEnergyResult
}

/** Baseline only. No unsupported weather, road-grade, charger or traffic claims. */
object EvBaselineEnergyEstimator {
    private const val MAX_OBD_AGE_MS = 10_000L

    fun estimate(input: BaselineEnergyInputs): BaselineEnergyResult {
        val route = input.route
        val km = route.totalKm
        if (route.routeId.isNullOrBlank() ||
            !validRouteGeometry(route.routePoints) ||
            km == null || !km.isFinite() || km <= 0.0
        ) return BaselineEnergyResult.Unavailable("Doğrulanmış OSRM rota mesafesi gerekli")

        if (!input.usableBatteryKwh.isFinite() || input.usableBatteryKwh <= 0.0 ||
            !input.consumptionWhPerKm.isFinite() || input.consumptionWhPerKm <= 0.0 ||
            !input.requiredArrivalSocPercent.isFinite() ||
            input.requiredArrivalSocPercent !in 0.0..100.0
        ) return BaselineEnergyResult.Unavailable("Batarya, tüketim veya rezerv bilgisi geçersiz")

        val soc = when (val source = input.departureSoc) {
            is DepartureSoc.FreshObd -> {
                if (source.measuredAtMs > input.nowMs ||
                    input.nowMs - source.measuredAtMs > MAX_OBD_AGE_MS
                ) return BaselineEnergyResult.Unavailable("OBD SoC ölçümü güncel değil")
                source.percent
            }
            is DepartureSoc.ExplicitAssumption -> source.percent
        }
        if (!soc.isFinite() || soc !in 0.0..100.0)
            return BaselineEnergyResult.Unavailable("Başlangıç SoC değeri geçersiz")

        val driveKwh = km * input.consumptionWhPerKm / 1000.0
        if (!driveKwh.isFinite())
            return BaselineEnergyResult.Unavailable("Rota enerji hesabı taşma yaptı")
        val arrivalPercent = soc - 100.0 * driveKwh / input.usableBatteryKwh
        val deficit = (input.requiredArrivalSocPercent - arrivalPercent).coerceAtLeast(0.0)
        val chargingKwh = deficit * input.usableBatteryKwh / 100.0
        return BaselineEnergyResult.Estimate(
            routeKm = km,
            driveEnergyKwh = driveKwh,
            arrivalSocPercent = arrivalPercent,
            requiredChargingEnergyKwh = chargingKwh,
            chargingStopNeeded = arrivalPercent < input.requiredArrivalSocPercent,
            usedLiveObd = input.departureSoc is DepartureSoc.FreshObd,
            consumptionBasis = input.consumptionBasis
        )
    }
}

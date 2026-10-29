package tr.id4.mediahub.nativecockpit.domain.usecase

import kotlin.math.*
import tr.id4.mediahub.nativecockpit.domain.model.*

/**
 * Deterministic, conservative baseline. NOT live charger availability,
 * charger-road detour time, battery charging curves, or verified optimality.
 * Missing calibration / route / candidate is always Unavailable.
 */
sealed interface AutomaticChargePlan {
    data class Unavailable(val reason: String) : AutomaticChargePlan
    data class Ready(
        val routeId: String,
        val source: String,
        val stops: List<PlannedDcStop>,
        val arrivalSocPercent: Double,
        val estimatedDriveKwh: Double
    ) : AutomaticChargePlan
}

data class PlannedDcStop(
    val station: ChargingStation,
    val routeKm: Double,
    val arrivalSocPercent: Double,
    val suggestedChargeKwh: Double,
    val verifiedPowerKw: Double?
)

object AutomaticChargePlanner {
    private const val EARTH_METERS = 6_371_008.8
    private const val MAX_STOPS = 12

    fun plan(
        route: RouteSnapshot,
        stations: List<ChargingStation>,
        departureSoc: DepartureSoc,
        batteryKwh: Double,
        consumptionWhPerKm: Double,
        reservePercent: Double,
        nowMs: Long
    ): AutomaticChargePlan {
        val id = route.routeId ?: return AutomaticChargePlan.Unavailable("Gerçek OSRM rotası yok")
        val estimated = EvBaselineEnergyEstimator.estimate(
            BaselineEnergyInputs(
                route = route,
                departureSoc = departureSoc,
                usableBatteryKwh = batteryKwh,
                consumptionWhPerKm = consumptionWhPerKm,
                consumptionBasis = ConsumptionBasis.REFERENCE_MODEL,
                requiredArrivalSocPercent = reservePercent,
                nowMs = nowMs
            )
        )
        if (estimated !is BaselineEnergyResult.Estimate) {
            return AutomaticChargePlan.Unavailable(
                (estimated as? BaselineEnergyResult.Unavailable)?.reason ?: "Enerji modeli hazır değil"
            )
        }
        val routeKm = estimated.routeKm
        val kwhPerKm = estimated.effectiveWhPerKm / 1000.0
        if (!routeKm.isFinite() || kwhPerKm <= 0.0) {
            return AutomaticChargePlan.Unavailable("Tüketim modeli geçersiz")
        }
        val startSoc = when (departureSoc) {
            is DepartureSoc.FreshObd -> departureSoc.percent
            is DepartureSoc.ExplicitAssumption -> departureSoc.percent
        }
        val sourceLabel = when (departureSoc) {
            is DepartureSoc.FreshObd -> "CANLI OBD başlangıç · MODEL/TAHMİN"
            is DepartureSoc.ExplicitAssumption -> "Varsayılan/seçilmiş başlangıç · MODEL/TAHMİN"
        }
        if (!estimated.chargingStopNeeded) return AutomaticChargePlan.Ready(
            id, sourceLabel, emptyList(), estimated.arrivalSocPercent, estimated.driveEnergyKwh
        )
        if (stations.isEmpty()) return AutomaticChargePlan.Unavailable(
            "Güzergâhta doğrulanmış DC istasyonu yok"
        )
        val routeIndex = StationCorridor.index(route.routePoints)
        val candidate = stations.asSequence().filter(routeIndex::isDcAlongRoute)
            .mapNotNull { station ->
                val fraction = alongFraction(route.routePoints, station.coordinate)
                    ?: return@mapNotNull null
                station to routeKm * fraction
            }.filter { it.second > 0.2 && it.second < routeKm - 0.2 }
            .sortedBy { it.second }.toList()
        if (candidate.isEmpty()) return AutomaticChargePlan.Unavailable(
            "Rota boyunca ulaşılabilir doğrulanmış DC adayı yok"
        )

        val reserveKwh = batteryKwh * reservePercent / 100.0
        var energy = batteryKwh * startSoc / 100.0
        var km = 0.0
        val stops = mutableListOf<PlannedDcStop>()
        val visited = HashSet<String>()
        repeat(MAX_STOPS + 1) {
            val endNeed = (routeKm - km) * kwhPerKm + reserveKwh
            if (energy >= endNeed) return AutomaticChargePlan.Ready(
                id, sourceLabel, stops, (energy - (routeKm - km) * kwhPerKm) /
                    batteryKwh * 100.0, estimated.driveEnergyKwh
            )
            if (stops.size >= MAX_STOPS) return AutomaticChargePlan.Unavailable(
                "Otomatik plan sınırı aşıldı; manuel rota doğrulaması gerekli"
            )
            val reachable = km + (energy - reserveKwh).coerceAtLeast(0.0) / kwhPerKm
            // Farthest reachable DC minimizes baseline number of stops.
            // Power information is NOT inferred from an unqualified numeric string.
            val next = candidate.asReversed().firstOrNull {
                it.second <= reachable + 0.001 &&
                    it.second > km + 0.2 &&
                    it.first.id !in visited
            } ?: return AutomaticChargePlan.Unavailable(
                "Rezerv SoC korunarak erişilebilen sonraki DC istasyonu yok"
            )
            val (station, atKm) = next
            energy -= (atKm - km) * kwhPerKm
            km = atKm
            if (!energy.isFinite() || energy < -0.001)
                return AutomaticChargePlan.Unavailable("Enerji hesabı geçersiz")
            // Full charge is an upper-bound scenario, NOT a charging-curve estimate.
            val charge = (batteryKwh - energy).coerceAtLeast(0.0)
            stops += PlannedDcStop(
                station = station,
                routeKm = atKm,
                arrivalSocPercent = energy / batteryKwh * 100.0,
                suggestedChargeKwh = charge,
                verifiedPowerKw = station.sockets.asSequence()
                    .filter { it.isDc }.mapNotNull { verifiedKw(it.powerRaw) }.maxOrNull()
            )
            energy = batteryKwh
            visited += station.id
        }
        return AutomaticChargePlan.Unavailable("Plan oluşturulamadı")
    }

    /**
     * Accept only explicit kW units. The current EPDK powerRaw contract
     * does not establish whether a bare '150' is kW, so never guess.
     */
    private fun verifiedKw(raw: String): Double? {
        val match = Regex("^\\s*(\\d+(?:[.,]\\d+)?)\\s*kW\\s*$", RegexOption.IGNORE_CASE)
            .matchEntire(raw) ?: return null
        return match.groupValues[1].replace(',', '.').toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in 1.0..700.0 }
    }

    private fun alongFraction(path: List<GeoCoordinate>, point: GeoCoordinate): Double? {
        if (!validRouteGeometry(path) || !point.valid()) return null
        val lengths = DoubleArray(path.size)
        for (i in 1..path.lastIndex) lengths[i] = lengths[i-1] + haversine(path[i-1], path[i])
        val total = lengths.last()
        if (!total.isFinite() || total <= 0.0) return null
        val dxUnit = 111_320.0 * cos(Math.toRadians(point.latitude))
        var best = Double.POSITIVE_INFINITY
        var along = 0.0
        for (i in 0 until path.lastIndex) {
            val a = path[i]; val b = path[i+1]
            val ax = (a.longitude-point.longitude)*dxUnit
            val ay = (a.latitude-point.latitude)*111_195.0
            val sx = (b.longitude-a.longitude)*dxUnit
            val sy = (b.latitude-a.latitude)*111_195.0
            val len2 = sx*sx+sy*sy
            if (len2 < 0.001) continue
            val t = (-(ax*sx+ay*sy)/len2).coerceIn(0.0, 1.0)
            val dist = hypot(ax+t*sx, ay+t*sy)
            if (dist < best) {
                best = dist
                along = lengths[i]+t*(lengths[i+1]-lengths[i])
            }
        }
        return (along/total).takeIf { it.isFinite() && it in 0.0..1.0 }
    }

    private fun haversine(a: GeoCoordinate, b: GeoCoordinate): Double {
        val x = Math.toRadians(b.latitude-a.latitude)
        val y = Math.toRadians(b.longitude-a.longitude)
        val p = Math.toRadians(a.latitude); val q = Math.toRadians(b.latitude)
        return 2*EARTH_METERS*asin(sqrt(
            (sin(x/2).pow(2)+cos(p)*cos(q)*sin(y/2).pow(2)).coerceIn(0.0,1.0)
        ))
    }
}

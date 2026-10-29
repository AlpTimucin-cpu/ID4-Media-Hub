package tr.id4.mediahub.nativecockpit.domain.usecase

import kotlin.math.*
import tr.id4.mediahub.nativecockpit.domain.model.*

/** Candidate status, not a guaranteed charger reservation or driving detour. */
sealed interface AutomaticChargePlan {
    data object Empty : AutomaticChargePlan
    data class Unavailable(val reason: String) : AutomaticChargePlan
    data class Estimate(
        val stops: List<ProposedDcStop>,
        val totalSuggestedChargeKwh: Double,
        val assumedChargeTargetPercent: Double = 80.0,
        val source: String = "MODEL/TAHMİN · gerçek sürüş sapması ve şarj doluluğu doğrulanmadı"
    ) : AutomaticChargePlan
}

data class ProposedDcStop(
    val station: ChargingStation,
    val distanceFromStartKm: Double,
    val suggestedEnergyKwh: Double,
    val estimatedArrivalSocPercent: Double
)

data class DCCorridorCandidate(
    val station: ChargingStation,
    val distanceFromStartKm: Double
)

/** Pure offline feasibility proposal; requires separately calibrated energy inputs. */
object AutomaticDcChargingPlanner {
    fun candidates(route: RouteSnapshot, stations: List<ChargingStation>): List<DCCorridorCandidate> {
        if (route.routeId.isNullOrBlank() || !validRouteGeometry(route.routePoints) ||
            route.totalKm == null || route.totalKm <= 0
        ) return emptyList()
        val path = route.routePoints
        val cumulative = DoubleArray(path.size)
        for (i in 1 until path.size) {
            cumulative[i] = cumulative[i - 1] + haversine(path[i-1], path[i])
        }
        val length = cumulative.last()
        if (length < 1.0) return emptyList()
        val index = StationCorridor.index(path)
        return stations.asSequence()
            .filter(index::isDcAlongRoute)
            .mapNotNull { station ->
                val along = projectedDistance(path, cumulative, station.coordinate)
                val km = along / length * route.totalKm
                km.takeIf { it.isFinite() && it in 1.0..route.totalKm }
                    ?.let { DCCorridorCandidate(station, it) }
            }
            .distinctBy { it.station.id }
            .sortedBy { it.distanceFromStartKm }
            .toList()
    }

    fun plan(
        route: RouteSnapshot,
        corridor: List<DCCorridorCandidate>,
        baseline: BaselineEnergyResult,
        usableBatteryKwh: Double,
        startSocPercent: Double,
        reserveSocPercent: Double
    ): AutomaticChargePlan {
        if (baseline !is BaselineEnergyResult.Estimate ||
            route.routeId.isNullOrBlank() || !route.totalKm.isValidPositive() ||
            !usableBatteryKwh.isValidPositive() ||
            !startSocPercent.isFinite() || startSocPercent !in 0.0..100.0 ||
            !reserveSocPercent.isFinite() || reserveSocPercent !in 0.0..100.0 ||
            !baseline.effectiveWhPerKm.isValidPositive()
        ) return AutomaticChargePlan.Unavailable("Doğrulanmış rota ve enerji kalibrasyonu yok")

        val energyPerKm = baseline.effectiveWhPerKm / 1000.0
        val batteryReserve = reserveSocPercent / 100.0 * usableBatteryKwh
        val destinationKm = route.totalKm!!
        var energyKwh = usableBatteryKwh * startSocPercent / 100.0
        var lastKm = 0.0
        val planned = mutableListOf<ProposedDcStop>()
        val used = HashSet<String>()
        // Charger speeds are NOT inferred from ambiguous EPDK powerRaw.
        // 80% is a labelled modelling assumption, not the car's command.
        repeat(12) {
            val destinationNeed = (destinationKm - lastKm) * energyPerKm
            if (energyKwh - destinationNeed >= batteryReserve) {
                return AutomaticChargePlan.Estimate(
                    planned, planned.sumOf { it.suggestedEnergyKwh }
                )
            }
            val reachable = corridor.asSequence()
                .filter { it.station.id !in used && it.distanceFromStartKm > lastKm + 1.0 }
                .filter { candidate ->
                    val demand = (candidate.distanceFromStartKm - lastKm) * energyPerKm
                    energyKwh - demand >= batteryReserve
                }
                .maxByOrNull { it.distanceFromStartKm }
                ?: return AutomaticChargePlan.Unavailable(
                    "Rezerv korunarak ulaşılabilecek doğrulanmış DC adayı bulunamadı"
                )

            val driveEnergy = (reachable.distanceFromStartKm - lastKm) * energyPerKm
            val arrivalKwh = energyKwh - driveEnergy
            val targetKwh = usableBatteryKwh * 0.8
            val chargeKwh = (targetKwh - arrivalKwh).coerceAtLeast(0.0)
            if (chargeKwh < 0.1) return AutomaticChargePlan.Unavailable(
                "80% şarj varsayımıyla devam eden rota güvenilir değil"
            )

            planned += ProposedDcStop(
                reachable.station,
                reachable.distanceFromStartKm,
                chargeKwh,
                100.0 * arrivalKwh / usableBatteryKwh
            )
            used += reachable.station.id
            energyKwh = targetKwh
            lastKm = reachable.distanceFromStartKm
        }
        return AutomaticChargePlan.Unavailable("Rota çok fazla şarj durağı gerektiriyor")
    }

    private fun Double?.isValidPositive() = this != null && isFinite() && this > 0.0

    private fun haversine(a: GeoCoordinate, b: GeoCoordinate): Double {
        val p = Math.toRadians(a.latitude)
        val q = Math.toRadians(b.latitude)
        val dLat = q-p
        val dLon = Math.toRadians(b.longitude-a.longitude)
        val z = sin(dLat/2).pow(2) + cos(p)*cos(q)*sin(dLon/2).pow(2)
        return 2.0*6_371_008.8*asin(sqrt(z.coerceIn(0.0,1.0)))
    }

    private fun projectedDistance(
        path: List<GeoCoordinate>,
        cumulative: DoubleArray,
        point: GeoCoordinate
    ): Double {
        val factorLon = 111_320.0 * cos(Math.toRadians(point.latitude))
        val factorLat = 111_195.0
        var nearest = Double.POSITIVE_INFINITY
        var best = 0.0
        for (i in 0 until path.lastIndex) {
            val a = path[i]
            val b = path[i+1]
            val ax=(a.longitude-point.longitude)*factorLon
            val ay=(a.latitude-point.latitude)*factorLat
            val dx=(b.longitude-a.longitude)*factorLon
            val dy=(b.latitude-a.latitude)*factorLat
            val length2=dx*dx+dy*dy
            val fraction=if(length2>0.01) (-(ax*dx+ay*dy)/length2).coerceIn(0.0,1.0) else 0.0
            val off=hypot(ax+fraction*dx,ay+fraction*dy)
            if(off<nearest) {
                nearest=off
                best=cumulative[i]+(cumulative[i+1]-cumulative[i])*fraction
            }
        }
        return best
    }
}

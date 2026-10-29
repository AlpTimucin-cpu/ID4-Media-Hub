package tr.id4.mediahub.nativecockpit.domain.usecase

import kotlin.math.*
import tr.id4.mediahub.nativecockpit.domain.model.*

/** Input power must be confirmed kW from a documented source, NOT EPDK powerRaw. */
data class AutomaticChargeInputs(
    val route: RouteSnapshot,
    val dcStations: List<ChargingStation>,
    val verifiedPowerKwByStationId: Map<String, Double>,
    val startSocPercent: Double,
    val usableBatteryKwh: Double,
    val consumptionWhPerKm: Double,
    val reserveSocPercent: Double,
    val fromLiveObd: Boolean,
    val preferredChargingCeilingPercent: Double = 80.0
)

data class PredictedChargeStop(
    val stationId: String,
    val stationName: String,
    val routeKm: Double,
    val chargerPowerKw: Double,
    val arriveSocPercent: Double,
    val chargeToSocPercent: Double,
    val chargeKwh: Double
)

sealed interface AutomaticChargeResult {
    data class Unavailable(val reason: String) : AutomaticChargeResult
    data class Estimate(
        val routeId: String,
        val stops: List<PredictedChargeStop>,
        val destinationSocPercent: Double,
        val usedLiveObd: Boolean
    ) : AutomaticChargeResult
}

/**
 * Offline, deterministic, conservative feasibility planner. NOT an optimal
 * charging-time or detour planner: charger occupancy, real detours, tariffs,
 * taper curves, and temperature are unknown.
 * All results must be labelled MODEL/TAHMİN.
 */
object AutomaticChargePlanner {
    private data class Candidate(
        val station: ChargingStation,
        val km: Double,
        val kw: Double
    )
    fun estimate(input: AutomaticChargeInputs): AutomaticChargeResult {
        val route = input.route
        val km = route.totalKm
        if (route.routeId.isNullOrBlank() || km == null || km <= 0.0 ||
            !km.isFinite() || !validRouteGeometry(route.routePoints))
            return AutomaticChargeResult.Unavailable("Doğrulanmış rota bulunmuyor")
        if (input.startSocPercent !in 0.0..100.0 ||
            input.reserveSocPercent !in 0.0..40.0 ||
            input.usableBatteryKwh !in 1.0..250.0 ||
            input.consumptionWhPerKm !in 50.0..600.0 ||
            input.preferredChargingCeilingPercent !in 60.0..100.0)
            return AutomaticChargeResult.Unavailable("Enerji girdileri kalibre edilmedi")

        val kwhPerKm = input.consumptionWhPerKm / 1000.0
        val pctPerKm = kwhPerKm * 100.0 / input.usableBatteryKwh
        val indexed = StationCorridor.index(route.routePoints)
        val geometryKm = routeGeometryKm(route.routePoints)
        if (geometryKm <= 0.0) return AutomaticChargeResult.Unavailable("Rota geometrisi bozuk")

        val candidates = input.dcStations.asSequence()
            .filter { it.hasDc && indexed.isDcAlongRoute(it) }
            .mapNotNull { station ->
                val power = input.verifiedPowerKwByStationId[station.id]
                    ?.takeIf { it.isFinite() && it in 20.0..1000.0 }
                    ?: return@mapNotNull null
                val fraction = projectRouteFraction(
                    route.routePoints, station.coordinate
                ) ?: return@mapNotNull null
                Candidate(station, fraction * km, power)
            }
            .filter { it.km > 0.5 && it.km < km - 0.5 }
            .distinctBy { it.station.id }
            .sortedBy { it.km }
            .toList()

        var positionKm = 0.0
        var currentSoc = input.startSocPercent
        val stops = ArrayList<PredictedChargeStop>()

        repeat(12) {
            val destinationSoc = currentSoc - (km - positionKm) * pctPerKm
            if (destinationSoc >= input.reserveSocPercent) {
                return AutomaticChargeResult.Estimate(
                    route.routeId!!, stops, destinationSoc, input.fromLiveObd
                )
            }
            // The farthest *reachable* verified DC minimizes number of stops,
            // not necessarily total charging time.
            val reachable = candidates.filter { candidate ->
                candidate.km > positionKm + 0.5 &&
                    currentSoc - (candidate.km - positionKm) * pctPerKm >= input.reserveSocPercent
            }
            val next = reachable.maxWithOrNull(compareBy<Candidate> { it.km }.thenBy { it.kw })
                ?: return AutomaticChargeResult.Unavailable(
                    "Rezerv SoC ile erişilebilir, gücü doğrulanmış DC bulunamadı"
                )
            val arriveSoc = currentSoc - (next.km - positionKm) * pctPerKm
            val nextCandidateKm = candidates.firstOrNull { it.km > next.km + 0.5 }?.km ?: km
            val minimumDeparture = input.reserveSocPercent +
                (nextCandidateKm - next.km) * pctPerKm
            if (minimumDeparture > 100.0) {
                return AutomaticChargeResult.Unavailable(
                    "Sonraki durak %100 SoC ile de erişilebilir değil"
                )
            }
            val chargeTarget = maxOf(
                input.preferredChargingCeilingPercent,
                minimumDeparture
            ).coerceAtMost(100.0)
            val charged = ((chargeTarget - arriveSoc).coerceAtLeast(0.0)) *
                input.usableBatteryKwh / 100.0
            stops += PredictedChargeStop(
                stationId = next.station.id,
                stationName = next.station.name,
                routeKm = next.km,
                chargerPowerKw = next.kw,
                arriveSocPercent = arriveSoc,
                chargeToSocPercent = chargeTarget,
                chargeKwh = charged
            )
            positionKm = next.km
            currentSoc = chargeTarget
        }
        return AutomaticChargeResult.Unavailable("12 durakta güvenilir plan tamamlanamadı")
    }

    private fun routeGeometryKm(points: List<GeoCoordinate>): Double =
        points.zipWithNext().sumOf { (a, b) -> haversineKm(a, b) }

    private fun projectRouteFraction(
        points: List<GeoCoordinate>, target: GeoCoordinate
    ): Double? {
        if (!target.valid()) return null
        val totalKm = routeGeometryKm(points)
        if (totalKm <= 0.0) return null
        var along = 0.0
        var selectedKm = 0.0
        var minOffset = Double.POSITIVE_INFINITY
        val cosLat = cos(Math.toRadians(target.latitude))
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            val x = (a.longitude - target.longitude) * 111_320.0 * cosLat
            val y = (a.latitude - target.latitude) * 111_195.0
            val dx = (b.longitude - a.longitude) * 111_320.0 * cosLat
            val dy = (b.latitude - a.latitude) * 111_195.0
            val length2 = dx * dx + dy * dy
            val f = if (length2 > 0.01)
                (-(x * dx + y * dy) / length2).coerceIn(0.0, 1.0) else 0.0
            val off = hypot(x + f * dx, y + f * dy)
            val segmentKm = haversineKm(a, b)
            if (off < minOffset) {
                minOffset = off
                selectedKm = along + segmentKm * f
            }
            along += segmentKm
        }
        return (selectedKm / totalKm).coerceIn(0.0, 1.0)
    }

    private fun haversineKm(a: GeoCoordinate, b: GeoCoordinate): Double {
        val p1 = Math.toRadians(a.latitude)
        val p2 = Math.toRadians(b.latitude)
        val dp = p2 - p1
        val dl = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dp/2).pow(2) + cos(p1) * cos(p2) * sin(dl/2).pow(2)
        return 12_742.0176 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}

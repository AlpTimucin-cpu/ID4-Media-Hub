package tr.id4.mediahub.nativecockpit.domain.usecase

import kotlin.math.*
import tr.id4.mediahub.nativecockpit.domain.model.*

data class SuggestedChargeStop(
    val stationId: String,
    val stationName: String,
    val coordinate: GeoCoordinate,
    val alongRouteKm: Double,
    val verifiedNominalKw: Double,
    val estimatedAddedKwh: Double
)

sealed interface AutoChargePlan {
    data class Unavailable(val reason: String) : AutoChargePlan
    data class Ready(
        val stops: List<SuggestedChargeStop>,
        val totalAddedKwh: Double,
        val note: String = "MODEL/TAHMİN · nominal güç; sapma, kuyruk ve fiili güç bilinmiyor"
    ) : AutoChargePlan
}

/**
 * Transparent route-only heuristic. Never invent charger power units, queue,
 * driving detours, connector compatibility or real arrival SoC.
 */
object AutoChargePlanner {
    private val KW = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*kW\s*$""", RegexOption.IGNORE_CASE)

    fun plan(
        route: RouteSnapshot?,
        estimate: BaselineEnergyResult?,
        stations: List<ChargingStation>,
        usableCapacityKwh: Double,
        reservePercent: Double
    ): AutoChargePlan {
        val energy = estimate as? BaselineEnergyResult.Estimate
            ?: return AutoChargePlan.Unavailable("Geçerli enerji modeli yok")
        if (route?.routeId == null || !validRouteGeometry(route.routePoints) ||
            !usableCapacityKwh.isFinite() || usableCapacityKwh <= 0.0 ||
            reservePercent !in 0.0..95.0 || !reservePercent.isFinite()
        ) return AutoChargePlan.Unavailable("Doğrulanmış rota/kapasite/rezerv verisi yok")
        if (!energy.chargingStopNeeded || energy.requiredChargingEnergyKwh <= 0)
            return AutoChargePlan.Ready(emptyList(), 0.0)

        val whPerKm = energy.effectiveWhPerKm
        if (!whPerKm.isFinite() || whPerKm <= 0.0)
            return AutoChargePlan.Unavailable("Tüketim kalibrasyonu yok")

        val initialSoc = energy.arrivalSocPercent +
            energy.driveEnergyKwh / usableCapacityKwh * 100.0
        if (!initialSoc.isFinite() || initialSoc !in 0.0..100.0)
            return AutoChargePlan.Unavailable("Başlangıç SoC güvenilir değil")

        data class Candidate(val station: ChargingStation, val alongKm: Double, val kw: Double)
        val candidates = stations.mapNotNull { station ->
            val kw = station.sockets.asSequence().filter { it.isDc }
                .mapNotNull { socket ->
                    val value = KW.matchEntire(socket.powerRaw)?.groupValues?.get(1)
                        ?.replace(",", ".")?.toDoubleOrNull()
                    value?.takeIf { it.isFinite() && it in 50.0..500.0 }
                }.maxOrNull() ?: return@mapNotNull null
            val along = project(route.routePoints, station.coordinate)
                ?: return@mapNotNull null
            if (along.second > 5_000.0 || along.first / 1000.0 >= energy.routeKm)
                return@mapNotNull null
            Candidate(station, along.first / 1000.0, kw)
        }.sortedBy { it.alongKm }

        if (candidates.isEmpty())
            return AutoChargePlan.Unavailable("Birimli (kW) doğrulanmış rota DC gücü yok")

        val stops = mutableListOf<SuggestedChargeStop>()
        var positionKm = 0.0
        var soc = initialSoc
        val kwhPerKm = whPerKm / 1_000.0

        repeat(12) {
            val remaining = energy.routeKm - positionKm
            if ((soc - reservePercent) * usableCapacityKwh / 100.0 >=
                remaining * kwhPerKm
            ) return AutoChargePlan.Ready(stops, stops.sumOf { it.estimatedAddedKwh })

            val reach = ((soc - reservePercent) * usableCapacityKwh / 100.0 /
                kwhPerKm).coerceAtLeast(0.0)
            val next = candidates.filter {
                it.alongKm > positionKm + 0.5 &&
                it.alongKm <= positionKm + reach - 0.5 &&
                stops.none { stop -> stop.stationId == it.station.id }
            }.maxWithOrNull(compareBy<Candidate> { it.alongKm }.thenBy { it.kw })
                ?: return AutoChargePlan.Unavailable(
                    "Rezervi koruyarak ulaşılabilir doğrulanmış DC durağı bulunamadı"
                )

            soc -= 100.0 * (next.alongKm - positionKm) * kwhPerKm / usableCapacityKwh
            val toDestination = energy.routeKm - next.alongKm
            val necessary = reservePercent +
                toDestination * kwhPerKm / usableCapacityKwh * 100.0 + 5.0
            val target = necessary.coerceAtLeast(80.0).coerceAtMost(90.0)
            val added = (target - soc).coerceAtLeast(0.0) * usableCapacityKwh / 100.0
            if (added < 0.01 || soc < 0.0) return AutoChargePlan.Unavailable(
                "Şarj hesaplaması tutarsız"
            )
            stops += SuggestedChargeStop(
                stationId = next.station.id,
                stationName = next.station.name,
                coordinate = next.station.coordinate,
                alongRouteKm = next.alongKm,
                verifiedNominalKw = next.kw,
                estimatedAddedKwh = added
            )
            positionKm = next.alongKm
            soc = target
        }
        return AutoChargePlan.Unavailable("12 durakta bile güvenilir şarj planı oluşmadı")
    }

    // Returns path distance from start and orthogonal deviation; no driving detour.
    private fun project(route: List<GeoCoordinate>, p: GeoCoordinate): Pair<Double, Double>? {
        if (!p.valid()) return null
        var bestOffset = Double.POSITIVE_INFINITY
        var bestAlong = 0.0
        var cumulative = 0.0
        val latScale = 111_195.0
        val lonScale = 111_320.0 * cos(Math.toRadians(p.latitude))
        for (i in 0 until route.lastIndex) {
            val a = route[i]; val b = route[i+1]
            val ax = (a.longitude - p.longitude) * lonScale
            val ay = (a.latitude - p.latitude) * latScale
            val dx = (b.longitude - a.longitude) * lonScale
            val dy = (b.latitude - a.latitude) * latScale
            val len = hypot(dx, dy)
            if (len < 0.1) continue
            val t = (-(ax*dx + ay*dy) / (len*len)).coerceIn(0.0, 1.0)
            val offset = hypot(ax + t*dx, ay + t*dy)
            if (offset < bestOffset) {
                bestOffset = offset
                bestAlong = cumulative + t*len
            }
            cumulative += len
        }
        return bestAlong to bestOffset
    }
}

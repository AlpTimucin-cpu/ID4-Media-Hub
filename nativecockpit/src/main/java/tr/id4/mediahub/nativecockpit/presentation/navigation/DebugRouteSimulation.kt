package tr.id4.mediahub.nativecockpit.presentation.navigation

import kotlin.math.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import tr.id4.mediahub.nativecockpit.domain.model.*

/**
 * DEBUG ONLY, app-local simulation. Does not inject Android mock locations and
 * never writes to OBD or the genuine LocationRepository. All generated values
 * must be labelled SIMULATION in the UI and excluded from production telemetry.
 */
internal data class SimulationFrame(
    val fix: GpsFix?,
    val telemetry: VehicleTelemetry
)

internal object DebugRouteSimulation {
    private const val SPEED_M_S = 12.5 // 45 km/h, deterministic demo speed
    private const val BATTERY_KWH = 60.0 // TEST FIXTURE, not a real car specification
    private const val WH_PER_KM = 180.0 // TEST FIXTURE, not a measured car value

    fun frames(route: RouteSnapshot?): Flow<SimulationFrame> = flow {
        val path = route?.routePoints?.takeIf(::validRouteGeometry)
        val distances = path?.let(::cumulativeMeters)
        val totalMeters = distances?.lastOrNull() ?: 0.0
        // Begin near a verified OSRM maneuver when possible, so TTS can be
        // exercised without replaying hundreds of kilometres in real time.
        val firstTurn = route?.maneuvers?.firstOrNull()
        val nearTurn = if (path != null && distances != null && firstTurn != null)
            nearestDistance(path, distances, firstTurn.location) else 0.0
        val startMeters = (nearTurn - 250.0).coerceAtLeast(0.0)
        var step = 0L
        while (true) {
            val now = System.currentTimeMillis()
            val at = if (totalMeters > 0.0) {
                (startMeters + step * SPEED_M_S).coerceAtMost(totalMeters)
            } else 0.0
            val location = if (path != null && distances != null)
                interpolated(path, distances, at) else null
            val soc = (80.0 - (at / 1_000.0 * WH_PER_KM / 1_000.0) /
                BATTERY_KWH * 100.0).coerceIn(0.0, 100.0)
            fun timed(value: Double) = TimedValue(value, now)
            emit(SimulationFrame(
                fix = location?.let { GpsFix(
                    it, accuracyMeters = 4f, recordedAtMillis = now,
                    speedMetersPerSecond = SPEED_M_S.toFloat()
                ) },
                telemetry = VehicleTelemetry(
                    socPercent = timed(soc),
                    speedKmh = timed(SPEED_M_S * 3.6),
                    batteryTemperatureC = timed(22.0),
                    powerKw = timed(8.1)
                )
            ))
            if (totalMeters > 0.0 && at >= totalMeters) step = 0L
            else step++
            delay(1_000L)
        }
    }

    private fun cumulativeMeters(path: List<GeoCoordinate>): DoubleArray {
        val result = DoubleArray(path.size)
        for (i in 1 until path.size)
            result[i] = result[i-1] + haversine(path[i-1], path[i])
        return result
    }

    private fun interpolated(
        path: List<GeoCoordinate>, distances: DoubleArray, meters: Double
    ): GeoCoordinate {
        val last = path.lastIndex
        val index = (0 until last).firstOrNull { distances[it + 1] >= meters }
            ?: return path.last()
        val segment = distances[index + 1] - distances[index]
        val f = if (segment > 0) ((meters - distances[index]) / segment)
            .coerceIn(0.0, 1.0) else 0.0
        return GeoCoordinate(
            path[index].latitude + (path[index + 1].latitude - path[index].latitude) * f,
            path[index].longitude + (path[index + 1].longitude - path[index].longitude) * f
        )
    }

    private fun nearestDistance(
        path: List<GeoCoordinate>, cumulative: DoubleArray, point: GeoCoordinate
    ): Double {
        var best = Double.POSITIVE_INFINITY
        var along = 0.0
        for (i in 0 until path.lastIndex) {
            val a = path[i]
            val b = path[i + 1]
            val cosLat = cos(Math.toRadians(point.latitude))
            val x = (a.longitude - point.longitude) * 111_320.0 * cosLat
            val y = (a.latitude - point.latitude) * 111_195.0
            val dx = (b.longitude - a.longitude) * 111_320.0 * cosLat
            val dy = (b.latitude - a.latitude) * 111_195.0
            val len2 = dx*dx + dy*dy
            if (len2 <= 0.01) continue
            val fraction = (-(x*dx + y*dy) / len2).coerceIn(0.0, 1.0)
            val offset = hypot(x+fraction*dx, y+fraction*dy)
            if (offset < best) {
                best = offset
                along = cumulative[i] + fraction * (cumulative[i+1]-cumulative[i])
            }
        }
        return along
    }

    private fun haversine(a: GeoCoordinate, b: GeoCoordinate): Double {
        val p = Math.toRadians(a.latitude)
        val q = Math.toRadians(b.latitude)
        val dp = q - p
        val dl = Math.toRadians(b.longitude - a.longitude)
        val t = sin(dp/2).pow(2) + cos(p)*cos(q)*sin(dl/2).pow(2)
        return 2*6_371_000.0*asin(sqrt(t.coerceIn(0.0,1.0)))
    }
}

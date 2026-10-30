package tr.id4.mediahub.nativecockpit.presentation.navigation

import kotlin.math.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import tr.id4.mediahub.nativecockpit.domain.model.*

/**
 * DEBUG ONLY. Follows existing OSRM geometry; never injects Android mock GPS,
 * mutates LocationRepository or writes real OBD values.
 */
internal data class SimulationFrame(
    val fix: GpsFix?,
    val telemetry: VehicleTelemetry
)

internal object DebugRouteSimulation {
    private const val SPEED_M_S = 12.5 // 45 km/h, test fixture
    private const val BATTERY_KWH = 60.0 // Test fixture, NOT an ID.4 specification
    private const val WH_PER_KM = 180.0 // Test fixture, NOT measured consumption
    private const val EARTH_RADIUS_M = 6_371_000.0

    private data class Geometry(val path: List<GeoCoordinate>, val cumulative: DoubleArray) {
        val length: Double get() = cumulative.last()
    }

    /**
     * Every emission has a fresh timestamp and moves along the ORIGINAL route.
     * Route changes are handled by collectLatest in NavigationViewModel.
     * tickMillis is configurable only for fast, deterministic test collection.
     */
    fun frames(route: RouteSnapshot?, tickMillis: Long = 1_000L): Flow<SimulationFrame> = flow {
        require(tickMillis > 0L)
        val geometry = geometry(route)
        val start = if (geometry != null && route != null) startMeters(route, geometry) else 0.0
        var tick = 0L
        while (true) {
            val along = if (geometry != null) {
                (start + tick.toDouble() * SPEED_M_S).coerceAtMost(geometry.length)
            } else 0.0
            emit(makeFrame(geometry, along, System.currentTimeMillis()))
            if (geometry != null && along >= geometry.length) tick = 0L
            else tick++
            delay(tickMillis)
        }
    }

    /** Deterministic injection point for JVM UAT: no Android mock provider. */
    fun frameAt(route: RouteSnapshot?, metersAlong: Double, nowMs: Long): SimulationFrame =
        makeFrame(geometry(route), metersAlong, nowMs)

    private fun geometry(route: RouteSnapshot?): Geometry? {
        val actual = route ?: return null
        if (actual.routeId.isNullOrBlank() ||
            !validRouteGeometry(actual.routePoints)) return null
        val path = actual.routePoints
        val cumulative = DoubleArray(path.size)
        for (i in 1..path.lastIndex) {
            cumulative[i] = cumulative[i - 1] + haversine(path[i - 1], path[i])
        }
        return cumulative.lastOrNull()?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { Geometry(path, cumulative) }
    }

    /** Choose a real upcoming OSRM turn with room for both 200 m and 50 m cues. */
    private fun startMeters(route: RouteSnapshot, geometry: Geometry): Double {
        val projected = route.maneuvers.asSequence()
            .filter { it.location.valid() && it.instructionTr.isNotBlank() }
            .map { nearestDistance(geometry, it.location) }
            .filter { it.isFinite() && it in 0.0..geometry.length }
            .toList().sorted()
        val target = projected.firstOrNull { it >= 230.0 }
            ?: projected.firstOrNull() ?: 0.0
        return (target - 250.0).coerceAtLeast(0.0)
    }

    private fun makeFrame(
        geometry: Geometry?, metersAlong: Double, nowMs: Long
    ): SimulationFrame {
        val safeMeters = metersAlong.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val along = geometry?.let { safeMeters.coerceAtMost(it.length) } ?: 0.0
        val point = geometry?.let { interpolated(it, along) }
        val soc = (80.0 - (along / 1_000.0 * WH_PER_KM / 1_000.0) /
            BATTERY_KWH * 100.0).coerceIn(0.0, 100.0)
        fun timed(value: Double) = TimedValue(value, nowMs)
        return SimulationFrame(
            fix = point?.let {
                GpsFix(
                    coordinate = it,
                    accuracyMeters = 4f,
                    recordedAtMillis = nowMs,
                    speedMetersPerSecond = SPEED_M_S.toFloat()
                )
            },
            telemetry = VehicleTelemetry(
                socPercent = timed(soc),
                speedKmh = timed(SPEED_M_S * 3.6),
                batteryTemperatureC = timed(22.0),
                powerKw = timed(8.1)
            )
        )
    }

    private fun interpolated(geometry: Geometry, meters: Double): GeoCoordinate {
        val path = geometry.path
        val d = geometry.cumulative
        val index = (0 until path.lastIndex).firstOrNull { d[it + 1] >= meters }
            ?: return path.last()
        val segment = d[index + 1] - d[index]
        val fraction = if (segment > 0.0)
            ((meters - d[index]) / segment).coerceIn(0.0, 1.0) else 0.0
        return GeoCoordinate(
            path[index].latitude + (path[index + 1].latitude - path[index].latitude) * fraction,
            path[index].longitude + (path[index + 1].longitude - path[index].longitude) * fraction
        )
    }

    private fun nearestDistance(geometry: Geometry, point: GeoCoordinate): Double {
        val path = geometry.path
        val d = geometry.cumulative
        var bestOffset = Double.POSITIVE_INFINITY
        var along = 0.0
        val cosLat = cos(Math.toRadians(point.latitude))
        for (i in 0 until path.lastIndex) {
            val a = path[i]; val b = path[i + 1]
            val ax = (a.longitude - point.longitude) * 111_320.0 * cosLat
            val ay = (a.latitude - point.latitude) * 111_195.0
            val dx = (b.longitude - a.longitude) * 111_320.0 * cosLat
            val dy = (b.latitude - a.latitude) * 111_195.0
            val len2 = dx * dx + dy * dy
            if (len2 <= .01) continue
            val t = (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val offset = hypot(ax + t * dx, ay + t * dy)
            if (offset < bestOffset) {
                bestOffset = offset
                along = d[i] + t * (d[i + 1] - d[i])
            }
        }
        return if (bestOffset.isFinite()) along else 0.0
    }

    private fun haversine(a: GeoCoordinate, b: GeoCoordinate): Double {
        val p = Math.toRadians(a.latitude)
        val q = Math.toRadians(b.latitude)
        val dp = q - p
        val dl = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dp / 2).pow(2) + cos(p) * cos(q) * sin(dl / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}

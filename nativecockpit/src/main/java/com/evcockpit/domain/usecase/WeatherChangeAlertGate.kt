package com.evcockpit.domain.usecase

import com.evcockpit.domain.model.RouteWeatherState
import com.evcockpit.domain.model.WeatherWaypoint

/**
 * Route-scoped warning deduplication. Speak only on an increase to a
 * noteworthy forecast severity; never repeat an unchanged forecast.
 */
internal class WeatherChangeAlertGate {
    private var routeId: String? = null
    private var previousSeverity = 0

    fun increasedWarning(state: RouteWeatherState, nowMs: Long): WeatherWaypoint? {
        if (state.routeId.isNullOrBlank() || state.points.isEmpty() ||
            state.fetchedAtMillis == null ||
            nowMs < state.fetchedAtMillis || nowMs - state.fetchedAtMillis > 45 * 60_000L
        ) return null
        if (routeId != state.routeId) {
            routeId = state.routeId
            previousSeverity = 0
        }
        val strongest = state.points.maxByOrNull(::severity) ?: return null
        val current = severity(strongest)
        val increased = current > previousSeverity && current >= 2
        previousSeverity = current
        return strongest.takeIf { increased }
    }

    internal fun severity(point: WeatherWaypoint): Int {
        val weather = when (point.weatherCode) {
            95, 96, 99 -> 4
            65, 67, 75, 82, 86 -> 3
            45, 48, 61, 63, 66, 71, 73, 77, 80, 81, 85 -> 2
            51, 53, 55, 56, 57 -> 1
            else -> 0
        }
        val wind = when {
            point.windSpeedKmh >= 75.0 -> 4
            point.windSpeedKmh >= 50.0 -> 3
            point.windSpeedKmh >= 35.0 -> 2
            else -> 0
        }
        return maxOf(weather, wind)
    }
}

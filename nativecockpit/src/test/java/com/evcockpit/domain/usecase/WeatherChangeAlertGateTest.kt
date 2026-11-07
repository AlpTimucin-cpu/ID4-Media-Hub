package com.evcockpit.domain.usecase

import com.evcockpit.domain.model.GeoCoordinate
import com.evcockpit.domain.model.RouteWeatherState
import com.evcockpit.domain.model.WeatherWaypoint
import org.junit.Assert.*
import org.junit.Test

class WeatherChangeAlertGateTest {
    private fun forecast(code: Int, routeId: String = "route-1", age: Long = 0L) =
        RouteWeatherState(
            routeId = routeId,
            fetchedAtMillis = 200_000L - age,
            points = listOf(WeatherWaypoint(
                coordinate = GeoCoordinate(38.0, 34.0),
                distanceFromStartKm = 40.0,
                temperatureC = 10.0,
                windSpeedKmh = 10.0,
                weatherCode = code,
                observedAt = "forecast UTC",
                forecastEtaEpochMs = 200_000L
            ))
        )

    @Test fun warnsOnSeverityIncreaseButNotOnRepeatedHourlyRefresh() {
        val gate = WeatherChangeAlertGate()
        assertNull(gate.increasedWarning(forecast(0), 200_000L))
        assertNotNull(gate.increasedWarning(forecast(63), 200_000L))
        assertNull(gate.increasedWarning(forecast(63), 200_000L))
        assertNull(gate.increasedWarning(forecast(0), 200_000L))
        assertNotNull(gate.increasedWarning(forecast(95), 200_000L))
    }

    @Test fun routeSwitchResetsGateButStaleModelsAreSilent() {
        val gate = WeatherChangeAlertGate()
        assertNotNull(gate.increasedWarning(forecast(95), 200_000L))
        assertNull(gate.increasedWarning(forecast(95), 200_000L))
        assertNotNull(gate.increasedWarning(forecast(95, "route-2"), 200_000L))
        assertNull(gate.increasedWarning(
            forecast(95, "route-3", age = 46 * 60_000L), 200_000L
        ))
    }
}

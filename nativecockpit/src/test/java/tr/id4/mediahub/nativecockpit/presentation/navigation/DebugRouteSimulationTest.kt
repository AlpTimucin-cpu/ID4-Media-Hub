package tr.id4.mediahub.nativecockpit.presentation.navigation

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import tr.id4.mediahub.nativecockpit.domain.model.*

class DebugRouteSimulationTest {
    @Test fun neverForgesGpsWhenNoVerifiedRouteExists() = runBlocking {
        val sample = DebugRouteSimulation.frames(null).first()
        assertNull(sample.fix)
        assertEquals(80.0, sample.telemetry.socPercent.value!!, 0.0001)
        assertEquals(45.0, sample.telemetry.speedKmh.value!!, 0.0001)
        assertEquals(22.0, sample.telemetry.batteryTemperatureC.value!!, 0.0001)
    }

    @Test fun generatedFixMovesAlongActualRouteAndIsFresh() = runBlocking {
        val route = RouteSnapshot(
            routeId = "fixture-route",
            totalKm = 0.6,
            routePoints = listOf(
                GeoCoordinate(37.0, 34.0),
                GeoCoordinate(37.003, 34.0),
                GeoCoordinate(37.006, 34.0)
            )
        )
        val points = DebugRouteSimulation.frames(route).take(2).toList()
        val first = points[0].fix!!
        val second = points[1].fix!!
        assertTrue(first.usable(System.currentTimeMillis()))
        assertTrue(second.coordinate.latitude > first.coordinate.latitude)
        assertEquals(34.0, second.coordinate.longitude, 0.000001)
        assertEquals(12.5f, first.speedMetersPerSecond!!, 0.001f)
        assertNotNull(points[1].telemetry.socPercent.measuredAtMillis)
    }
}

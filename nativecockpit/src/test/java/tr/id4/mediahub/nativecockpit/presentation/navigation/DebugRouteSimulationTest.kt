package tr.id4.mediahub.nativecockpit.presentation.navigation

import kotlinx.coroutines.flow.first
import tr.id4.mediahub.nativecockpit.domain.usecase.ManeuverGuidancePolicy
import tr.id4.mediahub.nativecockpit.domain.usecase.RouteProgressTracker
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import tr.id4.mediahub.nativecockpit.domain.model.*

class DebugRouteSimulationTest {
    @Test fun routeIdChangesWithTheSelectedRouteNotWithTheLastGpsFix() = runBlocking {
        val firstRoute = RouteSnapshot(
            routeId = "route-one",
            routePoints = listOf(GeoCoordinate(37.0, 34.0), GeoCoordinate(37.01, 34.0))
        )
        val secondRoute = firstRoute.copy(routeId = "route-two")
        val first = DebugRouteSimulation.frames(firstRoute).first()
        val second = DebugRouteSimulation.frames(secondRoute).first()
        assertEquals("route-one", first.routeId)
        assertEquals("route-two", second.routeId)
        assertNotNull(first.fix)
        assertNotNull(second.fix)
    }

    @Test fun neverForgesGpsWhenNoVerifiedRouteExists() = runBlocking {
        val sample = DebugRouteSimulation.frames(null).first()
        assertNull(sample.fix)
        assertNull(sample.routeId)
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
        assertEquals("fixture-route", points[0].routeId)
        assertEquals("fixture-route", points[1].routeId)
        val first = points[0].fix!!
        val second = points[1].fix!!
        assertTrue(first.usable(System.currentTimeMillis()))
        assertTrue(second.coordinate.latitude > first.coordinate.latitude)
        assertEquals(34.0, second.coordinate.longitude, 0.000001)
        assertEquals(12.5f, first.speedMetersPerSecond!!, 0.001f)
        assertNotNull(points[1].telemetry.socPercent.measuredAtMillis)
    }

    @Test fun simulatedFlowPassesTwoRealManeuverThresholds() = runBlocking {
        // This fixture mimics OSRM geometry and a real maneuvers[] step.
        val geometry = listOf(GeoCoordinate(37.0, 34.0), GeoCoordinate(37.01, 34.0))
        val route = RouteSnapshot(
            routeId = "osrm-simulation-test",
            routePoints = geometry,
            maneuvers = listOf(NavigationManeuver(
                "step-7", "sağa dönün", GeoCoordinate(37.005, 34.0)
            )),
            totalKm = 1.11,
            osrmDurationSeconds = 90.0
        )
        val frames = DebugRouteSimulation.frames(route, tickMillis = 1L)
            .take(21).toList()
        val progress = RouteProgressTracker()
        val distances = frames.mapNotNull { frame ->
            val now = frame.fix?.recordedAtMillis ?: return@mapNotNull null
            progress.enrich(route, frame.fix, now)?.nextTurnMeters
        }
        assertEquals(21, distances.size)
        assertTrue("TEST GPS must move forward along OSRM", distances.last() < distances.first())
        assertTrue("Simulator must cross 200 m TTS gate", distances.any { it in 100.0..200.0 })
        assertTrue("Simulator must cross 50 m TTS gate", distances.any { it in 0.0..50.0 })
    }

    @Test fun simulationFixesTriggerTtsExactlyOncePerStage() {
        val time = 1_800_000_000_000L
        val route = RouteSnapshot(
            routeId = "osrm-step-dedup",
            routePoints = listOf(
                GeoCoordinate(37.0, 34.0), GeoCoordinate(37.01, 34.0)
            ),
            maneuvers = listOf(NavigationManeuver(
                "step-7", "sağa dönün", GeoCoordinate(37.005, 34.0)
            )),
            totalKm = 1.11,
            osrmDurationSeconds = 90.0
        )
        val tracker = RouteProgressTracker()
        val policy = ManeuverGuidancePolicy()
        fun speech(at: Double, now: Long): String? {
            val fix = DebugRouteSimulation.frameAt(route, at, now).fix!!
            val enriched = tracker.enrich(route, fix, now)
            return policy.nextAnnouncement(enriched, fix, now)
        }

        assertNull(speech(300.0, time)) // 250 m remaining
        assertEquals("İki yüz metre sonra, sağa dönün", speech(385.0, time + 1_000L))
        policy.confirmSpoken(time + 1_000L) // TextToSpeech.onStart
        assertNull(speech(400.0, time + 2_000L))
        assertEquals("Elli metre sonra, sağa dönün", speech(520.0, time + 6_000L))
        policy.confirmSpoken(time + 6_000L)
        assertNull(speech(530.0, time + 7_000L))
    }
}

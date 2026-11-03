package com.evcockpit.presentation.navigation

import com.evcockpit.domain.model.GeoCoordinate
import com.evcockpit.domain.model.GpsFix
import com.evcockpit.domain.model.NavigationManeuver
import com.evcockpit.domain.model.RouteSnapshot
import com.evcockpit.domain.model.TimedValue
import com.evcockpit.domain.model.VehicleTelemetry
import com.evcockpit.domain.usecase.NavigationSpeechBridge
import org.junit.Assert.*
import org.junit.Test

class NavigationSourceSelectorTest {
    private val startTime = 1_800_000_000_000L
    private val route = RouteSnapshot(
        routeId = "simulation-route",
        routePoints = listOf(
            GeoCoordinate(37.0, 34.0),
            GeoCoordinate(37.01, 34.0)
        ),
        maneuvers = listOf(NavigationManeuver(
            "right-turn", "turn right", GeoCoordinate(37.005, 34.0)
        )),
        totalKm = 1.11,
        osrmDurationSeconds = 90.0
    )

    @Test fun debugFrameFeedsBothEffectiveGpsAndTelemetryWithoutChangingLiveValues() {
        val measuredGps = GpsFix(GeoCoordinate(38.0, 35.0), 4f, startTime)
        val realObd = VehicleTelemetry(socPercent = TimedValue(60.0, startTime))
        val synthetic = DebugRouteSimulation.frameAt(route, 385.0, startTime)
        val effective = NavigationSourceSelector.select(
            realObd, measuredGps, true, true, synthetic, route.routeId
        )

        assertTrue(effective.simulated)
        assertEquals(synthetic.fix, effective.gps)
        assertEquals(synthetic.telemetry, effective.telemetry)
        assertEquals(60.0, realObd.socPercent.value!!, 0.0001)
        assertEquals(measuredGps.coordinate, measuredGps.coordinate)
    }

    @Test fun switchToDifferentRouteInvalidatesOldFrameAndNeverLeaksLiveGps() {
        val measured = GpsFix(GeoCoordinate(38.0, 35.0), 3f, startTime)
        val live = VehicleTelemetry(socPercent = TimedValue(58.0, startTime))
        val frame = DebugRouteSimulation.frameAt(route, 385.0, startTime)
        val stale = NavigationSourceSelector.select(
            live, measured, true, true, frame, "changed-route"
        )
        assertTrue(stale.simulated)
        assertNull(stale.gps)
        assertNull(stale.telemetry.socPercent.value)

        val disconnected = NavigationSourceSelector.select(
            live, measured, true, false, frame, route.routeId
        )
        assertFalse(disconnected.simulated)
        assertEquals(measured, disconnected.gps)
        assertSame(live, disconnected.telemetry)

        val manualDisabled = NavigationSourceSelector.select(
            live, measured, false, true, frame, route.routeId
        )
        assertFalse(manualDisabled.simulated)
        assertEquals(measured, manualDisabled.gps)
    }

    @Test fun simulationFramesDriveTwoHundredAndFiftyMeterSpeechUsingSameSource() {
        val bridge = NavigationSpeechBridge()
        val spoken = mutableListOf<String>()
        val positions = listOf(300.0, 385.0, 400.0, 520.0, 530.0)
        val seconds = listOf(0, 1, 2, 6, 7)

        positions.forEachIndexed { index, along ->
            val now = startTime + seconds[index] * 1000L
            val frame = DebugRouteSimulation.frameAt(route, along, now)
            val selected = NavigationSourceSelector.select(
                VehicleTelemetry(), null, true, true, frame, route.routeId
            )
            val eval = bridge.evaluate(route, selected.gps, now)
            eval.cue?.let {
                spoken.add(it.message)
                bridge.confirmStarted(it.token, now)
            }
            assertNotNull(eval.route?.gpsProgressFraction)
        }

        assertEquals(
            listOf(
                "In two hundred meters, turn right",
                "In fifty meters, turn right"
            ),
            spoken
        )

        val staleFrame = DebugRouteSimulation.frameAt(route, 520.0, startTime + 8000L)
        val noNewFix = NavigationSourceSelector.select(
            VehicleTelemetry(), null, true, true, staleFrame, "another-route"
        )
        assertNull(bridge.evaluate(route.copy(routeId = "another-route"), noNewFix.gps,
            startTime + 8000L).cue)
    }
}

package com.evcockpit.domain.usecase

import com.evcockpit.domain.model.*
import com.evcockpit.presentation.navigation.DebugRouteSimulation
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NavigationSpeechBridgeTest {
    private val time = 1_800_000_000_000L
    private val route = RouteSnapshot(
        routeId = "verified-test-route",
        routePoints = listOf(GeoCoordinate(37.0, 34.0), GeoCoordinate(37.01, 34.0)),
        maneuvers = listOf(NavigationManeuver(
            "step-7", "turn right", GeoCoordinate(37.005, 34.0)
        )),
        totalKm = 1.11,
        osrmDurationSeconds = 90.0
    )

    @Test fun realSimulatorFlowFeedsTheSameBridgeAsTheViewModel() = runBlocking {
        val bridge = NavigationSpeechBridge()
        val frames = DebugRouteSimulation.frames(route, tickMillis = 1L).take(22).toList()
        val messages = mutableListOf<String>()
        frames.forEachIndexed { i, frame ->
            // Accelerate collection, retaining the normal one-second GPS cadence.
            val now = time + i * 1_000L
            val fix = frame.fix!!.copy(recordedAtMillis = now)
            val result = bridge.evaluate(route, fix, now)
            assertNotNull(result.route?.gpsProgressFraction)
            result.cue?.let {
                messages += it.message
                bridge.confirmStarted(it.token, now)
            }
        }
        assertEquals(listOf(
            "In two hundred meters, turn right",
            "In fifty meters, turn right"
        ), messages)
    }

    @Test fun lateCallbacksFromOldRouteCannotConsumeOrCancelNewCue() {
        val bridge = NavigationSpeechBridge()
        fun evaluate(r: RouteSnapshot, at: Long) = bridge.evaluate(
            r, DebugRouteSimulation.frameAt(r, 385.0, at).fix, at
        ).cue!!
        val old = evaluate(route, time)
        val replacement = route.copy(routeId = "replacement-route")
        val current = evaluate(replacement, time + 1_000L)
        assertNotEquals(old.token, current.token)
        bridge.confirmStarted(old.token, time + 1_100L)
        bridge.cancel(old.token)
        bridge.cancel(current.token)
        // A failed current cue can retry; an old onStart must not consume it.
        assertEquals(current.message, evaluate(replacement, time + 2_000L).message)
    }

    @Test fun resetDoesNotReuseCallbackTokens() {
        val bridge = NavigationSpeechBridge()
        val fix = DebugRouteSimulation.frameAt(route, 385.0, time).fix
        val first = bridge.evaluate(route, fix, time).cue!!
        bridge.reset()
        val nextFix = fix!!.copy(recordedAtMillis = time + 1_000L)
        val next = bridge.evaluate(route, nextFix, time + 1_000L).cue!!
        assertNotEquals(first.token, next.token)
        bridge.confirmStarted(first.token, time + 1_100L)
        bridge.cancel(next.token)
        assertNotNull(bridge.evaluate(
            route, nextFix.copy(recordedAtMillis = time + 2_000L), time + 2_000L
        ).cue)
    }

    @Test fun noRouteStaleAndOffRouteFixesNeverProduceSpeech() {
        val bridge = NavigationSpeechBridge()
        val fix = DebugRouteSimulation.frameAt(route, 385.0, time).fix!!
        assertNull(bridge.evaluate(null, fix, time).cue)
        assertNull(bridge.evaluate(route, fix, time + 20_000L).cue)
        assertNull(bridge.evaluate(route,
            fix.copy(coordinate = GeoCoordinate(38.0, 34.0)), time).cue)
    }
}

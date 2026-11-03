package com.evcockpit.presentation.navigation

import com.evcockpit.domain.model.GpsFix
import com.evcockpit.domain.model.VehicleTelemetry

/**
 * Resolves one coherent navigation input for the map, HUD and speech bridge.
 * Simulation is strictly DEBUG-only and scoped to the currently selected route.
 * It never mutates the real location repository or real OBD telemetry.
 */
internal data class EffectiveNavigationSources(
    val telemetry: VehicleTelemetry,
    val gps: GpsFix?,
    val simulated: Boolean
)

internal object NavigationSourceSelector {
    fun select(
        liveTelemetry: VehicleTelemetry,
        liveGps: GpsFix?,
        simulationEnabled: Boolean,
        debugBuild: Boolean,
        frame: SimulationFrame?,
        currentRouteId: String?
    ): EffectiveNavigationSources {
        if (!debugBuild || !simulationEnabled) {
            return EffectiveNavigationSources(liveTelemetry, liveGps, false)
        }

        val matching = frame?.takeIf {
            !currentRouteId.isNullOrBlank() && it.routeId == currentRouteId
        }
        return EffectiveNavigationSources(
            telemetry = matching?.telemetry ?: VehicleTelemetry(),
            gps = matching?.fix,
            simulated = true
        )
    }
}

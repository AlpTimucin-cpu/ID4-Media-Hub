package com.evcockpit.car

import com.evcockpit.domain.model.GpsFix
import com.evcockpit.domain.model.RouteSnapshot
import com.evcockpit.domain.model.validRouteGeometry
import java.util.Locale

/** Never invents a destination, remaining distance, or current position. */
data class CarRouteSummary(
    val routeAvailable: Boolean,
    val title: String,
    val detail: String,
    val locationStatus: String
) {
    companion object {
        fun from(route: RouteSnapshot?, gps: GpsFix?, nowMillis: Long): CarRouteSummary {
            val locationLabel = if (gps?.usable(nowMillis) == true)
                "GPS reading is current" else "No GPS reading / permission required"
            if (route == null || !validRouteGeometry(route.routePoints)) {
                return CarRouteSummary(false, "No route planned",
                    "Select an origin and destination in the phone app", locationLabel)
            }
            val destination = route.destination?.takeIf { it.isNotBlank() }
                ?.take(55) ?: "Destination"
            val distance = route.totalKm?.takeIf { it.isFinite() && it > 0 }
                ?.let { String.format(Locale.forLanguageTag("en-US"), "Total %.1f km · OSRM", it) }
                ?: "Route geometry available · length unknown"
            return CarRouteSummary(true, destination, distance, locationLabel)
        }
    }
}

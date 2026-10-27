package tr.id4.mediahub.nativecockpit.domain.usecase

import kotlin.math.*
import tr.id4.mediahub.nativecockpit.domain.model.ChargingStation
import tr.id4.mediahub.nativecockpit.domain.model.GeoCoordinate

/** Physical straight-line distance from point to real route line, NOT driving detour. */
object StationCorridor {
    const val MAX_DISTANCE_METERS = 2_000.0
    private const val EARTH_METERS = 6_371_008.8

    fun distanceMeters(station: GeoCoordinate, route: List<GeoCoordinate>): Double? {
        if (!station.valid() || route.size < 2 || route.any { !it.valid() }) return null
        var best = Double.POSITIVE_INFINITY
        for (i in 0 until route.lastIndex) {
            val a = route[i]
            val b = route[i + 1]
            // Fast bounding-box prefilter: ~0.03 degree covers >=2 km in Turkey.
            if (station.latitude < min(a.latitude,b.latitude)-.035 ||
                station.latitude > max(a.latitude,b.latitude)+.035 ||
                station.longitude < min(a.longitude,b.longitude)-.045 ||
                station.longitude > max(a.longitude,b.longitude)+.045) continue
            val latScale = EARTH_METERS * Math.PI / 180.0
            val lonScale = latScale * cos(Math.toRadians(station.latitude))
            val ax = (a.longitude - station.longitude) * lonScale
            val ay = (a.latitude - station.latitude) * latScale
            val bx = (b.longitude - station.longitude) * lonScale
            val by = (b.latitude - station.latitude) * latScale
            val dx=bx-ax; val dy=by-ay
            val len2=dx*dx+dy*dy
            val t = if (len2<1e-9) 0.0 else (-ax*dx-ay*dy).div(len2).coerceIn(0.0,1.0)
            best=min(best,hypot(ax+t*dx,ay+t*dy))
        }
        return best.takeIf { it.isFinite() }
    }

    fun isAlongRoute(station: ChargingStation, route: List<GeoCoordinate>): Boolean =
        station.hasDcCcs && (distanceMeters(station.coordinate,route) ?: Double.POSITIVE_INFINITY) <= MAX_DISTANCE_METERS
}

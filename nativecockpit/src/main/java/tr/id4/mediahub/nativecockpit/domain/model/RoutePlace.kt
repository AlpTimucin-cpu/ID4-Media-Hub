package tr.id4.mediahub.nativecockpit.domain.model

/** Only candidates returned by Photon; never a fabricated search suggestion. */
data class RoutePlace(
    val label: String,
    val coordinate: GeoCoordinate,
    val source: String = "Photon / OSM"
)

data class RouteOption(
    val snapshot: RouteSnapshot,
    val distanceKm: Double,
    val durationMinutesWithoutTraffic: Long
)

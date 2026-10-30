package tr.id4.mediahub.nativecockpit.domain.model

data class WeatherWaypoint(
    val coordinate: GeoCoordinate,
    val distanceFromStartKm: Double,
    val temperatureC: Double,
    val windSpeedKmh: Double,
    val weatherCode: Int,
    val observedAt: String
) {
    val iconKey: String get() = when (weatherCode) {
        0, 1 -> "sun"
        2, 3, 45, 48 -> "cloud"
        else -> "rain"
    }
    val summaryTr: String get() = when (weatherCode) {
        0, 1 -> "Açık"
        2, 3 -> "Bulutlu"
        45, 48 -> "Sisli"
        71, 73, 75, 77, 85, 86 -> "Karlı"
        95, 96, 99 -> "Fırtınalı"
        else -> "Yağışlı"
    }
}

data class RouteWeatherState(
    val routeId: String? = null,
    val routeLengthKm: Double? = null,
    val points: List<WeatherWaypoint> = emptyList(),
    val loading: Boolean = false,
    val fetchedAtMillis: Long? = null,
    val error: String? = null
) {
    val destination: WeatherWaypoint? get() = points.lastOrNull()?.takeIf {
        routeLengthKm != null && kotlin.math.abs(it.distanceFromStartKm - routeLengthKm) < 0.2
    }
}

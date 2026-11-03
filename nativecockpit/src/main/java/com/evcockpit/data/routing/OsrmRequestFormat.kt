package com.evcockpit.data.routing

import java.util.Locale
import com.evcockpit.domain.model.GeoCoordinate

/** OSRM uses longitude,latitude separated by a literal ';'. */
object OsrmRequestFormat {
    private const val ROOT = "https://router.project-osrm.org/route/v1/driving/"
    private const val PARAMETERS = "?alternatives=true&steps=true&geometries=geojson&overview=full&annotations=speed"

    fun coordinates(origin: GeoCoordinate, destination: GeoCoordinate): String {
        require(origin.valid() && destination.valid()) { "Geçersiz koordinat" }
        require(origin != destination) { "Başlangıç ve varış aynı olamaz" }
        fun point(value: GeoCoordinate): String = String.format(
            Locale.US, "%.6f,%.6f", value.longitude, value.latitude
        )
        return "${point(origin)};${point(destination)}"
    }

    fun url(origin: GeoCoordinate, destination: GeoCoordinate): String =
        ROOT + coordinates(origin, destination) + PARAMETERS
}

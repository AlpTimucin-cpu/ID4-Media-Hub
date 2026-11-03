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
                "GPS ölçümü güncel" else "GPS ölçümü yok / izin gerekli"
            if (route == null || !validRouteGeometry(route.routePoints)) {
                return CarRouteSummary(false, "Rota oluşturulmadı",
                    "Telefon uygulamasında başlangıç ve hedef seçin", locationLabel)
            }
            val destination = route.destination?.takeIf { it.isNotBlank() }
                ?.take(55) ?: "Hedef"
            val distance = route.totalKm?.takeIf { it.isFinite() && it > 0 }
                ?.let { String.format(Locale.forLanguageTag("tr-TR"), "Toplam %.1f km · OSRM", it) }
                ?: "Rota geometrisi mevcut · uzunluk bilinmiyor"
            return CarRouteSummary(true, destination, distance, locationLabel)
        }
    }
}

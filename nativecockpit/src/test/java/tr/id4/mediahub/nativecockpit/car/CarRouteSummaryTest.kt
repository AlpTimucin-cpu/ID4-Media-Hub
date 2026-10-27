package tr.id4.mediahub.nativecockpit.car

import org.junit.Assert.*
import org.junit.Test
import tr.id4.mediahub.nativecockpit.domain.model.GeoCoordinate
import tr.id4.mediahub.nativecockpit.domain.model.GpsFix
import tr.id4.mediahub.nativecockpit.domain.model.RouteSnapshot

class CarRouteSummaryTest {
    @Test fun noRouteNeverInventsDistance() {
        val info = CarRouteSummary.from(null,null,100_000)
        assertFalse(info.routeAvailable)
        assertFalse(info.detail.contains("km"))
    }
    @Test fun actualOsrmGeometryHasDistanceOnlyFromOsrm() {
        val route = RouteSnapshot(destination="Antalya", totalKm=550.3,
            routePoints=listOf(GeoCoordinate(38.0,35.0), GeoCoordinate(36.9,30.7)))
        val info = CarRouteSummary.from(route,null,100_000)
        assertTrue(info.routeAvailable)
        assertTrue(info.title.contains("Antalya"))
        assertTrue(info.detail.contains("550,3") || info.detail.contains("550.3"))
    }
    @Test fun routeWithoutValidGeometryNotReady() {
        val info = CarRouteSummary.from(RouteSnapshot(destination="Antalya", totalKm=500.0),null,100_000)
        assertFalse(info.routeAvailable)
    }
    @Test fun oldFixNeverClaimsCurrentGps() {
        val fix = GpsFix(GeoCoordinate(38.0,35.0),8f,80_000)
        assertTrue(CarRouteSummary.from(null,fix,100_000).locationStatus.contains("yok"))
    }
}

package tr.id4.mediahub.nativecockpit.domain.usecase

import org.junit.Assert.*
import org.junit.Test
import tr.id4.mediahub.nativecockpit.domain.model.*

class AutomaticChargePlannerTest {
    private val now = 1_800_000_000_000L
    private val route = RouteSnapshot(
        routeId = "verified-osrm",
        totalKm = 355.0,
        routePoints = listOf(GeoCoordinate(37.0, 34.0), GeoCoordinate(37.0, 38.0))
    )
    private val dc = ChargingStation(
        id = "dc-midpoint", name = "DC Yol Üzeri", operator = "Test",
        address = "fixture", coordinate = GeoCoordinate(37.0, 36.2),
        sockets = listOf(ChargingSocket("1", "CCS2", "DC", "150 kW")),
        sourceVerifiedAtUtc = "2026-10-29T00:00:00Z"
    )
    private fun plan(stations: List<ChargingStation>, route: RouteSnapshot = this.route) =
        AutomaticChargePlanner.plan(
            route, stations, DepartureSoc.ExplicitAssumption(80.0),
            batteryKwh = 60.0, consumptionWhPerKm = 180.0,
            reservePercent = 15.0, nowMs = now
        )

    @Test fun selectsReachableVerifiedDcAndKeepsReserve() {
        val outcome = plan(listOf(dc)) as AutomaticChargePlan.Ready
        assertEquals(1, outcome.stops.size)
        assertEquals("dc-midpoint", outcome.stops[0].station.id)
        assertTrue(outcome.stops[0].suggestedChargeKwh > 0.0)
        assertEquals(150.0, outcome.stops[0].verifiedPowerKw!!, 0.01)
        assertTrue(outcome.arrivalSocPercent >= 15.0)
    }

    @Test fun unknownPowerNeverGetsInventedAsKilowatts() {
        val outcome = plan(listOf(dc.copy(
            sockets = listOf(ChargingSocket("1", "CCS2", "DC", "150"))
        ))) as AutomaticChargePlan.Ready
        assertNull(outcome.stops[0].verifiedPowerKw)
    }

    @Test fun noVerifiedDcOrWrongRouteFailsClosed() {
        assertTrue(plan(emptyList()) is AutomaticChargePlan.Unavailable)
        assertTrue(plan(listOf(dc.copy(
            sockets = listOf(ChargingSocket("1", "Type2", "AC", "22 kW"))
        ))) is AutomaticChargePlan.Unavailable)
        assertTrue(plan(listOf(dc), route.copy(routeId = null)) is AutomaticChargePlan.Unavailable)
    }

    @Test fun shortRouteDoesNotInventChargeStop() {
        val short = route.copy(
            totalKm = 30.0,
            routePoints = listOf(GeoCoordinate(37.0, 34.0), GeoCoordinate(37.0, 34.3))
        )
        val result = plan(emptyList(), short) as AutomaticChargePlan.Ready
        assertTrue(result.stops.isEmpty())
    }
}

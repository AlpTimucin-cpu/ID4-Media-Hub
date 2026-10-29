package tr.id4.mediahub.nativecockpit.domain.usecase

import org.junit.Assert.*
import org.junit.Test
import tr.id4.mediahub.nativecockpit.domain.model.*

class AutomaticDcChargingPlannerTest {
    private val route = RouteSnapshot(
        routeId = "verified-osrm-fixture",
        totalKm = 350.0,
        routePoints = listOf(GeoCoordinate(38.0, 35.0), GeoCoordinate(38.0, 39.0))
    )
    private val dc = ChargingStation(
        id = "verified-dc", name = "Fixture DC", operator = "Fixture", address = "",
        coordinate = GeoCoordinate(38.0, 37.0),
        sockets = listOf(ChargingSocket("one", "CCS2", "DC", "unknown")),
        sourceVerifiedAtUtc = "2026-10-29T00:00:00Z"
    )
    private val baseline = EvBaselineEnergyEstimator.estimate(
        BaselineEnergyInputs(
            route = route,
            departureSoc = DepartureSoc.ExplicitAssumption(80.0),
            usableBatteryKwh = 60.0,
            consumptionWhPerKm = 180.0,
            consumptionBasis = ConsumptionBasis.REFERENCE_MODEL,
            requiredArrivalSocPercent = 15.0,
            nowMs = 1_800_000_000_000L
        )
    )

    @Test fun oneVerifiedDcCandidateEnablesModelStopWithoutAssumingPower() {
        val candidates = AutomaticDcChargingPlanner.candidates(route, listOf(dc))
        assertEquals(1, candidates.size)
        val plan = AutomaticDcChargingPlanner.plan(route, candidates, baseline, 60.0, 80.0, 15.0)
        assertTrue(plan is AutomaticChargePlan.Estimate)
        val estimate = plan as AutomaticChargePlan.Estimate
        assertEquals("verified-dc", estimate.stops.single().station.id)
        assertTrue(estimate.stops.single().suggestedEnergyKwh > 0)
        assertTrue(estimate.source.contains("MODEL/TAHMİN"))
    }

    @Test fun powerRequiresExplicitKilowattUnit() {
        val specified = dc.copy(
            sockets = listOf(ChargingSocket("one", "CCS2", "DC", "150 kW"))
        )
        val verified = AutomaticDcChargingPlanner.plan(
            route, AutomaticDcChargingPlanner.candidates(route, listOf(specified)),
            baseline, 60.0, 80.0, 15.0
        ) as AutomaticChargePlan.Estimate
        assertEquals(150.0, verified.stops.single().verifiedPowerKw!!, 0.01)

        val unknown = AutomaticDcChargingPlanner.plan(
            route, AutomaticDcChargingPlanner.candidates(route, listOf(dc.copy(
                sockets = listOf(ChargingSocket("one", "CCS2", "DC", "150"))
            ))),
            baseline, 60.0, 80.0, 15.0
        ) as AutomaticChargePlan.Estimate
        assertNull(unknown.stops.single().verifiedPowerKw)
    }

    @Test fun noReachableDcIsUnavailableNotAFabricatedPlan() {
        val result = AutomaticDcChargingPlanner.plan(route, emptyList(), baseline, 60.0, 80.0, 15.0)
        assertTrue(result is AutomaticChargePlan.Unavailable)
    }

    @Test fun missingCalibrationFailsClosed() {
        val result = AutomaticDcChargingPlanner.plan(route, emptyList(), baseline, Double.NaN, 80.0, 15.0)
        assertTrue(result is AutomaticChargePlan.Unavailable)
    }
}

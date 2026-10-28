package tr.id4.mediahub.nativecockpit.domain.usecase

import org.junit.Assert.*
import org.junit.Test
import tr.id4.mediahub.nativecockpit.domain.model.GeoCoordinate
import tr.id4.mediahub.nativecockpit.domain.model.RouteSnapshot

class EvBaselineEnergyEstimatorTest {
    private val now = 1_800_000_000_000L
    private val route = RouteSnapshot(
        routeId = "osrm-validated",
        routePoints = listOf(
            GeoCoordinate(37.0, 34.0), GeoCoordinate(37.4, 34.4)
        ),
        totalKm = 100.0
    )
    private fun inputs(
        soc: DepartureSoc = DepartureSoc.ExplicitAssumption(80.0),
        route: RouteSnapshot = this.route
    ) = BaselineEnergyInputs(
        route = route,
        departureSoc = soc,
        usableBatteryKwh = 60.0,
        consumptionWhPerKm = 180.0,
        consumptionBasis = ConsumptionBasis.REFERENCE_MODEL,
        requiredArrivalSocPercent = 12.0,
        nowMs = now
    )

    @Test fun baselineIsLabelledEstimateNotLiveBattery() {
        val result = EvBaselineEnergyEstimator.estimate(inputs())
            as BaselineEnergyResult.Estimate
        assertEquals(18.0, result.driveEnergyKwh, 0.0001)
        assertEquals(50.0, result.arrivalSocPercent, 0.0001)
        assertFalse(result.usedLiveObd)
        assertFalse(result.chargingStopNeeded)
    }

    @Test fun freshObdCanBeUsedAndStaleObdMustBeRejected() {
        val fresh = EvBaselineEnergyEstimator.estimate(
            inputs(DepartureSoc.FreshObd(80.0, now - 1_000))
        ) as BaselineEnergyResult.Estimate
        assertTrue(fresh.usedLiveObd)
        assertTrue(EvBaselineEnergyEstimator.estimate(
            inputs(DepartureSoc.FreshObd(80.0, now - 11_000))
        ) is BaselineEnergyResult.Unavailable)
    }

    @Test fun distantRouteRequiresChargingWithoutInventingStop() {
        val long = route.copy(totalKm = 400.0)
        val result = EvBaselineEnergyEstimator.estimate(
            inputs(route = long)
        ) as BaselineEnergyResult.Estimate
        assertTrue(result.chargingStopNeeded)
        assertEquals(-40.0, result.arrivalSocPercent, 0.0001)
        assertTrue(result.requiredChargingEnergyKwh > 0.0)
    }

    @Test fun invalidOrMissingRouteAndBatteryFailClosed() {
        assertTrue(EvBaselineEnergyEstimator.estimate(
            inputs(route = route.copy(routeId = null))
        ) is BaselineEnergyResult.Unavailable)
        assertTrue(EvBaselineEnergyEstimator.estimate(
            inputs().copy(usableBatteryKwh = 0.0)
        ) is BaselineEnergyResult.Unavailable)
        assertTrue(EvBaselineEnergyEstimator.estimate(
            inputs().copy(consumptionWhPerKm = Double.NaN)
        ) is BaselineEnergyResult.Unavailable)
    }
}

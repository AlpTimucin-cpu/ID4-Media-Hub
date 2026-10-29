package tr.id4.mediahub.nativecockpit.domain.usecase

import org.junit.Assert.*
import org.junit.Test
import tr.id4.mediahub.nativecockpit.domain.model.*

class AutomaticChargePlannerTest {
    private val route = RouteSnapshot(
        routeId = "osrm-route",
        routePoints = listOf(GeoCoordinate(37.0, 34.0), GeoCoordinate(39.0, 34.0)),
        totalKm = 220.0
    )
    private val station = ChargingStation(
        "verified-dc", "Test DC", "Test", "",
        GeoCoordinate(38.0, 34.0),
        listOf(ChargingSocket("socket", "CCS2", "DC", "raw-unverified")),
        "2026-10-29T00:00:00Z"
    )
    private fun inputs(soc: Double, power: Map<String,Double>) = AutomaticChargeInputs(
        route, listOf(station), power, soc, 60.0, 180.0, 15.0, false
    )

    @Test fun predictedStopRequiresVerifiedPowerAndReachability() {
        val result = AutomaticChargePlanner.estimate(
            inputs(50.0, mapOf(station.id to 150.0))
        )
        assertTrue(result is AutomaticChargeResult.Estimate)
        val estimate = result as AutomaticChargeResult.Estimate
        assertEquals(1, estimate.stops.size)
        assertEquals(station.id, estimate.stops.single().stationId)
        assertTrue(estimate.stops.single().chargeKwh > 0.0)
        assertTrue(estimate.destinationSocPercent >= 15.0)
    }

    @Test fun unverifiedRawPowerIsNotAcceptedAsKw() {
        assertTrue(
            AutomaticChargePlanner.estimate(inputs(50.0, emptyMap())) is
                AutomaticChargeResult.Unavailable
        )
    }

    @Test fun sufficientlyChargedVehicleNeedsNoStop() {
        val result = AutomaticChargePlanner.estimate(inputs(95.0, emptyMap()))
        assertTrue(result is AutomaticChargeResult.Estimate)
        assertTrue((result as AutomaticChargeResult.Estimate).stops.isEmpty())
    }

    @Test fun invalidCalibrationFailsClosed() {
        assertTrue(
            AutomaticChargePlanner.estimate(inputs(-1.0, mapOf(station.id to 150.0))) is
                AutomaticChargeResult.Unavailable
        )
    }
}

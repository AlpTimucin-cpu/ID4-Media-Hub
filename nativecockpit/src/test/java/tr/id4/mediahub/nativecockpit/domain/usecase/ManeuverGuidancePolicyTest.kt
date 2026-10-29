package tr.id4.mediahub.nativecockpit.domain.usecase

import org.junit.Assert.*
import org.junit.Test
import tr.id4.mediahub.nativecockpit.domain.model.*

class ManeuverGuidancePolicyTest {
    private val now = 1_800_000_000_000L
    private val a = GeoCoordinate(38.00, 35.00)
    private val b = GeoCoordinate(38.00, 35.03)
    private val destination = GeoCoordinate(38.00, 35.029)
    private val turn = NavigationManeuver("step-1", "sağa dönün", destination)
    private val route = RouteSnapshot(
        routeId = "real-route-id",
        nextManeuver = turn,
        routePoints = listOf(a, b)
    )
    private fun fix(
        lon: Double,
        time: Long = now,
        lat: Double = 38.00,
        accuracy: Float = 8f
    ) = GpsFix(GeoCoordinate(lat, lon), accuracy, time)

    @Test fun noRealRouteOrGpsNoSpeech() {
        val p = ManeuverGuidancePolicy()
        assertNull(p.nextAnnouncement(null, fix(35.027), now))
        assertNull(p.nextAnnouncement(RouteSnapshot(), fix(35.027), now))
        assertNull(p.nextAnnouncement(route, null, now))
        assertNull(p.nextAnnouncement(route, fix(35.027, now - 12_000), now))
        assertNull(p.nextAnnouncement(route, fix(35.027, accuracy = 60f), now))
    }

    @Test fun speechAtTwoHundredMetersOnly() {
        val p = ManeuverGuidancePolicy()
        // Do not read a turn from a kilometre away.
        assertNull(p.nextAnnouncement(route, fix(35.019), now))

        val first = fix(35.027, now + 1_000)
        assertEquals("İki yüz metre sonra, sağa dönün",
            p.nextAnnouncement(route, first, now + 1_000))
        assertNull(p.nextAnnouncement(route, first, now + 1_500))

        // If TTS is unavailable, do not silently consume the 200 m cue.
        assertEquals("İki yüz metre sonra, sağa dönün",
            p.nextAnnouncement(route, fix(35.0271, now + 2_000), now + 2_000))
        p.confirmSpoken(now + 2_000)
        assertNull(p.nextAnnouncement(route, fix(35.0272, now + 3_000), now + 3_000))

        val final = fix(35.0285, now + 7_000)
        assertEquals("Elli metre sonra, sağa dönün",
            p.nextAnnouncement(route, final, now + 7_000))
        p.confirmSpoken(now + 7_000)
        assertNull(p.nextAnnouncement(route, fix(35.0286, now + 8_000), now + 8_000))
    }

    @Test fun nearTurnAndOffRouteFailClosed() {
        val p = ManeuverGuidancePolicy()
        assertNull(p.nextAnnouncement(route, fix(35.028, lat = 38.001), now))
        val near = ManeuverGuidancePolicy()
        assertEquals("Elli metre sonra, sağa dönün",
            near.nextAnnouncement(route, fix(35.0289), now))
        near.confirmSpoken(now)
        assertNull(near.nextAnnouncement(route, fix(35.029, now + 5_000), now + 5_000))
        assertNull(near.nextAnnouncement(route, fix(35.030, now + 10_000), now + 10_000))
    }

    @Test fun neverSpeakUntrustedInstruction() {
        val p = ManeuverGuidancePolicy()
        val bad = route.copy(nextManeuver = turn.copy(
            instructionTr = "<script>sağa dön</script>"
        ))
        assertNull(p.nextAnnouncement(bad, fix(35.027), now))
        assertNull(p.nextAnnouncement(route.copy(routeId = null), fix(35.027), now))
    }

    @Test fun routeChangeResetsAcknowledgedStages() {
        val p = ManeuverGuidancePolicy()
        assertNotNull(p.nextAnnouncement(route, fix(35.027), now))
        p.confirmSpoken(now)
        val second = route.copy(routeId = "new-route", nextManeuver = turn.copy(id = "new-step"))
        assertNotNull(p.nextAnnouncement(second, fix(35.027, now + 5_000), now + 5_000))
    }
}

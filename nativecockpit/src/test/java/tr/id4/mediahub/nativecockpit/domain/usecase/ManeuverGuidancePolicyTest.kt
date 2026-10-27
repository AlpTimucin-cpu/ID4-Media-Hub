package tr.id4.mediahub.nativecockpit.domain.usecase

import org.junit.Assert.*
import org.junit.Test
import tr.id4.mediahub.nativecockpit.domain.model.*

class ManeuverGuidancePolicyTest {
    private val now=1_800_000_000_000L
    private val a=GeoCoordinate(38.00,35.00)
    private val b=GeoCoordinate(38.00,35.03)
    private val destination=GeoCoordinate(38.00,35.029)
    private val turn=NavigationManeuver("step-1","sağa dönün",destination)
    private val route=RouteSnapshot(routeId="real-route-id",nextManeuver=turn,
        routePoints=listOf(a,b))
    private fun fix(lon: Double,time:Long=now,lat:Double=38.00,accuracy:Float=8f) =
        GpsFix(GeoCoordinate(lat,lon),accuracy,time)

    @Test fun noRealRouteOrGpsNoSpeech() {
        val p=ManeuverGuidancePolicy()
        assertNull(p.nextAnnouncement(null,fix(35.02),now))
        assertNull(p.nextAnnouncement(RouteSnapshot(),fix(35.02),now))
        assertNull(p.nextAnnouncement(route,null,now))
        assertNull(p.nextAnnouncement(route,fix(35.02,now-12_000),now))
        assertNull(p.nextAnnouncement(route,fix(35.02,accuracy=60f),now))
    }

    @Test fun speaksOncePerStageNotRepeatedOnSameFix() {
        val p=ManeuverGuidancePolicy()
        val nearKm=fix(35.0190)
        assertEquals("Yaklaşık bir kilometre sonra, sağa dönün",
            p.nextAnnouncement(route,nearKm,now))
        assertNull(p.nextAnnouncement(route,nearKm,now+1000))
        assertNull(p.nextAnnouncement(route,fix(35.0191,now+1000),now+1000))
        assertEquals("Yaklaşık üç yüz metre sonra, sağa dönün",
            p.nextAnnouncement(route,fix(35.026,now+5000),now+5000))
        assertNull(p.nextAnnouncement(route,fix(35.026,now+6000),now+6000))
        assertEquals("Yaklaşık yüz metre sonra, sağa dönün",
            p.nextAnnouncement(route,fix(35.0279,now+10000),now+10000))
    }

    @Test fun nearTurnAndOffRouteFailClosed() {
        val p=ManeuverGuidancePolicy()
        assertNull(p.nextAnnouncement(route,fix(35.025,lat=38.001),now))
        val final=ManeuverGuidancePolicy()
        assertEquals("Şimdi, sağa dönün",final.nextAnnouncement(route,fix(35.0289),now))
        assertNull(final.nextAnnouncement(route,fix(35.029,now+5000),now+5000))
        assertNull(final.nextAnnouncement(route,fix(35.030,now+10000),now+10000))
    }

    @Test fun neverSpeakUntrustedInstruction() {
        val p=ManeuverGuidancePolicy()
        val bad=route.copy(nextManeuver=turn.copy(instructionTr="<script>sağa dön</script>"))
        assertNull(p.nextAnnouncement(bad,fix(35.02),now))
        assertNull(p.nextAnnouncement(route.copy(routeId=null),fix(35.02),now))
    }
}

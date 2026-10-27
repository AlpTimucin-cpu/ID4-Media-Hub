package tr.id4.mediahub.nativecockpit.data.routing

import org.junit.Assert.*
import org.junit.Test

/** Fixtures are test-only: not displayed to users, never presented as a real route. */
class OsrmResponseParserTest {
    private val fixture="""{
      "code":"Ok", "routes":[{
        "distance":1900,"duration":280,
        "geometry":{"type":"LineString","coordinates":[[35.0,38.0],[35.01,38.0],[35.02,38.0]]},
        "legs":[{"steps":[{"name":"D300","maneuver":{"type":"turn","modifier":"right","location":[35.01,38.0]}}]}]
      }]
    }"""
    @Test fun realSchemaCreatesOneOptionAndStableManeuverId() {
        val options=OsrmResponseParser.parse(fixture,"Test adresi")
        assertEquals(1,options.size)
        assertEquals(1.9,options[0].distanceKm,1e-6)
        assertEquals(3,options[0].snapshot.routePoints.size)
        assertNotNull(options[0].snapshot.routeId)
        assertEquals(1,options[0].snapshot.maneuvers.size)
        assertTrue(options[0].snapshot.maneuvers[0].instructionTr.contains("sağa"))
        assertNull(options[0].snapshot.remainingKm) // requires measured GPS
    }
    @Test fun noRouteFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            OsrmResponseParser.parse("""{"code":"NoRoute","routes":[]}""","Hedef")
        }
    }
    @Test fun malformedGeometryDoesNotInventRoad() {
        assertThrows(IllegalArgumentException::class.java) {
            OsrmResponseParser.parse(fixture.replace("[35.0,38.0]","[350.0,38.0]"),"Hedef")
        }
    }
    @Test fun unknownManeuversAreNeverTranslatedAsKnownTurns() {
        assertNull(OsrmTurkishInstructions.speak("unrecognized","right","Example"))
    }
    @Test fun photonNoFakeSuggestions() {
        val empty="""{"features":[]}"""
        assertTrue(PhotonResponseParser.parse(empty).isEmpty())
    }
}

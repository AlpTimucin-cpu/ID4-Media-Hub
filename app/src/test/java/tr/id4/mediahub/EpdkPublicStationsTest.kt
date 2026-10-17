package tr.id4.mediahub

import org.junit.Assert.*
import org.junit.Test

class EpdkPublicStationsTest {
    @Test fun noPrivateOrUnverifiedStations() {
        val json="""{"schema_version":1,
          "source_url":"https://apigateway.epdk.gov.tr/sarjIstasyonlari",
          "verified_at_utc":"2026-10-18T00:00:00Z",
          "all_rows_count":20000,"public_rows_count":10000,
          "geocoded_public_rows_count":1,
          "prices_included":false,"live_status_included":false,
          "stations":[{"official_station_id":"EK-1","name":"Deneme",
             "operator":"Operatör","operator_license":"L-1",
             "address":"Niğde","lat":37.9,"lon":34.6,
             "source":"EPDK","public":true,"sockets":[
               {"socket_id":"1","type":"CCS","kind":"DC","power_raw":"120"}]}]}"""
        val result=EpdkPublicStations.parse(json)
        assertEquals(1,result.stations.size)
        assertEquals("EPDK • resmî halka açık",result.stations[0].asPoint().source)
    }
}

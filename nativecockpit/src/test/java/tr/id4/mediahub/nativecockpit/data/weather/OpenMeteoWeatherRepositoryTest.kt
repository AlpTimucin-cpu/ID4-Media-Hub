package tr.id4.mediahub.nativecockpit.data.weather

import org.junit.Assert.*
import org.junit.Test
import tr.id4.mediahub.nativecockpit.domain.model.GeoCoordinate

class OpenMeteoWeatherRepositoryTest {
    private val repository = OpenMeteoWeatherRepository()

    @Test fun samplesAt100KmAndIncludesExactDestination() {
        val route = listOf(GeoCoordinate(37.0, 34.0), GeoCoordinate(39.0, 34.0))
        val samples = repository.sample(route)
        assertEquals(4, samples.size)
        assertEquals(0.0, samples.first().second, 0.001)
        assertEquals(100_000.0, samples[1].second, 0.01)
        assertEquals(200_000.0, samples[2].second, 0.01)
        assertEquals(39.0, samples.last().first.latitude, 0.000001)
        assertEquals(34.0, samples.last().first.longitude, 0.000001)
    }

    @Test fun malformedOrMissingRouteYieldsNoFakeForecastPoints() {
        assertTrue(repository.sample(emptyList()).isEmpty())
        assertTrue(repository.sample(listOf(GeoCoordinate(38.0, 34.0))).isEmpty())
        assertTrue(repository.sample(listOf(
            GeoCoordinate(Double.NaN, 34.0), GeoCoordinate(39.0, 34.0)
        )).isEmpty())
    }
}

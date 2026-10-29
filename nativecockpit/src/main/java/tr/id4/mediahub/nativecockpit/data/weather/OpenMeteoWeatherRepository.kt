package tr.id4.mediahub.nativecockpit.data.weather

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import tr.id4.mediahub.nativecockpit.domain.model.*

internal interface OpenMeteoService {
    @GET("v1/forecast")
    suspend fun weather(
        @Query("latitude") latitudes: String,
        @Query("longitude") longitudes: String,
        @Query("current") fields: String = "temperature_2m,wind_speed_10m,weather_code",
        @Query("current_weather") legacy: Boolean = true
    ): Response<JsonElement>
}

/** A bounded, cancellable request for real model-based current conditions. */
@Singleton
class OpenMeteoWeatherRepository @Inject constructor() {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .build()
    private val api = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .client(http)
        .addConverterFactory(GsonConverterFactory.create())
        .build().create(OpenMeteoService::class.java)

    suspend fun routeWeather(route: RouteSnapshot): Result<RouteWeatherState> =
        try {
            val positions = withContext(Dispatchers.Default) {
                sample(route.routePoints)
            }
            if (positions.isEmpty()) {
                Result.failure(IllegalArgumentException("Geçerli OSRM rota geometrisi yok"))
            } else {
                val data = withContext(Dispatchers.IO) {
                    val latitudes = positions.joinToString(",") {
                        String.format(Locale.US, "%.5f", it.first.latitude)
                    }
                    val longitudes = positions.joinToString(",") {
                        String.format(Locale.US, "%.5f", it.first.longitude)
                    }
                    val response = api.weather(latitudes, longitudes)
                    if (!response.isSuccessful) {
                        response.errorBody()?.close()
                        throw IOException("Open-Meteo HTTP ${response.code()}")
                    }
                    response.body() ?: throw IOException("Open-Meteo boş yanıt")
                }
                val objects = if (data.isJsonArray) {
                    data.asJsonArray.map { it.asJsonObject }
                } else listOf(data.asJsonObject)
                if (objects.size != positions.size)
                    throw IOException("Hava durumu nokta sayısı uyuşmuyor")
                val points = objects.mapIndexedNotNull { i, obj ->
                    val current = obj.getAsJsonObject("current")
                    val legacy = obj.getAsJsonObject("current_weather")
                    val temp = current?.safeNumber("temperature_2m")
                        ?: legacy?.safeNumber("temperature")
                    val wind = current?.safeNumber("wind_speed_10m")
                        ?: legacy?.safeNumber("windspeed")
                    val code = current?.safeNumber("weather_code")
                        ?: legacy?.safeNumber("weathercode")
                    val at = current?.get("time")?.asString
                        ?: legacy?.get("time")?.asString
                    if (temp == null || wind == null || code == null || at.isNullOrBlank() ||
                        temp !in -90.0..65.0 || wind !in 0.0..350.0 ||
                        code !in 0.0..100.0
                    ) null else WeatherWaypoint(
                        coordinate = positions[i].first,
                        distanceFromStartKm = positions[i].second / 1000.0,
                        temperatureC = temp,
                        windSpeedKmh = wind,
                        weatherCode = code.toInt(),
                        observedAt = at
                    )
                }
                if (points.isEmpty()) throw IOException("Geçerli hava durumu alınamadı")
                Result.success(RouteWeatherState(
                    routeId = route.routeId,
                    routeLengthKm = positions.last().second / 1000.0,
                    points = points,
                    fetchedAtMillis = System.currentTimeMillis(),
                    error = if (points.size < positions.size) "Bazı hava noktaları alınamadı" else null
                ))
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            Result.failure(error)
        }

    private fun JsonObject.safeNumber(field: String): Double? = try {
        get(field)?.takeUnless { it.isJsonNull }?.asDouble?.takeIf { it.isFinite() }
    } catch (_: RuntimeException) {
        null
    }

    /**
     * Geodesic sampling every 100 km including start and exact final destination.
     * Cap is fail-closed rather than silently skipping unrequested route segments.
     */
    internal fun sample(path: List<GeoCoordinate>): List<Pair<GeoCoordinate, Double>> {
        if (!validRouteGeometry(path)) return emptyList()
        val cumulative = DoubleArray(path.size)
        for (i in 1 until path.size) {
            cumulative[i] = cumulative[i - 1] + distance(path[i - 1], path[i])
        }
        val total = cumulative.last()
        if (total <= 1.0 || !total.isFinite()) return emptyList()
        val count = ceil(total / 100_000.0).toInt() + 1
        if (count !in 2..65) return emptyList()
        val steps = mutableListOf(0.0)
        var d = 100_000.0
        while (d < total - 1.0) {
            steps += d
            d += 100_000.0
        }
        steps += total
        return steps.map { target ->
            var segment = 0
            while (segment < path.lastIndex - 1 && cumulative[segment + 1] < target)
                segment++
            val length = cumulative[segment + 1] - cumulative[segment]
            val f = if (length > 0.0) (target - cumulative[segment]) / length else 0.0
            val a = path[segment]
            val b = path[segment + 1]
            GeoCoordinate(
                a.latitude + (b.latitude - a.latitude) * f,
                a.longitude + (b.longitude - a.longitude) * f
            ) to target
        }
    }

    private fun distance(a: GeoCoordinate, b: GeoCoordinate): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dlat = lat2 - lat1
        val dlon = Math.toRadians(b.longitude - a.longitude)
        val x = sin(dlat/2).pow(2) + cos(lat1)*cos(lat2)*sin(dlon/2).pow(2)
        return 2.0 * 6_371_008.8 * asin(sqrt(x.coerceIn(0.0,1.0)))
    }
}

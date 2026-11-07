package com.evcockpit.data.repository

import com.google.gson.JsonParser
import com.evcockpit.data.settings.AppConfigRepository
import com.evcockpit.domain.usecase.BoundedNetworkRetry
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import com.evcockpit.data.routing.*
import com.evcockpit.domain.model.*
import com.evcockpit.domain.repository.RouteRepository

@Singleton
class OsrmRouteRepository @Inject constructor(
    private val appConfig: AppConfigRepository
) : RouteRepository {
    private val http = OkHttpClient.Builder()
        .connectTimeout(9, TimeUnit.SECONDS)
        .readTimeout(17, TimeUnit.SECONDS)
        .callTimeout(22, TimeUnit.SECONDS)
        .build()
    private fun osrm(root: String): OsrmService = Retrofit.Builder()
        .baseUrl(root).client(http).build().create(OsrmService::class.java)

    private fun photon(root: String): PhotonService = Retrofit.Builder()
        .baseUrl(root).client(http).build().create(PhotonService::class.java)

    private val selected = MutableStateFlow<RouteSnapshot?>(null)
    private val variants = MutableStateFlow<List<RouteOption>>(emptyList())
    private val index = MutableStateFlow<Int?>(null)
    override val currentRoute: StateFlow<RouteSnapshot?> = selected.asStateFlow()
    override val options: StateFlow<List<RouteOption>> = variants.asStateFlow()
    override val selectedIndex: StateFlow<Int?> = index.asStateFlow()

    override suspend fun search(query: String): List<RoutePlace> = withContext(Dispatchers.IO) {
        require(query.trim().length in 3..100) { "Enter at least 3 characters" }
        val root = appConfig.configuration.first().endpoints.geocodingBaseUrl
        val result = BoundedNetworkRetry.io {
            photon(root).search(query.trim())
        }
        if (!result.isSuccessful) {
            result.errorBody()?.close()
            throw IOException(when (result.code()) {
                400 -> "Photon request rejected (HTTP 400)."
                429 -> "Address query rate limit reached (HTTP 429); try again later."
                in 500..599 -> "Photon server is unavailable (HTTP ${result.code()})."
                else -> "Address service returned HTTP ${result.code()}."
            })
        }
        val body = result.body() ?: throw IOException("Address service returned an empty response")
        body.use { PhotonResponseParser.parse(it.string()) }
    }

    override suspend fun calculate(
        origin: GeoCoordinate, destination: RoutePlace
    ): RouteCalculationResult = calculateVia(origin, destination, emptyList())

    override suspend fun calculateVia(
        origin: GeoCoordinate,
        destination: RoutePlace,
        stops: List<GeoCoordinate>
    ): RouteCalculationResult = withContext(Dispatchers.IO) {
        val root = appConfig.configuration.first().endpoints.osrmBaseUrl
            ?: return@withContext RouteCalculationResult.Failure(
                null, CoreIssue.CONFIG_UNCONFIRMED.messageTr +
                    " Kendi HTTPS OSRM sunucunuzun adresini Ayarlar bölümüne girin."
            )
        val url = try {
            OsrmRequestFormat.urlVia(origin, stops, destination.coordinate)
        } catch (error: IllegalArgumentException) {
            return@withContext RouteCalculationResult.Failure(null, error.message ?: "Coordinate error")
        }
        try {
            val response = BoundedNetworkRetry.io { osrm(root).route(url) }
            if (!response.isSuccessful) {
                val info = response.errorBody()?.use { body ->
                    val bytes = body.bytes()
                    if (bytes.size > 4_096) null else parseErrorCode(bytes.toString(Charsets.UTF_8))
                }
                response.body()?.close()
                return@withContext RouteCalculationResult.Failure(
                    response.code(), explainStatus(response.code(), info)
                )
            }
            val body = response.body()
                ?: return@withContext RouteCalculationResult.Failure(null, "OSRM returned an empty response")
            val alternatives = try {
                body.use { OsrmResponseParser.parse(it.string(), destination.label) }
            } catch (error: RuntimeException) {
                return@withContext RouteCalculationResult.Failure(null,
                    "Could not read OSRM route data: ${error.message?.take(80) ?: "invalid JSON"}")
            }
            // Publish only a completely validated response. Do not publish a partial route.
            variants.value = alternatives
            index.value = 0
            selected.value = alternatives.first().snapshot
            RouteCalculationResult.Success(alternatives.size)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: UnknownHostException) {
            RouteCalculationResult.Failure(null, "No internet connection or OSRM address could not be resolved")
        } catch (_: SocketTimeoutException) {
            RouteCalculationResult.Failure(null, "OSRM timed out. Check your internet connection")
        } catch (_: IOException) {
            RouteCalculationResult.Failure(null, "Could not connect to OSRM. Try again")
        } catch (error: RuntimeException) {
            RouteCalculationResult.Failure(null,
                "Could not process the route response: ${error.message?.take(90) ?: "beklenmeyen hata"}")
        }
    }

    override fun choose(index: Int) {
        val option = variants.value.getOrNull(index) ?: return
        this.index.value = index
        selected.value = option.snapshot
    }
    override fun clear() {
        variants.value = emptyList()
        index.value = null
        selected.value = null
    }

    companion object {
        fun explainStatus(status: Int, code: String?): String {
            val suffix = code?.let { " · OSRM: $it" } ?: ""
            return when (status) {
                400 -> "OSRM HTTP 400: coordinates or query parameters rejected$suffix"
                404 -> "OSRM HTTP 404: routing service not found$suffix"
                429 -> "OSRM HTTP 429: query rate limit exceeded$suffix"
                in 500..599 -> "OSRM HTTP $status: server temporarily unavailable$suffix"
                else -> "OSRM HTTP $status: request failed$suffix"
            }
        }
        /** Only expose the OSRM machine-readable error code, not arbitrary server text. */
        fun parseErrorCode(json: String): String? = try {
            JsonParser.parseString(json).asJsonObject.get("code")
                ?.asString?.takeIf { it.matches(Regex("[A-Za-z0-9_]{1,40}")) }
        } catch (_: Exception) { null }
    }
}

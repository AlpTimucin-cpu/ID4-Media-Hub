package tr.id4.mediahub.nativecockpit.data.repository

import com.google.gson.JsonParser
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
import tr.id4.mediahub.nativecockpit.data.routing.*
import tr.id4.mediahub.nativecockpit.domain.model.*
import tr.id4.mediahub.nativecockpit.domain.repository.RouteRepository

@Singleton
class OsrmRouteRepository @Inject constructor() : RouteRepository {
    private val http = OkHttpClient.Builder()
        .connectTimeout(9, TimeUnit.SECONDS)
        .readTimeout(17, TimeUnit.SECONDS)
        .callTimeout(22, TimeUnit.SECONDS)
        .build()
    private val osrm = Retrofit.Builder()
        .baseUrl("https://router.project-osrm.org/")
        .client(http).build().create(OsrmService::class.java)
    private val photon = Retrofit.Builder()
        .baseUrl("https://photon.komoot.io/")
        .client(http).build().create(PhotonService::class.java)

    private val selected = MutableStateFlow<RouteSnapshot?>(null)
    private val variants = MutableStateFlow<List<RouteOption>>(emptyList())
    private val index = MutableStateFlow<Int?>(null)
    override val currentRoute: StateFlow<RouteSnapshot?> = selected.asStateFlow()
    override val options: StateFlow<List<RouteOption>> = variants.asStateFlow()
    override val selectedIndex: StateFlow<Int?> = index.asStateFlow()

    override suspend fun search(query: String): List<RoutePlace> = withContext(Dispatchers.IO) {
        require(query.trim().length in 3..100) { "En az 3 harf gerekli" }
        val result = photon.search(query.trim())
        if (!result.isSuccessful) {
            result.errorBody()?.close()
            throw IOException(when (result.code()) {
                400 -> "Photon isteği reddedildi (HTTP 400)."
                429 -> "Adres sorgu sınırı doldu (HTTP 429); biraz sonra tekrar dene."
                in 500..599 -> "Photon sunucusu yanıt vermiyor (HTTP ${result.code()})."
                else -> "Adres servisi HTTP ${result.code()} hatası döndürdü."
            })
        }
        val body = result.body() ?: throw IOException("Adres servisi boş cevap verdi")
        body.use { PhotonResponseParser.parse(it.string()) }
    }

    override suspend fun calculate(
        origin: GeoCoordinate, destination: RoutePlace
    ): RouteCalculationResult = withContext(Dispatchers.IO) {
        val url = try {
            OsrmRequestFormat.url(origin, destination.coordinate)
        } catch (error: IllegalArgumentException) {
            return@withContext RouteCalculationResult.Failure(null, error.message ?: "Koordinat hatası")
        }
        try {
            val response = osrm.route(url)
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
                ?: return@withContext RouteCalculationResult.Failure(null, "OSRM boş yanıt verdi")
            val alternatives = try {
                body.use { OsrmResponseParser.parse(it.string(), destination.label) }
            } catch (error: RuntimeException) {
                return@withContext RouteCalculationResult.Failure(null,
                    "OSRM rota verisi okunamadı: ${error.message?.take(80) ?: "geçersiz JSON"}")
            }
            // Publish only a completely validated response. Do not publish a partial route.
            variants.value = alternatives
            index.value = 0
            selected.value = alternatives.first().snapshot
            RouteCalculationResult.Success(alternatives.size)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: UnknownHostException) {
            RouteCalculationResult.Failure(null, "İnternet bağlantısı yok veya OSRM adresi çözümlenemiyor")
        } catch (_: SocketTimeoutException) {
            RouteCalculationResult.Failure(null, "OSRM zaman aşımı. İnternet bağlantısını kontrol et")
        } catch (_: IOException) {
            RouteCalculationResult.Failure(null, "OSRM bağlantısı kurulamadı. Yeniden dene")
        } catch (error: RuntimeException) {
            RouteCalculationResult.Failure(null,
                "Rota yanıtı işlenemedi: ${error.message?.take(90) ?: "beklenmeyen hata"}")
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
                400 -> "OSRM HTTP 400: koordinatlar veya sorgu parametreleri reddedildi$suffix"
                404 -> "OSRM HTTP 404: rota servisi bulunamadı$suffix"
                429 -> "OSRM HTTP 429: sorgu sınırı aşıldı$suffix"
                in 500..599 -> "OSRM HTTP $status: sunucu geçici olarak kullanılamıyor$suffix"
                else -> "OSRM HTTP $status: istek başarısız$suffix"
            }
        }
        /** Only expose the OSRM machine-readable error code, not arbitrary server text. */
        fun parseErrorCode(json: String): String? = try {
            JsonParser.parseString(json).asJsonObject.get("code")
                ?.asString?.takeIf { it.matches(Regex("[A-Za-z0-9_]{1,40}")) }
        } catch (_: Exception) { null }
    }
}

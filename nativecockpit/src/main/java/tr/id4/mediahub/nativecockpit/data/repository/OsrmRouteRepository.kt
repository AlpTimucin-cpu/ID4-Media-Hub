package tr.id4.mediahub.nativecockpit.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import tr.id4.mediahub.nativecockpit.domain.model.*
import tr.id4.mediahub.nativecockpit.domain.repository.RouteRepository
import tr.id4.mediahub.nativecockpit.data.routing.*
import java.util.Locale
import java.util.concurrent.TimeUnit

@Singleton
class OsrmRouteRepository @Inject constructor() : RouteRepository {
    private val http=OkHttpClient.Builder()
        .connectTimeout(9,TimeUnit.SECONDS)
        .readTimeout(17,TimeUnit.SECONDS)
        .callTimeout(22,TimeUnit.SECONDS)
        .build()
    // Public demonstration endpoints. Configure your own provider for production.
    private val routes=Retrofit.Builder().baseUrl("https://router.project-osrm.org/")
        .client(http).build().create(OsrmService::class.java)
    private val photon=Retrofit.Builder().baseUrl("https://photon.komoot.io/")
        .client(http).build().create(PhotonService::class.java)
    private val selected=MutableStateFlow<RouteSnapshot?>(null)
    private val variants=MutableStateFlow<List<RouteOption>>(emptyList())
    private val currentIndex=MutableStateFlow<Int?>(null)
    override val currentRoute:StateFlow<RouteSnapshot?> = selected.asStateFlow()
    override val options:StateFlow<List<RouteOption>> = variants.asStateFlow()
    override val selectedIndex:StateFlow<Int?> = currentIndex.asStateFlow()

    override suspend fun search(query:String):List<RoutePlace> = withContext(Dispatchers.IO){
        require(query.trim().length in 3..100){"En az 3 harf gerekli"}
        photon.search(query.trim()).use { PhotonResponseParser.parse(it.string()) }
    }
    override suspend fun calculate(origin:GeoCoordinate,destination:RoutePlace) =
        withContext(Dispatchers.IO){
            require(origin.valid()&&destination.coordinate.valid()){"Koordinat geçersiz"}
            val target=destination.coordinate
            require(origin!=target){"Başlangıç ve varış aynı"}
            val coordinatePath=listOf(origin,target).joinToString(";"){
                String.format(Locale.US,"%.6f,%.6f",it.longitude,it.latitude)
            }
            // On a failed request keep the old route; do not show an imaginary replacement.
            val found=routes.route(coordinatePath).use { OsrmResponseParser.parse(it.string(),destination.label) }
            variants.value=found
            currentIndex.value=0
            selected.value=found.first().snapshot
        }
    override fun choose(index:Int){
        val item=variants.value.getOrNull(index)?:return
        currentIndex.value=index
        selected.value=item.snapshot
    }
    override fun clear(){variants.value=emptyList();currentIndex.value=null;selected.value=null}
}

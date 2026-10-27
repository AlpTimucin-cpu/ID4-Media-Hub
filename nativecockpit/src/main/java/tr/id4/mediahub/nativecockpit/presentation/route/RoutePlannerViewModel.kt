package tr.id4.mediahub.nativecockpit.presentation.route

import androidx.lifecycle.ViewModel
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import retrofit2.HttpException
import java.io.IOException
import tr.id4.mediahub.nativecockpit.data.location.FusedCurrentLocationProvider
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tr.id4.mediahub.nativecockpit.domain.model.*
import tr.id4.mediahub.nativecockpit.domain.repository.RouteRepository
import tr.id4.mediahub.nativecockpit.domain.repository.LocationRepository

data class RoutePlannerUiState(
    val originText:String="",val destinationText:String="",
    val originSelected:RoutePlace?=null,val destinationSelected:RoutePlace?=null,
    val originFromGps:Boolean=false,
    val gpsOrigin:GeoCoordinate?=null,
    val locatingGps:Boolean=false,
    val originMatches:List<RoutePlace> = emptyList(),
    val destinationMatches:List<RoutePlace> = emptyList(),
    val options:List<RouteOption> = emptyList(),val selectedIndex:Int?=null,
    val searchingOrigin:Boolean=false,val searchingDestination:Boolean=false,
    val planning:Boolean=false,val error:String?=null
) {
    val startLocation:GeoCoordinate? get() = (if(originFromGps) gpsOrigin else originSelected?.coordinate)?.takeIf { it.valid() }
    val destinationLocation:GeoCoordinate? get() = destinationSelected?.coordinate?.takeIf { it.valid() }
    val canCalculate:Boolean get() = !planning && !locatingGps && startLocation!=null && destinationLocation!=null
}

@HiltViewModel
class RoutePlannerViewModel @Inject constructor(
    private val routes:RouteRepository,
    private val currentLocation:FusedCurrentLocationProvider
):ViewModel(){
    private val state=MutableStateFlow(RoutePlannerUiState())
    val uiState:StateFlow<RoutePlannerUiState> = state.asStateFlow()
    private var originJob:Job?=null
    private var destinationJob:Job?=null
    private var planJob:Job?=null
    private var gpsJob:Job?=null
    init {
        viewModelScope.launch { routes.options.collect { options ->
            state.update { it.copy(options=options) }
        } }
        viewModelScope.launch { routes.selectedIndex.collect { index ->
            state.update { it.copy(selectedIndex=index) }
        } }
    }
    fun editOrigin(query:String){
        val text=query.take(100)
        gpsJob?.cancel();state.update { it.copy(originText=text,originSelected=null,originFromGps=false,gpsOrigin=null,locatingGps=false,originMatches=emptyList(),error=null) }
        originJob?.cancel();originJob=search(text,true)
    }
    fun editDestination(query:String){
        val text=query.take(100)
        state.update { it.copy(destinationText=text,destinationSelected=null,destinationMatches=emptyList(),error=null) }
        destinationJob?.cancel();destinationJob=search(text,false)
    }
    private fun search(query:String,isOrigin:Boolean):Job=viewModelScope.launch{
        if(query.trim().length<3){state.update{if(isOrigin)it.copy(searchingOrigin=false) else it.copy(searchingDestination=false)};return@launch}
        // Photon public demo: debounce and explicit user input; no mass geocoding.
        delay(550L)
        state.update{if(isOrigin)it.copy(searchingOrigin=true)else it.copy(searchingDestination=true)}
        try{
            val results=routes.search(query)
            val emptyError=if(results.isEmpty()) "Adres bulunamadı. Daha açık yaz." else null
            state.update{if(isOrigin)it.copy(originMatches=results,searchingOrigin=false,error=emptyError)
                else it.copy(destinationMatches=results,searchingDestination=false,error=emptyError)}
        }catch(e:CancellationException){throw e}
        catch(e:Exception){
            val message=when(e){
                is UnknownHostException -> "İnternet bağlantısı yok. Bağlantıyı kontrol et."
                is SocketTimeoutException -> "Adres servisi zaman aşımına uğradı."
                is HttpException -> when(e.code()){
                    400 -> "Photon adres isteğini reddetti (HTTP 400)."
                    429 -> "Photon istek sınırına ulaşıldı (HTTP 429)."
                    else -> "Photon HTTP ${e.code()} hatası verdi."
                }
                else -> "Adres araması başarısız: "+(e.message?.take(100)?:"bilinmeyen hata")
            }
            state.update{if(isOrigin)it.copy(searchingOrigin=false,error=message)
                else it.copy(searchingDestination=false,error=message)}
        }
    }
    fun chooseOrigin(item:RoutePlace){originJob?.cancel();gpsJob?.cancel();state.update{it.copy(originSelected=item,originText=item.label,originFromGps=false,gpsOrigin=null,locatingGps=false,originMatches=emptyList(),error=null)}}
    fun chooseDestination(item:RoutePlace){destinationJob?.cancel();state.update{it.copy(destinationSelected=item,destinationText=item.label,destinationMatches=emptyList(),error=null)}}
    fun useCurrentGps(){
        gpsJob?.cancel();originJob?.cancel()
        gpsJob=viewModelScope.launch {
            state.update{it.copy(locatingGps=true,originFromGps=false,gpsOrigin=null,originSelected=null,originMatches=emptyList(),error=null)}
            try{
                val result=currentLocation.getCurrentCoordinate()
                state.update{if(result.isSuccess)
                    it.copy(originFromGps=true,gpsOrigin=result.getOrNull(),originText="Güncel GPS konumum",locatingGps=false,error=null)
                else it.copy(originFromGps=false,gpsOrigin=null,locatingGps=false,
                    error=result.exceptionOrNull()?.message?:"GPS konumu alınamadı.")}
            }catch(e:CancellationException){throw e}
        }
    }
    fun permissionDenied(){
        gpsJob?.cancel()
        state.update{it.copy(locatingGps=false,originFromGps=false,gpsOrigin=null,error="Konum izni verilmedi. Başlangıç adresi de yazabilirsin.")}
    }
    fun plan(){
        if(!state.value.canCalculate) return
        planJob?.cancel()
        planJob=viewModelScope.launch{
            val before=state.value
            val target=before.destinationSelected
            val start=before.startLocation
            if(start==null||target==null){state.update{it.copy(error="Kalkış ve varış önerilerinden seçim yap.")};return@launch}
            state.update{it.copy(planning=true,error=null)}
            try{routes.calculate(start,target)
                state.update{it.copy(planning=false,error=null)}
            }catch(e:CancellationException){throw e}
            catch(e:Exception){state.update{it.copy(planning=false,
                error="Rota oluşturulamadı: "+(e.message?.take(130)?:"ağ hatası"))}}
        }
    }
    fun chooseAlternative(index:Int)=routes.choose(index)
    fun clear(){originJob?.cancel();destinationJob?.cancel();planJob?.cancel();gpsJob?.cancel();routes.clear();state.value=RoutePlannerUiState()}
}

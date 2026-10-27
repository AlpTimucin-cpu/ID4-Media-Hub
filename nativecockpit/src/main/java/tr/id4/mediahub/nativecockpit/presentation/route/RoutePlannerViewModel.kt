package tr.id4.mediahub.nativecockpit.presentation.route

import androidx.lifecycle.ViewModel
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
    val originMatches:List<RoutePlace> = emptyList(),
    val destinationMatches:List<RoutePlace> = emptyList(),
    val options:List<RouteOption> = emptyList(),val selectedIndex:Int?=null,
    val searchingOrigin:Boolean=false,val searchingDestination:Boolean=false,
    val planning:Boolean=false,val error:String?=null
)

@HiltViewModel
class RoutePlannerViewModel @Inject constructor(
    private val routes:RouteRepository,
    private val gps:LocationRepository
):ViewModel(){
    private val state=MutableStateFlow(RoutePlannerUiState())
    val uiState:StateFlow<RoutePlannerUiState> = state.asStateFlow()
    private var originJob:Job?=null
    private var destinationJob:Job?=null
    private var planJob:Job?=null
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
        state.update { it.copy(originText=text,originSelected=null,originFromGps=false,originMatches=emptyList(),error=null) }
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
            state.update{if(isOrigin)it.copy(originMatches=results,searchingOrigin=false)
                else it.copy(destinationMatches=results,searchingDestination=false)}
        }catch(e:CancellationException){throw e}
        catch(_:Exception){state.update{if(isOrigin)it.copy(searchingOrigin=false,error="Adres araması başarısız")
            else it.copy(searchingDestination=false,error="Adres araması başarısız")}}
    }
    fun chooseOrigin(item:RoutePlace){originJob?.cancel();state.update{it.copy(originSelected=item,originText=item.label,originFromGps=false,originMatches=emptyList(),error=null)}}
    fun chooseDestination(item:RoutePlace){destinationJob?.cancel();state.update{it.copy(destinationSelected=item,destinationText=item.label,destinationMatches=emptyList(),error=null)}}
    fun useCurrentGps(){
        val fix=gps.gpsFix.value?.takeIf { it.usable(System.currentTimeMillis()) && it.accuracyMeters<=35f }
        if(fix==null){state.update{it.copy(error="Güncel ve hassas GPS yok. Önce GPS iznini aç.")};return}
        originJob?.cancel();state.update{it.copy(originFromGps=true,originSelected=null,originText="Güncel GPS konumum",originMatches=emptyList(),error=null)}
    }
    fun plan(){
        planJob?.cancel()
        planJob=viewModelScope.launch{
            val before=state.value
            val target=before.destinationSelected
            val start=if(before.originFromGps){
                gps.gpsFix.value?.takeIf{it.usable(System.currentTimeMillis())&&it.accuracyMeters<=35f}?.coordinate
            }else before.originSelected?.coordinate
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
    fun clear(){originJob?.cancel();destinationJob?.cancel();planJob?.cancel();routes.clear();state.value=RoutePlannerUiState()}
}

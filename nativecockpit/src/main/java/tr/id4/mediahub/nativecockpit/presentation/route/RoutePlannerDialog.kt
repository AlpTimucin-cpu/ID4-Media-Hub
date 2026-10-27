package tr.id4.mediahub.nativecockpit.presentation.route

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import tr.id4.mediahub.nativecockpit.domain.model.RoutePlace

/** Places require explicit suggestion selection; no manual coordinate guesses. */
@Composable
fun RoutePlannerDialog(
    state:RoutePlannerUiState,
    onDismiss:()->Unit,
    onOriginChange:(String)->Unit,
    onDestinationChange:(String)->Unit,
    onOriginSelected:(RoutePlace)->Unit,
    onDestinationSelected:(RoutePlace)->Unit,
    onGps:()->Unit,
    onPlan:()->Unit,
    onChooseAlternative:(Int)->Unit,
    onClear:()->Unit
){
    Dialog(onDismissRequest=onDismiss,
        properties=DialogProperties(usePlatformDefaultWidth=false)){
        BoxWithConstraints(Modifier.fillMaxWidth(.94f).fillMaxHeight(.92f)){
            Surface(
                color=Color(0xF40A1728),
                shape=RoundedCornerShape(20.dp),
                modifier=Modifier.fillMaxWidth().heightIn(max=maxHeight)
            ) {
                Column(
                    modifier=Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement=Arrangement.spacedBy(10.dp)
                ) {
                    Text("ROTA OLUŞTUR",style=MaterialTheme.typography.titleMedium,color=Color.White)
                    Text("Photon / OSM adresleri · OSRM gerçek rota · canlı trafik değil",
                        style=MaterialTheme.typography.bodySmall,color=Color(0xFFABC5D5))
                    OutlinedTextField(
                        value=state.originText,onValueChange=onOriginChange,
                        label={Text("Nereden")},singleLine=true,
                        modifier=Modifier.fillMaxWidth()
                    )
                    TextButton(onClick=onGps,modifier=Modifier.heightIn(min=48.dp)){
                        Text("Güncel GPS konumumdan başlat")
                    }
                    if(state.searchingOrigin)LinearProgressIndicator(Modifier.fillMaxWidth())
                    Suggestions(state.originMatches,onOriginSelected)
                    OutlinedTextField(
                        value=state.destinationText,onValueChange=onDestinationChange,
                        label={Text("Nereye")},singleLine=true,
                        modifier=Modifier.fillMaxWidth()
                    )
                    if(state.searchingDestination)LinearProgressIndicator(Modifier.fillMaxWidth())
                    Suggestions(state.destinationMatches,onDestinationSelected)
                    state.error?.let{
                        Text(it,color=Color(0xFFFFB17C),style=MaterialTheme.typography.bodySmall)
                    }
                    if(state.planning)LinearProgressIndicator(Modifier.fillMaxWidth())
                    Button(
                        onClick=onPlan,
                        enabled=!state.planning &&
                            (state.originFromGps || state.originSelected!=null) &&
                            state.destinationSelected!=null,
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                    ){ Text("Gerçek yol rotasını hesapla") }
                    if(state.options.isNotEmpty()){
                        HorizontalDivider()
                        Text("OSRM alternatifleri (${state.options.size} doğrulanmış)",color=Color.White)
                        state.options.forEachIndexed { index,item ->
                            val selected=state.selectedIndex==index
                            OutlinedButton(onClick={onChooseAlternative(index)},
                                modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                                Text("${if(selected)"✓ " else ""}Rota ${index+1} · " +
                                    "%.1f km · %d dk".format(item.distanceKm,item.durationMinutesWithoutTraffic))
                            }
                        }
                        Text("Tahminler trafiksizdir. GPS ilerlemesi olmadan varış SoC hesaplanmaz.",
                            style=MaterialTheme.typography.bodySmall,color=Color(0xFFABC5D5))
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                        verticalAlignment=Alignment.CenterVertically) {
                        TextButton(onClick=onClear,modifier=Modifier.heightIn(min=48.dp)){
                            Text("Rotayı temizle")
                        }
                        TextButton(onClick=onDismiss,modifier=Modifier.heightIn(min=48.dp)){
                            Text("Haritaya dön")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Suggestions(items:List<RoutePlace>,onSelect:(RoutePlace)->Unit){
    items.forEach { place ->
        Surface(color=Color(0xFF19374B),shape=RoundedCornerShape(10.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp)
                .clickable { onSelect(place) }
                .padding(horizontal=12.dp,vertical=10.dp),
                verticalAlignment=Alignment.CenterVertically){
                Text(place.label,style=MaterialTheme.typography.bodySmall,color=Color.White)
            }
        }
    }
}

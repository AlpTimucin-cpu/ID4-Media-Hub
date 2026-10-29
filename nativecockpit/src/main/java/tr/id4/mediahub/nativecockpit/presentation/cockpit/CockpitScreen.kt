package tr.id4.mediahub.nativecockpit.presentation.cockpit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tr.id4.mediahub.nativecockpit.BuildConfig
import tr.id4.mediahub.nativecockpit.presentation.map.MapLibreCockpit
import tr.id4.mediahub.nativecockpit.domain.model.RouteSnapshot
import tr.id4.mediahub.nativecockpit.domain.model.RouteWeatherState
import tr.id4.mediahub.nativecockpit.domain.model.ChargingStation
import tr.id4.mediahub.nativecockpit.domain.model.StationFilter
import tr.id4.mediahub.nativecockpit.domain.model.GeoViewport
import tr.id4.mediahub.nativecockpit.presentation.charging.ChargingUiState
import tr.id4.mediahub.nativecockpit.presentation.charging.RouteChargingUiState
import tr.id4.mediahub.nativecockpit.domain.usecase.AutomaticChargePlan
import tr.id4.mediahub.nativecockpit.presentation.voice.NativeVoiceGuide
import tr.id4.mediahub.nativecockpit.presentation.route.RoutePlannerUiState
import tr.id4.mediahub.nativecockpit.presentation.route.RoutePlannerDialog
import tr.id4.mediahub.nativecockpit.presentation.navigation.NavigationRouteState
import tr.id4.mediahub.nativecockpit.presentation.navigation.RouteLoadStatus
import tr.id4.mediahub.nativecockpit.domain.model.RoutePlace
import tr.id4.mediahub.nativecockpit.domain.model.ObdConnectionState
import tr.id4.mediahub.nativecockpit.presentation.obd.ObdSettingsDialog
import tr.id4.mediahub.nativecockpit.presentation.media.ParkedStreamingDialog
import tr.id4.mediahub.nativecockpit.presentation.voice.TtsPackageWarningDialog

private val CardShape = RoundedCornerShape(22.dp)

/** Overlay on one real MapLibre map. Never displays fabricated GPS or battery values. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CockpitScreen(
    state: CockpitUiState,
    routeState: NavigationRouteState,
    loadStatus: RouteLoadStatus,
    obdConnection: ObdConnectionState,
    onObdRefresh: () -> Unit,
    onObdConnect: (String) -> Unit,
    onObdDisconnect: () -> Unit,
    onObdMebDiagnostic: (String) -> Unit,
    onObdClearDiagnostic: () -> Unit,
    routePlannerUi: RoutePlannerUiState,
    onRouteOriginChange:(String)->Unit,
    onRouteDestinationChange:(String)->Unit,
    onRouteOriginSelected:(RoutePlace)->Unit,
    onRouteDestinationSelected:(RoutePlace)->Unit,
    onRouteFromGps:()->Unit,
    onRoutePlan:()->Unit,
    onRouteAlternative:(Int)->Unit,
    onRouteClear:()->Unit,
    onGpsClick: () -> Unit,
    gpsError: String? = null,
    chargingUi: ChargingUiState,
    automaticChargePlan: RouteChargingUiState,
    weatherUi: RouteWeatherState,
    onStationFilter: (StationFilter) -> Unit,
    onMapViewport: (GeoViewport) -> Unit,
    onRefreshStations: () -> Unit,
    voiceEnabled: Boolean,
    onVoiceToggle: () -> Unit,
    voiceVolume: Float,
    onVoiceVolumeChange: (Float) -> Unit,
    voiceStatus: NativeVoiceGuide.Status,
    voiceError: String?,
    onVoiceTest: () -> Unit,
    onSimulationToggle: (Boolean) -> Unit
) {
    var selectedDock by remember { mutableStateOf<DockTab?>(null) }
    var showObdSettings by remember { mutableStateOf(false) }
    if (showObdSettings) ObdSettingsDialog(
        state = obdConnection,
        onRefresh = onObdRefresh,
        onConnect = onObdConnect,
        onDisconnect = onObdDisconnect,
        onMebDiagnostic = onObdMebDiagnostic,
        onClearDiagnostic = onObdClearDiagnostic,
        simulationSupported = BuildConfig.DEBUG,
        simulationEnabled = state.simulationEnabled,
        onSimulationToggle = onSimulationToggle,
        onDismiss = {showObdSettings = false}
    )
    var showRoutePlanner by remember { mutableStateOf(false) }
    if(showRoutePlanner) RoutePlannerDialog(
        state=routePlannerUi,onDismiss={showRoutePlanner=false},
        onOriginChange=onRouteOriginChange,onDestinationChange=onRouteDestinationChange,
        onOriginSelected=onRouteOriginSelected,onDestinationSelected=onRouteDestinationSelected,
        onGps=onRouteFromGps,onPlan=onRoutePlan,
        onChooseAlternative=onRouteAlternative,onClear=onRouteClear
    )
    var selectedStation by remember { mutableStateOf<ChargingStation?>(null) }
    var showCorridor by remember { mutableStateOf(false) }
    if (showCorridor) {
        ModalBottomSheet(
            onDismissRequest = { showCorridor = false },
            containerColor = Color(0xFF0A1728),
            contentColor = Color.White
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = 450.dp)
                .verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text("Rota Üzeri DC Şarj İstasyonları",
                    style = MaterialTheme.typography.titleLarge)
                chargingUi.corridorStations.forEach { station ->
                    TextButton(onClick = {
                        showCorridor = false
                        selectedStation = station
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(station.name + " · " + station.operator,
                            color = Color.White, maxLines = 2)
                    }
                }
                if (chargingUi.corridorStations.isEmpty()) {
                    Text("Rota çizgisine 5 km içinde doğrulanmış DC kaydı yok.")
                }
            }
        }
    }
    var showVoiceSettings by remember { mutableStateOf(false) }
    var showTtsMissing by remember { mutableStateOf(false) }
    LaunchedEffect(voiceStatus) {
        if (voiceStatus == NativeVoiceGuide.Status.TURKISH_VOICE_MISSING) {
            showTtsMissing = true
        }
    }
    if (showTtsMissing) {
        TtsPackageWarningDialog(onDismiss = { showTtsMissing = false })
    }
    if (showVoiceSettings) {
        DarkGlassDialog(
            onDismissRequest = { showVoiceSettings = false },
            title = { Text("Sesli yol tarifi") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Durum: " + when(voiceStatus) {
                        NativeVoiceGuide.Status.LOADING -> "Türkçe ses hazırlanıyor"
                        NativeVoiceGuide.Status.READY -> "Türkçe ses hazır"
                        NativeVoiceGuide.Status.TURKISH_VOICE_MISSING -> "Türkçe TTS paketi eksik"
                        NativeVoiceGuide.Status.INIT_FAILED -> "Ses motoru başlatılamadı"
                        NativeVoiceGuide.Status.AUDIO_FOCUS_DENIED -> "Ses odağı alınamadı"
                    })
                    Text("Ses seviyesi: %${(voiceVolume * 100).toInt()} · Uygulama sesi")
                    Slider(
                        value=voiceVolume,
                        onValueChange=onVoiceVolumeChange,
                        valueRange=.15f..1f,
                        enabled=voiceStatus==NativeVoiceGuide.Status.READY ||
                            voiceStatus==NativeVoiceGuide.Status.AUDIO_FOCUS_DENIED
                    )
                    Text("Sesli yönlendirme ${if(voiceEnabled) "açık" else "kapalı"}. " +
                         "Yalnız güncel GPS ve gerçek rota manevrası varsa konuşur.",fontSize=12.sp)
                    OutlinedButton(
                        onClick=onVoiceTest,
                        enabled=voiceEnabled && (voiceStatus==NativeVoiceGuide.Status.READY ||
                            voiceStatus==NativeVoiceGuide.Status.AUDIO_FOCUS_DENIED),
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                    ) { Text("Türkçe ses testi") }
                }
            },
            confirmButton = {
                TextButton(onClick={showVoiceSettings=false}) { Text("Tamam") }
            }
        )
    }
    selectedStation?.let { station ->
        // Existing station detail, now presented from the bottom.
        ModalBottomSheet(
            onDismissRequest = { selectedStation = null },
            containerColor = Color(0xFF0A1728),
            contentColor = Color.White
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max=420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal=20.dp, vertical=14.dp)) {
                Text(station.name.ifBlank { "Şarj istasyonu" },
                    style=MaterialTheme.typography.titleLarge)
                Text(station.operator.ifBlank { "Operatör belirtilmedi" })
                Text(station.address.ifBlank { "Adres belirtilmedi" })
                HorizontalDivider(Modifier.padding(vertical=9.dp))
                station.sockets.forEach { socket ->
                    Text("${socket.kind} · ${socket.type} · güç (ham): ${socket.powerRaw.ifBlank { "—" }}",
                        fontSize=12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("EPDK kayıt tarihi: ${station.sourceVerifiedAtUtc}", fontSize=11.sp)
                Text("Canlı müsaitlik, fiyat ve araçla erişim bilinmiyor.", fontSize=11.sp)
                TextButton(onClick={ selectedStation = null }) { Text("Kapat") }
            }
        }
    }

    // External streaming launches are off until a trusted Park signal exists.
    // GPS speed zero does not prove Park (traffic lights, poor GPS, etc.).
    if (selectedDock == DockTab.MEDIA) {
        ParkedStreamingDialog(
            verifiedParked = false,
            onDismiss = { selectedDock = null }
        )
    }
    // No fabricated control wiring: the currently absent modules say so.
    selectedDock?.takeUnless { it == DockTab.MEDIA }?.let { tab ->
        DarkGlassDialog(
            onDismissRequest = { selectedDock = null },
            title = { Text(tab.title) },
            text = {
                Text(when (tab) {
                    DockTab.ROUTE -> "Rota girişi harita üzerinden açılıyor."
                    DockTab.CHARGING -> "Kaynak EPDK · ${chargingUi.total} halka açık kayıt. " +
                        (chargingUi.verifiedAtUtc?.let { "Snapshot: $it. " } ?: "Veri bekleniyor. ") +
                        "Canlı doluluk ve fiyat yok. Rota filtresi yalnız gerçek OSRM rotasıyla çalışır."
                    DockTab.MEDIA -> "Medya bu sürümde devre dışı. Rota ve harita çalışmaya devam ediyor."
                    DockTab.SETTINGS -> "GPS ve OBD bağlantı ayarları henüz bu Kotlin uygulamasına bağlanmadı."
                })
            },
            confirmButton = {
                TextButton(onClick = { selectedDock = null }) { Text("Kapat") }
            }
        )
    }

    val visibleRoute = state.route ?: routeState.routeResult
    val snackbarHost = remember { SnackbarHostState() }
    val connectionError = (loadStatus as? RouteLoadStatus.Error)?.message
        ?: voiceError
        ?: chargingUi.error
        ?: weatherUi.error?.takeIf { weatherUi.routeId == visibleRoute?.routeId }
    LaunchedEffect(connectionError) {
        if (!connectionError.isNullOrBlank()) {
            // Show a non-blocking error without replacing existing glass panels.
            snackbarHost.showSnackbar(
                message = connectionError.take(130),
                duration = SnackbarDuration.Short
            )
        }
    }
    Column(Modifier.fillMaxSize().background(Ink)) {
        Header(
            voiceEnabled=voiceEnabled,
            onVoiceToggle=onVoiceToggle,
            onVoiceSettings={showVoiceSettings=true},
            voiceStatus=voiceStatus
        )
        if (state.simulationEnabled) {
            Text("SİMÜLASYON MODU · TEST VERİLERİ · GERÇEK KONUM/OBD DEĞİL",
                color=Color(0xFFFFCE73), fontSize=11.sp,
                modifier=Modifier.fillMaxWidth().background(Color(0xFF563215))
                    .padding(horizontal=12.dp, vertical=6.dp))
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 600.dp && maxWidth > maxHeight * 1.1f
            val compactLandscape = maxHeight < 420.dp
            MapLibreCockpit(
                gpsFix = state.gpsFix,
                routePoints = visibleRoute?.routePoints ?: emptyList(),
                stations = chargingUi.visible,
                plannedStopIds = (automaticChargePlan.plan as? AutomaticChargePlan.Estimate)
                    ?.stops?.map { it.station.id }?.toSet().orEmpty(),
                weatherPoints = weatherUi.points.takeIf {
                    weatherUi.routeId == visibleRoute?.routeId
                }.orEmpty(),
                speedProfile = visibleRoute?.profileSpeeds.orEmpty(),
                onViewportChanged = onMapViewport,
                onStationTapped = { selectedStation=it },
                modifier = Modifier.fillMaxSize()
            )
            Text(
                (loadStatus as? RouteLoadStatus.Error)?.message
                    ?: if (state.simulationEnabled) "TEST GPS · rota üzerinde simüle"
                    else if (state.gpsFix != null) "GPS güncel · araç işareti gerçek ölçüm"
                    else state.gpsWarning ?: gpsError ?: "GPS ölçümü bekleniyor · konum izni için dokun",
                color = Color.White, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xDE0B2639))
                    .clickable(enabled=!state.simulationEnabled) { onGpsClick() }
                    .padding(horizontal = 15.dp, vertical = 15.dp)
                    .semantics { contentDescription = "GPS konum iznini ve takibini aç" }
            )

            if (wide) {
                // Panels float above the ONE MapLibre native map.
                GlassSurface(
                    modifier = Modifier.align(Alignment.TopStart)
                        .padding(start = 13.dp, top = 12.dp)
                        .width(maxWidth * .285f)
                        .heightIn(max = maxHeight - 24.dp)
                        .verticalScroll(rememberScrollState())
                ) { VehiclePanel(state, compact = compactLandscape) }

                GlassSurface(
                    modifier = Modifier.align(Alignment.TopEnd)
                        .padding(end = 13.dp, top = 12.dp)
                        .width(maxWidth * .31f)
                        .heightIn(max = maxHeight * (if (maxHeight < 410.dp) .52f else .65f))
                        .verticalScroll(rememberScrollState())
                ) { RoutePanel(visibleRoute, routeState, state, loadStatus,
                    chargingUi, weatherUi, automaticChargePlan, { showCorridor = true }) }

                GlassSurface(
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(end = 13.dp, bottom = 12.dp)
                        .width(maxWidth * .65f)
                ) { RouteProgress(visibleRoute, state.simulationEnabled) }
            } else {
                // Portrait/small-screen: cards scroll; dock remains reachable.
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassSurface(Modifier.fillMaxWidth()) { VehiclePanel(state, compact = true) }
                    GlassSurface(Modifier.fillMaxWidth()) { RoutePanel(visibleRoute, routeState, state, loadStatus,
                    chargingUi, weatherUi, automaticChargePlan, { showCorridor = true }) }
                    GlassSurface(Modifier.fillMaxWidth()) { RouteProgress(visibleRoute, state.simulationEnabled) }
                }
            }
            SnackbarHost(hostState = snackbarHost,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom=68.dp))
            ChargingFilters(
                ui=chargingUi,
                onFilter=onStationFilter,
                onRefresh=onRefreshStations,
                modifier=Modifier.align(Alignment.BottomStart)
                    .padding(start=13.dp,bottom=12.dp)
            )
        }
        Dock(onTap = { tab ->
            if (tab == DockTab.ROUTE) showRoutePlanner = true
            else if (tab == DockTab.SETTINGS) {showObdSettings = true;onObdRefresh()}
            else selectedDock = tab
        })
    }
}

@Composable
private fun ChargingFilters(ui: ChargingUiState,
    onFilter: (StationFilter) -> Unit,onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.clip(RoundedCornerShape(12.dp))
        .background(Color(0xE9091D30)).padding(8.dp),
        verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(if(ui.loading) "EPDK yükleniyor…"
            else if(ui.total==0) "EPDK verisi yok"
            else "EPDK ${ui.visible.size} haritada · toplam ${ui.total}" +
                if(ui.fromCache) " · önbellek" else "",
            color=Color.White,fontSize=10.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            StationFilter.entries.forEach { option ->
                val title=when(option){
                    StationFilter.ALL -> "Tümü"
                    StationFilter.DC -> "DC"
                    StationFilter.ROUTE_CCS -> "Rota CCS"
                    StationFilter.ROUTE_DC -> "Rota DC"
                }
                TextButton(onClick={ {onFilter(option)} },
                    modifier=Modifier.heightIn(min=48.dp),
                    enabled=(option != StationFilter.ROUTE_CCS && option != StationFilter.ROUTE_DC) || ui.routeProvided) {
                    Text(title,color=if(option==ui.selectedFilter) NeonGreen else Color.White,
                        fontSize=11.sp)
                }
            }
            TextButton(onClick=onRefresh,modifier=Modifier.heightIn(min=48.dp)) {
                Text("Yenile",color=Turquoise,fontSize=11.sp)
            }
        }
        ui.error?.let { Text(it,color=Color(0xFFFFB86C),fontSize=10.sp) }
        if(ui.selectedFilter==StationFilter.ROUTE_CCS && !ui.routeProvided)
            Text("Önce gerçek rota oluştur",color=Color.White,fontSize=10.sp)
        Text(ui.verifiedAtUtc?.let { "Kaynak doğrulama: $it" } ?: "Kaynak tarihi bilinmiyor",
            color=SoftText,fontSize=9.sp)
        Text("Turkuaz halka: rotaya <5 km DC (sürüş sapması değil)",color=SoftText,fontSize=9.sp)
    }
}

@Composable
private fun Header(
    voiceEnabled: Boolean,
    onVoiceToggle: () -> Unit,
    onVoiceSettings: () -> Unit,
    voiceStatus: NativeVoiceGuide.Status
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min=64.dp)
            .padding(horizontal=14.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("ELEKTRİKLİ YOL ARKADAŞIM", color=Color.White,
                fontWeight=FontWeight.Bold,fontSize=14.sp,
                maxLines=1,overflow=TextOverflow.Ellipsis)
            Text("MAPLIBRE · OSRM · SESLİ ROTA · ADIM 7", color=SoftText,fontSize=10.sp,
                maxLines=1)
        }
        // Large and accessible one-tap mute: works immediately.
        IconButton(
            onClick=onVoiceToggle,
            modifier=Modifier.size(50.dp).semantics {
                contentDescription=if(voiceEnabled) "Sesli yönlendirmeyi kapat"
                                   else "Sesli yönlendirmeyi aç"
            }
        ) {
            Icon(if(voiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                contentDescription=null,
                tint=if(voiceEnabled && voiceStatus==NativeVoiceGuide.Status.READY)
                    NeonGreen else SoftText)
        }
        TextButton(onClick=onVoiceSettings,modifier=Modifier.heightIn(min=48.dp)) {
            Text("SES",color=Turquoise,fontSize=12.sp)
        }
    }
}

@Composable
private fun GlassSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.shadow(11.dp, CardShape).clip(CardShape)
            .background(Brush.verticalGradient(listOf(Color(0xEB102638), Color(0xE60B1728))))
            .border(1.dp, GlassLine, CardShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content
    )
}

@Composable
private fun VehiclePanel(state: CockpitUiState, compact: Boolean) {
    Text(if(state.simulationEnabled) "BATARYA DOLULUĞU · TEST" else "BATARYA DOLULUĞU · OBD", color = SoftText,
        fontSize = 11.sp, fontWeight = FontWeight.Medium)
    Text(CockpitFormat.percent(state.socPercent), fontSize = if (compact) 40.sp else 59.sp,
        lineHeight = if (compact) 44.sp else 63.sp,
        fontWeight = FontWeight.Bold, color = NeonGreen)
    Spacer(Modifier.height(5.dp))
    BatteryProgress(state.socPercent)
    Spacer(Modifier.height(12.dp))
    Text("TAHMİNİ MENZİL · MODEL · %15 REZERV", color = SoftText, fontSize = 10.sp)
    Text(CockpitFormat.distance(state.estimatedRangeKm),
        color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
    HorizontalDivider(color = GlassLine, modifier = Modifier.padding(vertical = 12.dp))
    MetricLine("Hız · ölçüm", CockpitFormat.speed(state.speedKmh))
    MetricLine("HV gerilimi", CockpitFormat.voltage(state.voltageV))
    MetricLine("Batarya sıcaklığı", CockpitFormat.temperature(state.batteryTemperatureC))
    MetricLine("Anlık güç", CockpitFormat.power(state.powerKw))
    Spacer(Modifier.height(5.dp))
    Text(state.socSourceLabel ?: if(state.socPercent == null)
        "OBD SoC güncel değil / bağlı değil" else "OBD ölçümü · en çok 10 sn eski",
        fontSize = 10.sp, color = if (state.socPercent == null) Color(0xFFFFBD78) else NeonGreen)
}

@Composable
private fun BatteryProgress(soc: Double?) {
    val valid = soc?.takeIf { it.isFinite() && it in 0.0..100.0 }
    val target = (valid?.div(100.0)?.toFloat() ?: 0f)
    val progress by animateFloatAsState(targetValue = target, label = "Batarya seviyesi")
    Box(
        Modifier.fillMaxWidth().height(8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF2B4052))
            .semantics { contentDescription = valid?.let { "Batarya yüzde ${it.toInt()}" } ?: "Batarya bilinmiyor" }
    ) {
        // Absent measurement = empty track, NOT a measured 0%.
        if (valid != null) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(NeonGreen))
        }
    }
}

@Composable
private fun MetricLine(title: String, value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, fontSize = 11.sp, color = SoftText, maxLines = 1)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            color = Color.White, maxLines = 1)
    }
}

@Composable
private fun RoutePanel(
    route: RouteSnapshot?,
    selection: NavigationRouteState,
    model: CockpitUiState,
    loadStatus: RouteLoadStatus,
    charging: ChargingUiState,
    weather: RouteWeatherState,
    autoCharge: RouteChargingUiState,
    onRouteDcClick: () -> Unit
) {
    Text("NAVİGASYON", color = Turquoise, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(7.dp))
    if (loadStatus is RouteLoadStatus.GpsLost) {
        Text("GPS Sinyali Bekleniyor...", color=Color(0xFFFFBD78), fontSize=11.sp)
    }
    if (route == null || loadStatus is RouteLoadStatus.Loading ||
        loadStatus is RouteLoadStatus.Error) {
        Text(if (loadStatus is RouteLoadStatus.Loading) "Rota hesaplanıyor…" else "Rota bekleniyor",
            fontSize = 19.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
        val selected = selection.startLocation != null || selection.destination != null
        Text(if (selected) "Seçimler hazır; gerçek OSRM rotasını hesapla"
            else "Başlangıç ve varış henüz seçilmedi", color = SoftText, fontSize = 11.sp)
        (loadStatus as? RouteLoadStatus.Error)?.message?.let {
            Text(it.take(120), color = Color(0xFFFFB17C), fontSize = 11.sp)
        }
    } else {
        Text(route.nextTurnInstruction?.takeIf { it.isNotBlank() } ?: "Manevra bekleniyor",
            color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        val m = route.nextTurnMeters?.takeIf { it.isFinite() && it >= 0.0 }
        Text(m?.let { "%.0f m sonra".format(it) } ?: "—", color = Turquoise, fontSize = 12.sp)
    }
    HorizontalDivider(color = GlassLine, modifier = Modifier.padding(vertical = 11.dp))
    MetricLine("Başlangıç", selection.startLabel?.take(30) ?: "—")
    MetricLine("Hedef", route?.destination?.takeIf { it.isNotBlank() }?.take(30)
        ?: selection.destination?.label?.take(30) ?: "—")
    MetricLine("Rota toplamı · OSRM", CockpitFormat.distance(route?.totalKm))
    MetricLine("Kalan · GPS", CockpitFormat.distance(route?.remainingKm))
    MetricLine("Tahmini sürüş", CockpitFormat.minutes(route?.estimatedDriveMinutes))
    MetricLine("Tahmini varış saati", CockpitFormat.eta(route?.estimatedArrivalEpochMs))
    MetricLine("Varış SoC · MODEL/TAHMİN", CockpitFormat.percent(route?.estimatedArrivalSocPercent))
    model.socSourceLabel?.let { Text(it, color=SoftText, fontSize=10.sp) }
    MetricLine("Şarj ihtiyacı · MODEL/TAHMİN", when(model.chargingNeededEstimate) {
        true -> "Rezerv için şarj gerekiyor"
        false -> "Hesaba göre gerekmiyor"
        null -> "—"
    })
    MetricLine("Gerekli şarj · MODEL/TAHMİN",
        model.chargingEnergyKwh?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { "%.1f kWh".format(it) } ?: "—")
    if (route?.routeId != null && autoCharge.routeId == route.routeId) {
        when (val plan = autoCharge.plan) {
            is AutomaticChargePlan.Estimate -> {
                MetricLine("Otomatik DC adayları · MODEL/TAHMİN",
                    "${plan.stops.size} durak · ${"%.1f".format(plan.totalSuggestedChargeKwh)} kWh")
                plan.stops.take(4).forEach { stop ->
                    MetricLine(
                        "DC aday · ${"%.0f".format(stop.distanceFromStartKm)} km",
                        stop.station.name.take(26) + " · +" +
                            "%.1f".format(stop.suggestedEnergyKwh) + " kWh" +
                            (stop.verifiedPowerKw?.let { " · " + "%.0f".format(it) + " kW" } ?: "")
                    )
                }
                Text(plan.source, color = SoftText, fontSize = 10.sp)
                if (autoCharge.catalogIsStale) {
                    Text("EPDK kayıtları güncel değil", color = Color(0xFFFFBD78), fontSize = 10.sp)
                }
            }
            is AutomaticChargePlan.Unavailable ->
                Text("Otomatik şarj planı: ${plan.reason}",
                    color = SoftText, fontSize = 10.sp)
            AutomaticChargePlan.Empty -> Unit
        }
    }
    model.energyEstimateStatus?.let { message ->
        Text("MODEL/TAHMİN oluşturulamadı: $message", color = SoftText, fontSize = 10.sp)
    }
    TextButton(onClick = onRouteDcClick,
        enabled = charging.routeProvided,
        modifier = Modifier.fillMaxWidth()) {
        Text("Rota Üzeri: ${charging.corridorCount?.toString() ?: "—"} Adet DC İstasyonu",
            color = Turquoise, fontSize = 12.sp)
    }
    val destinationWeather = weather.destination?.takeIf {
        route?.routeId != null && weather.routeId == route.routeId
    }
    MetricLine("Varış bölgesi · Güncel hava tahmini", destinationWeather?.let {
        "${it.summaryTr} · ${it.temperatureC.toInt()}°C"
    } ?: if(weather.loading) "Hava tahmini yükleniyor…" else "—")
    MetricLine("Varış bölgesi · Rüzgâr", destinationWeather?.let {
        "${it.windSpeedKmh.toInt()} km/sa"
    } ?: "—")
    if (weather.points.isNotEmpty() && weather.routeId == route?.routeId) {
        Text("Hava: Open-Meteo · güncel model tahmini, varış saatine göre değil",
            color = SoftText, fontSize = 10.sp)
    }
    if (weather.error != null && route?.routeId == weather.routeId) {
        Text(weather.error, color = SoftText, fontSize = 10.sp)
    }
    if (!route?.profileSpeeds.isNullOrEmpty()) {
        Text("Kırmızı/yeşil çizgiler: OSRM hız profili · CANLI TRAFİK DEĞİL",
            color = SoftText, fontSize = 10.sp)
    }
    Text("OSRM ETA trafiksiz MODEL; şarj süresi ayrıca hesaplanır.",
        color = SoftText, fontSize = 10.sp,
        modifier = Modifier.padding(top = 7.dp))
}

@Composable
private fun RouteProgress(route: RouteSnapshot?, simulation: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.DirectionsCar, null, tint = Turquoise)
        Spacer(Modifier.width(9.dp))
        Text("ROTA İLERLEMESİ", color = Color.White,
            fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(route?.validProgress()?.let { "%${(it * 100).toInt()} · ${if(simulation) "SİM" else "GPS"}" }
             ?: "GPS ilerlemesi yok", color = SoftText, fontSize = 11.sp)
    }
    Spacer(Modifier.height(11.dp))
    BoxWithConstraints(Modifier.fillMaxWidth().height(32.dp)) {
        val progress = route?.validProgress()
        val value by animateFloatAsState(targetValue = progress ?: 0f, label = "GPS rota ilerlemesi")
        Box(Modifier.fillMaxWidth().height(6.dp).align(Alignment.CenterStart)
            .background(Color(0xFF355269), RoundedCornerShape(6.dp)))
        // Show the car only after real GPS position has been projected to route.
        if (progress != null) {
            Box(Modifier.fillMaxWidth(value).height(6.dp).align(Alignment.CenterStart)
                .background(Turquoise, RoundedCornerShape(6.dp)))
            Icon(Icons.Default.DirectionsCar, "Gerçek GPS rota ilerlemesi",
                tint = NeonGreen,
                modifier = Modifier.offset(x = (maxWidth - 28.dp) * value)
                    .align(Alignment.CenterStart).size(28.dp))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Kalkış", color = SoftText, fontSize = 10.sp)
        Text("Varış · ${CockpitFormat.percent(route?.estimatedArrivalSocPercent)} MODEL",
            color = NeonGreen, fontSize = 10.sp)
    }
}

private enum class DockTab(val title: String, val icon: ImageVector) {
    ROUTE("Rota", Icons.Default.Map),
    CHARGING("Şarj", Icons.Default.Bolt),
    MEDIA("Medya", Icons.Default.SmartDisplay),
    SETTINGS("Ayarlar", Icons.Default.Settings)
}

@Composable
private fun Dock(onTap: (DockTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .background(Color(0xFA0B1828))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 15.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DockTab.entries.forEach { tab ->
            Row(
                Modifier.heightIn(min = 52.dp).widthIn(min = 112.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .clickable { onTap(tab) }
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(tab.icon, null, tint = if (tab == DockTab.ROUTE) NeonGreen else SoftText,
                    modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(tab.title, color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

/** Keep the existing dialog contents; change only the white Material container. */
@Composable
private fun DarkGlassDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            color = Color(0xFF0A0F1A),
            contentColor = Color.White,
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, Color(0xFF40576E)
            ),
            shadowElevation = 15.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    title()
                    text()
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End) { confirmButton() }
                }
            }
        }
    }
}

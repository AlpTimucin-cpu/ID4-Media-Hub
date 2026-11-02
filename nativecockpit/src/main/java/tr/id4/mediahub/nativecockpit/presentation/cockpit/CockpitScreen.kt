package tr.id4.mediahub.nativecockpit.presentation.cockpit

import androidx.compose.ui.res.stringResource
import tr.id4.mediahub.nativecockpit.R

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
import tr.id4.mediahub.nativecockpit.presentation.media.rememberPhoneMediaAllowed
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
    var showStationFilters by remember { mutableStateOf(false) }
    var compactDetails by remember { mutableStateOf<String?>(null) }
    if (showCorridor) {
        ModalBottomSheet(
            onDismissRequest = { showCorridor = false },
            containerColor = Color(0xFF0A1728),
            contentColor = Color.White
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = 450.dp)
                .verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(stringResource(R.string.native_dc_charging_stations_along_the_route),
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
                    Text(stringResource(R.string.native_no_validated_dc_station_within_5_km_of_the_route_line))
                }
            }
        }
    }
    if (showStationFilters) {
        ModalBottomSheet(
            onDismissRequest = { showStationFilters = false },
            containerColor = Color(0xFF0A1728),
            contentColor = Color.White
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(stringResource(R.string.native_epdk_station_filters), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ChargingFilters(
                    ui = chargingUi,
                    onFilter = onStationFilter,
                    onRefresh = onRefreshStations,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    if (compactDetails != null) {
        ModalBottomSheet(
            onDismissRequest = { compactDetails = null },
            containerColor = Color(0xFF0A1728),
            contentColor = Color.White
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()).padding(16.dp)
            ) {
                when (compactDetails) {
                    "VEHICLE" -> VehiclePanel(state, compact = true)
                    "ROUTE" -> RoutePanel(
                        state.route ?: routeState.routeResult,
                        routeState, state, loadStatus, chargingUi, weatherUi,
                        automaticChargePlan,
                        { compactDetails = null; showCorridor = true }
                    )
                    "PROGRESS" -> RouteProgress(
                        state.route ?: routeState.routeResult, state.simulationEnabled
                    )
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
            title = { Text(stringResource(R.string.native_voice_guidance)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.native_status) + when(voiceStatus) {
                        NativeVoiceGuide.Status.LOADING -> stringResource(R.string.native_preparing_english_speech)
                        NativeVoiceGuide.Status.READY -> stringResource(R.string.native_english_speech_ready)
                        NativeVoiceGuide.Status.TURKISH_VOICE_MISSING -> stringResource(R.string.native_english_tts_data_missing_90a54a)
                        NativeVoiceGuide.Status.INIT_FAILED -> stringResource(R.string.native_could_not_initialize_the_speech_engine)
                        NativeVoiceGuide.Status.AUDIO_FOCUS_DENIED -> stringResource(R.string.native_could_not_obtain_audio_focus)
                    })
                    Text("Volume: ${(voiceVolume * 100).toInt()}% · App audio")
                    Slider(
                        value=voiceVolume,
                        onValueChange=onVoiceVolumeChange,
                        valueRange=.15f..1f,
                        enabled=voiceStatus==NativeVoiceGuide.Status.READY ||
                            voiceStatus==NativeVoiceGuide.Status.AUDIO_FOCUS_DENIED
                    )
                    Text("Voice guidance ${if(voiceEnabled) "on" else "off"}. " +
                         "Speaks only with current GPS and an actual route maneuver.",fontSize=12.sp)
                    OutlinedButton(
                        onClick=onVoiceTest,
                        enabled=voiceEnabled && (voiceStatus==NativeVoiceGuide.Status.READY ||
                            voiceStatus==NativeVoiceGuide.Status.AUDIO_FOCUS_DENIED),
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                    ) { Text(stringResource(R.string.native_english_speech_test)) }
                }
            },
            confirmButton = {
                TextButton(onClick={showVoiceSettings=false}) { Text(stringResource(R.string.native_ok)) }
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
                Text(station.name.ifBlank { "Charging station" },
                    style=MaterialTheme.typography.titleLarge)
                Text(station.operator.ifBlank { "Operator not specified" })
                Text(station.address.ifBlank { "Address not specified" })
                HorizontalDivider(Modifier.padding(vertical=9.dp))
                station.sockets.forEach { socket ->
                    Text("${socket.kind} · ${socket.type} · power (raw): ${socket.powerRaw.ifBlank { "—" }}",
                        fontSize=12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("EPDK record date: ${station.sourceVerifiedAtUtc}", fontSize=11.sp)
                Text(stringResource(R.string.native_live_availability_price_and_road_access_are_unknown), fontSize=11.sp)
                TextButton(onClick={ selectedStation = null }) { Text(stringResource(R.string.native_close)) }
            }
        }
    }

    val phoneMediaAllowed = rememberPhoneMediaAllowed()
    if (selectedDock == DockTab.MEDIA) {
        ParkedStreamingDialog(
            playbackAllowed = phoneMediaAllowed,
            onDismiss = { selectedDock = null }
        )
    }
    // No fabricated control wiring: the currently absent modules say so.
    selectedDock?.takeUnless { it == DockTab.MEDIA }?.let { tab ->
        DarkGlassDialog(
            onDismissRequest = { selectedDock = null },
            title = { Text(stringResource(tab.titleRes)) },
            text = {
                Text(when (tab) {
                    DockTab.ROUTE -> "Route entry opens from the map."
                    DockTab.CHARGING -> "EPDK source · ${chargingUi.total} public records. " +
                        (chargingUi.verifiedAtUtc?.let { "Snapshot: $it. " } ?: "Waiting for data. ") +
                        "No live availability or prices. The route filter requires an actual OSRM route."
                    DockTab.MEDIA -> "Media is disabled in this version. Routes and maps remain available."
                    DockTab.SETTINGS -> "GPS and OBD connection settings are not yet connected in this Kotlin app."
                })
            },
            confirmButton = {
                TextButton(onClick = { selectedDock = null }) { Text(stringResource(R.string.native_close)) }
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
    Column(Modifier.fillMaxSize().background(Ink)
        .windowInsetsPadding(WindowInsets.safeDrawing)) {
        Header(
            voiceEnabled=voiceEnabled,
            onVoiceToggle=onVoiceToggle,
            onVoiceSettings={showVoiceSettings=true},
            voiceStatus=voiceStatus
        )
        if (BuildConfig.DEBUG && state.simulationEnabled) {
            Text(stringResource(R.string.native_simulation_mode_test_data_not_actual_location_obd),
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
                    ?: if (state.simulationEnabled && state.gpsFix == null) "TEST GPS · waiting for a valid OSRM route"
                    else if (state.simulationEnabled) "TEST GPS · simulated along the route"
                    else if (state.gpsFix != null) "GPS current · vehicle marker uses an actual reading"
                    else state.gpsWarning ?: gpsError ?: "Waiting for GPS · tap to grant location permission",
                color = Color.White, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xDE0B2639))
                    .clickable(enabled=!state.simulationEnabled) { onGpsClick() }
                    .padding(horizontal = 15.dp, vertical = 15.dp)
                    .semantics { contentDescription = "Enable GPS permission and tracking" }
            )

            if (wide && compactLandscape) {
                // Short landscape: no overlapping floating panels or wide EPDK overlay.
                // The complete existing panels stay accessible through bottom sheets.
                GlassSurface(
                    Modifier.align(Alignment.TopStart)
                        .padding(start = 10.dp, top = 8.dp)
                        .width(maxWidth * .26f)
                        .clickable { compactDetails = "VEHICLE" }
                ) {
                    Text(stringResource(R.string.native_battery) + CockpitFormat.percent(state.socPercent),
                        color = NeonGreen, fontSize = 12.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.native_vehicle_information), color = SoftText, fontSize = 10.sp)
                }

                GlassSurface(
                    Modifier.align(Alignment.TopEnd)
                        .padding(end = 10.dp, top = 8.dp)
                        .width(maxWidth * .29f)
                        .clickable { compactDetails = "ROUTE" }
                ) {
                    Text(stringResource(R.string.native_navigation), color = Turquoise, fontSize = 10.sp)
                    Text(visibleRoute?.nextTurnInstruction
                        ?.takeIf { it.isNotBlank() } ?: "Route details",
                        color = Color.White, fontSize = 12.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }

                // Progress never shares the bottom-left area with the EPDK control.
                GlassSurface(
                    Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .width(maxWidth * .36f)
                        .clickable { compactDetails = "PROGRESS" }
                ) {
                    val progress = visibleRoute?.validProgress()
                        ?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
                    Text(
                        progress?.let { "ROUTE PROGRESS · ${(it * 100).toInt()}%" }
                            ?: "ROUTE PROGRESS · No GPS",
                        color = Color.White, fontSize = 10.sp, maxLines = 1
                    )
                    LinearProgressIndicator(
                        progress = { progress?.toFloat() ?: 0f },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = NeonGreen,
                        trackColor = GlassLine
                    )
                }

                TextButton(
                    onClick = { showStationFilters = true },
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(start = 10.dp, bottom = 5.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xEA091D30))
                ) {
                    Text(stringResource(R.string.native_epdk_filter), color = Turquoise, fontSize = 11.sp)
                }
            } else if (wide) {
                // Spacious landscape keeps the original glass cockpit arrangement.
                GlassSurface(
                    modifier = Modifier.align(Alignment.TopStart)
                        .padding(start = 13.dp, top = 12.dp)
                        .width(maxWidth * .285f)
                        .heightIn(max = maxHeight - 24.dp)
                        .verticalScroll(rememberScrollState())
                ) { VehiclePanel(state, compact = false) }

                GlassSurface(
                    modifier = Modifier.align(Alignment.TopEnd)
                        .padding(end = 13.dp, top = 12.dp)
                        .width(maxWidth * .31f)
                        .heightIn(max = maxHeight * .55f)
                        .verticalScroll(rememberScrollState())
                ) { RoutePanel(visibleRoute, routeState, state, loadStatus,
                    chargingUi, weatherUi, automaticChargePlan, { showCorridor = true }) }

                GlassSurface(
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(end = 13.dp, bottom = 12.dp)
                        .width(maxWidth * .58f)
                ) { RouteProgress(visibleRoute, state.simulationEnabled) }

                TextButton(
                    onClick = { showStationFilters = true },
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(start = 13.dp, bottom = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xEA091D30))
                ) {
                    Text(stringResource(R.string.native_epdk_filter), color = Turquoise)
                }
            } else {
                // Portrait: all cards AND charging filters live in ONE scroll container.
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassSurface(Modifier.fillMaxWidth()) {
                        VehiclePanel(state, compact = true)
                    }
                    GlassSurface(Modifier.fillMaxWidth()) {
                        RoutePanel(visibleRoute, routeState, state, loadStatus,
                            chargingUi, weatherUi, automaticChargePlan,
                            { showCorridor = true })
                    }
                    GlassSurface(Modifier.fillMaxWidth()) {
                        RouteProgress(visibleRoute, state.simulationEnabled)
                    }
                    ChargingFilters(
                        ui = chargingUi, onFilter = onStationFilter,
                        onRefresh = onRefreshStations,
                        modifier = Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    )
                }
            }
            SnackbarHost(
                hostState = snackbarHost,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = if (compactLandscape) 50.dp else 68.dp)
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
        Text(if(ui.loading) "Loading EPDK…"
            else if(ui.total==0) "No EPDK data"
            else "EPDK ${ui.visible.size} on map · ${ui.total} total" +
                if(ui.fromCache) " · cached" else "",
            color=Color.White,fontSize=10.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            StationFilter.entries.forEach { option ->
                val title=when(option){
                    StationFilter.ALL -> "All"
                    StationFilter.DC -> "DC"
                    StationFilter.ROUTE_CCS -> "Route CCS"
                    StationFilter.ROUTE_DC -> "Route DC"
                }
                TextButton(onClick={ onFilter(option) },
                    modifier=Modifier.heightIn(min=48.dp),
                    enabled=(option != StationFilter.ROUTE_CCS && option != StationFilter.ROUTE_DC) || ui.routeProvided) {
                    Text(title,color=if(option==ui.selectedFilter) NeonGreen else Color.White,
                        fontSize=11.sp)
                }
            }
            TextButton(onClick=onRefresh,modifier=Modifier.heightIn(min=48.dp)) {
                Text(stringResource(R.string.native_refresh),color=Turquoise,fontSize=11.sp)
            }
        }
        ui.error?.let { Text(it,color=Color(0xFFFFB86C),fontSize=10.sp) }
        if(ui.selectedFilter==StationFilter.ROUTE_CCS && !ui.routeProvided)
            Text(stringResource(R.string.native_plan_an_actual_route_first),color=Color.White,fontSize=10.sp)
        Text(ui.verifiedAtUtc?.let { "Source verification: $it" } ?: "Source date unknown",
            color=SoftText,fontSize=9.sp)
        Text(stringResource(R.string.native_turquoise_ring_dc_within_5_km_of_route_not_driving_detour),color=SoftText,fontSize=9.sp)
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
            Text(stringResource(R.string.native_electric_road_companion), color=Color.White,
                fontWeight=FontWeight.Bold,fontSize=14.sp,
                maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(stringResource(R.string.native_maplibre_osrm_voice_guidance_step_7), color=SoftText,fontSize=10.sp,
                maxLines=1)
        }
        // Large and accessible one-tap mute: works immediately.
        IconButton(
            onClick=onVoiceToggle,
            modifier=Modifier.size(50.dp).semantics {
                contentDescription=if(voiceEnabled) stringResource(R.string.native_mute_voice_guidance)
                                   else stringResource(R.string.native_unmute_voice_guidance)
            }
        ) {
            Icon(if(voiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                contentDescription=null,
                tint=if(voiceEnabled && voiceStatus==NativeVoiceGuide.Status.READY)
                    NeonGreen else SoftText)
        }
        TextButton(onClick=onVoiceSettings,modifier=Modifier.heightIn(min=48.dp)) {
            Text(stringResource(R.string.native_voice),color=Turquoise,fontSize=12.sp)
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
    Text(if(state.simulationEnabled) stringResource(R.string.native_battery_state_of_charge_test) else stringResource(R.string.native_battery_state_of_charge_obd), color = SoftText,
        fontSize = 11.sp, fontWeight = FontWeight.Medium)
    Text(CockpitFormat.percent(state.socPercent), fontSize = if (compact) 40.sp else 59.sp,
        lineHeight = if (compact) 44.sp else 63.sp,
        fontWeight = FontWeight.Bold, color = NeonGreen)
    Spacer(Modifier.height(5.dp))
    BatteryProgress(state.socPercent)
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.native_estimated_range_model_15_reserve), color = SoftText, fontSize = 10.sp)
    Text(CockpitFormat.distance(state.estimatedRangeKm),
        color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
    HorizontalDivider(color = GlassLine, modifier = Modifier.padding(vertical = 12.dp))
    MetricLine(stringResource(R.string.native_speed_measured), CockpitFormat.speed(state.speedKmh))
    MetricLine(stringResource(R.string.native_hv_voltage), CockpitFormat.voltage(state.voltageV))
    MetricLine(stringResource(R.string.native_battery_temperature), CockpitFormat.temperature(state.batteryTemperatureC))
    MetricLine(stringResource(R.string.native_instantaneous_power), CockpitFormat.power(state.powerKw))
    Spacer(Modifier.height(5.dp))
    Text(state.socSourceLabel ?: if(state.socPercent == null)
        stringResource(R.string.native_obd_soc_stale_disconnected) else stringResource(R.string.native_obd_reading_no_more_than_10_seconds_old),
        fontSize = 10.sp, color = if (state.socPercent == null) Color(0xFFFFBD78) else NeonGreen)
}

@Composable
private fun BatteryProgress(soc: Double?) {
    val valid = soc?.takeIf { it.isFinite() && it in 0.0..100.0 }
    val target = (valid?.div(100.0)?.toFloat() ?: 0f)
    val progress by animateFloatAsState(targetValue = target, label = "Battery level")
    Box(
        Modifier.fillMaxWidth().height(8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF2B4052))
            .semantics { contentDescription = valid?.let { "Battery at ${it.toInt()} percent" } ?: "Battery unknown" }
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
    Text(stringResource(R.string.native_navigation), color = Turquoise, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(7.dp))
    if (loadStatus is RouteLoadStatus.GpsLost) {
        Text(stringResource(R.string.native_waiting_for_gps_signal), color=Color(0xFFFFBD78), fontSize=11.sp)
    }
    if (route == null || loadStatus is RouteLoadStatus.Loading ||
        loadStatus is RouteLoadStatus.Error) {
        Text(if (loadStatus is RouteLoadStatus.Loading) stringResource(R.string.native_calculating_route) else stringResource(R.string.native_waiting_for_route),
            fontSize = 19.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
        val selected = selection.startLocation != null || selection.destination != null
        Text(if (selected) stringResource(R.string.native_locations_selected_calculate_the_actual_osrm_route)
            else stringResource(R.string.native_origin_and_destination_have_not_been_selected), color = SoftText, fontSize = 11.sp)
        (loadStatus as? RouteLoadStatus.Error)?.message?.let {
            Text(it.take(120), color = Color(0xFFFFB17C), fontSize = 11.sp)
        }
    } else {
        Text(route.nextTurnInstruction?.takeIf { it.isNotBlank() }
                ?: if (model.simulationEnabled) stringResource(R.string.native_test_waiting_for_osrm_maneuver)
                   else stringResource(R.string.native_waiting_for_maneuver),
            color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        val m = route.nextTurnMeters?.takeIf { it.isFinite() && it >= 0.0 }
        Text(m?.let { "In %.0f m".format(it) } ?: "—", color = Turquoise, fontSize = 12.sp)
    }
    HorizontalDivider(color = GlassLine, modifier = Modifier.padding(vertical = 11.dp))
    MetricLine(stringResource(R.string.native_origin), selection.startLabel?.take(30) ?: "—")
    MetricLine(stringResource(R.string.native_destination), route?.destination?.takeIf { it.isNotBlank() }?.take(30)
        ?: selection.destination?.label?.take(30) ?: "—")
    MetricLine(stringResource(R.string.native_total_route_osrm), CockpitFormat.distance(route?.totalKm))
    MetricLine(stringResource(R.string.native_remaining_gps), CockpitFormat.distance(route?.remainingKm))
    MetricLine(stringResource(R.string.native_estimated_drive_time), CockpitFormat.minutes(route?.estimatedDriveMinutes))
    MetricLine(stringResource(R.string.native_estimated_arrival_time), CockpitFormat.eta(route?.estimatedArrivalEpochMs))
    MetricLine(stringResource(R.string.native_arrival_soc_model_estimate), CockpitFormat.percent(route?.estimatedArrivalSocPercent))
    model.socSourceLabel?.let { Text(it, color=SoftText, fontSize=10.sp) }
    MetricLine(stringResource(R.string.native_charging_needed_model_estimate), when(model.chargingNeededEstimate) {
        true -> stringResource(R.string.native_charging_required_to_preserve_the_reserve)
        false -> stringResource(R.string.native_not_required_according_to_the_model)
        null -> "—"
    })
    MetricLine(stringResource(R.string.native_required_charge_model_estimate),
        model.chargingEnergyKwh?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { "%.1f kWh".format(it) } ?: "—")
    if (route?.routeId != null && autoCharge.routeId == route.routeId) {
        when (val plan = autoCharge.plan) {
            is AutomaticChargePlan.Estimate -> {
                MetricLine(stringResource(R.string.native_automatic_dc_candidates_model_estimate),
                    "${plan.stops.size} stops · ${"%.1f".format(plan.totalSuggestedChargeKwh)} kWh")
                plan.stops.take(4).forEach { stop ->
                    MetricLine(
                        "DC candidate · ${"%.0f".format(stop.distanceFromStartKm)} km",
                        stop.station.name.take(26) + " · +" +
                            "%.1f".format(stop.suggestedEnergyKwh) + " kWh" +
                            (stop.verifiedPowerKw?.let { " · " + "%.0f".format(it) + " kW" } ?: "")
                    )
                }
                Text(plan.source, color = SoftText, fontSize = 10.sp)
                if (autoCharge.catalogIsStale) {
                    Text(stringResource(R.string.native_epdk_records_are_stale), color = Color(0xFFFFBD78), fontSize = 10.sp)
                }
            }
            is AutomaticChargePlan.Unavailable ->
                Text("Automatic charging plan: ${plan.reason}",
                    color = SoftText, fontSize = 10.sp)
            AutomaticChargePlan.Empty -> Unit
        }
    }
    model.energyEstimateStatus?.let { message ->
        Text("Could not create MODEL/ESTIMATE: $message", color = SoftText, fontSize = 10.sp)
    }
    TextButton(onClick = onRouteDcClick,
        enabled = charging.routeProvided,
        modifier = Modifier.fillMaxWidth()) {
        Text("Along the route: ${charging.corridorCount?.toString() ?: "—"} DC charging stations",
            color = Turquoise, fontSize = 12.sp)
    }
    val destinationWeather = weather.destination?.takeIf {
        route?.routeId != null && weather.routeId == route.routeId
    }
    MetricLine(stringResource(R.string.native_destination_area_current_weather_estimate), destinationWeather?.let {
        "${it.summaryTr} · ${it.temperatureC.toInt()}°C"
    } ?: if(weather.loading) "Loading weather estimate…" else "—")
    MetricLine(stringResource(R.string.native_destination_area_wind), destinationWeather?.let {
        "${it.windSpeedKmh.toInt()} km/h"
    } ?: "—")
    if (weather.points.isNotEmpty() && weather.routeId == route?.routeId) {
        Text(stringResource(R.string.native_weather_open_meteo_current_model_estimate_not_arrival_time_weather),
            color = SoftText, fontSize = 10.sp)
    }
    if (weather.error != null && route?.routeId == weather.routeId) {
        Text(weather.error, color = SoftText, fontSize = 10.sp)
    }
    if (!route?.profileSpeeds.isNullOrEmpty()) {
        Text(stringResource(R.string.native_red_green_lines_osrm_speed_profile_not_live_traffic),
            color = SoftText, fontSize = 10.sp)
    }
    Text(stringResource(R.string.native_osrm_eta_is_a_model_excluding_traffic_charging_time_is_calculated_sepa),
        color = SoftText, fontSize = 10.sp,
        modifier = Modifier.padding(top = 7.dp))
}

@Composable
private fun RouteProgress(route: RouteSnapshot?, simulation: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.DirectionsCar, null, tint = Turquoise)
        Spacer(Modifier.width(9.dp))
        Text(stringResource(R.string.native_route_progress), color = Color.White,
            fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(route?.validProgress()?.let { "%${(it * 100).toInt()} · ${if(simulation) "SIM" else "GPS"}" }
             ?: "No GPS progress", color = SoftText, fontSize = 11.sp)
    }
    Spacer(Modifier.height(11.dp))
    BoxWithConstraints(Modifier.fillMaxWidth().height(32.dp)) {
        val progress = route?.validProgress()
        val value by animateFloatAsState(targetValue = progress ?: 0f, label = "GPS route progress")
        Box(Modifier.fillMaxWidth().height(6.dp).align(Alignment.CenterStart)
            .background(Color(0xFF355269), RoundedCornerShape(6.dp)))
        // Show the car only after real GPS position has been projected to route.
        if (progress != null) {
            Box(Modifier.fillMaxWidth(value).height(6.dp).align(Alignment.CenterStart)
                .background(Turquoise, RoundedCornerShape(6.dp)))
            Icon(Icons.Default.DirectionsCar, "Actual GPS route progress",
                tint = NeonGreen,
                modifier = Modifier.offset(x = (maxWidth - 28.dp) * value)
                    .align(Alignment.CenterStart).size(28.dp))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.native_departure), color = SoftText, fontSize = 10.sp)
        Text("Arrival · ${CockpitFormat.percent(route?.estimatedArrivalSocPercent)} MODEL",
            color = NeonGreen, fontSize = 10.sp)
    }
}

private enum class DockTab(val titleRes: Int, val icon: ImageVector) {
    ROUTE(R.string.native_route, Icons.Default.Map),
    CHARGING(R.string.native_charging, Icons.Default.Bolt),
    MEDIA(R.string.native_media, Icons.Default.SmartDisplay),
    SETTINGS(R.string.native_settings, Icons.Default.Settings)
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
                Text(stringResource(tab.titleRes), color = Color.White, fontSize = 13.sp)
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

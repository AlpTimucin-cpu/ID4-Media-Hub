package com.evcockpit.presentation.cockpit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.evcockpit.presentation.media.MediaLauncher
import com.evcockpit.presentation.media.RadioScreen
import com.evcockpit.presentation.media.MediaSite
import androidx.compose.runtime.saveable.rememberSaveable
import com.evcockpit.presentation.media.MediaLaunchState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evcockpit.BuildConfig
import com.evcockpit.R
import com.evcockpit.domain.model.*
import com.evcockpit.domain.usecase.AutomaticChargePlan
import com.evcockpit.presentation.charging.ChargingUiState
import com.evcockpit.presentation.charging.RouteChargingUiState
import com.evcockpit.presentation.map.MapLibreCockpit
import com.evcockpit.presentation.media.ParkedStreamingDialog
import com.evcockpit.presentation.media.rememberPhoneMediaAllowed
import com.evcockpit.presentation.navigation.NavigationRouteState
import com.evcockpit.presentation.navigation.RouteLoadStatus
import com.evcockpit.presentation.obd.ObdSettingsDialog
import com.evcockpit.presentation.route.RoutePlannerDialog
import com.evcockpit.presentation.route.RoutePlannerUiState
import com.evcockpit.presentation.voice.NativeVoiceGuide
import com.evcockpit.presentation.voice.TtsPackageWarningDialog

/** One full-size map, with compact overlays for the essential driving information. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CockpitScreen(
    state: CockpitUiState,
    routeState: NavigationRouteState,
    loadStatus: RouteLoadStatus,
    obdConnection: ObdConnectionState,
    engineering: EngineeringSnapshot,
    engineeringMetrics: EngineeringMetrics,
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
    soundUnavailable: Boolean = false,
    onOpenSoundSettings: () -> Unit = {}
) {
    // Disconnects invalidate *all* live battery/vehicle readings immediately.
    // GPS and route state remain independent of the OBD adapter.
    val displayState = if (obdConnection.phase == ObdPhase.CONNECTED) state else state.copy(
        socPercent = null,
        speedKmh = null,
        voltageV = null,
        batteryTemperatureC = null,
        powerKw = null,
        estimatedRangeKm = null,
        socSourceLabel = null
    )
    val route = state.route ?: routeState.routeResult
    var routePlanner by remember { mutableStateOf(false) }
    var charging by remember { mutableStateOf(false) }
    var media by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var engineeringOpen by remember { mutableStateOf(false) }
    var voiceSettings by remember { mutableStateOf(false) }
    var station by remember { mutableStateOf<ChargingStation?>(null) }
    var missingVoice by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var mediaLaunch by remember { mutableStateOf<MediaLaunchState?>(null) }
    val phoneMediaAllowed = rememberPhoneMediaAllowed()
    val snackbar = remember { SnackbarHostState() }
    val error = (loadStatus as? RouteLoadStatus.Error)?.message
        ?: gpsError ?: voiceError ?: chargingUi.error
        ?: weatherUi.error?.takeIf { weatherUi.routeId == route?.routeId }

    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) snackbar.showSnackbar(error.take(160))
    }
    LaunchedEffect(voiceStatus) {
        if (voiceStatus == NativeVoiceGuide.Status.TURKISH_VOICE_MISSING) missingVoice = true
    }

    if (routePlanner) RoutePlannerDialog(
        state = routePlannerUi, onDismiss = { routePlanner = false },
        onOriginChange = onRouteOriginChange, onDestinationChange = onRouteDestinationChange,
        onOriginSelected = onRouteOriginSelected, onDestinationSelected = onRouteDestinationSelected,
        onGps = onRouteFromGps, onPlan = onRoutePlan,
        onChooseAlternative = onRouteAlternative, onClear = onRouteClear
    )
    if (engineeringOpen) TechnicalAnalysisSheet(
        snapshot = engineering, metrics = engineeringMetrics,
        obdPhase = obdConnection.phase,
        onDismiss = { engineeringOpen = false }
    )
    if (settings) ObdSettingsDialog(
        state = obdConnection, onRefresh = onObdRefresh, onConnect = onObdConnect,
        onDisconnect = onObdDisconnect, onMebDiagnostic = onObdMebDiagnostic,
        onClearDiagnostic = onObdClearDiagnostic,
        onDismiss = { settings = false }
    )
    if (media) ParkedStreamingDialog(
        playbackAllowed = phoneMediaAllowed, onDismiss = { media = false },
        onLaunchMediaEngine = {
            mediaLaunch = MediaLauncher.launchMediaEngine(context)
            if (mediaLaunch is MediaLaunchState.Launched) media = false
        }
    )
    if (mediaLaunch == MediaLaunchState.NotInstalled || mediaLaunch == MediaLaunchState.Blocked) {
        AlertDialog(
            onDismissRequest = { mediaLaunch = null },
            containerColor = Navy,
            title = { Text(stringResource(R.string.media_engine_unavailable_title)) },
            text = { Text(stringResource(if (mediaLaunch == MediaLaunchState.NotInstalled)
                R.string.media_engine_not_installed else R.string.media_engine_blocked)) },
            confirmButton = {
                TextButton(onClick = { mediaLaunch = null }) {
                    Text(stringResource(R.string.native_close))
                }
            }
        )
    }
    if (missingVoice) TtsPackageWarningDialog(onDismiss = { missingVoice = false })
    if (voiceSettings) AlertDialog(
        onDismissRequest = { voiceSettings = false },
        containerColor = Navy,
        title = { Text(stringResource(R.string.native_voice_guidance)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(when (voiceStatus) {
                    NativeVoiceGuide.Status.LOADING -> R.string.ux_tts_loading
                    NativeVoiceGuide.Status.READY -> R.string.ux_tts_ready
                    NativeVoiceGuide.Status.TURKISH_VOICE_MISSING -> R.string.ux_tts_missing
                    NativeVoiceGuide.Status.INIT_FAILED -> R.string.native_could_not_initialize_the_speech_engine
                    NativeVoiceGuide.Status.AUDIO_FOCUS_DENIED -> R.string.native_could_not_obtain_audio_focus
                }))
                Text(stringResource(R.string.cockpit_voice_volume))
                Slider(
                    value = voiceVolume.takeIf { it.isFinite() }?.coerceIn(.15f, 1f) ?: 1f,
                    onValueChange = onVoiceVolumeChange, valueRange = .15f..1f
                )
                OutlinedButton(onClick = onVoiceTest, enabled = voiceEnabled &&
                    (voiceStatus == NativeVoiceGuide.Status.READY ||
                        voiceStatus == NativeVoiceGuide.Status.AUDIO_FOCUS_DENIED)) {
                    Text(stringResource(R.string.ux_tts_test))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { voiceSettings = false }) { Text(stringResource(R.string.native_close)) }
        }
    )
    if (charging) ModalBottomSheet(
        onDismissRequest = { charging = false }, containerColor = Navy
    ) {
        ChargingPanel(
            ui = chargingUi, onFilter = onStationFilter, onRefresh = onRefreshStations,
            onStation = { station = it; charging = false }
        )
    }
    station?.let { selected ->
        val fallbackName = stringResource(R.string.cockpit_station_name)
        val fallbackOperator = stringResource(R.string.cockpit_operator_unknown)
        val fallbackAddress = stringResource(R.string.cockpit_address_unknown)
        val unknownValue = stringResource(R.string.cockpit_unknown_value)
        AlertDialog(
            onDismissRequest = { station = null }, containerColor = Navy,
            title = { Text(selected.name.ifBlank { fallbackName }) },
            text = {
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(selected.operator.ifBlank { fallbackOperator })
                    Text(selected.address.ifBlank { fallbackAddress })
                    selected.sockets.forEach { socket ->
                        Text(stringResource(R.string.cockpit_socket_summary,
                            socket.kind.toString(), socket.type.toString(),
                            socket.powerRaw.ifBlank { unknownValue }))
                    }
                    Text(stringResource(R.string.cockpit_source_verified, selected.sourceVerifiedAtUtc), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.native_live_availability_price_and_road_access_are_unknown))
                }
            },
            confirmButton = {
                TextButton(onClick = { station = null }) { Text(stringResource(R.string.native_close)) }
            }
        )
    }


    // Three non-overlapping columns; the center owns the only MapView.
    Column(Modifier.fillMaxSize().background(Ink)
        .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryLeftPanel(
                displayState, obdConnection.phase == ObdPhase.CONNECTED,
                onConnect = { settings = true; onObdRefresh() },
                modifier = Modifier.weight(.26f).fillMaxHeight()
            )
            Box(
                Modifier.weight(.46f).fillMaxHeight()
                    .background(Navy, RoundedCornerShape(14.dp))
            ) {
                MapLibreCockpit(
                    gpsFix = state.gpsFix,
                    routePoints = route?.routePoints.orEmpty(),
                    stations = chargingUi.allStations,
                    plannedStopIds = (automaticChargePlan.plan as? AutomaticChargePlan.Estimate)
                        ?.stops?.map { it.station.id }?.toSet().orEmpty(),
                    weatherPoints = weatherUi.points
                        .takeIf { weatherUi.routeId == route?.routeId }.orEmpty(),
                    speedProfile = route?.profileSpeeds.orEmpty(),
                    onViewportChanged = onMapViewport,
                    onStationTapped = { station = it },
                    modifier = Modifier.fillMaxSize()
                )
                NavigationGlassCard(
                    route, loadStatus is RouteLoadStatus.Loading,
                    onOpen = { routePlanner = true },
                    modifier = Modifier.align(Alignment.TopCenter)
                        .fillMaxWidth(.90f).padding(top = 8.dp)
                )
                Row(
                    Modifier.align(Alignment.BottomStart).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistPill(
                        if (state.gpsFix == null) "GPS bekleniyor" else "GPS aktif",
                        onGpsClick
                    )
                    AssistPill(
                        if (voiceEnabled) "Ses açık" else "Ses kapalı",
                        onVoiceToggle
                    )
                }
            }
            CockpitMediaPanel(
                playbackAllowed = phoneMediaAllowed,
                onMusic = { mediaLaunch = MediaLauncher.launchMediaEngine(context) },
                onVideo = { site ->
                    if (site == com.evcockpit.presentation.media.MediaSite.YOUTUBE) {
                        media = true
                    } else {
                        mediaLaunch = MediaLauncher.launchOfficialApp(context, site)
                    }
                },
                modifier = Modifier.weight(.28f).fillMaxHeight()
            )
        }
        CockpitDock(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            onRoute = { routePlanner = true },
            onCharging = { charging = true },
            onMedia = { media = true },
            onEngineering = { engineeringOpen = true },
            onSettings = { settings = true; onObdRefresh() }
        )
    }
}

@Composable
private fun VoiceButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(stringResource(R.string.native_voice), color = NeonGreen)
    }
}

@Composable
private fun QuickActions(
    gpsEnabled: Boolean,
    hasGpsFix: Boolean,
    voiceEnabled: Boolean,
    onGpsClick: () -> Unit,
    onVoiceToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier) {
        IconButton(onClick = onGpsClick, enabled = gpsEnabled) {
            Icon(Icons.Default.GpsFixed,
                contentDescription = stringResource(R.string.cockpit_gps),
                tint = if (hasGpsFix) NeonGreen else SoftText)
        }
        IconButton(onClick = onVoiceToggle) {
            Icon(
                if (voiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                contentDescription = stringResource(if (voiceEnabled)
                    R.string.native_mute_voice_guidance else R.string.native_unmute_voice_guidance),
                tint = if (voiceEnabled) NeonGreen else SoftText
            )
        }
    }
}

@Composable
private fun BatteryHud(state: CockpitUiState, compact: Boolean, showExample: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier, color = Navy.copy(alpha = .96f),
        shape = RoundedCornerShape(18.dp), shadowElevation = 8.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge,
                color = SoftText, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            if (compact) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BatteryPercent(state.socPercent, true, showExample, Modifier.weight(.58f))
                    RangeValue(state.estimatedRangeKm, Modifier.weight(.42f), showExample)
                }
            } else {
                BatteryPercent(state.socPercent, false, showExample)
                RangeValue(state.estimatedRangeKm, showExample = showExample)
            }
            // An illustration is never used as a live battery measurement.
            if (state.socPercent == null && showExample) Text(
                "Örnek %80 · gerçek BMS doluluğu değil",
                style = MaterialTheme.typography.labelSmall, color = SoftText
            )
            // No fake zero-percent bar when ELM327/BMS data is unavailable.
            val validSoc = state.socPercent?.takeIf { it.isFinite() && it in 0.0..100.0 }
            if (validSoc != null) {
                LinearProgressIndicator(
                    progress = { (validSoc / 100.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(3.dp),
                    color = NeonGreen, trackColor = GlassLine
                )
            } else {
                Text(
                    stringResource(R.string.cockpit_measurement_unavailable),
                    style = MaterialTheme.typography.labelSmall, color = SoftText
                )
            }
        }
    }
}

@Composable
private fun BatteryPercent(value: Double?, compact: Boolean, showExample: Boolean, modifier: Modifier = Modifier) {
    val percentSymbol = stringResource(R.string.cockpit_percent_symbol)
    val unknownValue = stringResource(R.string.cockpit_unknown_value)
    val number = value?.takeIf { it.isFinite() && it in 0.0..100.0 }
        ?.let { CockpitFormat.percent(it).removeSuffix(percentSymbol) }
        ?: if (showExample) "80" else unknownValue
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(number,
            fontSize = if (compact) 72.sp else 96.sp,
            lineHeight = if (compact) 80.sp else 104.sp,
            fontWeight = FontWeight.Light,
            color = if (value == null) Color.White.copy(alpha = .60f) else Color.White,
            maxLines = 1, softWrap = false)
        if (number != unknownValue) {
            Text(percentSymbol, Modifier.padding(bottom = if (compact) 8.dp else 12.dp),
                fontSize = if (compact) 30.sp else 40.sp,
                lineHeight = if (compact) 36.sp else 46.sp, color = SoftText)
        }
    }
}

@Composable
private fun RangeValue(km: Double?, modifier: Modifier = Modifier, showExample: Boolean) {
    val unit = stringResource(R.string.cockpit_distance_unit)
    Column(modifier) {
        val knownRange = km?.takeIf { it.isFinite() && it >= 0.0 }
        val displayRange = knownRange ?: if (showExample) 320.0 else null
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                displayRange?.let { CockpitFormat.distance(it).removeSuffix(" $unit") }
                    ?: stringResource(R.string.cockpit_unknown_value),
                Modifier.alignByBaseline(), fontSize = 39.sp, lineHeight = 46.sp,
                fontWeight = FontWeight.Light, color = NeonGreen, maxLines = 1, softWrap = false
            )
            if (knownRange != null) {
                Text(unit, Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.labelMedium, color = SoftText)
            }
        }
        Text(
            if (knownRange == null && showExample) "320 km · örnek model, canlı menzil değil"
            else stringResource(R.string.cockpit_estimated_range),
            style = MaterialTheme.typography.labelSmall, color = SoftText
        )
    }
}

@Composable
private fun ArrivalHud(route: RouteSnapshot?, state: CockpitUiState, loading: Boolean, compact: Boolean, onOpen: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        color = Navy.copy(alpha = .96f), shape = RoundedCornerShape(6.dp)
    ) {
        Column(Modifier.padding(if (compact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            Text(
                route?.nextTurnInstruction?.takeIf { it.isNotBlank() }
                    ?: if (loading) stringResource(R.string.native_calculating_route)
                       else "Rota çizmek için dokunun",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(CockpitFormat.eta(route?.estimatedArrivalEpochMs),
                        style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text(stringResource(R.string.cockpit_arrival),
                        style = MaterialTheme.typography.labelMedium, color = SoftText)
                }
                Column(Modifier.weight(1f)) {
                    Text(CockpitFormat.percent(route?.estimatedArrivalSocPercent),
                        style = MaterialTheme.typography.headlineSmall, color = NeonGreen)
                    Text(stringResource(R.string.cockpit_arrival_soc),
                        style = MaterialTheme.typography.labelMedium, color = SoftText)
                }
            }
            if (!compact) Text(
                route?.destination?.takeIf { it.isNotBlank() } ?: stringResource(R.string.cockpit_plan_route),
                style = MaterialTheme.typography.bodySmall, color = SoftText,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            state.socSourceLabel?.let { source ->
                Text(source, style = MaterialTheme.typography.labelSmall, color = SoftText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CockpitDock(
    modifier: Modifier = Modifier,
    onRoute: () -> Unit, onCharging: () -> Unit, onMedia: () -> Unit,
    onEngineering: () -> Unit, onSettings: () -> Unit
) {
    Surface(modifier.fillMaxWidth(), color = Navy.copy(alpha = .98f), shape = RoundedCornerShape(6.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            DockAction(Icons.Default.Map, stringResource(R.string.native_route), onRoute, Modifier.weight(1f))
            DockAction(Icons.Default.Bolt, stringResource(R.string.native_charging), onCharging, Modifier.weight(1f))
            DockAction(Icons.Default.SmartDisplay, stringResource(R.string.native_media), onMedia, Modifier.weight(1f))
            DockAction(Icons.Default.Analytics, "Hesaplar", onEngineering, Modifier.weight(1f))
            DockAction(Icons.Default.Settings, stringResource(R.string.native_settings), onSettings, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DockAction(icon: ImageVector, title: String, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier.heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(icon, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(20.dp))
        Text(title, color = SoftText, fontSize = 11.sp, lineHeight = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChargingPanel(ui: ChargingUiState, onFilter: (StationFilter) -> Unit,
    onRefresh: () -> Unit, onStation: (ChargingStation) -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.native_epdk_station_filters), style = MaterialTheme.typography.headlineSmall)
        StationFilter.entries.forEach { filter ->
            val title = stringResource(when (filter) {
                StationFilter.ALL -> R.string.cockpit_filter_all
                StationFilter.DC -> R.string.cockpit_filter_dc
                StationFilter.ROUTE_CCS -> R.string.cockpit_filter_route_ccs
                StationFilter.ROUTE_DC -> R.string.cockpit_filter_route_dc
            })
            FilterChip(selected = ui.selectedFilter == filter,
                onClick = { onFilter(filter) }, label = { Text(title) },
                enabled = ui.routeProvided || filter == StationFilter.ALL || filter == StationFilter.DC)
        }
        TextButton(onClick = onRefresh) { Text(stringResource(R.string.native_refresh)) }
        if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ui.error?.let { Text(it, color = SoftText) }
        Text(stringResource(R.string.cockpit_station_count, ui.visible.size, ui.total), style = MaterialTheme.typography.bodySmall)
        Text(ui.verifiedAtUtc ?: stringResource(R.string.cockpit_source_date_unknown), style = MaterialTheme.typography.bodySmall)
        ui.visible.take(100).forEach { candidate ->
            TextButton(onClick = { onStation(candidate) }, modifier = Modifier.fillMaxWidth()) {
                Text(candidate.name, modifier = Modifier.fillMaxWidth(), maxLines = 2)
            }
        }
    }
}

/** Actionable status hint shown when the real hardware is unavailable. */
@Composable
private fun HardwareNotice(title: String, detail: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Navy.copy(alpha = .96f),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 5.dp
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
            Text(title, color = NeonGreen, style = MaterialTheme.typography.labelLarge)
            Text(detail, color = SoftText, style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}


/** Landscape telemetry column. Every tile is independent; no nested card stack. */
@Composable
private fun TelemetryLeftPanel(
    data: CockpitUiState, connected: Boolean,
    onConnect: () -> Unit, modifier: Modifier = Modifier
) {
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(color = Navy, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text("EV COCKPIT", color = SoftText,
                    style = MaterialTheme.typography.labelMedium)
                val realSoc = data.socPercent?.takeIf { connected && it in 0.0..100.0 }
                val socText = realSoc?.let { CockpitFormat.percent(it) } ?: "80%"
                Text(socText,
                    fontSize = 52.sp, lineHeight = 58.sp,
                    fontWeight = FontWeight.Light,
                    color = if (realSoc == null) Color.White.copy(alpha = .75f) else Color.White
                )
                Text(if (realSoc == null) "ÖRNEK · canlı BMS verisi yok" else "BATARYA · CANLI",
                    color = SoftText, fontSize = 10.sp)
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = GlassLine)
                val realRange = data.estimatedRangeKm?.takeIf { connected && it >= 0.0 }
                Text(realRange?.let { CockpitFormat.distance(it) } ?: "320 km",
                    fontSize = 25.sp, color = NeonGreen)
                Text(if (realRange == null) "ÖRNEK MODEL · rota hesabında kullanılmaz"
                    else "MENZİL · TAHMİN",
                    color = SoftText, fontSize = 10.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TelemetryTile("HIZ", data.speedKmh?.takeIf { connected }?.let(CockpitFormat::speed)
                ?: "Bağlantı bekleniyor", Modifier.weight(1f))
            TelemetryTile("GÜÇ", data.powerKw?.takeIf { connected }?.let(CockpitFormat::power)
                ?: "Bekleniyor", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // A BMS capacity parameter is not a certified health-of-battery result.
            TelemetryTile("SoH %", "Doğrulanmadı", Modifier.weight(1f))
            TelemetryTile("BATARYA °C", data.batteryTemperatureC?.takeIf { connected }
                ?.let(CockpitFormat::temperature) ?: "Bekleniyor", Modifier.weight(1f))
        }
        if (!connected) {
            OutlinedButton(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                Text("OBD bağla", maxLines = 1)
            }
        }
    }
}

@Composable
private fun TelemetryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier.heightIn(min = 76.dp), color = Navy,
        shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, color = SoftText, style = MaterialTheme.typography.labelSmall,
                maxLines = 1)
            Text(value, color = Color.White,
                style = MaterialTheme.typography.titleSmall, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NavigationGlassCard(
    route: RouteSnapshot?, loading: Boolean,
    onOpen: () -> Unit, modifier: Modifier = Modifier
) {
    Surface(
        modifier.clickable(onClick = onOpen),
        color = Color(0xCF0A1728),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 4.dp
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("NAVİGASYON", color = NeonGreen,
                style = MaterialTheme.typography.labelSmall)
            Text(
                route?.nextTurnInstruction?.takeIf { it.isNotBlank() }
                    ?: if (loading) "Rota hesaplanıyor…" else "Rota çizmek için dokunun",
                color = Color.White, style = MaterialTheme.typography.titleSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            val arrival = route?.estimatedArrivalEpochMs?.let(CockpitFormat::eta)
            val left = route?.remainingKm?.let(CockpitFormat::distance)
            if (arrival != null || left != null) Text(
                listOfNotNull(left, arrival?.let { "Varış $it" }).joinToString(" · "),
                color = SoftText, style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun AssistPill(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = Color(0xD90A1728), shape = RoundedCornerShape(10.dp)
    ) {
        Text(text, color = SoftText,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
    }
}

@Composable
private fun CockpitMediaPanel(
    playbackAllowed: Boolean,
    onMusic: () -> Unit,
    onVideo: (MediaSite) -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Surface(modifier, color = Navy, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("MEDYA", color = Color.White, style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Radyo", "Müzik", "Video").forEachIndexed { index, name ->
                    FilterChip(
                        selected = tab == index,
                        onClick = { tab = index },
                        label = { Text(name, maxLines = 1, fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            when (tab) {
                0 -> Box(Modifier.fillMaxWidth().weight(1f)) {
                    RadioScreen(compact = true)
                }
                1 -> Column(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Müzik", color = Color.White)
                    Text("Kurulu medya motorunu aç", color = SoftText,
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onMusic, modifier = Modifier.fillMaxWidth()) {
                        Text("Müzik oynatıcıyı aç")
                    }
                }
                else -> Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (playbackAllowed) "Video uygulamaları · park hâlinde"
                        else "Video sürüşte kullanılamaz",
                        color = SoftText, style = MaterialTheme.typography.bodySmall
                    )
                    MediaSite.entries.forEach { site ->
                        OutlinedButton(
                            onClick = { onVideo(site) },
                            enabled = playbackAllowed,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(site.title) }
                    }
                }
            }
        }
    }
}

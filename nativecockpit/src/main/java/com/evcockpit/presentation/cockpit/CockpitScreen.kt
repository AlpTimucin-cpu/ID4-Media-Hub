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
        snapshot = engineering, obdPhase = obdConnection.phase,
        onDismiss = { engineeringOpen = false }
    )
    if (settings) ObdSettingsDialog(
        state = obdConnection, onRefresh = onObdRefresh, onConnect = onObdConnect,
        onDisconnect = onObdDisconnect, onMebDiagnostic = onObdMebDiagnostic,
        onClearDiagnostic = onObdClearDiagnostic,
        simulationSupported = BuildConfig.DEBUG, simulationEnabled = state.simulationEnabled,
        onSimulationToggle = onSimulationToggle, onDismiss = { settings = false }
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
                    NativeVoiceGuide.Status.LOADING -> R.string.native_preparing_english_speech
                    NativeVoiceGuide.Status.READY -> R.string.native_english_speech_ready
                    NativeVoiceGuide.Status.TURKISH_VOICE_MISSING -> R.string.native_english_tts_data_missing
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
                    Text(stringResource(R.string.native_english_speech_test))
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

    BoxWithConstraints(Modifier.fillMaxSize().background(Ink)) {
        val landscape = maxWidth > maxHeight
        val short = maxHeight < 480.dp
        val compactBattery = landscape && short
        val batteryWidth = if (compactBattery) maxWidth * .49f
            else 256.dp.coerceAtMost(maxWidth * .64f)
        val arrivalWidth = if (landscape) 294.dp.coerceAtMost(maxWidth * .43f) else maxWidth - 32.dp

        MapLibreCockpit(
            gpsFix = state.gpsFix, routePoints = route?.routePoints.orEmpty(),
            stations = chargingUi.visible,
            plannedStopIds = (automaticChargePlan.plan as? AutomaticChargePlan.Estimate)
                ?.stops?.map { it.station.id }?.toSet().orEmpty(),
            weatherPoints = weatherUi.points.takeIf { weatherUi.routeId == route?.routeId }.orEmpty(),
            speedProfile = route?.profileSpeeds.orEmpty(),
            onViewportChanged = onMapViewport, onStationTapped = { station = it },
            modifier = Modifier.fillMaxSize()
        )
        BatteryHud(
            state = state, compact = compactBattery,
            modifier = Modifier.align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start))
                .padding(start = 16.dp, top = 16.dp).width(batteryWidth)
        )
        Column(
            Modifier.align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                .padding(top = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            Surface(color = Navy.copy(alpha = .96f), shape = RoundedCornerShape(6.dp)) {
                if (landscape) Row(verticalAlignment = Alignment.CenterVertically) {
                    VoiceButton(onClick = { voiceSettings = true })
                    QuickActions(
                        gpsEnabled = !state.simulationEnabled,
                        hasGpsFix = state.gpsFix != null,
                        voiceEnabled = voiceEnabled,
                        onGpsClick = onGpsClick,
                        onVoiceToggle = onVoiceToggle
                    )
                } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    VoiceButton(onClick = { voiceSettings = true })
                    QuickActions(
                        gpsEnabled = !state.simulationEnabled,
                        hasGpsFix = state.gpsFix != null,
                        voiceEnabled = voiceEnabled,
                        onGpsClick = onGpsClick,
                        onVoiceToggle = onVoiceToggle
                    )
                }
            }
        }
        Column(
            Modifier.align(if (landscape) Alignment.TopEnd else Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(
                    if (landscape) WindowInsetsSides.Top + WindowInsetsSides.End
                    else WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                ))
                .padding(
                    start = if (landscape) 0.dp else 16.dp,
                    end = 16.dp,
                    top = if (landscape) 80.dp else 0.dp,
                    bottom = if (landscape) 0.dp else 112.dp
                ).width(arrivalWidth)
        ) {
            ArrivalHud(
                route = route, state = state, loading = loadStatus is RouteLoadStatus.Loading,
                compact = short, onOpen = { routePlanner = true }
            )
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding().padding(bottom = if (landscape) 80.dp else 260.dp)
        )
        CockpitDock(
            modifier = Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            onRoute = { routePlanner = true }, onCharging = { charging = true },
            onMedia = { media = true }, onEngineering = { engineeringOpen = true },
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
private fun BatteryHud(state: CockpitUiState, compact: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier, color = Navy.copy(alpha = .96f), shape = RoundedCornerShape(6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge,
                color = SoftText, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            if (compact) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BatteryPercent(state.socPercent, true, Modifier.weight(.58f))
                    RangeValue(state.estimatedRangeKm, Modifier.weight(.42f))
                }
            } else {
                BatteryPercent(state.socPercent, false)
                RangeValue(state.estimatedRangeKm)
            }
            LinearProgressIndicator(
                progress = { state.socPercent?.takeIf { it.isFinite() && it in 0.0..100.0 }
                    ?.div(100.0)?.toFloat() ?: 0f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(3.dp),
                color = NeonGreen, trackColor = GlassLine
            )
            if (state.socPercent == null) Text(stringResource(R.string.cockpit_measurement_unavailable),
                style = MaterialTheme.typography.labelSmall, color = SoftText)
        }
    }
}

@Composable
private fun BatteryPercent(value: Double?, compact: Boolean, modifier: Modifier = Modifier) {
    val percentSymbol = stringResource(R.string.cockpit_percent_symbol)
    val unknownValue = stringResource(R.string.cockpit_unknown_value)
    val number = value?.takeIf { it.isFinite() && it in 0.0..100.0 }
        ?.let { CockpitFormat.percent(it).removeSuffix(percentSymbol) } ?: unknownValue
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(number,
            fontSize = if (compact) 72.sp else 96.sp,
            lineHeight = if (compact) 80.sp else 104.sp,
            fontWeight = FontWeight.Light, color = Color.White,
            maxLines = 1, softWrap = false)
        Text(percentSymbol, Modifier.padding(bottom = if (compact) 8.dp else 12.dp),
            fontSize = if (compact) 30.sp else 40.sp,
            lineHeight = if (compact) 36.sp else 46.sp, color = SoftText)
    }
}

@Composable
private fun RangeValue(km: Double?, modifier: Modifier = Modifier) {
    val unit = stringResource(R.string.cockpit_distance_unit)
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(CockpitFormat.distance(km).removeSuffix(" $unit"),
                Modifier.alignByBaseline(), fontSize = 39.sp, lineHeight = 46.sp,
                fontWeight = FontWeight.Light, color = NeonGreen, maxLines = 1, softWrap = false)
            Text(unit, Modifier.alignByBaseline(),
                style = MaterialTheme.typography.labelMedium, color = SoftText)
        }
        Text(stringResource(R.string.cockpit_estimated_range),
            style = MaterialTheme.typography.labelSmall, color = SoftText)
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
                    ?: stringResource(if (loading) R.string.native_calculating_route
                        else R.string.native_waiting_for_maneuver),
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

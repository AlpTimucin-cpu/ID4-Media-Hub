# EV Cockpit — Production UX Complete Kotlin Sources

These are complete Kotlin sources from production UX PR #14. They are included for mobile editing and audit; source integration is confirmed only after the PR merge and passing CI. Do not copy a partial snippet into a Kotlin source file.

## nativecockpit/src/main/java/com/evcockpit/presentation/onboarding/WelcomeOnboarding.kt

```kotlin
package com.evcockpit.presentation.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.evcockpit.R

private val Night = Color(0xFF081320)
private val Card = Color(0xFF102338)
private val Neon = Color(0xFF39FF88)
private val Muted = Color(0xFFB6C6D2)

private enum class PermissionStep { LOCATION, BLUETOOTH, NOTIFICATIONS }

private fun permitted(context: Context, step: PermissionStep): Boolean =
    when (step) {
        PermissionStep.LOCATION ->
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        PermissionStep.BLUETOOTH ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        PermissionStep.NOTIFICATIONS ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

@Composable
fun WelcomeOnboarding(onFinish: () -> Unit) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val steps = remember { PermissionStep.entries }

    val locationRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            permissionDenied = false
            page++
        } else permissionDenied = true
    }
    val bluetoothRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) { permissionDenied = false; page++ }
        else permissionDenied = true
    }
    val notificationRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) { permissionDenied = false; page++ }
        else permissionDenied = true
    }

    val current = steps.getOrNull(page)
    val title = when (current) {
        PermissionStep.LOCATION -> R.string.ux_location_title
        PermissionStep.BLUETOOTH -> R.string.ux_bluetooth_title
        PermissionStep.NOTIFICATIONS -> R.string.ux_notification_title
        null -> R.string.ux_ready_title
    }
    val detail = when (current) {
        PermissionStep.LOCATION -> R.string.ux_location_reason
        PermissionStep.BLUETOOTH -> R.string.ux_bluetooth_reason
        PermissionStep.NOTIFICATIONS -> R.string.ux_notification_reason
        null -> R.string.ux_ready_reason
    }
    val stepIcon = when (current) {
        PermissionStep.LOCATION -> Icons.Default.LocationOn
        PermissionStep.BLUETOOTH -> Icons.Default.Bluetooth
        PermissionStep.NOTIFICATIONS -> Icons.Default.Notifications
        null -> Icons.Default.LocationOn
    }
    Column(
        Modifier.fillMaxSize().background(Night).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Icon(painterResource(R.drawable.ic_launcher_foreground), null,
            modifier = Modifier.size(100.dp), tint = Color.Unspecified)
        Text(stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Text(stringResource(R.string.ux_welcome_description), color = Muted,
            style = MaterialTheme.typography.bodyMedium)
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp), color = Neon,
            strokeWidth = 3.dp
        )
        LinearProgressIndicator(
            progress = { (page.coerceIn(0, steps.size).toFloat() / steps.size) },
            modifier = Modifier.fillMaxWidth(), color = Neon,
            trackColor = Card
        )
        Surface(
            color = Card, shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(stepIcon, null, tint = Neon, modifier = Modifier.size(34.dp))
                Text(stringResource(title), color = Color.White,
                    style = MaterialTheme.typography.titleLarge)
                Text(stringResource(detail), color = Muted)
                if (permissionDenied) {
                    Text(stringResource(R.string.ux_permission_denied), color = Neon,
                        style = MaterialTheme.typography.bodySmall)
                }
                if (current != null && permitted(context, current)) {
                    Text(stringResource(R.string.ux_already_granted),
                        color = Neon, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (current == null) {
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ux_open_cockpit))
            }
        } else {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    permissionDenied = false
                    if (permitted(context, current)) {
                        page++
                    } else when (current) {
                        PermissionStep.LOCATION -> locationRequest.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                        PermissionStep.BLUETOOTH ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                bluetoothRequest.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            } else page++
                        PermissionStep.NOTIFICATIONS ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else page++
                    }
                }
            ) {
                Text(stringResource(
                    if (permitted(context, current)) R.string.ux_continue
                    else R.string.ux_allow_permission
                ))
            }
            OutlinedButton(
                onClick = { permissionDenied = false; page++ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.ux_skip_permission))
            }
        }
        TextButton(onClick = onFinish) {
            Text(stringResource(R.string.ux_finish_later), color = Muted)
        }
    }
}
```

## nativecockpit/src/main/java/com/evcockpit/MainActivity.kt

```kotlin
package com.evcockpit

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.evcockpit.presentation.onboarding.WelcomeOnboarding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.evcockpit.domain.repository.ObdConnectionController
import com.evcockpit.domain.repository.VehicleRepository
import com.evcockpit.domain.model.VehicleTelemetry
import com.evcockpit.data.settings.UserSettings
import com.evcockpit.data.settings.UserSettingsRepository
import com.evcockpit.presentation.settings.SettingsScreen
import com.evcockpit.presentation.settings.SettingsViewModel
import com.evcockpit.presentation.sound.ActiveSoundManager
import com.evcockpit.presentation.voice.AppAudioDucking
import com.evcockpit.presentation.voice.LocalAppAudioDucking
import com.evcockpit.domain.model.ObdPhase
import com.evcockpit.presentation.navigation.NavigationViewModel
import com.evcockpit.presentation.charging.ChargingViewModel
import com.evcockpit.presentation.charging.RouteChargePlanViewModel
import com.evcockpit.presentation.weather.RouteWeatherViewModel
import com.evcockpit.presentation.cockpit.CockpitScreen
import com.evcockpit.presentation.cockpit.CockpitTheme
import com.evcockpit.presentation.media.enterOwnVideoPipWhenParked

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val navigation: NavigationViewModel by viewModels()
    private val charging: ChargingViewModel by viewModels()
    private val chargePlan: RouteChargePlanViewModel by viewModels()
    private val weather: RouteWeatherViewModel by viewModels()
    @Inject lateinit var obd: ObdConnectionController
    @Inject lateinit var vehicleRepository: VehicleRepository
    @Inject lateinit var settingsRepository: UserSettingsRepository
    @Inject lateinit var activeSound: ActiveSoundManager
    @Inject lateinit var audioDucking: AppAudioDucking

    private data class SoundFrame(
        val settings: UserSettings,
        val speed: Double?,
        val sampleAt: Long?,
        val ducked: Boolean
    )

    private val bluetoothPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) obd.refreshPaired() else obd.permissionDenied()
    }

    private fun refreshObdDevices() {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_CONNECT
        ) != PackageManager.PERMISSION_GRANTED) {
            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else obd.refreshPaired()
    }

    private fun connectObd(address: String) {
        try {
            obd.connect(address)
            if (obd.connection.value.phase != ObdPhase.CONNECTING &&
                obd.connection.value.phase != ObdPhase.CONNECTED) return
            // Explicit user action from a visible Activity; never auto-reconnect.
            ContextCompat.startForegroundService(
                this, Intent(this, ObdForegroundService::class.java)
            )
        } catch (_: Exception) {
            obd.disconnect() // Don't leave an unsupervised background socket.
            Toast.makeText(
                this, "OBD arka plan servisi başlatılamadı. Bağlantı kapatıldı.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun disconnectObd() {
        obd.disconnect()
        stopService(Intent(this, ObdForegroundService::class.java))
    }

    /**
     * For a future OWNED player Activity only; call after a trusted Park signal.
     * Third-party streaming packages own their own PiP and DRM surfaces.
     * Existing MapLibre and media app navigation are unaffected.
     */
    fun enterParkedOwnedVideoPip(
        verifiedParked: Boolean,
        ownVideoPlaying: Boolean
    ): Boolean = enterOwnVideoPipWhenParked(verifiedParked, ownVideoPlaying)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Observe real telemetry only while the phone UI is resumed.
        // Freshness check prevents stale/absent OBD readings producing sound.
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val ticker = flow {
                    while (true) {
                        emit(System.currentTimeMillis())
                        delay(1_000L)
                    }
                }
                combine(
                    settingsRepository.settings,
                    vehicleRepository.telemetry,
                    obd.connection,
                    audioDucking.ducked,
                    ticker
                ) { settings, telemetry, connection, ducked, now ->
                    SoundFrame(
                        settings = settings,
                        speed = if (connection.phase == ObdPhase.CONNECTED)
                            telemetry.speedKmh.current(now, 10_000L)
                        else null,
                        sampleAt = telemetry.speedKmh.measuredAtMillis,
                        ducked = ducked
                    )
                }.collect { frame ->
                    activeSound.setSpeechDucking(frame.ducked)
                    activeSound.update(frame.settings, frame.speed, frame.sampleAt, true)
                }
            }
        }

        setContent {
            val context = LocalContext.current
            val fused = remember(context) {
                LocationServices.getFusedLocationProviderClient(context.applicationContext)
            }
            var pendingRouteRequest by remember { mutableStateOf(false) }
            val locationManager = remember(context) {
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            }
            var fusedToken by remember { mutableStateOf<CancellationTokenSource?>(null) }
            DisposableEffect(fused) {
                onDispose { fusedToken?.cancel() }
            }
            // Route selection already has its own validated Fused request in ViewModel.
            // Map refresh uses an independent one-shot request, not stale lastLocation.
            @Suppress("MissingPermission")
            fun granted(forRoute: Boolean) {
                if (!locationManager.isLocationEnabled) {
                    navigation.reportGpsError("Location services are off. Enable location in your phone settings.")
                    return
                }
                navigation.startGps()
                if (forRoute) {
                    navigation.useCurrentGps()
                    return
                }
                val fine = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val request = CurrentLocationRequest.Builder()
                    .setPriority(if (fine) Priority.PRIORITY_HIGH_ACCURACY
                                 else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setMaxUpdateAgeMillis(3_000L)
                    .setDurationMillis(15_000L)
                    .build()
                fusedToken?.cancel()
                val token = CancellationTokenSource()
                fusedToken = token
                try {
                    fused.getCurrentLocation(request, token.token)
                        .addOnSuccessListener { location ->
                            if (location == null) {
                                navigation.reportGpsError(
                                    "No current location found. Try again outdoors."
                                )
                            } else {
                                navigation.acceptLastLocation(location)
                            }
                        }
                        .addOnFailureListener { error ->
                            navigation.reportGpsError(
                                "Could not get location: " + (error.message ?: "GPS error")
                            )
                        }
                } catch (_: SecurityException) {
                    navigation.reportGpsError("Location permission is no longer valid. Grant it again.")
                }
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { grants ->
                val accepted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                if (accepted) granted(pendingRouteRequest)
                else {
                    if (pendingRouteRequest) navigation.locationPermissionDenied()
                    else navigation.reportGpsError(
                        "Location permission was not granted. Enable it in Settings."
                    )
                }
                pendingRouteRequest = false
            }
            val requestGps: (Boolean) -> Unit = { fromRoute ->
                val allowed = listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                if (allowed) granted(fromRoute)
                else {
                    pendingRouteRequest = fromRoute
                    permissionLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                }
            }
            val gpsError by navigation.gpsError.collectAsStateWithLifecycle()
            val ui by navigation.uiState.collectAsStateWithLifecycle()
            val stations by charging.uiState.collectAsStateWithLifecycle()
            val automaticChargePlan by chargePlan.uiState.collectAsStateWithLifecycle()
            val routeWeather by weather.uiState.collectAsStateWithLifecycle()
            val obdConnection by obd.connection.collectAsStateWithLifecycle()
            val engineering by obd.engineering.collectAsStateWithLifecycle()
            val engineeringMetrics by obd.engineeringMetrics.collectAsStateWithLifecycle()
            val soundSettings by settingsViewModel.settings.collectAsStateWithLifecycle()
            var audioSettingsOpen by rememberSaveable { mutableStateOf(false) }
            val introPreferences = remember(context) {
                context.applicationContext.getSharedPreferences(
                    "ev_cockpit_onboarding_v1", Context.MODE_PRIVATE
                )
            }
            var onboardingCompleted by rememberSaveable {
                mutableStateOf(introPreferences.getBoolean("completed", false))
            }
            val soundUnavailable by activeSound.audioUnavailable.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppAudioDucking provides audioDucking) {
                CockpitTheme(nightMode = soundSettings.isNightModeEnabled) {
                    Box(Modifier.fillMaxSize()) {
                        if (!onboardingCompleted) {
                            WelcomeOnboarding(onFinish = {
                                introPreferences.edit().putBoolean("completed", true).apply()
                                onboardingCompleted = true
                            })
                        } else {
                        CockpitScreen(
                    state = ui.cockpit,
                    routeState = ui.routeState,
                    loadStatus = ui.loadStatus,
                    routePlannerUi = ui.planner,
                    onRouteOriginChange = navigation::editOrigin,
                    onRouteDestinationChange = navigation::editDestination,
                    onRouteOriginSelected = navigation::selectOrigin,
                    onRouteDestinationSelected = navigation::selectDestination,
                    onRouteFromGps = { requestGps(true) },
                    onRoutePlan = navigation::plan,
                    onRouteAlternative = navigation::selectAlternative,
                    onRouteClear = navigation::clearRoute,
                    onGpsClick = { requestGps(false) },
                    gpsError = gpsError,
                    chargingUi = stations,
                    automaticChargePlan = automaticChargePlan,
                    weatherUi = routeWeather,
                    onStationFilter = charging::changeFilter,
                    onMapViewport = charging::viewportChanged,
                    onRefreshStations = charging::refresh,
                    obdConnection = obdConnection,
                    engineering = engineering,
                    engineeringMetrics = engineeringMetrics,
                    onObdRefresh = ::refreshObdDevices,
                    onObdConnect = ::connectObd,
                    onObdDisconnect = ::disconnectObd,
                    onObdMebDiagnostic = obd::requestMebDiagnostic,
                    onObdClearDiagnostic = obd::clearMebDiagnostic,
                    voiceEnabled = ui.voice.enabled,
                    onVoiceToggle = { navigation.setVoiceEnabled(!ui.voice.enabled) },
                    voiceVolume = ui.voice.volume,
                    onVoiceVolumeChange = navigation::setVoiceVolume,
                    voiceStatus = ui.voice.status,
                    voiceError = ui.voice.error,
                    onVoiceTest = navigation::testVoice,
                    soundUnavailable = soundUnavailable,
                    onOpenSoundSettings = { audioSettingsOpen = true }
                )
                        // Separate settings affordance: original CockpitScreen untouched.
                        IconButton(
                            modifier = Modifier.align(Alignment.TopEnd)
                                .padding(top = 42.dp, end = 8.dp),
                            onClick = { audioSettingsOpen = true }
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = "Ses ayarları")
                        }
                        if (audioSettingsOpen) SettingsScreen(
                            viewModel = settingsViewModel,
                            activeSound = activeSound,
                            onClose = { audioSettingsOpen = false }
                        )
                        }
                    }
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        activeSound.onResume()
        navigation.setForeground(true)
        val allowed = listOf(Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allowed) navigation.startGps()
    }
    override fun onPause() {
        activeSound.onPause()
        navigation.setForeground(false)
        // OBD is retained only while the user-started connectedDevice FGS runs.
        super.onPause()
    }
}
```

## nativecockpit/src/main/java/com/evcockpit/presentation/cockpit/CockpitScreen.kt

```kotlin
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
        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp).padding(bottom = 92.dp).widthIn(max = 340.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (obdConnection.phase != ObdPhase.CONNECTED) {
                HardwareNotice(
                    stringResource(R.string.ux_obd_unavailable),
                    stringResource(R.string.ux_obd_help),
                    onClick = { settings = true; onObdRefresh() }
                )
            } else if (state.socPercent == null) {
                HardwareNotice(
                    stringResource(R.string.ux_battery_unavailable),
                    stringResource(R.string.ux_battery_help),
                    onClick = { settings = true }
                )
            }
            if (state.gpsFix == null) {
                HardwareNotice(
                    stringResource(R.string.ux_gps_waiting),
                    stringResource(R.string.ux_gps_help),
                    onClick = onGpsClick
                )
            }
            if (soundUnavailable) {
                HardwareNotice(
                    stringResource(R.string.ux_sound_unavailable),
                    stringResource(R.string.ux_sound_help),
                    onClick = onOpenSoundSettings
                )
            }
            if (voiceStatus == NativeVoiceGuide.Status.TURKISH_VOICE_MISSING) {
                HardwareNotice(
                    stringResource(R.string.ux_tts_missing),
                    stringResource(R.string.ux_tts_help),
                    onClick = { missingVoice = true }
                )
            }
        }
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
                        gpsEnabled = true,
                        hasGpsFix = state.gpsFix != null,
                        voiceEnabled = voiceEnabled,
                        onGpsClick = onGpsClick,
                        onVoiceToggle = onVoiceToggle
                    )
                } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    VoiceButton(onClick = { voiceSettings = true })
                    QuickActions(
                        gpsEnabled = true,
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
        if (number != unknownValue) {
            Text(percentSymbol, Modifier.padding(bottom = if (compact) 8.dp else 12.dp),
                fontSize = if (compact) 30.sp else 40.sp,
                lineHeight = if (compact) 36.sp else 46.sp, color = SoftText)
        }
    }
}

@Composable
private fun RangeValue(km: Double?, modifier: Modifier = Modifier) {
    val unit = stringResource(R.string.cockpit_distance_unit)
    Column(modifier) {
        val knownRange = km?.takeIf { it.isFinite() && it >= 0.0 }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                knownRange?.let { CockpitFormat.distance(it).removeSuffix(" $unit") }
                    ?: stringResource(R.string.cockpit_unknown_value),
                Modifier.alignByBaseline(), fontSize = 39.sp, lineHeight = 46.sp,
                fontWeight = FontWeight.Light, color = NeonGreen, maxLines = 1, softWrap = false
            )
            if (knownRange != null) {
                Text(unit, Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.labelMedium, color = SoftText)
            }
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
```

## nativecockpit/src/main/java/com/evcockpit/presentation/sound/ActiveSoundManager.kt

```kotlin
package com.evcockpit.presentation.sound

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.util.Log
import androidx.media3.common.PlaybackException
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.evcockpit.R
import com.evcockpit.data.settings.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EngineProfile(val label: String, val idle: Int, val accel: Int) {
    V8("V8", R.raw.v8_idle, R.raw.v8_accel),
    F1("F1", R.raw.f1_idle, R.raw.f1_accel),
    Futuristic("Futuristic", R.raw.futuristic_idle, R.raw.futuristic_accel),
    Turbine("Turbine", R.raw.turbine_idle, R.raw.turbine_accel),
    Stealth("Stealth", R.raw.stealth_idle, R.raw.stealth_accel);

    companion object {
        fun from(value: String): EngineProfile = entries.firstOrNull {
            it.name == value
        } ?: V8
    }
}

/**
 * In-cabin only synthetic audio. Never replace the vehicle's external AVAS.
 * Mutate ExoPlayer from the main thread; no music library or real engine sample.
 * A real fresh OBD speed is required (no speed => automatic silence).
 */
@Singleton
class ActiveSoundManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val main = Handler(Looper.getMainLooper())
    private var idle: ExoPlayer? = null
    private var acceleration: ExoPlayer? = null
    private var currentProfile: EngineProfile? = null
    private var lastSpeedKmh: Double? = null
    private var lastSpeedAtMs: Long? = null
    private var enabled = false
    private var volume = 1f
    private var ducked = false
    private var previewing = false
    private var previewToken = 0L
    private var activeInForeground = false
    private var lastSpeedInput: Double? = null
    private var lastInputTime: Long? = null
    private val _audioUnavailable = MutableStateFlow(false)
    val audioUnavailable: StateFlow<Boolean> = _audioUnavailable.asStateFlow()

    private fun player(resourceId: Int): ExoPlayer? {
        var candidate: ExoPlayer? = null
        return try {
            if (resourceId <= 0) {
                _audioUnavailable.value = true
                return null
            }
            // Resources may be stripped from a damaged install or an asset task
            // may fail to generate them. Audio must never crash the cockpit.
            context.resources.openRawResource(resourceId).use { /* readable */ }
            candidate = ExoPlayer.Builder(context).build()
            candidate.apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                    false
                )
                setHandleAudioBecomingNoisy(true)
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        _audioUnavailable.value = true
                        Log.e("EYA-ActiveSound", "Engine audio decoder/source unavailable", error)
                        pausePlayers()
                    }
                })
                setMediaItem(MediaItem.fromUri(
                    Uri.parse("android.resource://${context.packageName}/$resourceId")
                ))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
            }
        } catch (error: Resources.NotFoundException) {
            _audioUnavailable.value = true
            Log.w("EYA-ActiveSound", "Optional engine sound resource not installed", error)
            runCatching { candidate?.release() }
            null
        } catch (error: Exception) {
            _audioUnavailable.value = true
            Log.w("EYA-ActiveSound", "Engine WAV unavailable; continuing silently", error)
            runCatching { candidate?.release() }
            null
        }
    }

    private fun selectProfile(profile: EngineProfile): Boolean {
        if (profile == currentProfile && idle != null && acceleration != null) return true
        runCatching { idle?.release() }
        runCatching { acceleration?.release() }
        idle = player(profile.idle)
        acceleration = player(profile.accel)
        if (idle == null || acceleration == null) {
            runCatching { idle?.release() }
            runCatching { acceleration?.release() }
            idle = null
            acceleration = null
            currentProfile = null
            return false // Missing assets fail closed; never crash the UI.
        }
        currentProfile = profile
        _audioUnavailable.value = false
        return true
    }

    /** Call at most once per real OBD sample; time is the sample timestamp. */
    fun update(
        settings: UserSettings,
        speedKmh: Double?,
        speedAtMs: Long?,
        foreground: Boolean
    ) {
        main.post {
            activeInForeground = foreground
            enabled = settings.isEngineSoundEnabled
            volume = settings.engineVolume.coerceIn(0f, 1.5f)
            lastSpeedInput = speedKmh
            lastInputTime = speedAtMs
            val now = System.currentTimeMillis()
            // Even if the ViewModel supplies bad/stale data, never play audio.
            val validSpeed = speedKmh?.takeIf { it.isFinite() && it in 0.0..300.0 }
            val fresh = speedAtMs != null && speedAtMs <= now + 2_000L &&
                now - speedAtMs in 0L..10_000L
            if (!foreground || !enabled || previewing || validSpeed == null || !fresh) {
                if (!previewing) pausePlayers()
                return@post
            }
            if (!selectProfile(EngineProfile.from(settings.engineSoundProfile))) return@post
            if (lastSpeedAtMs == speedAtMs) {
                // Poll tick without a new CAN measurement: do not reset accel mode.
                applyVolumes()
                return@post
            }
            val deltaSeconds =
                lastSpeedAtMs?.let { (speedAtMs - it).toDouble() / 1000.0 } ?: 0.0
            val decision = EngineMotionPolicy.decide(
                speedKmh, lastSpeedKmh, deltaSeconds
            )
            lastSpeedKmh = speedKmh
            lastSpeedAtMs = speedAtMs
            runCatching {
                acceleration?.playbackParameters = PlaybackParameters(1f, decision.pitch)
                idle?.playbackParameters = PlaybackParameters(1f, 0.95f)
                when (decision.mode) {
                    EngineMotionPolicy.Mode.ACCELERATING -> {
                        idle?.pause()
                        acceleration?.play()
                    }
                    EngineMotionPolicy.Mode.IDLE -> {
                        acceleration?.pause()
                        idle?.play()
                    }
                    EngineMotionPolicy.Mode.SILENT -> pausePlayers()
                }
                applyVolumes()
            }.onFailure { error ->
                Log.e("EYA-ActiveSound", "Playback update failed", error)
                pausePlayers()
            }
        }
    }

    fun setSpeechDucking(active: Boolean) {
        main.post {
            ducked = active
            applyVolumes()
        }
    }

    private fun applyVolumes() {
        // Existing level is restored after TTS; no system stream is modified.
        val normalized = (volume / 1.5f).coerceIn(0f, 1f)
        val multiplier = if (ducked) 0.2f else 1f
        runCatching { idle?.volume = normalized * multiplier * 0.75f }
            .onFailure { Log.w("EYA-ActiveSound", "Idle volume unavailable", it) }
        runCatching { acceleration?.volume = normalized * multiplier }
            .onFailure { Log.w("EYA-ActiveSound", "Acceleration volume unavailable", it) }
    }

    fun preview(settings: UserSettings) {
        main.post {
            if (!activeInForeground) return@post
            previewToken++
            val token = previewToken
            previewing = true
            volume = settings.engineVolume.coerceIn(0f, 1.5f)
            if (!selectProfile(EngineProfile.from(settings.engineSoundProfile))) {
                previewing = false
                return@post
            }
            runCatching {
                idle?.pause()
                acceleration?.playbackParameters = PlaybackParameters(1f, 1.25f)
                acceleration?.play()
                applyVolumes()
            }.onFailure { error ->
                Log.e("EYA-ActiveSound", "Preview playback failed", error)
                previewing = false
                pausePlayers()
                return@post
            }
            main.postDelayed({
                if (previewToken == token) {
                    previewing = false
                    pausePlayers()
                }
            }, 3_000L)
        }
    }

    private fun pausePlayers() {
        runCatching { idle?.pause() }
        runCatching { acceleration?.pause() }
        lastSpeedAtMs = null
        lastSpeedKmh = null
    }

    fun onResume() { main.post { activeInForeground = true } }

    fun onPause() {
        main.post {
            activeInForeground = false
            previewing = false
            previewToken++
            pausePlayers()
        }
    }

    /** Use only when application-owned audio lifetime ends. */
    fun release() {
        main.post {
            pausePlayers()
            runCatching { idle?.release() }
            runCatching { acceleration?.release() }
            idle = null
            acceleration = null
            currentProfile = null
        }
    }
}
```

## nativecockpit/src/main/java/com/evcockpit/presentation/voice/NativeVoiceGuide.kt

```kotlin
package com.evcockpit.presentation.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Activity-scoped TTS: foregound-only, focus-safe, no stream-volume changes.
 * Media ducking is requested from Android, not implemented by changing
 * another app's volume. An unavailable tr-TR voice fails closed.
 */
class NativeVoiceGuide(context: Context, private val audioSignal: AppAudioDucking) {
    private val logTag = "EYA-Voice"

    companion object {
        private val GREETING_LOCALE = Locale.forLanguageTag("tr-TR")

        /** Voice has no guaranteed gender property: prefer explicit engine labels only. */
        fun availableTurkishVoices(engine: TextToSpeech): List<Voice> =
            runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
                .filter { v ->
                    v.locale.language.equals("tr", true) &&
                        v.locale.country.equals("TR", true) &&
                        !v.isNetworkConnectionRequired
                }
                .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.name })

        fun selectTurkishGreetingVoice(
            engine: TextToSpeech,
            preferredName: String? = null
        ): Boolean {
            if (engine.setLanguage(GREETING_LOCALE) < TextToSpeech.LANG_AVAILABLE) return false
            val installed = availableTurkishVoices(engine)
            val preferred = installed.firstOrNull { it.name == preferredName }
            val labelledFemale = installed.firstOrNull { v ->
                val n = v.name.lowercase(Locale.ROOT)
                listOf("female", "woman", "kadın", "kadin").any(n::contains)
            }
            // When no labelled female is installed use the system Turkish default.
            val selection = preferred ?: labelledFemale
            if (selection != null &&
                runCatching { engine.setVoice(selection) == TextToSpeech.SUCCESS }
                    .getOrDefault(false)
            ) return true
            return engine.setLanguage(GREETING_LOCALE) >= TextToSpeech.LANG_AVAILABLE
        }

    }

    enum class Status { LOADING, READY, TURKISH_VOICE_MISSING, INIT_FAILED, AUDIO_FOCUS_DENIED }

    var status: Status by mutableStateOf(Status.LOADING)
        private set
    var lastError: String? by mutableStateOf(null)
        private set

    private val main = Handler(Looper.getMainLooper())
    private val ducking = AudioDuckingManager(context) { stop() }
    private val attrs: AudioAttributes = ducking.attributes
    private var activeUtterance: String? = null
    private var startedDuckOwner: String? = null
    private var onStarted: (() -> Unit)? = null
    private var onFailed: (() -> Unit)? = null
    private var sequence = 0L
    private var closed = false

    private var tts: TextToSpeech? = null
    init {
        tts = try {
            TextToSpeech(context.applicationContext) { result ->
        main.post {
                if (closed) return@post
                val engine = tts
                if (result != TextToSpeech.SUCCESS || engine == null) {
                    status = Status.INIT_FAILED
                    lastError = "Could not initialize the TTS engine"
                    Log.e(logTag, "TextToSpeech initialization failed: result=$result")
                    return@post
                }
                val locale = GREETING_LOCALE
                val supported = TtsDebugLogger.languageAvailability(engine)
                val selected = if (supported >= TextToSpeech.LANG_AVAILABLE)
                    engine.setLanguage(locale) else TextToSpeech.LANG_NOT_SUPPORTED
                TtsDebugLogger.selectedLanguage(selected)
                if (selected < TextToSpeech.LANG_AVAILABLE) {
                    status = Status.TURKISH_VOICE_MISSING
                    lastError = "Türkçe ses verisi bulunamadı"
                    Log.e(logTag, "tr-TR voice unavailable: supported=$supported selected=$selected")
                } else {
                    engine.setAudioAttributes(attrs)
                    engine.setSpeechRate(1.0f)
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String) {
                            main.post {
                                if (!closed && activeUtterance == utteranceId) {
                                    startedDuckOwner?.let(audioSignal::end)
                                    startedDuckOwner = "navigation:" + utteranceId
                                    startedDuckOwner?.let(audioSignal::begin)
                                    Log.d(logTag, "Utterance started")
                                    TtsDebugLogger.event("utterance onStart")
                                    onStarted?.invoke()
                                    onStarted = null
                                    // After onStart, a future onError is observable
                                    // but must not mark this already-spoken stage retryable.
                                    onFailed = null
                                }
                            }
                        }
                        override fun onDone(utteranceId: String) { finish(utteranceId) }
                        override fun onError(utteranceId: String) {
                            synthesisError(utteranceId, "TTS speech failed")
                        }
                        override fun onError(utteranceId: String, errorCode: Int) {
                            TtsDebugLogger.utteranceError(errorCode)
                            synthesisError(utteranceId, "TTS speech error: $errorCode")
                        }
                        override fun onStop(utteranceId: String, interrupted: Boolean) {
                            finish(utteranceId)
                        }
                    })
                    status = Status.READY
                }
            }
            }
        } catch (error: RuntimeException) {
            status = Status.INIT_FAILED
            lastError = "Text-to-speech engine unavailable"
            Log.w(logTag, "TextToSpeech constructor failed; speech disabled", error)
            null
        }
    }

    /** Returns false rather than speaking without a valid audio-focus grant. */
    fun speak(
        text: String,
        volume: Float,
        onUtteranceStarted: (() -> Unit)? = null,
        onUtteranceFailed: (() -> Unit)? = null
    ): Boolean {
        if (closed || (status != Status.READY && status != Status.AUDIO_FOCUS_DENIED) ||
            text.isBlank() || text.length > 190)
            return false
        val engine = tts ?: return false
        stopCurrent(notifyFailure = false) // Replace old utterance without canceling the NEW cue.
        if (!ducking.acquire()) {
            status = Status.AUDIO_FOCUS_DENIED
            lastError = "Could not obtain audio focus"
            Log.w(logTag, "Navigation audio focus was not granted")
            return false
        }
        status = Status.READY
        lastError = null
        val id = "eya-voice-${++sequence}"
        activeUtterance = id
        onStarted = onUtteranceStarted
        onFailed = onUtteranceFailed
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,volume.coerceIn(.15f,1f))
        }
        val accepted = try {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, id) == TextToSpeech.SUCCESS
        } catch (_: RuntimeException) {
            false
        }
        if (!accepted) {
            lastError = "Could not queue the TTS announcement"
            Log.e(logTag, "TextToSpeech.speak() rejected the utterance")
            stop()
            return false
        }
        return true
    }

    fun stop() = stopCurrent(notifyFailure = true)

    private fun stopCurrent(notifyFailure: Boolean) {
        runCatching { tts?.stop() }
        activeUtterance = null
        onStarted = null
        val fail = onFailed
        onFailed = null
        // A replacement maneuver may already be pending: its policy state
        // must not be cleared by the PREVIOUS utterance's callback.
        if (notifyFailure) fail?.invoke()
        releaseFocus()
    }

    // The engine can accept speak() and fail later. Surface that failure.
    private fun synthesisError(id: String, reason: String) {
        main.post {
            if (!closed && activeUtterance == id) {
                lastError = reason
                Log.e(logTag, "Utterance synthesis failed id=$id: $reason")
                activeUtterance = null
                onStarted = null
                val fail = onFailed
                onFailed = null
                fail?.invoke()
                releaseFocus()
            }
        }
    }

    private fun finish(id: String) {
        main.post {
            if (id == activeUtterance) {
                Log.d(logTag, "Utterance finished")
                TtsDebugLogger.event("utterance finished, audio focus released")
                activeUtterance = null
                onStarted = null
                val fail = onFailed
                onFailed = null
                fail?.invoke()
                releaseFocus()
            }
        }
    }

    private fun releaseFocus() {
        startedDuckOwner?.let(audioSignal::end)
        startedDuckOwner = null
        ducking.release()
    }

    fun close() {
        if (closed) return
        closed = true
        stop()
        runCatching { tts?.shutdown() }
        tts = null
        ducking.close()
    }
}
```

## nativecockpit/src/main/java/com/evcockpit/presentation/voice/TtsDebugLogger.kt

```kotlin
package com.evcockpit.presentation.voice

import androidx.compose.ui.res.stringResource
import com.evcockpit.R

import android.speech.tts.TextToSpeech
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.util.Locale

/** Avoid logging text, location, or personal identifiers. */
object TtsDebugLogger {
    private const val TAG = "EYA-TTS-UAT"

    fun languageAvailability(tts: TextToSpeech): Int {
        val result = tts.isLanguageAvailable(Locale.forLanguageTag("tr-TR"))
        val label = when (result) {
            TextToSpeech.LANG_MISSING_DATA -> "LANG_MISSING_DATA"
            TextToSpeech.LANG_NOT_SUPPORTED -> "LANG_NOT_SUPPORTED"
            TextToSpeech.LANG_AVAILABLE -> "LANG_AVAILABLE"
            TextToSpeech.LANG_COUNTRY_AVAILABLE -> "LANG_COUNTRY_AVAILABLE"
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "LANG_COUNTRY_VAR_AVAILABLE"
            else -> "UNKNOWN(" + result + ")"
        }
        if (result < TextToSpeech.LANG_AVAILABLE)
            Log.e(TAG, "tr-TR availability: " + label)
        else Log.i(TAG, "tr-TR availability: " + label)
        return result
    }

    fun selectedLanguage(code: Int) {
        if (code < TextToSpeech.LANG_AVAILABLE)
            Log.e(TAG, "setLanguage(tr-TR) failed: " + code)
        else Log.i(TAG, "setLanguage(tr-TR) ready: " + code)
    }

    fun utteranceError(code: Int) {
        Log.e(TAG, "UtteranceProgressListener.onError: " + code)
    }

    fun event(event: String) {
        Log.d(TAG, event)
    }
}

@Composable
fun TtsPackageWarningDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0A0F1A),
            contentColor = Color.White,
            border = BorderStroke(1.dp, Color(0xFF46687E))
        ) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(stringResource(R.string.ux_tts_missing), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.ux_tts_help))
                Button(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent("com.android.settings.TTS_SETTINGS")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.onFailure {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                    onDismiss()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ux_open_tts_settings))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.native_ok), color = Color(0xFF00E676))
                }
            }
        }
    }
}
```

## nativecockpit/src/main/java/com/evcockpit/domain/usecase/ManeuverGuidancePolicy.kt

```kotlin
package com.evcockpit.domain.usecase

import com.evcockpit.domain.model.GeoCoordinate
import com.evcockpit.domain.model.GpsFix
import com.evcockpit.domain.model.RouteSnapshot
import com.evcockpit.domain.model.validRouteGeometry
import kotlin.math.cos
import kotlin.math.hypot

/** Pure Kotlin. No speech and no fake measurements. A route provider must
 * attach a stable routeId, current step id/coordinate and real road geometry.
 * Only fresh, precise GPS is eligible; the result is still approximate unless
 * a proper map matcher supplies a confirmed driving lane position.
 */
class ManeuverGuidancePolicy {
    private data class Projection(val metersAlong: Double, val offRoute: Double)
    private var routeAndStep: String? = null
    private var activeRouteId: String? = null
    // A GPS jitter or a recalculated next step must not repeat an already spoken cue.
    private val spokenStagesByStep = LinkedHashMap<String, Int>()
    private var lastLevel = -1
    private var proposedLevel: Int? = null
    private var proposedAtMs = 0L
    private var proposalSequence = 0L
    var pendingCueToken: Long? = null
        private set
    private var lastFixTime = 0L
    private var lastSpeechTime = 0L

    fun reset() {
        routeAndStep = null
        activeRouteId = null
        spokenStagesByStep.clear()
        lastLevel = -1
        clearPending()
        lastFixTime = 0L
        lastSpeechTime = 0L
    }

    /** Returns a single announcement in the route instruction language or null. Never synthesizes routes. */
    fun nextAnnouncement(route: RouteSnapshot?, fix: GpsFix?, nowMs: Long): String? {
        if (route == null || fix == null) return null
        val id = route.routeId?.trim()?.takeIf { it.isNotEmpty() && it.length <= 128 } ?: return null
        val turn = route.nextManeuver ?: return null
        val stepId = turn.id.trim().takeIf { it.isNotEmpty() && it.length <= 128 } ?: return null
        val instruction = turn.instructionTr.trim().takeIf {
            it.length in 4..140 && it.none { c -> c.isISOControl() || c == '<' || c == '>' }
        } ?: return null
        val path = route.routePoints
        if (!validRouteGeometry(path) || !turn.location.valid()) return null
        if (!fix.usable(nowMs) || fix.accuracyMeters > 35f) return null

        val key = "$id|$stepId"
        if (id != activeRouteId) {
            // Only a genuinely new route can discard deduplication history.
            activeRouteId = id
            spokenStagesByStep.clear()
            lastSpeechTime = 0L
        }
        if (key != routeAndStep) {
            routeAndStep = key
            lastLevel = spokenStagesByStep[key] ?: -1
            clearPending()
            lastFixTime = 0L
        }
        // The Compose UI can update every second with the same GPS fix.
        if (fix.recordedAtMillis <= lastFixTime) return null
        lastFixTime = fix.recordedAtMillis

        // Distance is projected ALONG the actual route, not crow-flies distance.
        val vehicle = nearest(path, fix.coordinate) ?: return null
        val maneuver = nearest(path, turn.location) ?: return null
        if (vehicle.offRoute > 35.0 || maneuver.offRoute > 25.0) return null
        val ahead = maneuver.metersAlong - vehicle.metersAlong
        if (ahead < -10.0 || ahead > 1_100.0) return null
        // Only two stages: announce at 200 m, then again at 50 m.
        // A driver joining close to a junction hears the 50 m stage only.
        val level = when {
            ahead <= 50.0 -> 1
            ahead <= 200.0 -> 0
            else -> return null
        }
        // A queued utterance remains pending until the Android engine emits
        // onStart. A 5s deadline avoids deadlock on broken TTS callbacks.
        if (proposedLevel != null && nowMs - proposedAtMs in 0L..4_999L) return null
        if (level <= lastLevel || (lastSpeechTime > 0 && nowMs - lastSpeechTime < 4_000L)) {
            clearPending()
            return null
        }
        // IMPORTANT: do not mark as spoken until TextToSpeech accepted the request.
        proposedLevel = level
        proposedAtMs = nowMs
        pendingCueToken = ++proposalSequence
        return (if (level == 0) "İki yüz metre sonra, " else "Elli metre sonra, ") +
            instruction
    }

    /** Called on the TTS engine's onStart callback, not on speak() enqueue. */
    fun confirmSpoken(nowMs: Long, token: Long? = pendingCueToken) {
        if (token == null || token != pendingCueToken) return
        proposedLevel?.let { level ->
            lastLevel = maxOf(lastLevel, level)
            routeAndStep?.let { key ->
                spokenStagesByStep[key] = maxOf(spokenStagesByStep[key] ?: -1, level)
                // Bound retained state for long routes.
                if (spokenStagesByStep.size > 150) {
                    spokenStagesByStep.remove(spokenStagesByStep.keys.first())
                }
            }
            lastSpeechTime = nowMs
        }
        clearPending()
    }

    /** A failed/interrupted utterance may be tried at the next fresh fix. */
    fun cancelPending(token: Long? = pendingCueToken) {
        if (token != null && token == pendingCueToken) clearPending()
    }

    private fun clearPending() {
        proposedLevel = null
        proposedAtMs = 0L
        pendingCueToken = null
    }

    private fun nearest(route: List<GeoCoordinate>, p: GeoCoordinate): Projection? {
        val metersPerLon = 111_320.0 * cos(Math.toRadians(p.latitude))
        val metersPerLat = 111_195.0
        var bestOffset = Double.POSITIVE_INFINITY
        var bestAlong = 0.0
        var cumulative = 0.0
        for (i in 0 until route.lastIndex) {
            val a = route[i]
            val b = route[i+1]
            val ax = (a.longitude-p.longitude)*metersPerLon
            val ay = (a.latitude-p.latitude)*metersPerLat
            val dx = (b.longitude-a.longitude)*metersPerLon
            val dy = (b.latitude-a.latitude)*metersPerLat
            val planarLength = hypot(dx,dy)
            val length = VehicleMotionMath.distanceMeters(a, b)
            if (planarLength < 0.1 || !length.isFinite() || length < 0.1) continue
            val t = (-(ax*dx + ay*dy)/(planarLength*planarLength)).coerceIn(0.0,1.0)
            val offset = hypot(ax+t*dx, ay+t*dy)
            if (offset < bestOffset) {
                bestOffset = offset
                bestAlong = cumulative + t*length
            }
            cumulative += length
        }
        return if (bestOffset.isFinite()) Projection(bestAlong,bestOffset) else null
    }
}
```


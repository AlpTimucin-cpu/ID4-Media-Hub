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
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.evcockpit.domain.repository.ObdConnectionController
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
    private val navigation: NavigationViewModel by viewModels()
    private val charging: ChargingViewModel by viewModels()
    private val chargePlan: RouteChargePlanViewModel by viewModels()
    private val weather: RouteWeatherViewModel by viewModels()
    @Inject lateinit var obd: ObdConnectionController

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
        super.onCreate(savedInstanceState)
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
            CockpitTheme {
                CockpitScreen(
                    state = ui.cockpit,
                    routeState = ui.routeState,
                    loadStatus = ui.loadStatus,
                    routePlannerUi = ui.planner,
                    onRouteOriginChange = navigation::editOrigin,
                    onRouteDestinationChange = navigation::editDestination,
                    onRouteOriginSelected = navigation::selectOrigin,
                    onRouteDestinationSelected = navigation::selectDestination,
                    onRouteFromGps = { if (ui.simulationEnabled) navigation.useCurrentGps() else requestGps(true) },
                    onRoutePlan = navigation::plan,
                    onRouteAlternative = navigation::selectAlternative,
                    onRouteClear = navigation::clearRoute,
                    onGpsClick = { if (!ui.simulationEnabled) requestGps(false) },
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
                    onSimulationToggle = { enabled ->
                        // Never mix a genuine Bluetooth session with synthetic frames.
                        if (enabled) disconnectObd()
                        navigation.setSimulationEnabled(enabled)
                    }
                )
            }
        }
    }
    override fun onResume() {
        super.onResume()
        navigation.setForeground(true)
        val allowed = listOf(Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allowed) navigation.startGps()
    }
    override fun onPause() {
        navigation.setForeground(false)
        // OBD is retained only while the user-started connectedDevice FGS runs.
        super.onPause()
    }
}

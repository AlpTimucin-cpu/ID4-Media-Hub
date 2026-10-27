package tr.id4.mediahub.nativecockpit

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import tr.id4.mediahub.nativecockpit.domain.repository.ObdConnectionController
import tr.id4.mediahub.nativecockpit.presentation.cockpit.CockpitScreen
import tr.id4.mediahub.nativecockpit.presentation.cockpit.CockpitViewModel
import tr.id4.mediahub.nativecockpit.presentation.charging.ChargingViewModel
import tr.id4.mediahub.nativecockpit.presentation.route.RoutePlannerViewModel
import tr.id4.mediahub.nativecockpit.presentation.cockpit.EyaTheme
import tr.id4.mediahub.nativecockpit.presentation.voice.NativeVoiceGuide
import tr.id4.mediahub.nativecockpit.presentation.voice.VoicePreference
import tr.id4.mediahub.nativecockpit.domain.usecase.ManeuverGuidancePolicy
import tr.id4.mediahub.nativecockpit.car.CarNavigationOwnership

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var obd: ObdConnectionController
    private val bluetoothPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) obd.refreshPaired() else obd.permissionDenied() }
    private fun refreshObdDevices() {
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED) {
            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else obd.refreshPaired()
    }

    private val viewModel: CockpitViewModel by viewModels()
    private val chargingViewModel: ChargingViewModel by viewModels()
    private val routePlannerViewModel: RoutePlannerViewModel by viewModels()
    private val guide by lazy { NativeVoiceGuide(applicationContext) }
    private val preferences by lazy { VoicePreference(applicationContext) }
    private val voicePolicy = ManeuverGuidancePolicy()
    private var foreground by mutableStateOf(false)
    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            viewModel.startGps()
            if (locationForRoute) routePlannerViewModel.useCurrentGps()
        } else if (locationForRoute) routePlannerViewModel.permissionDenied()
        locationForRoute = false
    }
    private var locationForRoute = false

    private fun hasGpsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun askForGpsPermission(forRoute: Boolean) {
        locationForRoute = forRoute
        locationPermission.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val charging by chargingViewModel.uiState.collectAsStateWithLifecycle()
            val routePlanner by routePlannerViewModel.uiState.collectAsStateWithLifecycle()
            val obdConnection by obd.connection.collectAsStateWithLifecycle()
            // Compose state reflects persisted user preference; the engine is Activity-owned.
            var voiceEnabled by remember { mutableStateOf(preferences.enabled) }
            var voiceVolume by remember { mutableStateOf(preferences.volume) }
            val voiceStatus=guide.status
            val carNavigating by CarNavigationOwnership.navigating.collectAsStateWithLifecycle()
            LaunchedEffect(state.route,state.gpsFix,voiceEnabled,voiceStatus,foreground,carNavigating) {
                if (foreground && !carNavigating && voiceEnabled &&
                    voiceStatus==NativeVoiceGuide.Status.READY) {
                    val message=voicePolicy.nextAnnouncement(
                        state.route,state.gpsFix,System.currentTimeMillis())
                    if (message!=null) guide.speak(message,voiceVolume)
                } else if (!foreground || !voiceEnabled || carNavigating) guide.stop()
            }
            EyaTheme {
                CockpitScreen(state,
                obdConnection=obdConnection,
                onObdRefresh={refreshObdDevices()},
                onObdConnect=obd::connect,
                onObdDisconnect=obd::disconnect,
                routePlannerUi=routePlanner,
                onRouteOriginChange=routePlannerViewModel::editOrigin,
                onRouteDestinationChange=routePlannerViewModel::editDestination,
                onRouteOriginSelected=routePlannerViewModel::chooseOrigin,
                onRouteDestinationSelected=routePlannerViewModel::chooseDestination,
                onRouteFromGps={
                    if (hasGpsPermission()) {
                        viewModel.startGps()
                        routePlannerViewModel.useCurrentGps()
                    } else askForGpsPermission(forRoute = true)
                },
                onRoutePlan=routePlannerViewModel::plan,
                onRouteAlternative=routePlannerViewModel::chooseAlternative,
                onRouteClear=routePlannerViewModel::clear,
                onGpsClick = {
                    if (hasGpsPermission()) viewModel.startGps()
                    else askForGpsPermission(forRoute = false)
                },chargingUi=charging,
                  onStationFilter=chargingViewModel::changeFilter,
                  onMapViewport=chargingViewModel::viewportChanged,
                  onRefreshStations=chargingViewModel::refresh,
                  voiceEnabled=voiceEnabled,
                  onVoiceToggle={
                      voiceEnabled=!voiceEnabled
                      preferences.enabled=voiceEnabled
                      if(!voiceEnabled) guide.stop()
                  },
                  voiceVolume=voiceVolume,
                  onVoiceVolumeChange={ newVolume ->
                      voiceVolume=newVolume.coerceIn(.15f,1f)
                      preferences.volume=voiceVolume
                  },
                  voiceStatus=voiceStatus,
                  onVoiceTest={
                      // Manually requested test sentence; never a simulated turn.
                      if(voiceEnabled) guide.speak(
                          "Elektrikli Yol Arkadaşım. Türkçe ses testi.",voiceVolume)
                  })
            }
        }
    }
    override fun onResume() {
        super.onResume()
        foreground = true
        if (hasGpsPermission()) viewModel.startGps()
    }
    override fun onPause() {
        foreground = false
        guide.stop() // Do not speak or track in background without a foreground service.
        obd.disconnect() // Explicit foreground-only OBD; no hidden background connection.
        viewModel.stopGps()
        super.onPause()
    }
    override fun onDestroy() {
        guide.close()
        super.onDestroy()
    }
}

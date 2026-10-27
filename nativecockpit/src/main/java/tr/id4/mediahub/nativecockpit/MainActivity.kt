package tr.id4.mediahub.nativecockpit

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import tr.id4.mediahub.nativecockpit.presentation.cockpit.CockpitScreen
import tr.id4.mediahub.nativecockpit.presentation.cockpit.CockpitViewModel
import tr.id4.mediahub.nativecockpit.presentation.charging.ChargingViewModel
import tr.id4.mediahub.nativecockpit.presentation.route.RoutePlannerViewModel
import tr.id4.mediahub.nativecockpit.presentation.cockpit.EyaTheme
import tr.id4.mediahub.nativecockpit.presentation.voice.NativeVoiceGuide
import tr.id4.mediahub.nativecockpit.presentation.voice.VoicePreference
import tr.id4.mediahub.nativecockpit.domain.usecase.ManeuverGuidancePolicy

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: CockpitViewModel by viewModels()
    private val chargingViewModel: ChargingViewModel by viewModels()
    private val routePlannerViewModel: RoutePlannerViewModel by viewModels()
    private val guide by lazy { NativeVoiceGuide(applicationContext) }
    private val preferences by lazy { VoicePreference(applicationContext) }
    private val voicePolicy = ManeuverGuidancePolicy()
    private var foreground by mutableStateOf(false)
    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startGps() }

    private fun hasGpsPermission(): Boolean = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val charging by chargingViewModel.uiState.collectAsStateWithLifecycle()
            val routePlanner by routePlannerViewModel.uiState.collectAsStateWithLifecycle()
            // Compose state reflects persisted user preference; the engine is Activity-owned.
            var voiceEnabled by remember { mutableStateOf(preferences.enabled) }
            var voiceVolume by remember { mutableStateOf(preferences.volume) }
            val voiceStatus=guide.status
            LaunchedEffect(state.route,state.gpsFix,voiceEnabled,voiceStatus,foreground) {
                if (foreground && voiceEnabled && voiceStatus==NativeVoiceGuide.Status.READY) {
                    val message=voicePolicy.nextAnnouncement(
                        state.route,state.gpsFix,System.currentTimeMillis())
                    if (message!=null) guide.speak(message,voiceVolume)
                } else if (!foreground || !voiceEnabled) guide.stop()
            }
            EyaTheme {
                CockpitScreen(state,
                routePlannerUi=routePlanner,
                onRouteOriginChange=routePlannerViewModel::editOrigin,
                onRouteDestinationChange=routePlannerViewModel::editDestination,
                onRouteOriginSelected=routePlannerViewModel::chooseOrigin,
                onRouteDestinationSelected=routePlannerViewModel::chooseDestination,
                onRouteFromGps={
                    if(hasGpsPermission()){
                        viewModel.startGps()
                        routePlannerViewModel.useCurrentGps()
                    }else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                onRoutePlan=routePlannerViewModel::plan,
                onRouteAlternative=routePlannerViewModel::chooseAlternative,
                onRouteClear=routePlannerViewModel::clear,
                onGpsClick = {
                    if (hasGpsPermission()) viewModel.startGps()
                    else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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
        viewModel.stopGps()
        super.onPause()
    }
    override fun onDestroy() {
        guide.close()
        super.onDestroy()
    }
}

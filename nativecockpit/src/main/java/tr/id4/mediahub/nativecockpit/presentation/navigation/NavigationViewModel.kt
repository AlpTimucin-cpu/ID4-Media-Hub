package tr.id4.mediahub.nativecockpit.presentation.navigation

import android.content.Context
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tr.id4.mediahub.nativecockpit.car.CarNavigationOwnership
import tr.id4.mediahub.nativecockpit.data.location.FusedCurrentLocationProvider
import tr.id4.mediahub.nativecockpit.domain.model.*
import tr.id4.mediahub.nativecockpit.domain.repository.LocationRepository
import tr.id4.mediahub.nativecockpit.domain.repository.RouteRepository
import tr.id4.mediahub.nativecockpit.domain.repository.VehicleRepository
import tr.id4.mediahub.nativecockpit.domain.usecase.ManeuverGuidancePolicy
import tr.id4.mediahub.nativecockpit.domain.usecase.RouteProgressTracker
import tr.id4.mediahub.nativecockpit.presentation.cockpit.CockpitUiState
import tr.id4.mediahub.nativecockpit.presentation.route.RoutePlannerUiState
import tr.id4.mediahub.nativecockpit.presentation.voice.NativeVoiceGuide
import tr.id4.mediahub.nativecockpit.presentation.voice.VoicePreference

data class VoiceUiState(
    val enabled: Boolean = true,
    val volume: Float = 0.8f,
    val status: NativeVoiceGuide.Status = NativeVoiceGuide.Status.LOADING
)

data class NavigationUiState(
    val cockpit: CockpitUiState = CockpitUiState(),
    val planner: RoutePlannerUiState = RoutePlannerUiState(),
    val voice: VoiceUiState = VoiceUiState()
)

/** Single source of truth for the route editor AND the main navigation panel. */
data class NavigationRouteState(
    val startLocation: GeoCoordinate? = null,
    val startLabel: String? = null,
    val destination: RoutePlace? = null,
    val routeResult: RouteSnapshot? = null,
    val calculating: Boolean = false,
    val error: String? = null
)

/** Single Activity ViewModel; one shared RouteRepository feeds planner, cockpit and car. */
@HiltViewModel
class NavigationViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val routes: RouteRepository,
    private val locationRepository: LocationRepository,
    private val currentLocation: FusedCurrentLocationProvider,
    private val vehicleRepository: VehicleRepository
) : ViewModel() {
    private val prefs = VoicePreference(context)
    private val guide = NativeVoiceGuide(context)
    private val policy = ManeuverGuidancePolicy()
    private val progress = RouteProgressTracker()
    private val ttsProgress = RouteProgressTracker()
    private val _foreground = MutableStateFlow(false)
    private val _gpsError = MutableStateFlow<String?>(null)
    val gpsError: StateFlow<String?> = _gpsError.asStateFlow()
    private val _planner = MutableStateFlow(RoutePlannerUiState())
    private val _voice = MutableStateFlow(
        VoiceUiState(prefs.enabled, prefs.volume, guide.status)
    )
    private var originJob: Job? = null
    private var destinationJob: Job? = null
    private var gpsJob: Job? = null
    private var planningJob: Job? = null

    /** Tick only expires previously measured values, never invents a new GPS fix. */
    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }
    private val cockpit = combine(
        vehicleRepository.telemetry,
        routes.currentRoute,
        locationRepository.gpsFix,
        ticker
    ) { telemetry, route, gps, now ->
        val usableGps = gps?.takeIf { it.usable(now) }
        CockpitUiState(
            socPercent = telemetry.socPercent.current(now),
            speedKmh = telemetry.speedKmh.current(now),
            voltageV = telemetry.voltageV.current(now),
            batteryTemperatureC = telemetry.batteryTemperatureC.current(now),
            powerKw = telemetry.powerKw.current(now),
            route = progress.enrich(route, usableGps, now),
            gpsFix = usableGps
        )
    }
    private val plannerState = combine(
        _planner, routes.options, routes.selectedIndex
    ) { state, options, index ->
        state.copy(options = options, selectedIndex = index)
    }
    val uiState: StateFlow<NavigationUiState> = combine(
        cockpit, plannerState, _voice
    ) { cockpit, planner, voice ->
        NavigationUiState(cockpit, planner, voice)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), NavigationUiState())

    val routeState: StateFlow<NavigationRouteState> = combine(
        _planner, routes.currentRoute
    ) { selection, active ->
        NavigationRouteState(
            startLocation = selection.startLocation,
            startLabel = if (selection.startLocation == null) null
                else selection.originText.takeIf(String::isNotBlank),
            destination = selection.destinationSelected,
            routeResult = active,
            calculating = selection.planning,
            error = selection.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), NavigationRouteState())

    init {
        viewModelScope.launch {
            snapshotFlow { guide.status }.collect { status ->
                _voice.update { it.copy(status = status) }
            }
        }
        // TTS only with real OSRM route + fresh GPS + foreground Activity.
        // Android Auto has its own owner; never speak twice simultaneously.
        viewModelScope.launch {
            combine(
                routes.currentRoute,
                locationRepository.gpsFix,
                _foreground,
                CarNavigationOwnership.navigating,
                _voice
            ) { route, gps, foreground, carNavigating, voice ->
                SpeechInputs(route, gps, foreground && !carNavigating, voice)
            }.collect { current ->
                if (!current.allowed || !current.voice.enabled ||
                    current.voice.status != NativeVoiceGuide.Status.READY &&
                        current.voice.status != NativeVoiceGuide.Status.AUDIO_FOCUS_DENIED) {
                    guide.stop()
                } else {
                    val now = System.currentTimeMillis()
                    val route = ttsProgress.enrich(current.route, current.fix, now)
                    policy.nextAnnouncement(route, current.fix, now)?.let { message ->
                        if (guide.speak(message, current.voice.volume)) {
                            policy.confirmSpoken(now)
                        }
                    }
                }
            }
        }
    }

    private data class SpeechInputs(
        val route: RouteSnapshot?, val fix: GpsFix?,
        val allowed: Boolean, val voice: VoiceUiState
    )

    fun setForeground(isForeground: Boolean) {
        _foreground.value = isForeground
        if (!isForeground) {
            guide.stop()
            locationRepository.stop()
        }
    }
    fun startGps() {
        _gpsError.value = null
        locationRepository.start()
    }
    fun stopGps() = locationRepository.stop()

    /** Last known Fused fix, delivered only after the user grants runtime permission. */
    @Suppress("DEPRECATION")
    fun acceptLastLocation(location: android.location.Location?) {
        if (location == null || location.isFromMockProvider || !location.hasAccuracy()) {
            reportGpsError("Konum okunamadı. GPS'i ve hassas konumu kontrol et.")
            return
        }
        val fix = GpsFix(
            coordinate = GeoCoordinate(location.latitude, location.longitude),
            accuracyMeters = location.accuracy,
            recordedAtMillis = location.time,
            bearingDegrees = location.bearing.takeIf { location.hasBearing() && it.isFinite() },
            speedMetersPerSecond = location.speed.takeIf { location.hasSpeed() && it.isFinite() }
        )
        if (fix.usable(System.currentTimeMillis())) {
            locationRepository.acceptFix(fix)
            _gpsError.value = null
        } else _gpsError.value = "Konum yeterince güncel veya hassas değil."

    }
    fun reportGpsError(message: String) {
        _gpsError.value = message
        _planner.update { it.copy(locatingGps = false, error = message) }
    }

    fun setVoiceEnabled(enabled: Boolean) {
        prefs.enabled = enabled
        _voice.update { it.copy(enabled = enabled) }
        if (!enabled) guide.stop()
    }
    fun setVoiceVolume(volume: Float) {
        val safe = volume.coerceIn(0.15f, 1f)
        prefs.volume = safe
        _voice.update { it.copy(volume = safe) }
    }
    fun testVoice() {
        val voice = _voice.value
        if (_foreground.value && voice.enabled &&
            (voice.status == NativeVoiceGuide.Status.READY ||
                 voice.status == NativeVoiceGuide.Status.AUDIO_FOCUS_DENIED)) {
            guide.speak("Elektrikli Yol Arkadaşım. Türkçe ses testi.", voice.volume)
        }
    }

    fun editOrigin(value: String) {
        val text = value.take(100)
        originJob?.cancel(); gpsJob?.cancel(); planningJob?.cancel()
        routes.clear() // never show an obsolete route for a newly edited origin
        _planner.update { it.copy(originText = text, originSelected = null,
            originFromGps = false, gpsOrigin = null, locatingGps = false,
            originMatches = emptyList(), error = null, planning = false) }
        originJob = search(text, true)
    }
    fun editDestination(value: String) {
        val text = value.take(100)
        destinationJob?.cancel(); planningJob?.cancel()
        routes.clear()
        _planner.update { it.copy(destinationText = text, destinationSelected = null,
            destinationMatches = emptyList(), planning = false, error = null) }
        destinationJob = search(text, false)
    }
    private fun search(value: String, origin: Boolean): Job = viewModelScope.launch {
        if (value.trim().length < 3) {
            _planner.update { if (origin) it.copy(searchingOrigin = false)
                else it.copy(searchingDestination = false) }
            return@launch
        }
        delay(550L) // protect the Photon demo server
        _planner.update { if (origin) it.copy(searchingOrigin = true)
            else it.copy(searchingDestination = true) }
        try {
            val found = routes.search(value)
            val error = if (found.isEmpty()) "Adres bulunamadı. Daha açık bir konum yaz." else null
            _planner.update { if (origin) it.copy(originMatches = found, searchingOrigin = false, error = error)
                else it.copy(destinationMatches = found, searchingDestination = false, error = error) }
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            val message = when (error) {
                is UnknownHostException -> "İnternet bağlantısı yok"
                is SocketTimeoutException -> "Photon sunucusu zaman aşımına uğradı"
                is IOException -> error.message ?: "Adres servisine ulaşılamadı"
                else -> "Adres araması başarısız: ${error.message?.take(80) ?: "bilinmeyen hata"}"
            }
            _planner.update { if (origin) it.copy(searchingOrigin = false, error = message)
                else it.copy(searchingDestination = false, error = message) }
        }
    }
    fun selectOrigin(place: RoutePlace) {
        originJob?.cancel(); gpsJob?.cancel(); planningJob?.cancel(); routes.clear()
        _planner.update { it.copy(originText = place.label, originSelected = place,
            originFromGps = false, gpsOrigin = null, locatingGps = false,
            originMatches = emptyList(), error = null, planning = false) }
    }
    fun selectDestination(place: RoutePlace) {
        destinationJob?.cancel(); planningJob?.cancel(); routes.clear()
        _planner.update { it.copy(destinationText = place.label, destinationSelected = place,
            destinationMatches = emptyList(), error = null, planning = false) }
    }
    /** Activity first verifies fine/coarse runtime permission. */
    fun useCurrentGps() {
        gpsJob?.cancel(); originJob?.cancel(); planningJob?.cancel(); routes.clear()
        gpsJob = viewModelScope.launch {
            _planner.update { it.copy(locatingGps = true, originFromGps = false,
                gpsOrigin = null, originSelected = null, originMatches = emptyList(), error = null) }
            try {
                val cached = locationRepository.gpsFix.value
                    ?.takeIf { it.usable(System.currentTimeMillis()) && it.accuracyMeters <= 35f }
                    ?.coordinate
                val result = if (cached != null)
                    Result.success(cached to locationRepository.gpsFix.value)
                else currentLocation.getCurrentMeasurement()

                // A single fresh Fused measurement updates the planner origin
                // and the same GPS StateFlow observed by MapLibre and the cockpit.
                result.getOrNull()?.second?.let(locationRepository::acceptFix)
                _gpsError.value = result.exceptionOrNull()?.message
                _planner.update { state ->
                    if (result.isSuccess) state.copy(
                        locatingGps = false, originFromGps = true,
                        gpsOrigin = result.getOrNull()?.first,
                        originText = "Güncel GPS konumum", error = null
                    ) else state.copy(locatingGps = false, originFromGps = false,
                        error = result.exceptionOrNull()?.message ?: "GPS konumu alınamadı")
                }
            } catch (cancel: CancellationException) { throw cancel }
        }
    }
    fun locationPermissionDenied() {
        _gpsError.value = "Konum izni verilmedi."
        gpsJob?.cancel()
        _planner.update { it.copy(locatingGps = false, originFromGps = false,
            gpsOrigin = null, error = "Konum izni verilmedi; başlangıç adresini elle seçebilirsin") }
    }
    fun plan() {
        if (!_planner.value.canCalculate) return
        planningJob?.cancel()
        planningJob = viewModelScope.launch {
            val state = _planner.value
            val start = state.startLocation
            val target = state.destinationSelected
            if (start == null || target == null) {
                _planner.update { it.copy(error = "Kalkış ve varış seçilmedi") }
                return@launch
            }
            _planner.update { it.copy(planning = true, error = null) }
            try {
                when (val result = routes.calculate(start, target)) {
                    is RouteCalculationResult.Success ->
                        _planner.update { it.copy(planning = false, error = null) }
                    is RouteCalculationResult.Failure ->
                        _planner.update { it.copy(planning = false, error = result.message) }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                _planner.update { it.copy(planning = false,
                    error = "Rota hatası: ${error.message?.take(100) ?: "beklenmeyen hata"}") }
            }
        }
    }
    fun selectAlternative(index: Int) { routes.choose(index); policy.reset() }
    fun clearRoute() {
        originJob?.cancel(); destinationJob?.cancel(); gpsJob?.cancel(); planningJob?.cancel()
        routes.clear(); policy.reset(); guide.stop(); _planner.value = RoutePlannerUiState()
    }
    override fun onCleared() {
        guide.close()
        locationRepository.stop()
        super.onCleared()
    }
}

package tr.id4.mediahub.nativecockpit.presentation.navigation

import android.content.Context
import tr.id4.mediahub.nativecockpit.BuildConfig
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
import kotlinx.coroutines.flow.collectLatest
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
import tr.id4.mediahub.nativecockpit.domain.usecase.EvBaselineEnergyEstimator
import tr.id4.mediahub.nativecockpit.domain.usecase.BaselineEnergyInputs
import tr.id4.mediahub.nativecockpit.domain.usecase.BaselineEnergyResult
import tr.id4.mediahub.nativecockpit.domain.usecase.DepartureSoc
import tr.id4.mediahub.nativecockpit.domain.usecase.ConsumptionBasis
import tr.id4.mediahub.nativecockpit.presentation.cockpit.CockpitUiState
import tr.id4.mediahub.nativecockpit.presentation.route.RoutePlannerUiState
import tr.id4.mediahub.nativecockpit.presentation.voice.NativeVoiceGuide
import tr.id4.mediahub.nativecockpit.presentation.voice.VoicePreference

data class VoiceUiState(
    val enabled: Boolean = true,
    val volume: Float = 0.8f,
    val status: NativeVoiceGuide.Status = NativeVoiceGuide.Status.LOADING
)

sealed interface RouteLoadStatus {
    data object Idle : RouteLoadStatus
    data object Empty : RouteLoadStatus
    data object Loading : RouteLoadStatus
    data class Success(val routeId: String) : RouteLoadStatus
    data class Error(val message: String) : RouteLoadStatus
}

data class NavigationUiState(
    val cockpit: CockpitUiState = CockpitUiState(),
    val planner: RoutePlannerUiState = RoutePlannerUiState(),
    val voice: VoiceUiState = VoiceUiState(),
    val routeState: NavigationRouteState = NavigationRouteState(),
    val loadStatus: RouteLoadStatus = RouteLoadStatus.Empty,
    val simulationEnabled: Boolean = false
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
    // Read only explicitly provisioned, independently verified calibration inputs.
    // No hard-coded battery capacity, consumption or assumed SoC.
    private val modelPrefs = context.applicationContext.getSharedPreferences(
        "eya_energy_calibration", Context.MODE_PRIVATE
    )
    private val guide = NativeVoiceGuide(context)
    private val policy = ManeuverGuidancePolicy()
    private val progress = RouteProgressTracker()
    private val ttsProgress = RouteProgressTracker()
    private val _foreground = MutableStateFlow(false)
    private val _simulationEnabled = MutableStateFlow(false)
    private val _simulationFrame = MutableStateFlow<SimulationFrame?>(null)
    private var gpsWaitSinceMs = System.currentTimeMillis()

    private data class DataSources(
        val telemetry: VehicleTelemetry,
        val gps: GpsFix?,
        val simulated: Boolean
    )

    // Real OBD and real FusedLocation are never overwritten by test data.
    private val sources = combine(
        vehicleRepository.telemetry, locationRepository.gpsFix,
        _simulationEnabled, _simulationFrame
    ) { telemetry, gps, enabled, frame ->
        if (enabled) DataSources(
            frame?.telemetry ?: VehicleTelemetry(),
            frame?.fix, true
        ) else DataSources(telemetry, gps, false)
    }
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
        sources, routes.currentRoute, ticker
    ) { source, route, now ->
        val usableGps = source.gps?.takeIf { it.usable(now) }
        if (!source.simulated && usableGps != null) gpsWaitSinceMs = now
        val telemetry = source.telemetry
        val energy = estimateEnergy(route, telemetry, now, source.simulated)
        val baseRoute = progress.enrich(route, usableGps, now)
        val visualRoute = if (energy is BaselineEnergyResult.Estimate) {
            baseRoute?.copy(estimatedArrivalSocPercent = energy.arrivalSocPercent)
        } else baseRoute?.copy(estimatedArrivalSocPercent = null)
        CockpitUiState(
            socPercent = telemetry.socPercent.current(now),
            speedKmh = telemetry.speedKmh.current(now),
            voltageV = telemetry.voltageV.current(now),
            batteryTemperatureC = telemetry.batteryTemperatureC.current(now),
            powerKw = telemetry.powerKw.current(now),
            chargingNeededEstimate = (energy as? BaselineEnergyResult.Estimate)?.chargingStopNeeded,
            energyEstimateStatus = (energy as? BaselineEnergyResult.Unavailable)?.reason,
            socSourceLabel = when {
                source.simulated -> "SİMÜLASYON · TEST VERİSİ"
                telemetry.socPercent.current(now) != null -> "GERÇEK OBD · ölçüm"
                route != null -> "Varsayılan Başlangıç %80 · MODEL/TAHMİN"
                else -> null
            },
            simulationEnabled = source.simulated,
            gpsWarning = if (!source.simulated && _foreground.value &&
                now - gpsWaitSinceMs > 10_000L && usableGps == null
            ) "GPS Sinyali Bekleniyor..." else null,
            route = visualRoute,
            gpsFix = usableGps
        )
    }
    /**
     * Fail-closed bridge to the tested energy estimator.
     * Calibration keys must be set from a separately validated configuration.
     * Unverified SoC/usable capacity never become made-up vehicle measurements.
     */
    private fun estimateEnergy(
        route: RouteSnapshot?,
        telemetry: VehicleTelemetry,
        now: Long,
        simulated: Boolean
    ): BaselineEnergyResult? {
        if (route?.routeId == null) return null
        // Damaged/old preference types must not crash the navigation UI.
        fun readFloat(key: String): Double? = runCatching {
            if (modelPrefs.contains(key)) modelPrefs.getFloat(key, Float.NaN)
                .toDouble().takeIf { it.isFinite() } else null
        }.getOrNull()
        // These deterministic values are only TEST FIXTURES in DEBUG simulation.
        val capacity = if (simulated) 60.0 else readFloat("usableBatteryKwh") ?: Double.NaN
        val consumption = if (simulated) 180.0 else readFloat("referenceWhPerKm") ?: Double.NaN
        val reserve = if (simulated) 15.0 else readFloat("arrivalReservePct") ?: Double.NaN
        if (!capacity.isFinite() || capacity <= 0.0 ||
            !consumption.isFinite() || consumption <= 0.0 ||
            !reserve.isFinite() || reserve !in 0.0..100.0
        ) return BaselineEnergyResult.Unavailable("Kalibre edilmiş kapasite/tüketim/rezerv verisi yok")

        val fresh = telemetry.socPercent.current(now)
        val timed = telemetry.socPercent.measuredAtMillis
        val soc = when {
            simulated -> DepartureSoc.ExplicitAssumption(fresh ?: 80.0)
            fresh != null && timed != null -> DepartureSoc.FreshObd(fresh, timed)
            else -> DepartureSoc.ExplicitAssumption(
                readFloat("explicitAssumedSocPct") ?: 80.0
            ) // Explicit, visibly labelled 80% default, never a live measurement.
        }

        return EvBaselineEnergyEstimator.estimate(
            BaselineEnergyInputs(
                route = route, departureSoc = soc,
                usableBatteryKwh = capacity,
                consumptionWhPerKm = consumption,
                consumptionBasis = ConsumptionBasis.REFERENCE_MODEL,
                requiredArrivalSocPercent = reserve,
                nowMs = now,
                speedKmh = telemetry.speedKmh.current(now)
            )
        )
    }

    private val plannerState = combine(
        _planner, routes.options, routes.selectedIndex
    ) { state, options, index ->
        state.copy(options = options, selectedIndex = index)
    }
    // One projection: MapLibre, route dialog and progress panel observe the same
    // emission. No second independently collected routeState StateFlow.
    val uiState: StateFlow<NavigationUiState> = combine(
        cockpit, plannerState, _voice
    ) { cockpit, planner, voice ->
        val route = cockpit.route
        val routeState = NavigationRouteState(
            startLocation = planner.startLocation,
            startLabel = planner.originText.takeIf {
                planner.startLocation != null && it.isNotBlank()
            },
            destination = planner.destinationSelected,
            routeResult = route,
            calculating = planner.planning,
            error = planner.error
        )
        val phase = when {
            planner.planning -> RouteLoadStatus.Loading
            planner.error != null -> RouteLoadStatus.Error(planner.error)
            route?.routeId != null -> RouteLoadStatus.Success(route.routeId)
            else -> RouteLoadStatus.Empty
        }
        NavigationUiState(cockpit, planner, voice, routeState, phase, cockpit.simulationEnabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), NavigationUiState())

    init {
        viewModelScope.launch {
            combine(_simulationEnabled, _foreground, routes.currentRoute) { enabled, front, route ->
                Triple(enabled && BuildConfig.DEBUG, front, route)
            }.collectLatest { (enabled, front, route) ->
                _simulationFrame.value = null
                if (enabled && front) {
                    DebugRouteSimulation.frames(route).collect { frame ->
                        _simulationFrame.value = frame
                    }
                }
            }
        }
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
                sources,
                _foreground,
                CarNavigationOwnership.navigating,
                _voice
            ) { route, source, foreground, carNavigating, voice ->
                SpeechInputs(route, source.gps, foreground && !carNavigating, voice)
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
        if (isForeground) gpsWaitSinceMs = System.currentTimeMillis()
        if (!isForeground) {
            guide.stop()
            locationRepository.stop()
        }
    }
    fun startGps() {
        _gpsError.value = null
        if (!_simulationEnabled.value) locationRepository.start()
    }

    fun setSimulationEnabled(enabled: Boolean) {
        val next = BuildConfig.DEBUG && enabled
        if (_simulationEnabled.value == next) return
        guide.stop()
        policy.reset()
        _simulationFrame.value = null
        _simulationEnabled.value = next
        gpsWaitSinceMs = System.currentTimeMillis()
        _gpsError.value = null
        if (next) locationRepository.stop()
        else if (_foreground.value) locationRepository.start()
    }
    fun stopGps() = locationRepository.stop()

    /** Last known Fused fix, delivered only after the user grants runtime permission. */
    @Suppress("DEPRECATION")
    fun acceptLastLocation(location: android.location.Location?) {
        if (_simulationEnabled.value) return
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
        if (_simulationEnabled.value) {
            _planner.update { it.copy(error = "Simülasyonda başlangıç ve varışı adres aramasından seç.") }
            return
        }
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
            routes.clear() // Don't retain a successful stale route during failed replanning.
            try {
                when (val result = routes.calculate(start, target)) {
                    is RouteCalculationResult.Success ->
                        _planner.update { it.copy(planning = false, error = null) }
                    is RouteCalculationResult.Failure ->
                        _planner.update { it.copy(
                            planning = false,
                            error = when {
                                result.message.contains("internet", ignoreCase = true) ->
                                    "İnternet Bağlantısı Yok"
                                else -> "Rota Hesaplanamadı (OSRM Hatası): " + result.message.take(90)
                            }
                        ) }
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

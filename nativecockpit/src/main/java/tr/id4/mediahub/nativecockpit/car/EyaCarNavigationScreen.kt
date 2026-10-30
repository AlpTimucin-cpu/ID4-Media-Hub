package tr.id4.mediahub.nativecockpit.car

import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tr.id4.mediahub.nativecockpit.domain.model.GpsFix
import tr.id4.mediahub.nativecockpit.domain.model.RouteSnapshot
import tr.id4.mediahub.nativecockpit.domain.model.validRouteGeometry
import tr.id4.mediahub.nativecockpit.domain.repository.RouteRepository
import tr.id4.mediahub.nativecockpit.domain.usecase.ManeuverGuidancePolicy
import tr.id4.mediahub.nativecockpit.domain.usecase.RouteProgressTracker
import tr.id4.mediahub.nativecockpit.presentation.voice.NativeVoiceGuide
import tr.id4.mediahub.nativecockpit.presentation.voice.VoicePreference
import kotlin.math.roundToInt

/** Real OSRM geometry + measured GPS only. Foreground Android Auto session, not a navigation FGS. */
class EyaCarNavigationScreen(
    context: CarContext,
    private val routes: RouteRepository
) : Screen(context) {
    private val tracker = CarGpsTracker(context.applicationContext)
    private val surface = EyaCarMapSurface(context)
    private val manager = context.getCarService(NavigationManager::class.java)
    private val voice = NativeVoiceGuide(context.applicationContext)
    private val voicePrefs = VoicePreference(context.applicationContext)
    private val speechPolicy = ManeuverGuidancePolicy()
    private val progress = RouteProgressTracker()
    private val guidancePolicy = CarGuidancePolicy()
    private val gate = CarTripUpdateGate()
    private var work: CoroutineScope? = null
    private var route: RouteSnapshot? = routes.currentRoute.value
    private var fix: GpsFix? = null
    private var guidance: CarGuidance? = null
    private var summary = CarRouteSummary.from(route, null, System.currentTimeMillis())
    private var activeRouteId: String? = null
    private var withoutGpsSinceMs: Long = 0L
    private var autoDriveTestRequested = false
    private var closed = false

    private val callbacks = object : NavigationManagerCallback {
        override fun onStopNavigation() {
            // Host navigation ownership changed: immediately stop all trip IPC and voice.
            endNavigation()
        }
        override fun onAutoDriveEnabled() {
            // Google Play requires a separate, conspicuously labelled test-only simulation.
            // This build never injects mock GPS into real driving or presents it as measured.
            autoDriveTestRequested = true
            invalidate()
        }
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                manager.setNavigationManagerCallback(callbacks)
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(surface)
                tracker.start()
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                work = scope
                val ticker = flow {
                    while (kotlin.coroutines.coroutineContext.isActive) {
                        emit(System.currentTimeMillis())
                        delay(1_000L)
                    }
                }
                scope.launch {
                    combine(routes.currentRoute, tracker.gpsFix, ticker) { r, f, now ->
                        Triple(r, f?.takeIf { it.usable(now) }, now)
                    }.collect { (r, f, now) ->
                        route = r
                        fix = f
                        surface.update(r, f)
                        val fresh = guidancePolicy.snapshot(r, f, now)
                        guidance = fresh
                        val newSummary = CarRouteSummary.from(r, f, now)
                        if (newSummary != summary) {
                            summary = newSummary
                            invalidate()
                        }
                        updateActiveNavigation(r, f, fresh, now)
                    }
                }
            }
            override fun onStop(owner: LifecycleOwner) {
                // No foreground service yet: stop rather than claim background navigation.
                endNavigation()
                work?.cancel(); work = null
                tracker.stop()
                surface.release()
                manager.clearNavigationManagerCallback()
            }
            override fun onDestroy(owner: LifecycleOwner) {
                endNavigation()
                work?.cancel(); work = null
                tracker.stop()
                surface.release()
                if (!closed) { closed = true; voice.close() }
            }
        })
    }

    private fun beginNavigation() {
        if (activeRouteId != null) return
        val current = guidance ?: return // user must have a REAL route and a fresh GPS fix
        if (route?.routeId != current.routeId) return
        try {
            manager.navigationStarted()
            activeRouteId = current.routeId
            CarNavigationOwnership.setNavigating(true)
            withoutGpsSinceMs = 0L
            gate.clear()
            speechPolicy.reset()
            updateActiveNavigation(route, fix, current, System.currentTimeMillis())
            invalidate()
        } catch (e: RuntimeException) {
            Log.w("EyaCarNavigation", "Host refused navigation start", e)
            endNavigation()
        }
    }

    private fun endNavigation() {
        val wasActive = activeRouteId != null
        activeRouteId = null
        withoutGpsSinceMs = 0L
        CarNavigationOwnership.setNavigating(false)
        voice.stop()
        speechPolicy.reset()
        gate.clear()
        if (wasActive) {
            try { manager.navigationEnded() }
            catch (e: RuntimeException) { Log.w("EyaCarNavigation", "Host navigation end", e) }
            invalidate()
        }
    }

    private fun updateActiveNavigation(
        currentRoute: RouteSnapshot?, currentFix: GpsFix?,
        current: CarGuidance?, now: Long
    ) {
        val started = activeRouteId ?: return
        if (currentRoute?.routeId != started || !validRouteGeometry(currentRoute.routePoints)) {
            endNavigation() // new alternative means a new explicit trip
            return
        }
        if (current == null || current.routeId != started) {
            if (withoutGpsSinceMs == 0L) withoutGpsSinceMs = now
            voice.stop()
            if (now - withoutGpsSinceMs >= 15_000L) endNavigation()
            return // never publish stale maneuver or estimated distance
        }
        withoutGpsSinceMs = 0L
        if (gate.shouldPublish(current, now)) {
            try { manager.updateTrip(CarTripFactory.trip(current)) }
            catch (e: RuntimeException) { Log.w("EyaCarNavigation", "Trip update rejected", e) }
            invalidate()
        }
        if (voicePrefs.enabled && currentFix != null &&
            voice.status == NativeVoiceGuide.Status.READY) {
            val enriched = progress.enrich(currentRoute, currentFix, now)
            val announcement = speechPolicy.nextAnnouncement(enriched, currentFix, now)
            if (announcement != null) {
                // An enqueue is not proof that the speech engine started.
                val queued = voice.speak(
                    announcement, voicePrefs.volume,
                    onUtteranceStarted = {
                        // NativeVoiceGuide marshals this callback onto the main thread.
                        speechPolicy.confirmSpoken(System.currentTimeMillis())
                    },
                    onUtteranceFailed = { speechPolicy.cancelPending() }
                )
                if (!queued) speechPolicy.cancelPending()
            }
        } else if (!voicePrefs.enabled) voice.stop()
    }

    override fun onGetTemplate(): Template {
        val active = activeRouteId != null
        val g = guidance?.takeIf { it.routeId == activeRouteId }
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle(summary.title).addText(summary.detail).build())
            .addRow(Row.Builder().setTitle("GPS").addText(summary.locationStatus).build())

        if (active) {
            val step = g?.nextCue
            val meters = g?.nextMeters
            pane.addRow(Row.Builder().setTitle("Sıradaki manevra")
                .addText(if (step != null && meters != null) {
                    "$step · ${meters.roundToInt()} m"
                } else "Güncel GPS ve manevra bekleniyor").build())
            pane.addRow(Row.Builder().setTitle("Navigasyonu bitir")
                .setOnClickListener { endNavigation() }.build())
        } else if (guidance != null) {
            pane.addRow(Row.Builder().setTitle("Navigasyonu başlat")
                .setOnClickListener { beginNavigation() }.build())
        } else {
            pane.addRow(Row.Builder().setTitle("Başlatmak için")
                .addText("Telefonda rota seçip konum izni verin").build())
        }
        if (autoDriveTestRequested) {
            pane.addRow(Row.Builder().setTitle("DHU test isteği")
                .addText("Simülasyon kapalı: gerçek GPS doğrulaması gerekir").build())
        }
        val content = PaneTemplate.Builder(pane.build())
        if (carContext.carAppApiLevel < 7) {
            @Suppress("DEPRECATION")
            return content.setTitle("Elektrikli Yol Arkadaşım").build()
        }
        return MapWithContentTemplate.Builder()
            .setContentTemplate(content.setHeader(
                Header.Builder().setTitle("Elektrikli Yol Arkadaşım").build()
            ).build())
            .build()
    }
}

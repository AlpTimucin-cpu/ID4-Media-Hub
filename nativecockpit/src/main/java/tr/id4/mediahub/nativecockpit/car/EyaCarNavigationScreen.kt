package tr.id4.mediahub.nativecockpit.car

import android.content.Context
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Header
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.AppManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tr.id4.mediahub.nativecockpit.domain.model.GpsFix
import tr.id4.mediahub.nativecockpit.domain.model.RouteSnapshot
import tr.id4.mediahub.nativecockpit.domain.repository.RouteRepository

/** Real OSRM data only, no fabricated route/ETA, no video/moving WebView. */
class EyaCarNavigationScreen(
    context: CarContext,
    private val routes: RouteRepository
) : Screen(context) {
    private val tracker = CarGpsTracker(context.applicationContext)
    private val surfaceRenderer = EyaCarMapSurface(context)
    private var work: CoroutineScope? = null
    private var latestRoute: RouteSnapshot? = routes.currentRoute.value
    private var latestGps: GpsFix? = null
    private var summary = CarRouteSummary.from(latestRoute, null, System.currentTimeMillis())

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java)
                    .setSurfaceCallback(surfaceRenderer)
                tracker.start()
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                work = scope
                // Clock tick expires old GPS without ever advancing the car position.
                val ticker = flow {
                    while (kotlin.coroutines.coroutineContext.isActive) {
                        emit(System.currentTimeMillis())
                        delay(1_000L)
                    }
                }
                scope.launch {
                    combine(routes.currentRoute, tracker.gpsFix, ticker) { route, fix, now ->
                        Triple(route, fix?.takeIf { it.usable(now) }, now)
                    }.collect { (route, fix, now) ->
                        val newSummary = CarRouteSummary.from(route, fix, now)
                        surfaceRenderer.update(route, fix)
                        if (newSummary != summary) {
                            summary = newSummary
                            invalidate()
                        }
                        latestRoute = route
                        latestGps = fix
                    }
                }
            }
            override fun onStop(owner: LifecycleOwner) {
                work?.cancel(); work = null
                tracker.stop()
                surfaceRenderer.release()
            }
            override fun onDestroy(owner: LifecycleOwner) {
                work?.cancel(); work = null
                tracker.stop()
                surfaceRenderer.release()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle(summary.title).addText(summary.detail).build())
            .addRow(Row.Builder().setTitle("Konum").addText(summary.locationStatus).build())
            .build()
        // Car App API 7+ uses the current map-aware template.
        // Older hosts retain a text-only fallback, never a dummy map.
        if (carContext.carAppApiLevel < 7) {
            @Suppress("DEPRECATION")
            return PaneTemplate.Builder(pane)
                .setTitle("Elektrikli Yol Arkadaşım")
                .build()
        }
        val content = PaneTemplate.Builder(pane)
            .setHeader(Header.Builder().setTitle("Elektrikli Yol Arkadaşım").build())
            .build()
        return MapWithContentTemplate.Builder()
            .setContentTemplate(content)
            .build()
    }
}

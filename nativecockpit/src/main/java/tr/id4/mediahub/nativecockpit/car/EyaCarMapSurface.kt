package tr.id4.mediahub.nativecockpit.car

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import android.view.Surface
import android.widget.FrameLayout
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import tr.id4.mediahub.nativecockpit.domain.model.GpsFix
import tr.id4.mediahub.nativecockpit.domain.model.RouteSnapshot
import tr.id4.mediahub.nativecockpit.domain.model.validRouteGeometry

/**
 * Renders MapLibre to the host Surface, never a browser/phone-screen mirror.
 * No MapView is created if Android Auto has not supplied a valid Surface.
 */
internal class EyaCarMapSurface(private val context: CarContext) : SurfaceCallback {
    private var activeSurface: Surface? = null
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var pendingRoute: RouteSnapshot? = null
    private var pendingGps: GpsFix? = null
    private var appliedRoute: String? = null
    private var appliedGpsAt: Long? = null
    private var centeredOnGps = false
    // Guards delayed MapLibre callbacks after Android Auto replaces a Surface.
    private var surfaceGeneration = 0L

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val hostSurface = surfaceContainer.surface ?: return
        if (!hostSurface.isValid ||
            surfaceContainer.width <= 0 || surfaceContainer.height <= 0) return
        if (activeSurface === hostSurface && display != null) {
            display?.resize(surfaceContainer.width, surfaceContainer.height,
                surfaceContainer.dpi.coerceAtLeast(120))
            return
        }
        release()
        activeSurface = hostSurface
        val generation = ++surfaceGeneration
        try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val virtual = dm.createVirtualDisplay(
                "Elektrikli Yol Arkadaşım – Navigasyon",
                surfaceContainer.width, surfaceContainer.height,
                surfaceContainer.dpi.coerceAtLeast(120),
                hostSurface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            ) ?: run {
                Log.e("EyaCarMap", "Android Auto VirtualDisplay unavailable")
                release()
                return
            }
            display = virtual
            val popup = Presentation(context, virtual.display)
            presentation = popup
            MapLibre.getInstance(context.applicationContext)
            val view = MapView(popup.context)
            mapView = view
            view.onCreate(null)
            val frame = FrameLayout(popup.context)
            frame.addView(view, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            popup.setContentView(frame)
            popup.show()
            view.onStart()
            view.onResume()
            view.getMapAsync { ready ->
                if (generation != surfaceGeneration || mapView !== view) return@getMapAsync
                map = ready
                ready.uiSettings.isAttributionEnabled = true
                ready.uiSettings.isLogoEnabled = true
                // Initial map center is a viewport, never a fabricated GPS position.
                ready.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(39.0,35.0)).zoom(5.2).build()
                ready.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/dark")) { loaded ->
                    if (generation != surfaceGeneration || mapView !== view) return@setStyle
                    style = loaded
                    appliedRoute = null
                    appliedGpsAt = null
                    render()
                }
            }
        } catch (e: RuntimeException) {
            Log.w("EyaCarMap", "Car surface failed", e)
            release()
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) { release() }

    fun update(route: RouteSnapshot?, fix: GpsFix?) {
        pendingRoute = route
        pendingGps = fix?.takeIf { it.usable(System.currentTimeMillis()) }
        render()
    }

    private fun render() {
        val ready = style ?: return
        val route = pendingRoute?.routePoints?.takeIf(::validRouteGeometry)
        val signature = pendingRoute?.routeId ?: route?.hashCode()?.toString() ?: "empty"
        if (appliedRoute != signature) {
            val collection = if (route == null)
                FeatureCollection.fromFeatures(emptyArray<Feature>())
            else FeatureCollection.fromFeature(Feature.fromGeometry(
                LineString.fromLngLats(route.map { Point.fromLngLat(it.longitude,it.latitude) })))
            val src = ready.getSourceAs<GeoJsonSource>("eya-car-route")
            if (src == null) {
                ready.addSource(GeoJsonSource("eya-car-route", collection))
                ready.addLayer(LineLayer("eya-car-route-line","eya-car-route")
                    .withProperties(lineColor(android.graphics.Color.rgb(0,176,255)),lineWidth(5f)))
            } else src.setGeoJson(collection)
            if (route != null && route.size >= 2) {
                try {
                    val bounds = LatLngBounds.Builder()
                    route.forEach { bounds.include(LatLng(it.latitude,it.longitude)) }
                    map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 65))
                } catch (_: Exception) { /* No guessed fallback geometry. */ }
            }
            appliedRoute = signature
        }
        val fix = pendingGps?.takeIf { it.usable(System.currentTimeMillis()) }
        val stamp = fix?.recordedAtMillis
        if (appliedGpsAt != stamp) {
            val collection = if (fix == null)
                FeatureCollection.fromFeatures(emptyArray<Feature>())
            else FeatureCollection.fromFeature(Feature.fromGeometry(
                Point.fromLngLat(fix.coordinate.longitude,fix.coordinate.latitude)))
            val src = ready.getSourceAs<GeoJsonSource>("eya-car-gps")
            if (src == null) {
                ready.addSource(GeoJsonSource("eya-car-gps",collection))
                ready.addLayer(CircleLayer("eya-car-marker","eya-car-gps")
                    .withProperties(circleColor(android.graphics.Color.rgb(0,230,118)),
                        circleRadius(9f),circleStrokeWidth(3f),
                        circleStrokeColor(android.graphics.Color.WHITE)))
            } else src.setGeoJson(collection)
            if (fix != null && !centeredOnGps && route == null) {
                map?.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    LatLng(fix.coordinate.latitude,fix.coordinate.longitude),13.5))
                centeredOnGps = true
            }
            appliedGpsAt = stamp
        }
    }

    fun release() {
        surfaceGeneration++ // Invalidate asynchronous map/style callbacks first.
        style = null
        map = null
        appliedGpsAt = null
        appliedRoute = null
        centeredOnGps = false
        mapView?.let { view ->
            try { view.onPause();view.onStop();view.onDestroy() }
            catch (e: RuntimeException) { Log.w("EyaCarMap", "MapView cleanup", e) }
        }
        mapView = null
        try { presentation?.dismiss() } catch (_: Exception) { }
        presentation = null
        try { display?.release() } catch (_: Exception) { }
        display = null
        // SurfaceContainer's Surface belongs to the Android Auto host.
        // Releasing it here can invalidate future sessions and cause crashes.
        activeSurface = null
    }
}

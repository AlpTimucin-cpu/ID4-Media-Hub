package tr.id4.mediahub.nativecockpit.presentation.map

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import com.google.gson.JsonObject
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import tr.id4.mediahub.nativecockpit.domain.model.GeoCoordinate
import tr.id4.mediahub.nativecockpit.domain.model.GpsFix
import tr.id4.mediahub.nativecockpit.domain.model.ChargingStation
import tr.id4.mediahub.nativecockpit.domain.model.GeoViewport
import tr.id4.mediahub.nativecockpit.domain.model.WeatherWaypoint
import tr.id4.mediahub.nativecockpit.domain.usecase.StationCorridor
import tr.id4.mediahub.nativecockpit.domain.model.validRouteGeometry

private const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"
private const val FALLBACK_STYLE = "https://tiles.openfreemap.org/styles/fiord"
private const val ROUTE_SRC = "eya-route-source"
private const val ROUTE_LAYER = "eya-route-line"
private const val STATION_SRC = "eya-station-source"
private const val STATION_ALL_LAYER = "eya-station-all"
private const val STATION_RING_LAYER = "eya-station-route-outline"
private const val STATION_DC_LAYER = "eya-station-dc-icons"
private const val STATION_ICON = "eya-station-bolt"
private const val STATION_CLUSTER_LAYER = "eya-station-cluster-circles"
private const val STATION_CLUSTER_COUNT_LAYER = "eya-station-cluster-count"
private const val WEATHER_SRC = "eya-weather-route"
private const val WEATHER_LAYER = "eya-weather-markers"

/** One MapView for this composition; Compose owns transparent cockpit overlays. */
@Composable
fun MapLibreCockpit(
    gpsFix: GpsFix?,
    routePoints: List<GeoCoordinate>,
    stations: List<ChargingStation>,
    weatherPoints: List<WeatherWaypoint>,
    onViewportChanged: (GeoViewport) -> Unit,
    onStationTapped: (ChargingStation) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    // Map callback must never capture stale Compose lists/lambdas.
    val stationIndex = remember(stations) { stations.associateBy { it.id } }
    val latestStationIndex by rememberUpdatedState(stationIndex)
    val latestViewport by rememberUpdatedState(onViewportChanged)
    val latestTap by rememberUpdatedState(onStationTapped)
    var mapStatus by remember { mutableStateOf("Harita yükleniyor…") }
    var mapObject by remember { mutableStateOf<MapLibreMap?>(null) }
    var activeStyle by remember { mutableStateOf<Style?>(null) }
    var fallback by remember { mutableStateOf(false) }
    var centeredOnRealGps by remember { mutableStateOf(false) }
    var fittedRouteKey by remember { mutableStateOf<Int?>(null) }
    // This object follows the lifecycle of the ONE MapView.
    val carAnimator = remember(context) { VehicleMarkerAnimator() }

    // MapLibre SDK needs initialization BEFORE MapView is constructed.
    val view = remember(context) {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).also { it.onCreate(null) }
    }

    DisposableEffect(view, owner) {
        var started = false
        var resumed = false
        fun start() { if (!started) { view.onStart(); started = true } }
        fun resume() { start(); if (!resumed) { view.onResume(); resumed = true } }
        fun pause() {
            carAnimator.clear() // no animation while backgrounded
            if (resumed) { view.onPause(); resumed = false }
        }
        fun stop() { pause(); if (started) { view.onStop(); started = false } }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        else if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        val callbacks = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(config: Configuration) = Unit
            override fun onLowMemory() { view.onLowMemory() }
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) view.onLowMemory()
            }
        }
        context.registerComponentCallbacks(callbacks)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            context.unregisterComponentCallbacks(callbacks)
            stop()
            carAnimator.release()
            view.onDestroy() // exactly once, on Composition disposal
        }
    }

    DisposableEffect(view) {
        var idleListener: MapLibreMap.OnCameraIdleListener? = null
        var clickListener: MapLibreMap.OnMapClickListener? = null
        var callbackMap: MapLibreMap? = null
        val failed = MapView.OnDidFailLoadingMapListener { message ->
            if (!fallback) {
                fallback = true
                mapStatus = "Koyu harita yeniden deneniyor…"
                mapObject?.setStyle(Style.Builder().fromUri(FALLBACK_STYLE)) { style ->
                    activeStyle = style
                    mapStatus = "Harita hazır · Fiord yedek stili"
                }
            } else {
                mapStatus = "Harita bağlantısı başarısız · yol çizilmiyor"
            }
        }
        view.addOnDidFailLoadingMapListener(failed)
        view.getMapAsync { map ->
            callbackMap = map
            mapObject = map
            map.uiSettings.isAttributionEnabled = true
            map.uiSettings.isLogoEnabled = true
            fun publishViewport() {
                val bounds=map.projection.visibleRegion.latLngBounds
                latestViewport(GeoViewport(bounds.southWest.latitude,bounds.southWest.longitude,
                    bounds.northEast.latitude,bounds.northEast.longitude))
            }
            idleListener = MapLibreMap.OnCameraIdleListener { publishViewport() }
            map.addOnCameraIdleListener(idleListener!!)
            clickListener = MapLibreMap.OnMapClickListener { point ->
                val clusters = map.queryRenderedFeatures(map.projection.toScreenLocation(point),
                    STATION_CLUSTER_LAYER)
                val cluster = clusters.firstOrNull { it.hasProperty("cluster_id") }
                if (cluster != null) {
                    val source = map.style?.getSourceAs<GeoJsonSource>(STATION_SRC)
                    val expansion = try { source?.getClusterExpansionZoom(cluster)?.toDouble() }
                        catch (_: Exception) { null }
                    val center = (cluster.geometry() as? Point)?.let {
                        LatLng(it.latitude(), it.longitude())
                    } ?: point
                    val zoom = (expansion ?: map.cameraPosition.zoom + 2.0)
                        .coerceAtLeast(map.cameraPosition.zoom + 1.0)
                        .coerceAtMost(17.0)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(center, zoom))
                    return@OnMapClickListener true
                }
                val hits=map.queryRenderedFeatures(map.projection.toScreenLocation(point),
                    STATION_DC_LAYER,STATION_RING_LAYER,STATION_ALL_LAYER)
                val id=hits.firstOrNull()?.let { f ->
                    if(f.hasProperty("stationId")) f.getStringProperty("stationId") else null
                }
                val station=latestStationIndex[id]
                if(station!=null) { latestTap(station); true } else false
            }
            map.addOnMapClickListener(clickListener!!)
            // Türkiye panoraması yalnız kamera başlangıcıdır; GPS değildir.
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(39.0, 35.0)).zoom(5.2).build()
            map.setStyle(Style.Builder().fromUri(DARK_STYLE)) { style ->
                activeStyle = style
                mapStatus = "Harita hazır · OpenFreeMap"
                publishViewport()
            }
        }
        onDispose {
            callbackMap?.let { m ->
                idleListener?.let { m.removeOnCameraIdleListener(it) }
                clickListener?.let { m.removeOnMapClickListener(it) }
            }
            view.removeOnDidFailLoadingMapListener(failed)
        }
    }

    // Show only actual OSRM geometry, fit once per selected alternative.
    LaunchedEffect(activeStyle, routePoints) {
        activeStyle?.let { updateRoute(it, routePoints) }
        if (activeStyle != null && validRouteGeometry(routePoints)) {
            val signature=routePoints.hashCode()
            if(fittedRouteKey!=signature){
                val b=LatLngBounds.Builder()
                routePoints.forEach { b.include(LatLng(it.latitude,it.longitude)) }
                try { mapObject?.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 76)) }
                catch (_:Exception) { /* keep real geometry if fit is unsupported */ }
                fittedRouteKey=signature
            }
        }
        if(routePoints.isEmpty())fittedRouteKey=null
    }
    LaunchedEffect(activeStyle, stations, routePoints) {
        val style = activeStyle ?: return@LaunchedEffect
        // Serialize the 13k+ stations off the UI thread; the source clusters them.
        val geo = withContext(Dispatchers.Default) {
            buildStationGeoJson(stations, routePoints)
        }
        updateStations(style, geo)
    }
    LaunchedEffect(activeStyle, weatherPoints) {
        activeStyle?.let { updateWeather(it, weatherPoints) }
    }
    LaunchedEffect(activeStyle, gpsFix, routePoints) {
        activeStyle?.let { carAnimator.update(it, gpsFix, routePoints, System.currentTimeMillis()) }
        if (!centeredOnRealGps && activeStyle != null &&
            gpsFix?.usable(System.currentTimeMillis()) == true) {
            mapObject?.moveCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(gpsFix.coordinate.latitude, gpsFix.coordinate.longitude), 13.5))
            centeredOnRealGps = true // later movement animation is Step 5
        }
    }

    Box(modifier.background(Color(0xFF0A0F1A))) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        if (!mapStatus.startsWith("Harita hazır")) {
            Text(mapStatus, modifier = Modifier.align(Alignment.Center)
                .background(Color(0xDF0B2032), RoundedCornerShape(12.dp))
                .padding(14.dp), color = Color.White, fontSize = 12.sp)
        }
        // Explicit provider attribution additionally to MapLibre's native attribution.
        Text("© OpenFreeMap · © OpenStreetMap",
            color = Color(0xFFD6E2EB), fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 2.dp, end = 9.dp)
                .background(Color(0xB9091B2A), RoundedCornerShape(6.dp))
                .clickable {
                    val uri = android.net.Uri.parse("https://www.openstreetmap.org/copyright")
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                    try { context.startActivity(intent) } catch (_: Exception) { }
                }.padding(horizontal = 7.dp, vertical = 3.dp))
    }
}

private fun updateRoute(style: Style, points: List<GeoCoordinate>) {
    val valid = validRouteGeometry(points)
    val geo = if (valid) {
        val line = LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) })
        FeatureCollection.fromFeature(Feature.fromGeometry(line))
    } else FeatureCollection.fromFeatures(emptyArray<Feature>())
    val source = style.getSourceAs<GeoJsonSource>(ROUTE_SRC)
    if (source != null) source.setGeoJson(geo)
    else {
        style.addSource(GeoJsonSource(ROUTE_SRC, geo))
        style.addLayer(LineLayer(ROUTE_LAYER, ROUTE_SRC).withProperties(
            lineColor(android.graphics.Color.rgb(0, 176, 255)), lineWidth(5f)
        ))
    }
}

/** Feature properties carry only station IDs; details come from verified repository. */
private fun buildStationGeoJson(
    stations: List<ChargingStation>, route: List<GeoCoordinate>
): FeatureCollection {
    val routeIndex=if(route.size>=2) StationCorridor.index(route) else null
    val features=stations.map { station ->
        val props=JsonObject().apply {
            addProperty("stationId",station.id)
            addProperty("dc",station.hasDc)
            addProperty("corridor",routeIndex?.isDcAlongRoute(station)==true)
        }
        Feature.fromGeometry(Point.fromLngLat(station.coordinate.longitude,
            station.coordinate.latitude),props)
    }
    return FeatureCollection.fromFeatures(features)
}

private fun updateStations(style: Style, geo: FeatureCollection) {
    val existing=style.getSourceAs<GeoJsonSource>(STATION_SRC)
    if(existing!=null){existing.setGeoJson(geo);return}
    // Clustering belongs to the data source: thousands of points do not become
    // thousands of on-screen circles at low zoom levels.
    val options = GeoJsonOptions()
        .withCluster(true)
        .withClusterRadius(50)
        .withClusterMaxZoom(14)
    style.addSource(GeoJsonSource(STATION_SRC, geo, options))
    style.addImage(STATION_ICON, lightningBitmap())
    val isCluster = Expression.has("point_count")
    val isSingle = Expression.not(isCluster)
    style.addLayer(CircleLayer(STATION_CLUSTER_LAYER, STATION_SRC)
        .withFilter(isCluster)
        .withProperties(
            circleColor(android.graphics.Color.rgb(16, 115, 155)),
            circleRadius(21f),
            circleStrokeWidth(2f),
            circleStrokeColor(android.graphics.Color.WHITE)
        ))
    style.addLayer(SymbolLayer(STATION_CLUSTER_COUNT_LAYER, STATION_SRC)
        .withFilter(isCluster)
        .withProperties(
            textField(Expression.get("point_count_abbreviated")),
            textColor(android.graphics.Color.WHITE),
            textSize(13f),
            textAllowOverlap(true)
        ))
    style.addLayer(CircleLayer(STATION_ALL_LAYER, STATION_SRC)
        .withFilter(isSingle)
        .withProperties(
            circleColor(android.graphics.Color.rgb(35, 164, 205)),
            circleRadius(5f),
            circleOpacity(.72f),
            circleStrokeWidth(1f),
            circleStrokeColor(android.graphics.Color.WHITE)
        ))
    style.addLayer(CircleLayer(STATION_RING_LAYER, STATION_SRC)
        .withFilter(Expression.all(isSingle,
            Expression.eq(Expression.get("corridor"), Expression.literal(true))))
        .withProperties(
            circleRadius(15f),
            circleColor(android.graphics.Color.TRANSPARENT),
            circleStrokeWidth(3f),
            circleStrokeColor(android.graphics.Color.rgb(16, 235, 235))
        ))
    style.addLayer(SymbolLayer(STATION_DC_LAYER, STATION_SRC)
        .withFilter(Expression.all(isSingle,
            Expression.eq(Expression.get("dc"), Expression.literal(true))))
        .withProperties(
            iconImage(STATION_ICON), iconSize(.50f), iconAllowOverlap(false)
        ))
}

/** Generate a vector-like bolt locally; no external icon/font/license dependency. */
private fun lightningBitmap(): Bitmap {
    val bmp=Bitmap.createBitmap(72,82,Bitmap.Config.ARGB_8888)
    val canvas=AndroidCanvas(bmp)
    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.FILL;color=android.graphics.Color.rgb(0,230,118)
    }
    val path=Path().apply {
        moveTo(39f,3f);lineTo(14f,43f);lineTo(34f,43f)
        lineTo(25f,79f);lineTo(61f,30f);lineTo(42f,30f);close()
    }
    canvas.drawPath(path,paint)
    return bmp
}

private fun updateWeather(style: Style, samples: List<WeatherWaypoint>) {
    val features = samples.filter { it.coordinate.valid() }.map { item ->
        val props = JsonObject().apply {
            addProperty("weatherIcon", "eya-weather-" + item.iconKey)
            addProperty("tempText", item.temperatureC.toInt().toString() + "°C")
        }
        Feature.fromGeometry(
            Point.fromLngLat(item.coordinate.longitude, item.coordinate.latitude), props
        )
    }
    val data = FeatureCollection.fromFeatures(features)
    val source = style.getSourceAs<GeoJsonSource>(WEATHER_SRC)
    if (source != null) {
        source.setGeoJson(data)
    } else {
        style.addSource(GeoJsonSource(WEATHER_SRC, data))
        listOf("sun", "cloud", "rain").forEach { kind ->
            style.addImage("eya-weather-" + kind, weatherBitmap(kind))
        }
        style.addLayer(SymbolLayer(WEATHER_LAYER, WEATHER_SRC).withProperties(
            iconImage(Expression.get("weatherIcon")),
            iconSize(0.8f),
            iconAllowOverlap(true),
            textField(Expression.get("tempText")),
            textSize(12f),
            textColor(android.graphics.Color.WHITE),
            textHaloColor(android.graphics.Color.rgb(4, 28, 45)),
            textHaloWidth(2f),
            textAllowOverlap(true)
        ))
    }
}

/** Draw weather pictograms locally; no extra tile, image or font requests. */
private fun weatherBitmap(type: String): Bitmap {
    val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.rgb(4, 28, 45)
    canvas.drawCircle(24f, 24f, 22f, paint)
    paint.color = if (type == "sun") android.graphics.Color.rgb(255, 194, 67)
        else android.graphics.Color.rgb(200, 227, 245)
    if (type == "sun") {
        canvas.drawCircle(24f, 24f, 11f, paint)
    } else {
        canvas.drawOval(12f, 19f, 36f, 32f, paint)
        canvas.drawCircle(20f, 19f, 8f, paint)
        canvas.drawCircle(28f, 19f, 7f, paint)
        if (type == "rain") {
            paint.color = android.graphics.Color.rgb(42, 182, 238)
            paint.strokeWidth = 3f
            canvas.drawLine(17f, 34f, 13f, 41f, paint)
            canvas.drawLine(30f, 34f, 26f, 41f, paint)
        }
    }
    return bitmap
}

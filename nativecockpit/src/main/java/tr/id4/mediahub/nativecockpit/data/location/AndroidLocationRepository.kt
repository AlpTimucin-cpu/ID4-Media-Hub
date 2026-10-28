package tr.id4.mediahub.nativecockpit.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tr.id4.mediahub.nativecockpit.domain.model.GeoCoordinate
import tr.id4.mediahub.nativecockpit.domain.model.GpsFix
import tr.id4.mediahub.nativecockpit.domain.repository.LocationRepository

/** No mock GPS and no background tracking; stopped from Activity.onPause(). */
@Singleton
class AndroidLocationRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationRepository, LocationListener {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _gpsFix = MutableStateFlow<GpsFix?>(null)
    override val gpsFix: StateFlow<GpsFix?> = _gpsFix.asStateFlow()
    private var running = false

    @SuppressLint("MissingPermission") // checked before location request
    @Synchronized override fun start() {
        if (running) return
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return
        try {
            // GPS_PROVIDER first. Never replace it with a simulated location.
            val provider = when {
                fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> return
            }
            manager.requestLocationUpdates(provider, 1_000L, 2f, this, Looper.getMainLooper())
            running = true
        } catch (_: SecurityException) {
            _gpsFix.value = null
        } catch (_: IllegalArgumentException) {
            _gpsFix.value = null
        }
    }

    @Synchronized override fun stop() {
        if (running) manager.removeUpdates(this)
        running = false
        _gpsFix.value = null // never keep a moving-vehicle pin after lifecycle stop
    }

    @Suppress("DEPRECATION") // Android 9–11 location.isFromMockProvider API
    override fun onLocationChanged(location: Location) {
        // Reject developer-injected/mock coordinates in the real navigation path.
        if (location.isFromMockProvider ||
            location.provider == LocationManager.PASSIVE_PROVIDER ||
            !location.hasAccuracy()) {
            _gpsFix.value = null
            return
        }
        val fix = GpsFix(
            coordinate = GeoCoordinate(location.latitude, location.longitude),
            accuracyMeters = location.accuracy,
            recordedAtMillis = location.time,
            bearingDegrees = location.bearing.takeIf { location.hasBearing() &&
                it.isFinite() && it >= 0f && it < 360f },
            speedMetersPerSecond = location.speed.takeIf { location.hasSpeed() &&
                it.isFinite() && it >= 0f && it <= 90f }
        ).takeIf { it.usable(System.currentTimeMillis()) }
        // Out-of-order sensor deliveries must not animate backwards.
        if (fix != null && (_gpsFix.value?.recordedAtMillis ?: 0L) >= fix.recordedAtMillis) return
        _gpsFix.value = fix
    }

    override fun acceptFix(fix: GpsFix) {
        if (!fix.usable(System.currentTimeMillis())) return
        val previous = _gpsFix.value?.recordedAtMillis ?: 0L
        if (fix.recordedAtMillis > previous) _gpsFix.value = fix
    }

    override fun onProviderDisabled(provider: String) { _gpsFix.value = null }
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
}

package tr.id4.mediahub.nativecockpit.car

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tr.id4.mediahub.nativecockpit.domain.model.GeoCoordinate
import tr.id4.mediahub.nativecockpit.domain.model.GpsFix

/** Foreground Car Screen only; does not share or stop the phone Activity's GPS listener. */
internal class CarGpsTracker(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val mutableFix = MutableStateFlow<GpsFix?>(null)
    val gpsFix: StateFlow<GpsFix?> = mutableFix
    private var started = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (location in result.locations) update(location)
        }
    }

    @SuppressLint("MissingPermission") // runtime permission is checked immediately above
    fun start() {
        if (started) return
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) { mutableFix.value = null; return }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .setMinUpdateDistanceMeters(2f)
            .build()
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { started = false; mutableFix.value = null }
            started = true
        } catch (_: SecurityException) { mutableFix.value = null }
    }

    private fun update(location: Location) {
        if (LocationCompat.isMock(location) || !location.hasAccuracy()) return
        val fix = GpsFix(
            coordinate = GeoCoordinate(location.latitude, location.longitude),
            accuracyMeters = location.accuracy,
            recordedAtMillis = location.time,
            bearingDegrees = location.bearing.takeIf { location.hasBearing() && it.isFinite() },
            speedMetersPerSecond = location.speed.takeIf { location.hasSpeed() && it.isFinite() }
        ).takeIf { it.usable(System.currentTimeMillis()) }
        if (fix != null && fix.recordedAtMillis > (mutableFix.value?.recordedAtMillis ?: 0L))
            mutableFix.value = fix
    }

    fun stop() {
        if (started) client.removeLocationUpdates(callback)
        started = false
        mutableFix.value = null
    }
}

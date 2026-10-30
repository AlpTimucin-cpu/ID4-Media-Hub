package tr.id4.mediahub.nativecockpit.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import tr.id4.mediahub.nativecockpit.domain.model.GeoCoordinate
import tr.id4.mediahub.nativecockpit.domain.model.GpsFix

/** Foreground-only, one-shot real location after explicit runtime permission. */
class FusedCurrentLocationProvider @Inject constructor(
    @ApplicationContext private val context:Context
) {
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }
    @SuppressLint("MissingPermission")
    suspend fun getCurrentMeasurement():Result<Pair<GeoCoordinate,GpsFix?>> {
        val fine=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val coarse=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if(!fine&&!coarse) return Result.failure(SecurityException("Konum izni verilmedi."))
        return try{
            val location=withTimeoutOrNull(17_000L){
                suspendCancellableCoroutine<Location?> { continuation ->
                    val token=CancellationTokenSource()
                    val request=CurrentLocationRequest.Builder()
                        .setPriority(if(fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                        .setMaxUpdateAgeMillis(5_000L)
                        .setDurationMillis(15_000L)
                        .build()
                    fused.getCurrentLocation(request,token.token)
                        .addOnSuccessListener { if(continuation.isActive) continuation.resume(it) }
                        .addOnFailureListener { if(continuation.isActive) continuation.resume(null) }
                    continuation.invokeOnCancellation{token.cancel()}
                }
            }?:return Result.failure(IllegalStateException("Konum alınamadı. GPS'i açıp tekrar dene."))
            @Suppress("DEPRECATION")
            val isMock=if(Build.VERSION.SDK_INT>=31) location.isMock else location.isFromMockProvider
            if(isMock) return Result.failure(IllegalStateException("Test konumu kullanılamaz."))
            val age=(SystemClock.elapsedRealtimeNanos()-location.elapsedRealtimeNanos)/1_000_000L
            if(age !in 0L..15_000L) return Result.failure(IllegalStateException("GPS konumu güncel değil."))
            if(!location.hasAccuracy()||!location.accuracy.isFinite()||
                location.accuracy>(if(fine)100f else 1_500f))
                return Result.failure(IllegalStateException("GPS doğruluğu yetersiz. Tekrar dene."))
            val point=GeoCoordinate(location.latitude,location.longitude)
            if(!point.valid()) Result.failure(IllegalStateException("GPS geçersiz koordinat döndürdü."))
            else {
                // Preserve approximate origins, but only publish a genuine, usable fix
                // to the shared map/vehicle telemetry state.
                val fix=GpsFix(
                    coordinate=point,
                    accuracyMeters=location.accuracy,
                    recordedAtMillis=location.time,
                    bearingDegrees=location.bearing.takeIf { location.hasBearing() && it.isFinite() && it>=0f && it<360f },
                    speedMetersPerSecond=location.speed.takeIf { location.hasSpeed() && it.isFinite() && it>=0f && it<=90f }
                ).takeIf { it.usable(System.currentTimeMillis()) }
                Result.success(point to fix)
            }
        }catch(c:kotlinx.coroutines.CancellationException){throw c}
        catch(e:SecurityException){Result.failure(SecurityException("Konum izni verilmedi."))}
        catch(e:Exception){Result.failure(IllegalStateException("Konum alınamadı: ${e.message?:"GPS hatası"}"))}
    }

    /** Existing location API remains compatible with all previous callers. */
    suspend fun getCurrentCoordinate():Result<GeoCoordinate> =
        getCurrentMeasurement().map { it.first }
}

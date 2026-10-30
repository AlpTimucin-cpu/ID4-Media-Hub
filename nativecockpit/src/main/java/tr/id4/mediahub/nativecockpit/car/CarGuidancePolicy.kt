package tr.id4.mediahub.nativecockpit.car

import tr.id4.mediahub.nativecockpit.domain.model.GpsFix
import tr.id4.mediahub.nativecockpit.domain.model.RouteSnapshot
import tr.id4.mediahub.nativecockpit.domain.model.validRouteGeometry
import tr.id4.mediahub.nativecockpit.domain.usecase.RouteProgressTracker
import kotlin.math.roundToLong

/** A snapshot backed by a VALIDATED OSRM route and an actual fresh GPS fix.
 * OSRM duration/arrival are traffic-free MODELS, not measured vehicle facts.
 */
data class CarGuidance(
    val routeId: String,
    val destination: String,
    val remainingMeters: Double,
    val remainingSeconds: Long,
    val destinationArrivalEpochMs: Long,
    val nextCue: String?,
    val nextManeuverId: String?,
    val nextMeters: Double?,
    val nextTurn: CarTurn?,
    val nextStepArrivalEpochMs: Long?,
    val nextStepSeconds: Long?,
    val gpsRecordedAtMs: Long
)

class CarGuidancePolicy {
    private val progress = RouteProgressTracker()

    fun snapshot(route: RouteSnapshot?, fix: GpsFix?, nowMs: Long): CarGuidance? {
        if (fix?.usable(nowMs) != true || fix.accuracyMeters > 35f) return null
        if (route == null || !validRouteGeometry(route.routePoints)) return null
        val id = route.routeId?.takeIf { it.isNotBlank() } ?: return null
        val enriched = progress.enrich(route, fix, nowMs) ?: return null
        val km = enriched.remainingKm?.takeIf { it.isFinite() && it >= 0 } ?: return null
        val endTime = enriched.estimatedArrivalEpochMs?.takeIf { it >= nowMs } ?: return null
        val seconds = ((endTime - nowMs) / 1000).coerceAtLeast(0L)
        val turn = enriched.nextManeuver
        val turnMeters = enriched.nextTurnMeters?.takeIf { it.isFinite() && it >= 0.0 }
        val cue = turn?.instructionTr?.takeIf { it.isNotBlank() && it.length <= 140 }
        // Travel time to the NEXT step is only a traffic-free distance-ratio estimate.
        val stepSeconds = turnMeters?.let { meters ->
            val fullMeters = km * 1_000.0
            if (fullMeters > 0) (seconds * (meters / fullMeters).coerceIn(0.0, 1.0))
                .roundToLong().coerceAtLeast(0L) else 0L
        }
        return CarGuidance(
            routeId = id,
            destination = route.destination?.takeIf { it.isNotBlank() }?.take(80) ?: "Varış noktası",
            remainingMeters = km * 1_000.0,
            remainingSeconds = seconds,
            destinationArrivalEpochMs = endTime,
            nextCue = cue,
            nextManeuverId = turn?.id,
            nextMeters = if (cue != null) turnMeters else null,
            nextTurn = CarTurnMapping.fromOsrm(turn?.osrmType, turn?.osrmModifier),
            nextStepArrivalEpochMs = stepSeconds?.let { nowMs + it * 1_000L },
            nextStepSeconds = stepSeconds,
            gpsRecordedAtMs = fix.recordedAtMillis
        )
    }
}

/** Avoid one host IPC per GPS/clock tick and retain responsive step transitions. */
class CarTripUpdateGate {
    private var last: CarGuidance? = null
    private var publishedMs: Long = 0L

    fun clear() { last = null; publishedMs = 0L }

    fun shouldPublish(candidate: CarGuidance, nowMs: Long): Boolean {
        val prior = last
        val changed = prior == null || prior.routeId != candidate.routeId ||
            prior.nextManeuverId != candidate.nextManeuverId ||
            kotlin.math.abs(prior.remainingMeters - candidate.remainingMeters) >= 100.0 ||
            (prior.nextMeters != null && candidate.nextMeters != null &&
                kotlin.math.abs(prior.nextMeters - candidate.nextMeters) >= 25.0) ||
            nowMs - publishedMs >= 10_000L
        if (changed) { last = candidate; publishedMs = nowMs }
        return changed
    }
}

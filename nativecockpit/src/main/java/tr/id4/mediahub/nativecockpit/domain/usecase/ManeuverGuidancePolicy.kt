package tr.id4.mediahub.nativecockpit.domain.usecase

import tr.id4.mediahub.nativecockpit.domain.model.GeoCoordinate
import tr.id4.mediahub.nativecockpit.domain.model.GpsFix
import tr.id4.mediahub.nativecockpit.domain.model.RouteSnapshot
import tr.id4.mediahub.nativecockpit.domain.model.validRouteGeometry
import kotlin.math.cos
import kotlin.math.hypot

/** Pure Kotlin. No speech and no fake measurements. A route provider must
 * attach a stable routeId, current step id/coordinate and real road geometry.
 * Only fresh, precise GPS is eligible; the result is still approximate unless
 * a proper map matcher supplies a confirmed driving lane position.
 */
class ManeuverGuidancePolicy {
    private data class Projection(val metersAlong: Double, val offRoute: Double)
    private var routeAndStep: String? = null
    private var lastLevel = -1
    private var proposedLevel: Int? = null
    private var lastFixTime = 0L
    private var lastSpeechTime = 0L

    fun reset() {
        routeAndStep = null
        lastLevel = -1
        proposedLevel = null
        lastFixTime = 0L
        lastSpeechTime = 0L
    }

    /** Returns a *single* Turkish announcement or null. Never synthesizes routes. */
    fun nextAnnouncement(route: RouteSnapshot?, fix: GpsFix?, nowMs: Long): String? {
        if (route == null || fix == null) return null
        val id = route.routeId?.trim()?.takeIf { it.isNotEmpty() && it.length <= 128 } ?: return null
        val turn = route.nextManeuver ?: return null
        val stepId = turn.id.trim().takeIf { it.isNotEmpty() && it.length <= 128 } ?: return null
        val instruction = turn.instructionTr.trim().takeIf {
            it.length in 4..140 && it.none { c -> c.isISOControl() || c == '<' || c == '>' }
        } ?: return null
        val path = route.routePoints
        if (!validRouteGeometry(path) || !turn.location.valid()) return null
        if (!fix.usable(nowMs) || fix.accuracyMeters > 35f) return null

        val key = "$id|$stepId"
        if (key != routeAndStep) {
            routeAndStep = key
            lastLevel = -1
            proposedLevel = null
            lastFixTime = 0L
            lastSpeechTime = 0L
        }
        // The Compose UI can update every second with the same GPS fix.
        if (fix.recordedAtMillis <= lastFixTime) return null
        lastFixTime = fix.recordedAtMillis

        // Distance is projected ALONG the actual route, not crow-flies distance.
        val vehicle = nearest(path, fix.coordinate) ?: return null
        val maneuver = nearest(path, turn.location) ?: return null
        if (vehicle.offRoute > 35.0 || maneuver.offRoute > 25.0) return null
        val ahead = maneuver.metersAlong - vehicle.metersAlong
        if (ahead < -10.0 || ahead > 1_100.0) return null
        // Only two stages: announce at 200 m, then again at 50 m.
        // A driver joining close to a junction hears the 50 m stage only.
        val level = when {
            ahead <= 50.0 -> 1
            ahead <= 200.0 -> 0
            else -> return null
        }
        if (level <= lastLevel || (lastSpeechTime > 0 && nowMs - lastSpeechTime < 4_000L)) {
            proposedLevel = null
            return null
        }
        // IMPORTANT: do not mark as spoken until TextToSpeech accepted the request.
        proposedLevel = level
        return (if (level == 0) "İki yüz metre sonra, " else "Elli metre sonra, ") +
            instruction
    }

    /** Called only when the Android TTS engine successfully queues an utterance. */
    fun confirmSpoken(nowMs: Long) {
        proposedLevel?.let { level ->
            lastLevel = maxOf(lastLevel, level)
            lastSpeechTime = nowMs
        }
        proposedLevel = null
    }

    private fun nearest(route: List<GeoCoordinate>, p: GeoCoordinate): Projection? {
        val metersPerLon = 111_320.0 * cos(Math.toRadians(p.latitude))
        val metersPerLat = 111_195.0
        var bestOffset = Double.POSITIVE_INFINITY
        var bestAlong = 0.0
        var cumulative = 0.0
        for (i in 0 until route.lastIndex) {
            val a = route[i]
            val b = route[i+1]
            val ax = (a.longitude-p.longitude)*metersPerLon
            val ay = (a.latitude-p.latitude)*metersPerLat
            val dx = (b.longitude-a.longitude)*metersPerLon
            val dy = (b.latitude-a.latitude)*metersPerLat
            val length = hypot(dx,dy)
            if (length < 0.1 || !length.isFinite()) continue
            val t = (-(ax*dx + ay*dy)/(length*length)).coerceIn(0.0,1.0)
            val offset = hypot(ax+t*dx, ay+t*dy)
            if (offset < bestOffset) {
                bestOffset = offset
                bestAlong = cumulative + t*length
            }
            cumulative += length
        }
        return if (bestOffset.isFinite()) Projection(bestAlong,bestOffset) else null
    }
}

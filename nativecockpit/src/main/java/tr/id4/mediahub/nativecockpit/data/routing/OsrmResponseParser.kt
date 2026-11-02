package tr.id4.mediahub.nativecockpit.data.routing

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import tr.id4.mediahub.nativecockpit.domain.model.*
import java.util.UUID
import kotlin.math.ceil

/** Parses only verifiable OSRM fields. Unknown maneuvers are dropped, not guessed. */
object OsrmResponseParser {
    fun parse(json: String, targetLabel: String): List<RouteOption> {
        require(json.length in 30..6_000_000) { "Invalid OSRM response size" }
        val root=JsonParser.parseString(json).asJsonObject
        require(root.string("code")=="Ok") { "OSRM returned no usable route" }
        val routes=root.getAsJsonArray("routes") ?: error("No route")
        require(routes.size() in 1..12) { "Invalid number of alternatives" }
        val sessionId=UUID.randomUUID().toString()
        val result=routes.mapIndexedNotNull { index, el ->
            try {
                val route=el.asJsonObject
                val distance=route.number("distance") ?: return@mapIndexedNotNull null
                val seconds=route.number("duration") ?: return@mapIndexedNotNull null
                if (distance !in 50.0..5_000_000.0 || seconds !in 10.0..259_200.0)
                    return@mapIndexedNotNull null
                val geometry=route.getAsJsonObject("geometry") ?: return@mapIndexedNotNull null
                if(geometry.string("type")!="LineString")return@mapIndexedNotNull null
                val vertices=geometry.getAsJsonArray("coordinates") ?: return@mapIndexedNotNull null
                if(vertices.size() !in 2..20_000)return@mapIndexedNotNull null
                val points=vertices.map { c ->
                    val a=c.asJsonArray
                    require(a.size()>=2)
                    GeoCoordinate(a[1].asDouble,a[0].asDouble).also { require(it.valid()) }
                }
                if(!validRouteGeometry(points))return@mapIndexedNotNull null
                val id="$sessionId:$index"
                val maneuvers=mutableListOf<NavigationManeuver>()
                route.getAsJsonArray("legs")?.forEachIndexed { legIndex, legElement ->
                    legElement.asJsonObject.getAsJsonArray("steps")?.forEachIndexed { stepIndex, element ->
                        val step=element.asJsonObject
                        val m=step.getAsJsonObject("maneuver") ?: return@forEachIndexed
                        val pos=m.getAsJsonArray("location") ?: return@forEachIndexed
                        if(pos.size()<2)return@forEachIndexed
                        val coordinate=GeoCoordinate(pos[1].asDouble,pos[0].asDouble)
                        if(!coordinate.valid())return@forEachIndexed
                        val instruction=OsrmTurkishInstructions.speak(
                            m.string("type"),m.string("modifier"),step.string("name"))
                            ?:return@forEachIndexed
                        maneuvers.add(NavigationManeuver("$id:$legIndex:$stepIndex", instruction,coordinate,
                            m.string("type"), m.string("modifier")))
                    }
                }
                // OSRM speed annotations describe the router's speed profile.
                // Only align them to geometry if there is exactly one leg.
                val allLegs = route.getAsJsonArray("legs")
                val rawSpeeds = if (allLegs?.size() == 1) runCatching {
                    allLegs[0].asJsonObject.getAsJsonObject("annotation")
                        ?.getAsJsonArray("speed")
                }.getOrNull() else null
                val segments: List<ProfileSpeedSegment> =
                    if (rawSpeeds != null && rawSpeeds.size() == points.size - 1) {
                        rawSpeeds.mapIndexedNotNull { i, el ->
                            val metresPerSecond = runCatching { el.asDouble }.getOrNull()
                            metresPerSecond?.takeIf { it.isFinite() && it in 0.0..80.0 }?.let { v ->
                                ProfileSpeedSegment(points[i], points[i + 1], v * 3.6)
                            }
                        }.takeIf { it.size == rawSpeeds.size() }.orEmpty()
                    } else emptyList()
                val snap=RouteSnapshot(
                    destination=targetLabel.take(120),
                    routeId=id, routePoints=points,
                    totalKm=distance/1000,
                    osrmDurationSeconds=seconds,
                    estimatedDriveMinutes=ceil(seconds/60).toLong(),
                    maneuvers=maneuvers,
                    profileSpeeds=segments
                )
                RouteOption(snap,distance/1000,ceil(seconds/60).toLong())
            }catch(_: RuntimeException){null}
        }
        require(result.isNotEmpty()) { "Invalid OSRM data" }
        return result
    }
    private fun JsonObject.string(key:String):String? =
        get(key)?.takeUnless{it.isJsonNull}?.takeIf{it.isJsonPrimitive&&it.asJsonPrimitive.isString}?.asString
    private fun JsonObject.number(key:String):Double?=try{
        get(key)?.asDouble?.takeIf { it.isFinite() }
    }catch(_: Exception){null}
}

/** OSRM supplies turn type/modifier, not Turkish text. No fake events or place names. */
object OsrmTurkishInstructions {
    fun speak(type:String?,modifier:String?,name:String?):String? {
        val direction=when(modifier){
            "left"->"left";"right"->"right";"slight left"->"slightly left"
            "slight right"->"slightly right";"sharp left"->"sharply left"
            "sharp right"->"sharply right";"uturn"->"back";"straight"->"straight"
            else->null
        }
        val message=when(type){
            "turn", "end of road"->direction?.let { if(it=="straight")"Continue straight" else "Turn $it" }
            "new name", "continue"->"Continue straight"
            "fork"->direction?.let {"Take the fork $it"}
            "merge"->direction?.let {"Merge $it"}
            "on ramp"->direction?.let {"Take the connecting road $it"}
            "off ramp"->direction?.let {"Take the exit $it"}
            "roundabout","rotary"->"Enter the roundabout"
            "exit roundabout", "exit rotary"->"Exit the roundabout"
            else->null // OSRM unknown type: stay silent
        }?:return null
        val road=name.orEmpty().trim().takeIf {
            it.length in 2..55 && it.none { c -> c.isISOControl() || c=='<' || c=='>' }
        }
        return (if(road==null)message else "$message, toward $road").take(140)
    }
}

package tr.id4.mediahub.nativecockpit.domain.usecase

import tr.id4.mediahub.nativecockpit.domain.model.*
import kotlin.math.*

/** Estimated progress from measured GPS and original OSRM GeoJSON. Not lane-level map matching. */
class RouteProgressTracker {
    private data class Projection(val meters:Double,val offset:Double)
    private var cachedId:String?=null
    private var coords:List<GeoCoordinate> = emptyList()
    private var distanceMeters= doubleArrayOf()
    private var total=0.0
    private var maneuvers=emptyList<Pair<NavigationManeuver,Double>>()

    private fun load(route:RouteSnapshot){
        if(route.routeId==cachedId && coords===route.routePoints)return
        cachedId=route.routeId;coords=route.routePoints
        distanceMeters=DoubleArray(coords.size)
        for(i in 1 until coords.size){
            distanceMeters[i]=distanceMeters[i-1]+haversine(coords[i-1],coords[i])
        }
        total=distanceMeters.lastOrNull()?:0.0
        maneuvers=route.maneuvers.mapNotNull{m->
            project(m.location)?.takeIf{it.offset<=75}?.let{m to it.meters}
        }.sortedBy{it.second}
    }
    fun enrich(route:RouteSnapshot?,fix:GpsFix?,nowMs:Long):RouteSnapshot? {
        if(route==null)return null
        val empty=route.copy(remainingKm=null,estimatedArrivalEpochMs=null,
            nextManeuver=null,nextTurnMeters=null,nextTurnInstruction=null,gpsProgressFraction=null)
        if(fix?.usable(nowMs)!=true || fix.accuracyMeters>35f ||
            !validRouteGeometry(route.routePoints))return empty
        load(route)
        if(total<=0.0)return empty
        val location=project(fix.coordinate)?:return empty
        if(location.offset>35.0)return empty // avoid wrong-side/parallel-road announcements
        val fraction=(location.meters/total).coerceIn(0.0,1.0)
        val distanceKm=route.totalKm?.takeIf{it.isFinite()&&it>=0}?:return empty
        val duration=route.osrmDurationSeconds?.takeIf{it.isFinite()&&it>0}?:return empty
        val remaining=distanceKm*(1.0-fraction)
        val remainingSeconds=duration*(1.0-fraction)
        val next=maneuvers.firstOrNull{it.second>=location.meters-9.0}
        // Treat clock ETA as MODEL; OSRM has NO traffic feed.
        return empty.copy(remainingKm=remaining,
            estimatedDriveMinutes=ceil(remainingSeconds/60.0).toLong(),
            estimatedArrivalEpochMs=nowMs+(remainingSeconds*1000).toLong(),
            gpsProgressFraction=fraction.toFloat(),
            nextManeuver=next?.first,
            nextTurnInstruction=next?.first?.instructionTr,
            nextTurnMeters=next?.let{(it.second-location.meters).coerceAtLeast(0.0)})
    }
    private fun project(position:GeoCoordinate):Projection? {
        if(coords.size<2 || distanceMeters.size!=coords.size)return null
        val cosLat=cos(Math.toRadians(position.latitude))
        val unitX=111_320.0*cosLat;val unitY=111_195.0
        var best=Double.POSITIVE_INFINITY;var along=0.0
        for(i in 0 until coords.lastIndex){
            val a=coords[i];val b=coords[i+1]
            val ax=(a.longitude-position.longitude)*unitX
            val ay=(a.latitude-position.latitude)*unitY
            val dx=(b.longitude-a.longitude)*unitX
            val dy=(b.latitude-a.latitude)*unitY
            val len2=dx*dx+dy*dy
            if(len2<.01 || !len2.isFinite())continue
            val t=(-(ax*dx+ay*dy)/len2).coerceIn(0.0,1.0)
            val offset=hypot(ax+t*dx,ay+t*dy)
            if(offset<best){best=offset;along=distanceMeters[i]+t*(distanceMeters[i+1]-distanceMeters[i])}
        }
        return if(best.isFinite())Projection(along,best)else null
    }
    private fun haversine(a:GeoCoordinate,b:GeoCoordinate):Double {
        val lat1=Math.toRadians(a.latitude);val lat2=Math.toRadians(b.latitude)
        val deltaLat=lat2-lat1;val deltaLon=Math.toRadians(b.longitude-a.longitude)
        val t=sin(deltaLat/2).pow(2)+cos(lat1)*cos(lat2)*sin(deltaLon/2).pow(2)
        return 2.0*6_371_000.0*asin(sqrt(t.coerceIn(0.0,1.0)))
    }
}

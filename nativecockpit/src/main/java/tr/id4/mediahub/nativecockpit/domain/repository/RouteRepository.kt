package tr.id4.mediahub.nativecockpit.domain.repository

import kotlinx.coroutines.flow.StateFlow
import tr.id4.mediahub.nativecockpit.domain.model.*

/** One active OSRM route, one selected alternative and one Photon autocomplete owner. */
interface RouteRepository {
    val currentRoute: StateFlow<RouteSnapshot?>
    val options: StateFlow<List<RouteOption>>
    val selectedIndex: StateFlow<Int?>

    suspend fun search(query: String): List<RoutePlace>
    suspend fun calculate(origin: GeoCoordinate, destination: RoutePlace)
    fun choose(index: Int)
    fun clear()
}

package tr.id4.mediahub.nativecockpit.domain.repository

import kotlinx.coroutines.flow.StateFlow
import tr.id4.mediahub.nativecockpit.domain.model.*

/** Shared singleton backing phone, map, charging corridor and Android Auto. */
interface RouteRepository {
    val currentRoute: StateFlow<RouteSnapshot?>
    val options: StateFlow<List<RouteOption>>
    val selectedIndex: StateFlow<Int?>
    suspend fun search(query: String): List<RoutePlace>
    suspend fun calculate(origin: GeoCoordinate, destination: RoutePlace): RouteCalculationResult
    fun choose(index: Int)
    fun clear()
}

package tr.id4.mediahub.nativecockpit.presentation.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import tr.id4.mediahub.nativecockpit.data.weather.OpenMeteoWeatherRepository
import tr.id4.mediahub.nativecockpit.domain.model.RouteWeatherState
import tr.id4.mediahub.nativecockpit.domain.repository.RouteRepository

@HiltViewModel
class RouteWeatherViewModel @Inject constructor(
    private val weather: OpenMeteoWeatherRepository,
    routes: RouteRepository
) : ViewModel() {
    val uiState: StateFlow<RouteWeatherState> = routes.currentRoute
        .flatMapLatest { route ->
            flow {
                if (route?.routeId == null) {
                    emit(RouteWeatherState())
                    return@flow
                }
                emit(RouteWeatherState(routeId = route.routeId, loading = true))
                var previous: RouteWeatherState? = null
                while (true) {
                    // Refresh every 15 minutes while actively observed.
                    // Cancelled on route switch, onStop, or ViewModel clearance.
                    val result = weather.routeWeather(route)
                    val next = result.fold(
                        onSuccess = { it },
                        onFailure = {
                            previous?.copy(error = "Hava verisi güncellenemedi (önceki tahmin)")
                                ?: RouteWeatherState(routeId = route.routeId,
                                    error = "Hava durumu alınamadı; rota ve TTS kullanılabilir")
                        }
                    )
                    previous = next.takeIf { it.points.isNotEmpty() }
                    emit(next)
                    delay(15 * 60_000L)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RouteWeatherState())
}

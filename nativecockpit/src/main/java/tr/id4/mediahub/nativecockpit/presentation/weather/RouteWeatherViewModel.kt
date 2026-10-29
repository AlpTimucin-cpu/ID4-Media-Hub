package tr.id4.mediahub.nativecockpit.presentation.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
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
                // Cancellation on route switch prevents showing an old destination.
                val result = weather.routeWeather(route)
                emit(result.fold(
                    onSuccess = { it },
                    onFailure = {
                        RouteWeatherState(routeId = route.routeId,
                            error = "Hava durumu alınamadı; rota ve TTS kullanılabilir")
                    }
                ))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RouteWeatherState())
}

package tr.id4.mediahub.nativecockpit.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import tr.id4.mediahub.nativecockpit.BuildConfig
import tr.id4.mediahub.nativecockpit.domain.repository.RouteRepository

/** Android Auto Car App Library entry point. Does NOT start an Activity or play video. */
@AndroidEntryPoint
class EyaCarAppService : CarAppService() {
    @Inject lateinit var routes: RouteRepository

    override fun createHostValidator(): HostValidator =
        if (BuildConfig.DEBUG) HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        else HostValidator.Builder(this)
            .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
            .build()

    override fun onCreateSession(): Session = EyaCarSession(routes)
    override fun onCreateSession(sessionInfo: SessionInfo): Session = EyaCarSession(routes)
}

private class EyaCarSession(private val routes: RouteRepository) : Session() {
    override fun onCreateScreen(intent: Intent): Screen =
        EyaCarNavigationScreen(carContext, routes)
}

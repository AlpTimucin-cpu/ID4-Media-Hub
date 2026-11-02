package tr.id4.mediahub.nativecockpit.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import tr.id4.mediahub.nativecockpit.data.repository.OsrmRouteRepository
import tr.id4.mediahub.nativecockpit.domain.repository.RouteRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RouteModule {
    @Binds
    @Singleton
    abstract fun bindRouteRepository(impl: OsrmRouteRepository): RouteRepository
}

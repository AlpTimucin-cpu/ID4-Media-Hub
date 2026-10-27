package tr.id4.mediahub.nativecockpit.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import tr.id4.mediahub.nativecockpit.data.location.AndroidLocationRepository
import tr.id4.mediahub.nativecockpit.domain.repository.LocationRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {
    @Binds @Singleton
    abstract fun bindLocationRepository(repository: AndroidLocationRepository): LocationRepository
}

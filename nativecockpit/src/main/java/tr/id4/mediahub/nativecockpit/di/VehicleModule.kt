package tr.id4.mediahub.nativecockpit.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import tr.id4.mediahub.nativecockpit.data.repository.DisconnectedVehicleRepository
import tr.id4.mediahub.nativecockpit.domain.repository.VehicleRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class VehicleModule {
    @Binds
    @Singleton
    abstract fun bindVehicleRepository(
        implementation: DisconnectedVehicleRepository
    ): VehicleRepository
}

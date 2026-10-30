package tr.id4.mediahub.nativecockpit.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import tr.id4.mediahub.nativecockpit.data.repository.Elm327VehicleRepository
import tr.id4.mediahub.nativecockpit.domain.repository.VehicleRepository
import tr.id4.mediahub.nativecockpit.domain.repository.ObdConnectionController

@Module
@InstallIn(SingletonComponent::class)
abstract class VehicleModule {
    @Binds @Singleton
    abstract fun bindVehicleRepository(implementation: Elm327VehicleRepository): VehicleRepository
    @Binds @Singleton
    abstract fun bindObdController(implementation: Elm327VehicleRepository): ObdConnectionController
}

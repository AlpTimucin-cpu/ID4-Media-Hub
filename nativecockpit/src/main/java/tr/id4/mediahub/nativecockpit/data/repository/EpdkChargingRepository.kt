package tr.id4.mediahub.nativecockpit.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tr.id4.mediahub.nativecockpit.data.remote.EpdkService
import tr.id4.mediahub.nativecockpit.data.remote.EpdkSnapshotParser
import tr.id4.mediahub.nativecockpit.domain.model.ChargingCatalog
import tr.id4.mediahub.nativecockpit.domain.repository.ChargingRepository

@Singleton
class EpdkChargingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val service: EpdkService
): ChargingRepository {
    private val mutex=Mutex()
    private val _catalog=MutableStateFlow(ChargingCatalog())
    override val catalog: StateFlow<ChargingCatalog> = _catalog.asStateFlow()
    private val cache: File get()=File(context.cacheDir,"eya_epdk_public_v1.json.gz")

    override suspend fun refresh() = mutex.withLock {
        withContext(Dispatchers.IO) {
            val previous=_catalog.value
            _catalog.value=previous.copy(loading=true,error=null)
            // Immediately publish cached source WITH original snapshot date.
            val cached=runCatching { if(cache.isFile) EpdkSnapshotParser.parseGzip(
                cache.inputStream().use { EpdkSnapshotParser.readBounded(it,EpdkSnapshotParser.MAX_GZIP_BYTES) }) else null
            }.getOrNull()
            if(cached!=null) _catalog.value=ChargingCatalog(cached.stations,cached.verifiedAtUtc,
                loading=true,staleCache=true)
            try {
                val network=service.getPublicStations().use {
                    EpdkSnapshotParser.readBounded(it.byteStream(),EpdkSnapshotParser.MAX_GZIP_BYTES)
                }
                val result=EpdkSnapshotParser.parseGzip(network)
                // Atomic replacement; preserve previous cache if network is corrupt.
                val temp=File(cache.parentFile,cache.name+".new")
                try {
                    temp.writeBytes(network)
                    check(temp.renameTo(cache)) { "İstasyon önbelleği güncellenemedi" }
                } finally { temp.delete() }
                _catalog.value=ChargingCatalog(result.stations,result.verifiedAtUtc,loading=false)
            } catch(e: Exception) {
                val existing=_catalog.value
                _catalog.value=existing.copy(loading=false,staleCache=existing.stations.isNotEmpty(),
                    error="EPDK yenilenemedi · ${e.javaClass.simpleName}")
            }
        }
        Unit
    }
}

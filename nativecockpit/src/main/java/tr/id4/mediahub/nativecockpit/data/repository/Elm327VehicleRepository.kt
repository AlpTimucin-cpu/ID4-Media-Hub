package tr.id4.mediahub.nativecockpit.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import tr.id4.mediahub.nativecockpit.data.obd.ElmSpeedParser
import tr.id4.mediahub.nativecockpit.data.obd.MebUdsParser
import tr.id4.mediahub.nativecockpit.domain.model.ObdConnectionState
import tr.id4.mediahub.nativecockpit.domain.model.ObdPhase
import tr.id4.mediahub.nativecockpit.domain.model.PairedObdDevice
import tr.id4.mediahub.nativecockpit.domain.model.TimedValue
import tr.id4.mediahub.nativecockpit.domain.model.VehicleTelemetry
import tr.id4.mediahub.nativecockpit.domain.repository.ObdConnectionController
import tr.id4.mediahub.nativecockpit.domain.repository.VehicleRepository

/**
 * Bluetooth Classic ELM327 SPP. All traffic is serialized in one worker:
 * - standard 010D speed polling when the ECU supports it;
 * - explicit, one-shot, read-only UDS 22 queries from a strict allowlist.
 * ELM adapter settings are restored after diagnostics; no VIN, no writes,
 * no unverified battery values flowing into cockpit telemetry.
 */
@Singleton
class Elm327VehicleRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : VehicleRepository, ObdConnectionController {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _telemetry = MutableStateFlow(VehicleTelemetry())
    override val telemetry: StateFlow<VehicleTelemetry> = _telemetry.asStateFlow()
    private val _connection = MutableStateFlow(ObdConnectionState())
    override val connection: StateFlow<ObdConnectionState> = _connection.asStateFlow()
    private val diagnosticRequests = Channel<String>(capacity=1)
    private var task: Job? = null
    @Volatile private var socket: BluetoothSocket? = null
    private val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private fun hasPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override fun refreshPaired() {
        if (!hasPermission()) { permissionDenied(); return }
        try {
            val found = adapter?.bondedDevices.orEmpty().mapNotNull { device ->
                val address = device.address.orEmpty()
                if (!BluetoothAdapter.checkBluetoothAddress(address)) return@mapNotNull null
                PairedObdDevice(device.name?.take(60) ?: "Eşleştirilmiş Bluetooth aygıtı", address)
            }.sortedBy { it.name.lowercase() }
            _connection.value = _connection.value.copy(devices=found,
                error=if (adapter == null) "Bluetooth donanımı bulunamadı" else null)
        } catch (_: SecurityException) { permissionDenied() }
    }

    override fun permissionDenied() {
        _connection.value = _connection.value.copy(error="Bluetooth bağlantı izni gerekli")
    }

    @SuppressLint("MissingPermission")
    override fun connect(address: String) {
        if (_connection.value.phase == ObdPhase.CONNECTING ||
            _connection.value.phase == ObdPhase.CONNECTED) return
        if (!hasPermission()) { permissionDenied(); return }
        refreshPaired()
        val device = try { adapter?.bondedDevices?.firstOrNull { it.address == address } }
            catch (_: SecurityException) { null }
        if (device == null) {
            _connection.value = _connection.value.copy(error="Önce adaptörü Android Bluetooth ayarlarında eşleştirin")
            return
        }
        task?.cancel()
        task = scope.launch {
            _connection.value = _connection.value.copy(phase=ObdPhase.CONNECTING,
                selectedDevice=address,error=null,lastSpeedAtMs=null,
                lastPollSucceeded=false,diagnosticBusy=false,diagnosticResult=null)
            _telemetry.value = VehicleTelemetry()
            try {
                withContext(Dispatchers.IO) { runSession(device) }
            } catch (_: CancellationException) {
                // disconnect() closes socket to unblock Bluetooth reads.
            } catch (e: Exception) {
                _connection.value = _connection.value.copy(phase=ObdPhase.ERROR,
                    diagnosticBusy=false,
                    error=when(e) {
                        is SecurityException -> "Bluetooth izni reddedildi"
                        is IOException -> "ELM327 bağlantısı/yanıtı başarısız; adaptörü kontrol edin"
                        else -> "ELM327 bağlantısı kurulamadı"
                    })
            } finally {
                socket?.closeSilently();socket=null
                _telemetry.value=VehicleTelemetry()
                if (_connection.value.phase != ObdPhase.ERROR)
                    _connection.value=_connection.value.copy(phase=ObdPhase.DISCONNECTED,
                        diagnosticBusy=false,diagnosticResult=null)
            }
        }
    }

    override fun requestMebDiagnostic(command: String) {
        if (command !in MebUdsParser.ALLOWED_REQUESTS) return
        if (_connection.value.phase != ObdPhase.CONNECTED || _connection.value.diagnosticBusy) return
        _connection.value=_connection.value.copy(diagnosticBusy=true,diagnosticResult=null,error=null)
        if (diagnosticRequests.trySend(command).isFailure) {
            _connection.value=_connection.value.copy(diagnosticBusy=false,
                error="OBD teşhis kuyruğu şu an meşgul")
        }
    }

    override fun clearMebDiagnostic() {
        _connection.value=_connection.value.copy(diagnosticResult=null)
    }

    @SuppressLint("MissingPermission")
    private suspend fun runSession(device: BluetoothDevice) {
        val conn=device.createRfcommSocketToServiceRecord(spp)
        socket=conn
        val watchdog=scope.launch {
            delay(12_000L)
            if (!conn.isConnected) conn.closeSilently()
        }
        try { conn.connect() } finally { watchdog.cancel() }
        if (!conn.isConnected) throw IOException("SPP connection unavailable")
        for (command in listOf("ATZ","ATE0","ATL0","ATS0","ATH0","ATSP0"))
            sendAndRead(conn,command)
        _connection.value=_connection.value.copy(phase=ObdPhase.CONNECTED,error=null)
        while (kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.isActive == true) {
            val query=diagnosticRequests.tryReceive().getOrNull()
            if (query != null) {
                try {
                    val response=runMebReadOnlyQuery(conn,query)
                    val parsed=MebUdsParser.parse(query,response,System.currentTimeMillis())
                    _connection.value=_connection.value.copy(diagnosticBusy=false,
                        diagnosticResult=parsed,error=null)
                } catch (e:CancellationException) { throw e }
                catch (_:Exception) {
                    _connection.value=_connection.value.copy(diagnosticBusy=false,
                        error="MEB ECU yanıtı alınamadı; adaptör 29-bit CAN desteklemiyor olabilir")
                    // Uncertain adapter state: end session rather than misinterpret speed.
                    throw IOException("MEB diagnostic session failed")
                }
            }
            val response=sendAndRead(conn,"010D")
            val speed=ElmSpeedParser.decode(response)
            if (speed != null) {
                val now=System.currentTimeMillis()
                _telemetry.value=VehicleTelemetry(speedKmh=TimedValue(speed,now))
                _connection.value=_connection.value.copy(lastPollSucceeded=true,
                    lastSpeedAtMs=now,error=null)
            } else {
                _telemetry.value=VehicleTelemetry()
                _connection.value=_connection.value.copy(lastPollSucceeded=false,
                    error="Araç 010D hız sorgusuna geçerli yanıt vermedi")
            }
            delay(2_500L)
        }
    }

    private suspend fun runMebReadOnlyQuery(conn:BluetoothSocket,command:String):String {
        // ELM CAN 29-bit 500 kbps / battery ECU request 17FC007B.
        // Only send requests explicitly chosen by the driver.
        var initiated=false
        try {
            for (adapterCommand in listOf("ATH1","ATSP7","ATCAF1","ATCFC1",
                "ATSH${MebUdsParser.REQUEST_ECU}")) {
                val reply=sendAndRead(conn,adapterCommand)
                if (reply.contains('?') || reply.uppercase().contains("ERROR"))
                    throw IOException("ELM protocol setting unsupported")
            }
            initiated=true
            return sendAndRead(conn,command)
        } finally {
            if (initiated || conn.isConnected) {
                // Restore original 010D automatic protocol and hidden headers.
                sendAndRead(conn,"ATSP0")
                sendAndRead(conn,"ATH0")
            }
        }
    }

    private suspend fun sendAndRead(conn:BluetoothSocket,command:String):String {
        conn.outputStream.write((command+"\r").toByteArray(Charsets.US_ASCII))
        conn.outputStream.flush()
        return withTimeout(6_000L) {
            val input=conn.inputStream
            val builder=StringBuilder()
            val chunk=ByteArray(256)
            while (!builder.contains('>')) {
                val ready=input.available()
                if (ready>0) {
                    val count=input.read(chunk,0,minOf(chunk.size,ready))
                    if (count<0) throw IOException("ELM327 connection closed")
                    builder.append(String(chunk,0,count,Charsets.US_ASCII))
                    if (builder.length>4096) throw IOException("ELM327 reply oversized")
                } else delay(35)
            }
            builder.toString()
        }
    }

    override fun disconnect() {
        task?.cancel();task=null
        socket?.closeSilently();socket=null
        while (diagnosticRequests.tryReceive().isSuccess) { /* discard pending reads */ }
        _telemetry.value=VehicleTelemetry()
        _connection.value=_connection.value.copy(phase=ObdPhase.DISCONNECTED,
            selectedDevice=null,lastPollSucceeded=false,lastSpeedAtMs=null,
            diagnosticBusy=false,diagnosticResult=null,error=null)
    }

    private fun BluetoothSocket.closeSilently() { try { close() } catch (_:Exception) { } }
}

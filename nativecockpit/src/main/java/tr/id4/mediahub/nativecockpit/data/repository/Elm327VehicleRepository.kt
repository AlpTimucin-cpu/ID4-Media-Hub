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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import tr.id4.mediahub.nativecockpit.data.obd.ElmSpeedParser
import tr.id4.mediahub.nativecockpit.domain.model.ObdConnectionState
import tr.id4.mediahub.nativecockpit.domain.model.ObdPhase
import tr.id4.mediahub.nativecockpit.domain.model.PairedObdDevice
import tr.id4.mediahub.nativecockpit.domain.model.TimedValue
import tr.id4.mediahub.nativecockpit.domain.model.VehicleTelemetry
import tr.id4.mediahub.nativecockpit.domain.repository.ObdConnectionController
import tr.id4.mediahub.nativecockpit.domain.repository.VehicleRepository

/**
 * Bluetooth Classic ELM327 SPP, read-only OBD-II Mode 01 PID 0D.
 * No ECU writes, no UDS guesses, and no fabricated HV battery SoC.
 * Driver starts/stops the connection explicitly from the PHONE interface.
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
    private var task: Job? = null
    @Volatile private var socket: BluetoothSocket? = null
    private val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private fun hasPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // Android 12+ checked above
    override fun refreshPaired() {
        if (!hasPermission()) { permissionDenied(); return }
        try {
            val found = adapter?.bondedDevices.orEmpty().mapNotNull { device ->
                val address = device.address.orEmpty()
                if (!BluetoothAdapter.checkBluetoothAddress(address)) return@mapNotNull null
                PairedObdDevice(device.name?.take(60) ?: "Eşleştirilmiş Bluetooth aygıtı", address)
            }.sortedBy { it.name.lowercase() }
            _connection.value = _connection.value.copy(devices = found,
                error = if (adapter == null) "Bluetooth donanımı bulunamadı" else null)
        } catch (_: SecurityException) { permissionDenied() }
    }

    override fun permissionDenied() {
        _connection.value = _connection.value.copy(
            error = "Bluetooth bağlantı izni gerekli (Android ayarları).")
    }

    @SuppressLint("MissingPermission") // explicit runtime permission + bonded validation
    override fun connect(address: String) {
        if (_connection.value.phase == ObdPhase.CONNECTING ||
            _connection.value.phase == ObdPhase.CONNECTED) return
        if (!hasPermission()) { permissionDenied(); return }
        refreshPaired()
        val device = try {
            adapter?.bondedDevices?.firstOrNull { it.address == address }
        } catch (_: SecurityException) { null }
        if (device == null) {
            _connection.value = _connection.value.copy(error = "Önce ELM327 adaptörünü telefon ayarlarında eşleştirin")
            return
        }
        task?.cancel()
        task = scope.launch {
            _connection.value = _connection.value.copy(phase = ObdPhase.CONNECTING,
                selectedDevice = address, error = null, lastSpeedAtMs = null,
                lastPollSucceeded = false)
            _telemetry.value = VehicleTelemetry()
            try {
                withContext(Dispatchers.IO) { runSession(device) }
            } catch (_: CancellationException) {
                // The socket close in disconnect() interrupts blocking Bluetooth operations.
            } catch (e: Exception) {
                _connection.value = _connection.value.copy(phase = ObdPhase.ERROR,
                    error = when (e) {
                        is SecurityException -> "Bluetooth izni reddedildi"
                        is IOException -> "ELM327 bağlantısı veya yanıtı başarısız. Adaptörü kontrol edin."
                        else -> "ELM327 bağlantısı kurulamadı"
                    })
            } finally {
                socket?.closeSilently(); socket = null
                _telemetry.value = VehicleTelemetry() // stale speed never survives disconnect
                if (_connection.value.phase != ObdPhase.ERROR)
                    _connection.value = _connection.value.copy(phase = ObdPhase.DISCONNECTED)
            }
        }
    }

    private suspend fun runSession(device: BluetoothDevice) {
        val conn = device.createRfcommSocketToServiceRecord(spp)
        socket = conn
        // BluetoothSocket.connect() is blocking, and cannot be interrupted by coroutine
        // cancellation alone. A separate watchdog closes the socket after 12 seconds.
        val watchdog = scope.launch {
            delay(12_000L)
            if (!conn.isConnected) conn.closeSilently()
        }
        try { conn.connect() } finally { watchdog.cancel() }
        if (!conn.isConnected) throw IOException("SPP connection unavailable")
        for (command in listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")) {
            sendAndRead(conn, command)
        }
        _connection.value = _connection.value.copy(phase = ObdPhase.CONNECTED, error = null)
        while (kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.isActive == true) {
            val response = sendAndRead(conn, "010D")
            val speed = ElmSpeedParser.decode(response)
            if (speed != null) {
                val now = System.currentTimeMillis()
                _telemetry.value = VehicleTelemetry(speedKmh = TimedValue(speed, now))
                _connection.value = _connection.value.copy(lastPollSucceeded = true,
                    lastSpeedAtMs = now, error = null)
            } else {
                _telemetry.value = VehicleTelemetry()
                _connection.value = _connection.value.copy(lastPollSucceeded = false,
                    error = "Araç 010D hız sorgusuna geçerli yanıt vermedi. SoC okunmuyor.")
            }
            delay(2_500L)
        }
    }

    private suspend fun sendAndRead(conn: BluetoothSocket, command: String): String {
        val output = conn.outputStream
        output.write((command + "\r").toByteArray(Charsets.US_ASCII))
        output.flush()
        // Avoid uninterruptible blocking reads: poll available bytes until '>' prompt.
        return withTimeout(6_000L) {
            val input = conn.inputStream
            val builder = StringBuilder()
            val chunk = ByteArray(256)
            while (!builder.contains('>')) {
                val ready = input.available()
                if (ready > 0) {
                    val count = input.read(chunk, 0, minOf(chunk.size, ready))
                    if (count < 0) throw IOException("ELM327 closed connection")
                    builder.append(String(chunk, 0, count, Charsets.US_ASCII))
                    if (builder.length > 4096) throw IOException("ELM327 reply oversized")
                } else delay(35)
            }
            builder.toString()
        }
    }

    override fun disconnect() {
        task?.cancel();task = null
        socket?.closeSilently();socket = null
        _telemetry.value = VehicleTelemetry()
        _connection.value = _connection.value.copy(phase = ObdPhase.DISCONNECTED,
            selectedDevice = null, lastPollSucceeded = false,
            lastSpeedAtMs = null, error = null)
    }

    private fun BluetoothSocket.closeSilently() {
        try { close() } catch (_: Exception) { }
    }
}

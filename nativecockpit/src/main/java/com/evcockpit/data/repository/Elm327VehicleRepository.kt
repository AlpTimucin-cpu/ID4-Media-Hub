package com.evcockpit.data.repository

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
import com.evcockpit.data.obd.ElmSpeedParser
import com.evcockpit.data.obd.MebUdsParser
import com.evcockpit.domain.model.EngineeringMath
import com.evcockpit.domain.model.EngineeringMetrics
import com.evcockpit.domain.model.EngineeringSnapshot
import com.evcockpit.domain.model.MebResponseStatus
import com.evcockpit.domain.model.ObdConnectionState
import com.evcockpit.domain.model.ObdPhase
import com.evcockpit.domain.model.PairedObdDevice
import com.evcockpit.domain.model.TimedValue
import com.evcockpit.domain.model.VehicleTelemetry
import com.evcockpit.domain.repository.ObdConnectionController
import com.evcockpit.domain.repository.ObdTransport
import com.evcockpit.domain.usecase.Elm327Session
import com.evcockpit.domain.usecase.ElmSessionPhase
import com.evcockpit.domain.repository.VehicleRepository

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
    private val _engineering = MutableStateFlow(EngineeringSnapshot())
    override val engineering: StateFlow<EngineeringSnapshot> = _engineering.asStateFlow()
    private val _metrics = MutableStateFlow(EngineeringMetrics())
    override val engineeringMetrics: StateFlow<EngineeringMetrics> = _metrics.asStateFlow()

    private fun recalculate() {
        _metrics.value = EngineeringMath.calculate(_engineering.value, System.currentTimeMillis())
    }
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
                PairedObdDevice(device.name?.take(60) ?: "Paired Bluetooth device", address)
            }.sortedBy { it.name.lowercase() }
            _connection.value = _connection.value.copy(devices=found,
                error=if (adapter == null) "Bluetooth hardware was not found" else null)
        } catch (_: SecurityException) { permissionDenied() }
    }

    override fun permissionDenied() {
        _connection.value = _connection.value.copy(error="Bluetooth connection permission is required")
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
            _connection.value = _connection.value.copy(error="Pair the adapter in Android Bluetooth settings first")
            return
        }
        task?.cancel()
        task = scope.launch {
            _connection.value = _connection.value.copy(phase=ObdPhase.CONNECTING,
                selectedDevice=address,error=null,lastSpeedAtMs=null,
                lastPollSucceeded=false,diagnosticBusy=false,diagnosticResult=null)
            _telemetry.value = VehicleTelemetry()
            _engineering.value = EngineeringSnapshot()
            _metrics.value = EngineeringMetrics()
            try {
                withContext(Dispatchers.IO) { runSession(device) }
            } catch (_: CancellationException) {
                // disconnect() closes socket to unblock Bluetooth reads.
            } catch (e: Exception) {
                _connection.value = _connection.value.copy(phase=ObdPhase.ERROR,
                    diagnosticBusy=false,
                    error=when(e) {
                        is SecurityException -> "Bluetooth permission denied"
                        is IOException -> "ELM327 connection/response failed; check the adapter"
                        else -> "Could not connect to ELM327"
                    })
            } finally {
                socket?.closeSilently();socket=null
                _telemetry.value=VehicleTelemetry()
                _engineering.value=EngineeringSnapshot()
                _metrics.value=EngineeringMetrics()
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
                error="The OBD diagnostic queue is busy")
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
        // Adapt the same physical RFCOMM socket to the tested Elm327Session.
        // The single runSession worker still owns all CAN header/filter changes.
        val transport = object : ObdTransport {
            override val connected: Boolean get() = conn.isConnected
            override suspend fun connect(timeoutMillis: Long) {
                if (!conn.isConnected) throw IOException("SPP socket is no longer connected")
            }
            override suspend fun exchange(command: String, timeoutMillis: Long): String =
                sendAndRead(conn, command)
            override fun disconnect() { conn.closeSilently() }
        }
        val protocol = Elm327Session(transport)
        try {
            protocol.initialize() // ATZ, ATE0, ATL0; READY only on real response.
            if (protocol.state.value.phase != ElmSessionPhase.READY) {
                throw IOException("ELM327 initialization was not acknowledged")
            }
            for (command in listOf("ATS0","ATH0","ATSP0"))
                sendAndRead(conn, command)
            _connection.value = _connection.value.copy(phase=ObdPhase.CONNECTED, error=null)
        // Background-in-app polling starts immediately after explicit pairing.
        // A single worker owns ALL ELM I/O: no parallel CAN commands or write DIDs.
        val cycle = listOf(
            "22028C", "221E3B", "221E3D", "221E0E", "221E0F",
            "221E33", "221E34", "221E1B", "221E32", "222AB2"
        )
        var cursor = 0
        while (kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.isActive == true) {
            val manual = diagnosticRequests.tryReceive().getOrNull()
            val command = manual ?: cycle[cursor++ % cycle.size]
            try {
                val response = runMebReadOnlyQuery(conn, protocol, command)
                val parsed = MebUdsParser.parse(command, response, System.currentTimeMillis())
                if (parsed.status == MebResponseStatus.VALID_RAW) {
                    _engineering.value = EngineeringMath.update(
                        _engineering.value, command, parsed.payloadHex, parsed.receivedAtMillis
                    )
                } else {
                    _engineering.value = _engineering.value.copy(
                        unsuccessfulReads = _engineering.value.unsuccessfulReads + 1
                    )
                }
                recalculate()
                if (manual != null) {
                    _connection.value = _connection.value.copy(
                        diagnosticBusy = false, diagnosticResult = parsed, error = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _engineering.value = _engineering.value.copy(
                    unsuccessfulReads = _engineering.value.unsuccessfulReads + 1
                )
                if (manual != null) _connection.value = _connection.value.copy(
                    diagnosticBusy = false,
                    error = "MEB read failed; inspect adapter CAN protocol support"
                )
                // A rejected, unsupported or timed-out DID is not evidence of an
                // ECU defect; continue with other safe reads.
            }

            try {
                val speed = ElmSpeedParser.decode(protocol.readRawOnce("010D"))
                if (speed != null) {
                    val now = System.currentTimeMillis()
                    _telemetry.value = VehicleTelemetry(speedKmh = TimedValue(speed, now))
                    _engineering.value = _engineering.value.copy(speedKmh = TimedValue(speed, now))
                    recalculate()
                    _connection.value = _connection.value.copy(
                        lastPollSucceeded = true, lastSpeedAtMs = now
                    )
                } else {
                    _telemetry.value = VehicleTelemetry()
                    _connection.value = _connection.value.copy(lastPollSucceeded = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _telemetry.value = VehicleTelemetry()
                _connection.value = _connection.value.copy(lastPollSucceeded = false)
            }
            delay(700L)
        }
        } finally {
            protocol.close() // Closes only the socket owned by this session.
        }
    }

    private suspend fun runMebReadOnlyQuery(
        conn: BluetoothSocket, protocol: Elm327Session, command: String
    ): String {
        // ELM: battery ECU 29-bit/500kbps; 2AB2 capacity is 11-bit 710/77A.
        // UDS service 22 READ ONLY; this function never sends service 2E/31/14.
        var initiated=false
        try {
            val capacityRead = command == "222AB2"
            // Match CAN settings actually observed in this vehicle's Car Scanner
            // session. Older ELM firmware requires 6-digit ATSH + ATCP17.
            val configuration = if (capacityRead) listOf(
                "ATH1", "ATSP6", "ATCAF1", "ATCFC1",
                "ATSH710", "ATCRA77A", "ATFCSH710",
                "ATFCSD300000", "ATFCSM1"
            ) else listOf(
                "ATH1", "ATSP7", "ATCP17", "ATCAF1", "ATCFC1",
                "ATSHFC007B", "ATCRA17FE007B", "ATFCSH17FC007B",
                "ATFCSD300000", "ATFCSM1"
            )
            for (adapterCommand in configuration) {
                val reply=sendAndRead(conn,adapterCommand)
                if (reply.contains('?') || reply.uppercase().contains("ERROR"))
                    throw IOException("ELM protocol setting unsupported")
            }
            initiated=true
            return protocol.readRawOnce(command)
        } finally {
            if (initiated || conn.isConnected) {
                // Drop restrictive CAN RX filter / custom ISO-TP flow control
                // before trying generic SAE 010D across any 11-bit ECU.
                for (reset in listOf("ATFCSM0", "ATCRA", "ATSP0", "ATH0")) {
                    try { sendAndRead(conn, reset) }
                    catch (_: Exception) { /* fail closed on the next read */ }
                }
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
        _engineering.value=EngineeringSnapshot()
        _metrics.value=EngineeringMetrics()
        _connection.value=_connection.value.copy(phase=ObdPhase.DISCONNECTED,
            selectedDevice=null,lastPollSucceeded=false,lastSpeedAtMs=null,
            diagnosticBusy=false,diagnosticResult=null,error=null)
    }

    private fun BluetoothSocket.closeSilently() { try { close() } catch (_:Exception) { } }
}

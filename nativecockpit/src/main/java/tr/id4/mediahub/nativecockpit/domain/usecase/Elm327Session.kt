package tr.id4.mediahub.nativecockpit.domain.usecase

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tr.id4.mediahub.nativecockpit.domain.repository.ObdTransport

/** One request at a time; bounded retry with explicit disconnect on failure. */
class Elm327Session(private val transport: ObdTransport) {
    private val mutex = Mutex()
    @Volatile private var closed = false

    suspend fun initialize(): String = mutex.withLock {
        closed = false
        connectAndInitialize()
    }

    suspend fun readOnly(command: String): String = mutex.withLock {
        val query = command.uppercase().replace(" ", "")
        require(query.matches(Regex("(?:01|09)[0-9A-F]{2,6}|22[0-9A-F]{4}"))) {
            "Gecersiz OBD okuma sorgusu"
        }
        check(!closed)
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                if (!transport.connected) connectAndInitialize()
                val result = transport.exchange(query, 3_000L)
                if (result.isBlank() || result.contains("NO DATA", true) ||
                    result.contains("ERROR", true)
                ) throw IOException("OBD yanit veremedi")
                return@withLock result
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                last = e
                transport.disconnect()
            } catch (e: Exception) {
                last = e
                transport.disconnect()
            }
            if (attempt < 2) delay(400L * (1 shl attempt))
        }
        throw IOException("OBD 3 denemede okunamadi", last)
    }

    private suspend fun connectAndInitialize(): String {
        check(!closed)
        if (!transport.connected) transport.connect()
        return try {
            var version = ""
            for (cmd in listOf("ATZ", "ATE0", "ATL0")) {
                val reply = transport.exchange(cmd, if (cmd == "ATZ") 6_000L else 3_000L)
                if (reply.contains("ERROR", true) || reply.contains("?"))
                    throw IOException("ELM init: $cmd")
                if (cmd == "ATZ") version = reply
            }
            version
        } catch (e: Exception) {
            transport.disconnect()
            throw e
        }
    }

    fun close() {
        closed = true
        transport.disconnect()
    }
}

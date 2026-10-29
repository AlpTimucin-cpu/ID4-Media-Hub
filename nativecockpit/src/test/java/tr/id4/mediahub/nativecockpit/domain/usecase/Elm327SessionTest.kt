package tr.id4.mediahub.nativecockpit.domain.usecase

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import tr.id4.mediahub.nativecockpit.domain.repository.ObdTransport

class Elm327SessionTest {
    private class FakeTransport : ObdTransport {
        override var connected: Boolean = false
            private set
        val commands = mutableListOf<String>()
        var failOnce = false
        var connections = 0
        override suspend fun connect(timeoutMillis: Long) {
            connections++
            connected = true
        }
        override suspend fun exchange(command: String, timeoutMillis: Long): String {
            commands.add(command)
            if (failOnce && command == "221E0E") {
                failOnce = false
                throw IOException("Adapter disconnected")
            }
            return when (command) {
                "ATZ" -> "ELM327 v1.5"
                "ATE0", "ATL0" -> "OK"
                else -> "62 1E 0E 04 68"
            }
        }
        override fun disconnect() { connected = false }
    }

    @Test fun startsWithExactInitializationSequence() = runBlocking {
        val fake = FakeTransport()
        val session = Elm327Session(fake)
        session.initialize()
        assertEquals(listOf("ATZ", "ATE0", "ATL0"), fake.commands)
        assertEquals("62 1E 0E 04 68", session.readOnly("22 1E 0E"))
        session.close()
    }

    @Test fun retriesConnectionAfterReadFailure() = runBlocking {
        val fake = FakeTransport().apply { failOnce = true }
        val session = Elm327Session(fake)
        session.initialize()
        assertEquals("62 1E 0E 04 68", session.readOnly("221E0E"))
        assertEquals(2, fake.connections)
        assertEquals(2, fake.commands.count { it == "ATZ" })
        session.close()
    }

    @Test fun dangerousNonReadCommandsAreRejected(): Unit = runBlocking {
        val session = Elm327Session(FakeTransport())
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { session.readOnly("2F1234") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { session.readOnly("ATSH7E0") }
        }
    }
}

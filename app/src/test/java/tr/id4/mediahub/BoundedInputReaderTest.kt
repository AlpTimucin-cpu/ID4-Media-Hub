package tr.id4.mediahub

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class BoundedInputReaderTest {
    @Test fun emptyInputIsSupported() {
        assertArrayEquals(byteArrayOf(), BoundedInputReader.read(ByteArrayInputStream(byteArrayOf()), 10))
    }

    @Test fun exactBoundaryIsAccepted() {
        val input = ByteArray(8192) { (it % 251).toByte() }
        assertArrayEquals(input, BoundedInputReader.read(ByteArrayInputStream(input), input.size))
    }

    @Test fun oversizedInputIsRejectedWithoutConsumingEntireStream() {
        val input = ByteArray(200_000) { 42 }
        val stream = ByteArrayInputStream(input)
        assertNull(BoundedInputReader.read(stream, 512))
        assertEquals(input.size - 513, stream.available())
    }

    @Test fun smallChunksDoNotLoseData() {
        val data = "ABCDEF".toByteArray()
        val source = object : InputStream() {
            private val input = ByteArrayInputStream(data)
            override fun read(): Int = input.read()
            override fun read(bytes: ByteArray, off: Int, len: Int): Int =
                input.read(bytes, off, minOf(2, len))
        }
        assertArrayEquals(data, BoundedInputReader.read(source, 20))
    }

    @Test(expected = IOException::class)
    fun failuresArePropagatedToCaller() {
        BoundedInputReader.read(object : InputStream() {
            override fun read(): Int = throw IOException("Synthetic test failure")
            override fun read(bytes: ByteArray, off: Int, len: Int): Int =
                throw IOException("Synthetic test failure")
        }, 100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidLimitIsRejected() {
        BoundedInputReader.read(ByteArrayInputStream(byteArrayOf()), 0)
    }
}

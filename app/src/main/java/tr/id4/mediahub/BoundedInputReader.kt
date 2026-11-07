package tr.id4.mediahub

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reads up to [maxBytes], returning null if the source exceeds that limit. */
internal object BoundedInputReader {
    fun read(input: InputStream, maxBytes: Int): ByteArray? {
        require(maxBytes in 1..1_048_576) { "Invalid input size limit" }
        val output = ByteArrayOutputStream(minOf(8192, maxBytes))
        val chunk = ByteArray(8192)
        var total = 0
        // Read one extra byte to distinguish an exactly-full input from an oversized one.
        while (total <= maxBytes) {
            val read = input.read(chunk, 0, minOf(chunk.size, maxBytes + 1 - total))
            if (read < 0) return output.toByteArray()
            if (read == 0) continue
            total += read
            if (total > maxBytes) return null
            output.write(chunk, 0, read)
        }
        return null
    }
}

package tr.id4.mediahub

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object PlaylistLoader {
    private const val MAX_BYTES = 2_000_000

    fun fromHttps(url: String): String {
        require(M3uParser.isHttpsUrl(url)) { "Yalnızca HTTPS M3U adresleri kabul edilir." }
        val connection = (URL(url).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "audio/x-mpegurl, application/x-mpegURL, text/plain")
        }
        return try {
            require(connection.responseCode == 200) { "Liste sunucusu HTTP ${connection.responseCode} döndürdü." }
            connection.inputStream.use { readLimited(it) }
        } finally { connection.disconnect() }
    }

    fun readLimited(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            require(total <= MAX_BYTES) { "M3U listesi 2 MB sınırını aşıyor." }
            out.write(buffer, 0, count)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}

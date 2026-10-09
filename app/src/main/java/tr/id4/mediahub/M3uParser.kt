package tr.id4.mediahub

/** Pure Kotlin parser for user-owned M3U IPTV lists, intentionally independent of Android. */
data class PlaylistEntry(
    val name: String,
    val url: String,
    val group: String,
    val isAudio: Boolean,
    val logo: String = ""
)

object M3uParser {
    private val attribute = Regex("([A-Za-z][A-Za-z0-9_-]*)=\"([^\"]*)\"")
    private val audioGroups = setOf("radio", "radyo", "internet radio", "internet radyo", "audio", "podcast", "podcasts")
    private val audioExtensions = setOf("mp3", "aac", "ogg", "opus", "flac", "m4a")
    private const val MAX_ENTRIES = 2500

    fun parse(text: String): List<PlaylistEntry> {
        // HLS manifests describe stream segments, not an IPTV channel catalogue.
        if (text.lineSequence().any { it.startsWith("#EXT-X-", ignoreCase = true) }) return emptyList()
        val result = mutableListOf<PlaylistEntry>()
        val used = mutableSetOf<String>()
        var title: String? = null
        var group = "Diğer"
        var audioHint = false
        var logo = ""
        var waiting = false
        for (line in text.lineSequence()) {
            val current = line.trim().removePrefix("\uFEFF")
            if (current.startsWith("#EXTINF:", ignoreCase = true)) {
                val attrs = attribute.findAll(current).associate {
                    it.groupValues[1].lowercase() to it.groupValues[2].trim()
                }
                group = attrs["group-title"].orEmpty().ifBlank { "Diğer" }.take(100)
                logo = attrs["tvg-logo"].orEmpty().takeIf { isHttpsUrl(it) }.orEmpty()
                val label = current.substringAfter(',', "").trim()
                title = attrs["tvg-name"].orEmpty().ifBlank { label }.take(160)
                audioHint = attrs["radio"].equals("true", true) || attrs["radio"] == "1" ||
                    attrs["type"].equals("radio", true) || attrs["tvg-type"].equals("radio", true)
                waiting = true
            } else if (current.startsWith("#EXTGRP:", ignoreCase = true) && waiting) {
                group = current.substringAfter(':').trim().ifBlank { group }.take(100)
            } else if (waiting && current.isNotEmpty() && !current.startsWith('#')) {
                val url = current
                if (isHttpsUrl(url) && used.add(url)) {
                    val path = url.substringBefore('?').substringBefore('#').lowercase()
                    val extension = path.substringAfterLast('.', "")
                    val normalizedGroup = group.trim().lowercase()
                    val groupIsAudio = audioGroups.any { normalizedGroup == it || normalizedGroup.startsWith("$it ") || normalizedGroup.startsWith("$it-") }
                    val clearAudio = audioHint || groupIsAudio || extension in audioExtensions
                    val clearlyVideo = extension in setOf("mp4", "mkv", "mov", "avi", "webm", "ts")
                    result.add(PlaylistEntry(title.orEmpty().ifBlank { "Kanal ${result.size + 1}" },
                        url, group, clearAudio && !clearlyVideo, logo))
                }
                waiting = false
                title = null
                group = "Diğer"
                audioHint = false
                logo = ""
                if (result.size >= MAX_ENTRIES) break
            }
        }
        return result
    }

    /** Network sources must be HTTPS and must have a hostname. */
    fun isHttpsUrl(url: String): Boolean = try {
        val address = java.net.URI(url)
        address.scheme.equals("https", ignoreCase = true) && !address.host.isNullOrBlank() &&
            address.rawUserInfo == null && url.length <= 2048
    } catch (_: Exception) { false }
}

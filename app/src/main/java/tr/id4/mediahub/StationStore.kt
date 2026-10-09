package tr.id4.mediahub

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Station(
    val id: String,
    val name: String,
    val url: String,
    val group: String = "Radyo",
    val favorite: Boolean = false
) {
    /** Keep M3U query tokens private until the station is actually played. */
    fun toMediaItem(includeUri: Boolean = false): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .also { builder ->
            if (includeUri) {
                builder.setUri(Uri.parse(url))
                if (url.substringBefore('?').endsWith(".m3u8", ignoreCase = true))
                    builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
        }
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(name)
            .setSubtitle(group)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .build())
        .build()
}

/** Small, local catalogue: only audio stations are exposed to Android Auto. */
object StationStore {
    private const val PREFS = "id4_media_hub"
    private const val KEY = "stations"
    private const val MAX_STATIONS = 2500
    private val defaults = listOf(
        Station("soma-groovesalad", "Groove Salad • SomaFM", "https://ice5.somafm.com/groovesalad-128-mp3", "SomaFM"),
        Station("soma-gsclassic", "Groove Salad Classic • SomaFM", "https://ice5.somafm.com/gsclassic-128-mp3", "SomaFM")
    )

    fun load(context: Context): List<Station> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return defaults
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until minOf(array.length(), MAX_STATIONS)) {
                    val obj = array.getJSONObject(i)
                    val station = Station(
                        obj.getString("id"), obj.getString("name"), obj.getString("url"),
                        obj.optString("group", "Radyo"), obj.optBoolean("favorite", false)
                    )
                    if (validUrl(station.url)) add(station)
                }
            }
        } catch (_: Exception) { defaults }
    }

    fun add(context: Context, name: String, url: String, group: String = "Radyo"): Boolean {
        val cleanUrl = url.trim()
        if (name.isBlank() || !validUrl(cleanUrl)) return false
        val stations = load(context).toMutableList()
        if (stations.size >= MAX_STATIONS || stations.any { it.url == cleanUrl }) return false
        stations.add(Station(UUID.randomUUID().toString(), name.trim().take(160), cleanUrl, group.trim().take(100).ifBlank { "Radyo" }))
        save(context, stations)
        return true
    }

    fun addAll(context: Context, entries: List<PlaylistEntry>): Int {
        val stations = load(context).toMutableList()
        val used = stations.mapTo(mutableSetOf()) { it.url }
        var count = 0
        for (entry in entries) {
            if (stations.size >= MAX_STATIONS) break
            if (entry.isAudio && validUrl(entry.url) && used.add(entry.url)) {
                stations.add(Station(UUID.randomUUID().toString(), entry.name, entry.url, entry.group))
                count++
            }
        }
        if (count > 0) save(context, stations)
        return count
    }

    fun toggleFavorite(context: Context, id: String) =
        save(context, load(context).map { if (it.id == id) it.copy(favorite = !it.favorite) else it })

    fun remove(context: Context, id: String) =
        save(context, load(context).filterNot { it.id == id })

    private fun save(context: Context, stations: List<Station>) {
        val data = JSONArray()
        stations.forEach { s ->
            data.put(JSONObject().put("id", s.id).put("name", s.name).put("url", s.url)
                .put("group", s.group).put("favorite", s.favorite))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, data.toString()).apply()
        // Refresh an already open Android Auto library; never broadcast playlist contents/URLs.
        context.sendBroadcast(Intent(AudioLibraryService.ACTION_CATALOG_UPDATED).setPackage(context.packageName))
    }

    fun validUrl(url: String): Boolean = try {
        M3uParser.isHttpsUrl(url.trim())
    } catch (_: Exception) { false }
}

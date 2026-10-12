package tr.id4.mediahub

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class VideoChannel(
    val id: String,
    val name: String,
    val url: String,
    val group: String,
    val favorite: Boolean,
    val logo: String = "",
    val tvgId: String = ""
)

/** Private on-device catalogue. Video channels never appear in the Android Auto media tree. */
object VideoStore {
    private const val PREFS = "id4_video_library"
    private const val KEY = "channels"

    fun load(context: Context): List<VideoChannel> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until minOf(array.length(), 2500)) {
                    val obj = array.getJSONObject(i)
                    val url = obj.getString("url")
                    if (M3uParser.isHttpsUrl(url)) add(VideoChannel(
                        obj.getString("id"), M3uParser.displayTitle(obj.getString("name")), url,
                        obj.optString("group", "Diğer"), obj.optBoolean("favorite", false),
                        obj.optString("logo", "").takeIf { M3uParser.isHttpsUrl(it) }.orEmpty(),
                        obj.optString("tvgId", "").take(160)
                    ))
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun add(context: Context, name: String, url: String, group: String = "Diğer"): Boolean {
        if (name.isBlank() || !M3uParser.isHttpsUrl(url)) return false
        val channels = load(context).toMutableList()
        if (channels.size >= 2500 || channels.any { it.url == url }) return false
        channels.add(VideoChannel(UUID.randomUUID().toString(), M3uParser.displayTitle(name), url,
            group.trim().take(100).ifBlank { "Diğer" }, false))
        save(context, channels)
        return true
    }

    fun addAll(context: Context, items: List<PlaylistEntry>): Int {
        val channels = load(context).toMutableList()
        val existing = channels.mapTo(mutableSetOf()) { it.url }
        var added = 0
        for (item in items) {
            if (channels.size >= 2500) break
            if (!item.isAudio && M3uParser.isHttpsUrl(item.url) && existing.add(item.url)) {
                channels.add(VideoChannel(UUID.randomUUID().toString(), M3uParser.displayTitle(item.name), item.url, item.group, false, item.logo, item.tvgId))
                added++
            }
        }
        if (added > 0) save(context, channels)
        return added
    }

    fun toggleFavorite(context: Context, id: String) {
        save(context, load(context).map { if (it.id == id) it.copy(favorite = !it.favorite) else it })
    }

    fun remove(context: Context, id: String) = save(context, load(context).filterNot { it.id == id })

    private fun save(context: Context, channels: List<VideoChannel>) {
        val json = JSONArray()
        channels.forEach { channel ->
            json.put(JSONObject().put("id", channel.id).put("name", channel.name)
                .put("url", channel.url).put("group", channel.group)
                .put("favorite", channel.favorite).put("logo", channel.logo)
                .put("tvgId", channel.tvgId))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json.toString()).apply()
    }
}

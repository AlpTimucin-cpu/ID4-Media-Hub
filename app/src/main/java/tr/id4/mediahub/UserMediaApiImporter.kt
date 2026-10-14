package tr.id4.mediahub

import org.json.JSONArray
import org.json.JSONObject

/**
 * Explicitly user-authorized HTTPS API importer. Supports documented common
 * JSON catalogue structures and M3U, not arbitrary proprietary provider APIs.
 * No provider keys, cookies, tokens or server data are hard-coded in the APK.
 */
object UserMediaApiImporter {
    private const val MAX_ITEMS = 15000
    private val rootFields = listOf("items", "data", "results", "channels", "streams", "videos", "movies", "series", "entries")
    private val nameFields = listOf("title", "name", "label", "stream_name", "streamName")
    private val urlFields = listOf("url", "stream_url", "streamUrl", "play_url", "playUrl", "media_url", "mediaUrl", "source")
    private val groupFields = listOf("group", "group_title", "groupTitle", "category", "genre")
    private val logoFields = listOf("logo", "tvg_logo", "tvgLogo", "poster", "image", "thumbnail")

    private fun firstString(json: JSONObject, keys: List<String>): String =
        keys.firstNotNullOfOrNull { key ->
            val value = json.optString(key, "").trim()
            value.takeIf { it.isNotEmpty() && it != "null" }
        }.orEmpty()

    private fun root(value: Any?): JSONArray? {
        return when (value) {
            is JSONArray -> value
            is JSONObject -> {
                var found: JSONArray? = null
                for (key in rootFields) {
                    val nested = value.opt(key)
                    found = when (nested) {
                        is JSONArray -> nested
                        is JSONObject -> root(nested)
                        else -> null
                    }
                    if (found != null) break
                }
                found
            }
            else -> null
        }
    }

    /** Parse format automatically; all candidate media URLs must be HTTPS. */
    fun parse(text: String): List<PlaylistEntry> {
        val trimmed = text.trimStart('\uFEFF','\r','\n',' ','\t')
        if (trimmed.startsWith("#EXTM3U",true))
            return M3uParser.parse(text).filterNot { it.isAudio }

        if (!trimmed.startsWith("[") && !trimmed.startsWith("{"))
            return emptyList()

        val json: Any = if(trimmed.startsWith("[")) JSONArray(trimmed)
                        else JSONObject(trimmed)
        val rows = root(json) ?: if(json is JSONObject) JSONArray().put(json)
                               else return emptyList()
        val found = ArrayList<PlaylistEntry>(minOf(rows.length(), MAX_ITEMS))
        val seen = HashSet<String>()
        for (i in 0 until minOf(rows.length(), MAX_ITEMS)) {
            val obj = rows.optJSONObject(i) ?: continue
            val title = firstString(obj, nameFields).take(100)
            val url = firstString(obj, urlFields)
            if (title.isBlank() || !M3uParser.isHttpsUrl(url) || !seen.add(url)) continue
            val group = firstString(obj, groupFields).take(100).ifBlank { "API" }
            val logo = firstString(obj, logoFields).takeIf(M3uParser::isHttpsUrl).orEmpty()
            val tvgId = obj.optString("tvg-id", "").take(160)
            found.add(PlaylistEntry(title,url,group,false,logo,tvgId))
        }
        return found
    }

    /** Load on a background thread from an HTTPS URL entered by the user. */
    fun fetch(url: String): List<PlaylistEntry> {
        require(M3uParser.isHttpsUrl(url)) { "HTTPS API adresi gerekli" }
        return parse(PlaylistLoader.fromHttps(url))
    }
}

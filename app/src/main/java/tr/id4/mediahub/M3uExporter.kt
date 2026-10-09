package tr.id4.mediahub

/** Lossless enough M3U backup for our own catalogue: no credentials or binary files. */
object M3uExporter {
    private fun clean(value: String, limit: Int): String = value
        .replace(Regex("[\\r\\n\\u0000-\\u001F]+"), " ")
        .replace('"', '\'')
        .trim().take(limit)

    fun export(items: List<PlaylistEntry>): String = buildString {
        append("#EXTM3U\n")
        items.filter { M3uParser.isHttpsUrl(it.url) }.distinctBy { it.url }.forEach { item ->
            val title = clean(item.name, 160).ifBlank { "Adsız kanal" }
            val group = clean(item.group, 100)
            append("#EXTINF:-1 tvg-name=\"").append(title)
                .append("\" group-title=\"").append(group)
                .append("\" radio=\"").append(if (item.isAudio) "true" else "false")
                .append("\",").append(title).append('\n')
            append(item.url.trim()).append('\n')
        }
    }
}

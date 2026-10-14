package tr.id4.mediahub

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

/**
 * TMDB official metadata API, independent of REC TV's proprietary catalog.
 * NEVER retrieves protected film/series streams or app credentials.
 */
data class TmdbTitle(
    val id: Int,
    val name: String,
    val overview: String,
    val posterUrl: String,
    val year: String,
    val rating: Double,
    val isSeries: Boolean
)

object TmdbCatalogClient {
    // User's bearer token remains in memory; never written as plain text on disk.
    @Volatile private var sessionToken: String = ""
    private const val API = "https://api.themoviedb.org/3/"
    private const val MAX_JSON = 1_500_000
    private val posterPattern = Regex("^/[A-Za-z0-9_-]{5,60}\\.(?:jpg|png|webp)$")
    private val tokenPattern = Regex("^[A-Za-z0-9._-]{24,700}$")

    fun hasToken(context: Context): Boolean = token(context).isNotBlank()
    private fun token(context: Context): String = sessionToken

    fun setToken(context: Context, value: String): Boolean {
        val clean=value.trim().removePrefix("Bearer ").trim()
        if (!tokenPattern.matches(clean)) return false
        sessionToken = clean
        return true
    }

    fun removeToken(context: Context) {
        sessionToken = ""
    }

    fun posterUrl(path: String?): String {
        val safe=path.orEmpty()
        return if(posterPattern.matches(safe)) "https://image.tmdb.org/t/p/w185"+safe else ""
    }

    /** User search or Turkish-localized weekly trending, movie/TV endpoints only. */
    fun relativePath(series: Boolean, term: String): String {
        val kind=if(series)"tv" else "movie"
        val query=term.trim().take(80)
        return if(query.isBlank()) "trending/"+kind+"/week?language=tr-TR"
        else "search/"+kind+"?language=tr-TR&include_adult=false&query="+
            URLEncoder.encode(query,"UTF-8")
    }

    fun parseJson(body: String, series: Boolean): List<TmdbTitle> {
        val array=JSONObject(body).optJSONArray("results") ?: return emptyList()
        return buildList {
            for(i in 0 until minOf(array.length(),20)) {
                val o=array.optJSONObject(i) ?: continue
                if(o.optBoolean("adult",false)) continue
                val id=o.optInt("id",0)
                val name=o.optString(if(series)"name" else "title","").trim().take(140)
                if(id<1 || name.isBlank()) continue
                val released=o.optString(if(series)"first_air_date" else "release_date","")
                add(TmdbTitle(id,name,
                    o.optString("overview","").trim().take(650),
                    posterUrl(o.optString("poster_path","")),
                    released.take(4).takeIf { it.length==4 && it.all(Char::isDigit) }.orEmpty(),
                    o.optDouble("vote_average",0.0).coerceIn(0.0,10.0),
                    series))
            }
        }
    }

    fun search(context: Context, series: Boolean, term: String): List<TmdbTitle> {
        val key=token(context)
        require(tokenPattern.matches(key)) { "TMDB API Read Access Token gerekli" }
        val address=URL(API+relativePath(series,term))
        val conn=address.openConnection() as HttpsURLConnection
        conn.connectTimeout=8_000
        conn.readTimeout=14_000
        conn.instanceFollowRedirects=false
        conn.setRequestProperty("Authorization","Bearer "+key)
        conn.setRequestProperty("Accept","application/json")
        try {
            val status=conn.responseCode
            require(status==200) { when(status){
                401,403 -> "TMDB erişim anahtarını kontrol et"
                429 -> "TMDB istek sınırı aşıldı. Biraz sonra tekrar dene."
                else -> "TMDB bağlantısı HTTP "+status+" yanıtı verdi"
            }}
            val bytes=conn.inputStream.use{it.readNBytes(MAX_JSON+1)}
            require(bytes.size<=MAX_JSON){"TMDB yanıtı çok büyük"}
            return parseJson(String(bytes,Charsets.UTF_8),series)
        } finally { conn.disconnect() }
    }

    /**
     * Local playback may launch only a separately supplied, authorized video
     * with the SAME normalized title. TMDB entries themselves are NOT streams.
     */
    fun matchingLocalTitle(t: TmdbTitle, sources: List<VideoChannel>): VideoChannel? {
        fun fold(s:String)=java.text.Normalizer.normalize(
            s.lowercase(Locale.ROOT).replace('ı','i'),java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"),"")
            .replace(Regex("[^a-z0-9 ]")," ").replace(Regex("\\s+")," ").trim()
        val wanted=fold(t.name)
        if(wanted.isBlank())return null
        val required=if(t.isSeries) VideoLibraryKind.SERIES else VideoLibraryKind.MOVIE
        return sources.firstOrNull {
            VideoLibraryClassifier.classify(it)==required &&
                fold(it.name)==wanted && M3uParser.isHttpsUrl(it.url)
        }
    }
}

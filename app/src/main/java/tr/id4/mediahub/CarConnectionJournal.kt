package tr.id4.mediahub

import android.content.Context
import java.text.DateFormat
import java.util.Date

/**
 * Local evidence about actual Media3 connections. Does not identify users,
 * store client packages, record media URLs, or assert car video support.
 *
 * Note: a car may connect a media session without browsing library root.
 * Tracking these separately avoids false negatives during AA diagnosis.
 */
object CarConnectionJournal {
    private const val PREF = "id4_car_connection_journal"
    private const val ANDROID_AUTO = "com.google.android.projection.gearhead"

    fun classifyClient(packageName: String, ownPackage: String): String = when (packageName) {
        ANDROID_AUTO -> "android_auto"
        ownPackage -> "phone"
        else -> "other_browser"
    }

    private fun write(context: Context, client: String, action: String) {
        val type = classifyClient(client, context.packageName)
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (action == "session") {
            editor.putLong("session_last", now).putString("session_last_type", type)
            if (type == "android_auto") editor.putLong("android_auto_session", now)
        } else {
            editor.putLong("library_last", now).putString("library_last_type", type)
            if (type == "android_auto") editor.putLong("android_auto_library", now)
        }
        editor.apply()
    }

    fun recordSessionConnection(context: Context, controllerPackage: String) =
        write(context, controllerPackage, "session")

    fun recordLibraryBrowse(context: Context, controllerPackage: String) =
        write(context, controllerPackage, "library")

    private fun format(ms: Long): String =
        if (ms <= 0L) "Henüz kaydedilmedi"
        else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(ms))

    fun summary(context: Context): String {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        fun clientLabel(code: String?): String = when (code) {
            "android_auto" -> "Android Auto"
            "phone" -> "Media Hub (telefon)"
            "other_browser" -> "Başka medya istemcisi"
            else -> "Bilinmiyor"
        }
        val sessionMs = p.getLong("session_last", 0)
        val browseMs = p.getLong("library_last", 0)
        val aaSessionMs = p.getLong("android_auto_session", 0)
        val aaLibraryMs = p.getLong("android_auto_library", 0)
        return buildString {
            appendLine("Son medya oturum bağlantısı: " + format(sessionMs))
            appendLine("Oturum istemcisi: " + clientLabel(p.getString("session_last_type", "")))
            appendLine("Son kitaplık isteği: " + format(browseMs))
            appendLine("Kitaplık istemcisi: " + clientLabel(p.getString("library_last_type", "")))
            appendLine("Android Auto oturum isteği: " + format(aaSessionMs))
            appendLine("Android Auto kitaplık isteği: " + format(aaLibraryMs))
            appendLine("Yalnızca Media Hub'ın telefon oturumu kaydedilmişse " +
                "bu, araçtaki uygulama görünürlüğünü kanıtlamaz.")
            appendLine("Android Auto kaydı olmaması tek başına uyumsuzluk kanıtı değildir.")
            append("Bu ölçüm video aktarımı veya sürüş halinde video desteği değildir.")
        }
    }

    fun clear(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
}

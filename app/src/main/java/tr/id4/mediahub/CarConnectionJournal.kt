package tr.id4.mediahub

import android.content.Context
import java.text.DateFormat
import java.util.Date

/**
 * On-device media browser evidence, not a claim that video is supported.
 * Records *library root requests* rather than phone UI opens.
 * Never stores controller IDs, stream URLs, auth tokens, or locations.
 */
object CarConnectionJournal {
    private const val PREF = "id4_car_connection_journal"
    private const val AUTO = "com.google.android.projection.gearhead"

    /** MediaLibraryService callback controllers are resolved by Android's session system. */
    fun classifyClient(packageName: String, ownPackage: String): String = when (packageName) {
        AUTO -> "android_auto"
        ownPackage -> "phone"
        else -> "other_browser"
    }

    fun recordLibraryBrowse(context: Context, controllerPackage: String) {
        val type = classifyClient(controllerPackage, context.packageName)
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putLong("last_any_library", now)
            .putString("last_client_type", type)
            .apply()
        if (type == "android_auto") {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putLong("last_android_auto", now).apply()
        }
    }

    fun summary(context: Context): String {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val last = prefs.getLong("last_any_library", 0L)
        val aa = prefs.getLong("last_android_auto", 0L)
        val kind = prefs.getString("last_client_type", "") ?: ""
        fun format(ms: Long) = if (ms <= 0L) "Henüz kayıt yok"
            else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                .format(Date(ms))
        val label = when (kind) {
            "android_auto" -> "Android Auto"
            "phone" -> "Telefondaki Media Hub"
            "other_browser" -> "Başka medya istemcisi"
            else -> "Bilinmiyor"
        }
        return "Son medya kitaplığı isteği: " + format(last) +
            "\nSon istemci: " + label +
            "\nAndroid Auto kitaplık isteği: " + format(aa) +
            "\nBu kayıt, uygulamanın Android Auto'da video gösterebildiğini kanıtlamaz."
    }

    fun clear(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
}

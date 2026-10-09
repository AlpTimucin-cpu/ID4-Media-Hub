package tr.id4.mediahub

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Clear local-only Android Auto capability report. This does not attempt to
 * bypass projection / driving restrictions or claim video capability.
 */
object CarCompatibility {
    private const val AA_PACKAGE = "com.google.android.projection.gearhead"

    private fun appVersion(activity: Activity, packageName: String): String {
        return try {
            val info = activity.packageManager.getPackageInfo(packageName, 0)
            info.versionName ?: "Bilinmiyor"
        } catch (_: PackageManager.NameNotFoundException) {
            "Yüklü görünmüyor veya gizli"
        } catch (_: Exception) {
            "Sorgulanamadı"
        }
    }

    fun report(activity: Activity): String = buildString {
        appendLine("ID.4 Media Hub • Araç bağlantı raporu")
        appendLine("Android API: " + Build.VERSION.SDK_INT)
        appendLine("Cihaz: " + Build.MANUFACTURER + " " + Build.MODEL)
        appendLine("Android Auto: " + appVersion(activity, AA_PACKAGE))
        appendLine("Media Hub: " + appVersion(activity, activity.packageName))
        appendLine("Android Auto ses kataloğu: MediaLibraryService (uygulama içinde)")
        appendLine("Telefon video oynatıcısı: Media3 + Web Medya")
        appendLine("Android Auto park halindeki VIDEO: bu platformda onaylanmış destek yok")
        appendLine("ID.4 üzerinde gerçek video: DOĞRULANMADI")
        appendLine("Bu rapor tek başına bağlantı veya araçtaki görünürlüğü kanıtlamaz.")
    }

    fun show(activity: Activity) {
        val contents = report(activity)
        AlertDialog.Builder(activity)
            .setTitle("Android Auto / ID.4 tanılama")
            .setMessage(contents)
            .setNegativeButton("Kapat", null)
            .setPositiveButton("Raporu kopyala") { _, _ ->
                val clipboard=activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("ID4 bağlantı raporu", contents))
            }
            .show()
    }
}

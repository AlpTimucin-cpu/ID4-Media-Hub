package tr.id4.mediahub

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.widget.Toast

/** Launch an already installed SportzX app by its visible name; no APK is bundled or modified. */
object SportzXLauncher {
    private const val SITE = "https://sportzxlive.com/"
    private const val KNOWN_PACKAGE = "com.sportzx.live"

    fun openIfInstalled(activity: Activity) {
        // Package name seen in multiple Android APK catalogs; not proof of authorship or safety.
        val known = activity.packageManager.getLaunchIntentForPackage(KNOWN_PACKAGE)
        if (known != null) {
            try {
                activity.startActivity(known)
                return
            } catch (_: Exception) {
                Toast.makeText(activity, "Yüklü SportzX başlatılamadı. Uygulamanın kendi açılışını kontrol et.", Toast.LENGTH_LONG).show()
            }
        }
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val options = activity.packageManager.queryIntentActivities(query, 0)
        val matches = options.filter {
            it.loadLabel(activity.packageManager).toString().replace(" ", "")
                .contains("sportzx", ignoreCase = true) ||
                it.activityInfo.packageName.contains("sportzx", ignoreCase = true)
        }.distinctBy { it.activityInfo.packageName }
        when (matches.size) {
            0 -> AlertDialog.Builder(activity)
                .setTitle("SportzX bulunamadı")
                .setMessage("Telefonda SportzX adlı bir uygulama bulunamadı. Bağlantısını verdiğiniz site, APK imzası ve yayın izinleri bakımından bağımsız doğrulanmadı. İsterseniz siteyi haricî tarayıcıda açabilirsiniz. Rastgele APK kurmayın.")
                .setNegativeButton("Kapat", null)
                .setPositiveButton("Siteyi incele") { _, _ -> openSite(activity) }
                .show()
            1 -> launch(activity, matches.first())
            else -> AlertDialog.Builder(activity)
                .setTitle("Açılacak uygulamayı seç")
                .setItems(matches.map { "${it.loadLabel(activity.packageManager)} (${it.activityInfo.packageName})" }.toTypedArray()) { _, selected ->
                    launch(activity, matches[selected])
                }.show()
        }
    }

    /** Opens only the user-provided webpage, after an explicit confirmation. No APK is downloaded. */
    fun openSite(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("SportzX internet sitesi")
            .setMessage("sportzxlive.com adresini haricî tarayıcıda açacaksınız. Sitenin resmî geliştiriciye ait olduğu, APK güvenliği ve yayın lisansları doğrulanmadı. APK indirme veya kurma işlemi yapılmaz.")
            .setNegativeButton("İptal", null)
            .setPositiveButton("Tarayıcıda aç") { _, _ ->
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SITE)))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(activity, "Tarayıcı bulunamadı", Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun launch(activity: Activity, resolved: ResolveInfo) {
        val intent = activity.packageManager.getLaunchIntentForPackage(resolved.activityInfo.packageName)
            ?: Intent.makeMainActivity(android.content.ComponentName(
                resolved.activityInfo.packageName, resolved.activityInfo.name))
        try {
            activity.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(activity, "SportzX açılamadı. Uygulamayı telefonda kontrol edin.", Toast.LENGTH_LONG).show()
        }
    }
}

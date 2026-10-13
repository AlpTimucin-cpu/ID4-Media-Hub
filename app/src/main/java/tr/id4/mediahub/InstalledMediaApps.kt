package tr.id4.mediahub

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager

/** Launch supported media apps installed on the same Android phone. No web redirects. */
object InstalledMediaApps {
    const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    const val NETFLIX_PACKAGE = "com.netflix.mediaclient"
    const val PRIME_VIDEO_PACKAGE = "com.amazon.avod.thirdpartyclient"
    const val REC_TV_PACKAGE = "com.rectv.shot"

    fun openYouTube(activity: Activity) = open(activity, "YouTube", YOUTUBE_PACKAGE)
    fun openNetflix(activity: Activity) = open(activity, "Netflix", NETFLIX_PACKAGE)
    fun openPrimeVideo(activity: Activity) = open(activity, "Prime Video", PRIME_VIDEO_PACKAGE)
    fun openRecTv(activity: Activity) = open(activity, "REC TV", REC_TV_PACKAGE)

    fun open(activity: Activity, title: String, packageName: String) {
        val pm = activity.packageManager
        val exists = try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        if (!exists) {
            AlertDialog.Builder(activity)
                .setTitle("$title yüklü değil")
                .setMessage("Bu telefonda $title uygulaması yüklü görünmüyor. " +
                    "ID.4 Yol Arkadaşım tarayıcıya yönlendirme yapmayacak. " +
                    "Lütfen kurulum ve hesap durumunu cihaz üzerinden kontrol et.")
                .setPositiveButton("Tamam", null)
                .show()
            return
        }
        val intent = pm.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            AlertDialog.Builder(activity)
                .setTitle("$title başlatılamadı")
                .setMessage("$title kurulu ancak başlatılabilir ekran bulunamadı. " +
                    "Uygulamanın etkin olduğundan emin ol.")
                .setPositiveButton("Tamam", null).show()
            return
        }
        try {
            activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            showError(activity, title)
        } catch (_: SecurityException) {
            showError(activity, title)
        } catch (_: IllegalStateException) {
            showError(activity, title)
        }
    }

    private fun showError(activity: Activity, title: String) {
        AlertDialog.Builder(activity)
            .setTitle("$title açılamadı")
            .setMessage("Android, kurulu $title uygulamasının başlatılmasını reddetti. " +
                "Telefonun uygulama çekmecesinden doğrudan çalışıp çalışmadığını kontrol et.")
            .setPositiveButton("Tamam", null)
            .show()
    }
}

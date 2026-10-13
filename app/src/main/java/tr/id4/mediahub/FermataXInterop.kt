package tr.id4.mediahub

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri

/**
 * Optional, user-approved PHONE-SIDE interop with the exported VIEW activity
 * of the supplied FermataX 2.2.6 debug manifest.
 *
 * Never invokes a car projection service, parked launcher, privileged binder,
 * speed signal, DRM hook, or private content provider. No APK bytes are copied.
 */
object FermataXInterop {
    const val DEBUG_PACKAGE = "me.app.fermataX.auto.debug"
    const val EXPORTED_ACTIVITY = "me.aap.fermata.ui.activity.MainActivity"

    fun canShare(url: String): Boolean = M3uParser.isHttpsUrl(url)

    fun offerVideo(activity: Activity, channel: VideoChannel?) {
        if (channel == null) {
            AlertDialog.Builder(activity)
                .setTitle("Önce bir kanal seç")
                .setMessage("FermataX'e göndermek istediğin kanalı önce Media Hub'da seç.")
                .setPositiveButton("Tamam", null)
                .show()
            return
        }
        if (!canShare(channel.url)) {
            AlertDialog.Builder(activity)
                .setTitle("Bağlantı gönderilemedi")
                .setMessage("Yalnızca geçerli HTTPS yayın adresleri FermataX'e aktarılabilir.")
                .setPositiveButton("Tamam", null)
                .show()
            return
        }
        val installed = CarCompanionApps.installedVersion(activity, DEBUG_PACKAGE)
        if (installed == null) {
            AlertDialog.Builder(activity)
                .setTitle("FermataX kurulu değil")
                .setMessage("Yüklediğin manifestte doğrulanan FermataX Debug paketi " +
                    "telefonda bulunamadı. Başka bir uygulama otomatik kurulmayacak.")
                .setPositiveButton("Tamam", null)
                .show()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("FermataX ile aç")
            .setMessage(
                "“" + channel.name.take(80) + "” yayınının HTTPS bağlantısı " +
                    "telefonda yüklü FermataX ($installed) uygulamasına gönderilecek. " +
                    "Abonelik bağlantısında erişim anahtarı bulunabilir. " +
                    "Gönderimi onaylıyor musun? Video araç ekranına gönderilmez."
            )
            .setNegativeButton("İptal", null)
            .setPositiveButton("Bağlantıyı gönder") { _, _ ->
                // The exported MainActivity is confirmed in the user-supplied
                // 2.2.6 manifest. ACTION_VIEW allows FermataX to choose its
                // own decoding; playback itself needs a real-device test.
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setClassName(DEBUG_PACKAGE, EXPORTED_ACTIVITY)
                    setDataAndType(Uri.parse(channel.url), "video/*")
                }
                try {
                    activity.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    unavailable(activity)
                } catch (_: SecurityException) {
                    unavailable(activity)
                } catch (_: IllegalArgumentException) {
                    unavailable(activity)
                }
            }
            .show()
    }

    private fun unavailable(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("FermataX açılamadı")
            .setMessage("FermataX bağlantı isteğini kabul etmedi veya Android engelledi. " +
                "Medya Hub kitaplığın değişmedi. Bu bağlantının gerçek cihazda testi gerekir.")
            .setPositiveButton("Tamam", null)
            .show()
    }
}

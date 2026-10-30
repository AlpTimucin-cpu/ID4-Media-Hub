package tr.id4.mediahub.nativecockpit.presentation.media

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Official DRM apps own their playback and PiP.
 * PictureInPictureParams on our Activity cannot force an external Activity into PiP.
 */
object MediaLauncher {
    fun openOfficialApp(context: Context, site: MediaSite): Result<Unit> =
        runCatching {
            val packageName = when (site) {
                MediaSite.NETFLIX -> "com.netflix.mediaclient"
                MediaSite.DISNEY -> "com.disney.disneyplus"
                MediaSite.PRIME -> "com.amazon.avod.thirdpartyclient"
                MediaSite.YOUTUBE -> error("YouTube uses internal WebView")
            }
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: error("Resmi uygulama bulunamadı: " + site.title)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.i("EYA-MEDIA-UAT", "External app launched: " + site.name +
                ", PiP controlled by external app")
            Unit
        }.onFailure {
            Log.w("EYA-MEDIA-UAT", "Launch failed: " + site.name +
                ", reason=" + it.javaClass.simpleName)
        }
}

package com.evcockpit.presentation.media

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Official DRM apps own their playback and PiP.
 * PictureInPictureParams on our Activity cannot force an external Activity into PiP.
 */
sealed interface MediaLaunchState {
    data class Launched(val packageName: String) : MediaLaunchState
    data object NotInstalled : MediaLaunchState
    data object Blocked : MediaLaunchState
}

object MediaLauncher {
    // Exact IDs produced by the pinned media-engine build, not a display name.
    val mediaEnginePackages = listOf(
        "me.app.fermataX.auto.evcockpitpreview.debug",
        "me.app.fermataX.auto.evcockpitpreview"
    )

    fun launchMediaEngine(context: Context): MediaLaunchState {
        for (packageName in mediaEnginePackages) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                    ?: continue
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return MediaLaunchState.Launched(packageName)
            } catch (_: ActivityNotFoundException) {
                // The app may have been uninstalled after package resolution.
                continue
            } catch (_: SecurityException) {
                return MediaLaunchState.Blocked
            }
        }
        return MediaLaunchState.NotInstalled
    }

    fun openOfficialApp(context: Context, site: MediaSite): Result<Unit> =
        runCatching {
            val packageName = when (site) {
                MediaSite.NETFLIX -> "com.netflix.mediaclient"
                MediaSite.DISNEY -> "com.disney.disneyplus"
                MediaSite.PRIME -> "com.amazon.avod.thirdpartyclient"
                MediaSite.YOUTUBE -> error("YouTube uses internal WebView")
            }
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: error("Official application not found: " + site.title)
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

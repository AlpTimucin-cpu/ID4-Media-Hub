package tr.id4.mediahub.nativecockpit.presentation.media

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import tr.id4.mediahub.nativecockpit.presentation.cockpit.EyaTheme

/**
 * Dedicated, non-exported, app-owned browser. The original MainActivity,
 * Android Auto navigation surface and Mola playback service remain untouched.
 */
class MediaWebViewActivity : ComponentActivity() {
    private val browser: MediaBrowserViewModel by viewModels()
    private var verifiedParked = false
    private var videoFullScreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        verifiedParked = intent.getBooleanExtra(EXTRA_PARK, false)
        // Fail closed: this must come from an AUTHORITATIVE Park gear source,
        // never an inferred 0 km/h GPS reading.
        if (!verifiedParked) {
            finish()
            return
        }
        val site = MediaWebsite.entries.firstOrNull {
            it.name == intent.getStringExtra(EXTRA_SITE)
        } ?: run { finish(); return }

        updatePipPermission()
        setContent {
            EyaTheme {
                MaterialTheme {
                    MediaWebViewScreen(
                        site = site,
                        viewModel = browser,
                        verifiedParked = verifiedParked,
                        onFullScreenVideo = { active ->
                            videoFullScreen = active
                            updatePipPermission()
                        },
                        onExternalFallback = { fallback ->
                            // Explicit user choice, not a silent package redirect.
                            val pkg = when (fallback) {
                                MediaWebsite.NETFLIX -> "com.netflix.mediaclient"
                                MediaWebsite.DISNEY -> "com.disney.disneyplus"
                                MediaWebsite.PRIME -> "com.amazon.avod.thirdpartyclient"
                                MediaWebsite.YOUTUBE -> "com.google.android.youtube"
                            }
                            packageManager.getLaunchIntentForPackage(pkg)?.let {
                                startActivity(it)
                            }
                        },
                        onClose = ::finish
                    )
                }
            }
        }
    }

    private fun updatePipPermission() {
        if (Build.VERSION.SDK_INT >= 26) {
            val p = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
            if (Build.VERSION.SDK_INT >= 31) {
                p.setAutoEnterEnabled(verifiedParked && videoFullScreen)
            }
            setPictureInPictureParams(p.build())
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // No PiP without a trusted Park state and a real full-screen video surface.
        if (Build.VERSION.SDK_INT in 26..30 && verifiedParked && videoFullScreen) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9)).build()
            )
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean, newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!verifiedParked && isInPictureInPictureMode) finish()
    }

    companion object {
        private const val EXTRA_SITE = "eya.media.site"
        private const val EXTRA_PARK = "eya.media.verified_park"

        fun newIntent(context: Context, site: MediaWebsite, verifiedParked: Boolean): Intent =
            Intent(context, MediaWebViewActivity::class.java).apply {
                putExtra(EXTRA_SITE, site.name)
                putExtra(EXTRA_PARK, verifiedParked)
            }
    }
}

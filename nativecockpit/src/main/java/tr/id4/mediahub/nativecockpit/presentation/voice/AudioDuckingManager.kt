package tr.id4.mediahub.nativecockpit.presentation.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * One audio focus owner for short navigation speech.
 * Android ducks cooperative music players; this does NOT alter system stream volume.
 */
class AudioDuckingManager(
    context: Context,
    private val onFocusLost: () -> Unit
) : AutoCloseable {
    val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            main.post { onFocusLost() }
        }
    }
    private val focusRequest = AudioFocusRequest.Builder(
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
    )
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener(listener, main)
        .build()

    private var held = false
    private var closed = false

    @Synchronized
    fun acquire(): Boolean {
        if (closed) return false
        if (held) return true
        held = manager.requestAudioFocus(focusRequest) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return held
    }

    @Synchronized
    fun release() {
        if (held) manager.abandonAudioFocusRequest(focusRequest)
        held = false
    }

    @Synchronized
    override fun close() {
        release()
        closed = true
    }
}

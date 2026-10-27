package tr.id4.mediahub.nativecockpit.presentation.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Activity-scoped TTS: foregound-only, focus-safe, no stream-volume changes.
 * Media ducking is requested from Android, not implemented by changing
 * another app's volume. An unavailable tr-TR voice fails closed.
 */
class NativeVoiceGuide(context: Context) {
    enum class Status { LOADING, READY, TURKISH_VOICE_MISSING, INIT_FAILED, AUDIO_FOCUS_DENIED }

    var status: Status by mutableStateOf(Status.LOADING)
        private set

    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var activeUtterance: String? = null
    private var sequence = 0L
    private var closed = false
    private var holdingFocus = false
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            main.post { stop() }
        }
    }
    private val request = AudioFocusRequest.Builder(
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
    ).setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener(focusListener,main)
        .build()

    private var tts: TextToSpeech? = null
    init {
        tts = TextToSpeech(context.applicationContext) { result ->
        main.post {
                if (closed) return@post
                val engine = tts
                if (result != TextToSpeech.SUCCESS || engine == null) {
                    status = Status.INIT_FAILED
                    return@post
                }
                val locale = Locale.forLanguageTag("tr-TR")
                val supported = engine.isLanguageAvailable(locale)
                val selected = if (supported >= TextToSpeech.LANG_AVAILABLE)
                    engine.setLanguage(locale) else TextToSpeech.LANG_NOT_SUPPORTED
                if (selected < TextToSpeech.LANG_AVAILABLE) {
                    status = Status.TURKISH_VOICE_MISSING
                } else {
                    engine.setAudioAttributes(attrs)
                    engine.setSpeechRate(1.0f)
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String) = Unit
                        override fun onDone(utteranceId: String) { finish(utteranceId) }
                        override fun onError(utteranceId: String) { finish(utteranceId) }
                        override fun onStop(utteranceId: String, interrupted: Boolean) {
                            finish(utteranceId)
                        }
                    })
                    status = Status.READY
                }
            }
        }
    }

    /** Returns false rather than speaking without a valid audio-focus grant. */
    fun speak(text: String, volume: Float): Boolean {
        if (closed || (status != Status.READY && status != Status.AUDIO_FOCUS_DENIED) ||
            text.isBlank() || text.length > 190)
            return false
        val engine = tts ?: return false
        stop() // Flush any previous announcement and release its audio focus.
        if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            status = Status.AUDIO_FOCUS_DENIED
            return false
        }
        holdingFocus = true
        status = Status.READY
        val id = "eya-voice-${++sequence}"
        activeUtterance = id
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,volume.coerceIn(.15f,1f))
        }
        if (engine.speak(text,TextToSpeech.QUEUE_FLUSH,params,id) != TextToSpeech.SUCCESS) {
            stop()
            return false
        }
        return true
    }

    fun stop() {
        tts?.stop()
        activeUtterance = null
        releaseFocus()
    }

    private fun finish(id: String) {
        main.post {
            if (id == activeUtterance) {
                activeUtterance = null
                releaseFocus()
            }
        }
    }

    private fun releaseFocus() {
        if (holdingFocus) {
            audio.abandonAudioFocusRequest(request)
            holdingFocus = false
        }
    }

    fun close() {
        if (closed) return
        closed = true
        stop()
        tts?.shutdown()
        tts = null
    }
}

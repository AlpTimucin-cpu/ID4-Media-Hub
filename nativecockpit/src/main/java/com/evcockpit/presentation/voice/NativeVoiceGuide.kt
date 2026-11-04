package com.evcockpit.presentation.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Activity-scoped TTS: foregound-only, focus-safe, no stream-volume changes.
 * Media ducking is requested from Android, not implemented by changing
 * another app's volume. An unavailable tr-TR voice fails closed.
 */
class NativeVoiceGuide(context: Context, private val audioSignal: AppAudioDucking) {
    private val logTag = "EYA-Voice"

    companion object {
        private val GREETING_LOCALE = Locale.forLanguageTag("tr-TR")

        /** Voice has no guaranteed gender property: prefer explicit engine labels only. */
        fun availableTurkishVoices(engine: TextToSpeech): List<Voice> =
            runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
                .filter { v ->
                    v.locale.language.equals("tr", true) &&
                        v.locale.country.equals("TR", true) &&
                        !v.isNetworkConnectionRequired
                }
                .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.name })

        fun selectTurkishGreetingVoice(
            engine: TextToSpeech,
            preferredName: String? = null
        ): Boolean {
            if (engine.setLanguage(GREETING_LOCALE) < TextToSpeech.LANG_AVAILABLE) return false
            val installed = availableTurkishVoices(engine)
            val preferred = installed.firstOrNull { it.name == preferredName }
            val labelledFemale = installed.firstOrNull { v ->
                val n = v.name.lowercase(Locale.ROOT)
                listOf("female", "woman", "kadın", "kadin").any(n::contains)
            }
            // When no labelled female is installed use the system Turkish default.
            val selection = preferred ?: labelledFemale
            if (selection != null &&
                runCatching { engine.setVoice(selection) == TextToSpeech.SUCCESS }
                    .getOrDefault(false)
            ) return true
            return engine.setLanguage(GREETING_LOCALE) >= TextToSpeech.LANG_AVAILABLE
        }

    }

    enum class Status { LOADING, READY, TURKISH_VOICE_MISSING, INIT_FAILED, AUDIO_FOCUS_DENIED }

    var status: Status by mutableStateOf(Status.LOADING)
        private set
    var lastError: String? by mutableStateOf(null)
        private set

    private val main = Handler(Looper.getMainLooper())
    private val ducking = AudioDuckingManager(context) { stop() }
    private val attrs: AudioAttributes = ducking.attributes
    private var activeUtterance: String? = null
    private var startedDuckOwner: String? = null
    private var onStarted: (() -> Unit)? = null
    private var onFailed: (() -> Unit)? = null
    private var sequence = 0L
    private var closed = false

    private var tts: TextToSpeech? = null
    init {
        tts = try {
            TextToSpeech(context.applicationContext) { result ->
        main.post {
                if (closed) return@post
                val engine = tts
                if (result != TextToSpeech.SUCCESS || engine == null) {
                    status = Status.INIT_FAILED
                    lastError = "Could not initialize the TTS engine"
                    Log.e(logTag, "TextToSpeech initialization failed: result=$result")
                    return@post
                }
                val locale = GREETING_LOCALE
                val supported = TtsDebugLogger.languageAvailability(engine)
                val selected = if (supported >= TextToSpeech.LANG_AVAILABLE)
                    engine.setLanguage(locale) else TextToSpeech.LANG_NOT_SUPPORTED
                TtsDebugLogger.selectedLanguage(selected)
                if (selected < TextToSpeech.LANG_AVAILABLE) {
                    status = Status.TURKISH_VOICE_MISSING
                    lastError = "Türkçe ses verisi bulunamadı"
                    Log.e(logTag, "tr-TR voice unavailable: supported=$supported selected=$selected")
                } else {
                    engine.setAudioAttributes(attrs)
                    engine.setSpeechRate(1.0f)
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String) {
                            main.post {
                                if (!closed && activeUtterance == utteranceId) {
                                    startedDuckOwner?.let(audioSignal::end)
                                    startedDuckOwner = "navigation:" + utteranceId
                                    startedDuckOwner?.let(audioSignal::begin)
                                    Log.d(logTag, "Utterance started")
                                    TtsDebugLogger.event("utterance onStart")
                                    onStarted?.invoke()
                                    onStarted = null
                                    // After onStart, a future onError is observable
                                    // but must not mark this already-spoken stage retryable.
                                    onFailed = null
                                }
                            }
                        }
                        override fun onDone(utteranceId: String) { finish(utteranceId) }
                        override fun onError(utteranceId: String) {
                            synthesisError(utteranceId, "TTS speech failed")
                        }
                        override fun onError(utteranceId: String, errorCode: Int) {
                            TtsDebugLogger.utteranceError(errorCode)
                            synthesisError(utteranceId, "TTS speech error: $errorCode")
                        }
                        override fun onStop(utteranceId: String, interrupted: Boolean) {
                            finish(utteranceId)
                        }
                    })
                    status = Status.READY
                }
            }
            }
        } catch (error: RuntimeException) {
            status = Status.INIT_FAILED
            lastError = "Text-to-speech engine unavailable"
            Log.w(logTag, "TextToSpeech constructor failed; speech disabled", error)
            null
        }
    }

    /** Returns false rather than speaking without a valid audio-focus grant. */
    fun speak(
        text: String,
        volume: Float,
        onUtteranceStarted: (() -> Unit)? = null,
        onUtteranceFailed: (() -> Unit)? = null
    ): Boolean {
        if (closed || (status != Status.READY && status != Status.AUDIO_FOCUS_DENIED) ||
            text.isBlank() || text.length > 190)
            return false
        val engine = tts ?: return false
        stopCurrent(notifyFailure = false) // Replace old utterance without canceling the NEW cue.
        if (!ducking.acquire()) {
            status = Status.AUDIO_FOCUS_DENIED
            lastError = "Could not obtain audio focus"
            Log.w(logTag, "Navigation audio focus was not granted")
            return false
        }
        status = Status.READY
        lastError = null
        val id = "eya-voice-${++sequence}"
        activeUtterance = id
        onStarted = onUtteranceStarted
        onFailed = onUtteranceFailed
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,volume.coerceIn(.15f,1f))
        }
        val accepted = try {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, id) == TextToSpeech.SUCCESS
        } catch (_: RuntimeException) {
            false
        }
        if (!accepted) {
            lastError = "Could not queue the TTS announcement"
            Log.e(logTag, "TextToSpeech.speak() rejected the utterance")
            stop()
            return false
        }
        return true
    }

    fun stop() = stopCurrent(notifyFailure = true)

    private fun stopCurrent(notifyFailure: Boolean) {
        runCatching { tts?.stop() }
        activeUtterance = null
        onStarted = null
        val fail = onFailed
        onFailed = null
        // A replacement maneuver may already be pending: its policy state
        // must not be cleared by the PREVIOUS utterance's callback.
        if (notifyFailure) fail?.invoke()
        releaseFocus()
    }

    // The engine can accept speak() and fail later. Surface that failure.
    private fun synthesisError(id: String, reason: String) {
        main.post {
            if (!closed && activeUtterance == id) {
                lastError = reason
                Log.e(logTag, "Utterance synthesis failed id=$id: $reason")
                activeUtterance = null
                onStarted = null
                val fail = onFailed
                onFailed = null
                fail?.invoke()
                releaseFocus()
            }
        }
    }

    private fun finish(id: String) {
        main.post {
            if (id == activeUtterance) {
                Log.d(logTag, "Utterance finished")
                TtsDebugLogger.event("utterance finished, audio focus released")
                activeUtterance = null
                onStarted = null
                val fail = onFailed
                onFailed = null
                fail?.invoke()
                releaseFocus()
            }
        }
    }

    private fun releaseFocus() {
        startedDuckOwner?.let(audioSignal::end)
        startedDuckOwner = null
        ducking.release()
    }

    fun close() {
        if (closed) return
        closed = true
        stop()
        runCatching { tts?.shutdown() }
        tts = null
        ducking.close()
    }
}

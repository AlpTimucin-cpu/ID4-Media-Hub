package com.evcockpit.presentation.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.evcockpit.data.settings.UserSettings
import com.evcockpit.data.settings.UserSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One brief Turkish greeting after a real user-initiated OBD connection or
 * Android Auto Session creation. Android TTS / AudioFocus only: no sound assets.
 *
 * The singleton is shared across Activity and CarAppService within one process.
 * It is NOT a vehicle ignition signal; no greeting on ordinary Activity launch.
 */
@Singleton
class WelcomeGreetingManager @Inject constructor(
    @ApplicationContext context: Context,
    private val settings: UserSettingsRepository,
    private val audioDucking: AppAudioDucking
) {
    private val appContext = context.applicationContext
    private val logTag = "EYA-Welcome"
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var welcomeSettings = UserSettings()
    private val sessionGate = WelcomeSessionGate()
    private val preferences = VoicePreference(appContext)
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
        ) main.post { closeSession() }
    }
    private val focusRequest = AudioFocusRequest.Builder(
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
    )
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener(focusListener, main)
        .build()

    private var engine: TextToSpeech? = null
    private var initializationPending = false
    private var focusGranted = false
    private var active = false
    private val initTimeout = Runnable {
        if (initializationPending) {
            Log.w(logTag, "Turkish TTS initialization timed out")
            closeSession()
        }
    }
    private val speechTimeout = Runnable {
        Log.w(logTag, "Welcome synthesis timed out")
        closeSession()
    }

    /**
     * Thread-safe and idempotent. Call only after the OBD phase is CONNECTED
     * or from a real CarAppService.onCreateSession callback.
     */
    fun launchWelcomeGreeting() {
        if (!sessionGate.claim()) return
        scope.launch {
            // DataStore is asynchronous: read the actual persisted user setting.
            val current = runCatching { settings.settings.first() }.getOrNull() ?: return@launch
            if (!current.isWelcomeGreetingEnabled) return@launch
            welcomeSettings = current
            startSession()
        }
    }

    private fun startSession() {
        if (active) return
        active = true
        initializationPending = true
        main.postDelayed(initTimeout, 8_000L)

        // This engine is separate from en-US navigation TTS and is short-lived.
        try {
            engine = TextToSpeech(appContext) { result ->
                main.post {
                    if (!active || !initializationPending) return@post
                    initializationPending = false
                    main.removeCallbacks(initTimeout)
                    val localEngine = engine
                    if (result != TextToSpeech.SUCCESS || localEngine == null ||
                        !NativeVoiceGuide.selectTurkishGreetingVoice(localEngine, welcomeSettings.ttsVoiceName)
                    ) {
                        Log.w(logTag, "Turkish text-to-speech voice unavailable")
                        closeSession()
                        return@post
                    }
                    localEngine.setAudioAttributes(audioAttributes)
                    localEngine.setSpeechRate(welcomeSettings.ttsSpeechRate)
                    localEngine.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String) {
                                if (utteranceId == UTTERANCE_ID) main.post {
                                    if (active) {
                                        audioDucking.begin("welcome")
                                        Log.d(logTag, "Welcome utterance started")
                                    }
                                }
                            }

                            override fun onDone(utteranceId: String) =
                                finishIfWelcome(utteranceId)

                            override fun onError(utteranceId: String) =
                                finishIfWelcome(utteranceId)

                            override fun onError(utteranceId: String, errorCode: Int) =
                                finishIfWelcome(utteranceId)

                            override fun onStop(
                                utteranceId: String, interrupted: Boolean
                            ) = finishIfWelcome(utteranceId)
                        }
                    )
                    // Let the voice service finish preparation / audio routing.
                    main.postDelayed({
                        if (active) speakGreeting()
                    }, 1_000L)
                }
            }
        } catch (error: RuntimeException) {
            Log.w(logTag, "Could not create TTS engine", error)
            closeSession()
        }
    }

    private fun speakGreeting() {
        val tts = engine ?: return closeSession()
        val granted = runCatching {
            audio.requestAudioFocus(focusRequest) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }.getOrDefault(false)
        if (!granted) {
            Log.w(logTag, "Welcome audio focus denied")
            closeSession()
            return
        }
        focusGranted = true

        val params = Bundle().apply {
            putFloat(
                TextToSpeech.Engine.KEY_PARAM_VOLUME,
                preferences.volume.coerceIn(0.15f, 1.0f)
            )
        }
        // Exactly one TTS request, with its required stable utterance ID.
        val queued = runCatching {
            tts.speak(
                "Merhaba yol arkadaşım. İyi yolculuklar.",
                TextToSpeech.QUEUE_FLUSH,
                params,
                UTTERANCE_ID
            ) == TextToSpeech.SUCCESS
        }.getOrDefault(false)
        if (queued) {
            main.postDelayed(speechTimeout, 15_000L)
        } else {
            Log.w(logTag, "TTS did not accept the greeting")
            closeSession()
        }
    }

    private fun finishIfWelcome(id: String) {
        if (id == UTTERANCE_ID) main.post { closeSession() }
    }

    private fun closeSession() {
        if (!active) return
        active = false
        audioDucking.end("welcome")
        initializationPending = false
        main.removeCallbacks(initTimeout)
        main.removeCallbacks(speechTimeout)

        val old = engine
        engine = null
        runCatching { old?.stop() }
        runCatching { old?.shutdown() }

        if (focusGranted) {
            focusGranted = false
            runCatching { audio.abandonAudioFocusRequest(focusRequest) }
        }
    }

    companion object {
        private const val UTTERANCE_ID = "welcome_greeting"
    }
}

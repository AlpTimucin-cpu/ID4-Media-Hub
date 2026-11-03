package com.evcockpit.presentation.settings

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evcockpit.data.settings.UserSettings
import com.evcockpit.data.settings.UserSettingsRepository
import com.evcockpit.presentation.voice.NativeVoiceGuide
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class VoiceOption(val name: String, val label: String)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: UserSettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val settings = repository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings()
    )
    private val voiceOptionsState = MutableStateFlow<List<VoiceOption>>(emptyList())
    val voiceOptions = voiceOptionsState.asStateFlow()
    private val main = Handler(Looper.getMainLooper())
    private var catalogEngine: TextToSpeech? = null
    private var voiceRequested = false
    private var disposed = false

    /** Android TTS Voice does not contain a reliable biological gender field. */
    fun loadInstalledTurkishVoices() {
        if (voiceRequested) return
        voiceRequested = true
        catalogEngine = TextToSpeech(context.applicationContext) { status ->
            main.postDelayed({
                val tts = catalogEngine ?: return@postDelayed
                if (!disposed && status == TextToSpeech.SUCCESS) {
                    voiceOptionsState.value = NativeVoiceGuide
                        .availableTurkishVoices(tts).map { voice ->
                            val n = voice.name.lowercase(Locale.ROOT)
                            val label = when {
                                listOf("female", "woman", "kadin", "kadın").any(n::contains) ->
                                    "Kadın etiketi • " + voice.name
                                n.contains("male") || n.contains("man") ->
                                    "Erkek etiketi • " + voice.name
                                else -> "Türkçe ses • " + voice.name
                            }
                            VoiceOption(voice.name, label)
                        }
                }
                tts.shutdown()
                catalogEngine = null
            }, 180L)
        }
    }

    fun setWelcome(value: Boolean) = viewModelScope.launch {
        repository.setWelcomeGreetingEnabled(value)
    }
    fun setVoice(value: String?) = viewModelScope.launch {
        repository.setTtsVoiceName(value)
    }
    fun setRate(value: Float) = viewModelScope.launch {
        repository.setTtsSpeechRate(value)
    }
    fun setNight(value: Boolean) = viewModelScope.launch {
        repository.setNightModeEnabled(value)
    }
    fun setEngineEnabled(value: Boolean) = viewModelScope.launch {
        repository.setEngineSoundEnabled(value)
    }
    fun setEngineProfile(value: String) = viewModelScope.launch {
        repository.setEngineSoundProfile(value)
    }
    fun setEngineVolume(value: Float) = viewModelScope.launch {
        repository.setEngineVolume(value)
    }

    override fun onCleared() {
        disposed = true
        catalogEngine?.shutdown()
        catalogEngine = null
        super.onCleared()
    }
}

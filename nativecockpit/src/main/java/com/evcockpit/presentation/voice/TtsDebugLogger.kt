package com.evcockpit.presentation.voice

import androidx.compose.ui.res.stringResource
import com.evcockpit.R

import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.util.Locale

/** Avoid logging text, location, or personal identifiers. */
object TtsDebugLogger {
    private const val TAG = "EYA-TTS-UAT"

    fun languageAvailability(tts: TextToSpeech): Int {
        val result = tts.isLanguageAvailable(Locale("en", "US"))
        val label = when (result) {
            TextToSpeech.LANG_MISSING_DATA -> "LANG_MISSING_DATA"
            TextToSpeech.LANG_NOT_SUPPORTED -> "LANG_NOT_SUPPORTED"
            TextToSpeech.LANG_AVAILABLE -> "LANG_AVAILABLE"
            TextToSpeech.LANG_COUNTRY_AVAILABLE -> "LANG_COUNTRY_AVAILABLE"
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "LANG_COUNTRY_VAR_AVAILABLE"
            else -> "UNKNOWN(" + result + ")"
        }
        if (result < TextToSpeech.LANG_AVAILABLE)
            Log.e(TAG, "en-US availability: " + label)
        else Log.i(TAG, "en-US availability: " + label)
        return result
    }

    fun selectedLanguage(code: Int) {
        if (code < TextToSpeech.LANG_AVAILABLE)
            Log.e(TAG, "setLanguage(en-US) failed: " + code)
        else Log.i(TAG, "setLanguage(en-US) ready: " + code)
    }

    fun utteranceError(code: Int) {
        Log.e(TAG, "UtteranceProgressListener.onError: " + code)
    }

    fun event(event: String) {
        Log.d(TAG, event)
    }
}

@Composable
fun TtsPackageWarningDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0A0F1A),
            contentColor = Color.White,
            border = BorderStroke(1.dp, Color(0xFF46687E))
        ) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(stringResource(R.string.native_english_tts_data_missing), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.native_download_english_voice_data_from_settings_language_and_input_text_to_s) +
                    "Menu names may vary by device.")
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.native_ok), color = Color(0xFF00E676))
                }
            }
        }
    }
}

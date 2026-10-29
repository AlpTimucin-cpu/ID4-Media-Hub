package tr.id4.mediahub.nativecockpit.presentation.voice

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
        val result = tts.isLanguageAvailable(Locale("tr", "TR"))
        val label = when (result) {
            TextToSpeech.LANG_MISSING_DATA -> "LANG_MISSING_DATA"
            TextToSpeech.LANG_NOT_SUPPORTED -> "LANG_NOT_SUPPORTED"
            TextToSpeech.LANG_AVAILABLE -> "LANG_AVAILABLE"
            TextToSpeech.LANG_COUNTRY_AVAILABLE -> "LANG_COUNTRY_AVAILABLE"
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "LANG_COUNTRY_VAR_AVAILABLE"
            else -> "UNKNOWN(" + result + ")"
        }
        if (result < TextToSpeech.LANG_AVAILABLE)
            Log.e(TAG, "tr-TR availability: " + label)
        else Log.i(TAG, "tr-TR availability: " + label)
        return result
    }

    fun selectedLanguage(code: Int) {
        if (code < TextToSpeech.LANG_AVAILABLE)
            Log.e(TAG, "setLanguage(tr-TR) failed: " + code)
        else Log.i(TAG, "setLanguage(tr-TR) ready: " + code)
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
                Text("Türkçe TTS Paketi Eksik", style = MaterialTheme.typography.titleLarge)
                Text("Lütfen Ayarlar > Dil ve Giriş > Metin Okuma kısmından Türkçe " +
                    "paketini indirin. Menü adı cihazınıza göre değişebilir.")
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Tamam", color = Color(0xFF00E676))
                }
            }
        }
    }
}

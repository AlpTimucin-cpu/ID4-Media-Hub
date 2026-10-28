package tr.id4.mediahub.nativecockpit.presentation.media

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** Phone-side external app handoff only. Never bypass car UX/driving restrictions. */
enum class StreamingApp(val title: String, val androidPackage: String) {
    NETFLIX("Netflix", "com.netflix.mediaclient"),
    DISNEY("Disney+", "com.disney.disneyplus"),
    PRIME("Prime Video", "com.amazon.avod.thirdpartyclient")
}

/** True only from an authoritative Park gear / vehicle UX-restrictions source. */
@Composable
fun ParkedStreamingDialog(
    verifiedParked: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0A1728),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Medya uygulamaları", style = MaterialTheme.typography.titleLarge,
                    color = Color.White)
                Text(
                    if (verifiedParked) "Araç park halinde doğrulandı."
                    else "Video yalnızca araç Park konumundayken açılır. " +
                        "Park bilgisi doğrulanamadığı için başlatma kapalı.",
                    color = Color(0xFFABC5D5),
                    style = MaterialTheme.typography.bodySmall
                )
                StreamingApp.entries.forEach { app ->
                    Button(
                        enabled = verifiedParked,
                        onClick = {
                            if (!verifiedParked) return@Button
                            error = tryLaunch(context, app)
                            if (error == null) onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text(app.title) }
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFB17C))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Kapat")
                }
            }
        }
    }
}

/** No embedding, screen-capture, DRM changes or third-party PiP coercion. */
private fun tryLaunch(context: Context, service: StreamingApp): String? {
    val intent = context.packageManager
        .getLaunchIntentForPackage(service.androidPackage)
        ?: return service.title + " cihazda kurulu değil veya kullanılamıyor."
    return try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    } catch (_: ActivityNotFoundException) {
        service.title + " başlatılamadı."
    } catch (_: SecurityException) {
        service.title + " başlatma izni bulunamadı."
    }
}

package tr.id4.mediahub.nativecockpit.presentation.media

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Internal phone media selector. The caller supplies host eligibility;
 * a connected car never inherits disconnected-phone video permission.
 */
@Composable
fun ParkedStreamingDialog(
    playbackAllowed: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember { mutableStateOf<MediaSite?>(null) }
    LaunchedEffect(playbackAllowed) {
        if (!playbackAllowed) { selected = null }
    }

    // Every site uses the internal browser; no automatic external app launch.
    val service = selected
    if (service != null && playbackAllowed) {
        Dialog(
            onDismissRequest = { selected = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0A0F1A)
            ) {
                MediaWebViewScreen(
                    service = service,
                    playbackAllowed = playbackAllowed,
                    onClose = { selected = null }
                )
            }
        }
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0A1728),
            shadowElevation = 12.dp
        ) {
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Dahili medya tarayıcısı",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    if (playbackAllowed) "Telefonda uygulama içi medya"
                    else "Araç bağlantısı etkin veya bağlantı durumu bekleniyor. " +
                         "Video bu ekranda kullanılamıyor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFABC5D5)
                )
                MediaSite.entries.forEach { item ->
                    Button(
                        enabled = playbackAllowed,
                        onClick = {
                            if (playbackAllowed) {
                                selected = item
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text(item.title) }
                }
                Text(
                    "Hesabınızla site içinde giriş yapabilirsiniz. Oynatma desteği servise ve cihazınıza bağlıdır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB7CEDF)
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Kapat") }
            }
        }
    }
}

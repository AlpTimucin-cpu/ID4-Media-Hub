package tr.id4.mediahub.nativecockpit.presentation.media

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Only the media entry is changed. Existing cockpit, Mola services and
 * their layouts are untouched. A confirmed PARK signal is mandatory.
 */
@Composable
fun ParkedStreamingDialog(
    verifiedParked: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<MediaSite?>(null) }
    var external by remember { mutableStateOf<MediaSite?>(null) }
    var launchError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(verifiedParked) {
        if (!verifiedParked) { selected = null; external = null }
    }

    // YouTube is embedded; official subscription apps own their DRM and PiP.
    val service = selected
    if (service != null && verifiedParked) {
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
                    verifiedParked = verifiedParked,
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
                    if (verifiedParked) "Park durumu doğrulandı."
                    else "Araç Park konumu doğrulanmadı. " +
                         "Video oynatma güvenlik nedeniyle kapalı.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFABC5D5)
                )
                MediaSite.entries.forEach { item ->
                    Button(
                        enabled = verifiedParked,
                        onClick = {
                            if (verifiedParked) {
                                launchError = null
                                if (item == MediaSite.YOUTUBE) selected = item
                                else external = item
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text(item.title) }
                }
                external?.let { site ->
                    Text("DRM koruması: Bu içerik WebView'de oynatılamayabilir. " +
                        "Resmi uygulamaya geçebilirsiniz. PiP, harici uygulamanın denetimindedir.",
                        color = Color(0xFFFFBD78))
                    Button(onClick = {
                        MediaLauncher.openOfficialApp(context, site).fold(
                            onSuccess = { external = null; onDismiss() },
                            onFailure = { launchError = "Uygulama açılamadı: " +
                                (it.message ?: "Bilinmeyen hata") }
                        )
                    }) { Text("Resmi uygulamada aç") }
                    TextButton(onClick = { external = null }) { Text("Vazgeç") }
                }
                launchError?.let { Text(it, color = Color(0xFFFF9C85)) }
                Text(
                    "Netflix, Disney+ ve Prime Video'nun DRM korumalı " +
                    "içerikleri WebView'de çalışmayabilir. Resmi uygulamaya " +
                    "geçiş kullanıcı onayıyla yapılır.",
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

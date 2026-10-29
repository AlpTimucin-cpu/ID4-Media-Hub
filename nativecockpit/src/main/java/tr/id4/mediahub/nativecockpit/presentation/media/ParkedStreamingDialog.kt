package tr.id4.mediahub.nativecockpit.presentation.media

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** Embedded browsing replaces external package launching, without touching Mola. */
@Composable
fun ParkedStreamingDialog(
    verifiedParked: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember { mutableStateOf<MediaSite?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier.fillMaxWidth().fillMaxHeight(0.9f),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0A1728),
            shadowElevation = 12.dp
        ) {
            val site = selected
            if (site != null) {
                MediaWebViewScreen(
                    service = site,
                    verifiedParked = verifiedParked,
                    onClose = { selected = null }
                )
            } else {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Medya tarayıcısı", style = MaterialTheme.typography.titleLarge,
                        color = Color.White)
                    Text(
                        if (verifiedParked) "Park doğrulandı. İçerikler uygulama içinde açılır."
                        else "Güvenilir Park sinyali yok. Video başlatma engellendi; " +
                             "sıfır GPS hızı Park doğrulaması değildir.",
                        color = Color(0xFFABC5D5),
                        style = MaterialTheme.typography.bodySmall
                    )
                    MediaSite.entries.forEach { service ->
                        OutlinedButton(
                            enabled = verifiedParked,
                            onClick = { selected = service },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text(service.title)
                        }
                    }
                    Text(
                        "Netflix, Disney+ ve Prime Video korumalı içerikleri " +
                            "WebView içinde çalışmayabilir; resmi uygulamaları kullanın.",
                        color = Color(0xFFFFBD78),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Kapat")
                    }
                }
            }
        }
    }
}

package com.evcockpit.presentation.media

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Standalone Mola-style media hub. It DOES NOT require the old mediahub APK,
 * a Fermata package or another external player.
 */
@Composable
fun ParkedStreamingDialog(
    playbackAllowed: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onLaunchMediaEngine: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) } // Video / Radio / Music
    var webSite by remember { mutableStateOf<MediaSite?>(null) }
    var externalError by remember { mutableStateOf<Pair<MediaSite, MediaLaunchState>?>(null) }

    LaunchedEffect(playbackAllowed) {
        if (!playbackAllowed) webSite = null
    }

    if (webSite == MediaSite.YOUTUBE && playbackAllowed) {
        Dialog(
            onDismissRequest = { webSite = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(Modifier.fillMaxSize(), color = Color(0xFF0A0F1A)) {
                MediaWebViewScreen(
                    service = MediaSite.YOUTUBE,
                    playbackAllowed = playbackAllowed,
                    onClose = { webSite = null }
                )
            }
        }
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(.94f).fillMaxHeight(.91f),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF0D1929),
            contentColor = Color.White
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("EV COCKPIT · MEDYA",
                        style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onDismiss) { Text("Kapat") }
                }
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = Color(0xFF0D1929),
                    contentColor = Color(0xFF3DEA9B)
                ) {
                    listOf("Video", "Radyo", "Müzik").forEachIndexed { index, title ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(title) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                when (tab) {
                    0 -> Column(
                        Modifier.fillMaxWidth().weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            if (playbackAllowed)
                                "Resmî video katalogları · araç park hâlindeyken kullanın."
                            else "Android Auto bağlıyken video kullanılamaz.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        MediaSite.entries.forEach { site ->
                            Card(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                                enabled = playbackAllowed,
                                onClick = {
                                    if (site == MediaSite.YOUTUBE) webSite = site
                                    else {
                                        when (val result =
                                            MediaLauncher.launchOfficialApp(context, site)) {
                                            is MediaLaunchState.Launched -> onDismiss()
                                            else -> externalError = site to result
                                        }
                                    }
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF172B40)
                                )
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(site.title,
                                        style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (site == MediaSite.YOUTUBE)
                                            "Uygulama içi YouTube web oynatıcı"
                                        else "Resmî uygulamayı aç · abonelik ve DRM sağlayıcıya aittir",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                    1 -> Box(Modifier.weight(1f)) {
                        RadioScreen(compact = true)
                    }
                    else -> Box(Modifier.weight(1f)) {
                        // Media engine button now opens this app-owned Media3 player.
                        InternalMediaPlayer(
                            modifier = Modifier.fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            allowVideo = false
                        )
                    }
                }
            }
        }
    }

    externalError?.let { (site, state) ->
        AlertDialog(
            onDismissRequest = { externalError = null },
            title = { Text(site.title) },
            text = {
                Text(if (state == MediaLaunchState.NotInstalled)
                    "Bu hizmetin resmî uygulaması yüklü değil."
                    else "Bu hizmet açılamadı. Uygulama izinlerini kontrol edin.")
            },
            confirmButton = {
                TextButton(onClick = { externalError = null }) { Text("Tamam") }
            }
        )
    }
}

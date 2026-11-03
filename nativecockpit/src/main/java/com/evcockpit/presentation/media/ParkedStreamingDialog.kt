package com.evcockpit.presentation.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.evcockpit.R

/** Phone media browser: embedded YouTube, external licensed DRM apps, and radio. */
@Composable
fun ParkedStreamingDialog(
    playbackAllowed: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onLaunchMediaEngine: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var radioTab by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<MediaSite?>(null) }
    var appError by remember { mutableStateOf<Pair<MediaSite, MediaLaunchState>?>(null) }

    LaunchedEffect(playbackAllowed) {
        if (!playbackAllowed) selected = null
    }

    // YouTube only: fullscreen ChromeClient stays owned by the WebView host.
    val service = selected
    if (service == MediaSite.YOUTUBE && playbackAllowed) {
        Dialog(
            onDismissRequest = { selected = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(Modifier.fillMaxSize(), color = Color(0xFF0A0F1A)) {
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
            modifier = modifier.fillMaxWidth().heightIn(max = 600.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0A1728),
            contentColor = Color.White,
            shadowElevation = 12.dp
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.native_in_app_media_browser),
                    style = MaterialTheme.typography.titleLarge
                )
                TabRow(selectedTabIndex = if (radioTab) 1 else 0) {
                    Tab(selected = !radioTab, onClick = { radioTab = false },
                        text = { Text(stringResource(R.string.media_video_tab)) })
                    Tab(selected = radioTab, onClick = { radioTab = true },
                        text = { Text(stringResource(R.string.media_radio_tab)) })
                }
                if (radioTab) {
                    RadioScreen()
                } else {
                    Text(
                        if (playbackAllowed)
                            stringResource(R.string.native_in_app_media_on_the_phone)
                        else stringResource(R.string.native_video_is_unavailable_on_this_screen),
                        color = Color(0xFFABC5D5),
                        style = MaterialTheme.typography.bodySmall
                    )
                    onLaunchMediaEngine?.let { launch ->
                        OutlinedButton(
                            onClick = launch,
                            enabled = playbackAllowed,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text(stringResource(R.string.media_engine_open)) }
                    }
                    MediaSite.entries.forEach { site ->
                        Button(
                            enabled = playbackAllowed,
                            onClick = {
                                if (site == MediaSite.YOUTUBE) {
                                    selected = site
                                } else {
                                    when (val result = MediaLauncher.launchOfficialApp(context, site)) {
                                        is MediaLaunchState.Launched -> onDismiss()
                                        else -> appError = site to result
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text(site.title) }
                    }
                    Text(
                        stringResource(R.string.native_sign_in_to_your_account_on_the_site_playback_support_depends_on_the_se),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB7CEDF)
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.native_close))
                }
            }
        }
    }

    appError?.let { (site, state) ->
        AlertDialog(
            onDismissRequest = { appError = null },
            containerColor = Color(0xEE15263A),
            tonalElevation = 14.dp,
            shape = RoundedCornerShape(24.dp),
            title = { Text(site.title, color = Color.White) },
            text = {
                Text(
                    stringResource(
                        if (state == MediaLaunchState.NotInstalled)
                            R.string.media_external_not_installed
                        else R.string.media_external_launch_blocked
                    ),
                    color = Color(0xFFE0ECF7)
                )
            },
            confirmButton = {
                TextButton(onClick = { appError = null }) {
                    Text(stringResource(R.string.native_close))
                }
            }
        )
    }
}

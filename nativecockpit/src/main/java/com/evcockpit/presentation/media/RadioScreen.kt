package com.evcockpit.presentation.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.evcockpit.R

@Composable
fun RadioScreen(model: RadioViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
        }
    }
    var currentName by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var playbackFailed by remember { mutableStateOf(false) }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
            }
            override fun onPlayerError(error: PlaybackException) {
                playbackFailed = true
                buffering = false
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        player.addListener(listener)
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.radio_description), style = MaterialTheme.typography.bodySmall)
        currentName?.let { name ->
            Text(stringResource(R.string.radio_selected, name))
            if (buffering) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (playbackFailed) Text(stringResource(R.string.radio_playback_error))
            Row {
                TextButton(onClick = {
                    if (!playbackFailed && (playing || player.playWhenReady)) player.pause()
                    else { if (player.playerError != null) player.prepare(); playbackFailed = false; player.play() }
                }) {
                    Text(stringResource(if (playing || buffering) R.string.radio_pause else R.string.radio_play))
                }
                TextButton(onClick = { player.stop(); currentName = null; playbackFailed = false }) {
                    Text(stringResource(R.string.radio_stop))
                }
            }
        }
        when {
            state.loading -> CircularProgressIndicator()
            state.failed -> Text(stringResource(R.string.radio_load_error))
            state.stations.isEmpty() -> Text(stringResource(R.string.radio_empty))
        }
        TextButton(onClick = model::refresh, enabled = !state.loading) {
            Text(stringResource(R.string.radio_refresh))
        }
        LazyColumn(Modifier.fillMaxWidth().height(260.dp)) {
            items(state.stations, key = { it.id }) { station ->
                Column(Modifier.fillMaxWidth().clickable {
                    currentName = station.name
                    playbackFailed = false
                    player.setMediaItem(MediaItem.fromUri(station.urlResolved))
                    player.prepare()
                    player.play()
                }.padding(vertical = 12.dp, horizontal = 8.dp)) {
                    Text(station.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.radio_votes, station.votes),
                        style = MaterialTheme.typography.bodySmall)
                    if (!station.tags.isNullOrBlank()) Text(station.tags,
                        maxLines = 2, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
        }
        Text(stringResource(R.string.radio_attribution), style = MaterialTheme.typography.labelSmall)
    }
}

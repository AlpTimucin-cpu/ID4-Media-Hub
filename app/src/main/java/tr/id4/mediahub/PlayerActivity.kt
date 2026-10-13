package tr.id4.mediahub

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Phone-only video player. No Android Auto projection or driving restriction modification. */
class PlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    private var videoView: PlayerView? = null
    private var streamUri: Uri? = null
    private var playbackInfo: TextView? = null
    private var channelId: String = ""
    private var isVod = false
    private var ended = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val value = intent.getStringExtra(EXTRA_URL) ?: intent.dataString ?: ""
        val parsed = Uri.parse(value)
        if (parsed.scheme != "content" && !M3uParser.isHttpsUrl(value)) {
            finish()
            return
        }
        streamUri = parsed
        channelId = intent.getStringExtra(EXTRA_ID).orEmpty()
        isVod = intent.getBooleanExtra(EXTRA_IS_VOD, false) && channelId.isNotBlank()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 16, 27))
        }
        val header = TextView(this).apply {
            text = intent.getStringExtra(EXTRA_TITLE) ?: "Video oynatıcı"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(24, 24, 24, 12)
        }
        layout.addView(header)
        playbackInfo = TextView(this).apply {
            text = "Yayın hazırlanıyor…"
            setTextColor(Color.rgb(180, 201, 226))
            textSize = 14f
            setPadding(24, 6, 24, 12)
        }.also { layout.addView(it) }
        videoView = PlayerView(this).apply { useController = true }
        layout.addView(videoView, LinearLayout.LayoutParams(-1, 0, 1f))
        val warning = TextView(this).apply {
            text = "Video yalnızca güvenli biçimde park halindeyken izlenmelidir. Android Auto ekranına yansıtılmaz."
            setTextColor(Color.LTGRAY)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(22, 14, 22, 26)
        }
        layout.addView(warning)
        layout.addView(android.widget.Button(this).apply {
            text = "Yeniden dene"
            setOnClickListener {
                playbackInfo?.text = "Yayın yeniden deneniyor…"
                player?.prepare()
                player?.play()
            }
        })
        setContentView(layout)
    }

    override fun onStart() {
        super.onStart()
        val uri = streamUri ?: return
        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    playbackInfo?.text = when (playbackState) {
                        Player.STATE_BUFFERING -> "Yayın yükleniyor…"
                        Player.STATE_READY -> "Yayın hazır • Oynatılıyor"
                        Player.STATE_ENDED -> {
                            ended = true
                            if (isVod) VodProgressStore.clear(this@PlayerActivity, channelId)
                            "Video sona erdi"
                        }
                        else -> "Oynatıcı hazırlanıyor…"
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    playbackInfo?.text = "Oynatma başarısız: ${error.errorCodeName}. Bağlantıyı kontrol edip Yeniden dene'ye dokun."
                    Toast.makeText(this@PlayerActivity,
                        "Video açılamadı: ${error.errorCodeName}. Yayın linkini ve formatı kontrol et.",
                        Toast.LENGTH_LONG).show()
                }
            })
            val media = MediaItem.Builder().setUri(uri)
            when (StreamFormatDetector.detect(uri.toString())) {
                StreamFormat.HLS -> media.setMimeType(MimeTypes.APPLICATION_M3U8)
                StreamFormat.DASH -> media.setMimeType(MimeTypes.APPLICATION_MPD)
                StreamFormat.AUTO -> { }
            }
            setMediaItem(media.build())
            prepare()
            if (isVod) {
                val saved=VodProgressStore.position(this@PlayerActivity,channelId)
                if(saved>0L) {
                    seekTo(saved)
                    playbackInfo?.text = "Kaldığın yerden devam ediliyor: " +
                        (saved/60_000L) + ". dakika"
                }
            }
            playWhenReady = true // User tapped a channel: start playback on the phone
        }
        videoView?.player = player
    }

    override fun onStop() {
        if (isVod && !ended) player?.let {
            VodProgressStore.save(this,channelId,it.currentPosition,it.duration)
        }
        videoView?.player = null
        player?.release()
        player = null
        super.onStop()
    }

    companion object {
        const val EXTRA_URL = "source_url"
        const val EXTRA_TITLE = "source_title"
        const val EXTRA_ID = "source_channel_id"
        const val EXTRA_IS_VOD = "source_is_vod"
    }
}

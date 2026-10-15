package tr.id4.mediahub

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.PlayerView

/**
 * Phone-only VOD with bounded reconnection and private diagnostic export.
 * Distinct from DiPlay's BYD/CarPlay projection receiver: no USB, MFi, or
 * Android Auto video access or proprietary code is imported.
 */
class PlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    private var videoView: PlayerView? = null
    private var streamUri: Uri? = null
    private var playbackInfo: TextView? = null
    private var channelId: String = ""
    private var isVod = false
    private var ended = false
    private var sessionActive = false
    private val diagnostics = PlaybackDiagnostics()
    private val retries = StreamRetryBudget()
    private val retryHandler = Handler(Looper.getMainLooper())
    private var scheduledRetry: Runnable? = null
    private var sessionGeneration = 0

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
        layout.addView(TextView(this).apply {
            text = intent.getStringExtra(EXTRA_TITLE) ?: "Video oynatıcı"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(24, 24, 24, 12)
        })
        playbackInfo = TextView(this).apply {
            text = "Yayın hazırlanıyor…"
            setTextColor(Color.rgb(180, 201, 226))
            textSize = 14f
            setPadding(24, 6, 24, 12)
        }.also { layout.addView(it) }
        videoView = PlayerView(this).apply { useController = true }
        layout.addView(videoView, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(TextView(this).apply {
            text = "Video yalnızca güvenli biçimde park halindeyken izlenmelidir. " +
                "Android Auto ekranına yansıtılmaz."
            setTextColor(Color.LTGRAY)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(22, 14, 22, 12)
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        actions.addView(Button(this).apply {
            text = "Yeniden dene"
            isAllCaps = false
            setOnClickListener {
                cancelRecovery()
                retries.reset()
                diagnostics.start(SystemClock.elapsedRealtime())
                playbackInfo?.text = "Yayın yeniden deneniyor…"
                player?.apply { prepare(); playWhenReady = true }
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(Button(this).apply {
            text = "Tanı raporunu paylaş"
            isAllCaps = false
            setOnClickListener { shareDiagnostics() }
        }, LinearLayout.LayoutParams(0, -2, 1.2f))
        layout.addView(actions)
        setContentView(layout)
    }

    @OptIn(UnstableApi::class)
    override fun onStart() {
        super.onStart()
        val uri = streamUri ?: return
        cancelRecovery()
        retries.reset()
        diagnostics.start(SystemClock.elapsedRealtime())
        ended = false
        sessionActive = true

        val instance = ExoPlayer.Builder(this).build()
        instance.addAnalyticsListener(object : AnalyticsListener {
            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long
            ) {
                diagnostics.dropped(droppedFrames)
            }
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime, decoderName: String,
                initializedTimestampMs: Long, initializationDurationMs: Long
            ) {
                diagnostics.videoDecoder(decoderName, initializationDurationMs)
            }
            override fun onVideoFrameProcessingOffset(
                eventTime: AnalyticsListener.EventTime,
                totalProcessingOffsetUs: Long, frameCount: Int
            ) {
                diagnostics.processedFrames(frameCount)
            }
        })
        instance.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                diagnostics.firstFrame(SystemClock.elapsedRealtime())
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                diagnostics.videoSize(videoSize.width, videoSize.height)
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (!sessionActive) return
                when (state) {
                    Player.STATE_BUFFERING -> {
                        diagnostics.buffering(SystemClock.elapsedRealtime())
                        playbackInfo?.text = "Yayın yükleniyor…"
                    }
                    Player.STATE_READY -> {
                        diagnostics.ready(SystemClock.elapsedRealtime())
                        playbackInfo?.text = "Yayın hazır • Oynatılıyor"
                    }
                    Player.STATE_ENDED -> {
                        diagnostics.ready(SystemClock.elapsedRealtime())
                        ended = true
                        if (isVod) VodProgressStore.clear(this@PlayerActivity, channelId)
                        playbackInfo?.text = "Video sona erdi"
                    }
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                if (!sessionActive) return
                diagnostics.ready(SystemClock.elapsedRealtime())
                val httpCode = findHttpStatus(error)
                val failure = VodErrorPolicy.classify(httpCode, error.errorCodeName)
                // Only safe enumerated codes and categories; NEVER raw exception text.
                diagnostics.problem(failure.kind.name + " / " + error.errorCodeName)
                val delay = retries.nextDelay(failure.canRetry)
                if (delay == null) {
                    playbackInfo?.text = failure.message +
                        " Bağlantın yetkili ve güncelse Yeniden dene kullanılabilir."
                } else {
                    playbackInfo?.text = failure.message + " " +
                        (delay/1000.0) + " sn sonra otomatik deneme " +
                        "(${retries.attempts}/2)."
                    scheduleRecovery(delay)
                }
            }
        })
        val media = MediaItem.Builder().setUri(uri)
        when (StreamFormatDetector.detect(uri.toString())) {
            StreamFormat.HLS -> media.setMimeType(MimeTypes.APPLICATION_M3U8)
            StreamFormat.DASH -> media.setMimeType(MimeTypes.APPLICATION_MPD)
            StreamFormat.AUTO -> {}
        }
        instance.setMediaItem(media.build())
        player = instance
        videoView?.player = instance
        instance.prepare()
        if (isVod) {
            val saved = VodProgressStore.position(this, channelId)
            if (saved > 0L) {
                instance.seekTo(saved)
                playbackInfo?.text = "Kaldığın yerden devam ediliyor: " +
                    (saved/60_000L) + ". dakika"
            }
        }
        instance.playWhenReady = true
    }

    @OptIn(UnstableApi::class)
    private fun findHttpStatus(error: PlaybackException): Int? {
        var cause: Throwable? = error
        repeat(8) {
            val current = cause ?: return null
            if (current is HttpDataSource.InvalidResponseCodeException)
                return current.responseCode
            cause = current.cause
        }
        return null
    }

    private fun cancelRecovery() {
        scheduledRetry?.let { retryHandler.removeCallbacks(it) }
        scheduledRetry = null
        sessionGeneration++
    }

    private fun scheduleRecovery(delayMs: Long) {
        val generation = sessionGeneration
        val task = Runnable {
            scheduledRetry = null
            if (!sessionActive || isFinishing || isDestroyed ||
                generation != sessionGeneration) return@Runnable
            diagnostics.retry()
            playbackInfo?.text = "Yayın sunucusuna yeniden bağlanılıyor…"
            player?.apply { prepare(); playWhenReady = true }
        }
        scheduledRetry?.let { retryHandler.removeCallbacks(it) }
        scheduledRetry = task
        retryHandler.postDelayed(task, delayMs)
    }

    private fun shareDiagnostics() {
        // No URL, signed query token, title, filename, credentials or headers.
        val report = buildString {
            appendLine("ID.4 Yol Arkadaşım • Film oynatıcı tanı raporu")
            appendLine("Sürüm: 0.53 Beta")
            appendLine("Android API seviyesi: " + android.os.Build.VERSION.SDK_INT)
            appendLine("Kaynak tipi: " +
                if (streamUri?.scheme == "content") "Yerel belge" else "HTTPS")
            appendLine(diagnostics.report(SystemClock.elapsedRealtime()))
            appendLine("Rapor yalnızca kullanıcı paylaşmayı seçtiğinde oluşturulur.")
        }
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, report)
            }, "Özel bağlantı içermeyen tanı raporunu paylaş"))
        } catch (_: ActivityNotFoundException) {
            playbackInfo?.text = "Paylaşım uygulaması bulunamadı."
        }
    }

    override fun onStop() {
        sessionActive = false
        cancelRecovery()
        diagnostics.ready(SystemClock.elapsedRealtime())
        if (isVod && !ended) player?.let {
            VodProgressStore.save(this, channelId, it.currentPosition, it.duration)
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

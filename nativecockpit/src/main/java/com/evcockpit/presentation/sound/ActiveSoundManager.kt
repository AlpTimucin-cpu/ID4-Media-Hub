package com.evcockpit.presentation.sound

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.PlaybackException
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.evcockpit.R
import com.evcockpit.data.settings.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class EngineProfile(val label: String, val idle: Int, val accel: Int) {
    V8("V8", R.raw.v8_idle, R.raw.v8_accel),
    F1("F1", R.raw.f1_idle, R.raw.f1_accel),
    Futuristic("Futuristic", R.raw.futuristic_idle, R.raw.futuristic_accel),
    Turbine("Turbine", R.raw.turbine_idle, R.raw.turbine_accel),
    Stealth("Stealth", R.raw.stealth_idle, R.raw.stealth_accel);

    companion object {
        fun from(value: String): EngineProfile = entries.firstOrNull {
            it.name == value
        } ?: V8
    }
}

/**
 * In-cabin only synthetic audio. Never replace the vehicle's external AVAS.
 * Mutate ExoPlayer from the main thread; no music library or real engine sample.
 * A real fresh OBD speed is required (no speed => automatic silence).
 */
@Singleton
class ActiveSoundManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val main = Handler(Looper.getMainLooper())
    private var idle: ExoPlayer? = null
    private var acceleration: ExoPlayer? = null
    private var currentProfile: EngineProfile? = null
    private var lastSpeedKmh: Double? = null
    private var lastSpeedAtMs: Long? = null
    private var enabled = false
    private var volume = 1f
    private var ducked = false
    private var previewing = false
    private var previewToken = 0L
    private var activeInForeground = false
    private var lastSpeedInput: Double? = null
    private var lastInputTime: Long? = null

    private fun player(resourceId: Int): ExoPlayer? {
        var candidate: ExoPlayer? = null
        return try {
            // Resource existence is checked before Media3 starts asynchronous loading.
            context.resources.openRawResource(resourceId).use { /* readable */ }
            candidate = ExoPlayer.Builder(context).build()
            candidate.apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                    false
                )
                setHandleAudioBecomingNoisy(true)
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("EYA-ActiveSound", "Engine audio decoder/source unavailable", error)
                        pausePlayers()
                    }
                })
                setMediaItem(MediaItem.fromUri(
                    Uri.parse("android.resource://${context.packageName}/$resourceId")
                ))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
            }
        } catch (error: Exception) {
            Log.e("EYA-ActiveSound", "Engine WAV could not be loaded", error)
            runCatching { candidate?.release() }
            null
        }
    }

    private fun selectProfile(profile: EngineProfile): Boolean {
        if (profile == currentProfile && idle != null && acceleration != null) return true
        runCatching { idle?.release() }
        runCatching { acceleration?.release() }
        idle = player(profile.idle)
        acceleration = player(profile.accel)
        if (idle == null || acceleration == null) {
            runCatching { idle?.release() }
            runCatching { acceleration?.release() }
            idle = null
            acceleration = null
            currentProfile = null
            return false // Missing assets fail closed; never crash the UI.
        }
        currentProfile = profile
        return true
    }

    /** Call at most once per real OBD sample; time is the sample timestamp. */
    fun update(
        settings: UserSettings,
        speedKmh: Double?,
        speedAtMs: Long?,
        foreground: Boolean
    ) {
        main.post {
            activeInForeground = foreground
            enabled = settings.isEngineSoundEnabled
            volume = settings.engineVolume.coerceIn(0f, 1.5f)
            lastSpeedInput = speedKmh
            lastInputTime = speedAtMs
            if (!foreground || !enabled || previewing ||
                speedKmh == null || speedAtMs == null) {
                if (!previewing) pausePlayers()
                return@post
            }
            if (!selectProfile(EngineProfile.from(settings.engineSoundProfile))) return@post
            if (lastSpeedAtMs == speedAtMs) {
                // Poll tick without a new CAN measurement: do not reset accel mode.
                applyVolumes()
                return@post
            }
            val deltaSeconds =
                lastSpeedAtMs?.let { (speedAtMs - it).toDouble() / 1000.0 } ?: 0.0
            val decision = EngineMotionPolicy.decide(
                speedKmh, lastSpeedKmh, deltaSeconds
            )
            lastSpeedKmh = speedKmh
            lastSpeedAtMs = speedAtMs
            runCatching {
                acceleration?.playbackParameters = PlaybackParameters(1f, decision.pitch)
                idle?.playbackParameters = PlaybackParameters(1f, 0.95f)
                when (decision.mode) {
                    EngineMotionPolicy.Mode.ACCELERATING -> {
                        idle?.pause()
                        acceleration?.play()
                    }
                    EngineMotionPolicy.Mode.IDLE -> {
                        acceleration?.pause()
                        idle?.play()
                    }
                    EngineMotionPolicy.Mode.SILENT -> pausePlayers()
                }
                applyVolumes()
            }.onFailure { error ->
                Log.e("EYA-ActiveSound", "Playback update failed", error)
                pausePlayers()
            }
        }
    }

    fun setSpeechDucking(active: Boolean) {
        main.post {
            ducked = active
            applyVolumes()
        }
    }

    private fun applyVolumes() {
        // Media3 player's volume is 0..1. UI 150% means max permitted app gain,
        // NOT boosting above device/system maximum (which risks clipping).
        val normalized = (volume / 1.5f).coerceIn(0f, 1f)
        val multiplier = if (ducked) 0.2f else 1f
        idle?.volume = normalized * multiplier * 0.75f
        acceleration?.volume = normalized * multiplier
    }

    fun preview(settings: UserSettings) {
        main.post {
            if (!activeInForeground) return@post
            previewToken++
            val token = previewToken
            previewing = true
            volume = settings.engineVolume.coerceIn(0f, 1.5f)
            if (!selectProfile(EngineProfile.from(settings.engineSoundProfile))) {
                previewing = false
                return@post
            }
            runCatching {
                idle?.pause()
                acceleration?.playbackParameters = PlaybackParameters(1f, 1.25f)
                acceleration?.play()
                applyVolumes()
            }.onFailure { error ->
                Log.e("EYA-ActiveSound", "Preview playback failed", error)
                previewing = false
                pausePlayers()
                return@post
            }
            main.postDelayed({
                if (previewToken == token) {
                    previewing = false
                    pausePlayers()
                }
            }, 3_000L)
        }
    }

    private fun pausePlayers() {
        runCatching { idle?.pause() }
        runCatching { acceleration?.pause() }
        lastSpeedAtMs = null
        lastSpeedKmh = null
    }

    fun onResume() { main.post { activeInForeground = true } }

    fun onPause() {
        main.post {
            activeInForeground = false
            previewing = false
            previewToken++
            pausePlayers()
        }
    }

    /** Use only when application-owned audio lifetime ends. */
    fun release() {
        main.post {
            pausePlayers()
            idle?.release()
            acceleration?.release()
            idle = null
            acceleration = null
            currentProfile = null
        }
    }
}

package com.moody.moodyvideoeditor.utils

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.NoiseSuppressor
import android.media.audiofx.PresetReverb
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.moody.moodyvideoeditor.data.AudioAdjustmentSettings

// Real-time preview of audio FX.
// Applies to BOTH ExoPlayers (video + audio) so preview works regardless
// of which player is currently active.
object AudioPreviewEngine {

    private val equalizers = mutableListOf<Equalizer>()
    private val reverbs = mutableListOf<PresetReverb>()
    private val noiseSuppressors = mutableListOf<NoiseSuppressor>()
    private val loudnessEnhancers = mutableListOf<LoudnessEnhancer>()
    private var currentFx: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private var retryRunnable: Runnable? = null

    fun apply(
        exoPlayer: ExoPlayer,
        audioExoPlayer: ExoPlayer,
        fx: String,
        intensity: Float
    ) {
        release()

        if (fx == "none" || fx.isBlank()) {
            resetPitch(exoPlayer)
            resetPitch(audioExoPlayer)
            currentFx = null
            return
        }

        val t = (intensity / 100f).coerceIn(0f, 2f)
        currentFx = fx

        // 1. PITCH SHIFT — both players
        val pitch = when (fx) {
            "deep" -> 1f - 0.15f * t
            "monster" -> 1f - 0.30f * t
            "chipmunk" -> 1f + 0.50f * t
            "baby" -> 1f + 0.60f * t
            "underwater" -> 1f - 0.10f * t
            else -> 1f
        }.coerceIn(0.5f, 2.0f)

        setPitch(exoPlayer, pitch)
        setPitch(audioExoPlayer, pitch)

        // 2. EQ / REVERB — both session IDs
        applyEqWithRetry(exoPlayer, audioExoPlayer, fx, t, attempt = 0)
    }

    fun applySettings(
        exoPlayer: ExoPlayer,
        audioExoPlayer: ExoPlayer,
        layers: List<AudioAdjustmentSettings>,
        processors: List<AudioSettingsAudioProcessor>
    ) {
        release()
        val settings = layers.map { it.clamped() }
        val echoAmount = settings.maxOfOrNull { it.echo }?.div(100f) ?: 0f
        val reverbAmount = settings.maxOfOrNull { it.reverb }?.div(100f) ?: 0f
        val studioReverbAmount =
            settings.maxOfOrNull { it.studioReverb }?.div(100f) ?: 0f
        processors.forEach {
            it.setEffects(echoAmount, reverbAmount, studioReverbAmount)
        }
        val pitch = pitchFor(settings)
        setPitch(exoPlayer, pitch)
        setPitch(audioExoPlayer, pitch)
        if (settings.isEmpty()) return

        val settingsKey = "audio-settings:${settings.hashCode()}"
        currentFx = settingsKey
        applySettingsWithRetry(exoPlayer, audioExoPlayer, settings, settingsKey, attempt = 0)
    }

    private fun applySettingsWithRetry(
        exoPlayer: ExoPlayer,
        audioExoPlayer: ExoPlayer,
        settings: List<AudioAdjustmentSettings>,
        settingsKey: String,
        attempt: Int
    ) {
        if (currentFx != settingsKey) return

        val sidVideo = try {
            exoPlayer.audioSessionId
        } catch (_: RuntimeException) {
            0
        }
        val sidAudio = try {
            audioExoPlayer.audioSessionId
        } catch (_: RuntimeException) {
            0
        }

        if (sidVideo == 0 && sidAudio == 0) {
            if (attempt < 25) {
                val retry = Runnable {
                    applySettingsWithRetry(
                        exoPlayer, audioExoPlayer, settings, settingsKey, attempt + 1
                    )
                }
                retryRunnable = retry
                handler.postDelayed(retry, 120)
            } else {
                Log.w("AudioPreviewEngine", "Audio settings preview unavailable: no audio session")
            }
            return
        }

        if (sidVideo > 0) applySettingsToSession(sidVideo, settings)
        if (sidAudio > 0 && sidAudio != sidVideo) applySettingsToSession(sidAudio, settings)
    }

    private fun applySettingsToSession(
        sessionId: Int,
        layers: List<AudioAdjustmentSettings>
    ) {
        val bass = layers.sumOf { it.bass.toDouble() }.toFloat().coerceIn(0f, 100f)
        val mids = layers.sumOf { it.mids.toDouble() }.toFloat().coerceIn(0f, 100f)
        val treble = layers.sumOf { it.treble.toDouble() }.toFloat().coerceIn(0f, 100f)
        val vocals = layers.sumOf { it.vocals.toDouble() }.toFloat().coerceIn(0f, 100f)
        val enhancement =
            layers.sumOf { it.vocalEnhancement.toDouble() }.toFloat().coerceIn(0f, 100f)
        val loudness =
            layers.sumOf { it.loudness.toDouble() }.toFloat().coerceIn(0f, 100f)

        if (bass + mids + treble + vocals + enhancement > 0f) {
            tryEq(
                sessionId,
                listOf(
                    0 to (bass * 0.12f),
                    1 to (bass * 0.06f + mids * 0.03f),
                    2 to (mids * 0.06f + vocals * 0.04f),
                    3 to (treble * 0.05f + vocals * 0.04f + enhancement * 0.03f),
                    4 to (treble * 0.12f + enhancement * 0.04f)
                )
            )
        }

        if (loudness > 0f) {
            tryLoudnessEnhancer(sessionId, (loudness * 12f).toInt())
        }
    }

    private fun pitchFor(layers: List<AudioAdjustmentSettings>): Float {
        val semitones = layers.sumOf { ((it.pitch - 50f) * 0.24f).toDouble() }
        return Math.pow(2.0, semitones / 12.0).toFloat().coerceIn(0.5f, 2f)
    }

    private fun setPitch(player: ExoPlayer, pitch: Float) {
        try {
            val current = player.playbackParameters
            player.playbackParameters = PlaybackParameters(current.speed, pitch)
        } catch (_: RuntimeException) {
        }
    }

    private fun tryLoudnessEnhancer(sessionId: Int, gainMb: Int) {
        try {
            val enhancer = LoudnessEnhancer(sessionId).apply {
                setTargetGain(gainMb.coerceIn(0, 1200))
                enabled = true
            }
            loudnessEnhancers.add(enhancer)
        } catch (error: RuntimeException) {
            Log.w("AudioPreviewEngine", "Could not create preview loudness enhancer", error)
        }
    }

    private fun applyEqWithRetry(
        exoPlayer: ExoPlayer,
        audioExoPlayer: ExoPlayer,
        fx: String,
        t: Float,
        attempt: Int
    ) {
        if (currentFx != fx) return

        val sidVideo = try {
            exoPlayer.audioSessionId
        } catch (_: Throwable) {
            0
        }
        val sidAudio = try {
            audioExoPlayer.audioSessionId
        } catch (_: Throwable) {
            0
        }

        // If neither session is ready, retry
        if (sidVideo == 0 && sidAudio == 0) {
            if (attempt < 25) {
                val r = Runnable {
                    applyEqWithRetry(exoPlayer, audioExoPlayer, fx, t, attempt + 1)
                }
                retryRunnable = r
                handler.postDelayed(r, 120)
            }
            return
        }

        if (sidVideo > 0) applyFxToSession(sidVideo, fx, t)
        if (sidAudio > 0 && sidAudio != sidVideo) applyFxToSession(sidAudio, fx, t)
    }

    private fun applyFxToSession(sessionId: Int, fx: String, t: Float) {
        when (fx) {
            // EQ effects
            "warm" -> tryEq(
                sessionId, listOf(
                    0 to (8f * t), 1 to (6f * t), 2 to 0f, 3 to 0f, 4 to (-4f * t)
                )
            )

            "bright" -> tryEq(
                sessionId, listOf(
                    0 to (-4f * t), 1 to (-2f * t), 2 to 0f, 3 to (6f * t), 4 to (10f * t)
                )
            )

            "vocal" -> tryEq(
                sessionId, listOf(
                    0 to (-6f * t), 1 to 0f, 2 to (10f * t), 3 to (6f * t), 4 to 0f
                )
            )

            "podcast" -> tryEq(
                sessionId, listOf(
                    0 to (-8f * t), 1 to (2f * t), 2 to (8f * t), 3 to (4f * t), 4 to (-4f * t)
                )
            )

            "telephone" -> tryEq(
                sessionId, listOf(
                    0 to (-15f * t), 1 to (-15f * t), 2 to (10f * t),
                    3 to (-8f * t), 4 to (-15f * t)
                )
            )

            "radio" -> tryEq(
                sessionId, listOf(
                    0 to (-12f * t), 1 to (-8f * t), 2 to (8f * t),
                    3 to (-6f * t), 4 to (-12f * t)
                )
            )

            "whisper" -> tryEq(
                sessionId, listOf(
                    0 to (-8f * t), 1 to 0f, 2 to 0f, 3 to (8f * t), 4 to (12f * t)
                )
            )

            "underwater" -> tryEq(
                sessionId, listOf(
                    0 to (-15f * t), 1 to (-15f * t), 2 to 0f,
                    3 to (-12f * t), 4 to (-20f * t)
                )
            )

            "studio" -> tryEq(
                sessionId, listOf(
                    0 to (3f * t), 1 to (2f * t), 2 to (2f * t),
                    3 to (3f * t), 4 to (4f * t)
                )
            )

            "noise_reduction" -> tryNoiseSuppressor(sessionId, t)
            "robot" -> tryEq(
                sessionId, listOf(
                    0 to (-12f * t), 1 to (4f * t), 2 to (8f * t),
                    3 to (-8f * t), 4 to (6f * t)
                )
            )

            // Reverb effects
            "echo", "reverb" -> tryReverb(sessionId, PresetReverb.PRESET_MEDIUMROOM)
            "cave", "stadium" -> tryReverb(sessionId, PresetReverb.PRESET_LARGEROOM)
        }
    }

    private fun tryEq(sessionId: Int, bands: List<Pair<Int, Float>>) {
        try {
            val eq = Equalizer(0, sessionId)
            eq.enabled = true
            val numBands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val minMb = range[0]
            val maxMb = range[1]

            bands.forEach { (bandIdx, gainDb) ->
                if (bandIdx < numBands) {
                    val gainMb = (gainDb * 100f).toInt()
                        .coerceIn(minMb.toInt(), maxMb.toInt())
                        .toShort()
                    try {
                        eq.setBandLevel(bandIdx.toShort(), gainMb)
                    } catch (_: Throwable) {
                    }
                }
            }
            equalizers.add(eq)
        } catch (error: RuntimeException) {
            Log.w("AudioPreviewEngine", "Could not create preview equalizer", error)
        }
    }

    private fun tryReverb(sessionId: Int, preset: Short) {
        try {
            val rev = PresetReverb(0, sessionId)
            rev.preset = preset
            rev.enabled = true
            reverbs.add(rev)
        } catch (error: RuntimeException) {
            Log.w("AudioPreviewEngine", "Could not create preview reverb", error)
        }
    }

    private fun tryNoiseSuppressor(sessionId: Int, intensity: Float) {
        if (!NoiseSuppressor.isAvailable()) {
            tryEq(
                sessionId,
                listOf(0 to (-10f * intensity), 4 to (-8f * intensity))
            )
            return
        }
        try {
            val suppressor = NoiseSuppressor.create(sessionId) ?: run {
                tryEq(sessionId, listOf(0 to (-10f * intensity), 4 to (-8f * intensity)))
                return
            }
            suppressor.enabled = true
            noiseSuppressors.add(suppressor)
        } catch (_: RuntimeException) {
            tryEq(
                sessionId,
                listOf(0 to (-10f * intensity), 4 to (-8f * intensity))
            )
        }
    }

    private fun resetPitch(player: ExoPlayer) {
        setPitch(player, 1f)
    }

    fun release() {
        retryRunnable?.let { handler.removeCallbacks(it) }
        retryRunnable = null

        equalizers.forEach {
            try {
                it.enabled = false
                it.release()
            } catch (_: Throwable) {
            }
        }
        equalizers.clear()

        reverbs.forEach {
            try {
                it.enabled = false
                it.release()
            } catch (_: Throwable) {
            }
        }
        reverbs.clear()

        noiseSuppressors.forEach {
            try {
                it.enabled = false
                it.release()
            } catch (_: RuntimeException) {
            }
        }
        noiseSuppressors.clear()

        loudnessEnhancers.forEach {
            try {
                it.enabled = false
                it.release()
            } catch (error: RuntimeException) {
                Log.w("AudioPreviewEngine", "Could not release preview loudness enhancer", error)
            }
        }
        loudnessEnhancers.clear()

        currentFx = null
    }
}
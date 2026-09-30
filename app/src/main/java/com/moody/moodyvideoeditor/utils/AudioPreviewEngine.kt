package com.moody.moodyvideoeditor.utils

import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.os.Handler
import android.os.Looper
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer

/**
 * Real-time preview of audio FX.
 *
 * Applies to BOTH ExoPlayers (video + audio) so preview works regardless
 * of which player is currently active.
 */
object AudioPreviewEngine {

    private val equalizers = mutableListOf<Equalizer>()
    private val reverbs = mutableListOf<PresetReverb>()
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

        // ═══ 1. PITCH SHIFT — both players ═══
        val pitch = when (fx) {
            "deep" -> 1f - 0.15f * t
            "monster" -> 1f - 0.30f * t
            "chipmunk" -> 1f + 0.50f * t
            "baby" -> 1f + 0.60f * t
            "underwater" -> 1f - 0.10f * t
            else -> 1f
        }.coerceIn(0.5f, 2.0f)

        try {
            exoPlayer.playbackParameters = PlaybackParameters(1f, pitch)
        } catch (_: Throwable) {
        }
        try {
            audioExoPlayer.playbackParameters = PlaybackParameters(1f, pitch)
        } catch (_: Throwable) {
        }

        // ═══ 2. EQ / REVERB — both session IDs ═══
        applyEqWithRetry(exoPlayer, audioExoPlayer, fx, t, attempt = 0)
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
        } catch (_: Throwable) {
        }
    }

    private fun tryReverb(sessionId: Int, preset: Short) {
        try {
            val rev = PresetReverb(0, sessionId)
            rev.preset = preset
            rev.enabled = true
            reverbs.add(rev)
        } catch (_: Throwable) {
        }
    }

    private fun resetPitch(player: ExoPlayer) {
        try {
            player.playbackParameters = PlaybackParameters(1f, 1f)
        } catch (_: Throwable) {
        }
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

        currentFx = null
    }
}
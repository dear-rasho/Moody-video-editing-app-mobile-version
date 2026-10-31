package com.moody.moodyvideoeditor.utils

import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import java.util.WeakHashMap
import kotlin.math.log10
import kotlin.math.roundToInt

// Mirrors js/features/volume.js
// Applied to ExoPlayer + used in FFmpeg export (volume filter).
object VolumeEngine {
    const val MIN_VOLUME = 0f
    const val MAX_VOLUME = 3.0f
    const val DEFAULT_VOLUME = 1.0f

    fun clamp(v: Float): Float =
        (v.takeIf { it.isFinite() } ?: DEFAULT_VOLUME).coerceIn(MIN_VOLUME, MAX_VOLUME)

    fun effectiveVolume(volume: Float, isMuted: Boolean): Float =
        if (isMuted) 0f else clamp(volume)

    private val enhancers = WeakHashMap<ExoPlayer, Pair<Int, LoudnessEnhancer>>()

    fun applyToPlayer(player: ExoPlayer, volume: Float) {
        val effective = clamp(volume)
        player.volume = effective.coerceAtMost(1f)

        val sessionId = player.audioSessionId
        synchronized(enhancers) {
            val current = enhancers[player]
            if (current != null && current.first != sessionId) {
                releaseEnhancer(current.second)
                enhancers.remove(player)
            }

            if (sessionId <= 0) return

            val gainMb = if (effective <= 1f) 0 else {
                (20f * log10(effective) * 100f).roundToInt().coerceAtLeast(0)
            }
            if (gainMb == 0) {
                enhancers[player]?.let { releaseEnhancer(it.second) }
                enhancers.remove(player)
                return
            }

            try {
                val enhancer = enhancers[player]?.second ?: LoudnessEnhancer(sessionId).also {
                    enhancers[player] = sessionId to it
                }
                enhancer.setTargetGain(gainMb)
                enhancer.enabled = true
            } catch (error: RuntimeException) {
                Log.w("VolumeEngine", "Could not boost preview audio", error)
                enhancers.remove(player)?.let { releaseEnhancer(it.second) }
            }
        }
    }

    fun releasePlayer(player: ExoPlayer) {
        synchronized(enhancers) {
            enhancers.remove(player)?.let { releaseEnhancer(it.second) }
        }
    }

    private fun releaseEnhancer(enhancer: LoudnessEnhancer) {
        try {
            enhancer.enabled = false
            enhancer.release()
        } catch (error: RuntimeException) {
            Log.w("VolumeEngine", "Could not release preview volume booster", error)
        }
    }

    // FFmpeg volume filter
    fun buildFfmpegFilter(volume: Float, isMuted: Boolean): String {
        val v = effectiveVolume(volume, isMuted)
        return "volume=${"%.3f".format(java.util.Locale.US, v)}"
    }
}
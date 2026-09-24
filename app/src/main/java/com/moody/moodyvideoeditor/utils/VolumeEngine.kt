package com.moody.moodyvideoeditor.utils

/**
 * Mirrors js/features/volume.js
 * Applied to ExoPlayer + used in FFmpeg export (volume filter).
 */
object VolumeEngine {
    const val MIN_VOLUME = 0f
    const val MAX_VOLUME = 1.0f
    const val DEFAULT_VOLUME = 1.0f

    fun clamp(v: Float): Float = v.coerceIn(MIN_VOLUME, MAX_VOLUME)

    fun effectiveVolume(volume: Float, isMuted: Boolean): Float =
        if (isMuted) 0f else clamp(volume)

    /** FFmpeg volume filter */
    fun buildFfmpegFilter(volume: Float, isMuted: Boolean): String {
        val v = effectiveVolume(volume, isMuted)
        return "volume=${"%.3f".format(v)}"
    }
}
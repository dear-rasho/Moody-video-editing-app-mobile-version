package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.EditorClip

/**
 * Mirrors js/features/speed.js
 * Speed application + linked clip sync.
 */
object SpeedEngine {

    const val MIN_SPEED = 0.25f
    const val MAX_SPEED = 4.0f
    const val DEFAULT_SPEED = 1.0f

    val PRESETS = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 3.0f, 4.0f)

    /**
     * Mirrors JS: ensureBase(clip)
     * Returns the base (unspedded) source duration in ms.
     */
    fun getBaseDuration(clip: EditorClip): Long {
        return (clip.sourceEndMs - clip.sourceStartMs).coerceAtLeast(EditorClip.MIN_DURATION_MS)
    }

    /**
     * Mirrors JS: applySpeed(clip, speed)
     * speed is stored on clip. Duration is derived from sourceDuration / speed.
     */
    fun applySpeed(clip: EditorClip, speed: Float): EditorClip {
        val clamped = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        return clip.copy(speed = clamped)
    }

    /**
     * Mirrors JS: syncLinkedSpeed(primaryClip, speed)
     * Propagates speed to the linked video/audio clip.
     * Returns updated clips list.
     */
    fun syncLinkedSpeed(
        primaryClip: EditorClip,
        speed: Float,
        allClips: List<EditorClip>
    ): List<EditorClip> {
        val linkId = primaryClip.linkedId ?: return allClips
        val clamped = speed.coerceIn(MIN_SPEED, MAX_SPEED)

        return allClips.map { clip ->
            if (clip.id != primaryClip.id && clip.linkedId == linkId) {
                clip.copy(
                    speed = clamped,
                    timelineStartMs = primaryClip.timelineStartMs
                )
            } else clip
        }
    }

    /**
     * Applies playbackRate to ExoPlayer — clamp to safe range.
     * Mirrors JS: applyToMediaElements()
     */
    fun clampForExoPlayer(speed: Float): Float {
        // ExoPlayer supports 0.1–8.0 typically; Media3 supports up to 4x default
        return speed.coerceIn(0.25f, 4.0f)
    }

    /**
     * Snap to nearest preset if within 0.01.
     * Used for chip active-state highlighting.
     */
    fun matchedPreset(speed: Float): Float? {
        return PRESETS.firstOrNull { kotlin.math.abs(it - speed) < 0.01f }
    }
}
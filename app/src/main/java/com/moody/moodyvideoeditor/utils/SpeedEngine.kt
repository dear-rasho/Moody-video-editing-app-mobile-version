package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.KeyframeLibrary
import com.moody.moodyvideoeditor.data.KeyframeMap
import kotlin.math.roundToLong

// Mirrors js/features/speed.js
// Speed application + linked clip sync.
object SpeedEngine {

    const val MIN_SPEED = 0.25f
    const val MAX_SPEED = 4.0f
    const val DEFAULT_SPEED = 1.0f

    val PRESETS = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 3.0f, 4.0f)

    // Mirrors JS: ensureBase(clip)
    // Returns the base (unspedded) source duration in ms.
    fun getBaseDuration(clip: EditorClip): Long {
        return (clip.sourceEndMs - clip.sourceStartMs).coerceAtLeast(EditorClip.MIN_DURATION_MS)
    }

    // Mirrors JS: applySpeed(clip, speed)
    // speed is stored on clip. Duration is derived from sourceDuration / speed.
    fun applySpeed(clip: EditorClip, speed: Float): EditorClip {
        val clamped = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        return clip.copy(speed = clamped)
    }

    // Mirrors JS: syncLinkedSpeed(primaryClip, speed)
    // Propagates speed to the linked video/audio clip.
    // Returns updated clips list.
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

    // Applies playbackRate to ExoPlayer — clamp to safe range.
    // Mirrors JS: applyToMediaElements()
    fun clampForExoPlayer(speed: Float): Float {
        // ExoPlayer supports 0.1–8.0 typically; Media3 supports up to 4x default
        return speed.coerceIn(0.25f, 4.0f)
    }

    // Snap to nearest preset if within 0.01.
    // Used for chip active-state highlighting.
    fun matchedPreset(speed: Float): Float? {
        return PRESETS.firstOrNull { kotlin.math.abs(it - speed) < 0.01f }
    }

    fun speedAt(clip: EditorClip, timelineOffsetMs: Long): Float {
        return speedAt(clip.keyframes, clip.speed, timelineOffsetMs)
    }

    fun speedAt(keyframes: KeyframeMap, baseSpeed: Float, timelineOffsetMs: Long): Float {
        val localSec = timelineOffsetMs.coerceAtLeast(0L) / 1000f
        return com.moody.moodyvideoeditor.utils.KeyframeStore.sample(
            keyframes,
            KeyframeLibrary.SPEED,
            localSec,
            baseSpeed
        ).coerceIn(MIN_SPEED, MAX_SPEED)
    }

    fun playbackSpeedAt(clip: EditorClip, timelineOffsetMs: Long): Float {
        return playbackSpeedAt(
            clip.keyframes, clip.speed, clip.durationMs, timelineOffsetMs
        )
    }

    fun playbackSpeedAt(
        keyframes: KeyframeMap,
        baseSpeed: Float,
        durationMs: Long,
        timelineOffsetMs: Long
    ): Float {
        val track = keyframes[KeyframeLibrary.SPEED].orEmpty()
        if (track.isEmpty()) return baseSpeed.coerceIn(MIN_SPEED, MAX_SPEED)

        return speedAt(
            keyframes,
            baseSpeed,
            timelineOffsetMs.coerceAtLeast(0L).coerceAtMost(durationMs)
        )
    }

    fun sourcePositionAt(clip: EditorClip, timelineOffsetMs: Long): Long {
        return sourcePositionAt(
            clip.sourceStartMs,
            clip.sourceDurationMs,
            clip.speed,
            clip.durationMs,
            clip.keyframes,
            timelineOffsetMs
        )
    }

    fun timelineOffsetForSourcePosition(clip: EditorClip, sourcePositionMs: Long): Long {
        val target = sourcePositionMs.coerceIn(clip.sourceStartMs, clip.sourceEndMs)
        if (target <= clip.sourceStartMs) return 0L
        if (target >= clip.sourceEndMs) return clip.durationMs

        var low = 0L
        var high = clip.durationMs
        while (low < high) {
            val middle = low + (high - low) / 2L
            if (sourcePositionAt(clip, middle) >= target) high = middle
            else low = middle + 1L
        }
        return low
    }

    fun sourcePositionAt(
        sourceStartMs: Long,
        sourceDurationMs: Long,
        baseSpeed: Float,
        clipDurationMs: Long,
        keyframes: KeyframeMap,
        timelineOffsetMs: Long
    ): Long {
        val sourceEndMs = sourceStartMs + sourceDurationMs
        val track = keyframes[KeyframeLibrary.SPEED].orEmpty()
        val offsetMs = timelineOffsetMs.coerceIn(0L, clipDurationMs)
        if (track.isEmpty()) {
            return (sourceStartMs + offsetMs * baseSpeed)
                .toLong().coerceIn(sourceStartMs, sourceEndMs)
        }

        val targetSec = offsetMs / 1000f
        val elapsedSourceSec = integrateSpeed(keyframes, baseSpeed, targetSec)
        return (sourceStartMs + elapsedSourceSec * 1000f)
            .roundToLong().coerceIn(sourceStartMs, sourceEndMs)
    }

    fun durationMsFor(
        sourceDurationMs: Long,
        baseSpeed: Float,
        keyframes: KeyframeMap
    ): Long {
        val safeSourceMs = sourceDurationMs.coerceAtLeast(EditorClip.MIN_DURATION_MS)
        val safeBaseSpeed = baseSpeed.coerceIn(MIN_SPEED, MAX_SPEED)
        if (keyframes[KeyframeLibrary.SPEED].orEmpty().isEmpty()) {
            return (safeSourceMs / safeBaseSpeed).toLong()
                .coerceAtLeast(EditorClip.MIN_DURATION_MS)
        }

        val sourceDurationSec = safeSourceMs / 1000f
        val frames = keyframes[KeyframeLibrary.SPEED].orEmpty().sortedBy { it.time }
        var cursorSec = 0f
        var sourceProgressSec = 0f

        for (frame in frames) {
            val segmentEndSec = frame.time.coerceAtLeast(cursorSec)
            if (segmentEndSec <= cursorSec) continue
            val segmentDurationSec = segmentEndSec - cursorSec
            val steps = kotlin.math.ceil(segmentDurationSec * 50f).toInt().coerceIn(1, 2000)
            val stepSec = segmentDurationSec / steps
            for (step in 0 until steps) {
                val midpointSec = cursorSec + (step + 0.5f) * stepSec
                val rate = speedAt(keyframes, safeBaseSpeed, (midpointSec * 1000f).toLong())
                val progress = rate * stepSec
                if (sourceProgressSec + progress >= sourceDurationSec) {
                    val remainingSec = sourceDurationSec - sourceProgressSec
                    return ((cursorSec + step * stepSec + remainingSec / rate) * 1000f)
                        .toLong().coerceAtLeast(EditorClip.MIN_DURATION_MS)
                }
                sourceProgressSec += progress
            }
            cursorSec = segmentEndSec
        }

        val tailSpeed = speedAt(keyframes, safeBaseSpeed, (cursorSec * 1000f).toLong())
            .coerceAtLeast(MIN_SPEED)
        val remainingSec = (sourceDurationSec - sourceProgressSec).coerceAtLeast(0f)
        return ((cursorSec + remainingSec / tailSpeed) * 1000f)
            .toLong().coerceAtLeast(EditorClip.MIN_DURATION_MS)
    }

    private fun integrateSpeed(
        keyframes: KeyframeMap,
        baseSpeed: Float,
        durationSec: Float
    ): Float {
        if (durationSec <= 0f) return baseSpeed
        val steps = kotlin.math.ceil(durationSec * 50f).toInt().coerceIn(1, 2000)
        val stepSec = durationSec / steps
        var total = 0f
        for (index in 0 until steps) {
            val midpointSec = (index + 0.5f) * stepSec
            total += speedAt(keyframes, baseSpeed, (midpointSec * 1000f).toLong()) * stepSec
        }
        return total
    }
}
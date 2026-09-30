package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.EditorClip
import java.util.UUID

/**
 * Mirrors js/features/duplicate.js — updated for playhead-based duplication
 * with stack placement when overlap exists.
 */
object DuplicateEngine {

    /**
     * 🆕 Duplicate clip at playhead position.
     *
     * Behavior:
     * 1. Copy placed at playhead timeline position
     * 2. If overlap with existing clip on same track → move to nearest empty track ABOVE
     * 3. If no free track exists → create new top track
     */
    fun duplicateAt(
        source: EditorClip,
        allClips: List<EditorClip>,
        playheadMs: Long
    ): EditorClip {
        val durMs = source.durationMs

        // Find empty track at playhead
        val targetTrack = findFreeTrackForDuplicate(
            allClips = allClips,
            sourceTrack = source.trackIndex,
            isAudio = source.isAudio,
            startMs = playheadMs,
            endMs = playheadMs + durMs
        )

        return source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name} copy",
            timelineStartMs = playheadMs,
            trackIndex = targetTrack,
            linkedId = null
        )
    }

    /**
     * Find a track where duplicate can fit at given time range.
     * Priority: same track if empty, else next track above, else new top track.
     */
    private fun findFreeTrackForDuplicate(
        allClips: List<EditorClip>,
        sourceTrack: Int,
        isAudio: Boolean,
        startMs: Long,
        endMs: Long
    ): Int {
        val sameTypeClips = allClips.filter { it.isAudio == isAudio }

        // Max existing track index for this type
        val maxTrack = sameTypeClips.maxOfOrNull { it.trackIndex } ?: -1

        // Try tracks starting from sourceTrack, going up
        for (t in sourceTrack..(maxTrack + 1)) {
            val hasOverlap = sameTypeClips.any { c ->
                c.trackIndex == t &&
                        c.timelineStartMs < endMs &&
                        startMs < c.timelineEndMs
            }
            if (!hasOverlap) return t
        }

        // Fallback: new top track
        return maxTrack + 1
    }

    /**
     * Legacy method — kept for compatibility with old call sites.
     */
    fun duplicateAfter(clip: EditorClip, allClips: List<EditorClip>): EditorClip {
        val sameTrack = allClips.filter {
            it.trackIndex == clip.trackIndex && it.isAudio == clip.isAudio
        }
        val endTime = sameTrack.maxOfOrNull { it.timelineEndMs } ?: clip.timelineEndMs
        return clip.copy(
            id = UUID.randomUUID().toString(),
            name = "${clip.name} copy",
            timelineStartMs = endTime,
            linkedId = null
        )
    }
}
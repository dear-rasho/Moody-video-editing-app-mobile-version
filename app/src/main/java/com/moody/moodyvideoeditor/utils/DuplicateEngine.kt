package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.EditorClip
import java.util.UUID

/**
 * Mirrors js/features/duplicate.js — updated for playhead-based duplication
 * with stack placement when overlap exists.
 *
 * 🆕 Now duplicates BOTH video + linked audio partner.
 */
object DuplicateEngine {

    /**
     * 🆕 Duplicate clip at playhead position WITH its linked partner.
     *
     * Returns: list of new clips (primary + optional linked partner).
     */
    fun duplicateAt(
        source: EditorClip,
        allClips: List<EditorClip>,
        playheadMs: Long
    ): List<EditorClip> {
        val durMs = source.durationMs
        val newLinkId = "lk-${System.currentTimeMillis()}-${(1000..9999).random()}"

        val result = mutableListOf<EditorClip>()

        // ─── Find linked partner (video ↔ audio) ───
        val linkedPartner = source.linkedId?.let { lid ->
            allClips.firstOrNull { it.linkedId == lid && it.id != source.id }
        }

        // ─── Place PRIMARY copy ───
        val primaryTrack = findFreeTrackForDuplicate(
            allClips = allClips,
            sourceTrack = source.trackIndex,
            isAudio = source.isAudio,
            startMs = playheadMs,
            endMs = playheadMs + durMs
        )

        val primaryCopy = source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name} copy",
            timelineStartMs = playheadMs,
            trackIndex = primaryTrack,
            linkedId = if (linkedPartner != null) newLinkId else null
        )
        result.add(primaryCopy)

        // ─── Place LINKED PARTNER copy (if exists) ───
        if (linkedPartner != null) {
            val linkedTrack = findFreeTrackForDuplicate(
                allClips = allClips,
                sourceTrack = linkedPartner.trackIndex,
                isAudio = linkedPartner.isAudio,
                startMs = playheadMs,
                endMs = playheadMs + durMs
            )

            val linkedCopy = linkedPartner.copy(
                id = UUID.randomUUID().toString(),
                name = "${linkedPartner.name} copy",
                timelineStartMs = playheadMs,
                trackIndex = linkedTrack,
                linkedId = newLinkId
            )
            result.add(linkedCopy)
        }

        return result
    }

    private fun findFreeTrackForDuplicate(
        allClips: List<EditorClip>,
        sourceTrack: Int,
        isAudio: Boolean,
        startMs: Long,
        endMs: Long
    ): Int {
        val sameTypeClips = allClips.filter { it.isAudio == isAudio }
        val maxTrack = sameTypeClips.maxOfOrNull { it.trackIndex } ?: -1

        for (t in sourceTrack..(maxTrack + 1)) {
            val hasOverlap = sameTypeClips.any { c ->
                c.trackIndex == t &&
                        c.timelineStartMs < endMs &&
                        startMs < c.timelineEndMs
            }
            if (!hasOverlap) return t
        }
        return maxTrack + 1
    }

    /**
     * Legacy method — kept for compatibility.
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
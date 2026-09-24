package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.EditorClip
import java.util.UUID

/**
 * Mirrors js/features/duplicate.js
 */
object DuplicateEngine {

    fun duplicateAfter(clip: EditorClip, allClips: List<EditorClip>): EditorClip {
        // Find latest end on same track
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
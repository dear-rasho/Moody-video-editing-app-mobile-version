package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.EditorClip

object TimelineEngine {

    fun rangesOverlap(aS: Long, aE: Long, bS: Long, bE: Long): Boolean {
        return aS < bE && bS < aE
    }

    fun trackHasOverlap(
        clips: List<EditorClip>,
        trackIndex: Int,
        isAudio: Boolean,
        startMs: Long,
        endMs: Long,
        excludeClipId: String? = null
    ): Boolean {
        val track = clips.filter { it.trackIndex == trackIndex && it.isAudio == isAudio }
        for (clip in track) {
            if (clip.id == excludeClipId) continue
            if (rangesOverlap(startMs, endMs, clip.timelineStartMs, clip.timelineEndMs)) {
                return true
            }
        }
        return false
    }

    fun recalcTrackTimings(
        clips: MutableList<EditorClip>,
        trackIndex: Int,
        isAudio: Boolean
    ): MutableList<EditorClip> {
        val trackClips = clips
            .filter { it.trackIndex == trackIndex && it.isAudio == isAudio }
            .sortedBy { it.timelineStartMs }
        var cursor = 0L
        trackClips.forEach { clip ->
            val idx = clips.indexOfFirst { it.id == clip.id }
            if (idx >= 0) {
                clips[idx] = clips[idx].copy(timelineStartMs = cursor)
                cursor += clips[idx].durationMs
            }
        }
        return clips
    }
}
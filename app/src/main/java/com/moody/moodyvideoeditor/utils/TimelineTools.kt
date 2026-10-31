package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.EditorClip

object TimelineTools {

    fun closeGapsOnTrack(track: List<EditorClip>): List<EditorClip> {
        if (track.size < 2) return track

        val sorted = track.sortedBy { it.timelineStartMs }
        val result = sorted.toMutableList()
        var cursor = sorted.first().timelineEndMs

        for (i in 1 until result.size) {
            val clip = result[i]
            if (clip.timelineStartMs > cursor) {
                result[i] = clip.copy(timelineStartMs = cursor)
            }
            cursor = maxOf(cursor, result[i].timelineEndMs)
        }
        return result
    }

    // MAGNET — pack clips after the selected clip on the same track.

    fun closeGapsAfterClip(
        track: List<EditorClip>,
        anchorClipId: String
    ): List<EditorClip> {
        if (track.size < 2) return track
        val sorted = track.sortedBy { it.timelineStartMs }
        val anchorIdx = sorted.indexOfFirst { it.id == anchorClipId }
        if (anchorIdx < 0 || anchorIdx == sorted.lastIndex) return track

        val result = sorted.toMutableList()
        var cursor = result[anchorIdx].timelineEndMs
        for (i in anchorIdx + 1 until result.size) {
            val clip = result[i]
            val startMs = maxOf(clip.timelineStartMs, cursor)
            result[i] = clip.copy(timelineStartMs = startMs)
            cursor = startMs + clip.durationMs
        }
        return result
    }

    fun closeGapToNextClipAcrossVisualTracks(
        clips: List<EditorClip>,
        anchorClip: EditorClip
    ): List<EditorClip> {
        if (anchorClip.isAudio) return clips

        val hasLaterClipOnAnchorTrack = clips.any {
            !it.isAudio &&
                    it.trackIndex == anchorClip.trackIndex &&
                    it.id != anchorClip.id &&
                    it.timelineStartMs >= anchorClip.timelineEndMs
        }
        if (hasLaterClipOnAnchorTrack) return clips

        val nextClip = clips.asSequence()
            .filter {
                !it.isAudio &&
                        it.id != anchorClip.id &&
                        it.timelineStartMs > anchorClip.timelineEndMs
            }
            .minWithOrNull(compareBy<EditorClip>({ it.timelineStartMs }, { it.trackIndex }))
            ?: return clips

        val movedNextClip = nextClip.copy(timelineStartMs = anchorClip.timelineEndMs)
        val trackAfterMove = clips
            .filter { !it.isAudio && it.trackIndex == nextClip.trackIndex }
            .map { if (it.id == nextClip.id) movedNextClip else it }
        val repackedTrack = closeGapsAfterClip(trackAfterMove, nextClip.id)
        val newStarts = repackedTrack.associate { it.id to it.timelineStartMs }
        return clips.map { clip ->
            newStarts[clip.id]?.let { startMs ->
                if (startMs != clip.timelineStartMs) clip.copy(timelineStartMs = startMs)
                else clip
            } ?: clip
        }
    }


    fun selectForwardOnLayer(
        track: List<EditorClip>,
        anchor: EditorClip
    ): Set<String> {
        val sorted = track.sortedBy { it.timelineStartMs }
        val idx = sorted.indexOfFirst { it.id == anchor.id }
        if (idx < 0) return setOf(anchor.id)
        return sorted.subList(idx, sorted.size).map { it.id }.toSet()
    }

    fun selectBackwardOnLayer(
        track: List<EditorClip>,
        anchor: EditorClip
    ): Set<String> {
        val sorted = track.sortedBy { it.timelineStartMs }
        val idx = sorted.indexOfFirst { it.id == anchor.id }
        if (idx < 0) return setOf(anchor.id)
        return sorted.subList(0, idx + 1).map { it.id }.toSet()
    }


    //  SMART MEDIA PLACEMENT

    data class Placement(val trackIndex: Int, val createNewLayer: Boolean)

    fun findPlacement(
        visualTracks: List<List<EditorClip>>,
        visualLayerCount: Int,
        playheadMs: Long,
        durMs: Long
    ): Placement {
        val endMs = playheadMs + durMs

        // Rule 1: first layer completely empty → use it
        val v1 = visualTracks.getOrNull(0).orEmpty()
        if (v1.isEmpty()) return Placement(0, false)

        // Rule 2: first layer with no overlap in range
        for (t in 0 until visualLayerCount) {
            val track = visualTracks.getOrNull(t).orEmpty()
            val overlap = track.any { clip ->
                clip.timelineStartMs < endMs && playheadMs < clip.timelineEndMs
            }
            if (!overlap) return Placement(t, false)
        }

        // Rule 3: all layers overlapped → new top layer
        return Placement(visualLayerCount, true)
    }
}
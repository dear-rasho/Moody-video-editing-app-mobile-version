package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.EditorClip

object TimelineTools {

    // ═══════════════════════════════════════════════════════════
    //  MAGNET — close gaps from playhead onwards (SAME layer)
    // ═══════════════════════════════════════════════════════════
    fun closeGapsFromPlayhead(
        track: List<EditorClip>,
        playheadMs: Long
    ): List<EditorClip> {
        if (track.size < 2) return track
        val sorted = track.sortedBy { it.timelineStartMs }

        var chainStartIdx = -1
        for (i in sorted.indices) {
            if (sorted[i].timelineEndMs >= playheadMs) {
                chainStartIdx = i
                break
            }
        }
        if (chainStartIdx < 0) return track

        val result = sorted.toMutableList()
        var cursor = result[chainStartIdx].timelineStartMs
        for (i in chainStartIdx until result.size) {
            result[i] = result[i].copy(timelineStartMs = cursor)
            cursor += result[i].durationMs
        }
        return result
    }

    // ═══════════════════════════════════════════════════════════
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

    // ═══════════════════════════════════════════════════════════
    //  SMART MEDIA PLACEMENT
    // ═══════════════════════════════════════════════════════════
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
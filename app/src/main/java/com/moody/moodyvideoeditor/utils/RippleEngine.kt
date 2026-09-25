package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.EditorClip

/**
 * Mirrors js/workspace/timeline.js ripple insert logic.
 *
 * Jab clip ko nayi jagah daala jaye aur wahan overlap ho,
 * to us track ke aage wale clips ko shift karo (right me).
 */
object RippleEngine {

    /**
     * Ek track ke clips ko reflow karo jab ek naya clip daalna hai.
     *
     * @param trackClips us track ke saare clips (moved clip ke alawa)
     * @param movingClip jo clip move ho raha hai (target position ke saath)
     * @return updated clips list us track ke liye
     */
    fun reflowTrack(
        trackClips: List<EditorClip>,
        movingClip: EditorClip
    ): List<EditorClip> {
        val targetStart = movingClip.timelineStartMs
        val targetEnd = targetStart + movingClip.durationMs

        // sort by start time
        val sorted = trackClips.sortedBy { it.timelineStartMs }.toMutableList()

        // 1) un clips ko dhundho jo target range ke saath overlap karte hain
        val overlapping = sorted.filter { c ->
            c.id != movingClip.id &&
                    c.timelineStartMs < targetEnd &&
                    targetStart < c.timelineEndMs
        }

        if (overlapping.isEmpty()) {
            // no overlap → bas moving clip add karo, reflow nahi
            return sorted + movingClip
        }

        // 2) overlapping clips ko target end ke baad shift karo
        //    chain: pehla shift → uske baad wala bhi shift ho
        var cursor = targetEnd
        val result = mutableListOf<EditorClip>()

        sorted.forEach { c ->
            when {
                c.id == movingClip.id -> {
                    // skip — last me add karenge
                }

                c.timelineEndMs <= targetStart -> {
                    // target se pehle koi overlap nahi → as-is
                    result.add(c)
                    cursor = maxOf(cursor, c.timelineEndMs)
                }

                else -> {
                    // shift this clip to cursor
                    val shifted = c.copy(timelineStartMs = cursor)
                    result.add(shifted)
                    cursor += c.durationMs
                }
            }
        }

        // 3) moving clip insert karo correct position pe
        result.add(movingClip.copy(timelineStartMs = targetStart))
        return result.sortedBy { it.timelineStartMs }
    }

    /**
     * Check karta hai ki given track pe given range fit hoga ya nahi
     * (ripple ke bina, sirf check).
     */
    fun hasOverlap(
        trackClips: List<EditorClip>,
        excludeClipId: String?,
        startMs: Long,
        endMs: Long
    ): Boolean {
        return trackClips.any { c ->
            c.id != excludeClipId &&
                    c.timelineStartMs < endMs &&
                    startMs < c.timelineEndMs
        }
    }

    /**
     * Kya shift ke baad bhi clips max duration se aage jayenge?
     * (Ye check karta hai ki nayi layer ki zaroorat hai ya nahi.)
     */
    fun needsNewLayer(
        shiftedTrackClips: List<EditorClip>,
        maxAllowedMs: Long = Long.MAX_VALUE
    ): Boolean {
        return shiftedTrackClips.any { it.timelineEndMs > maxAllowedMs }
    }
}
package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.EditorClip
import kotlin.math.abs

/**
 * Mirrors js/workspace/trimHandles.js
 * Snap targets + hysteresis logic for trim handles.
 */
data class SnapTarget(
    val timeMs: Long,
    val type: SnapType,
    val clipId: String? = null,
    val clipName: String? = null
)

enum class SnapType { START, END, PLAYHEAD }

object TimelineSnapEngine {

    const val SNAP_ENTER_PX = 14f
    const val SNAP_RELEASE_PX = 28f

    /**
     * Mirrors JS getSnapTargets()
     */
    fun buildTargets(
        clips: List<EditorClip>,
        excludeClipId: String?,
        playheadMs: Long
    ): List<SnapTarget> {
        val targets = mutableListOf<SnapTarget>()
        clips.forEach { clip ->
            if (clip.id == excludeClipId) return@forEach
            targets.add(SnapTarget(clip.timelineStartMs, SnapType.START, clip.id, clip.name))
            targets.add(SnapTarget(clip.timelineEndMs, SnapType.END, clip.id, clip.name))
        }
        targets.add(SnapTarget(playheadMs, SnapType.PLAYHEAD))
        return targets
    }

    /**
     * Mirrors JS trySnap()
     * Hysteresis: if activeSnap, stay until releaseMs exceeded.
     */
    fun trySnap(
        rawTimeMs: Long,
        targets: List<SnapTarget>,
        enterThresholdMs: Long,
        releaseThresholdMs: Long,
        activeSnap: SnapTarget?
    ): SnapTarget? {
        if (activeSnap != null) {
            val dist = abs(rawTimeMs - activeSnap.timeMs)
            if (dist <= releaseThresholdMs) return activeSnap
        }
        var best: SnapTarget? = null
        var bestDist = enterThresholdMs
        targets.forEach { t ->
            val d = abs(t.timeMs - rawTimeMs)
            if (d < bestDist) {
                bestDist = d
                best = t
            }
        }
        return best
    }

    fun labelFor(target: SnapTarget): String = when (target.type) {
        SnapType.PLAYHEAD -> "🔗 Snap: Playhead"
        SnapType.START -> "🔗 Start of ${target.clipName?.take(18) ?: "Layer"}"
        SnapType.END -> "🔗 End of ${target.clipName?.take(18) ?: "Layer"}"
    }

    /**
     * Convert px threshold → ms based on timeline width.
     */
    fun pxToMs(px: Float, totalMs: Long, widthPx: Int): Long {
        if (widthPx <= 0 || totalMs <= 0) return 0
        val msPerPx = totalMs.toFloat() / widthPx.toFloat()
        return (px * msPerPx).toLong()
    }
}
package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.TransitionLibrary
import com.moody.moodyvideoeditor.data.TransitionState
import kotlin.math.ceil
import kotlin.math.min

data class TransitionExportStep(
    val durationMs: Long,
    val offsetMs: Long,
    val ffmpegTransition: String
)

object TransitionExportPlan {
    fun create(
        clipDurationsMs: List<Long>,
        transitions: List<TransitionState?>,
        fps: Int
    ): List<TransitionExportStep> {
        if (clipDurationsMs.size < 2 || fps <= 0) return emptyList()

        val frameMs = ceil(1000.0 / fps).toLong().coerceAtLeast(1L)
        var cumulativeMs = clipDurationsMs.first().coerceAtLeast(frameMs)

        return (1 until clipDurationsMs.size).map { index ->
            val previousDuration = clipDurationsMs[index - 1].coerceAtLeast(frameMs)
            val currentDuration = clipDurationsMs[index].coerceAtLeast(frameMs)
            val state = transitions.getOrNull(index)
            val preset = state
                ?.takeIf { it.isActive }
                ?.let { TransitionLibrary.find(it.key) }
            val isActive = state?.isActive == true
            val requestedDuration = if (isActive) {
                state!!.durationMs.coerceIn(200L, 3000L)
            } else {
                frameMs
            }
            val maxDuration = (min(previousDuration, currentDuration) - frameMs)
                .coerceAtLeast(frameMs)
            val durationMs = requestedDuration.coerceAtMost(maxDuration)
            val offsetMs = (cumulativeMs - durationMs).coerceAtLeast(0L)

            cumulativeMs += currentDuration - durationMs
            TransitionExportStep(
                durationMs = durationMs,
                offsetMs = offsetMs,
                ffmpegTransition = preset?.ffmpegXfade
                    ?.takeIf { it.isNotBlank() } ?: "fade"
            )
        }
    }
}

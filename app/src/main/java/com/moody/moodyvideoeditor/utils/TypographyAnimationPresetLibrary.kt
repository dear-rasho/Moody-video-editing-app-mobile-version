package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.data.KeyframeLibrary
import com.moody.moodyvideoeditor.data.KeyframeMap

data class TypographyAnimationPoint(
    val progress: Float,
    val value: Float
)

data class TypographyAnimationPreset(
    val id: String,
    val name: String,
    val description: String,
    val sizeScale: List<TypographyAnimationPoint>? = null,
    val letterSpacing: List<TypographyAnimationPoint>? = null
)

object TypographyAnimationPresetLibrary {
    val presets = listOf(
        TypographyAnimationPreset(
            id = "spacing-unfold",
            name = "Spacing Unfold",
            description = "Wide letter spacing closes into a clean title",
            sizeScale = listOf(
                TypographyAnimationPoint(0f, 0.9f),
                TypographyAnimationPoint(0.35f, 1.08f),
                TypographyAnimationPoint(1f, 1f)
            ),
            letterSpacing = listOf(
                TypographyAnimationPoint(0f, 300f),
                TypographyAnimationPoint(0.45f, 0f),
                TypographyAnimationPoint(1f, 0f)
            )
        ),
        TypographyAnimationPreset(
            id = "spacing-tighten",
            name = "Spacing Tighten",
            description = "Compressed letters spring back to normal",
            sizeScale = listOf(
                TypographyAnimationPoint(0f, 1.15f),
                TypographyAnimationPoint(0.5f, 1f),
                TypographyAnimationPoint(1f, 1f)
            ),
            letterSpacing = listOf(
                TypographyAnimationPoint(0f, -200f),
                TypographyAnimationPoint(0.55f, 0f),
                TypographyAnimationPoint(1f, 0f)
            )
        ),
        TypographyAnimationPreset(
            id = "size-pop",
            name = "Size Pop",
            description = "Text pops up from invisible to full size",
            sizeScale = listOf(
                TypographyAnimationPoint(0f, 0f),
                TypographyAnimationPoint(0.32f, 1.3f),
                TypographyAnimationPoint(0.62f, 0.94f),
                TypographyAnimationPoint(1f, 1f)
            )
        ),
        TypographyAnimationPreset(
            id = "size-grow",
            name = "Size Grow",
            description = "A smooth size reveal with a gentle overshoot",
            sizeScale = listOf(
                TypographyAnimationPoint(0f, 0f),
                TypographyAnimationPoint(0.7f, 1.12f),
                TypographyAnimationPoint(1f, 1f)
            )
        ),
        TypographyAnimationPreset(
            id = "tracking-wave",
            name = "Tracking Wave",
            description = "Spacing and size pulse before settling",
            sizeScale = listOf(
                TypographyAnimationPoint(0f, 0.9f),
                TypographyAnimationPoint(0.35f, 1.12f),
                TypographyAnimationPoint(0.7f, 0.96f),
                TypographyAnimationPoint(1f, 1f)
            ),
            letterSpacing = listOf(
                TypographyAnimationPoint(0f, -80f),
                TypographyAnimationPoint(0.35f, 180f),
                TypographyAnimationPoint(0.7f, -40f),
                TypographyAnimationPoint(1f, 0f)
            )
        )
    )

    fun find(id: String): TypographyAnimationPreset? = presets.firstOrNull { it.id == id }

    fun keyframesFor(
        preset: TypographyAnimationPreset,
        baseSize: Int,
        baseLetterSpacing: Float,
        durationSec: Float
    ): KeyframeMap {
        val duration = durationSec.coerceAtLeast(0.1f)
        val tracks = mutableMapOf<String, List<Keyframe>>()
        preset.sizeScale?.let { points ->
            tracks[KeyframeLibrary.TEXT_SIZE] = points.map { point ->
                Keyframe(
                    time = point.progress.coerceIn(0f, 1f) * duration,
                    value = if (point.value == 0f) 0f
                    else (baseSize * point.value).coerceIn(0f, 200f)
                )
            }
        }
        preset.letterSpacing?.let { points ->
            tracks[KeyframeLibrary.TEXT_LETTER_SPACING] = points.map { point ->
                Keyframe(
                    time = point.progress.coerceIn(0f, 1f) * duration,
                    value = point.value.coerceIn(-200f, 300f)
                )
            }
        }
        if (preset.sizeScale == null) {
            tracks[KeyframeLibrary.TEXT_SIZE] = listOf(
                Keyframe(0f, baseSize.toFloat().coerceIn(0f, 200f)),
                Keyframe(duration, baseSize.toFloat().coerceIn(0f, 200f))
            )
        }
        if (preset.letterSpacing == null) {
            tracks[KeyframeLibrary.TEXT_LETTER_SPACING] = listOf(
                Keyframe(0f, baseLetterSpacing.coerceIn(-200f, 300f)),
                Keyframe(duration, baseLetterSpacing.coerceIn(-200f, 300f))
            )
        }
        return tracks
    }

    fun valueAt(points: List<TypographyAnimationPoint>, progress: Float): Float {
        val sorted = points.sortedBy { it.progress }
        if (sorted.isEmpty()) return 0f
        val p = progress.coerceIn(0f, 1f)
        if (p <= sorted.first().progress) return sorted.first().value
        if (p >= sorted.last().progress) return sorted.last().value
        val (left, right) = sorted.zipWithNext().first { (a, b) ->
            p in a.progress..b.progress
        }
        val fraction = (p - left.progress) / (right.progress - left.progress)
        return left.value + (right.value - left.value) * fraction
    }
}

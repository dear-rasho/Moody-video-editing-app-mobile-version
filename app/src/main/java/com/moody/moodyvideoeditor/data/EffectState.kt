package com.moody.moodyvideoeditor.data

/**
 * Mirrors js/workspace/effectLayer.js effectState object.
 * Stored on each effect clip in the timeline.
 *
 * kind:
 *   "effect"      → preset (motion + filters + overlay sab mix)
 *   "filter"      → CSS filters only
 *   "adjustment"  → pixel-level grading
 *   "colorWheel"  → pixel-level HSL tones
 *   "chroma"      → color keying
 */
data class EffectState(
    val kind: String = KIND_EFFECT,
    val presetKey: String? = null,
    val filters: ColorFilterValues? = null,
    val motion: MotionConfig? = null,
    val overlay: OverlayConfig? = null
) {
    companion object {
        const val KIND_EFFECT = "effect"
        const val KIND_FILTER = "filter"
        const val KIND_ADJUSTMENT = "adjustment"
        const val KIND_COLOR_WHEEL = "colorWheel"
        const val KIND_CHROMA = "chroma"
    }
}
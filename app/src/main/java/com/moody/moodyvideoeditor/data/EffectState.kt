package com.moody.moodyvideoeditor.data

data class EffectState(
    val kind: String = KIND_EFFECT,
    val presetKey: String? = null,
    val filters: ColorFilterValues? = null,
    val motion: MotionConfig? = null,
    val overlay: OverlayConfig? = null,
    val masterIntensity: Float = 100f   // 🆕 0-200%
) {
    companion object {
        const val KIND_EFFECT = "effect"
        const val KIND_FILTER = "filter"
        const val KIND_ADJUSTMENT = "adjustment"
        const val KIND_COLOR_WHEEL = "colorWheel"
        const val KIND_CHROMA = "chroma"
    }
}
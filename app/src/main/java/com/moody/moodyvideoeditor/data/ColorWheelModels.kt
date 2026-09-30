package com.moody.moodyvideoeditor.data

/**
 * Mirrors js/features/colorWheel.js DEFAULTS.
 * 3 tone wheels + HDR White.
 */
data class ToneValue(
    val hue: Float = 0f,          // 0..360
    val saturation: Float = 0f,   // 0..100
    val intensity: Float = 0f     // 0..100
) {
    val isActive: Boolean
        get() = intensity > 0f && saturation > 0f

    companion object {
        val ZERO = ToneValue(0f, 0f, 0f)
    }
}

data class ColorWheelState(
    val shadows: ToneValue = ToneValue.ZERO,
    val midtones: ToneValue = ToneValue.ZERO,
    val highlights: ToneValue = ToneValue.ZERO,
    val hdrWhite: Float = 100f    // 0..200
) {
    val isDefault: Boolean
        get() = this == ColorWheelState()

    /** Mirrors JS: check if layer should be created */
    val hasAnyChange: Boolean
        get() = shadows.isActive || midtones.isActive || highlights.isActive || hdrWhite != 100f

    fun toneFor(key: String): ToneValue = when (key) {
        "shadows" -> shadows
        "midtones" -> midtones
        "highlights" -> highlights
        else -> ToneValue.ZERO
    }

    fun setTone(key: String, value: ToneValue): ColorWheelState = when (key) {
        "shadows" -> copy(shadows = value)
        "midtones" -> copy(midtones = value)
        "highlights" -> copy(highlights = value)
        else -> this
    }

    companion object {
        /** Mirrors JS toneKeys + toneLabels */
        val TONE_KEYS = listOf("shadows", "midtones", "highlights")
        val TONE_LABELS = listOf("Shadows", "Midtones", "Highlights")
    }
}
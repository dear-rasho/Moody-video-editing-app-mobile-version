package com.moody.moodyvideoeditor.data

// ═══════════════════════════════════════════════════════════════
//  COLOR MATTE — Data models
// ═══════════════════════════════════════════════════════════════

enum class ColorMatteMode {
    SOLID,          // 1 solid color
    RAMP,           // 2-color linear gradient
    FOUR_COLOR      // 4-corner gradient (TL, TR, BL, BR)
}

data class ColorMatteStyle(
    val mode: ColorMatteMode = ColorMatteMode.SOLID,

    // SOLID mode
    val solidColor: Long = 0xFF000000,

    // RAMP mode
    val rampColor1: Long = 0xFF000000,
    val rampColor2: Long = 0xFFFFFFFF,
    val rampAngleDeg: Float = 90f,       // 0=top→bottom, 90=left→right

    // FOUR_COLOR mode
    val topLeft: Long = 0xFFFF0000,
    val topRight: Long = 0xFF00FF00,
    val bottomLeft: Long = 0xFF0000FF,
    val bottomRight: Long = 0xFFFFCC00
)

object ColorMatteDefaults {
    const val DEFAULT_COLOR: Long = 0xFF000000
    const val DEFAULT_OPACITY: Float = 100f
    const val DEFAULT_DURATION_MS: Long = 5000L
    const val MIN_DURATION_MS: Long = 500L
    const val MAX_DURATION_MS: Long = 300_000L
}

data class ColorMattePreset(
    val label: String,
    val colorHex: String,
    val colorLong: Long
)

object ColorMatteLibrary {

    val SOLID_PRESETS: List<ColorMattePreset> = listOf(
        ColorMattePreset("Black", "#000000", 0xFF000000L),
        ColorMattePreset("White", "#FFFFFF", 0xFFFFFFFFL),
        ColorMattePreset("Red", "#FF0000", 0xFFFF0000L),
        ColorMattePreset("Green", "#00FF00", 0xFF00FF00L),
        ColorMattePreset("Blue", "#0066FF", 0xFF0066FFL),
        ColorMattePreset("Yellow", "#FFCC00", 0xFFFFCC00L),
        ColorMattePreset("Orange", "#FF6B00", 0xFFFF6B00L),
        ColorMattePreset("Cyan", "#00E5FF", 0xFF00E5FFL),
        ColorMattePreset("Magenta", "#FF00FF", 0xFFFF00FFL),
        ColorMattePreset("Purple", "#7C3AED", 0xFF7C3AEDL),
        ColorMattePreset("Pink", "#FF4F8B", 0xFFFF4F8BL),
        ColorMattePreset("Gray", "#808080", 0xFF808080L)
    )

    // 10 ready-made gradient presets
    data class GradientPreset(
        val label: String,
        val color1: Long,
        val color2: Long
    )

    val RAMP_PRESETS: List<GradientPreset> = listOf(
        GradientPreset("Sunset", 0xFFFF6B00, 0xFFFF0066),
        GradientPreset("Ocean", 0xFF0066FF, 0xFF00E5FF),
        GradientPreset("Purple", 0xFF7C3AED, 0xFFFF00FF),
        GradientPreset("Fire", 0xFFFFCC00, 0xFFFF0000),
        GradientPreset("Forest", 0xFF00FF00, 0xFF006633),
        GradientPreset("Night", 0xFF000000, 0xFF1A1A2E),
        GradientPreset("Sky", 0xFF60EFFF, 0xFF7C3AED),
        GradientPreset("Rose", 0xFFFF4F8B, 0xFFFFCC00),
        GradientPreset("Mono", 0xFF000000, 0xFFFFFFFF),
        GradientPreset("Vaporwave", 0xFFFF00FF, 0xFF00E5FF)
    )

    fun longToHex(colorLong: Long): String {
        val r = ((colorLong shr 16) and 0xFF).toInt()
        val g = ((colorLong shr 8) and 0xFF).toInt()
        val b = (colorLong and 0xFF).toInt()
        return "#%02X%02X%02X".format(r, g, b)
    }
}
package com.moody.moodyvideoeditor.data

import java.util.UUID

enum class BrushType {
    PEN,
    MARKER,
    CHALK,
    NEON,
    GLOW,
    SPRAY
}

data class BrushPoint(
    val x: Float,
    val y: Float
)

// 🆕 Gradient ramp configuration for a brush stroke.
// Color interpolates along the stroke path.
data class BrushGradient(
    val enabled: Boolean = false,
    val color1: Long = 0xFFFF0000,   // start color
    val color2: Long = 0xFF00FF00,   // end color
    val color3: Long = 0x00000000,   // optional mid color (if hasMid=true)
    val hasMid: Boolean = false,
    val mode: String = "linear"      // "linear" | "reverse"
)

data class BrushStroke(
    val id: String = UUID.randomUUID().toString(),
    val type: BrushType = BrushType.PEN,
    val color: Long = 0xFFFF0000,
    val width: Float = 20f,
    val opacity: Float = 1f,
    val points: List<BrushPoint> = emptyList(),
    val startMs: Long = 0L,
    val endMs: Long = Long.MAX_VALUE,

    // 🆕 Color ramping
    val gradient: BrushGradient = BrushGradient()
)

data class BrushState(
    val strokes: List<BrushStroke> = emptyList()
) {
    val isEmpty: Boolean get() = strokes.isEmpty()
}

object BrushLibrary {

    data class Preset(
        val type: BrushType,
        val label: String,
        val icon: String,
        val defaultWidth: Float,
        val defaultOpacity: Float
    )

    val PRESETS: List<Preset> = listOf(
        Preset(BrushType.PEN, "Pen", "✒️", 8f, 1f),
        Preset(BrushType.MARKER, "Marker", "🖍️", 30f, 0.7f),
        Preset(BrushType.CHALK, "Chalk", "🪨", 25f, 0.9f),
        Preset(BrushType.NEON, "Neon", "💡", 20f, 1f),
        Preset(BrushType.GLOW, "Glow", "🌟", 35f, 0.8f),
        Preset(BrushType.SPRAY, "Spray", "💨", 40f, 0.5f)
    )

    val COLOR_PRESETS = listOf(
        0xFFFFFFFF, 0xFFFF0000, 0xFFFF6B00, 0xFFFFCC00,
        0xFF00FF00, 0xFF00E5FF, 0xFF0066FF, 0xFF7C3AED,
        0xFFFF00FF, 0xFF000000
    )

    fun findPreset(type: BrushType): Preset? =
        PRESETS.firstOrNull { it.type == type }
}
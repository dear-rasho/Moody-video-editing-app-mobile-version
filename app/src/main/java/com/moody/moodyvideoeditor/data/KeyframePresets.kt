package com.moody.moodyvideoeditor.data

/**
 * Ready-made keyframe animation patterns.
 * Tap a preset → auto-applies keyframes across the clip duration.
 */
data class PresetKeyframe(
    val prop: String,      // "x", "y", "scale", "rotation", "opacity"
    val timeFrac: Float,   // 0.0 = start, 1.0 = end
    val value: Float,
    val ease: String = "easeInOut"
)

data class TransformPreset(
    val key: String,
    val label: String,
    val icon: String,
    val keyframes: List<PresetKeyframe>
)

object KeyframePresets {

    val PRESETS = listOf(
        TransformPreset(
            "fadeIn", "Fade In", "🌅", listOf(
                PresetKeyframe("scale", 0f, 100f),
                PresetKeyframe("x", 0f, 50f),
                PresetKeyframe("x", 1f, 50f),
                PresetKeyframe("scale", 1f, 100f)
            )
        ),

        TransformPreset(
            "zoomIn", "Zoom In", "🔍", listOf(
                PresetKeyframe("scale", 0f, 100f, "easeOut"),
                PresetKeyframe("scale", 1f, 140f, "easeOut")
            )
        ),

        TransformPreset(
            "zoomOut", "Zoom Out", "🔎", listOf(
                PresetKeyframe("scale", 0f, 140f, "easeIn"),
                PresetKeyframe("scale", 1f, 100f, "easeIn")
            )
        ),

        TransformPreset(
            "slideLeft", "Slide Left", "⬅️", listOf(
                PresetKeyframe("x", 0f, 20f, "easeOutCubic"),
                PresetKeyframe("x", 1f, 50f, "easeOutCubic")
            )
        ),

        TransformPreset(
            "slideRight", "Slide Right", "➡️", listOf(
                PresetKeyframe("x", 0f, 80f, "easeOutCubic"),
                PresetKeyframe("x", 1f, 50f, "easeOutCubic")
            )
        ),

        TransformPreset(
            "slideUp", "Slide Up", "⬆️", listOf(
                PresetKeyframe("y", 0f, 80f, "easeOutCubic"),
                PresetKeyframe("y", 1f, 50f, "easeOutCubic")
            )
        ),

        TransformPreset(
            "pulse", "Pulse", "💓", listOf(
                PresetKeyframe("scale", 0f, 100f),
                PresetKeyframe("scale", 0.5f, 115f),
                PresetKeyframe("scale", 1f, 100f)
            )
        ),

        TransformPreset(
            "spin", "Spin 360°", "🌀", listOf(
                PresetKeyframe("rotation", 0f, 0f, "linear"),
                PresetKeyframe("rotation", 1f, 360f, "linear")
            )
        ),

        TransformPreset(
            "bounceIn", "Bounce In", "🏀", listOf(
                PresetKeyframe("y", 0f, 20f, "easeOutBounce"),
                PresetKeyframe("y", 1f, 50f, "easeOutBounce")
            )
        ),

        TransformPreset(
            "shake", "Shake", "📳", listOf(
                PresetKeyframe("x", 0f, 50f),
                PresetKeyframe("x", 0.25f, 47f),
                PresetKeyframe("x", 0.5f, 53f),
                PresetKeyframe("x", 0.75f, 48f),
                PresetKeyframe("x", 1f, 50f)
            )
        )
    )

    fun findByKey(key: String): TransformPreset? = PRESETS.firstOrNull { it.key == key }
}
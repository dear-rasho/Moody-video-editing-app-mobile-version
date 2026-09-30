package com.moody.moodyvideoeditor.data

enum class MaskType {
    NONE,
    CIRCLE,
    RECTANGLE,
    LINEAR,
    HEART,
    CUSTOM
}

data class MaskPoint(
    val x: Float,
    val y: Float,
    val handleInX: Float = 0f,
    val handleInY: Float = 0f,
    val handleOutX: Float = 0f,
    val handleOutY: Float = 0f,
    val hasHandles: Boolean = false
) {
    val inX: Float get() = x + handleInX
    val inY: Float get() = y + handleInY
    val outX: Float get() = x + handleOutX
    val outY: Float get() = y + handleOutY

    fun lerp(other: MaskPoint, t: Float): MaskPoint = MaskPoint(
        x = x + (other.x - x) * t,
        y = y + (other.y - y) * t,
        handleInX = handleInX + (other.handleInX - handleInX) * t,
        handleInY = handleInY + (other.handleInY - handleInY) * t,
        handleOutX = handleOutX + (other.handleOutX - handleOutX) * t,
        handleOutY = handleOutY + (other.handleOutY - handleOutY) * t,
        hasHandles = if (t < 0.5f) hasHandles else other.hasHandles
    )
}

data class MaskKeyframe(
    val timeMs: Long,
    val centerX: Float,
    val centerY: Float,
    val radius: Float = 0.3f,
    val width: Float = 0.5f,
    val height: Float = 0.5f,
    val rotation: Float = 0f,
    val cornerRadius: Float = 0f,
    val scale: Float = 1f,
    val positionY: Float = 0.5f,
    val feather: Float = 0f,
    val expansion: Float = 0f,
    val opacity: Float = 100f,
    val customPoints: List<MaskPoint> = emptyList(),
    val ease: String = "easeInOut"
)

data class MaskState(
    val type: MaskType = MaskType.NONE,

    // Common
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val rotation: Float = 0f,
    val feather: Float = 0f,               // UNBOUNDED
    val isInverted: Boolean = false,
    val opacity: Float = 100f,

    // Circle
    val radius: Float = 0.3f,

    // Rectangle
    val width: Float = 0.5f,
    val height: Float = 0.5f,
    val cornerRadius: Float = 0f,

    // Linear
    val positionY: Float = 0.5f,

    // Heart
    val scale: Float = 1f,

    // Custom
    val customPoints: List<MaskPoint> = emptyList(),
    val customClosed: Boolean = false,      // 🆕 default = FALSE
    val expansion: Float = 0f,

    // Appearance
    val strokeColor: Long = 0xFF22C55E,
    val overlayColor: Long = 0xFF000000,

    // Animation
    val keyframes: List<MaskKeyframe> = emptyList()
) {
    val isActive: Boolean get() = type != MaskType.NONE

    val hasKeyframes: Boolean get() = keyframes.isNotEmpty()

    /** 🆕 Only true when explicitly closed by user */
    val hasCustomPath: Boolean
        get() = type == MaskType.CUSTOM && customPoints.size >= 3 && customClosed

    /** Has at least 3 points but user hasn't closed yet */
    val isReadyToClose: Boolean
        get() = type == MaskType.CUSTOM && customPoints.size >= 3 && !customClosed

    fun currentRadius(): Float = radius
    fun currentSize(): Pair<Float, Float> = width to height
}

object MaskLibrary {

    data class Preset(
        val type: MaskType,
        val label: String,
        val icon: String
    )

    val PRESETS: List<Preset> = listOf(
        Preset(MaskType.NONE, "None", "∅"),
        Preset(MaskType.CIRCLE, "Circle", "⭕"),
        Preset(MaskType.RECTANGLE, "Rectangle", "▭"),
        Preset(MaskType.LINEAR, "Linear", "／"),
        Preset(MaskType.HEART, "Heart", "❤"),
        Preset(MaskType.CUSTOM, "Custom", "✏️")
    )

    fun defaultFor(type: MaskType): MaskState = when (type) {
        MaskType.NONE -> MaskState(type = MaskType.NONE)
        MaskType.CIRCLE -> MaskState(type = MaskType.CIRCLE, radius = 0.3f)
        MaskType.RECTANGLE -> MaskState(
            type = MaskType.RECTANGLE,
            width = 0.6f, height = 0.5f, cornerRadius = 0.1f
        )

        MaskType.LINEAR -> MaskState(type = MaskType.LINEAR, positionY = 0.5f)
        MaskType.HEART -> MaskState(type = MaskType.HEART, scale = 1f)
        MaskType.CUSTOM -> MaskState(
            type = MaskType.CUSTOM,
            customClosed = false  // 🆕 start unclosed
        )
    }
}
package com.moody.moodyvideoeditor.data

// ═══════════════════════════════════════════════════════════════
//  MASK TYPES
// ═══════════════════════════════════════════════════════════════

enum class MaskType {
    NONE,
    CIRCLE,
    RECTANGLE,
    LINEAR,
    HEART,
    CUSTOM
}

// ═══════════════════════════════════════════════════════════════
//  MASK POINT
//  Normalized (0..1) point with optional bezier handles.
//  inX/inY/outX/outY are computed absolute positions.
// ═══════════════════════════════════════════════════════════════

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

// ═══════════════════════════════════════════════════════════════
//  LEGACY SNAPSHOT KEYFRAME
//  Kept for backward compat with old saved projects.
//  New code uses MaskState.propertyKeyframes.
// ═══════════════════════════════════════════════════════════════

data class MaskKeyframe(
    val timeMs: Long,
    val positionX: Float,
    val positionY: Float,
    val radius: Float = 0.3f,
    val width: Float = 0.5f,
    val height: Float = 0.5f,
    val rotation: Float = 0f,
    val cornerRadius: Float = 0f,
    val scale: Float = 1f,
    val linearPosition: Float = 0.5f,
    val feather: Float = 0f,
    val expansion: Float = 0f,
    val opacity: Float = 100f,
    val customPoints: List<MaskPoint> = emptyList(),
    val ease: String = "easeInOut"
) {
    init {
        require(cornerRadius.isFinite() && cornerRadius >= 0f)
    }
}

// ═══════════════════════════════════════════════════════════════
//  MASK STATE
//  Full mask configuration with per-property keyframes.
// ═══════════════════════════════════════════════════════════════

data class MaskState(
    val type: MaskType = MaskType.NONE,

    // ─── Common ───
    val positionX: Float = 0.5f,
    val positionY: Float = 0.5f,
    val rotation: Float = 0f,
    val feather: Float = 0f,
    val isInverted: Boolean = false,
    val opacity: Float = 100f,

    // ─── Circle only ───
    val radius: Float = 0.3f,

    // ─── Rectangle only ───
    val width: Float = 0.5f,
    val height: Float = 0.5f,
    // Corner radius is non-negative; zero keeps sharp corners.
    val cornerRadius: Float = 0f,
    // ─── Linear only ───
    val linearPosition: Float = 0.5f,

    // ─── Heart only ───
    val scale: Float = 1f,

    // ─── Custom only ───
    val customPoints: List<MaskPoint> = emptyList(),
    val customClosed: Boolean = false,
    val expansion: Float = 0f,

    // ─── Appearance (editor overlay only) ───
    val strokeColor: Long = 0xFF22C55E,
    val overlayColor: Long = 0xFF000000,

    // ─── Per-property keyframes ───
    // Each property animates independently.
    // Keys: "positionX", "positionY", "rotation", "feather", "opacity",
    //       "radius", "width", "height", "cornerRadius",
    //       "linearPosition", "scale", "expansion"
    val propertyKeyframes: Map<String, List<Keyframe>> = emptyMap(),

    // ─── Legacy snapshot keyframes ───
    // Kept for backward compat. New saves use propertyKeyframes.
    val keyframes: List<MaskKeyframe> = emptyList()
) {
    init {
        require(cornerRadius.isFinite() && cornerRadius >= 0f)
        require(feather.isFinite() && feather in 0f..300f)
        require(propertyKeyframes["cornerRadius"].orEmpty().all {
            it.value.isFinite() && it.value >= 0f
        })
        require(propertyKeyframes["feather"].orEmpty().all {
            it.value.isFinite() && it.value in 0f..300f
        })
    }

    // ─── Computed properties ───

    val isActive: Boolean
        get() = type != MaskType.NONE

    val hasKeyframes: Boolean
        get() = propertyKeyframes.values.any { it.isNotEmpty() } ||
                keyframes.isNotEmpty()

    val hasPropertyKeyframes: Boolean
        get() = propertyKeyframes.values.any { it.isNotEmpty() }

    val hasCustomPath: Boolean
        get() = type == MaskType.CUSTOM &&
                customPoints.size >= 3 &&
                customClosed

    val isReadyToClose: Boolean
        get() = type == MaskType.CUSTOM &&
                customPoints.size >= 3 &&
                !customClosed

    val totalKeyframeCount: Int
        get() = propertyKeyframes.values.sumOf { it.size } +
                keyframes.size

    fun currentRadius(): Float = radius
    fun currentSize(): Pair<Float, Float> = width to height

    // ─── Read a property by name ───

    fun valueOf(prop: String): Float = when (prop) {
        "positionX" -> positionX
        "positionY" -> positionY
        "rotation" -> rotation
        "feather" -> feather
        "opacity" -> opacity
        "radius" -> radius
        "width" -> width
        "height" -> height
        "cornerRadius" -> cornerRadius
        "linearPosition" -> linearPosition
        "scale" -> scale
        "expansion" -> expansion
        else -> 0f
    }

    // ─── Write a property by name ───

    fun withValue(prop: String, v: Float): MaskState = when (prop) {
        "positionX" -> copy(positionX = v)
        "positionY" -> copy(positionY = v)
        "rotation" -> copy(rotation = v)
        "feather" -> copy(feather = v.coerceIn(0f, 300f))
        "opacity" -> copy(opacity = v)
        "radius" -> copy(radius = v)
        "width" -> copy(width = v)
        "height" -> copy(height = v)
        "cornerRadius" -> copy(cornerRadius = v.coerceAtLeast(0f))
        "linearPosition" -> copy(linearPosition = v)
        "scale" -> copy(scale = v)
        "expansion" -> copy(expansion = v)
        else -> this
    }
}

// ═══════════════════════════════════════════════════════════════
//  ANIMATABLE MASK PROPERTIES
//  Master list of properties that support per-property keyframes.
// ═══════════════════════════════════════════════════════════════

object MaskAnimatableProps {

    // All animatable properties across all mask types
    val ALL = listOf(
        "positionX",
        "positionY",
        "rotation",
        "feather",
        "opacity",
        "radius",
        "width",
        "height",
        "cornerRadius",
        "linearPosition",
        "scale",
        "expansion"
    )

    // Which properties are relevant for a given mask type
    fun forType(type: MaskType): List<String> = when (type) {
        MaskType.NONE -> emptyList()

        MaskType.CIRCLE -> listOf(
            "positionX", "positionY", "rotation",
            "feather", "opacity",
            "radius", "expansion"
        )

        MaskType.RECTANGLE -> listOf(
            "positionX", "positionY", "rotation",
            "feather", "opacity",
            "width", "height", "cornerRadius", "expansion"
        )

        MaskType.LINEAR -> listOf(
            "positionX", "positionY", "rotation",
            "feather", "opacity",
            "linearPosition", "expansion"
        )

        MaskType.HEART -> listOf(
            "positionX", "positionY", "rotation",
            "feather", "opacity",
            "scale", "expansion"
        )

        MaskType.CUSTOM -> listOf(
            "positionX", "positionY", "rotation",
            "feather", "opacity",
            "cornerRadius", "expansion"
        )
    }

    // Human-readable label for each property
    fun labelFor(prop: String): String = when (prop) {
        "positionX" -> "Position X"
        "positionY" -> "Position Y"
        "rotation" -> "Rotation"
        "feather" -> "Feather"
        "opacity" -> "Opacity"
        "radius" -> "Radius"
        "width" -> "Width"
        "height" -> "Height"
        "cornerRadius" -> "Corner"
        "linearPosition" -> "Line Position"
        "scale" -> "Scale"
        "expansion" -> "Expand"
        else -> prop
    }

    // Sensible min/max for each property
    fun rangeFor(prop: String): ClosedFloatingPointRange<Float> = when (prop) {
        "positionX", "positionY" -> -1_000_000f..1_000_000f
        "rotation" -> -1800f..1800f
        "feather" -> 0f..300f
        "opacity" -> 0f..1000f
        "radius" -> 0.01f..20f
        "width", "height" -> 0.01f..20f
        "cornerRadius" -> 0f..5f
        "linearPosition" -> 0f..10f
        "scale" -> 0.3f..25f
        "expansion" -> -2000f..2000f
        else -> 0f..1f
    }

    // Suggested decimal places for display
    fun decimalsFor(prop: String): Int = when (prop) {
        "positionX", "positionY", "linearPosition" -> 2
        "rotation" -> 1
        "feather" -> 0
        "opacity" -> 0
        "radius" -> 2
        "width", "height" -> 2
        "cornerRadius" -> 3
        "positionY" -> 2
        "scale" -> 2
        "expansion" -> 0
        else -> 2
    }
}

// ═══════════════════════════════════════════════════════════════
//  MASK LIBRARY — presets + factory
// ═══════════════════════════════════════════════════════════════

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

        MaskType.CIRCLE -> MaskState(
            type = MaskType.CIRCLE,
            radius = 0.3f
        )

        MaskType.RECTANGLE -> MaskState(
            type = MaskType.RECTANGLE,
            width = 0.6f,
            height = 0.5f,
            cornerRadius = 0.1f
        )

        MaskType.LINEAR -> MaskState(type = MaskType.LINEAR)

        MaskType.HEART -> MaskState(
            type = MaskType.HEART,
            scale = 1f
        )

        MaskType.CUSTOM -> MaskState(
            type = MaskType.CUSTOM,
            customClosed = false
        )
    }
}
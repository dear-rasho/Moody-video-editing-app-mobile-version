package com.moody.moodyvideoeditor.data

/**
 * Mirrors js/workspace/keyframeStore.js
 * Keyframe = { time (sec), value, ease }
 */
data class Keyframe(
    val time: Float,             // seconds since clip start
    val value: Float,
    val ease: String = DEFAULT_EASE
) {
    companion object {
        const val DEFAULT_EASE = "quadInOut"
    }
}

/**
 * Per-clip keyframe map.
 * Key = property name ("x", "y", "scale", "rotation", "anchorX", "anchorY", ...)
 * Value = sorted list of keyframes
 */
typealias KeyframeMap = Map<String, List<Keyframe>>

object KeyframeLibrary {

    // Mirrors js/workspace/keyframeStore.js ANIMATABLE_PROPS
    val ANIMATABLE_PROPS = listOf(
        "x", "y",
        "scale", "rotation",
        "anchorX", "anchorY",
        "cropL", "cropR", "cropT", "cropB"
    )

    // 25 easing functions — mirrors keyframeStore.js easeFn()
    val EASING_OPTIONS = listOf(
        "linear",
        "easeIn", "easeOut", "easeInOut",
        "sineIn", "sineOut", "sineInOut",
        "quadIn", "quadOut", "quadInOut",
        "cubicIn", "cubicOut", "cubicInOut",
        "quartIn", "quartOut", "quartInOut",
        "quintIn", "quintOut", "quintInOut",
        "expoIn", "expoOut", "expoInOut",
        "backIn", "backOut", "backInOut",
        "elasticIn", "elasticOut",
        "bounceOut"
    )

    val EASING_LABELS = mapOf(
        "linear" to "Linear",
        "easeIn" to "Ease In",
        "easeOut" to "Ease Out",
        "easeInOut" to "Ease In-Out",
        "sineIn" to "Sine In",
        "sineOut" to "Sine Out",
        "sineInOut" to "Sine In-Out",
        "quadIn" to "Quad In",
        "quadOut" to "Quad Out",
        "quadInOut" to "Quad In-Out",
        "cubicIn" to "Cubic In",
        "cubicOut" to "Cubic Out",
        "cubicInOut" to "Cubic In-Out",
        "quartIn" to "Quart In",
        "quartOut" to "Quart Out",
        "quartInOut" to "Quart In-Out",
        "quintIn" to "Quint In",
        "quintOut" to "Quint Out",
        "quintInOut" to "Quint In-Out",
        "expoIn" to "Expo In",
        "expoOut" to "Expo Out",
        "expoInOut" to "Expo In-Out",
        "backIn" to "Back In",
        "backOut" to "Back Out",
        "backInOut" to "Back In-Out",
        "elasticIn" to "Elastic In",
        "elasticOut" to "Elastic Out",
        "bounceOut" to "Bounce Out"
    )
}
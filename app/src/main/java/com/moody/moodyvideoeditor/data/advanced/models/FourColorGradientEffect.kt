package com.moody.moodyvideoeditor.data.advanced.models

import com.moody.moodyvideoeditor.data.Keyframe

enum class GradientBlendMode(val key: String) {
    NORMAL("normal"),
    ADD("add"),
    MULTIPLY("multiply"),
    SCREEN("screen"),
    OVERLAY("overlay");

    companion object {
        fun fromKey(key: String): GradientBlendMode =
            values().firstOrNull { it.key.equals(key, ignoreCase = true) } ?: NORMAL
    }
}

data class FourColorGradientEffect(
    val color1: Long = 0xFFFF0000L,
    val color2: Long = 0xFF00FF00L,
    val color3: Long = 0xFF0000FFL,
    val color4: Long = 0xFFFFCC00L,
    val blendMode: GradientBlendMode = GradientBlendMode.NORMAL,
    val globalOpacity: Float = 100f,
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)
package com.moody.moodyvideoeditor.data.advanced.models

import com.moody.moodyvideoeditor.data.Keyframe

data class DropShadowEffect(
    val shadowColor: Long = 0xFF000000L,
    val opacity: Float = 50f,
    val distance: Float = 5f,
    val directionAngle: Float = 135f,
    val blurSoftness: Float = 5f,
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)
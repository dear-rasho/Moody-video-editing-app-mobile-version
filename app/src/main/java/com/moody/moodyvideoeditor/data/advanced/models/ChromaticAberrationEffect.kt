package com.moody.moodyvideoeditor.data.advanced.models

import com.moody.moodyvideoeditor.data.Keyframe

data class ChromaticAberrationEffect(
    val redShiftX: Float = 0f,
    val redShiftY: Float = 0f,
    val blueShiftX: Float = 0f,
    val blueShiftY: Float = 0f,
    val blurRadius: Float = 0f,
    val falloffThreshold: Float = 0.5f,
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)
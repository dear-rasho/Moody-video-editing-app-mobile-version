package com.moody.moodyvideoeditor.data.advanced.models

import com.moody.moodyvideoeditor.data.Keyframe

data class TurbulentDisplaceEffect(
    val amount: Float = 50f,
    val size: Float = 100f,
    val offsetX: Float = 0.5f,
    val offsetY: Float = 0.5f,
    val evolutionSpeed: Float = 1f,
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)
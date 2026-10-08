package com.moody.moodyvideoeditor.data.advanced.models

import com.moody.moodyvideoeditor.data.Keyframe

data class RoughenEdgesEffect(
    val borderWidth: Float = 20f,
    val edgeSharpness: Float = 1f,
    val fractalScale: Float = 100f,
    val evolution: Float = 0f,
    val complexity: Float = 1f,
    val randomSeed: Float = 0f,
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)
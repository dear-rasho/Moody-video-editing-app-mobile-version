package com.moody.moodyvideoeditor.data.advanced.models

import com.moody.moodyvideoeditor.data.Keyframe

data class MirrorEffect(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val angleDeg: Float = 90f,
    val opacity: Float = 100f,
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)
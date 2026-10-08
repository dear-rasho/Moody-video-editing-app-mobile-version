package com.moody.moodyvideoeditor.data.advanced.models

import com.moody.moodyvideoeditor.data.Keyframe

data class MotionBlurEffect(
    val shutterAngle: Float = 180f,
    val samples: Float = 16f,
    val intensity: Float = 1f,
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)
package com.moody.moodyvideoeditor.data.advanced.models

import com.moody.moodyvideoeditor.data.Keyframe

data class RoundedCropEffect(
    val cornerRadius: Float = 40f,
    val cropTop: Float = 0f,
    val cropBottom: Float = 0f,
    val cropLeft: Float = 0f,
    val cropRight: Float = 0f,
    val feathering: Float = 0f,
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)
package com.moody.moodyvideoeditor.data.advanced.models

import com.moody.moodyvideoeditor.data.Keyframe

enum class BlurDimension { BOTH, HORIZONTAL, VERTICAL }

data class GaussianBlurEffect(
    val blurriness: Float = 0f,       // 0 – 1000
    val dimension: BlurDimension = BlurDimension.BOTH,
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)
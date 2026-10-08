package com.moody.moodyvideoeditor.data.advanced.models

import com.moody.moodyvideoeditor.data.Keyframe

enum class TrackMatteType(val key: String) {
    ALPHA("alpha"),
    ALPHA_INVERTED("alpha_inv"),
    LUMA("luma"),
    LUMA_INVERTED("luma_inv");

    companion object {
        fun fromKey(key: String): TrackMatteType =
            values().firstOrNull { it.key.equals(key, ignoreCase = true) } ?: ALPHA
    }
}

data class TrackMatteEffect(
    val matteType: TrackMatteType = TrackMatteType.ALPHA,
    val targetLayerId: String? = null,
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)
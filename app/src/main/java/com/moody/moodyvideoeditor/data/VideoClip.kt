package com.moody.moodyvideoeditor.data

import android.net.Uri

data class VideoClip(
    val id: String,
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    var startTimeMs: Long = 0,
    var endTimeMs: Long = durationMs,
    var playbackSpeed: Float = 1.0f
) {
    val visibleDurationMs: Long
        get() = (endTimeMs - startTimeMs).coerceAtLeast(0L)

    val visibleDurationSec: Float
        get() = visibleDurationMs / 1000f
}
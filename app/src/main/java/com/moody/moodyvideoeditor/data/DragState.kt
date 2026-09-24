package com.moody.moodyvideoeditor.data

/**
 * Timeline pe clip drag karne ki state.
 */
data class DragState(
    val clipId: String,
    val startClientX: Float,
    val startClientY: Float,
    val startTrackIndex: Int,
    val startIsAudio: Boolean,
    val startTimelineMs: Long,

    // Live update
    var currentClientX: Float = startClientX,
    var currentClientY: Float = startClientY,
    var currentTrackIndex: Int = startTrackIndex,
    var currentIsAudio: Boolean = startIsAudio,
    var currentTimelineMs: Long = startTimelineMs,

    // Flags
    var isDragging: Boolean = false
) {
    val deltaX: Float get() = currentClientX - startClientX
    val deltaY: Float get() = currentClientY - startClientY
    val isVerticalMove: Boolean get() = currentTrackIndex != startTrackIndex || currentIsAudio != startIsAudio
}
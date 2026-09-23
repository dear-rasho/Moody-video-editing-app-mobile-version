package com.moody.moodyvideoeditor.data

/**
 * Poori app ka state
 * Yeh JS ke appState jaisa hai
 */
data class EditorState(
    val timeline: Timeline = Timeline(),
    val currentTimeMs: Long = 0,
    val isPlaying: Boolean = false,
    val selectedClipId: String? = null
) {
    val totalDurationMs: Long
        get() = timeline.totalDurationMs()
}
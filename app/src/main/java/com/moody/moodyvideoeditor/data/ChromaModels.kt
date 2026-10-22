package com.moody.moodyvideoeditor.data

// Chroma key data model
// Green screen removal via YUV chroma distance (preview) + FFmpeg (export)
data class ChromaState(
    val keyColor: Long = 0L,
    val similarity: Float = 45f,       // 0..100 — threshold
    val smoothness: Float = 15f,       // 0..100 — edge softness
    val spill: Float = 50f,            // 0..100 — color spill suppression
    val intensity: Float = 100f        // 0..100 — effect strength
) {
    val isActive: Boolean get() = keyColor != 0L && intensity > 0f
}
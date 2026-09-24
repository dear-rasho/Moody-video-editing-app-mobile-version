package com.moody.moodyvideoeditor.data

/**
 * Mirrors js/features/chromakey.js DEFAULTS.
 */
data class ChromaState(
    val keyColor: Long = 0xFF00FF00,   // ARGB green default
    val similarity: Float = 30f,       // 0..100
    val smoothness: Float = 20f,       // 0..100
    val spill: Float = 50f,            // 0..100
    val intensity: Float = 100f        // 0..100
) {
    val isActive: Boolean get() = keyColor != 0L && intensity > 0f
}
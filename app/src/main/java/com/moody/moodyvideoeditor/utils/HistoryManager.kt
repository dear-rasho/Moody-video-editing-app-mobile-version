package com.moody.moodyvideoeditor.data

import android.net.Uri
import java.util.UUID

data class EditorClip(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val timelineStartMs: Long,
    val speed: Float = 1.0f,
    val volume: Float = 1.0f,
    val scale: Float = 1.0f,
    val rotation: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val cropL: Float = 0f,
    val cropR: Float = 0f,
    val cropT: Float = 0f,
    val cropB: Float = 0f
) {
    val sourceDurationMs: Long get() = sourceEndMs - sourceStartMs
    val durationMs: Long get() = (sourceDurationMs / speed).toLong()
    val timelineEndMs: Long get() = timelineStartMs + durationMs
}

data class EditorState(
    val clips: List<EditorClip> = emptyList(),
    val currentIndex: Int = 0,
    val currentPosMs: Long = 0L,
    val isPlaying: Boolean = false,
    val speed: Float = 1.0f,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val rotation: Int = 0,
    val aspectMode: Int = 0,
    val aspectRatio: String = "16:9",
    val text: String = "",
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val textSize: Int = 24,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val effect: String = "none",
    val sticker: String = "",
    val stickerX: Float = 0.5f,
    val stickerY: Float = 0.5f,
    val overlay: String = "none",
    val brightness: Float = 1.0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f
) {
    val totalDurationMs: Long get() = clips.maxOfOrNull { it.timelineEndMs } ?: 0L
    val currentClip: EditorClip? get() = clips.getOrNull(currentIndex)
}
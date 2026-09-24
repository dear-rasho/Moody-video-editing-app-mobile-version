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

    // ── Clip properties ──
    val speed: Float = 1.0f,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val rotation: Int = 0,
    val aspectMode: Int = 0,
    val aspectRatio: String = "16:9",

    // ── Text ──
    val text: String = "",
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val textSize: Int = 24,
    val textAnimation: String = "none",
    val textFont: String = "Arial",

    // ── Effects / Filters ──
    val effect: String = "none",
    val filter: String = "none",
    val overlay: String = "none",
    val transition: String = "none",
    val motion: String = "none",

    // ── Sticker ──
    val sticker: String = "",
    val stickerX: Float = 0.5f,
    val stickerY: Float = 0.5f,

    // ── Adjustments ──
    val brightness: Float = 1.0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val exposure: Float = 1.0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,

    // ── Color Wheel ──
    val shadowsHue: Float = 0f,
    val shadowsSat: Float = 0f,
    val midtonesHue: Float = 0f,
    val midtonesSat: Float = 0f,
    val highlightsHue: Float = 0f,
    val highlightsSat: Float = 0f,
    val hdrWhite: Float = 100f,

    // ── Chroma Key ──
    val chromaColor: Int = 0xFF00FF00.toInt(),
    val chromaSimilarity: Float = 30f,
    val chromaSmoothness: Float = 20f,
    val chromaSpill: Float = 50f,
    val chromaIntensity: Float = 100f,

    // ── Audio FX ──
    val audioFx: String = "none",
    val soundFx: String = "none",

    // ── Beats ──
    val beatsDetected: Boolean = false,
    val beatsCount: Int = 0,
    val beatsFilter: String = "all",

    // ── History ──
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
) {
    val totalDurationMs: Long get() = clips.maxOfOrNull { it.timelineEndMs } ?: 0L
    val currentClip: EditorClip? get() = clips.getOrNull(currentIndex)
}
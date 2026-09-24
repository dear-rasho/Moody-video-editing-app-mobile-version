package com.moody.moodyvideoeditor.data

import android.net.Uri
import java.util.UUID

// ═══════════════════════════════════════════════════════════════
//  ADJUSTMENT DATA — 24 sliders (JS jaisa)
// ═══════════════════════════════════════════════════════════════
data class AdjustmentData(
    // Light (7)
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val exposure: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val shadows: Float = 0f,
    val highlights: Float = 0f,
    // Color (3)
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val clarity: Float = 0f,
    // Temperature (2)
    val temperature: Float = 0f,
    val tint: Float = 0f,
    // Details (3)
    val noise: Float = 0f,
    val sharpen: Float = 0f,
    val vignette: Float = 0f,
    // Color Channels (9)
    val reds: Float = 0f,
    val oranges: Float = 0f,
    val yellows: Float = 0f,
    val greens: Float = 0f,
    val cyans: Float = 0f,
    val blues: Float = 0f,
    val purples: Float = 0f,
    val magentas: Float = 0f,
    val skinTones: Float = 0f,
) {
    val isDefault: Boolean
        get() =
            brightness == 0f && contrast == 0f && exposure == 0f &&
                whites == 0f && blacks == 0f && shadows == 0f && highlights == 0f &&
                saturation == 0f && vibrance == 0f && clarity == 0f &&
                temperature == 0f && tint == 0f &&
                noise == 0f && sharpen == 0f && vignette == 0f &&
                reds == 0f && oranges == 0f && yellows == 0f && greens == 0f &&
                cyans == 0f && blues == 0f && purples == 0f && magentas == 0f &&
                skinTones == 0f
}

// ═══════════════════════════════════════════════════════════════
//  EDITOR CLIP
// ═══════════════════════════════════════════════════════════════
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
    val cropB: Float = 0f,
    val trackIndex: Int = 0,
    val isAudio: Boolean = false,
    val adjustments: AdjustmentData = AdjustmentData(),
) {
    val sourceDurationMs: Long get() = sourceEndMs - sourceStartMs
    val durationMs: Long get() = (sourceDurationMs / speed).toLong()
    val timelineEndMs: Long get() = timelineStartMs + durationMs
}

// ═══════════════════════════════════════════════════════════════
//  EDITOR STATE
// ═══════════════════════════════════════════════════════════════
data class EditorState(
    val clips: List<EditorClip> = emptyList(),
    val currentIndex: Int = 0,
    val currentPosMs: Long = 0L,
    val isPlaying: Boolean = false,
    // Selection
    val selectedClipId: String? = null,
    val selectedTrackIndex: Int = 0,
    val selectedIsAudio: Boolean = false,
    // Layer counts
    val visualLayerCount: Int = 3,
    val audioLayerCount: Int = 2,
    // Playback
    val speed: Float = 1.0f,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val rotation: Int = 0,
    val aspectMode: Int = 0,
    val aspectRatio: String = "16:9",
    // Text
    val text: String = "",
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val textSize: Int = 24,
    val textAnimation: String = "none",
    val textFont: String = "Arial",
    // Effects / Filters
    val effect: String = "none",
    val filter: String = "none",
    val overlay: String = "none",
    val transition: String = "none",
    val motion: String = "none",
    // Sticker
    val sticker: String = "",
    val stickerX: Float = 0.5f,
    val stickerY: Float = 0.5f,
    // Color Wheel
    val shadowsHue: Float = 0f,
    val shadowsSat: Float = 0f,
    val midtonesHue: Float = 0f,
    val midtonesSat: Float = 0f,
    val highlightsHue: Float = 0f,
    val highlightsSat: Float = 0f,
    val hdrWhite: Float = 100f,
    // Chroma Key
    val chromaColor: Int = 0xFF00FF00.toInt(),
    val chromaSimilarity: Float = 30f,
    val chromaSmoothness: Float = 20f,
    val chromaSpill: Float = 50f,
    val chromaIntensity: Float = 100f,
    // Audio
    val audioFx: String = "none",
    val soundFx: String = "none",
    // Beats
    val beatsDetected: Boolean = false,
    val beatsCount: Int = 0,
    val beatsFilter: String = "all",
    // History
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
) {
    val totalDurationMs: Long get() = clips.maxOfOrNull { it.timelineEndMs } ?: 0L
    val currentClip: EditorClip? get() = clips.getOrNull(currentIndex)

    val selectedClip: EditorClip?
        get() =
            clips.firstOrNull {
                it.id == selectedClipId && it.trackIndex == selectedTrackIndex && it.isAudio == selectedIsAudio
            }

    fun clipsOf(
        trackIndex: Int,
        isAudio: Boolean,
    ): List<EditorClip> = clips.filter { it.trackIndex == trackIndex && it.isAudio == isAudio }
}

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
    val cropB: Float = 0f,
    val trackIndex: Int = 0,
    val isAudio: Boolean = false,
    val adjustments: AdjustmentData = AdjustmentData(),
    val sourceTotalMs: Long = Long.MAX_VALUE,
    val linkedId: String? = null,
    val isMuted: Boolean = false,

    // 🆕 Feature states (per-clip)
    val filters: FilterState = FilterState(),
    val colorWheel: ColorWheelState = ColorWheelState(),
    val overlay: OverlayState = OverlayState(),
    val effectKeys: List<String> = emptyList()
) {
    val sourceDurationMs: Long get() = sourceEndMs - sourceStartMs
    val durationMs: Long get() = (sourceDurationMs / speed).toLong()
    val timelineEndMs: Long get() = timelineStartMs + durationMs

    companion object {
        const val MIN_DURATION_MS = 300L
    }
}

// ═══════════════════════════════════════════════════════════════
//  AdjustmentData — UNCHANGED
// ═══════════════════════════════════════════════════════════════
data class AdjustmentData(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val exposure: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val shadows: Float = 0f,
    val highlights: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val clarity: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val noise: Float = 0f,
    val sharpen: Float = 0f,
    val vignette: Float = 0f,
    val reds: Float = 0f,
    val oranges: Float = 0f,
    val yellows: Float = 0f,
    val greens: Float = 0f,
    val cyans: Float = 0f,
    val blues: Float = 0f,
    val purples: Float = 0f,
    val magentas: Float = 0f,
    val skinTones: Float = 0f
) {
    val isDefault: Boolean get() = this == AdjustmentData()
}

// ═══════════════════════════════════════════════════════════════
//  EditorState — UPDATED with textClips + stickerClips
// ═══════════════════════════════════════════════════════════════
data class EditorState(
    val clips: List<EditorClip> = emptyList(),
    val textClips: List<TextClip> = emptyList(),
    val stickerClips: List<StickerClip> = emptyList(),
    val currentIndex: Int = 0,
    val currentPosMs: Long = 0L,
    val isPlaying: Boolean = false,
    val selectedClipId: String? = null,
    val selectedTextId: String? = null,
    val selectedStickerId: String? = null,
    val selectedTrackIndex: Int = 0,
    val selectedIsAudio: Boolean = false,
    val visualLayerCount: Int = 3,
    val audioLayerCount: Int = 2,
    val speed: Float = 1.0f,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val rotation: Int = 0,
    val aspectMode: Int = 0,
    val aspectRatio: String = "16:9",
    val chromaColor: Int = 0xFF00FF00.toInt(),
    val chromaSimilarity: Float = 30f,
    val chromaSmoothness: Float = 20f,
    val chromaSpill: Float = 50f,
    val chromaIntensity: Float = 100f,
    val audioFx: String = "none",
    val soundFx: String = "none",
    val beatsDetected: Boolean = false,
    val beatsCount: Int = 0,
    val beatsFilter: String = "all",
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
) {
    val totalDurationMs: Long get() = clips.maxOfOrNull { it.timelineEndMs } ?: 10000L
    val currentClip: EditorClip? get() = clips.getOrNull(currentIndex)
    val selectedClip: EditorClip? get() = clips.firstOrNull { it.id == selectedClipId }
    val selectedText: TextClip? get() = textClips.firstOrNull { it.id == selectedTextId }
    val selectedSticker: StickerClip? get() = stickerClips.firstOrNull { it.id == selectedStickerId }

    fun clipsOf(trackIndex: Int, isAudio: Boolean): List<EditorClip> =
        clips.filter { it.trackIndex == trackIndex && it.isAudio == isAudio }
}
package com.moody.moodyvideoeditor.data

import android.net.Uri
import java.util.UUID

data class EditorClip(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String,
    val type: String = "video/mp4",
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
    val linkGroupId: String? = null,   // 🆕 user-defined multi-clip link
    val isMuted: Boolean = false,
    // 🆕 PER-CLIP AUDIO EFFECTS
    val audioFx: String = "none",
    val audioFxIntensity: Float = 100f,
    val soundFx: String = "none",
    val soundFxIntensity: Float = 100f,

    val filters: FilterState = FilterState(),
    val colorWheel: ColorWheelState = ColorWheelState(),
    val overlay: OverlayState = OverlayState(),
    val effectKeys: List<String> = emptyList(),
    val effectState: EffectState? = null,
    val textState: TextState? = null,
    val stickerState: StickerState? = null,

    val chroma: ChromaState? = null,
    val freeze: FreezeState? = null,
    val transition: TransitionState? = null,
    val ratio: RatioState? = null,
    val mask: MaskState = MaskState(),

    val brush: BrushState = BrushState(),

    // 🆕 Color Matte — full style (solid / ramp / 4-color)
    val matteStyle: ColorMatteStyle = ColorMatteStyle(),
    // 🆕 Advanced Effects — stored on the clip itself (no separate layer)
    val advancedEffects: List<com.moody.moodyvideoeditor.data.advanced.AdvancedEffectState> = emptyList(),
    val visualizer: VisualizerState? = null,

    val keyframes: Map<String, List<Keyframe>> = emptyMap()
) {
    val sourceDurationMs: Long get() = sourceEndMs - sourceStartMs
    val durationMs: Long get() = (sourceDurationMs / speed).toLong()
    val timelineEndMs: Long get() = timelineStartMs + durationMs

    val isVisualClip: Boolean
        get() = !isAudio &&
                (type.startsWith("video/") || type.startsWith("image/"))

    val isTextClip: Boolean get() = type == "text/plain"
    val isStickerClip: Boolean get() = type == "sticker/plain"
    val isEffectClip: Boolean get() = type == "effect/plain"
    val isAdjustmentClip: Boolean get() = type == "adjustment/plain"
    val isOverlayClip: Boolean get() = type == "overlay/plain"
    val isChromaClip: Boolean get() = type == "chroma/plain"
    val isBrushClip: Boolean get() = type == "brush/plain"
    val isVisualizerClip: Boolean get() = type == "visualizer/plain"
    val isColorMatteClip: Boolean get() = type == "matte/plain"


    // Backward-compat accessor — returns solid color if legacy code refers
    val matteColor: Long get() = matteStyle.solidColor

    // 🆕 Link anchor priority (higher = anchor)
    val linkAnchorPriority: Int
        get() = when {
            !isAudio && isVisualClip && trackIndex == 0 -> 100  // V1 video
            !isAudio && isVisualClip -> 90                       // higher video
            isAudio && !isAudioEffectClip -> 80                  // source audio
            isColorMatteClip -> 70                               // 🆕 color matte
            isTextClip -> 60
            isStickerClip -> 55
            isVisualizerClip -> 50
            isOverlayClip -> 45
            isEffectClip -> 40
            isBrushClip -> 35
            isAdjustmentClip -> 30
            isFilterLayerClip -> 25
            else -> 10
        }

    // 🆕 Audio effect layers
    val isAudioFxClip: Boolean get() = type == "audiofx/plain"
    val isSoundFxClip: Boolean get() = type == "soundfx/plain"
    val isAudioEffectClip: Boolean get() = isAudioFxClip || isSoundFxClip

    // 🆕 Filter layer (adjustment-like visual layer)
    val isFilterLayerClip: Boolean get() = type == "filter/plain"

    companion object {
        const val MIN_DURATION_MS = 300L
    }
}

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

data class EditorState(
    val clips: List<EditorClip> = emptyList(),
    val currentIndex: Int = 0,
    val currentPosMs: Long = 0L,
    val isPlaying: Boolean = false,
    val selectedClipId: String? = null,
    val multiSelectedIds: Set<String> = emptySet(),
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
    val audioFx: String = "none",
    val soundFx: String = "none",
    val beatsDetected: Boolean = false,
    val beatsCount: Int = 0,
    val beatsFilter: String = "all",
    val beatTimesMs: List<Long> = emptyList(),
    val beatStrengths: List<Float> = emptyList(),   // 🆕
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val timelineZoom: Float = 0f,
    val hiddenVisualTracks: Set<Int> = emptySet(),
    val mutedAudioTracks: Set<Int> = emptySet(),

    val exportResolution: String = "720p",
    val exportFps: Int = 30,
    val exportBitrateKbps: Int = 8000,
    val exportFormat: String = "mp4",
    val exportFolderUri: String? = null,

    // 🆕 Live preview filters (temporary, not committed)
    val previewFilters: FilterState? = null,

    // 🆕 Live preview effect (temporary, not committed)
    val previewEffectState: EffectState? = null,

    // 🆕 Live preview advanced effect (before Apply)
    val previewAdvancedEffect: com.moody.moodyvideoeditor.data.advanced.AdvancedEffectState? = null
) {
    val totalDurationMs: Long
        get() = clips.maxOfOrNull { it.timelineEndMs } ?: 10000L

    val currentClip: EditorClip?
        get() = clips.getOrNull(currentIndex)

    val selectedClip: EditorClip?
        get() = clips.firstOrNull { it.id == selectedClipId }

    fun clipsOf(trackIndex: Int, isAudio: Boolean): List<EditorClip> =
        clips.filter { it.trackIndex == trackIndex && it.isAudio == isAudio }

    fun timelineVisualList(): List<List<EditorClip>> {
        if (visualLayerCount <= 0) return emptyList()
        val result = MutableList(visualLayerCount) { emptyList<EditorClip>() }
        clips.filter { !it.isAudio }.forEach { c ->
            if (c.trackIndex in 0 until visualLayerCount) {
                result[c.trackIndex] = result[c.trackIndex] + c
            }
        }
        return result
    }

    fun timelineAudioList(): List<List<EditorClip>> {
        if (audioLayerCount <= 0) return emptyList()
        val result = MutableList(audioLayerCount) { emptyList<EditorClip>() }
        clips.filter { it.isAudio }.forEach { c ->
            if (c.trackIndex in 0 until audioLayerCount) {
                result[c.trackIndex] = result[c.trackIndex] + c
            }
        }
        return result
    }

    fun audioOrVisualTracks(isAudio: Boolean): List<List<EditorClip>> =
        if (isAudio) timelineAudioList() else timelineVisualList()

    fun withTrackList(
        isAudio: Boolean,
        newList: List<List<EditorClip>>
    ): EditorState {
        val otherClips = clips.filter { it.isAudio != isAudio }
        val newClips = newList.flatten()
        return copy(
            clips = otherClips + newClips,
            visualLayerCount = if (isAudio) visualLayerCount else newList.size,
            audioLayerCount = if (isAudio) newList.size else audioLayerCount
        )
    }
}
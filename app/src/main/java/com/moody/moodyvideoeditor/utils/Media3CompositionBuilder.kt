package com.moody.moodyvideoeditor.utils

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SpeedChangingAudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.moody.moodyvideoeditor.data.AudioAdjustmentSettings
import com.moody.moodyvideoeditor.data.EditorClip

@UnstableApi
class Media3CompositionBuilder(private val context: Context) {

    /**
     * Builds a composition only for contiguous, single-track visual clips with no unsupported
     * editing features. A null result means the caller should use the existing FFmpeg path.
     */
    fun buildComposition(
        clips: List<EditorClip>,
        outputWidth: Int,
        outputHeight: Int,
        fps: Int
    ): Composition? {
        if (clips.isEmpty() || outputWidth <= 0 || outputHeight <= 0 || fps <= 0) {
            return null
        }

        val orderedClips = clips.sortedBy(EditorClip::timelineStartMs)
        if (orderedClips.any { !isSupportedClip(it) } || !hasContiguousTimeline(orderedClips)) {
            return null
        }
        val editedItems = orderedClips.map { clip ->
            val mediaItemBuilder = MediaItem.Builder()
                .setUri(clip.uri)

            if (clip.type.startsWith("image/")) {
                // Image duration describes the full source timeline so the clip trim below applies.
                mediaItemBuilder.setImageDurationMs(clip.sourceEndMs)
            }

            val mediaItem = mediaItemBuilder
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.sourceStartMs)
                        .setEndPositionMs(clip.sourceEndMs)
                        .build()
                )
                .build()

            val videoEffects = mutableListOf<Effect>()
            if (clip.speed != 1f) {
                videoEffects.add(SpeedChangeEffect(clip.speed))
            }

            videoEffects.add(AdjustmentGlEffect(clip.adjustments))
            videoEffects.add(AdjustmentSpatialGlEffect(clip.adjustments))
            videoEffects.add(FilterLayerGlEffect(clip.filters))
            videoEffects.add(ColorWheelGlEffect(clip.colorWheel))

            val audioProcessors = mutableListOf<AudioProcessor>()
            if (clip.speed != 1f) {
                audioProcessors.add(
                    SpeedChangingAudioProcessor(constantSpeedProvider(clip.speed))
                )
            }

            EditedMediaItem.Builder(mediaItem)
                .setFrameRate(fps)
                .setEffects(Effects(audioProcessors, videoEffects))
                .build()
        }

        val sequence = EditedMediaItemSequence.Builder()
            .addItems(editedItems)
            .build()

        return Composition.Builder(sequence).build()
    }

    private fun isSupportedClip(clip: EditorClip): Boolean {
        if (!clip.isVisualClip || clip.isAudio || clip.trackIndex != 0) return false
        if (clip.isTextClip || clip.isEffectClip || clip.isAdjustmentClip ||
            clip.isOverlayClip || clip.isColorMatteClip || clip.isSpotlightClip ||
            clip.isVisualizerClip || clip.isFilterLayerClip || clip.isAudioEffectClip
        ) return false

        if (!clip.type.startsWith("video/") && !clip.type.startsWith("image/")) return false
        if (clip.effectState != null || clip.textState != null ||
            clip.transition?.isActive == true || clip.mask.isActive ||
            clip.layerShadow.enabled || clip.overlay.type != "none" ||
            clip.chromaKey.isActive || clip.spotlight != null ||
            clip.visualizer != null || clip.freeze != null ||
            clip.lightLeak != null
        ) return false

        if (clip.keyframes.values.any { it.isNotEmpty() }) return false
        if (clip.sourceStartMs < 0L || clip.sourceEndMs <= clip.sourceStartMs) return false
        if (!clip.speed.isFinite() || clip.speed <= 0f) return false

        // These clip-level controls are not represented in the 8a composition skeleton.
        if (clip.scale != 1f || clip.scaleX != 1f || clip.scaleY != 1f ||
            clip.rotation != 0f || clip.offsetX != 0f || clip.offsetY != 0f ||
            clip.cropL != 0f || clip.cropR != 0f || clip.cropT != 0f || clip.cropB != 0f ||
            clip.blendMode != "normal" || clip.blendOpacity != 100f ||
            clip.layerOpacity != 100f
        ) return false

        // Retain embedded audio only when it needs no clip-level processing.
        if (clip.isMuted || clip.volume != 1f ||
            clip.audioFx != "none" || clip.soundFx != "none" ||
            clip.audioAdjustments != AudioAdjustmentSettings()
        ) return false

        return true
    }

    private fun hasContiguousTimeline(clips: List<EditorClip>): Boolean {
        var expectedStartMs = 0L
        for (clip in clips) {
            if (clip.timelineStartMs != expectedStartMs) return false
            expectedStartMs = clip.timelineEndMs
        }
        return true
    }

    private fun constantSpeedProvider(speed: Float): SpeedProvider =
        object : SpeedProvider {
            override fun getSpeed(timeUs: Long): Float = speed

            override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
        }
}

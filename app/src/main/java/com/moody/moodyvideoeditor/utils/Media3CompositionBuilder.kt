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
     * Builds a composition only for contiguous tracks with no unsupported editing features.
     * A null result means the caller should use the existing FFmpeg path.
     */
    fun buildComposition(
        clips: List<EditorClip>,
        textSequences: List<TextOverlaySequence>,
        maskSequences: List<MaskSequenceData>,
        outputWidth: Int,
        outputHeight: Int,
        fps: Int,
        timestampOffsetSec: Double = 0.0
    ): Composition? {
        // Text composition parity is unresolved, so keep text-bearing projects on FFmpeg.
        val compositionClips = clips.filterNot(EditorClip::isTextClip)
        if (clips.any(EditorClip::isTextClip) || textSequences.isNotEmpty()) return null
        if (compositionClips.isEmpty() || outputWidth <= 0 || outputHeight <= 0 || fps <= 0) {
            return null
        }
        if (compositionClips.any { !it.isAudio && !it.isVisualClip }) return null

        val visualClipsByTrack = compositionClips
            .filter { !it.isAudio && it.isVisualClip }
            .groupBy(EditorClip::trackIndex)
            .toSortedMap()
        val audioClipsByTrack = compositionClips
            .filter(EditorClip::isAudio)
            .groupBy(EditorClip::trackIndex)
            .toSortedMap()

        if (visualClipsByTrack.isEmpty() && audioClipsByTrack.isEmpty()) return null
        if (visualClipsByTrack.any { (trackIndex, trackClips) ->
                trackIndex < 0 ||
                        trackClips.any { !isSupportedVisualClip(it) } ||
                        !hasContiguousTimeline(trackClips.sortedBy(EditorClip::timelineStartMs))
            }
        ) {
            return null
        }
        if (audioClipsByTrack.any { (trackIndex, trackClips) ->
                trackIndex < 0 ||
                        trackClips.any { !isSupportedAudioClip(it) } ||
                        !hasContiguousTimeline(trackClips.sortedBy(EditorClip::timelineStartMs))
            }
        ) {
            return null
        }

        val visualClips = visualClipsByTrack.values.flatten()
        if (maskSequences.any { sequence ->
                sequence.frameCount <= 0 ||
                        (sequence.staticFilePath == null && sequence.fps <= 0) ||
                        sequence.clipEndSec <= sequence.clipStartSec ||
                        visualClips.none { it.id == sequence.clipId && it.mask.isActive }
            }
        ) {
            return null
        }
        if (visualClips.any { clip ->
                clip.mask.isActive && maskSequences.none { it.clipId == clip.id }
            }
        ) {
            return null
        }

        // The first compositor input is topmost and supplies output timestamps. Equal visual
        // track durations avoid holding or truncating a layer after its sequence ends.
        val visualTrackEndTimes = visualClipsByTrack.values.map { trackClips ->
            trackClips.maxOf(EditorClip::timelineEndMs)
        }
        if (visualTrackEndTimes.distinct().size > 1) return null

        val sequences = mutableListOf<EditedMediaItemSequence>()
        visualClipsByTrack.entries
            .sortedByDescending { it.key }
            .forEach { (_, trackClips) ->
                val items = trackClips
                    .sortedBy(EditorClip::timelineStartMs)
                    .map { clip ->
                        buildVisualItem(
                            clip = clip,
                            textSequences = textSequences,
                            maskSequences = maskSequences,
                            fps = fps,
                            timestampOffsetSec = timestampOffsetSec,
                            outputWidth = outputWidth,
                            outputHeight = outputHeight
                        )
                    }
                sequences += EditedMediaItemSequence.Builder()
                    .addItems(items)
                    .build()
            }

        audioClipsByTrack.entries
            .sortedBy { it.key }
            .forEach { (_, trackClips) ->
                val items = trackClips
                    .sortedBy(EditorClip::timelineStartMs)
                    .map(::buildAudioItem)
                sequences += EditedMediaItemSequence.Builder()
                    .addItems(items)
                    .build()
            }

        return Composition.Builder(sequences).build()
    }

    private fun buildVisualItem(
        clip: EditorClip,
        textSequences: List<TextOverlaySequence>,
        maskSequences: List<MaskSequenceData>,
        fps: Int,
        timestampOffsetSec: Double,
        outputWidth: Int,
        outputHeight: Int
    ): EditedMediaItem {
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
        videoEffects.add(
            TransformGlEffect(
                targetWidth = outputWidth,
                targetHeight = outputHeight,
                scale = clip.scale,
                scaleX = clip.scaleX,
                scaleY = clip.scaleY,
                rotationDeg = clip.rotation,
                offsetX = clip.offsetX,
                offsetY = clip.offsetY,
                cropL = clip.cropL,
                cropR = clip.cropR,
                cropT = clip.cropT,
                cropB = clip.cropB,
                layerOpacity = clip.layerOpacity,
                keyframes = clip.keyframes,
                clipStartSec = clip.timelineStartMs / 1000.0,
                timestampOffsetSec = timestampOffsetSec,
                fps = fps,
                positionHandledByOverlay = isPositionHandledByOverlay(clip)
            )
        )
        maskSequences
            .filter { it.clipId == clip.id }
            .forEach { mask ->
                videoEffects.add(
                    MaskOverlayGlEffect(
                        pattern = mask.pattern,
                        staticFilePath = mask.staticFilePath,
                        fps = mask.fps,
                        frameCount = mask.frameCount,
                        clipStartSec = mask.clipStartSec,
                        clipEndSec = mask.clipEndSec,
                        timestampOffsetSec = timestampOffsetSec
                    )
                )
            }
        textSequences
            .filter { sequence ->
                sequence.startSec < clip.timelineEndMs / 1000.0 &&
                        sequence.endSec > clip.timelineStartMs / 1000.0
            }
            .forEach { sequence ->
                videoEffects.add(TextOverlayGlEffect(sequence))
            }

        val audioProcessors = mutableListOf<AudioProcessor>()
        if (clip.speed != 1f) {
            audioProcessors.add(
                SpeedChangingAudioProcessor(constantSpeedProvider(clip.speed))
            )
        }

        return EditedMediaItem.Builder(mediaItem)
            .setFrameRate(fps)
            .setEffects(Effects(audioProcessors, videoEffects))
            .build()
    }

    private fun buildAudioItem(clip: EditorClip): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(clip.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.sourceStartMs)
                    .setEndPositionMs(clip.sourceEndMs)
                    .build()
            )
            .build()
        return EditedMediaItem.Builder(mediaItem).build()
    }

    private fun isSupportedVisualClip(clip: EditorClip): Boolean {
        if (!clip.isVisualClip || clip.isAudio || clip.trackIndex < 0) return false
        if (clip.isTextClip || clip.isEffectClip || clip.isAdjustmentClip ||
            clip.isOverlayClip || clip.isColorMatteClip || clip.isSpotlightClip ||
            clip.isVisualizerClip || clip.isFilterLayerClip || clip.isAudioEffectClip
        ) return false

        if (!clip.type.startsWith("video/") && !clip.type.startsWith("image/")) return false
        if (clip.effectState != null || clip.textState != null ||
            clip.transition?.isActive == true ||
            clip.layerShadow.enabled || clip.overlay.type != "none" ||
            clip.chromaKey.isActive || clip.spotlight != null ||
            clip.visualizer != null || clip.freeze != null ||
            clip.lightLeak != null
        ) return false

        if (clip.rotation != 0f) return false
        if (clip.keyframes.values.any { it.isNotEmpty() }) return false
        if (clip.sourceStartMs < 0L || clip.sourceEndMs <= clip.sourceStartMs) return false
        if (!clip.speed.isFinite() || clip.speed <= 0f) return false

        // Blend controls remain deferred to 8d-B2c.
        if (clip.blendMode != "normal" || clip.blendOpacity != 100f) return false

        // Retain embedded audio only when it needs no clip-level processing.
        if (clip.isMuted || clip.volume != 1f ||
            clip.audioFx != "none" || clip.soundFx != "none" ||
            clip.audioAdjustments != AudioAdjustmentSettings()
        ) return false

        return true
    }

    private fun isSupportedAudioClip(clip: EditorClip): Boolean {
        if (!clip.isAudio || !clip.type.startsWith("audio/") || clip.trackIndex < 0) {
            return false
        }
        if (clip.speed != 1f || clip.volume != 1f || clip.isMuted) return false
        if (clip.audioFx != "none" || clip.soundFx != "none" ||
            clip.audioAdjustments != AudioAdjustmentSettings()
        ) return false
        if (clip.keyframes.values.any { it.isNotEmpty() }) return false
        if (clip.sourceStartMs < 0L || clip.sourceEndMs <= clip.sourceStartMs) return false
        if (clip.linkedId != null || clip.linkGroupId != null) return false
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

    private fun isPositionHandledByOverlay(clip: EditorClip): Boolean {
        val blendKey = clip.blendMode.lowercase()
        val positionByOverlay = blendKey == "normal" || blendKey.isBlank()
        if (!positionByOverlay) return false

        return preservesLayerOverscan(clip) ||
                clip.keyframes["x"].orEmpty().isNotEmpty() ||
                clip.keyframes["y"].orEmpty().isNotEmpty()
    }

    private fun preservesLayerOverscan(clip: EditorClip): Boolean {
        val maxKeyframedScale = clip.keyframes["scale"].orEmpty()
            .maxOfOrNull { it.value / 100f } ?: clip.scale
        val maxScaleX = maxOf(
            clip.scale * clip.scaleX,
            clip.keyframes["scaleX"].orEmpty().maxOfOrNull { it.value / 100f } ?: 0f
        )
        val maxScaleY = maxOf(
            clip.scale * clip.scaleY,
            clip.keyframes["scaleY"].orEmpty().maxOfOrNull { it.value / 100f } ?: 0f
        )
        val hasOversizedScale = maxOf(
            maxScaleX,
            maxScaleY,
            maxKeyframedScale * clip.scaleX,
            maxKeyframedScale * clip.scaleY
        ) > 1f
        val hasVisibleCrop = listOf("cropL", "cropR", "cropT", "cropB")
            .any { property ->
                clip.keyframes[property].orEmpty().any { kotlin.math.abs(it.value) > 0.0001f }
            }

        return hasOversizedScale &&
                !hasVisibleCrop &&
                !CropHelper.isCropped(clip) &&
                !clip.mask.isActive
    }

    private fun constantSpeedProvider(speed: Float): SpeedProvider =
        object : SpeedProvider {
            override fun getSpeed(timeUs: Long): Float = speed

            override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
        }

}

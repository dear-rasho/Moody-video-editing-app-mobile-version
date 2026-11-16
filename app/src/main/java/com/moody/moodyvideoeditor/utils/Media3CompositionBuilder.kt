package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.util.Log
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
        Log.i(
            LOG_TAG,
            "buildComposition called: clips=${clips.size} " +
                    "clipTypes=${clips.joinToString { "${it.type}@${it.trackIndex}" }} " +
                    "textSequences=${textSequences.size} maskSequences=${maskSequences.size} " +
                    "${outputWidth}x${outputHeight}@$fps"
        )
        if (clips.isEmpty()) {
            Log.w(LOG_TAG, "REJECT: clips.isEmpty")
            return null
        }

        // Text composition parity is unresolved, so keep text-bearing projects on FFmpeg.
        val filterLayerClips = clips.filter(EditorClip::isFilterLayerClip)
        val compositionClips = clips.filterNot {
            it.isTextClip || it.isFilterLayerClip
        }
        if (clips.any(EditorClip::isTextClip)) {
            val reason = if (textSequences.isEmpty()) {
                "text clips exist but no textSequences"
            } else {
                "text clips are not supported"
            }
            Log.w(LOG_TAG, "REJECT: $reason")
            return null
        }
        if (textSequences.isNotEmpty()) {
            Log.w(LOG_TAG, "REJECT: textSequences are not supported")
            return null
        }
        if (compositionClips.isEmpty()) {
            Log.w(LOG_TAG, "REJECT: compositionClips.isEmpty")
            return null
        }
        if (outputWidth <= 0 || outputHeight <= 0 || fps <= 0) {
            Log.w(
                LOG_TAG,
                "REJECT: invalid output settings width=$outputWidth height=$outputHeight fps=$fps"
            )
            return null
        }
        val unsupportedClipType = compositionClips.firstOrNull {
            !it.isAudio && !it.isVisualClip
        }
        if (unsupportedClipType != null) {
            Log.w(
                LOG_TAG,
                "REJECT: unsupported clip type id=${unsupportedClipType.id} " +
                        "type=${unsupportedClipType.type}"
            )
            logRejectionReason(unsupportedClipType)
            return null
        }

        val visualClipsByTrack = compositionClips
            .filter { !it.isAudio && it.isVisualClip }
            .groupBy(EditorClip::trackIndex)
            .toSortedMap()
        val audioClipsByTrack = compositionClips
            .filter(EditorClip::isAudio)
            .groupBy(EditorClip::trackIndex)
            .toSortedMap()

        if (visualClipsByTrack.isEmpty() && audioClipsByTrack.isEmpty()) {
            Log.w(LOG_TAG, "REJECT: no visual or audio tracks")
            return null
        }

        var hasVisualTrackRejection = false
        visualClipsByTrack.forEach { (trackIndex, trackClips) ->
            var trackRejected = false
            if (trackIndex < 0) {
                Log.w(LOG_TAG, "REJECT: visual track $trackIndex < 0")
                trackRejected = true
            }
            trackClips.forEach { clip ->
                val reason = unsupportedVisualClipReason(clip)
                if (reason != null) {
                    Log.w(
                        LOG_TAG,
                        "REJECT: clip ${clip.id} type=${clip.type} track=$trackIndex " +
                                "isSupportedVisualClip=false reason=$reason"
                    )
                    logRejectionReason(clip)
                    trackRejected = true
                }
            }
            val sortedClips = trackClips.sortedBy(EditorClip::timelineStartMs)
            if (!hasContiguousTimeline(sortedClips)) {
                Log.w(
                    LOG_TAG,
                    "REJECT: visual track $trackIndex has gaps; " +
                            "segments=${sortedClips.joinToString { "${it.id}:" +
                            "${it.timelineStartMs}-${it.timelineEndMs}" }}"
                )
                trackRejected = true
            }
            hasVisualTrackRejection = hasVisualTrackRejection || trackRejected
        }
        if (hasVisualTrackRejection) {
            Log.w(LOG_TAG, "REJECT: visual track validation failed")
            return null
        }

        var hasAudioTrackRejection = false
        audioClipsByTrack.forEach { (trackIndex, trackClips) ->
            var trackRejected = false
            if (trackIndex < 0) {
                Log.w(LOG_TAG, "REJECT: audio track $trackIndex < 0")
                trackRejected = true
            }
            trackClips.forEach { clip ->
                val reason = unsupportedAudioClipReason(clip)
                if (reason != null) {
                    Log.w(
                        LOG_TAG,
                        "REJECT: audio clip ${clip.id} type=${clip.type} track=$trackIndex " +
                                "isSupportedAudioClip=false reason=$reason"
                    )
                    logAudioRejectionReason(clip)
                    trackRejected = true
                }
            }
            val sortedClips = trackClips.sortedBy(EditorClip::timelineStartMs)
            if (!hasContiguousTimeline(sortedClips)) {
                Log.w(
                    LOG_TAG,
                    "REJECT: audio track $trackIndex has gaps; " +
                            "segments=${sortedClips.joinToString { "${it.id}:" +
                            "${it.timelineStartMs}-${it.timelineEndMs}" }}"
                )
                trackRejected = true
            }
            hasAudioTrackRejection = hasAudioTrackRejection || trackRejected
        }
        if (hasAudioTrackRejection) {
            Log.w(LOG_TAG, "REJECT: audio track validation failed")
            return null
        }

        val visualClips = visualClipsByTrack.values.flatten()
        val invalidFilterLayer = filterLayerClips.firstOrNull { layer ->
            if (layer.filters.isDefault) {
                false
            } else {
                val targetBaseClips = visualClips.filter {
                    it.trackIndex < layer.trackIndex &&
                            it.timelineEndMs > layer.timelineStartMs &&
                            it.timelineStartMs < layer.timelineEndMs
                }
                layer.trackIndex < 0 ||
                        layer.layerOpacity != 100f ||
                        layer.keyframes.values.any { it.isNotEmpty() } ||
                        targetBaseClips.isEmpty() ||
                        targetBaseClips.any {
                                    (layer.timelineStartMs > it.timelineStartMs ||
                                            layer.timelineEndMs < it.timelineEndMs)
                        }
            }
        }
        if (invalidFilterLayer != null) {
            val targetBaseClips = visualClips.filter {
                it.trackIndex < invalidFilterLayer.trackIndex &&
                it.timelineEndMs > invalidFilterLayer.timelineStartMs &&
                        it.timelineStartMs < invalidFilterLayer.timelineEndMs
            }
            val reason = when {
                invalidFilterLayer.trackIndex < 0 -> "invalid trackIndex"
                invalidFilterLayer.layerOpacity != 100f -> "layer opacity is not default"
                invalidFilterLayer.keyframes.values.any { it.isNotEmpty() } ->
                    "filter layer keyframes are not supported"
                targetBaseClips.isEmpty() -> "no overlapping visual clips on a lower track"
                else -> "layer does not fully cover overlapping visual clips"
            }
            Log.w(
                LOG_TAG,
                "REJECT: filter layer ${invalidFilterLayer.id} is unsupported, reason: $reason"
            )
            return null
        }
        filterLayerClips
            .filterNot { it.filters.isDefault }
            .sortedBy(EditorClip::trackIndex)
            .forEach { layer ->
                visualClips
                    .filter {
                        it.trackIndex < layer.trackIndex &&
                                layer.timelineStartMs <= it.timelineStartMs &&
                                layer.timelineEndMs >= it.timelineEndMs
                    }
                    .forEach { baseClip ->
                        Log.i(
                            LOG_TAG,
                            "Filter layer ${layer.id} covers full window of base ${baseClip.id}"
                        )
                    }
            }
        val invalidMaskSequences = maskSequences.filter { sequence ->
            sequence.frameCount <= 0 ||
                    (sequence.staticFilePath == null && sequence.fps <= 0) ||
                    sequence.clipEndSec <= sequence.clipStartSec ||
                    visualClips.none { it.id == sequence.clipId && it.mask.isActive }
        }
        invalidMaskSequences.forEach { sequence ->
            val reasons = buildList {
                if (sequence.frameCount <= 0) add("frameCount <= 0")
                if (sequence.staticFilePath == null && sequence.fps <= 0) {
                    add("staticFilePath is null and fps <= 0")
                }
                if (sequence.clipEndSec <= sequence.clipStartSec) {
                    add("clipEndSec <= clipStartSec")
                }
                if (visualClips.none { it.id == sequence.clipId && it.mask.isActive }) {
                    add("no active masked visual clip matches clipId")
                }
            }
            Log.w(
                LOG_TAG,
                "REJECT: maskSequence invalid clipId=${sequence.clipId}, " +
                        "reason: ${reasons.joinToString()}"
            )
        }
        if (invalidMaskSequences.isNotEmpty()) {
            return null
        }
        val missingMaskSequences = visualClips.filter { clip ->
            clip.mask.isActive && maskSequences.none { it.clipId == clip.id }
        }
        missingMaskSequences.forEach { clip ->
            Log.w(
                LOG_TAG,
                "REJECT: active maskSequence missing for clip ${clip.id}"
            )
        }
        if (missingMaskSequences.isNotEmpty()) {
            return null
        }

        // The first compositor input is topmost and supplies output timestamps. Equal visual
        // track durations avoid holding or truncating a layer after its sequence ends.
        val visualTrackEndTimes = visualClipsByTrack.values.map { trackClips ->
            trackClips.maxOf(EditorClip::timelineEndMs)
        }
        if (visualTrackEndTimes.distinct().size > 1) {
            val trackEndTimes = visualClipsByTrack.mapValues { (_, trackClips) ->
                trackClips.maxOf(EditorClip::timelineEndMs)
            }
            Log.w(
                LOG_TAG,
                "REJECT: visual track end times differ: $trackEndTimes"
            )
            return null
        }

        val sequences = mutableListOf<EditedMediaItemSequence>()
        visualClipsByTrack.entries
            .sortedByDescending { it.key }
            .forEach { (_, trackClips) ->
                val items = trackClips
                    .sortedBy(EditorClip::timelineStartMs)
                    .map { clip ->
                        buildVisualItem(
                            clip = clip,
                            filterLayers = filterLayerClips.filter { layer ->
                                layer.trackIndex > clip.trackIndex &&
                                        !layer.filters.isDefault &&
                                        layer.timelineStartMs <= clip.timelineStartMs &&
                                        layer.timelineEndMs >= clip.timelineEndMs
                            }.sortedBy(EditorClip::trackIndex),
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
        filterLayers: List<EditorClip>,
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
        filterLayers.forEach { layer ->
            videoEffects.add(FilterLayerGlEffect(layer.filters))
        }
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

    private fun unsupportedVisualClipReason(clip: EditorClip): String? {
        if (!clip.isVisualClip || clip.isAudio || clip.trackIndex < 0) {
            return "not a visual clip or invalid trackIndex"
        }
        if (clip.isTextClip || clip.isEffectClip || clip.isAdjustmentClip ||
            clip.isOverlayClip || clip.isColorMatteClip || clip.isSpotlightClip ||
            clip.isVisualizerClip || clip.isAudioEffectClip
        ) return "clip is a text/effect/adjustment/overlay/matte/spotlight/visualizer/audio-effect layer"

        if (!clip.type.startsWith("video/") && !clip.type.startsWith("image/")) {
            return "unsupported media type ${clip.type}"
        }
        if (clip.effectState != null || clip.textState != null ||
            clip.transition?.isActive == true ||
            clip.layerShadow.enabled || clip.overlay.type != "none" ||
            clip.chromaKey.isActive || clip.spotlight != null ||
            clip.visualizer != null || clip.freeze != null ||
            clip.lightLeak != null
        ) return "unsupported effect, text, transition, shadow, overlay, key, spotlight, visualizer, freeze, or light leak state"

        if (clip.rotation != 0f) return "rotation is non-zero"
        if (clip.keyframes.values.any { it.isNotEmpty() }) return "keyframes are present"
        if (clip.sourceStartMs < 0L || clip.sourceEndMs <= clip.sourceStartMs) {
            return "invalid source trim range"
        }
        if (!clip.speed.isFinite() || clip.speed <= 0f) return "invalid playback speed"

        // Blend controls remain deferred to 8d-B2c.
        if (clip.blendMode != "normal" || clip.blendOpacity != 100f) {
            return "blend mode or opacity is not default"
        }

        // Retain embedded audio only when it needs no clip-level processing.
        if (clip.isMuted || clip.volume != 1f ||
            clip.audioFx != "none" || clip.soundFx != "none" ||
            clip.audioAdjustments != AudioAdjustmentSettings()
        ) return "clip audio is muted or processed"

        return null
    }

    private fun logRejectionReason(clip: EditorClip) {
        if (!clip.isVisualClip) Log.w(LOG_TAG, "  - not visual: ${clip.type}")
        if (clip.isAudio) Log.w(LOG_TAG, "  - isAudio")
        if (clip.trackIndex < 0) Log.w(LOG_TAG, "  - trackIndex<0")
        if (clip.isTextClip) Log.w(LOG_TAG, "  - isTextClip")
        if (clip.isEffectClip) Log.w(LOG_TAG, "  - isEffectClip")
        if (clip.isAdjustmentClip) Log.w(LOG_TAG, "  - isAdjustmentClip")
        if (clip.isOverlayClip) Log.w(LOG_TAG, "  - isOverlayClip")
        if (clip.isColorMatteClip) Log.w(LOG_TAG, "  - isColorMatteClip")
        if (clip.isSpotlightClip) Log.w(LOG_TAG, "  - isSpotlightClip")
        if (clip.isVisualizerClip) Log.w(LOG_TAG, "  - isVisualizerClip")
        if (clip.isFilterLayerClip) Log.w(LOG_TAG, "  - isFilterLayerClip")
        if (clip.isAudioEffectClip) Log.w(LOG_TAG, "  - isAudioEffectClip")
        if (!clip.type.startsWith("video/") && !clip.type.startsWith("image/")) {
            Log.w(LOG_TAG, "  - unsupported media type: ${clip.type}")
        }
        if (clip.effectState != null) Log.w(LOG_TAG, "  - effectState != null")
        if (clip.textState != null) Log.w(LOG_TAG, "  - textState != null")
        if (clip.transition?.isActive == true) Log.w(LOG_TAG, "  - transition active")
        if (clip.layerShadow.enabled) Log.w(LOG_TAG, "  - layerShadow enabled")
        if (clip.overlay.type != "none") Log.w(LOG_TAG, "  - overlay: ${clip.overlay.type}")
        if (clip.chromaKey.isActive) Log.w(LOG_TAG, "  - chromaKey active")
        if (clip.spotlight != null) Log.w(LOG_TAG, "  - spotlight != null")
        if (clip.visualizer != null) Log.w(LOG_TAG, "  - visualizer != null")
        if (clip.freeze != null) Log.w(LOG_TAG, "  - freeze != null")
        if (clip.lightLeak != null) Log.w(LOG_TAG, "  - lightLeak != null")
        if (clip.rotation != 0f) Log.w(LOG_TAG, "  - rotation: ${clip.rotation}")
        if (clip.keyframes.values.any { it.isNotEmpty() }) {
            Log.w(LOG_TAG, "  - keyframes non-empty")
        }
        if (clip.sourceStartMs < 0L) Log.w(LOG_TAG, "  - sourceStartMs < 0")
        if (clip.sourceEndMs <= clip.sourceStartMs) {
            Log.w(LOG_TAG, "  - sourceEnd invalid")
        }
        if (!clip.speed.isFinite() || clip.speed <= 0f) {
            Log.w(LOG_TAG, "  - speed invalid: ${clip.speed}")
        }
        if (clip.blendMode != "normal") Log.w(LOG_TAG, "  - blendMode: ${clip.blendMode}")
        if (clip.blendOpacity != 100f) {
            Log.w(LOG_TAG, "  - blendOpacity: ${clip.blendOpacity}")
        }
        if (clip.isMuted) Log.w(LOG_TAG, "  - isMuted")
        if (clip.volume != 1f) Log.w(LOG_TAG, "  - volume: ${clip.volume}")
        if (clip.audioFx != "none") Log.w(LOG_TAG, "  - audioFx: ${clip.audioFx}")
        if (clip.soundFx != "none") Log.w(LOG_TAG, "  - soundFx: ${clip.soundFx}")
        if (clip.audioAdjustments != AudioAdjustmentSettings()) {
            Log.w(LOG_TAG, "  - audioAdjustments non-default")
        }
    }

    private fun logAudioRejectionReason(clip: EditorClip) {
        if (!clip.isAudio) Log.w(LOG_TAG, "  - isAudio=false")
        if (!clip.type.startsWith("audio/")) Log.w(LOG_TAG, "  - unsupported audio type: ${clip.type}")
        if (clip.trackIndex < 0) Log.w(LOG_TAG, "  - trackIndex<0")
        if (clip.speed != 1f) Log.w(LOG_TAG, "  - audio speed: ${clip.speed}")
        if (clip.volume != 1f) Log.w(LOG_TAG, "  - volume: ${clip.volume}")
        if (clip.isMuted) Log.w(LOG_TAG, "  - isMuted")
        if (clip.audioFx != "none") Log.w(LOG_TAG, "  - audioFx: ${clip.audioFx}")
        if (clip.soundFx != "none") Log.w(LOG_TAG, "  - soundFx: ${clip.soundFx}")
        if (clip.audioAdjustments != AudioAdjustmentSettings()) {
            Log.w(LOG_TAG, "  - audioAdjustments non-default")
        }
        if (clip.keyframes.values.any { it.isNotEmpty() }) {
            Log.w(LOG_TAG, "  - keyframes non-empty")
        }
        if (clip.sourceStartMs < 0L) Log.w(LOG_TAG, "  - sourceStartMs < 0")
        if (clip.sourceEndMs <= clip.sourceStartMs) {
            Log.w(LOG_TAG, "  - sourceEnd invalid")
        }
    }

    private fun unsupportedAudioClipReason(clip: EditorClip): String? {
        if (!clip.isAudio || !clip.type.startsWith("audio/") || clip.trackIndex < 0) {
            return "not an audio clip or invalid media type/trackIndex"
        }
        if (clip.speed != 1f || clip.volume != 1f || clip.isMuted) {
            return "audio speed, volume, or mute state is not default"
        }
        if (clip.audioFx != "none" || clip.soundFx != "none" ||
            clip.audioAdjustments != AudioAdjustmentSettings()
        ) return "audio effects or adjustments are present"
        if (clip.keyframes.values.any { it.isNotEmpty() }) return "keyframes are present"
        if (clip.sourceStartMs < 0L || clip.sourceEndMs <= clip.sourceStartMs) {
            return "invalid source trim range"
        }
        return null
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

    private companion object {
        const val LOG_TAG = "MEDIA3_COMPOSITION"
    }
}

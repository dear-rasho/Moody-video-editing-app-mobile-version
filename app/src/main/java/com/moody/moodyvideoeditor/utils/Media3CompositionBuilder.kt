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
     * Builds a composition for non-overlapping tracks with no unsupported editing features.
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

        val adjustmentLayerClips = clips.filter(EditorClip::isAdjustmentClip)
        val filterLayerClips = clips.filter(EditorClip::isFilterLayerClip)
        val compositionClips = clips.filterNot {
            it.isTextClip || it.isFilterLayerClip || it.isAdjustmentClip
        }
        if (clips.any(EditorClip::isTextClip) && textSequences.isEmpty()) {
            Log.w(LOG_TAG, "REJECT: text clips exist but no textSequences")
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
            Log.i(LOG_TAG, "Visual track $trackIndex clips:")
            sortedClips.forEach { clip ->
                Log.i(
                    LOG_TAG,
                    "  clip ${clip.id}: timelineStart=${clip.timelineStartMs}ms, " +
                            "timelineEnd=${clip.timelineEndMs}ms"
                )
            }
            val gaps = computeGaps(sortedClips)
            if (gaps == null) {
                trackRejected = true
            } else {
                Log.i(LOG_TAG, "Gaps computed: ${formatGaps(gaps)}")
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
            val gaps = computeGaps(sortedClips)
            if (gaps == null) {
                trackRejected = true
            } else if (gaps.isNotEmpty()) {
                Log.i(
                    LOG_TAG,
                    "Audio track $trackIndex gaps computed: ${formatGaps(gaps)}"
                )
            }
            hasAudioTrackRejection = hasAudioTrackRejection || trackRejected
        }
        if (hasAudioTrackRejection) {
            Log.w(LOG_TAG, "REJECT: audio track validation failed")
            return null
        }

        val visualClips = visualClipsByTrack.values.flatten()
        val invalidAdjustmentLayer = adjustmentLayerClips.firstOrNull { layer ->
            val hasAdjustment = !layer.adjustments.isDefault
            val hasColorWheel = layer.colorWheel.hasAnyChange
            if (!hasAdjustment && !hasColorWheel) {
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
                        targetBaseClips.isEmpty()
            }
        }
        if (invalidAdjustmentLayer != null) {
            val targetBaseClips = visualClips.filter {
                it.trackIndex < invalidAdjustmentLayer.trackIndex &&
                        it.timelineEndMs > invalidAdjustmentLayer.timelineStartMs &&
                        it.timelineStartMs < invalidAdjustmentLayer.timelineEndMs
            }
            val reason = when {
                invalidAdjustmentLayer.trackIndex < 0 -> "invalid trackIndex"
                invalidAdjustmentLayer.layerOpacity != 100f -> "layer opacity is not default"
                invalidAdjustmentLayer.keyframes.values.any { it.isNotEmpty() } ->
                    "adjustment layer keyframes are not supported"
                targetBaseClips.isEmpty() -> "no overlapping visual clips on a lower track"
                else -> "unsupported adjustment layer configuration"
            }
            Log.w(
                LOG_TAG,
                "REJECT: adjustment layer ${invalidAdjustmentLayer.id} is unsupported, " +
                        "reason: $reason"
            )
            return null
        }

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
                        targetBaseClips.isEmpty()
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
                else -> "unsupported filter layer configuration"
            }
            Log.w(
                LOG_TAG,
                "REJECT: filter layer ${invalidFilterLayer.id} is unsupported, reason: $reason"
            )
            return null
        }
        val unsupportedPartialLayer = (adjustmentLayerClips.filter {
            !it.adjustments.isDefault || it.colorWheel.hasAnyChange
        } + filterLayerClips.filterNot { it.filters.isDefault })
            .asSequence()
            .flatMap { layer ->
                visualClips.asSequence()
                    .filter { baseClip ->
                        baseClip.trackIndex < layer.trackIndex &&
                                layer.timelineEndMs > baseClip.timelineStartMs &&
                                layer.timelineStartMs < baseClip.timelineEndMs
                    }
                    .filter { baseClip ->
                        (layer.timelineStartMs > baseClip.timelineStartMs ||
                                layer.timelineEndMs < baseClip.timelineEndMs) &&
                                (baseClip.speed != 1f ||
                                        baseClip.durationMs != baseClip.sourceDurationMs)
                    }
                    .map { baseClip -> Triple(layer, baseClip, layer.isAdjustmentClip) }
            }
            .firstOrNull()
        if (unsupportedPartialLayer != null) {
            val (layer, baseClip, isAdjustmentLayer) = unsupportedPartialLayer
            Log.w(
                LOG_TAG,
                "REJECT: partial ${if (isAdjustmentLayer) "adjustment" else "filter"} layer " +
                        "${layer.id} over base clip ${baseClip.id} " +
                        "cannot be split with 1:1 source timing; speed=${baseClip.speed}, " +
                        "timelineDuration=${baseClip.durationMs}ms, " +
                        "sourceDuration=${baseClip.sourceDurationMs}ms"
            )
            return null
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
                val sortedClips = trackClips.sortedBy(EditorClip::timelineStartMs)
                val gaps = checkNotNull(computeGaps(sortedClips))
                val sequenceBuilder = EditedMediaItemSequence.Builder()
                if (gaps.isNotEmpty()) {
                    sequenceBuilder
                        .experimentalSetForceVideoTrack(true)
                        .experimentalSetForceAudioTrack(true)
                }

                var cursorMs = 0L
                var gapIndex = 0
                sortedClips.forEach { baseClip ->
                    if (gapIndex < gaps.size && gaps[gapIndex].first == cursorMs) {
                        val gap = gaps[gapIndex++]
                        val gapDurationMs = gap.last - gap.first + 1L
                        sequenceBuilder.addGap(gapDurationMs * 1_000L)
                        cursorMs += gapDurationMs
                    }

                    val overlappingAdjustments = adjustmentLayerClips
                        .filter { layer ->
                            (!layer.adjustments.isDefault || layer.colorWheel.hasAnyChange) &&
                                    layer.trackIndex > baseClip.trackIndex &&
                                    layer.timelineEndMs > baseClip.timelineStartMs &&
                                    layer.timelineStartMs < baseClip.timelineEndMs
                        }
                        .sortedBy(EditorClip::trackIndex)
                    val overlappingFilters = filterLayerClips
                        .filter { layer ->
                            !layer.filters.isDefault &&
                                    layer.trackIndex > baseClip.trackIndex &&
                                    layer.timelineEndMs > baseClip.timelineStartMs &&
                                    layer.timelineStartMs < baseClip.timelineEndMs
                        }
                        .sortedBy(EditorClip::trackIndex)
                    Log.i(
                        LOG_TAG,
                        "Base clip ${baseClip.id} " +
                                "[${baseClip.timelineStartMs}-${baseClip.timelineEndMs}ms] " +
                                "overlapping adjustments: ${overlappingAdjustments.joinToString {
                                    "${it.id}[${it.timelineStartMs}-${it.timelineEndMs}]"
                                }} filters: ${overlappingFilters.joinToString {
                                    "${it.id}[${it.timelineStartMs}-${it.timelineEndMs}]"
                                }}"
                    )

                    val segments = splitClipAtLayerBoundaries(
                        baseClip = baseClip,
                        adjustmentLayers = overlappingAdjustments,
                        filterLayers = overlappingFilters
                    )
                    Log.i(
                        LOG_TAG,
                        "Base clip ${baseClip.id} split into ${segments.size} segments"
                    )
                    segments.forEach { segment ->
                        Log.i(
                            LOG_TAG,
                            "  [${segment.clip.timelineStartMs}-${segment.timelineEndMs}ms] " +
                                    "source=[${segment.clip.sourceStartMs}-" +
                                    "${segment.clip.sourceEndMs}ms] filters=" +
                                    segment.filterLayers.joinToString { it.id } +
                                    " adjustments=" +
                                    segment.adjustmentLayers.joinToString { it.id }
                        )
                        sequenceBuilder.addItem(
                            buildVisualItem(
                                clip = segment.clip,
                                timelineEndMs = segment.timelineEndMs,
                                adjustmentLayers = segment.adjustmentLayers,
                                filterLayers = segment.filterLayers,
                                textSequences = textSequences,
                                maskSequences = maskSequences,
                                fps = fps,
                                timestampOffsetSec = timestampOffsetSec,
                                outputWidth = outputWidth,
                                outputHeight = outputHeight
                            )
                        )
                    }
                    cursorMs = baseClip.timelineEndMs
                }
                sequences += sequenceBuilder.build()
            }

        audioClipsByTrack.entries
            .sortedBy { it.key }
            .forEach { (_, trackClips) ->
                val sortedClips = trackClips.sortedBy(EditorClip::timelineStartMs)
                val gaps = checkNotNull(computeGaps(sortedClips))
                val sequenceBuilder = EditedMediaItemSequence.Builder()
                if (gaps.isNotEmpty()) {
                    sequenceBuilder.experimentalSetForceAudioTrack(true)
                }

                var cursorMs = 0L
                var gapIndex = 0
                sortedClips.forEach { clip ->
                    if (gapIndex < gaps.size && gaps[gapIndex].first == cursorMs) {
                        val gap = gaps[gapIndex++]
                        val gapDurationMs = gap.last - gap.first + 1L
                        sequenceBuilder.addGap(gapDurationMs * 1_000L)
                        cursorMs += gapDurationMs
                    }
                    sequenceBuilder.addItem(buildAudioItem(clip))
                    cursorMs = clip.timelineEndMs
                }
                sequences += sequenceBuilder.build()
            }

        return Composition.Builder(sequences).build()
    }

    private fun buildVisualItem(
        clip: EditorClip,
        timelineEndMs: Long,
        adjustmentLayers: List<EditorClip>,
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
        adjustmentLayers.forEach { layer ->
            videoEffects.add(AdjustmentGlEffect(layer.adjustments))
            videoEffects.add(AdjustmentSpatialGlEffect(layer.adjustments))
        }
        videoEffects.add(FilterLayerGlEffect(clip.filters))
        filterLayers.forEach { layer ->
            videoEffects.add(FilterLayerGlEffect(layer.filters))
        }
        videoEffects.add(ColorWheelGlEffect(clip.colorWheel))
        adjustmentLayers
            .filter { it.colorWheel.hasAnyChange }
            .forEach { layer ->
                videoEffects.add(ColorWheelGlEffect(layer.colorWheel))
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
                sequence.startSec < timelineEndMs / 1000.0 &&
                        sequence.endSec > clip.timelineStartMs / 1000.0
            }
            .forEach { sequence ->
                val clipStartSec = clip.timelineStartMs / 1000.0
                videoEffects.add(
                    TextOverlayGlEffect(
                        sequence.copy(
                            startSec = sequence.startSec - clipStartSec,
                            endSec = sequence.endSec - clipStartSec
                        )
                    )
                )
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
        if (clip.isTextClip || clip.isEffectClip ||
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

    private data class VisualClipSegment(
        val clip: EditorClip,
        val timelineEndMs: Long,
        val adjustmentLayers: List<EditorClip>,
        val filterLayers: List<EditorClip>
    )

    private fun splitClipAtLayerBoundaries(
        baseClip: EditorClip,
        adjustmentLayers: List<EditorClip>,
        filterLayers: List<EditorClip>
    ): List<VisualClipSegment> {
        if (adjustmentLayers.isEmpty() && filterLayers.isEmpty()) {
            return listOf(
                VisualClipSegment(
                    clip = baseClip,
                    timelineEndMs = baseClip.timelineEndMs,
                    adjustmentLayers = emptyList(),
                    filterLayers = emptyList()
                )
            )
        }

        val allLayers = adjustmentLayers + filterLayers
        if (allLayers.all { layer ->
                layer.timelineStartMs <= baseClip.timelineStartMs &&
                        layer.timelineEndMs >= baseClip.timelineEndMs
            }
        ) {
            return listOf(
                VisualClipSegment(
                    clip = baseClip,
                    timelineEndMs = baseClip.timelineEndMs,
                    adjustmentLayers = adjustmentLayers.sortedBy(EditorClip::trackIndex),
                    filterLayers = filterLayers.sortedBy(EditorClip::trackIndex)
                )
            )
        }

        check(baseClip.speed == 1f && baseClip.durationMs == baseClip.sourceDurationMs)

        val boundaries = sortedSetOf(baseClip.timelineStartMs, baseClip.timelineEndMs)
        allLayers.forEach { layer ->
            if (layer.timelineStartMs > baseClip.timelineStartMs &&
                layer.timelineStartMs < baseClip.timelineEndMs
            ) {
                boundaries.add(layer.timelineStartMs)
            }
            if (layer.timelineEndMs > baseClip.timelineStartMs &&
                layer.timelineEndMs < baseClip.timelineEndMs
            ) {
                boundaries.add(layer.timelineEndMs)
            }
        }

        val sortedBoundaries = boundaries.toList()
        return (0 until sortedBoundaries.lastIndex).map { index ->
            val segmentStartMs = sortedBoundaries[index]
            val segmentEndMs = sortedBoundaries[index + 1]
            val sourceOffsetAtStartMs = segmentStartMs - baseClip.timelineStartMs
            val sourceOffsetAtEndMs = segmentEndMs - baseClip.timelineStartMs
            val segmentClip = baseClip.copy(
                sourceStartMs = baseClip.sourceStartMs + sourceOffsetAtStartMs,
                sourceEndMs = baseClip.sourceStartMs + sourceOffsetAtEndMs,
                timelineStartMs = segmentStartMs
            )
            fun coversSegment(layer: EditorClip): Boolean =
                layer.timelineStartMs <= segmentStartMs &&
                        layer.timelineEndMs >= segmentEndMs

            VisualClipSegment(
                clip = segmentClip,
                timelineEndMs = segmentEndMs,
                adjustmentLayers = adjustmentLayers
                    .filter(::coversSegment)
                    .sortedBy(EditorClip::trackIndex),
                filterLayers = filterLayers
                    .filter(::coversSegment)
                    .sortedBy(EditorClip::trackIndex)
            )
        }
    }

    private fun computeGaps(clips: List<EditorClip>): List<LongRange>? {
        val gaps = mutableListOf<LongRange>()
        var expectedStartMs = 0L
        for (clip in clips) {
            if (clip.timelineStartMs < 0L || clip.timelineEndMs <= clip.timelineStartMs) {
                Log.w(
                    LOG_TAG,
                    "REJECT: clip ${clip.id} has invalid timeline window " +
                            "${clip.timelineStartMs}-${clip.timelineEndMs}ms"
                )
                return null
            }
            if (clip.timelineStartMs < expectedStartMs) {
                Log.w(
                    LOG_TAG,
                    "REJECT: overlapping clips; clip ${clip.id} starts at " +
                            "${clip.timelineStartMs}ms before previous clip ends at " +
                            "${expectedStartMs}ms"
                )
                return null
            }
            if (clip.timelineStartMs > expectedStartMs) {
                gaps.add(expectedStartMs until clip.timelineStartMs)
            }
            expectedStartMs = clip.timelineEndMs
        }
        return gaps
    }

    private fun formatGaps(gaps: List<LongRange>): String =
        gaps.joinToString(prefix = "[", postfix = "]") {
            "${it.first}..${it.last + 1L}ms"
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

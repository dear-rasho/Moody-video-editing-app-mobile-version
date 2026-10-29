package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.util.Log
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.viewmodel.EditorViewModel
import com.moody.moodyvideoeditor.viewmodel.TimedTextClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

object CaptionGenerator {

    private const val TAG = "CaptionGenerator"

    enum class GroupingMode(val key: String, val label: String, val icon: String) {
        WORDS("words", "Words", "🔤"),
        LINES("lines", "Lines", "📝")
    }

    data class Result(
        val success: Boolean,
        val segmentsAdded: Int,
        val detectedLanguage: String,
        val segments: List<CaptionSegment> = emptyList(),
        val errorMessage: String? = null
    )

    suspend fun generate(
        context: Context,
        clip: EditorClip,
        language: String,
        modelFile: String,
        grouping: GroupingMode,
        wordsPerLayer: Int = 2,
        wordsPerLine: Int = 1,
        viewModel: EditorViewModel,
        onStatus: (String) -> Unit,
        onProgress: (Float) -> Unit,
        captionPresetId: String? = CaptionPresetLibrary.presets.first().id,
        highlightColor: Long = 0xFF00D9FFL
    ): Result {
        try {
            // ─── Load model ───
            onStatus("Loading model…")
            onProgress(0.05f)

            val modelFileRef = WhisperModelManager.getModelFile(context, modelFile)
            if (!modelFileRef.exists()) {
                return Result(false, 0, "", emptyList(), "Model not downloaded")
            }

            val loaded = withContext(Dispatchers.IO) {
                WhisperEngine.loadModel(modelFileRef)
            }
            if (!loaded) {
                return Result(false, 0, "", emptyList(), "Model load failed")
            }
            onProgress(0.15f)

            val durationSec = clip.sourceDurationMs.coerceAtLeast(0L) / 1000f
            if (durationSec < 0.5f) {
                return Result(false, 0, "", emptyList(), "Audio too short (< 0.5s)")
            }

            onProgress(0.30f)

            val allTimelineSegments = mutableListOf<CaptionSegment>()
            var detectedLanguage = "unknown"
            var transcriptionLanguage = language
            var totalAdded = 0

            onStatus("Preparing audio chunks…")
            val extractionSucceeded = WhisperAudioExtractor.forEachPcmChunk(
                context = context,
                uri = clip.uri,
                sourceStartMs = clip.sourceStartMs,
                sourceDurationMs = clip.sourceDurationMs,
                chunkDurationMs = TRANSCRIPTION_CHUNK_SECONDS * 1000L
            ) { index, chunkCount, chunkStartMs, chunkPcm ->
                currentCoroutineContext().ensureActive()
                withContext(Dispatchers.Main) {
                    onStatus(
                        "Transcribing ${index + 1}/$chunkCount " +
                                "(${"%.1f".format(chunkStartMs / 1000f)}s / " +
                                "${"%.1f".format(durationSec)}s) · $totalAdded layers added"
                    )
                }
                val result = withContext(Dispatchers.Default) {
                    WhisperEngine.transcribe(chunkPcm, transcriptionLanguage)
                }
                currentCoroutineContext().ensureActive()
                if (result.language != "unknown") {
                    detectedLanguage = result.language
                    if (language.equals("auto", ignoreCase = true)) {
                        transcriptionLanguage = result.language
                    }
                }

                val absoluteSegments = result.segments.map { segment ->
                    segment.copy(
                        startMs = segment.startMs + chunkStartMs,
                        endMs = segment.endMs + chunkStartMs
                    )
                }
                val effectiveLanguage = if (language.equals("auto", ignoreCase = true)) {
                    result.language.takeUnless { it == "unknown" } ?: transcriptionLanguage
                } else {
                    language
                }
                val processedSegments = if (
                    effectiveLanguage == "ur" ||
                    effectiveLanguage == "hi" ||
                    effectiveLanguage == "ar"
                ) {
                    absoluteSegments.map { segment ->
                        segment.copy(text = Transliterator.toRoman(segment.text))
                    }
                } else {
                    absoluteSegments
                }
                val timelineSegments = fitSegmentsToClipDuration(
                    processedSegments,
                    clip.durationMs,
                    clip.speed
                )
                withContext(Dispatchers.Main) {
                    allTimelineSegments += timelineSegments
                    val textClips = mutableListOf<TimedTextClip>()
                    timelineSegments.forEach { segment ->
                        currentCoroutineContext().ensureActive()
                        try {
                            val presetId =
                                captionPresetId ?: CaptionPresetLibrary.presets.first().id
                            val chunks = buildPresetChunks(
                                text = segment.text,
                                startMs = clip.timelineStartMs + segment.startMs,
                                endMs = clip.timelineStartMs + segment.endMs,
                                grouping = grouping,
                                wordsPerLayer = wordsPerLayer,
                                wordsPerLine = wordsPerLine
                            )
                            chunks.forEach { (text, start, end) ->
                                textClips += TimedTextClip(
                                    textState = CaptionPresetLibrary.applyTo(
                                        text, presetId, highlightColor
                                    ),
                                    startMs = start,
                                    endMs = end
                                )
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (t: Throwable) {
                            Log.e(TAG, "Failed to add \"${segment.text}\"", t)
                        }
                    }
                    viewModel.createTextClipsAtTimes(textClips)
                    totalAdded += textClips.size
                    onProgress(
                        0.30f + 0.55f * (index + 1).toFloat() / chunkCount.coerceAtLeast(1)
                    )
                    onStatus("Added $totalAdded caption layers")
                }
            }

            if (!extractionSucceeded) {
                if (totalAdded == 0) {
                    return Result(false, 0, detectedLanguage, emptyList(), "Audio extraction failed")
                }
                onProgress(1f)
                return Result(
                    true,
                    totalAdded,
                    detectedLanguage,
                    allTimelineSegments.sortedBy { it.startMs },
                    "Audio extraction stopped after partial caption generation"
                )
            }

            if (allTimelineSegments.isEmpty()) {
                onProgress(1f)
                return Result(
                    false, 0, detectedLanguage, emptyList(),
                    "No speech detected in the selected clip range"
                )
            }

            onProgress(1f)
            Log.e(TAG, "🎉 Complete: $totalAdded clips added")

            return Result(
                success = true,
                segmentsAdded = totalAdded,
                detectedLanguage = detectedLanguage,
                segments = allTimelineSegments.sortedBy { it.startMs }
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            Log.e(TAG, "💥 generate failed", t)
            return Result(false, 0, "", emptyList(), t.message ?: "Unknown error")
        }
    }

    private const val TRANSCRIPTION_CHUNK_SECONDS = 30

    internal fun fitSegmentsToClipDuration(
        segments: List<CaptionSegment>,
        clipDurationMs: Long,
        speed: Float
    ): List<CaptionSegment> {
        if (clipDurationMs <= 0L) return emptyList()
        val playbackSpeed = speed.takeIf { it.isFinite() && it > 0f } ?: 1f
        return segments.mapNotNull { segment ->
            val startMs = (segment.startMs / playbackSpeed).toLong()
                .coerceIn(0L, clipDurationMs)
            val endMs = (segment.endMs / playbackSpeed).toLong()
                .coerceIn(0L, clipDurationMs)
            if (endMs <= startMs) null
            else segment.copy(startMs = startMs, endMs = endMs)
        }
    }

    internal fun buildPresetChunks(
        text: String,
        startMs: Long,
        endMs: Long,
        grouping: GroupingMode,
        wordsPerLayer: Int,
        wordsPerLine: Int
    ): List<Triple<String, Long, Long>> {
        val words = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty()) return emptyList()
        val chunkSize = when (grouping) {
            GroupingMode.WORDS -> wordsPerLayer.coerceIn(1, 7)
            GroupingMode.LINES -> wordsPerLine.coerceIn(1, 5)
        }
        val chunks = words.chunked(chunkSize)
        val totalDuration = (endMs - startMs).coerceAtLeast(chunks.size.toLong())
        val chunkDuration = totalDuration / chunks.size
        return chunks.mapIndexed { index, chunk ->
            val chunkStart = startMs + index * chunkDuration
            val chunkEnd = if (index == chunks.lastIndex) endMs
            else startMs + (index + 1) * chunkDuration
            Triple(chunk.joinToString(" "), chunkStart, chunkEnd)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  MANUAL CAPTIONS
    // ═══════════════════════════════════════════════════════════

    suspend fun generateFromManual(
        parsedCaptions: List<ManualCaptionParser.ParsedCaption>,
        grouping: GroupingMode,
        wordsPerLayer: Int = 2,
        wordsPerLine: Int = 1,
        viewModel: EditorViewModel,
        offsetMs: Long = 0L,
        onProgress: (Float) -> Unit = {},
        captionPresetId: String? = CaptionPresetLibrary.presets.first().id,
        highlightColor: Long = 0xFF00D9FFL
    ): Result {
        if (parsedCaptions.isEmpty()) {
            return Result(false, 0, "", emptyList(), "No valid captions found")
        }

        var totalAdded = 0
        val allSegments = mutableListOf<CaptionSegment>()
        val textClips = mutableListOf<TimedTextClip>()

        parsedCaptions.forEachIndexed { idx, cap ->
            val segStart = offsetMs + cap.startMs
            val segEnd = offsetMs + cap.endMs
            if (cap.text.isBlank()) return@forEachIndexed

            allSegments.add(
                CaptionSegment(
                    startMs = cap.startMs,
                    endMs = cap.endMs,
                    text = cap.text
                )
            )

            try {
                val presetId = captionPresetId ?: CaptionPresetLibrary.presets.first().id
                val chunks = buildPresetChunks(
                    text = cap.text,
                    startMs = segStart,
                    endMs = segEnd,
                    grouping = grouping,
                    wordsPerLayer = wordsPerLayer,
                    wordsPerLine = wordsPerLine
                )
                chunks.forEach { (text, start, end) ->
                    textClips += TimedTextClip(
                        textState = CaptionPresetLibrary.applyTo(
                            text, presetId, highlightColor
                        ),
                        startMs = start,
                        endMs = end
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Manual caption failed: \"${cap.text}\"", t)
            }

            onProgress((idx + 1).toFloat() / parsedCaptions.size)
        }

        viewModel.createTextClipsAtTimes(textClips)
        totalAdded = textClips.size
        Log.e(TAG, "✅ Manual: $totalAdded clips from ${parsedCaptions.size} captions")

        return Result(
            success = totalAdded > 0,
            segmentsAdded = totalAdded,
            detectedLanguage = "",
            segments = allSegments
        )
    }
}

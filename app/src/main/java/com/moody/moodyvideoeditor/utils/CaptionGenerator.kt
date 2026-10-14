package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.util.Log
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.TextState
import com.moody.moodyvideoeditor.viewmodel.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CaptionGenerator {

    private const val TAG = "CaptionGenerator"

    enum class GroupingMode(val key: String, val label: String, val icon: String) {
        WORD("word", "Word by Word", "🔤"),
        PHRASE("phrase", "Short Phrase (3-5)", "📝"),
        SENTENCE("sentence", "Sentence", "📄")
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
        viewModel: EditorViewModel,
        onStatus: (String) -> Unit,
        onProgress: (Float) -> Unit
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

            // ─── Extract PCM ───
            onStatus("Extracting audio…")
            val pcm = withContext(Dispatchers.IO) {
                WhisperAudioExtractor.extractPcm(context, clip.uri)
            }
            if (pcm == null || pcm.isEmpty()) {
                return Result(false, 0, "", emptyList(), "Audio extraction failed")
            }

            val durationSec = pcm.size.toFloat() / 16000f
            Log.e(
                TAG,
                "📊 Audio: ${pcm.size} samples, " +
                        "${"%.2f".format(durationSec)}s"
            )

            if (durationSec < 0.5f) {
                return Result(
                    false, 0, "", emptyList(),
                    "Audio too short (< 0.5s)"
                )
            }

            onProgress(0.30f)

            // ─── Transcribe (single-pass) ───
            onStatus("Transcribing… (${"%.1f".format(durationSec)}s)")
            onProgress(0.35f)

            val result = withContext(Dispatchers.Default) {
                WhisperEngine.transcribe(pcm, language)
            }

            onProgress(0.85f)

            if (result.isEmpty) {
                onProgress(1f)
                Log.e(TAG, "❌ No segments (lang=${result.language})")
                return Result(
                    success = false,
                    segmentsAdded = 0,
                    detectedLanguage = result.language,
                    segments = emptyList(),
                    errorMessage = "No speech detected — audio may be silent, " +
                            "too quiet, or non-verbal"
                )
            }

            Log.e(
                TAG,
                "✅ Transcribed: ${result.segments.size} raw segments, " +
                        "lang=${result.language}"
            )

            // ─── Grouping ───
            onStatus("Grouping captions…")
            val grouped = groupSegments(result.segments, grouping)
            Log.e(
                TAG,
                "🔀 Grouped ${result.segments.size} → ${grouped.size} " +
                        "(${grouping.key})"
            )

            if (grouped.isEmpty()) {
                onProgress(1f)
                return Result(
                    false, 0, result.language, emptyList(),
                    "No captions after grouping"
                )
            }

            onProgress(0.92f)

            // ─── Add text layers ───
            onStatus("Adding ${grouped.size} text layers…")
            var added = 0
            grouped.forEach { seg ->
                try {
                    viewModel.createTextClipAtTime(
                        textState = TextState(
                            content = seg.text,
                            fontSize = 40,
                            fontWeight = "bold",
                            color = 0xFFFFFFFF,
                            alignment = "center",
                            positionY = 82f,
                            backgroundEnabled = true,
                            backgroundType = "rounded",
                            backgroundColor1 = 0xFF000000L,
                            backgroundPaddingX = 16f,
                            backgroundPaddingY = 8f,
                            backgroundCornerRadius = 12f,
                            animation = "fadeIn",
                            animationDuration = 0.3f
                        ),
                        startMs = clip.timelineStartMs + seg.startMs,
                        endMs = clip.timelineStartMs + seg.endMs
                    )
                    added++
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to add \"${seg.text}\"", t)
                }
            }

            onProgress(1f)
            Log.e(TAG, "🎉 Complete: $added / ${grouped.size} layers added")

            return Result(
                success = true,
                segmentsAdded = added,
                detectedLanguage = result.language,
                segments = grouped
            )
        } catch (t: Throwable) {
            Log.e(TAG, "💥 generate failed", t)
            return Result(false, 0, "", emptyList(), t.message ?: "Unknown error")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  GROUPING
    // ═══════════════════════════════════════════════════════════

    private fun groupSegments(
        segments: List<CaptionSegment>,
        mode: GroupingMode
    ): List<CaptionSegment> {
        return when (mode) {
            GroupingMode.SENTENCE -> segments
            GroupingMode.PHRASE -> segments.flatMap { splitIntoPhrases(it, 3, 5) }
            GroupingMode.WORD -> segments.flatMap { splitIntoWords(it) }
        }
    }

    private fun splitIntoWords(seg: CaptionSegment): List<CaptionSegment> {
        val words = seg.text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        if (words.size == 1) return listOf(seg)

        val totalMs = (seg.endMs - seg.startMs).coerceAtLeast(100L)
        val perWord = totalMs / words.size

        return words.mapIndexed { i, w ->
            CaptionSegment(
                startMs = seg.startMs + i * perWord,
                endMs = seg.startMs + (i + 1) * perWord,
                text = w
            )
        }
    }

    private fun splitIntoPhrases(
        seg: CaptionSegment,
        minWords: Int,
        maxWords: Int
    ): List<CaptionSegment> {
        val words = seg.text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        if (words.size <= maxWords) return listOf(seg)

        val totalMs = (seg.endMs - seg.startMs).coerceAtLeast(100L)
        val chunks = mutableListOf<List<String>>()
        var i = 0
        while (i < words.size) {
            val remaining = words.size - i
            val size = when {
                remaining <= maxWords -> remaining
                remaining - maxWords < minWords -> remaining
                else -> maxWords
            }
            chunks.add(words.subList(i, i + size))
            i += size
        }

        val totalWords = words.size
        var cursor = seg.startMs
        return chunks.map { chunk ->
            val dur = (totalMs * chunk.size / totalWords).coerceAtLeast(100L)
            val r = CaptionSegment(
                startMs = cursor,
                endMs = (cursor + dur).coerceAtMost(seg.endMs),
                text = chunk.joinToString(" ")
            )
            cursor += dur
            r
        }
    }
}
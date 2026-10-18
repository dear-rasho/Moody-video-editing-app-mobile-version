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

    var autoStyleEnabled: Boolean = true

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
            if (durationSec < 0.5f) {
                return Result(false, 0, "", emptyList(), "Audio too short (< 0.5s)")
            }

            onProgress(0.30f)

            // ─── Transcribe ───
            onStatus("Transcribing… (${"%.1f".format(durationSec)}s, lang=$language)")
            onProgress(0.35f)

            val result = withContext(Dispatchers.Default) {
                WhisperEngine.transcribe(pcm, language)
            }

            onProgress(0.85f)

            if (result.isEmpty) {
                onProgress(1f)
                return Result(
                    false, 0, result.language, emptyList(),
                    "No speech detected"
                )
            }

            val effectiveLanguage = if (language.equals("auto", ignoreCase = true)) {
                result.language
            } else {
                language
            }
            // ═══════════════════════════════════════════════════
            //  TRANSLITERATE Urdu/Hindi → Roman
            //  If user selected Urdu/Hindi, force Roman output
            //  regardless of what script Whisper produced.
            // ═══════════════════════════════════════════════════
            val shouldTransliterate = effectiveLanguage == "ur" ||
                    effectiveLanguage == "hi" ||
                    effectiveLanguage == "ar"

            val processedSegments = if (shouldTransliterate) {
                Log.e(TAG, "🔤 Transliterating $effectiveLanguage → Roman")
                result.segments.map { seg ->
                    val roman = Transliterator.toRoman(seg.text)
                    if (roman != seg.text) {
                        Log.e(TAG, "  \"${seg.text}\" → \"$roman\"")
                    }
                    seg.copy(text = roman)
                }
            } else {
                result.segments
            }

            Log.e(
                TAG,
                "✅ Transcribed: ${result.segments.size} segments, " +
                        "detected=${result.language}, using=$effectiveLanguage"
            )

            // ─── Create text clips ───
            onStatus("Styling ${result.segments.size} captions…")
            onProgress(0.92f)

            var totalAdded = 0
            processedSegments.forEach { seg ->
            try {
                    if (autoStyleEnabled) {
                        val words = seg.text.split(Regex("\\s+"))
                            .filter { it.isNotBlank() }
                        if (words.isEmpty()) return@forEach

                        val segStart = clip.timelineStartMs + seg.startMs
                        val segEnd = clip.timelineStartMs + seg.endMs

                        val clips = when (grouping) {
                            GroupingMode.WORDS -> CaptionStyleEngine.buildWordsMode(
                                words = words,
                                segStartMs = segStart,
                                segEndMs = segEnd,
                                language = effectiveLanguage,
                                wordsPerLayer = wordsPerLayer.coerceIn(1, 7)
                            )
                            GroupingMode.LINES -> CaptionStyleEngine.buildLinesMode(
                                words = words,
                                segStartMs = segStart,
                                segEndMs = segEnd,
                                language = effectiveLanguage,
                                wordsPerLine = wordsPerLine.coerceIn(1, 5)
                            )
                        }

                        clips.forEach { sc ->
                            viewModel.createTextClipAtTime(
                                textState = sc.toTextState(),
                                startMs = sc.startMs,
                                endMs = sc.endMs
                            )
                            totalAdded++
                        }
                    } else {
                        viewModel.createTextClipAtTime(
                            textState = simpleCaption(seg.text),
                            startMs = clip.timelineStartMs + seg.startMs,
                            endMs = clip.timelineStartMs + seg.endMs
                        )
                        totalAdded++
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to add \"${seg.text}\"", t)
                }
            }

            onProgress(1f)
            Log.e(TAG, "🎉 Complete: $totalAdded clips added")

            return Result(
                success = true,
                segmentsAdded = totalAdded,
                detectedLanguage = result.language,
                segments = processedSegments
            )
        } catch (t: Throwable) {
            Log.e(TAG, "💥 generate failed", t)
            return Result(false, 0, "", emptyList(), t.message ?: "Unknown error")
        }
    }

    private fun simpleCaption(text: String) = TextState(
        content = text,
        fontSize = 40,
        fontWeight = "bold",
        color = 0xFFFFFFFF,
        alignment = "center",
        positionY = 82f,
        // 🆕 TIGHT LAYOUT — box height = character height
        lineHeight = 1.0f,           // ← default 1.2 → 1.0
        letterSpacing = 0f,
        maxWidth = 100f,             // ← full width (text wrap hoga but tight)
        backgroundEnabled = true,
        backgroundType = "rounded",
        backgroundColor1 = 0xFF000000L,
        backgroundPaddingX = 8f,     // ← pehle 16f
        backgroundPaddingY = 2f,     // ← pehle 8f (YEH sabse important)
        backgroundCornerRadius = 6f, // ← pehle 12f
        animation = "fadeIn",
        animationDuration = 0.3f
    )
    // ═══════════════════════════════════════════════════════════
    //  MANUAL CAPTIONS — auto-detect fonts per caption
    // ═══════════════════════════════════════════════════════════

    suspend fun generateFromManual(
        parsedCaptions: List<ManualCaptionParser.ParsedCaption>,
        grouping: GroupingMode,
        autoStyle: Boolean,
        wordsPerLayer: Int = 2,
        wordsPerLine: Int = 1,
        viewModel: EditorViewModel,
        offsetMs: Long = 0L,
        onProgress: (Float) -> Unit = {}
    ): Result {
        if (parsedCaptions.isEmpty()) {
            return Result(false, 0, "", emptyList(), "No valid captions found")
        }

        var totalAdded = 0
        val allSegments = mutableListOf<CaptionSegment>()

        parsedCaptions.forEachIndexed { idx, cap ->
            val segStart = offsetMs + cap.startMs
            val segEnd = offsetMs + cap.endMs
            val words = cap.text.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) return@forEachIndexed

            allSegments.add(
                CaptionSegment(
                    startMs = cap.startMs,
                    endMs = cap.endMs,
                    text = cap.text
                )
            )

            // Auto-detect language from this caption's text
            val detectedLang = CaptionLanguage.detectLanguage(cap.text)
            Log.e(TAG, "Manual caption \"${cap.text.take(30)}\" → lang=$detectedLang")

            try {
                if (!autoStyle) {
                    viewModel.createTextClipAtTime(
                        textState = simpleCaption(cap.text),
                        startMs = segStart,
                        endMs = segEnd
                    )
                    totalAdded++
                } else {
                    // Use wordsPerLayer = 1 for manual (no layer count)
                    val clips = when (grouping) {
                        GroupingMode.WORDS -> CaptionStyleEngine.buildWordsMode(
                            words = words,
                            segStartMs = segStart,
                            segEndMs = segEnd,
                            language = detectedLang,
                            wordsPerLayer = wordsPerLayer.coerceIn(1, 7)
                        )
                        GroupingMode.LINES -> CaptionStyleEngine.buildLinesMode(
                            words = words,
                            segStartMs = segStart,
                            segEndMs = segEnd,
                            language = detectedLang,
                            wordsPerLine = wordsPerLine.coerceIn(1, 5)
                        )
                    }

                    clips.forEach { sc ->
                        viewModel.createTextClipAtTime(
                            textState = sc.toTextState(),
                            startMs = sc.startMs,
                            endMs = sc.endMs
                        )
                        totalAdded++
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Manual caption failed: \"${cap.text}\"", t)
            }

            onProgress((idx + 1).toFloat() / parsedCaptions.size)
        }

        Log.e(TAG, "✅ Manual: $totalAdded clips from ${parsedCaptions.size} captions")

        return Result(
            success = totalAdded > 0,
            segmentsAdded = totalAdded,
            detectedLanguage = "",
            segments = allSegments
        )
    }
}

package com.moody.moodyvideoeditor.utils

import android.util.Log
import org.json.JSONObject
import java.io.File

object WhisperEngine {

    private const val TAG = "WhisperEngine"
    private const val SAMPLE_RATE = 16000

    // ═══════════════════════════════════════════════════════════
    //  CHUNKED TRANSCRIPTION
    //  10s chunks = smallest safe memory footprint on mobile
    // ═══════════════════════════════════════════════════════════
    private const val CHUNK_SECONDS = 10
    private const val CHUNK_SAMPLES = CHUNK_SECONDS * SAMPLE_RATE

    init {
        try {
            System.loadLibrary("moodywhisper")
            Log.e(TAG, "✅ libmoodywhisper.so loaded")
        } catch (t: Throwable) {
            Log.e(TAG, "❌ Failed to load libmoodywhisper.so", t)
        }
    }

    @Volatile
    private var contextPtr: Long = 0L

    @Volatile
    private var loadedModelPath: String? = null

    val isLoaded: Boolean get() = contextPtr != 0L

    fun loadModel(modelFile: File): Boolean {
        if (!modelFile.exists() || modelFile.length() < 1000) {
            Log.e(TAG, "❌ Model file invalid: ${modelFile.absolutePath}")
            return false
        }

        if (loadedModelPath == modelFile.absolutePath && contextPtr != 0L) {
            return true
        }

        unload()

        // ─── Memory pre-check ───
        val modelMb = modelFile.length() / 1024 / 1024
        val runtime = Runtime.getRuntime()
        val maxMb = runtime.maxMemory() / 1024 / 1024
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        Log.e(
            TAG,
            "📊 Memory: model=${modelMb}MB, heap used=${usedMb}MB / max=${maxMb}MB"
        )

        if (modelMb > 100 && maxMb < 256) {
            Log.e(TAG, "⚠️ Low heap for large model — may crash")
        }

        return try {
            contextPtr = nativeInit(modelFile.absolutePath)
            if (contextPtr == 0L) {
                Log.e(TAG, "❌ nativeInit returned 0")
                false
            } else {
                loadedModelPath = modelFile.absolutePath
                Log.e(TAG, "✅ Model loaded: ${modelFile.name}")
                true
            }
        } catch (t: Throwable) {
            Log.e(TAG, "❌ loadModel failed", t)
            false
        }
    }

    fun unload() {
        if (contextPtr != 0L) {
            try {
                nativeRelease(contextPtr)
            } catch (_: Throwable) {}
            contextPtr = 0L
            loadedModelPath = null
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  LEGACY single-pass transcribe
    // ═══════════════════════════════════════════════════════════

    fun transcribe(pcm: FloatArray, language: String): TranscriptionResult {
        if (contextPtr == 0L) {
            return TranscriptionResult("unknown", emptyList())
        }
        return try {
            val jsonStr = nativeTranscribe(contextPtr, pcm, language)
            parseResult(jsonStr, timeOffsetMs = 0L)
        } catch (t: Throwable) {
            Log.e(TAG, "transcribe failed", t)
            TranscriptionResult("unknown", emptyList())
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  CHUNKED TRANSCRIBE — 10s chunks
    //  Each chunk isolated → one crash doesn't kill all
    // ═══════════════════════════════════════════════════════════

    fun transcribeChunked(
        pcm: FloatArray,
        language: String,
        onProgress: (Float) -> Unit = {}
    ): TranscriptionResult {
        if (contextPtr == 0L) {
            Log.e(TAG, "❌ transcribeChunked: context not loaded")
            return TranscriptionResult("unknown", emptyList())
        }

        if (pcm.isEmpty()) {
            return TranscriptionResult("unknown", emptyList())
        }

        val totalSamples = pcm.size
        val totalChunks = (totalSamples + CHUNK_SAMPLES - 1) / CHUNK_SAMPLES
        val totalSec = totalSamples.toFloat() / SAMPLE_RATE

        Log.e(
            TAG,
            "🔀 Chunked: $totalSec s → $totalChunks chunks " +
                    "(${CHUNK_SECONDS}s each)"
        )

        val allSegments = mutableListOf<CaptionSegment>()
        var detectedLang = "unknown"
        var failedChunks = 0

        for (i in 0 until totalChunks) {
            val startSample = i * CHUNK_SAMPLES
            val endSample = minOf(startSample + CHUNK_SAMPLES, totalSamples)
            val chunkSize = endSample - startSample

            // Skip tiny chunks (< 0.3s)
            if (chunkSize < SAMPLE_RATE * 3 / 10) {
                Log.e(TAG, "⏭️ Chunk $i too small ($chunkSize), skip")
                onProgress((i + 1).toFloat() / totalChunks.toFloat())
                continue
            }

            val chunk = pcm.copyOfRange(startSample, endSample)
            val offsetMs = (startSample.toLong() * 1000L) / SAMPLE_RATE

            Log.e(
                TAG,
                "▶️ Chunk ${i + 1}/$totalChunks: " +
                        "samples=$chunkSize, offset=${offsetMs}ms"
            )

            val chunkResult = try {
                val jsonStr = nativeTranscribe(contextPtr, chunk, language)
                parseResult(jsonStr, timeOffsetMs = offsetMs)
            } catch (t: Throwable) {
                Log.e(TAG, "❌ Chunk $i failed: ${t.message}", t)
                failedChunks++
                TranscriptionResult("unknown", emptyList())
            }

            if (chunkResult.language != "unknown" && detectedLang == "unknown") {
                detectedLang = chunkResult.language
            }

            allSegments.addAll(chunkResult.segments)

            val progress = (i + 1).toFloat() / totalChunks.toFloat()
            onProgress(progress)
            Log.e(TAG, "✅ Chunk ${i + 1} done → progress=${(progress * 100).toInt()}%")
        }

        Log.e(
            TAG,
            "🏁 Chunked complete: ${allSegments.size} segments, " +
                    "lang=$detectedLang, failedChunks=$failedChunks"
        )

        return TranscriptionResult(detectedLang, allSegments)
    }

    private fun parseResult(
        jsonStr: String,
        timeOffsetMs: Long
    ): TranscriptionResult {
        return try {
            val obj = JSONObject(jsonStr)
            if (obj.has("error")) {
                val err = obj.getString("error")
                Log.e(TAG, "⚠️ Transcription error: $err")
                return TranscriptionResult("unknown", emptyList())
            }
            val lang = obj.optString("lang", "unknown")
            val arr = obj.optJSONArray("segments")
                ?: return TranscriptionResult(lang, emptyList())

            val segments = mutableListOf<CaptionSegment>()
            for (i in 0 until arr.length()) {
                val seg = arr.getJSONObject(i)
                val text = seg.optString("text", "").trim()
                if (text.isEmpty()) continue

                segments.add(
                    CaptionSegment(
                        startMs = seg.optLong("start", 0L) + timeOffsetMs,
                        endMs = seg.optLong("end", 0L) + timeOffsetMs,
                        text = text
                    )
                )
            }
            TranscriptionResult(lang, segments)
        } catch (t: Throwable) {
            Log.e(TAG, "parseResult failed", t)
            TranscriptionResult("unknown", emptyList())
        }
    }

    private external fun nativeInit(modelPath: String): Long
    private external fun nativeRelease(ptr: Long)
    private external fun nativeTranscribe(
        ptr: Long,
        pcm: FloatArray,
        language: String
    ): String
}
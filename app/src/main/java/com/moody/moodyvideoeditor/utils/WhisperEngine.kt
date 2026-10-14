package com.moody.moodyvideoeditor.utils

import android.util.Log
import org.json.JSONObject
import java.io.File

object WhisperEngine {

    private const val TAG = "WhisperEngine"

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
    //  SINGLE-PASS TRANSCRIBE
    //  Whisper.cpp internally chunks audio into 30s windows,
    //  so long audio (>30s) is handled automatically.
    //  Language detection works reliably in single-pass mode.
    // ═══════════════════════════════════════════════════════════

    fun transcribe(
        pcm: FloatArray,
        language: String
    ): TranscriptionResult {
        if (contextPtr == 0L) {
            Log.e(TAG, "❌ Context not loaded")
            return TranscriptionResult("unknown", emptyList())
        }

        if (pcm.isEmpty()) {
            Log.e(TAG, "❌ Empty PCM")
            return TranscriptionResult("unknown", emptyList())
        }

        Log.e(
            TAG,
            "▶️ transcribe: ${pcm.size} samples " +
                    "(${"%.2f".format(pcm.size / 16000.0)}s), lang=$language"
        )

        return try {
            val jsonStr = nativeTranscribe(contextPtr, pcm, language)
            parseResult(jsonStr)
        } catch (t: Throwable) {
            Log.e(TAG, "❌ transcribe failed", t)
            TranscriptionResult("unknown", emptyList())
        }
    }

    private fun parseResult(jsonStr: String): TranscriptionResult {
        return try {
            val obj = JSONObject(jsonStr)

            if (obj.has("error")) {
                val err = obj.getString("error")
                Log.e(TAG, "⚠️ Native error: $err")
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
                        startMs = seg.optLong("start", 0L),
                        endMs = seg.optLong("end", 0L),
                        text = text
                    )
                )
            }

            Log.e(TAG, "✅ Parsed: lang=$lang, segments=${segments.size}")
            TranscriptionResult(lang, segments)
        } catch (t: Throwable) {
            Log.e(TAG, "❌ parseResult failed", t)
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
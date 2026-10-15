package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

object WhisperModelManager {

    private const val TAG = "WhisperModel"

    const val MODEL_TINY = "ggml-tiny.bin"
    const val MODEL_BASE = "ggml-base.bin"

    private const val BASE_URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"

    data class ModelInfo(
        val fileName: String,
        val label: String,
        val sizeBytes: Long,
        val speedLabel: String,
        val qualityLabel: String,
        val bestFor: String
    )

    val MODELS = listOf(
        ModelInfo(
            fileName = MODEL_TINY,
            label = "Fast",
            sizeBytes = 75L * 1024 * 1024,
            speedLabel = "~10x faster",
            qualityLabel = "Basic accuracy",
            bestFor = "English only"
        ),
        ModelInfo(
            fileName = MODEL_BASE,
            label = "Multilingual",
            sizeBytes = 142L * 1024 * 1024,
            speedLabel = "~3x faster",
            qualityLabel = "Good accuracy",
            bestFor = "Urdu, Hindi, Arabic, etc."
        )
    )

    fun modelsDir(context: Context): File {
        val dir = File(context.filesDir, "whisper_models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getModelFile(context: Context, fileName: String): File =
        File(modelsDir(context), fileName)

    fun getPartialFile(context: Context, fileName: String): File =
        File(modelsDir(context), "$fileName.part")

    fun isModelDownloaded(context: Context, fileName: String): Boolean {
        val f = getModelFile(context, fileName)
        val minSize = when (fileName) {
            MODEL_TINY -> 70L * 1024 * 1024
            MODEL_BASE -> 130L * 1024 * 1024
            else -> 70L * 1024 * 1024
        }
        return f.exists() && f.length() >= minSize
    }

    fun isTinyReady(context: Context): Boolean =
        isModelDownloaded(context, MODEL_TINY)

    fun isBaseReady(context: Context): Boolean =
        isModelDownloaded(context, MODEL_BASE)

    fun isAnyModelReady(context: Context): Boolean =
        isTinyReady(context) || isBaseReady(context)

    fun hasPartialDownload(context: Context, fileName: String): Boolean {
        val p = getPartialFile(context, fileName)
        return p.exists() && p.length() > 1024 * 1024
    }

    fun getPartialSize(context: Context, fileName: String): Long {
        val p = getPartialFile(context, fileName)
        return if (p.exists()) p.length() else 0L
    }

    suspend fun downloadModel(
        context: Context,
        fileName: String,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val target = getModelFile(context, fileName)
        val partial = getPartialFile(context, fileName)

        try {
            if (isModelDownloaded(context, fileName)) {
                onProgress(1f)
                return@withContext true
            }

            var existingBytes = if (partial.exists()) partial.length() else 0L
            val url = URL(BASE_URL + fileName)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true

            if (existingBytes > 0L) {
                conn.setRequestProperty("Range", "bytes=$existingBytes-")
            }
            conn.connect()

            val code = conn.responseCode
            val isResuming = (code == HttpURLConnection.HTTP_PARTIAL)
            val isFresh = (code == HttpURLConnection.HTTP_OK)
            if (!isResuming && !isFresh) {
                Log.e(TAG, "HTTP error: $code")
                return@withContext false
            }
            if (isFresh && existingBytes > 0L) {
                partial.delete()
                existingBytes = 0L
            }

            val totalSize = if (isResuming) {
                existingBytes + conn.contentLengthLong
            } else conn.contentLengthLong
            var downloaded = existingBytes

            val raf = RandomAccessFile(partial, "rw")
            try {
                if (isResuming && existingBytes > 0L) raf.seek(existingBytes)
                else raf.setLength(0L)

                conn.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var lastUpdate = 0L
                    while (input.read(buffer).also { read = it } > 0) {
                        raf.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 150) {
                            lastUpdate = now
                            val p = if (totalSize > 0)
                                downloaded.toFloat() / totalSize.toFloat()
                            else 0f
                            onProgress(p.coerceIn(0f, 1f))
                        }
                    }
                }
            } finally {
                raf.close()
            }
            conn.disconnect()

            if (partial.length() < 30L * 1024 * 1024) {
                Log.e(TAG, "Downloaded too small: ${partial.length()}")
                return@withContext false
            }
            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) return@withContext false

            onProgress(1f)
            Log.e(TAG, "✅ Downloaded: $fileName (${target.length() / 1024 / 1024} MB)")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "downloadModel failed", t)
            false
        }
    }

    fun deleteModel(context: Context, fileName: String) {
        try {
            getModelFile(context, fileName).delete()
            getPartialFile(context, fileName).delete()
        } catch (_: Throwable) {}
    }
}
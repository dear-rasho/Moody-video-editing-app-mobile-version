package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object WhisperAudioExtractor {

    private const val TAG = "WhisperAudio"
    private const val TARGET_SAMPLE_RATE = 16000

    suspend fun extractPcm(
        context: Context,
        uri: Uri
    ): FloatArray? = withContext(Dispatchers.IO) {
        val outFile = File(
            context.cacheDir,
            "whisper_${System.currentTimeMillis()}.pcm"
        )

        try {
            val inputFile = copyToCache(context, uri) ?: return@withContext null

            val cmd = "-y -i \"${inputFile.absolutePath}\" " +
                    "-ar $TARGET_SAMPLE_RATE -ac 1 -f f32le " +
                    "-acodec pcm_f32le \"${outFile.absolutePath}\""

            Log.e(TAG, "Extracting audio: $cmd")

            val session = FFmpegKit.execute(cmd)
            if (!ReturnCode.isSuccess(session.returnCode)) {
                Log.e(TAG, "FFmpeg failed: ${session.allLogsAsString}")
                outFile.delete()
                return@withContext null
            }

            if (!outFile.exists() || outFile.length() == 0L) {
                Log.e(TAG, "Output file empty")
                return@withContext null
            }

            val bytes = outFile.readBytes()
            val floatCount = bytes.size / 4
            val result = FloatArray(floatCount)

            val buffer = java.nio.ByteBuffer
                .wrap(bytes)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .asFloatBuffer()

            buffer.get(result)

            Log.e(
                TAG,
                "Extracted PCM: ${result.size} samples, " +
                        "${result.size / TARGET_SAMPLE_RATE}s duration"
            )

            result
        } catch (t: Throwable) {
            Log.e(TAG, "extractPcm failed", t)
            null
        } finally {
            outFile.delete()
        }
    }

    private fun copyToCache(context: Context, uri: Uri): File? {
        return try {
            val ext = when {
                uri.toString().endsWith(".mp4") -> "mp4"
                uri.toString().endsWith(".mov") -> "mov"
                uri.toString().endsWith(".mp3") -> "mp3"
                uri.toString().endsWith(".wav") -> "wav"
                uri.toString().endsWith(".m4a") -> "m4a"
                else -> "mp4"
            }
            val file = File(
                context.cacheDir,
                "whisper_input_${System.currentTimeMillis()}.$ext"
            )
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            if (file.exists() && file.length() > 0) file else null
        } catch (t: Throwable) {
            Log.e(TAG, "copyToCache failed", t)
            null
        }
    }
}
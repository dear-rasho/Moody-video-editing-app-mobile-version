package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

object WhisperAudioExtractor {

    private const val TAG = "WhisperAudio"
    private const val TARGET_SAMPLE_RATE = 16000

    suspend fun extractPcm(
        context: Context,
        uri: Uri,
        sourceStartMs: Long = 0L,
        sourceDurationMs: Long? = null
    ): FloatArray? = withContext(Dispatchers.IO) {
        if (sourceStartMs < 0L || sourceDurationMs != null && sourceDurationMs <= 0L) {
            return@withContext null
        }
        val outFile = File(
            context.cacheDir,
            "whisper_${System.currentTimeMillis()}.pcm"
        )

        try {
            val inputFile = copyToCache(context, uri) ?: return@withContext null

            val startSeconds = String.format(Locale.US, "%.3f", sourceStartMs / 1000.0)
            val durationArgs = sourceDurationMs?.let {
                "-t ${String.format(Locale.US, "%.3f", it / 1000.0)} "
            } ?: ""
            val cmd = "-y -ss $startSeconds -i \"${inputFile.absolutePath}\" " +
                    durationArgs +
                    "-vn " +
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

            // ═══════════════════════════════════════════════════════════
            //  DEBUG — audio sanity check
            //  peak < 0.001  → silent / nearly silent
            //  rms  < 0.005  → very quiet (may fail Whisper)
            //  duration < 1s → too short
            // ═══════════════════════════════════════════════════════════
            var peak = 0f
            var sumSq = 0.0
            for (v in result) {
                val a = abs(v)
                if (a > peak) peak = a
                sumSq += v.toDouble() * v.toDouble()
            }
            val rms = if (result.isNotEmpty())
                sqrt(sumSq / result.size).toFloat() else 0f
            val durationSec = result.size.toFloat() / TARGET_SAMPLE_RATE

            Log.e(
                TAG,
                "📊 Audio stats → samples=${result.size}, " +
                        "duration=%.2fs, peak=%.4f, rms=%.4f".format(
                            durationSec, peak, rms
                        )
            )

            if (peak < 0.001f) {
                Log.e(TAG, "🔇 WARNING: Audio is SILENT (peak=$peak)")
            } else if (peak < 0.05f) {
                Log.e(
                    TAG,
                    "🔉 WARNING: Audio is very quiet (peak=$peak). " +
                            "Whisper may struggle."
                )
            } else {
                Log.e(TAG, "✅ Audio level OK (peak=$peak)")
            }

            if (durationSec < 1.0f) {
                Log.e(
                    TAG,
                    "⚠️ WARNING: Audio shorter than 1 second " +
                            "(duration=${durationSec}s)"
                )
            }

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
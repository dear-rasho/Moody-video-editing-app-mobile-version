package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

object WhisperAudioExtractor {

    private const val TAG = "WhisperAudio"
    private const val TARGET_SAMPLE_RATE = 16000
    private const val PCM_BYTES_PER_SAMPLE = 4

    internal fun buildChunkRanges(
        durationMs: Long,
        chunkDurationMs: Long
    ): List<Pair<Long, Long>> {
        if (durationMs <= 0L || chunkDurationMs <= 0L) return emptyList()
        val ranges = mutableListOf<Pair<Long, Long>>()
        var startMs = 0L
        while (startMs < durationMs) {
            val lengthMs = minOf(chunkDurationMs, durationMs - startMs)
            ranges += startMs to lengthMs
            startMs += lengthMs
        }
        return ranges
    }

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

    suspend fun forEachPcmChunk(
        context: Context,
        uri: Uri,
        sourceStartMs: Long,
        sourceDurationMs: Long,
        chunkDurationMs: Long,
        onChunk: suspend (
            chunkIndex: Int,
            chunkCount: Int,
            chunkStartMs: Long,
            pcm: FloatArray
        ) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (sourceStartMs < 0L || sourceDurationMs <= 0L || chunkDurationMs <= 0L) {
            return@withContext false
        }

        val inputFile = copyToCache(context, uri) ?: return@withContext false
        val inputIsTemporary = uri.scheme != "file"
        val outputFile = File(context.cacheDir, "whisper_audio_${UUID.randomUUID()}.pcm")
        val chunkRanges = buildChunkRanges(sourceDurationMs, chunkDurationMs)
        val chunkCount = chunkRanges.size
        val coroutineContext = currentCoroutineContext()

        try {
            coroutineContext.ensureActive()
            val session = FFmpegKit.executeWithArguments(
                arrayOf(
                    "-nostdin",
                    "-y",
                    "-ss", (sourceStartMs / 1000.0).toString(),
                    "-i", inputFile.absolutePath,
                    "-t", (sourceDurationMs / 1000.0).toString(),
                    "-vn",
                    "-ar", TARGET_SAMPLE_RATE.toString(),
                    "-ac", "1",
                    "-f", "f32le",
                    "-acodec", "pcm_f32le",
                    outputFile.absolutePath
                )
            )
            if (!ReturnCode.isSuccess(session.returnCode)) {
                Log.e(TAG, "Audio extraction failed: ${session.allLogsAsString}")
                return@withContext false
            }
            if (!outputFile.exists() || outputFile.length() < PCM_BYTES_PER_SAMPLE) {
                Log.e(TAG, "Extracted PCM output is empty")
                return@withContext false
            }

            RandomAccessFile(outputFile, "r").use { pcmFile ->
                val totalSamples = pcmFile.length() / PCM_BYTES_PER_SAMPLE
                var samplesRead = 0L

                chunkRanges.forEachIndexed { chunkIndex, (chunkStartMs, chunkLengthMs) ->
                    coroutineContext.ensureActive()
                    val expectedSamples =
                        (chunkLengthMs * TARGET_SAMPLE_RATE + 500L) / 1000L
                    val sampleCount = minOf(expectedSamples, totalSamples - samplesRead)
                    if (sampleCount <= 0L) {
                        Log.e(TAG, "PCM ended before chunk ${chunkIndex + 1}")
                        return@withContext false
                    }

                    val bytes = ByteArray((sampleCount * PCM_BYTES_PER_SAMPLE).toInt())
                    pcmFile.readFully(bytes)
                    val pcm = FloatArray(sampleCount.toInt())
                    ByteBuffer.wrap(bytes)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asFloatBuffer()
                        .get(pcm)
                    samplesRead += sampleCount
                    onChunk(chunkIndex, chunkCount, chunkStartMs, pcm)
                }
            }
            true
        } finally {
            if (inputIsTemporary) inputFile.delete()
            outputFile.delete()
        }
    }

    private fun copyToCache(context: Context, uri: Uri): File? {
        if (uri.scheme == "file") {
            return uri.path?.let(::File)?.takeIf { it.isFile && it.canRead() }
        }

        val file = File(context.cacheDir, "whisper_input_${UUID.randomUUID()}")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().buffered(128 * 1024).use { output ->
                    input.copyTo(output, bufferSize = 128 * 1024)
                }
            }
            if (file.exists() && file.length() > 0) file else {
                file.delete()
                null
            }
        } catch (t: Throwable) {
            file.delete()
            Log.e(TAG, "copyToCache failed", t)
            null
        }
    }
}
package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.IOException
import java.util.UUID

data class ExtractedAudio(
    val uri: Uri,
    val durationMs: Long
)

object AudioExtractionEngine {

    fun extractAudio(context: Context, sourceUri: Uri): ExtractedAudio {
        val extractionDirectory = File(context.filesDir, "audio_extractions")
        if (!extractionDirectory.exists() && !extractionDirectory.mkdirs()) {
            throw IOException("Could not create audio extraction storage")
        }

        val id = UUID.randomUUID().toString()
        val extension = sourceUri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,10}")) }
            ?: "video"
        val sourceFile = File(context.cacheDir, "audio_source_$id.$extension")
        val outputFile = File(extractionDirectory, "extracted_$id.m4a")

        try {
            val input = context.contentResolver.openInputStream(sourceUri)
                ?: throw IOException("Selected video cannot be opened")
            input.use { source ->
                sourceFile.outputStream().use { destination ->
                    source.copyTo(destination)
                }
            }
            if (sourceFile.length() == 0L) {
                throw IOException("Selected video is empty")
            }

            val session = FFmpegKit.executeWithArguments(
                arrayOf(
                    "-nostdin",
                    "-y",
                    "-i", sourceFile.absolutePath,
                    "-map", "0:a:0",
                    "-vn",
                    "-c:a", "aac",
                    "-b:a", "192k",
                    "-movflags", "+faststart",
                    outputFile.absolutePath
                )
            )
            if (!ReturnCode.isSuccess(session.returnCode) || outputFile.length() == 0L) {
                val details = session.failStackTrace
                    ?.takeIf { it.isNotBlank() }
                    ?: session.allLogsAsString
                throw IOException(
                    if (details.isNullOrBlank()) {
                        "Could not extract audio. The selected video may not contain audio."
                    } else {
                        "Could not extract audio: ${details.takeLast(500)}"
                    }
                )
            }

            val durationMs = readAudioDurationMs(outputFile)
            if (durationMs <= 0L) {
                throw IOException("The extracted audio has no readable duration")
            }
            return ExtractedAudio(Uri.fromFile(outputFile), durationMs)
        } catch (error: Exception) {
            outputFile.delete()
            throw error
        } finally {
            sourceFile.delete()
        }
    }

    private fun readAudioDurationMs(file: File): Long {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true &&
                    format.containsKey(MediaFormat.KEY_DURATION)
                ) {
                    return format.getLong(MediaFormat.KEY_DURATION) / 1_000L
                }
            }
            throw IOException("No audio track was created")
        } finally {
            extractor.release()
        }
    }
}

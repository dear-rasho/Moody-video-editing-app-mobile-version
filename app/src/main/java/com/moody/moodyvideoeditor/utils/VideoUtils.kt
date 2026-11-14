package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log

object VideoUtils {
    private const val TAG = "VideoUtils"

    data class MediaMetadata(
        val durationMs: Long,
        val hasAudioTrack: Boolean
    )

    fun getMediaMetadata(context: Context, uri: Uri): MediaMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            MediaMetadata(
                durationMs = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 0L,
                hasAudioTrack = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO
                )?.equals("yes", ignoreCase = true) == true
            )
        } catch (error: Exception) {
            Log.w(TAG, "Could not read media metadata for $uri", error)
            MediaMetadata(durationMs = 0L, hasAudioTrack = false)
        } finally {
            try {
                retriever.release()
            } catch (error: RuntimeException) {
                Log.w(TAG, "Could not release media metadata retriever", error)
            }
        }
    }

    fun hasAudioTrack(context: Context, uri: Uri): Boolean =
        getMediaMetadata(context, uri).hasAudioTrack

    fun getFileName(context: Context, uri: Uri): String {
        var name = "video.mp4"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return name
    }

    fun getVideoDuration(context: Context, uri: Uri): Long {
        return getMediaMetadata(context, uri).durationMs
    }


    fun getMimeType(context: Context, uri: Uri): String {
        try {
            val type = context.contentResolver.getType(uri)
            if (!type.isNullOrBlank()) return type
        } catch (_: Exception) {
        }

        val name = getFileName(context, uri).lowercase()
        return when {
            // VIDEO
            name.endsWith(".mp4") -> "video/mp4"
            name.endsWith(".m4v") -> "video/mp4"
            name.endsWith(".mov") -> "video/quicktime"
            name.endsWith(".mkv") -> "video/x-matroska"
            name.endsWith(".webm") -> "video/webm"
            name.endsWith(".avi") -> "video/x-msvideo"
            name.endsWith(".3gp") -> "video/3gpp"
            name.endsWith(".3g2") -> "video/3gpp2"
            name.endsWith(".flv") -> "video/x-flv"
            name.endsWith(".wmv") -> "video/x-ms-wmv"
            name.endsWith(".mpg") || name.endsWith(".mpeg") -> "video/mpeg"
            name.endsWith(".ts") -> "video/mp2t"

            // AUDIO
            name.endsWith(".mp3") -> "audio/mpeg"
            name.endsWith(".wav") -> "audio/wav"
            name.endsWith(".aac") -> "audio/aac"
            name.endsWith(".m4a") -> "audio/mp4"
            name.endsWith(".ogg") -> "audio/ogg"
            name.endsWith(".oga") -> "audio/ogg"
            name.endsWith(".opus") -> "audio/opus"
            name.endsWith(".flac") -> "audio/flac"
            name.endsWith(".amr") -> "audio/amr"
            name.endsWith(".3gp") -> "audio/3gpp"

            // IMAGE
            name.endsWith(".jpg") -> "image/jpeg"
            name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".jpe") -> "image/jpeg"
            name.endsWith(".jfif") -> "image/jpeg"
            name.endsWith(".jif") -> "image/jpeg"
            name.endsWith(".jfi") -> "image/jpeg"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".apng") -> "image/apng"
            name.endsWith(".webp") -> "image/webp"
            name.endsWith(".gif") -> "image/gif"
            name.endsWith(".bmp") -> "image/bmp"
            name.endsWith(".heic") -> "image/heic"
            name.endsWith(".heif") -> "image/heif"
            name.endsWith(".avif") -> "image/avif"
            name.endsWith(".tif") || name.endsWith(".tiff") -> "image/tiff"
            name.endsWith(".svg") -> "image/svg+xml"

            else -> "video/mp4"
        }
    }

    fun isImage(mimeType: String): Boolean = mimeType.startsWith("image/")
    fun isVideo(mimeType: String): Boolean = mimeType.startsWith("video/")
    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }
}
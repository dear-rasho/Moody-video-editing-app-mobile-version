package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object VideoUtils {

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
        // ═══ Attempt 1 — MediaMetadataRetriever ═══
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            retriever.release()
            if (duration > 100L) return duration
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // ═══ Attempt 2 — MediaExtractor fallback ═══
        try {
            val extractor = android.media.MediaExtractor()
            extractor.setDataSource(context, uri, null)
            var duration = 0L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                    val d = format.getLong(android.media.MediaFormat.KEY_DURATION) / 1000L
                    if (d > duration) duration = d
                }
            }
            extractor.release()
            if (duration > 100L) return duration
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // ═══ Attempt 3 — ExoPlayer sync metadata ═══
        try {
            val player = androidx.media3.exoplayer.ExoPlayer.Builder(context).build()
            val mediaItem = androidx.media3.common.MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = false

            val deadline = System.currentTimeMillis() + 2000L
            while (player.playbackState != androidx.media3.common.Player.STATE_READY &&
                System.currentTimeMillis() < deadline
            ) {
                Thread.sleep(50)
            }
            val duration = if (player.duration > 0) player.duration else 0L
            player.release()
            if (duration > 100L) return duration
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return 0L
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
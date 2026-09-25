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

    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }
}
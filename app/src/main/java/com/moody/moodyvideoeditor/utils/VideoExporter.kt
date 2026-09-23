package com.moody.moodyvideoeditor.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.moody.moodyvideoeditor.data.EditorClip
import java.io.File

@UnstableApi
class VideoExporter(
    private val context: Context,
    private val onProgress: (Float) -> Unit,
    private val onSuccess: (Uri) -> Unit,
    private val onError: (String) -> Unit
) {
    private var transformer: Transformer? = null

    fun export(clips: List<EditorClip>, fileName: String) {
        if (clips.isEmpty()) {
            onError("No clips to export")
            return
        }

        try {
            val outputFile = createOutputFile(fileName)

            val editedItems = clips.map { clip ->
                val mediaItem = MediaItem.Builder()
                    .setUri(clip.uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.sourceStartMs)
                            .setEndPositionMs(clip.sourceEndMs)
                            .build()
                    )
                    .build()

                EditedMediaItem.Builder(mediaItem)
                    .setRemoveAudio(false)
                    .build()
            }

            val sequence = EditedMediaItemSequence(editedItems)
            val composition = Composition.Builder(listOf(sequence)).build()

            val listener = object : Transformer.Listener {
                override fun onCompleted(
                    composition: Composition,
                    exportResult: ExportResult
                ) {
                    onSuccess(Uri.fromFile(outputFile))
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exception: ExportException
                ) {
                    onError("Export failed: ${exception.message}")
                }
            }

            val t = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(listener)
                .build()

            transformer = t
            t.start(composition, outputFile.absolutePath)

        } catch (e: Exception) {
            onError("Export error: ${e.message}")
        }
    }

    fun cancel() {
        try {
            transformer?.cancel()
            transformer = null
        } catch (_: Exception) {
        }
    }

    private fun createOutputFile(fileName: String): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "MoodyEditor"
            )
        } else {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "MoodyEditor"
            )
        }
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$fileName.mp4")
    }

    fun saveToGallery(sourceFile: File): Uri? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, sourceFile.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MoodyEditor")
                }
            }
            val uri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return null

            context.contentResolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { it.copyTo(out) }
            }
            uri
        } catch (_: Exception) {
            null
        }
    }
}
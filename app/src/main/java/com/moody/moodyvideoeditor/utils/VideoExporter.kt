package com.moody.moodyvideoeditor.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.data.EditorClip
import java.io.File

class VideoExporter(
    private val context: Context,
    private val onProgress: (Float) -> Unit,
    private val onSuccess: (Uri) -> Unit,
    private val onError: (String) -> Unit
) {
    private var ffmpeg: FFmpegExecutor? = null

    fun export(
        clips: List<EditorClip>,
        fileName: String,
        adjustments: AdjustmentData = AdjustmentData()
    ) {
        if (clips.isEmpty()) {
            onError("No clips to export")
            return
        }

        val outputFile = createOutputFile(fileName)
        val videoFilters = FFmpegFilters.build(adjustments)

        ffmpeg = FFmpegExecutor(
            context = context,
            onProgress = onProgress,
            onSuccess = { file ->
                val galleryUri = saveToGallery(file)
                if (galleryUri != null) onSuccess(galleryUri)
                else onSuccess(Uri.fromFile(file))
            },
            onError = onError
        )

        ffmpeg?.export(
            clips = clips,
            outputFile = outputFile,
            videoFilters = videoFilters
        )
    }

    fun cancel() {
        ffmpeg?.cancel()
    }

    private fun createOutputFile(fileName: String): File {
        val dir = File(context.cacheDir, "MoodyExports")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$fileName.mp4")
    }

    private fun saveToGallery(sourceFile: File): Uri? {
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
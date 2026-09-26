package com.moody.moodyvideoeditor.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
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
    private var sessionId: Long? = null

    fun export(
        clips: List<EditorClip>,
        fileName: String,
        adjustments: AdjustmentData = AdjustmentData(),
        aspectRatio: String = "16:9",
        resolution: String = "720p",
        fps: Int = 30,
        bitrateKbps: Int = 8000,
        format: String = "mp4",
        customFolderUri: String? = null
    ) {
        if (clips.isEmpty()) {
            onError("No content to export")
            return
        }

        val visualClips = clips.filter {
            it.isVisualClip && it.uri.toString().isNotBlank() && it.uri != Uri.EMPTY
        }
        val textClips = clips.filter { it.isTextClip }
        val stickerClips = clips.filter { it.isStickerClip }
        val overlayClips = clips.filter { it.isOverlayClip }

        val totalDurationMs = clips.maxOfOrNull { it.timelineEndMs } ?: 5000L

        when {
            visualClips.isNotEmpty() -> {
                val outputFile = createOutputFile(fileName, format)
                val adjustmentFilters = FFmpegFilters.build(adjustments)
                val textStickerFilters = buildTextStickerFilters(textClips, stickerClips)

                val (targetW, targetH) = ExportSettings.targetDimensions(
                    resolution, aspectRatio
                )

                ffmpeg = FFmpegExecutor(
                    context = context,
                    onProgress = onProgress,
                    onSuccess = { file ->
                        val galleryUri = saveToGallery(file, customFolderUri)
                        if (galleryUri != null) onSuccess(galleryUri)
                        else onSuccess(Uri.fromFile(file))
                    },
                    onError = onError
                )
                ffmpeg?.export(
                    clips = visualClips,
                    outputFile = outputFile,
                    videoFilters = adjustmentFilters,
                    targetW = targetW,
                    targetH = targetH,
                    fps = fps,
                    bitrateKbps = bitrateKbps,
                    textFilters = textStickerFilters
                )
            }

            textClips.isNotEmpty() || stickerClips.isNotEmpty() -> {
                exportSynthetic(
                    textClips = textClips,
                    stickerClips = stickerClips,
                    overlayClips = overlayClips,
                    totalDurationMs = totalDurationMs,
                    fileName = fileName,
                    aspectRatio = aspectRatio,
                    customFolderUri = customFolderUri
                )
            }

            else -> onError("Nothing to export — timeline is empty")
        }
    }

    private fun buildTextStickerFilters(
        textClips: List<EditorClip>,
        stickerClips: List<EditorClip>
    ): String {
        val filters = mutableListOf<String>()

        val fontPath = FontFileHelper.getFontPath(context)
        val fontPart = fontPath?.let { "fontfile='${FontFileHelper.escapeFontPath(it)}':" } ?: ""

        textClips.forEach { clip ->
            val st = clip.textState ?: return@forEach
            if (st.content.isBlank()) return@forEach

            val startSec = clip.timelineStartMs / 1000.0
            val endSec = clip.timelineEndMs / 1000.0

            val escaped = st.content
                .replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("'", "\\'")
                .replace("%", "\\%")
                .replace("\n", " ")

            val posX = "(w-text_w)*${st.positionX / 100.0}"
            val posY = "(h-text_h)*${st.positionY / 100.0}"

            val textColor = String.format("0x%06X", (st.color and 0xFFFFFF))
            val fontSize = (st.fontSize * 1.5).toInt().coerceIn(16, 200)

            filters.add(
                "drawtext=$fontPart" +
                        "text='$escaped':" +
                        "fontsize=$fontSize:" +
                        "fontcolor=$textColor:" +
                        "x=$posX:y=$posY:" +
                        "enable='between(t,$startSec,$endSec)'"
            )
        }

        stickerClips.forEach { clip ->
            val ss = clip.stickerState ?: return@forEach
            if (ss.emoji.isBlank()) return@forEach

            val startSec = clip.timelineStartMs / 1000.0
            val endSec = clip.timelineEndMs / 1000.0

            val escaped = ss.emoji
                .replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("'", "\\'")

            val posX = "(w-text_w)*${ss.x / 100.0}"
            val posY = "(h-text_h)*${ss.y / 100.0}"

            filters.add(
                "drawtext=$fontPart" +
                        "text='$escaped':" +
                        "fontsize=96:" +
                        "x=$posX:y=$posY:" +
                        "enable='between(t,$startSec,$endSec)'"
            )
        }

        return filters.joinToString(",")
    }

    private fun exportSynthetic(
        textClips: List<EditorClip>,
        stickerClips: List<EditorClip>,
        overlayClips: List<EditorClip>,
        totalDurationMs: Long,
        fileName: String,
        aspectRatio: String = "16:9",
        customFolderUri: String? = null
    ) {
        val outputFile = createOutputFile(fileName, "mp4")
        val durSec = (totalDurationMs / 1000.0).coerceAtLeast(1.0)
        val (targetW, targetH) = ExportSettings.targetDimensions("720p", aspectRatio)

        val filterChain = mutableListOf<String>()

        val fontPath = FontFileHelper.getFontPath(context)
        val fontPart = fontPath?.let { "fontfile='${FontFileHelper.escapeFontPath(it)}':" } ?: ""

        textClips.forEach { clip ->
            val st = clip.textState ?: return@forEach
            if (st.content.isBlank()) return@forEach
            val startSec = clip.timelineStartMs / 1000.0
            val endSec = clip.timelineEndMs / 1000.0

            val escaped = st.content
                .replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("'", "\\'")
                .replace("%", "\\%")
                .replace("\n", " ")

            val posX = "(w-text_w)*${st.positionX / 100.0}"
            val posY = "(h-text_h)*${st.positionY / 100.0}"
            val textColor = String.format("0x%06X", (st.color and 0xFFFFFF))
            val fontSize = (st.fontSize * 1.5).toInt().coerceIn(16, 200)

            filterChain.add(
                "drawtext=$fontPart" +
                        "text='$escaped':" +
                        "fontsize=$fontSize:" +
                        "fontcolor=$textColor:" +
                        "x=$posX:y=$posY:" +
                        "enable='between(t,$startSec,$endSec)'"
            )
        }

        stickerClips.forEach { clip ->
            val ss = clip.stickerState ?: return@forEach
            if (ss.emoji.isBlank()) return@forEach
            val startSec = clip.timelineStartMs / 1000.0
            val endSec = clip.timelineEndMs / 1000.0

            val escaped = ss.emoji
                .replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("'", "\\'")

            val posX = "(w-text_w)*${ss.x / 100.0}"
            val posY = "(h-text_h)*${ss.y / 100.0}"

            filterChain.add(
                "drawtext=$fontPart" +
                        "text='$escaped':" +
                        "fontsize=96:" +
                        "x=$posX:y=$posY:" +
                        "enable='between(t,$startSec,$endSec)'"
            )
        }

        overlayClips.forEach { clip ->
            val ov = clip.overlay ?: return@forEach
            when (ov.type) {
                "vignette" -> filterChain.add(
                    "vignette=angle=${(ov.intensity / 100f * 0.7f)}"
                )

                "blackBars" -> filterChain.add(
                    "drawbox=y=0:h=h*0.08:color=black@1:t=fill," +
                            "drawbox=y=h*0.92:h=h*0.08:color=black@1:t=fill"
                )
            }
        }

        val vf = if (filterChain.isEmpty()) {
            "color=c=black:s=${targetW}x${targetH}:d=$durSec"
        } else {
            filterChain.joinToString(",")
        }

        val args = mutableListOf(
            "-y",
            "-f", "lavfi",
            "-i", "color=c=black:s=${targetW}x${targetH}:d=$durSec",
            "-vf", vf,
            "-c:v", "mpeg4",
            "-qscale:v", "4",
            "-pix_fmt", "yuv420p",
            "-b:v", "5000k",
            "-movflags", "+faststart",
            outputFile.absolutePath
        )

        try {
            val session = FFmpegKit.executeAsync(
                args.joinToString(" "),
                { s ->
                    if (ReturnCode.isSuccess(s.returnCode)) {
                        val galleryUri = saveToGallery(outputFile, customFolderUri)
                        if (galleryUri != null) onSuccess(galleryUri)
                        else onSuccess(Uri.fromFile(outputFile))
                    } else {
                        val logs = s.allLogsAsString ?: "Unknown error"
                        onError("Synthetic export failed: ${logs.takeLast(1500)}")
                    }
                },
                { _ -> },
                { stats ->
                    try {
                        val t = stats.time
                        if (t > 0) {
                            val p = ((t / (totalDurationMs.toDouble() * 1.2))
                                .coerceIn(0.0, 0.95)).toFloat()
                            onProgress(p)
                        }
                    } catch (_: Exception) {
                    }
                }
            )
            sessionId = session.sessionId
        } catch (e: Exception) {
            onError("FFmpeg error: ${e.message}")
        }
    }

    fun cancel() {
        ffmpeg?.cancel()
        sessionId?.let {
            try {
                FFmpegKit.cancel(it)
            } catch (_: Exception) {
            }
        }
    }

    private fun createOutputFile(fileName: String, format: String): File {
        val dir = File(context.cacheDir, "MoodyExports")
        if (!dir.exists()) dir.mkdirs()
        val ext = if (format == "mov") "mov" else "mp4"
        return File(dir, "$fileName.$ext")
    }

    private fun saveToGallery(sourceFile: File, customFolderUri: String? = null): Uri? {
        if (customFolderUri != null) {
            try {
                val treeUri = Uri.parse(customFolderUri)
                val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri, parentDocId
                )
                val mimeType = if (sourceFile.name.endsWith(".mov")) "video/quicktime"
                else "video/mp4"

                val docUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    parentDocUri,
                    mimeType,
                    sourceFile.name
                )
                if (docUri != null) {
                    context.contentResolver.openOutputStream(docUri)?.use { out ->
                        sourceFile.inputStream().use { it.copyTo(out) }
                    }
                    return docUri
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

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
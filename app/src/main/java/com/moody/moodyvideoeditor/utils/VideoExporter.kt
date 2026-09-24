package com.moody.moodyvideoeditor.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
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
        adjustments: AdjustmentData = AdjustmentData()
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
            // ─── VIDEO / IMAGE PATH ───────────────────
            visualClips.isNotEmpty() -> {
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
                    clips = visualClips,
                    outputFile = outputFile,
                    videoFilters = videoFilters
                )
            }

            // ─── SYNTHETIC PATH (text / sticker only) ─
            textClips.isNotEmpty() || stickerClips.isNotEmpty() -> {
                exportSynthetic(
                    textClips = textClips,
                    stickerClips = stickerClips,
                    overlayClips = overlayClips,
                    totalDurationMs = totalDurationMs,
                    fileName = fileName
                )
            }

            else -> onError("Nothing to export — timeline is empty")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SYNTHETIC EXPORT — black canvas + drawtext + emoji overlay
    // ═══════════════════════════════════════════════════════════
    private fun exportSynthetic(
        textClips: List<EditorClip>,
        stickerClips: List<EditorClip>,
        overlayClips: List<EditorClip>,
        totalDurationMs: Long,
        fileName: String
    ) {
        val outputFile = createOutputFile(fileName)
        val durSec = (totalDurationMs / 1000.0).coerceAtLeast(1.0)

        val filterChain = mutableListOf<String>()

        // 1) Text drawtext filters (one per text clip)
        textClips.forEachIndexed { idx, clip ->
            val st = clip.textState ?: return@forEachIndexed
            if (st.content.isBlank()) return@forEachIndexed

            val startSec = clip.timelineStartMs / 1000.0
            val endSec = clip.timelineEndMs / 1000.0

            // Escape special chars for FFmpeg drawtext
            val escaped = st.content
                .replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("'", "\\'")
                .replace("%", "\\%")

            // Position: center by default
            val posX = "w*${st.positionX / 100.0}-text_w/2"
            val posY = "h*${st.positionY / 100.0}-text_h/2"

            // Convert color to 0xRRGGBB
            val textColor = String.format("0x%06X", (st.color and 0xFFFFFF))

            // Text size scaled for 1280x720 output
            val fontSize = (st.fontSize * 2).coerceIn(16, 200)

            filterChain.add(
                "drawtext=text='$escaped':" +
                        "fontsize=$fontSize:" +
                        "fontcolor=$textColor:" +
                        "x=$posX:y=$posY:" +
                        "enable='between(t,$startSec,$endSec)'"
            )
        }

        // 2) Sticker emojis (best-effort drawtext)
        stickerClips.forEach { clip ->
            val ss = clip.stickerState ?: return@forEach
            if (ss.emoji.isBlank()) return@forEach

            val startSec = clip.timelineStartMs / 1000.0
            val endSec = clip.timelineEndMs / 1000.0

            val escaped = ss.emoji
                .replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("'", "\\'")

            val posX = "w*${ss.x / 100.0}-text_w/2"
            val posY = "h*${ss.y / 100.0}-text_h/2"
            val fontSize = (48 * 2).coerceIn(48, 300)

            filterChain.add(
                "drawtext=text='$escaped':" +
                        "fontsize=$fontSize:" +
                        "x=$posX:y=$posY:" +
                        "enable='between(t,$startSec,$endSec)'"
            )
        }

        // 3) Overlays (vignette, blackBars) — simple ones via filters
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
            "color=c=black:s=1280x720:d=$durSec"
        } else {
            filterChain.joinToString(",")
        }

        // Build FFmpeg command
        val args = mutableListOf(
            "-y",
            "-f", "lavfi",
            "-i", "color=c=black:s=1280x720:d=$durSec",
            "-vf", vf,
            "-c:v", "mpeg4",
            "-qscale:v", "4",
            "-movflags", "+faststart",
            outputFile.absolutePath
        )

        try {
            val session = FFmpegKit.executeAsync(
                args.joinToString(" "),
                { s ->
                    if (ReturnCode.isSuccess(s.returnCode)) {
                        val galleryUri = saveToGallery(outputFile)
                        if (galleryUri != null) onSuccess(galleryUri)
                        else onSuccess(Uri.fromFile(outputFile))
                    } else {
                        val logs = s.allLogsAsString ?: "Unknown error"
                        onError("Synthetic export failed: ${logs.takeLast(300)}")
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
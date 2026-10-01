package com.moody.moodyvideoeditor.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.data.EditorClip
import java.io.File

class VideoExporter(
    private val context: Context,
    private val onProgress: (Float) -> Unit,
    private val onSuccess: (Uri) -> Unit,
    private val onError: (String) -> Unit,
    private val onCancelled: () -> Unit = {}
) {
    private var ffmpeg: FFmpegExecutor? = null
    private var sessionId: Long? = null
    private var isCancelled = false

    fun export(
        clips: List<EditorClip>,
        fileName: String,
        adjustments: AdjustmentData = AdjustmentData(),
        aspectRatio: String = "16:9",
        resolution: String = "720p",
        fps: Int = 30,
        bitrateKbps: Int = 8000,
        format: String = "mp4",
        customFolderUri: String? = null,
        customStartMs: Long = 0L,
        customEndMs: Long = 0L
    ) {
        if (clips.isEmpty()) {
            onError("❌ No content to export")
            return
        }

        val totalTimeline = clips.maxOfOrNull { it.timelineEndMs } ?: 5000L
        val rangeStart = customStartMs.coerceIn(0L, totalTimeline)
        val rangeEnd = if (customEndMs > rangeStart) {
            customEndMs.coerceIn(rangeStart + 500L, totalTimeline)
        } else {
            totalTimeline
        }

        Log.e("EXPORT_RANGE", "Range: $rangeStart → $rangeEnd (of $totalTimeline)")

        // ═══════════════════════════════════════════════════════════
        //  Visual clips (video/image) → FFmpeg base
        //  🆕 FIX: Only BASE TRACK (V1) visual clips go to FFmpeg
        //          so transitions work properly
        // ═══════════════════════════════════════════════════════════
        val allVisual = clips.filter {
            it.isVisualClip &&
                    it.uri.toString().isNotBlank() &&
                    it.uri != Uri.EMPTY
        }

        // 🆕 Find base track (lowest track index with visual clips)
        val baseTrackIndex = allVisual.minOfOrNull { it.trackIndex } ?: 0
        val baseVisualClips = allVisual.filter { it.trackIndex == baseTrackIndex }
        val higherTrackVisualClips = allVisual.filter { it.trackIndex > baseTrackIndex }

        if (higherTrackVisualClips.isNotEmpty()) {
            Log.w(
                "EXPORT",
                "⚠️ ${higherTrackVisualClips.size} higher-track visual clips " +
                        "are NOT included in FFmpeg base. " +
                        "Only V${baseTrackIndex + 1} clips exported. " +
                        "Higher-track video overlay support is future work."
            )
        }

        // Overlay image clips (higher track images) — currently also skipped
        // because they can't be overlaid via FFmpeg easily with transitions
        val overlayImageClips = trimClipsToRange(
            baseVisualClips.filter {
                it.type.startsWith("image/") && it.trackIndex > 0
            },
            rangeStart, rangeEnd
        )

        // 🆕 Base visual clips (V1) → main export track
        val trimmedVisualClips = trimClipsToRange(
            baseVisualClips.filter {
                !(it.type.startsWith("image/") && it.trackIndex > 0)
            },
            rangeStart, rangeEnd
        )

        // ═══════════════════════════════════════════════════════════
        //  Audio-only clips (mp3/wav) — trimmed to range
        // ═══════════════════════════════════════════════════════════
        val audioOnlyClips = trimClipsToRange(
            clips.filter {
                it.isAudio &&
                        !it.isAudioEffectClip &&
                        it.uri.toString().isNotBlank() &&
                        it.uri != Uri.EMPTY
            },
            rangeStart, rangeEnd
        )

        // 🆕 ALL text + sticker clips (any track) → overlaid on top
        val trimmedTextClips = trimClipsToRange(
            clips.filter { it.isTextClip || it.isStickerClip },
            rangeStart, rangeEnd
        )
        val exportDurationMs = rangeEnd - rangeStart

        val (targetW, targetH) = ExportSettings.targetDimensions(
            resolution, aspectRatio
        )

        when {
            // ─── Base visual + audio-only ───
            trimmedVisualClips.isNotEmpty() || audioOnlyClips.isNotEmpty() -> {
                val outputFile = createOutputFile(fileName, format)

                val textSequences = try {
                    TextBitmapRenderer.renderCombinedOverlays(
                        context = context,
                        textClips = trimmedTextClips,
                        imageClips = overlayImageClips,
                        W = targetW,
                        H = targetH,
                        fps = fps,
                        totalDurationMs = exportDurationMs
                    )
                } catch (e: Throwable) {
                    Log.e("EXPORT", "Text render failed", e)
                    emptyList()
                }

                Log.e(
                    "EXPORT",
                    "BaseVisual=${trimmedVisualClips.size} (V${baseTrackIndex + 1}), " +
                            "AudioOnly=${audioOnlyClips.size}, " +
                            "Text=${trimmedTextClips.size}, " +
                            "TextSeqs=${textSequences.size}"
                )

                ffmpeg = FFmpegExecutor(
                    context = context,
                    onProgress = { if (!isCancelled) onProgress(it) },
                    onSuccess = { file ->
                        if (isCancelled) return@FFmpegExecutor
                        val galleryUri = saveToGallery(file, customFolderUri)
                        if (galleryUri != null) onSuccess(galleryUri)
                        else onSuccess(Uri.fromFile(file))
                    },
                    onError = { msg ->
                        if (isCancelled) onCancelled()
                        else onError(msg)
                    }
                )
                ffmpeg?.export(
                    clips = trimmedVisualClips,
                    allClips = clips,
                    outputFile = outputFile,
                    targetW = targetW,
                    targetH = targetH,
                    fps = fps,
                    bitrateKbps = bitrateKbps,
                    textSequences = textSequences,
                    audioOnlyClips = audioOnlyClips,
                    explicitDurationMs = exportDurationMs
                )
            }

            // ─── Only text/stickers ───
            trimmedTextClips.isNotEmpty() || overlayImageClips.isNotEmpty() -> {
                exportSynthetic(
                    textClips = trimmedTextClips + overlayImageClips,
                    totalDurationMs = exportDurationMs,
                    fileName = fileName,
                    aspectRatio = aspectRatio,
                    resolution = resolution,
                    fps = fps,
                    bitrateKbps = bitrateKbps,
                    customFolderUri = customFolderUri
                )
            }

            else -> onError("❌ Nothing to export in selected range")
        }
    }

    fun cancel() {
        isCancelled = true
        ffmpeg?.cancel()
        sessionId?.let {
            try {
                FFmpegKit.cancel(it)
            } catch (_: Exception) {
            }
        }
    }

    private fun trimClipsToRange(
        clips: List<EditorClip>,
        rangeStart: Long,
        rangeEnd: Long
    ): List<EditorClip> {
        return clips
            .filter { it.timelineEndMs > rangeStart && it.timelineStartMs < rangeEnd }
            .map { clip ->
                val clipStart = clip.timelineStartMs
                val clipEnd = clip.timelineEndMs

                val leftCut = (rangeStart - clipStart).coerceAtLeast(0L)
                val rightCut = (clipEnd - rangeEnd).coerceAtLeast(0L)

                val speed = clip.speed.coerceAtLeast(0.01f)
                val sourceLeftCut = (leftCut * speed).toLong()
                val sourceRightCut = (rightCut * speed).toLong()

                val newSourceStart = clip.sourceStartMs + sourceLeftCut
                val newSourceEnd = (clip.sourceEndMs - sourceRightCut)
                    .coerceAtLeast(newSourceStart + 33L)

                val newTimelineStart = (clipStart - rangeStart).coerceAtLeast(0L)

                clip.copy(
                    sourceStartMs = newSourceStart,
                    sourceEndMs = newSourceEnd,
                    timelineStartMs = newTimelineStart
                )
            }
    }

    private fun exportSynthetic(
        textClips: List<EditorClip>,
        totalDurationMs: Long,
        fileName: String,
        aspectRatio: String,
        resolution: String,
        fps: Int,
        bitrateKbps: Int,
        customFolderUri: String?
    ) {
        val outputFile = createOutputFile(fileName, "mp4")
        val durSec = (totalDurationMs / 1000.0).coerceAtLeast(1.0)
        val (targetW, targetH) = ExportSettings.targetDimensions(resolution, aspectRatio)

        val sequences = try {
            TextBitmapRenderer.renderCombinedOverlays(
                context = context,
                textClips = textClips.filter { it.isTextClip || it.isStickerClip },
                imageClips = textClips.filter {
                    it.isVisualClip && it.type.startsWith("image/")
                },
                W = targetW,
                H = targetH,
                fps = fps,
                totalDurationMs = totalDurationMs
            )
        } catch (e: Throwable) {
            Log.e("EXPORT", "Synthetic render failed", e)
            emptyList()
        }

        val args = mutableListOf<String>()
        args.add("-y")
        args.add("-f"); args.add("lavfi")
        args.add("-t"); args.add(durSec.toString())
        args.add("-i"); args.add("color=c=black:s=${targetW}x${targetH}:r=$fps")

        args.add("-f"); args.add("lavfi")
        args.add("-t"); args.add(durSec.toString())
        args.add("-i"); args.add("anullsrc=r=44100:cl=stereo")

        sequences.forEach { seq ->
            if (seq.startSec > 0.0001) {
                args.add("-itsoffset"); args.add("%.4f".format(seq.startSec))
            }
            args.add("-framerate"); args.add(seq.fps.toString())
            args.add("-start_number"); args.add(seq.startNumber.toString())
            args.add("-i"); args.add(seq.pattern)
        }

        val filterParts = mutableListOf<String>()
        filterParts.add("[0:v]format=yuva420p[base]")

        var lastLabel = "base"
        sequences.forEachIndexed { idx, seq ->
            val inIdx = idx + 2
            val srcLabel = "seqsrc$idx"
            val outLabel = "ov$idx"
            filterParts.add("[$inIdx:v]format=rgba[$srcLabel]")
            val startS = "%.4f".format(seq.startSec)
            val endS = "%.4f".format(seq.endSec)
            filterParts.add(
                "[$lastLabel][$srcLabel]overlay=0:0:" +
                        "enable='between(t,$startS,$endS)'" +
                        "[$outLabel]"
            )
            lastLabel = outLabel
        }
        filterParts.add("[$lastLabel]format=yuv420p[outv]")
        filterParts.add("[1:a]anull[outa]")

        args.add("-filter_complex")
        args.add(filterParts.joinToString(";"))
        args.add("-map"); args.add("[outv]")
        args.add("-map"); args.add("[outa]")

        args.add("-c:v"); args.add("mpeg4")
        args.add("-qscale:v"); args.add("4")
        args.add("-pix_fmt"); args.add("yuv420p")
        args.add("-b:v"); args.add("${bitrateKbps}k")
        args.add("-r"); args.add(fps.toString())
        args.add("-c:a"); args.add("aac")
        args.add("-b:a"); args.add("128k")
        args.add("-movflags"); args.add("+faststart")
        args.add("-t"); args.add(durSec.toString())
        args.add(outputFile.absolutePath)

        try {
            val argsArray = args.toTypedArray()
            val session = FFmpegKit.executeWithArgumentsAsync(
                argsArray,
                { s ->
                    if (isCancelled) {
                        onCancelled()
                    } else if (ReturnCode.isSuccess(s.returnCode)) {
                        val galleryUri = saveToGallery(outputFile, customFolderUri)
                        if (galleryUri != null) onSuccess(galleryUri)
                        else onSuccess(Uri.fromFile(outputFile))
                    } else {
                        val logs = s.allLogsAsString ?: "Unknown error"
                        onError("❌ Export failed:\n${logs.takeLast(1500)}")
                    }
                },
                { _ -> },
                { stats ->
                    if (!isCancelled) {
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
                }
            )
            sessionId = session.sessionId
        } catch (e: Exception) {
            onError("❌ FFmpeg error: ${e.message}")
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
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

    suspend fun export(
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
        } else totalTimeline

        Log.e("EXPORT_RANGE", "Range: $rangeStart → $rangeEnd (of $totalTimeline)")

        // ─── Visual clips (video/image) ───
        val allVisual = clips.filter {
            it.isVisualClip &&
                    it.uri.toString().isNotBlank() &&
                    it.uri != Uri.EMPTY
        }
        val baseTrackIndex = allVisual.minOfOrNull { it.trackIndex } ?: 0
        val baseVisualClips = allVisual.filter { it.trackIndex == baseTrackIndex }
        val higherTrackVisualClips = allVisual.filter { it.trackIndex > baseTrackIndex }

        if (higherTrackVisualClips.isNotEmpty()) {
            Log.w(
                "EXPORT",
                "⚠️ ${higherTrackVisualClips.size} higher-track visual clips skipped"
            )
        }

        val overlayImageClips = trimClipsToRange(
            baseVisualClips.filter {
                it.type.startsWith("image/") && it.trackIndex > 0
            },
            rangeStart, rangeEnd
        )

        val trimmedVisualClips = trimClipsToRange(
            baseVisualClips.filter {
                !(it.type.startsWith("image/") && it.trackIndex > 0)
            },
            rangeStart, rangeEnd
        )

        // ─── Audio-only clips ───
        val audioOnlyClips = trimClipsToRange(
            clips.filter {
                it.isAudio &&
                        !it.isAudioEffectClip &&
                        it.uri.toString().isNotBlank() &&
                        it.uri != Uri.EMPTY
            },
            rangeStart, rangeEnd
        )

        // ─── Text + sticker ───
        val trimmedTextClips = trimClipsToRange(
            clips.filter { it.isTextClip || it.isStickerClip },
            rangeStart, rangeEnd
        )

        // ─── Overlay clips ───
        val trimmedOverlayClips = trimClipsToRange(
            clips.filter {
                it.isOverlayClip ||
                        (it.isEffectClip && it.effectState?.overlay != null)
            },
            rangeStart, rangeEnd
        )

        // ─── Visualizer clips (KEEP original timeline!) ───
        val vizClips = clips.filter { it.isVisualizerClip }

        val exportDurationMs = rangeEnd - rangeStart

        val (targetW, targetH) = ExportSettings.targetDimensions(
            resolution, aspectRatio
        )

        Log.e(
            "EXPORT",
            "BaseVisual=${trimmedVisualClips.size}, " +
                    "AudioOnly=${audioOnlyClips.size}, " +
                    "Text=${trimmedTextClips.size}, " +
                    "Overlay=${trimmedOverlayClips.size}, " +
                    "Visualizer=${vizClips.size}"
        )

        val hasBaseVideo = trimmedVisualClips.isNotEmpty()
        val hasSynthetic = trimmedTextClips.isNotEmpty() ||
                overlayImageClips.isNotEmpty() ||
                vizClips.isNotEmpty()

        when {
            // ═══════════════════════════════════════════════════════
            //  CASE 1: Base visual clips exist
            //  Progress map:
            //   0-20%  → text/sticker PNG rendering
            //  20-40%  → visualizer PNG rendering
            //  40-100% → FFmpeg encoding
            // ═══════════════════════════════════════════════════════
            hasBaseVideo -> {
                val outputFile = createOutputFile(fileName, format)

                // Notify start
                onProgress(0.01f)

                val textSequences = try {
                    TextBitmapRenderer.renderCombinedOverlays(
                        context = context,
                        textClips = trimmedTextClips,
                        imageClips = overlayImageClips,
                        overlayClips = trimmedOverlayClips,
                        W = targetW, H = targetH, fps = fps,
                        totalDurationMs = exportDurationMs,
                        onProgress = { p -> onProgress(p * 0.20f) }
                    )
                } catch (e: Throwable) {
                    Log.e("EXPORT", "Text render failed", e)
                    emptyList()
                }

                val vizSequences = if (vizClips.isNotEmpty()) {
                    try {
                        VisualizerBitmapRenderer.renderCombinedOverlays(
                            context = context,
                            visualizerClips = vizClips,
                            allClips = clips,
                            rangeStart = rangeStart,
                            W = targetW, H = targetH, fps = fps,
                            totalDurationMs = exportDurationMs,
                            onProgress = { p -> onProgress(0.20f + p * 0.20f) }
                        )
                    } catch (e: Throwable) {
                        Log.e("EXPORT", "Visualizer render failed", e)
                        emptyList()
                    }
                } else emptyList()

                val allSequences = textSequences + vizSequences

                onProgress(0.40f)

                ffmpeg = FFmpegExecutor(
                    context = context,
                    onProgress = { p ->
                        if (!isCancelled) onProgress(0.40f + p * 0.60f)
                    },
                    onSuccess = { file ->
                        if (isCancelled) return@FFmpegExecutor
                        val galleryUri = saveToGallery(file, customFolderUri)
                        if (galleryUri != null) onSuccess(galleryUri)
                        else onSuccess(Uri.fromFile(file))
                    },
                    onError = { msg ->
                        if (isCancelled) onCancelled() else onError(msg)
                    }
                )
                ffmpeg?.export(
                    clips = trimmedVisualClips,
                    allClips = clips,
                    outputFile = outputFile,
                    targetW = targetW, targetH = targetH,
                    fps = fps, bitrateKbps = bitrateKbps,
                    textSequences = allSequences,
                    audioOnlyClips = audioOnlyClips,
                    explicitDurationMs = exportDurationMs
                )
            }

            // ═══════════════════════════════════════════════════════
            //  CASE 2: No base video, has synthetic layers
            // ═══════════════════════════════════════════════════════
            hasSynthetic -> {
                exportSyntheticFull(
                    allClips = clips,
                    textClips = trimmedTextClips,
                    overlayImageClips = overlayImageClips,
                    overlayClips = trimmedOverlayClips,
                    vizClips = vizClips,
                    audioOnlyClips = audioOnlyClips,
                    rangeStart = rangeStart,
                    rangeEnd = rangeEnd,
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

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════
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

    private fun copyUriToCache(uri: Uri, fileName: String): File? {
        return try {
            val mime = try {
                context.contentResolver.getType(uri) ?: ""
            } catch (_: Throwable) {
                ""
            }

            val ext = when {
                mime.startsWith("audio/mpeg") -> "mp3"
                mime.startsWith("audio/wav") -> "wav"
                mime.startsWith("audio/aac") -> "aac"
                mime.startsWith("audio/mp4") -> "m4a"
                mime.startsWith("audio/ogg") -> "ogg"
                mime.startsWith("video/quicktime") -> "mov"
                mime.startsWith("video/webm") -> "webm"
                else -> "mp4"
            }

            val baseName = fileName.substringBeforeLast('.')
            val realFileName = "$baseName.$ext"
            val file = File(context.cacheDir, realFileName)

            if (file.exists() && file.length() > 0) return file
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            if (file.length() > 0) file else null
        } catch (e: Exception) {
            Log.e("FFMPEG", "Copy failed", e)
            null
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SYNTHETIC FULL EXPORT (no base video)
    //  Progress map:
    //   0-25%  → visualizer PNG rendering
    //  25-50%  → text/sticker PNG rendering
    //  50-100% → FFmpeg encoding
    // ═══════════════════════════════════════════════════════════
    private suspend fun exportSyntheticFull(
        allClips: List<EditorClip>,
        textClips: List<EditorClip>,
        overlayImageClips: List<EditorClip>,
        overlayClips: List<EditorClip>,
        vizClips: List<EditorClip>,
        audioOnlyClips: List<EditorClip>,
        rangeStart: Long,
        rangeEnd: Long,
        fileName: String,
        aspectRatio: String,
        resolution: String,
        fps: Int,
        bitrateKbps: Int,
        customFolderUri: String?
    ) {
        val (targetW, targetH) = ExportSettings.targetDimensions(resolution, aspectRatio)
        val durationMs = (rangeEnd - rangeStart).coerceAtLeast(500L)

        onProgress(0.01f)

        // ─── Visualizer PNG sequences ───
        val vizSequences = if (vizClips.isNotEmpty()) {
            try {
                VisualizerBitmapRenderer.renderCombinedOverlays(
                    context = context,
                    visualizerClips = vizClips,
                    allClips = allClips,
                    rangeStart = rangeStart,
                    W = targetW, H = targetH, fps = fps,
                    totalDurationMs = durationMs,
                    onProgress = { p -> onProgress(p * 0.25f) }
                )
            } catch (e: Throwable) {
                Log.e("EXPORT", "Visualizer render failed", e)
                onError("❌ Visualizer render failed: ${e.message}")
                return
            }
        } else emptyList()

        // ─── Text + overlay PNG sequences ───
        val textSequences = if (
            textClips.isNotEmpty() ||
            overlayImageClips.isNotEmpty() ||
            overlayClips.isNotEmpty()
        ) {
            try {
                TextBitmapRenderer.renderCombinedOverlays(
                    context = context,
                    textClips = textClips,
                    imageClips = overlayImageClips,
                    overlayClips = overlayClips,
                    W = targetW, H = targetH, fps = fps,
                    totalDurationMs = durationMs,
                    onProgress = { p -> onProgress(0.25f + p * 0.25f) }
                )
            } catch (e: Throwable) {
                Log.e("EXPORT", "Text render failed", e)
                emptyList()
            }
        } else emptyList()

        val allSequences = vizSequences + textSequences

        onProgress(0.50f)

        // ═══════════════════════════════════════════════════════
        //  COLLECT AUDIO
        // ═══════════════════════════════════════════════════════
        val audioToMix = mutableListOf<EditorClip>()
        audioToMix.addAll(audioOnlyClips)

        vizClips.forEach { viz ->
            val linkedId = viz.visualizer?.linkedAudioClipId ?: return@forEach
            val linkedAudio = allClips.firstOrNull {
                it.id == linkedId && it.isAudio && !it.isAudioEffectClip &&
                        it.uri.toString().isNotBlank() && it.uri != Uri.EMPTY
            }
            if (linkedAudio != null && audioToMix.none { it.id == linkedAudio.id }) {
                val clipStart = linkedAudio.timelineStartMs
                val clipEnd = linkedAudio.timelineEndMs
                if (clipEnd > rangeStart && clipStart < rangeEnd) {
                    val leftCut = (rangeStart - clipStart).coerceAtLeast(0L)
                    val rightCut = (clipEnd - rangeEnd).coerceAtLeast(0L)
                    val speed = linkedAudio.speed.coerceAtLeast(0.01f)
                    val newSourceStart = linkedAudio.sourceStartMs +
                            (leftCut * speed).toLong()
                    val newSourceEnd = (linkedAudio.sourceEndMs -
                            (rightCut * speed).toLong())
                        .coerceAtLeast(newSourceStart + 100L)
                    val newTimelineStart = (clipStart - rangeStart)
                        .coerceAtLeast(0L)
                    audioToMix.add(
                        linkedAudio.copy(
                            sourceStartMs = newSourceStart,
                            sourceEndMs = newSourceEnd,
                            timelineStartMs = newTimelineStart
                        )
                    )
                }
            }
        }

        val audioLocalFiles = mutableListOf<File>()
        val audioLocalClips = mutableListOf<EditorClip>()
        audioToMix.forEach { clip ->
            val f = copyUriToCache(clip.uri, "audio_${clip.id}.mp3")
            if (f != null) {
                audioLocalFiles.add(f)
                audioLocalClips.add(clip)
            }
        }

        Log.e(
            "EXPORT",
            "Synthetic: ${allSequences.size} seq, ${audioLocalFiles.size} audio"
        )

        // ═══════════════════════════════════════════════════════
        //  BUILD FFmpeg ARGS
        // ═══════════════════════════════════════════════════════
        val outputFile = createOutputFile(fileName, "mp4")
        val durSec = (durationMs / 1000.0).coerceAtLeast(0.5)

        val args = mutableListOf<String>()
        args.add("-y")

        args.add("-f"); args.add("lavfi")
        args.add("-t"); args.add(durSec.toString())
        args.add("-i"); args.add("color=c=black:s=${targetW}x${targetH}:r=$fps")

        args.add("-f"); args.add("lavfi")
        args.add("-t"); args.add(durSec.toString())
        args.add("-i"); args.add("anullsrc=r=44100:cl=stereo")

        audioLocalFiles.forEach { f ->
            args.add("-i"); args.add(f.absolutePath)
        }

        val seqStartIdx = 2 + audioLocalFiles.size
        allSequences.forEach { seq ->
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
        allSequences.forEachIndexed { idx, seq ->
            val inIdx = seqStartIdx + idx
            val srcLabel = "seqsrc$idx"
            val outLabel = "ov$idx"
            filterParts.add("[$inIdx:v]format=rgba[$srcLabel]")
            val startS = "%.4f".format(seq.startSec)
            val endS = "%.4f".format(seq.endSec)
            filterParts.add(
                "[$lastLabel][$srcLabel]overlay=0:0:" +
                        "enable='between(t,$startS,$endS)'[$outLabel]"
            )
            lastLabel = outLabel
        }
        filterParts.add("[$lastLabel]format=yuv420p[outv]")

        if (audioLocalFiles.isEmpty()) {
            filterParts.add("[1:a]anull[outa]")
        } else {
            val audioLabels = mutableListOf<String>()
            audioLocalClips.forEachIndexed { idx, clip ->
                val inputIdx = 2 + idx
                val durSecClip = ((clip.sourceEndMs - clip.sourceStartMs) / 1000.0)
                    .coerceAtLeast(0.1)
                val delayMs = clip.timelineStartMs.toInt().coerceAtLeast(0)
                val speed = clip.speed.coerceAtLeast(0.01f)

                val chain = mutableListOf<String>()
                chain.add(
                    "atrim=start=${clip.sourceStartMs / 1000.0}:duration=$durSecClip"
                )
                chain.add("asetpts=PTS-STARTPTS")
                if (delayMs > 0) chain.add("adelay=$delayMs|$delayMs")
                if (speed != 1.0f) chain.add("atempo=${speed.coerceIn(0.5f, 2.0f)}")
                if (clip.volume != 1.0f) chain.add("volume=${clip.volume}")
                chain.add("aresample=44100")

                val label = "audio$idx"
                filterParts.add("[$inputIdx:a]${chain.joinToString(",")}[$label]")
                audioLabels.add("[$label]")
            }
            filterParts.add(
                "${audioLabels.joinToString("")}amix=" +
                        "inputs=${audioLabels.size}:duration=longest[outa]"
            )
        }

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
                                val p = ((t / (durationMs.toDouble() * 1.2))
                                    .coerceIn(0.0, 0.95)).toFloat()
                                onProgress(0.50f + p * 0.50f)
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

    // ═══════════════════════════════════════════════════════════
    //  SYNTHETIC EXPORT (text/sticker only — legacy path)
    // ═══════════════════════════════════════════════════════════
    private suspend fun exportSynthetic(
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

        onProgress(0.01f)

        val sequences = try {
            TextBitmapRenderer.renderCombinedOverlays(
                context = context,
                textClips = textClips.filter { it.isTextClip || it.isStickerClip },
                imageClips = textClips.filter {
                    it.isVisualClip && it.type.startsWith("image/")
                },
                overlayClips = emptyList(),
                W = targetW, H = targetH, fps = fps,
                totalDurationMs = totalDurationMs,
                onProgress = { p -> onProgress(p * 0.40f) }
            )
        } catch (e: Throwable) {
            Log.e("EXPORT", "Synthetic render failed", e)
            emptyList()
        }

        onProgress(0.40f)

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
                                onProgress(0.40f + p * 0.60f)
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

    // ═══════════════════════════════════════════════════════════
    //  FILE HELPERS
    // ═══════════════════════════════════════════════════════════
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
                val mimeType = if (sourceFile.name.endsWith(".mov"))
                    "video/quicktime" else "video/mp4"
                val docUri = DocumentsContract.createDocument(
                    context.contentResolver, parentDocUri, mimeType, sourceFile.name
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
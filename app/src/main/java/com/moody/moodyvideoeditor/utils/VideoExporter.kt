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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ExportMode {
    VIDEO,
    AUDIO,
    IMAGE_SEQUENCE
}

class VideoExporter(
    private val context: Context,
    private val onProgress: (Float) -> Unit,
    private val onSuccess: (Uri) -> Unit,
    private val onError: (String) -> Unit,
    private val onCancelled: () -> Unit = {}
) {
    private var ffmpeg: FFmpegExecutor? = null
    private var sessionId: Long? = null

    @Volatile
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
        customEndMs: Long = 0L,
        exportMode: String = "video",
        audioFormat: String = "mp3",
        audioBitrateKbps: Int = 192,
        imageFormat: String = "jpeg",
        jpegQuality: Int = 90
    ) {
        isCancelled = false
        ffmpeg = null
        sessionId = null

        Log.e("EXPORT", "=== START ===")
        Log.e("EXPORT", "clips=${clips.size}, mode=$exportMode, format=$format")
        Log.e("EXPORT", "resolution=$resolution, fps=$fps, bitrate=$bitrateKbps")
        Log.e("EXPORT", "custom folder=$customFolderUri")
        Log.e("EXPORT", "range: $customStartMs - $customEndMs")

        if (clips.isEmpty()) {
            onError("No content to export")
            return
        }
        if (fps <= 0 || bitrateKbps <= 0) {
            onError("FPS and bitrate must be greater than zero")
            return
        }

        val totalTimeline = clips.maxOfOrNull { it.timelineEndMs } ?: 5000L
        if (totalTimeline <= 0L) {
            onError("The project has no exportable duration")
            return
        }
        val rangeStart = customStartMs.coerceIn(0L, totalTimeline)
        val rangeEnd = if (customEndMs > rangeStart) {
            customEndMs.coerceAtMost(totalTimeline)
        } else totalTimeline
        if (rangeEnd <= rangeStart) {
            onError("The selected export range is empty")
            return
        }

        Log.i("EXPORT_RANGE", "Range: $rangeStart - $rangeEnd (of $totalTimeline)")

        when (exportMode) {
            "audio" -> {
                exportAudio(
                    allClips = clips,
                    fileName = fileName,
                    audioFormat = audioFormat,
                    audioBitrateKbps = audioBitrateKbps,
                    customFolderUri = customFolderUri,
                    rangeStart = rangeStart,
                    rangeEnd = rangeEnd,
                    durationMs = rangeEnd - rangeStart
                )
                return
            }

            "image" -> {
                exportImages(
                    allClips = clips,
                    fileName = fileName,
                    aspectRatio = aspectRatio,
                    resolution = resolution,
                    fps = fps,
                    imageFormat = imageFormat,
                    jpegQuality = jpegQuality,
                    customFolderUri = customFolderUri,
                    rangeStart = rangeStart,
                    rangeEnd = rangeEnd,
                    durationMs = rangeEnd - rangeStart
                )
                return
            }
        }

        val allVisual = clips.filter {
            it.isVisualClip &&
                    it.uri.toString().isNotBlank() &&
                    it.uri != Uri.EMPTY
        }
        val baseTrackIndex = allVisual.minOfOrNull { it.trackIndex } ?: 0
        val baseVisualClips = allVisual.filter { it.trackIndex == baseTrackIndex }
        val higherTrackVisualClips = allVisual.filter { it.trackIndex > baseTrackIndex }

        val higherTrackImageClips = higherTrackVisualClips.filter {
            it.type.startsWith("image/")
        }

        val higherTrackVideoClips = higherTrackVisualClips.filter {
            !it.type.startsWith("image/")
        }
        if (higherTrackVideoClips.isNotEmpty()) {
            Log.w(
                "EXPORT",
                "${higherTrackVideoClips.size} higher-track video clips skipped"
            )
        }

        val overlayImageClips = trimClipsToRange(
            baseVisualClips.filter {
                it.type.startsWith("image/") && it.trackIndex > 0
            } + higherTrackImageClips,
            rangeStart, rangeEnd
        )
        val trimmedVisualClips = trimClipsToRange(
            baseVisualClips.filter {
                !(it.type.startsWith("image/") && it.trackIndex > 0)
            },
            rangeStart, rangeEnd
        )

        val audioOnlyClips = trimClipsToRange(
            clips.filter {
                it.isAudio &&
                        !it.isAudioEffectClip &&
                        it.uri.toString().isNotBlank() &&
                        it.uri != Uri.EMPTY
            },
            rangeStart, rangeEnd
        )

        val trimmedTextClips = trimClipsToRange(
            clips.filter { it.isTextClip || it.isStickerClip },
            rangeStart, rangeEnd
        )

        val trimmedOverlayClips = trimClipsToRange(
            clips.filter {
                it.isOverlayClip ||
                        (it.isEffectClip && it.effectState?.overlay != null)
            },
            rangeStart, rangeEnd
        )

        val vizClips = clips.filter {
            it.isVisualizerClip &&
                    it.timelineEndMs > rangeStart &&
                    it.timelineStartMs < rangeEnd
        }

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
            hasBaseVideo -> {
                val outputFile = createOutputFile(fileName, format)
                onProgress(0.01f)

                val textSequences = try {
                    TextBitmapRenderer.renderCombinedOverlays(
                        context = context,
                        textClips = trimmedTextClips,
                        imageClips = overlayImageClips,
                        overlayClips = trimmedOverlayClips,
                        W = targetW, H = targetH, fps = fps,
                        totalDurationMs = exportDurationMs,
                        onProgress = { p -> onProgress(p * 0.20f) },
                        shouldCancel = { isCancelled },
                        imageFormat = imageFormat,
                        jpegQuality = jpegQuality
                    )
                } catch (e: CancellationException) {
                    if (isCancelled) {
                        onCancelled()
                        return
                    }
                    throw e
                } catch (e: Exception) {
                    Log.e("EXPORT", "Text render failed", e)
                    onError("Text render failed: ${e.message}")
                    return
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
                            onProgress = { p -> onProgress(0.20f + p * 0.20f) },
                            shouldCancel = { isCancelled },
                            imageFormat = imageFormat,
                            jpegQuality = jpegQuality
                        )
                    } catch (e: CancellationException) {
                        cleanupSequences(textSequences)
                        if (isCancelled) {
                            onCancelled()
                            return
                        }
                        throw e
                    } catch (e: Exception) {
                        Log.e("EXPORT", "Visualizer render failed", e)
                        cleanupSequences(textSequences)
                        onError("Visualizer render failed: ${e.message}")
                        return
                    }
                } else emptyList()

                val allSequences = (textSequences + vizSequences)
                    .sortedBy { it.trackIndex }

                onProgress(0.40f)

                ffmpeg = FFmpegExecutor(
                    context = context,
                    onProgress = { p ->
                        if (!isCancelled) onProgress(0.40f + p * 0.60f)
                    },
                    onSuccess = { file ->
                        if (isCancelled) {
                            cleanupSequences(allSequences)
                            return@FFmpegExecutor
                        }
                        completeExport(file, customFolderUri, allSequences)
                    },
                    onError = { msg ->
                        cleanupSequences(allSequences)
                        if (isCancelled) onCancelled() else onError(msg)
                    }
                )
                if (isCancelled) {
                    cleanupSequences(allSequences)
                    onCancelled()
                    return
                }
                try {
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
                } catch (e: CancellationException) {
                    cleanupSequences(allSequences)
                    if (isCancelled) onCancelled() else throw e
                }
            }

            hasSynthetic -> {
                try {
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
                        customFolderUri = customFolderUri,
                        imageFormat = imageFormat,
                        jpegQuality = jpegQuality
                    )
                } catch (e: CancellationException) {
                    if (isCancelled) onCancelled() else throw e
                }
            }

            else -> onError("Nothing to export in selected range")
        }
    }

    private suspend fun exportAudio(
        allClips: List<EditorClip>,
        fileName: String,
        audioFormat: String,
        audioBitrateKbps: Int,
        customFolderUri: String?,
        rangeStart: Long,
        rangeEnd: Long,
        durationMs: Long
    ) {
        val ext = if (audioFormat == "m4a") "m4a" else "mp3"
        val outputFile = createOutputFile(fileName, ext)

        Log.e("EXPORT_AUDIO", "Starting audio export: ${outputFile.absolutePath}")

        onProgress(0.01f)

        val executor = FFmpegExecutor(
            context = context,
            onProgress = { p -> if (!isCancelled) onProgress(0.05f + p * 0.90f) },
            onSuccess = { file ->
                if (isCancelled) {
                    onCancelled()
                    return@FFmpegExecutor
                }
                Log.e("EXPORT_AUDIO", "FFmpeg done: ${file.absolutePath}")
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    val uri = try {
                        saveAudioToGallerySync(
                            file = file,
                            customFolderUri = customFolderUri,
                            mimeType = if (audioFormat == "m4a") "audio/mp4"
                            else "audio/mpeg"
                        )
                    } catch (t: Throwable) {
                        Log.e("EXPORT_AUDIO", "Save failed", t)
                        null
                    }
                    withContext(Dispatchers.Main) {
                        onProgress(1f)
                        onSuccess(uri ?: Uri.fromFile(file))
                    }
                }
            },
            onError = { msg ->
                Log.e("EXPORT_AUDIO", "FFmpeg error: $msg")
                if (isCancelled) onCancelled() else onError(msg)
            }
        )
        ffmpeg = executor

        try {
            executor.exportAudioOnly(
                allClips = allClips,
                outputFile = outputFile,
                durationMs = durationMs,
                audioFormat = audioFormat,
                bitrateKbps = audioBitrateKbps,
                rangeStartMs = rangeStart,
                rangeEndMs = rangeEnd
            )
        } catch (e: CancellationException) {
            if (isCancelled) onCancelled() else throw e
        }
    }

    private suspend fun exportImages(
        allClips: List<EditorClip>,
        fileName: String,
        aspectRatio: String,
        resolution: String,
        fps: Int,
        imageFormat: String,
        jpegQuality: Int,
        customFolderUri: String?,
        rangeStart: Long,
        rangeEnd: Long,
        durationMs: Long
    ) {
        val (targetW, targetH) = ExportSettings.targetDimensions(
            resolution, aspectRatio
        )

        val baseFolder = File(context.cacheDir, "MoodyExports")
        if (!baseFolder.exists()) baseFolder.mkdirs()
        val outputDir = File(baseFolder, fileName)
        if (outputDir.exists()) outputDir.deleteRecursively()
        outputDir.mkdirs()

        Log.e("EXPORT_IMG", "Starting image export: ${outputDir.absolutePath}")

        onProgress(0.01f)

        val executor = FFmpegExecutor(
            context = context,
            onProgress = { p -> if (!isCancelled) onProgress(0.05f + p * 0.90f) },
            onSuccess = { dir ->
                if (isCancelled) {
                    onCancelled()
                    return@FFmpegExecutor
                }
                Log.e("EXPORT_IMG", "FFmpeg done: ${dir.absolutePath}")
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    val uri = try {
                        saveSequenceToGallerySync(
                            sourceDir = dir,
                            customFolderUri = customFolderUri,
                            format = imageFormat
                        )
                    } catch (t: Throwable) {
                        Log.e("EXPORT_IMG", "Save failed", t)
                        null
                    }
                    withContext(Dispatchers.Main) {
                        onProgress(1f)
                        onSuccess(uri ?: Uri.fromFile(dir))
                    }
                }
            },
            onError = { msg ->
                Log.e("EXPORT_IMG", "FFmpeg error: $msg")
                if (isCancelled) onCancelled() else onError(msg)
            }
        )
        ffmpeg = executor

        try {
            executor.exportImageSequence(
                allClips = allClips,
                outputDir = outputDir,
                targetW = targetW,
                targetH = targetH,
                fps = fps,
                durationMs = durationMs,
                imageFormat = imageFormat,
                jpegQuality = jpegQuality,
                rangeStartMs = rangeStart,
                rangeEndMs = rangeEnd,
                onFrameProgress = { _, _ -> }
            )
        } catch (e: CancellationException) {
            if (isCancelled) onCancelled() else throw e
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

    private fun completeExport(
        file: File,
        customFolderUri: String?,
        sequences: List<TextOverlaySequence>
    ) {
        try {
            Log.e("EXPORT_SAVE", "Complete export: ${file.absolutePath}")
            Log.e("EXPORT_SAVE", "Custom folder: $customFolderUri")

            val galleryUri = saveToGallery(file, customFolderUri)
            Log.e("EXPORT_SAVE", "Result URI: $galleryUri")

            onSuccess(galleryUri ?: Uri.fromFile(file))
        } catch (e: Exception) {
            Log.e("EXPORT_SAVE", "Complete export failed", e)
            onError("Could not save export: ${e.message}")
        } finally {
            cleanupSequences(sequences)
        }
    }

    private fun cleanupSequences(sequences: List<TextOverlaySequence>) {
        sequences.mapNotNull { it.workingDirectory }.distinct().forEach { directory ->
            if (directory.exists() && !directory.deleteRecursively()) {
                Log.w("EXPORT", "Could not remove: ${directory.name}")
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
        customFolderUri: String?,
        imageFormat: String,
        jpegQuality: Int
    ) {
        val (targetW, targetH) = ExportSettings.targetDimensions(resolution, aspectRatio)
        val durationMs = (rangeEnd - rangeStart).coerceAtLeast(500L)

        onProgress(0.01f)

        val vizSequences = if (vizClips.isNotEmpty()) {
            try {
                VisualizerBitmapRenderer.renderCombinedOverlays(
                    context = context,
                    visualizerClips = vizClips,
                    allClips = allClips,
                    rangeStart = rangeStart,
                    W = targetW, H = targetH, fps = fps,
                    totalDurationMs = durationMs,
                    onProgress = { p -> onProgress(p * 0.25f) },
                    shouldCancel = { isCancelled },
                    imageFormat = imageFormat,
                    jpegQuality = jpegQuality
                )
            } catch (e: CancellationException) {
                if (isCancelled) {
                    onCancelled()
                    return
                }
                throw e
            } catch (e: Exception) {
                Log.e("EXPORT", "Visualizer render failed", e)
                onError("Visualizer render failed: ${e.message}")
                return
            }
        } else emptyList()

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
                    onProgress = { p -> onProgress(0.25f + p * 0.25f) },
                    shouldCancel = { isCancelled },
                    imageFormat = imageFormat,
                    jpegQuality = jpegQuality
                )
            } catch (e: CancellationException) {
                cleanupSequences(vizSequences)
                if (isCancelled) {
                    onCancelled()
                    return
                }
                throw e
            } catch (e: Exception) {
                Log.e("EXPORT", "Text render failed", e)
                cleanupSequences(vizSequences)
                onError("Text render failed: ${e.message}")
                return
            }
        } else emptyList()

        val allSequences = (vizSequences + textSequences)
            .sortedBy { it.trackIndex }

        onProgress(0.50f)

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

        val (audioLocalFiles, audioLocalClips) = try {
            withContext(Dispatchers.IO) {
                val files = mutableListOf<File>()
                val localClips = mutableListOf<EditorClip>()
                audioToMix.forEach { clip ->
                    if (isCancelled) throw CancellationException("cancelled")
                    val file = copyUriToCache(clip.uri, "audio_${clip.id}.mp3")
                        ?: throw java.io.IOException("Could not read: ${clip.name}")
                    files.add(file)
                    localClips.add(clip)
                }
                files to localClips
            }
        } catch (e: CancellationException) {
            cleanupSequences(allSequences)
            throw e
        } catch (e: Exception) {
            cleanupSequences(allSequences)
            onError("Could not prepare audio: ${e.message}")
            return
        }

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

        args.addAll(
            listOf(
                "-c:v", "libx264",
                "-preset", "ultrafast",
                "-tune", "fastdecode",
                "-b:v", "${bitrateKbps}k",
                "-pix_fmt", "yuv420p",
                "-r", fps.toString(),
                "-c:a", "aac",
                "-b:a", "128k",
                "-movflags", "+faststart",
                "-threads", "0",
                "-t", durSec.toString(),
                outputFile.absolutePath
            )
        )

        try {
            val argsArray = args.toTypedArray()
            val session = FFmpegKit.executeWithArgumentsAsync(
                argsArray,
                { s ->
                    if (isCancelled) {
                        cleanupSequences(allSequences)
                        onCancelled()
                    } else if (ReturnCode.isSuccess(s.returnCode)) {
                        completeExport(outputFile, customFolderUri, allSequences)
                    } else {
                        val logs = s.allLogsAsString ?: "Unknown error"
                        cleanupSequences(allSequences)
                        onError("Export failed:\n${logs.takeLast(1500)}")
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
            cleanupSequences(allSequences)
            onError("FFmpeg error: ${e.message}")
        }
    }

    private fun createOutputFile(fileName: String, format: String): File {
        val dir = File(context.cacheDir, "MoodyExports")
        if (!dir.exists()) dir.mkdirs()
        val ext = when (format.lowercase()) {
            "mov" -> "mov"
            "mp3" -> "mp3"
            "m4a" -> "m4a"
            "png" -> "png"
            "jpg", "jpeg" -> "jpg"
            else -> "mp4"
        }
        return File(dir, "$fileName.$ext")
    }

    private fun saveToGallery(sourceFile: File, customFolderUri: String? = null): Uri? {
        Log.e("EXPORT_SAVE", "saveToGallery: ${sourceFile.name}")
        Log.e("EXPORT_SAVE", "File exists: ${sourceFile.exists()}, size: ${sourceFile.length()}")
        Log.e("EXPORT_SAVE", "Custom folder: $customFolderUri")

        if (customFolderUri != null && customFolderUri.isNotBlank()) {
            val uri = saveToCustomFolder(sourceFile, customFolderUri)
            if (uri != null) {
                Log.e("EXPORT_SAVE", "SUCCESS custom: $uri")
                return uri
            }
            Log.e("EXPORT_SAVE", "Custom folder FAILED, fallback to MediaStore")
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
            Log.e("EXPORT_SAVE", "SUCCESS MediaStore: $uri")
            uri
        } catch (e: Exception) {
            Log.e("EXPORT_SAVE", "MediaStore save failed", e)
            null
        }
    }

    private fun saveToCustomFolder(sourceFile: File, folderUriString: String): Uri? {
        return try {
            Log.e("EXPORT_SAVE", "saveToCustomFolder: $folderUriString")

            val treeUri = Uri.parse(folderUriString)
            val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
            Log.e("EXPORT_SAVE", "parentDocId: $parentDocId")

            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri, parentDocId
            )
            Log.e("EXPORT_SAVE", "parentDocUri: $parentDocUri")

            val mimeType = when {
                sourceFile.name.endsWith(".mov") -> "video/quicktime"
                sourceFile.name.endsWith(".mp4") -> "video/mp4"
                sourceFile.name.endsWith(".mp3") -> "audio/mpeg"
                sourceFile.name.endsWith(".m4a") -> "audio/mp4"
                sourceFile.name.endsWith(".png") -> "image/png"
                sourceFile.name.endsWith(".jpg") ||
                        sourceFile.name.endsWith(".jpeg") -> "image/jpeg"

                else -> "application/octet-stream"
            }

            val docUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                mimeType,
                sourceFile.name
            )

            if (docUri == null) {
                Log.e("EXPORT_SAVE", "createDocument returned null")
                return null
            }

            Log.e("EXPORT_SAVE", "docUri: $docUri")

            context.contentResolver.openOutputStream(docUri)?.use { out ->
                sourceFile.inputStream().use { it.copyTo(out) }
            }

            Log.e("EXPORT_SAVE", "Wrote file: ${sourceFile.length()} bytes")
            docUri
        } catch (e: Exception) {
            Log.e("EXPORT_SAVE", "saveToCustomFolder FAILED", e)
            null
        }
    }

    private suspend fun saveAudioToGallerySync(
        file: File,
        customFolderUri: String?,
        mimeType: String
    ): Uri? = withContext(Dispatchers.IO) {
        Log.e("EXPORT_SAVE_AUDIO", "Audio save: ${file.name}")
        Log.e("EXPORT_SAVE_AUDIO", "File: ${file.absolutePath}, size: ${file.length()}")
        Log.e("EXPORT_SAVE_AUDIO", "Custom folder: $customFolderUri")

        if (customFolderUri != null && customFolderUri.isNotBlank()) {
            val customUri = saveToCustomFolder(file, customFolderUri)
            if (customUri != null) {
                Log.e("EXPORT_SAVE_AUDIO", "SUCCESS custom: $customUri")
                return@withContext customUri
            }
            Log.e("EXPORT_SAVE_AUDIO", "Custom folder FAILED, fallback")
        }

        try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.IS_MUSIC, true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Audio.Media.RELATIVE_PATH,
                        "Music/MoodyEditor"
                    )
                }
            }
            val uri = context.contentResolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values
            )
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                Log.e("EXPORT_SAVE_AUDIO", "SUCCESS MediaStore: $uri")
                uri
            } else {
                Log.e("EXPORT_SAVE_AUDIO", "MediaStore insert null")
                null
            }
        } catch (e: Exception) {
            Log.e("EXPORT_SAVE_AUDIO", "MediaStore save failed", e)
            null
        }
    }

    private suspend fun saveSequenceToGallerySync(
        sourceDir: File,
        customFolderUri: String?,
        format: String
    ): Uri? = withContext(Dispatchers.IO) {
        Log.e("EXPORT_SAVE_IMG", "Sequence save: ${sourceDir.absolutePath}")
        Log.e("EXPORT_SAVE_IMG", "Custom folder: $customFolderUri")

        try {
            val files = sourceDir.listFiles()?.sortedBy { it.name } ?: emptyList()
            Log.e("EXPORT_SAVE_IMG", "Found ${files.size} images")
            if (files.isEmpty()) return@withContext null

            val mimeType = if (format == "jpeg") "image/jpeg" else "image/png"
            val savedUris = mutableListOf<Uri>()

            if (customFolderUri != null && customFolderUri.isNotBlank()) {
                try {
                    val treeUri = Uri.parse(customFolderUri)
                    val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
                    val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri, parentDocId
                    )

                    val subFolder = DocumentsContract.createDocument(
                        context.contentResolver,
                        parentDocUri,
                        "vnd.android.document/directory",
                        sourceDir.name
                    )
                    val targetParent = subFolder ?: parentDocUri

                    files.forEach { f ->
                        try {
                            val docUri = DocumentsContract.createDocument(
                                context.contentResolver,
                                targetParent,
                                mimeType,
                                f.name
                            )
                            if (docUri != null) {
                                context.contentResolver.openOutputStream(docUri)
                                    ?.use { out ->
                                        f.inputStream().use { it.copyTo(out) }
                                    }
                                savedUris.add(docUri)
                            }
                        } catch (e: Exception) {
                            Log.e("EXPORT_SAVE_IMG", "Failed: ${f.name}", e)
                        }
                    }

                    if (savedUris.isNotEmpty()) {
                        Log.e(
                            "EXPORT_SAVE_IMG",
                            "SUCCESS custom: ${savedUris.size} images"
                        )
                        return@withContext savedUris.first()
                    }
                } catch (e: Exception) {
                    Log.e("EXPORT_SAVE_IMG", "Custom sequence FAILED", e)
                }
            }

            files.forEach { f ->
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, f.name)
                        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(
                                MediaStore.Images.Media.RELATIVE_PATH,
                                "Pictures/MoodyEditor/${sourceDir.name}"
                            )
                        }
                    }
                    val uri = context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                    )
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            f.inputStream().use { it.copyTo(out) }
                        }
                        savedUris.add(uri)
                    }
                } catch (e: Exception) {
                    Log.e("EXPORT_SAVE_IMG", "Failed: ${f.name}", e)
                }
            }

            Log.e(
                "EXPORT_SAVE_IMG",
                "SUCCESS MediaStore: ${savedUris.size} images"
            )
            savedUris.firstOrNull()
        } catch (e: Exception) {
            Log.e("EXPORT_SAVE_IMG", "saveSequenceToGallerySync failed", e)
            null
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
            val tempFile = File(
                context.cacheDir,
                "$realFileName.${java.util.UUID.randomUUID()}.part"
            )
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw java.io.IOException("Could not open media URI")
                input.use {
                    tempFile.outputStream().use { output -> it.copyTo(output) }
                }
                if (tempFile.length() <= 0L) {
                    throw java.io.IOException("Media URI produced empty file")
                }
                if (file.exists() && !file.delete()) {
                    throw java.io.IOException("Could not replace cached file")
                }
                if (!tempFile.renameTo(file)) {
                    throw java.io.IOException("Could not finalize cached file")
                }
            } finally {
                tempFile.delete()
            }
            if (file.length() > 0) file else null
        } catch (e: Exception) {
            Log.e("FFMPEG", "Copy failed", e)
            null
        }
    }
}
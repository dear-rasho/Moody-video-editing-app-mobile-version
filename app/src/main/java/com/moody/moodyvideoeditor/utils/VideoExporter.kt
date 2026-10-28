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
        jpegQuality: Int = 90,
        outputWidth: Int = 0,
        outputHeight: Int = 0
    ) {
        isCancelled = false
        ffmpeg = null
        sessionId = null

        Log.e("EXPORT", "=== START ===")
        Log.e("EXPORT", "clips=${clips.size}, mode=$exportMode, format=$format")

        if (clips.isEmpty()) {
            onError("No content to export")
            return
        }
        if (fps <= 0 || bitrateKbps <= 0) {
            onError("FPS and bitrate must be greater than zero")
            return
        }
        if ((outputWidth > 0) != (outputHeight > 0)) {
            onError("Both output dimensions must be provided together")
            return
        }

        val (targetW, targetH) = if (outputWidth > 0 && outputHeight > 0) {
            outputWidth to outputHeight
        } else {
            ExportSettings.targetDimensions(resolution, aspectRatio)
        }
        if (targetW % 2 != 0 || targetH % 2 != 0) {
            onError("Output dimensions must be even numbers")
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
                    targetW = targetW,
                    targetH = targetH,
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
            it.isVisualClip && it.uri.toString().isNotBlank() && it.uri != Uri.EMPTY
        }
        val trimmedVisualClips = trimClipsToRange(allVisual, rangeStart, rangeEnd)

        val allMatteClips = clips.filter { it.isColorMatteClip }
        val trimmedMatteClips = trimClipsToRange(allMatteClips, rangeStart, rangeEnd)
        val overlayImageClips = emptyList<EditorClip>()

        val audioOnlyClips = trimClipsToRange(clips.filter {
            it.isAudio && !it.isAudioEffectClip && it.uri.toString()
                .isNotBlank() && it.uri != Uri.EMPTY
        }, rangeStart, rangeEnd)
        val trimmedTextClips = trimClipsToRange(
            clips.filter { it.isTextClip || it.isStickerClip },
            rangeStart,
            rangeEnd
        )
        val trimmedOverlayClips = trimClipsToRange(
            clips.filter { it.isOverlayClip || (it.isEffectClip && it.effectState?.overlay != null) },
            rangeStart,
            rangeEnd
        )
        val spotlightClips = trimClipsToRange(
            clips.filter { it.isSpotlightClip },
            rangeStart,
            rangeEnd
        )
        val vizClips =
            clips.filter { it.isVisualizerClip && it.timelineEndMs > rangeStart && it.timelineStartMs < rangeEnd }

        val exportDurationMs = rangeEnd - rangeStart
        val hasBaseVideo = trimmedVisualClips.isNotEmpty() || trimmedMatteClips.isNotEmpty()
        val hasSynthetic =
            trimmedTextClips.isNotEmpty() || overlayImageClips.isNotEmpty() ||
                    vizClips.isNotEmpty() || spotlightClips.isNotEmpty()

        when {
            hasBaseVideo -> {
                val outputFile = createOutputFile(fileName, format)
                onProgress(0.01f)

                val textSequences = try {
                    TextBitmapRenderer.renderOverlaysByTrack(
                        context = context,
                        textClips = trimmedTextClips,
                        imageClips = overlayImageClips,
                        overlayClips = trimmedOverlayClips,
                        W = targetW,
                        H = targetH,
                        fps = fps,
                        totalDurationMs = exportDurationMs,
                        onProgress = { p -> onProgress(p * 0.20f) },
                        shouldCancel = { isCancelled },
                        imageFormat = imageFormat,
                        jpegQuality = jpegQuality
                    )
                } catch (e: Exception) {
                    if (isCancelled) {
                        onCancelled(); return
                    }
                    onError("Text render failed: ${e.message}"); return
                }

                val vizSequences = if (vizClips.isNotEmpty()) {
                    try {
                        VisualizerBitmapRenderer.renderCombinedOverlays(
                            context = context,
                            visualizerClips = vizClips,
                            allClips = clips,
                            rangeStart = rangeStart,
                            W = targetW,
                            H = targetH,
                            fps = fps,
                            totalDurationMs = exportDurationMs,
                            onProgress = { p -> onProgress(0.20f + p * 0.20f) },
                            shouldCancel = { isCancelled },
                            imageFormat = imageFormat,
                            jpegQuality = jpegQuality
                        )
                    } catch (e: Exception) {
                        cleanupSequences(textSequences)
                        if (isCancelled) {
                            onCancelled(); return
                        }
                        onError("Visualizer render failed: ${e.message}"); return
                    }
                } else emptyList()

                val spotlightSequences = if (spotlightClips.isNotEmpty()) {
                    try {
                        SpotlightBitmapRenderer.render(
                            context = context,
                            clips = spotlightClips,
                            width = targetW,
                            height = targetH,
                            fps = fps,
                            totalDurationMs = exportDurationMs,
                            onProgress = { p -> onProgress(0.40f + p * 0.10f) },
                            shouldCancel = { isCancelled }
                        )
                    } catch (e: Exception) {
                        cleanupSequences(textSequences + vizSequences)
                        if (isCancelled) {
                            onCancelled(); return
                        }
                        onError("Spotlight render failed: ${e.message}"); return
                    }
                } else emptyList()

                val allSequences =
                    (textSequences + vizSequences + spotlightSequences).sortedBy { it.trackIndex }
                onProgress(0.40f)

                ffmpeg = FFmpegExecutor(
                    context = context,
                    onProgress = { p -> if (!isCancelled) onProgress(0.40f + p * 0.60f) },
                    onSuccess = { file ->
                        if (isCancelled) {
                            cleanupSequences(allSequences); return@FFmpegExecutor
                        }
                        completeExport(file, customFolderUri, allSequences)
                    },
                    onError = { msg ->
                        cleanupSequences(allSequences)
                        if (isCancelled) onCancelled() else onError(msg)
                    }
                )
                if (isCancelled) {
                    cleanupSequences(allSequences); onCancelled(); return
                }

                try {
                    ffmpeg?.export(
                        clips = trimmedVisualClips,
                        allClips = clips,
                        outputFile = outputFile,
                        targetW = targetW,
                        targetH = targetH,
                        fps = fps,
                        bitrateKbps = bitrateKbps,
                        textSequences = allSequences,
                        audioOnlyClips = audioOnlyClips,
                        matteClips = trimmedMatteClips,
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
                        spotlightClips = spotlightClips,
                        matteClips = trimmedMatteClips,
                        audioOnlyClips = audioOnlyClips,
                        rangeStart = rangeStart,
                        rangeEnd = rangeEnd,
                        fileName = fileName,
                        targetW = targetW,
                        targetH = targetH,
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
        onProgress(0.01f)
        val executor = FFmpegExecutor(
            context = context,
            onProgress = { p -> if (!isCancelled) onProgress(0.05f + p * 0.90f) },
            onSuccess = { file ->
                if (isCancelled) {
                    onCancelled(); return@FFmpegExecutor
                }
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    val uri = try {
                        saveAudioToGallerySync(
                            file = file,
                            customFolderUri = customFolderUri,
                            mimeType = if (audioFormat == "m4a") "audio/mp4" else "audio/mpeg"
                        )
                    } catch (t: Throwable) {
                        null
                    }
                    withContext(Dispatchers.Main) {
                        onProgress(1f); onSuccess(
                        uri ?: Uri.fromFile(
                            file
                        )
                    )
                    }
                }
            },
            onError = { msg -> if (isCancelled) onCancelled() else onError(msg) }
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
        targetW: Int,
        targetH: Int,
        fps: Int,
        imageFormat: String,
        jpegQuality: Int,
        customFolderUri: String?,
        rangeStart: Long,
        rangeEnd: Long,
        durationMs: Long
    ) {
        val baseFolder = File(context.cacheDir, "MoodyExports")
        if (!baseFolder.exists()) baseFolder.mkdirs()
        val outputDir = File(baseFolder, fileName)
        if (outputDir.exists()) outputDir.deleteRecursively()
        outputDir.mkdirs()
        onProgress(0.01f)
        val executor = FFmpegExecutor(
            context = context,
            onProgress = { p -> if (!isCancelled) onProgress(0.05f + p * 0.90f) },
            onSuccess = { dir ->
                if (isCancelled) {
                    onCancelled(); return@FFmpegExecutor
                }
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    val uri = try {
                        saveSequenceToGallerySync(
                            sourceDir = dir,
                            customFolderUri = customFolderUri,
                            format = imageFormat
                        )
                    } catch (t: Throwable) {
                        null
                    }
                    withContext(Dispatchers.Main) {
                        onProgress(1f); onSuccess(
                        uri ?: Uri.fromFile(
                            dir
                        )
                    )
                    }
                }
            },
            onError = { msg -> if (isCancelled) onCancelled() else onError(msg) }
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
                onFrameProgress = { _, _ -> })
        } catch (e: CancellationException) {
            if (isCancelled) onCancelled() else throw e
        }
    }

    fun cancel() {
        isCancelled = true
        try {
            ffmpeg?.cancel()
        } catch (_: Throwable) {
        }
    }

    private fun completeExport(
        file: File,
        customFolderUri: String?,
        sequences: List<TextOverlaySequence>
    ) {
        try {
            val galleryUri = saveToGallery(file, customFolderUri)
            onSuccess(galleryUri ?: Uri.fromFile(file))
        } catch (e: Exception) {
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
        return clips.filter { it.timelineEndMs > rangeStart && it.timelineStartMs < rangeEnd }
            .map { clip ->
                val clipStart = clip.timelineStartMs
                val clipEnd = clip.timelineEndMs
                val leftCut = (rangeStart - clipStart).coerceAtLeast(0L)
                val rightCut = (clipEnd - rangeEnd).coerceAtLeast(0L)
                val speed = clip.speed.coerceAtLeast(0.01f)
                val newSourceStart = clip.sourceStartMs + (leftCut * speed).toLong()
                val newSourceEnd =
                    (clip.sourceEndMs - (rightCut * speed).toLong()).coerceAtLeast(newSourceStart + 33L)
                val newTimelineStart = (clipStart - rangeStart).coerceAtLeast(0L)
                val trimmedMask = MaskEngine.trimToTime(clip.mask, leftCut)
                clip.copy(
                    sourceStartMs = newSourceStart,
                    sourceEndMs = newSourceEnd,
                    timelineStartMs = newTimelineStart,
                    mask = trimmedMask,
                    keyframes = KeyframeStore.rebaseToTime(clip.keyframes, leftCut)
                )
            }
    }

    private suspend fun exportSyntheticFull(
        allClips: List<EditorClip>,
        textClips: List<EditorClip>,
        overlayImageClips: List<EditorClip>,
        overlayClips: List<EditorClip>,
        vizClips: List<EditorClip>,
        spotlightClips: List<EditorClip> = emptyList(),
        matteClips: List<EditorClip> = emptyList(),
        audioOnlyClips: List<EditorClip>,
        rangeStart: Long,
        rangeEnd: Long,
        fileName: String,
        targetW: Int,
        targetH: Int,
        fps: Int,
        bitrateKbps: Int,
        customFolderUri: String?,
        imageFormat: String,
        jpegQuality: Int
    ) {
        val durationMs = (rangeEnd - rangeStart).coerceAtLeast(500L)
        onProgress(0.01f)

        val vizSequences = if (vizClips.isNotEmpty()) {
            try {
                VisualizerBitmapRenderer.renderCombinedOverlays(
                    context = context,
                    visualizerClips = vizClips,
                    allClips = allClips,
                    rangeStart = rangeStart,
                    W = targetW,
                    H = targetH,
                    fps = fps,
                    totalDurationMs = durationMs,
                    onProgress = { p -> onProgress(p * 0.25f) },
                    shouldCancel = { isCancelled },
                    imageFormat = imageFormat,
                    jpegQuality = jpegQuality
                )
            } catch (e: Exception) {
                if (isCancelled) {
                    onCancelled(); return
                }
                onError("Visualizer render failed: ${e.message}"); return
            }
        } else emptyList()

        val textSequences =
            if (textClips.isNotEmpty() || overlayImageClips.isNotEmpty() ||
                overlayClips.isNotEmpty()
            ) {
                try {
                    TextBitmapRenderer.renderOverlaysByTrack(
                        context = context,
                        textClips = textClips,
                        imageClips = overlayImageClips,
                        overlayClips = overlayClips,
                        W = targetW,
                        H = targetH,
                        fps = fps,
                        totalDurationMs = durationMs,
                        onProgress = { p -> onProgress(0.25f + p * 0.25f) },
                        shouldCancel = { isCancelled },
                        imageFormat = imageFormat,
                        jpegQuality = jpegQuality
                    )
                } catch (e: Exception) {
                    cleanupSequences(vizSequences)
                    if (isCancelled) {
                        onCancelled(); return
                    }
                    onError("Text render failed: ${e.message}"); return
                }
            } else emptyList()

        val spotlightSequences = if (spotlightClips.isNotEmpty()) {
            try {
                SpotlightBitmapRenderer.render(
                    context = context,
                    clips = spotlightClips,
                    width = targetW,
                    height = targetH,
                    fps = fps,
                    totalDurationMs = durationMs,
                    onProgress = { p -> onProgress(0.50f + p * 0.10f) },
                    shouldCancel = { isCancelled }
                )
            } catch (e: Exception) {
                cleanupSequences(vizSequences + textSequences)
                if (isCancelled) {
                    onCancelled(); return
                }
                onError("Spotlight render failed: ${e.message}"); return
            }
        } else emptyList()

        val allSequences =
            (vizSequences + textSequences + spotlightSequences).sortedBy { it.trackIndex }
        onProgress(0.50f)

        val audioToMix = mutableListOf<EditorClip>()
        audioToMix.addAll(audioOnlyClips)

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
            cleanupSequences(allSequences); throw e
        } catch (e: Exception) {
            cleanupSequences(allSequences); onError("Could not prepare audio: ${e.message}"); return
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

        audioLocalFiles.forEach { f -> args.add("-i"); args.add(f.absolutePath) }

        val seqStartIdx = 2 + audioLocalFiles.size
        allSequences.forEach { seq ->
            if (seq.startSec > 0.0001) {
                args.add("-itsoffset"); args.add("%.4f".format(seq.startSec))
            }
            if (seq.isStaticImage) {
                args.add("-loop"); args.add("1")
                args.add("-framerate"); args.add(seq.fps.toString())
                args.add("-t"); args.add((seq.endSec - seq.startSec).coerceAtLeast(0.01).toString())
            } else {
                args.add("-framerate"); args.add(seq.fps.toString())
                args.add("-start_number"); args.add(seq.startNumber.toString())
            }
            args.add("-i"); args.add(seq.pattern)
        }

        val filterParts = mutableListOf<String>()
        filterParts.add("[0:v]format=yuva420p[base]")

        var lastLabel = "base"
        allSequences.forEachIndexed { idx, seq ->
            val inIdx = seqStartIdx + idx
            val srcLabel = "seqsrc$idx"
            val outLabel = "ov$idx"
            val sourceFilter = if (seq.blendMode.equals("addition", ignoreCase = true)) {
                "format=rgba,geq=r='r(X,Y)*alpha(X,Y)/255':g='g(X,Y)*alpha(X,Y)/255':b='b(X,Y)*alpha(X,Y)/255':a='alpha(X,Y)'"
            } else {
                "format=rgba"
            }
            filterParts.add("[$inIdx:v]$sourceFilter[$srcLabel]")
            val startS = "%.4f".format(seq.startSec)
            val endS = "%.4f".format(seq.endSec)
            if (seq.blendMode.equals("addition", ignoreCase = true)) {
                filterParts.add("[$lastLabel]format=rgba[baseadd$idx]")
                filterParts.add(
                    "[baseadd$idx][$srcLabel]blend=all_mode=addition:shortest=0:enable='between(t,$startS,$endS)'[$outLabel]"
                )
            } else {
                filterParts.add(
                    "[$lastLabel][$srcLabel]overlay=0:0:enable='between(t,$startS,$endS)'[$outLabel]"
                )
            }
            lastLabel = outLabel
        }
        filterParts.add("[$lastLabel]format=yuv420p[outv]")

        val finalOutv =
            WatermarkHelper.addWatermarkToFilter(
                context, args, filterParts, "outv", targetW, targetH, fps
            )

        if (audioLocalFiles.isEmpty()) {
            filterParts.add("[1:a]anull[outa]")
        } else {
            val audioLabels = mutableListOf<String>()
            audioLocalClips.forEachIndexed { idx, clip ->
                val inputIdx = 2 + idx
                val durSecClip =
                    ((clip.sourceEndMs - clip.sourceStartMs) / 1000.0).coerceAtLeast(0.1)
                val delayMs = clip.timelineStartMs.toInt().coerceAtLeast(0)
                val speed = clip.speed.coerceAtLeast(0.01f)
                val chain = mutableListOf<String>()
                chain.add("atrim=start=${clip.sourceStartMs / 1000.0}:duration=$durSecClip")
                chain.add("asetpts=PTS-STARTPTS")
                if (delayMs > 0) chain.add("adelay=$delayMs|$delayMs")
                if (speed != 1.0f) chain.add("atempo=${speed.coerceIn(0.5f, 2.0f)}")
                if (clip.volume != 1.0f) chain.add("volume=${clip.volume}")
                chain.add("aresample=44100")
                val label = "audio$idx"
                filterParts.add("[$inputIdx:a]${chain.joinToString(",")}[$label]")
                audioLabels.add("[$label]")
            }
            filterParts.add("${audioLabels.joinToString("")}amix=inputs=${audioLabels.size}:duration=longest[outa]")
        }

        // 🚀 FIX: FFmpeg ko force karo ki wo saare inputs ko sahi se handle kare
        args.add("-filter_complex")
        args.add(filterParts.joinToString(";"))
        args.add("-map"); args.add("[$finalOutv]")

        // Audio map karte waqt ? ka use karo taaki agar audio na ho to error na aaye
        args.add("-map"); args.add("[outa]?")

        args.addAll(listOf(
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
        ))
        try {
            val argsArray = args.toTypedArray()
            val session = FFmpegKit.executeWithArgumentsAsync(
                argsArray,
                { s ->
                    if (isCancelled) {
                        cleanupSequences(allSequences); onCancelled()
                    } else if (ReturnCode.isSuccess(s.returnCode)) {
                        completeExport(outputFile, customFolderUri, allSequences)
                    } else {
                        val logs = s.allLogsAsString ?: "Unknown error"; cleanupSequences(
                            allSequences
                        ); onError("Export failed:\n${logs.takeLast(1500)}")
                    }
                },
                { _ -> },
                { stats ->
                    if (!isCancelled) {
                        try {
                            val t = stats.time; if (t > 0) {
                                val p = ((t / (durationMs.toDouble() * 1.2)).coerceIn(
                                    0.0,
                                    0.95
                                )).toFloat(); onProgress(0.50f + p * 0.50f)
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            )
            sessionId = session.sessionId
        } catch (e: Exception) {
            cleanupSequences(allSequences); onError("FFmpeg error: ${e.message}")
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
        if (customFolderUri != null && customFolderUri.isNotBlank()) {
            val uri = saveToCustomFolder(sourceFile, customFolderUri)
            if (uri != null) return uri
        }
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, sourceFile.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MoodyEditor")
                }
            }
            val uri =
                context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return null
            context.contentResolver.openOutputStream(uri)
                ?.use { out -> sourceFile.inputStream().use { it.copyTo(out) } }
            uri
        } catch (e: Exception) {
            null
        }
    }

    private fun saveToCustomFolder(sourceFile: File, folderUriString: String): Uri? {
        return try {
            val treeUri = Uri.parse(folderUriString)
            val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
            val mimeType = when {
                sourceFile.name.endsWith(".mov") -> "video/quicktime"
                sourceFile.name.endsWith(".mp4") -> "video/mp4"
                sourceFile.name.endsWith(".mp3") -> "audio/mpeg"
                sourceFile.name.endsWith(".m4a") -> "audio/mp4"
                sourceFile.name.endsWith(".png") -> "image/png"
                sourceFile.name.endsWith(".jpg") || sourceFile.name.endsWith(".jpeg") -> "image/jpeg"
                else -> "application/octet-stream"
            }
            val docUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                mimeType,
                sourceFile.name
            ) ?: return null
            context.contentResolver.openOutputStream(docUri)
                ?.use { out -> sourceFile.inputStream().use { it.copyTo(out) } }
            docUri
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun saveAudioToGallerySync(
        file: File,
        customFolderUri: String?,
        mimeType: String
    ): Uri? = withContext(Dispatchers.IO) {
        if (customFolderUri != null && customFolderUri.isNotBlank()) {
            val customUri = saveToCustomFolder(file, customFolderUri)
            if (customUri != null) return@withContext customUri
        }
        try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.IS_MUSIC, true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/MoodyEditor")
                }
            }
            val uri =
                context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)
                    ?.use { out -> file.inputStream().use { it.copyTo(out) } }; uri
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun saveSequenceToGallerySync(
        sourceDir: File,
        customFolderUri: String?,
        format: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val files = sourceDir.listFiles()?.sortedBy { it.name } ?: emptyList()
            if (files.isEmpty()) return@withContext null
            val mimeType = if (format == "jpeg") "image/jpeg" else "image/png"
            val savedUris = mutableListOf<Uri>()
            if (customFolderUri != null && customFolderUri.isNotBlank()) {
                try {
                    val treeUri = Uri.parse(customFolderUri)
                    val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
                    val parentDocUri =
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
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
                                context.contentResolver.openOutputStream(docUri)?.use { out ->
                                    f.inputStream().use { it.copyTo(out) }
                                }; savedUris.add(docUri)
                            }
                        } catch (_: Exception) {
                        }
                    }
                    if (savedUris.isNotEmpty()) return@withContext savedUris.first()
                } catch (_: Exception) {
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
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values
                    )
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)
                            ?.use { out -> f.inputStream().use { it.copyTo(out) } }; savedUris.add(
                            uri
                        )
                    }
                } catch (_: Exception) {
                }
            }
            savedUris.firstOrNull()
        } catch (e: Exception) {
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
            val tempFile =
                File(context.cacheDir, "$realFileName.${java.util.UUID.randomUUID()}.part")
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw java.io.IOException("Could not open media URI")
                input.use { tempFile.outputStream().use { output -> it.copyTo(output) } }
                if (tempFile.length() <= 0L) throw java.io.IOException("Media URI produced empty file")
                if (file.exists() && !file.delete()) throw java.io.IOException("Could not replace cached file")
                if (!tempFile.renameTo(file)) throw java.io.IOException("Could not finalize cached file")
            } finally {
                tempFile.delete()
            }
            if (file.length() > 0) file else null
        } catch (e: Exception) {
            null
        }
    }
}
package com.moody.moodyvideoeditor.utils

import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.data.EditorClip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val MIN_PARALLEL_EXPORT_TOTAL_MEMORY_BYTES = 8L * 1024L * 1024L * 1024L
private const val MIN_PARALLEL_EXPORT_AVAILABLE_MEMORY_BYTES = 2L * 1024L * 1024L * 1024L

internal fun isParallelExportMemorySafe(
    totalMemoryBytes: Long,
    availableMemoryBytes: Long,
    lowMemory: Boolean
): Boolean =
    !lowMemory &&
            totalMemoryBytes >= MIN_PARALLEL_EXPORT_TOTAL_MEMORY_BYTES &&
            availableMemoryBytes >= MIN_PARALLEL_EXPORT_AVAILABLE_MEMORY_BYTES

internal fun isParallelExportThermallySafe(thermalStatus: Int?): Boolean =
    thermalStatus != null && thermalStatus < PowerManager.THERMAL_STATUS_MODERATE

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
    private var nativeVideoExporter: NativeVideoExporter? = null
    private var sessionId: Long? = null

    @Volatile
    private var isCancelled = false
    private val activeSegmentExporters = ConcurrentHashMap.newKeySet<FFmpegExecutor>()

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
        nativeVideoExporter = null
        sessionId = null
        val exportStartedAtMs = SystemClock.elapsedRealtime()

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
            clips.filter { it.isTextClip },
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
        val visualizerClips = clips.filter {
            it.isVisualizerClip && it.timelineEndMs > rangeStart &&
                    it.timelineStartMs < rangeEnd
        }

        val edgeGlowClips = clips.filter {
            it.isEffectClip &&
                    it.effectState?.edgeGlow != null &&
                    it.timelineEndMs > rangeStart &&
                    it.timelineStartMs < rangeEnd
        }

        val exportDurationMs = rangeEnd - rangeStart
        val exportClips = trimClipsToRange(clips, rangeStart, rangeEnd)
        val hasBaseVideo = trimmedVisualClips.isNotEmpty() || trimmedMatteClips.isNotEmpty()
        val hasSynthetic =
            trimmedTextClips.isNotEmpty() || overlayImageClips.isNotEmpty() ||
                    spotlightClips.isNotEmpty() || visualizerClips.isNotEmpty() ||
                    edgeGlowClips.isNotEmpty()

        when {
            hasBaseVideo -> {
                // ═══════════════════════════════════════════════════════════════
                //  🚀 PARALLEL SEGMENT EXPORT (CapCut-style)
                //  Simple sequential videos → 2-3 parallel segments → concat
                //  Complex videos (transitions, heavy layers) → single-pass fallback
                // ═══════════════════════════════════════════════════════════════
                val parallelEligible = isParallelEligible(
                    exportDurationMs = exportDurationMs,
                    trimmedVisualClips = trimmedVisualClips,
                    visualizerClips = visualizerClips,
                    spotlightClips = spotlightClips,
                    edgeGlowClips = edgeGlowClips,
                    allClips = clips
                )

                if (parallelEligible) {
                    try {
                        onProgress(0.01f)
                        val success = runParallelSegmentExport(
                            allClips = clips,
                            fileName = fileName,
                            targetW = targetW,
                            targetH = targetH,
                            fps = fps,
                            bitrateKbps = bitrateKbps,
                            format = format,
                            customFolderUri = customFolderUri,
                            rangeStart = rangeStart,
                            rangeEnd = rangeEnd,
                            onProgressUpdate = { progress ->
                                if (!isCancelled) onProgress(0.01f + progress * 0.94f)
                            }
                        )
                        if (success) {
                            onProgress(1f)
                            return
                        }
                        Log.w("EXPORT", "Parallel export skipped; using single-pass")
                    } catch (e: CancellationException) {
                        onCancelled()
                        return
                    } catch (e: Exception) {
                        Log.w("EXPORT", "Parallel export failed; using single-pass", e)
                    }
                }

                // ─── Native (Media3) fast path for simple single-clip exports ───
                if (format.equals("mp4", ignoreCase = true) &&
                    trimmedMatteClips.isEmpty() &&
                    trimmedVisualClips.size == 1
                ) {
                    val nativeClip = trimmedVisualClips.single()
                    val expectedAudio = nativeAudioTrackForSimpleClip(
                        clip = nativeClip,
                        audioClips = audioOnlyClips,
                        allClips = clips,
                        rangeStartMs = rangeStart,
                        rangeEndMs = rangeEnd
                    )
                    if (expectedAudio != null) {
                        val exporter = NativeVideoExporter(context) { progress ->
                            if (!isCancelled) onProgress(0.05f + progress * 0.90f)
                        }
                        val includeAudio =
                            expectedAudio.takeIf { exporter.hasAudioTrack(nativeClip.uri) == it }
                        if (includeAudio != null) {
                            val nativeOutput = createOutputFile(fileName, "mp4")
                            nativeVideoExporter = exporter
                            try {
                                Log.i(
                                    "EXPORT_PLAN",
                                    "route=media3-transformer durationMs=${rangeEnd - rangeStart} " +
                                            "resolution=${targetW}x$targetH fps=$fps audio=$includeAudio"
                                )
                                onProgress(0.02f)
                                exporter.export(
                                    inputUri = nativeClip.uri,
                                    outputFile = nativeOutput,
                                    sourceStartMs = nativeClip.sourceStartMs,
                                    sourceEndMs = nativeClip.sourceEndMs,
                                    width = targetW,
                                    height = targetH,
                                    fps = fps,
                                    bitrateKbps = bitrateKbps,
                                    includeAudio = includeAudio
                                )
                                nativeVideoExporter = null
                                if (isCancelled) {
                                    nativeOutput.delete()
                                    onCancelled()
                                    return
                                }
                                onProgress(0.97f)
                                Log.i(
                                    "EXPORT_RESULT",
                                    "route=media3-transformer elapsedMs=" +
                                            "${SystemClock.elapsedRealtime() - exportStartedAtMs} " +
                                            "outputBytes=${nativeOutput.length()}"
                                )
                                completeExport(nativeOutput, customFolderUri, emptyList())
                                onProgress(1f)
                                return
                            } catch (e: CancellationException) {
                                nativeVideoExporter = null
                                nativeOutput.delete()
                                if (isCancelled) {
                                    onCancelled()
                                    return
                                }
                                throw e
                            } catch (e: Exception) {
                                nativeVideoExporter = null
                                nativeOutput.delete()
                                onProgress(0.01f)
                                Log.w(
                                    "NATIVE_EXPORT",
                                    "Native Media3 export failed; falling back to FFmpeg",
                                    e
                                )
                            }
                        }
                    }
                }

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
                        onProgress = { p -> onProgress(p * 0.15f) },
                        shouldCancel = { isCancelled },
                        imageFormat = imageFormat,
                        jpegQuality = jpegQuality
                    )
                } catch (e: Exception) {
                    if (isCancelled) {
                        onCancelled(); return
                    }
                    Log.e("EXPORT", "Text overlay rendering failed", e)
                    onError("Text render failed: ${e.message}"); return
                }

                val visualizerSequences = if (visualizerClips.isNotEmpty()) {
                    try {
                        VisualizerBitmapRenderer.render(
                            context = context,
                            visualizerClips = visualizerClips,
                            allClips = clips,
                            rangeStartMs = rangeStart,
                            rangeEndMs = rangeEnd,
                            width = targetW,
                            height = targetH,
                            fps = fps,
                            onProgress = { p -> onProgress(0.15f + p * 0.15f) },
                            shouldCancel = { isCancelled }
                        )
                    } catch (e: Exception) {
                        cleanupSequences(textSequences)
                        if (isCancelled) {
                            onCancelled(); return
                        }
                        Log.e("EXPORT", "Visualizer rendering failed", e)
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
                            onProgress = { p -> onProgress(0.30f + p * 0.08f) },
                            shouldCancel = { isCancelled }
                        )
                    } catch (e: Exception) {
                        cleanupSequences(textSequences + visualizerSequences)
                        if (isCancelled) {
                            onCancelled(); return
                        }
                        Log.e("EXPORT", "Spotlight rendering failed", e)
                        onError("Spotlight render failed: ${e.message}"); return
                    }
                } else emptyList()

                val edgeGlowSequences = if (edgeGlowClips.isNotEmpty()) {
                    try {
                        EdgeGlowBitmapRenderer.render(
                            context = context,
                            clips = edgeGlowClips,
                            width = targetW,
                            height = targetH,
                            fps = fps,
                            rangeStartMs = rangeStart,
                            rangeEndMs = rangeEnd,
                            onProgress = { p -> onProgress(0.38f + p * 0.02f) },
                            shouldCancel = { isCancelled }
                        )
                    } catch (e: Exception) {
                        cleanupSequences(textSequences + visualizerSequences + spotlightSequences)
                        if (e is CancellationException) {
                            if (isCancelled) {
                                onCancelled(); return
                            }
                            throw e
                        }
                        if (isCancelled) {
                            onCancelled(); return
                        }
                        Log.e("EXPORT", "Edge glow rendering failed", e)
                        onError("Edge glow render failed: ${e.message}"); return
                    }
                } else emptyList()

                val allSequences =
                    (textSequences + visualizerSequences + spotlightSequences + edgeGlowSequences)
                        .sortedBy { it.trackIndex }
                onProgress(0.40f)
                Log.i(
                    "EXPORT_PLAN",
                    "route=ffmpeg durationMs=$exportDurationMs rangeMs=$rangeStart-$rangeEnd " +
                            "clips=${exportClips.size} resolution=${targetW}x$targetH fps=$fps"
                )

                ffmpeg = FFmpegExecutor(
                    context = context,
                    onProgress = { p -> if (!isCancelled) onProgress(0.40f + p * 0.60f) },
                    onSuccess = { file ->
                        if (isCancelled) {
                            cleanupSequences(allSequences); return@FFmpegExecutor
                        }
                        Log.i(
                            "EXPORT_RESULT",
                            "route=ffmpeg elapsedMs=" +
                                    "${SystemClock.elapsedRealtime() - exportStartedAtMs} " +
                                    "outputBytes=${file.length()}"
                        )
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
                        allClips = exportClips,
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
                        visualizerClips = visualizerClips,
                        spotlightClips = spotlightClips,
                        edgeGlowClips = edgeGlowClips,
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

    suspend fun prepareExportCache(
        clips: List<EditorClip>,
        targetW: Int,
        targetH: Int,
        fps: Int,
        bitrateKbps: Int,
        format: String
    ) {
        isCancelled = false
        val durationMs = clips.maxOfOrNull { it.timelineEndMs } ?: return
        if (durationMs <= 0L) return

        val visualClips = trimClipsToRange(
            clips.filter {
                it.isVisualClip && it.uri.toString().isNotBlank() && it.uri != Uri.EMPTY
            },
            0L,
            durationMs
        )
        val visualizerClips = clips.filter {
            it.isVisualizerClip && it.timelineEndMs > 0L && it.timelineStartMs < durationMs
        }
        val spotlightClips = clips.filter {
            it.isSpotlightClip && it.timelineEndMs > 0L && it.timelineStartMs < durationMs
        }
        val edgeGlowClips = clips.filter {
            it.isEffectClip && it.effectState?.edgeGlow != null &&
                    it.timelineEndMs > 0L && it.timelineStartMs < durationMs
        }
        if (!isParallelEligible(
                durationMs,
                visualClips,
                visualizerClips,
                spotlightClips,
                edgeGlowClips,
                clips
            )
        ) {
            return
        }

        runParallelSegmentExport(
            allClips = clips,
            fileName = "prepared",
            targetW = targetW,
            targetH = targetH,
            fps = fps,
            bitrateKbps = bitrateKbps,
            format = format,
            customFolderUri = null,
            rangeStart = 0L,
            rangeEnd = durationMs,
            preRenderOnly = true
        )
    }

    /**
     * ⚡ NEW: Decides whether a project is safe for parallel segment export.
     *
     * Conditions (ALL must be true):
     *  - Duration >= 45s (below that, overhead > benefit)
     *  - At least 2 visual clips, or 1 visual clip with composited layers
     *  - No visualizer / spotlight / edge-glow clips (these render frame ranges)
     *  - No active transitions (cross-segment xfade doesn't concat)
     *  - No layer shadows (per-frame compositing)
     *  - No animated transforms (per-frame keyframe eval)
     */
    private fun isParallelEligible(
        exportDurationMs: Long,
        trimmedVisualClips: List<EditorClip>,
        visualizerClips: List<EditorClip>,
        spotlightClips: List<EditorClip>,
        edgeGlowClips: List<EditorClip>,
        allClips: List<EditorClip>
    ): Boolean {
        if (exportDurationMs < 45_000L) return false
        if (trimmedVisualClips.isEmpty()) return false
        if (trimmedVisualClips.size == 1 && allClips.none {
                it.isAdjustmentClip || it.isColorMatteClip || it.isTextClip ||
                        it.isOverlayClip || it.isEffectClip || it.isFilterLayerClip
            }
        ) return false
        if (visualizerClips.isNotEmpty()) return false
        if (spotlightClips.isNotEmpty()) return false
        if (edgeGlowClips.isNotEmpty()) return false
        if (allClips.any { it.transition?.isActive == true }) return false
        if (allClips.any { it.layerShadow.enabled }) return false
        if (allClips.any { TransformApplier.hasAnimatedTransform(it) }) return false
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (activityManager == null) {
            Log.w("EXPORT_PARALLEL", "Skipping parallel export: ActivityManager unavailable")
            return false
        }
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        if (!isParallelExportMemorySafe(
                memoryInfo.totalMem,
                memoryInfo.availMem,
                memoryInfo.lowMemory
            )
        ) {
            Log.i(
                "EXPORT_PARALLEL",
                "Skipping parallel export: insufficient memory headroom " +
                        "totalBytes=${memoryInfo.totalMem} availableBytes=${memoryInfo.availMem} " +
                        "lowMemory=${memoryInfo.lowMemory}"
            )
            return false
        }
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.currentThermalStatus
        } else {
            null
        }
        if (!isParallelExportThermallySafe(thermalStatus)) {
            Log.i(
                "EXPORT_PARALLEL",
                "Skipping parallel export: thermal status is elevated or unavailable " +
                        "status=$thermalStatus"
            )
            return false
        }
        return true
    }

    /**
     * Splits the timeline into 2-3 segments, renders them in parallel, then
     * concatenates with stream copy. Export falls back to single-pass on failure.
     */
    private suspend fun runParallelSegmentExport(
        allClips: List<EditorClip>,
        fileName: String,
        targetW: Int,
        targetH: Int,
        fps: Int,
        bitrateKbps: Int,
        format: String,
        customFolderUri: String?,
        rangeStart: Long,
        rangeEnd: Long,
        preRenderOnly: Boolean = false,
        onProgressUpdate: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val totalDurMs = rangeEnd - rangeStart
        if (totalDurMs <= 0L) return@withContext false
        val operationStartedAtMs = SystemClock.elapsedRealtime()

        val segmentCount = if (totalDurMs < 120_000L) 2 else 3
        val segDurMs = totalDurMs / segmentCount
        Log.i(
            "EXPORT_PARALLEL",
            "Starting parallel export: totalMs=$totalDurMs segs=$segmentCount segMs=$segDurMs"
        )

        val segFiles = mutableListOf<File>()
        val segErrors = Collections.synchronizedList(mutableListOf<String>())
        val cacheHits = AtomicLong(0L)
        val segmentProgress = FloatArray(segmentCount)
        val progressLock = Any()

        fun reportSegmentProgress(index: Int, progress: Float) {
            val averageProgress = synchronized(progressLock) {
                segmentProgress[index] =
                    maxOf(segmentProgress[index], progress.coerceIn(0f, 1f))
                segmentProgress.average().toFloat()
            }
            if (!preRenderOnly) onProgressUpdate(averageProgress)
        }

        try {
            coroutineScope {
                val jobs = (0 until segmentCount).map { i ->
                    async(Dispatchers.Default) {
                        val segStart = rangeStart + i * segDurMs
                        val segEnd = if (i == segmentCount - 1) rangeEnd
                        else rangeStart + (i + 1) * segDurMs
                        var segFile: File? = null
                        var cacheKey: String? = null
                        var cacheReserved = false
                        var segTextSeq: List<TextOverlaySequence> = emptyList()

                        try {
                            val segVisual = trimClipsToRange(
                                allClips.filter {
                                    it.isVisualClip &&
                                            it.uri.toString().isNotBlank() &&
                                            it.uri != Uri.EMPTY
                                },
                                segStart, segEnd
                            )
                            val segAudio = trimClipsToRange(
                                allClips.filter {
                                    it.isAudio && !it.isAudioEffectClip &&
                                            it.uri.toString().isNotBlank() &&
                                            it.uri != Uri.EMPTY
                                },
                                segStart, segEnd
                            )
                            val segText = trimClipsToRange(
                                allClips.filter { it.isTextClip }, segStart, segEnd
                            )
                            val segOverlay = trimClipsToRange(
                                allClips.filter {
                                    it.isOverlayClip ||
                                            (it.isEffectClip && it.effectState?.overlay != null)
                                },
                                segStart, segEnd
                            )
                            val segAll = trimClipsToRange(allClips, segStart, segEnd)
                            val segMattes = segAll.filter { it.isColorMatteClip }
                            val segDur = segEnd - segStart
                            val key = ExportSegmentCache.keyFor(
                                segmentIndex = i,
                                startMs = segStart,
                                endMs = segEnd,
                                clips = segAll,
                                width = targetW,
                                height = targetH,
                                fps = fps,
                                bitrateKbps = bitrateKbps,
                                format = format
                            )
                            cacheKey = key
                            ExportSegmentCache.find(context, key, format)?.let {
                                cacheHits.incrementAndGet()
                                reportSegmentProgress(i, 1f)
                                return@async it
                            }
                            if (preRenderOnly) {
                                val estimatedBytes =
                                    (segDur.toDouble() * (bitrateKbps + 128) / 8.0 * 1.15)
                                        .toLong()
                                        .coerceAtLeast(1L)
                                if (!ExportSegmentCache.reserve(context, key, estimatedBytes)) {
                                    Log.i(
                                        "EXPORT_CACHE",
                                        "Skipping segment $i: cache storage budget reached"
                                    )
                                    return@async null
                                }
                                cacheReserved = true
                                segFile = ExportSegmentCache.temporaryFile(context, key, format)
                            } else {
                                segFile = createOutputFile("${fileName}_seg$i", format)
                            }
                            val outputFile = checkNotNull(segFile)

                            segTextSeq = if (segText.isNotEmpty() || segOverlay.isNotEmpty()) {
                                TextBitmapRenderer.renderOverlaysByTrack(
                                    context = context,
                                    textClips = segText,
                                    imageClips = emptyList(),
                                    overlayClips = segOverlay,
                                    W = targetW,
                                    H = targetH,
                                    fps = fps,
                                    totalDurationMs = segDur,
                                    onProgress = { progress ->
                                        reportSegmentProgress(i, progress * 0.25f)
                                    },
                                    shouldCancel = { isCancelled },
                                    imageFormat = "jpeg",
                                    jpegQuality = 90
                                )
                            } else emptyList()

                            var succeeded = false
                            var innerExporter: FFmpegExecutor? = null
                            suspendCancellableCoroutine<Unit> { cont ->
                                val exporter = FFmpegExecutor(
                                    context = context,
                                    onProgress = { progress ->
                                        reportSegmentProgress(i, 0.25f + progress * 0.75f)
                                    },
                                    onSuccess = {
                                        succeeded = true
                                        if (cont.isActive) cont.resumeWith(Result.success(Unit))
                                    },
                                    onError = { msg ->
                                        segErrors.add("seg$i: $msg")
                                        if (cont.isActive) cont.resumeWith(Result.success(Unit))
                                    }
                                )
                                innerExporter = exporter
                                activeSegmentExporters.add(exporter)
                                cont.invokeOnCancellation {
                                    exporter.cancel()
                                    activeSegmentExporters.remove(exporter)
                                }
                                launch(Dispatchers.Default) {
                                    try {
                                        exporter.export(
                                            clips = segVisual,
                                            allClips = segAll,
                                            outputFile = outputFile,
                                            targetW = targetW,
                                            targetH = targetH,
                                            fps = fps,
                                            bitrateKbps = bitrateKbps,
                                            textSequences = segTextSeq,
                                            audioOnlyClips = segAudio,
                                            matteClips = segMattes,
                                            explicitDurationMs = segDur
                                        )
                                    } catch (e: CancellationException) {
                                        if (cont.isActive) cont.cancel(e)
                                    } catch (e: Exception) {
                                        segErrors.add("seg$i: ${e.message}")
                                        if (cont.isActive) {
                                            cont.resumeWith(Result.success(Unit))
                                        }
                                    }
                                }
                            }
                            innerExporter?.let(activeSegmentExporters::remove)
                            cleanupSequences(segTextSeq)

                            if (!succeeded || !outputFile.exists() || outputFile.length() <= 0L) {
                                outputFile.delete()
                                if (cacheReserved) ExportSegmentCache.release(key)
                                null
                            } else if (preRenderOnly) {
                                reportSegmentProgress(i, 1f)
                                val cachedFile =
                                    ExportSegmentCache.commit(context, key, format, outputFile)
                                cacheReserved = false
                                cachedFile
                            } else {
                                reportSegmentProgress(i, 1f)
                                outputFile
                            }
                        } catch (e: CancellationException) {
                            segFile?.delete()
                            if (cacheReserved) cacheKey?.let(ExportSegmentCache::release)
                            cleanupSequences(segTextSeq)
                            throw e
                        } catch (e: Exception) {
                            Log.e("EXPORT_PARALLEL", "Segment $i failed", e)
                            segErrors.add("seg$i: ${e.message}")
                            segFile?.delete()
                            if (cacheReserved) cacheKey?.let(ExportSegmentCache::release)
                            cleanupSequences(segTextSeq)
                            null
                        }
                    }
                }
                segFiles.addAll(jobs.awaitAll().filterNotNull())
            }

            if (preRenderOnly) {
                val availableCacheFiles =
                    segFiles.filter { it.isFile && it.length() > 0L }
                Log.i(
                    "EXPORT_CACHE",
                    "Prepared ${availableCacheFiles.size}/$segmentCount matching export segments " +
                            "cacheHits=${cacheHits.get()} " +
                            "bytes=${availableCacheFiles.sumOf { it.length() }} " +
                            "wallMs=${SystemClock.elapsedRealtime() - operationStartedAtMs}"
                )
                return@withContext availableCacheFiles.isNotEmpty()
            }

            if (segFiles.size != segmentCount) {
                Log.e(
                    "EXPORT_PARALLEL",
                    "Only ${segFiles.size}/$segmentCount segments succeeded: $segErrors"
                )
                segFiles.forEach { it.delete() }
                return@withContext false
            }

            val finalFile = createOutputFile(fileName, format)
            val concatList = File(context.cacheDir, "concat_${System.nanoTime()}.txt")
            try {
                concatList.writeText(
                    segFiles.joinToString("\n") { "file '${it.absolutePath}'" }
                )
                val concatArgs = listOf(
                    "-y",
                    "-f", "concat",
                    "-safe", "0",
                    "-i", concatList.absolutePath,
                    "-c", "copy",
                    "-movflags", "+faststart",
                    finalFile.absolutePath
                )

                var concatOk = false
                suspendCancellableCoroutine<Unit> { cont ->
                    FFmpegKit.executeWithArgumentsAsync(
                        concatArgs.toTypedArray(),
                        { session ->
                            concatOk = ReturnCode.isSuccess(session.returnCode)
                            if (!concatOk) {
                                Log.e(
                                    "EXPORT_PARALLEL",
                                    "Concat failed: ${session.allLogsAsString?.takeLast(1000)}"
                                )
                            }
                            if (cont.isActive) cont.resumeWith(Result.success(Unit))
                        },
                        { },
                        { }
                    )
                }

                if (!concatOk || !finalFile.exists() || finalFile.length() <= 0) {
                    segFiles.forEach { it.delete() }
                    return@withContext false
                }

                Log.i(
                    "EXPORT_PARALLEL",
                    "Parallel export complete: bytes=${finalFile.length()} segs=$segmentCount " +
                            "cacheHits=${cacheHits.get()}"
                )

                completeExport(finalFile, customFolderUri, emptyList())
                return@withContext true
            } finally {
                concatList.delete()
                segFiles.forEach { it.delete() }
            }
        } catch (e: CancellationException) {
            if (!preRenderOnly) segFiles.forEach { it.delete() }
            throw e
        } catch (e: Exception) {
            Log.e("EXPORT_PARALLEL", "Parallel export crashed", e)
            if (!preRenderOnly) segFiles.forEach { it.delete() }
            return@withContext false
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

    private fun nativeAudioTrackForSimpleClip(
        clip: EditorClip,
        audioClips: List<EditorClip>,
        allClips: List<EditorClip>,
        rangeStartMs: Long,
        rangeEndMs: Long
    ): Boolean? {
        if (!clip.type.startsWith("video/") ||
            clip.timelineStartMs > 1L ||
            clip.timelineEndMs < rangeEndMs - rangeStartMs ||
            clip.speed != 1f ||
            clip.volume != 1f ||
            clip.audioFx != "none" ||
            clip.audioAdjustments != com.moody.moodyvideoeditor.data.AudioAdjustmentSettings() ||
            clip.adjustments != AdjustmentData() ||
            !clip.filters.isDefault ||
            !clip.colorWheel.isDefault ||
            clip.overlay.isActive ||
            clip.keyframes.values.any { it.isNotEmpty() } ||
            clip.transition?.isActive == true ||
            clip.mask.isActive ||
            CropHelper.isCropped(clip) ||
            clip.layerShadow.enabled ||
            clip.scale != 1f ||
            clip.scaleX != 1f ||
            clip.scaleY != 1f ||
            clip.rotation != 0f ||
            clip.offsetX != 0f ||
            clip.offsetY != 0f ||
            clip.layerOpacity != 100f ||
            clip.blendMode != "normal" ||
            clip.blendOpacity != 100f ||
            clip.ratio != null ||
            clip.freeze != null ||
            clip.lightLeak != null ||
            clip.spotlight != null ||
            clip.visualizer != null ||
            clip.chromaKey.isActive ||
            clip.effectState != null ||
            clip.textState != null ||
            clip.soundFx != "none"
        ) return null

        val sourceAudio = audioClips.singleOrNull()
        if (audioClips.size > 1) return null
        if (sourceAudio != null &&
            (clip.linkedId == null ||
                    sourceAudio.linkedId != clip.linkedId ||
                    sourceAudio.uri != clip.uri ||
                    sourceAudio.timelineStartMs != clip.timelineStartMs ||
                    sourceAudio.timelineEndMs != clip.timelineEndMs ||
                    sourceAudio.sourceStartMs != clip.sourceStartMs ||
                    sourceAudio.sourceEndMs != clip.sourceEndMs ||
                    sourceAudio.speed != 1f ||
                    sourceAudio.volume != 1f ||
                    sourceAudio.isMuted ||
                    sourceAudio.audioFx != "none" ||
                    sourceAudio.audioAdjustments !=
                    com.moody.moodyvideoeditor.data.AudioAdjustmentSettings() ||
                    sourceAudio.soundFx != "none" ||
                    sourceAudio.keyframes.values.any { it.isNotEmpty() })
        ) return null

        val activeIds = allClips.filter {
            it.timelineEndMs > rangeStartMs && it.timelineStartMs < rangeEndMs
        }.map { it.id }.toSet()
        if (activeIds.any { it != clip.id && it != sourceAudio?.id }) return null
        return sourceAudio != null
    }

    fun cancel() {
        isCancelled = true
        nativeVideoExporter?.cancel()
        activeSegmentExporters.toList().forEach { exporter ->
            exporter.cancel()
        }
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
                val newSourceStart = SpeedEngine.sourcePositionAt(clip, leftCut)
                val newSourceEnd = SpeedEngine.sourcePositionAt(
                    clip,
                    (clip.durationMs - rightCut).coerceAtLeast(leftCut)
                ).coerceAtLeast(newSourceStart + 33L)
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
        visualizerClips: List<EditorClip>,
        spotlightClips: List<EditorClip> = emptyList(),
        edgeGlowClips: List<EditorClip> = emptyList(),
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
                        onProgress = { p -> onProgress(0.05f + p * 0.15f) },
                        shouldCancel = { isCancelled },
                        imageFormat = imageFormat,
                        jpegQuality = jpegQuality
                    )
                } catch (e: Exception) {
                    if (isCancelled) {
                        onCancelled(); return
                    }
                    Log.e("EXPORT", "Text overlay rendering failed", e)
                    onError("Text render failed: ${e.message}"); return
                }
            } else emptyList()

        val visualizerSequences = if (visualizerClips.isNotEmpty()) {
            try {
                VisualizerBitmapRenderer.render(
                    context = context,
                    visualizerClips = visualizerClips,
                    allClips = allClips,
                    rangeStartMs = rangeStart,
                    rangeEndMs = rangeEnd,
                    width = targetW,
                    height = targetH,
                    fps = fps,
                    onProgress = { p -> onProgress(0.20f + p * 0.18f) },
                    shouldCancel = { isCancelled }
                )
            } catch (e: Exception) {
                cleanupSequences(textSequences)
                if (isCancelled) {
                    onCancelled(); return
                }
                Log.e("EXPORT", "Visualizer rendering failed", e)
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
                    totalDurationMs = durationMs,
                    onProgress = { p -> onProgress(0.38f + p * 0.08f) },
                    shouldCancel = { isCancelled }
                )
            } catch (e: Exception) {
                cleanupSequences(textSequences + visualizerSequences)
                if (isCancelled) {
                    onCancelled(); return
                }
                Log.e("EXPORT", "Spotlight rendering failed", e)
                onError("Spotlight render failed: ${e.message}"); return
            }
        } else emptyList()

        val edgeGlowSequences = if (edgeGlowClips.isNotEmpty()) {
            try {
                EdgeGlowBitmapRenderer.render(
                    context = context,
                    clips = edgeGlowClips,
                    width = targetW,
                    height = targetH,
                    fps = fps,
                    rangeStartMs = rangeStart,
                    rangeEndMs = rangeEnd,
                    onProgress = { p -> onProgress(0.46f + p * 0.04f) },
                    shouldCancel = { isCancelled }
                )
            } catch (e: Exception) {
                cleanupSequences(textSequences + visualizerSequences + spotlightSequences)
                if (e is CancellationException) {
                    if (isCancelled) {
                        onCancelled(); return
                    }
                    throw e
                }
                if (isCancelled) {
                    onCancelled(); return
                }
                Log.e("EXPORT", "Edge glow rendering failed", e)
                onError("Edge glow render failed: ${e.message}"); return
            }
        } else emptyList()

        val allSequences =
            (textSequences + visualizerSequences + spotlightSequences + edgeGlowSequences)
                .sortedBy { it.trackIndex }
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
            cleanupSequences(allSequences)
            Log.e("EXPORT", "Could not prepare audio", e)
            onError("Could not prepare audio: ${e.message}")
            return
        }

        val outputFile = createOutputFile(fileName, "mp4")
        val durSec = (durationMs / 1000.0).coerceAtLeast(0.5)

        val args = mutableListOf<String>()
        args.addAll(
            listOf(
                "-filter_complex_threads",
                (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4).toString()
            )
        )
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
                if (clip.audioFx != "none") {
                    val filter = AudioEngine.buildAudioFilter(clip.audioFx, clip.audioFxIntensity)
                    if (filter.isNotBlank()) chain.add(filter)
                }
                chain.add("aresample=44100")
                val label = "audio$idx"
                filterParts.add("[$inputIdx:a]${chain.joinToString(",")}[$label]")
                val adjustedLabel = AudioEngine.appendTimelineAudioAdjustments(
                    filterParts,
                    clip,
                    allClips,
                    rangeStart,
                    label,
                    "audio${idx}adjusted"
                )
                audioLabels.add("[$adjustedLabel]")
            }
            filterParts.add(
                "${audioLabels.joinToString("")}amix=inputs=${audioLabels.size}:" +
                        "duration=longest:normalize=0[outa]"
            )
        }
        val finalAudioLabel = AudioEngine.appendTimelineAudioEffects(
            filterParts,
            allClips,
            rangeStart,
            "outa",
            "finala"
        )

        args.add("-filter_complex")
        args.add(filterParts.joinToString(";"))
        args.add("-map"); args.add("[$finalOutv]")
        args.add("-map"); args.add("[$finalAudioLabel]?")

        val codecCandidates = VideoEncoderPolicy.codecArgumentCandidates(
            targetW, targetH, fps, bitrateKbps
        )
        val codecArguments = codecCandidates.first()
        args.addAll(codecArguments)
        args.addAll(
            listOf(
                "-b:v", "${bitrateKbps}k",
                "-maxrate", "${(bitrateKbps * 1.2).toInt()}k",
                "-bufsize", "${(bitrateKbps * 2).toInt()}k",
                "-pix_fmt", "yuv420p",
                "-r", fps.toString(),
                "-c:a", "aac",
                "-b:a", "128k",
                "-movflags", "+faststart"
            )
        )
        if (codecArguments.getOrNull(1) == "libx264") {
            args.addAll(listOf("-threads", "0"))
        }
        args.addAll(listOf("-t", durSec.toString(), outputFile.absolutePath))
        try {
            submitVideoSession(
                args = args,
                outputFile = outputFile,
                durationMs = durationMs,
                customFolderUri = customFolderUri,
                sequences = allSequences,
                fallbackCodecArguments = codecCandidates.drop(1)
            )
        } catch (e: Exception) {
            cleanupSequences(allSequences)
            Log.e("EXPORT", "FFmpeg export failed", e)
            onError("FFmpeg error: ${e.message}")
        }
    }

    private fun submitVideoSession(
        args: List<String>,
        outputFile: File,
        durationMs: Long,
        customFolderUri: String?,
        sequences: List<TextOverlaySequence>,
        fallbackCodecArguments: List<List<String>>
    ) {
        val startedAtMs = SystemClock.elapsedRealtime()
        val processedMediaMs = AtomicLong(0L)
        val session = FFmpegKit.executeWithArgumentsAsync(
                args.toTypedArray(),
                { s ->
                    val wallMs =
                        (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(1L)
                    val mediaMs = processedMediaMs.get()
                    Log.i(
                        "EXPORT_PERF",
                        "Video session: result=${s.returnCode}, wallMs=$wallMs, " +
                                "mediaMs=$mediaMs, " +
                                "realtimeFactor=${mediaMs / wallMs.toDouble()}"
                    )
                    if (isCancelled) {
                        cleanupSequences(sequences); onCancelled()
                    } else if (ReturnCode.isSuccess(s.returnCode)) {
                        val codecIndex = args.indexOf("-c:v")
                        Log.i(
                            "EXPORT_ENCODER",
                            "FFmpeg session completed with encoder=" +
                                    args.getOrNull(codecIndex + 1)
                        )
                        completeExport(outputFile, customFolderUri, sequences)
                    } else {
                        val logs = s.allLogsAsString ?: "Unknown error"
                        if (fallbackCodecArguments.isNotEmpty() &&
                            VideoEncoderPolicy.isCodecInitializationFailure(logs)
                        ) {
                            val fallbackArgs = VideoEncoderPolicy.replaceCodecArguments(
                                args,
                                fallbackCodecArguments.first()
                            )
                            if (fallbackArgs != null) {
                                val codecIndex = args.indexOf("-c:v")
                                val failedEncoder = args.getOrNull(codecIndex + 1)
                                Log.w(
                                    "EXPORT_ENCODER",
                                    "FFmpeg encoder $failedEncoder failed; retrying with " +
                                            "${fallbackCodecArguments.first().getOrNull(1)}. " +
                                            "Reason: ${logs.takeLast(1200)}"
                                )
                                submitVideoSession(
                                    args = fallbackArgs,
                                    outputFile = outputFile,
                                    durationMs = durationMs,
                                    customFolderUri = customFolderUri,
                                    sequences = sequences,
                                    fallbackCodecArguments = fallbackCodecArguments.drop(1)
                                )
                                return@executeWithArgumentsAsync
                            }
                        }
                        Log.e("EXPORT", "FFmpeg failed: ${logs.takeLast(5000)}")
                        cleanupSequences(sequences)
                        onError("Export failed:\n${logs.takeLast(1500)}")
                    }
                },
                { _ -> },
                { stats ->
                    if (!isCancelled) {
                        try {
                            val t = stats.time; if (t > 0) {
                                processedMediaMs.set(t.toLong())
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
            if (uri.scheme.equals("file", ignoreCase = true)) {
                val sourceFile = uri.path?.let(::File)
                if (sourceFile?.isFile == true && sourceFile.canRead()) return sourceFile
            }

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
            // ⚡ URI-hash cache — same source never re-copies
            val uriHash = uri.toString().hashCode().toUInt().toString(16)
            val realFileName = "${baseName}_${uriHash}.$ext"
            val file = File(context.cacheDir, realFileName)

            if (file.exists() && file.length() > 0) return file
            val tempFile =
                File(context.cacheDir, "$realFileName.${java.util.UUID.randomUUID()}.part")
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw java.io.IOException("Could not open media URI")
                input.use {
                    tempFile.outputStream().use { output ->
                        it.copyTo(output, bufferSize = 256 * 1024)
                    }
                }
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
package com.moody.moodyvideoeditor.utils

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.data.ColorFilterValues
import com.moody.moodyvideoeditor.data.ColorWheelState
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.KeyframeLibrary
import com.moody.moodyvideoeditor.data.MaskType
import com.moody.moodyvideoeditor.data.MotionConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

private data class LayeredRenderItem(
    val trackIndex: Int,
    val timelineStartMs: Long,
    val kind: Int,
    val matteClip: EditorClip? = null,
    val visualClip: EditorClip? = null,
    val adjustmentClip: EditorClip? = null,
    val sequence: TextOverlaySequence? = null,
    val matteIdx: Int = -1,
    val clipIdx: Int = -1,
    val sequenceIdx: Int = -1,
    val inputIndex: Int = -1
)

private data class LayerShadowFilterOutputs(
    val combinedLayer: String,
    val shadowOnly: String,
    val padding: Int
)

class FFmpegExecutor(
    private val context: Context,
    private val onProgress: (Float) -> Unit,
    private val onSuccess: (File) -> Unit,
    private val onError: (String) -> Unit
) {
    @Volatile
    private var isCancelled = false
    private var currentSession: FFmpegSession? = null
    private val maskSequenceDirectories = mutableListOf<File>()

    private fun renderMatteToPng(
        style: com.moody.moodyvideoeditor.data.ColorMatteStyle,
        W: Int,
        H: Int
    ): File {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        when (style.mode) {
            com.moody.moodyvideoeditor.data.ColorMatteMode.SOLID -> {
                canvas.drawColor(style.solidColor.toInt())
            }

            com.moody.moodyvideoeditor.data.ColorMatteMode.RAMP -> {
                val rad = Math.toRadians(style.rampAngleDeg.toDouble())
                val dx = kotlin.math.cos(rad).toFloat()
                val dy = kotlin.math.sin(rad).toFloat()
                val halfDiag = kotlin.math.sqrt(W.toFloat() * W + H.toFloat() * H) / 2f
                val cx = W / 2f
                val cy = H / 2f
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    isDither = true
                    shader = android.graphics.LinearGradient(
                        cx - dx * halfDiag, cy - dy * halfDiag,
                        cx + dx * halfDiag, cy + dy * halfDiag,
                        style.rampColor1.toInt(),
                        style.rampColor2.toInt(),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
            }

            com.moody.moodyvideoeditor.data.ColorMatteMode.FOUR_COLOR -> {
                val tiny = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                tiny.setPixel(0, 0, style.topLeft.toInt() or 0xFF000000.toInt())
                tiny.setPixel(1, 0, style.topRight.toInt() or 0xFF000000.toInt())
                tiny.setPixel(0, 1, style.bottomLeft.toInt() or 0xFF000000.toInt())
                tiny.setPixel(1, 1, style.bottomRight.toInt() or 0xFF000000.toInt())

                val paint = android.graphics.Paint().apply {
                    isFilterBitmap = true
                    isAntiAlias = true
                    isDither = true
                }
                canvas.drawBitmap(
                    tiny,
                    null,
                    android.graphics.RectF(0f, 0f, W.toFloat(), H.toFloat()),
                    paint
                )
                tiny.recycle()
            }
        }

        val file =
            File(context.cacheDir, "matte_${style.mode.name}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bmp.recycle()
        return file
    }

    private fun buildFastVideoArgs(
        bitrateKbps: Int,
        targetW: Int,
        targetH: Int,
        encoderThreads: Int = 0
    ): List<String> {
        return listOf(
            "-c:v", "libx264",
            "-preset", "ultrafast",
            "-tune", "fastdecode",
            "-b:v", "${bitrateKbps}k",
            "-s:v", "${targetW}x${targetH}",
            "-aspect", "$targetW:$targetH",
            "-pix_fmt", "yuv420p",
            "-color_range", "tv",
            "-colorspace", "bt709",
            "-color_primaries", "bt709",
            "-color_trc", "bt709",
            "-threads", encoderThreads.toString()
        )
    }

    private fun recommendedFfmpegThreads(targetW: Int, targetH: Int): Int {
        val cpuLimit = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val pixelCount = targetW.toLong() * targetH
        val resolutionLimit = when {
            pixelCount >= 3840L * 2160L -> 2
            pixelCount >= 2560L * 1440L -> 3
            else -> 6
        }
        val memoryClassMb = context.getSystemService(ActivityManager::class.java)?.memoryClass ?: 256
        val memoryLimit = when {
            memoryClassMb <= 192 -> 1
            memoryClassMb <= 256 -> 2
            memoryClassMb <= 384 -> 4
            else -> 6
        }
        return minOf(cpuLimit, resolutionLimit, memoryLimit).coerceAtLeast(1)
    }

    suspend fun export(
        clips: List<EditorClip>,
        allClips: List<EditorClip>,
        outputFile: File,
        targetW: Int = 1280,
        targetH: Int = 720,
        fps: Int = 30,
        bitrateKbps: Int = 8000,
        textSequences: List<TextOverlaySequence> = emptyList(),
        audioOnlyClips: List<EditorClip> = emptyList(),
        matteClips: List<EditorClip> = emptyList(),
        explicitDurationMs: Long = 0L
    ) {
        isCancelled = false
        try {
            if (clips.isEmpty() && audioOnlyClips.isEmpty() && matteClips.isEmpty()) {
                onError("No clips to export")
                return
            }

            val computedTotal = (clips.maxOfOrNull { it.timelineEndMs } ?: 0L)
                .coerceAtLeast(audioOnlyClips.maxOfOrNull { it.timelineEndMs } ?: 0L)
                .coerceAtLeast(matteClips.maxOfOrNull { it.timelineEndMs } ?: 0L)
            val totalDurationMs = if (explicitDurationMs > 0L) explicitDurationMs else computedTotal

            val (localFiles, audioLocalFiles) = withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                val videoFiles = mutableListOf<File>()
                for (clip in clips) {
                    currentCoroutineContext().ensureActive()
                    if (isCancelled) throw CancellationException("Export cancelled")
                    val file = copyUriToCache(clip.uri, "clip_${clip.id}.mp4")
                        ?: throw IllegalStateException("Could not read file: ${clip.name}")
                    videoFiles.add(file)
                }
                val audioFiles = mutableListOf<File>()
                for (clip in audioOnlyClips) {
                    currentCoroutineContext().ensureActive()
                    if (isCancelled) throw CancellationException("Export cancelled")
                    val file = copyUriToCache(clip.uri, "audio_${clip.id}.mp3")
                        ?: throw IllegalStateException("Could not read audio: ${clip.name}")
                    audioFiles.add(file)
                }
                videoFiles to audioFiles
            }

            val maskSequences = renderMaskSequences(clips + matteClips, targetW, targetH, fps)

            val lightningClips =
                allClips.filter { c -> c.isEffectClip && c.effectState?.lightning != null && c.timelineEndMs > 0L && c.timelineStartMs < totalDurationMs }
            val lightningSequences = if (lightningClips.isNotEmpty()) {
                try {
                    LightningBitmapRenderer.renderLightningStrikes(
                        context, lightningClips, targetW, targetH, fps, totalDurationMs,
                        onProgress = { p -> onProgress(p * 0.15f) },
                        shouldCancel = { isCancelled }
                    )
                } catch (e: Exception) {
                    emptyList()
                }
            } else emptyList()

            val particleClips =
                allClips.filter { c -> c.isEffectClip && c.effectState?.particle != null && c.timelineEndMs > 0L && c.timelineStartMs < totalDurationMs }
            val particleSequences = if (particleClips.isNotEmpty()) {
                try {
                    ParticleBitmapRenderer.renderParticleSequences(
                        context, particleClips, targetW, targetH, fps, totalDurationMs,
                        onProgress = { p -> onProgress(p * 0.15f) },
                        shouldCancel = { isCancelled }
                    )
                } catch (e: Exception) {
                    emptyList()
                }
            } else emptyList()

            val fireClips =
                allClips.filter { c -> c.isEffectClip && c.effectState?.fire != null && c.timelineEndMs > 0L && c.timelineStartMs < totalDurationMs }
            val fireSequences = if (fireClips.isNotEmpty()) {
                try {
                    FireBitmapRenderer.renderFireSequences(
                        context, fireClips, targetW, targetH, fps, totalDurationMs,
                        onProgress = { p -> onProgress(p * 0.15f) },
                        shouldCancel = { isCancelled }
                    )
                } catch (e: Exception) {
                    emptyList()
                }
            } else emptyList()

            val dustClips =
                allClips.filter { c -> c.isEffectClip && c.effectState?.dust != null && c.timelineEndMs > 0L && c.timelineStartMs < totalDurationMs }
            val dustSequences = if (dustClips.isNotEmpty()) {
                try {
                    DustBitmapRenderer.renderDustSequences(
                        context, dustClips, targetW, targetH, fps, totalDurationMs,
                        onProgress = { p -> onProgress(p * 0.15f) },
                        shouldCancel = { isCancelled }
                    )
                } catch (e: Exception) {
                    emptyList()
                }
            } else emptyList()

            val cropClips = clips.filter { c ->
                c.isVisualClip && com.moody.moodyvideoeditor.utils.CropHelper.isCropped(c)
            }
            val cropAlphaSequences = if (cropClips.isNotEmpty()) {
                try {
                    com.moody.moodyvideoeditor.utils.CropAlphaRenderer.render(
                        context = context,
                        clips = cropClips,
                        canvasW = targetW,
                        canvasH = targetH,
                        fps = fps,
                        totalMs = totalDurationMs,
                        onProgress = { p -> onProgress(0.10f + p * 0.10f) },
                        shouldCancel = { isCancelled }
                    )
                } catch (e: Exception) {
                    Log.e("FFMPEG", "Crop alpha render failed", e)
                    emptyList()
                }
            } else emptyList()

            val allExtraSequences =
                lightningSequences + particleSequences + fireSequences + dustSequences
            val adjustmentClips = allClips.filter { it.isAdjustmentClip }
            val needsLayeredCrop = cropAlphaSequences.isNotEmpty()
            val hasPositionedVisualClip = clips.any {
                it.isVisualClip &&
                        (kotlin.math.abs(it.offsetX) > 0.001f ||
                                kotlin.math.abs(it.offsetY) > 0.001f)
            }

            val needsLayered = needsLayeredCrop || hasPositionedVisualClip ||
                    clips.map { it.trackIndex }
                        .distinct().size > 1 || maskSequences.isNotEmpty() || matteClips.isNotEmpty() ||
                    allExtraSequences.isNotEmpty() || adjustmentClips.isNotEmpty() ||
                    clips.any { TransformApplier.hasAnimatedTransform(it) } ||
                    clips.any { it.layerShadow.enabled }

            val orderedTransitionClips = clips.sortedBy { it.timelineStartMs }
            val canExportTransitionsWithMattes =
                matteClips.isNotEmpty() &&
                        !needsLayeredCrop &&
                        !hasPositionedVisualClip &&
                        clips.map { it.trackIndex }.distinct().size == 1 &&
                        maskSequences.all { sequence ->
                            matteClips.any { it.id == sequence.clipId }
                        } &&
                        allExtraSequences.isEmpty() &&
                        adjustmentClips.isEmpty() &&
                        clips.none { TransformApplier.hasAnimatedTransform(it) } &&
                        clips.none { it.layerShadow.enabled } &&
                        orderedTransitionClips.size >= 2 &&
                        orderedTransitionClips.all {
                            kotlin.math.abs(it.speed - 1.0f) < 0.01f
                        } &&
                        orderedTransitionClips.zipWithNext().all { (a, b) ->
                            kotlin.math.abs(b.timelineStartMs - a.timelineEndMs) < 100L
                        } &&
                        orderedTransitionClips.drop(1).any {
                            it.transition?.isActive == true
                        } &&
                        matteClips.all {
                            it.trackIndex > orderedTransitionClips.first().trackIndex
                        }

            if (canExportTransitionsWithMattes) {
                exportMultipleClips(
                    clips,
                    allClips,
                    localFiles,
                    audioOnlyClips,
                    audioLocalFiles,
                    outputFile,
                    targetW,
                    targetH,
                    fps,
                    bitrateKbps,
                    textSequences,
                    totalDurationMs,
                    matteClips,
                    maskSequences
                )
                return
            }

            if (needsLayered) {
                exportLayeredTracks(
                    clips, allClips, localFiles, audioOnlyClips, audioLocalFiles,
                    outputFile, targetW, targetH, fps, bitrateKbps,
                    textSequences, maskSequences, matteClips, totalDurationMs, allExtraSequences,
                    cropAlphaSequences
                )
                return
            }

            when {
                localFiles.size == 1 && audioLocalFiles.isEmpty() -> {
                    exportSingleClip(
                        clips[0],
                        allClips,
                        localFiles[0],
                        outputFile,
                        targetW,
                        targetH,
                        fps,
                        bitrateKbps,
                        textSequences,
                        totalDurationMs
                    )
                }

                localFiles.size == 1 && audioLocalFiles.isNotEmpty() -> {
                    exportSingleClipWithAudio(
                        clips[0],
                        allClips,
                        localFiles[0],
                        audioOnlyClips,
                        audioLocalFiles,
                        outputFile,
                        targetW,
                        targetH,
                        fps,
                        bitrateKbps,
                        textSequences,
                        totalDurationMs
                    )
                }

                localFiles.size >= 2 -> {
                    exportMultipleClips(
                        clips,
                        allClips,
                        localFiles,
                        audioOnlyClips,
                        audioLocalFiles,
                        outputFile,
                        targetW,
                        targetH,
                        fps,
                        bitrateKbps,
                        textSequences,
                        totalDurationMs
                    )
                }

                else -> onError("No visual clips")
            }
        } catch (e: CancellationException) {
            cleanupMaskSequences()
            throw e
        } catch (e: Exception) {
            cleanupMaskSequences()
            Log.e("FFMPEG", "Export crash", e)
            onError("Export failed: ${e.message}")
        }
    }

    private data class MaskFrameSequence(
        val clipId: String,
        val pattern: String,
        val staticFile: File?,
        val fps: Int,
        val directory: File
    )

    private suspend fun renderMaskSequences(
        clips: List<EditorClip>,
        width: Int,
        height: Int,
        fps: Int
    ): List<MaskFrameSequence> = withContext(Dispatchers.IO) {
        val maskedClips = clips.filter { clip ->
            val mask = clip.mask
            mask.isActive && (mask.type != MaskType.CUSTOM || (mask.customPoints.size >= 3 && mask.customClosed))
        }

        if (maskedClips.isEmpty()) return@withContext emptyList()

        val output = mutableListOf<MaskFrameSequence>()
        try {
            maskedClips.forEach { clip ->
                currentCoroutineContext().ensureActive()
                val directory = File(context.cacheDir, "mask_${clip.id}_${System.nanoTime()}")
                if (!directory.mkdirs()) throw java.io.IOException("Could not create mask render directory")
                maskSequenceDirectories.add(directory)

                val hasAnimation = clip.mask.hasKeyframes
                val frameCount = if (hasAnimation) {
                    ((clip.durationMs.toDouble() * fps) / 1000.0).toLong()
                        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
                } else 1

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap)
                    repeat(frameCount) { frameIndex ->
                        currentCoroutineContext().ensureActive()
                        canvas.drawColor(Color.WHITE, android.graphics.PorterDuff.Mode.SRC)
                        val timeSec = if (hasAnimation) frameIndex.toFloat() / fps else 0f
                        MaskEngine.drawMask(
                            canvas = canvas,
                            state = MaskEngine.sampleAt(clip.mask, timeSec),
                            viewWidth = width.toFloat(),
                            viewHeight = height.toFloat()
                        )
                        val frameFile =
                            File(directory, "mask_%05d.png".format(Locale.US, frameIndex + 1))
                        FileOutputStream(frameFile).use { stream ->
                            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                                throw java.io.IOException("Could not encode mask frame")
                            }
                        }
                    }
                } finally {
                    bitmap.recycle()
                }

                val staticFile = if (hasAnimation) null else File(directory, "mask_00001.png")
                output += MaskFrameSequence(
                    clip.id,
                    File(directory, "mask_%05d.png").absolutePath,
                    staticFile,
                    fps,
                    directory
                )
            }
            output
        } catch (error: Throwable) {
            cleanupMaskSequences()
            throw error
        }
    }

    private fun cleanupMaskSequences() {
        synchronized(maskSequenceDirectories) {
            maskSequenceDirectories.toList().forEach { directory ->
                if (directory.exists() && !directory.deleteRecursively()) {
                    Log.w("FFMPEG", "Could not remove mask sequence ${directory.name}")
                }
            }
            maskSequenceDirectories.clear()
        }
    }

    suspend fun exportAudioOnly(
        allClips: List<EditorClip>,
        outputFile: File,
        durationMs: Long,
        audioFormat: String = "mp3",
        bitrateKbps: Int = 192,
        rangeStartMs: Long = 0L,
        rangeEndMs: Long = 0L
    ) {
        isCancelled = false
        try {
            val audioClips = allClips.filter {
                it.isAudio && !it.isAudioEffectClip && it.uri.toString()
                    .isNotBlank() && it.uri != Uri.EMPTY
            }
            if (audioClips.isEmpty()) {
                onError("No audio clips found in timeline")
                return
            }

            val effectiveStart = rangeStartMs.coerceAtLeast(0L)
            val effectiveEnd = if (rangeEndMs > effectiveStart) rangeEndMs else durationMs
            val effectiveDurMs = (effectiveEnd - effectiveStart).coerceAtLeast(500L)

            val audioLocalFiles = withContext(Dispatchers.IO) {
                val files = mutableListOf<File>()
                for (clip in audioClips) {
                    currentCoroutineContext().ensureActive()
                    if (isCancelled) throw CancellationException("Export cancelled")
                    val file = copyUriToCache(clip.uri, "audio_${clip.id}.mp3")
                        ?: throw IllegalStateException("Could not read: ${clip.name}")
                    files.add(file)
                }
                files
            }

            val args = mutableListOf<String>()
            args.add("-y"); args.add("-f"); args.add("lavfi"); args.add("-t"); args.add((effectiveDurMs / 1000.0).toString())
            args.add("-i"); args.add("anullsrc=r=44100:cl=stereo")
            audioLocalFiles.forEach { f -> args.add("-i"); args.add(f.absolutePath) }

            val filterParts = mutableListOf<String>()
            val audioLabels = mutableListOf<String>()
            filterParts.add("[0:a]aresample=44100[basea]")
            audioLabels.add("[basea]")

            audioClips.forEachIndexed { idx, clip ->
                val inputIdx = idx + 1
                val clipStart = clip.timelineStartMs
                val clipEnd = clip.timelineEndMs
                if (clipEnd <= effectiveStart || clipStart >= effectiveEnd) return@forEachIndexed

                val leftCut = (effectiveStart - clipStart).coerceAtLeast(0L)
                val rightCut = (clipEnd - effectiveEnd).coerceAtLeast(0L)
                val speed = clip.speed.coerceAtLeast(0.01f)
                val srcStartMs = clip.sourceStartMs + (leftCut * speed).toLong()
                val srcEndMs =
                    (clip.sourceEndMs - (rightCut * speed).toLong()).coerceAtLeast(srcStartMs + 100L)
                val durSec = ((srcEndMs - srcStartMs) / 1000.0).coerceAtLeast(0.1)
                val delayMs = ((clipStart - effectiveStart).coerceAtLeast(0L)).toInt()

                val chain = mutableListOf<String>()
                chain.add("atrim=start=${srcStartMs / 1000.0}:duration=$durSec")
                chain.add("asetpts=PTS-STARTPTS")
                if (delayMs > 0) chain.add("adelay=$delayMs|$delayMs")
                if (speed != 1.0f) chain.add("atempo=${speed.coerceIn(0.5f, 2.0f)}")
                if (clip.volume != 1.0f) chain.add("volume=${clip.volume}")
                if (clip.audioFx != "none") {
                    val fxFilter = AudioEngine.buildAudioFilter(clip.audioFx, clip.audioFxIntensity)
                    if (fxFilter.isNotBlank()) chain.add(fxFilter)
                }
                chain.add("aresample=44100")

                val label = "a$idx"
                filterParts.add("[$inputIdx:a]${chain.joinToString(",")}[$label]")
                audioLabels.add("[$label]")
            }

            if (audioLabels.size == 1) {
                onError("No audio in selected range")
                return
            }

            filterParts.add("${audioLabels.joinToString("")}amix=inputs=${audioLabels.size}:duration=longest:dropout_transition=0[outa]")
            val finalAudioLabel = AudioEngine.appendTimelineAudioEffects(
                filterParts,
                allClips,
                effectiveStart,
                "outa",
                "finala"
            )
            val codecArgs = if (audioFormat == "m4a") listOf(
                "-c:a",
                "aac",
                "-b:a",
                "${bitrateKbps}k"
            ) else listOf("-c:a", "libmp3lame", "-b:a", "${bitrateKbps}k")

            args.add("-filter_complex"); args.add(filterParts.joinToString(";")); args.add("-map"); args.add(
                "[$finalAudioLabel]"
            )
            args.addAll(codecArgs); args.add("-ar"); args.add("44100"); args.add("-ac"); args.add("2")
            args.add("-t"); args.add((effectiveDurMs / 1000.0).toString()); args.add("-threads"); args.add(
                "0"
            ); args.add(outputFile.absolutePath)

            val session = FFmpegKit.executeWithArgumentsAsync(
                args.toTypedArray(),
                { s ->
                    if (isCancelled) onError("Audio export cancelled")
                    else if (ReturnCode.isSuccess(s.returnCode)) {
                        if (outputFile.exists() && outputFile.length() > 0) onSuccess(outputFile) else onError(
                            "Audio output empty"
                        )
                    } else onError(
                        "Audio export failed:\n${
                            (s.allLogsAsString ?: "Unknown").takeLast(
                                1200
                            )
                        }"
                    )
                },
                { _ -> },
                { stats ->
                    if (!isCancelled) {
                        try {
                            val t = stats.time
                            if (t > 0) onProgress(
                                ((t / (effectiveDurMs.toDouble() * 1.2)).coerceIn(
                                    0.0,
                                    0.95
                                )).toFloat()
                            )
                        } catch (_: Exception) {
                        }
                    }
                }
            )
            currentSession = session
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError("Audio export crash: ${e.message}")
        }
    }

    suspend fun exportImageSequence(
        allClips: List<EditorClip>,
        outputDir: File,
        targetW: Int,
        targetH: Int,
        fps: Int,
        durationMs: Long,
        imageFormat: String = "png",
        jpegQuality: Int = 90,
        rangeStartMs: Long = 0L,
        rangeEndMs: Long = 0L,
        onFrameProgress: (Int, Int) -> Unit = { _, _ -> }
    ) {
        isCancelled = false
        var generatedSequences = emptyList<TextOverlaySequence>()
        try {
            if (!outputDir.exists()) outputDir.mkdirs()
            val effectiveStart = rangeStartMs.coerceAtLeast(0L)
            val effectiveEnd = if (rangeEndMs > effectiveStart) rangeEndMs else durationMs
            val effectiveDurMs = (effectiveEnd - effectiveStart).coerceAtLeast(500L)
            val totalFrames = ((effectiveDurMs.toDouble() * fps / 1000.0).toInt()).coerceAtLeast(1)
            val ext = if (imageFormat == "jpeg") "jpg" else "png"
            val pattern = File(outputDir, "MoodyExport_frame_%05d.$ext").absolutePath
            val trimmedAllClips = trimClipsToRange(allClips, effectiveStart, effectiveEnd)

            val textClips = trimmedAllClips.filter { it.isTextClip }
            val overlayImageClips =
                trimmedAllClips.filter { it.isVisualClip && it.type.startsWith("image/") && it.trackIndex > 0 }
            val overlayClips =
                trimmedAllClips.filter { it.isOverlayClip || (it.isEffectClip && it.effectState?.overlay != null) }
            val vizClips = trimmedAllClips.filter { it.isVisualizerClip }
            val spotlightClips = trimmedAllClips.filter { it.isSpotlightClip }

            val textSequences =
                if (textClips.isNotEmpty() || overlayImageClips.isNotEmpty() ||
                    overlayClips.isNotEmpty()
                ) {
                    TextBitmapRenderer.renderOverlaysByTrack(
                        context = context,
                        textClips = textClips,
                        imageClips = overlayImageClips,
                        overlayClips = overlayClips,
                        W = targetW,
                        H = targetH,
                        fps = fps,
                        totalDurationMs = effectiveDurMs,
                        onProgress = { p -> onProgress(p * 0.25f) },
                        shouldCancel = { isCancelled },
                        imageFormat = imageFormat,
                        jpegQuality = jpegQuality
                    )
                } else emptyList()
            generatedSequences = textSequences

            val vizSequences = if (vizClips.isNotEmpty()) {
                VisualizerBitmapRenderer.renderCombinedOverlays(
                    context,
                    vizClips,
                    allClips,
                    effectiveStart,
                    targetW,
                    targetH,
                    fps,
                    effectiveDurMs,
                    onProgress = { p -> onProgress(0.25f + p * 0.25f) },
                    shouldCancel = { isCancelled },
                    imageFormat = imageFormat,
                    jpegQuality = jpegQuality
                )
            } else emptyList()
            generatedSequences = textSequences + vizSequences

            val spotlightSequences = if (spotlightClips.isNotEmpty()) {
                SpotlightBitmapRenderer.render(
                    context = context,
                    clips = spotlightClips,
                    width = targetW,
                    height = targetH,
                    fps = fps,
                    totalDurationMs = effectiveDurMs,
                    onProgress = { p -> onProgress(0.50f + p * 0.10f) },
                    shouldCancel = { isCancelled }
                )
            } else emptyList()

            val allSequences =
                (textSequences + vizSequences + spotlightSequences).sortedBy { it.trackIndex }
            generatedSequences = allSequences
            val baseVisualClips = trimmedAllClips.filter {
                it.isVisualClip && it.uri.toString()
                    .isNotBlank() && it.uri != Uri.EMPTY && it.trackIndex == 0
            }

            if (baseVisualClips.isEmpty() && allSequences.isEmpty()) {
                onError("Nothing to export as sequence")
                return
            }

            val args = mutableListOf<String>()
            args.add("-y")
            val baseLocalFiles = withContext(Dispatchers.IO) {
                val files = mutableListOf<File>()
                for (clip in baseVisualClips) {
                    if (isCancelled) throw CancellationException("cancelled")
                    val file = copyUriToCache(clip.uri, "seq_${clip.id}.mp4")
                        ?: throw IllegalStateException("Could not read ${clip.name}")
                    files.add(file)
                }
                files
            }

            if (baseLocalFiles.isEmpty()) {
                args.add("-f"); args.add("lavfi"); args.add("-t"); args.add((effectiveDurMs / 1000.0).toString())
                args.add("-i"); args.add("color=c=black:s=${targetW}x${targetH}:r=$fps")
            } else {
                baseVisualClips.forEachIndexed { idx, clip ->
                    if (isImage(clip)) {
                        args.add("-loop"); args.add("1"); args.add("-framerate"); args.add(fps.toString()); args.add(
                            "-t"
                        ); args.add((clip.durationMs / 1000.0).toString())
                    } else {
                        val srcDurSec =
                            ((clip.sourceEndMs - clip.sourceStartMs) / 1000.0).coerceAtLeast(0.1)
                        if (clip.sourceStartMs > 0) {
                            args.add("-ss"); args.add((clip.sourceStartMs / 1000.0).toString())
                        }
                        args.add("-t"); args.add(srcDurSec.toString())
                    }
                    args.add("-i"); args.add(baseLocalFiles[idx].absolutePath)
                }
            }

            val seqStartIdx = baseLocalFiles.size.coerceAtLeast(1)
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
            val baseVf = mutableListOf<String>()
            if (baseLocalFiles.isEmpty()) {
                baseVf.add("setsar=1")
            } else if (baseVisualClips.size == 1) {
                val clip = baseVisualClips[0]
                if (clip.speed != 1.0f && !isImage(clip)) baseVf.add("setpts=${1.0f / clip.speed}*PTS")
                baseVf.addAll(buildPerClipFilterChain(clip, allClips, fps))
                baseVf.addAll(buildVisualTransformFilters(clip, targetW, targetH))
                flattenBaseLayerOpacity(clip)?.let(baseVf::add)
            } else {
                baseVisualClips.forEachIndexed { idx, clip ->
                    val vf = mutableListOf<String>()
                    if (isImage(clip)) vf.add("setpts=PTS-STARTPTS") else {
                        vf.add("trim=duration=${(clip.sourceEndMs - clip.sourceStartMs) / 1000.0}"); vf.add(
                            "setpts=PTS-STARTPTS"
                        )
                    }
                    if (clip.speed != 1.0f && !isImage(clip)) vf.add("setpts=${1.0f / clip.speed}*PTS")
                    vf.addAll(
                        buildVisualTransformFilters(
                            clip,
                            targetW,
                            targetH
                        )
                    ); vf.add("fps=$fps"); vf.add("format=yuv420p")
                    flattenBaseLayerOpacity(clip)?.let { flattenFilter ->
                        vf.add(vf.lastIndex, flattenFilter)
                    }
                    filterParts.add("[$idx:v]${vf.joinToString(",")}[v$idx]")
                }
                filterParts.add("${(0 until baseVisualClips.size).joinToString("") { "[v$it]" }}concat=n=${baseVisualClips.size}:v=1:a=0[basev]")
            }

            if (baseLocalFiles.isNotEmpty() && baseVisualClips.size == 1) filterParts.add(
                "[0:v]${
                    baseVf.joinToString(
                        ","
                    )
                }[basev]"
            )
            else if (baseLocalFiles.isEmpty()) filterParts.add("[0:v]setsar=1[basev]")

            var lastLabel = "basev"
            allSequences.forEachIndexed { idx, seq ->
                val inIdx = seqStartIdx + idx
                addSequenceSourceFilter(filterParts, inIdx, "seqsrc$idx", seq)
                val enable = "between(t,%.4f,%.4f)".format(seq.startSec, seq.endSec)
                if (seq.blendMode.equals("addition", ignoreCase = true)) {
                    filterParts.add("[$lastLabel]format=rgba[baseadd$idx]")
                    filterParts.add(
                        "[baseadd$idx][seqsrc$idx]blend=all_mode=addition:shortest=0:enable='$enable'[ov$idx]"
                    )
                } else {
                    filterParts.add(
                        "[$lastLabel][seqsrc$idx]overlay=0:0:enable='$enable'[ov$idx]"
                    )
                }
                lastLabel = "ov$idx"
            }
            filterParts.add("[$lastLabel]format=yuv420p[outv]")

            val finalOutv = WatermarkHelper.addWatermarkToFilter(
                context, args, filterParts, "outv", targetW, targetH, fps
            )
            args.add("-filter_complex"); args.add(filterParts.joinToString(";"))
            args.add("-map"); args.add("[$finalOutv]"); args.add("-frames:v"); args.add(totalFrames.toString())
            args.add("-r"); args.add(fps.toString()); args.add("-threads"); args.add("0"); args.add(
                pattern
            )

            val session = FFmpegKit.executeWithArgumentsAsync(
                args.toTypedArray(),
                { s ->
                    if (isCancelled) {
                        cleanupSequences(allSequences)
                        onError("Sequence export cancelled")
                    }
                    else if (ReturnCode.isSuccess(s.returnCode)) {
                        val files = outputDir.listFiles()
                            ?.filter { it.name.startsWith("MoodyExport_frame_") } ?: emptyList()
                        cleanupSequences(allSequences)
                        if (files.isNotEmpty()) onSuccess(outputDir) else onError("No images created")
                    } else {
                        cleanupSequences(allSequences)
                        onError("Sequence failed:\n${(s.allLogsAsString ?: "").takeLast(1200)}")
                    }
                },
                { _ -> },
                { stats ->
                    if (!isCancelled) {
                        try {
                            val t = stats.time
                            if (t > 0) onProgress(
                                0.5f + ((t / (effectiveDurMs.toDouble() * 1.2)).coerceIn(
                                    0.0,
                                    0.45
                                )).toFloat()
                            )
                        } catch (_: Exception) {
                        }
                    }
                }
            )
            currentSession = session
        } catch (e: CancellationException) {
            cleanupSequences(generatedSequences)
            throw e
        } catch (e: Exception) {
            cleanupSequences(generatedSequences)
            onError("Sequence export crash: ${e.message}")
        }
    }

    private fun isImage(clip: EditorClip): Boolean = clip.type.startsWith("image/")

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

    private fun createOutputFile(fileName: String, format: String): File {
        val dir = File(context.cacheDir, "MoodyExports")
        if (!dir.exists()) dir.mkdirs()
        val ext = when (format.lowercase()) {
            "mov" -> "mov"; "mp3" -> "mp3"; "m4a" -> "m4a"; "png" -> "png"; "jpg", "jpeg" -> "jpg"; else -> "mp4"
        }
        return File(dir, "$fileName.$ext")
    }

    private fun completeExport(
        file: File,
        customFolderUri: String?,
        sequences: List<TextOverlaySequence>
    ) {
        try {
            saveToGallery(file, customFolderUri)
            onSuccess(file)
        } catch (e: Exception) {
            onError("Could not save export: ${e.message}")
        } finally {
            cleanupSequences(sequences)
        }
    }

    private fun cleanupSequences(sequences: List<TextOverlaySequence>) {
        sequences.mapNotNull { it.workingDirectory }.distinct().forEach { directory ->
            if (directory.exists() && !directory.deleteRecursively()) Log.w(
                "EXPORT",
                "Could not remove: ${directory.name}"
            )
        }
    }

    private fun saveToGallery(sourceFile: File, customFolderUri: String? = null): Uri? {
        if (customFolderUri != null && customFolderUri.isNotBlank()) {
            val uri = saveToCustomFolder(sourceFile, customFolderUri)
            if (uri != null) return uri
        }
        return try {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, sourceFile.name)
                put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(
                    android.provider.MediaStore.Video.Media.RELATIVE_PATH,
                    "Movies/MoodyEditor"
                )
            }
            val uri = context.contentResolver.insert(
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: return null
            context.contentResolver.openOutputStream(uri)
                ?.use { out -> sourceFile.inputStream().use { it.copyTo(out) } }
            uri
        } catch (e: Exception) {
            Log.e("EXPORT_SAVE", "MediaStore save failed", e); null
        }
    }

    private fun saveToCustomFolder(sourceFile: File, folderUriString: String): Uri? {
        return try {
            val treeUri = Uri.parse(folderUriString)
            val parentDocId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri =
                android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
            val mimeType = when {
                sourceFile.name.endsWith(".mov") -> "video/quicktime"; sourceFile.name.endsWith(".mp4") -> "video/mp4"
                sourceFile.name.endsWith(".mp3") -> "audio/mpeg"; sourceFile.name.endsWith(".m4a") -> "audio/mp4"
                sourceFile.name.endsWith(".png") -> "image/png"; sourceFile.name.endsWith(".jpg") || sourceFile.name.endsWith(
                    ".jpeg"
                ) -> "image/jpeg"

                else -> "application/octet-stream"
            }
            val docUri = android.provider.DocumentsContract.createDocument(
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

    private fun copyUriToCache(uri: Uri, fileName: String): File? {
        return try {
            val mime = try {
                context.contentResolver.getType(uri) ?: ""
            } catch (_: Throwable) {
                ""
            }
            val ext = when {
                mime.startsWith("audio/mpeg") -> "mp3"; mime.startsWith("audio/wav") -> "wav"; mime.startsWith(
                    "audio/aac"
                ) -> "aac"

                mime.startsWith("audio/mp4") -> "m4a"; mime.startsWith("audio/ogg") -> "ogg"; mime.startsWith(
                    "video/quicktime"
                ) -> "mov"

                mime.startsWith("video/webm") -> "webm"; mime.startsWith("image/png") -> "png"; mime.startsWith(
                    "image/jpeg"
                ) -> "jpg"

                mime.startsWith("image/webp") -> "webp"; else -> "mp4"
            }
            val file = File(context.cacheDir, "${fileName.substringBeforeLast('.')}.$ext")
            if (file.exists() && file.length() > 0) return file

            val tempFile =
                File(context.cacheDir, "${file.name}.${java.util.UUID.randomUUID()}.part")
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw java.io.IOException("Could not open URI")
                input.use { tempFile.outputStream().use { output -> it.copyTo(output) } }
                if (file.exists() && !file.delete()) throw java.io.IOException("Could not replace file")
                if (!tempFile.renameTo(file)) throw java.io.IOException("Could not finalize file")
            } finally {
                tempFile.delete()
            }

            if (file.length() > 0) file else null
        } catch (e: Exception) {
            Log.e("FFMPEG", "Copy failed", e); null
        }
    }

    private fun addSequenceInputs(args: MutableList<String>, sequences: List<TextOverlaySequence>) {
        sequences.forEach { seq ->
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
    }

    private fun buildOverlayChain(
        filterParts: MutableList<String>,
        sequences: List<TextOverlaySequence>,
        seqStartIdx: Int,
        baseLabel: String
    ): String {
        var lastLabel = baseLabel
        sequences.forEachIndexed { idx, seq ->
            val inIdx = seqStartIdx + idx
            addSequenceSourceFilter(filterParts, inIdx, "seqsrc$idx", seq)
            val enable = "between(t,%.4f,%.4f)".format(seq.startSec, seq.endSec)
            if (seq.blendMode.equals("addition", ignoreCase = true)) {
                filterParts.add("[$lastLabel]format=rgba[baseadd$idx]")
                filterParts.add(
                    "[baseadd$idx][seqsrc$idx]blend=all_mode=addition:shortest=0:enable='$enable'[ov$idx]"
                )
            } else {
                filterParts.add(
                    "[$lastLabel][seqsrc$idx]overlay=0:0:enable='$enable'[ov$idx]"
                )
            }
            lastLabel = "ov$idx"
        }
        filterParts.add("[$lastLabel]null[outv]")
        return "outv"
    }

    private fun addSequenceSourceFilter(
        filterParts: MutableList<String>,
        inputIndex: Int,
        outputLabel: String,
        sequence: TextOverlaySequence
    ) {
        val filters = mutableListOf("format=rgba")
        val opacityTrack =
            sequence.layerOpacityKeyframes[KeyframeLibrary.LAYER_OPACITY].orEmpty()
        if (sequence.layerOpacity < 100f || opacityTrack.isNotEmpty()) {
            val localTime = "(T-${String.format(Locale.US, "%.6f", sequence.startSec)})"
            val opacity = KeyframeStore.ffmpegExpression(
                sequence.layerOpacityKeyframes,
                KeyframeLibrary.LAYER_OPACITY,
                sequence.layerOpacity,
                localTime
            )
            val rgb = if (sequence.blendMode.equals("addition", ignoreCase = true)) {
                "r(X,Y)*alpha(X,Y)/255"
            } else {
                "r(X,Y)"
            }
            val green = if (sequence.blendMode.equals("addition", ignoreCase = true)) {
                "g(X,Y)*alpha(X,Y)/255"
            } else {
                "g(X,Y)"
            }
            val blue = if (sequence.blendMode.equals("addition", ignoreCase = true)) {
                "b(X,Y)*alpha(X,Y)/255"
            } else {
                "b(X,Y)"
            }
            val alpha = KeyframeStore.escapeFfmpegExpression(
                "alpha(X,Y)*clip(($opacity)/100,0,1)"
            )
            filters.add(
                "geq=r='$rgb':g='$green':b='$blue':a='$alpha'"
            )
        } else if (sequence.blendMode.equals("addition", ignoreCase = true)) {
            filters.add(
                "geq=r='r(X,Y)*alpha(X,Y)/255':g='g(X,Y)*alpha(X,Y)/255':" +
                        "b='b(X,Y)*alpha(X,Y)/255':a='alpha(X,Y)'"
            )
        }
        filterParts.add("[$inputIndex:v]${filters.joinToString(",")}[$outputLabel]")
    }

    private fun flattenBaseLayerOpacity(clip: EditorClip): String? {
        if (clip.layerOpacity >= 100f &&
            clip.keyframes[KeyframeLibrary.LAYER_OPACITY].isNullOrEmpty()
        ) return null
        return "format=rgba,geq=r='r(X,Y)*alpha(X,Y)/255':" +
                "g='g(X,Y)*alpha(X,Y)/255':b='b(X,Y)*alpha(X,Y)/255':a='255'"
    }

    private fun enforceOutputDimensions(
        args: MutableList<String>,
        filterParts: MutableList<String>,
        inputLabel: String,
        targetW: Int,
        targetH: Int,
        fps: Int
    ): String {
        filterParts.add("[$inputLabel]scale=$targetW:$targetH:flags=lanczos,setsar=1[exportv]")
        return WatermarkHelper.addWatermarkToFilter(
            context, args, filterParts, "exportv", targetW, targetH, fps
        )
    }

    /**
     * FIXED: Crop is offset-aware, no double-crop. Preview & export match.
     */
    private fun buildVisualTransformFilters(
        clip: EditorClip,
        targetW: Int,
        targetH: Int,
        transparentPadding: Boolean = false,
        positionByOverlay: Boolean = false
    ): List<String> {
        val filters = mutableListOf<String>()
        val keepTransparentPadding = transparentPadding
        if (keepTransparentPadding) filters.add("format=rgba")

        val scaleTrack = clip.keyframes["scale"].orEmpty()
        val hasAnimatedScale = scaleTrack
            .map { it.value }
            .distinctBy { (it * 1000f).toInt() }
            .size > 1
        val userScale = if (scaleTrack.isNotEmpty() && !hasAnimatedScale) {
            (scaleTrack.first().value / 100f).coerceIn(0.1f, 5f)
        } else {
            clip.scale.coerceIn(0.1f, 5f)
        }
        val hasScaleTrack = hasAnimatedScale
        val offsetXpx = (clip.offsetX * targetW).toInt()
        val offsetYpx = (clip.offsetY * targetH).toInt()
        val hasOffset = offsetXpx != 0 || offsetYpx != 0
        val fillColor = if (keepTransparentPadding) "black@0" else "black"
        val preserveOverscan =
            keepTransparentPadding && positionByOverlay && preservesLayerOverscan(clip)
        val overscanSize = if (preserveOverscan) {
            overscanCanvasSize(clip, targetW, targetH)
        } else {
            targetW to targetH
        }

        // ═══════════════════════════════════════════════════════════
        //  STEP 1: Fit source into canvas (letterbox) — WITHOUT user scale
        //  Because user scale must apply to ORIGINAL clip, not letterboxed
        // ═══════════════════════════════════════════════════════════
        if (hasScaleTrack) {
            // Keep the transformed image larger than the output canvas so
            // animated position can reveal parts that extend beyond its edges.
            filters.add("scale=$targetW:$targetH:force_original_aspect_ratio=decrease:eval=frame")
            val scaleExpr = KeyframeStore.ffmpegExpression(clip.keyframes, "scale", clip.scale * 100f)
            filters.add("scale=w='iw*($scaleExpr/100)':h='ih*($scaleExpr/100)':eval=frame")
            if (preserveOverscan) {
                filters.add(
                    "pad=${overscanSize.first}:${overscanSize.second}:" +
                            "x='(ow-iw)/2':y='(oh-ih)/2':color=$fillColor:eval=frame"
                )
            } else {
                val cropX = "max(0\\,min(iw-$targetW\\,(iw-$targetW)/2-$offsetXpx))"
                val cropY = "max(0\\,min(ih-$targetH\\,(ih-$targetH)/2-$offsetYpx))"
                filters.add(
                    "crop=w='min(iw,$targetW)':h='min(ih,$targetH)':" +
                            "x='$cropX':y='$cropY':eval=frame"
                )
                val padX = "max(0\\,min($targetW-iw\\,($targetW-iw)/2+$offsetXpx))"
                val padY = "max(0\\,min($targetH-ih\\,($targetH-ih)/2+$offsetYpx))"
                filters.add("pad=$targetW:$targetH:x='$padX':y='$padY':color=$fillColor:eval=frame")
            }
        } else if (kotlin.math.abs(userScale - 1.0f) > 0.01f) {
            // Static user scale
            if (userScale > 1.0f) {
                // ═══════════════════════════════════════════════════
                //  ZOOM-IN: Fit FIRST to canvas, then scale up, then crop shifted
                //  This ensures black bars stay outside the crop window
                // ═══════════════════════════════════════════════════
                filters.add("scale=$targetW:$targetH:force_original_aspect_ratio=decrease:eval=frame")
                filters.add("pad=$targetW:$targetH:(ow-iw)/2:(oh-ih)/2:color=$fillColor")
                val scaleFmt = String.format(Locale.US, "%.4f", userScale)
                filters.add("scale=iw*$scaleFmt:ih*$scaleFmt")
                if (preserveOverscan) {
                    filters.add(
                        "pad=${overscanSize.first}:${overscanSize.second}:" +
                                "x='(ow-iw)/2':y='(oh-ih)/2':color=$fillColor"
                    )
                } else {
                    // Shift crop window with the clip's static position.
                    val cropX = "max(0\\,min(iw-$targetW\\,(iw-$targetW)/2-$offsetXpx))"
                    val cropY = "max(0\\,min(ih-$targetH\\,(ih-$targetH)/2-$offsetYpx))"
                    filters.add("crop=$targetW:$targetH:$cropX:$cropY")
                }
            } else {
                // ═══════════════════════════════════════════════════
                //  ZOOM-OUT: Fit, scale down, pad to canvas (no crop)
                // ═══════════════════════════════════════════════════
                filters.add("scale=$targetW:$targetH:force_original_aspect_ratio=decrease:eval=frame")
                val scaleFmt = String.format(Locale.US, "%.4f", userScale)
                filters.add("scale=iw*$scaleFmt:ih*$scaleFmt")
                filters.add("pad=$targetW:$targetH:(ow-iw)/2:(oh-ih)/2:color=$fillColor")
            }
        } else {
            // ═══════════════════════════════════════════════════════════
            //  NO SCALE: Fit + pad
            //  If offset exists, we need to SHIFT the clip inside the canvas
            //  so user sees the shift (like preview)
            // ═══════════════════════════════════════════════════════════
            filters.add("scale=$targetW:$targetH:force_original_aspect_ratio=decrease:eval=frame")
            if (hasOffset) {
                // Pad with extra space to allow shifting without cutting
                // Extra = |offsetX| + |offsetY| max
                val extraX = kotlin.math.abs(offsetXpx).coerceAtLeast(0)
                val extraY = kotlin.math.abs(offsetYpx).coerceAtLeast(0)
                val padW = targetW + extraX * 2
                val padH = targetH + extraY * 2
                filters.add("pad=$padW:$padH:(ow-iw)/2:(oh-ih)/2:color=$fillColor")
                // Then crop target size, shifted by offset
                val cropX = "(iw-$targetW)/2+$offsetXpx"
                val cropY = "(ih-$targetH)/2+$offsetYpx"
                filters.add("crop=$targetW:$targetH:$cropX:$cropY")
            } else {
                filters.add("pad=$targetW:$targetH:(ow-iw)/2:(oh-ih)/2:color=$fillColor")
            }
        }

        // ═══════════════════════════════════════════════════════════
        //  ROTATION (unchanged)
        // ═══════════════════════════════════════════════════════════
        val hasRotationTrack = clip.keyframes["rotation"].orEmpty().isNotEmpty()
        if (hasRotationTrack) {
            val rotationExpr = KeyframeStore.ffmpegExpression(clip.keyframes, "rotation", clip.rotation)
            val angleExpr = "(($rotationExpr)*PI/180)"
            val outputSize = if (preserveOverscan) {
                val rotationCanvas = rotationCanvasSize(overscanSize.first, overscanSize.second)
                "ow=$rotationCanvas:oh=$rotationCanvas"
            } else {
                "ow=$targetW:oh=$targetH"
            }
            filters.add("rotate=angle='$angleExpr':c=$fillColor:$outputSize")
        } else if (kotlin.math.abs(clip.rotation) > 0.1f) {
            val angle = String.format(
                Locale.US,
                "%.4f",
                Math.toRadians(clip.rotation.toDouble())
            )
            val outputSize = if (preserveOverscan) {
                val rotationCanvas = rotationCanvasSize(overscanSize.first, overscanSize.second)
                "ow=$rotationCanvas:oh=$rotationCanvas"
            } else {
                "ow=$targetW:oh=$targetH"
            }
            filters.add("rotate=$angle:c=$fillColor:$outputSize")
        }

        val opacityTrack = clip.keyframes[KeyframeLibrary.LAYER_OPACITY].orEmpty()
        if (clip.layerOpacity < 100f || opacityTrack.isNotEmpty()) {
            filters.add("format=rgba")
            val opacity = KeyframeStore.ffmpegExpression(
                clip.keyframes,
                KeyframeLibrary.LAYER_OPACITY,
                clip.layerOpacity,
                "T"
            )
            val alphaExpression = KeyframeStore.escapeFfmpegExpression(
                "alpha(X,Y)*clip(($opacity)/100,0,1)"
            )
            filters.add(
                "geq=r='r(X,Y)':g='g(X,Y)':b='b(X,Y)':a='$alphaExpression'"
            )
        }
        if (keepTransparentPadding) filters.add("format=rgba")
        filters.add("setsar=1")
        return filters
    }

    private fun preservesLayerOverscan(clip: EditorClip): Boolean {
        val maxKeyframedScale = clip.keyframes["scale"].orEmpty()
            .maxOfOrNull { it.value / 100f } ?: clip.scale
        val hasOversizedScale = maxOf(clip.scale, maxKeyframedScale) > 1f
        val hasVisibleCrop = listOf("cropL", "cropR", "cropT", "cropB")
            .any { property ->
                clip.keyframes[property].orEmpty().any { kotlin.math.abs(it.value) > 0.0001f }
            }

        return hasOversizedScale &&
                !hasVisibleCrop &&
                !CropHelper.isCropped(clip) &&
                !clip.mask.isActive
    }

    private fun buildTransparentCropFilter(clip: EditorClip): String? {
        if (!CropHelper.isCropped(clip) &&
            listOf("cropL", "cropR", "cropT", "cropB").none {
                clip.keyframes[it].orEmpty().isNotEmpty()
            }
        ) return null

        fun cropExpression(property: String, value: Float): String =
            KeyframeStore.ffmpegExpression(clip.keyframes, property, value, "T")

        val left = cropExpression("cropL", clip.cropL)
        val right = cropExpression("cropR", clip.cropR)
        val top = cropExpression("cropT", clip.cropT)
        val bottom = cropExpression("cropB", clip.cropB)
        val alpha = KeyframeStore.escapeFfmpegExpression(
            "if(gte(X,clip($left,0,1)*W)*lt(X,(1-clip($right,0,1))*W)*" +
                    "gte(Y,clip($top,0,1)*H)*lt(Y,(1-clip($bottom,0,1))*H),alpha(X,Y),0)"
        )
        return "format=rgba,geq=r='r(X,Y)':g='g(X,Y)':b='b(X,Y)':a='$alpha'"
    }

    private fun overscanCanvasSize(
        clip: EditorClip,
        targetW: Int,
        targetH: Int
    ): Pair<Int, Int> {
        val maxKeyframedScale = clip.keyframes["scale"].orEmpty()
            .maxOfOrNull { it.value / 100f } ?: clip.scale
        val scale = maxOf(clip.scale, maxKeyframedScale).coerceIn(1f, 5f)

        fun evenDimension(value: Float): Int {
            val roundedUp = kotlin.math.ceil(value).toInt().coerceAtLeast(2)
            return if (roundedUp % 2 == 0) roundedUp else roundedUp + 1
        }

        return evenDimension(targetW * scale) to evenDimension(targetH * scale)
    }

    private fun rotationCanvasSize(width: Int, height: Int): Int {
        val diagonal = kotlin.math.ceil(kotlin.math.hypot(width.toDouble(), height.toDouble()))
            .toInt()
        return if (diagonal % 2 == 0) diagonal else diagonal + 1
    }

    private fun addLayerShadowFilters(
        filterParts: MutableList<String>,
        sourceLabel: String,
        state: com.moody.moodyvideoeditor.data.LayerShadowState,
        targetW: Int,
        clipIdx: Int
    ): LayerShadowFilterOutputs {
        val padding = LayerShadowRenderer.ffmpegPadding(state, targetW)
        val (offsetX, offsetY) = LayerShadowRenderer.ffmpegOffset(state, targetW)
        val blur = LayerShadowRenderer.ffmpegBlur(state, targetW)
        fun shiftedCoordinate(axis: String, offset: Float): String {
            val magnitude = LayerShadowRenderer.ffmpegNumber(kotlin.math.abs(offset))
            return if (offset >= 0f) "$axis-$magnitude" else "$axis+$magnitude"
        }
        val sampleX = shiftedCoordinate("X", offsetX)
        val sampleY = shiftedCoordinate("Y", offsetY)
        val blurValue = LayerShadowRenderer.ffmpegNumber(blur)
        val color = state.color.toInt()
        val opacity = String.format(Locale.US, "%.6f", Color.alpha(color) / 255f)
        val prefix = "layer_shadow_$clipIdx"
        val alphaInput = "${prefix}_alpha_input"
        val originalInput = "${prefix}_original_input"
        val blurredAlpha = "${prefix}_blurred"
        val shadow = "${prefix}_only"
        val original = "${prefix}_original"
        val combined = "${prefix}_combined"

        filterParts.add("[$sourceLabel]split=2[$alphaInput][$originalInput]")
        val alphaFilters = mutableListOf(
            "format=rgba",
            "pad=iw+${2 * padding}:ih+${2 * padding}:$padding:$padding:color=black@0",
            "alphaextract"
        )
        if (blur > 0.01f) {
            alphaFilters.add("gblur=sigma=$blurValue:steps=2")
        }
        alphaFilters.add("geq=lum='lum($sampleX,$sampleY)'")
        filterParts.add("[$alphaInput]${alphaFilters.joinToString(",")}[$blurredAlpha]")

        filterParts.add(
            "[$blurredAlpha]format=rgba," +
                    "geq=r='${Color.red(color)}':g='${Color.green(color)}':" +
                    "b='${Color.blue(color)}':a='r(X,Y)*$opacity'[$shadow]"
        )
        filterParts.add(
            "[$originalInput]format=rgba," +
                    "pad=iw+${2 * padding}:ih+${2 * padding}:$padding:$padding:color=black@0[$original]"
        )
        filterParts.add("[$shadow][$original]overlay=0:0:shortest=1:format=auto[$combined]")
        return LayerShadowFilterOutputs(combined, shadow, padding)
    }

    private fun buildAudioOnlyMix(
        filterParts: MutableList<String>,
        audioLocalFiles: List<File>,
        audioClips: List<EditorClip>,
        firstInputIdx: Int,
        baseAudioLabel: String,
        outLabel: String
    ): Boolean {
        if (audioLocalFiles.isEmpty()) return false
        val audioLabels = mutableListOf<String>()
        audioClips.forEachIndexed { idx, clip ->
            val inputIdx = firstInputIdx + idx
            val durSec = ((clip.sourceEndMs - clip.sourceStartMs) / 1000.0).coerceAtLeast(0.1)
            val delayMs = (clip.timelineStartMs / 1000.0 * 1000).toInt()
            val speed = clip.speed.coerceAtLeast(0.01f)

            val chain = mutableListOf(
                "atrim=start=${(clip.sourceStartMs / 1000.0)}:duration=$durSec",
                "asetpts=PTS-STARTPTS"
            )
            if (delayMs > 0) chain.add("adelay=$delayMs|$delayMs")
            if (speed != 1.0f) chain.add("atempo=${speed.coerceIn(0.5f, 2.0f)}")
            if (clip.volume != 1.0f) chain.add("volume=${clip.volume}")
            if (clip.audioFx != "none") {
                val fx = AudioEngine.buildAudioFilter(clip.audioFx, clip.audioFxIntensity)
                if (fx.isNotBlank()) chain.add(fx)
            }
            chain.add("aresample=44100")

            filterParts.add("[$inputIdx:a]${chain.joinToString(",")}[extra$idx]")
            audioLabels.add("[extra$idx]")
        }
        filterParts.add("[${baseAudioLabel}]${audioLabels.joinToString("")}amix=inputs=${audioLabels.size + 1}:duration=longest[$outLabel]")
        return true
    }

    private fun animatedColorToFfmpeg(cfg: com.moody.moodyvideoeditor.utils.AnimatedColorConfig): List<String> {
        val I = (cfg.intensity / 100f).coerceIn(0f, 2f);
        val sp = cfg.speed.coerceIn(0.2f, 5f);
        val f = Locale.US
        fun fmt(v: Float) = String.format(f, "%.4f", v)
        return when (cfg.type) {
            "hueCycle" -> listOf("hue=h=${fmt(60f * I * sp)}*t"); "hueFast" -> listOf(
                "hue=h=${
                    fmt(
                        200f * I * sp
                    )
                }*t"
            ); "hueReverse" -> listOf("hue=h=-${fmt(60f * I * sp)}*t")
            "sunsetShift" -> listOf("hue=h=${fmt(300f * sp)}*t")
            "rainbowFlow" -> listOf(
                "hue=h=${fmt(90f * sp)}*t",
                "eq=saturation=1+${fmt(0.4f * I)}*sin(t*${fmt(2f * sp)}):eval=frame"
            )

            "warmCool" -> listOf(
                "hue=h=${fmt(45f * I)}*sin(t*${fmt(1.5f * sp)})",
                "eq=saturation=1+${fmt(0.3f * I)}*abs(sin(t*${fmt(1.5f * sp)})):eval=frame"
            )

            "satPulse" -> listOf("eq=saturation=1+${fmt(0.6f * I)}*sin(t*${fmt(3f * sp)}):eval=frame"); "satFade" -> listOf(
                "eq=saturation=1-${fmt(0.7f * I)}*abs(sin(t*${fmt(2f * sp)})):eval=frame"
            )

            "brightPulse" -> listOf("eq=brightness=${fmt(0.30f * I)}*sin(t*${fmt(3f * sp)}):eval=frame"); "brightFlicker" -> listOf(
                "eq=brightness=${fmt(0.30f * I)}*sin(t*${fmt(20f * sp)}):eval=frame"
            )

            "chromaWave" -> listOf(
                "hue=h=${fmt(90f * I)}*sin(t*${fmt(2f * sp)})",
                "eq=brightness=${fmt(0.15f * I)}*cos(t*${fmt(3f * sp)}):eval=frame"
            )

            "cyberShift" -> listOf(
                "hue=h=${fmt(120f * I)}*sin(t*${fmt(1.5f * sp)})",
                "eq=saturation=1.5:eval=frame",
                "eq=contrast=1.2:eval=frame"
            )

            "oceanWave" -> listOf(
                "hue=h=${fmt(40f * I)}*sin(t*${fmt(0.8f * sp)})",
                "eq=saturation=1+${fmt(0.4f * I)}*sin(t*${fmt(0.8f * sp)}):eval=frame",
                "eq=brightness=${fmt(0.15f * I)}*sin(t*${fmt(0.8f * sp)}):eval=frame"
            )

            "icePulse" -> listOf(
                "hue=h=${fmt(40f * I)}*sin(t*${fmt(2f * sp)})",
                "eq=brightness=${fmt(0.20f * I)}*sin(t*${fmt(2f * sp)}):eval=frame",
                "eq=saturation=1+${fmt(0.5f * I)}*sin(t*${fmt(2f * sp)}):eval=frame"
            )

            "purpleHaze" -> listOf(
                "hue=h=${fmt(40f * I)}*sin(t*${fmt(0.7f * sp)})",
                "eq=saturation=1+${fmt(0.3f * I)}*sin(t*${fmt(0.7f * sp)}):eval=frame"
            )

            "toxicGreen" -> listOf(
                "hue=h=${fmt(30f * I)}*sin(t*${fmt(2.5f * sp)})",
                "eq=saturation=1+${fmt(0.4f * I)}*sin(t*${fmt(2.5f * sp)}):eval=frame",
                "eq=contrast=1.15:eval=frame"
            )

            "goldShimmer" -> listOf(
                "hue=h=${fmt(20f * I)}*sin(t*${fmt(3f * sp)})",
                "eq=brightness=${fmt(0.15f * I)}*sin(t*${fmt(3f * sp)}):eval=frame",
                "eq=saturation=1+${fmt(0.4f * I)}*sin(t*${fmt(3f * sp)}):eval=frame"
            )

            "neonPulse" -> listOf(
                "eq=saturation=1+${fmt(0.5f * I)}*sin(t*${fmt(4f * sp)}):eval=frame",
                "eq=brightness=${fmt(0.20f * I)}*sin(t*${fmt(4f * sp)}):eval=frame"
            )

            "fireFlicker" -> listOf(
                "hue=h=-${fmt(20f * I)}*sin(t*${fmt(10f * sp)})",
                "eq=brightness=${fmt(0.20f * I)}*sin(t*${fmt(15f * sp)}):eval=frame"
            )

            "heartbeatColor" -> listOf(
                "eq=saturation=1+${fmt(0.8f * I)}*abs(sin(t*${fmt(3f * sp)})):eval=frame",
                "eq=brightness=${fmt(0.15f * I)}*abs(sin(t*${fmt(3f * sp)})):eval=frame",
                "eq=contrast=1.1:eval=frame"
            )

            "bloodPulse" -> listOf(
                "eq=saturation=1+${fmt(0.6f * I)}*sin(t*${fmt(2f * sp)}):eval=frame",
                "eq=contrast=1+${fmt(0.2f * I)}*sin(t*${fmt(2f * sp)}):eval=frame"
            )

            "vividBreath" -> listOf(
                "eq=saturation=1+${fmt(0.8f * I)}*sin(t*${fmt(1.2f * sp)}):eval=frame",
                "eq=contrast=1+${fmt(0.2f * I)}*sin(t*${fmt(1.2f * sp)}):eval=frame"
            )

            "colorStrobe" -> listOf(
                "eq=saturation=1.4+${fmt(0.4f * I)}*sin(t*${fmt(24f * sp)}):eval=frame",
                "eq=brightness=${fmt(0.20f * I)}*sin(t*${fmt(24f * sp)}):eval=frame"
            )

            "invertFlash" -> listOf("hue=h=${fmt(180f * I)}*abs(sin(t*${fmt(2f * sp)}))"); "disco" -> listOf(
                "hue=h=${fmt(90f * sp)}*t",
                "eq=saturation=1.4:eval=frame"
            )

            "sepiaPulse" -> listOf(
                "hue=h=${fmt(20f * I)}*sin(t*${fmt(2f * sp)})",
                "eq=saturation=1-${fmt(0.30f * I)}*sin(t*${fmt(2f * sp)}):eval=frame"
            )

            else -> emptyList()
        }
    }

    private fun buildAnimatedBlurFilters(
        cfg: com.moody.moodyvideoeditor.utils.BlurConfig,
        start: Double,
        end: Double,
        name: String
    ): List<String> {
        val f = Locale.US
        fun fmt(v: Float) = String.format(f, "%.3f", v)
        val startStr = String.format(f, "%.4f", start);
        val endStr = String.format(f, "%.4f", end)
        if (cfg.type == "mosaic") return listOf(
            "pixelize=w=${
                cfg.blockSize.coerceIn(4f, 100f).toInt()
            }:h=${cfg.blockSize.coerceIn(4f, 100f).toInt()}:enable='between(t,$startStr,$endStr)'"
        )
        if (cfg.type !in listOf("blurIn", "blurOut", "halo")) return emptyList()

        val I = cfg.intensity.coerceIn(0f, 100f) / 100f;
        val maxR = (50f * I).coerceIn(0.5f, 50f)
        val sp = cfg.speed.coerceIn(0.2f, 5f);
        val animDur = (cfg.animDuration / sp).coerceAtLeast(0.1f)
        val effAnim = minOf(animDur.toDouble(), (end - start).coerceAtLeast(0.1)).coerceAtLeast(0.1)
        val filters = mutableListOf<String>()
        for (i in 0 until 12) {
            val tStart = start + i * (effAnim / 12)
            val tEnd =
                if (i == 11) (if (cfg.type == "blurOut") end else start + effAnim) else start + (i + 1) * (effAnim / 12)
            val localT = ((i + 0.5) * (effAnim / 12)).toFloat()
            val sigma = when (cfg.type) {
                "blurIn" -> maxR * (1f - (localT / animDur).coerceIn(
                    0f,
                    1f
                )); "blurOut" -> maxR * (localT / animDur).coerceIn(0f, 1f)
                "halo" -> maxR * 1.3f * (1f - (localT / animDur).coerceIn(0f, 1f)); else -> maxR
            }.coerceAtLeast(0.01f)
            filters.add(
                "gblur=sigma=${fmt(sigma)}:steps=1:enable='between(t,${
                    String.format(
                        f,
                        "%.4f",
                        tStart
                    )
                },${String.format(f, "%.4f", tEnd)})'"
            )
        }
        return filters
    }

    private fun exportLayeredTracks(
        clips: List<EditorClip>,
        allClips: List<EditorClip>,
        localFiles: List<File>,
        audioOnlyClips: List<EditorClip>,
        audioLocalFiles: List<File>,
        outputFile: File,
        targetW: Int,
        targetH: Int,
        fps: Int,
        bitrateKbps: Int,
        sequences: List<TextOverlaySequence>,
        maskSequences: List<MaskFrameSequence>,
        colorMettes: List<EditorClip>,
        totalDurationMs: Long,
        extraSequences: List<TextOverlaySequence>,
        cropAlphaSequences: List<com.moody.moodyvideoeditor.utils.CropAlphaSequence> = emptyList()
    ) {
        try {
            val durationSec = (totalDurationMs / 1000.0).coerceAtLeast(0.1)
            val orderedClips = clips.indices.map { clips[it] to localFiles[it] }
                .sortedWith(compareBy({ it.first.trackIndex }, { it.first.timelineStartMs }))
            val args = mutableListOf("-y")

            orderedClips.forEach { (clip, file) ->
                if (isImage(clip)) args.addAll(
                    listOf(
                        "-loop",
                        "1",
                        "-framerate",
                        fps.toString(),
                        "-t",
                        (clip.durationMs / 1000.0).toString()
                    )
                )
                else {
                    if (clip.sourceStartMs > 0L) args.addAll(
                        listOf(
                            "-ss",
                            (clip.sourceStartMs / 1000.0).toString()
                        )
                    )
                    args.addAll(
                        listOf(
                            "-t",
                            ((clip.sourceEndMs - clip.sourceStartMs) / 1000.0).coerceAtLeast(0.1)
                                .toString()
                        )
                    )
                }
                args.addAll(listOf("-i", file.absolutePath))
            }

            audioLocalFiles.forEach { file -> args.addAll(listOf("-i", file.absolutePath)) }
            val backgroundInputIdx = orderedClips.size + audioLocalFiles.size
            args.addAll(
                listOf(
                    "-f",
                    "lavfi",
                    "-t",
                    durationSec.toString(),
                    "-i",
                    "color=c=black:s=${targetW}x${targetH}:r=$fps"
                )
            )
            // ═══════════════════════════════════════════════════════════
            //  FIX: Input index tracking — ab sequence + order correct hai
            //  Order: video files → audio files → black bg → mattes → silence
            //         → sequences → cropAlpha → masks
            // ═══════════════════════════════════════════════════════════
            val matteInputIndices = mutableMapOf<String, Int>()
            var nextInputIdx = backgroundInputIdx + 1

            // 1. Matte inputs
            colorMettes.forEach { matte ->
                val pngFile = renderMatteToPng(matte.matteStyle, targetW, targetH)
                args.addAll(
                    listOf(
                        "-loop", "1",
                        "-framerate", fps.toString(),
                        "-t", (matte.durationMs / 1000.0).coerceAtLeast(0.1).toString(),
                        "-i", pngFile.absolutePath
                    )
                )
                matteInputIndices[matte.id] = nextInputIdx++
            }

            // 2. Silence input
            val silenceInputIdx = nextInputIdx++
            args.addAll(
                listOf(
                    "-f", "lavfi",
                    "-t", durationSec.toString(),
                    "-i", "anullsrc=r=44100:cl=stereo"
                )
            )

            // 3. Sequence inputs (text overlays)
            val combinedSequences = sequences + extraSequences
            val sequenceStartIdx = nextInputIdx
            combinedSequences.forEach { seq ->
                if (seq.startSec > 0.0001) {
                    args.add("-itsoffset")
                    args.add("%.4f".format(seq.startSec))
                }
                if (seq.isStaticImage) {
                    args.addAll(
                        listOf(
                            "-loop", "1",
                            "-framerate", seq.fps.toString(),
                            "-t", (seq.endSec - seq.startSec).coerceAtLeast(0.01).toString(),
                            "-i", seq.pattern
                        )
                    )
                } else {
                    args.addAll(
                        listOf(
                            "-framerate", seq.fps.toString(),
                            "-start_number", seq.startNumber.toString(),
                            "-i", seq.pattern
                        )
                    )
                }
                nextInputIdx++
            }

            // 4. Crop alpha inputs
            val cropAlphaInputIndices = mutableMapOf<String, Int>()
            cropAlphaSequences.forEach { seq ->
                if (seq.isStatic && seq.staticFile != null) {
                    args.addAll(
                        listOf(
                            "-loop", "1",
                            "-framerate", fps.toString(),
                            "-t", durationSec.toString(),
                            "-i", seq.staticFile.absolutePath
                        )
                    )
                } else {
                    args.addAll(
                        listOf(
                            "-framerate", seq.fps.toString(),
                            "-start_number", "1",
                            "-i", seq.pattern
                        )
                    )
                }
                cropAlphaInputIndices[seq.clipId] = nextInputIdx++
            }

            // 5. Mask inputs — YAHI BUG THA
            val maskInputIndices = mutableMapOf<String, Int>()
            maskSequences.forEach { seq ->
                if (seq.staticFile != null) {
                    args.addAll(
                        listOf(
                            "-loop", "1",
                            "-framerate", fps.toString(),
                            "-t", durationSec.toString(),
                            "-i", seq.staticFile.absolutePath
                        )
                    )
                } else {
                    args.addAll(
                        listOf(
                            "-framerate", seq.fps.toString(),
                            "-start_number", "1",
                            "-i", seq.pattern
                        )
                    )
                }
                maskInputIndices[seq.clipId] = nextInputIdx++
            }

            val filterParts = mutableListOf<String>()
            filterParts.add("[$backgroundInputIdx:v]setpts=PTS-STARTPTS,format=rgba[canvas0]")
            val renderList = mutableListOf<LayeredRenderItem>()

            colorMettes.forEachIndexed { idx, matte ->
                renderList.add(
                    LayeredRenderItem(
                        matte.trackIndex,
                        matte.timelineStartMs,
                        0,
                        matteClip = matte,
                        matteIdx = idx,
                        inputIndex = matteInputIndices[matte.id] ?: -1
                    )
                )
            }
            orderedClips.forEachIndexed { idx, (clip, _) ->
                renderList.add(
                    LayeredRenderItem(
                        clip.trackIndex,
                        clip.timelineStartMs,
                        1,
                        visualClip = clip,
                        clipIdx = idx,
                        inputIndex = idx
                    )
                )
            }
            combinedSequences.forEachIndexed { idx, seq ->
                renderList.add(
                    LayeredRenderItem(
                        seq.trackIndex,
                        (seq.startSec * 1000.0).toLong(),
                        2,
                        sequence = seq,
                        sequenceIdx = idx,
                        inputIndex = sequenceStartIdx + idx
                    )
                )
            }
            allClips.filter { it.isAdjustmentClip }.forEachIndexed { idx, adj ->
                renderList.add(
                    LayeredRenderItem(
                        adj.trackIndex,
                        adj.timelineStartMs,
                        3,
                        adjustmentClip = adj,
                        clipIdx = 10000 + idx,
                        inputIndex = -1
                    )
                )
            }

            renderList.sortWith(compareBy({ it.trackIndex }, { it.timelineStartMs }))
            var canvasLabel = "canvas0"

            renderList.forEach { item ->
                when (item.kind) {
                    0 -> {
                        val matte = item.matteClip ?: return@forEach
                        val startSec = matte.timelineStartMs / 1000.0;
                        val endSec = matte.timelineEndMs / 1000.0
                        val alpha = (matte.filters.opacity / 100f).coerceIn(0f, 1f)
                        val startExpr = String.format(Locale.US, "%.4f", startSec);
                        val endExpr = String.format(Locale.US, "%.4f", endSec)
                        val scaledLabel = "matte_scaled_${item.matteIdx}";
                        val outLabel = "canvas_m${item.matteIdx}"
                        val chain = mutableListOf(
                            "format=rgba",
                            "colorchannelmixer=aa=${String.format(Locale.US, "%.3f", alpha)}"
                        )
                        buildTransparentCropFilter(matte)?.let(chain::add)
                        chain.addAll(
                            buildVisualTransformFilters(
                                matte,
                                targetW,
                                targetH,
                                true,
                                true
                            )
                        )
                        chain.add("setpts=PTS-STARTPTS+${startSec}/TB")
                        filterParts.add("[${item.inputIndex}:v]${chain.joinToString(",")}[$scaledLabel]")

                        val maskIdx = maskInputIndices[matte.id]
                        val matteLayerLabel = if (maskIdx != null) {
                            filterParts.add("[$maskIdx:v]format=rgba,alphaextract,format=gray,setpts=PTS-STARTPTS+${startSec}/TB[matte_mask_pts${item.matteIdx}]")
                            filterParts.add("[$scaledLabel]format=rgba[matte_layer_rgba${item.matteIdx}]")
                            filterParts.add("[matte_layer_rgba${item.matteIdx}][matte_mask_pts${item.matteIdx}]alphamerge[matte_mask_ready${item.matteIdx}]")
                            "matte_mask_ready${item.matteIdx}"
                        } else {
                            scaledLabel
                        }

                        if (matte.blendMode.lowercase() == "normal" || matte.blendMode.isBlank()) filterParts.add(
                            "[$canvasLabel][$matteLayerLabel]overlay=0:0:eof_action=pass:repeatlast=0:enable='between(t,$startExpr,$endExpr)'[$outLabel]"
                        )
                        else {
                            val ffMode = ffmpegBlendName(matte.blendMode.lowercase())
                            val blendAlpha = String.format(
                                Locale.US,
                                "%.3f",
                                (matte.blendOpacity / 100f).coerceIn(0f, 1f)
                            )
                            filterParts.add("[$canvasLabel]format=rgba[matte_blend_base_${item.matteIdx}]")
                            filterParts.add("[$matteLayerLabel]format=rgba[matte_blend_layer_rgba_${item.matteIdx}]")
                            filterParts.add("[matte_blend_base_${item.matteIdx}][matte_blend_layer_rgba_${item.matteIdx}]blend=all_mode=$ffMode:all_opacity=$blendAlpha:shortest=0[matte_blend_out_${item.matteIdx}]")
                            filterParts.add("[matte_blend_base_${item.matteIdx}][matte_blend_out_${item.matteIdx}]overlay=0:0:eof_action=pass:repeatlast=0:enable='between(t,$startExpr,$endExpr)'[$outLabel]")
                        }
                        canvasLabel = outLabel
                    }

                    1 -> {
                        val clip = item.visualClip ?: return@forEach
                        val startSec = clip.timelineStartMs / 1000.0;
                        val endSec = (clip.timelineStartMs + clip.durationMs) / 1000.0
                        val startExpr = String.format(Locale.US, "%.4f", startSec);
                        val endExpr = String.format(Locale.US, "%.4f", endSec)
                        val clipDurationSec = (clip.durationMs / 1000.0).coerceAtLeast(0.1)
                        val blendKey = clip.blendMode.lowercase();
                        val isBlendActive = blendKey != "normal" && blendKey.isNotBlank()

                        val filters = mutableListOf("setpts=PTS-STARTPTS")
                        if (!isImage(clip) && clip.speed != 1.0f) filters.add("setpts=${1.0f / clip.speed}*PTS")
                        filters.addAll(buildPerClipFilterChain(clip, allClips, fps))
                        filters.addAll(
                            buildVisualTransformFilters(
                                clip,
                                targetW,
                                targetH,
                                true,
                                positionByOverlay = !isBlendActive
                            )
                        )
                        filters.add("fps=$fps"); filters.add("trim=duration=$clipDurationSec"); filters.add(
                            "setpts=PTS-STARTPTS+${startSec}/TB"
                        ); filters.add("format=rgba")

                        val unmasked = "layer_unmasked${item.clipIdx}"
                        filterParts.add("[${item.inputIndex}:v]${filters.joinToString(",")}[$unmasked]")

                        val maskIdx = maskInputIndices[clip.id]
                        var layerLabel = if (maskIdx != null) {
                            filterParts.add("[$maskIdx:v]format=rgba,alphaextract,format=gray,setpts=PTS-STARTPTS+${startSec}/TB[mask_pts${item.clipIdx}]")
                            filterParts.add("[$unmasked]format=rgba[layer_rgba${item.clipIdx}]")
                            filterParts.add("[layer_rgba${item.clipIdx}][mask_pts${item.clipIdx}]alphamerge[mask_ready${item.clipIdx}]")
                            "mask_ready${item.clipIdx}"
                        } else unmasked

                        val cropAlphaIdx = cropAlphaInputIndices[clip.id]
                        if (cropAlphaIdx != null) {
                            filterParts.add("[$cropAlphaIdx:v]format=gray,setpts=PTS-STARTPTS+${startSec}/TB[crop_pts${item.clipIdx}]")
                            filterParts.add("[$layerLabel]format=rgba[crop_layer_rgba${item.clipIdx}]")
                            filterParts.add("[crop_layer_rgba${item.clipIdx}][crop_pts${item.clipIdx}]alphamerge[crop_ready${item.clipIdx}]")
                            layerLabel = "crop_ready${item.clipIdx}"
                        }

                        val unshadowedLayerLabel = layerLabel
                        val shadowOutputs = if (clip.layerShadow.enabled) {
                            addLayerShadowFilters(
                                filterParts,
                                layerLabel,
                                clip.layerShadow,
                                targetW,
                                item.clipIdx
                            ).also { layerLabel = it.combinedLayer }
                        } else {
                            null
                        }

                        val outLabel = "canvas_c${item.clipIdx}"
                        if (!isBlendActive) {
                            val hasXKf = clip.keyframes["x"].orEmpty().isNotEmpty()
                            val hasYKf = clip.keyframes["y"].orEmpty().isNotEmpty()

                            val preserveScaleOverscan = preservesLayerOverscan(clip)

                            if (preserveScaleOverscan) {
                                val baseX = 50f + clip.offsetX * 100f
                                val baseY = 50f + clip.offsetY * 100f
                                val localTimeExpression =
                                    "(t-${String.format(Locale.US, "%.6f", startSec)})"
                                val xPosition = KeyframeStore.escapeFfmpegExpression(
                                    KeyframeStore.ffmpegExpression(
                                        clip.keyframes,
                                        "x",
                                        baseX,
                                        localTimeExpression
                                    )
                                )
                                val yPosition = KeyframeStore.escapeFfmpegExpression(
                                    KeyframeStore.ffmpegExpression(
                                        clip.keyframes,
                                        "y",
                                        baseY,
                                        localTimeExpression
                                    )
                                )
                                val x = "((main_w-overlay_w)/2+(($xPosition-50)*$targetW/100))"
                                val y = "((main_h-overlay_h)/2+(($yPosition-50)*$targetH/100))"
                                filterParts.add("[$canvasLabel][$layerLabel]overlay=x='$x':y='$y':eval=frame:eof_action=pass:repeatlast=0:enable='between(t,$startExpr,$endExpr)'[$outLabel]")
                            } else if (!hasXKf && !hasYKf) {
                                val padding = shadowOutputs?.padding ?: 0
                                filterParts.add("[$canvasLabel][$layerLabel]overlay=${-padding}:${-padding}:eof_action=pass:repeatlast=0:enable='between(t,$startExpr,$endExpr)'[$outLabel]")
                            } else {
                                val padding = shadowOutputs?.padding ?: 0
                                val baseX = 50f + clip.offsetX * 100f
                                val baseY = 50f + clip.offsetY * 100f
                                val localTimeExpression = "(t-${String.format(Locale.US, "%.6f", startSec)})"

                                val x = "((${KeyframeStore.ffmpegExpression(clip.keyframes, "x", baseX, localTimeExpression)}-50)*$targetW/100-$padding)"
                                val y = "((${KeyframeStore.ffmpegExpression(clip.keyframes, "y", baseY, localTimeExpression)}-50)*$targetH/100-$padding)"

                                filterParts.add("[$canvasLabel][$layerLabel]overlay=x='$x':y='$y':eval=frame:eof_action=pass:repeatlast=0:enable='between(t,$startExpr,$endExpr)'[$outLabel]")
                            }
                        }
                        else {
                            var blendBaseLabel = canvasLabel
                            shadowOutputs?.let { shadow ->
                                val shadowCanvas = "canvas_shadow_${item.clipIdx}"
                                filterParts.add(
                                    "[$blendBaseLabel][${shadow.shadowOnly}]overlay=" +
                                            "x='-${shadow.padding}':y='-${shadow.padding}':" +
                                            "eof_action=pass:repeatlast=0:" +
                                            "enable='between(t,$startExpr,$endExpr)'[$shadowCanvas]"
                                )
                                blendBaseLabel = shadowCanvas
                            }
                            val ffMode = ffmpegBlendName(blendKey);
                            val blendAlpha = String.format(
                                Locale.US,
                                "%.3f",
                                (clip.blendOpacity / 100f).coerceIn(0f, 1f)
                            )
                            filterParts.add("[$blendBaseLabel]format=rgba[blend_base_${item.clipIdx}]"); filterParts.add(
                                "[$unshadowedLayerLabel]format=rgba[blend_layer_rgba_${item.clipIdx}]"
                            )
                            filterParts.add("[blend_base_${item.clipIdx}][blend_layer_rgba_${item.clipIdx}]blend=all_mode=$ffMode:all_opacity=$blendAlpha:shortest=0[blend_out_${item.clipIdx}]")
                            filterParts.add("[$blendBaseLabel][blend_out_${item.clipIdx}]overlay=0:0:eof_action=pass:repeatlast=0:enable='between(t,$startExpr,$endExpr)'[$outLabel]")
                        }
                        canvasLabel = outLabel
                    }

                    2 -> {
                        val seq = item.sequence ?: return@forEach
                        addSequenceSourceFilter(
                            filterParts,
                            item.inputIndex,
                            "seqsrc_${item.sequenceIdx}",
                            seq
                        )
                        val startSec = String.format(Locale.US, "%.4f", seq.startSec)
                        val endSec = String.format(Locale.US, "%.4f", seq.endSec)
                        val enable = "between(t,$startSec,$endSec)"
                        val outLabel = "canvas_s${item.sequenceIdx}"
                        if (seq.blendMode.equals("addition", ignoreCase = true)) {
                            filterParts.add("[$canvasLabel]format=rgba[canvas_add_${item.sequenceIdx}]")
                            filterParts.add(
                                "[canvas_add_${item.sequenceIdx}][seqsrc_${item.sequenceIdx}]blend=all_mode=addition:shortest=0:enable='$enable'[$outLabel]"
                            )
                        } else {
                            filterParts.add(
                                "[$canvasLabel][seqsrc_${item.sequenceIdx}]overlay=0:0:enable='$enable'[$outLabel]"
                            )
                        }
                        canvasLabel = outLabel
                    }

                    3 -> {
                        val adj = item.adjustmentClip ?: return@forEach
                        val hasAdj = !adj.adjustments.isDefault
                        if (hasAdj) {
                            val startSec = adj.timelineStartMs / 1000.0;
                            val endSec = adj.timelineEndMs / 1000.0
                            if (endSec > startSec + 0.01) {
                                val enable = adjustmentLayerEnableExpression(adj, allClips)
                                val uid = "adj_${item.clipIdx}"
                                filterParts.add("[$canvasLabel]split=2[canvas_${uid}_base][canvas_${uid}_src]")

                                var currentChain = "canvas_${uid}_src"
                                val adjFilter = buildFullAdjustmentFilter(adj.adjustments)
                                if (adjFilter.isNotBlank()) {
                                    filterParts.add("[$currentChain]$adjFilter[canvas_${uid}_adjstep]")
                                    currentChain = "canvas_${uid}_adjstep"
                                }
                                if (currentChain != "canvas_${uid}_src") {
                                    filterParts.add("[canvas_${uid}_base][$currentChain]overlay=0:0:enable='$enable'[canvas_${uid}_final]")
                                    canvasLabel = "canvas_${uid}_final"
                                }
                            }
                        }
                    }
                }
            }

            filterParts.add("[$canvasLabel]format=yuv420p[outv]")
            val audioLabels = mutableListOf<String>()

            audioOnlyClips.forEachIndexed { index, clip ->
                val inputIdx = orderedClips.size + index
                val duration = ((clip.sourceEndMs - clip.sourceStartMs) / 1000.0).coerceAtLeast(0.1)
                val af = mutableListOf(
                    "atrim=start=${clip.sourceStartMs / 1000.0}:duration=$duration",
                    "asetpts=PTS-STARTPTS"
                )
                if (clip.speed != 1.0f) af.add("atempo=${clip.speed.coerceIn(0.5f, 2.0f)}")
                af.add("atrim=duration=${(clip.durationMs / 1000.0).coerceAtLeast(0.1)}")
                af.add("volume=${if (clip.isMuted) 0f else clip.volume}")
                if (clip.audioFx != "none") {
                    val fx = AudioEngine.buildAudioFilter(clip.audioFx, clip.audioFxIntensity)
                    if (fx.isNotBlank()) af.add(fx)
                }
                af.add(
                    "adelay=${clip.timelineStartMs.coerceAtLeast(0L)}|${
                        clip.timelineStartMs.coerceAtLeast(
                            0L
                        )
                    }"
                )
                af.add("aresample=44100")
                filterParts.add("[$inputIdx:a]${af.joinToString(",")}[extraAudio$index]")
                audioLabels.add("[extraAudio$index]")
            }

            filterParts.add("[$silenceInputIdx:a]aresample=44100[silentAudio]")
            audioLabels.add("[silentAudio]")
            filterParts.add("${audioLabels.joinToString("")}amix=inputs=${audioLabels.size}:duration=longest:dropout_transition=0[mixedAudio]")
            val fxApplied = applyAudioEffectLayers(
                filterParts,
                allClips,
                0L,
                "mixedAudio",
                "finalAudio"
            )
            val finalAudioLabel = if (fxApplied) "finalAudio" else "mixedAudio"

            val finalVideo = enforceOutputDimensions(args, filterParts, "outv", targetW, targetH, fps)
            args.addAll(
                listOf(
                    "-filter_complex_threads",
                    recommendedFfmpegThreads(targetW, targetH).toString(),
                    "-filter_complex",
                    filterParts.joinToString(";"),
                    "-map",
                    "[$finalVideo]",
                    "-map",
                    "[$finalAudioLabel]",
                    "-t",
                    durationSec.toString()
                )
            )
            args.addAll(buildFastVideoArgs(bitrateKbps, targetW, targetH))
            args.addAll(
                listOf(
                    "-r",
                    fps.toString(),
                    "-c:a",
                    "aac",
                    "-b:a",
                    "128k",
                    "-movflags",
                    "+faststart",
                    outputFile.absolutePath
                )
            )

            execute(args, outputFile, (durationSec * 1000.0).toLong())
        } catch (e: Exception) {
            Log.e("FFMPEG", "Layered-track export error", e)
            onError("Layered-track export failed: ${e.message}")
        }
    }

    private fun exportSingleClip(
        clip: EditorClip,
        allClips: List<EditorClip>,
        localFile: File,
        outputFile: File,
        targetW: Int,
        targetH: Int,
        fps: Int,
        bitrateKbps: Int,
        sequences: List<TextOverlaySequence>,
        totalDurationMs: Long
    ) {
        try {
            val img = isImage(clip);
            val durSec = ((clip.sourceEndMs - clip.sourceStartMs) / 1000.0).coerceAtLeast(0.1)
            val clipDurSec = (clip.durationMs / 1000.0).coerceAtLeast(0.1);
            val totalDurSec = (totalDurationMs / 1000.0).coerceAtLeast(durSec)
            val extraPadSec = (totalDurSec - clipDurSec).coerceAtLeast(0.0)

            val args = mutableListOf("-y")
            if (img) args.addAll(
                listOf(
                    "-loop",
                    "1",
                    "-framerate",
                    fps.toString(),
                    "-t",
                    clipDurSec.toString()
                )
            )
            else {
                if (clip.sourceStartMs > 0) args.addAll(
                    listOf(
                        "-ss",
                        (clip.sourceStartMs / 1000.0).toString()
                    )
                )
                if (clip.sourceEndMs > clip.sourceStartMs) args.addAll(
                    listOf(
                        "-t",
                        durSec.toString()
                    )
                )
            }
            args.addAll(listOf("-i", localFile.absolutePath))

            args.addAll(
                listOf(
                    "-f",
                    "lavfi",
                    "-t",
                    totalDurSec.toString(),
                    "-i",
                    "anullsrc=r=44100:cl=stereo"
                )
            )
            val audioInputIdx = 1
            val seqStartIdx = 2
            addSequenceInputs(args, sequences)

            val filterParts = mutableListOf<String>()
            val baseVf = mutableListOf<String>()
            if (clip.speed != 1.0f && !img) baseVf.add("setpts=${1.0f / clip.speed}*PTS")
            baseVf.addAll(buildPerClipFilterChain(clip, allClips, fps))
            baseVf.addAll(buildVisualTransformFilters(clip, targetW, targetH))
            if (extraPadSec > 0.05) baseVf.add(
                "tpad=stop_mode=add:stop_duration=${
                    String.format(
                        Locale.US,
                        "%.3f",
                        extraPadSec
                    )
                }"
            )

            filterParts.add("[0:v]${baseVf.joinToString(",")}[base]")
            val outVLabel = buildOverlayChain(filterParts, sequences, seqStartIdx, "base")
            val finalVideoLabel = enforceOutputDimensions(
                args, filterParts, outVLabel, targetW, targetH, fps
            )

            val af = mutableListOf<String>()
            if (clip.speed != 1.0f && !img) af.add("atempo=${clip.speed.coerceIn(0.5f, 2.0f)}")
            if (clip.volume != 1.0f) af.add("volume=${clip.volume}")
            if (clip.audioFx != "none") {
                val fx = AudioEngine.buildAudioFilter(clip.audioFx, clip.audioFxIntensity)
                if (fx.isNotBlank()) af.add(fx)
            }

            if (af.isEmpty()) filterParts.add("[$audioInputIdx:a]aresample=44100[basea]")
            else filterParts.add("[$audioInputIdx:a]${af.joinToString(",")},aresample=44100[basea]")

            val fxApplied = applyAudioEffectLayers(filterParts, allClips, 0L, "basea", "fxa")
            val afterFxLabel = if (fxApplied) "fxa" else "basea"

            args.addAll(
                listOf(
                    "-filter_complex",
                    filterParts.joinToString(";"),
                    "-map",
                    "[$finalVideoLabel]",
                    "-map",
                    "[$afterFxLabel]",
                    "-t",
                    totalDurSec.toString()
                )
            )
            args.addAll(buildFastVideoArgs(bitrateKbps, targetW, targetH))
            args.addAll(
                listOf(
                    "-r",
                    fps.toString(),
                    "-c:a",
                    "aac",
                    "-b:a",
                    "128k",
                    "-movflags",
                    "+faststart",
                    outputFile.absolutePath
                )
            )
            execute(args, outputFile, (totalDurSec * 1000.0).toLong())
        } catch (e: Throwable) {
            onError("Build error: ${e.message}")
        }
    }

    private fun exportSingleClipWithAudio(
        clip: EditorClip,
        allClips: List<EditorClip>,
        localFile: File,
        audioClips: List<EditorClip>,
        audioLocalFiles: List<File>,
        outputFile: File,
        targetW: Int,
        targetH: Int,
        fps: Int,
        bitrateKbps: Int,
        sequences: List<TextOverlaySequence>,
        totalDurationMs: Long
    ) {
        try {
            val img = isImage(clip);
            val sourceDurSec = ((clip.sourceEndMs - clip.sourceStartMs) / 1000.0).coerceAtLeast(0.1)
            val clipDurSec = (clip.durationMs / 1000.0).coerceAtLeast(0.1);
            val totalDurSec = (totalDurationMs / 1000.0).coerceAtLeast(clipDurSec)
            val extraPadSec = (totalDurSec - clipDurSec).coerceAtLeast(0.0)

            val args = mutableListOf("-y")
            if (img) args.addAll(
                listOf(
                    "-loop",
                    "1",
                    "-framerate",
                    fps.toString(),
                    "-t",
                    sourceDurSec.toString()
                )
            )
            else {
                if (clip.sourceStartMs > 0) args.addAll(
                    listOf(
                        "-ss",
                        (clip.sourceStartMs / 1000.0).toString()
                    )
                )
                if (clip.sourceEndMs > clip.sourceStartMs) args.addAll(
                    listOf(
                        "-t",
                        sourceDurSec.toString()
                    )
                )
            }
            args.addAll(listOf("-i", localFile.absolutePath))

            args.addAll(
                listOf(
                    "-f",
                    "lavfi",
                    "-t",
                    totalDurSec.toString(),
                    "-i",
                    "anullsrc=r=44100:cl=stereo"
                )
            )
            val audioInputIdx = 1

            val audioStartIdx = 2
            audioLocalFiles.forEach { f -> args.addAll(listOf("-i", f.absolutePath)) }

            val seqStartIdx = audioStartIdx + audioLocalFiles.size
            addSequenceInputs(args, sequences)

            val filterParts = mutableListOf<String>()
            val baseVf = mutableListOf<String>()
            if (clip.speed != 1.0f && !img) baseVf.add("setpts=${1.0f / clip.speed}*PTS")
            baseVf.addAll(buildPerClipFilterChain(clip, allClips, fps))
            baseVf.addAll(buildVisualTransformFilters(clip, targetW, targetH))
            if (extraPadSec > 0.05) baseVf.add(
                "tpad=stop_mode=add:stop_duration=${
                    String.format(
                        Locale.US,
                        "%.3f",
                        extraPadSec
                    )
                }"
            )

            filterParts.add("[0:v]${baseVf.joinToString(",")}[base]")
            val outVLabel = buildOverlayChain(filterParts, sequences, seqStartIdx, "base")
            val finalVideoLabel = enforceOutputDimensions(
                args, filterParts, outVLabel, targetW, targetH, fps
            )

            val af = mutableListOf<String>()
            if (clip.speed != 1.0f && !img) af.add("atempo=${clip.speed.coerceIn(0.5f, 2.0f)}")
            if (clip.volume != 1.0f) af.add("volume=${clip.volume}")
            if (clip.audioFx != "none") {
                val fx = AudioEngine.buildAudioFilter(clip.audioFx, clip.audioFxIntensity)
                if (fx.isNotBlank()) af.add(fx)
            }

            if (af.isEmpty()) filterParts.add("[$audioInputIdx:a]aresample=44100[basea]")
            else filterParts.add("[$audioInputIdx:a]${af.joinToString(",")},aresample=44100[basea]")

            val mixed = buildAudioOnlyMix(
                filterParts,
                audioLocalFiles,
                audioClips,
                audioStartIdx,
                "basea",
                "mixeda"
            )
            val afterMixLabel = if (mixed) "mixeda" else "basea"
            val fxApplied = applyAudioEffectLayers(filterParts, allClips, 0L, afterMixLabel, "fxa")
            val finalAudioLabel = if (fxApplied) "fxa" else afterMixLabel

            args.addAll(
                listOf(
                    "-filter_complex",
                    filterParts.joinToString(";"),
                    "-map",
                    "[$finalVideoLabel]",
                    "-map",
                    "[$finalAudioLabel]",
                    "-t",
                    totalDurSec.toString()
                )
            )
            args.addAll(buildFastVideoArgs(bitrateKbps, targetW, targetH))
            args.addAll(
                listOf(
                    "-r",
                    fps.toString(),
                    "-c:a",
                    "aac",
                    "-b:a",
                    "128k",
                    "-movflags",
                    "+faststart",
                    outputFile.absolutePath
                )
            )
            execute(args, outputFile, (totalDurSec * 1000.0).toLong())
        } catch (e: Throwable) {
            onError("Build error: ${e.message}")
        }
    }

    private fun exportMultipleClips(
        clips: List<EditorClip>,
        allClips: List<EditorClip>,
        localFiles: List<File>,
        audioClips: List<EditorClip>,
        audioLocalFiles: List<File>,
        outputFile: File,
        targetW: Int,
        targetH: Int,
        fps: Int,
        bitrateKbps: Int,
        sequences: List<TextOverlaySequence>,
        totalDurationMs: Long,
        matteClips: List<EditorClip> = emptyList(),
        maskSequences: List<MaskFrameSequence> = emptyList()
    ) {
        val sortedClips = clips.sortedBy { it.timelineStartMs }
        val sortedLocalFiles = sortedClips.mapNotNull { c ->
            val idx = clips.indexOf(c); if (idx >= 0) localFiles.getOrNull(idx) else null
        }

        if (sortedClips.size != sortedLocalFiles.size) {
            exportWithConcat(
                sortedClips,
                allClips,
                sortedLocalFiles,
                audioClips,
                audioLocalFiles,
                outputFile,
                targetW,
                targetH,
                fps,
                bitrateKbps,
                sequences,
                totalDurationMs
            )
            return
        }

        val useXfade =
            sortedClips.size >= 2 && sortedClips.all { it.trackIndex == sortedClips.first().trackIndex } &&
                    sortedClips.all { kotlin.math.abs(it.speed - 1.0f) < 0.01f } && sortedClips.drop(
                1
            ).any { it.transition?.isActive == true } &&
                    sortedClips.zipWithNext()
                        .all { (a, b) -> kotlin.math.abs((b.timelineStartMs - a.timelineEndMs)) < 100L }

        if (useXfade) exportWithTransitions(
            sortedClips,
            allClips,
            sortedLocalFiles,
            audioClips,
            audioLocalFiles,
            outputFile,
            targetW,
            targetH,
            fps,
            bitrateKbps,
            sequences,
            totalDurationMs,
            matteClips,
            maskSequences
        )
        else exportWithConcat(
            sortedClips,
            allClips,
            sortedLocalFiles,
            audioClips,
            audioLocalFiles,
            outputFile,
            targetW,
            targetH,
            fps,
            bitrateKbps,
            sequences,
            totalDurationMs
        )
    }

    private fun exportWithTransitions(
        clips: List<EditorClip>,
        allClips: List<EditorClip>,
        localFiles: List<File>,
        audioClips: List<EditorClip>,
        audioLocalFiles: List<File>,
        outputFile: File,
        targetW: Int,
        targetH: Int,
        fps: Int,
        bitrateKbps: Int,
        sequences: List<TextOverlaySequence>,
        totalDurationMs: Long,
        matteClips: List<EditorClip> = emptyList(),
        maskSequences: List<MaskFrameSequence> = emptyList()
    ) {
        try {
            val args = mutableListOf("-y")
            clips.forEachIndexed { idx, clip ->
                if (isImage(clip)) args.addAll(
                    listOf(
                        "-loop",
                        "1",
                        "-framerate",
                        fps.toString(),
                        "-t",
                        (clip.durationMs / 1000.0).toString()
                    )
                )
                else {
                    if (clip.sourceStartMs > 0) args.addAll(
                        listOf(
                            "-ss",
                            (clip.sourceStartMs / 1000.0).toString()
                        )
                    )
                    args.addAll(
                        listOf(
                            "-t",
                            ((clip.sourceEndMs - clip.sourceStartMs) / 1000.0).coerceAtLeast(0.1)
                                .toString()
                        )
                    )
                }
                args.addAll(listOf("-threads", "1", "-i", localFiles[idx].absolutePath))
            }

            val silentIdx = mutableMapOf<Int, Int>();
            var nextIdx = clips.size
            clips.forEachIndexed { idx, clip ->
                args.addAll(
                    listOf(
                        "-f",
                        "lavfi",
                        "-t",
                        (clip.durationMs / 1000.0).toString(),
                        "-i",
                        "anullsrc=r=44100:cl=stereo"
                    )
                )
                silentIdx[idx] = nextIdx++
            }

            val audioStartIdx = nextIdx
            audioLocalFiles.forEach { f -> args.addAll(listOf("-i", f.absolutePath)); nextIdx++ }
            val matteInputIndices = mutableMapOf<String, Int>()
            matteClips.sortedBy { it.trackIndex }.forEach { matte ->
                val matteFile = renderMatteToPng(matte.matteStyle, targetW, targetH)
                args.addAll(
                    listOf(
                        "-loop", "1",
                        "-framerate", fps.toString(),
                        "-t", (matte.durationMs / 1000.0).coerceAtLeast(0.1).toString(),
                        "-i", matteFile.absolutePath
                    )
                )
                matteInputIndices[matte.id] = nextIdx++
            }
            val matteMaskInputIndices = mutableMapOf<String, Int>()
            maskSequences.forEach { sequence ->
                val matte = matteClips.firstOrNull { it.id == sequence.clipId }
                    ?: return@forEach
                if (sequence.staticFile != null) {
                    args.addAll(
                        listOf(
                            "-loop", "1",
                            "-framerate", fps.toString(),
                            "-t", (matte.durationMs / 1000.0).coerceAtLeast(0.1).toString(),
                            "-i", sequence.staticFile.absolutePath
                        )
                    )
                } else {
                    args.addAll(
                        listOf(
                            "-framerate", sequence.fps.toString(),
                            "-start_number", "1",
                            "-i", sequence.pattern
                        )
                    )
                }
                matteMaskInputIndices[sequence.clipId] = nextIdx++
            }
            val seqStartIdx = nextIdx
            addSequenceInputs(args, sequences)

            val filterParts = mutableListOf<String>()
            clips.forEachIndexed { idx, clip ->
                val vf = mutableListOf<String>()
                if (isImage(clip)) vf.add("setpts=PTS-STARTPTS") else vf.addAll(
                    listOf(
                        "trim=duration=${(clip.sourceEndMs - clip.sourceStartMs) / 1000.0}",
                        "setpts=PTS-STARTPTS"
                    )
                )
                if (clip.speed != 1.0f && !isImage(clip)) vf.add("setpts=${1.0f / clip.speed}*PTS")
                vf.addAll(buildPerClipFilterChain(clip, allClips, fps))
                vf.addAll(buildVisualTransformFilters(clip, targetW, targetH))
                vf.addAll(
                    listOf(
                        "fps=$fps",
                        "settb=AVTB",
                        "setpts=PTS-STARTPTS",
                        "format=yuv420p",
                        "setsar=1"
                    )
                )
                filterParts.add("[$idx:v]${vf.joinToString(",")}[nv$idx]")
            }

            val transitionSteps = TransitionExportPlan.create(
                clips.map { it.durationMs },
                clips.map { it.transition },
                fps
            )
            val exportDurationSec = (totalDurationMs / 1000.0).coerceAtLeast(0.1)
            var currentLabel = "nv0"
            for (i in 1 until clips.size) {
                val step = transitionSteps[i - 1]
                filterParts.add("[$currentLabel][nv$i]xfade=transition=${step.ffmpegTransition}:duration=${step.durationMs / 1000.0}:offset=${step.offsetMs / 1000.0}[xfd$i]")
                currentLabel = "xfd$i"
                step.lightLeakColor?.let { lightColor ->
                    val startSec = step.offsetMs / 1000.0
                    val durationSec = (step.durationMs / 1000.0).coerceAtLeast(0.001)
                    val endSec = startSec + durationSec
                    val progressExpr = "clip((T-$startSec)/$durationSec,0,1)"
                    val centerX = if (step.lightLeakMovesRight) {
                        "W*$progressExpr"
                    } else {
                        "W*(1-$progressExpr)"
                    }
                    val centerY = "H*(0.25+0.5*$progressExpr)"
                    val radius = "max(W,H)*0.72"
                    val radialAlpha = "clip(1-hypot(X-$centerX,Y-$centerY)/($radius),0,1)"
                    val beamAlpha = "clip(1-abs(X-$centerX)/(W*0.5),0,1)"
                    val pulse = "sin(PI*$progressExpr)*0.68"
                    val alphaExpr =
                        "255*clip(1-(1-($radialAlpha)*($pulse))*(1-($beamAlpha)*($pulse)),0,1)"
                    val leakLabel = "transition_leak_$i"
                    val blendedLabel = "transition_light_$i"
                    filterParts.add(
                        "color=c=0x${lightColor}:s=${targetW}x${targetH}:r=$fps:d=$exportDurationSec," +
                                "format=rgba," +
                                "geq=r='r(X,Y)':g='g(X,Y)':b='b(X,Y)':a='$alphaExpr'[$leakLabel]"
                    )
                    filterParts.add(
                        "[$currentLabel][$leakLabel]overlay=x=0:y=0:format=auto:" +
                                "eof_action=pass:repeatlast=0:" +
                                "enable='between(t,$startSec,$endSec)'[$blendedLabel]"
                    )
                    currentLabel = blendedLabel
                }
            }

            matteClips.sortedBy { it.trackIndex }.forEachIndexed { index, matte ->
                val inputIndex = matteInputIndices[matte.id] ?: return@forEachIndexed
                val startSec = matte.timelineStartMs.coerceAtLeast(0L) / 1000.0
                val endSec = matte.timelineEndMs.coerceAtMost(totalDurationMs) / 1000.0
                if (endSec <= startSec) return@forEachIndexed
                val alpha = (matte.filters.opacity / 100f).coerceIn(0f, 1f)
                val matteLabel = "transition_matte_$index"
                val outLabel = "transition_matte_canvas_$index"
                val matteFilters = mutableListOf(
                    "format=rgba",
                    "colorchannelmixer=aa=${String.format(Locale.US, "%.3f", alpha)}"
                )
                buildTransparentCropFilter(matte)?.let(matteFilters::add)
                matteFilters.addAll(
                    buildVisualTransformFilters(matte, targetW, targetH, true, true)
                )
                matteFilters.add("setpts=PTS-STARTPTS+${startSec}/TB")
                filterParts.add(
                    "[$inputIndex:v]${matteFilters.joinToString(",")}[$matteLabel]"
                )
                val maskInputIndex = matteMaskInputIndices[matte.id]
                val compositedMatteLabel = if (maskInputIndex != null) {
                    val maskLabel = "transition_matte_mask_$index"
                    val maskedLabel = "transition_matte_masked_$index"
                    val maskFilters = mutableListOf("format=rgba")
                    buildTransparentCropFilter(matte)?.let(maskFilters::add)
                    maskFilters.add("alphaextract")
                    maskFilters.add("format=gray")
                    maskFilters.add("setpts=PTS-STARTPTS+${startSec}/TB")
                    filterParts.add(
                        "[$maskInputIndex:v]${maskFilters.joinToString(",")}[$maskLabel]"
                    )
                    filterParts.add(
                        "[$matteLabel]format=rgba[transition_matte_rgba_$index]"
                    )
                    filterParts.add(
                        "[transition_matte_rgba_$index][$maskLabel]alphamerge[$maskedLabel]"
                    )
                    maskedLabel
                } else {
                    matteLabel
                }
                filterParts.add(
                    "[$currentLabel][$compositedMatteLabel]overlay=0:0:eof_action=pass:" +
                            "repeatlast=0:enable='between(t,$startSec,$endSec)'[$outLabel]"
                )
                currentLabel = outLabel
            }

            clips.forEachIndexed { idx, clip ->
                val srcIdx = silentIdx.getValue(idx)
                filterParts.add("[$srcIdx:a]atrim=start=0:duration=${clip.durationMs / 1000.0},asetpts=PTS-STARTPTS,aresample=44100[ca$idx]")
            }

            var currentAudioLabel = "ca0"
            for (i in 1 until clips.size) {
                val step = transitionSteps[i - 1]
                filterParts.add("[$currentAudioLabel][ca$i]acrossfade=d=${step.durationMs / 1000.0}:c1=tri:c2=tri[across$i]")
                currentAudioLabel = "across$i"
            }
            var finalAudioLabel = currentAudioLabel

            if (audioLocalFiles.isNotEmpty()) {
                if (buildAudioOnlyMix(
                        filterParts,
                        audioLocalFiles,
                        audioClips,
                        audioStartIdx,
                        finalAudioLabel,
                        "mixeda"
                    )
                ) finalAudioLabel = "mixeda"
            }
            if (applyAudioEffectLayers(
                    filterParts,
                    allClips,
                    0L,
                    finalAudioLabel,
                    "fxa"
                )
            ) finalAudioLabel = "fxa"

            val outVLabel = buildOverlayChain(filterParts, sequences, seqStartIdx, currentLabel)
            val finalVideoLabel = enforceOutputDimensions(
                args, filterParts, outVLabel, targetW, targetH, fps
            )
            val totalDurSec = (totalDurationMs / 1000.0).coerceAtLeast(0.1)

            args.addAll(
                listOf(
                    "-filter_complex_threads",
                    recommendedFfmpegThreads(targetW, targetH).toString(),
                    "-filter_complex",
                    filterParts.joinToString(";"),
                    "-map",
                    "[$finalVideoLabel]",
                    "-map",
                    "[$finalAudioLabel]",
                    "-t",
                    totalDurSec.toString()
                )
            )
            args.addAll(
                buildFastVideoArgs(
                    bitrateKbps,
                    targetW,
                    targetH,
                    recommendedFfmpegThreads(targetW, targetH)
                )
            )
            args.addAll(
                listOf(
                    "-r",
                    fps.toString(),
                    "-c:a",
                    "aac",
                    "-b:a",
                    "128k",
                    "-movflags",
                    "+faststart",
                    outputFile.absolutePath
                )
            )

            execute(args, outputFile, (totalDurSec * 1000.0).toLong())
        } catch (e: Throwable) {
            cleanupMaskSequences()
            onError("Transition export failed: ${e.message}")
        }
    }

    private fun exportWithConcat(
        clips: List<EditorClip>,
        allClips: List<EditorClip>,
        localFiles: List<File>,
        audioClips: List<EditorClip>,
        audioLocalFiles: List<File>,
        outputFile: File,
        targetW: Int,
        targetH: Int,
        fps: Int,
        bitrateKbps: Int,
        sequences: List<TextOverlaySequence>,
        totalDurationMs: Long
    ) {
        try {
            val args = mutableListOf("-y")
            clips.forEachIndexed { idx, clip ->
                if (isImage(clip)) args.addAll(
                    listOf(
                        "-loop",
                        "1",
                        "-framerate",
                        fps.toString(),
                        "-t",
                        (clip.durationMs / 1000.0).toString()
                    )
                )
                args.addAll(listOf("-i", localFiles[idx].absolutePath))
            }

            val silentAudioInputs = mutableMapOf<Int, Int>();
            var nextInputIdx = clips.size
            clips.forEachIndexed { idx, clip ->
                args.addAll(
                    listOf(
                        "-f",
                        "lavfi",
                        "-t",
                        (clip.durationMs / 1000.0).toString(),
                        "-i",
                        "anullsrc=r=44100:cl=stereo"
                    )
                )
                silentAudioInputs[idx] = nextInputIdx++
            }

            val audioStartIdx = nextInputIdx
            audioLocalFiles.forEach { f ->
                args.addAll(
                    listOf(
                        "-i",
                        f.absolutePath
                    )
                ); nextInputIdx++
            }
            val seqStartIdx = nextInputIdx
            addSequenceInputs(args, sequences)

            val filterParts = mutableListOf<String>()
            val concatInputs = mutableListOf<String>()
            clips.forEachIndexed { idx, clip ->
                val durationSec = clip.durationMs / 1000.0
                val vf = mutableListOf<String>()
                if (isImage(clip)) vf.add("setpts=PTS-STARTPTS") else vf.addAll(
                    listOf(
                        "trim=start=${clip.sourceStartMs / 1000.0}:duration=$durationSec",
                        "setpts=PTS-STARTPTS"
                    )
                )
                if (clip.speed != 1.0f && !isImage(clip)) vf.add("setpts=${1.0f / clip.speed}*PTS")
                vf.addAll(buildPerClipFilterChain(clip, allClips, fps))
                vf.addAll(buildVisualTransformFilters(clip, targetW, targetH))
                vf.addAll(listOf("fps=$fps", "format=yuv420p"))
                filterParts.add("[$idx:v]${vf.joinToString(",")}[v$idx]")

                val aIdx = silentAudioInputs.getValue(idx)
                val af = mutableListOf("asetpts=PTS-STARTPTS", "aresample=44100")
                filterParts.add("[$aIdx:a]${af.joinToString(",")}[a$idx]")
                concatInputs.add("[v$idx][a$idx]")
            }

            filterParts.add("${concatInputs.joinToString("")}concat=n=${clips.size}:v=1:a=1[concatv][basea]")

            val mixed = if (audioLocalFiles.isNotEmpty()) buildAudioOnlyMix(
                filterParts,
                audioLocalFiles,
                audioClips,
                audioStartIdx,
                "basea",
                "mixeda"
            ) else false
            val afterMixLabel = if (mixed) "mixeda" else "basea"
            val fxApplied = applyAudioEffectLayers(filterParts, allClips, 0L, afterMixLabel, "fxa")
            val finalAudioLabel = if (fxApplied) "fxa" else afterMixLabel

            val outVLabel = buildOverlayChain(filterParts, sequences, seqStartIdx, "concatv")
            val finalVideoLabel = enforceOutputDimensions(
                args, filterParts, outVLabel, targetW, targetH, fps
            )
            val totalDurSec = (totalDurationMs / 1000.0).coerceAtLeast(0.1)

            args.addAll(
                listOf(
                    "-filter_complex",
                    filterParts.joinToString(";"),
                    "-map",
                    "[$finalVideoLabel]",
                    "-map",
                    "[$finalAudioLabel]",
                    "-t",
                    totalDurSec.toString()
                )
            )
            args.addAll(buildFastVideoArgs(bitrateKbps, targetW, targetH))
            args.addAll(
                listOf(
                    "-r",
                    fps.toString(),
                    "-c:a",
                    "aac",
                    "-b:a",
                    "128k",
                    "-movflags",
                    "+faststart",
                    outputFile.absolutePath
                )
            )
            execute(args, outputFile, (totalDurSec * 1000.0).toLong())
        } catch (e: Throwable) {
            onError("Concat export: ${e.message}")
        }
    }

    private fun buildPerClipFilterChain(
        clip: EditorClip,
        allClips: List<EditorClip>,
        fps: Int
    ): List<String> {
        val filters = mutableListOf<String>()

        val filterLayers = allClips.filter {
            it.isFilterLayerClip &&
                    it.timelineEndMs > clip.timelineStartMs &&
                    it.timelineStartMs < clip.timelineEndMs
        }.sortedBy { it.trackIndex }
        val effectLayers = allClips.filter {
            it.isEffectClip &&
                    it.trackIndex > clip.trackIndex &&
                    it.timelineEndMs > clip.timelineStartMs &&
                    it.timelineStartMs < clip.timelineEndMs
        }.sortedBy { it.trackIndex }
        fun staticLayerOpacity(layer: EditorClip): Float =
            if (layer.keyframes[KeyframeLibrary.LAYER_OPACITY].isNullOrEmpty()) {
                (layer.layerOpacity / 100f).coerceIn(0f, 1f)
            } else {
                1f
            }
        if (!clip.adjustments.isDefault) {
            val adjustmentFilter = buildFullAdjustmentFilter(clip.adjustments)
            if (adjustmentFilter.isNotBlank()) filters.add(adjustmentFilter)
        }

        val ownFilter = clip.filters.toColorFilterValues()
        val hasColorFilterLut = EffectsEngine.hasColorEffect(ownFilter) ||
                filterLayers.any {
                    EffectsEngine.hasColorEffect(
                        EffectsEngine.scaleColorFilter(
                            it.filters.toColorFilterValues(),
                            staticLayerOpacity(it)
                        )
                    )
                } ||
                effectLayers.any { effect ->
                    effect.effectState?.filters?.let {
                        EffectsEngine.hasColorEffect(
                            EffectsEngine.scaleColorFilter(it, staticLayerOpacity(effect))
                        )
                    } == true ||
                            effect.effectState?.animatedColor != null
                }
        if (hasColorFilterLut) {
            filters.add(if (clip.layerShadow.enabled) "format=rgba" else "format=rgb24")
        }
        if (EffectsEngine.hasColorEffect(ownFilter)) {
            filters.add(colorFilterValuesToFfmpeg(ownFilter))
        }

        effectLayers.forEach { effClip ->
                val start =
                    ((effClip.timelineStartMs - clip.timelineStartMs).coerceAtLeast(0L)) / 1000.0
                val end =
                    ((effClip.timelineEndMs - clip.timelineStartMs).coerceAtMost(clip.durationMs)) / 1000.0
                if (end > start) {
                    val enable =
                        String.format(Locale.US, ":enable='between(t,%.4f,%.4f)'", start, end)

                    fun add(c: String) {
                        if (c.isNotBlank()) c.split(",").forEach {
                            if (it.trim().isNotEmpty()) filters.add("${it.trim()}$enable")
                        }
                    }

                    val opacity = staticLayerOpacity(effClip)
                    effClip.effectState?.motion?.let {
                        add(motionToFfmpeg(it.copy(intensity = it.intensity * opacity)))
                    }
                    effClip.effectState?.filters?.let {
                        add(
                            colorFilterValuesToFfmpeg(
                                EffectsEngine.scaleColorFilter(it, opacity)
                            )
                        )
                    }
                    effClip.effectState?.animatedColor?.let {
                        val list = animatedColorToFfmpeg(it)
                        if (list.isNotEmpty() && opacity >= 0.999f) {
                            list.forEach { f -> filters.add("$f$enable") }
                        }
                        else add(
                            colorFilterValuesToFfmpeg(
                                EffectsEngine.scaleColorFilter(
                                    com.moody.moodyvideoeditor.utils.AnimatedColorEngine.compute(
                                        it,
                                        ((start + end) / 2.0).toFloat()
                                    ),
                                    opacity
                                )
                            )
                        )
                    }
                    effClip.effectState?.blur?.let { blur ->
                        filters.addAll(
                            buildAnimatedBlurFilters(
                                if (opacity >= 0.999f) blur else blur.copy(
                                    intensity = blur.intensity * opacity
                                ),
                                start,
                                end,
                                "blur${kotlin.math.abs(effClip.id.hashCode()) % 9999}"
                            )
                        )
                    }
                }
            }

        filterLayers.forEach { layer ->
            val start = (layer.timelineStartMs - clip.timelineStartMs).coerceAtLeast(0L)
            val end = (layer.timelineEndMs - clip.timelineStartMs).coerceAtMost(clip.durationMs)
            if (end <= start) return@forEach

            val enable = filterLayerEnableExpression(layer, filterLayers, clip)
            val layerValues = EffectsEngine.scaleColorFilter(
                layer.filters.toColorFilterValues(),
                staticLayerOpacity(layer)
            )
            if (EffectsEngine.hasColorEffect(layerValues)) {
                filters.add(colorFilterValuesToFfmpeg(layerValues, enable))
            }
        }

        val adjustmentColorWheels = allClips.filter { e ->
            e.isAdjustmentClip &&
                    e.colorWheel.hasAnyChange &&
                    e.trackIndex > clip.trackIndex &&
                    e.timelineEndMs > clip.timelineStartMs &&
                    e.timelineStartMs < clip.timelineEndMs
        }.sortedBy { it.trackIndex }

        if (adjustmentColorWheels.isNotEmpty() || clip.colorWheel.hasAnyChange) {
            if (!hasColorFilterLut) {
                filters.add("scale=in_color_matrix=bt709:in_range=tv:out_color_matrix=bt709:out_range=pc:flags=bilinear")
                filters.add("format=rgb24")
            }

            if (clip.colorWheel.hasAnyChange) {
                filters.add(buildColorWheelFilter(clip.colorWheel))
            }

            adjustmentColorWheels.forEach { adjLayer ->
                val relStartSec =
                    (adjLayer.timelineStartMs - clip.timelineStartMs).coerceAtLeast(0L) / 1000.0
                val relEndSec =
                    (adjLayer.timelineEndMs - clip.timelineStartMs).coerceAtMost(clip.durationMs) / 1000.0
                val coversWhole =
                    relStartSec < 0.01 && relEndSec > (clip.durationMs / 1000.0) - 0.01

                if (coversWhole) filters.add(buildColorWheelFilter(adjLayer.colorWheel))
                else filters.add(
                    buildColorWheelFilter(adjLayer.colorWheel, relStartSec, relEndSec)
                )
            }

            filters.add("scale=in_range=pc:out_range=tv")
            filters.add(if (clip.layerShadow.enabled) "format=rgba" else "format=yuv420p")
        } else if (hasColorFilterLut && !clip.layerShadow.enabled) {
            filters.add("format=yuv420p")
        }

        allClips.filter { it.isEffectClip && it.effectState?.lightLeak != null && it.trackIndex > clip.trackIndex && it.timelineEndMs > clip.timelineStartMs && it.timelineStartMs < clip.timelineEndMs }
            .sortedBy { it.trackIndex }.forEach { leak ->
                val opacity = staticLayerOpacity(leak)
                val filter = LightLeakEngine.buildFfmpegFilter(
                    com.moody.moodyvideoeditor.data.LightLeakState(
                        presetKey = "",
                        config = leak.effectState!!.lightLeak!!.copy(
                            intensity = leak.effectState!!.lightLeak!!.intensity * opacity
                        ),
                        opacity = 100f
                    )
                )
                if (filter.isNotBlank()) filters.add(filter)
            }

        if (clip.chromaKey.isActive) {
            val ckFilter = ChromaKeyEngine.buildFfmpegFilter(clip.chromaKey)
            if (ckFilter.isNotBlank()) {
                filters.add("format=rgba")
                filters.add(ckFilter)
            }
        }
        return filters
    }

    private fun applyAudioEffectLayers(
        filterParts: MutableList<String>,
        allClips: List<EditorClip>,
        rangeStartMs: Long,
        baseLabel: String,
        outLabel: String
    ): Boolean = AudioEngine.appendTimelineAudioEffects(
        filterParts,
        allClips,
        rangeStartMs,
        baseLabel,
        outLabel
    ) != baseLabel

    private fun motionToFfmpeg(m: MotionConfig): String {
        val I = (m.intensity / 100f).coerceIn(0.1f, 3.0f);
        val S = m.speed.coerceIn(0.2f, 10f)
        return when (m.type) {
            "shake" -> "rotate=${
                (2f * I).coerceIn(
                    0.5f,
                    5f
                )
            }*sin($S*t*37)*PI/180:c=none:ow=iw:oh=ih"

            "bounce" -> "scale=w=iw*(1+${
                (0.12f * I).coerceIn(
                    0.02f,
                    0.25f
                )
            }*abs(sin($S*t*4))):h=ih*(1+${
                (0.12f * I).coerceIn(
                    0.02f,
                    0.25f
                )
            }*abs(sin($S*t*4))):eval=frame"

            "pulse" -> "scale=w=iw*(1+${
                (0.08f * I).coerceIn(
                    0.02f,
                    0.15f
                )
            }*sin($S*t*3)):h=ih*(1+${(0.08f * I).coerceIn(0.02f, 0.15f)}*sin($S*t*3)):eval=frame"

            "zoomPulse" -> "scale=w=iw*(1+${
                (0.35f * I).coerceIn(
                    0.05f,
                    0.6f
                )
            }*0.5*(1+sin($S*t*2))):h=ih*(1+${
                (0.35f * I).coerceIn(
                    0.05f,
                    0.6f
                )
            }*0.5*(1+sin($S*t*2))):eval=frame"

            "rotate" -> "rotate=${(5f * I).coerceIn(1f, 15f)}*sin($S*t*2)*PI/180:c=none:ow=iw:oh=ih"
            "glitch" -> "noise=alls=${(20 * I).toInt().coerceIn(5, 80)}:allf=t+u"
            else -> ""
        }
    }

    private fun colorFilterValuesToFfmpeg(cf: ColorFilterValues, enable: String? = null): String {
        if (!EffectsEngine.hasColorEffect(cf)) return ""
        val lutFile = ensureColorFilterLut(cf)
        val safePath = lutFile.absolutePath.replace("\\", "/")
            .replace(":", "\\:")
            .replace("'", "\\'")
            .replace(" ", "\\ ")
        val enableOption = enable?.let { ":enable='$it'" }.orEmpty()
        return "lut3d=file=$safePath:interp=tetrahedral$enableOption"
    }

    @Synchronized
    private fun ensureColorFilterLut(filter: ColorFilterValues): File {
        val key = MessageDigest.getInstance("SHA-256")
            .digest(filter.toString().toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xff) }
        val file = File(context.cacheDir, "color-filter-preview-parity-v1-$key.cube")
        if (file.exists() && file.length() > 0L) return file

        val lutSize = 65
        val maxIndex = lutSize - 1
        val matrix = EffectsEngine.buildColorMatrix(filter)
        val contents = buildString {
            appendLine("TITLE \"Moody Color Filter Preview Parity\"")
            appendLine("LUT_3D_SIZE $lutSize")
            appendLine("DOMAIN_MIN 0.0 0.0 0.0")
            appendLine("DOMAIN_MAX 1.0 1.0 1.0")
            for (blue in 0 until lutSize) {
                for (green in 0 until lutSize) {
                    for (red in 0 until lutSize) {
                        val output = FilterPixelEngine.apply(
                            red * 255f / maxIndex,
                            green * 255f / maxIndex,
                            blue * 255f / maxIndex,
                            matrix
                        )
                        append(
                            """%.6f %.6f %.6f
""".format(
                                Locale.US,
                                output.first / 255f,
                                output.second / 255f,
                                output.third / 255f
                            )
                        )
                    }
                }
            }
        }
        file.writeText(contents)
        return file
    }

    private fun filterLayerEnableExpression(
        layer: EditorClip,
        layers: List<EditorClip>,
        baseClip: EditorClip
    ): String {
        fun relativeSeconds(ms: Long) = String.format(
            Locale.US,
            "%.4f",
            (ms - baseClip.timelineStartMs).coerceAtLeast(0L) / 1000.0
        )

        val start = relativeSeconds(layer.timelineStartMs)
        val end = relativeSeconds(layer.timelineEndMs.coerceAtMost(baseClip.timelineEndMs))
        val conditions = mutableListOf("gte(t,$start)", "lt(t,$end)")
        layers.filter {
            it.trackIndex > layer.trackIndex &&
                    it.timelineEndMs > layer.timelineStartMs &&
                    it.timelineStartMs < layer.timelineEndMs
        }.forEach { higherLayer ->
            val higherStart = relativeSeconds(higherLayer.timelineStartMs)
            val higherEnd =
                relativeSeconds(higherLayer.timelineEndMs.coerceAtMost(baseClip.timelineEndMs))
            conditions.add("not(gte(t,$higherStart)*lt(t,$higherEnd))")
        }
        return conditions.joinToString("*")
    }

    private fun com.moody.moodyvideoeditor.data.FilterState.toColorFilterValues() =
        ColorFilterValues(
            brightness = brightness,
            contrast = contrast,
            saturation = saturation,
            hue = hue,
            grayscale = grayscale,
            sepia = sepia,
            invert = invert,
            blur = blur,
            opacity = opacity
        )

    private fun ffmpegBlendName(key: String): String = when (key.lowercase()) {
        "multiply" -> "multiply"; "screen" -> "screen"; "overlay" -> "overlay"; "darken" -> "darken"; "lighten" -> "lighten"
        "hardlight" -> "hardlight"; "softlight" -> "softlight"; "colordodge" -> "dodge"; "colorburn" -> "burn"
        "vividlight" -> "vividlight"; "linearlight" -> "linearlight"; "pinlight" -> "pinlight"; "difference" -> "difference"
        "exclusion" -> "exclusion"; "subtract" -> "subtract"; "divide" -> "divide"; "add", "lineardodge" -> "addition"
        "hue" -> "hue"; "saturation" -> "saturation"; "color" -> "color"; "luminosity" -> "luminosity"; else -> "overlay"
    }

    private fun execute(args: List<String>, outputFile: File, expectedDurationMs: Long) {
        try {
            val argsArray = args.toTypedArray()
            Log.e("FFMPEG_ARGS", "========================================")
            Log.e("FFMPEG_ARGS", "FULL COMMAND:")
            Log.e("FFMPEG_ARGS", argsArray.joinToString(" "))
            Log.e("FFMPEG_ARGS", "========================================")

            // 🛡️ SAFETY: FFmpeg if() crash workaround — pre-check
            argsArray.forEachIndexed { idx, arg ->
                if (idx > 0 && argsArray[idx - 1] == "-filter_complex") {
                    if (arg.contains("if(")) {
                        onError(
                            "Internal error: keyframe expression uses if() which FFmpeg " +
                                    "cannot parse. This should never happen — please report."
                        )
                        return
                    }
                }
            }

            val session = FFmpegKit.executeWithArgumentsAsync(
                argsArray,
                { s ->
                    try {
                        Log.e("FFMPEG_RESULT", "Return code: ${s.returnCode}")
                        if (ReturnCode.isSuccess(s.returnCode)) {
                            if (outputFile.exists()) {
                                cleanupMaskSequences(); onSuccess(outputFile)
                            } else {
                                cleanupMaskSequences(); onError("Output not created")
                            }
                        } else if (ReturnCode.isCancel(s.returnCode)) {
                            cleanupMaskSequences(); onError("Export cancelled")
                        } else {
                            val output = s.allLogsAsString ?: ""
                            Log.e("FFMPEG_FAILED", "=== FFmpeg logs ===")
                            Log.e("FFMPEG_FAILED", output)
                            cleanupMaskSequences()
                            onError(
                                "FFmpeg error:\n${
                                    output.lines().filter { it.isNotBlank() }.takeLast(20)
                                        .joinToString("\n")
                                }"
                            )
                        }
                    } catch (e: Throwable) {
                        cleanupMaskSequences(); onError("Callback error: ${e.message}")
                    }
                },
                { _ -> },
                { stats ->
                    try {
                        val t = stats.time
                        if (t > 0L) {
                            onProgress(
                                (t.toDouble() / expectedDurationMs.coerceAtLeast(1L))
                                    .coerceIn(0.0, 0.95)
                                    .toFloat()
                            )
                        }
                    } catch (_: Throwable) {
                    }
                }
            )
            currentSession = session
        } catch (e: Throwable) {
            cleanupMaskSequences(); onError("Execute error: ${e.message}")
        }
    }

    fun cancel() {
        isCancelled = true
        try {
            currentSession?.let { FFmpegKit.cancel(it.sessionId) }
        } catch (_: Throwable) {
        }
    }

    private fun applyColorWheelExportPixel(
        r: Float,
        g: Float,
        b: Float,
        state: ColorWheelState
    ): Triple<Float, Float, Float> {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            return ColorWheelEngine.applyPixel(r, g, b, state)
        }

        val matrix = ColorWheelEngine.buildColorMatrix(state) ?: return Triple(r, g, b)
        val values = matrix.array
        return Triple(
            (values[0] * r + values[1] * g + values[2] * b + values[3] * 255f + values[4])
                .coerceIn(0f, 255f),
            (values[5] * r + values[6] * g + values[7] * b + values[8] * 255f + values[9])
                .coerceIn(0f, 255f),
            (values[10] * r + values[11] * g + values[12] * b + values[13] * 255f + values[14])
                .coerceIn(0f, 255f)
        )
    }

    @Synchronized
    private fun ensureColorWheelLut(state: ColorWheelState): File {
        val key = MessageDigest.getInstance("SHA-256")
            .digest(state.toString().toByteArray(Charsets.UTF_8)).take(12)
            .joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xff) }
        val file = File(context.cacheDir, "color-wheel-v11-preview-parity-$key.cube")
        if (file.exists() && file.length() > 0L) {
            Log.e("CW_LUT", "♻️ Reusing existing LUT: ${file.name} (${file.length()} bytes)")
            return file
        }

        val contents = buildString {
            appendLine("TITLE \"Moody Color Wheel\"")
            appendLine("LUT_3D_SIZE 33")
            appendLine("DOMAIN_MIN 0.0 0.0 0.0")
            appendLine("DOMAIN_MAX 1.0 1.0 1.0")
            for (b in 0 until 33) for (g in 0 until 33) for (r in 0 until 33) {
                val out = applyColorWheelExportPixel(
                    r * 255f / 32f,
                    g * 255f / 32f,
                    b * 255f / 32f,
                    state
                )
                append(
                    """%.6f %.6f %.6f
""".format(Locale.US, out.first / 255f, out.second / 255f, out.third / 255f)
                )
            }
        }
        try {
            file.writeText(contents); Log.e("CW_LUT", "✅ Generated LUT: ${file.absolutePath}")
        } catch (t: Throwable) {
            Log.e("CW_LUT", "❌ Failed LUT", t)
        }
        return file
    }

    private fun buildColorWheelFilter(
        state: ColorWheelState,
        startSec: Double? = null,
        endSec: Double? = null
    ): String {
        try {
            val lutFile = ensureColorWheelLut(state)
            if (lutFile.exists() && lutFile.length() > 10000L) {
                val safePath =
                    lutFile.absolutePath.replace("\\", "/").replace(":", "\\:").replace("'", "\\'")
                        .replace(" ", "\\ ")
                Log.e("CW_EXPORT", "✅ Using LUT: ${lutFile.absolutePath}")
                val enable = if (startSec != null && endSec != null) ":enable='between(t,${
                    "%.4f".format(
                        Locale.US,
                        startSec
                    )
                },${"%.4f".format(Locale.US, endSec)})'" else ""
                return "lut3d=file=$safePath:interp=tetrahedral$enable"
            }
        } catch (e: Throwable) {
            Log.e("CW_EXPORT", "❌ LUT generation failed", e)
        }

        Log.e("CW_EXPORT", "🔄 Falling back to colorbalance filter")
        val (sR, sG, sB) = ColorWheelEngine.getRGBShift(state.shadows)
        val (mR, mG, mB) = ColorWheelEngine.getRGBShift(state.midtones)
        val (hR, hG, hB) = ColorWheelEngine.getRGBShift(state.highlights)
        val loc = Locale.US
        fun f(v: Float): String = String.format(loc, "%.4f", v)
        val enable = if (startSec != null && endSec != null) ":enable='between(t,${
            "%.4f".format(
                Locale.US,
                startSec
            )
        },${"%.4f".format(Locale.US, endSec)})'" else ""
        return "colorbalance=rs=${f(sR)}:gs=${f(sG)}:bs=${f(sB)}:rm=${f(mR)}:gm=${f(mG)}:bm=${f(mB)}:rh=${
            f(
                hR
            )
        }:gh=${f(hG)}:bh=${f(hB)}:pl=1$enable"
    }

    private fun buildFullAdjustmentFilter(adj: AdjustmentData): String {
        val parts = mutableListOf<String>()
        if (ColorMatrixBuilder.hasRealTimeAdjustments(adj)) {
            val lutPath =
                ensureAdjustmentLut(adj).absolutePath.replace("\\", "\\\\").replace("'", "\\'")
            parts.add("format=rgb24")
            parts.add("lut3d=file='$lutPath':interp=tetrahedral")
            parts.add("format=yuv420p")
        }
        val spatial = FFmpegFilters.build(adj)
        if (spatial.isNotBlank()) parts.add(spatial)
        return parts.joinToString(",")
    }

    private fun adjustmentLayerEnableExpression(
        adjustmentClip: EditorClip,
        allClips: List<EditorClip>
    ): String {
        fun seconds(ms: Long) = String.format(Locale.US, "%.4f", ms / 1000.0)
        val conditions = mutableListOf(
            "gte(t,${seconds(adjustmentClip.timelineStartMs)})",
            "lt(t,${seconds(adjustmentClip.timelineEndMs)})"
        )
        allClips.filter {
            it.isAdjustmentClip &&
                    it.trackIndex > adjustmentClip.trackIndex &&
                    it.timelineEndMs > adjustmentClip.timelineStartMs &&
                    it.timelineStartMs < adjustmentClip.timelineEndMs
        }.forEach { higherLayer ->
            conditions.add(
                "not(gte(t,${seconds(higherLayer.timelineStartMs)})*" +
                        "lt(t,${seconds(higherLayer.timelineEndMs)}))"
            )
        }
        return conditions.joinToString("*")
    }

    @Synchronized
    private fun ensureAdjustmentLut(adj: AdjustmentData): File {
        val key = MessageDigest.getInstance("SHA-256").digest(adj.toString().toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xff) }
        val file = File(context.cacheDir, "adjustment-preview-parity-v4-$key.cube")
        if (file.exists() && file.length() > 0L) return file

        val lutSize = 65
        val lutMaxIndex = lutSize - 1
        val colorMatrix = ColorMatrixBuilder.build(adj)
        val contents = buildString {
            appendLine("TITLE \"Moody Adjustment Preview Parity\"")
            appendLine("LUT_3D_SIZE $lutSize")
            appendLine("DOMAIN_MIN 0.0 0.0 0.0")
            appendLine("DOMAIN_MAX 1.0 1.0 1.0")
            for (b in 0 until lutSize) for (g in 0 until lutSize) for (r in 0 until lutSize) {
                val out = AdjustmentPixelEngine.apply(
                    r * 255f / lutMaxIndex,
                    g * 255f / lutMaxIndex,
                    b * 255f / lutMaxIndex,
                    adj,
                    colorMatrix
                )
                append(
                    """%.6f %.6f %.6f
""".format(Locale.US, out.first / 255f, out.second / 255f, out.third / 255f)
                )
            }
        }
        file.writeText(contents)
        return file
    }
}
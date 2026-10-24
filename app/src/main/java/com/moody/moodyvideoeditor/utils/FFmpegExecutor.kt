package com.moody.moodyvideoeditor.utils

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

class FFmpegExecutor(
    private val context: Context,
    private val onProgress: (Float) -> Unit,
    private val onSuccess: (File) -> Unit,
    private val onError: (String) -> Unit
) {
    @Volatile
    private var isCancelled = false
    private var currentSession: FFmpegSession? = null
    private val audioCache = mutableMapOf<String, Boolean>()
    private val maskSequenceDirectories = mutableListOf<File>()

    private var xfadeFallback: (() -> Unit)? = null

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
                tiny.setPixel(0, 0, style.topLeft.toInt())
                tiny.setPixel(1, 0, style.topRight.toInt())
                tiny.setPixel(0, 1, style.bottomLeft.toInt())
                tiny.setPixel(1, 1, style.bottomRight.toInt())

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
            "-threads", encoderThreads.toString()
        )
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

            val needsLayered = needsLayeredCrop || clips.map { it.trackIndex }
                .distinct().size > 1 || maskSequences.isNotEmpty() || matteClips.isNotEmpty() ||
                    allExtraSequences.isNotEmpty() || adjustmentClips.isNotEmpty() ||
                    clips.any { ChromaEngine.isActive(it.chroma) || TransformApplier.hasAnimatedTransform(it) }

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
            val codecArgs = if (audioFormat == "m4a") listOf(
                "-c:a",
                "aac",
                "-b:a",
                "${bitrateKbps}k"
            ) else listOf("-c:a", "libmp3lame", "-b:a", "${bitrateKbps}k")

            args.add("-filter_complex"); args.add(filterParts.joinToString(";")); args.add("-map"); args.add(
                "[outa]"
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
        try {
            if (!outputDir.exists()) outputDir.mkdirs()
            val effectiveStart = rangeStartMs.coerceAtLeast(0L)
            val effectiveEnd = if (rangeEndMs > effectiveStart) rangeEndMs else durationMs
            val effectiveDurMs = (effectiveEnd - effectiveStart).coerceAtLeast(500L)
            val totalFrames = ((effectiveDurMs.toDouble() * fps / 1000.0).toInt()).coerceAtLeast(1)
            val ext = if (imageFormat == "jpeg") "jpg" else "png"
            val pattern = File(outputDir, "MoodyExport_frame_%05d.$ext").absolutePath
            val trimmedAllClips = trimClipsToRange(allClips, effectiveStart, effectiveEnd)

            val textClips = trimmedAllClips.filter { it.isTextClip || it.isStickerClip }
            val overlayImageClips =
                trimmedAllClips.filter { it.isVisualClip && it.type.startsWith("image/") && it.trackIndex > 0 }
            val overlayClips =
                trimmedAllClips.filter { it.isOverlayClip || (it.isEffectClip && it.effectState?.overlay != null) }
            val vizClips = trimmedAllClips.filter { it.isVisualizerClip }

            val textSequences =
                if (textClips.isNotEmpty() || overlayImageClips.isNotEmpty() || overlayClips.isNotEmpty()) {
                    TextBitmapRenderer.renderCombinedOverlays(
                        context,
                        textClips,
                        overlayImageClips,
                        overlayClips,
                        targetW,
                        targetH,
                        fps,
                        effectiveDurMs,
                        onProgress = { p -> onProgress(p * 0.25f) },
                        shouldCancel = { isCancelled },
                        imageFormat = imageFormat,
                        jpegQuality = jpegQuality
                    )
                } else emptyList()

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

            val allSequences = (textSequences + vizSequences).sortedBy { it.trackIndex }
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
                args.add("-framerate"); args.add(seq.fps.toString()); args.add("-start_number"); args.add(
                seq.startNumber.toString()
            )
                args.add("-i"); args.add(seq.pattern)
            }

            val filterParts = mutableListOf<String>()
            val baseVf = mutableListOf<String>()
            if (baseLocalFiles.isEmpty()) {
                baseVf.add("setsar=1")
            } else if (baseVisualClips.size == 1) {
                val clip = baseVisualClips[0]
                if (clip.speed != 1.0f && !isImage(clip)) baseVf.add("setpts=${1.0f / clip.speed}*PTS")
                baseVf.addAll(buildPerClipFilterChain(clip, allClips))
                baseVf.addAll(buildVisualTransformFilters(clip, targetW, targetH))
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
                filterParts.add("[$inIdx:v]format=rgba[seqsrc$idx]")
                filterParts.add(
                    "[$lastLabel][seqsrc$idx]overlay=0:0:enable='between(t,%.4f,%.4f)'[ov$idx]".format(
                        seq.startSec,
                        seq.endSec
                    )
                )
                lastLabel = "ov$idx"
            }
            filterParts.add("[$lastLabel]format=yuv420p[outv]")

            val finalOutv = WatermarkHelper.addWatermarkToFilter(context, filterParts, "outv", targetW, targetH)
            args.add("-filter_complex"); args.add(filterParts.joinToString(";"))
            args.add("-map"); args.add("[$finalOutv]"); args.add("-frames:v"); args.add(totalFrames.toString())
            args.add("-r"); args.add(fps.toString()); args.add("-threads"); args.add("0"); args.add(
                pattern
            )

            val session = FFmpegKit.executeWithArgumentsAsync(
                args.toTypedArray(),
                { s ->
                    if (isCancelled) onError("Sequence export cancelled")
                    else if (ReturnCode.isSuccess(s.returnCode)) {
                        val files = outputDir.listFiles()
                            ?.filter { it.name.startsWith("MoodyExport_frame_") } ?: emptyList()
                        if (files.isNotEmpty()) onSuccess(outputDir) else onError("No images created")
                    } else onError("Sequence failed:\n${(s.allLogsAsString ?: "").takeLast(1200)}")
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
            throw e
        } catch (e: Exception) {
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

    private fun clipHasAudio(clip: EditorClip): Boolean {
        val key = clip.uri.toString()
        audioCache[key]?.let { return it }
        val has = try {
            val extractor = android.media.MediaExtractor()
            try {
                extractor.setDataSource(context, clip.uri, null)
                var found = false
                for (i in 0 until extractor.trackCount) {
                    if ((extractor.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME)
                            ?: "").startsWith("audio/")
                    ) {
                        found = true; break
                    }
                }
                found
            } finally {
                extractor.release()
            }
        } catch (_: Throwable) {
            false
        }
        audioCache[key] = has
        return has
    }

    private fun addSequenceInputs(args: MutableList<String>, sequences: List<TextOverlaySequence>) {
        sequences.forEach { seq ->
            if (seq.startSec > 0.0001) {
                args.add("-itsoffset"); args.add("%.4f".format(seq.startSec))
            }
            args.add("-framerate"); args.add(seq.fps.toString()); args.add("-start_number"); args.add(
            seq.startNumber.toString()
        )
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
            filterParts.add("[$inIdx:v]format=rgba[seqsrc$idx]")
            filterParts.add(
                "[$lastLabel][seqsrc$idx]overlay=0:0:enable='between(t,%.4f,%.4f)'[ov$idx]".format(
                    seq.startSec,
                    seq.endSec
                )
            )
            lastLabel = "ov$idx"
        }
        filterParts.add("[$lastLabel]null[outv]")
        return "outv"
    }

    private fun enforceOutputDimensions(
        filterParts: MutableList<String>,
        inputLabel: String,
        targetW: Int,
        targetH: Int
    ): String {
        filterParts.add("[$inputLabel]scale=$targetW:$targetH:flags=lanczos,setsar=1[exportv]")
        // Watermark yahan add ho raha hai
        return WatermarkHelper.addWatermarkToFilter(context, filterParts, "exportv", targetW, targetH)
    }
    private fun buildVisualTransformFilters(
        clip: EditorClip,
        targetW: Int,
        targetH: Int,
        transparentPadding: Boolean = false,
        positionByOverlay: Boolean = false
    ): List<String> {
        val filters = mutableListOf<String>()
        if (transparentPadding) filters.add("format=rgba")

        // Crop is now handled via pre-rendered alpha masks (see CropAlphaRenderer)
        filters.add("scale=$targetW:$targetH:force_original_aspect_ratio=decrease:eval=frame")
        filters.add("pad=$targetW:$targetH:(ow-iw)/2:(oh-ih)/2:color=${if (transparentPadding) "black@0" else "black"}")

        val userScale = clip.scale.coerceIn(0.1f, 5f)
        val hasScaleTrack = clip.keyframes["scale"].orEmpty().isNotEmpty()
        if (hasScaleTrack) {
            val scaleExpr = KeyframeStore.escapeFfmpegExpression(
                KeyframeStore.ffmpegExpression(clip.keyframes, "scale", clip.scale * 100f)
            )
            filters.add("scale=w='iw*($scaleExpr/100)':h='ih*($scaleExpr/100)':eval=frame")
            filters.add("crop=w='min(iw,$targetW)':h='min(ih,$targetH)':x='(iw-ow)/2':y='(ih-oh)/2':eval=frame")
            filters.add("pad=$targetW:$targetH:(ow-iw)/2:(oh-ih)/2:color=${if (transparentPadding) "black@0" else "black"}")
        } else if (kotlin.math.abs(userScale - 1.0f) > 0.01f) {
            filters.add(
                "scale=iw*${String.format(Locale.US, "%.4f", userScale)}:ih*${
                    String.format(
                        Locale.US,
                        "%.4f",
                        userScale
                    )
                }"
            )
            if (userScale < 1f) filters.add("pad=$targetW:$targetH:(ow-iw)/2:(oh-ih)/2:color=${if (transparentPadding) "black@0" else "black"}")
            else filters.add("crop=$targetW:$targetH")
        }

        val hasRotationTrack = clip.keyframes["rotation"].orEmpty().isNotEmpty()
        if (hasRotationTrack) {
            val rotationExpr = KeyframeStore.escapeFfmpegExpression(
                KeyframeStore.ffmpegExpression(clip.keyframes, "rotation", clip.rotation)
            )
            filters.add(
                "rotate=angle='($rotationExpr)*PI/180':c=${if (transparentPadding) "black@0" else "black"}:" +
                        "ow=$targetW:oh=$targetH"
            )
        } else if (kotlin.math.abs(clip.rotation) > 0.1f) {
            filters.add(
                "rotate=${
                    String.format(
                        Locale.US,
                        "%.4f",
                        Math.toRadians(clip.rotation.toDouble())
                    )
                }:c=${if (transparentPadding) "black@0" else "black"}:ow=$targetW:oh=$targetH"
            )
        }

        val offsetXpx = (clip.offsetX * targetW).toInt();
        val offsetYpx = (clip.offsetY * targetH).toInt()
        if (!positionByOverlay && (offsetXpx != 0 || offsetYpx != 0)) {
            filters.add("crop=$targetW:$targetH:max(0\\,min(iw-$targetW\\,(iw-$targetW)/2+$offsetXpx)):max(0\\,min(ih-$targetH\\,(ih-$targetH)/2+$offsetYpx))")
        }
        if (transparentPadding) filters.add("format=rgba")
        filters.add("setsar=1")
        return filters
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

            val matteInputIndices = mutableMapOf<String, Int>()
            var nextMatteIdx = backgroundInputIdx + 1
            colorMettes.forEach { matte ->
                val pngFile = renderMatteToPng(matte.matteStyle, targetW, targetH)
                args.addAll(
                    listOf(
                        "-loop",
                        "1",
                        "-framerate",
                        fps.toString(),
                        "-t",
                        (matte.durationMs / 1000.0).coerceAtLeast(0.1).toString(),
                        "-i",
                        pngFile.absolutePath
                    )
                )
                matteInputIndices[matte.id] = nextMatteIdx++
            }

            val silenceInputIdx = nextMatteIdx
            args.addAll(
                listOf(
                    "-f",
                    "lavfi",
                    "-t",
                    durationSec.toString(),
                    "-i",
                    "anullsrc=r=44100:cl=stereo"
                )
            )
            val sequenceStartIdx = silenceInputIdx + 1
            val combinedSequences = sequences + extraSequences
            addSequenceInputs(args, combinedSequences)

            // 🆕 Crop alpha mask inputs
            val cropAlphaInputIndices = mutableMapOf<String, Int>()
            var nextCropAlphaIdx = sequenceStartIdx + combinedSequences.size + maskSequences.size
            cropAlphaSequences.forEach { seq ->
                if (seq.isStatic && seq.staticFile != null) {
                    args.addAll(listOf(
                        "-loop", "1", "-framerate", fps.toString(),
                        "-t", durationSec.toString(),
                        "-i", seq.staticFile.absolutePath
                    ))
                } else {
                    args.addAll(listOf(
                        "-framerate", seq.fps.toString(),
                        "-start_number", "1",
                        "-i", seq.pattern
                    ))
                }
                cropAlphaInputIndices[seq.clipId] = nextCropAlphaIdx++
            }

            val maskInputIndices = mutableMapOf<String, Int>()
            var nextMaskInputIdx = nextCropAlphaIdx
            maskSequences.forEach { seq ->
                if (seq.staticFile != null) args.addAll(
                    listOf(
                        "-loop",
                        "1",
                        "-framerate",
                        fps.toString(),
                        "-t",
                        durationSec.toString(),
                        "-i",
                        seq.staticFile.absolutePath
                    )
                )
                else args.addAll(
                    listOf(
                        "-framerate",
                        seq.fps.toString(),
                        "-start_number",
                        "1",
                        "-i",
                        seq.pattern
                    )
                )
                maskInputIndices[seq.clipId] = nextMaskInputIdx++
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
                        filters.addAll(buildPerClipFilterChain(clip, allClips))
                        filters.addAll(
                            buildVisualTransformFilters(
                                clip,
                                targetW,
                                targetH,
                                true,
                                positionByOverlay = !isBlendActive
                            )
                        )
                        clip.chroma?.takeIf { ChromaEngine.isActive(it) }?.let { chroma ->
                            filters.add("format=yuva420p")
                            filters.add(ChromaEngine.buildFfmpegFilter(chroma))
                            filters.add("format=rgba")
                        }
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

                        // 🆕 Apply crop alpha mask
                        val cropAlphaIdx = cropAlphaInputIndices[clip.id]
                        if (cropAlphaIdx != null) {
                            filterParts.add("[$cropAlphaIdx:v]format=gray,setpts=PTS-STARTPTS+${startSec}/TB[crop_pts${item.clipIdx}]")
                            filterParts.add("[$layerLabel]format=rgba[crop_layer_rgba${item.clipIdx}]")
                            filterParts.add("[crop_layer_rgba${item.clipIdx}][crop_pts${item.clipIdx}]alphamerge[crop_ready${item.clipIdx}]")
                            layerLabel = "crop_ready${item.clipIdx}"
                        }

                        val outLabel = "canvas_c${item.clipIdx}"
                        if (!isBlendActive) {
                            val baseX = 50f + clip.offsetX * 100f
                            val baseY = 50f + clip.offsetY * 100f
                            val localTimeExpression =
                                "(t-${String.format(Locale.US, "%.6f", startSec)})"
                            val x = KeyframeStore.escapeFfmpegExpression(
                                "((${KeyframeStore.ffmpegExpression(clip.keyframes, "x", baseX, localTimeExpression)}-50)*$targetW/100)"
                            )
                            val y = KeyframeStore.escapeFfmpegExpression(
                                "((${KeyframeStore.ffmpegExpression(clip.keyframes, "y", baseY, localTimeExpression)}-50)*$targetH/100)"
                            )
                            filterParts.add("[$canvasLabel][$layerLabel]overlay=x='$x':y='$y':eval=frame:eof_action=pass:repeatlast=0:enable='between(t,$startExpr,$endExpr)'[$outLabel]")
                        }
                        else {
                            val ffMode = ffmpegBlendName(blendKey);
                            val blendAlpha = String.format(
                                Locale.US,
                                "%.3f",
                                (clip.blendOpacity / 100f).coerceIn(0f, 1f)
                            )
                            filterParts.add("[$canvasLabel]format=rgba[blend_base_${item.clipIdx}]"); filterParts.add(
                                "[$layerLabel]format=rgba[blend_layer_rgba_${item.clipIdx}]"
                            )
                            filterParts.add("[blend_base_${item.clipIdx}][blend_layer_rgba_${item.clipIdx}]blend=all_mode=$ffMode:all_opacity=$blendAlpha:shortest=0[blend_out_${item.clipIdx}]")
                            filterParts.add("[blend_base_${item.clipIdx}][blend_out_${item.clipIdx}]overlay=0:0:eof_action=pass:repeatlast=0:enable='between(t,$startExpr,$endExpr)'[$outLabel]")
                        }
                        canvasLabel = outLabel
                    }

                    2 -> {
                        val seq = item.sequence ?: return@forEach
                        filterParts.add("[${item.inputIndex}:v]format=rgba[seqsrc_${item.sequenceIdx}]")
                        filterParts.add(
                            "[$canvasLabel][seqsrc_${item.sequenceIdx}]overlay=0:0:enable='between(t,${
                                String.format(
                                    Locale.US,
                                    "%.4f",
                                    seq.startSec
                                )
                            },${
                                String.format(
                                    Locale.US,
                                    "%.4f",
                                    seq.endSec
                                )
                            })'[canvas_s${item.sequenceIdx}]"
                        )
                        canvasLabel = "canvas_s${item.sequenceIdx}"
                    }

                    3 -> {
                        val adj = item.adjustmentClip ?: return@forEach
                        val hasAdj = !adj.adjustments.isDefault
                        if (hasAdj) {
                            val startSec = adj.timelineStartMs / 1000.0;
                            val endSec = adj.timelineEndMs / 1000.0
                            if (endSec > startSec + 0.01) {
                                val startExpr = String.format(Locale.US, "%.4f", startSec);
                                val endExpr = String.format(Locale.US, "%.4f", endSec)
                                val uid = "adj_${item.clipIdx}"
                                filterParts.add("[$canvasLabel]split=2[canvas_${uid}_base][canvas_${uid}_src]")

                                var currentChain = "canvas_${uid}_src"
                                val adjFilter = buildFullAdjustmentFilter(adj.adjustments)
                                if (adjFilter.isNotBlank()) {
                                    filterParts.add("[$currentChain]$adjFilter[canvas_${uid}_adjstep]")
                                    currentChain = "canvas_${uid}_adjstep"
                                }
                                if (currentChain != "canvas_${uid}_src") {
                                    filterParts.add("[canvas_${uid}_base][$currentChain]overlay=0:0:enable='between(t,$startExpr,$endExpr)'[canvas_${uid}_final]")
                                    canvasLabel = "canvas_${uid}_final"
                                }
                            }
                        }
                    }
                }
            }

            filterParts.add("[$canvasLabel]format=yuv420p[outv]")
            val audioLabels = mutableListOf<String>()

            orderedClips.forEachIndexed { index, (clip, _) ->
                if (isImage(clip) || !clipHasAudio(clip)) return@forEachIndexed
                val af = mutableListOf("asetpts=PTS-STARTPTS")
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
                filterParts.add("[$index:a]${af.joinToString(",")}[trackAudio$index]")
                audioLabels.add("[trackAudio$index]")
            }

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

            val finalVideo = enforceOutputDimensions(filterParts, "outv", targetW, targetH)
            args.addAll(
                listOf(
                    "-filter_complex",
                    filterParts.joinToString(";"),
                    "-map",
                    "[$finalVideo]",
                    "-map",
                    "[mixedAudio]",
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

            execute(args, outputFile)
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

            val audioInputIdx: Int
            if (img) {
                args.addAll(
                    listOf(
                        "-f",
                        "lavfi",
                        "-t",
                        totalDurSec.toString(),
                        "-i",
                        "anullsrc=r=44100:cl=stereo"
                    )
                ); audioInputIdx = 1
            } else audioInputIdx = 0

            val seqStartIdx = if (img) 2 else 1
            addSequenceInputs(args, sequences)

            val filterParts = mutableListOf<String>()
            val baseVf = mutableListOf<String>()
            if (clip.speed != 1.0f && !img) baseVf.add("setpts=${1.0f / clip.speed}*PTS")
            baseVf.addAll(buildPerClipFilterChain(clip, allClips))
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
            val finalVideoLabel = enforceOutputDimensions(filterParts, outVLabel, targetW, targetH)

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
            execute(args, outputFile)
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

            val audioInputIdx: Int
            if (img) {
                args.addAll(
                    listOf(
                        "-f",
                        "lavfi",
                        "-t",
                        totalDurSec.toString(),
                        "-i",
                        "anullsrc=r=44100:cl=stereo"
                    )
                ); audioInputIdx = 1
            } else audioInputIdx = 0

            val audioStartIdx = if (img) 2 else 1
            audioLocalFiles.forEach { f -> args.addAll(listOf("-i", f.absolutePath)) }

            val seqStartIdx = audioStartIdx + audioLocalFiles.size
            addSequenceInputs(args, sequences)

            val filterParts = mutableListOf<String>()
            val baseVf = mutableListOf<String>()
            if (clip.speed != 1.0f && !img) baseVf.add("setpts=${1.0f / clip.speed}*PTS")
            baseVf.addAll(buildPerClipFilterChain(clip, allClips))
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
            val finalVideoLabel = enforceOutputDimensions(filterParts, outVLabel, targetW, targetH)

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
            execute(args, outputFile)
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
        totalDurationMs: Long
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
            totalDurationMs
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
                if (!clipHasAudio(clip)) {
                    args.addAll(
                        listOf(
                            "-f",
                            "lavfi",
                            "-t",
                            (clip.durationMs / 1000.0).toString(),
                            "-i",
                            "anullsrc=r=44100:cl=stereo"
                        )
                    ); silentIdx[idx] = nextIdx++
                }
            }

            val audioStartIdx = nextIdx
            audioLocalFiles.forEach { f -> args.addAll(listOf("-i", f.absolutePath)); nextIdx++ }
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
                vf.addAll(buildPerClipFilterChain(clip, allClips)); vf.addAll(
                buildVisualTransformFilters(clip, targetW, targetH)
            )
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
            var currentLabel = "nv0"
            for (i in 1 until clips.size) {
                val step = transitionSteps[i - 1]
                filterParts.add("[$currentLabel][nv$i]xfade=transition=${step.ffmpegTransition}:duration=${step.durationMs / 1000.0}:offset=${step.offsetMs / 1000.0}[xfd$i]")
                currentLabel = "xfd$i"
            }

            clips.forEachIndexed { idx, clip ->
                val srcIdx = if (clipHasAudio(clip)) idx else (silentIdx[idx] ?: idx)
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
            val finalVideoLabel = enforceOutputDimensions(filterParts, outVLabel, targetW, targetH)
            val totalDurSec = (totalDurationMs / 1000.0).coerceAtLeast(0.1)

            args.addAll(
                listOf(
                    "-filter_complex_threads",
                    "1",
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
            args.addAll(buildFastVideoArgs(bitrateKbps, targetW, targetH, 1))
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

            xfadeFallback = {
                exportWithConcat(
                    clips,
                    allClips,
                    localFiles,
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
            execute(args, outputFile)
        } catch (e: Throwable) {
            xfadeFallback = null
            exportWithConcat(
                clips,
                allClips,
                localFiles,
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
            xfadeFallback = null
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

            val imageAudioInputs = mutableMapOf<Int, Int>();
            var nextInputIdx = clips.size
            clips.forEachIndexed { idx, clip ->
                if (isImage(clip)) {
                    args.addAll(
                        listOf(
                            "-f",
                            "lavfi",
                            "-t",
                            (clip.durationMs / 1000.0).toString(),
                            "-i",
                            "anullsrc=r=44100:cl=stereo"
                        )
                    ); imageAudioInputs[idx] = nextInputIdx++
                }
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
                vf.addAll(buildPerClipFilterChain(clip, allClips)); vf.addAll(
                buildVisualTransformFilters(clip, targetW, targetH)
            )
                vf.addAll(listOf("fps=$fps", "format=yuv420p"))
                filterParts.add("[$idx:v]${vf.joinToString(",")}[v$idx]")

                val aIdx = imageAudioInputs[idx] ?: idx
                val af = mutableListOf<String>()
                if (isImage(clip)) af.add("asetpts=PTS-STARTPTS") else af.addAll(
                    listOf(
                        "atrim=start=${clip.sourceStartMs / 1000.0}:duration=$durationSec",
                        "asetpts=PTS-STARTPTS"
                    )
                )
                if (clip.speed != 1.0f && !isImage(clip)) af.add(
                    "atempo=${
                        clip.speed.coerceIn(
                            0.5f,
                            2.0f
                        )
                    }"
                )
                if (clip.audioFx != "none") {
                    val fx = AudioEngine.buildAudioFilter(clip.audioFx, clip.audioFxIntensity)
                    if (fx.isNotBlank()) af.add(fx)
                }
                af.add("aresample=44100")
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
            val finalVideoLabel = enforceOutputDimensions(filterParts, outVLabel, targetW, targetH)
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
            execute(args, outputFile)
        } catch (e: Throwable) {
            onError("Concat export: ${e.message}")
        }
    }

    private fun buildPerClipFilterChain(
        clip: EditorClip,
        allClips: List<EditorClip>
    ): List<String> {
        val filters = mutableListOf<String>()

        // Filter Layers Above
        allClips.filter { it.isFilterLayerClip && it.trackIndex > clip.trackIndex && it.timelineEndMs > clip.timelineStartMs && it.timelineStartMs < clip.timelineEndMs }
            .sortedBy { it.trackIndex }.forEach { layer ->
                val cfv = ColorFilterValues(
                    layer.filters.brightness,
                    layer.filters.contrast,
                    layer.filters.saturation,
                    layer.filters.hue,
                    layer.filters.grayscale,
                    layer.filters.sepia,
                    layer.filters.invert,
                    layer.filters.blur,
                    layer.filters.opacity
                )
                val str = colorFilterValuesToFfmpeg(cfv)
                if (str.isNotBlank()) filters.add(str)
            }

        // Effect Layers Above
        allClips.filter { it.isEffectClip && it.trackIndex > clip.trackIndex && it.timelineEndMs > clip.timelineStartMs && it.timelineStartMs < clip.timelineEndMs }
            .sortedBy { it.trackIndex }.forEach { effClip ->
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

                    effClip.effectState?.motion?.let { add(motionToFfmpeg(it)) }
                    effClip.effectState?.filters?.let { add(colorFilterValuesToFfmpeg(it)) }
                    effClip.effectState?.animatedColor?.let {
                        val list = animatedColorToFfmpeg(it)
                        if (list.isNotEmpty()) list.forEach { f -> filters.add("$f$enable") }
                        else add(
                            colorFilterValuesToFfmpeg(
                                com.moody.moodyvideoeditor.utils.AnimatedColorEngine.compute(
                                    it,
                                    ((start + end) / 2.0).toFloat()
                                )
                            )
                        )
                    }
                    effClip.effectState?.blur?.let {
                        filters.addAll(
                            buildAnimatedBlurFilters(
                                it,
                                start,
                                end,
                                "blur${kotlin.math.abs(effClip.id.hashCode()) % 9999}"
                            )
                        )
                    }
                    effClip.effectState?.edgeGlow?.let {
                        if (it.intensity > 1f) add(
                            buildEdgeGlowDrawboxFilter(it)
                        )
                    }
                }
            }

        // 🆕 COLOR WHEELS FROM ADJUSTMENT LAYERS ABOVE — PER CLIP
        // Full range RGB force karo taaki LUT preview jaisa exactly kaam kare
        allClips.filter { e ->
            e.isAdjustmentClip &&
                    e.colorWheel.hasAnyChange &&
                    e.trackIndex > clip.trackIndex &&
                    e.timelineEndMs > clip.timelineStartMs &&
                    e.timelineStartMs < clip.timelineEndMs
        }.sortedBy { it.trackIndex }.forEach { adjLayer ->
            val relStartSec =
                (adjLayer.timelineStartMs - clip.timelineStartMs).coerceAtLeast(0L) / 1000.0
            val relEndSec =
                (adjLayer.timelineEndMs - clip.timelineStartMs).coerceAtMost(clip.durationMs) / 1000.0
            val coversWhole = relStartSec < 0.01 && relEndSec > (clip.durationMs / 1000.0) - 0.01

            filters.add("scale=in_color_matrix=bt709:in_range=tv:out_color_matrix=bt709:out_range=pc:flags=bilinear")
            filters.add("format=rgb24")

            if (coversWhole) filters.add(buildColorWheelFilter(adjLayer.colorWheel))
            else filters.add(buildColorWheelFilter(adjLayer.colorWheel, relStartSec, relEndSec))

            filters.add("scale=in_range=pc:out_range=tv")
            filters.add("format=yuv420p")
        }

        // Clip's own color wheel
        if (clip.colorWheel.hasAnyChange) {
            filters.add("scale=in_color_matrix=bt709:in_range=tv:out_color_matrix=bt709:out_range=pc:flags=bilinear")
            filters.add("format=rgb24")
            filters.add(buildColorWheelFilter(clip.colorWheel))
            filters.add("scale=in_range=pc:out_range=tv")
            filters.add("format=yuv420p")
        }

        // Clip Filters
        val f = clip.filters
        if (f.brightness != 100f || f.contrast != 100f || f.saturation != 100f) {
            val eq = mutableListOf<String>()
            if (f.brightness != 100f) eq.add(
                "brightness=${
                    ((f.brightness - 100f) / 100f).coerceIn(
                        -1f,
                        1f
                    )
                }"
            )
            if (f.contrast != 100f) eq.add("contrast=${(f.contrast / 100f).coerceIn(0f, 2f)}")
            if (f.saturation != 100f) eq.add("saturation=${(f.saturation / 100f).coerceIn(0f, 3f)}")
            if (eq.isNotEmpty()) filters.add("eq=${eq.joinToString(":")}")
        }
        if (f.hue != 0f) filters.add("hue=h=${f.hue}")
        if (f.grayscale > 0f) filters.add("format=gray")
        if (f.invert > 0f) filters.add("negate")

        // Clip Adjustments
        if (!clip.adjustments.isDefault) {
            val adjF = buildFullAdjustmentFilter(clip.adjustments)
            if (adjF.isNotBlank()) filters.add(adjF)
        }

        // Light Leaks
        allClips.filter { it.isEffectClip && it.effectState?.lightLeak != null && it.trackIndex > clip.trackIndex && it.timelineEndMs > clip.timelineStartMs && it.timelineStartMs < clip.timelineEndMs }
            .sortedBy { it.trackIndex }.forEach { leak ->
                val filter = LightLeakEngine.buildFfmpegFilter(
                    com.moody.moodyvideoeditor.data.LightLeakState(
                        presetKey = "",
                        config = leak.effectState!!.lightLeak!!,
                        opacity = 100f
                    )
                )
                if (filter.isNotBlank()) filters.add(filter)
            }

        return filters
    }

    private fun buildEdgeGlowDrawboxFilter(glow: com.moody.moodyvideoeditor.data.EdgeGlowConfig): String {
        return try {
            val colorLong = glow.color
            val r = ((colorLong shr 16) and 0xFF).toInt()
            val g = ((colorLong shr 8) and 0xFF).toInt()
            val b = (colorLong and 0xFF).toInt()
            val colorSpec = "${String.format(Locale.US, "0x%02X%02X%02X", r, g, b)}@${
                String.format(
                    Locale.US,
                    "%.3f",
                    (glow.intensity / 100f).coerceIn(0f, 1f)
                )
            }"
            val t = glow.radius.coerceIn(2f, 80f).toInt()
            listOf(
                "drawbox=x=0:y=0:w=iw:h=$t:color=$colorSpec:t=fill",
                "drawbox=x=0:y=ih-$t:w=iw:h=$t:color=$colorSpec:t=fill",
                "drawbox=x=0:y=0:w=$t:h=ih:color=$colorSpec:t=fill",
                "drawbox=x=iw-$t:y=0:w=$t:h=ih:color=$colorSpec:t=fill"
            ).joinToString(",")
        } catch (_: Throwable) {
            ""
        }
    }

    private fun applyAudioEffectLayers(
        filterParts: MutableList<String>,
        allClips: List<EditorClip>,
        rangeStartMs: Long,
        baseLabel: String,
        outLabel: String
    ): Boolean {
        val layers = allClips.filter { it.isAudioEffectClip }.sortedBy { it.timelineStartMs }
        if (layers.isEmpty()) return false

        val n = layers.size
        val splitLabels = (0..n).map { "fxsplt$it" }
        filterParts.add("[$baseLabel]asplit=${n + 1}${splitLabels.joinToString("") { "[$it]" }}")

        val baseChain = layers.mapNotNull { layer ->
            val s = ((layer.timelineStartMs - rangeStartMs).coerceAtLeast(0L)) / 1000.0
            val e = (layer.timelineEndMs - rangeStartMs) / 1000.0
            if (e - s < 0.02) null else "volume=enable=between(t,%.3f,%.3f):volume=0".format(s, e)
        }.joinToString(",").ifEmpty { "anull" }
        filterParts.add("[${splitLabels[0]}]$baseChain[fxn0]")

        layers.forEachIndexed { i, layer ->
            val s = ((layer.timelineStartMs - rangeStartMs).coerceAtLeast(0L)) / 1000.0
            val e = (layer.timelineEndMs - rangeStartMs) / 1000.0
            if (e - s < 0.02) {
                filterParts.add("[${splitLabels[i + 1]}]anull[fxn${i + 1}]"); return@forEachIndexed
            }

            val fx = AudioEngine.buildAudioFilter(
                if (layer.isAudioFxClip) layer.audioFx else layer.soundFx,
                if (layer.isAudioFxClip) layer.audioFxIntensity else layer.soundFxIntensity
            )
            filterParts.add(
                "[${splitLabels[i + 1]}]${
                    if (fx.isNotBlank()) "volume=enable=not(between(t,%.3f,%.3f)):volume=0,$fx".format(
                        s,
                        e
                    ) else "volume=enable=not(between(t,%.3f,%.3f)):volume=0".format(s, e)
                }[fxn${i + 1}]"
            )
        }

        filterParts.add("${(0..n).joinToString("") { "[fxn$it]" }}amix=inputs=${n + 1}:duration=longest[$outLabel]")
        return true
    }

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

    private fun colorFilterValuesToFfmpeg(cf: ColorFilterValues): String {
        val parts = mutableListOf<String>()
        if (cf.brightness != 100f) parts.add(
            "lutrgb=r='val*${
                String.format(
                    Locale.US,
                    "%.4f",
                    (cf.brightness / 100f).coerceIn(0f, 3f)
                )
            }':g='val*${
                String.format(
                    Locale.US,
                    "%.4f",
                    (cf.brightness / 100f).coerceIn(0f, 3f)
                )
            }':b='val*${String.format(Locale.US, "%.4f", (cf.brightness / 100f).coerceIn(0f, 3f))}'"
        )
        if (cf.contrast != 100f) parts.add(
            "eq=contrast=${
                String.format(
                    Locale.US,
                    "%.4f",
                    (cf.contrast / 100f).coerceIn(0f, 3f)
                )
            }"
        )
        if (cf.saturation != 100f) parts.add(
            "eq=saturation=${
                String.format(
                    Locale.US,
                    "%.4f",
                    (cf.saturation / 100f).coerceIn(0f, 3f)
                )
            }"
        )
        if (cf.hue != 0f) parts.add("hue=h=${cf.hue}")
        if (cf.grayscale > 0f) parts.add(
            "eq=saturation=${
                String.format(
                    Locale.US,
                    "%.4f",
                    (1f - (cf.grayscale / 100f).coerceIn(0f, 1f))
                )
            }"
        )
        if (cf.sepia > 0f) {
            val a = (cf.sepia / 100f).coerceIn(0f, 1f);
            val f = Locale.US
            val sr = 0.393f;
            val sg = 0.769f;
            val sb = 0.189f;
            val mr = 0.349f;
            val mg = 0.686f;
            val mb = 0.168f;
            val hr = 0.272f;
            val hg = 0.534f;
            val hb = 0.131f
            parts.add(
                "colorchannelmixer=rr=${
                    "%.4f".format(
                        f,
                        (1f - a) + a * sr
                    )
                }:rg=${"%.4f".format(f, a * sg)}:rb=${"%.4f".format(f, a * sb)}:ra=0:gr=${
                    "%.4f".format(
                        f,
                        a * mr
                    )
                }:gg=${"%.4f".format(f, (1f - a) + a * mg)}:gb=${
                    "%.4f".format(
                        f,
                        a * mb
                    )
                }:ga=0:br=${"%.4f".format(f, a * hr)}:bg=${"%.4f".format(f, a * hg)}:bb=${
                    "%.4f".format(
                        f,
                        (1f - a) + a * hb
                    )
                }:ba=0"
            )
        }
        if (cf.invert > 0f) parts.add("negate")
        return parts.joinToString(",")
    }

    private fun ffmpegBlendName(key: String): String = when (key.lowercase()) {
        "multiply" -> "multiply"; "screen" -> "screen"; "overlay" -> "overlay"; "darken" -> "darken"; "lighten" -> "lighten"
        "hardlight" -> "hardlight"; "softlight" -> "softlight"; "colordodge" -> "dodge"; "colorburn" -> "burn"
        "vividlight" -> "vividlight"; "linearlight" -> "linearlight"; "pinlight" -> "pinlight"; "difference" -> "difference"
        "exclusion" -> "exclusion"; "subtract" -> "subtract"; "divide" -> "divide"; "add", "lineardodge" -> "addition"
        "hue" -> "hue"; "saturation" -> "saturation"; "color" -> "color"; "luminosity" -> "luminosity"; else -> "overlay"
    }

    private fun execute(args: List<String>, outputFile: File) {
        try {
            val argsArray = args.toTypedArray()
            Log.e("FFMPEG_ARGS", "========================================")
            Log.e("FFMPEG_ARGS", "FULL COMMAND:")
            Log.e("FFMPEG_ARGS", argsArray.joinToString(" "))
            Log.e("FFMPEG_ARGS", "========================================")

            val session = FFmpegKit.executeWithArgumentsAsync(
                argsArray,
                { s ->
                    try {
                        Log.e("FFMPEG_RESULT", "Return code: ${s.returnCode}")
                        if (ReturnCode.isSuccess(s.returnCode)) {
                            xfadeFallback = null
                            if (outputFile.exists()) {
                                cleanupMaskSequences(); onSuccess(outputFile)
                            } else {
                                cleanupMaskSequences(); onError("Output not created")
                            }
                        } else if (ReturnCode.isCancel(s.returnCode)) {
                            xfadeFallback =
                                null; cleanupMaskSequences(); onError("Export cancelled")
                        } else {
                            val output = s.allLogsAsString ?: ""
                            Log.e("FFMPEG_FAILED", "=== FFmpeg logs ===")
                            Log.e("FFMPEG_FAILED", output)
                            val fb = xfadeFallback
                            if (fb != null) {
                                xfadeFallback = null
                                Log.e("FFMPEG_TRANS", "xfade failed, fallback to concat")
                                try {
                                    fb.invoke()
                                } catch (e: Throwable) {
                                    onError("Export failed: ${e.message}")
                                }
                            } else {
                                cleanupMaskSequences()
                                onError(
                                    "FFmpeg error:\n${
                                        output.lines().filter { it.isNotBlank() }.takeLast(20)
                                            .joinToString("\n")
                                    }"
                                )
                            }
                        }
                    } catch (e: Throwable) {
                        cleanupMaskSequences(); onError("Callback error: ${e.message}")
                    }
                },
                { _ -> },
                { stats ->
                    try {
                        val t = stats.time; if (t > 0) onProgress(
                            ((t / 10000.0).coerceIn(
                                0.0,
                                0.95
                            )).toFloat()
                        )
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

    @Synchronized
    private fun ensureColorWheelLut(state: ColorWheelState): File {
        val key = MessageDigest.getInstance("SHA-256")
            .digest(state.toString().toByteArray(Charsets.UTF_8)).take(12)
            .joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xff) }
        val file = File(context.cacheDir, "color-wheel-v10-exact-$key.cube")
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
                val out = ColorWheelEngine.applyPixel(
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
        val std = FFmpegFilters.build(adj)
        if (std.isNotBlank()) parts.add(std)

        if (adj.reds != 0f || adj.oranges != 0f || adj.yellows != 0f || adj.greens != 0f || adj.cyans != 0f || adj.blues != 0f || adj.purples != 0f || adj.magentas != 0f || adj.skinTones != 0f) {
            val lutPath =
                ensureAdjustmentLut(adj).absolutePath.replace("\\", "\\\\").replace("'", "\\'")
            parts.add("format=rgb24"); parts.add("lut3d=file='$lutPath':interp=tetrahedral"); parts.add(
                "format=yuv420p"
            )
        }
        return parts.joinToString(",")
    }

    @Synchronized
    private fun ensureAdjustmentLut(adj: AdjustmentData): File {
        val keyData =
            "${adj.reds}_${adj.oranges}_${adj.yellows}_${adj.greens}_${adj.cyans}_${adj.blues}_${adj.purples}_${adj.magentas}_${adj.skinTones}"
        val key = MessageDigest.getInstance("SHA-256").digest(keyData.toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xff) }
        val file = File(context.cacheDir, "adjustment-hsl-v3-$key.cube")
        if (file.exists() && file.length() > 0L) return file

        val contents = buildString {
            appendLine("TITLE \"Moody Adjustment HSL\""); appendLine("LUT_3D_SIZE 33")
            appendLine("DOMAIN_MIN 0.0 0.0 0.0"); appendLine("DOMAIN_MAX 1.0 1.0 1.0")
            for (b in 0 until 33) for (g in 0 until 33) for (r in 0 until 33) {
                val out = applyAdjustmentPixel(r * 255f / 32f, g * 255f / 32f, b * 255f / 32f, adj)
                append(
                    """%.6f %.6f %.6f
""".format(Locale.US, out.first / 255f, out.second / 255f, out.third / 255f)
                )
            }
        }
        file.writeText(contents)
        return file
    }

    private fun applyAdjustmentPixel(
        rIn: Float,
        gIn: Float,
        bIn: Float,
        adj: AdjustmentData
    ): Triple<Float, Float, Float> {
        val rn = rIn / 255f;
        val gn = gIn / 255f;
        val bn = bIn / 255f
        val maxC = maxOf(rn, gn, bn);
        val minC = minOf(rn, gn, bn)
        var h = 0f;
        var s = 0f;
        val v = maxC
        if (maxC != minC) {
            val d = maxC - minC; s = d / maxC
            h = when (maxC) {
                rn -> ((gn - bn) / d + (if (gn < bn) 6f else 0f)) * 60f; gn -> ((bn - rn) / d + 2f) * 60f; else -> ((rn - gn) / d + 4f) * 60f
            }
        }
        var lumDelta = 0f;
        var satDelta = 0f
        if (adj.reds != 0f) {
            val w = hueWeight(
                h,
                0f,
                30f,
                s
            ); lumDelta += (adj.reds / 100f) * w; satDelta += (adj.reds / 100f) * w
        }
        if (adj.oranges != 0f) {
            val w = hueWeight(
                h,
                30f,
                25f,
                s
            ); lumDelta += (adj.oranges / 100f) * w; satDelta += (adj.oranges / 100f) * w
        }
        if (adj.yellows != 0f) {
            val w = hueWeight(
                h,
                60f,
                30f,
                s
            ); lumDelta += (adj.yellows / 100f) * w; satDelta += (adj.yellows / 100f) * w
        }
        if (adj.greens != 0f) {
            val w = hueWeight(
                h,
                120f,
                45f,
                s
            ); lumDelta += (adj.greens / 100f) * w; satDelta += (adj.greens / 100f) * w
        }
        if (adj.cyans != 0f) {
            val w = hueWeight(
                h,
                180f,
                30f,
                s
            ); lumDelta += (adj.cyans / 100f) * w; satDelta += (adj.cyans / 100f) * w
        }
        if (adj.blues != 0f) {
            val w = hueWeight(
                h,
                240f,
                30f,
                s
            ); lumDelta += (adj.blues / 100f) * w; satDelta += (adj.blues / 100f) * w
        }
        if (adj.purples != 0f) {
            val w = hueWeight(
                h,
                280f,
                25f,
                s
            ); lumDelta += (adj.purples / 100f) * w; satDelta += (adj.purples / 100f) * w
        }
        if (adj.magentas != 0f) {
            val w = hueWeight(
                h,
                300f,
                25f,
                s
            ); lumDelta += (adj.magentas / 100f) * w; satDelta += (adj.magentas / 100f) * w
        }
        if (adj.skinTones != 0f) {
            val w = hueWeight(
                h,
                25f,
                20f,
                s
            ); lumDelta += (adj.skinTones / 100f) * w; satDelta += (adj.skinTones / 100f) * w
        }

        val newV = (v + lumDelta * 0.85f).coerceIn(0f, 1f);
        val newS = (s + satDelta * 0.60f).coerceIn(0f, 1f)
        val c = newV * newS;
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f));
        val m = newV - c
        var r2 = 0f;
        var g2 = 0f;
        var b2 = 0f
        when {
            h < 60f -> {
                r2 = c; g2 = x; b2 = 0f
            }; h < 120f -> {
            r2 = x; g2 = c; b2 = 0f
        }

            h < 180f -> {
                r2 = 0f; g2 = c; b2 = x
            }; h < 240f -> {
            r2 = 0f; g2 = x; b2 = c
        }

            h < 300f -> {
                r2 = x; g2 = 0f; b2 = c
            }; else -> {
            r2 = c; g2 = 0f; b2 = x
        }
        }
        return Triple(
            (r2 + m).coerceIn(0f, 1f) * 255f,
            (g2 + m).coerceIn(0f, 1f) * 255f,
            (b2 + m).coerceIn(0f, 1f) * 255f
        )
    }

    private fun hueWeight(hue: Float, targetHue: Float, range: Float, sat: Float): Float {
        var diff = kotlin.math.abs(hue - targetHue)
        diff = kotlin.math.min(diff, 360f - diff)
        return (1f - smoothstep(range * 0.25f, range, diff)) * smoothstep(0.02f, 0.15f, sat)
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
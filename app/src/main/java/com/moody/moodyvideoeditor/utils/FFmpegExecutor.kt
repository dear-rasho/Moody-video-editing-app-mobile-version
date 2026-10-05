package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.moody.moodyvideoeditor.data.ColorFilterValues
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.MotionConfig
import com.moody.moodyvideoeditor.data.TransitionLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

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

    private var xfadeFallback: (() -> Unit)? = null

    // ═══════════════════════════════════════════════════════════
    //  FAST VIDEO ARGS
    // ═══════════════════════════════════════════════════════════

    private fun buildFastVideoArgs(
        bitrateKbps: Int,
        targetW: Int,
        targetH: Int
    ): List<String> {
        return listOf(
            "-c:v", "libx264",
            "-preset", "ultrafast",
            "-tune", "fastdecode",
            "-b:v", "${bitrateKbps}k",
            "-s:v", "${targetW}x${targetH}",
            "-aspect", "$targetW:$targetH",
            "-pix_fmt", "yuv420p",
            "-threads", "0"
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  MAIN EXPORT ENTRY
    // ═══════════════════════════════════════════════════════════

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
        explicitDurationMs: Long = 0L
    ) {
        isCancelled = false
        try {
            if (clips.isEmpty() && audioOnlyClips.isEmpty()) {
                onError("No clips to export")
                return
            }

            val computedTotal = (
                    clips.maxOfOrNull { it.timelineEndMs } ?: 0L
                    ).coerceAtLeast(
                    audioOnlyClips.maxOfOrNull { it.timelineEndMs } ?: 0L
                )
            val totalDurationMs = if (explicitDurationMs > 0L)
                explicitDurationMs else computedTotal

            val (localFiles, audioLocalFiles) = withContext(Dispatchers.IO) {
                coroutineContext.ensureActive()
                val videoFiles = mutableListOf<File>()
                for (clip in clips) {
                    coroutineContext.ensureActive()
                    if (isCancelled) throw CancellationException("Export cancelled")
                    val file = copyUriToCache(clip.uri, "clip_${clip.id}.mp4")
                        ?: throw IllegalStateException("Could not read file: ${clip.name}")
                    videoFiles.add(file)
                }

                val audioFiles = mutableListOf<File>()
                for (clip in audioOnlyClips) {
                    coroutineContext.ensureActive()
                    if (isCancelled) throw CancellationException("Export cancelled")
                    val file = copyUriToCache(clip.uri, "audio_${clip.id}.mp3")
                        ?: throw IllegalStateException("Could not read audio: ${clip.name}")
                    audioFiles.add(file)
                }
                videoFiles to audioFiles
            }

            if (clips.map { it.trackIndex }.distinct().size > 1) {
                exportLayeredTracks(
                    clips = clips,
                    allClips = allClips,
                    localFiles = localFiles,
                    audioOnlyClips = audioOnlyClips,
                    audioLocalFiles = audioLocalFiles,
                    outputFile = outputFile,
                    targetW = targetW,
                    targetH = targetH,
                    fps = fps,
                    bitrateKbps = bitrateKbps,
                    sequences = textSequences,
                    totalDurationMs = totalDurationMs
                )
                return
            }

            when {
                localFiles.size == 1 && audioLocalFiles.isEmpty() -> {
                    exportSingleClip(
                        clips[0], allClips, localFiles[0], outputFile,
                        targetW, targetH, fps, bitrateKbps, textSequences,
                        totalDurationMs
                    )
                }

                localFiles.size == 1 && audioLocalFiles.isNotEmpty() -> {
                    exportSingleClipWithAudio(
                        clips[0], allClips, localFiles[0],
                        audioOnlyClips, audioLocalFiles,
                        outputFile, targetW, targetH, fps, bitrateKbps,
                        textSequences, totalDurationMs
                    )
                }

                localFiles.size >= 2 -> {
                    exportMultipleClips(
                        clips, allClips, localFiles,
                        audioOnlyClips, audioLocalFiles,
                        outputFile, targetW, targetH, fps, bitrateKbps,
                        textSequences, totalDurationMs
                    )
                }

                else -> {
                    onError("No visual clips")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FFMPEG", "Export crash", e)
            onError("Export failed: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  AUDIO ONLY EXPORT
    // ═══════════════════════════════════════════════════════════

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
                it.isAudio &&
                        !it.isAudioEffectClip &&
                        it.uri.toString().isNotBlank() &&
                        it.uri != Uri.EMPTY
            }

            if (audioClips.isEmpty()) {
                onError("No audio clips found in timeline")
                return
            }

            Log.e("FFMPEG_AUDIO", "Exporting ${audioClips.size} audio clips to $audioFormat")

            val effectiveStart = rangeStartMs.coerceAtLeast(0L)
            val effectiveEnd = if (rangeEndMs > effectiveStart) rangeEndMs
            else durationMs
            val effectiveDurMs = (effectiveEnd - effectiveStart).coerceAtLeast(500L)

            val audioLocalFiles = withContext(Dispatchers.IO) {
                val files = mutableListOf<File>()
                for (clip in audioClips) {
                    coroutineContext.ensureActive()
                    if (isCancelled) throw CancellationException("Export cancelled")
                    val file = copyUriToCache(clip.uri, "audio_${clip.id}.mp3")
                        ?: throw IllegalStateException("Could not read: ${clip.name}")
                    files.add(file)
                }
                files
            }

            val args = mutableListOf<String>()
            args.add("-y")

            args.add("-f"); args.add("lavfi")
            args.add("-t"); args.add((effectiveDurMs / 1000.0).toString())
            args.add("-i"); args.add("anullsrc=r=44100:cl=stereo")

            audioLocalFiles.forEach { f ->
                args.add("-i"); args.add(f.absolutePath)
            }

            val filterParts = mutableListOf<String>()
            val audioLabels = mutableListOf<String>()

            filterParts.add("[0:a]aresample=44100[basea]")
            audioLabels.add("[basea]")

            audioClips.forEachIndexed { idx, clip ->
                val inputIdx = idx + 1
                val clipStart = clip.timelineStartMs
                val clipEnd = clip.timelineEndMs

                if (clipEnd <= effectiveStart || clipStart >= effectiveEnd) {
                    return@forEachIndexed
                }

                val leftCut = (effectiveStart - clipStart).coerceAtLeast(0L)
                val rightCut = (clipEnd - effectiveEnd).coerceAtLeast(0L)
                val speed = clip.speed.coerceAtLeast(0.01f)
                val srcStartMs = clip.sourceStartMs + (leftCut * speed).toLong()
                val srcEndMs = (clip.sourceEndMs - (rightCut * speed).toLong())
                    .coerceAtLeast(srcStartMs + 100L)
                val durSec = ((srcEndMs - srcStartMs) / 1000.0).coerceAtLeast(0.1)
                val delayMs = ((clipStart - effectiveStart).coerceAtLeast(0L)).toInt()

                val chain = mutableListOf<String>()
                chain.add("atrim=start=${srcStartMs / 1000.0}:duration=$durSec")
                chain.add("asetpts=PTS-STARTPTS")
                if (delayMs > 0) chain.add("adelay=$delayMs|$delayMs")
                if (speed != 1.0f) chain.add("atempo=${speed.coerceIn(0.5f, 2.0f)}")
                if (clip.volume != 1.0f) chain.add("volume=${clip.volume}")
                if (clip.audioFx != "none") {
                    val fxFilter = AudioEngine.buildAudioFilter(
                        clip.audioFx, clip.audioFxIntensity
                    )
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

            filterParts.add(
                "${audioLabels.joinToString("")}" +
                        "amix=inputs=${audioLabels.size}:duration=longest:dropout_transition=0[outa]"
            )

            val codecArgs = if (audioFormat == "m4a") {
                listOf("-c:a", "aac", "-b:a", "${bitrateKbps}k")
            } else {
                listOf("-c:a", "libmp3lame", "-b:a", "${bitrateKbps}k")
            }

            args.add("-filter_complex")
            args.add(filterParts.joinToString(";"))
            args.add("-map"); args.add("[outa]")
            args.addAll(codecArgs)
            args.add("-ar"); args.add("44100")
            args.add("-ac"); args.add("2")
            args.add("-t"); args.add((effectiveDurMs / 1000.0).toString())
            args.add("-threads"); args.add("0")
            args.add(outputFile.absolutePath)

            Log.e("FFMPEG_AUDIO_ARGS", "Command: ${args.joinToString(" ")}")

            val argsArray = args.toTypedArray()
            val session = FFmpegKit.executeWithArgumentsAsync(
                argsArray,
                { s ->
                    if (isCancelled) {
                        onError("Audio export cancelled")
                    } else if (ReturnCode.isSuccess(s.returnCode)) {
                        if (outputFile.exists() && outputFile.length() > 0) {
                            onSuccess(outputFile)
                        } else {
                            onError("Audio output empty")
                        }
                    } else {
                        val logs = s.allLogsAsString ?: "Unknown"
                        Log.e("FFMPEG_AUDIO", "FAILED: $logs")
                        onError("Audio export failed:\n${logs.takeLast(1200)}")
                    }
                },
                { _ -> },
                { stats ->
                    if (!isCancelled) {
                        try {
                            val t = stats.time
                            if (t > 0) {
                                val p = ((t / (effectiveDurMs.toDouble() * 1.2))
                                    .coerceIn(0.0, 0.95)).toFloat()
                                onProgress(p)
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            )
            currentSession = session
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FFMPEG_AUDIO", "Crash", e)
            onError("Audio export crash: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  IMAGE SEQUENCE EXPORT
    // ═══════════════════════════════════════════════════════════

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

            val totalFrames = ((effectiveDurMs.toDouble() * fps / 1000.0).toInt())
                .coerceAtLeast(1)

            Log.e(
                "FFMPEG_SEQ",
                "Export image sequence: $totalFrames frames, format=$imageFormat"
            )

            val ext = if (imageFormat == "jpeg") "jpg" else "png"
            val pattern = File(outputDir, "MoodyExport_frame_%05d.$ext").absolutePath

            val trimmedAllClips = trimClipsToRange(allClips, effectiveStart, effectiveEnd)

            val textClips = trimmedAllClips.filter {
                it.isTextClip || it.isStickerClip
            }
            val overlayImageClips = trimmedAllClips.filter {
                it.isVisualClip && it.type.startsWith("image/") &&
                        it.trackIndex > 0
            }
            val overlayClips = trimmedAllClips.filter {
                it.isOverlayClip ||
                        (it.isEffectClip && it.effectState?.overlay != null)
            }
            val vizClips = trimmedAllClips.filter { it.isVisualizerClip }

            val textSequences = if (textClips.isNotEmpty() ||
                overlayImageClips.isNotEmpty() ||
                overlayClips.isNotEmpty()
            ) {
                TextBitmapRenderer.renderCombinedOverlays(
                    context = context,
                    textClips = textClips,
                    imageClips = overlayImageClips,
                    overlayClips = overlayClips,
                    W = targetW, H = targetH, fps = fps,
                    totalDurationMs = effectiveDurMs,
                    onProgress = { p -> onProgress(p * 0.25f) },
                    shouldCancel = { isCancelled },
                    imageFormat = imageFormat,
                    jpegQuality = jpegQuality
                )
            } else emptyList()

            val vizSequences = if (vizClips.isNotEmpty()) {
                VisualizerBitmapRenderer.renderCombinedOverlays(
                    context = context,
                    visualizerClips = vizClips,
                    allClips = allClips,
                    rangeStart = effectiveStart,
                    W = targetW, H = targetH, fps = fps,
                    totalDurationMs = effectiveDurMs,
                    onProgress = { p -> onProgress(0.25f + p * 0.25f) },
                    shouldCancel = { isCancelled },
                    imageFormat = imageFormat,
                    jpegQuality = jpegQuality
                )
            } else emptyList()

            val allSequences = (textSequences + vizSequences).sortedBy { it.trackIndex }

            val baseVisualClips = trimmedAllClips.filter {
                it.isVisualClip &&
                        it.uri.toString().isNotBlank() &&
                        it.uri != Uri.EMPTY &&
                        it.trackIndex == 0
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
                args.add("-f"); args.add("lavfi")
                args.add("-t"); args.add((effectiveDurMs / 1000.0).toString())
                args.add("-i")
                args.add("color=c=black:s=${targetW}x${targetH}:r=$fps")
            } else {
                baseVisualClips.forEachIndexed { idx, clip ->
                    val img = isImage(clip)
                    if (img) {
                        args.add("-loop"); args.add("1")
                        args.add("-framerate"); args.add(fps.toString())
                        args.add("-t"); args.add((clip.durationMs / 1000.0).toString())
                    } else {
                        val srcDurSec = ((clip.sourceEndMs - clip.sourceStartMs) / 1000.0)
                            .coerceAtLeast(0.1)
                        if (clip.sourceStartMs > 0) {
                            args.add("-ss")
                            args.add((clip.sourceStartMs / 1000.0).toString())
                        }
                        args.add("-t"); args.add(srcDurSec.toString())
                    }
                    args.add("-i"); args.add(baseLocalFiles[idx].absolutePath)
                }
            }

            val seqStartIdx = baseLocalFiles.size.coerceAtLeast(1)
            allSequences.forEach { seq ->
                if (seq.startSec > 0.0001) {
                    args.add("-itsoffset")
                    args.add("%.4f".format(seq.startSec))
                }
                args.add("-framerate"); args.add(seq.fps.toString())
                args.add("-start_number"); args.add(seq.startNumber.toString())
                args.add("-i"); args.add(seq.pattern)
            }

            val filterParts = mutableListOf<String>()
            val baseVf = mutableListOf<String>()

            if (baseLocalFiles.isEmpty()) {
                baseVf.add("setsar=1")
            } else if (baseVisualClips.size == 1) {
                val clip = baseVisualClips[0]
                if (clip.speed != 1.0f && !isImage(clip)) {
                    baseVf.add("setpts=${1.0f / clip.speed}*PTS")
                }
                baseVf.addAll(buildPerClipFilterChain(clip, allClips))
                baseVf.addAll(buildVisualTransformFilters(clip, targetW, targetH))
            } else {
                baseVisualClips.forEachIndexed { idx, clip ->
                    val vf = mutableListOf<String>()
                    if (isImage(clip)) {
                        vf.add("setpts=PTS-STARTPTS")
                    } else {
                        vf.add(
                            "trim=start=${clip.sourceStartMs / 1000.0}:" +
                                    "duration=${
                                        (clip.sourceEndMs - clip.sourceStartMs) / 1000.0
                                    }"
                        )
                        vf.add("setpts=PTS-STARTPTS")
                    }
                    if (clip.speed != 1.0f && !isImage(clip)) {
                        vf.add("setpts=${1.0f / clip.speed}*PTS")
                    }
                    vf.addAll(buildVisualTransformFilters(clip, targetW, targetH))
                    vf.add("fps=$fps")
                    vf.add("format=yuv420p")
                    filterParts.add("[$idx:v]${vf.joinToString(",")}[v$idx]")
                }

                val concatInputs = (0 until baseVisualClips.size)
                    .joinToString("") { "[v$it]" }
                filterParts.add(
                    "${concatInputs}concat=n=${baseVisualClips.size}:v=1:a=0[basev]"
                )
            }

            if (baseLocalFiles.isNotEmpty() && baseVisualClips.size == 1) {
                filterParts.add("[0:v]${baseVf.joinToString(",")}[basev]")
            } else if (baseLocalFiles.isEmpty()) {
                filterParts.add("[0:v]setsar=1[basev]")
            }

            var lastLabel = "basev"
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

            args.add("-filter_complex")
            args.add(filterParts.joinToString(";"))
            args.add("-map"); args.add("[outv]")
            args.add("-frames:v"); args.add(totalFrames.toString())
            args.add("-r"); args.add(fps.toString())
            args.add("-threads"); args.add("0")
            args.add(pattern)

            Log.e("FFMPEG_SEQ", "Pattern: $pattern")
            Log.e("FFMPEG_SEQ", "Total frames: $totalFrames")

            val argsArray = args.toTypedArray()
            val session = FFmpegKit.executeWithArgumentsAsync(
                argsArray,
                { s ->
                    if (isCancelled) {
                        onError("Sequence export cancelled")
                    } else if (ReturnCode.isSuccess(s.returnCode)) {
                        val files = outputDir.listFiles()?.filter {
                            it.name.startsWith("MoodyExport_frame_")
                        } ?: emptyList()
                        if (files.isNotEmpty()) {
                            Log.e("FFMPEG_SEQ", "Created ${files.size} images")
                            onSuccess(outputDir)
                        } else {
                            onError("No images created")
                        }
                    } else {
                        val logs = s.allLogsAsString ?: "Unknown"
                        Log.e("FFMPEG_SEQ", "FAILED: $logs")
                        onError("Sequence failed:\n${logs.takeLast(1200)}")
                    }
                },
                { _ -> },
                { stats ->
                    if (!isCancelled) {
                        try {
                            val t = stats.time
                            if (t > 0) {
                                val p = 0.5f + ((t / (effectiveDurMs.toDouble() * 1.2))
                                    .coerceIn(0.0, 0.45)).toFloat()
                                onProgress(p)
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            )
            currentSession = session
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FFMPEG_SEQ", "Crash", e)
            onError("Sequence export crash: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════

    private fun isImage(clip: EditorClip): Boolean =
        clip.type.startsWith("image/")

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
                mime.startsWith("image/jpeg") -> "jpg"
                mime.startsWith("image/png") -> "png"
                mime.startsWith("image/webp") -> "webp"
                mime.startsWith("image/gif") -> "gif"
                mime.startsWith("image/bmp") -> "bmp"
                mime.startsWith("image/heic") -> "heic"
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
                    ?: throw java.io.IOException("Could not open media URI: $uri")
                input.use {
                    tempFile.outputStream().use { output -> it.copyTo(output) }
                }
                if (tempFile.length() <= 0L) {
                    throw java.io.IOException("Media URI produced an empty file: $uri")
                }
                if (file.exists() && !file.delete()) {
                    throw java.io.IOException("Could not replace cached media file")
                }
                if (!tempFile.renameTo(file)) {
                    throw java.io.IOException("Could not finalize cached media file")
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

    private fun clipHasAudio(clip: EditorClip): Boolean {
        val key = clip.uri.toString()
        audioCache[key]?.let { return it }
        val has = try {
            val extractor = android.media.MediaExtractor()
            try {
                extractor.setDataSource(context, clip.uri, null)
                var found = false
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        found = true
                        break
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

    private fun addSequenceInputs(
        args: MutableList<String>,
        sequences: List<TextOverlaySequence>
    ) {
        sequences.forEach { seq ->
            if (seq.startSec > 0.0001) {
                args.add("-itsoffset")
                args.add("%.4f".format(seq.startSec))
            }
            args.add("-framerate"); args.add(seq.fps.toString())
            args.add("-start_number"); args.add(seq.startNumber.toString())
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
        filterParts.add("[$lastLabel]null[outv]")
        return "outv"
    }

    private fun enforceOutputDimensions(
        filterParts: MutableList<String>,
        inputLabel: String,
        targetW: Int,
        targetH: Int
    ): String {
        filterParts.add(
            "[$inputLabel]scale=$targetW:$targetH:flags=lanczos,setsar=1[exportv]"
        )
        return "exportv"
    }

    // ═══════════════════════════════════════════════════════════
    //  VISUAL TRANSFORM FILTERS — ASPECT RATIO FIX
    // ═══════════════════════════════════════════════════════════

    private fun buildVisualTransformFilters(
        clip: EditorClip,
        targetW: Int,
        targetH: Int,
        transparentPadding: Boolean = false,
        positionByOverlay: Boolean = false
    ): List<String> {
        val filters = mutableListOf<String>()

        if (transparentPadding) {
            filters.add("format=rgba")
        }
        filters.add(
            "scale=$targetW:$targetH:" +
                    "force_original_aspect_ratio=decrease"
        )
        filters.add(
            "pad=$targetW:$targetH:(ow-iw)/2:(oh-ih)/2:" +
                    "color=${if (transparentPadding) "black@0" else "black"}"
        )

        val userScale = clip.scale.coerceIn(0.1f, 5f)
        if (kotlin.math.abs(userScale - 1.0f) > 0.01f) {
            val scaleStr = String.format(java.util.Locale.US, "%.4f", userScale)
            filters.add("scale=iw*$scaleStr:ih*$scaleStr")
            if (userScale < 1f) {
                filters.add(
                    "pad=$targetW:$targetH:(ow-iw)/2:(oh-ih)/2:" +
                            "color=${if (transparentPadding) "black@0" else "black"}"
                )
            } else {
                filters.add("crop=$targetW:$targetH")
            }
        }

        if (kotlin.math.abs(clip.rotation) > 0.1f) {
            val rad = String.format(
                java.util.Locale.US, "%.4f",
                Math.toRadians(clip.rotation.toDouble())
            )
            filters.add(
                "rotate=$rad:c=${if (transparentPadding) "black@0" else "black"}:" +
                        "ow=$targetW:oh=$targetH"
            )
        }

        val offsetXpx = (clip.offsetX * targetW).toInt()
        val offsetYpx = (clip.offsetY * targetH).toInt()

        if (!positionByOverlay && (offsetXpx != 0 || offsetYpx != 0)) {
            filters.add(
                "crop=$targetW:$targetH:" +
                        "max(0\\,min(iw-$targetW\\,(iw-$targetW)/2+$offsetXpx)):" +
                        "max(0\\,min(ih-$targetH\\,(ih-$targetH)/2+$offsetYpx))"
            )
        }

        if (transparentPadding) filters.add("format=rgba")
        filters.add("setsar=1")
        return filters
    }

    // ═══════════════════════════════════════════════════════════
    //  AUDIO ONLY MIX
    // ═══════════════════════════════════════════════════════════

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
            val durSec = ((clip.sourceEndMs - clip.sourceStartMs) / 1000.0)
                .coerceAtLeast(0.1)
            val startSec = clip.timelineStartMs / 1000.0
            val delayMs = (startSec * 1000).toInt()
            val speed = clip.speed.coerceAtLeast(0.01f)

            val chain = mutableListOf<String>()
            chain.add("atrim=start=${(clip.sourceStartMs / 1000.0)}:duration=$durSec")
            chain.add("asetpts=PTS-STARTPTS")
            if (delayMs > 0) chain.add("adelay=$delayMs|$delayMs")
            if (speed != 1.0f) {
                chain.add("atempo=${speed.coerceIn(0.5f, 2.0f)}")
            }
            if (clip.volume != 1.0f) {
                chain.add("volume=${clip.volume}")
            }
            if (clip.audioFx != "none") {
                val fxFilter = AudioEngine.buildAudioFilter(
                    clip.audioFx, clip.audioFxIntensity
                )
                if (fxFilter.isNotBlank()) chain.add(fxFilter)
            }
            chain.add("aresample=44100")

            val label = "extra$idx"
            filterParts.add("[$inputIdx:a]${chain.joinToString(",")}[$label]")
            audioLabels.add("[$label]")
        }

        val allMixInputs = "[$baseAudioLabel]" + audioLabels.joinToString("")
        filterParts.add(
            "${allMixInputs}amix=inputs=${audioLabels.size + 1}:duration=longest[$outLabel]"
        )
        return true
    }

    // ═══════════════════════════════════════════════════════════
    //  SINGLE CLIP EXPORT
    // ═══════════════════════════════════════════════════════════

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
        totalDurationMs: Long
    ) {
        try {
            val durationSec = (totalDurationMs / 1000.0).coerceAtLeast(0.1)
            val orderedClips = clips.indices
                .map { clips[it] to localFiles[it] }
                .sortedWith(
                    compareBy<Pair<EditorClip, File>>(
                        { it.first.trackIndex },
                        { it.first.timelineStartMs }
                    )
                )
            val args = mutableListOf("-y")

            orderedClips.forEach { (clip, file) ->
                if (isImage(clip)) {
                    args.addAll(
                        listOf(
                            "-loop", "1",
                            "-framerate", fps.toString(),
                            "-t", (clip.durationMs / 1000.0).toString()
                        )
                    )
                } else {
                    if (clip.sourceStartMs > 0L) {
                        args.addAll(
                            listOf("-ss", (clip.sourceStartMs / 1000.0).toString())
                        )
                    }
                    args.addAll(
                        listOf(
                            "-t",
                            ((clip.sourceEndMs - clip.sourceStartMs) / 1000.0)
                                .coerceAtLeast(0.1).toString()
                        )
                    )
                }
                args.addAll(listOf("-i", file.absolutePath))
            }

            audioLocalFiles.forEach { file ->
                args.addAll(listOf("-i", file.absolutePath))
            }

            val backgroundInputIdx = orderedClips.size + audioLocalFiles.size
            args.addAll(
                listOf(
                    "-f", "lavfi",
                    "-t", durationSec.toString(),
                    "-i", "color=c=black:s=${targetW}x${targetH}:r=$fps"
                )
            )
            val silenceInputIdx = backgroundInputIdx + 1
            args.addAll(
                listOf(
                    "-f", "lavfi",
                    "-t", durationSec.toString(),
                    "-i", "anullsrc=r=44100:cl=stereo"
                )
            )

            val sequenceStartIdx = silenceInputIdx + 1
            addSequenceInputs(args, sequences)

            val filterParts = mutableListOf<String>()
            filterParts.add(
                "[$backgroundInputIdx:v]setpts=PTS-STARTPTS,format=rgba[canvas0]"
            )

            var canvasLabel = "canvas0"
            orderedClips.forEachIndexed { index, (clip, _) ->
                val startSec = clip.timelineStartMs / 1000.0
                val endSec = (clip.timelineStartMs + clip.durationMs) / 1000.0
                val startExpr = String.format(java.util.Locale.US, "%.4f", startSec)
                val endExpr = String.format(java.util.Locale.US, "%.4f", endSec)
                val clipDurationSec = (clip.durationMs / 1000.0).coerceAtLeast(0.1)
                val filters = mutableListOf("setpts=PTS-STARTPTS")
                if (!isImage(clip) && clip.speed != 1.0f) {
                    filters.add("setpts=${1.0f / clip.speed}*PTS")
                }
                filters.addAll(buildPerClipFilterChain(clip, allClips))
                filters.addAll(
                    buildVisualTransformFilters(
                        clip = clip,
                        targetW = targetW,
                        targetH = targetH,
                        transparentPadding = true,
                        positionByOverlay = true
                    )
                )
                filters.add("fps=$fps")
                filters.add("trim=duration=$clipDurationSec")
                filters.add("setpts=PTS-STARTPTS+${startSec}/TB")
                filters.add("format=rgba")
                val layerLabel = "layer$index"
                filterParts.add(
                    "[${index}:v]${filters.joinToString(",")}[$layerLabel]"
                )

                val outputLabel = "canvas${index + 1}"
                val x = (clip.offsetX * targetW).toInt()
                val y = (clip.offsetY * targetH).toInt()
                filterParts.add(
                    "[$canvasLabel][$layerLabel]overlay=$x:$y:" +
                            "eof_action=pass:repeatlast=0:" +
                            "enable='between(t,$startExpr,$endExpr)'" +
                            "[$outputLabel]"
                )
                canvasLabel = outputLabel
            }

            val audioLabels = mutableListOf<String>()
            orderedClips.forEachIndexed { index, (clip, _) ->
                if (isImage(clip) || !clipHasAudio(clip)) return@forEachIndexed
                val clipDurationSec = (clip.durationMs / 1000.0).coerceAtLeast(0.1)
                val filters = mutableListOf("asetpts=PTS-STARTPTS")
                if (clip.speed != 1.0f) {
                    filters.add("atempo=${clip.speed.coerceIn(0.5f, 2.0f)}")
                }
                filters.add("atrim=duration=$clipDurationSec")
                filters.add("volume=${if (clip.isMuted) 0f else clip.volume}")
                if (clip.audioFx != "none") {
                    val audioFilter = AudioEngine.buildAudioFilter(
                        clip.audioFx, clip.audioFxIntensity
                    )
                    if (audioFilter.isNotBlank()) filters.add(audioFilter)
                }
                val delayMs = clip.timelineStartMs.coerceAtLeast(0L)
                filters.add("adelay=$delayMs|$delayMs")
                filters.add("aresample=44100")
                val label = "trackAudio$index"
                filterParts.add(
                    "[$index:a]${filters.joinToString(",")}[$label]"
                )
                audioLabels.add("[$label]")
            }

            audioOnlyClips.forEachIndexed { index, clip ->
                val inputIdx = orderedClips.size + index
                val duration = ((clip.sourceEndMs - clip.sourceStartMs) / 1000.0)
                    .coerceAtLeast(0.1)
                val clipDurationSec = (clip.durationMs / 1000.0).coerceAtLeast(0.1)
                val filters = mutableListOf(
                    "atrim=start=${clip.sourceStartMs / 1000.0}:duration=$duration",
                    "asetpts=PTS-STARTPTS"
                )
                if (clip.speed != 1.0f) {
                    filters.add("atempo=${clip.speed.coerceIn(0.5f, 2.0f)}")
                }
                filters.add("atrim=duration=$clipDurationSec")
                filters.add("volume=${if (clip.isMuted) 0f else clip.volume}")
                if (clip.audioFx != "none") {
                    val audioFilter = AudioEngine.buildAudioFilter(
                        clip.audioFx, clip.audioFxIntensity
                    )
                    if (audioFilter.isNotBlank()) filters.add(audioFilter)
                }
                val delayMs = clip.timelineStartMs.coerceAtLeast(0L)
                filters.add("adelay=$delayMs|$delayMs")
                filters.add("aresample=44100")
                val label = "extraAudio$index"
                filterParts.add(
                    "[$inputIdx:a]${filters.joinToString(",")}[$label]"
                )
                audioLabels.add("[$label]")
            }

            filterParts.add("[$silenceInputIdx:a]aresample=44100[silentAudio]")
            audioLabels.add("[silentAudio]")
            filterParts.add(
                "${audioLabels.joinToString("")}amix=" +
                        "inputs=${audioLabels.size}:duration=longest:" +
                        "dropout_transition=0[mixedAudio]"
            )

            val outputVideo = buildOverlayChain(
                filterParts, sequences, sequenceStartIdx, canvasLabel
            )
            val finalVideo = enforceOutputDimensions(
                filterParts, outputVideo, targetW, targetH
            )
            args.addAll(
                listOf(
                    "-filter_complex", filterParts.joinToString(";"),
                    "-map", "[$finalVideo]",
                    "-map", "[mixedAudio]",
                    "-t", durationSec.toString()
                )
            )
            args.addAll(buildFastVideoArgs(bitrateKbps, targetW, targetH))
            args.addAll(
                listOf(
                    "-r", fps.toString(),
                    "-c:a", "aac",
                    "-b:a", "128k",
                    "-movflags", "+faststart",
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
            val img = isImage(clip)
            val durSec = ((clip.sourceEndMs - clip.sourceStartMs) / 1000.0)
                .coerceAtLeast(0.1)
            val clipDurSec = (clip.durationMs / 1000.0).coerceAtLeast(0.1)
            val totalDurSec = (totalDurationMs / 1000.0).coerceAtLeast(durSec)
            val extraPadSec = (totalDurSec - clipDurSec).coerceAtLeast(0.0)

            val args = mutableListOf<String>()
            args.add("-y")

            if (img) {
                args.add("-loop"); args.add("1")
                args.add("-framerate"); args.add(fps.toString())
                args.add("-t"); args.add(clipDurSec.toString())
            } else {
                if (clip.sourceStartMs > 0) {
                    args.add("-ss"); args.add((clip.sourceStartMs / 1000.0).toString())
                }
                if (clip.sourceEndMs > clip.sourceStartMs) {
                    args.add("-t"); args.add(durSec.toString())
                }
            }
            args.add("-i"); args.add(localFile.absolutePath)

            val audioInputIdx: Int
            if (img) {
                args.add("-f"); args.add("lavfi")
                args.add("-t"); args.add(totalDurSec.toString())
                args.add("-i"); args.add("anullsrc=r=44100:cl=stereo")
                audioInputIdx = 1
            } else {
                audioInputIdx = 0
            }

            val seqStartIdx = if (img) 2 else 1
            addSequenceInputs(args, sequences)

            val filterParts = mutableListOf<String>()
            val baseVf = mutableListOf<String>()

            if (clip.speed != 1.0f && !img) {
                baseVf.add("setpts=${1.0f / clip.speed}*PTS")
            }

            baseVf.addAll(buildPerClipFilterChain(clip, allClips))
            baseVf.addAll(buildVisualTransformFilters(clip, targetW, targetH))

            if (extraPadSec > 0.05) {
                val padSec = String.format(
                    java.util.Locale.US, "%.3f", extraPadSec
                )
                baseVf.add("tpad=stop_mode=add:stop_duration=$padSec")
            }

            filterParts.add("[0:v]${baseVf.joinToString(",")}[base]")

            val outVLabel = buildOverlayChain(
                filterParts, sequences, seqStartIdx, "base"
            )
            val finalVideoLabel = enforceOutputDimensions(
                filterParts, outVLabel, targetW, targetH
            )

            val af = mutableListOf<String>()
            if (clip.speed != 1.0f && !img) {
                af.add("atempo=${clip.speed.coerceIn(0.5f, 2.0f)}")
            }
            if (clip.volume != 1.0f) af.add("volume=${clip.volume}")
            if (clip.audioFx != "none") {
                val fxFilter = AudioEngine.buildAudioFilter(
                    clip.audioFx, clip.audioFxIntensity
                )
                if (fxFilter.isNotBlank()) af.add(fxFilter)
            }

            if (af.isEmpty()) {
                filterParts.add("[$audioInputIdx:a]aresample=44100[basea]")
            } else {
                filterParts.add(
                    "[$audioInputIdx:a]${af.joinToString(",")},aresample=44100[basea]"
                )
            }

            val fxApplied = applyAudioEffectLayers(
                filterParts, allClips, 0L, "basea", "fxa"
            )
            val afterFxLabel = if (fxApplied) "fxa" else "basea"

            args.add("-filter_complex")
            args.add(filterParts.joinToString(";"))
            args.add("-map"); args.add("[$finalVideoLabel]")
            args.add("-map"); args.add("[$afterFxLabel]")
            args.add("-t"); args.add(totalDurSec.toString())

            args.addAll(buildFastVideoArgs(bitrateKbps, targetW, targetH))
            args.add("-r"); args.add(fps.toString())
            args.add("-c:a"); args.add("aac")
            args.add("-b:a"); args.add("128k")
            args.add("-movflags"); args.add("+faststart")
            args.add(outputFile.absolutePath)

            Log.e("FFMPEG_ARGS_SINGLE", args.joinToString(" "))
            execute(args, outputFile)
        } catch (e: Throwable) {
            Log.e("FFMPEG", "Single build error", e)
            onError("Build error: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SINGLE CLIP WITH AUDIO EXPORT
    // ═══════════════════════════════════════════════════════════

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
            val img = isImage(clip)
            val sourceDurSec = ((clip.sourceEndMs - clip.sourceStartMs) / 1000.0)
                .coerceAtLeast(0.1)
            val clipDurSec = (clip.durationMs / 1000.0).coerceAtLeast(0.1)
            val totalDurSec = (totalDurationMs / 1000.0).coerceAtLeast(clipDurSec)
            val extraPadSec = (totalDurSec - clipDurSec).coerceAtLeast(0.0)

            val args = mutableListOf<String>()
            args.add("-y")

            if (img) {
                args.add("-loop"); args.add("1")
                args.add("-framerate"); args.add(fps.toString())
                args.add("-t"); args.add(sourceDurSec.toString())
            } else {
                if (clip.sourceStartMs > 0) {
                    args.add("-ss"); args.add((clip.sourceStartMs / 1000.0).toString())
                }
                if (clip.sourceEndMs > clip.sourceStartMs) {
                    args.add("-t"); args.add(sourceDurSec.toString())
                }
            }
            args.add("-i"); args.add(localFile.absolutePath)

            val audioInputIdx: Int
            if (img) {
                args.add("-f"); args.add("lavfi")
                args.add("-t"); args.add(totalDurSec.toString())
                args.add("-i"); args.add("anullsrc=r=44100:cl=stereo")
                audioInputIdx = 1
            } else {
                audioInputIdx = 0
            }

            val audioStartIdx = if (img) 2 else 1
            audioLocalFiles.forEach { f ->
                args.add("-i"); args.add(f.absolutePath)
            }

            val seqStartIdx = audioStartIdx + audioLocalFiles.size
            addSequenceInputs(args, sequences)

            val filterParts = mutableListOf<String>()
            val baseVf = mutableListOf<String>()

            if (clip.speed != 1.0f && !img) {
                baseVf.add("setpts=${1.0f / clip.speed}*PTS")
            }

            baseVf.addAll(buildPerClipFilterChain(clip, allClips))
            baseVf.addAll(buildVisualTransformFilters(clip, targetW, targetH))

            if (extraPadSec > 0.05) {
                val padSec = String.format(
                    java.util.Locale.US, "%.3f", extraPadSec
                )
                baseVf.add("tpad=stop_mode=add:stop_duration=$padSec")
            }

            filterParts.add("[0:v]${baseVf.joinToString(",")}[base]")

            val outVLabel = buildOverlayChain(
                filterParts, sequences, seqStartIdx, "base"
            )
            val finalVideoLabel = enforceOutputDimensions(
                filterParts, outVLabel, targetW, targetH
            )

            val af = mutableListOf<String>()
            if (clip.speed != 1.0f && !img) {
                af.add("atempo=${clip.speed.coerceIn(0.5f, 2.0f)}")
            }
            if (clip.volume != 1.0f) af.add("volume=${clip.volume}")
            if (clip.audioFx != "none") {
                val fxFilter = AudioEngine.buildAudioFilter(
                    clip.audioFx, clip.audioFxIntensity
                )
                if (fxFilter.isNotBlank()) af.add(fxFilter)
            }

            if (af.isEmpty()) {
                filterParts.add("[$audioInputIdx:a]aresample=44100[basea]")
            } else {
                filterParts.add(
                    "[$audioInputIdx:a]${af.joinToString(",")},aresample=44100[basea]"
                )
            }

            val mixed = buildAudioOnlyMix(
                filterParts = filterParts,
                audioLocalFiles = audioLocalFiles,
                audioClips = audioClips,
                firstInputIdx = audioStartIdx,
                baseAudioLabel = "basea",
                outLabel = "mixeda"
            )
            val afterMixLabel = if (mixed) "mixeda" else "basea"

            val fxApplied = applyAudioEffectLayers(
                filterParts, allClips, 0L, afterMixLabel, "fxa"
            )
            val finalAudioLabel = if (fxApplied) "fxa" else afterMixLabel

            args.add("-filter_complex")
            args.add(filterParts.joinToString(";"))
            args.add("-map"); args.add("[$finalVideoLabel]")
            args.add("-map"); args.add("[$finalAudioLabel]")
            args.add("-t"); args.add(totalDurSec.toString())

            args.addAll(buildFastVideoArgs(bitrateKbps, targetW, targetH))
            args.add("-r"); args.add(fps.toString())
            args.add("-c:a"); args.add("aac")
            args.add("-b:a"); args.add("128k")
            args.add("-movflags"); args.add("+faststart")
            args.add(outputFile.absolutePath)

            Log.e("FFMPEG_ARGS_SINGLE_AUDIO", args.joinToString(" "))
            execute(args, outputFile)
        } catch (e: Throwable) {
            Log.e("FFMPEG", "Single+Audio error", e)
            onError("Build error: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  MULTIPLE CLIPS DISPATCH
    // ═══════════════════════════════════════════════════════════

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
            val originalIdx = clips.indexOf(c)
            if (originalIdx >= 0) localFiles.getOrNull(originalIdx) else null
        }

        if (sortedClips.size != sortedLocalFiles.size) {
            Log.e("FFMPEG", "Sort mismatch, falling back to concat")
            exportWithConcat(
                sortedClips, allClips, sortedLocalFiles,
                audioClips, audioLocalFiles,
                outputFile, targetW, targetH, fps, bitrateKbps,
                sequences, totalDurationMs
            )
            return
        }

        val sameTrack = sortedClips.all {
            it.trackIndex == sortedClips.first().trackIndex
        }
        val allNormalSpeed = sortedClips.all {
            kotlin.math.abs(it.speed - 1.0f) < 0.01f
        }
        val hasAnyTransition = sortedClips.drop(1).any {
            it.transition?.isActive == true &&
                    TransitionLibrary.find(it.transition!!.key)
                        ?.ffmpegXfade?.isNotBlank() == true
        }
        val allAdjacent = sortedClips.zipWithNext().all { (a, b) ->
            val gap = b.timelineStartMs - a.timelineEndMs
            kotlin.math.abs(gap) < 500L
        }

        val useXfade = sortedClips.size >= 2 &&
                sameTrack &&
                allNormalSpeed &&
                allAdjacent &&
                hasAnyTransition

        Log.e(
            "FFMPEG_TRANS",
            "clips=${sortedClips.size}, sameTrack=$sameTrack, " +
                    "allNormalSpeed=$allNormalSpeed, allAdjacent=$allAdjacent, " +
                    "hasAnyTrans=$hasAnyTransition, useXfade=$useXfade"
        )

        if (useXfade) {
            exportWithTransitions(
                sortedClips, allClips, sortedLocalFiles,
                audioClips, audioLocalFiles,
                outputFile, targetW, targetH, fps, bitrateKbps,
                sequences, totalDurationMs
            )
        } else {
            exportWithConcat(
                sortedClips, allClips, sortedLocalFiles,
                audioClips, audioLocalFiles,
                outputFile, targetW, targetH, fps, bitrateKbps,
                sequences, totalDurationMs
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  EXPORT WITH TRANSITIONS (xfade)
    // ═══════════════════════════════════════════════════════════

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
            val args = mutableListOf<String>()
            args.add("-y")

            clips.forEachIndexed { idx, clip ->
                val img = isImage(clip)
                if (img) {
                    args.add("-loop"); args.add("1")
                    args.add("-framerate"); args.add(fps.toString())
                    args.add("-t"); args.add((clip.durationMs / 1000.0).toString())
                } else {
                    val srcDurSec = ((clip.sourceEndMs - clip.sourceStartMs) / 1000.0)
                        .coerceAtLeast(0.1)
                    if (clip.sourceStartMs > 0) {
                        args.add("-ss")
                        args.add((clip.sourceStartMs / 1000.0).toString())
                    }
                    args.add("-t"); args.add(srcDurSec.toString())
                }
                args.add("-i"); args.add(localFiles[idx].absolutePath)
            }

            val silentIdx = mutableMapOf<Int, Int>()
            var nextIdx = clips.size
            clips.forEachIndexed { idx, clip ->
                if (!clipHasAudio(clip)) {
                    args.add("-f"); args.add("lavfi")
                    args.add("-t"); args.add((clip.durationMs / 1000.0).toString())
                    args.add("-i"); args.add("anullsrc=r=44100:cl=stereo")
                    silentIdx[idx] = nextIdx
                    nextIdx++
                }
            }

            val audioStartIdx = nextIdx
            audioLocalFiles.forEach { f ->
                args.add("-i"); args.add(f.absolutePath)
                nextIdx++
            }

            val seqStartIdx = nextIdx
            addSequenceInputs(args, sequences)

            val filterParts = mutableListOf<String>()

            clips.forEachIndexed { idx, clip ->
                val vf = mutableListOf<String>()

                if (isImage(clip)) {
                    vf.add("setpts=PTS-STARTPTS")
                } else {
                    vf.add(
                        "trim=start=${clip.sourceStartMs / 1000.0}:" +
                                "duration=${
                                    (clip.sourceEndMs - clip.sourceStartMs) / 1000.0
                                }"
                    )
                    vf.add("setpts=PTS-STARTPTS")
                }

                if (clip.speed != 1.0f && !isImage(clip)) {
                    vf.add("setpts=${1.0f / clip.speed}*PTS")
                }

                vf.addAll(buildPerClipFilterChain(clip, allClips))
                vf.addAll(buildVisualTransformFilters(clip, targetW, targetH))
                vf.add("fps=$fps")
                vf.add("format=yuv420p")

                filterParts.add("[$idx:v]${vf.joinToString(",")}[nv$idx]")
            }

            var currentLabel = "nv0"
            var cumulativeOffsetSec = clips[0].durationMs / 1000.0

            for (i in 1 until clips.size) {
                val trans = clips[i].transition
                val isActive = trans?.isActive == true
                val preset = if (isActive) TransitionLibrary.find(trans!!.key) else null
                val xfadeName = preset?.ffmpegXfade?.takeIf { it.isNotBlank() } ?: "fade"
                val transDurSec = if (isActive)
                    (trans!!.durationMs / 1000.0).coerceIn(0.2, 3.0)
                else 0.05

                val offsetSec = (cumulativeOffsetSec - transDurSec).coerceAtLeast(0.05)

                val outLabel = "xfd$i"
                filterParts.add(
                    "[$currentLabel][nv$i]xfade=" +
                            "transition=$xfadeName:" +
                            "duration=$transDurSec:" +
                            "offset=$offsetSec" +
                            "[$outLabel]"
                )

                currentLabel = outLabel
                cumulativeOffsetSec += (clips[i].durationMs / 1000.0) - transDurSec
            }

            val audioLabels = mutableListOf<String>()
            clips.forEachIndexed { idx, clip ->
                val hasAudio = clipHasAudio(clip)
                val srcIdx = if (hasAudio) idx else (silentIdx[idx] ?: idx)
                val durSec = clip.durationMs / 1000.0
                val label = "ca$idx"

                val af = mutableListOf<String>()
                if (hasAudio) {
                    af.add("atrim=start=${clip.sourceStartMs / 1000.0}:duration=$durSec")
                    af.add("asetpts=PTS-STARTPTS")
                } else {
                    af.add("atrim=start=0:duration=$durSec")
                    af.add("asetpts=PTS-STARTPTS")
                }
                af.add("aresample=44100")

                filterParts.add("[$srcIdx:a]${af.joinToString(",")}[$label]")
                audioLabels.add("[$label]")
            }

            filterParts.add(
                "${audioLabels.joinToString("")}concat=n=${clips.size}:v=0:a=1[basea]"
            )

            var finalAudioLabel = "basea"

            if (audioLocalFiles.isNotEmpty()) {
                val mixed = buildAudioOnlyMix(
                    filterParts, audioLocalFiles, audioClips,
                    audioStartIdx, finalAudioLabel, "mixeda"
                )
                if (mixed) finalAudioLabel = "mixeda"
            }

            val fxApplied = applyAudioEffectLayers(
                filterParts, allClips, 0L, finalAudioLabel, "fxa"
            )
            if (fxApplied) finalAudioLabel = "fxa"

            val outVLabel = buildOverlayChain(
                filterParts, sequences, seqStartIdx, currentLabel
            )
            val finalVideoLabel = enforceOutputDimensions(
                filterParts, outVLabel, targetW, targetH
            )

            val totalDurSec = (totalDurationMs / 1000.0).coerceAtLeast(0.1)

            args.add("-filter_complex")
            args.add(filterParts.joinToString(";"))
            args.add("-map"); args.add("[$finalVideoLabel]")
            args.add("-map"); args.add("[$finalAudioLabel]")
            args.add("-t"); args.add(totalDurSec.toString())

            args.addAll(buildFastVideoArgs(bitrateKbps, targetW, targetH))
            args.add("-r"); args.add(fps.toString())
            args.add("-c:a"); args.add("aac")
            args.add("-b:a"); args.add("128k")
            args.add("-movflags"); args.add("+faststart")
            args.add(outputFile.absolutePath)

            xfadeFallback = {
                Log.e("FFMPEG_TRANS", "Retrying as CONCAT (no transitions)")
                exportWithConcat(
                    clips, allClips, localFiles,
                    audioClips, audioLocalFiles,
                    outputFile, targetW, targetH, fps, bitrateKbps,
                    sequences, totalDurationMs
                )
            }

            Log.e("FFMPEG_ARGS_TRANS", args.joinToString(" "))
            execute(args, outputFile)
        } catch (e: Throwable) {
            Log.e("FFMPEG", "Transition export error", e)
            xfadeFallback = null
            exportWithConcat(
                clips, allClips, localFiles,
                audioClips, audioLocalFiles,
                outputFile, targetW, targetH, fps, bitrateKbps,
                sequences, totalDurationMs
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  EXPORT WITH CONCAT (fallback / default)
    // ═══════════════════════════════════════════════════════════

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

            val args = mutableListOf<String>()
            args.add("-y")

            clips.forEachIndexed { idx, clip ->
                val file = localFiles[idx]
                val img = isImage(clip)
                if (img) {
                    args.add("-loop"); args.add("1")
                    args.add("-framerate"); args.add(fps.toString())
                    args.add("-t"); args.add((clip.durationMs / 1000.0).toString())
                }
                args.add("-i"); args.add(file.absolutePath)
            }

            val imageAudioInputs = mutableMapOf<Int, Int>()
            var nextInputIdx = clips.size
            clips.forEachIndexed { idx, clip ->
                if (isImage(clip)) {
                    val durSec = clip.durationMs / 1000.0
                    args.add("-f"); args.add("lavfi")
                    args.add("-t"); args.add(durSec.toString())
                    args.add("-i"); args.add("anullsrc=r=44100:cl=stereo")
                    imageAudioInputs[idx] = nextInputIdx
                    nextInputIdx++
                }
            }

            val audioStartIdx = nextInputIdx
            audioLocalFiles.forEach { f ->
                args.add("-i"); args.add(f.absolutePath)
                nextInputIdx++
            }

            val seqStartIdx = nextInputIdx
            addSequenceInputs(args, sequences)

            val filterParts = mutableListOf<String>()
            val concatInputs = mutableListOf<String>()

            clips.forEachIndexed { idx, clip ->
                val img = isImage(clip)
                val durationSec = clip.durationMs / 1000.0

                val vf = mutableListOf<String>()
                if (img) {
                    vf.add("setpts=PTS-STARTPTS")
                } else {
                    val ssSec = clip.sourceStartMs / 1000.0
                    vf.add("trim=start=$ssSec:duration=$durationSec")
                    vf.add("setpts=PTS-STARTPTS")
                }
                if (clip.speed != 1.0f && !img) {
                    vf.add("setpts=${1.0f / clip.speed}*PTS")
                }

                vf.addAll(buildPerClipFilterChain(clip, allClips))
                vf.addAll(buildVisualTransformFilters(clip, targetW, targetH))
                vf.add("fps=$fps")
                vf.add("format=yuv420p")
                filterParts.add("[$idx:v]${vf.joinToString(",")}[v$idx]")

                val aIdx = imageAudioInputs[idx] ?: idx
                val aStart = if (img) 0.0 else clip.sourceStartMs / 1000.0

                val af = mutableListOf<String>()
                if (img) {
                    af.add("asetpts=PTS-STARTPTS")
                } else {
                    af.add("atrim=start=$aStart:duration=$durationSec")
                    af.add("asetpts=PTS-STARTPTS")
                }
                if (clip.speed != 1.0f && !img) {
                    af.add("atempo=${clip.speed.coerceIn(0.5f, 2.0f)}")
                }
                if (clip.audioFx != "none") {
                    val fxFilter = AudioEngine.buildAudioFilter(
                        clip.audioFx, clip.audioFxIntensity
                    )
                    if (fxFilter.isNotBlank()) af.add(fxFilter)
                }
                af.add("aresample=44100")
                filterParts.add("[$aIdx:a]${af.joinToString(",")}[a$idx]")

                concatInputs.add("[v$idx][a$idx]")
            }

            filterParts.add(
                "${concatInputs.joinToString("")}concat=n=${clips.size}:v=1:a=1[concatv][basea]"
            )

            val mixed = if (audioLocalFiles.isNotEmpty()) {
                buildAudioOnlyMix(
                    filterParts = filterParts,
                    audioLocalFiles = audioLocalFiles,
                    audioClips = audioClips,
                    firstInputIdx = audioStartIdx,
                    baseAudioLabel = "basea",
                    outLabel = "mixeda"
                )
            } else false

            val afterMixLabel = if (mixed) "mixeda" else "basea"

            val fxApplied = applyAudioEffectLayers(
                filterParts, allClips, 0L, afterMixLabel, "fxa"
            )
            val finalAudioLabel = if (fxApplied) "fxa" else afterMixLabel

            val outVLabel = buildOverlayChain(
                filterParts, sequences, seqStartIdx, "concatv"
            )
            val finalVideoLabel = enforceOutputDimensions(
                filterParts, outVLabel, targetW, targetH
            )

            val totalDurSec = (totalDurationMs / 1000.0).coerceAtLeast(0.1)

            args.add("-filter_complex")
            args.add(filterParts.joinToString(";"))
            args.add("-map"); args.add("[$finalVideoLabel]")
            args.add("-map"); args.add("[$finalAudioLabel]")
            args.add("-t"); args.add(totalDurSec.toString())

            args.addAll(buildFastVideoArgs(bitrateKbps, targetW, targetH))
            args.add("-r"); args.add(fps.toString())
            args.add("-c:a"); args.add("aac")
            args.add("-b:a"); args.add("128k")
            args.add("-movflags"); args.add("+faststart")
            args.add(outputFile.absolutePath)

            Log.e("FFMPEG_ARGS_CONCAT", args.joinToString(" "))
            execute(args, outputFile)
        } catch (e: Throwable) {
            Log.e("FFMPEG", "Concat export error", e)
            onError("Concat export: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PER-CLIP FILTER CHAIN
    // ═══════════════════════════════════════════════════════════

    private fun buildPerClipFilterChain(
        clip: EditorClip,
        allClips: List<EditorClip>
    ): List<String> {
        val filters = mutableListOf<String>()

        val filterLayersAbove = allClips.filter { e ->
            e.isFilterLayerClip &&
                    e.trackIndex > clip.trackIndex &&
                    e.timelineEndMs > clip.timelineStartMs &&
                    e.timelineStartMs < clip.timelineEndMs
        }.sortedBy { it.trackIndex }

        filterLayersAbove.forEach { layer ->
            val ff = layer.filters
            val cfv = ColorFilterValues(
                brightness = ff.brightness,
                contrast = ff.contrast,
                saturation = ff.saturation,
                hue = ff.hue,
                grayscale = ff.grayscale,
                sepia = ff.sepia,
                invert = ff.invert,
                blur = ff.blur,
                opacity = ff.opacity
            )
            val filterStr = colorFilterValuesToFfmpeg(cfv)
            if (filterStr.isNotBlank()) filters.add(filterStr)
        }

        val effectClipsAbove = allClips.filter { e ->
            e.isEffectClip &&
                    e.trackIndex > clip.trackIndex &&
                    e.timelineEndMs > clip.timelineStartMs &&
                    e.timelineStartMs < clip.timelineEndMs
        }.sortedBy { it.trackIndex }

        effectClipsAbove.forEach { effClip ->
            val m = effClip.effectState?.motion
            if (m != null) {
                val f = motionToFfmpeg(m)
                if (f.isNotBlank()) filters.add(f)
            }

            val cf = effClip.effectState?.filters
            if (cf != null) {
                val f = colorFilterValuesToFfmpeg(cf)
                if (f.isNotBlank()) filters.add(f)
            }
        }

        val f = clip.filters
        if (f.brightness != 100f || f.contrast != 100f || f.saturation != 100f) {
            val eqParts = mutableListOf<String>()
            if (f.brightness != 100f) {
                eqParts.add(
                    "brightness=${((f.brightness - 100f) / 100f).coerceIn(-1f, 1f)}"
                )
            }
            if (f.contrast != 100f) {
                eqParts.add("contrast=${(f.contrast / 100f).coerceIn(0f, 2f)}")
            }
            if (f.saturation != 100f) {
                eqParts.add("saturation=${(f.saturation / 100f).coerceIn(0f, 3f)}")
            }
            if (eqParts.isNotEmpty()) filters.add("eq=${eqParts.joinToString(":")}")
        }
        if (f.hue != 0f) filters.add("hue=h=${f.hue}")
        if (f.grayscale > 0f) filters.add("format=gray")
        if (f.invert > 0f) filters.add("negate")

        val adj = clip.adjustments
        if (!adj.isDefault) {
            val adjFilter = FFmpegFilters.build(adj)
            if (adjFilter.isNotBlank()) filters.add(adjFilter)
        }

        val chroma = clip.chroma
        if (chroma != null && chroma.isActive) {
            try {
                val cf = ChromaEngine.buildFfmpegFilter(chroma)
                if (cf.isNotBlank()) filters.add(cf)
            } catch (_: Throwable) {
            }
        }

        return filters
    }

    // ═══════════════════════════════════════════════════════════
    //  AUDIO EFFECT LAYERS
    // ═══════════════════════════════════════════════════════════

    private fun applyAudioEffectLayers(
        filterParts: MutableList<String>,
        allClips: List<EditorClip>,
        rangeStartMs: Long,
        baseLabel: String,
        outLabel: String
    ): Boolean {
        val layers = allClips
            .filter { it.isAudioEffectClip }
            .sortedBy { it.timelineStartMs }

        if (layers.isEmpty()) return false

        val n = layers.size
        val splitLabels = (0..n).map { "fxsplt$it" }

        filterParts.add(
            "[$baseLabel]asplit=${n + 1}" +
                    splitLabels.joinToString("") { "[$it]" }
        )

        val baseChainParts = mutableListOf<String>()
        layers.forEach { layer ->
            val s = ((layer.timelineStartMs - rangeStartMs).coerceAtLeast(0L)) / 1000.0
            val e = (layer.timelineEndMs - rangeStartMs) / 1000.0
            if (e - s < 0.02) return@forEach
            baseChainParts.add(
                "volume=enable='between(t,%.3f,%.3f)':volume=0".format(s, e)
            )
        }
        val baseChain = if (baseChainParts.isEmpty()) "anull"
        else baseChainParts.joinToString(",")
        filterParts.add("[${splitLabels[0]}]$baseChain[fxn0]")

        layers.forEachIndexed { i, layer ->
            val s = ((layer.timelineStartMs - rangeStartMs).coerceAtLeast(0L)) / 1000.0
            val e = (layer.timelineEndMs - rangeStartMs) / 1000.0
            if (e - s < 0.02) {
                filterParts.add("[${splitLabels[i + 1]}]anull[fxn${i + 1}]")
                return@forEachIndexed
            }

            val fxKey = if (layer.isAudioFxClip) layer.audioFx else layer.soundFx
            val intensity = if (layer.isAudioFxClip) layer.audioFxIntensity
            else layer.soundFxIntensity

            val gate = "volume=enable='not(between(t,%.3f,%.3f))':volume=0"
                .format(s, e)
            val fxFilter = AudioEngine.buildAudioFilter(fxKey, intensity)

            val chain = if (fxFilter.isNotBlank()) "$gate,$fxFilter" else gate
            filterParts.add("[${splitLabels[i + 1]}]$chain[fxn${i + 1}]")
        }

        val mixInputs = (0..n).joinToString("") { "[fxn$it]" }
        filterParts.add(
            "${mixInputs}amix=inputs=${n + 1}:duration=longest[$outLabel]"
        )
        return true
    }

    // ═══════════════════════════════════════════════════════════
    //  MOTION → FFMPEG FILTER
    // ═══════════════════════════════════════════════════════════

    private fun motionToFfmpeg(m: MotionConfig): String {
        val I = (m.intensity / 100f).coerceIn(0.1f, 3.0f)
        val S = m.speed.coerceIn(0.2f, 10f)
        val PI = "3.141592653589793"

        return when (m.type) {
            "shake" -> {
                val ampX = (6f * I).toInt().coerceAtLeast(2)
                val ampY = (6f * I).toInt().coerceAtLeast(2)
                "crop=iw-$ampX:ih-$ampY:" +
                        "'(iw-ow)/2+$ampX*sin($S*t*37)':" +
                        "'(ih-oh)/2+$ampY*cos($S*t*41)'"
            }

            "bounce" -> {
                val amp = 0.12f * I
                "scale=iw*(1+$amp*abs(sin($S*t*4))):" +
                        "ih*(1+$amp*abs(sin($S*t*4))):eval=frame"
            }

            "pulse" -> {
                val amp = 0.08f * I
                "scale=iw*(1+$amp*sin($S*t*3)):" +
                        "ih*(1+$amp*sin($S*t*3)):eval=frame"
            }

            "zoomPulse" -> {
                val amp = 0.35f * I
                "scale=iw*(1+$amp*0.5*(1+sin($S*t*2))):" +
                        "ih*(1+$amp*0.5*(1+sin($S*t*2))):eval=frame"
            }

            "rotate" -> {
                val amp = 0.05f * I
                "rotate=$S*t*$amp*sin($S*t*2)*$PI/180:c=none:ow=iw:oh=ih"
            }

            "glitch" -> {
                "noise=alls=${(20 * I).toInt().coerceIn(5, 80)}:allf=t+u"
            }

            else -> ""
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  COLOR FILTER VALUES → FFMPEG
    // ═══════════════════════════════════════════════════════════

    private fun colorFilterValuesToFfmpeg(cf: ColorFilterValues): String {
        val parts = mutableListOf<String>()

        if (cf.brightness != 100f) {
            val m = String.format(
                java.util.Locale.US,
                "%.4f",
                (cf.brightness / 100f).coerceIn(0f, 3f)
            )
            parts.add("lutrgb=r='val*$m':g='val*$m':b='val*$m'")
        }

        if (cf.contrast != 100f) {
            val c = String.format(
                java.util.Locale.US,
                "%.4f",
                (cf.contrast / 100f).coerceIn(0f, 3f)
            )
            parts.add("eq=contrast=$c")
        }

        if (cf.saturation != 100f) {
            val s = String.format(
                java.util.Locale.US,
                "%.4f",
                (cf.saturation / 100f).coerceIn(0f, 3f)
            )
            parts.add("eq=saturation=$s")
        }

        if (cf.hue != 0f) {
            parts.add("hue=h=${cf.hue}")
        }

        if (cf.grayscale > 0f) {
            val s = String.format(
                java.util.Locale.US,
                "%.4f",
                (1f - (cf.grayscale / 100f).coerceIn(0f, 1f))
            )
            parts.add("eq=saturation=$s")
        }

        if (cf.sepia > 0f) {
            val a = (cf.sepia / 100f).coerceIn(0f, 1f)

            val sr = 0.393f
            val sg = 0.769f
            val sb = 0.189f
            val mr = 0.349f
            val mg = 0.686f
            val mb = 0.168f
            val hr = 0.272f
            val hg = 0.534f
            val hb = 0.131f

            val rr = (1f - a) + a * sr
            val rg = a * sg
            val rb = a * sb
            val gr = a * mr
            val gg = (1f - a) + a * mg
            val gb = a * mb
            val br = a * hr
            val bg = a * hg
            val bb = (1f - a) + a * hb

            val f = java.util.Locale.US
            parts.add(
                "colorchannelmixer=" +
                        "rr=${"%.4f".format(f, rr)}:" +
                        "rg=${"%.4f".format(f, rg)}:" +
                        "rb=${"%.4f".format(f, rb)}:" +
                        "ra=0:" +
                        "gr=${"%.4f".format(f, gr)}:" +
                        "gg=${"%.4f".format(f, gg)}:" +
                        "gb=${"%.4f".format(f, gb)}:" +
                        "ga=0:" +
                        "br=${"%.4f".format(f, br)}:" +
                        "bg=${"%.4f".format(f, bg)}:" +
                        "bb=${"%.4f".format(f, bb)}:" +
                        "ba=0"
            )
        }
        if (cf.invert > 0f) {
            parts.add("negate")
        }

        return parts.joinToString(",")
    }

    // ═══════════════════════════════════════════════════════════
    //  EXECUTE FFMPEG SESSION
    // ═══════════════════════════════════════════════════════════

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
                        Log.e(
                            "FFMPEG_RESULT",
                            "Return code: ${s.returnCode}"
                        )

                        if (ReturnCode.isSuccess(s.returnCode)) {
                            xfadeFallback = null
                            if (outputFile.exists()) {
                                Log.e(
                                    "FFMPEG_RESULT",
                                    "SUCCESS: ${outputFile.absolutePath} " +
                                            "(${outputFile.length()} bytes)"
                                )
                                onSuccess(outputFile)
                            } else {
                                onError("Output not created")
                            }
                        } else if (ReturnCode.isCancel(s.returnCode)) {
                            xfadeFallback = null
                            onError("Export cancelled")
                        } else {
                            val output = s.allLogsAsString ?: ""
                            Log.e("FFMPEG_FAILED", "=== FFmpeg logs ===")
                            Log.e("FFMPEG_FAILED", output)

                            val fb = xfadeFallback
                            if (fb != null) {
                                xfadeFallback = null
                                Log.e(
                                    "FFMPEG_TRANS",
                                    "xfade failed, fallback to concat"
                                )
                                try {
                                    fb.invoke()
                                } catch (e: Throwable) {
                                    Log.e("FFMPEG", "Fallback crashed", e)
                                    onError("Export failed: ${e.message}")
                                }
                            } else {
                                val lastLines = output.lines()
                                    .filter { it.isNotBlank() }
                                    .takeLast(20)
                                    .joinToString("\n")
                                onError("FFmpeg error:\n$lastLines")
                            }
                        }
                    } catch (e: Throwable) {
                        Log.e("FFMPEG", "Callback error", e)
                        onError("Callback error: ${e.message}")
                    }
                },
                { _ -> },
                { statistics ->
                    try {
                        val timeMs = statistics.time
                        if (timeMs > 0) {
                            val p = ((timeMs / 10000.0).coerceIn(0.0, 0.95)).toFloat()
                            onProgress(p)
                        } else {
                            Log.e(
                                "FFMPEG_STATS",
                                "time=0, might not have started yet"
                            )
                        }
                    } catch (t: Throwable) {
                        Log.e("FFMPEG_STATS", "Stats error", t)
                    }
                }
            )
            currentSession = session
        } catch (e: Throwable) {
            Log.e("FFMPEG", "Execute error", e)
            onError("Execute error: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  CANCEL
    // ═══════════════════════════════════════════════════════════

    fun cancel() {
        isCancelled = true
        try {
            currentSession?.let { FFmpegKit.cancel(it.sessionId) }
        } catch (_: Throwable) {
        }
    }
}
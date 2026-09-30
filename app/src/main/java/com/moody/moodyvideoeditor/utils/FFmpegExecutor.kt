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
import java.io.File

class FFmpegExecutor(
    private val context: Context,
    private val onProgress: (Float) -> Unit,
    private val onSuccess: (File) -> Unit,
    private val onError: (String) -> Unit
) {
    private var currentSession: FFmpegSession? = null

    fun export(
        clips: List<EditorClip>,
        allClips: List<EditorClip>,
        outputFile: File,
        targetW: Int = 1280,
        targetH: Int = 720,
        fps: Int = 30,
        bitrateKbps: Int = 8000,
        textSequences: List<TextOverlaySequence> = emptyList()
    ) {
        try {
            if (clips.isEmpty()) {
                onError("❌ No clips to export")
                return
            }

            val localFiles = mutableListOf<File>()
            for (clip in clips) {
                val f = copyUriToCache(clip.uri, "clip_${clip.id}.mp4")
                if (f == null) {
                    onError("❌ Could not read file: ${clip.name}")
                    return
                }
                localFiles.add(f)
            }

            if (localFiles.size == 1) {
                exportSingleClip(
                    clips[0], allClips, localFiles[0], outputFile,
                    targetW, targetH, fps, bitrateKbps, textSequences
                )
            } else {
                exportMultipleClips(
                    clips, allClips, localFiles, outputFile,
                    targetW, targetH, fps, bitrateKbps, textSequences
                )
            }
        } catch (e: Throwable) {
            Log.e("FFMPEG", "Export crash", e)
            onError("❌ Export failed: ${e.message}")
        }
    }

    private fun isImage(clip: EditorClip): Boolean = clip.type.startsWith("image/")

    private fun copyUriToCache(uri: Uri, fileName: String): File? {
        return try {
            val file = File(context.cacheDir, fileName)
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
    //  🆕 SEQUENCE INPUTS — with -itsoffset for chunk positioning
    // ═══════════════════════════════════════════════════════════
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

    // ═══════════════════════════════════════════════════════════
    //  🆕 SIMPLE OVERLAY CHAIN — combined frames have full alpha
    //  Simple overlay=0:0 + enable window per chunk. No tpad. No setpts.
    // ═══════════════════════════════════════════════════════════
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

            // Convert to RGBA
            filterParts.add("[$inIdx:v]format=rgba[$srcLabel]")

            val startS = "%.4f".format(seq.startSec)
            val endS = "%.4f".format(seq.endSec)

            // Simple overlay with enable window
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

    // ═══════════════════════════════════════════════════════════
    //  SINGLE CLIP EXPORT
    // ═══════════════════════════════════════════════════════════
    private fun exportSingleClip(
        clip: EditorClip,
        allClips: List<EditorClip>,
        localFile: File,
        outputFile: File,
        targetW: Int,
        targetH: Int,
        fps: Int,
        bitrateKbps: Int,
        sequences: List<TextOverlaySequence>
    ) {
        try {
            val img = isImage(clip)
            val durSec = ((clip.sourceEndMs - clip.sourceStartMs) / 1000.0)
                .coerceAtLeast(0.1)
            val args = mutableListOf<String>()
            args.add("-y")

            if (img) {
                args.add("-loop"); args.add("1")
                args.add("-framerate"); args.add(fps.toString())
                args.add("-t"); args.add(durSec.toString())
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
                args.add("-t"); args.add(durSec.toString())
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

            val perClipFilters = buildPerClipFilterChain(clip, allClips)
            baseVf.addAll(perClipFilters)

            baseVf.add(
                "scale=$targetW:$targetH:force_original_aspect_ratio=decrease," +
                        "pad=$targetW:$targetH:(ow-iw)/2:(oh-ih)/2,setsar=1"
            )
            filterParts.add("[0:v]${baseVf.joinToString(",")}[base]")

            val outVLabel = buildOverlayChain(
                filterParts, sequences, seqStartIdx, "base"
            )

            val af = mutableListOf<String>()
            if (clip.speed != 1.0f && !img) {
                af.add("atempo=${clip.speed.coerceIn(0.5f, 2.0f)}")
            }
            if (clip.volume != 1.0f) af.add("volume=${clip.volume}")

            if (af.isEmpty()) {
                filterParts.add("[$audioInputIdx:a]anull[outa]")
            } else {
                filterParts.add("[$audioInputIdx:a]${af.joinToString(",")}[outa]")
            }

            args.add("-filter_complex")
            args.add(filterParts.joinToString(";"))
            args.add("-map"); args.add("[$outVLabel]")
            args.add("-map"); args.add("[outa]")
            args.add("-t"); args.add(durSec.toString())

            args.add("-c:v"); args.add("mpeg4")
            args.add("-qscale:v"); args.add("4")
            args.add("-pix_fmt"); args.add("yuv420p")
            args.add("-b:v"); args.add("${bitrateKbps}k")
            args.add("-r"); args.add(fps.toString())
            args.add("-c:a"); args.add("aac")
            args.add("-b:a"); args.add("128k")
            args.add("-movflags"); args.add("+faststart")
            args.add(outputFile.absolutePath)

            execute(args, outputFile)
        } catch (e: Throwable) {
            Log.e("FFMPEG", "Build error", e)
            onError("❌ Build error: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  MULTI-CLIP EXPORT
    // ═══════════════════════════════════════════════════════════
    private fun exportMultipleClips(
        clips: List<EditorClip>,
        allClips: List<EditorClip>,
        localFiles: List<File>,
        outputFile: File,
        targetW: Int,
        targetH: Int,
        fps: Int,
        bitrateKbps: Int,
        sequences: List<TextOverlaySequence>
    ) {
        try {
            val args = mutableListOf<String>()
            args.add("-y")

            // Video inputs — trim in filter
            localFiles.forEach { file ->
                args.add("-i"); args.add(file.absolutePath)
            }

            // Silent audio for images
            val imageAudioInputs = mutableMapOf<Int, Int>()
            var nextInputIdx = clips.size
            clips.forEachIndexed { idx, clip ->
                if (isImage(clip)) {
                    val durSec = ((clip.sourceEndMs - clip.sourceStartMs) / 1000.0)
                        .coerceAtLeast(0.1)
                    args.add("-f"); args.add("lavfi")
                    args.add("-t"); args.add(durSec.toString())
                    args.add("-i"); args.add("anullsrc=r=44100:cl=stereo")
                    imageAudioInputs[idx] = nextInputIdx
                    nextInputIdx++
                }
            }

            // Sequence inputs
            val seqStartIdx = nextInputIdx
            addSequenceInputs(args, sequences)

            val filterParts = mutableListOf<String>()
            val concatInputs = mutableListOf<String>()

            clips.forEachIndexed { idx, clip ->
                val img = isImage(clip)
                val durationSec = ((clip.sourceEndMs - clip.sourceStartMs) / 1000.0)
                    .coerceAtLeast(0.1)

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

                val perClipFilters = buildPerClipFilterChain(clip, allClips)
                vf.addAll(perClipFilters)

                vf.add("scale=$targetW:$targetH:force_original_aspect_ratio=decrease")
                vf.add("pad=$targetW:$targetH:(ow-iw)/2:(oh-ih)/2")
                vf.add("setsar=1")
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
                af.add("aresample=44100")
                filterParts.add("[$aIdx:a]${af.joinToString(",")}[a$idx]")

                concatInputs.add("[v$idx][a$idx]")
            }

            filterParts.add(
                "${concatInputs.joinToString("")}concat=n=${clips.size}:v=1:a=1[concatv][outa]"
            )

            val outVLabel = buildOverlayChain(
                filterParts, sequences, seqStartIdx, "concatv"
            )

            args.add("-filter_complex")
            args.add(filterParts.joinToString(";"))
            args.add("-map"); args.add("[$outVLabel]")
            args.add("-map"); args.add("[outa]")

            args.add("-c:v"); args.add("mpeg4")
            args.add("-qscale:v"); args.add("4")
            args.add("-pix_fmt"); args.add("yuv420p")
            args.add("-b:v"); args.add("${bitrateKbps}k")
            args.add("-r"); args.add(fps.toString())
            args.add("-c:a"); args.add("aac")
            args.add("-b:a"); args.add("128k")
            args.add("-movflags"); args.add("+faststart")
            args.add(outputFile.absolutePath)

            execute(args, outputFile)
        } catch (e: Throwable) {
            Log.e("FFMPEG", "Multi-clip error", e)
            onError("❌ Multi-clip error: ${e.message}")
        }
    }

    private fun buildPerClipFilterChain(
        clip: EditorClip,
        allClips: List<EditorClip>
    ): List<String> {
        val filters = mutableListOf<String>()

        val effectClipsAbove = allClips.filter { e ->
            e.isEffectClip &&
                    e.trackIndex > clip.trackIndex &&
                    e.timelineEndMs > clip.timelineStartMs &&
                    e.timelineStartMs < clip.timelineEndMs
        }.sortedBy { it.trackIndex }

        val motions = effectClipsAbove.mapNotNull { it.effectState?.motion }
        motions.forEach { m ->
            val f = motionToFfmpeg(m)
            if (f.isNotBlank()) filters.add(f)
        }

        val effectColorFilters = effectClipsAbove.mapNotNull { it.effectState?.filters }
        effectColorFilters.forEach { cf ->
            val f = colorFilterValuesToFfmpeg(cf)
            if (f.isNotBlank()) filters.add(f)
        }

        val f = clip.filters
        if (f.brightness != 100f || f.contrast != 100f || f.saturation != 100f) {
            val eqParts = mutableListOf<String>()
            if (f.brightness != 100f) {
                eqParts.add("brightness=${((f.brightness - 100f) / 100f).coerceIn(-1f, 1f)}")
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
            } catch (e: Throwable) {
            }
        }

        return filters
    }

    private fun motionToFfmpeg(m: MotionConfig): String {
        val I = (m.intensity / 100f).coerceIn(0.1f, 3.0f)
        val S = m.speed.coerceIn(0.2f, 10f)

        return when (m.type) {
            "shake" -> {
                val ampX = (6f * I).toInt().coerceAtLeast(2)
                val ampY = (6f * I).toInt().coerceAtLeast(2)
                "crop=iw-$ampX:ih-$ampY:'(iw-ow)/2+$ampX*sin($S*t*37)':'(ih-oh)/2+$ampY*cos($S*t*41)'"
            }

            "bounce" -> {
                val amp = 0.12f * I
                "scale=iw*(1+$amp*abs(sin($S*t*4))):ih*(1+$amp*abs(sin($S*t*4))):eval=frame"
            }

            "pulse" -> {
                val amp = 0.08f * I
                "scale=iw*(1+$amp*sin($S*t*3)):ih*(1+$amp*sin($S*t*3)):eval=frame"
            }

            "zoomPulse" -> {
                val amp = 0.35f * I
                "scale=iw*(1+$amp*0.5*(1+sin($S*t*2))):ih*(1+$amp*0.5*(1+sin($S*t*2))):eval=frame"
            }

            "rotate" -> {
                val amp = 0.05f * I
                "rotate=$S*t*$amp*sin($S*t*2)*PI/180:c=none:ow=iw:oh=ih"
            }

            "glitch" -> {
                "noise=alls=${(20 * I).toInt().coerceIn(5, 80)}:allf=t+u"
            }

            else -> ""
        }
    }

    private fun colorFilterValuesToFfmpeg(cf: ColorFilterValues): String {
        val parts = mutableListOf<String>()
        if (cf.brightness != 100f || cf.contrast != 100f || cf.saturation != 100f) {
            val eqParts = mutableListOf<String>()
            if (cf.brightness != 100f) {
                eqParts.add("brightness=${((cf.brightness - 100f) / 100f).coerceIn(-1f, 1f)}")
            }
            if (cf.contrast != 100f) {
                eqParts.add("contrast=${(cf.contrast / 100f).coerceIn(0f, 2f)}")
            }
            if (cf.saturation != 100f) {
                eqParts.add("saturation=${(cf.saturation / 100f).coerceIn(0f, 3f)}")
            }
            if (eqParts.isNotEmpty()) parts.add("eq=${eqParts.joinToString(":")}")
        }
        if (cf.hue != 0f) parts.add("hue=h=${cf.hue}")
        if (cf.grayscale > 0f) parts.add("format=gray")
        if (cf.invert > 0f) parts.add("negate")
        return parts.joinToString(",")
    }

    private fun execute(args: List<String>, outputFile: File) {
        try {
            val argsArray = args.toTypedArray()
            Log.e("FFMPEG_ARGS", "═══════ ARGS ═══════")
            argsArray.forEach { Log.e("FFMPEG_ARGS", it) }
            Log.e("FFMPEG_ARGS", "═════════════════════")

            val session = FFmpegKit.executeWithArgumentsAsync(
                argsArray,
                { s ->
                    try {
                        if (ReturnCode.isSuccess(s.returnCode)) {
                            if (outputFile.exists()) onSuccess(outputFile)
                            else onError("❌ Output not created")
                        } else if (ReturnCode.isCancel(s.returnCode)) {
                            onError("❌ Export cancelled")
                        } else {
                            val output = s.allLogsAsString ?: ""
                            Log.e("FFMPEG_FULL", output)
                            val lastLines = output.lines()
                                .filter { it.isNotBlank() }
                                .takeLast(10)
                                .joinToString("\n")
                            onError("❌ FFmpeg error:\n$lastLines")
                        }
                    } catch (e: Throwable) {
                        onError("❌ Callback error: ${e.message}")
                    }
                },
                { _ -> },
                { statistics ->
                    try {
                        val timeMs = statistics.time
                        if (timeMs > 0) {
                            val p = ((timeMs / 10000.0).coerceIn(0.0, 0.95)).toFloat()
                            onProgress(p)
                        }
                    } catch (_: Throwable) {
                    }
                }
            )
            currentSession = session
        } catch (e: Throwable) {
            onError("❌ Execute error: ${e.message}")
        }
    }

    fun cancel() {
        try {
            currentSession?.let { FFmpegKit.cancel(it.sessionId) }
        } catch (_: Throwable) {
        }
    }
}
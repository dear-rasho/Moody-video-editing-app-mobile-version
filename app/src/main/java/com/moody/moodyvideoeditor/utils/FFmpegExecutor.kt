package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.moody.moodyvideoeditor.data.EditorClip
import java.io.File

class FFmpegExecutor(
    private val context: Context,
    private val onProgress: (Float) -> Unit,
    private val onSuccess: (File) -> Unit,
    private val onError: (String) -> Unit
) {
    private var currentSession: FFmpegSession? = null

    // ═══════════════════════════════════════════════════════════
    //  PUBLIC — Export
    // ═══════════════════════════════════════════════════════════
    fun export(
        clips: List<EditorClip>,
        outputFile: File,
        videoFilters: String = "",
        audioFilters: String = "",
        targetW: Int = 1280,
        targetH: Int = 720,
        fps: Int = 30,
        bitrateKbps: Int = 8000
    ) {
        if (clips.isEmpty()) {
            onError("No clips to export")
            return
        }

        // ⚠️ CRITICAL: content:// URI ko real file mein copy karo
        val localFiles = mutableListOf<File>()
        for (clip in clips) {
            val f = copyUriToCache(clip.uri, "clip_${clip.id}.mp4")
            if (f == null) {
                onError("Could not read video file")
                return
            }
            localFiles.add(f)
        }

        if (localFiles.size == 1) {
            exportSingleClip(
                clips[0], localFiles[0], outputFile,
                videoFilters, audioFilters, targetW, targetH,
                fps, bitrateKbps
            )
        } else {
            exportMultipleClips(
                clips, localFiles, outputFile,
                videoFilters, audioFilters, targetW, targetH,
                fps, bitrateKbps
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  URI → FILE
    // ═══════════════════════════════════════════════════════════
    private fun copyUriToCache(uri: Uri, fileName: String): File? {
        return try {
            val file = File(context.cacheDir, fileName)
            if (file.exists() && file.length() > 0) return file

            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (file.length() > 0) file else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SINGLE CLIP
    // ═══════════════════════════════════════════════════════════
    private fun exportSingleClip(
        clip: EditorClip,
        localFile: File,
        outputFile: File,
        videoFilters: String,
        audioFilters: String,
        targetW: Int,
        targetH: Int,
        fps: Int,
        bitrateKbps: Int
    ) {
        try {
            val args = mutableListOf<String>()
            args.add("-y")
            args.add("-i")
            args.add(localFile.absolutePath)

            // Trim
            if (clip.sourceStartMs > 0) {
                args.add("-ss")
                args.add((clip.sourceStartMs / 1000.0).toString())
            }
            if (clip.sourceEndMs > clip.sourceStartMs) {
                args.add("-t")
                args.add(((clip.sourceEndMs - clip.sourceStartMs) / 1000.0).toString())
            }

            // Video filters
            val vf = mutableListOf<String>()
            if (clip.speed != 1.0f) {
                vf.add("setpts=${1.0f / clip.speed}*PTS")
            }
            if (videoFilters.isNotBlank()) {
                vf.add(videoFilters)
            }
            // 🆕 Scale + pad to target ratio
            vf.add(
                "scale=$targetW:$targetH:force_original_aspect_ratio=decrease," +
                        "pad=$targetW:$targetH:(ow-iw)/2:(oh-ih)/2,setsar=1"
            )
            args.add("-vf")
            args.add(vf.joinToString(","))

            // Audio filters
            val af = mutableListOf<String>()
            if (clip.speed != 1.0f) {
                af.add("atempo=${clip.speed.coerceIn(0.5f, 2.0f)}")
            }
            if (audioFilters.isNotBlank()) {
                af.add(audioFilters)
            }
            if (af.isNotEmpty()) {
                args.add("-af")
                args.add(af.joinToString(","))
            }

            // ⚠️ Safe codec (h264_mediacodec sab devices pe nahi chalta)
            args.add("-c:v")
            args.add("mpeg4")
            args.add("-b:v")
            args.add("${bitrateKbps}k")
            args.add("-r")
            args.add(fps.toString())
            args.add("-c:a")
            args.add("aac")
            args.add("-b:a")
            args.add("128k")
            args.add("-movflags")
            args.add("+faststart")
            args.add(outputFile.absolutePath)

            execute(args)
        } catch (e: Exception) {
            onError("Build error: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  MULTIPLE CLIPS
    // ═══════════════════════════════════════════════════════════
    private fun exportMultipleClips(
        clips: List<EditorClip>,
        localFiles: List<File>,
        outputFile: File,
        videoFilters: String,
        audioFilters: String,
        targetW: Int,
        targetH: Int,
        fps: Int,
        bitrateKbps: Int
    ) {
        try {
            val args = mutableListOf<String>()
            args.add("-y")

            localFiles.forEach { f ->
                args.add("-i")
                args.add(f.absolutePath)
            }

            val filterParts = mutableListOf<String>()
            val concatInputs = mutableListOf<String>()

            clips.forEachIndexed { idx, clip ->
                val ssSec = clip.sourceStartMs / 1000.0
                val durationSec = (clip.sourceEndMs - clip.sourceStartMs) / 1000.0

                // Video filter per clip
                var vFilter = "[$idx:v]trim=start=$ssSec:duration=$durationSec,setpts=PTS-STARTPTS"
                if (clip.speed != 1.0f) {
                    vFilter += ",setpts=${1.0f / clip.speed}*PTS"
                }
                if (videoFilters.isNotBlank()) {
                    vFilter += ",$videoFilters"
                }
                vFilter += ",scale=$targetW:$targetH:force_original_aspect_ratio=decrease,pad=$targetW:$targetH:(ow-iw)/2:(oh-ih)/2,setsar=1[v$idx]"
                filterParts.add(vFilter)

                // Audio filter per clip
                var aFilter =
                    "[$idx:a]atrim=start=$ssSec:duration=$durationSec,asetpts=PTS-STARTPTS"
                if (clip.speed != 1.0f) {
                    aFilter += ",atempo=${clip.speed.coerceIn(0.5f, 2.0f)}"
                }
                if (audioFilters.isNotBlank()) {
                    aFilter += ",$audioFilters"
                }
                aFilter += "[a$idx]"
                filterParts.add(aFilter)

                concatInputs.add("[v$idx][a$idx]")
            }

            filterParts.add("${concatInputs.joinToString("")}concat=n=${clips.size}:v=1:a=1[outv][outa]")

            args.add("-filter_complex")
            args.add(filterParts.joinToString(";"))
            args.add("-map")
            args.add("[outv]")
            args.add("-map")
            args.add("[outa]")
            args.add("-c:v")
            args.add("mpeg4")
            args.add("-b:v")
            args.add("${bitrateKbps}k")
            args.add("-r")
            args.add(fps.toString())
            args.add("-c:a")
            args.add("aac")
            args.add("-b:a")
            args.add("128k")
            args.add("-movflags")
            args.add("+faststart")
            args.add(outputFile.absolutePath)

            execute(args)
        } catch (e: Exception) {
            onError("Multi-clip error: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  EXECUTE
    // ═══════════════════════════════════════════════════════════
    private fun execute(args: List<String>) {
        try {
            val session = FFmpegKit.executeAsync(
                args.joinToString(" "),
                { s ->
                    if (ReturnCode.isSuccess(s.returnCode)) {
                        onSuccess(File(args.last()))
                    } else if (ReturnCode.isCancel(s.returnCode)) {
                        onError("Export cancelled")
                    } else {
                        val output = s.allLogsAsString ?: "Unknown error"
                        onError("FFmpeg failed: ${output.takeLast(300)}")
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
                    } catch (_: Exception) {
                    }
                }
            )
            currentSession = session
        } catch (e: Exception) {
            onError("Execute error: ${e.message}")
        }
    }

    fun cancel() {
        try {
            currentSession?.let { FFmpegKit.cancel(it.sessionId) }
        } catch (_: Exception) {
        }
    }
}
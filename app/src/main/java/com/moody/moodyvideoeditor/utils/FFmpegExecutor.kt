package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.net.Uri
import android.util.Log
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

    fun export(
        clips: List<EditorClip>,
        outputFile: File,
        videoFilters: String = "",
        audioFilters: String = "",
        targetW: Int = 1280,
        targetH: Int = 720,
        fps: Int = 30,
        bitrateKbps: Int = 8000,
        textFilters: String = ""
    ) {
        if (clips.isEmpty()) {
            onError("No clips to export")
            return
        }

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
                videoFilters, audioFilters, targetW, targetH, fps, bitrateKbps,
                textFilters
            )
        } else {
            exportMultipleClips(
                clips, localFiles, outputFile,
                videoFilters, audioFilters, targetW, targetH, fps, bitrateKbps,
                textFilters
            )
        }
    }

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

    private fun exportSingleClip(
        clip: EditorClip,
        localFile: File,
        outputFile: File,
        videoFilters: String,
        audioFilters: String,
        targetW: Int,
        targetH: Int,
        fps: Int,
        bitrateKbps: Int,
        textFilters: String
    ) {
        try {
            val args = mutableListOf<String>()
            args.add("-y")
            args.add("-i")
            args.add(localFile.absolutePath)

            if (clip.sourceStartMs > 0) {
                args.add("-ss")
                args.add((clip.sourceStartMs / 1000.0).toString())
            }
            if (clip.sourceEndMs > clip.sourceStartMs) {
                args.add("-t")
                args.add(((clip.sourceEndMs - clip.sourceStartMs) / 1000.0).toString())
            }

            val vf = mutableListOf<String>()
            if (clip.speed != 1.0f) {
                vf.add("setpts=${1.0f / clip.speed}*PTS")
            }
            if (videoFilters.isNotBlank()) {
                vf.add(videoFilters)
            }
            vf.add(
                "scale=$targetW:$targetH:force_original_aspect_ratio=decrease," +
                        "pad=$targetW:$targetH:(ow-iw)/2:(oh-ih)/2,setsar=1"
            )
            if (textFilters.isNotBlank()) {
                vf.add(textFilters)
            }
            args.add("-vf")
            args.add(vf.joinToString(","))

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

            args.add("-c:v")
            args.add("mpeg4")   // fallback — hamesha available
            args.add("-preset")
            args.add("medium")
            args.add("-profile:v")
            args.add("baseline")
            args.add("-level")
            args.add("3.1")
            args.add("-pix_fmt")
            args.add("yuv420p")
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

    private fun exportMultipleClips(
        clips: List<EditorClip>,
        localFiles: List<File>,
        outputFile: File,
        videoFilters: String,
        audioFilters: String,
        targetW: Int,
        targetH: Int,
        fps: Int,
        bitrateKbps: Int,
        textFilters: String
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

                var vFilter = "[$idx:v]trim=start=$ssSec:duration=$durationSec,setpts=PTS-STARTPTS"
                if (clip.speed != 1.0f) {
                    vFilter += ",setpts=${1.0f / clip.speed}*PTS"
                }
                if (videoFilters.isNotBlank()) {
                    vFilter += ",$videoFilters"
                }
                vFilter += ",scale=$targetW:$targetH:force_original_aspect_ratio=decrease,pad=$targetW:$targetH:(ow-iw)/2:(oh-ih)/2,setsar=1[v$idx]"
                filterParts.add(vFilter)

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

            filterParts.add("${concatInputs.joinToString("")}concat=n=${clips.size}:v=1:a=1[concatv][outa]")

            val postFilter = if (textFilters.isNotBlank()) {
                filterParts.add("[concatv]$textFilters[outv]")
                "[outv]"
            } else {
                "[concatv]"
            }

            args.add("-filter_complex")
            args.add(filterParts.joinToString(";"))
            args.add("-map")
            args.add(postFilter)
            args.add("-map")
            args.add("[outa]")

            args.add("-c:v")
            args.add("mpeg4")
            args.add("-qscale:v")
            args.add("4")
            args.add("-pix_fmt")
            args.add("yuv420p")
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

    private fun execute(args: List<String>) {
        try {
            val fullCmd = args.joinToString(" ")
            Log.e("FFMPEG_CMD", "========= COMMAND =========")
            Log.e("FFMPEG_CMD", fullCmd)
            Log.e("FFMPEG_CMD", "=============================")

            val session = FFmpegKit.executeAsync(
                fullCmd,
                { s ->
                    if (ReturnCode.isSuccess(s.returnCode)) {
                        onSuccess(File(args.last()))
                    } else if (ReturnCode.isCancel(s.returnCode)) {
                        onError("Export cancelled")
                    } else {
                        val output = s.allLogsAsString ?: ""

                        // Full log to Logcat
                        Log.e("FFMPEG_FULL", output)

                        // Extract last non-blank lines = actual error
                        val lastLines = output.lines()
                            .filter { it.isNotBlank() }
                            .takeLast(6)
                            .joinToString("\n")

                        onError("FFmpeg error:\n$lastLines")
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
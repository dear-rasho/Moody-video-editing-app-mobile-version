package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.moody.moodyvideoeditor.data.EditorClip
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class NativeVideoExporter(
    context: Context,
    private val onProgress: (Float) -> Unit
) {
    private val appContext = context.applicationContext
    @Volatile
    private var activeTransformer: Transformer? = null
    @Volatile
    private var activeContinuation: CancellableContinuation<Unit>? = null
    @Volatile
    private var activePostProcessSessionId: Long? = null

    fun hasAudioTrack(inputUri: Uri): Boolean? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(appContext, inputUri, emptyMap())
            (0 until extractor.trackCount).any { index ->
                extractor.getTrackFormat(index)
                    .getString(android.media.MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            }
        } catch (error: Exception) {
            Log.w("NATIVE_EXPORT", "Could not inspect input audio tracks", error)
            null
        } finally {
            extractor.release()
        }
    }

    suspend fun export(
        inputUri: Uri,
        outputFile: File,
        sourceStartMs: Long,
        sourceEndMs: Long,
        width: Int,
        height: Int,
        fps: Int,
        bitrateKbps: Int,
        includeAudio: Boolean
    ) {
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(sourceStartMs.coerceAtLeast(0L))
            .setEndPositionMs(sourceEndMs.coerceAtLeast(sourceStartMs + 1L))
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(inputUri)
            .setClippingConfiguration(clipping)
            .build()
        val effects = Effects(
            emptyList(),
            listOf(
                Presentation.createForWidthAndHeight(
                    width,
                    height,
                    Presentation.LAYOUT_SCALE_TO_FIT
                )
            )
        )
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setFrameRate(fps)
            .setRemoveAudio(!includeAudio)
            .setEffects(effects)
            .build()

        runTransformer(outputFile, bitrateKbps, "single-item") { transformer ->
            transformer.start(editedMediaItem, outputFile.absolutePath)
        }
        ensureExactOutputDimensions(outputFile, width, height, fps, bitrateKbps)
    }

    suspend fun exportWithComposition(
        clips: List<EditorClip>,
        textSequences: List<TextOverlaySequence>,
        maskSequences: List<MaskSequenceData>,
        outputFile: File,
        width: Int,
        height: Int,
        fps: Int,
        bitrateKbps: Int
    ): Boolean {
        val composition = Media3CompositionBuilder(appContext).buildComposition(
            clips = clips,
            textSequences = textSequences,
            maskSequences = maskSequences,
            outputWidth = width,
            outputHeight = height,
            fps = fps
        ) ?: run {
            Log.i("NATIVE_EXPORT", "Composition rejected; caller should use FFmpeg")
            return false
        }
        val outputComposition = Composition.Builder(composition.sequences)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(
                        Presentation.createForWidthAndHeight(
                            width,
                            height,
                            Presentation.LAYOUT_SCALE_TO_FIT
                        )
                    )
                )
            )
            .build()

        runTransformer(outputFile, bitrateKbps, "composition") { transformer ->
            transformer.start(outputComposition, outputFile.absolutePath)
        }
        ensureExactOutputDimensions(outputFile, width, height, fps, bitrateKbps)
        return true
    }

    private suspend fun ensureExactOutputDimensions(
        outputFile: File,
        width: Int,
        height: Int,
        fps: Int,
        bitrateKbps: Int
    ) {
        val actualDimensions = readVideoDimensions(outputFile)
        if (actualDimensions == (width to height)) return
        check(actualDimensions.first >= width && actualDimensions.second >= height) {
            "Media3 output ${actualDimensions.first}x${actualDimensions.second} is smaller than " +
                    "requested ${width}x$height; cannot crop without upscaling"
        }

        val exactOutput = File(
            outputFile.parentFile,
            "${outputFile.nameWithoutExtension}-exact-${System.nanoTime()}.mp4"
        )
        val cropStartedAtMs = SystemClock.elapsedRealtime()
        try {
            val candidates = VideoEncoderPolicy.codecArgumentCandidates(
                width,
                height,
                fps,
                bitrateKbps
            )
            var lastError: Exception? = null
            for ((index, codecArguments) in candidates.withIndex()) {
                exactOutput.delete()
                val arguments = buildList {
                    addAll(
                        listOf(
                            "-hide_banner",
                            "-i", outputFile.absolutePath,
                            "-map", "0:v:0",
                            "-map", "0:a?",
                            "-vf", "crop=$width:$height:0:0"
                        )
                    )
                    addAll(codecArguments)
                    addAll(
                        listOf(
                            "-b:v", "${bitrateKbps}k",
                            "-c:a", "copy",
                            "-map_metadata", "0",
                            "-map_chapters", "0",
                            "-movflags", "+faststart",
                            "-y", exactOutput.absolutePath
                        )
                    )
                }
                val session = executePostProcess(arguments)
                if (!ReturnCode.isSuccess(session.returnCode)) {
                    val logs = session.allLogsAsString.orEmpty()
                    val error = IllegalStateException(
                        "Could not crop Media3 output with " +
                                "${codecArguments.getOrNull(1)}: ${logs.takeLast(1200)}"
                    )
                    if (index < candidates.lastIndex &&
                        VideoEncoderPolicy.isCodecInitializationFailure(logs)
                    ) {
                        Log.w(
                            "NATIVE_EXPORT",
                            "Crop encoder ${codecArguments.getOrNull(1)} failed; " +
                                    "retrying with ${candidates[index + 1].getOrNull(1)}",
                            error
                        )
                        lastError = error
                        continue
                    }
                    throw error
                }

                val encodedDimensions = readVideoDimensions(exactOutput)
                if (encodedDimensions == (width to height)) {
                    replaceFile(outputFile, exactOutput)
                    Log.i(
                        "NATIVE_EXPORT",
                        "Cropped encoder-aligned output from " +
                                "${actualDimensions.first}x${actualDimensions.second} to " +
                                "${width}x$height using ${codecArguments.getOrNull(1)}; " +
                                "elapsedMs=${SystemClock.elapsedRealtime() - cropStartedAtMs}"
                    )
                    return
                }

                lastError = IllegalStateException(
                    "Crop encode produced ${encodedDimensions.first}x${encodedDimensions.second}; " +
                            "expected ${width}x$height"
                )
                Log.w(
                    "NATIVE_EXPORT",
                    "Crop encoder ${codecArguments.getOrNull(1)} did not preserve the exact " +
                            "dimensions; trying the next encoder"
                )
            }
            throw lastError ?: IllegalStateException(
                "No encoder produced the requested ${width}x$height dimensions"
            )
        } finally {
            exactOutput.delete()
        }
    }

    private suspend fun executePostProcess(arguments: List<String>): FFmpegSession {
        lateinit var session: FFmpegSession
        val result = suspendCancellableCoroutine<FFmpegSession> { continuation ->
            session = FFmpegKit.executeWithArgumentsAsync(arguments.toTypedArray()) {
                activePostProcessSessionId = null
                if (continuation.isActive) continuation.resume(it)
            }
            activePostProcessSessionId = session.sessionId
            continuation.invokeOnCancellation {
                activePostProcessSessionId = null
                FFmpegKit.cancel(session.sessionId)
            }
            if (!continuation.isActive) activePostProcessSessionId = null
        }
        if (activePostProcessSessionId == session.sessionId) {
            activePostProcessSessionId = null
        }
        return result
    }

    private fun readVideoDimensions(file: File): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val width = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: error("Missing video width in ${file.name}")
            val height = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: error("Missing video height in ${file.name}")
            width to height
        } finally {
            retriever.release()
        }
    }

    private fun replaceFile(original: File, replacement: File) {
        val backup = File(original.parentFile, "${original.name}.before-crop")
        check(!backup.exists() || backup.delete()) {
            "Could not clear previous export backup ${backup.name}"
        }
        check(original.renameTo(backup)) {
            "Could not preserve original Media3 output before dimension crop"
        }
        if (!replacement.renameTo(original)) {
            val restored = backup.renameTo(original)
            check(restored) {
                "Could not install cropped export or restore original output"
            }
            error("Could not replace Media3 output with exact-dimension crop")
        }
        if (!backup.delete()) {
            Log.w(
                "NATIVE_EXPORT",
                "Cropped output is ready, but temporary backup ${backup.name} could not be deleted"
            )
        }
    }

    private suspend fun runTransformer(
        outputFile: File,
        bitrateKbps: Int,
        exportKind: String,
        start: (Transformer) -> Unit
    ) = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val handler = Handler(Looper.getMainLooper())
            val progressHolder = ProgressHolder()
            lateinit var progressRunnable: Runnable
            val startedAtMs = SystemClock.elapsedRealtime()

            val listener = object : Transformer.Listener {
                override fun onCompleted(
                    composition: Composition,
                    exportResult: androidx.media3.transformer.ExportResult
                ) {
                    handler.removeCallbacks(progressRunnable)
                    activeTransformer = null
                    activeContinuation = null
                    Log.i(
                        "NATIVE_EXPORT",
                        "Transformer completed: kind=$exportKind, " +
                                "elapsedMs=${SystemClock.elapsedRealtime() - startedAtMs}, " +
                                "outputBytes=${outputFile.length()}"
                    )
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: androidx.media3.transformer.ExportResult,
                    exportException: androidx.media3.transformer.ExportException
                ) {
                    handler.removeCallbacks(progressRunnable)
                    activeTransformer = null
                    activeContinuation = null
                    Log.e(
                        "NATIVE_EXPORT",
                        "Transformer failed: kind=$exportKind, " +
                                "elapsedMs=${SystemClock.elapsedRealtime() - startedAtMs}",
                        exportException
                    )
                    if (continuation.isActive) {
                        continuation.resumeWithException(exportException)
                    }
                }
            }

            val transformer = try {
                Transformer.Builder(appContext)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(
                        DefaultEncoderFactory.Builder(appContext)
                            .setEnableFallback(true)
                            .setRequestedVideoEncoderSettings(
                                VideoEncoderSettings.Builder()
                                    .setBitrate(bitrateKbps * 1000)
                                    .build()
                            )
                            .build()
                    )
                    .addListener(listener)
                    .build()
            } catch (error: Exception) {
                continuation.resumeWithException(error)
                return@suspendCancellableCoroutine
            }
            activeTransformer = transformer
            activeContinuation = continuation

            progressRunnable = object : Runnable {
                override fun run() {
                    if (!continuation.isActive) return
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress.coerceIn(0, 100) / 100f)
                    }
                    handler.postDelayed(this, 250L)
                }
            }
            continuation.invokeOnCancellation {
                handler.post {
                    handler.removeCallbacks(progressRunnable)
                    if (activeTransformer === transformer) {
                        activeTransformer = null
                        activeContinuation = null
                        Log.i("NATIVE_EXPORT", "Cancelling Transformer: kind=$exportKind")
                        transformer.cancel()
                    }
                }
            }

            try {
                Log.i(
                    "NATIVE_EXPORT",
                    "Starting Media3 Transformer: kind=$exportKind, " +
                            "bitrateKbps=$bitrateKbps, output=${outputFile.name}"
                )
                start(transformer)
                handler.post(progressRunnable)
            } catch (error: Exception) {
                activeTransformer = null
                activeContinuation = null
                transformer.removeAllListeners()
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }

    fun cancel() {
        Handler(Looper.getMainLooper()).post {
            activePostProcessSessionId?.let(FFmpegKit::cancel)
            activePostProcessSessionId = null
            val transformer = activeTransformer
            val continuation = activeContinuation
            activeTransformer = null
            activeContinuation = null
            if (transformer != null) {
                Log.i("NATIVE_EXPORT", "Cancel requested")
            }
            transformer?.cancel()
            continuation?.cancel(CancellationException("Native export cancelled"))
        }
    }
}

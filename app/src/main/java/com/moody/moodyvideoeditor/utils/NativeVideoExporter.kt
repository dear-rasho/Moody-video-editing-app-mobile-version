package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.media.MediaExtractor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
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
    ) = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val handler = Handler(Looper.getMainLooper())
            val progressHolder = ProgressHolder()
            lateinit var progressRunnable: Runnable

            val listener = object : Transformer.Listener {
                override fun onCompleted(
                    composition: androidx.media3.transformer.Composition,
                    exportResult: androidx.media3.transformer.ExportResult
                ) {
                    handler.removeCallbacks(progressRunnable)
                    activeTransformer = null
                    activeContinuation = null
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onError(
                    composition: androidx.media3.transformer.Composition,
                    exportResult: androidx.media3.transformer.ExportResult,
                    exportException: androidx.media3.transformer.ExportException
                ) {
                    handler.removeCallbacks(progressRunnable)
                    activeTransformer = null
                    activeContinuation = null
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
                        transformer.cancel()
                    }
                }
            }

            try {
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

                Log.i(
                    "NATIVE_EXPORT",
                    "Starting Media3 hardware pipeline: ${width}x$height@$fps, " +
                            "audio=$includeAudio"
                )
                transformer.start(editedMediaItem, outputFile.absolutePath)
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
            val transformer = activeTransformer
            val continuation = activeContinuation
            activeTransformer = null
            activeContinuation = null
            transformer?.cancel()
            continuation?.cancel(CancellationException("Native export cancelled"))
        }
    }
}

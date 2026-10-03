package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.util.Log
import com.moody.moodyvideoeditor.data.EditorClip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

object VisualizerBitmapRenderer {

    private const val TAG = "VIZ_RENDER"

    // ═══════════════════════════════════════════════════════════
    //  🚀 SPEED BOOST — 60 → 90, parallel 1 → 4
    // ═══════════════════════════════════════════════════════════
    private const val CHUNK_SIZE = 90           // ← pehle 60
    private const val MAX_PARALLEL_CHUNKS = 4   // ← pehle 1

    suspend fun renderCombinedOverlays(
        context: Context,
        visualizerClips: List<EditorClip>,
        allClips: List<EditorClip>,
        rangeStart: Long,
        W: Int,
        H: Int,
        fps: Int,
        totalDurationMs: Long,
        onProgress: (Float) -> Unit = {},
        shouldCancel: () -> Boolean = { false },
        imageFormat: String = "png",
        jpegQuality: Int = 90
    ): List<TextOverlaySequence> = withContext(Dispatchers.Default) {

        if (visualizerClips.isEmpty()) return@withContext emptyList()
        require(W > 0 && H > 0) { "Render dimensions must be positive" }
        require(fps > 0) { "Frame rate must be positive" }
        require(totalDurationMs > 0L) { "Render duration must be positive" }

        val totalFrames = (totalDurationMs.toDouble() * fps / 1000.0)
            .toLong().coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt().coerceAtLeast(1)

        val workingDirectory = File(
            context.cacheDir,
            "render_visualizer_${UUID.randomUUID()}"
        )
        if (!workingDirectory.mkdirs()) {
            throw java.io.IOException("Could not create visualizer-render cache directory")
        }

        val sortedViz = visualizerClips.sortedBy { it.timelineStartMs }
        val renderTrackIndex = visualizerClips.minOfOrNull { it.trackIndex } ?: 0

        val vizBitmaps = mutableMapOf<String, Bitmap?>()
        try {
            sortedViz.forEach { c ->
                val uri = c.visualizer?.imageUri
                if (!uri.isNullOrBlank()) {
                    try {
                        val parsed = android.net.Uri.parse(uri)
                        val bmp = withContext(Dispatchers.IO) {
                            val bounds = BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            context.contentResolver.openInputStream(parsed)?.use {
                                BitmapFactory.decodeStream(it, null, bounds)
                            }
                            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                                null
                            } else {
                                val maxSize = maxOf(W, H) * 2
                                var sampleSize = 1
                                while (
                                    bounds.outWidth / sampleSize > maxSize ||
                                    bounds.outHeight / sampleSize > maxSize
                                ) {
                                    sampleSize *= 2
                                }
                                val options = BitmapFactory.Options().apply {
                                    inSampleSize = sampleSize
                                }
                                context.contentResolver.openInputStream(parsed)?.use {
                                    BitmapFactory.decodeStream(it, null, options)
                                }
                            }
                        }
                        vizBitmaps[c.id] = bmp
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not load visualizer image for ${c.id}", e)
                        vizBitmaps[c.id] = null
                    }
                }
            }

            val emptyPngBytes = ByteArrayOutputStream().use { baos ->
                val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
                try {
                    if (imageFormat == "jpeg") {
                        bmp.eraseColor(Color.BLACK)
                        if (!bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)) {
                            throw java.io.IOException("Could not encode empty visualizer frame")
                        }
                    } else {
                        if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)) {
                            throw java.io.IOException("Could not encode empty visualizer frame")
                        }
                    }
                    baos.toByteArray()
                } finally {
                    bmp.recycle()
                }
            }

            val totalChunks = (totalFrames - 1) / CHUNK_SIZE + 1
            val completedCounter = AtomicInteger(0)
            val nextChunk = AtomicInteger(0)
            val audioById = allClips.associateBy { it.id }

            Log.e(
                TAG, "Rendering $totalFrames frames in $totalChunks chunks " +
                        "(format=$imageFormat)"
            )

            val sequences = coroutineScope {
                val results = arrayOfNulls<TextOverlaySequence>(totalChunks)
                List(minOf(MAX_PARALLEL_CHUNKS, totalChunks)) {
                    async {
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val chunkIdx = nextChunk.getAndIncrement()
                            if (chunkIdx >= totalChunks) break
                            if (shouldCancel()) {
                                throw CancellationException("Visualizer rendering cancelled")
                            }
                            results[chunkIdx] = renderChunk(
                                workingDirectory = workingDirectory,
                                chunkIdx = chunkIdx,
                                chunkSize = CHUNK_SIZE,
                                totalFrames = totalFrames,
                                fps = fps,
                                W = W, H = H,
                                rangeStart = rangeStart,
                                sortedViz = sortedViz,
                                vizBitmaps = vizBitmaps,
                                audioById = audioById,
                                emptyPngBytes = emptyPngBytes,
                                renderTrackIndex = renderTrackIndex,
                                shouldCancel = shouldCancel,
                                imageFormat = imageFormat,
                                jpegQuality = jpegQuality,
                                onFrameDone = {
                                    val done = completedCounter.incrementAndGet()
                                    onProgress(done.toFloat() / totalFrames.toFloat())
                                }
                            )
                        }
                    }
                }.awaitAll()
                results.mapNotNull { it }
            }

            Log.e(TAG, "Done: ${sequences.size} chunks, $totalFrames frames")
            sequences.sortedBy { it.startSec }
        } catch (e: Throwable) {
            workingDirectory.deleteRecursively()
            throw e
        } finally {
            vizBitmaps.values.filterNotNull().distinct().forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    private suspend fun renderChunk(
        workingDirectory: File,
        chunkIdx: Int,
        chunkSize: Int,
        totalFrames: Int,
        fps: Int,
        W: Int,
        H: Int,
        rangeStart: Long,
        sortedViz: List<EditorClip>,
        vizBitmaps: Map<String, Bitmap?>,
        audioById: Map<String, EditorClip>,
        emptyPngBytes: ByteArray,
        renderTrackIndex: Int,
        shouldCancel: () -> Boolean,
        imageFormat: String,
        jpegQuality: Int,
        onFrameDone: () -> Unit
    ): TextOverlaySequence {

        val chunkStart = chunkIdx * chunkSize
        val chunkEnd = (chunkStart + chunkSize).coerceAtMost(totalFrames)
        val chunkFrames = chunkEnd - chunkStart

        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val visibleViz = ArrayList<EditorClip>(sortedViz.size)

        val ext = if (imageFormat == "jpeg") "jpg" else "png"

        try {
            for (i in 0 until chunkFrames) {
                currentCoroutineContext().ensureActive()
                if (shouldCancel()) {
                    throw CancellationException("Visualizer rendering cancelled")
                }
                val globalFrame = chunkStart + i
                val timelineMsLocal = (globalFrame.toLong() * 1000L) / fps
                val timelineMsReal = timelineMsLocal + rangeStart

                val targetFile = File(
                    workingDirectory,
                    "viz_combined_${chunkIdx}_f%05d.$ext".format(i + 1)
                )

                visibleViz.clear()
                sortedViz.forEach { vc ->
                    val isVisible = timelineMsReal >= vc.timelineStartMs &&
                            timelineMsReal < vc.timelineEndMs &&
                            vc.visualizer != null
                    if (isVisible) visibleViz.add(vc)
                }

                when {
                    visibleViz.isEmpty() -> {
                        FileOutputStream(targetFile).use { it.write(emptyPngBytes) }
                    }

                    else -> {
                        if (imageFormat == "jpeg") {
                            canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
                        } else {
                            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                        }

                        visibleViz.forEach { vc ->
                            val state = vc.visualizer ?: return@forEach
                            val imageBmp = vizBitmaps[vc.id]

                            val timelineOffset =
                                (timelineMsReal - vc.timelineStartMs).coerceAtLeast(0L)

                            val audioClip = state.linkedAudioClipId?.let(audioById::get)
                            val sourceStart = audioClip?.sourceStartMs ?: 0L
                            val speed = audioClip?.speed?.coerceAtLeast(0.01f) ?: 1.0f

                            val relativeMs = sourceStart +
                                    (timelineOffset * speed).toLong()
                            val elapsedSec = timelineMsLocal / 1000f

                            VisualizerBitmapHelper.drawVisualizerFrame(
                                canvas = canvas,
                                state = state,
                                relativeMs = relativeMs,
                                elapsedSec = elapsedSec,
                                W = W, H = H,
                                imageBitmap = imageBmp,
                                instanceKey = "export_${vc.id}"
                            )
                        }

                        FileOutputStream(targetFile).use { fos ->
                            val ok = if (imageFormat == "jpeg") {
                                bmp.compress(
                                    Bitmap.CompressFormat.JPEG,
                                    jpegQuality.coerceIn(60, 100),
                                    fos
                                )
                            } else {
                                bmp.compress(
                                    Bitmap.CompressFormat.PNG,
                                    90,
                                    fos
                                )
                            }
                            if (!ok) {
                                throw java.io.IOException("Could not encode visualizer frame")
                            }
                        }
                    }
                }

                onFrameDone()
            }
        } finally {
            bmp.recycle()
        }

        val pattern = File(
            workingDirectory,
            "viz_combined_${chunkIdx}_f%05d.$ext"
        ).absolutePath

        val startSec = chunkStart.toDouble() / fps.toDouble()
        val endSec = chunkEnd.toDouble() / fps.toDouble()

        return TextOverlaySequence(
            pattern = pattern,
            frameCount = chunkFrames,
            fps = fps,
            startNumber = 1,
            startSec = startSec,
            endSec = endSec,
            trackIndex = renderTrackIndex,
            workingDirectory = workingDirectory,
            imageFormat = imageFormat
        )
    }
}
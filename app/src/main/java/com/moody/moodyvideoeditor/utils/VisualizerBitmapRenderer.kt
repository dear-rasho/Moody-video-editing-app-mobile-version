package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.util.Log
import com.moody.moodyvideoeditor.data.EditorClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

object VisualizerBitmapRenderer {

    private const val TAG = "VIZ_RENDER"
    private const val CHUNK_SIZE = 60

    suspend fun renderCombinedOverlays(
        context: Context,
        visualizerClips: List<EditorClip>,
        allClips: List<EditorClip>,
        rangeStart: Long,
        W: Int,
        H: Int,
        fps: Int,
        totalDurationMs: Long,
        onProgress: (Float) -> Unit = {}
    ): List<TextOverlaySequence> = withContext(Dispatchers.Default) {

        if (visualizerClips.isEmpty()) return@withContext emptyList()

        val totalFrames = ((totalDurationMs * fps) / 1000L)
            .toInt().coerceAtLeast(1)

        try {
            context.cacheDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("viz_combined_") && f.name.endsWith(".png")) {
                    f.delete()
                }
            }
        } catch (_: Exception) {
        }

        val sortedViz = visualizerClips.sortedBy { it.timelineStartMs }
        // 🆕 Compute min track index for correct composite layering
        val renderTrackIndex = visualizerClips.minOfOrNull { it.trackIndex } ?: 0

        // Preload images
        val vizBitmaps = mutableMapOf<String, Bitmap?>()
        sortedViz.forEach { c ->
            val uri = c.visualizer?.imageUri
            if (!uri.isNullOrBlank()) {
                try {
                    val parsed = android.net.Uri.parse(uri)
                    val bmp = context.contentResolver.openInputStream(parsed)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                    vizBitmaps[c.id] = bmp
                } catch (_: Throwable) {
                    vizBitmaps[c.id] = null
                }
            }
        }

        // Empty transparent PNG cache
        val emptyPngBytes: ByteArray = ByteArrayOutputStream().use { baos ->
            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
            bmp.recycle()
            baos.toByteArray()
        }

        val totalChunks = (totalFrames + CHUNK_SIZE - 1) / CHUNK_SIZE
        val completedCounter = AtomicInteger(0)

        Log.e(TAG, "Rendering $totalFrames frames in $totalChunks chunks")

        val sequences = coroutineScope {
            (0 until totalChunks).map { chunkIdx ->
                async(Dispatchers.Default) {
                    renderChunk(
                        context = context,
                        chunkIdx = chunkIdx,
                        chunkSize = CHUNK_SIZE,
                        totalFrames = totalFrames,
                        fps = fps,
                        W = W, H = H,
                        rangeStart = rangeStart,
                        sortedViz = sortedViz,
                        vizBitmaps = vizBitmaps,
                        allClips = allClips,
                        emptyPngBytes = emptyPngBytes,
                        renderTrackIndex = renderTrackIndex,   // 🆕 add this
                        onFrameDone = {
                            val done = completedCounter.incrementAndGet()
                            onProgress(done.toFloat() / totalFrames.toFloat())
                        }
                    )
                }
            }.awaitAll()
        }

        Log.e(TAG, "Done: ${sequences.size} chunks, $totalFrames frames")
        sequences.sortedBy { it.startSec }
    }

    private fun renderChunk(
        context: Context,
        chunkIdx: Int,
        chunkSize: Int,
        totalFrames: Int,
        fps: Int,
        W: Int,
        H: Int,
        rangeStart: Long,
        sortedViz: List<EditorClip>,
        vizBitmaps: Map<String, Bitmap?>,
        allClips: List<EditorClip>,
        emptyPngBytes: ByteArray,
        renderTrackIndex: Int,          // 🆕 add this
        onFrameDone: () -> Unit
    ): TextOverlaySequence {

        val chunkStart = chunkIdx * chunkSize
        val chunkEnd = (chunkStart + chunkSize).coerceAtMost(totalFrames)
        val chunkFrames = chunkEnd - chunkStart

        // Reuse ONE bitmap for whole chunk
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        var previousFile: File? = null
        var previousVisibleKey: Set<String> = emptySet()

        for (i in 0 until chunkFrames) {
            val globalFrame = chunkStart + i
            val timelineMsLocal = (globalFrame.toLong() * 1000L) / fps
            val timelineMsReal = timelineMsLocal + rangeStart

            val targetFile = File(
                context.cacheDir,
                "viz_combined_${chunkIdx}_f%05d.png".format(i + 1)
            )

            // Find visible visualizers
            val visibleViz = sortedViz.filter { vc ->
                timelineMsReal >= vc.timelineStartMs &&
                        timelineMsReal < vc.timelineEndMs &&
                        vc.visualizer != null
            }

            when {
                // Empty frame
                visibleViz.isEmpty() -> {
                    FileOutputStream(targetFile).use { it.write(emptyPngBytes) }
                }

                // Visualizers are always animated → render
                else -> {
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                    visibleViz.forEach { vc ->
                        val state = vc.visualizer ?: return@forEach
                        val imageBmp = vizBitmaps[vc.id]

                        val timelineOffset =
                            (timelineMsReal - vc.timelineStartMs).coerceAtLeast(0L)

                        val audioClip = allClips.firstOrNull {
                            it.id == vc.visualizer?.linkedAudioClipId
                        }
                        val sourceStart = audioClip?.sourceStartMs ?: 0L
                        val speed = audioClip?.speed?.coerceAtLeast(0.01f) ?: 1.0f

                        val relativeMs = sourceStart +
                                (timelineOffset * speed).toLong()
                        val elapsedSec = timelineMsLocal / 1000f

                        try {
                            VisualizerBitmapHelper.drawVisualizerFrame(
                                canvas = canvas,
                                state = state,
                                relativeMs = relativeMs,
                                elapsedSec = elapsedSec,
                                W = W, H = H,
                                imageBitmap = imageBmp,
                                instanceKey = "export_${vc.id}"
                            )
                        } catch (e: Throwable) {
                            Log.e(TAG, "Visualizer frame failed", e)
                        }
                    }

                    FileOutputStream(targetFile).use { fos ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 85, fos)
                    }
                }
            }

            previousFile = targetFile
            previousVisibleKey = visibleViz.map { it.id }.toSet()

            onFrameDone()
        }

        bmp.recycle()

        val pattern = File(
            context.cacheDir,
            "viz_combined_${chunkIdx}_f%05d.png"
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
            trackIndex = renderTrackIndex      // 🆕 add this
        )
    }
}
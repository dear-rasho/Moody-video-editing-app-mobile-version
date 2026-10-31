package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.graphics.Bitmap
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

object DustBitmapRenderer {

    private const val TAG = "DUST_RENDER"
    private const val CHUNK_SIZE = 60
    private const val MAX_PARALLEL_CHUNKS = 4

    suspend fun renderDustSequences(
        context: Context,
        dustClips: List<EditorClip>,
        W: Int, H: Int,
        fps: Int,
        totalDurationMs: Long,
        onProgress: (Float) -> Unit = {},
        shouldCancel: () -> Boolean = { false }
    ): List<TextOverlaySequence> = withContext(Dispatchers.Default) {

        if (dustClips.isEmpty()) return@withContext emptyList()

        val totalFrames = ((totalDurationMs.toDouble() * fps) / 1000.0)
            .toLong().coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt().coerceAtLeast(1)

        val workingDir = File(
            context.cacheDir,
            "render_dust_${UUID.randomUUID()}"
        )
        if (!workingDir.mkdirs()) {
            throw java.io.IOException("Could not create dust render dir")
        }

        try {
            val emptyPng = ByteArrayOutputStream().use { baos ->
                val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
                try {
                    bmp.eraseColor(Color.TRANSPARENT)
                    if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)) {
                        throw java.io.IOException("Empty PNG encode failed")
                    }
                    baos.toByteArray()
                } finally {
                    bmp.recycle()
                }
            }

            val totalChunks = (totalFrames + CHUNK_SIZE - 1) / CHUNK_SIZE
            val completedCounter = AtomicInteger(0)
            val nextChunk = AtomicInteger(0)
            val renderTrackIndex = dustClips.minOfOrNull { it.trackIndex } ?: 0

            Log.e(TAG, "Rendering $totalFrames frames in $totalChunks chunks")

            val sequences = coroutineScope {
                val results = arrayOfNulls<TextOverlaySequence>(totalChunks)
                List(minOf(MAX_PARALLEL_CHUNKS, totalChunks)) {
                    async {
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val chunkIdx = nextChunk.getAndIncrement()
                            if (chunkIdx >= totalChunks) break
                            if (shouldCancel()) {
                                throw CancellationException("Dust cancelled")
                            }
                            results[chunkIdx] = renderChunk(
                                workingDir = workingDir,
                                chunkIdx = chunkIdx,
                                chunkSize = CHUNK_SIZE,
                                totalFrames = totalFrames,
                                fps = fps,
                                W = W, H = H,
                                dustClips = dustClips,
                                emptyPng = emptyPng,
                                renderTrackIndex = renderTrackIndex,
                                shouldCancel = shouldCancel,
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

            Log.e(TAG, "Done: ${sequences.size} chunks")
            sequences.sortedBy { it.startSec }
        } catch (e: Throwable) {
            workingDir.deleteRecursively()
            throw e
        }
    }

    private suspend fun renderChunk(
        workingDir: File,
        chunkIdx: Int,
        chunkSize: Int,
        totalFrames: Int,
        fps: Int,
        W: Int, H: Int,
        dustClips: List<EditorClip>,
        emptyPng: ByteArray,
        renderTrackIndex: Int,
        shouldCancel: () -> Boolean,
        onFrameDone: () -> Unit
    ): TextOverlaySequence {

        val chunkStart = chunkIdx * chunkSize
        val chunkEnd = (chunkStart + chunkSize).coerceAtMost(totalFrames)
        val chunkFrames = chunkEnd - chunkStart

        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        try {
            for (i in 0 until chunkFrames) {
                currentCoroutineContext().ensureActive()
                if (shouldCancel()) throw CancellationException("cancelled")

                val globalFrame = chunkStart + i
                val timelineMs = (globalFrame.toLong() * 1000L) / fps

                val targetFile = File(
                    workingDir,
                    "dust_${chunkIdx}_f%05d.png".format(i + 1)
                )

                val activeClips = dustClips.filter { clip ->
                    timelineMs >= clip.timelineStartMs &&
                            timelineMs < clip.timelineEndMs &&
                            clip.effectState?.dust != null
                }

                if (activeClips.isEmpty()) {
                    FileOutputStream(targetFile).use { it.write(emptyPng) }
                    onFrameDone()
                    continue
                }

                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                var drewSomething = false

                activeClips.forEach { clip ->
                    val cfg = clip.effectState?.dust ?: return@forEach
                    val localMs = (timelineMs - clip.timelineStartMs)
                        .coerceAtLeast(0L)
                    val localSec = localMs / 1000f
                    val layerOpacity = KeyframeStore.layerOpacityAt(
                        clip.keyframes, clip.layerOpacity, localSec
                    ) / 100f
                    if (layerOpacity <= 0f) return@forEach

                    DustEngine.drawOnCanvas(
                        canvas = canvas,
                        cfg = cfg.copy(opacity = cfg.opacity * layerOpacity),
                        localTimeSec = localSec,
                        W = W, H = H
                    )
                    drewSomething = true
                }

                FileOutputStream(targetFile).use { fos ->
                    if (drewSomething) {
                        if (!bmp.compress(Bitmap.CompressFormat.PNG, 90, fos)) {
                            throw java.io.IOException("Frame encode failed")
                        }
                    } else {
                        fos.write(emptyPng)
                    }
                }

                onFrameDone()
            }
        } finally {
            bmp.recycle()
        }

        val pattern = File(
            workingDir,
            "dust_${chunkIdx}_f%05d.png"
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
            workingDirectory = workingDir,
            imageFormat = "png"
        )
    }
}
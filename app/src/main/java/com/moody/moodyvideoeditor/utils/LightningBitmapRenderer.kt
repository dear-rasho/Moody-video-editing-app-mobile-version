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

// ═══════════════════════════════════════════════════════════════
//  LIGHTNING BITMAP RENDERER
//  Renders a lightning-bolt PNG sequence for export overlay.
//  One PNG per video frame — transparent when flash is off.
// ═══════════════════════════════════════════════════════════════

object LightningBitmapRenderer {

    private const val TAG = "LIGHTNING_RENDER"
    private const val CHUNK_SIZE = 60
    private const val MAX_PARALLEL_CHUNKS = 4

    suspend fun renderLightningStrikes(
        context: Context,
        lightningClips: List<EditorClip>,
        W: Int,
        H: Int,
        fps: Int,
        totalDurationMs: Long,
        onProgress: (Float) -> Unit = {},
        shouldCancel: () -> Boolean = { false }
    ): List<TextOverlaySequence> = withContext(Dispatchers.Default) {

        if (lightningClips.isEmpty()) return@withContext emptyList()

        val totalFrames = ((totalDurationMs.toDouble() * fps) / 1000.0)
            .toLong().coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt().coerceAtLeast(1)

        val workingDir = File(
            context.cacheDir,
            "render_lightning_${UUID.randomUUID()}"
        )
        if (!workingDir.mkdirs()) {
            throw java.io.IOException("Could not create lightning render dir")
        }

        try {
            // Empty PNG (fully transparent)
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
            val renderTrackIndex = lightningClips.minOfOrNull { it.trackIndex } ?: 0

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
                                throw CancellationException("Lightning cancelled")
                            }
                            results[chunkIdx] = renderChunk(
                                workingDir = workingDir,
                                chunkIdx = chunkIdx,
                                chunkSize = CHUNK_SIZE,
                                totalFrames = totalFrames,
                                fps = fps,
                                W = W, H = H,
                                lightningClips = lightningClips,
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

            Log.e(TAG, "Done: ${sequences.size} chunks, $totalFrames frames")
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
        lightningClips: List<EditorClip>,
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
                if (shouldCancel()) {
                    throw CancellationException("Lightning cancelled")
                }

                val globalFrame = chunkStart + i
                val timelineMs = (globalFrame.toLong() * 1000L) / fps

                val targetFile = File(
                    workingDir,
                    "light_${chunkIdx}_f%05d.png".format(i + 1)
                )

                // Find any active lightning effect clip at this time
                val activeClips = lightningClips.filter { clip ->
                    timelineMs >= clip.timelineStartMs &&
                            timelineMs < clip.timelineEndMs &&
                            clip.effectState?.lightning != null
                }

                if (activeClips.isEmpty()) {
                    FileOutputStream(targetFile).use { it.write(emptyPng) }
                    onFrameDone()
                    continue
                }

                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                var drewSomething = false

                activeClips.forEach { clip ->
                    val cfg = clip.effectState?.lightning ?: return@forEach
                    val localMs = (timelineMs - clip.timelineStartMs).coerceAtLeast(0L)
                    val localSec = localMs / 1000f
                    val layerOpacity = KeyframeStore.layerOpacityAt(
                        clip.keyframes, clip.layerOpacity, localSec
                    ) / 100f
                    if (layerOpacity <= 0f) return@forEach
                    val effectiveConfig = cfg.copy(intensity = cfg.intensity * layerOpacity)

                    val flash = LightningEngine.flashAt(effectiveConfig, localSec)
                    if (flash <= 0.01f) return@forEach

                    // ── Soft ambient screen flash (matches preview) ──
                    val flashColor = effectiveConfig.color
                    val r = ((flashColor shr 16) and 0xFF).toInt()
                    val g = ((flashColor shr 8) and 0xFF).toInt()
                    val b = (flashColor and 0xFF).toInt()
                    val a = (flash * 0.25f * 255).toInt().coerceIn(0, 255)

                    val ambientPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(a, r, g, b)
                    }
                    canvas.drawRect(
                        0f, 0f, W.toFloat(), H.toFloat(), ambientPaint
                    )

                    // ── Bolt lines with glow ──
                    val freq = effectiveConfig.frequency.coerceIn(0.2f, 5f)
                    val cycleIdx = kotlin.math.floor(localSec * freq).toInt()

                    LightningEngine.drawBoltAndroid(
                        canvas = canvas,
                        cfg = effectiveConfig,
                        flashStrength = flash,
                        cycleIndex = cycleIdx,
                        W = W, H = H
                    )
                    drewSomething = true
                }
                FileOutputStream(targetFile).use { fos ->
                    if (drewSomething) {
                        if (!bmp.compress(Bitmap.CompressFormat.PNG, 90, fos)) {
                            throw java.io.IOException("Lightning frame encode failed")
                        }
                    } else {
                        // No visible bolt in this frame — use empty PNG for speed
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
            "light_${chunkIdx}_f%05d.png"
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
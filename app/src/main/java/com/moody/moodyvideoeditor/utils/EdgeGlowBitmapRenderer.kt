package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.moody.moodyvideoeditor.data.EditorClip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.ceil

object EdgeGlowBitmapRenderer {
    suspend fun render(
        context: Context,
        clips: List<EditorClip>,
        width: Int,
        height: Int,
        fps: Int,
        rangeStartMs: Long,
        rangeEndMs: Long,
        shouldCancel: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {}
    ): List<TextOverlaySequence> = withContext(Dispatchers.Default) {
        if (clips.isEmpty()) return@withContext emptyList()
        require(width > 0 && height > 0) { "Render dimensions must be positive" }
        require(fps > 0) { "Frame rate must be positive" }
        require(rangeEndMs > rangeStartMs) { "Render range must be positive" }

        val visibleClips = clips.mapNotNull { clip ->
            val config = clip.effectState?.edgeGlow ?: return@mapNotNull null
            if (!clip.isEffectClip || config.intensity <= 0f) return@mapNotNull null
            val startMs = maxOf(clip.timelineStartMs, rangeStartMs)
            val endMs = minOf(clip.timelineEndMs, rangeEndMs)
            if (endMs <= startMs) null else Triple(clip, config, startMs to endMs)
        }.sortedWith(compareBy({ it.first.trackIndex }, { it.third.first }))
        if (visibleClips.isEmpty()) return@withContext emptyList()

        val root = File(context.cacheDir, "render_edge_glow_${UUID.randomUUID()}")
        if (!root.mkdirs()) throw java.io.IOException("Could not create edge-glow render directory")

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = ComposeCanvas(bitmap.asImageBitmap())
        val drawScope = CanvasDrawScope()
        val totalFrames = visibleClips.sumOf { (_, config, interval) ->
            if (config.animation == com.moody.moodyvideoeditor.data.EdgeGlowAnimation.STATIC) 1L
            else ceil((interval.second - interval.first) * fps / 1000.0).toLong().coerceAtLeast(1L)
        }.coerceAtLeast(1L)
        var completedFrames = 0L

        try {
            val sequences = visibleClips.mapIndexed { index, (clip, config, interval) ->
                if (shouldCancel()) throw CancellationException("Edge-glow rendering cancelled")
                val startMs = interval.first
                val endMs = interval.second
                val static = config.animation == com.moody.moodyvideoeditor.data.EdgeGlowAnimation.STATIC
                val frameCount = if (static) 1 else {
                    ceil((endMs - startMs) * fps / 1000.0).toInt().coerceAtLeast(1)
                }
                val directory = File(root, "glow_$index")
                if (!directory.mkdirs()) {
                    throw java.io.IOException("Could not create edge-glow frame directory")
                }
                val pattern = File(directory, "frame_%05d.png")

                for (frame in 0 until frameCount) {
                    if (shouldCancel()) throw CancellationException("Edge-glow rendering cancelled")
                    bitmap.eraseColor(Color.TRANSPARENT)
                    val frameTimeSec = (startMs + frame * 1000.0 / fps).toFloat() / 1000f
                    drawScope.draw(
                        density = Density(1f, 1f),
                        layoutDirection = LayoutDirection.Ltr,
                        canvas = canvas,
                        size = Size(width.toFloat(), height.toFloat())
                    ) {
                        drawEdgeGlow(config, frameTimeSec)
                    }
                    val output = File(directory, "frame_%05d.png".format(frame + 1))
                    FileOutputStream(output).use {
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                            throw java.io.IOException("Could not encode edge-glow frame")
                        }
                    }
                    completedFrames++
                    onProgress(completedFrames.toFloat() / totalFrames)
                }

                TextOverlaySequence(
                    pattern = if (static) File(directory, "frame_00001.png").absolutePath
                    else pattern.absolutePath,
                    frameCount = frameCount,
                    fps = fps,
                    startNumber = 1,
                    startSec = (startMs - rangeStartMs) / 1000.0,
                    endSec = (endMs - rangeStartMs) / 1000.0,
                    trackIndex = Int.MAX_VALUE / 2 + index,
                    workingDirectory = root,
                    imageFormat = "png",
                    isStaticImage = static
                )
            }
            sequences
        } catch (error: Throwable) {
            root.deleteRecursively()
            throw error
        } finally {
            bitmap.recycle()
        }
    }
}

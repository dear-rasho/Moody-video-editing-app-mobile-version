package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.SpotlightState
import com.moody.moodyvideoeditor.ui.features.drawSpotlight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object SpotlightBitmapRenderer {
    suspend fun render(
        context: Context,
        clips: List<EditorClip>,
        width: Int,
        height: Int,
        fps: Int,
        totalDurationMs: Long,
        onProgress: (Float) -> Unit = {},
        shouldCancel: () -> Boolean = { false }
    ): List<TextOverlaySequence> = withContext(Dispatchers.Default) {
        if (clips.isEmpty()) return@withContext emptyList()
        require(width > 0 && height > 0) { "Render dimensions must be positive" }
        require(fps > 0) { "Frame rate must be positive" }
        require(totalDurationMs > 0L) { "Render duration must be positive" }

        val root = File(context.cacheDir, "render_spotlight_${UUID.randomUUID()}")
        if (!root.mkdirs()) throw java.io.IOException("Could not create spotlight-render directory")

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = ComposeCanvas(bitmap.asImageBitmap())
        val drawScope = CanvasDrawScope()
        val sequences = mutableListOf<TextOverlaySequence>()
        var completedWork = 0
        val totalWork = clips.sumOf { clip ->
            if (clip.keyframes.keys.any { it.startsWith("spotlight.") }) {
                ((clip.durationMs.coerceAtMost(totalDurationMs) * fps) / 1000L)
                    .toInt().coerceAtLeast(1)
            } else 1
        }.coerceAtLeast(1)

        try {
            clips.sortedWith(compareBy({ it.trackIndex }, { it.timelineStartMs }))
                .forEachIndexed { clipIndex, clip ->
                    val directory = File(root, "spotlight_$clipIndex")
                    if (!directory.mkdirs()) {
                        throw java.io.IOException("Could not create spotlight frame directory")
                    }
                    val hasAnimation = clip.keyframes.keys.any { it.startsWith("spotlight.") }
                    val startSec = (clip.timelineStartMs / 1000.0).coerceAtLeast(0.0)
                    val endSec = (clip.timelineEndMs / 1000.0)
                        .coerceAtMost(totalDurationMs / 1000.0)
                    val visibleDurationMs = ((endSec - startSec) * 1000.0)
                        .toLong().coerceAtLeast(1L)
                    val frameCount = if (hasAnimation) {
                        ((visibleDurationMs * fps) / 1000L)
                            .toInt().coerceAtLeast(1)
                    } else 1
                    val pattern = File(directory, if (hasAnimation) "frame_%05d.png" else "spotlight.png")

                    for (frame in 0 until frameCount) {
                        if (shouldCancel()) throw CancellationException("Spotlight render cancelled")
                        val localTimeSec = frame.toFloat() / fps
                        val spotlight = SpotlightAnimation.sample(
                            clip.spotlight ?: SpotlightState(),
                            clip.keyframes,
                            localTimeSec
                        )
                        bitmap.eraseColor(AndroidColor.TRANSPARENT)
                        drawScope.draw(
                            density = Density(1f, 1f),
                            layoutDirection = LayoutDirection.Ltr,
                            canvas = canvas,
                            size = Size(width.toFloat(), height.toFloat())
                        ) {
                            drawSpotlight(spotlight)
                        }
                        LayerShadowRenderer.applyToBitmap(
                            android.graphics.Canvas(bitmap),
                            clip.layerShadow,
                            width,
                            height
                        ) { shadowCanvas ->
                            shadowCanvas.drawBitmap(bitmap, 0f, 0f, null)
                        }
                        val imageFile = if (hasAnimation) {
                            File(directory, "frame_%05d.png".format(frame + 1))
                        } else pattern
                        FileOutputStream(imageFile).use { output ->
                            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                                throw java.io.IOException("Could not encode spotlight image")
                            }
                        }
                        completedWork++
                        onProgress(completedWork.toFloat() / totalWork)
                    }
                    sequences += TextOverlaySequence(
                        pattern = pattern.absolutePath,
                        frameCount = frameCount,
                        fps = fps,
                        startNumber = 1,
                        startSec = startSec,
                        endSec = endSec,
                        trackIndex = clip.trackIndex,
                        workingDirectory = root,
                        imageFormat = "png",
                        blendMode = "addition",
                        isStaticImage = !hasAnimation
                    )
                }
            sequences
        } catch (error: Exception) {
            root.deleteRecursively()
            throw error
        } finally {
            bitmap.recycle()
        }
    }
}

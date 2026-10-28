package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.OverlayState
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
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Canvas as ComposeCanvas

data class TextOverlaySequence(
    val pattern: String,
    val frameCount: Int,
    val fps: Int,
    val startNumber: Int,
    val startSec: Double,
    val endSec: Double,
    val trackIndex: Int = 0,
    val workingDirectory: File? = null,
    val imageFormat: String = "png",
    val blendMode: String = "normal",
    val isStaticImage: Boolean = false
)

private data class OverlayRenderBuffer(
    val bitmap: Bitmap,
    val androidCanvas: Canvas,
    val composeCanvas: ComposeCanvas,
    val drawScope: CanvasDrawScope
)

object TextBitmapRenderer {

    private const val REFERENCE_WIDTH_PX = 720f
    private const val TAG = "TEXT_RENDER"
    private const val CHUNK_SIZE = 60
    private const val MAX_PARALLEL_CHUNKS = 4

    suspend fun renderOverlaysByTrack(
        context: Context,
        textClips: List<EditorClip>,
        imageClips: List<EditorClip> = emptyList(),
        overlayClips: List<EditorClip> = emptyList(),
        W: Int,
        H: Int,
        fps: Int,
        totalDurationMs: Long,
        onProgress: (Float) -> Unit = {},
        shouldCancel: () -> Boolean = { false },
        imageFormat: String = "png",
        jpegQuality: Int = 90
    ): List<TextOverlaySequence> {
        val trackIndices = (
                textClips.map { it.trackIndex } +
                        imageClips.map { it.trackIndex } +
                        overlayClips.map { it.trackIndex }
                ).distinct().sorted()
        if (trackIndices.isEmpty()) return emptyList()

        val rendered = mutableListOf<TextOverlaySequence>()
        try {
            trackIndices.forEachIndexed { index, trackIndex ->
                if (shouldCancel()) throw CancellationException("Text rendering cancelled")
                val trackSequences = renderCombinedOverlays(
                    context = context,
                    textClips = textClips.filter { it.trackIndex == trackIndex },
                    imageClips = imageClips.filter { it.trackIndex == trackIndex },
                    overlayClips = overlayClips.filter { it.trackIndex == trackIndex },
                    W = W,
                    H = H,
                    fps = fps,
                    totalDurationMs = totalDurationMs,
                    onProgress = { progress ->
                        onProgress((index + progress) / trackIndices.size)
                    },
                    shouldCancel = shouldCancel,
                    imageFormat = imageFormat,
                    jpegQuality = jpegQuality
                )
                rendered += trackSequences
            }
        } catch (error: Exception) {
            rendered.mapNotNull { it.workingDirectory }.distinct().forEach { directory ->
                directory.deleteRecursively()
            }
            throw error
        }
        return rendered
    }

    suspend fun renderCombinedOverlays(
        context: Context,
        textClips: List<EditorClip>,
        imageClips: List<EditorClip> = emptyList(),
        overlayClips: List<EditorClip> = emptyList(),
        W: Int,
        H: Int,
        fps: Int,
        totalDurationMs: Long,
        onProgress: (Float) -> Unit = {},
        shouldCancel: () -> Boolean = { false },
        imageFormat: String = "png",
        jpegQuality: Int = 90
    ): List<TextOverlaySequence> = withContext(Dispatchers.Default) {

        if (textClips.isEmpty() && imageClips.isEmpty() && overlayClips.isEmpty()) {
            return@withContext emptyList()
        }
        require(W > 0 && H > 0) { "Render dimensions must be positive" }
        require(fps > 0) { "Frame rate must be positive" }
        require(totalDurationMs > 0L) { "Render duration must be positive" }

        val totalFrames = ((totalDurationMs * fps) / 1000L)
            .toInt().coerceAtLeast(1)

        val workingDirectory = File(
            context.cacheDir,
            "render_text_${UUID.randomUUID()}"
        )
        if (!workingDirectory.mkdirs()) {
            throw java.io.IOException("Could not create text-render cache directory")
        }

        val sortedTextClips = textClips.sortedWith(
            compareBy({ it.trackIndex }, { it.timelineStartMs })
        )
        val sortedImageClips = imageClips.sortedWith(
            compareBy({ it.trackIndex }, { it.timelineStartMs })
        )
        val sortedOverlayClips = overlayClips.sortedWith(
            compareBy({ it.trackIndex }, { it.timelineStartMs })
        )
        val renderTrackIndex = (
                textClips.map { it.trackIndex } +
                        imageClips.map { it.trackIndex } +
                        overlayClips.map { it.trackIndex }
                ).minOrNull() ?: 0

        val imageBitmaps = mutableMapOf<String, Bitmap>()

        try {
            sortedImageClips.forEach { clip ->
                imageBitmaps[clip.id] = withContext(Dispatchers.IO) {
                    decodeImage(context, clip, W, H)
                }
            }

            val emptyBytes = ByteArrayOutputStream().use { baos ->
                val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
                try {
                    if (imageFormat == "jpeg") {
                        bmp.eraseColor(Color.BLACK)
                        if (!bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)) {
                            throw java.io.IOException("Could not encode empty text frame")
                        }
                    } else {
                        if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)) {
                            throw java.io.IOException("Could not encode empty text frame")
                        }
                    }
                    baos.toByteArray()
                } finally {
                    bmp.recycle()
                }
            }

            val totalChunks = (totalFrames + CHUNK_SIZE - 1) / CHUNK_SIZE
            val completedCounter = AtomicInteger(0)
            val nextChunk = AtomicInteger(0)
            val pixelCount = W.toLong() * H
            val parallelChunkLimit = when {
                pixelCount >= 3840L * 2160L -> 1
                pixelCount >= 1920L * 1080L -> 2
                else -> MAX_PARALLEL_CHUNKS
            }

            val sequences = coroutineScope {
                val results = arrayOfNulls<TextOverlaySequence>(totalChunks)
                List(minOf(parallelChunkLimit, totalChunks)) {
                    async {
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val chunkIdx = nextChunk.getAndIncrement()
                            if (chunkIdx >= totalChunks) break
                            if (shouldCancel()) {
                                throw CancellationException("Text rendering cancelled")
                            }
                            results[chunkIdx] = renderChunk(
                                workingDirectory = workingDirectory,
                                chunkIdx = chunkIdx,
                                chunkSize = CHUNK_SIZE,
                                totalFrames = totalFrames,
                                fps = fps,
                                W = W, H = H,
                                layerClips = sortedTextClips,
                                imageClips = sortedImageClips,
                                overlayClips = sortedOverlayClips,
                                imageBitmaps = imageBitmaps,
                                emptyBytes = emptyBytes,
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
            sequences.sortedBy { it.startSec }
        } catch (e: Throwable) {
            workingDirectory.deleteRecursively()
            throw e
        } finally {
            imageBitmaps.values.distinct().forEach { bitmap ->
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
        W: Int, H: Int,
        layerClips: List<EditorClip>,
        imageClips: List<EditorClip>,
        overlayClips: List<EditorClip>,
        imageBitmaps: Map<String, Bitmap>,
        emptyBytes: ByteArray,
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

        val overlayBuffer = if (overlayClips.isNotEmpty()) {
            val overlayBitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            OverlayRenderBuffer(
                bitmap = overlayBitmap,
                androidCanvas = Canvas(overlayBitmap),
                composeCanvas = ComposeCanvas(overlayBitmap.asImageBitmap()),
                drawScope = CanvasDrawScope()
            )
        } else null

        var previousFile: File? = null
        var previousVisibleKey: Set<String> = emptySet()

        val ext = if (imageFormat == "jpeg") "jpg" else "png"

        try {
            for (i in 0 until chunkFrames) {
                currentCoroutineContext().ensureActive()
                if (shouldCancel()) {
                    throw CancellationException("Text rendering cancelled")
                }
                val globalFrame = chunkStart + i
                val timelineMs = (globalFrame.toLong() * 1000L) / fps

                val targetFile = File(
                    workingDirectory,
                    "combined_${chunkIdx}_f%05d.$ext".format(i + 1)
                )

                val analysis = analyzeFrame(
                    timelineMs = timelineMs,
                    textClips = layerClips,
                    imageClips = imageClips,
                    overlayClips = overlayClips
                )

                when {
                    !analysis.needsRender -> {
                        FileOutputStream(targetFile).use { it.write(emptyBytes) }
                    }
                    !analysis.anyAnimated && analysis.visibleKey == previousVisibleKey && previousFile != null -> {
                        previousFile.copyTo(targetFile, overwrite = true)
                    }
                    else -> {
                        if (imageFormat == "jpeg") {
                            canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
                        } else {
                            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                        }

                        overlayClips.forEach { clip ->
                            if (timelineMs >= clip.timelineStartMs && timelineMs < clip.timelineEndMs) {
                                val localSec = (timelineMs - clip.timelineStartMs) / 1000f
                                val ov = extractOverlay(clip)
                                if (ov != null && ov.isActive) {
                                    overlayBuffer?.let {
                                        MaskEngine.drawMaskedLayer(canvas, clip.mask, localSec, W.toFloat(), H.toFloat()) {
                                            drawOverlayOnCanvas(canvas, it, localSec, ov, W, H)
                                        }
                                    }
                                }
                            }
                        }

                        layerClips.forEach { clip ->
                            if (timelineMs >= clip.timelineStartMs && timelineMs < clip.timelineEndMs) {
                                val localSec = (timelineMs - clip.timelineStartMs) / 1000f
                                if (clip.isTextClip) {
                                    MaskEngine.drawMaskedLayer(canvas, clip.mask, localSec, W.toFloat(), H.toFloat()) {
                                        drawTextClipAtTime(canvas, clip, localSec, W, H)
                                    }
                                } else if (clip.isStickerClip) {
                                    MaskEngine.drawMaskedLayer(canvas, clip.mask, localSec, W.toFloat(), H.toFloat()) {
                                        drawStickerClipAtTime(canvas, clip, localSec, W, H)
                                    }
                                }
                            }
                        }

                        imageClips.forEach { clip ->
                            if (timelineMs >= clip.timelineStartMs && timelineMs < clip.timelineEndMs) {
                                val localSec = (timelineMs - clip.timelineStartMs) / 1000f
                                imageBitmaps[clip.id]?.let { image ->
                                    MaskEngine.drawMaskedLayer(canvas, clip.mask, localSec, W.toFloat(), H.toFloat()) {
                                        drawImageClip(canvas, clip, image, localSec, W, H)
                                    }
                                }
                            }
                        }

                        FileOutputStream(targetFile).use { fos ->
                            val ok = if (imageFormat == "jpeg") {
                                bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(60, 100), fos)
                            } else {
                                bmp.compress(Bitmap.CompressFormat.PNG, 90, fos)
                            }
                            if (!ok) throw java.io.IOException("Could not encode text frame")
                        }
                    }
                }
                previousFile = targetFile
                previousVisibleKey = analysis.visibleKey
                onFrameDone()
            }
        } finally {
            bmp.recycle()
            overlayBuffer?.bitmap?.recycle()
        }

        val pattern = File(workingDirectory, "combined_${chunkIdx}_f%05d.$ext").absolutePath
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

    private data class FrameAnalysis(
        val needsRender: Boolean,
        val anyAnimated: Boolean,
        val visibleKey: Set<String>
    )

    private fun analyzeFrame(
        timelineMs: Long,
        textClips: List<EditorClip>,
        imageClips: List<EditorClip>,
        overlayClips: List<EditorClip>
    ): FrameAnalysis {
        val visibleKey = mutableSetOf<String>()
        var anyAnimated = false

        textClips.forEach { clip ->
            if (timelineMs >= clip.timelineStartMs && timelineMs < clip.timelineEndMs) {
                visibleKey.add(clip.id)
                val localSec = (timelineMs - clip.timelineStartMs) / 1000f

                if (clip.isTextClip) {
                    val st = clip.textState
                    if (st != null) {
                        val animDur = st.animationDuration.coerceAtLeast(0.1f)
                        if ((!st.animation.equals("none", ignoreCase = true) ||
                                !st.captionPresetAnimation.equals("none", ignoreCase = true)) &&
                            localSec < animDur
                        ) {
                            anyAnimated = true
                        }
                        if (!st.captionPresetAnimation.equals("none", ignoreCase = true) &&
                            localSec < st.captionPresetAnimationDuration.coerceAtLeast(0.1f)
                        ) {
                            anyAnimated = true
                        }
                        if (CaptionPresetLibrary.hasMovingHighlight(st.captionPresetId)) {
                            anyAnimated = true
                        }
                    }
                    if (clip.keyframes.isNotEmpty()) anyAnimated = true
                }
                if (clip.isStickerClip) {
                    val ss = clip.stickerState
                    if (ss != null) {
                        val animDur = ss.animationDuration.coerceAtLeast(0.1f)
                        if (!ss.animation.equals("none", ignoreCase = true) && localSec < animDur) {
                            anyAnimated = true
                        }
                    }
                    if (clip.keyframes.isNotEmpty()) anyAnimated = true
                }
                if (clip.mask.hasKeyframes) anyAnimated = true
            }
        }

        imageClips.forEach { clip ->
            if (timelineMs >= clip.timelineStartMs && timelineMs < clip.timelineEndMs) {
                visibleKey.add(clip.id)
                if (clip.keyframes.isNotEmpty() || clip.mask.hasKeyframes) {
                    anyAnimated = true
                }
            }
        }

        overlayClips.forEach { clip ->
            if (timelineMs >= clip.timelineStartMs && timelineMs < clip.timelineEndMs) {
                visibleKey.add(clip.id)
                anyAnimated = true
            }
        }

        return FrameAnalysis(needsRender = visibleKey.isNotEmpty(), anyAnimated = anyAnimated, visibleKey = visibleKey)
    }

    private fun decodeImage(context: Context, clip: EditorClip, targetW: Int, targetH: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(clip.uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: throw java.io.IOException("Could not read image: ${clip.name}")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw java.io.IOException("Invalid image dimensions: ${clip.name}")
        }

        val maxDimension = maxOf(targetW, targetH) * 2
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(clip.uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw java.io.IOException("Could not decode image: ${clip.name}")
    }

    private fun drawImageClip(canvas: Canvas, clip: EditorClip, bitmap: Bitmap, localTimeSec: Float, W: Int, H: Int) {
        val transform = TransformApplier.resolveLive(clip, localTimeSec)
        val fitScale = minOf(W.toFloat() / bitmap.width, H.toFloat() / bitmap.height)
        val imageW = bitmap.width * fitScale
        val imageH = bitmap.height * fitScale
        val destination = android.graphics.RectF((W - imageW) / 2f, (H - imageH) / 2f, (W + imageW) / 2f, (H + imageH) / 2f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (clip.filters.opacity / 100f * 255f).toInt().coerceIn(0, 255)
        }

        val pivotX = transform.anchorX / 100f * W
        val pivotY = transform.anchorY / 100f * H
        val positionX = (transform.x - 50f) / 100f * W
        val positionY = (transform.y - 50f) / 100f * H

        val saveCount = canvas.save()
        try {
            canvas.translate(pivotX + positionX, pivotY + positionY)
            canvas.rotate(transform.rotation)
            canvas.scale(transform.scale / 100f, transform.scale / 100f)
            canvas.translate(-pivotX, -pivotY)

            val safeCrop = com.moody.moodyvideoeditor.utils.CropHelper.clampAll(
                transform.cropL, transform.cropR, transform.cropT, transform.cropB
            )
            if (com.moody.moodyvideoeditor.utils.CropHelper.isFullyCropped(
                    safeCrop.l, safeCrop.r, safeCrop.t, safeCrop.b
                )
            ) return

            // Image-relative crop → convert to canvas coords
            val bmpAspect = bitmap.width.toFloat() / bitmap.height
            val canvasAR = W.toFloat() / H
            val fitW: Float; val fitH: Float
            if (bmpAspect >= canvasAR) { fitW = 1f; fitH = canvasAR / bmpAspect }
            else { fitW = bmpAspect / canvasAR; fitH = 1f }
            val leftOff = (1f - fitW) / 2f
            val topOff = (1f - fitH) / 2f

            val x1 = (leftOff + safeCrop.l * fitW) * W
            val y1 = (topOff + safeCrop.t * fitH) * H
            val x2 = (leftOff + (1f - safeCrop.r) * fitW) * W
            val y2 = (topOff + (1f - safeCrop.b) * fitH) * H
            if (x2 <= x1 || y2 <= y1) return
            canvas.clipRect(x1, y1, x2, y2)

            canvas.drawBitmap(bitmap, null, destination, paint)
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    private fun extractOverlay(clip: EditorClip): OverlayState? {
        if (clip.isOverlayClip) {
            val ov = clip.overlay
            if (ov.type != "none") return ov
        }
        val effOv = clip.effectState?.overlay
        if (effOv != null) {
            return OverlayState(
                type = effOv.type, intensity = effOv.intensity,
                color = effOv.color, color2 = effOv.color2,
                size = effOv.size, opacity = effOv.opacity,
                speed = effOv.speed, shape = effOv.shape,
                glowEnabled = effOv.glowEnabled, glowRadius = effOv.glowRadius,
                glowColor = effOv.glowColor
            )
        }
        return null
    }

    private fun drawOverlayOnCanvas(
        androidCanvas: Canvas, buffer: OverlayRenderBuffer,
        timeSec: Float, overlay: OverlayState, W: Int, H: Int
    ) {
        buffer.androidCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        buffer.drawScope.draw(
            density = Density(1f, 1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = buffer.composeCanvas,
            size = ComposeSize(W.toFloat(), H.toFloat())
        ) {
            OverlayEngine.draw(this, timeSec, overlay)
        }
        androidCanvas.drawBitmap(buffer.bitmap, 0f, 0f, null)
    }

    private fun drawTextClipAtTime(canvas: Canvas, clip: EditorClip, localTimeSec: Float, W: Int, H: Int) {
        val st = clip.textState ?: return
        if (st.content.isBlank()) return
        val animSec = if (st.animation.equals("none", true)) 0f else st.animationDuration.coerceAtLeast(0.1f)
        val progress = if (animSec <= 0f) 1f else (localTimeSec / animSec).coerceIn(0f, 1f)
        val presetProgress = if (st.captionPresetAnimation.equals("none", true)) 1f
        else (localTimeSec / st.captionPresetAnimationDuration.coerceAtLeast(0.1f)).coerceIn(0f, 1f)
        val frame = try {
            AnimationsEngine.computeTextFrame(
                st.animation,
                st.captionPresetAnimation,
                progress,
                localTimeSec,
                presetProgress
            )
        } catch (_: Throwable) {
            AnimationsEngine.Frame()
        }
        val sampled = TransformApplier.resolveLive(clip, localTimeSec)
        drawTextWithFrame(
            canvas,
            st,
            frame,
            progress,
            W,
            H,
            sampled,
            (localTimeSec * 1000f).toLong(),
            clip.durationMs
        )
    }

    private fun drawStickerClipAtTime(canvas: Canvas, clip: EditorClip, localTimeSec: Float, W: Int, H: Int) {
        val ss = clip.stickerState ?: return
        if (ss.emoji.isBlank()) return

        val animDur = ss.animationDuration.coerceAtLeast(0.1f)
        val progress = (localTimeSec / animDur).coerceIn(0f, 1f)
        val frame = try {
            AnimationsEngine.computeFrame(ss.animation, progress, localTimeSec)
        } catch (_: Throwable) {
            AnimationsEngine.Frame()
        }

        val sampled = TransformApplier.resolveLive(clip, localTimeSec)
        val baseSize = 48f * (minOf(W, H).toFloat() / REFERENCE_WIDTH_PX)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = baseSize
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
            color = Color.WHITE
        }

        val minDim = minOf(W, H).toFloat()
        val cx = W / 2f + (sampled.x.coerceIn(0f, 100f) - 50f) / 100f * minDim
        val cy = H / 2f + (sampled.y.coerceIn(0f, 100f) - 50f) / 100f * minDim
        val fm = paint.fontMetrics
        val baseline = -(fm.ascent + fm.descent) / 2f

        val alphaTotal = (ss.opacity / 100f * frame.alpha).coerceIn(0f, 1f)
        val alphaInt = (alphaTotal * 255).toInt().coerceIn(0, 255)
        if (alphaInt <= 0) return
        paint.alpha = alphaInt

        if (frame.blurRadiusPx > 0.5f) {
            paint.maskFilter = BlurMaskFilter(frame.blurRadiusPx.coerceIn(0.5f, 40f), BlurMaskFilter.Blur.NORMAL)
        }

        // 3D Flip Approximation (jaisa text mein hota hai)
        val flipScaleY = if (frame.rotationX != 0f) {
            kotlin.math.abs(kotlin.math.cos(Math.toRadians(frame.rotationX.toDouble()))).toFloat().coerceAtLeast(0.05f)
        } else 1f

        val flipScaleX = if (frame.rotationY != 0f) {
            kotlin.math.abs(kotlin.math.cos(Math.toRadians(frame.rotationY.toDouble()))).toFloat().coerceAtLeast(0.05f)
        } else 1f

        canvas.save()
        // Apply Translation (Animation + User Position)
        canvas.translate(cx + frame.translateX, cy + frame.translateY)
        // Apply Rotation (Animation + User Rotation)
        canvas.rotate(sampled.rotation + frame.rotationZ)
        // Apply Scale (X, Y independently) + Flip
        canvas.scale(
            (sampled.scale / 100f) * frame.scaleX * flipScaleX,
            (sampled.scale / 100f) * frame.scaleY * flipScaleY
        )

        canvas.drawText(ss.emoji, 0f, baseline, paint)
        canvas.restore()
    }

    private fun drawTextBackground(
        canvas: Canvas, state: com.moody.moodyvideoeditor.data.TextState,
        lines: List<String>, basePaint: Paint, scale: Float, alphaInt: Int
    ) {
        if (!state.backgroundEnabled || lines.isEmpty()) return

        val padX = state.backgroundPaddingX * scale
        val padY = state.backgroundPaddingY * scale

        var measuredW = 0f
        lines.forEach { line ->
            val w = basePaint.measureText(line)
            if (w > measuredW) measuredW = w
        }

        // ✅ FIX: EXACT MATCH TO COMPOSE ENGINE
        val initialSize = state.fontSize.toFloat() * scale
        val lineHeightPx = initialSize * state.lineHeight.coerceIn(0.1f, 200f)
        val lineCount = lines.size.coerceAtLeast(1)
        val measuredH = lineCount * lineHeightPx

        val boxW = measuredW + padX * 2f
        val boxH = measuredH + padY * 2f
        val boxLeft = -boxW / 2f
        val boxTop = -boxH / 2f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            alpha = alphaInt
        }

        if (state.backgroundGradientEnabled) {
            val rad = Math.toRadians(state.backgroundGradientAngle.toDouble())
            val dx = kotlin.math.cos(rad).toFloat()
            val dy = kotlin.math.sin(rad).toFloat()
            val ext = maxOf(boxW, boxH)
            bgPaint.shader = LinearGradient(
                -dx * ext, -dy * ext, dx * ext, dy * ext,
                state.backgroundColor1.toInt(), state.backgroundColor2.toInt(),
                Shader.TileMode.CLAMP
            )
        } else {
            bgPaint.color = state.backgroundColor1.toInt()
        }

        when (state.backgroundType) {
            "rounded" -> {
                val rect = android.graphics.RectF(boxLeft, boxTop, boxLeft + boxW, boxTop + boxH)
                val radiusPx = state.backgroundCornerRadius * scale * 1.5f
                canvas.drawRoundRect(rect, radiusPx, radiusPx, bgPaint)
            }
            "character" -> {
                val tightBoxW = measuredW + 4f * scale
                val tightBoxH = measuredH + 4f * scale
                val rect = android.graphics.RectF(-tightBoxW / 2f, -tightBoxH / 2f, tightBoxW / 2f, tightBoxH / 2f)
                canvas.drawRect(rect, bgPaint)
            }
            "outline" -> {
                val rect = android.graphics.RectF(boxLeft, boxTop, boxLeft + boxW, boxTop + boxH)
                val radiusPx = state.backgroundCornerRadius * scale * 1.5f
                val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 3f * scale * 1.5f
                    alpha = alphaInt
                    if (bgPaint.shader != null) shader = bgPaint.shader else color = state.backgroundColor1.toInt()
                }
                canvas.drawRoundRect(rect, radiusPx, radiusPx, outlinePaint)
            }
        }
    }

    private fun drawTextWithFrame(
        canvas: Canvas,
        st: com.moody.moodyvideoeditor.data.TextState,
        frame: AnimationsEngine.Frame,
        progress: Float,
        W: Int, H: Int,
        sampled: TransformValues,
        localTimeMs: Long,
        clipDurationMs: Long
    ) {
        try {
            val content = if (st.animation.equals("typewriter", ignoreCase = true)) {
                val total = st.content.length
                val visible = (progress * total).toInt().coerceIn(0, total)
                st.content.substring(0, visible)
            } else st.content

            if (content.isEmpty()) return

            val typeface = try {
                FontLibrary.typefaceFor(st.fontFamily, bold = st.fontWeight == "bold", italic = st.fontStyle == "italic")
            } catch (_: Throwable) {
                Typeface.DEFAULT
            }

            val lines = content.split('\n')
            val scale = minOf(W, H).toFloat() / REFERENCE_WIDTH_PX

            val initialSize = st.fontSize.toFloat() * scale
            val safeLineHeight = st.lineHeight.coerceIn(0.1f, 200f)

            val lineHeightPx = initialSize * safeLineHeight

            val fontSize = TextRenderContract.fittedFontSizePx(
                content = content,
                baseSizePx = st.fontSize.toFloat(),
                canvasWidthPx = W.toFloat(),
                canvasHeightPx = H.toFloat(),
                maxWidthPercent = st.maxWidth,
                typeface = typeface
            )

            val spacingPx = st.letterSpacing * scale
            val spacingEm = if (fontSize <= 0f) 0f else (spacingPx / fontSize).coerceIn(-0.3f, 0.3f)

            val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                this.textSize = fontSize
                textAlign = Paint.Align.CENTER
                letterSpacing = spacingEm
                color = st.color.toInt()
            }

            // ✅ CLAMP WIDENED HERE TOO
            val clampedX = sampled.x.coerceIn(-50f, 150f)
            val clampedY = sampled.y.coerceIn(-50f, 150f)

            val minDim = minOf(W, H).toFloat()
            val cx = W / 2f + (clampedX - 50f) / 100f * minDim
            val cy = H / 2f + (clampedY - 50f) / 100f * minDim

            val fm = basePaint.fontMetrics

            val lineCount = lines.size
            val firstBaseline = -(lineCount - 1) * lineHeightPx / 2f - (fm.ascent + fm.descent) / 2f

            val flipScaleY = if (frame.rotationX != 0f) {
                kotlin.math.abs(kotlin.math.cos(Math.toRadians(frame.rotationX.toDouble()))).toFloat().coerceAtLeast(0.05f)
            } else 1f

            val flipScaleX = if (frame.rotationY != 0f) {
                kotlin.math.abs(kotlin.math.cos(Math.toRadians(frame.rotationY.toDouble()))).toFloat().coerceAtLeast(0.05f)
            } else 1f

            canvas.save()
            canvas.translate(cx + frame.translateX, cy + frame.translateY)
            canvas.rotate(sampled.rotation + frame.rotationZ)
            canvas.scale(
                (sampled.scale / 100f) * frame.scaleX * flipScaleX,
                (sampled.scale / 100f) * frame.scaleY * flipScaleY
            )

            val alphaTotal = (st.opacity / 100f * frame.alpha).coerceIn(0f, 1f)
            val alphaInt = (alphaTotal * 255).toInt().coerceIn(0, 255)

            if (alphaInt <= 0) {
                canvas.restore()
                return
            }

            if (st.backgroundEnabled) {
                drawTextBackground(canvas, st, lines, basePaint, scale, alphaInt)
            }

            if (st.glowEnabled && st.glowRadius > 0f) {
                val glowColorInt = st.glowColor.toInt()
                val glowR = (st.glowRadius * scale).coerceIn(2f, 100f)
                TextRenderContract.glowLayers().forEach { layer ->
                    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.typeface = typeface
                        this.textSize = fontSize * layer.fontScale
                        textAlign = basePaint.textAlign
                        letterSpacing = basePaint.letterSpacing
                        color = glowColorInt
                        alpha = (alphaInt * layer.alpha).toInt().coerceIn(0, 255)
                        maskFilter = BlurMaskFilter((glowR * layer.blurScale).coerceIn(2f, 100f), BlurMaskFilter.Blur.NORMAL)
                    }
                    lines.forEachIndexed { i, line ->
                        val lineY = firstBaseline + i * lineHeightPx
                        canvas.drawText(line, 0f, lineY, glowPaint)
                    }
                }
            }

            if (st.shadowEnabled) {
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.typeface = typeface
                    this.textSize = fontSize
                    textAlign = basePaint.textAlign
                    letterSpacing = basePaint.letterSpacing
                    shader = null
                    color = st.color.toInt()
                    alpha = alphaInt
                    setShadowLayer(
                        (st.shadowBlur * scale).coerceIn(0f, 100f),
                        (st.shadowOffsetX * scale).coerceIn(-60f, 60f),
                        (st.shadowOffsetY * scale).coerceIn(-60f, 60f),
                        st.shadowColor.toInt()
                    )
                }
                lines.forEachIndexed { i, line ->
                    val lineY = firstBaseline + i * lineHeightPx
                    canvas.drawText(line, 0f, lineY, shadowPaint)
                }
            }

            if (st.strokeEnabled && st.strokeWidth > 0f) {
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.typeface = typeface
                    this.textSize = fontSize
                    textAlign = basePaint.textAlign
                    letterSpacing = basePaint.letterSpacing
                    shader = null
                    color = st.strokeColor.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = (st.strokeWidth * scale * 2f).coerceIn(1f, 60f)
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                    alpha = alphaInt
                    setShadowLayer(0f, 0f, 0f, 0)

                    if (frame.blurRadiusPx > 0.5f) {
                        maskFilter = BlurMaskFilter(frame.blurRadiusPx.coerceIn(0.5f, 40f), BlurMaskFilter.Blur.NORMAL)
                    }
                }
                lines.forEachIndexed { i, line ->
                    val lineY = firstBaseline + i * lineHeightPx
                    canvas.drawText(line, 0f, lineY, strokePaint)
                }
            }

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                this.textSize = fontSize
                textAlign = basePaint.textAlign
                letterSpacing = basePaint.letterSpacing
                alpha = alphaInt
                setShadowLayer(0f, 0f, 0f, 0)

                if (frame.blurRadiusPx > 0.5f) {
                    maskFilter = BlurMaskFilter(frame.blurRadiusPx.coerceIn(0.5f, 40f), BlurMaskFilter.Blur.NORMAL)
                }
            }

            if (st.gradientEnabled) {
                val gradientWidth = lines.maxOfOrNull { basePaint.measureText(it) }
                    ?.coerceAtLeast(1f) ?: 1f
                val gradientHeight = (lineHeightPx * lines.size).coerceAtLeast(1f)
                fillPaint.shader = LinearGradient(
                    (st.gradientStartX.coerceIn(0f, 1f) - 0.5f) * gradientWidth,
                    (st.gradientStartY.coerceIn(0f, 1f) - 0.5f) * gradientHeight,
                    (st.gradientEndX.coerceIn(0f, 1f) - 0.5f) * gradientWidth,
                    (st.gradientEndY.coerceIn(0f, 1f) - 0.5f) * gradientHeight,
                    st.gradientColor1.toInt(), st.gradientColor2.toInt(), Shader.TileMode.CLAMP
                )
            } else {
                fillPaint.color = st.color.toInt()
            }

            val captionRanges = CaptionPresetLibrary.colorRanges(
                text = content,
                presetId = st.captionPresetId,
                highlightColor = st.captionHighlightColor,
                elapsedMs = localTimeMs,
                durationMs = clipDurationMs,
                preserveGradient = st.gradientEnabled
            )
            if (captionRanges.isEmpty()) {
                lines.forEachIndexed { i, line ->
                    val lineY = firstBaseline + i * lineHeightPx
                    canvas.drawText(line, 0f, lineY, fillPaint)
                }
            } else {
                fillPaint.textAlign = Paint.Align.LEFT
                fillPaint.shader = null
                val maxLineWidth = lines.maxOfOrNull { fillPaint.measureText(it) } ?: 0f
                val alignOffset = when (st.alignment) {
                    "left" -> -maxLineWidth / 2f
                    else -> 0f
                }
                var lineStart = 0
                lines.forEachIndexed { lineIndex, line ->
                    val lineEnd = lineStart + line.length
                    val lineWidth = fillPaint.measureText(line)
                    var x = when (st.alignment) {
                        "left" -> alignOffset
                        "right" -> maxLineWidth / 2f - lineWidth
                        else -> -lineWidth / 2f
                    }
                    var charIndex = lineStart
                    while (charIndex < lineEnd) {
                        val range = captionRanges.firstOrNull {
                            charIndex >= it.start && charIndex < it.end
                        }
                        var runEnd = charIndex + 1
                        while (runEnd < lineEnd &&
                            captionRanges.firstOrNull {
                                runEnd >= it.start && runEnd < it.end
                            }?.color == range?.color
                        ) {
                            runEnd++
                        }
                        val run = content.substring(charIndex, runEnd)
                        val baseRunColor = when {
                            range != null -> range.color
                            st.gradientEnabled -> sampleTextGradient(
                                st = st,
                                x = x + fillPaint.measureText(run) / 2f + maxLineWidth / 2f,
                                y = lineIndex * lineHeightPx + lineHeightPx / 2f,
                                width = maxLineWidth.coerceAtLeast(1f),
                                height = (lineHeightPx * lines.size).coerceAtLeast(1f)
                            )
                            else -> st.color
                        }
                        val runColor = baseRunColor.toInt()
                        fillPaint.color = Color.argb(
                            alphaInt,
                            Color.red(runColor),
                            Color.green(runColor),
                            Color.blue(runColor)
                        )
                        canvas.drawText(run, x, firstBaseline + lineIndex * lineHeightPx, fillPaint)
                        x += fillPaint.measureText(run)
                        charIndex = runEnd
                    }
                    lineStart = lineEnd + 1
                }
            }

            canvas.restore()
        } catch (e: Throwable) {
            Log.e(TAG, "drawTextWithFrame failed", e)
        }
    }

    private fun sampleTextGradient(
        st: com.moody.moodyvideoeditor.data.TextState,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ): Long {
        val startX = st.gradientStartX.coerceIn(0f, 1f) * width
        val startY = st.gradientStartY.coerceIn(0f, 1f) * height
        val endX = st.gradientEndX.coerceIn(0f, 1f) * width
        val endY = st.gradientEndY.coerceIn(0f, 1f) * height
        val dx = endX - startX
        val dy = endY - startY
        val denominator = dx * dx + dy * dy
        val amount = if (denominator <= 0.0001f) 0f else (
            ((x - startX) * dx + (y - startY) * dy) / denominator
        ).coerceIn(0f, 1f)
        val start = st.gradientColor1.toInt()
        val end = st.gradientColor2.toInt()
        fun channel(shift: Int): Int {
            val a = (start ushr shift) and 0xFF
            val b = (end ushr shift) and 0xFF
            return (a + (b - a) * amount).toInt().coerceIn(0, 255)
        }
        return (
            (channel(24).toLong() shl 24) or
                (channel(16).toLong() shl 16) or
                (channel(8).toLong() shl 8) or channel(0).toLong()
            )
    }
}
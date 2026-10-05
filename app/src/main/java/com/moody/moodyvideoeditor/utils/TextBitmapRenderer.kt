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
    val imageFormat: String = "png"
)

private data class OverlayRenderBuffer(
    val bitmap: Bitmap,
    val androidCanvas: Canvas,
    val composeCanvas: ComposeCanvas,
    val drawScope: CanvasDrawScope
)

object TextBitmapRenderer {

    // ✅ SAME reference as TextScaler (720)
    private const val REFERENCE_WIDTH_PX = 720f

    private const val TAG = "TEXT_RENDER"
    private const val CHUNK_SIZE = 60
    private const val MAX_PARALLEL_CHUNKS = 4

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
            // Decode image clips once (used for higher-track images)
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

            Log.e(
                TAG, "Rendering $totalFrames frames in $totalChunks chunks " +
                        "(format=$imageFormat, quality=$jpegQuality, W=$W, H=$H)"
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
                                throw CancellationException("Text rendering cancelled")
                            }
                            results[chunkIdx] = renderChunk(
                                workingDirectory = workingDirectory,
                                chunkIdx = chunkIdx,
                                chunkSize = CHUNK_SIZE,
                                totalFrames = totalFrames,
                                fps = fps,
                                W = W,
                                H = H,
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

            Log.e(TAG, "Done: ${sequences.size} chunks, $totalFrames frames")
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
        W: Int,
        H: Int,
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
                    overlayClips = overlayClips
                )

                when {
                    !analysis.needsRender -> {
                        FileOutputStream(targetFile).use { it.write(emptyBytes) }
                    }

                    !analysis.anyAnimated &&
                            analysis.visibleKey == previousVisibleKey &&
                            previousFile != null -> {
                        previousFile.copyTo(targetFile, overwrite = true)
                    }

                    else -> {
                        if (imageFormat == "jpeg") {
                            canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
                        } else {
                            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                        }

                        overlayClips.forEach { clip ->
                            if (timelineMs >= clip.timelineStartMs &&
                                timelineMs < clip.timelineEndMs
                            ) {
                                val localSec = (timelineMs - clip.timelineStartMs) / 1000f
                                val ov = extractOverlay(clip)
                                if (ov != null && ov.isActive) {
                                    overlayBuffer?.let {
                                        drawOverlayOnCanvas(
                                            canvas, it, localSec, ov, W, H
                                        )
                                    }
                                }
                            }
                        }

                        layerClips.forEach { clip ->
                            if (timelineMs >= clip.timelineStartMs &&
                                timelineMs < clip.timelineEndMs
                            ) {
                                val localSec = (timelineMs - clip.timelineStartMs) / 1000f
                                if (clip.isTextClip) {
                                    drawTextClipAtTime(canvas, clip, localSec, W, H)
                                } else if (clip.isStickerClip) {
                                    drawStickerClipAtTime(canvas, clip, localSec, W, H)
                                }
                            }
                        }

                        imageClips.forEach { clip ->
                            if (timelineMs >= clip.timelineStartMs &&
                                timelineMs < clip.timelineEndMs
                            ) {
                                val localSec = (timelineMs - clip.timelineStartMs) / 1000f
                                imageBitmaps[clip.id]?.let { image ->
                                    drawImageClip(canvas, clip, image, localSec, W, H)
                                }
                            }
                        }

                        FileOutputStream(targetFile).use { fos ->
                            val ok = if (imageFormat == "jpeg") {
                                bmp.compress(
                                    Bitmap.CompressFormat.JPEG,
                                    jpegQuality.coerceIn(60, 100),
                                    fos
                                )
                            } else {
                                bmp.compress(Bitmap.CompressFormat.PNG, 90, fos)
                            }
                            if (!ok) {
                                throw java.io.IOException("Could not encode text frame")
                            }
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

        val pattern = File(
            workingDirectory,
            "combined_${chunkIdx}_f%05d.$ext"
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

    private data class FrameAnalysis(
        val needsRender: Boolean,
        val anyAnimated: Boolean,
        val visibleKey: Set<String>
    )

    private fun analyzeFrame(
        timelineMs: Long,
        textClips: List<EditorClip>,
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
                        if (!st.animation.equals("none", ignoreCase = true) &&
                            localSec < animDur
                        ) {
                            anyAnimated = true
                        }
                    }
                    if (clip.keyframes.isNotEmpty()) anyAnimated = true
                }

                if (clip.isStickerClip) {
                    val ss = clip.stickerState
                    if (ss != null) {
                        val animDur = ss.animationDuration.coerceAtLeast(0.1f)
                        if (!ss.animation.equals("none", ignoreCase = true) &&
                            localSec < animDur
                        ) {
                            anyAnimated = true
                        }
                    }
                    if (clip.keyframes.isNotEmpty()) anyAnimated = true
                }
            }
        }

        overlayClips.forEach { clip ->
            if (timelineMs >= clip.timelineStartMs && timelineMs < clip.timelineEndMs) {
                visibleKey.add(clip.id)
                anyAnimated = true
            }
        }

        return FrameAnalysis(
            needsRender = visibleKey.isNotEmpty(),
            anyAnimated = anyAnimated,
            visibleKey = visibleKey
        )
    }

    private fun decodeImage(
        context: Context,
        clip: EditorClip,
        targetW: Int,
        targetH: Int
    ): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(clip.uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: throw java.io.IOException("Could not read image: ${clip.name}")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw java.io.IOException("Invalid image dimensions: ${clip.name}")
        }

        val maxDimension = maxOf(targetW, targetH) * 2
        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > maxDimension ||
            bounds.outHeight / sampleSize > maxDimension
        ) {
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

    private fun drawImageClip(
        canvas: Canvas,
        clip: EditorClip,
        bitmap: Bitmap,
        localTimeSec: Float,
        W: Int,
        H: Int
    ) {
        val transform = TransformApplier.resolveLive(clip, localTimeSec)
        val fitScale = minOf(W.toFloat() / bitmap.width, H.toFloat() / bitmap.height)
        val imageW = bitmap.width * fitScale
        val imageH = bitmap.height * fitScale
        val destination = android.graphics.RectF(
            (W - imageW) / 2f,
            (H - imageH) / 2f,
            (W + imageW) / 2f,
            (H + imageH) / 2f
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (clip.filters.opacity / 100f * 255f).toInt().coerceIn(0, 255)
        }

        val cropSx = 1f /
                (1f - transform.cropL - transform.cropR).coerceAtLeast(0.05f)
        val cropSy = 1f /
                (1f - transform.cropT - transform.cropB).coerceAtLeast(0.05f)
        val cropTx = -(transform.cropL - transform.cropR) / 2f * W
        val cropTy = -(transform.cropT - transform.cropB) / 2f * H
        val pivotX = transform.anchorX / 100f * W
        val pivotY = transform.anchorY / 100f * H
        val positionX = (transform.x - 50f) / 100f * W
        val positionY = (transform.y - 50f) / 100f * H

        val saveCount = canvas.save()
        try {
            canvas.translate(pivotX + positionX + cropTx, pivotY + positionY + cropTy)
            canvas.rotate(transform.rotation)
            canvas.scale(
                transform.scale / 100f * cropSx,
                transform.scale / 100f * cropSy
            )
            canvas.translate(-pivotX, -pivotY)
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
                type = effOv.type,
                intensity = effOv.intensity,
                color = effOv.color
            )
        }
        return null
    }

    private fun drawOverlayOnCanvas(
        androidCanvas: Canvas,
        buffer: OverlayRenderBuffer,
        timeSec: Float,
        overlay: OverlayState,
        W: Int,
        H: Int
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

    private fun drawTextClipAtTime(
        canvas: Canvas,
        clip: EditorClip,
        localTimeSec: Float,
        W: Int,
        H: Int
    ) {
        val st = clip.textState ?: return
        if (st.content.isBlank()) return

        val animSec = if (st.animation.equals("none", true)) 0f
        else st.animationDuration.coerceAtLeast(0.1f)

        val progress = if (animSec <= 0f) 1f
        else (localTimeSec / animSec).coerceIn(0f, 1f)

        val frame = try {
            AnimationsEngine.computeFrame(st.animation, progress, localTimeSec)
        } catch (_: Throwable) {
            AnimationsEngine.Frame()
        }

        val sampled = TransformApplier.resolveLive(clip, localTimeSec)
        drawTextWithFrame(canvas, st, frame, progress, W, H, sampled)
    }

    private fun drawStickerClipAtTime(
        canvas: Canvas,
        clip: EditorClip,
        localTimeSec: Float,
        W: Int,
        H: Int
    ) {
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

        val baseSize = 48f * (W / REFERENCE_WIDTH_PX)
        val combinedScale = (sampled.scale / 100f) * frame.scaleX
        val fontSize = baseSize * combinedScale

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontSize
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
            color = Color.WHITE
        }

        val cx = sampled.x.coerceIn(0f, 100f) / 100f * W
        val cy = sampled.y.coerceIn(0f, 100f) / 100f * H

        val fm = paint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f

        val alphaTotal = (ss.opacity / 100f * frame.alpha).coerceIn(0f, 1f)
        val alphaInt = (alphaTotal * 255).toInt().coerceIn(0, 255)
        if (alphaInt <= 0) return
        paint.alpha = alphaInt

        canvas.save()
        canvas.translate(cx + frame.translateX, cy + frame.translateY)
        canvas.rotate(sampled.rotation + frame.rotationZ)
        canvas.drawText(ss.emoji, 0f, baseline - cy, paint)
        canvas.restore()
    }

    private fun drawTextWithFrame(
        canvas: Canvas,
        st: com.moody.moodyvideoeditor.data.TextState,
        frame: AnimationsEngine.Frame,
        progress: Float,
        W: Int,
        H: Int,
        sampled: TransformValues
    ) {
        try {
            val content = if (st.animation.equals("typewriter", ignoreCase = true)) {
                val total = st.content.length
                val visible = (progress * total).toInt().coerceIn(0, total)
                st.content.substring(0, visible)
            } else st.content

            if (content.isEmpty()) return

            val scale = W.toFloat() / REFERENCE_WIDTH_PX

            val baseFontSize = st.fontSize.coerceAtLeast(8)
            val initialFontSize = (baseFontSize * scale).coerceAtLeast(8f)

            val typeface = try {
                FontLibrary.typefaceFor(
                    st.fontFamily,
                    bold = st.fontWeight == "bold",
                    italic = st.fontStyle == "italic"
                )
            } catch (_: Throwable) {
                Typeface.DEFAULT
            }

            val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                this.textSize = initialFontSize
                textAlign = Paint.Align.CENTER
            }
            val naturalWidth = measurePaint.measureText(content)
            val maxWidthPx = W * (st.maxWidth.coerceIn(30f, 100f) / 100f)
            val fontSize = if (naturalWidth > maxWidthPx) {
                initialFontSize * (maxWidthPx / naturalWidth)
            } else {
                initialFontSize
            }

            val spacingPx = st.letterSpacing * scale
            val spacingEm = if (fontSize <= 0f) 0f
            else (spacingPx / fontSize).coerceIn(-0.3f, 0.3f)

            val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                this.textSize = fontSize
                textAlign = when (st.alignment) {
                    "left" -> Paint.Align.LEFT
                    "right" -> Paint.Align.RIGHT
                    else -> Paint.Align.CENTER
                }
                letterSpacing = spacingEm
                color = st.color.toInt()
            }

            val clampedX = sampled.x.coerceIn(-50f, 150f)
            val clampedY = sampled.y.coerceIn(0f, 100f)

            val cx = clampedX / 100f * W
            val cy = clampedY / 100f * H

            val fm = basePaint.fontMetrics
            val baseline = cy - (fm.ascent + fm.descent) / 2f

            canvas.save()
            canvas.translate(cx + frame.translateX, cy + frame.translateY)
            canvas.rotate(sampled.rotation + frame.rotationZ)
            canvas.scale(
                (sampled.scale / 100f) * frame.scaleX,
                (sampled.scale / 100f) * frame.scaleY
            )

            val tx = 0f
            val ty = baseline - cy

            val alphaTotal = (st.opacity / 100f * frame.alpha).coerceIn(0f, 1f)
            val alphaInt = (alphaTotal * 255).toInt().coerceIn(0, 255)

            if (alphaInt <= 0) {
                canvas.restore()
                return
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
                        maskFilter = BlurMaskFilter(
                            (glowR * layer.blurScale).coerceIn(2f, 100f),
                            BlurMaskFilter.Blur.NORMAL
                        )
                    }
                    canvas.drawText(content, tx, ty, glowPaint)
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
                canvas.drawText(content, tx, ty, shadowPaint)
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
                }
                canvas.drawText(content, tx, ty, strokePaint)
            }

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                this.textSize = fontSize
                textAlign = basePaint.textAlign
                letterSpacing = basePaint.letterSpacing
                alpha = alphaInt
                setShadowLayer(0f, 0f, 0f, 0)
            }

            if (st.gradientEnabled) {
                val rad = Math.toRadians(st.gradientAngle.toDouble())
                val dx = kotlin.math.cos(rad).toFloat() * W * 0.5f
                val dy = kotlin.math.sin(rad).toFloat() * H * 0.5f
                fillPaint.shader = LinearGradient(
                    cx - dx, cy - dy,
                    cx + dx, cy + dy,
                    st.gradientColor1.toInt(),
                    st.gradientColor2.toInt(),
                    Shader.TileMode.CLAMP
                )
            } else {
                fillPaint.color = st.color.toInt()
            }

            canvas.drawText(content, tx, ty, fillPaint)
            canvas.restore()

        } catch (e: Throwable) {
            Log.e(TAG, "drawTextWithFrame failed", e)
        }
    }
}
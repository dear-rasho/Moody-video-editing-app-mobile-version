package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.graphics.Bitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Canvas as ComposeCanvas

data class TextOverlaySequence(
    val pattern: String,
    val frameCount: Int,
    val fps: Int,
    val startNumber: Int,
    val startSec: Double,
    val endSec: Double
)

object TextBitmapRenderer {

    private const val REFERENCE_WIDTH_PX = 400f
    private const val TAG = "TEXT_RENDER"

    // 🆕 Small chunk = faster first feedback
    private const val CHUNK_SIZE = 60

    suspend fun renderCombinedOverlays(
        context: Context,
        textClips: List<EditorClip>,
        imageClips: List<EditorClip> = emptyList(),
        overlayClips: List<EditorClip> = emptyList(),
        W: Int,
        H: Int,
        fps: Int,
        totalDurationMs: Long,
        onProgress: (Float) -> Unit = {}
    ): List<TextOverlaySequence> = withContext(Dispatchers.Default) {

        if (textClips.isEmpty() && imageClips.isEmpty() && overlayClips.isEmpty()) {
            return@withContext emptyList()
        }

        val totalFrames = ((totalDurationMs * fps) / 1000L)
            .toInt().coerceAtLeast(1)

        // Cleanup
        try {
            context.cacheDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("combined_") && f.name.endsWith(".png")) {
                    f.delete()
                }
            }
        } catch (_: Exception) {
        }

        val sortedTextClips = textClips.sortedWith(
            compareBy({ it.trackIndex }, { it.timelineStartMs })
        )
        val sortedOverlayClips = overlayClips.sortedWith(
            compareBy({ it.trackIndex }, { it.timelineStartMs })
        )

        // 🆕 Pre-generate empty transparent PNG (reused for empty frames)
        val emptyPngBytes: ByteArray = ByteArrayOutputStream().use { baos ->
            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
            bmp.recycle()
            baos.toByteArray()
        }

        val totalChunks = (totalFrames + CHUNK_SIZE - 1) / CHUNK_SIZE
        val completedCounter = AtomicInteger(0)

        Log.e(TAG, "Rendering $totalFrames frames in $totalChunks chunks")

        // 🆕 Render all chunks in parallel (each chunk runs its frames sequentially)
        val sequences = coroutineScope {
            (0 until totalChunks).map { chunkIdx ->
                async(Dispatchers.Default) {
                    val seq = renderChunk(
                        context = context,
                        chunkIdx = chunkIdx,
                        chunkSize = CHUNK_SIZE,
                        totalFrames = totalFrames,
                        fps = fps,
                        W = W,
                        H = H,
                        textClips = sortedTextClips,
                        overlayClips = sortedOverlayClips,
                        emptyPngBytes = emptyPngBytes,
                        onFrameDone = {
                            val done = completedCounter.incrementAndGet()
                            onProgress(done.toFloat() / totalFrames.toFloat())
                        }
                    )
                    seq
                }
            }.awaitAll()
        }

        Log.e(TAG, "Done: ${sequences.size} chunks, $totalFrames frames")
        sequences.sortedBy { it.startSec }
    }

    // ═══════════════════════════════════════════════════════════
    //  RENDER SINGLE CHUNK (sequential frames, cached bitmaps)
    // ═══════════════════════════════════════════════════════════
    private fun renderChunk(
        context: Context,
        chunkIdx: Int,
        chunkSize: Int,
        totalFrames: Int,
        fps: Int,
        W: Int,
        H: Int,
        textClips: List<EditorClip>,
        overlayClips: List<EditorClip>,
        emptyPngBytes: ByteArray,
        onFrameDone: () -> Unit
    ): TextOverlaySequence {

        val chunkStart = chunkIdx * chunkSize
        val chunkEnd = (chunkStart + chunkSize).coerceAtMost(totalFrames)
        val chunkFrames = chunkEnd - chunkStart

        // 🆕 Reuse ONE bitmap for the whole chunk
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Frame-reuse cache (for static content)
        var previousFile: File? = null
        var previousVisibleKey: Set<String> = emptySet()

        for (i in 0 until chunkFrames) {
            val globalFrame = chunkStart + i
            val timelineMs = (globalFrame.toLong() * 1000L) / fps

            val targetFile = File(
                context.cacheDir,
                "combined_${chunkIdx}_f%05d.png".format(i + 1)
            )

            // ── Analyze visible content for this frame ──
            val analysis = analyzeFrame(
                timelineMs = timelineMs,
                textClips = textClips,
                overlayClips = overlayClips
            )

            when {
                // Case 1: Nothing visible → write cached empty PNG
                !analysis.needsRender -> {
                    FileOutputStream(targetFile).use { it.write(emptyPngBytes) }
                }

                // Case 2: Same static content as previous frame → copy file
                !analysis.anyAnimated &&
                        analysis.visibleKey == previousVisibleKey &&
                        previousFile != null -> {
                    previousFile.copyTo(targetFile, overwrite = true)
                }

                // Case 3: Render normally
                else -> {
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                    // Draw overlays (behind text)
                    overlayClips.forEach { clip ->
                        if (timelineMs >= clip.timelineStartMs &&
                            timelineMs < clip.timelineEndMs
                        ) {
                            val localSec = (timelineMs - clip.timelineStartMs) / 1000f
                            val ov = extractOverlay(clip)
                            if (ov != null && ov.isActive) {
                                try {
                                    drawOverlayOnCanvas(canvas, localSec, ov, W, H)
                                } catch (_: Throwable) {
                                }
                            }
                        }
                    }

                    // Draw text + stickers
                    textClips.forEach { clip ->
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

                    FileOutputStream(targetFile).use { fos ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 85, fos)
                    }
                }
            }

            previousFile = targetFile
            previousVisibleKey = analysis.visibleKey

            onFrameDone()
        }

        bmp.recycle()

        val pattern = File(
            context.cacheDir,
            "combined_${chunkIdx}_f%05d.png"
        ).absolutePath

        val startSec = chunkStart.toDouble() / fps.toDouble()
        val endSec = chunkEnd.toDouble() / fps.toDouble()

        return TextOverlaySequence(
            pattern = pattern,
            frameCount = chunkFrames,
            fps = fps,
            startNumber = 1,
            startSec = startSec,
            endSec = endSec
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  FRAME ANALYSIS — determines if frame is empty/static/animated
    // ═══════════════════════════════════════════════════════════
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

                // Text animation
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

                // Sticker animation
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
                // Overlays are always animated (rain, snow, etc.)
                anyAnimated = true
            }
        }

        return FrameAnalysis(
            needsRender = visibleKey.isNotEmpty(),
            anyAnimated = anyAnimated,
            visibleKey = visibleKey
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  Existing helper functions (unchanged)
    // ═══════════════════════════════════════════════════════════
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
        timeSec: Float,
        overlay: OverlayState,
        W: Int,
        H: Int
    ) {
        try {
            val overlayBmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val imageBitmap = overlayBmp.asImageBitmap()
            val composeCanvas = ComposeCanvas(imageBitmap)

            val drawScope = CanvasDrawScope()
            drawScope.draw(
                density = Density(1f, 1f),
                layoutDirection = LayoutDirection.Ltr,
                canvas = composeCanvas,
                size = ComposeSize(W.toFloat(), H.toFloat())
            ) {
                OverlayEngine.draw(this, timeSec, overlay)
            }

            androidCanvas.drawBitmap(overlayBmp, 0f, 0f, null)
            overlayBmp.recycle()
        } catch (e: Throwable) {
            Log.e("OVERLAY_DRAW", "Failed: ${overlay.type}", e)
        }
    }

    private fun androidFamilyFor(fontName: String): String {
        val n = fontName.lowercase().trim()
        return when {
            n.contains("mono") || n.contains("courier") || n.contains("consol") ||
                    n.contains("menlo") || n.contains("monaco") || n.contains("code") ->
                "monospace"

            n.contains("script") || n.contains("brush") || n.contains("hand") ||
                    n.contains("comic") || n.contains("cursive") ||
                    n.contains("dancing") || n.contains("pacific") ||
                    n.contains("vibes") || n.contains("amita") ||
                    n.contains("chopin") || n.contains("musiclife") ||
                    n.contains("caveat") || n.contains("allura") ||
                    n.contains("satisfy") || n.contains("kaushan") ||
                    n.contains("parisienne") || n.contains("sacramento") ||
                    n.contains("tangerine") || n.contains("indie") ||
                    n.contains("patrick") || n.contains("kalam") ->
                "cursive"

            n.contains("serif") || n.contains("times") || n.contains("georgia") ||
                    n.contains("garamond") || n.contains("baskerville") ||
                    n.contains("playfair") || n.contains("cinzel") ||
                    n.contains("bodoni") || n.contains("cormorant") ||
                    n.contains("merriweather") || n.contains("lora") ||
                    n.contains("crimson") || n.contains("prata") ||
                    n.contains("cardo") || n.contains("spectral") ||
                    n.contains("abril") ->
                "serif"

            else -> "sans-serif"
        }
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

        val stickerSizeDp = baseSize * (sampled.scale / 100f)
        val halfWPct = (stickerSizeDp / 2f / W * 100f).coerceAtMost(50f)
        val halfHPct = (stickerSizeDp / 2f / H * 100f).coerceAtMost(50f)
        val clampedX = sampled.x.coerceIn(halfWPct, 100f - halfWPct)
        val clampedY = sampled.y.coerceIn(halfHPct, 100f - halfHPct)

        val cx = clampedX / 100f * W
        val cy = clampedY / 100f * H

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

            val baseFontSize = st.fontSize.coerceAtLeast(8)
            val initialFontSize = (baseFontSize * (W.toFloat() / REFERENCE_WIDTH_PX))
                .coerceAtLeast(10f)

            val typeface = try {
                val style = when {
                    st.fontWeight == "bold" && st.fontStyle == "italic" ->
                        Typeface.BOLD_ITALIC

                    st.fontWeight == "bold" -> Typeface.BOLD
                    st.fontStyle == "italic" -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
                Typeface.create(androidFamilyFor(st.fontFamily), style)
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

            val spacingPx = st.letterSpacing * (W.toFloat() / REFERENCE_WIDTH_PX)
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

            val textW = basePaint.measureText(content)
            val textH = basePaint.fontMetrics.let { it.descent - it.ascent }
            val halfWPct = (textW / 2f / W * 100f).coerceAtMost(50f)
            val halfHPct = (textH / 2f / H * 100f).coerceAtMost(50f)
            val clampedX = sampled.x.coerceIn(halfWPct, 100f - halfWPct)
            val clampedY = sampled.y.coerceIn(halfHPct, 100f - halfHPct)

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
                val glowR = st.glowRadius.coerceIn(4f, 60f)
                listOf(
                    glowR * 1.6f to 0.30f,
                    glowR * 1.0f to 0.50f,
                    glowR * 0.55f to 0.70f
                ).forEach { (radius, a) ->
                    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.typeface = typeface
                        this.textSize = fontSize
                        textAlign = basePaint.textAlign
                        letterSpacing = basePaint.letterSpacing
                        color = glowColorInt
                        alpha = (alphaInt * a).toInt().coerceIn(0, 255)
                        maskFilter = BlurMaskFilter(
                            radius.coerceIn(2f, 80f),
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
                        st.shadowBlur.coerceIn(0f, 50f),
                        st.shadowOffsetX.coerceIn(-40f, 40f),
                        st.shadowOffsetY.coerceIn(-40f, 40f),
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
                    strokeWidth = (st.strokeWidth * 2f).coerceIn(2f, 50f)
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
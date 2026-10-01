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
import java.io.File
import java.io.FileOutputStream
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

    fun renderCombinedOverlays(
        context: Context,
        textClips: List<EditorClip>,
        imageClips: List<EditorClip> = emptyList(),
        overlayClips: List<EditorClip> = emptyList(),
        W: Int,
        H: Int,
        fps: Int,
        totalDurationMs: Long
    ): List<TextOverlaySequence> {
        if (textClips.isEmpty() && imageClips.isEmpty() && overlayClips.isEmpty()) {
            return emptyList()
        }

        val totalFrames = ((totalDurationMs * fps) / 1000L).toInt().coerceAtLeast(1)
        val CHUNK_SIZE = 600
        val sequences = mutableListOf<TextOverlaySequence>()

        try {
            context.cacheDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("combined_") && f.name.endsWith(".png")) {
                    f.delete()
                }
            }
        } catch (_: Exception) {
        }

        // Sort text/sticker by track for z-index
        val sortedTextClips = textClips.sortedWith(
            compareBy({ it.trackIndex }, { it.timelineStartMs })
        )

        // Sort overlay clips by track
        val sortedOverlayClips = overlayClips.sortedWith(
            compareBy({ it.trackIndex }, { it.timelineStartMs })
        )

        var chunkStart = 0
        var chunkIdx = 0

        while (chunkStart < totalFrames) {
            val chunkEnd = (chunkStart + CHUNK_SIZE).coerceAtMost(totalFrames)
            val chunkFrames = chunkEnd - chunkStart

            for (i in 0 until chunkFrames) {
                val globalFrame = chunkStart + i
                val timelineMs = (globalFrame.toLong() * 1000L) / fps

                val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                // 1️⃣ Overlay clips (behind text) — exact same as preview
                sortedOverlayClips.forEach { clip ->
                    if (timelineMs >= clip.timelineStartMs &&
                        timelineMs < clip.timelineEndMs
                    ) {
                        val localSec = (timelineMs - clip.timelineStartMs) / 1000f
                        val ov = extractOverlay(clip)
                        if (ov != null && ov.isActive) {
                            try {
                                drawOverlayOnCanvas(canvas, localSec, ov, W, H)
                            } catch (e: Throwable) {
                                Log.e(
                                    "OVERLAY_RENDER",
                                    "Overlay failed: ${ov.type}", e
                                )
                            }
                        }
                    }
                }

                // 2️⃣ Text + sticker
                sortedTextClips.forEach { clip ->
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

                val file = File(
                    context.cacheDir,
                    "combined_${chunkIdx}_f%05d.png".format(i + 1)
                )
                FileOutputStream(file).use { fos ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 85, fos)
                }
                bmp.recycle()
            }

            val pattern = File(
                context.cacheDir,
                "combined_${chunkIdx}_f%05d.png"
            ).absolutePath

            val startSec = chunkStart.toDouble() / fps.toDouble()
            val endSec = chunkEnd.toDouble() / fps.toDouble()

            sequences.add(
                TextOverlaySequence(
                    pattern = pattern,
                    frameCount = chunkFrames,
                    fps = fps,
                    startNumber = 1,
                    startSec = startSec,
                    endSec = endSec
                )
            )

            chunkStart = chunkEnd
            chunkIdx++
        }

        Log.e(
            "TEXT_RENDER",
            "Combined: ${sequences.size} chunk(s), frames=$totalFrames, " +
                    "text=${sortedTextClips.size}, overlay=${sortedOverlayClips.size}"
        )
        return sequences
    }

    // ═══════════════════════════════════════════════════════════
    //  EXTRACT OVERLAY from clip
    // ═══════════════════════════════════════════════════════════
    private fun extractOverlay(clip: EditorClip): OverlayState? {
        // Direct overlay clip
        if (clip.isOverlayClip) {
            val ov = clip.overlay
            if (ov.type != "none") return ov
        }
        // Effect clip with overlay config
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

    // ═══════════════════════════════════════════════════════════
    //  DRAW OVERLAY — Render overlay to bitmap, then blit
    // ═══════════════════════════════════════════════════════════
    private fun drawOverlayOnCanvas(
        androidCanvas: Canvas,
        timeSec: Float,
        overlay: OverlayState,
        W: Int,
        H: Int
    ) {
        try {
            // 1. Create transparent bitmap
            val overlayBmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)

            // 2. Wrap in Compose ImageBitmap + Canvas
            val imageBitmap = overlayBmp.asImageBitmap()
            val composeCanvas = ComposeCanvas(imageBitmap)

            // 3. Draw via Compose CanvasDrawScope
            val drawScope = CanvasDrawScope()
            drawScope.draw(
                density = Density(1f, 1f),
                layoutDirection = LayoutDirection.Ltr,
                canvas = composeCanvas,
                size = ComposeSize(W.toFloat(), H.toFloat())
            ) {
                OverlayEngine.draw(this, timeSec, overlay)
            }

            // 4. Blit onto main Android canvas
            androidCanvas.drawBitmap(overlayBmp, 0f, 0f, null)

            // 5. Recycle
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
            // ═══ CONTENT (typewriter) ═══
            val content = if (st.animation.equals("typewriter", ignoreCase = true)) {
                val total = st.content.length
                val visible = (progress * total).toInt().coerceIn(0, total)
                st.content.substring(0, visible)
            } else st.content

            if (content.isEmpty()) return

            // ═══ FONT SIZE ═══
            val baseFontSize = st.fontSize.coerceAtLeast(8)
            val initialFontSize = (baseFontSize * (W.toFloat() / REFERENCE_WIDTH_PX))
                .coerceAtLeast(10f)

            // ═══ TYPEFACE ═══
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

            // ═══ MEASURE + auto-shrink ═══
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

            // ═══ LETTER SPACING ═══
            val spacingPx = st.letterSpacing * (W.toFloat() / REFERENCE_WIDTH_PX)
            val spacingEm = if (fontSize <= 0f) 0f
            else (spacingPx / fontSize).coerceIn(-0.3f, 0.3f)

            // ═══ BASE PAINT ═══
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

            // ═══ CLAMP POSITION ═══
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

            // ═══ CANVAS TRANSFORM ═══
            canvas.save()
            canvas.translate(cx + frame.translateX, cy + frame.translateY)
            canvas.rotate(sampled.rotation + frame.rotationZ)
            canvas.scale(
                (sampled.scale / 100f) * frame.scaleX,
                (sampled.scale / 100f) * frame.scaleY
            )

            val tx = 0f
            val ty = baseline - cy

            // ═══ ALPHA ═══
            val alphaTotal = (st.opacity / 100f * frame.alpha).coerceIn(0f, 1f)
            val alphaInt = (alphaTotal * 255).toInt().coerceIn(0, 255)

            if (alphaInt <= 0) {
                canvas.restore()
                return
            }

            // ═══ GLOW layers ═══
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

            // ═══ SHADOW ═══
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

            // ═══ STROKE ═══
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

            // ═══ FILL (gradient or solid) ═══
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
            Log.e("TEXT_RENDER", "drawTextWithFrame failed", e)
        }
    }
}
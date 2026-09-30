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
import com.moody.moodyvideoeditor.data.EditorClip
import java.io.File
import java.io.FileOutputStream

data class TextOverlaySequence(
    val pattern: String,
    val frameCount: Int,
    val fps: Int,
    val startNumber: Int,
    val startSec: Double,
    val endSec: Double
)

object TextBitmapRenderer {

    private const val REFERENCE_WIDTH_PX = 560f

    private val LOOPING_ANIMATIONS = setOf(
        "wave", "bounceWave", "sineWave", "waterRipple", "heatWave",
        "pulsingWave", "turbulent", "float", "squeezeStretch",
        "pendulum", "gentleTilt", "pulse", "shake", "flicker",
        "neonGlow", "staticNoise", "shakeJitter", "infiniteScroll",
        "flagWave", "cyberpunk", "matrixRain", "propeller", "spark"
    )

    fun renderCombinedOverlays(
        context: Context,
        textClips: List<EditorClip>,
        imageClips: List<EditorClip> = emptyList(),
        W: Int,
        H: Int,
        fps: Int,
        totalDurationMs: Long
    ): List<TextOverlaySequence> {
        if (textClips.isEmpty() && imageClips.isEmpty()) return emptyList()

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

        Log.e("TEXT_RENDER", "Combined: ${sequences.size} chunk(s), frames=$totalFrames")
        return sequences
    }

    private fun androidFamilyFor(fontName: String): String {
        val n = fontName.lowercase().trim()
        return when {
            n.contains("mono") || n.contains("courier") || n.contains("consol") ||
                    n.contains("menlo") || n.contains("monaco") || n.contains("code") ->
                "monospace"

            n.contains("script") || n.contains("brush") || n.contains("hand") ||
                    n.contains("comic") || n.contains("cursive") || n.contains("dancing") ||
                    n.contains("pacific") || n.contains("vibes") || n.contains("amita") ||
                    n.contains("chopin") || n.contains("musiclife") || n.contains("caveat") ||
                    n.contains("allura") || n.contains("satisfy") || n.contains("kaushan") ||
                    n.contains("parisienne") || n.contains("sacramento") ||
                    n.contains("tangerine") || n.contains("indie") || n.contains("patrick") ||
                    n.contains("kalam") ->
                "cursive"

            n.contains("serif") || n.contains("times") || n.contains("georgia") ||
                    n.contains("garamond") || n.contains("baskerville") ||
                    n.contains("playfair") || n.contains("cinzel") || n.contains("bodoni") ||
                    n.contains("cormorant") || n.contains("merriweather") ||
                    n.contains("lora") || n.contains("crimson") || n.contains("prata") ||
                    n.contains("cardo") || n.contains("spectral") || n.contains("abril") ->
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

        val sampled = TransformApplier
            .resolveLive(clip, localTimeSec)

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

        val frame = try {
            AnimationsEngine.computeFrame("none", 1f, localTimeSec)
        } catch (_: Throwable) {
            AnimationsEngine.Frame()
        }

        val sampled = TransformApplier
            .resolveLive(clip, localTimeSec)

        val baseSize = 48f * (W / REFERENCE_WIDTH_PX)
        val fontSize = baseSize * (sampled.scale / 100f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontSize
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
            color = Color.WHITE
        }

        val cx = sampled.x / 100f * W
        val cy = sampled.y / 100f * H
        val fm = paint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f

        canvas.save()
        canvas.translate(cx + frame.translateX, cy + frame.translateY)
        canvas.rotate(sampled.rotation + frame.rotationZ)
        canvas.scale(frame.scaleX, frame.scaleY)
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
            // ═══════════════════════════════════════════════════════
            //  1. CONTENT (typewriter handling)
            // ═══════════════════════════════════════════════════════
            val content = if (st.animation.equals("typewriter", ignoreCase = true)) {
                val total = st.content.length
                val visible = (progress * total).toInt().coerceIn(0, total)
                st.content.substring(0, visible)
            } else st.content

            if (content.isEmpty()) return

            // ═══════════════════════════════════════════════════════
            //  2. FONT SIZE calculation
            // ═══════════════════════════════════════════════════════
            val baseFontSize = st.fontSize.coerceAtLeast(8)
            val initialFontSize = (baseFontSize * (W.toFloat() / REFERENCE_WIDTH_PX))
                .coerceAtLeast(10f)

            // ═══════════════════════════════════════════════════════
            //  3. TYPEFACE
            // ═══════════════════════════════════════════════════════
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

            // ═══════════════════════════════════════════════════════
            //  4. MEASURE + auto-shrink to maxWidth
            // ═══════════════════════════════════════════════════════
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

            // ═══════════════════════════════════════════════════════
            //  5. LETTER SPACING (in em units, matching preview)
            // ═══════════════════════════════════════════════════════
            val spacingPx = st.letterSpacing * (W.toFloat() / REFERENCE_WIDTH_PX)
            val spacingEm = if (fontSize <= 0f) 0f
            else (spacingPx / fontSize).coerceIn(-0.3f, 0.3f)

            // ═══════════════════════════════════════════════════════
            //  6. BASE PAINT
            // ═══════════════════════════════════════════════════════
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

            // ═══════════════════════════════════════════════════════
            //  7. LIVE POSITION from sampled (keyframe-aware)
            // ═══════════════════════════════════════════════════════
            val cx = sampled.x / 100f * W
            val cy = sampled.y / 100f * H
            val fm = basePaint.fontMetrics
            val baseline = cy - (fm.ascent + fm.descent) / 2f

            // ═══════════════════════════════════════════════════════
            //  8. CANVAS TRANSFORM (raw pixels, animation offsets unscaled)
            // ═══════════════════════════════════════════════════════
            canvas.save()
            canvas.translate(cx + frame.translateX, cy + frame.translateY)
            canvas.rotate(sampled.rotation + frame.rotationZ)
            canvas.scale(
                (sampled.scale / 100f) * frame.scaleX,
                (sampled.scale / 100f) * frame.scaleY
            )

            val tx = 0f
            val ty = baseline - cy

            // ═══════════════════════════════════════════════════════
            //  9. ALPHA
            // ═══════════════════════════════════════════════════════
            val alphaTotal = (st.opacity / 100f * frame.alpha).coerceIn(0f, 1f)
            val alphaInt = (alphaTotal * 255).toInt().coerceIn(0, 255)

            if (alphaInt <= 0) {
                canvas.restore()
                return
            }

            // ═══════════════════════════════════════════════════════
            //  10. GLOW layers (behind)
            // ═══════════════════════════════════════════════════════
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

            // ═══════════════════════════════════════════════════════
            //  11. SHADOW (behind fill)
            // ═══════════════════════════════════════════════════════
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

            // ═══════════════════════════════════════════════════════
            //  12. STROKE outline
            // ═══════════════════════════════════════════════════════
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

            // ═══════════════════════════════════════════════════════
            //  13. FILL (with optional gradient)
            // ═══════════════════════════════════════════════════════
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

            // ═══════════════════════════════════════════════════════
            //  14. RESTORE canvas
            // ═══════════════════════════════════════════════════════
            canvas.restore()

        } catch (e: Throwable) {
            Log.e("TEXT_RENDER", "drawTextWithFrame failed", e)
        }
    }
}
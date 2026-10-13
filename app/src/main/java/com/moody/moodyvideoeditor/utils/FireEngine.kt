package com.moody.moodyvideoeditor.utils

import android.graphics.Paint
import android.graphics.Path
import com.moody.moodyvideoeditor.data.FireConfig
import com.moody.moodyvideoeditor.data.FireStyle
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

object FireEngine {

    private fun hash01(n: Double): Double {
        val x = sin(n * 12.9898 + 78.233) * 43758.5453
        return x - floor(x)
    }

    // ═══════════════════════════════════════════════════════════
    //  DRAW FIRE
    // ═══════════════════════════════════════════════════════════
    fun drawOnCanvas(
        canvas: android.graphics.Canvas,
        cfg: FireConfig,
        localTimeSec: Float,
        W: Int, H: Int
    ) {
        val intensity = (cfg.masterIntensity / 100f) *
                (cfg.intensity / 100f)
        if (intensity <= 0.01f) return

        when (cfg.style) {
            FireStyle.FIRE_BOTTOM -> drawFlames(
                canvas, cfg, localTimeSec, W, H,
                fromTop = false, intensity
            )
            FireStyle.FIRE_TOP -> drawFlames(
                canvas, cfg, localTimeSec, W, H,
                fromTop = true, intensity
            )
            FireStyle.LAVA_FLOW -> drawLava(
                canvas, cfg, localTimeSec, W, H, intensity
            )
            FireStyle.ASH_FALL -> drawParticles(
                canvas, cfg, localTimeSec, W, H,
                style = FireStyle.ASH_FALL, intensity
            )
            FireStyle.EMBER_RISE -> drawParticles(
                canvas, cfg, localTimeSec, W, H,
                style = FireStyle.EMBER_RISE, intensity
            )
            FireStyle.SMOKE_DRIFT -> drawParticles(
                canvas, cfg, localTimeSec, W, H,
                style = FireStyle.SMOKE_DRIFT, intensity
            )
            FireStyle.HEAT_HAZE -> drawHeatHaze(
                canvas, cfg, localTimeSec, W, H, intensity
            )
            FireStyle.FLAME_BORDER -> drawFlameBorder(
                canvas, cfg, localTimeSec, W, H, intensity
            )
            FireStyle.PLASMA_FIRE -> drawFlames(
                canvas, cfg, localTimeSec, W, H,
                fromTop = false, intensity, plasma = true
            )
            FireStyle.BLUE_FLAME -> drawFlames(
                canvas, cfg, localTimeSec, W, H,
                fromTop = false, intensity, blue = true
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  FLAMES — main fire effect
    // ═══════════════════════════════════════════════════════════
    private fun drawFlames(
        canvas: android.graphics.Canvas,
        cfg: FireConfig,
        timeSec: Float,
        W: Int, H: Int,
        fromTop: Boolean,
        intensity: Float,
        plasma: Boolean = false,
        blue: Boolean = false
    ) {
        val t = timeSec * cfg.speed.coerceIn(0.2f, 5f)
        val cols = cfg.density.coerceIn(4, 40)
        val waviness = cfg.waviness.coerceIn(0f, 1f)
        val baseH = (cfg.flameHeight.coerceIn(0.05f, 1f) * H).coerceAtLeast(30f)

        val wStep = W.toFloat() / cols

        // Layer 1: back layer (dark red / deep base)
        drawFlameLayer(
            canvas, cfg, t, W, H, cols, wStep, baseH,
            fromTop, layerScale = 1.4f, alphaMul = 0.35f,
            useColor = cfg.colorCool, waviness = waviness, plasma, blue,
            offset = 0.5f
        )
        // Layer 2: mid layer (orange)
        drawFlameLayer(
            canvas, cfg, t, W, H, cols, wStep, baseH,
            fromTop, layerScale = 1.0f, alphaMul = 0.55f,
            useColor = cfg.colorMid, waviness = waviness, plasma, blue,
            offset = 1.3f
        )
        // Layer 3: hot layer (yellow/white)
        drawFlameLayer(
            canvas, cfg, t, W, H, cols, wStep, baseH,
            fromTop, layerScale = 0.65f, alphaMul = 0.75f,
            useColor = cfg.colorHot, waviness = waviness, plasma, blue,
            offset = 2.1f
        )

        // Sparks rising from fire
        if (cfg.particleCount > 0) {
            drawFireSparks(
                canvas, cfg, t, W, H, fromTop, intensity
            )
        }
    }

    private fun drawFlameLayer(
        canvas: android.graphics.Canvas,
        cfg: FireConfig,
        t: Float,
        W: Int, H: Int,
        cols: Int,
        wStep: Float,
        baseH: Float,
        fromTop: Boolean,
        layerScale: Float,
        alphaMul: Float,
        useColor: Long,
        waviness: Float,
        plasma: Boolean,
        blue: Boolean,
        offset: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            val r = ((useColor shr 16) and 0xFF).toInt()
            val g = ((useColor shr 8) and 0xFF).toInt()
            val b = (useColor and 0xFF).toInt()
            color = android.graphics.Color.argb(
                (alphaMul * 220).toInt().coerceIn(0, 255),
                r, g, b
            )
            maskFilter = android.graphics.BlurMaskFilter(
                (wStep * 1.5f).coerceAtLeast(4f),
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
        }

        val path = Path()
        val points = 60
        val totalW = W.toFloat()

        for (i in 0..points) {
            val frac = i.toFloat() / points
            val x = frac * totalW

            // Multiple sine waves create flame fingers
            val wave1 = sin((frac * cols * 1.7f + t * 2.5f + offset).toDouble())
                .toFloat()
            val wave2 = sin((frac * cols * 3.1f - t * 3.7f + offset * 1.3f).toDouble())
                .toFloat() * 0.5f
            val wave3 = sin((frac * cols * 0.9f + t * 1.3f).toDouble())
                .toFloat() * 0.7f

            val hNorm = 0.5f +
                    wave1 * 0.35f * waviness +
                    wave2 * 0.25f * waviness +
                    wave3 * 0.20f * waviness

            val flameH = (baseH * layerScale * (0.55f + hNorm * 0.65f))
                .coerceAtLeast(8f)

            val y = if (fromTop) flameH else H.toFloat() - flameH

            if (i == 0) path.moveTo(x, if (fromTop) 0f else H.toFloat())
            path.lineTo(x, y)
        }

        // Close along edge
        if (fromTop) {
            path.lineTo(W.toFloat(), 0f)
            path.lineTo(0f, 0f)
        } else {
            path.lineTo(W.toFloat(), H.toFloat())
            path.lineTo(0f, H.toFloat())
        }
        path.close()

        canvas.drawPath(path, paint)
    }

    // ═══════════════════════════════════════════════════════════
    //  FIRE SPARKS
    // ═══════════════════════════════════════════════════════════
    private fun drawFireSparks(
        canvas: android.graphics.Canvas,
        cfg: FireConfig,
        t: Float,
        W: Int, H: Int,
        fromTop: Boolean,
        intensity: Float
    ) {
        val count = (cfg.particleCount * intensity).toInt()
            .coerceIn(10, 400)

        for (i in 0 until count) {
            val seed = i.toDouble()
            val startX = hash01(seed * 1.13).toFloat() * W
            val periodSec = 1.5f + hash01(seed * 2.37).toFloat() * 2f
            val phase = (t / periodSec) % 1f
            val lifeFrac = phase

            if (lifeFrac < 0f || lifeFrac > 1f) continue

            val speed = cfg.particleSpeed * (0.6f + hash01(seed * 3.71).toFloat())
            val startY = if (fromTop) 0f else H.toFloat()
            val moveDist = speed * phase * 1.5f
            val y = if (fromTop) moveDist else (H.toFloat() - moveDist)

            val drift = sin((t * 2f + i).toDouble()).toFloat() * 25f * cfg.waviness
            val x = startX + drift

            val alpha = ((1f - lifeFrac) * intensity * 200).toInt()
                .coerceIn(0, 255)
            val size = 1.2f + hash01(seed * 5.13).toFloat() * 2.5f

            // Color per spark
            val col = if (hash01(seed * 7.11) > 0.5f) cfg.colorHot else cfg.colorMid
            val r = ((col shr 16) and 0xFF).toInt()
            val g = ((col shr 8) and 0xFF).toInt()
            val b = (col and 0xFF).toInt()

            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(alpha, r, g, b)
                maskFilter = android.graphics.BlurMaskFilter(
                    size * 1.5f, android.graphics.BlurMaskFilter.Blur.NORMAL
                )
            }
            canvas.drawCircle(x, y, size, p)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  LAVA FLOW
    // ═══════════════════════════════════════════════════════════
    private fun drawLava(
        canvas: android.graphics.Canvas,
        cfg: FireConfig,
        timeSec: Float,
        W: Int, H: Int,
        intensity: Float
    ) {
        val t = timeSec * cfg.speed.coerceIn(0.2f, 5f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val blobCount = 25
        for (i in 0 until blobCount) {
            val seed = i.toDouble()
            val seedF = seed.toFloat()
            val bx = hash01(seed * 1.11).toFloat() * W
            val flow = (t * (20f + hash01(seed * 2.3).toFloat() * 30f) +
                    seedF * 50f) % (H.toFloat() + 100f)
            val by = H.toFloat() - flow
            val size = 30f + hash01(seed * 3.7).toFloat() * 70f

            val a = (0.5f * intensity * 255).toInt().coerceIn(0, 255)
            val r = ((cfg.colorMid shr 16) and 0xFF).toInt()
            val g = ((cfg.colorMid shr 8) and 0xFF).toInt()
            val b = (cfg.colorMid and 0xFF).toInt()
            paint.color = android.graphics.Color.argb(a, r, g, b)
            paint.maskFilter = android.graphics.BlurMaskFilter(
                size * 0.5f, android.graphics.BlurMaskFilter.Blur.NORMAL
            )
            canvas.drawCircle(bx, by, size, paint)

            val rHot = ((cfg.colorHot shr 16) and 0xFF).toInt()
            val gHot = ((cfg.colorHot shr 8) and 0xFF).toInt()
            val bHot = (cfg.colorHot and 0xFF).toInt()
            paint.color = android.graphics.Color.argb(a, rHot, gHot, bHot)
            paint.maskFilter = android.graphics.BlurMaskFilter(
                size * 0.25f, android.graphics.BlurMaskFilter.Blur.NORMAL
            )
            canvas.drawCircle(bx, by, size * 0.5f, paint)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PARTICLES (ash, embers, smoke)
    // ═══════════════════════════════════════════════════════════
    private fun drawParticles(
        canvas: android.graphics.Canvas,
        cfg: FireConfig,
        timeSec: Float,
        W: Int, H: Int,
        style: FireStyle,
        intensity: Float
    ) {
        val t = timeSec * cfg.speed.coerceIn(0.2f, 5f)
        val count = (cfg.particleCount * intensity).toInt()
            .coerceIn(10, 500)

        for (i in 0 until count) {
            val seed = i.toDouble()
            val startX = hash01(seed * 1.17).toFloat() * W
            val period = 2f + hash01(seed * 2.91).toFloat() * 4f
            val phase = (t / period + hash01(seed * 3.13).toFloat()) % 1f

            val speed = cfg.particleSpeed *
                    (0.5f + hash01(seed * 4.27).toFloat())
            val moveDist = speed * phase * 2f

            val x: Float
            val y: Float
            val size: Float
            val col: Long
            val alphaBase: Float

            when (style) {
                FireStyle.ASH_FALL -> {
                    x = startX + sin((t * 1.5f + i).toDouble())
                        .toFloat() * 20f
                    y = moveDist
                    size = 2f + hash01(seed * 5.11).toFloat() * 3f
                    col = cfg.colorMid
                    alphaBase = 0.7f * (1f - phase * 0.5f)
                }
                FireStyle.EMBER_RISE -> {
                    x = startX + sin((t * 2f + i).toDouble()).toFloat() * 25f
                    y = H.toFloat() - moveDist
                    size = 1.5f + hash01(seed * 6.13).toFloat() * 3f
                    col = if (hash01(seed * 7.3) > 0.5f) cfg.colorHot
                    else cfg.colorMid
                    alphaBase = 0.85f * (1f - phase * 0.6f)
                }
                FireStyle.SMOKE_DRIFT -> {
                    x = startX + t * 30f + sin((t + i).toDouble())
                        .toFloat() * 30f
                    y = H.toFloat() * (0.3f + hash01(seed * 8.1).toFloat() * 0.7f)
                    size = 15f + hash01(seed * 9.3).toFloat() * 30f
                    col = cfg.colorMid
                    alphaBase = 0.25f
                }
                else -> return
            }

            val finalX = (x % W + W) % W
            val alpha = (alphaBase * intensity * 255).toInt().coerceIn(0, 255)

            val r = ((col shr 16) and 0xFF).toInt()
            val g = ((col shr 8) and 0xFF).toInt()
            val b = (col and 0xFF).toInt()

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(alpha, r, g, b)
                if (style == FireStyle.SMOKE_DRIFT ||
                    style == FireStyle.EMBER_RISE
                ) {
                    maskFilter = android.graphics.BlurMaskFilter(
                        size * 1.5f, android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
                }
            }
            canvas.drawCircle(finalX, y, size, paint)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  HEAT HAZE
    // ═══════════════════════════════════════════════════════════
    private fun drawHeatHaze(
        canvas: android.graphics.Canvas,
        cfg: FireConfig,
        timeSec: Float,
        W: Int, H: Int,
        intensity: Float
    ) {
        val t = timeSec * cfg.speed.coerceIn(0.2f, 5f)
        // Draw wavy semi-transparent orange bands
        val bandCount = 12
        for (i in 0 until bandCount) {
            val yFrac = i.toFloat() / bandCount
            val y = yFrac * H
            val wave = sin((t * 2f + i * 0.7f).toDouble()).toFloat()

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                val a = (0.08f * intensity * 255).toInt().coerceIn(0, 255)
                color = android.graphics.Color.argb(
                    a,
                    ((cfg.colorMid shr 16) and 0xFF).toInt(),
                    ((cfg.colorMid shr 8) and 0xFF).toInt(),
                    (cfg.colorMid and 0xFF).toInt()
                )
                maskFilter = android.graphics.BlurMaskFilter(
                    25f, android.graphics.BlurMaskFilter.Blur.NORMAL
                )
            }
            canvas.drawRect(
                0f, y + wave * 15f, W.toFloat(), y + 40f + wave * 15f, paint
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  FLAME BORDER
    // ═══════════════════════════════════════════════════════════
    private fun drawFlameBorder(
        canvas: android.graphics.Canvas,
        cfg: FireConfig,
        timeSec: Float,
        W: Int, H: Int,
        intensity: Float
    ) {
        // Top
        drawFlames(
            canvas, cfg.copy(flameHeight = 0.15f), timeSec, W, H,
            fromTop = true, intensity * 0.7f
        )
        // Bottom
        drawFlames(
            canvas, cfg.copy(flameHeight = 0.15f), timeSec, W, H,
            fromTop = false, intensity * 0.7f
        )
        // Left edge: rotate
        canvas.save()
        canvas.rotate(-90f, 0f, 0f)
        drawFlames(
            canvas, cfg.copy(flameHeight = 0.15f, density = 8),
            timeSec, H, W, fromTop = true, intensity * 0.5f
        )
        canvas.restore()
        // Right edge
        canvas.save()
        canvas.rotate(90f, W.toFloat(), 0f)
        drawFlames(
            canvas, cfg.copy(flameHeight = 0.15f, density = 8),
            timeSec, H, W, fromTop = true, intensity * 0.5f
        )
        canvas.restore()
    }
}
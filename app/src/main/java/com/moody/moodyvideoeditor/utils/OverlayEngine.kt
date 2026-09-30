package com.moody.moodyvideoeditor.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.moody.moodyvideoeditor.data.OverlayState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mirrors js/workspace/overlayRenderer.js
 * Deterministic overlay rendering — same frame = same pattern.
 */
object OverlayEngine {

    /** Mirrors JS hash(n) — pseudo-random deterministic */
    fun hash(n: Double): Double {
        val x = sin(n * 12.9898 + 78.233) * 43758.5453
        return x - kotlin.math.floor(x)
    }

    private fun fI(v: Float, min: Int = 0, max: Int = 255): Int =
        v.toInt().coerceIn(min, max)

    // ═══════════════════════════════════════════════════════════
    //  MAIN DISPATCH
    // ═══════════════════════════════════════════════════════════
    fun draw(
        scope: DrawScope,
        time: Float,
        overlay: OverlayState
    ) {
        if (!overlay.isActive) return
        val W = scope.size.width
        val H = scope.size.height
        val I = (overlay.intensity / 100f).coerceIn(0f, 2f)
        val color = Color(overlay.color.toULong().toLong())

        when (overlay.type) {
            // Particles
            "rain" -> drawRain(scope, W, H, time, I, color)
            "snow" -> drawSnow(scope, W, H, time, I, color)
            "dust" -> drawDust(scope, W, H, time, I, color)
            "sparks" -> drawSparks(scope, W, H, time, I, color)
            "embers" -> drawEmbers(scope, W, H, time, I)
            "stars" -> drawStars(scope, W, H, time, I, color)
            "bokeh" -> drawBokeh(scope, W, H, time, I, color)
            "fireFlies" -> drawFireFlies(scope, W, H, time, I)

            // Atmosphere
            "fog" -> drawFog(scope, W, H, time, I, color)
            "smoke" -> drawSmoke(scope, W, H, time, I)
            "haze" -> drawHaze(scope, W, H, I, color)
            "mist" -> drawMist(scope, W, H, time, I, color)

            // Noise
            "noise" -> drawNoise(scope, W, H, time, I, blackOnly = false)
            "filmGrain" -> drawNoise(scope, W, H, time, I * 0.6f, blackOnly = false)
            "blackNoise" -> drawNoise(scope, W, H, time, I, blackOnly = true)
            "whiteNoise" -> drawWhiteNoise(scope, W, H, time, I)
            "scanlines" -> drawScanlines(scope, W, H, I)
            "staticTV" -> drawStaticTV(scope, W, H, time, I)

            // Light
            "lightLeak" -> drawLightLeak(scope, W, H, time, I)
            "lensFlare" -> drawLensFlare(scope, W, H, I)
            "bloom" -> drawBloom(scope, W, H, I, color)
            "sunburst" -> drawSunburst(scope, W, H, time, I)
            "godRays" -> drawGodRays(scope, W, H, time, I)

            // Flicker
            "flicker" -> drawFlicker(scope, W, H, time, I, 0.5f, 15f)
            "strobe" -> drawFlicker(scope, W, H, time, I, 0.85f, 8f)
            "pulseFx" -> drawPulseFx(scope, W, H, time, I)
            "blink" -> drawFlicker(scope, W, H, time, I, 0.95f, 4f)

            // Tone wash
            "blueLake" -> drawToneWash(scope, W, H, I, 0xFF0A4A8A, 0.55f)
            "warmWash" -> drawToneWash(scope, W, H, I, 0xFFFF8A3A, 0.35f)
            "coolWash" -> drawToneWash(scope, W, H, I, 0xFF3A8AFF, 0.35f)
            "tealWash" -> drawToneWash(scope, W, H, I, 0xFF00D4A0, 0.4f)
            "roseWash" -> drawToneWash(scope, W, H, I, 0xFFFF3A80, 0.4f)

            // Edges
            "sharpenEdges" -> drawSharpenEdges(scope, W, H, I)
            "edgeGlow" -> drawEdgeGlow(scope, W, H, I, color)

            // Misc
            "vignette" -> drawVignette(scope, W, H, I)
            "blackBars" -> drawBlackBars(scope, W, H, I)
            "vhsLines" -> drawVhsLines(scope, W, H, time, I)
            "glitchBars" -> drawGlitchBars(scope, W, H, time, I)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PARTICLES
    // ═══════════════════════════════════════════════════════════
    private fun drawRain(
        scope: DrawScope,
        W: Float,
        H: Float,
        time: Float,
        I: Float,
        color: Color
    ) {
        val count = (180 * I).toInt().coerceAtLeast(0)
        for (i in 0 until count) {
            val a = hash(i.toDouble()).toFloat()
            val b = hash((i + 101).toDouble()).toFloat()
            val x = (a * W + time * 180f) % W
            val y = (b * H + time * 850f + i * 37f) % H
            val len = 12f + b * 18f
            scope.drawLine(
                color = color.copy(alpha = 0.55f),
                start = Offset(x, y),
                end = Offset(x - 3f, y + len),
                strokeWidth = 1.4f
            )
        }
    }

    private fun drawSnow(
        scope: DrawScope,
        W: Float,
        H: Float,
        time: Float,
        I: Float,
        color: Color
    ) {
        val count = (140 * I).toInt().coerceAtLeast(0)
        for (i in 0 until count) {
            val a = hash(i.toDouble()).toFloat()
            val b = hash((i + 202).toDouble()).toFloat()
            val c = hash((i + 303).toDouble()).toFloat()
            val size = 1.5f + a * 2.5f
            val drift = sin((time * 0.8f + i).toDouble()).toFloat() * 20f
            val x = (b * W + drift + W) % W
            val y = (c * H + time * (60f + a * 40f) + i * 13f) % H
            scope.drawCircle(
                color = color.copy(alpha = (0.55f + a * 0.4f).coerceIn(0f, 1f)),
                radius = size,
                center = Offset(x, y)
            )
        }
    }

    private fun drawDust(
        scope: DrawScope,
        W: Float,
        H: Float,
        time: Float,
        I: Float,
        color: Color
    ) {
        val count = (120 * I).toInt().coerceAtLeast(0)
        for (i in 0 until count) {
            val a = hash((i + 500).toDouble()).toFloat()
            val b = hash((i + 600).toDouble()).toFloat()
            val c = hash((i + 700).toDouble()).toFloat()
            val drift = sin((time * 1.2f + i * 0.3f).toDouble()).toFloat() * 25f
            val driftY = cos((time * 0.9f + i * 0.5f).toDouble()).toFloat() * 15f
            val x = (a * W + drift + W) % W
            val y = (b * H + driftY + H) % H
            val size = 0.6f + c * 1.4f
            scope.drawCircle(
                color = color.copy(alpha = (0.35f + c * 0.5f).coerceIn(0f, 1f)),
                radius = size,
                center = Offset(x, y)
            )
        }
    }

    private fun drawSparks(
        scope: DrawScope,
        W: Float,
        H: Float,
        time: Float,
        I: Float,
        color: Color
    ) {
        val count = (70 * I).toInt().coerceAtLeast(0)
        for (i in 0 until count) {
            val a = hash((i + 800).toDouble()).toFloat()
            val b = hash((i + 900).toDouble()).toFloat()
            val speed = 200f + a * 400f
            val x = a * W
            val yRaw = (b * H - time * speed) % H
            val y = (yRaw + H) % H
            if (y < -20f) continue
            val size = 1f + a * 2f
            scope.drawCircle(
                color = color.copy(alpha = (0.6f + b * 0.4f).coerceIn(0f, 1f)),
                radius = size,
                center = Offset(x, y)
            )
        }
    }

    private fun drawEmbers(scope: DrawScope, W: Float, H: Float, time: Float, I: Float) {
        val count = (80 * I).toInt().coerceAtLeast(0)
        for (i in 0 until count) {
            val a = hash((i + 1100).toDouble()).toFloat()
            val b = hash((i + 1200).toDouble()).toFloat()
            val speed = 40f + a * 80f
            val x = a * W + sin((time * 2f + i).toDouble()).toFloat() * 20f
            val y = H - ((b * H + time * speed) % H)
            val size = 1f + b * 2f
            val col = if (a > 0.5f) Color(0xFFFF6B1A) else Color(0xFFFFCC00)
            scope.drawCircle(
                color = col.copy(alpha = (0.5f + a * 0.4f).coerceIn(0f, 1f)),
                radius = size,
                center = Offset(x, y)
            )
        }
    }

    private fun drawStars(
        scope: DrawScope,
        W: Float,
        H: Float,
        time: Float,
        I: Float,
        color: Color
    ) {
        val count = (200 * I).toInt().coerceAtLeast(0)
        for (i in 0 until count) {
            val a = hash((i + 1300).toDouble()).toFloat()
            val b = hash((i + 1400).toDouble()).toFloat()
            val c = hash((i + 1500).toDouble()).toFloat()
            val twinkle = 0.5f + abs(sin((time * 3f + i * 0.7f).toDouble()).toFloat()) * 0.5f
            val x = a * W
            val y = b * H
            val size = 0.5f + c * 1.5f
            scope.drawCircle(
                color = color.copy(alpha = (twinkle * (0.4f + c * 0.6f)).coerceIn(0f, 1f)),
                radius = size,
                center = Offset(x, y)
            )
        }
    }

    private fun drawBokeh(
        scope: DrawScope,
        W: Float,
        H: Float,
        time: Float,
        I: Float,
        color: Color
    ) {
        val count = (30 * I).toInt().coerceAtLeast(0)
        val r = fI(color.red * 255f)
        val g = fI(color.green * 255f)
        val b = fI(color.blue * 255f)
        for (i in 0 until count) {
            val a = hash((i + 1600).toDouble()).toFloat()
            val bb = hash((i + 1700).toDouble()).toFloat()
            val c = hash((i + 1800).toDouble()).toFloat()
            val drift = sin((time * 0.4f + i * 0.8f).toDouble()).toFloat() * 40f
            val x = (a * W + drift + W) % W
            val y = (bb * H + cos((time * 0.3f + i).toDouble()).toFloat() * 30f + H) % H
            val size = 15f + c * 40f
            scope.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(r, g, b, 140),
                        Color(r, g, b, 0)
                    ),
                    center = Offset(x, y),
                    radius = size
                ),
                radius = size,
                center = Offset(x, y)
            )
        }
    }

    private fun drawFireFlies(scope: DrawScope, W: Float, H: Float, time: Float, I: Float) {
        val count = (25 * I).toInt().coerceAtLeast(0)
        for (i in 0 until count) {
            val a = hash((i + 1900).toDouble()).toFloat()
            val b = hash((i + 2000).toDouble()).toFloat()
            val c = hash((i + 2100).toDouble()).toFloat()
            val drift = sin((time * 1.5f + i * 1.3f).toDouble()).toFloat() * 60f
            val driftY = cos((time * 1.2f + i * 0.9f).toDouble()).toFloat() * 40f
            val x = (a * W + drift + W) % W
            val y = (b * H + driftY + H) % H
            val size = 2f + c * 3f
            val pulse = 0.4f + abs(sin((time * 4f + i).toDouble()).toFloat()) * 0.6f
            scope.drawCircle(
                color = Color(1f, 0.94f, 0.47f, pulse * 0.9f),
                radius = size,
                center = Offset(x, y)
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ATMOSPHERE
    // ═══════════════════════════════════════════════════════════
    private fun drawFog(scope: DrawScope, W: Float, H: Float, time: Float, I: Float, color: Color) {
        val r = fI(color.red * 255f)
        val g = fI(color.green * 255f)
        val b = fI(color.blue * 255f)
        val a1 = fI(38f * I)
        for (i in 0 until 8) {
            val a = hash((i + 3000).toDouble()).toFloat()
            val bb = hash((i + 3100).toDouble()).toFloat()
            val c = hash((i + 3200).toDouble()).toFloat()
            val drift = (time * (5f + a * 15f) + i * 200f) % (W + 400f) - 200f
            val y = bb * H
            val size = 200f + c * 300f
            scope.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(red = r, green = g, blue = b, alpha = a1),
                        Color(red = r, green = g, blue = b, alpha = 0)
                    ),
                    center = Offset(drift, y),
                    radius = size
                ),
                radius = size,
                center = Offset(drift, y)
            )
        }
    }

    private fun drawSmoke(scope: DrawScope, W: Float, H: Float, time: Float, I: Float) {
        val a1 = fI(31f * I)
        for (i in 0 until 10) {
            val a = hash((i + 3300).toDouble()).toFloat()
            val b = hash((i + 3400).toDouble()).toFloat()
            val drift = (time * (3f + a * 8f) + i * 150f) % (W + 300f) - 150f
            val y = H - ((time * (10f + b * 30f) + i * 100f) % (H + 200f))
            val size = 120f + a * 200f
            scope.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(red = 200, green = 200, blue = 210, alpha = a1),
                        Color(red = 200, green = 200, blue = 210, alpha = 0)
                    ),
                    center = Offset(drift, y),
                    radius = size
                ),
                radius = size,
                center = Offset(drift, y)
            )
        }
    }

    private fun drawHaze(scope: DrawScope, W: Float, H: Float, I: Float, color: Color) {
        scope.drawRect(
            color = color.copy(alpha = (0.35f * I).coerceIn(0f, 1f)),
            topLeft = Offset.Zero,
            size = Size(W, H)
        )
    }

    private fun drawMist(
        scope: DrawScope,
        W: Float,
        H: Float,
        time: Float,
        I: Float,
        color: Color
    ) {
        val r = fI(color.red * 255f)
        val g = fI(color.green * 255f)
        val b = fI(color.blue * 255f)
        val a1 = fI(76f * I)
        for (i in 0 until 5) {
            val a = hash((i + 3500).toDouble()).toFloat()
            val drift = (time * (8f + a * 10f) + i * 300f) % (W + 400f) - 200f
            val size = 250f + a * 200f
            val y = H * 0.7f
            scope.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(red = r, green = g, blue = b, alpha = a1),
                        Color(red = r, green = g, blue = b, alpha = 0)
                    ),
                    center = Offset(drift, y),
                    radius = size
                ),
                radius = size,
                center = Offset(drift, y)
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  NOISE / TEXTURE
    // ═══════════════════════════════════════════════════════════
    private fun drawNoise(
        scope: DrawScope,
        W: Float,
        H: Float,
        time: Float,
        I: Float,
        blackOnly: Boolean
    ) {
        val seed = (time * 24f).toInt()
        val step = 3f
        val alphaI = fI(140f * I)
        var y = 0f
        while (y < H) {
            var x = 0f
            while (x < W) {
                val n = hash((x * 12.9898 + y * 78.233 + seed * 17.13).toDouble())
                if (n > 0.55) {
                    val v = if (blackOnly) 0 else fI((n * 255).toFloat())
                    scope.drawRect(
                        color = Color(red = v, green = v, blue = v, alpha = alphaI),
                        topLeft = Offset(x, y),
                        size = Size(step, step)
                    )
                }
                x += step
            }
            y += step
        }
    }

    private fun drawWhiteNoise(scope: DrawScope, W: Float, H: Float, time: Float, I: Float) {
        val seed = (time * 30f).toInt()
        val count = (400 * I).toInt().coerceAtLeast(0)
        val alphaF = (0.45f * I).coerceIn(0f, 1f)
        for (i in 0 until count) {
            val x = hash((i + seed * 3.13).toDouble()).toFloat() * W
            val y = hash((i + seed * 7.77).toDouble()).toFloat() * H
            val size = 1f + hash((i + seed).toDouble()).toFloat() * 3f
            scope.drawRect(
                color = Color.White.copy(alpha = alphaF),
                topLeft = Offset(x, y),
                size = Size(size, size)
            )
        }
    }

    private fun drawScanlines(scope: DrawScope, W: Float, H: Float, I: Float) {
        val alphaF = (0.28f * I).coerceIn(0f, 1f)
        var y = 0f
        while (y < H) {
            scope.drawRect(
                color = Color.Black.copy(alpha = alphaF),
                topLeft = Offset(0f, y),
                size = Size(W, 1.5f)
            )
            y += 4f
        }
    }

    private fun drawStaticTV(scope: DrawScope, W: Float, H: Float, time: Float, I: Float) {
        val seed = (time * 20f).toInt()
        scope.drawRect(
            color = Color.Black.copy(alpha = (0.4f * I).coerceIn(0f, 1f)),
            topLeft = Offset.Zero, size = Size(W, H)
        )
        val count = (500 * I).toInt().coerceAtLeast(0)
        val alphaI = fI(140f * I)
        for (i in 0 until count) {
            val x = hash((i + seed * 5.7).toDouble()).toFloat() * W
            val y = hash((i + seed * 11.3).toDouble()).toFloat() * H
            val v = fI((hash((i + seed * 3.1).toDouble()) * 255).toFloat())
            scope.drawRect(
                color = Color(red = v, green = v, blue = v, alpha = alphaI),
                topLeft = Offset(x, y),
                size = Size(3f, 3f)
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  LIGHT
    // ═══════════════════════════════════════════════════════════
    private fun drawLightLeak(scope: DrawScope, W: Float, H: Float, time: Float, I: Float) {
        val x = W * (0.5f + sin((time * 0.6f).toDouble()).toFloat() * 0.4f)
        val size = maxOf(W, H) * 0.9f
        val a1 = (140f * I).toInt().coerceIn(0, 255) / 255f
        val a2 = (64f * I).toInt().coerceIn(0, 255) / 255f
        scope.drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(red = 1f, green = 0.63f, blue = 0.31f, alpha = a1),
                    Color(red = 1f, green = 0.31f, blue = 0.59f, alpha = a2),
                    Color(red = 1f, green = 0f, blue = 0.39f, alpha = 0f)
                ),
                center = Offset(x, H * 0.3f),
                radius = size
            ),
            radius = size,
            center = Offset(x, H * 0.3f)
        )
    }

    private fun drawLensFlare(scope: DrawScope, W: Float, H: Float, I: Float) {
        val cx = W * 0.7f
        val cy = H * 0.3f
        val size = maxOf(W, H) * 0.6f
        scope.drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = (0.8f * I).coerceIn(0f, 1f)),
                    Color(0.7f, 0.86f, 1f, (0.4f * I).coerceIn(0f, 1f)),
                    Color(0.7f, 0.86f, 1f, 0f)
                ),
                center = Offset(cx, cy),
                radius = size
            ),
            radius = size,
            center = Offset(cx, cy)
        )
    }

    private fun drawBloom(scope: DrawScope, W: Float, H: Float, I: Float, color: Color) {
        val r = fI(color.red * 255f)
        val g = fI(color.green * 255f)
        val b = fI(color.blue * 255f)
        val size = maxOf(W, H) * 0.7f
        val a1 = fI(152f * I)
        scope.drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(red = r, green = g, blue = b, alpha = a1),
                    Color(red = r, green = g, blue = b, alpha = 0)
                ),
                center = Offset(W / 2f, H / 2f),
                radius = size
            ),
            radius = size,
            center = Offset(W / 2f, H / 2f)
        )
    }

    private fun drawSunburst(scope: DrawScope, W: Float, H: Float, time: Float, I: Float) {
        val cx = W / 2f
        val cy = H / 2f
        val rays = 16
        val len = maxOf(W, H) * 1.2f
        val rot = time * 0.4f
        val alphaI = fI(89f * I)
        for (i in 0 until rays) {
            val a = (i.toFloat() / rays) * 2f * Math.PI.toFloat() + rot
            val dx = cos(a) * len
            val dy = sin(a) * len
            val perpX = -sin(a) * 30f
            val perpY = cos(a) * 30f
            val path = Path().apply {
                moveTo(cx, cy)
                lineTo(cx + dx + perpX, cy + dy + perpY)
                lineTo(cx + dx - perpX, cy + dy - perpY)
                close()
            }
            scope.drawPath(
                path = path,
                color = Color(red = 255, green = 220, blue = 150, alpha = alphaI)
            )
        }
    }

    private fun drawGodRays(scope: DrawScope, W: Float, H: Float, time: Float, I: Float) {
        val srcX = W * 0.3f + sin((time * 0.3f).toDouble()).toFloat() * 40f
        val srcY = -H * 0.2f
        val rays = 12
        val alphaI = fI(71f * I)
        for (i in 0 until rays) {
            val a = (i.toFloat() / rays) * Math.PI.toFloat() * 0.8f -
                    Math.PI.toFloat() * 0.4f + Math.PI.toFloat() * 0.5f
            val dx = cos(a)
            val dy = sin(a)
            val path = Path().apply {
                moveTo(srcX - 10f, srcY)
                lineTo(srcX + 10f, srcY)
                lineTo(srcX + dx * 60f + 60f, srcY + dy * H * 1.5f)
                lineTo(srcX + dx * 60f - 60f, srcY + dy * H * 1.5f)
                close()
            }
            scope.drawPath(
                path = path,
                color = Color(red = 255, green = 240, blue = 200, alpha = alphaI)
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  FLICKER
    // ═══════════════════════════════════════════════════════════
    private fun drawFlicker(
        scope: DrawScope,
        W: Float,
        H: Float,
        time: Float,
        I: Float,
        amount: Float,
        hz: Float
    ) {
        val phase = sin((time * hz * Math.PI * 2f).toDouble()).toFloat()
        val v = if (phase > 0f) amount * I else 0f
        if (v <= 0.01f) return
        scope.drawRect(
            color = Color.Black.copy(alpha = v.coerceAtMost(1f)),
            topLeft = Offset.Zero, size = Size(W, H)
        )
    }

    private fun drawPulseFx(scope: DrawScope, W: Float, H: Float, time: Float, I: Float) {
        val v = abs(sin((time * 2.5f).toDouble()).toFloat()) * 0.4f * I
        scope.drawRect(
            color = Color.White.copy(alpha = v.coerceAtMost(1f)),
            topLeft = Offset.Zero, size = Size(W, H)
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  TONE WASH
    // ═══════════════════════════════════════════════════════════
    private fun drawToneWash(
        scope: DrawScope,
        W: Float,
        H: Float,
        I: Float,
        colorLong: Long,
        baseAlpha: Float
    ) {
        val c = Color(colorLong.toULong().toLong())
        scope.drawRect(
            color = c.copy(alpha = (baseAlpha * I).coerceIn(0f, 1f)),
            topLeft = Offset.Zero, size = Size(W, H)
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  EDGES
    // ═══════════════════════════════════════════════════════════
    private fun drawSharpenEdges(scope: DrawScope, W: Float, H: Float, I: Float) {
        val a = (I * 0.4f).coerceIn(0f, 1f)
        scope.drawRect(
            color = Color.White.copy(alpha = a * 0.15f),
            topLeft = Offset(0f, 0f),
            size = Size(W, H),
            style = Stroke(width = 2f)
        )
    }

    private fun drawEdgeGlow(scope: DrawScope, W: Float, H: Float, I: Float, color: Color) {
        scope.drawRect(
            color = color.copy(alpha = (I * 0.45f).coerceIn(0f, 1f)),
            topLeft = Offset(0f, 0f),
            size = Size(W, H),
            style = Stroke(width = 3f)
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  MISC
    // ═══════════════════════════════════════════════════════════
    private fun drawVignette(scope: DrawScope, W: Float, H: Float, I: Float) {
        val radius = maxOf(W, H) * 0.75f
        scope.drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = (0.85f * I).coerceIn(0f, 1f))
                ),
                center = Offset(W / 2f, H / 2f),
                radius = radius
            ),
            topLeft = Offset.Zero, size = Size(W, H)
        )
    }

    private fun drawBlackBars(scope: DrawScope, W: Float, H: Float, I: Float) {
        val barH = H * (0.06f + 0.06f * I)
        scope.drawRect(color = Color.Black, topLeft = Offset.Zero, size = Size(W, barH))
        scope.drawRect(color = Color.Black, topLeft = Offset(0f, H - barH), size = Size(W, barH))
    }

    private fun drawVhsLines(scope: DrawScope, W: Float, H: Float, time: Float, I: Float) {
        val alphaF = (0.35f * I).coerceIn(0f, 1f)
        for (i in 0 until 6) {
            val y = (hash((i + (time * 3f).toInt()).toDouble()) * H).toFloat()
            scope.drawRect(
                color = if (i % 2 == 0) Color.White.copy(alpha = alphaF)
                else Color.Black.copy(alpha = alphaF),
                topLeft = Offset(0f, y),
                size = Size(W, 2f + I * 4f)
            )
        }
        val tearY = (time * 300f) % H
        scope.drawRect(
            color = Color.White.copy(alpha = (0.4f * I).coerceIn(0f, 1f)),
            topLeft = Offset(0f, tearY),
            size = Size(W, 3f)
        )
    }

    private fun drawGlitchBars(scope: DrawScope, W: Float, H: Float, time: Float, I: Float) {
        val seed = (time * 20f).toInt()
        for (i in 0 until 5) {
            val y = (hash((i + seed * 3.7).toDouble()) * H).toFloat()
            val h = 4f + hash((i + seed * 5.1).toDouble()).toFloat() * 20f
            val off = (hash((i + seed * 7.3).toDouble()).toFloat() - 0.5f) * 60f * I
            scope.drawRect(
                color = Color.White.copy(alpha = (0.45f * I).coerceIn(0f, 1f)),
                topLeft = Offset(off, y),
                size = Size(W, h)
            )
        }
    }
}
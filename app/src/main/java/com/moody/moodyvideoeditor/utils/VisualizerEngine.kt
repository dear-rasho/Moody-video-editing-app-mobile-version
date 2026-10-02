package com.moody.moodyvideoeditor.utils

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.moody.moodyvideoeditor.data.VisualizerPreset
import com.moody.moodyvideoeditor.data.VisualizerState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface as AndroidTypeface

object VisualizerEngine {

    // ═══════════════════════════════════════════════════════════
    //  PER-INSTANCE SMOOTHING STATE
    // ═══════════════════════════════════════════════════════════
    private val smoothBass = HashMap<String, Float>()
    private val smoothMid = HashMap<String, Float>()
    private val smoothTreble = HashMap<String, Float>()
    private val smoothRms = HashMap<String, Float>()
    private val smoothBeat = HashMap<String, Float>()
    private val imageRotation = HashMap<String, Float>()

    // Center image cache
    private var cachedImage: ImageBitmap? = null
    private var cachedImageKey: String? = null

    data class Features(
        val bass: Float,
        val mid: Float,
        val treble: Float,
        val rms: Float,
        val beat: Float,
        val bars: FloatArray
    )

    // ═══════════════════════════════════════════════════════════
    //  MAIN ENTRY
    // ═══════════════════════════════════════════════════════════
    fun draw(
        scope: DrawScope,
        state: VisualizerState,
        relativeMs: Long,
        elapsedSec: Float,
        instanceKey: String = "default"
    ) {
        if (!state.isActive) return

        val W = scope.size.width
        val H = scope.size.height
        val cx = state.positionX * W
        val cy = state.positionY * H
        val minDim = min(W, H)
        val baseR = state.size * minDim

        val beatPulse = state.strengthAt(relativeMs)

        val feat = computeFeatures(
            instanceKey = instanceKey,
            beatPulse = beatPulse,
            state = state
        )

        val ctx = DrawCtx(
            scope = scope,
            W = W, H = H,
            cx = cx, cy = cy,
            baseR = baseR,
            feat = feat,
            elapsed = elapsedSec,
            state = state,
            instanceKey = instanceKey
        )

        when (state.preset) {
            VisualizerPreset.NEON_GLOW_RING -> drawNeonGlow(ctx)
            VisualizerPreset.FREQUENCY_SPECTRUM_RING -> drawSpectrumRing(ctx)
            VisualizerPreset.PARTICLE_ORBIT_RING -> drawParticleOrbit(ctx)
            VisualizerPreset.LIQUID_WAVE_RING -> drawLiquidWave(ctx)
            VisualizerPreset.DOUBLE_ORBIT_RINGS -> drawDoubleOrbit(ctx)
            VisualizerPreset.DOTTED_RADIAL_WAVE -> drawDottedRadial(ctx)
            VisualizerPreset.VINYL_RECORD_SPIN -> drawVinylRecord(ctx)
            VisualizerPreset.AUDIO_REACTIVE_CENTER_ART -> drawCenterArt(ctx)
            VisualizerPreset.BROKEN_SEGMENT_RING -> drawBrokenSegment(ctx)
            VisualizerPreset.VORTEX_TUNNEL -> drawVortexTunnel(ctx)
        }
    }

    private data class DrawCtx(
        val scope: DrawScope,
        val W: Float, val H: Float,
        val cx: Float, val cy: Float,
        val baseR: Float,
        val feat: Features,
        val elapsed: Float,
        val state: VisualizerState,
        val instanceKey: String
    ) {
        fun color1() = Color(state.color1).copy(alpha = state.opacity)
        fun color2() = Color(state.color2).copy(alpha = state.opacity)
        fun color1a(a: Float) = Color(state.color1).copy(alpha = a * state.opacity)
        fun color2a(a: Float) = Color(state.color2).copy(alpha = a * state.opacity)
    }

    // ═══════════════════════════════════════════════════════════
    //  FEATURES
    // ═══════════════════════════════════════════════════════════
    private fun computeFeatures(
        instanceKey: String,
        beatPulse: Float,
        state: VisualizerState
    ): Features {
        val baseline = 0.02f
        val k = state.lerpFactor.coerceIn(0.05f, 0.95f)

        val bassTarget = (beatPulse * state.bassRingBoost).coerceIn(0f, 1f)
        val midTarget = (beatPulse * 0.55f * state.midBarBoost).coerceIn(0f, 1f)
        val trebleTarget = (beatPulse * 0.35f * state.trebleSpikeBoost).coerceIn(0f, 1f)
        val rmsTarget = (beatPulse * 0.75f).coerceIn(0f, 1f)

        val bassPrev = smoothBass[instanceKey] ?: baseline
        val midPrev = smoothMid[instanceKey] ?: baseline
        val treblePrev = smoothTreble[instanceKey] ?: baseline
        val rmsPrev = smoothRms[instanceKey] ?: baseline
        val beatPrev = smoothBeat[instanceKey] ?: 0f

        val bassK = if (bassTarget > bassPrev) k * 2.5f else k * 0.35f
        val midK = if (midTarget > midPrev) k * 2.0f else k * 0.40f
        val trebleK = if (trebleTarget > treblePrev) k * 2.5f else k * 0.50f
        val rmsK = if (rmsTarget > rmsPrev) k * 2.0f else k * 0.40f
        val beatK = if (beatPulse > beatPrev) k * 3.0f else k * 0.50f

        val bass = bassPrev + (bassTarget - bassPrev) * bassK.coerceIn(0f, 1f)
        val mid = midPrev + (midTarget - midPrev) * midK.coerceIn(0f, 1f)
        val treble = treblePrev + (trebleTarget - treblePrev) * trebleK.coerceIn(0f, 1f)
        val rms = rmsPrev + (rmsTarget - rmsPrev) * rmsK.coerceIn(0f, 1f)
        val beat = beatPrev + (beatPulse - beatPrev) * beatK.coerceIn(0f, 1f)

        smoothBass[instanceKey] = bass
        smoothMid[instanceKey] = mid
        smoothTreble[instanceKey] = treble
        smoothRms[instanceKey] = rms
        smoothBeat[instanceKey] = beat

        val barCount = 64
        val bars = FloatArray(barCount)
        for (i in 0 until barCount) {
            val frac = i.toFloat() / barCount
            val energy = when {
                frac < 0.20f -> bass * (1f - frac * 3f) + beat * 0.35f
                frac < 0.60f -> mid * (1f - (frac - 0.20f) * 1.8f) + beat * 0.20f
                else -> treble * (1f - (frac - 0.60f) * 1.8f) + beat * 0.10f
            }
            bars[i] = energy.coerceIn(0f, 1f)
        }

        return Features(bass, mid, treble, rms, beat, bars)
    }

    // ═══════════════════════════════════════════════════════════
    //  1. NEON GLOW RING
    // ═══════════════════════════════════════════════════════════
    private fun drawNeonGlow(c: DrawCtx) {
        val scope = c.scope
        val pulse = 1f + c.feat.bass * 0.35f + c.feat.beat * 0.30f
        val r = c.baseR * pulse

        if (c.state.glow) {
            listOf(1.35f to 0.12f, 1.15f to 0.25f, 1.0f to 0.5f).forEach { (mul, a) ->
                scope.drawCircle(
                    color = c.color1a(a * (1f + c.feat.beat * 0.8f)),
                    radius = r * mul,
                    center = Offset(c.cx, c.cy),
                    style = Stroke(width = c.baseR * 0.14f)
                )
            }
        }

        scope.drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(c.color1(), c.color2(), c.color1()),
                center = Offset(c.cx, c.cy)
            ),
            radius = r,
            center = Offset(c.cx, c.cy),
            style = Stroke(width = c.baseR * 0.08f, cap = StrokeCap.Round)
        )

        drawCenterContent(c, r * c.state.imageScale)
    }

    // ═══════════════════════════════════════════════════════════
    //  2. FREQUENCY SPECTRUM RING
    // ═══════════════════════════════════════════════════════════
    private fun drawSpectrumRing(c: DrawCtx) {
        val scope = c.scope
        val bars = c.feat.bars
        val count = bars.size
        val r0 = c.baseR * (1f + c.feat.bass * 0.1f + c.feat.beat * 0.1f)
        val rMax = c.baseR * 0.55f
        val barW = (2f * PI.toFloat() * c.baseR / count) * 0.6f

        val brush = Brush.sweepGradient(
            colors = listOf(c.color1(), c.color2(), c.color1()),
            center = Offset(c.cx, c.cy)
        )

        bars.forEachIndexed { i, v ->
            val angle = (i.toFloat() / count) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val len = v * rMax + c.baseR * 0.05f
            val cosA = cos(angle)
            val sinA = sin(angle)

            scope.drawLine(
                brush = brush,
                start = Offset(c.cx + cosA * r0, c.cy + sinA * r0),
                end = Offset(c.cx + cosA * (r0 + len), c.cy + sinA * (r0 + len)),
                strokeWidth = barW.coerceAtLeast(2f),
                cap = StrokeCap.Round
            )
        }

        drawCenterContent(c, c.baseR * c.state.imageScale * 0.85f)
    }

    // ═══════════════════════════════════════════════════════════
    //  3. PARTICLE ORBIT RING
    // ═══════════════════════════════════════════════════════════
    private fun drawParticleOrbit(c: DrawCtx) {
        val scope = c.scope
        val count = 120
        val baseOrbit = c.baseR * (0.95f + c.feat.bass * 0.1f)
        val blastR = c.feat.beat * c.baseR * 0.5f

        val brush = Brush.sweepGradient(
            colors = listOf(c.color1(), c.color2(), c.color1()),
            center = Offset(c.cx, c.cy)
        )

        for (i in 0 until count) {
            val phase = (i * 0.1f) % (2f * PI.toFloat())
            val speed = 0.3f + (i % 10) * 0.07f
            val angle = phase + c.elapsed * speed * 0.5f
            val radialMod = if (c.feat.bars.isNotEmpty())
                c.feat.bars[(i * c.feat.bars.size / count) % c.feat.bars.size]
            else 0f
            val r = baseOrbit + radialMod * c.baseR * 0.25f + blastR
            val dotR = c.baseR * (0.02f + radialMod * 0.025f + c.feat.beat * 0.02f)

            scope.drawCircle(
                brush = brush,
                radius = dotR.coerceAtLeast(1.5f),
                center = Offset(c.cx + cos(angle) * r, c.cy + sin(angle) * r)
            )
        }

        scope.drawCircle(
            color = c.color1a(0.3f + c.feat.beat * 0.5f),
            radius = baseOrbit,
            center = Offset(c.cx, c.cy),
            style = Stroke(width = 1f + c.feat.beat * 2f)
        )

        drawCenterContent(c, c.baseR * c.state.imageScale * 0.7f)
    }

    // ═══════════════════════════════════════════════════════════
    //  4. LIQUID WAVE RING
    // ═══════════════════════════════════════════════════════════
    private fun drawLiquidWave(c: DrawCtx) {
        val scope = c.scope
        val segments = 120
        val path = Path()

        for (i in 0..segments) {
            val angle = (i.toFloat() / segments) * 2f * PI.toFloat()
            val wave = sin(angle * 5f + c.elapsed * 2f) * 0.08f +
                    sin(angle * 3f - c.elapsed * 1.5f) * 0.05f
            val fftMod = if (c.feat.bars.isNotEmpty())
                c.feat.bars[i % c.feat.bars.size] * 0.25f
            else 0f
            val r = c.baseR *
                    (1f + wave + fftMod + c.feat.bass * 0.1f + c.feat.beat * 0.15f)
            val x = c.cx + cos(angle) * r
            val y = c.cy + sin(angle) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        if (c.state.glow) {
            scope.drawPath(
                path = path,
                color = c.color1a(0.2f * (1f + c.feat.beat * 1.5f)),
                style = Stroke(width = c.baseR * 0.15f)
            )
        }

        scope.drawPath(
            path = path,
            brush = Brush.sweepGradient(
                colors = listOf(c.color1(), c.color2(), c.color1()),
                center = Offset(c.cx, c.cy)
            ),
            style = Stroke(width = c.baseR * 0.06f, cap = StrokeCap.Round)
        )

        drawCenterContent(c, c.baseR * c.state.imageScale * 0.75f)
    }

    // ═══════════════════════════════════════════════════════════
    //  5. DOUBLE ORBIT RINGS
    // ═══════════════════════════════════════════════════════════
    private fun drawDoubleOrbit(c: DrawCtx) {
        val scope = c.scope

        val rIn = c.baseR * 0.65f *
                (1f + c.feat.bass * 0.25f + c.feat.beat * 0.25f)
        if (c.state.glow) {
            scope.drawCircle(
                color = c.color1a(0.2f * (1f + c.feat.beat)),
                radius = rIn,
                center = Offset(c.cx, c.cy),
                style = Stroke(width = c.baseR * 0.1f)
            )
        }
        scope.drawCircle(
            color = c.color1(),
            radius = rIn,
            center = Offset(c.cx, c.cy),
            style = Stroke(width = c.baseR * 0.05f)
        )

        val rOut = c.baseR * (1f + c.feat.treble * 0.2f)
        val count = 48
        c.feat.bars.take(count).forEachIndexed { i, v ->
            val angle = (i.toFloat() / count) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val len = v * c.baseR * 0.3f
            scope.drawLine(
                color = c.color2a(0.9f),
                start = Offset(c.cx + cos(angle) * rOut, c.cy + sin(angle) * rOut),
                end = Offset(
                    c.cx + cos(angle) * (rOut + len),
                    c.cy + sin(angle) * (rOut + len)
                ),
                strokeWidth = c.baseR * 0.025f,
                cap = StrokeCap.Round
            )
        }

        drawCenterContent(c, rIn * c.state.imageScale)
    }

    // ═══════════════════════════════════════════════════════════
    //  6. DOTTED RADIAL WAVE
    // ═══════════════════════════════════════════════════════════
    private fun drawDottedRadial(c: DrawCtx) {
        val scope = c.scope
        val rings = 3
        val brush = Brush.sweepGradient(
            colors = listOf(c.color1(), c.color2(), c.color1()),
            center = Offset(c.cx, c.cy)
        )

        for (ring in 1..rings) {
            val dotCount = 16 + ring * 8
            val baseRr = c.baseR * (ring.toFloat() / rings) *
                    (1f + c.feat.rms * 0.15f + c.feat.beat * 0.15f)
            val freqBand = ring.toFloat() / rings

            for (i in 0 until dotCount) {
                val angle = (i.toFloat() / dotCount) * 2f * PI.toFloat() +
                        c.elapsed * 0.4f * ring
                val barIdx = ((1f - freqBand) * (c.feat.bars.size - 1)).toInt()
                    .coerceIn(0, c.feat.bars.size - 1)
                val v = c.feat.bars[barIdx]
                val r = baseRr + v * c.baseR * 0.15f

                scope.drawCircle(
                    brush = brush,
                    radius = (c.baseR * 0.02f + v * c.baseR * 0.02f +
                            c.feat.beat * c.baseR * 0.015f).coerceAtLeast(1.5f),
                    center = Offset(c.cx + cos(angle) * r, c.cy + sin(angle) * r)
                )
            }
        }

        drawCenterContent(c, c.baseR * c.state.imageScale * 0.5f)
    }

    // ═══════════════════════════════════════════════════════════
    //  7. VINYL RECORD SPIN
    // ═══════════════════════════════════════════════════════════
    private fun drawVinylRecord(c: DrawCtx) {
        val scope = c.scope
        val rot = c.elapsed * (45f + c.feat.beat * 180f)

        scope.rotate(degrees = rot, pivot = Offset(c.cx, c.cy)) {
            drawCircle(
                color = Color(0xFF141414).copy(alpha = c.state.opacity * 0.9f),
                radius = c.baseR,
                center = Offset(c.cx, c.cy)
            )

            for (i in 1..6) {
                val r = c.baseR * (i / 7f)
                drawCircle(
                    color = Color.White.copy(alpha = 0.06f * c.state.opacity),
                    radius = r,
                    center = Offset(c.cx, c.cy),
                    style = Stroke(width = 1f)
                )
            }

            drawArc(
                color = c.color1a(0.85f),
                startAngle = 0f,
                sweepAngle = 90f + c.feat.bass * 90f + c.feat.beat * 60f,
                useCenter = false,
                topLeft = Offset(c.cx - c.baseR * 0.85f, c.cy - c.baseR * 0.85f),
                size = Size(c.baseR * 1.7f, c.baseR * 1.7f),
                style = Stroke(width = c.baseR * 0.06f, cap = StrokeCap.Round)
            )
        }

        scope.drawCircle(
            color = Color.Black,
            radius = c.baseR * 0.08f,
            center = Offset(c.cx, c.cy)
        )
        drawCenterContent(c, c.baseR * c.state.imageScale * 0.75f)

        val count = 64
        c.feat.bars.forEachIndexed { i, v ->
            if (i >= count) return@forEachIndexed
            val angle = (i.toFloat() / count) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val r0 = c.baseR * 1.02f
            val len = v * c.baseR * 0.25f
            scope.drawLine(
                color = c.color2a(0.85f),
                start = Offset(c.cx + cos(angle) * r0, c.cy + sin(angle) * r0),
                end = Offset(
                    c.cx + cos(angle) * (r0 + len),
                    c.cy + sin(angle) * (r0 + len)
                ),
                strokeWidth = c.baseR * 0.02f,
                cap = StrokeCap.Round
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  8. AUDIO-REACTIVE CENTER ART
    // ═══════════════════════════════════════════════════════════
    private fun drawCenterArt(c: DrawCtx) {
        val scope = c.scope
        val pulse = 1f + c.feat.bass * 0.2f + c.feat.beat * 0.2f
        val ringR = c.baseR * pulse

        if (c.state.glow) {
            scope.drawCircle(
                color = c.color1a(0.25f * (1f + c.feat.beat)),
                radius = ringR * 1.15f,
                center = Offset(c.cx, c.cy),
                style = Stroke(width = c.baseR * 0.15f)
            )
        }

        val count = 72
        c.feat.bars.forEachIndexed { i, v ->
            if (i >= count) return@forEachIndexed
            val angle = (i.toFloat() / count) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val r0 = ringR + c.baseR * 0.05f
            val len = v * c.baseR * 0.28f
            scope.drawLine(
                color = if (i % 2 == 0) c.color1() else c.color2(),
                start = Offset(c.cx + cos(angle) * r0, c.cy + sin(angle) * r0),
                end = Offset(
                    c.cx + cos(angle) * (r0 + len),
                    c.cy + sin(angle) * (r0 + len)
                ),
                strokeWidth = c.baseR * 0.025f,
                cap = StrokeCap.Round
            )
        }

        scope.drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(c.color1(), c.color2(), c.color1()),
                center = Offset(c.cx, c.cy)
            ),
            radius = ringR,
            center = Offset(c.cx, c.cy),
            style = Stroke(width = c.baseR * 0.06f)
        )

        drawCenterContent(c, ringR * 0.9f)
    }

    // ═══════════════════════════════════════════════════════════
    //  9. BROKEN SEGMENT RING
    // ═══════════════════════════════════════════════════════════
    private fun drawBrokenSegment(c: DrawCtx) {
        val scope = c.scope
        val segments = 4
        val gapDeg = 30f
        val arcDeg = (360f / segments) - gapDeg

        val brush = Brush.sweepGradient(
            colors = listOf(c.color1(), c.color2(), c.color1()),
            center = Offset(c.cx, c.cy)
        )

        for (i in 0 until segments) {
            val baseAngle = i * (360f / segments)
            val segRot = c.elapsed * (20f + i * 10f) *
                    (if (i % 2 == 0) 1f else -1f) +
                    c.feat.beat * 30f * (if (i % 2 == 0) 1f else -1f)
            val startAngle = baseAngle + segRot

            val rPulse = c.baseR *
                    (1f + c.feat.bass * 0.15f * (if (i % 2 == 0) 1f else 0.5f) +
                            c.feat.beat * 0.15f)

            if (c.state.glow) {
                scope.drawArc(
                    color = c.color1a(0.2f * (1f + c.feat.beat)),
                    startAngle = startAngle,
                    sweepAngle = arcDeg,
                    useCenter = false,
                    topLeft = Offset(c.cx - rPulse, c.cy - rPulse),
                    size = Size(rPulse * 2f, rPulse * 2f),
                    style = Stroke(width = c.baseR * 0.14f, cap = StrokeCap.Round)
                )
            }

            scope.drawArc(
                brush = brush,
                startAngle = startAngle,
                sweepAngle = arcDeg,
                useCenter = false,
                topLeft = Offset(c.cx - rPulse, c.cy - rPulse),
                size = Size(rPulse * 2f, rPulse * 2f),
                style = Stroke(width = c.baseR * 0.06f, cap = StrokeCap.Round)
            )
        }

        drawCenterContent(c, c.baseR * c.state.imageScale * 0.7f)
    }

    // ═══════════════════════════════════════════════════════════
    //  10. VORTEX TUNNEL
    // ═══════════════════════════════════════════════════════════
    private fun drawVortexTunnel(c: DrawCtx) {
        val scope = c.scope
        val rings = 12

        for (i in 0 until rings) {
            val t = i.toFloat() / rings
            val depth = ((c.elapsed * (0.5f + c.feat.beat * 1.5f) + t) % 1f)
            val r = c.baseR * (0.1f + depth * 0.9f) *
                    (1f + c.feat.rms * 0.15f + c.feat.beat * 0.1f)
            val alpha = ((1f - depth) * 0.9f).coerceIn(0f, 1f)

            scope.drawCircle(
                color = if (i % 2 == 0) c.color1a(alpha) else c.color2a(alpha),
                radius = r,
                center = Offset(c.cx, c.cy),
                style = Stroke(width = c.baseR * 0.03f * (1f - depth * 0.5f))
            )
        }

        val count = 48
        c.feat.bars.forEachIndexed { i, v ->
            if (i >= count) return@forEachIndexed
            val angle = (i.toFloat() / count) * 2f * PI.toFloat() -
                    PI.toFloat() / 2f + c.elapsed * 0.3f
            val r0 = c.baseR * 1.05f
            val len = v * c.baseR * 0.35f
            scope.drawLine(
                color = c.color1a(0.7f),
                start = Offset(c.cx + cos(angle) * r0, c.cy + sin(angle) * r0),
                end = Offset(
                    c.cx + cos(angle) * (r0 + len),
                    c.cy + sin(angle) * (r0 + len)
                ),
                strokeWidth = c.baseR * 0.02f,
                cap = StrokeCap.Round
            )
        }

        drawCenterContent(c, c.baseR * c.state.imageScale * 0.35f)
    }

    // ═══════════════════════════════════════════════════════════
    //  CENTER CONTENT — image + text inside circle
    // ═══════════════════════════════════════════════════════════
    private fun drawCenterContent(c: DrawCtx, baseSize: Float) {
        if (!c.state.hasCenterContent) return

        // Bass-only or RMS pulse
        val energy = if (c.state.imageBassOnly) c.feat.bass else c.feat.rms
        val beatBoost = c.feat.beat * c.state.imagePulseAmount * 1.5f
        val pulseFactor = 1f + (energy * c.state.imagePulseAmount) + beatBoost

        val size = baseSize * pulseFactor
        val left = c.cx - size
        val top = c.cy - size

        // Idle rotation (only for image layer)
        val rotKey = c.instanceKey
        val prevRot = imageRotation[rotKey] ?: 0f
        val newRot = if (c.state.imageIdleRotation && c.state.showImage) {
            (prevRot + c.state.imageIdleSpeed) % 360f
        } else 0f
        imageRotation[rotKey] = newRot

        val circlePath = Path().apply {
            addOval(Rect(left, top, left + size * 2, top + size * 2))
        }

        c.scope.clipPath(circlePath) {
            val hasText = c.state.showText && c.state.textContent.isNotBlank()
            val hasImg = c.state.showImage && cachedImage != null

            // Order: image below text OR text below image
            val drawImageFirst = !c.state.textOnTopOfImage || !hasText

            if (drawImageFirst) {
                if (hasImg) drawImageLayer(c, size, left, top, newRot)
                if (hasText) drawTextLayer(c, size)
            } else {
                if (hasText) drawTextLayer(c, size)
                if (hasImg) drawImageLayer(c, size, left, top, newRot)
            }
        }

        // Ring around center content
        c.scope.drawCircle(
            color = c.color1a(0.6f + c.feat.beat * 0.4f),
            radius = size,
            center = Offset(c.cx, c.cy),
            style = Stroke(width = c.baseR * (0.02f + c.feat.beat * 0.02f))
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  IMAGE LAYER
    // ═══════════════════════════════════════════════════════════
    private fun DrawScope.drawImageLayer(
        c: DrawCtx,
        size: Float,
        left: Float,
        top: Float,
        rotationDeg: Float
    ) {
        val img = cachedImage ?: return
        rotate(degrees = rotationDeg, pivot = Offset(c.cx, c.cy)) {
            drawImage(
                image = img,
                dstOffset = IntOffset(left.toInt(), top.toInt()),
                dstSize = IntSize((size * 2).toInt(), (size * 2).toInt()),
                alpha = c.state.imageOpacity * c.state.opacity
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  TEXT LAYER — full TextState properties
    // ═══════════════════════════════════════════════════════════
    private fun DrawScope.drawTextLayer(
        c: DrawCtx,
        circleRadius: Float
    ) {
        val ts = c.state.textState
        val content = ts.content.ifBlank { c.state.textContent }
        if (content.isBlank()) return

        val circleDiameter = circleRadius * 2f

        // Font size scale relative to circle size
        val fontScale = (circleDiameter / 400f).coerceIn(0.1f, 3f)
        val baseFontSize = (ts.fontSize.toFloat() * fontScale).coerceAtLeast(6f)

        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas

            // ─── TYPEFACE ───
            val family = when {
                ts.fontFamily.lowercase().contains("mono") ||
                        ts.fontFamily.lowercase().contains("courier") ||
                        ts.fontFamily.lowercase().contains("consol") ->
                    AndroidTypeface.MONOSPACE

                ts.fontFamily.lowercase().contains("serif") ||
                        ts.fontFamily.lowercase().contains("times") ||
                        ts.fontFamily.lowercase().contains("georgia") ||
                        ts.fontFamily.lowercase().contains("playfair") ||
                        ts.fontFamily.lowercase().contains("garamond") ->
                    AndroidTypeface.SERIF

                ts.fontFamily.lowercase().contains("script") ||
                        ts.fontFamily.lowercase().contains("brush") ||
                        ts.fontFamily.lowercase().contains("cursive") ||
                        ts.fontFamily.lowercase().contains("hand") ->
                    AndroidTypeface.create("cursive", AndroidTypeface.NORMAL)

                else -> AndroidTypeface.SANS_SERIF
            }
            val typefaceStyle = when {
                ts.fontWeight == "bold" && ts.fontStyle == "italic" ->
                    AndroidTypeface.BOLD_ITALIC

                ts.fontWeight == "bold" -> AndroidTypeface.BOLD
                ts.fontStyle == "italic" -> AndroidTypeface.ITALIC
                else -> AndroidTypeface.NORMAL
            }
            val typeface = AndroidTypeface.create(family, typefaceStyle)

            val align = when (ts.alignment) {
                "left" -> AndroidPaint.Align.LEFT
                "right" -> AndroidPaint.Align.RIGHT
                else -> AndroidPaint.Align.CENTER
            }

            // ─── LETTER SPACING ───
            val letterSpacingEm = if (baseFontSize > 0f) {
                (ts.letterSpacing / baseFontSize).coerceIn(-0.3f, 0.3f)
            } else 0f

            val alphaInt = (ts.opacity / 100f * c.state.opacity * 255)
                .toInt().coerceIn(0, 255)
            if (alphaInt <= 0) return@drawIntoCanvas

            // ─── BASE PAINT ───
            val basePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                textSize = baseFontSize
                textAlign = align
                this.typeface = typeface
                letterSpacing = letterSpacingEm
                color = ts.color.toInt()
                this.alpha = alphaInt
            }

            // ─── MEASURE + AUTO-FIT WIDTH ───
            val maxTextWidth = circleDiameter * (ts.maxWidth / 100f)
                .coerceIn(0.3f, 1f) * 0.88f
            val naturalWidth = basePaint.measureText(content)
            val fitFontSize = if (naturalWidth > maxTextWidth && naturalWidth > 0f) {
                baseFontSize * (maxTextWidth / naturalWidth)
            } else baseFontSize
            basePaint.textSize = fitFontSize

            val fm = basePaint.fontMetrics
            val baseline = c.cy - (fm.ascent + fm.descent) / 2f

            val textX = when (align) {
                AndroidPaint.Align.LEFT -> c.cx - maxTextWidth / 2f
                AndroidPaint.Align.RIGHT -> c.cx + maxTextWidth / 2f
                else -> c.cx
            }

            // ─── GLOW ───
            if (ts.glowEnabled && ts.glowRadius > 0f) {
                val glowColor = ts.glowColor.toInt()
                val glowR = (ts.glowRadius * fontScale).coerceIn(2f, 80f)
                listOf(
                    glowR * 1.6f to 0.30f,
                    glowR * 1.0f to 0.50f,
                    glowR * 0.55f to 0.70f
                ).forEach { (r, a) ->
                    val gp = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                        textSize = fitFontSize
                        textAlign = align
                        this.typeface = typeface
                        letterSpacing = letterSpacingEm
                        color = glowColor
                        this.alpha = (alphaInt * a).toInt().coerceIn(0, 255)
                        maskFilter = BlurMaskFilter(
                            r.coerceIn(1f, 100f),
                            BlurMaskFilter.Blur.NORMAL
                        )
                    }
                    nativeCanvas.drawText(content, textX, baseline, gp)
                }
            }

            // ─── SHADOW ───
            if (ts.shadowEnabled) {
                val sp = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    textSize = fitFontSize
                    textAlign = align
                    this.typeface = typeface
                    letterSpacing = letterSpacingEm
                    color = ts.color.toInt()
                    alpha = alphaInt
                    setShadowLayer(
                        ts.shadowBlur.coerceIn(0f, 50f),
                        ts.shadowOffsetX.coerceIn(-40f, 40f),
                        ts.shadowOffsetY.coerceIn(-40f, 40f),
                        ts.shadowColor.toInt()
                    )
                }
                nativeCanvas.drawText(content, textX, baseline, sp)
            }

            // ─── STROKE ───
            if (ts.strokeEnabled && ts.strokeWidth > 0f) {
                val strokePx = (ts.strokeWidth * fontScale * 2f).coerceIn(1f, 40f)
                val sp = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    textSize = fitFontSize
                    textAlign = align
                    this.typeface = typeface
                    letterSpacing = letterSpacingEm
                    color = ts.strokeColor.toInt()
                    style = AndroidPaint.Style.STROKE
                    strokeWidth = strokePx
                    strokeJoin = AndroidPaint.Join.ROUND
                    strokeCap = AndroidPaint.Cap.ROUND
                    alpha = alphaInt
                }
                nativeCanvas.drawText(content, textX, baseline, sp)
            }

            // ─── FILL ───
            val fillPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                textSize = fitFontSize
                textAlign = align
                this.typeface = typeface
                letterSpacing = letterSpacingEm
                alpha = alphaInt
            }

            if (ts.gradientEnabled) {
                val rad = Math.toRadians(ts.gradientAngle.toDouble())
                val dx = cos(rad).toFloat() * circleRadius
                val dy = sin(rad).toFloat() * circleRadius
                fillPaint.shader = LinearGradient(
                    c.cx - dx, c.cy - dy,
                    c.cx + dx, c.cy + dy,
                    ts.gradientColor1.toInt(),
                    ts.gradientColor2.toInt(),
                    Shader.TileMode.CLAMP
                )
            } else {
                fillPaint.color = ts.color.toInt()
            }

            nativeCanvas.drawText(content, textX, baseline, fillPaint)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════
    fun setCenterImage(uri: String?, bitmap: ImageBitmap?) {
        if (uri != cachedImageKey) {
            cachedImage = bitmap
            cachedImageKey = uri
        }
    }

    fun clearCache() {
        smoothBass.clear()
        smoothMid.clear()
        smoothTreble.clear()
        smoothRms.clear()
        smoothBeat.clear()
        imageRotation.clear()
        cachedImage = null
        cachedImageKey = null
    }
}
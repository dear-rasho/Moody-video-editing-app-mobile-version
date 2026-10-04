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
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface as AndroidTypeface

object VisualizerEngine {

    private val smoothBass = ConcurrentHashMap<String, Float>()
    private val smoothMid = ConcurrentHashMap<String, Float>()
    private val smoothTreble = ConcurrentHashMap<String, Float>()
    private val smoothRms = ConcurrentHashMap<String, Float>()
    private val smoothBeat = ConcurrentHashMap<String, Float>()
    private val imageRotation = ConcurrentHashMap<String, Float>()

    @Volatile
    private var cachedImage: ImageBitmap? = null

    @Volatile
    private var cachedImageKey: String? = null

    data class Features(
        val bass: Float, val mid: Float, val treble: Float,
        val rms: Float, val beat: Float, val bars: FloatArray
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
        val offsetMs = state.audioOffsetMs.toLong()
        val adjustedMs = (relativeMs + offsetMs).coerceAtLeast(0L)
        val beatPulse = state.strengthAt(adjustedMs)
        drawInternal(scope, state, beatPulse, elapsedSec, instanceKey)
    }

    fun drawPreview(
        scope: DrawScope,
        state: VisualizerState,
        elapsedSec: Float,
        instanceKey: String = "preview"
    ) {
        if (!state.isActive) return
        val t = elapsedSec * 2f
        val beatPulse = ((sin(t * PI.toFloat() * 2f) + 1f) / 2f).coerceIn(0f, 1f)
        drawInternal(scope, state, beatPulse, elapsedSec, instanceKey)
    }

    private fun drawInternal(
        scope: DrawScope,
        state: VisualizerState,
        beatPulse: Float,
        elapsedSec: Float,
        instanceKey: String
    ) {
        val W = scope.size.width
        val H = scope.size.height
        val cx = state.positionX * W
        val cy = state.positionY * H
        val minDim = min(W, H)
        val baseR = state.size * minDim

        val feat = computeFeatures(instanceKey, beatPulse, state)

        val ctx = DrawCtx(
            scope = scope, W = W, H = H,
            cx = cx, cy = cy, baseR = baseR,
            feat = feat, elapsed = elapsedSec,
            state = state, instanceKey = instanceKey
        )

        when (state.preset) {
            // 🆕 PREMIUM
            VisualizerPreset.AUDIO_SPHERE -> drawAudioSphere(ctx)
            VisualizerPreset.WAVEFORM_RING -> drawWaveformRing(ctx)
            VisualizerPreset.SYMMETRIC_WAVE -> drawSymmetricWave(ctx)

            // SPECTRUM
            VisualizerPreset.CIRCULAR_SPECTRUM,
            VisualizerPreset.RADIAL_BARS,
            VisualizerPreset.STAR_BURST -> drawCircularBars(ctx, 1.0f, 0)

            VisualizerPreset.INNER_RADIAL_BARS -> drawCircularBars(ctx, 1.0f, 1)
            VisualizerPreset.GLOW_WAVES -> drawCircularBars(ctx, 1.4f, 2)
            VisualizerPreset.THICK_BARS -> drawCircularBars(ctx, 1.6f, 3)
            VisualizerPreset.THIN_STRINGS -> drawCircularBars(ctx, 0.5f, 4)
            VisualizerPreset.DUAL_RING -> drawRings(ctx, 2, false)
            VisualizerPreset.LINEAR_WAVEFORM,
            VisualizerPreset.DOUBLE_SIDED_BARS -> drawLinearBars(ctx, 0)

            VisualizerPreset.MIRRORED_LINEAR -> drawLinearBars(ctx, 1)
            VisualizerPreset.HEARTBEAT_WAVE -> drawWaveform(ctx, 0)
            VisualizerPreset.SINE_WAVE -> drawWaveform(ctx, 1)
            VisualizerPreset.SQUARE_SPECTRUM -> drawPolygonBars(ctx, 4)
            VisualizerPreset.TRIANGLE_BEATS -> drawPolygonBars(ctx, 3)
            VisualizerPreset.HEXAGON_PULSE -> drawPolygonBars(ctx, 6)
            VisualizerPreset.DOT_MATRIX -> drawDotsOnRing(ctx, 3)
            VisualizerPreset.PERSPECTIVE_3D -> drawLinearBars(ctx, 2)
            VisualizerPreset.FREQUENCY_VOLCANO -> drawCircularBars(ctx, 1.2f, 5)
            VisualizerPreset.TORNADO_SPIRAL -> drawSpiral(ctx, 0)

            // PARTICLES
            VisualizerPreset.BASS_PARTICLES -> drawParticles(ctx, 0)
            VisualizerPreset.FLOATING_DUST -> drawParticles(ctx, 1)
            VisualizerPreset.FIREFLY_GLOW -> drawParticles(ctx, 2)
            VisualizerPreset.SPARK_TRAIL -> drawParticles(ctx, 3)
            VisualizerPreset.MAGIC_DUST -> drawParticles(ctx, 4)
            VisualizerPreset.CONFETTI_POP -> drawParticles(ctx, 5)
            VisualizerPreset.BUBBLES_POP -> drawParticles(ctx, 6)
            VisualizerPreset.PLASMA_ORBS -> drawParticles(ctx, 7)
            VisualizerPreset.DISINTEGRATION -> drawParticles(ctx, 8)
            VisualizerPreset.ELECTRIC_STORM -> drawParticles(ctx, 9)
            VisualizerPreset.LIQUID_DROPS -> drawLiquid(ctx, 0)
            VisualizerPreset.SAND_STORM -> drawLiquid(ctx, 1)
            VisualizerPreset.MATRIX_RAIN -> drawFalling(ctx, 0)
            VisualizerPreset.SNOWFALL -> drawFalling(ctx, 1)
            VisualizerPreset.METEOR_SHOWER -> drawFalling(ctx, 2)
            VisualizerPreset.SMOKE_AURA -> drawClouds(ctx, 0)
            VisualizerPreset.COSMIC_NEBULA -> drawClouds(ctx, 1)
            VisualizerPreset.INK_BLEED -> drawClouds(ctx, 2)
            VisualizerPreset.GALAXY_VORTEX -> drawSpiral(ctx, 1)
            VisualizerPreset.CYBER_GRID -> drawGrid(ctx, 0)

            // NEON
            VisualizerPreset.NEON_GLOW_RING -> drawNeonRing(ctx)
            VisualizerPreset.RGB_GLITCH -> drawGlitch(ctx, 0)
            VisualizerPreset.VHS_NOISE -> drawGlitch(ctx, 1)
            VisualizerPreset.SCANLINE_DISTORT -> drawGlitch(ctx, 2)
            VisualizerPreset.GLITCH_TWITCH -> drawGlitch(ctx, 3)
            VisualizerPreset.CRT_FLICKER -> drawGlitch(ctx, 4)
            VisualizerPreset.PIXEL_DISSOLVE -> drawGlitch(ctx, 5)
            VisualizerPreset.LED_MATRIX -> drawLedMatrix(ctx)
            VisualizerPreset.DIGITAL_EQ -> drawLinearBars(ctx, 3)
            VisualizerPreset.CHROMA_PULSE -> drawNeonRing(ctx, hueShift = true)
            VisualizerPreset.VAPORWAVE_GRID -> drawGrid(ctx, 1)
            VisualizerPreset.TRON_WIREFRAME -> drawGrid(ctx, 2)
            VisualizerPreset.LASER_TUNNEL -> drawSpiral(ctx, 2)
            VisualizerPreset.LASER_BEAM -> drawLaserBeam(ctx, 0)
            VisualizerPreset.NEON_TRACER -> drawLaserBeam(ctx, 1)
            VisualizerPreset.SYNTH_SUN -> drawDisc(ctx, 0)
            VisualizerPreset.ARCADE_GAMEOVER -> drawDisc(ctx, 1)
            VisualizerPreset.HOLOGRAM -> drawDisc(ctx, 2)
            VisualizerPreset.ECG_GRID -> drawWaveform(ctx, 2)
            VisualizerPreset.VECTOR_WAVE -> drawWaveform(ctx, 3)

            // GEOMETRIC
            VisualizerPreset.MINIMAL_DOTS -> drawDotsOnRing(ctx, 1)
            VisualizerPreset.ROTATING_POLY -> drawGeometric(ctx, 0)
            VisualizerPreset.KALEIDOSCOPE -> drawKaleidoscope(ctx)
            VisualizerPreset.INTERLOCKING_RINGS -> drawRings(ctx, 3, true)
            VisualizerPreset.EXPANDING_SQUARES -> drawGeometric(ctx, 1)
            VisualizerPreset.ORIGAMI -> drawGeometric(ctx, 2)
            VisualizerPreset.FRACTAL_ZOOM -> drawSpiral(ctx, 3)
            VisualizerPreset.PARALLAX_LINES -> drawLinearBars(ctx, 4)
            VisualizerPreset.ISOMETRIC_BLOCKS -> drawGeometric(ctx, 3)
            VisualizerPreset.SYMMETRIC_MIRROR -> drawKaleidoscope(ctx, mirrored = true)
            VisualizerPreset.CROSSHAIR -> drawGeometric(ctx, 4)
            VisualizerPreset.DNA_STRAND -> drawDnaStrand(ctx)
            VisualizerPreset.CONCENTRIC_RINGS -> drawRings(ctx, 4, true)
            VisualizerPreset.FLOATING_SHARDS -> drawGeometric(ctx, 5)
            VisualizerPreset.INFINITE_TUNNEL -> drawSpiral(ctx, 4)
            VisualizerPreset.SHAPE_MORPH -> drawGeometric(ctx, 6)
            VisualizerPreset.GYROSCOPE -> drawGeometric(ctx, 7)
            VisualizerPreset.SPLIT_DIAGONAL -> drawSplitDiagonal(ctx)
            VisualizerPreset.CHECKERBOARD -> drawCheckerboard(ctx)
            VisualizerPreset.VECTOR_RIBBON -> drawRibbon(ctx)

            // CINEMATIC
            VisualizerPreset.LENS_FLARE -> drawCinematic(ctx, 0)
            VisualizerPreset.CINEMATIC_DUST -> drawParticles(ctx, 1)
            VisualizerPreset.SUNBEAMS -> drawCinematic(ctx, 1)
            VisualizerPreset.LIGHT_LEAK -> drawCinematic(ctx, 2)
            VisualizerPreset.GOLDEN_HOUR -> drawCinematic(ctx, 3)
            VisualizerPreset.PRISM_RAINBOW -> drawCinematic(ctx, 4)
            VisualizerPreset.VIGNETTE_BREATHE -> drawCinematic(ctx, 5)
            VisualizerPreset.BLUR_DISSOLVE -> drawCinematic(ctx, 6)
            VisualizerPreset.CAMERA_SHUTTER -> drawCinematic(ctx, 7)
            VisualizerPreset.FILM_GRAIN -> drawCinematic(ctx, 8)
            VisualizerPreset.FOGGY_AMBIANCE -> drawClouds(ctx, 3)
            VisualizerPreset.BOKEH_DRIFT -> drawCinematic(ctx, 9)
            VisualizerPreset.SHADOW_WAVE -> drawWaveform(ctx, 4)
            VisualizerPreset.WATER_RIPPLE -> drawLiquid(ctx, 2)
            VisualizerPreset.CLOUDY_TIMELAPSE -> drawClouds(ctx, 4)
            VisualizerPreset.LIGHT_STREAK -> drawLaserBeam(ctx, 2)
            VisualizerPreset.VINTAGE_COUNTDOWN -> drawCinematic(ctx, 10)
            VisualizerPreset.RAINDROPS -> drawFalling(ctx, 3)
            VisualizerPreset.CAMERA_SHAKE -> drawCinematic(ctx, 11)
            VisualizerPreset.HORIZON_ZOOM -> drawCinematic(ctx, 12)
        }
    }

    private data class DrawCtx(
        val scope: DrawScope, val W: Float, val H: Float,
        val cx: Float, val cy: Float, val baseR: Float,
        val feat: Features, val elapsed: Float,
        val state: VisualizerState, val instanceKey: String
    ) {
        fun color1() = Color(state.color1).copy(alpha = state.opacity)
        fun color2() = Color(state.color2).copy(alpha = state.opacity)
        fun color1a(a: Float) = Color(state.color1).copy(alpha = a * state.opacity)
        fun color2a(a: Float) = Color(state.color2).copy(alpha = a * state.opacity)
        fun sweep() = Brush.sweepGradient(
            colors = listOf(color1(), color2(), color1()),
            center = Offset(cx, cy)
        )

        fun glowBoost(): Float {
            val base = if (state.glow) state.glowIntensity else 0f
            val beatBoost = feat.beat * state.beatGlow * 0.5f
            return (base + beatBoost).coerceIn(0f, 1f)
        }

        fun beatScale(): Float = 1f + feat.beat * state.beatPulse
    }

    // ═══════════════════════════════════════════════════════════
    //  FEATURES
    // ═══════════════════════════════════════════════════════════
    private fun computeFeatures(
        instanceKey: String, beatPulse: Float, state: VisualizerState
    ): Features {
        val baseline = 0.02f
        val k = state.lerpFactor.coerceIn(0.05f, 0.95f)

        val rawFft = AudioVisualizerBridge.getFft()
        val useLiveFft = rawFft.isNotEmpty() && AudioVisualizerBridge.isFresh()

        val bassTarget: Float
        val midTarget: Float
        val trebleTarget: Float
        val rmsTarget: Float
        val liveBands: FloatArray?

        if (useLiveFft) {
            val bands = state.bands.coerceIn(1, 6400)
            liveBands = AudioVisualizerBridge.sampleBands(
                fft = rawFft,
                bands = bands,
                startHz = state.startFrequencyHz,
                endHz = state.endFrequencyHz
            )

            val sens = state.overallSensitivity.coerceIn(0f, 3f)
            val quarter = (bands / 4).coerceAtLeast(1)
            val half = (bands / 2).coerceAtLeast(1)

            bassTarget = (liveBands.take(quarter).average().toFloat() *
                    state.bassInfluence * sens).coerceIn(0f, 1f)
            midTarget = (liveBands.drop(quarter).take(half).average().toFloat() *
                    state.midInfluence * sens).coerceIn(0f, 1f)
            trebleTarget = (liveBands.drop(quarter + half).average().toFloat() *
                    state.trebleInfluence * sens).coerceIn(0f, 1f)
            rmsTarget = (liveBands.average().toFloat() * sens).coerceIn(0f, 1f)
        } else {
            val boost = state.sensitivity.coerceIn(0f, 3f)
            bassTarget = (beatPulse * state.bassRingBoost * boost).coerceIn(0f, 1f)
            midTarget = (beatPulse * 0.55f * state.midBarBoost * boost)
                .coerceIn(0f, 1f)
            trebleTarget = (beatPulse * 0.35f * state.trebleSpikeBoost * boost)
                .coerceIn(0f, 1f)
            rmsTarget = (beatPulse * 0.75f * boost).coerceIn(0f, 1f)
            liveBands = null
        }

        val bassPrev = smoothBass[instanceKey] ?: baseline
        val midPrev = smoothMid[instanceKey] ?: baseline
        val treblePrev = smoothTreble[instanceKey] ?: baseline
        val rmsPrev = smoothRms[instanceKey] ?: baseline
        val beatPrev = smoothBeat[instanceKey] ?: 0f

        val attack = (1f - state.attack).coerceIn(0.05f, 1f)
        val release = (1f - state.release).coerceIn(0.02f, 1f)

        val bassK = if (bassTarget > bassPrev) attack * 2.5f else release * 0.35f
        val midK = if (midTarget > midPrev) attack * 2.0f else release * 0.40f
        val trebleK = if (trebleTarget > treblePrev) attack * 2.5f else release * 0.50f
        val rmsK = if (rmsTarget > rmsPrev) attack * 2.0f else release * 0.40f
        val beatK = if (beatPulse > beatPrev) attack * 3.0f else release * 0.50f

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

        val barCount = state.bands.coerceIn(1, 6400)
        val bars: FloatArray = if (liveBands != null && liveBands.size == barCount) {
            liveBands
        } else {
            FloatArray(barCount).also { arr ->
                for (i in 0 until barCount) {
                    val frac = i.toFloat() / barCount
                    val e = when {
                        frac < 0.20f -> bass * (1f - frac * 3f) + beat * 0.35f
                        frac < 0.60f -> mid * (1f - (frac - 0.20f) * 1.8f) + beat * 0.20f
                        else -> treble * (1f - (frac - 0.60f) * 1.8f) + beat * 0.10f
                    }
                    arr[i] = e.coerceIn(0f, 1f)
                }
            }
        }
        return Features(bass, mid, treble, rms, beat, bars)
    }

    // ═══════════════════════════════════════════════════════════
    //  1. CIRCULAR BARS
    // ═══════════════════════════════════════════════════════════
    private fun drawCircularBars(c: DrawCtx, widthMul: Float, variant: Int) {
        val s = c.scope
        val bars = c.feat.bars
        val count = bars.size
        if (count == 0) return

        val r0 = c.baseR * (1f + c.feat.bass * 0.1f + c.feat.beat * 0.1f)
        val rMax = c.baseR * (0.45f + variant * 0.05f) * c.state.maxHeight
        val barW = (2f * PI.toFloat() * c.baseR / count) * 0.6f * widthMul *
                c.state.lineWidth

        bars.forEachIndexed { i, v ->
            val angle = (i.toFloat() / count) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val len = v * rMax + c.baseR * 0.05f

            val startR = if (variant == 1) r0 - len else r0
            val endR = if (variant == 1) r0 else r0 + len

            if (c.state.glow && variant != 4) {
                s.drawLine(
                    color = c.color1a(0.20f + v * 0.3f),
                    start = Offset(c.cx + cos(angle) * startR, c.cy + sin(angle) * startR),
                    end = Offset(c.cx + cos(angle) * endR, c.cy + sin(angle) * endR),
                    strokeWidth = barW * 2.0f,
                    cap = StrokeCap.Round
                )
            }

            s.drawLine(
                brush = c.sweep(),
                start = Offset(c.cx + cos(angle) * startR, c.cy + sin(angle) * startR),
                end = Offset(c.cx + cos(angle) * endR, c.cy + sin(angle) * endR),
                strokeWidth = barW.coerceAtLeast(2f),
                cap = StrokeCap.Round
            )
        }

        if (variant == 5) {
            for (k in 0 until 8) {
                val a = k * PI.toFloat() / 4f
                val spike = c.baseR * (0.6f + c.feat.beat * 0.5f)
                s.drawLine(
                    color = c.color2a(0.9f),
                    start = Offset(c.cx, c.cy),
                    end = Offset(c.cx + cos(a) * spike, c.cy + sin(a) * spike),
                    strokeWidth = c.state.lineWidth
                )
            }
        }

        drawCenterContent(c, c.baseR * c.state.imageScale * 0.85f)
    }

    // ═══════════════════════════════════════════════════════════
    //  2. LINEAR BARS
    // ═══════════════════════════════════════════════════════════
    private fun drawLinearBars(c: DrawCtx, variant: Int) {
        val s = c.scope
        val bars = c.feat.bars
        val count = bars.size
        if (count == 0) return

        val totalW = c.baseR * 2f
        val startX = c.cx - c.baseR
        val barW = totalW / count * 0.7f * c.state.lineWidth
        val maxH = c.baseR * 0.9f * c.state.maxHeight
        val baseY = c.cy

        bars.forEachIndexed { i, v ->
            val x = startX + (i.toFloat() / count) * totalW
            val h = v * maxH + 2f

            when (variant) {
                0 -> {
                    s.drawLine(
                        color = c.color1a(0.9f),
                        start = Offset(x, baseY - h),
                        end = Offset(x, baseY + h),
                        strokeWidth = barW,
                        cap = StrokeCap.Round
                    )
                }

                1 -> {
                    s.drawLine(
                        color = c.color1a(0.9f),
                        start = Offset(x, baseY - c.baseR * 0.4f - h),
                        end = Offset(x, baseY - c.baseR * 0.4f),
                        strokeWidth = barW, cap = StrokeCap.Round
                    )
                    s.drawLine(
                        color = c.color2a(0.9f),
                        start = Offset(x, baseY + c.baseR * 0.4f),
                        end = Offset(x, baseY + c.baseR * 0.4f + h),
                        strokeWidth = barW, cap = StrokeCap.Round
                    )
                }

                2 -> {
                    s.drawLine(
                        color = c.color1a(0.9f),
                        start = Offset(x, baseY),
                        end = Offset(x + (i - count / 2) * 0.3f, baseY - h),
                        strokeWidth = barW, cap = StrokeCap.Round
                    )
                }

                3 -> {
                    val segments = 12
                    for (seg in 0 until segments) {
                        if (v * segments < seg) break
                        val segY = baseY + c.baseR * 0.45f - seg * (maxH / segments)
                        val col = when {
                            seg < 6 -> Color(0xFF22C55E)
                            seg < 9 -> Color(0xFFFFD166)
                            else -> Color(0xFFFF3B3B)
                        }
                        s.drawRect(
                            color = col,
                            topLeft = Offset(x - barW / 2f, segY - 2f),
                            size = Size(barW, 3f)
                        )
                    }
                }

                4 -> {
                    s.drawLine(
                        color = c.color1a(0.7f),
                        start = Offset(x, baseY - h * 0.6f),
                        end = Offset(x, baseY + h * 0.6f),
                        strokeWidth = barW * 0.5f, cap = StrokeCap.Round
                    )
                    s.drawLine(
                        color = c.color2a(0.5f),
                        start = Offset(x, baseY - h),
                        end = Offset(x, baseY + h),
                        strokeWidth = barW * 0.3f, cap = StrokeCap.Round
                    )
                }
            }
        }

        if (variant == 0 || variant == 1) {
            s.drawLine(
                color = c.color1a(0.3f),
                start = Offset(startX, baseY),
                end = Offset(startX + totalW, baseY),
                strokeWidth = 1.5f
            )
        }

        drawCenterContent(c, c.baseR * c.state.imageScale * 0.5f)
    }

    // ═══════════════════════════════════════════════════════════
    //  3. POLYGON BARS
    // ═══════════════════════════════════════════════════════════
    private fun drawPolygonBars(c: DrawCtx, sides: Int) {
        val s = c.scope
        val bars = c.feat.bars
        val count = bars.size
        if (count == 0) return

        val r0 = c.baseR * (0.85f + c.feat.bass * 0.1f)
        val rMax = c.baseR * 0.5f * c.state.maxHeight
        val barW = c.state.lineWidth * 1.5f

        bars.forEachIndexed { i, v ->
            val t = i.toFloat() / count
            val edge = t * sides
            val edgeIdx = edge.toInt() % sides
            val edgeT = edge - edgeIdx
            val a1 = (edgeIdx.toFloat() / sides) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val a2 = ((edgeIdx + 1).toFloat() / sides) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val ax = cos(a1) * r0
            val ay = sin(a1) * r0
            val bx = cos(a2) * r0
            val by = sin(a2) * r0
            val px = ax + (bx - ax) * edgeT
            val py = ay + (by - ay) * edgeT
            val angle = atan2(py, px)
            val len = v * rMax + 4f

            s.drawLine(
                color = c.color1a(0.9f),
                start = Offset(c.cx + px, c.cy + py),
                end = Offset(
                    c.cx + px + cos(angle) * len,
                    c.cy + py + sin(angle) * len
                ),
                strokeWidth = barW, cap = StrokeCap.Round
            )
        }

        val path = Path()
        for (i in 0..sides) {
            val a = (i.toFloat() / sides) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val x = c.cx + cos(a) * r0
            val y = c.cy + sin(a) * r0
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        s.drawPath(
            path = path,
            color = c.color2a(0.7f),
            style = Stroke(width = c.state.lineWidth)
        )

        drawCenterContent(c, c.baseR * c.state.imageScale * 0.6f)
    }

    // ═══════════════════════════════════════════════════════════
    //  4. WAVEFORM
    // ═══════════════════════════════════════════════════════════
    private fun drawWaveform(c: DrawCtx, variant: Int) {
        val s = c.scope
        val segments = 200
        val path = Path()
        val width = c.baseR * 2f
        val startX = c.cx - c.baseR
        val barsSize = c.feat.bars.size
        if (barsSize == 0) return

        for (i in 0..segments) {
            val t = i.toFloat() / segments
            val x = startX + t * width
            val barIdx = (t * barsSize).toInt().coerceIn(0, barsSize - 1)
            val v = c.feat.bars[barIdx]

            val y = when (variant) {
                0 -> c.cy + (v - 0.5f) * c.baseR * 0.8f * c.state.maxHeight
                1 -> c.cy + sin(t * 12f + c.elapsed * 6f) * c.baseR *
                        (0.15f + v * 0.5f) * c.state.maxHeight

                2 -> c.cy + (if (i % 30 < 8) -1f else 1f) * v * c.baseR * 0.6f *
                        c.state.maxHeight

                3 -> c.cy + (v - 0.5f) * c.baseR * 1.2f * c.state.maxHeight
                4 -> c.cy + sin(t * 4f + c.elapsed) * v * c.baseR * 0.9f *
                        c.state.maxHeight

                else -> c.cy
            }

            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        if (c.state.glow) {
            s.drawPath(
                path = path,
                color = c.color1a(0.25f),
                style = Stroke(
                    width = c.state.lineWidth * 2.4f,
                    cap = StrokeCap.Round
                )
            )
        }
        s.drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                colors = listOf(c.color1(), c.color2(), c.color1())
            ),
            style = Stroke(width = c.state.lineWidth, cap = StrokeCap.Round)
        )

        drawCenterContent(c, c.baseR * c.state.imageScale * 0.55f)
    }

    // ═══════════════════════════════════════════════════════════
    //  5. DOTS ON RING
    // ═══════════════════════════════════════════════════════════
    private fun drawDotsOnRing(c: DrawCtx, rings: Int) {
        val s = c.scope
        val barsSize = c.feat.bars.size
        if (barsSize == 0) return

        for (ring in 1..rings) {
            val dotCount = 12 + ring * 8
            val baseRr = c.baseR * (ring.toFloat() / rings) *
                    (1f + c.feat.rms * 0.15f + c.feat.beat * 0.15f)
            for (i in 0 until dotCount) {
                val angle = (i.toFloat() / dotCount) * 2f * PI.toFloat() +
                        c.elapsed * 0.4f * ring
                val v = c.feat.bars[i % barsSize]
                val r = baseRr + v * c.baseR * 0.15f * c.state.maxHeight
                s.drawCircle(
                    brush = c.sweep(),
                    radius = (c.baseR * 0.02f + v * c.baseR * 0.02f +
                            c.feat.beat * c.baseR * 0.015f)
                        .coerceAtLeast(1.5f) * c.state.particleSize,
                    center = Offset(c.cx + cos(angle) * r, c.cy + sin(angle) * r)
                )
            }
        }
        drawCenterContent(c, c.baseR * c.state.imageScale * 0.55f)
    }

    // ═══════════════════════════════════════════════════════════
    //  6. PARTICLES
    // ═══════════════════════════════════════════════════════════
    private fun drawParticles(c: DrawCtx, variant: Int) {
        val s = c.scope
        val count = (60 + variant * 10).coerceAtMost(160)
        val blast = c.feat.beat * c.baseR * 0.8f
        val baseSpeed = 0.3f + variant * 0.05f

        for (i in 0 until count) {
            val seed = i.toDouble()
            val a = (hash(seed * 12.9898) * 2.0 * PI).toFloat()
            val phase = (hash(seed * 78.233) * c.elapsed * baseSpeed).toFloat()
            val dist = ((hash(seed * 45.123).toFloat() + phase) % 1f) * c.baseR +
                    blast * (variant % 3) * 0.5f
            val jitter = when (variant) {
                1, 4 -> hash(seed * 33.3).toFloat() * 0.4f - 0.2f
                2 -> sin(c.elapsed * 4f + i).toFloat() * 0.3f
                else -> 0f
            }
            val r = c.baseR * 0.8f * (dist / c.baseR) + jitter * c.baseR
            val x = c.cx + cos(a) * r
            val y = c.cy + sin(a) * r

            val baseSize = when (variant) {
                2 -> 2f + hash(seed * 12.0).toFloat() * 2f
                7 -> 4f + c.feat.bass * 6f
                5 -> 3f
                6 -> 3f + c.feat.beat * 4f
                else -> 1.5f + hash(seed * 55.0).toFloat() * 2f
            }
            val size = baseSize * c.state.particleSize

            val col = when (variant) {
                5 -> when (i % 4) {
                    0 -> Color(0xFFFF0066); 1 -> Color(0xFF00FFCC)
                    2 -> Color(0xFFFFCC00); else -> Color(0xFF7C3AED)
                }

                7 -> Color(c.state.color1)
                9 -> Color(0xFF60EFFF)
                else -> if (i % 2 == 0) c.color1() else c.color2()
            }

            val alpha = (0.5f + c.feat.beat * 0.5f).coerceIn(0f, 1f)
            s.drawCircle(
                color = col.copy(alpha = alpha),
                radius = size,
                center = Offset(x, y)
            )
        }
        drawCenterContent(c, c.baseR * c.state.imageScale * 0.5f)
    }

    // ═══════════════════════════════════════════════════════════
    //  7. FALLING PARTICLES
    // ═══════════════════════════════════════════════════════════
    private fun drawFalling(c: DrawCtx, variant: Int) {
        val s = c.scope
        val count = when (variant) {
            0 -> 60; 1 -> 100; 2 -> 20; else -> 80
        }
        val speed = 100f + c.feat.rms * 400f

        for (i in 0 until count) {
            val a = hash(i.toDouble() * 3.3).toFloat()
            val bx = hash(i.toDouble() * 7.7).toFloat()
            val cy0 = hash(i.toDouble() * 11.1).toFloat()
            val x = c.W * a
            val y = (cy0 * c.H + c.elapsed * speed) % c.H
            val col = when (variant) {
                0 -> Color(0xFF22C55E).copy(alpha = 0.9f)
                1 -> Color.White.copy(alpha = 0.85f)
                2 -> Color(0xFFFF8A3A).copy(alpha = 0.9f)
                else -> Color(0xFF60EFFF).copy(alpha = 0.7f)
            }
            val size = (when (variant) {
                0 -> 2f
                1 -> 1.5f + bx * 2.5f
                2 -> 3f
                else -> 1.5f
            }) * c.state.particleSize
            s.drawCircle(color = col, radius = size, center = Offset(x, y))
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  8. CLOUDS
    // ═══════════════════════════════════════════════════════════
    private fun drawClouds(c: DrawCtx, variant: Int) {
        val s = c.scope
        val count = 8 + variant * 2
        val col = when (variant) {
            0 -> Color(0xFF888899)
            1 -> Color(0xFF7C3AED)
            2 -> Color(0xFF1A1A2E)
            3 -> Color(0xFFAABBCC)
            else -> Color(0xFFDDEEFF)
        }
        val alpha = (0.25f + c.feat.bass * 0.4f).coerceIn(0f, 0.7f)

        for (i in 0 until count) {
            val a = hash(i.toDouble() * 5.5).toFloat()
            val b = hash(i.toDouble() * 9.9).toFloat()
            val drift = ((c.elapsed * (5f + a * 15f) + i * 200f) % (c.baseR * 3f)) -
                    c.baseR * 1f
            val x = c.cx + cos(i.toFloat()) * c.baseR * 0.6f + drift
            val y = c.cy + (b - 0.5f) * c.baseR * 1.2f
            val size = c.baseR * (0.3f + a * 0.4f)

            s.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        col.copy(alpha = alpha),
                        col.copy(alpha = 0f)
                    ),
                    center = Offset(x, y),
                    radius = size
                ),
                radius = size,
                center = Offset(x, y)
            )
        }
        drawCenterContent(c, c.baseR * c.state.imageScale * 0.55f)
    }

    // ═══════════════════════════════════════════════════════════
    //  9. SPIRAL
    // ═══════════════════════════════════════════════════════════
    private fun drawSpiral(c: DrawCtx, variant: Int) {
        val s = c.scope
        val turns = 6 + variant
        val points = 200
        val path = Path()

        for (i in 0..points) {
            val t = i.toFloat() / points
            val angle = t * turns * 2f * PI.toFloat() +
                    c.elapsed * (1f + variant * 0.3f)
            val r = c.baseR * t * (1f + c.feat.rms * 0.15f + c.feat.beat * 0.2f)
            val x = c.cx + cos(angle) * r
            val y = c.cy + sin(angle) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (c.state.glow) {
            s.drawPath(
                path, c.color1a(0.25f),
                style = Stroke(width = c.state.lineWidth * 3f)
            )
        }
        s.drawPath(
            path, c.sweep(),
            style = Stroke(width = c.state.lineWidth, cap = StrokeCap.Round)
        )

        if (variant >= 2) {
            for (k in 1..6) {
                val rr = c.baseR * (k / 6f) * (0.5f + c.feat.beat * 0.5f)
                s.drawCircle(
                    color = c.color2a(0.4f),
                    radius = rr, center = Offset(c.cx, c.cy),
                    style = Stroke(width = c.state.lineWidth * 0.6f)
                )
            }
        }
        drawCenterContent(c, c.baseR * c.state.imageScale * 0.4f)
    }

    // ═══════════════════════════════════════════════════════════
    //  10. RINGS
    // ═══════════════════════════════════════════════════════════
    private fun drawRings(c: DrawCtx, count: Int, animated: Boolean) {
        val s = c.scope
        for (i in 0 until count) {
            val phase = if (animated) c.elapsed * (1f + i * 0.3f) else 0f
            val r = c.baseR * (0.35f + i * 0.25f) *
                    (1f + c.feat.bass * 0.15f + c.feat.beat * 0.15f)
            val offsetX = if (animated) sin(phase) * c.baseR * 0.15f else 0f
            val offsetY = if (animated) cos(phase) * c.baseR * 0.15f else 0f
            s.drawCircle(
                brush = c.sweep(),
                radius = r,
                center = Offset(c.cx + offsetX, c.cy + offsetY),
                style = Stroke(
                    width = c.state.lineWidth + c.feat.beat * 2f
                )
            )
            if (c.state.glow) {
                s.drawCircle(
                    color = c.color1a(0.15f),
                    radius = r,
                    center = Offset(c.cx + offsetX, c.cy + offsetY),
                    style = Stroke(width = 10f)
                )
            }
        }
        drawCenterContent(c, c.baseR * c.state.imageScale * 0.55f)
    }

    // ═══════════════════════════════════════════════════════════
    //  11. GLITCH
    // ═══════════════════════════════════════════════════════════
    private fun drawGlitch(c: DrawCtx, variant: Int) {
        val s = c.scope
        val W = c.W
        val H = c.H
        val t = c.elapsed

        when (variant) {
            0 -> {
                val shift = c.feat.beat * 20f + 5f
                s.drawRect(Color.Red.copy(alpha = 0.25f), Offset(0f, 0f), Size(W, H))
                s.drawRect(
                    Color.Blue.copy(alpha = 0.25f), Offset(shift, 0f),
                    Size(W - shift, H)
                )
                s.drawRect(
                    Color.Green.copy(alpha = 0.15f), Offset(-shift, 0f),
                    Size(W - shift, H)
                )
            }

            1 -> {
                for (i in 0 until 20) {
                    val y = hash(i.toDouble() + t).toFloat() * H
                    val h = 2f + hash(i.toDouble() * 3.3).toFloat() * 6f
                    s.drawRect(
                        Color.White.copy(alpha = 0.15f),
                        Offset(0f, y), Size(W, h)
                    )
                }
            }

            2 -> {
                var y = 0f
                while (y < H) {
                    s.drawRect(
                        Color.Black.copy(alpha = 0.35f),
                        Offset(0f, y), Size(W, 1.5f)
                    )
                    y += 4f
                }
            }

            3 -> {
                val ox = sin(t * 40f) * c.feat.beat * 20f
                val oy = cos(t * 35f) * c.feat.beat * 10f
                s.drawCircle(
                    c.color1a(0.4f), radius = c.baseR,
                    center = Offset(c.cx + ox, c.cy + oy)
                )
            }

            4 -> {
                val alpha = (0.1f + abs(sin(t * 6f)) * 0.3f * c.feat.beat)
                s.drawRect(
                    Color.Black.copy(alpha = alpha),
                    Offset(0f, 0f), Size(W, H)
                )
            }

            5 -> {
                val step = 20f
                var y = 0f
                while (y < H) {
                    var x = 0f
                    while (x < W) {
                        if (hash((x + y + t * 100).toDouble()) < c.feat.beat * 0.3f) {
                            s.drawRect(
                                c.color1a(0.5f), Offset(x, y),
                                Size(step, step)
                            )
                        }
                        x += step
                    }
                    y += step
                }
            }
        }
        drawCenterContent(c, c.baseR * c.state.imageScale * 0.6f)
    }

    // ═══════════════════════════════════════════════════════════
    //  12. GRID
    // ═══════════════════════════════════════════════════════════
    private fun drawGrid(c: DrawCtx, variant: Int) {
        val s = c.scope
        val W = c.W
        val H = c.H
        val horizon = H * 0.55f

        for (i in -10..10) {
            val x = c.cx + i * (c.baseR / 6f) * (1f + c.feat.beat * 0.3f)
            s.drawLine(
                color = c.color1a(0.5f),
                start = Offset(x, horizon),
                end = Offset(c.cx + (x - c.cx) * 3f, H),
                strokeWidth = c.state.lineWidth * 0.6f
            )
        }

        var y = horizon
        var step = 4f
        while (y < H) {
            val phase = (c.elapsed * 40f) % step
            s.drawLine(
                color = c.color2a(0.6f),
                start = Offset(0f, y + phase),
                end = Offset(W, y + phase),
                strokeWidth = c.state.lineWidth * 0.5f
            )
            y += step
            step *= 1.15f
        }

        if (variant == 2) {
            s.drawRect(c.color1a(0.1f), Offset(0f, 0f), Size(W, horizon))
        }

        drawCenterContent(c, c.baseR * c.state.imageScale * 0.4f)
    }

    // ═══════════════════════════════════════════════════════════
    //  13. GEOMETRIC
    // ═══════════════════════════════════════════════════════════
    private fun drawGeometric(c: DrawCtx, variant: Int) {
        val s = c.scope
        val barsSize = c.feat.bars.size

        when (variant) {
            0 -> {
                s.rotate(degrees = c.elapsed * 40f, pivot = Offset(c.cx, c.cy)) {
                    val r = c.baseR * (0.6f + c.feat.beat * 0.3f)
                    val path = Path()
                    for (i in 0..6) {
                        val a = i * PI.toFloat() / 3f - PI.toFloat() / 2f
                        val x = c.cx + cos(a) * r
                        val y = c.cy + sin(a) * r
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    s.drawPath(
                        path, c.sweep(),
                        style = Stroke(width = c.state.lineWidth)
                    )
                }
            }

            1 -> {
                for (i in 0 until 5) {
                    val t = ((c.elapsed * 0.5f + i * 0.2f) % 1f)
                    val sz = c.baseR * (0.2f + t * 1.2f)
                    val alpha = (1f - t).coerceIn(0f, 1f) *
                            (0.6f + c.feat.beat * 0.4f)
                    s.drawRect(
                        color = c.color1a(alpha),
                        topLeft = Offset(c.cx - sz, c.cy - sz),
                        size = Size(sz * 2f, sz * 2f),
                        style = Stroke(width = c.state.lineWidth)
                    )
                }
            }

            2 -> {
                s.rotate(degrees = c.elapsed * 20f, pivot = Offset(c.cx, c.cy)) {
                    val r = c.baseR * 0.7f
                    val path = Path()
                    path.moveTo(c.cx, c.cy - r)
                    path.lineTo(c.cx + r, c.cy)
                    path.lineTo(c.cx, c.cy + r)
                    path.lineTo(c.cx - r, c.cy)
                    path.close()
                    s.drawPath(path, c.color1a(0.4f))
                    s.drawPath(
                        path, c.color2a(0.9f),
                        style = Stroke(width = c.state.lineWidth)
                    )
                }
            }

            3 -> {
                if (barsSize == 0) return
                for (i in 0 until 6) {
                    val a = i * PI.toFloat() / 3f
                    val r = c.baseR * (0.5f + c.feat.bars[i % barsSize] * 0.5f)
                    val x = c.cx + cos(a) * r
                    val y = c.cy + sin(a) * r
                    s.drawRect(
                        color = c.color1a(0.7f),
                        topLeft = Offset(x - 10f, y - 10f),
                        size = Size(20f, 20f)
                    )
                }
            }

            4 -> {
                val len = c.baseR * 1.2f
                s.drawLine(
                    c.color1a(0.8f),
                    Offset(c.cx - len, c.cy), Offset(c.cx + len, c.cy),
                    strokeWidth = c.state.lineWidth
                )
                s.drawLine(
                    c.color2a(0.8f),
                    Offset(c.cx, c.cy - len), Offset(c.cx, c.cy + len),
                    strokeWidth = c.state.lineWidth
                )
                val rr = c.baseR * (0.4f + c.feat.beat * 0.3f)
                s.drawCircle(
                    c.color1a(0.9f), radius = rr,
                    center = Offset(c.cx, c.cy),
                    style = Stroke(width = c.state.lineWidth)
                )
            }

            5 -> {
                for (i in 0 until 12) {
                    val a = hash(i.toDouble() * 4.4).toFloat() * 2f * PI.toFloat()
                    val r = c.baseR *
                            (0.3f + hash(i.toDouble() * 7.7).toFloat() * 0.6f)
                    val x = c.cx + cos(a) * r
                    val y = c.cy + sin(a) * r
                    s.rotate(
                        degrees = c.elapsed * 30f + i * 30f,
                        pivot = Offset(x, y)
                    ) {
                        s.drawRect(
                            color = c.color1a(0.7f),
                            topLeft = Offset(x - 5f, y - 5f),
                            size = Size(10f, 10f)
                        )
                    }
                }
            }

            6 -> {
                val t = (sin(c.elapsed * 2f) + 1f) / 2f
                val sides = 3 + (t * 5f).toInt()
                val r = c.baseR * 0.7f
                val path = Path()
                for (i in 0..sides) {
                    val a = i * 2f * PI.toFloat() / sides
                    val x = c.cx + cos(a) * r
                    val y = c.cy + sin(a) * r
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                s.drawPath(path, c.color1a(0.3f))
                s.drawPath(
                    path, c.color2a(0.9f),
                    style = Stroke(width = c.state.lineWidth)
                )
            }

            7 -> {
                s.rotate(degrees = c.elapsed * 60f, pivot = Offset(c.cx, c.cy)) {
                    s.drawOval(
                        color = c.color1a(0.8f),
                        topLeft = Offset(c.cx - c.baseR, c.cy - c.baseR * 0.4f),
                        size = Size(c.baseR * 2f, c.baseR * 0.8f),
                        style = Stroke(width = c.state.lineWidth)
                    )
                }
                s.rotate(degrees = c.elapsed * 45f, pivot = Offset(c.cx, c.cy)) {
                    s.drawOval(
                        color = c.color2a(0.8f),
                        topLeft = Offset(c.cx - c.baseR, c.cy - c.baseR),
                        size = Size(c.baseR * 0.8f, c.baseR * 2f),
                        style = Stroke(width = c.state.lineWidth)
                    )
                }
            }
        }
        drawCenterContent(c, c.baseR * c.state.imageScale * 0.5f)
    }

    // ═══════════════════════════════════════════════════════════
    //  14. CINEMATIC
    // ═══════════════════════════════════════════════════════════
    private fun drawCinematic(c: DrawCtx, variant: Int) {
        val s = c.scope
        val W = c.W
        val H = c.H
        when (variant) {
            0 -> {
                val intensity = c.feat.beat * 0.5f + 0.3f
                s.drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = intensity),
                            Color(0xFFFFCC88).copy(alpha = intensity * 0.4f),
                            Color.Transparent
                        ),
                        center = Offset(W * 0.7f, H * 0.3f),
                        radius = c.baseR * 1.5f
                    ),
                    radius = c.baseR * 1.5f,
                    center = Offset(W * 0.7f, H * 0.3f)
                )
            }

            1 -> {
                for (i in 0 until 12) {
                    val a = (i.toFloat() / 12) * 2f * PI.toFloat() +
                            c.elapsed * 0.3f
                    s.drawLine(
                        color = Color(0xFFFFEEAA).copy(alpha = 0.15f),
                        start = Offset(W / 2, 0f),
                        end = Offset(W / 2 + cos(a) * W, H + sin(a) * H),
                        strokeWidth = 8f
                    )
                }
            }

            2 -> {
                val x = W * (0.5f + sin(c.elapsed * 0.6f) * 0.4f)
                s.drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF9966).copy(alpha = 0.5f),
                            Color(0xFFFF3366).copy(alpha = 0.2f),
                            Color.Transparent
                        ),
                        center = Offset(x, H * 0.4f),
                        radius = c.baseR * 1.8f
                    ),
                    radius = c.baseR * 1.8f,
                    center = Offset(x, H * 0.4f)
                )
            }

            3 -> {
                s.drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFCC66).copy(alpha = 0.3f),
                            Color(0xFFFF6B1A).copy(alpha = 0.1f)
                        )
                    ),
                    topLeft = Offset.Zero, size = Size(W, H)
                )
            }

            4 -> {
                val shift = c.feat.beat * 10f
                s.drawRect(
                    Color(0xFFFF0000).copy(alpha = 0.15f),
                    Offset(-shift, 0f), Size(W, H)
                )
                s.drawRect(
                    Color(0xFF00FF00).copy(alpha = 0.15f),
                    Offset(0f, 0f), Size(W, H)
                )
                s.drawRect(
                    Color(0xFF0000FF).copy(alpha = 0.15f),
                    Offset(shift, 0f), Size(W, H)
                )
            }

            5 -> {
                val vig = (0.3f + c.feat.bass * 0.4f).coerceIn(0f, 0.9f)
                s.drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = vig)
                        ),
                        center = Offset(W / 2, H / 2),
                        radius = c.baseR * 1.5f
                    ),
                    topLeft = Offset.Zero, size = Size(W, H)
                )
            }

            6 -> {
                val alpha = c.feat.beat * 0.3f
                s.drawRect(
                    Color.White.copy(alpha = alpha),
                    Offset.Zero, Size(W, H)
                )
            }

            7 -> {
                val a = c.feat.beat * 0.5f
                s.drawRect(Color.Black.copy(alpha = a), Offset.Zero, Size(W, H))
            }

            8 -> {
                for (i in 0 until 200) {
                    val x = hash((i + c.elapsed * 100).toDouble())
                        .toFloat() * W
                    val y = hash((i + c.elapsed * 77).toDouble())
                        .toFloat() * H
                    s.drawRect(
                        Color.White.copy(alpha = 0.1f),
                        Offset(x, y), Size(2f, 2f)
                    )
                }
            }

            9 -> {
                for (i in 0 until 25) {
                    val x = hash((i * 3.3).toDouble()).toFloat() * W
                    val y = hash((i * 7.7).toDouble()).toFloat() * H
                    val r = 15f + hash((i * 11.1).toDouble()).toFloat() * 40f
                    s.drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                c.color1a(0.25f),
                                Color.Transparent
                            ),
                            center = Offset(x, y),
                            radius = r
                        ),
                        radius = r, center = Offset(x, y)
                    )
                }
            }

            10 -> {
                val n = ((c.elapsed * 1f).toInt() % 5) + 1
                s.drawIntoCanvas { canvas ->
                    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.WHITE
                        textSize = c.baseR * 1.2f
                        textAlign = AndroidPaint.Align.CENTER
                        typeface = AndroidTypeface.DEFAULT_BOLD
                        alpha = (255 * (0.8f + c.feat.beat * 0.2f)).toInt()
                    }
                    canvas.nativeCanvas.drawText(
                        "$n", c.cx, c.cy + c.baseR * 0.4f, paint
                    )
                }
            }

            11 -> {
                val ox = sin(c.elapsed * 50f) * c.feat.beat * 15f
                val oy = cos(c.elapsed * 47f) * c.feat.beat * 10f
                s.drawRect(c.color1a(0.15f), Offset(ox, oy), Size(W, H))
            }

            12 -> {
                val r = c.baseR *
                        (1f + sin(c.elapsed * 1f) * 0.1f + c.feat.beat * 0.2f)
                s.drawCircle(
                    color = c.color1a(0.15f),
                    radius = r, center = Offset(c.cx, c.cy),
                    style = Stroke(width = c.state.lineWidth)
                )
            }
        }
        drawCenterContent(c, c.baseR * c.state.imageScale * 0.55f)
    }

    // ═══════════════════════════════════════════════════════════
    //  15. NEON RING
    // ═══════════════════════════════════════════════════════════
    private fun drawNeonRing(c: DrawCtx, hueShift: Boolean = false) {
        val s = c.scope
        val pulse = 1f + c.feat.bass * 0.35f + c.feat.beat * c.state.beatPulse
        val r = c.baseR * pulse
        val glow = c.glowBoost()

        if (glow > 0.05f) {
            listOf(1.35f to 0.12f, 1.15f to 0.25f, 1.0f to 0.5f)
                .forEach { (m, a) ->
                    s.drawCircle(
                        color = c.color1a(a * (1f + c.feat.beat * 0.8f)),
                        radius = r * m,
                        center = Offset(c.cx, c.cy),
                        style = Stroke(width = c.baseR * 0.14f)
                    )
                }
        }

        val colors = if (hueShift) {
            listOf(
                Color.hsv((c.elapsed * 60f) % 360f, 0.9f, 1f),
                Color.hsv((c.elapsed * 60f + 120f) % 360f, 0.9f, 1f),
                Color.hsv((c.elapsed * 60f + 240f) % 360f, 0.9f, 1f)
            )
        } else {
            listOf(c.color1(), c.color2(), c.color1())
        }

        s.drawCircle(
            brush = Brush.sweepGradient(
                colors = colors,
                center = Offset(c.cx, c.cy)
            ),
            radius = r,
            center = Offset(c.cx, c.cy),
            style = Stroke(
                width = c.state.lineWidth * 3f,
                cap = StrokeCap.Round
            )
        )

        drawCenterContent(c, r * c.state.imageScale)
    }

    // ═══════════════════════════════════════════════════════════
    //  16. LED MATRIX
    // ═══════════════════════════════════════════════════════════
    private fun drawLedMatrix(c: DrawCtx) {
        val s = c.scope
        val barsSize = c.feat.bars.size
        if (barsSize == 0) return

        val step = c.baseR * 0.1f
        val cols = 20
        val rows = 20
        val startX = c.cx - cols * step / 2f
        val startY = c.cy - rows * step / 2f

        for (j in 0 until rows) {
            for (i in 0 until cols) {
                val barIdx = ((i / cols.toFloat()) * barsSize)
                    .toInt().coerceIn(0, barsSize - 1)
                val v = c.feat.bars[barIdx]
                val isOn = ((i + j) % 2 == 0) && v > 0.3f
                val col = if (isOn) c.color1() else c.color1a(0.1f)
                s.drawRect(
                    color = col,
                    topLeft = Offset(startX + i * step, startY + j * step),
                    size = Size(step * 0.7f, step * 0.7f)
                )
            }
        }
        drawCenterContent(c, c.baseR * c.state.imageScale * 0.5f)
    }

    // ═══════════════════════════════════════════════════════════
    //  17. LASER BEAM
    // ═══════════════════════════════════════════════════════════
    private fun drawLaserBeam(c: DrawCtx, variant: Int) {
        val s = c.scope
        val count = if (variant == 0) 8 else 20
        for (i in 0 until count) {
            val a = (i.toFloat() / count) * 2f * PI.toFloat() +
                    c.elapsed * (0.5f + variant * 0.2f)
            val len = c.baseR * (1.2f + c.feat.beat * 0.5f)
            val width = if (variant == 1) c.state.lineWidth * 0.6f
            else c.state.lineWidth
            s.drawLine(
                color = c.color1a(0.8f),
                start = Offset(c.cx, c.cy),
                end = Offset(c.cx + cos(a) * len, c.cy + sin(a) * len),
                strokeWidth = width
            )
        }
        s.drawCircle(
            c.color2(), radius = c.baseR * 0.15f,
            center = Offset(c.cx, c.cy)
        )
        drawCenterContent(c, c.baseR * c.state.imageScale * 0.5f)
    }

    // ═══════════════════════════════════════════════════════════
    //  18. DISC
    // ═══════════════════════════════════════════════════════════
    private fun drawDisc(c: DrawCtx, variant: Int) {
        val s = c.scope
        val r = c.baseR * (1f + c.feat.beat * 0.1f)
        when (variant) {
            0 -> {
                s.drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFF0066), Color(0xFFFFCC00)),
                        startY = c.cy - r, endY = c.cy + r
                    ),
                    radius = r, center = Offset(c.cx, c.cy)
                )
                for (i in 0 until 6) {
                    val y = c.cy - r * 0.3f + i * (r * 0.2f)
                    s.drawLine(
                        color = Color(0xFF1A1A2E).copy(alpha = 0.9f),
                        start = Offset(c.cx - r, y), end = Offset(c.cx + r, y),
                        strokeWidth = 2f + i * 1.2f
                    )
                }
            }

            1 -> {
                s.rotate(degrees = c.elapsed * 30f, pivot = Offset(c.cx, c.cy)) {
                    s.drawCircle(
                        color = c.color1a(0.9f),
                        radius = r, center = Offset(c.cx, c.cy),
                        style = Stroke(width = c.state.lineWidth)
                    )
                    s.drawCircle(
                        color = c.color2a(0.9f),
                        radius = r * 0.6f, center = Offset(c.cx, c.cy),
                        style = Stroke(width = c.state.lineWidth)
                    )
                }
            }

            2 -> {
                for (i in 0 until 5) {
                    s.drawCircle(
                        color = Color(0xFF60EFFF).copy(
                            alpha = (0.3f - i * 0.05f) * c.state.opacity
                        ),
                        radius = r * (1f - i * 0.15f) *
                                (1f + c.feat.beat * 0.2f),
                        center = Offset(c.cx, c.cy),
                        style = Stroke(width = c.state.lineWidth)
                    )
                }
            }
        }
        drawCenterContent(c, c.baseR * c.state.imageScale * 0.55f)
    }

    // ═══════════════════════════════════════════════════════════
    //  19. DNA STRAND
    // ═══════════════════════════════════════════════════════════
    private fun drawDnaStrand(c: DrawCtx) {
        val s = c.scope
        val barsSize = c.feat.bars.size
        if (barsSize == 0) return

        val points = 60
        for (i in 0..points) {
            val t = i.toFloat() / points
            val y = c.cy + (t - 0.5f) * c.baseR * 2f
            val phase = t * 6f * PI.toFloat() + c.elapsed * 3f
            val x1 = c.cx + sin(phase) * c.baseR * 0.4f
            val x2 = c.cx - sin(phase) * c.baseR * 0.4f
            val v = c.feat.bars[(t * barsSize).toInt().coerceIn(0, barsSize - 1)]
            val alpha = 0.4f + v * 0.6f
            s.drawCircle(
                c.color1a(alpha),
                radius = (3f + v * 4f) * c.state.particleSize,
                center = Offset(x1, y)
            )
            s.drawCircle(
                c.color2a(alpha),
                radius = (3f + v * 4f) * c.state.particleSize,
                center = Offset(x2, y)
            )
            if (i % 4 == 0) {
                s.drawLine(
                    color = c.color1a(0.3f),
                    start = Offset(x1, y), end = Offset(x2, y),
                    strokeWidth = c.state.lineWidth * 0.6f
                )
            }
        }
        drawCenterContent(c, c.baseR * c.state.imageScale * 0.5f)
    }

    // ═══════════════════════════════════════════════════════════
    //  20. KALEIDOSCOPE
    // ═══════════════════════════════════════════════════════════
    private fun drawKaleidoscope(c: DrawCtx, mirrored: Boolean = false) {
        val s = c.scope
        val barsSize = c.feat.bars.size
        if (barsSize == 0) return

        val segments = if (mirrored) 2 else 8
        val r = c.baseR * (0.9f + c.feat.beat * 0.15f)

        for (i in 0 until segments) {
            s.rotate(
                degrees = i * (360f / segments) + c.elapsed * 15f,
                pivot = Offset(c.cx, c.cy)
            ) {
                for (k in 0 until 8) {
                    val angle = (k.toFloat() / 8) * PI.toFloat() / 2f
                    val len = r * c.feat.bars[k % barsSize] * 0.7f
                    s.drawLine(
                        color = if (k % 2 == 0) c.color1a(0.8f)
                        else c.color2a(0.8f),
                        start = Offset(c.cx, c.cy),
                        end = Offset(
                            c.cx + cos(angle) * len,
                            c.cy + sin(angle) * len
                        ),
                        strokeWidth = c.state.lineWidth
                    )
                }
            }
        }
        drawCenterContent(c, c.baseR * c.state.imageScale * 0.4f)
    }

    // ═══════════════════════════════════════════════════════════
    //  21. SPLIT DIAGONAL
    // ═══════════════════════════════════════════════════════════
    private fun drawSplitDiagonal(c: DrawCtx) {
        val s = c.scope
        val offset = c.feat.beat * 20f
        s.drawLine(
            color = c.color1a(0.9f),
            start = Offset(c.cx - c.baseR + offset, c.cy - c.baseR),
            end = Offset(c.cx + c.baseR + offset, c.cy + c.baseR),
            strokeWidth = c.state.lineWidth
        )
        s.drawLine(
            color = c.color2a(0.9f),
            start = Offset(c.cx - c.baseR - offset, c.cy - c.baseR),
            end = Offset(c.cx + c.baseR - offset, c.cy + c.baseR),
            strokeWidth = c.state.lineWidth
        )
        drawCenterContent(c, c.baseR * c.state.imageScale * 0.5f)
    }

    // ═══════════════════════════════════════════════════════════
    //  22. CHECKERBOARD
    // ═══════════════════════════════════════════════════════════
    private fun drawCheckerboard(c: DrawCtx) {
        val s = c.scope
        val step = c.baseR * 0.15f
        val cols = 14
        val rows = 14
        val ox = c.cx - cols * step / 2f
        val oy = c.cy - rows * step / 2f
        for (j in 0 until rows) {
            for (i in 0 until cols) {
                val on = (i + j) % 2 == 0
                val wave = sin((i + j) * 0.5f + c.elapsed * 3f) *
                        c.feat.beat * 5f
                val col = if (on) Color.Black.copy(alpha = 0.7f)
                else Color.White.copy(alpha = 0.4f)
                s.drawRect(
                    color = col,
                    topLeft = Offset(ox + i * step + wave, oy + j * step),
                    size = Size(step, step)
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  23. RIBBON
    // ═══════════════════════════════════════════════════════════
    private fun drawRibbon(c: DrawCtx) {
        val s = c.scope
        val points = 100
        val path = Path()
        for (i in 0..points) {
            val t = i.toFloat() / points
            val x = c.cx - c.baseR + t * c.baseR * 2f
            val y = c.cy + sin(t * 6f * PI.toFloat() + c.elapsed * 3f) *
                    c.baseR * (0.3f + c.feat.rms * 0.5f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (c.state.glow) {
            s.drawPath(
                path, c.color1a(0.3f),
                style = Stroke(
                    width = c.state.lineWidth * 4f,
                    cap = StrokeCap.Round
                )
            )
        }
        s.drawPath(
            path, c.sweep(),
            style = Stroke(width = c.state.lineWidth, cap = StrokeCap.Round)
        )
        drawCenterContent(c, c.baseR * c.state.imageScale * 0.5f)
    }

    // ═══════════════════════════════════════════════════════════
    //  24. LIQUID
    // ═══════════════════════════════════════════════════════════
    private fun drawLiquid(c: DrawCtx, variant: Int) {
        val s = c.scope
        val path = Path()
        val segments = 120

        for (i in 0..segments) {
            val a = (i.toFloat() / segments) * 2f * PI.toFloat()
            val wave = when (variant) {
                0 -> sin(a * 6f + c.elapsed * 3f) * 0.1f
                1 -> sin(a * 20f + c.elapsed * 8f) * 0.05f
                2 -> sin(a * 4f + c.elapsed * 5f) * 0.15f
                else -> 0f
            }
            val r = c.baseR *
                    (1f + wave + c.feat.bass * 0.1f + c.feat.beat * 0.1f)
            val x = c.cx + cos(a) * r
            val y = c.cy + sin(a) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        if (c.state.glow) {
            s.drawPath(
                path, c.color1a(0.2f),
                style = Stroke(width = 12f)
            )
        }
        s.drawPath(
            path, c.sweep(),
            style = Stroke(width = c.state.lineWidth, cap = StrokeCap.Round)
        )
        drawCenterContent(c, c.baseR * c.state.imageScale * 0.55f)
    }

    // ═══════════════════════════════════════════════════════════
    //  PREMIUM — AUDIO SPHERE
    // ═══════════════════════════════════════════════════════════
    private fun drawAudioSphere(c: DrawCtx) {
        val s = c.scope
        val bars = c.feat.bars
        val count = bars.size.coerceAtMost(180)
        if (count == 0) return

        val baseRadius = c.baseR * 0.7f
        val maxBarLen = c.baseR * 0.9f * c.state.maxHeight

        if (c.glowBoost() > 0.05f) {
            val glowR = baseRadius * (1.3f + c.feat.bass * 0.4f)
            s.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        c.color1a(0.35f + c.feat.beat * 0.3f),
                        c.color2a(0.18f),
                        Color.Transparent
                    ),
                    center = Offset(c.cx, c.cy),
                    radius = glowR
                ),
                radius = glowR, center = Offset(c.cx, c.cy)
            )
        }

        val angleStep = (2f * PI.toFloat()) / count
        bars.take(count).forEachIndexed { i, v ->
            val angle = i * angleStep - PI.toFloat() / 2f
            val len = v * maxBarLen + 4f

            val startR: Float
            val endR: Float
            when (c.state.sideMode) {
                "a" -> {
                    startR = baseRadius; endR = baseRadius + len
                }

                "b" -> {
                    startR = baseRadius - len; endR = baseRadius
                }

                else -> {
                    startR = baseRadius - len; endR = baseRadius + len
                }
            }

            val x1 = c.cx + cos(angle) * startR
            val y1 = c.cy + sin(angle) * startR
            val x2 = c.cx + cos(angle) * endR
            val y2 = c.cy + sin(angle) * endR

            if (c.state.glow) {
                s.drawLine(
                    color = c.color1a(0.15f + v * 0.3f),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = c.state.lineWidth * 2.5f,
                    cap = StrokeCap.Round
                )
            }
            s.drawLine(
                brush = c.sweep(),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = c.state.lineWidth.coerceAtLeast(1.5f),
                cap = StrokeCap.Round
            )
        }

        s.drawCircle(
            color = c.color2a(0.8f),
            radius = baseRadius,
            center = Offset(c.cx, c.cy),
            style = Stroke(width = c.state.lineWidth)
        )

        drawCenterContent(c, baseRadius * c.state.imageScale)
    }

    // ═══════════════════════════════════════════════════════════
    //  PREMIUM — WAVEFORM RING
    // ═══════════════════════════════════════════════════════════
    private fun drawWaveformRing(c: DrawCtx) {
        val s = c.scope
        val bars = c.feat.bars
        val count = bars.size.coerceAtMost(360)
        if (count == 0) return

        val baseRadius = c.baseR * 0.85f
        val innerRadius = c.baseR * 0.6f
        val maxLen = c.baseR * 0.6f * c.state.maxHeight

        s.drawCircle(
            color = c.color2a(0.3f),
            radius = baseRadius,
            center = Offset(c.cx, c.cy),
            style = Stroke(width = c.state.lineWidth * 0.6f)
        )

        s.drawCircle(
            color = Color.Black.copy(alpha = 0.85f),
            radius = innerRadius,
            center = Offset(c.cx, c.cy)
        )

        val angleStep = (2f * PI.toFloat()) / count
        bars.take(count).forEachIndexed { i, v ->
            val angle = i * angleStep - PI.toFloat() / 2f
            val len = (v * maxLen + 2f)

            val startR: Float
            val endR: Float
            when (c.state.sideMode) {
                "a" -> {
                    startR = innerRadius; endR = innerRadius + len
                }

                "b" -> {
                    startR = innerRadius - len; endR = innerRadius
                }

                else -> {
                    startR = innerRadius - len; endR = innerRadius + len
                }
            }

            val x1 = c.cx + cos(angle) * startR
            val y1 = c.cy + sin(angle) * startR
            val x2 = c.cx + cos(angle) * endR
            val y2 = c.cy + sin(angle) * endR

            if (c.state.glow) {
                s.drawLine(
                    color = c.color1a(0.2f + v * 0.4f),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = c.state.lineWidth * 3.5f,
                    cap = StrokeCap.Round
                )
            }
            s.drawLine(
                brush = Brush.sweepGradient(
                    colors = listOf(c.color1(), c.color2(), c.color1()),
                    center = Offset(c.cx, c.cy)
                ),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = c.state.lineWidth.coerceAtLeast(1.5f),
                cap = StrokeCap.Round
            )
        }

        s.drawCircle(
            color = c.color1a(0.6f),
            radius = baseRadius * (1f + c.feat.rms * 0.08f),
            center = Offset(c.cx, c.cy),
            style = Stroke(width = c.state.lineWidth * 0.8f)
        )

        drawCenterContent(c, innerRadius * c.state.imageScale * 0.9f)
    }

    // ═══════════════════════════════════════════════════════════
    //  PREMIUM — SYMMETRIC WAVE
    // ═══════════════════════════════════════════════════════════
    private fun drawSymmetricWave(c: DrawCtx) {
        val s = c.scope
        val W = c.W
        val midY = c.cy
        val maxHalf = W * 0.42f
        val maxHeight = c.baseR * 0.7f * c.state.maxHeight
        val samples = minOf(c.feat.bars.size, 256)

        if (samples == 0) return

        for (i in 0 until samples) {
            val t = i.toFloat() / samples
            val v = c.feat.bars[i]
            val h = v * maxHeight + 2f
            val x = c.cx - t * maxHalf
            val barW = (maxHalf / samples) * 0.55f * c.state.lineWidth

            if (c.state.glow) {
                s.drawLine(
                    color = c.color1a(0.15f + v * 0.3f),
                    start = Offset(x, midY - h),
                    end = Offset(x, midY + h),
                    strokeWidth = barW * 3f,
                    cap = StrokeCap.Round
                )
            }
            s.drawLine(
                color = c.color1a(0.9f),
                start = Offset(x, midY - h),
                end = Offset(x, midY + h),
                strokeWidth = barW,
                cap = StrokeCap.Round
            )
        }

        for (i in 0 until samples) {
            val t = i.toFloat() / samples
            val v = c.feat.bars[i]
            val h = v * maxHeight + 2f
            val x = c.cx + t * maxHalf
            val barW = (maxHalf / samples) * 0.55f * c.state.lineWidth

            if (c.state.glow) {
                s.drawLine(
                    color = c.color2a(0.15f + v * 0.3f),
                    start = Offset(x, midY - h),
                    end = Offset(x, midY + h),
                    strokeWidth = barW * 3f,
                    cap = StrokeCap.Round
                )
            }
            s.drawLine(
                color = c.color2a(0.9f),
                start = Offset(x, midY - h),
                end = Offset(x, midY + h),
                strokeWidth = barW,
                cap = StrokeCap.Round
            )
        }

        s.drawLine(
            color = c.color1a(0.6f),
            start = Offset(c.cx - maxHalf, midY),
            end = Offset(c.cx + maxHalf, midY),
            strokeWidth = c.state.lineWidth
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  CENTER CONTENT — TEXT/IMAGE (UNTOUCHED)
    // ═══════════════════════════════════════════════════════════
    private fun drawCenterContent(c: DrawCtx, baseSize: Float) {
        if (!c.state.hasCenterContent) return

        val energy = if (c.state.imageBassOnly) c.feat.bass else c.feat.rms
        val beatBoost = c.feat.beat * c.state.imagePulseAmount * 1.5f
        val pulseFactor = 1f + (energy * c.state.imagePulseAmount) + beatBoost

        val size = baseSize * pulseFactor
        val left = c.cx - size
        val top = c.cy - size

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

            val drawImageFirst = !c.state.textOnTopOfImage || !hasText

            if (drawImageFirst) {
                if (hasImg) drawImageLayer(c, size, left, top, newRot)
                if (hasText) drawTextLayer(c, size)
            } else {
                if (hasText) drawTextLayer(c, size)
                if (hasImg) drawImageLayer(c, size, left, top, newRot)
            }
        }

        c.scope.drawCircle(
            color = c.color1a(0.6f + c.feat.beat * 0.4f),
            radius = size,
            center = Offset(c.cx, c.cy),
            style = Stroke(width = c.baseR * (0.02f + c.feat.beat * 0.02f))
        )
    }

    private fun DrawScope.drawImageLayer(
        c: DrawCtx, size: Float, left: Float, top: Float, rotationDeg: Float
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

    private fun DrawScope.drawTextLayer(c: DrawCtx, circleRadius: Float) {
        val ts = c.state.textState
        val content = ts.content.ifBlank { c.state.textContent }
        if (content.isBlank()) return

        val circleDiameter = circleRadius * 2f
        val fontScale = (circleDiameter / 400f).coerceIn(0.1f, 3f)
        val baseFontSize = (ts.fontSize.toFloat() * fontScale).coerceAtLeast(6f)

        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas

            val family = when {
                ts.fontFamily.lowercase().contains("mono") ->
                    AndroidTypeface.MONOSPACE

                ts.fontFamily.lowercase().contains("serif") ->
                    AndroidTypeface.SERIF

                ts.fontFamily.lowercase().contains("script") ||
                        ts.fontFamily.lowercase().contains("brush") ->
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

            val letterSpacingEm = if (baseFontSize > 0f)
                (ts.letterSpacing / baseFontSize).coerceIn(-0.3f, 0.3f)
            else 0f

            val alphaInt = (ts.opacity / 100f * c.state.opacity * 255)
                .toInt().coerceIn(0, 255)
            if (alphaInt <= 0) return@drawIntoCanvas

            val basePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                textSize = baseFontSize
                textAlign = align
                this.typeface = typeface
                letterSpacing = letterSpacingEm
                color = ts.color.toInt()
                this.alpha = alphaInt
            }

            val maxTextWidth = circleDiameter * (ts.maxWidth / 100f)
                .coerceIn(0.3f, 1f) * 0.88f
            val naturalWidth = basePaint.measureText(content)
            val fitFontSize = if (naturalWidth > maxTextWidth && naturalWidth > 0f)
                baseFontSize * (maxTextWidth / naturalWidth)
            else baseFontSize
            basePaint.textSize = fitFontSize

            val fm = basePaint.fontMetrics
            val baseline = c.cy - (fm.ascent + fm.descent) / 2f
            val textX = c.cx

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

            if (ts.strokeEnabled && ts.strokeWidth > 0f) {
                val strokePx = (ts.strokeWidth * fontScale * 2f)
                    .coerceIn(1f, 40f)
                val sp = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    textSize = fitFontSize
                    textAlign = align
                    this.typeface = typeface
                    letterSpacing = letterSpacingEm
                    color = ts.strokeColor.toInt()
                    this.style = AndroidPaint.Style.STROKE
                    strokeWidth = strokePx
                    strokeJoin = AndroidPaint.Join.ROUND
                    strokeCap = AndroidPaint.Cap.ROUND
                    alpha = alphaInt
                }
                nativeCanvas.drawText(content, textX, baseline, sp)
            }

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
    @Synchronized
    fun setCenterImage(uri: String?, bitmap: ImageBitmap?) {
        if (uri != cachedImageKey) {
            cachedImage = bitmap
            cachedImageKey = uri
        }
    }

    @Synchronized
    fun clearCache() {
        smoothBass.clear(); smoothMid.clear(); smoothTreble.clear()
        smoothRms.clear(); smoothBeat.clear(); imageRotation.clear()
        cachedImage = null; cachedImageKey = null
    }

    private fun hash(n: Double): Double {
        val x = sin(n * 12.9898 + 78.233) * 43758.5453
        return x - floor(x)
    }
}
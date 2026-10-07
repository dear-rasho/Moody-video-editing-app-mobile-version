package com.moody.moodyvideoeditor.ui.components

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import coil.decode.BitmapFactoryDecoder
import coil.request.ImageRequest
import com.moody.moodyvideoeditor.data.BrushPoint
import com.moody.moodyvideoeditor.data.BrushStroke
import com.moody.moodyvideoeditor.data.BrushType
import com.moody.moodyvideoeditor.data.ColorFilterValues
import com.moody.moodyvideoeditor.data.ColorMatteMode
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.EffectState
import com.moody.moodyvideoeditor.data.FilterState
import com.moody.moodyvideoeditor.data.MaskState
import com.moody.moodyvideoeditor.data.MaskType
import com.moody.moodyvideoeditor.data.OverlayState
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectState
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectType
import com.moody.moodyvideoeditor.data.advanced.BlurDimension
import com.moody.moodyvideoeditor.utils.BrushEngine
import com.moody.moodyvideoeditor.utils.ColorMatrixBuilder
import com.moody.moodyvideoeditor.utils.ColorWheelEngine
import com.moody.moodyvideoeditor.utils.ColorWheelPreviewFilter
import com.moody.moodyvideoeditor.utils.EffectsEngine
import com.moody.moodyvideoeditor.utils.FontLibrary
import com.moody.moodyvideoeditor.utils.KeyframeStore
import com.moody.moodyvideoeditor.utils.MaskEngine
import com.moody.moodyvideoeditor.utils.OverlayEngine
import com.moody.moodyvideoeditor.utils.RatioHelper
import com.moody.moodyvideoeditor.utils.TextRenderContract
import com.moody.moodyvideoeditor.utils.TextScaler
import com.moody.moodyvideoeditor.utils.Transform2D
import com.moody.moodyvideoeditor.utils.TransformApplier
import com.moody.moodyvideoeditor.utils.TransitionRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

// ═══════════════════════════════════════════════════════════════
//  HELPER — Per-clip color matrix
// ═══════════════════════════════════════════════════════════════

private fun buildClipMatrix(
    clip: EditorClip,
    globalMatrix: android.graphics.ColorMatrix,
    applyGlobal: Boolean,
    colorWheelMatrix: android.graphics.ColorMatrix? = null,
    useExactColorWheels: Boolean = false
): android.graphics.ColorMatrix? {
    val cm = android.graphics.ColorMatrix()

    if (!clip.adjustments.isDefault &&
        ColorMatrixBuilder.hasRealTimeAdjustments(clip.adjustments)
    ) {
        cm.postConcat(ColorMatrixBuilder.build(clip.adjustments))
    }
    val ownFilter = ColorFilterValues(
        brightness = clip.filters.brightness,
        contrast = clip.filters.contrast,
        saturation = clip.filters.saturation,
        hue = clip.filters.hue,
        grayscale = clip.filters.grayscale,
        sepia = clip.filters.sepia,
        invert = clip.filters.invert,
        blur = clip.filters.blur,
        opacity = clip.filters.opacity
    )
    if (EffectsEngine.hasColorEffect(ownFilter)) {
        cm.postConcat(EffectsEngine.buildColorMatrix(ownFilter))
    }

    if (applyGlobal) cm.postConcat(globalMatrix)
    if (!useExactColorWheels) {
        ColorWheelEngine.buildColorMatrix(clip.colorWheel)?.let(cm::postConcat)
        colorWheelMatrix?.let(cm::postConcat)
    }

    val hasChange =
        !clip.adjustments.isDefault ||
                clip.filters.hasAnyChange ||
                (clip.colorWheel.hasAnyChange && !useExactColorWheels) ||
                applyGlobal ||
                (colorWheelMatrix != null && !useExactColorWheels)

    return if (hasChange) cm else null
}

private fun colorWheelStatesForClip(
    clip: EditorClip,
    activeWheels: List<EditorClip>
) = buildList {
    if (clip.colorWheel.hasAnyChange) add(clip.colorWheel)
    activeWheels
        .filter { it.trackIndex > clip.trackIndex && it.colorWheel.hasAnyChange }
        .forEach { add(it.colorWheel) }
}

private fun colorWheelMatrixForClip(
    clip: EditorClip,
    activeWheels: List<EditorClip>
): android.graphics.ColorMatrix? {
    val applicableWheels = activeWheels.filter {
        it.trackIndex > clip.trackIndex && it.colorWheel.hasAnyChange
    }
    if (applicableWheels.isEmpty()) return null

    val result = android.graphics.ColorMatrix()
    applicableWheels.forEach { wheel ->
        ColorWheelEngine.buildColorMatrix(wheel.colorWheel)?.let(result::postConcat)
    }
    return result
}

private fun fittedBoundsFractions(
    containerWidth: Float,
    containerHeight: Float,
    contentAspectRatio: Float
): Pair<Float, Float> {
    if (containerWidth <= 0f || containerHeight <= 0f ||
        !contentAspectRatio.isFinite() || contentAspectRatio <= 0f
    ) {
        return 1f to 1f
    }
    val containerAspectRatio = containerWidth / containerHeight
    return if (contentAspectRatio >= containerAspectRatio) {
        1f to (containerAspectRatio / contentAspectRatio)
    } else {
        (contentAspectRatio / containerAspectRatio) to 1f
    }
}

// ═══════════════════════════════════════════════════════════════
//  ADVANCED EFFECTS — Full Preview Renderers
// ═══════════════════════════════════════════════════════════════

@Composable
private fun MirrorRenderedContent(
    mirror: com.moody.moodyvideoeditor.data.advanced.MirrorEffect,
    localTimeSec: Float,
    content: @Composable () -> Unit
) {
    val cX = KeyframeStore.sample(mirror.keyframes, "centerX", localTimeSec, mirror.centerX)
    val cY = KeyframeStore.sample(mirror.keyframes, "centerY", localTimeSec, mirror.centerY)
    val angle = KeyframeStore.sample(mirror.keyframes, "angleDeg", localTimeSec, mirror.angleDeg)
    val op = KeyframeStore.sample(mirror.keyframes, "opacity", localTimeSec, mirror.opacity)

    val angleNorm = ((angle % 360f) + 360f) % 360f
    val isHorizontal = angleNorm in 45f..135f || angleNorm in 225f..315f

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 1f - (op / 200f).coerceIn(0f, 0.5f)
                }
        ) { content() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (isHorizontal) scaleX = -1f else scaleY = -1f
                    alpha = (op / 100f).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(
                        pivotFractionX = cX.coerceIn(0f, 1f),
                        pivotFractionY = cY.coerceIn(0f, 1f)
                    )
                }
        ) { content() }
    }
}

@Composable
private fun RoundedCropWrapper(
    crop: com.moody.moodyvideoeditor.data.advanced.RoundedCropEffect,
    localTimeSec: Float,
    content: @Composable () -> Unit
) {
    val cr = KeyframeStore.sample(crop.keyframes, "cornerRadius", localTimeSec, crop.cornerRadius)
    val ct = KeyframeStore.sample(crop.keyframes, "cropTop", localTimeSec, crop.cropTop)
    val cb = KeyframeStore.sample(crop.keyframes, "cropBottom", localTimeSec, crop.cropBottom)
    val cl = KeyframeStore.sample(crop.keyframes, "cropLeft", localTimeSec, crop.cropLeft)
    val crR = KeyframeStore.sample(crop.keyframes, "cropRight", localTimeSec, crop.cropRight)
    val feather = KeyframeStore.sample(crop.keyframes, "feathering", localTimeSec, crop.feathering)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cr.dp))
            .padding(
                top = (ct * 100f).dp,
                bottom = (cb * 100f).dp,
                start = (cl * 100f).dp,
                end = (crR * 100f).dp
            )
            .then(
                if (feather > 0.5f) Modifier.blur(
                    radius = (feather / 10f).dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded
                ) else Modifier
            )
    ) { content() }
}

@Composable
private fun FourColorGradientOverlay(
    grad: com.moody.moodyvideoeditor.data.advanced.FourColorGradientEffect,
    localTimeSec: Float
) {
    val opacity = KeyframeStore.sample(
        grad.keyframes, "globalOpacity", localTimeSec, grad.globalOpacity
    )
    if (opacity < 0.5f) return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val tlR = ((grad.color1 shr 16) and 0xFF) / 255f
            val tlG = ((grad.color1 shr 8) and 0xFF) / 255f
            val tlB = (grad.color1 and 0xFF) / 255f
            val trR = ((grad.color2 shr 16) and 0xFF) / 255f
            val trG = ((grad.color2 shr 8) and 0xFF) / 255f
            val trB = (grad.color2 and 0xFF) / 255f
            val brR = ((grad.color3 shr 16) and 0xFF) / 255f
            val brG = ((grad.color3 shr 8) and 0xFF) / 255f
            val brB = (grad.color3 and 0xFF) / 255f
            val blR = ((grad.color4 shr 16) and 0xFF) / 255f
            val blG = ((grad.color4 shr 8) and 0xFF) / 255f
            val blB = (grad.color4 and 0xFF) / 255f

            val stepX = (size.width / 96f).coerceAtLeast(1f)
            val stepY = (size.height / 96f).coerceAtLeast(1f)
            val alphaVal = (opacity / 100f).coerceIn(0f, 1f)

            var y = 0f
            while (y < size.height) {
                var x = 0f
                while (x < size.width) {
                    val fx = (x / size.width).coerceIn(0f, 1f)
                    val fy = (y / size.height).coerceIn(0f, 1f)
                    val wTL = (1f - fx) * (1f - fy)
                    val wTR = fx * (1f - fy)
                    val wBL = (1f - fx) * fy
                    val wBR = fx * fy
                    val r = tlR * wTL + trR * wTR + blR * wBL + brR * wBR
                    val g = tlG * wTL + trG * wTR + blG * wBL + brG * wBR
                    val b = tlB * wTL + trB * wTR + blB * wBL + brB * wBR
                    drawRect(
                        color = Color(r, g, b, alphaVal),
                        topLeft = Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(stepX, stepY)
                    )
                    x += stepX
                }
                y += stepY
            }
        }
    }
}

@Composable
private fun ChromaticAberrationWrapper(
    ca: com.moody.moodyvideoeditor.data.advanced.ChromaticAberrationEffect,
    localTimeSec: Float,
    content: @Composable () -> Unit
) {
    val rx = KeyframeStore.sample(ca.keyframes, "redShiftX", localTimeSec, ca.redShiftX)
    val ry = KeyframeStore.sample(ca.keyframes, "redShiftY", localTimeSec, ca.redShiftY)
    val bx = KeyframeStore.sample(ca.keyframes, "blueShiftX", localTimeSec, ca.blueShiftX)
    val by = KeyframeStore.sample(ca.keyframes, "blueShiftY", localTimeSec, ca.blueShiftY)

    val hasShift = abs(rx) > 0.1f || abs(ry) > 0.1f || abs(bx) > 0.1f || abs(by) > 0.1f
    if (!hasShift) {
        content()
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()

        if (abs(rx) > 0.1f || abs(ry) > 0.1f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = rx * 3f
                        translationY = ry * 3f
                        alpha = 0.25f
                        blendMode = BlendMode.Screen
                    }
            ) { content() }
        }

        if (abs(bx) > 0.1f || abs(by) > 0.1f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = -bx * 3f
                        translationY = -by * 3f
                        alpha = 0.25f
                        blendMode = BlendMode.Screen
                    }
            ) { content() }
        }
    }
}

@Composable
private fun RoughenEdgesOverlay(
    r: com.moody.moodyvideoeditor.data.advanced.RoughenEdgesEffect,
    localTimeSec: Float,
    content: @Composable () -> Unit
) {
    val bw = KeyframeStore.sample(r.keyframes, "borderWidth", localTimeSec, r.borderWidth)
    if (bw < 0.5f) {
        content()
        return
    }

    val evolution = KeyframeStore.sample(r.keyframes, "evolution", localTimeSec, r.evolution)

    Box(modifier = Modifier.fillMaxSize()) {
        content()

        Canvas(modifier = Modifier.fillMaxSize()) {
            val steps = 60
            val seed = (evolution * 10f).toInt()
            val rnd = java.util.Random(seed.toLong())
            val barWidth = (bw / 2f).coerceIn(1f, 40f)

            for (i in 0..steps) {
                val x = (i.toFloat() / steps) * size.width
                val noise = (rnd.nextFloat() - 0.5f) * (bw * 0.5f)
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(
                        size.width / steps + 1f, barWidth + noise
                    )
                )
            }
            for (i in 0..steps) {
                val x = (i.toFloat() / steps) * size.width
                val noise = (rnd.nextFloat() - 0.5f) * (bw * 0.5f)
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = Offset(x, size.height - barWidth - noise),
                    size = androidx.compose.ui.geometry.Size(
                        size.width / steps + 1f, barWidth + noise
                    )
                )
            }
            for (i in 0..steps) {
                val y = (i.toFloat() / steps) * size.height
                val noise = (rnd.nextFloat() - 0.5f) * (bw * 0.5f)
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = Offset(0f, y),
                    size = androidx.compose.ui.geometry.Size(
                        barWidth + noise, size.height / steps + 1f
                    )
                )
            }
            for (i in 0..steps) {
                val y = (i.toFloat() / steps) * size.height
                val noise = (rnd.nextFloat() - 0.5f) * (bw * 0.5f)
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = Offset(size.width - barWidth - noise, y),
                    size = androidx.compose.ui.geometry.Size(
                        barWidth + noise, size.height / steps + 1f
                    )
                )
            }
        }
    }
}

@Composable
private fun MotionBlurOverlay(
    m: com.moody.moodyvideoeditor.data.advanced.MotionBlurEffect,
    localTimeSec: Float,
    content: @Composable () -> Unit
) {
    val shutter = KeyframeStore.sample(
        m.keyframes, "shutterAngle", localTimeSec, m.shutterAngle
    )
    val intensity = KeyframeStore.sample(
        m.keyframes, "intensity", localTimeSec, m.intensity
    )

    if (shutter < 10f || intensity < 0.1f) {
        content()
        return
    }

    val blurAmount = ((shutter / 720f) * 6f * intensity).coerceIn(0f, 12f)

    Box(modifier = Modifier.fillMaxSize()) {
        repeat(3) { i ->
            val factor = (i + 1) / 4f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 0.15f * (1f - factor)
                        scaleX = 1f + 0.03f * factor * intensity
                        scaleY = 1f + 0.03f * factor * intensity
                    }
                    .blur(
                        radius = (blurAmount * factor).dp,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                    )
            ) { content() }
        }

        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun TurbulentDisplaceWrapper(
    t: com.moody.moodyvideoeditor.data.advanced.TurbulentDisplaceEffect,
    localTimeSec: Float,
    content: @Composable () -> Unit
) {
    val amount = KeyframeStore.sample(t.keyframes, "amount", localTimeSec, t.amount)
    val speed = KeyframeStore.sample(
        t.keyframes, "evolutionSpeed", localTimeSec, t.evolutionSpeed
    )
    val offX = KeyframeStore.sample(t.keyframes, "offsetX", localTimeSec, t.offsetX)
    val offY = KeyframeStore.sample(t.keyframes, "offsetY", localTimeSec, t.offsetY)

    if (amount < 1f) {
        content()
        return
    }

    val time = localTimeSec * speed
    Box(modifier = Modifier.fillMaxSize()) {
        content()

        Canvas(modifier = Modifier.fillMaxSize()) {
            val bands = (amount / 5f).toInt().coerceIn(2, 40)
            val amp = amount * 0.05f
            repeat(bands) { i ->
                val yBase = (i.toFloat() / bands) * size.height
                val phase = offX * 6f + offY * 6f + time * 3f + i * 0.5f
                val offset = sin(phase.toDouble()).toFloat() * amp
                drawRect(
                    color = Color.Transparent,
                    topLeft = Offset(offset, yBase),
                    size = androidx.compose.ui.geometry.Size(size.width, 1f)
                )
            }
        }
    }
}

@Composable
private fun DropShadowBehind(
    effect: com.moody.moodyvideoeditor.data.advanced.DropShadowEffect,
    localTimeSec: Float,
    content: @Composable () -> Unit
) {
    val op = KeyframeStore.sample(effect.keyframes, "opacity", localTimeSec, effect.opacity)
    val dist = KeyframeStore.sample(effect.keyframes, "distance", localTimeSec, effect.distance)
    val angle = KeyframeStore.sample(
        effect.keyframes, "directionAngle", localTimeSec, effect.directionAngle
    )
    val soft = KeyframeStore.sample(
        effect.keyframes, "blurSoftness", localTimeSec, effect.blurSoftness
    )

    val rad = Math.toRadians(angle.toDouble())
    val dx = (cos(rad) * dist).toFloat()
    val dy = (sin(rad) * dist).toFloat()

    Box(modifier = Modifier.fillMaxSize()) {
        // Shadow behind
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = dx
                    translationY = dy
                    alpha = (op / 100f).coerceIn(0f, 1f)
                }
                .blur(
                    radius = soft.dp.coerceAtLeast(0.dp),
                    edgeTreatment = BlurredEdgeTreatment.Unbounded
                )
        ) { content() }

        // Original on top
        content()
    }
}

// ═══════════════════════════════════════════════════════════════
//  APPLY ADVANCED EFFECTS — Composed modifier chain
//  Returns a wrapped composable that renders all effects.
// ═══════════════════════════════════════════════════════════════

@Composable
private fun RenderAdvancedEffects(
    clip: EditorClip,
    localTimeSec: Float,
    content: @Composable () -> Unit
) {
    if (clip.advancedEffects.isEmpty()) {
        content()
        return
    }

    // Build chain in reverse order (last applied wraps first)
    var wrapped: @Composable () -> Unit = content
    val fxList = clip.advancedEffects
    for (idx in fxList.indices.reversed()) {
        val fx = fxList[idx]
        val prev = wrapped
        wrapped = {
            when (fx.type) {
                AdvancedEffectType.MIRROR -> {
                    val m = fx.mirror
                    if (m != null) MirrorRenderedContent(m, localTimeSec) { prev() }
                    else prev()
                }

                AdvancedEffectType.ROUNDED_CROP -> {
                    val r = fx.roundedCrop
                    if (r != null) RoundedCropWrapper(r, localTimeSec) { prev() }
                    else prev()
                }

                AdvancedEffectType.CHROMATIC_ABERRATION -> {
                    val c = fx.chromaticAberration
                    if (c != null) ChromaticAberrationWrapper(c, localTimeSec) { prev() }
                    else prev()
                }

                AdvancedEffectType.ROUGHEN_EDGES -> {
                    val r = fx.roughenEdges
                    if (r != null) RoughenEdgesOverlay(r, localTimeSec) { prev() }
                    else prev()
                }

                AdvancedEffectType.MOTION_BLUR -> {
                    val m = fx.motionBlur
                    if (m != null) MotionBlurOverlay(m, localTimeSec) { prev() }
                    else prev()
                }

                AdvancedEffectType.TURBULENT_DISPLACE -> {
                    val t = fx.turbulentDisplace
                    if (t != null) TurbulentDisplaceWrapper(t, localTimeSec) { prev() }
                    else prev()
                }

                AdvancedEffectType.DROP_SHADOW -> {
                    val s = fx.dropShadow
                    if (s != null) DropShadowBehind(s, localTimeSec) { prev() }
                    else prev()
                }

                AdvancedEffectType.GAUSSIAN_BLUR -> {
                    val b = fx.gaussianBlur
                    if (b != null) {
                        val radius = KeyframeStore.sample(
                            b.keyframes, "blurriness", localTimeSec, b.blurriness
                        )
                        if (radius > 0.5f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(
                                        radiusX = radius.dp,
                                        radiusY = if (b.dimension == BlurDimension.VERTICAL)
                                            0.dp else radius.dp,
                                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                                    )
                            ) { prev() }
                        } else prev()
                    } else prev()
                }

                AdvancedEffectType.FOUR_COLOR_GRADIENT -> {
                    val g = fx.fourColorGradient
                    if (g != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            prev()
                            FourColorGradientOverlay(g, localTimeSec)
                        }
                    } else prev()
                }

                AdvancedEffectType.TRACK_MATTE -> prev()
            }
        }
    }
    wrapped()
}

// ═══════════════════════════════════════════════════════════════
//  MAIN PREVIEW CANVAS
// ═══════════════════════════════════════════════════════════════

@OptIn(UnstableApi::class)
@Composable
fun PreviewCanvas(
    exoPlayer: ExoPlayer,
    hasVideo: Boolean,
    rotation: Int,
    aspectMode: Int,
    clips: List<EditorClip>,
    currentPosMs: Long,
    isPlaying: Boolean = false,
    hiddenVisualTracks: Set<Int> = emptySet(),
    aspectRatioKey: String = "16:9",
    selectedClipId: String? = null,
    multiSelectedIds: Set<String> = emptySet(),
    previewFilters: FilterState? = null,
    previewEffectState: EffectState? = null,
    previewAdvancedEffect: AdvancedEffectState? = null,
    isDrawingMode: Boolean = false,
    activeBrushType: BrushType = BrushType.PEN,
    activeBrushColor: Long = 0xFFFF0000,
    activeBrushWidth: Float = 20f,
    activeBrushOpacity: Float = 1f,
    onBrushStrokeComplete: (BrushStroke) -> Unit = {},
    isMaskPenMode: Boolean = false,
    isMaskHandMode: Boolean = false,
    onMaskGestureStart: () -> Unit = {},
    onMaskGestureEnd: () -> Unit = {},
    onMaskPointAdd: (Float, Float) -> Unit = { _, _ -> },
    onMaskAnchorMove: (Int, Float, Float) -> Unit = { _, _, _ -> },
    onMaskHandleMove: (Int, Boolean, Float, Float) -> Unit = { _, _, _, _ -> },
    onMaskPointToggle: (Int) -> Unit = {},
    onMaskPointDelete: (Int) -> Unit = {},
    onMaskMove: (Float, Float) -> Unit = { _, _ -> },
    onMaskStateChanged: (MaskState) -> Unit = {},
    onClosePath: () -> Unit = {},
    onClipSelected: (String) -> Unit = {},
    onDeleteLayer: (String) -> Unit = {},
    onGroupGestureStart: () -> Unit = {},
    onGroupGestureEnd: () -> Unit = {},
    onGroupGesture: (String, Float, Float, Float, Float) -> Unit =
        { _, _, _, _, _ -> },
    onTextPositionChanged: (String, Float, Float) -> Unit = { _, _, _ -> },
    onTextTransformChanged: (String, Float, Float) -> Unit = { _, _, _ -> },
    onStickerPositionChanged: (String, Float, Float) -> Unit = { _, _, _ -> },
    onStickerTransformChanged: (String, Float, Float) -> Unit = { _, _, _ -> },
    onBrushPositionChanged: (String, Float, Float) -> Unit = { _, _, _ -> },
    onBrushTransformChanged: (String, Float, Float) -> Unit = { _, _, _ -> },
    onVisualizerPositionChanged: (String, Float, Float) -> Unit = { _, _, _ -> },
    onVisualizerTransformChanged: (String, Float, Float) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current

    val baseActiveClips = clips
        .filter {
            !it.isAudio &&
                    !it.isAdjustmentClip &&
                    !it.isEffectClip &&
                    !it.isFilterLayerClip &&
                    currentPosMs >= it.timelineStartMs &&
                    currentPosMs < it.timelineEndMs &&
                    !hiddenVisualTracks.contains(it.trackIndex)
        }
        .sortedWith(compareBy({ it.trackIndex }, { it.timelineStartMs }))

    // Inject previewAdvancedEffect on selected clip
    val activeClips = if (previewAdvancedEffect != null) {
        baseActiveClips.map { clip ->
            if (clip.id == selectedClipId) {
                clip.copy(
                    advancedEffects = clip.advancedEffects + previewAdvancedEffect
                )
            } else clip
        }
    } else baseActiveClips

    val topVideoClip = activeClips
        .filter { it.isVisualClip && !it.type.startsWith("image/") }
        .maxByOrNull { it.trackIndex }

    val activeVisual = activeClips.maxByOrNull { it.trackIndex }
    val videoTrackIdx = activeVisual?.trackIndex ?: 0

    val activeTransitionClip = remember(currentPosMs, clips) {
        clips.firstOrNull { c ->
            !c.isAudio && c.transition != null && c.transition.isActive &&
                    currentPosMs >= c.timelineStartMs &&
                    currentPosMs < c.timelineStartMs + c.transition.durationMs
        }
    }

    val outgoingClip = remember(activeTransitionClip?.id, clips) {
        activeTransitionClip?.let { tc ->
            clips.filter {
                it.isVisualClip && it.trackIndex == tc.trackIndex &&
                        it.id != tc.id &&
                        it.timelineEndMs <= tc.timelineStartMs + 50L
            }.maxByOrNull { it.timelineEndMs }
        }
    }

    var outgoingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(outgoingClip?.id) {
        outgoingBitmap?.takeIf { !it.isRecycled }?.recycle()
        outgoingBitmap = null
        val oc = outgoingClip ?: return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) {
            try {
                if (oc.type.startsWith("image/")) {
                    android.graphics.BitmapFactory.decodeStream(
                        context.contentResolver.openInputStream(oc.uri)
                    )
                } else {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, oc.uri)
                    val timeUs = (oc.sourceEndMs - 33).coerceAtLeast(0L) * 1000L
                    val b = retriever.getFrameAtTime(
                        timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    retriever.release()
                    b
                }
            } catch (e: Exception) {
                Log.e("PREVIEW_TRANS", "Outgoing bitmap load failed", e)
                null
            }
        }
        outgoingBitmap = bmp
    }

    DisposableEffect(Unit) {
        onDispose { outgoingBitmap?.takeIf { !it.isRecycled }?.recycle() }
    }

    val selectedClip = clips.firstOrNull { it.id == selectedClipId }
    val maskToRender: MaskState? = if ((isMaskPenMode || isMaskHandMode) && selectedClip != null) {
        val timeSec = ((currentPosMs - selectedClip.timelineStartMs) / 1000f)
            .coerceAtLeast(0f)
        MaskEngine.sampleAt(selectedClip.mask, timeSec)
    } else null

    val activeAdjustment = clips
        .filter {
            it.isAdjustmentClip &&
                    currentPosMs >= it.timelineStartMs &&
                    currentPosMs < it.timelineEndMs &&
                    !hiddenVisualTracks.contains(it.trackIndex)
        }
        .maxByOrNull { it.trackIndex }
        ?.adjustments
    val activeColorWheels = clips.filter {
        it.isAdjustmentClip &&
                it.colorWheel.hasAnyChange &&
                currentPosMs >= it.timelineStartMs &&
                currentPosMs < it.timelineEndMs &&
                !hiddenVisualTracks.contains(it.trackIndex)
    }.sortedBy { it.trackIndex }

    val activeEffects = EffectsEngine.getEffectsAbove(clips, currentPosMs, videoTrackIdx)
    val timeSec = currentPosMs / 1000f

    val motionFrames = activeEffects.mapNotNull { clip ->
        clip.effectState?.motion?.let { EffectsEngine.computeMotion(it, timeSec) }
    }.toMutableList()

    previewEffectState?.motion?.let { m ->
        motionFrames.add(EffectsEngine.computeMotion(m, timeSec))
    }
    val combinedMotion = EffectsEngine.combineMotions(motionFrames)

    val filterList = activeEffects.mapNotNull { it.effectState?.filters }.toMutableList()
    previewEffectState?.filters?.let { filterList.add(it) }

    val combinedFilter = if (filterList.isNotEmpty())
        EffectsEngine.combineFilters(filterList) else null

    val previewOverlays = previewEffectState?.overlay?.let {
        listOf(OverlayState(type = it.type, intensity = it.intensity, color = it.color))
    } ?: emptyList()
    val allOverlays = EffectsEngine.collectActiveOverlays(clips, currentPosMs, videoTrackIdx)
        .toMutableList()
        .apply { addAll(previewOverlays) }

    val activeFilterLayer = clips
        .filter {
            it.isFilterLayerClip &&
                    currentPosMs >= it.timelineStartMs &&
                    currentPosMs < it.timelineEndMs &&
                    !hiddenVisualTracks.contains(it.trackIndex)
        }
        .maxByOrNull { it.trackIndex }

    val hasAdjustments = activeAdjustment?.let {
        ColorMatrixBuilder.hasRealTimeAdjustments(it)
    } ?: false
    val hasFilters = EffectsEngine.hasColorEffect(combinedFilter)
    val applyMatrix = hasAdjustments || hasFilters ||
            activeFilterLayer != null || previewFilters != null

    val combinedMatrix = remember(
        activeAdjustment, combinedFilter, activeFilterLayer?.id, previewFilters
    ) {
        val cm = android.graphics.ColorMatrix()
        if (hasAdjustments && activeAdjustment != null) {
            cm.postConcat(ColorMatrixBuilder.build(activeAdjustment))
        }
        if (hasFilters && combinedFilter != null) {
            cm.postConcat(EffectsEngine.buildColorMatrix(combinedFilter))
        }
        activeFilterLayer?.let { layer ->
            val cfv = ColorFilterValues(
                brightness = layer.filters.brightness,
                contrast = layer.filters.contrast,
                saturation = layer.filters.saturation,
                hue = layer.filters.hue,
                grayscale = layer.filters.grayscale,
                sepia = layer.filters.sepia,
                invert = layer.filters.invert,
                blur = layer.filters.blur,
                opacity = layer.filters.opacity
            )
            if (EffectsEngine.hasColorEffect(cfv)) {
                cm.postConcat(EffectsEngine.buildColorMatrix(cfv))
            }
        }
        previewFilters?.let { pf ->
            val cfv = ColorFilterValues(
                brightness = pf.brightness,
                contrast = pf.contrast,
                saturation = pf.saturation,
                hue = pf.hue,
                grayscale = pf.grayscale,
                sepia = pf.sepia,
                invert = pf.invert,
                blur = pf.blur,
                opacity = pf.opacity
            )
            if (EffectsEngine.hasColorEffect(cfv)) {
                cm.postConcat(EffectsEngine.buildColorMatrix(cfv))
            }
        }
        cm
    }

    val opacityAlpha = EffectsEngine.opacityAlpha(combinedFilter)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
        contentAlignment = Alignment.Center
    ) {
        val windowW = maxWidth.value
        val windowH = maxHeight.value
        if (windowW <= 0f || windowH <= 0f) return@BoxWithConstraints

        val targetRatio = RatioHelper.ratioValue(aspectRatioKey)
        val windowRatio = windowW / windowH

        val canvasW: Float
        val canvasH: Float
        if (targetRatio >= windowRatio) {
            canvasW = windowW
            canvasH = windowW / targetRatio
        } else {
            canvasH = windowH
            canvasW = windowH * targetRatio
        }

        val sideBar = ((windowW - canvasW) / 2f).coerceAtLeast(0f)
        val topBar = ((windowH - canvasH) / 2f).coerceAtLeast(0f)

        Box(
            modifier = Modifier
                .width(canvasW.dp)
                .height(canvasH.dp)
                .align(Alignment.Center)
                .clipToBounds()
                .background(Color.Black)
        ) {
            activeClips.forEach { clip ->
                when {
                    // ─── VIDEO ───
                    clip.isVisualClip && !clip.type.startsWith("image/") -> {
                        if (clip.id != topVideoClip?.id) return@forEach
                        val videoLocalSec =
                            ((currentPosMs - clip.timelineStartMs) / 1000f)
                                .coerceAtLeast(0f)
                        val videoTransform2 = TransformApplier.resolveLive(clip, videoLocalSec)
                        val videoSize = exoPlayer.videoSize
                        val rawVideoAspect = if (videoSize.width > 0 && videoSize.height > 0) {
                            videoSize.width * videoSize.pixelWidthHeightRatio /
                                    videoSize.height
                        } else {
                            canvasW / canvasH
                        }
                        val videoAspect = if (
                            videoSize.unappliedRotationDegrees % 180 != 0
                        ) {
                            1f / rawVideoAspect
                        } else {
                            rawVideoAspect
                        }
                        val videoBounds = fittedBoundsFractions(
                            canvasW, canvasH, videoAspect
                        )
                        val useExactColorWheels = Build.VERSION.SDK_INT >= 33
                        val videoWheelStates = remember(clip.colorWheel, activeColorWheels) {
                            colorWheelStatesForClip(clip, activeColorWheels)
                        }
                        val videoWheelEffect = remember(videoWheelStates) {
                            if (useExactColorWheels) {
                                ColorWheelPreviewFilter.createRenderEffect(videoWheelStates)
                            } else null
                        }
                        val useExactVideoColorWheels =
                            useExactColorWheels &&
                                    (videoWheelStates.isEmpty() || videoWheelEffect != null)
                        val videoWheelMatrix = remember(
                            clip.id, activeColorWheels, useExactVideoColorWheels
                        ) {
                            if (useExactVideoColorWheels) null
                            else colorWheelMatrixForClip(clip, activeColorWheels)
                        }

                        MaskedClipContent(mask = maskAtClipTime(clip, currentPosMs)) {
                            RenderAdvancedEffects(clip, videoLocalSec) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            val w = size.width
                                            val h = size.height
                                            val cropSx = 1f /
                                                    (1f - videoTransform2.cropL -
                                                            videoTransform2.cropR)
                                                        .coerceAtLeast(0.05f)
                                            val cropSy = 1f /
                                                    (1f - videoTransform2.cropT -
                                                            videoTransform2.cropB)
                                                        .coerceAtLeast(0.05f)
                                            val cropTx =
                                                -(videoTransform2.cropL -
                                                        videoTransform2.cropR) / 2f * w
                                            val cropTy =
                                                -(videoTransform2.cropT -
                                                        videoTransform2.cropB) / 2f * h
                                            val posTx =
                                                (videoTransform2.x - 50f) / 100f * w
                                            val posTy =
                                                (videoTransform2.y - 50f) / 100f * h

                                            val transT: Transform2D =
                                                if (activeTransitionClip != null) {
                                                    val st =
                                                        activeTransitionClip.transition!!
                                                    val prog = (
                                                            (currentPosMs -
                                                                    activeTransitionClip
                                                                        .timelineStartMs)
                                                                .toFloat() /
                                                                    st.durationMs
                                                                        .coerceAtLeast(1L)
                                                            ).coerceIn(0f, 1f)
                                                    TransitionRenderer
                                                        .getIncomingTransform(
                                                            st.key, prog, w, h
                                                        )
                                                } else Transform2D()

                                            translationX = posTx + cropTx +
                                                    combinedMotion.tx + transT.tx
                                            translationY = posTy + cropTy +
                                                    combinedMotion.ty + transT.ty
                                            scaleX = (videoTransform2.scale / 100f) *
                                                    cropSx * combinedMotion.scale *
                                                    transT.scaleX
                                            scaleY = (videoTransform2.scale / 100f) *
                                                    cropSy * combinedMotion.scale *
                                                    transT.scaleY
                                            rotationZ = videoTransform2.rotation +
                                                    combinedMotion.rotation +
                                                    transT.rotZ
                                            transformOrigin = TransformOrigin(
                                                pivotFractionX =
                                                    videoTransform2.anchorX / 100f,
                                                pivotFractionY =
                                                    videoTransform2.anchorY / 100f
                                            )
                                            alpha = opacityAlpha * transT.alpha
                                            this.clip = true
                                        }
                                ) {
                                    AndroidView(
                                        factory = { ctx ->
                                            LayoutInflater.from(ctx)
                                                .inflate(
                                                    com.moody.moodyvideoeditor.R.layout
                                                        .view_player,
                                                    null
                                                ) as PlayerView
                                        },
                                        update = { view ->
                                            view.player = exoPlayer
                                            view.resizeMode =
                                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                                            view.rotation = rotation.toFloat()
                                            val sv = view.videoSurfaceView
                                            if (sv is TextureView) {
                                                val videoCm = buildClipMatrix(
                                                    clip,
                                                    combinedMatrix,
                                                    applyMatrix,
                                                    videoWheelMatrix,
                                                    useExactVideoColorWheels
                                                )
                                                if (videoCm != null) {
                                                    val paint = Paint().apply {
                                                        colorFilter =
                                                            ColorMatrixColorFilter(videoCm)
                                                    }
                                                    sv.setLayerType(
                                                        View.LAYER_TYPE_HARDWARE, paint
                                                    )
                                                } else {
                                                    sv.setLayerType(
                                                        View.LAYER_TYPE_HARDWARE, null
                                                    )
                                                }
                                                if (Build.VERSION.SDK_INT >= 31) {
                                                    view.setRenderEffect(
                                                        if (Build.VERSION.SDK_INT >= 33)
                                                            videoWheelEffect
                                                        else null
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (clip.id == selectedClipId && !isDrawingMode) {
                                        LayerTransformHandles(
                                            clipId = clip.id,
                                            scale = videoTransform2.scale,
                                            rotation = videoTransform2.rotation,
                                            widthFraction = videoBounds.first,
                                            heightFraction = videoBounds.second,
                                            positionX = videoTransform2.x,
                                            positionY = videoTransform2.y,
                                            onPositionChanged = { x, y ->
                                                onGroupGesture(
                                                    clip.id, x, y,
                                                    videoTransform2.scale,
                                                    videoTransform2.rotation
                                                )
                                            },
                                            onGestureStart = onGroupGestureStart,
                                            onGestureEnd = onGroupGestureEnd,
                                            onTransformChanged = { scale, rotation ->
                                                onGroupGesture(
                                                    clip.id,
                                                    videoTransform2.x,
                                                    videoTransform2.y,
                                                    scale,
                                                    rotation
                                                )
                                            },
                                            onDelete = { onDeleteLayer(clip.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ─── IMAGE ───
                    clip.isVisualClip && clip.type.startsWith("image/") -> {
                        val imgLocalSec =
                            ((currentPosMs - clip.timelineStartMs) / 1000f)
                                .coerceAtLeast(0f)
                        val imgTransform = TransformApplier.resolveLive(clip, imgLocalSec)

                        val isSelected = clip.id == selectedClipId
                        val isMulti = clip.id in multiSelectedIds
                        val borderColor = when {
                            isSelected -> Color(0xFF60EFFF)
                            isMulti -> Color(0xFFFFD166)
                            else -> Color.Transparent
                        }

                        MaskedClipContent(mask = maskAtClipTime(clip, currentPosMs)) {
                            RenderAdvancedEffects(clip, imgLocalSec) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .graphicsLayer {
                                            val w = size.width
                                            val h = size.height
                                            val cropSx = 1f /
                                                    (1f - imgTransform.cropL -
                                                            imgTransform.cropR)
                                                        .coerceAtLeast(0.05f)
                                            val cropSy = 1f /
                                                    (1f - imgTransform.cropT -
                                                            imgTransform.cropB)
                                                        .coerceAtLeast(0.05f)
                                            val cropTx =
                                                -(imgTransform.cropL -
                                                        imgTransform.cropR) / 2f * w
                                            val cropTy =
                                                -(imgTransform.cropT -
                                                        imgTransform.cropB) / 2f * h
                                            val posTx =
                                                (imgTransform.x - 50f) / 100f * w
                                            val posTy =
                                                (imgTransform.y - 50f) / 100f * h

                                            val transT: Transform2D =
                                                if (activeTransitionClip != null &&
                                                    activeTransitionClip.id == clip.id
                                                ) {
                                                    val st = activeTransitionClip.transition!!
                                                    val prog = (
                                                            (currentPosMs -
                                                                    activeTransitionClip
                                                                        .timelineStartMs)
                                                                .toFloat() /
                                                                    st.durationMs
                                                                        .coerceAtLeast(1L)
                                                            ).coerceIn(0f, 1f)
                                                    TransitionRenderer
                                                        .getIncomingTransform(
                                                            st.key, prog, w, h
                                                        )
                                                } else Transform2D()

                                            translationX = posTx + cropTx +
                                                    combinedMotion.tx + transT.tx
                                            translationY = posTy + cropTy +
                                                    combinedMotion.ty + transT.ty
                                            scaleX = (imgTransform.scale / 100f) *
                                                    cropSx * combinedMotion.scale *
                                                    transT.scaleX
                                            scaleY = (imgTransform.scale / 100f) *
                                                    cropSy * combinedMotion.scale *
                                                    transT.scaleY
                                            rotationZ = imgTransform.rotation +
                                                    combinedMotion.rotation +
                                                    transT.rotZ
                                            transformOrigin = TransformOrigin(
                                                pivotFractionX = imgTransform.anchorX / 100f,
                                                pivotFractionY = imgTransform.anchorY / 100f
                                            )
                                            alpha = opacityAlpha * transT.alpha
                                            this.clip = true
                                        }
                                        .then(
                                            if (isMulti) Modifier.border(
                                                width = 1.dp, color = borderColor,
                                                shape = RoundedCornerShape(4.dp)
                                            ) else Modifier
                                        )
                                        .pointerInput(clip.id, isSelected, isMulti, isDrawingMode) {
                                            if (isDrawingMode) return@pointerInput
                                            awaitEachGesture {
                                                awaitFirstDown(requireUnconsumed = true)
                                                onGroupGestureStart()

                                                val baseX = imgTransform.x
                                                val baseY = imgTransform.y
                                                val baseScale = imgTransform.scale
                                                val baseRot = imgTransform.rotation
                                                var accumPanX = 0f
                                                var accumPanY = 0f
                                                var accumZoom = 1f
                                                var accumRot = 0f
                                                var lastDist = 0f
                                                var lastAngle = 0f
                                                var hasMulti = false

                                                if (!isSelected && !isMulti) onClipSelected(clip.id)

                                                var continueGesture = true
                                                while (continueGesture) {
                                                    val event = awaitPointerEvent()
                                                    val pressed =
                                                        event.changes.filter { it.pressed }
                                                    if (pressed.isEmpty()) {
                                                        continueGesture = false
                                                    } else {
                                                        if (pressed.size == 1) {
                                                            val ch = pressed.first()
                                                            val pan =
                                                                ch.position - ch.previousPosition
                                                            accumPanX += pan.x
                                                            accumPanY += pan.y
                                                            ch.consume()
                                                        } else if (pressed.size >= 2) {
                                                            val c1 = pressed[0]
                                                            val c2 = pressed[1]
                                                            val d = c1.position - c2.position
                                                            val dist = sqrt(d.x * d.x + d.y * d.y)
                                                            val angle = atan2(d.y, d.x)
                                                            if (hasMulti && lastDist > 1f) {
                                                                accumZoom *= dist / lastDist
                                                                accumRot += Math.toDegrees(
                                                                    normalizeAngle(angle - lastAngle)
                                                                        .toDouble()
                                                                ).toFloat()
                                                            }
                                                            lastDist = dist
                                                            lastAngle = angle
                                                            hasMulti = true
                                                            c1.consume()
                                                            c2.consume()
                                                        }
                                                        val newX =
                                                            (baseX + accumPanX / size.width * 100f)
                                                                .coerceIn(0f, 100f)
                                                        val newY =
                                                            (baseY + accumPanY / size.height * 100f)
                                                                .coerceIn(0f, 100f)
                                                        val newScale = (baseScale * accumZoom)
                                                            .coerceIn(10f, 500f)
                                                        val newRot = baseRot + accumRot

                                                        onGroupGesture(
                                                            clip.id, newX, newY,
                                                            newScale, newRot
                                                        )
                                                    }
                                                }
                                                onGroupGestureEnd()
                                            }
                                        }
                                        .pointerInput(clip.id, isMulti, isDrawingMode) {
                                            if (isDrawingMode) return@pointerInput
                                            detectTapGestures {
                                                if (!isMulti) onClipSelected(clip.id)
                                            }
                                        }
                                ) {
                                    val imageRequest = remember(clip.uri) {
                                        ImageRequest.Builder(context)
                                            .data(clip.uri)
                                            .decoderFactory(BitmapFactoryDecoder.Factory())
                                            .allowHardware(false)
                                            .crossfade(false)
                                            .build()
                                    }
                                    val imgPainter = rememberAsyncImagePainter(imageRequest)
                                    val imageIntrinsicSize = imgPainter.intrinsicSize
                                    val imageAspect = if (
                                        imageIntrinsicSize.width > 0f &&
                                        imageIntrinsicSize.height > 0f
                                    ) {
                                        imageIntrinsicSize.width / imageIntrinsicSize.height
                                    } else {
                                        canvasW / canvasH
                                    }
                                    val imageBounds = fittedBoundsFractions(
                                        canvasW, canvasH, imageAspect
                                    )
                                    val useExactImageColorWheels = Build.VERSION.SDK_INT >= 33
                                    val imageWheelStates = remember(
                                        clip.colorWheel, activeColorWheels
                                    ) {
                                        colorWheelStatesForClip(clip, activeColorWheels)
                                    }
                                    val imageWheelEffect = remember(imageWheelStates) {
                                        if (useExactImageColorWheels) {
                                            ColorWheelPreviewFilter.createRenderEffect(
                                                imageWheelStates
                                            )?.asComposeRenderEffect()
                                        } else null
                                    }
                                    val useExactImageWheels =
                                        useExactImageColorWheels &&
                                                (imageWheelStates.isEmpty() ||
                                                        imageWheelEffect != null)
                                    val imgColorFilter = remember(
                                        clip.filters, clip.adjustments,
                                        combinedMatrix, applyMatrix, activeColorWheels, clip.id,
                                        useExactImageWheels
                                    ) {
                                        val cm = buildClipMatrix(
                                            clip,
                                            combinedMatrix,
                                            applyMatrix,
                                            if (useExactImageWheels) null
                                            else colorWheelMatrixForClip(clip, activeColorWheels),
                                            useExactImageWheels
                                        )
                                        if (cm == null) null
                                        else androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                            androidx.compose.ui.graphics.ColorMatrix(cm.array)
                                        )
                                    }
                                    Image(
                                        painter = imgPainter,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        colorFilter = imgColorFilter,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                renderEffect = imageWheelEffect
                                            }
                                    )
                                    if (isSelected && !isDrawingMode) {
                                        LayerTransformHandles(
                                            clipId = clip.id,
                                            scale = imgTransform.scale,
                                            rotation = imgTransform.rotation,
                                            widthFraction = imageBounds.first,
                                            heightFraction = imageBounds.second,
                                            positionX = imgTransform.x,
                                            positionY = imgTransform.y,
                                            onPositionChanged = { x, y ->
                                                onGroupGesture(
                                                    clip.id, x, y,
                                                    imgTransform.scale,
                                                    imgTransform.rotation
                                                )
                                            },
                                            onGestureStart = onGroupGestureStart,
                                            onGestureEnd = onGroupGestureEnd,
                                            onTransformChanged = { scale, rotation ->
                                                onGroupGesture(
                                                    clip.id,
                                                    imgTransform.x,
                                                    imgTransform.y,
                                                    scale,
                                                    rotation
                                                )
                                            },
                                            onDelete = { onDeleteLayer(clip.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ─── COLOR MATTE ───
                    clip.isColorMatteClip -> {
                        val localSec =
                            ((currentPosMs - clip.timelineStartMs) / 1000f)
                                .coerceAtLeast(0f)
                        val matteTransform = TransformApplier.resolveLive(clip, localSec)
                        val matteAlpha = (clip.filters.opacity / 100f)
                            .coerceIn(0f, 1f)

                        val isSel = clip.id == selectedClipId
                        val isMulti = clip.id in multiSelectedIds
                        val borderColor = when {
                            isSel -> Color(0xFF60EFFF)
                            isMulti -> Color(0xFFFFD166)
                            else -> Color.Transparent
                        }

                        MaskedClipContent(mask = maskAtClipTime(clip, currentPosMs)) {
                            RenderAdvancedEffects(clip, localSec) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .graphicsLayer {
                                            val w = size.width
                                            val h = size.height
                                            val cropSx = 1f /
                                                    (1f - matteTransform.cropL -
                                                            matteTransform.cropR)
                                                        .coerceAtLeast(0.05f)
                                            val cropSy = 1f /
                                                    (1f - matteTransform.cropT -
                                                            matteTransform.cropB)
                                                        .coerceAtLeast(0.05f)
                                            val cropTx =
                                                -(matteTransform.cropL -
                                                        matteTransform.cropR) / 2f * w
                                            val cropTy =
                                                -(matteTransform.cropT -
                                                        matteTransform.cropB) / 2f * h
                                            val posTx =
                                                (matteTransform.x - 50f) / 100f * w
                                            val posTy =
                                                (matteTransform.y - 50f) / 100f * h

                                            translationX = posTx + cropTx +
                                                    combinedMotion.tx
                                            translationY = posTy + cropTy +
                                                    combinedMotion.ty
                                            scaleX = (matteTransform.scale / 100f) *
                                                    cropSx * combinedMotion.scale
                                            scaleY = (matteTransform.scale / 100f) *
                                                    cropSy * combinedMotion.scale
                                            rotationZ = matteTransform.rotation +
                                                    combinedMotion.rotation
                                            transformOrigin = TransformOrigin(
                                                pivotFractionX =
                                                    matteTransform.anchorX / 100f,
                                                pivotFractionY =
                                                    matteTransform.anchorY / 100f
                                            )
                                            alpha = matteAlpha * opacityAlpha
                                        }
                                        .then(
                                            if (isSel || isMulti) Modifier.border(
                                                width = if (isSel) 1.5.dp else 1.dp,
                                                color = borderColor,
                                                shape = RoundedCornerShape(4.dp)
                                            ) else Modifier
                                        )
                                        .pointerInput(clip.id, isSel, isMulti, isDrawingMode) {
                                            if (isDrawingMode) return@pointerInput
                                            awaitEachGesture {
                                                awaitFirstDown(requireUnconsumed = true)
                                                onGroupGestureStart()

                                                val baseX = matteTransform.x
                                                val baseY = matteTransform.y
                                                val baseScale = matteTransform.scale
                                                val baseRot = matteTransform.rotation
                                                var accumPanX = 0f
                                                var accumPanY = 0f
                                                var accumZoom = 1f
                                                var accumRot = 0f
                                                var lastDist = 0f
                                                var lastAngle = 0f
                                                var hasMulti = false

                                                if (!isSel && !isMulti) onClipSelected(clip.id)

                                                var continueGesture = true
                                                while (continueGesture) {
                                                    val event = awaitPointerEvent()
                                                    val pressed =
                                                        event.changes.filter { it.pressed }
                                                    if (pressed.isEmpty()) {
                                                        continueGesture = false
                                                    } else {
                                                        if (pressed.size == 1) {
                                                            val ch = pressed.first()
                                                            val pan =
                                                                ch.position - ch.previousPosition
                                                            accumPanX += pan.x
                                                            accumPanY += pan.y
                                                            ch.consume()
                                                        } else if (pressed.size >= 2) {
                                                            val c1 = pressed[0]
                                                            val c2 = pressed[1]
                                                            val d = c1.position - c2.position
                                                            val dist = sqrt(d.x * d.x + d.y * d.y)
                                                            val angle = atan2(d.y, d.x)
                                                            if (hasMulti && lastDist > 1f) {
                                                                accumZoom *= dist / lastDist
                                                                accumRot += Math.toDegrees(
                                                                    normalizeAngle(angle - lastAngle)
                                                                        .toDouble()
                                                                ).toFloat()
                                                            }
                                                            lastDist = dist
                                                            lastAngle = angle
                                                            hasMulti = true
                                                            c1.consume()
                                                            c2.consume()
                                                        }
                                                        val newX =
                                                            (baseX + accumPanX / size.width * 100f)
                                                                .coerceIn(0f, 100f)
                                                        val newY =
                                                            (baseY + accumPanY / size.height * 100f)
                                                                .coerceIn(0f, 100f)
                                                        val newScale = (baseScale * accumZoom)
                                                            .coerceIn(10f, 500f)
                                                        val newRot = baseRot + accumRot

                                                        if (isMulti) {
                                                            onGroupGesture(
                                                                clip.id, newX, newY,
                                                                newScale, newRot
                                                            )
                                                        } else {
                                                            onBrushPositionChanged(
                                                                clip.id, newX, newY
                                                            )
                                                            onBrushTransformChanged(
                                                                clip.id, newScale, newRot
                                                            )
                                                        }
                                                    }
                                                }
                                                onGroupGestureEnd()
                                            }
                                        }
                                        .pointerInput(clip.id, isMulti, isDrawingMode) {
                                            if (isDrawingMode) return@pointerInput
                                            detectTapGestures {
                                                if (!isMulti) onClipSelected(clip.id)
                                            }
                                        }
                                ) {
                                    ColorMatteRenderer(
                                        style = clip.matteStyle,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    if (isSel && !isDrawingMode) {
                                        LayerTransformHandles(
                                            clipId = clip.id,
                                            scale = matteTransform.scale,
                                            rotation = matteTransform.rotation,
                                            positionX = matteTransform.x,
                                            positionY = matteTransform.y,
                                            onPositionChanged = { x, y ->
                                                onBrushPositionChanged(clip.id, x, y)
                                            },
                                            onGestureStart = onGroupGestureStart,
                                            onGestureEnd = onGroupGestureEnd,
                                            onTransformChanged = { s, r ->
                                                onBrushTransformChanged(clip.id, s, r)
                                            },
                                            onDelete = { onDeleteLayer(clip.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ─── TEXT ───
                    clip.isTextClip -> {
                        val st = clip.textState ?: return@forEach
                        val textLocalSec = ((currentPosMs - clip.timelineStartMs) / 1000f)
                            .coerceAtLeast(0f)
                        MaskedClipContent(mask = maskAtClipTime(clip, currentPosMs)) {
                            RenderAdvancedEffects(clip, textLocalSec) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    InteractiveTextOverlay(
                                        clip = clip,
                                        textState = st,
                                        canvasW = canvasW,
                                        canvasH = canvasH,
                                        currentPosMs = currentPosMs,
                                        isSelected = clip.id == selectedClipId,
                                        isMulti = clip.id in multiSelectedIds,
                                        isDrawingMode = isDrawingMode,
                                        onSelect = { onClipSelected(clip.id) },
                                        onGroupGestureStart = onGroupGestureStart,
                                        onGroupGestureEnd = onGroupGestureEnd,
                                        onGroupGesture = onGroupGesture,
                                        onPositionChanged = { x, y ->
                                            onTextPositionChanged(clip.id, x, y)
                                        },
                                        onTransformChanged = { s, r ->
                                            onTextTransformChanged(clip.id, s, r)
                                        },
                                        onDeleteLayer = { onDeleteLayer(clip.id) },
                                    )
                                }
                            }
                        }
                    }

                    // ─── STICKER ───
                    clip.isStickerClip -> {
                        val ss = clip.stickerState ?: return@forEach
                        val stickerLocalSec = ((currentPosMs - clip.timelineStartMs) / 1000f)
                            .coerceAtLeast(0f)
                        MaskedClipContent(mask = maskAtClipTime(clip, currentPosMs)) {
                            RenderAdvancedEffects(clip, stickerLocalSec) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    InteractiveStickerOverlay(
                                        clip = clip,
                                        stickerState = ss,
                                        canvasW = canvasW,
                                        canvasH = canvasH,
                                        currentPosMs = currentPosMs,
                                        isSelected = clip.id == selectedClipId,
                                        isMulti = clip.id in multiSelectedIds,
                                        isDrawingMode = isDrawingMode,
                                        onSelect = { onClipSelected(clip.id) },
                                        onGroupGestureStart = onGroupGestureStart,
                                        onGroupGestureEnd = onGroupGestureEnd,
                                        onGroupGesture = onGroupGesture,
                                        onPositionChanged = { x, y ->
                                            onStickerPositionChanged(clip.id, x, y)
                                        },
                                        onTransformChanged = { s, r ->
                                            onStickerTransformChanged(clip.id, s, r)
                                        },
                                        onDeleteLayer = { onDeleteLayer(clip.id) },
                                    )
                                }
                            }
                        }
                    }

                    // ─── VISUALIZER ───
                    clip.isVisualizerClip -> {
                        val vs = clip.visualizer ?: return@forEach
                        val vizLocalSec = ((currentPosMs - clip.timelineStartMs) / 1000f)
                            .coerceAtLeast(0f)

                        val minDimDp = minOf(canvasW, canvasH)
                        val radiusDp = vs.size * minDimDp
                        val diameterDp = radiusDp * 2f
                        val leftDp = vs.positionX * canvasW - radiusDp
                        val topDp = vs.positionY * canvasH - radiusDp

                        val isSel = clip.id == selectedClipId
                        val isMulti = clip.id in multiSelectedIds

                        Box(modifier = Modifier.fillMaxSize()) {
                            MaskedClipContent(
                                mask = maskAtClipTime(clip, currentPosMs)
                            ) {
                                RenderAdvancedEffects(clip, vizLocalSec) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        VisualizerOverlay(
                                            state = vs,
                                            visualizerClip = clip,
                                            allClips = clips,
                                            currentPosMs = currentPosMs,
                                            isPlaying = isPlaying,
                                            enabled = true,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .offset(x = leftDp.dp, y = topDp.dp)
                                    .size(diameterDp.dp)
                                    .then(
                                        if (isSel || isMulti) Modifier.border(
                                            width = if (isSel) 2.dp else 1.dp,
                                            color = if (isSel) Color(0xFF60EFFF)
                                            else Color(0xFFFFD166),
                                            shape = RoundedCornerShape(8.dp)
                                        ) else Modifier
                                    )
                                    .pointerInput(
                                        clip.id, isSel, isMulti, canvasW, canvasH, isDrawingMode
                                    ) {
                                        if (isDrawingMode) return@pointerInput
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = true)
                                            onGroupGestureStart()

                                            val baseX = vs.positionX * 100f
                                            val baseY = vs.positionY * 100f
                                            val baseScale = vs.size / 0.32f * 100f
                                            val baseRot = vs.rotation
                                            var accumPanX = 0f
                                            var accumPanY = 0f
                                            var accumZoom = 1f
                                            var accumRot = 0f
                                            var lastDist = 0f
                                            var lastAngle = 0f
                                            var hasMulti = false

                                            if (!isSel && !isMulti) onClipSelected(clip.id)

                                            var continueGesture = true
                                            while (continueGesture) {
                                                val event = awaitPointerEvent()
                                                val pressed = event.changes.filter { it.pressed }
                                                if (pressed.isEmpty()) {
                                                    continueGesture = false
                                                } else {
                                                    if (pressed.size == 1) {
                                                        val ch = pressed.first()
                                                        val pan = ch.position - ch.previousPosition
                                                        accumPanX += pan.x
                                                        accumPanY += pan.y
                                                        ch.consume()
                                                    } else if (pressed.size >= 2) {
                                                        val c1 = pressed[0]
                                                        val c2 = pressed[1]
                                                        val d = c1.position - c2.position
                                                        val dist = sqrt(d.x * d.x + d.y * d.y)
                                                        val angle = atan2(d.y, d.x)
                                                        if (hasMulti && lastDist > 1f) {
                                                            accumZoom *= dist / lastDist
                                                            accumRot += Math.toDegrees(
                                                                normalizeAngle(angle - lastAngle)
                                                                    .toDouble()
                                                            ).toFloat()
                                                        }
                                                        lastDist = dist
                                                        lastAngle = angle
                                                        hasMulti = true
                                                        c1.consume()
                                                        c2.consume()
                                                    }
                                                    val newX = (baseX + accumPanX /
                                                            size.width * 100f).coerceIn(0f, 100f)
                                                    val newY = (baseY + accumPanY /
                                                            size.height * 100f).coerceIn(0f, 100f)
                                                    val newScale = (baseScale * accumZoom)
                                                        .coerceIn(10f, 500f)
                                                    val newRot = baseRot + accumRot

                                                    if (isMulti) {
                                                        onGroupGesture(
                                                            clip.id, newX, newY,
                                                            newScale, newRot
                                                        )
                                                    } else {
                                                        onVisualizerPositionChanged(
                                                            clip.id, newX, newY
                                                        )
                                                        onVisualizerTransformChanged(
                                                            clip.id, newScale, newRot
                                                        )
                                                    }
                                                }
                                            }
                                            onGroupGestureEnd()
                                        }
                                    }
                                    .pointerInput(clip.id, isMulti, isDrawingMode) {
                                        if (isDrawingMode) return@pointerInput
                                        detectTapGestures {
                                            if (!isMulti) onClipSelected(clip.id)
                                        }
                                    }
                            ) {
                                if (isSel && !isDrawingMode) {
                                    LayerTransformHandles(
                                        clipId = clip.id,
                                        scale = (vs.size / 0.32f) * 100f,
                                        rotation = vs.rotation,
                                        positionX = vs.positionX * 100f,
                                        positionY = vs.positionY * 100f,
                                        onPositionChanged = { x, y ->
                                            onVisualizerPositionChanged(clip.id, x, y)
                                        },
                                        onGestureStart = onGroupGestureStart,
                                        onGestureEnd = onGroupGestureEnd,
                                        onTransformChanged = { scale, rotation ->
                                            onVisualizerTransformChanged(
                                                clip.id, scale, rotation
                                            )
                                        },
                                        onDelete = { onDeleteLayer(clip.id) },
                                    )
                                }
                            }
                        }
                    }

                    // ─── BRUSH ───
                    clip.isBrushClip -> {
                        val brushLocalSec =
                            ((currentPosMs - clip.timelineStartMs) / 1000f)
                                .coerceAtLeast(0f)
                        val brushTransform =
                            TransformApplier.resolveLive(clip, brushLocalSec)

                        val isSelected = clip.id == selectedClipId
                        val isMulti = clip.id in multiSelectedIds
                        val borderColor = when {
                            isSelected -> Color(0xFF60EFFF)
                            isMulti -> Color(0xFFFFD166)
                            else -> Color.Transparent
                        }

                        if (clip.brush.strokes.isEmpty()) {
                            if (isDrawingMode && clip.id == selectedClipId) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .graphicsLayer { alpha = 0.55f }
                                        .border(
                                            width = 2.dp,
                                            color = Color(0xFF7C3AED),
                                            shape = RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.layout.Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = androidx.compose.foundation
                                            .layout.Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("✏️", fontSize = 32.sp)
                                        Text(
                                            "Draw here",
                                            color = Color(0xFF7C3AED),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Drag your finger to draw a stroke",
                                            color = Color(0xFFAAAAAA),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                            return@forEach
                        }

                        MaskedClipContent(mask = maskAtClipTime(clip, currentPosMs)) {
                            RenderAdvancedEffects(clip, brushLocalSec) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .graphicsLayer {
                                            val w = size.width
                                            val h = size.height
                                            translationX =
                                                (brushTransform.x - 50f) / 100f * w +
                                                        combinedMotion.tx
                                            translationY =
                                                (brushTransform.y - 50f) / 100f * h +
                                                        combinedMotion.ty
                                            scaleX = (brushTransform.scale / 100f) *
                                                    combinedMotion.scale
                                            scaleY = (brushTransform.scale / 100f) *
                                                    combinedMotion.scale
                                            rotationZ = brushTransform.rotation +
                                                    combinedMotion.rotation
                                            alpha = opacityAlpha
                                            transformOrigin = TransformOrigin(
                                                pivotFractionX =
                                                    brushTransform.anchorX / 100f,
                                                pivotFractionY =
                                                    brushTransform.anchorY / 100f
                                            )
                                        }
                                        .then(
                                            if (isSelected || isMulti) Modifier.border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = borderColor,
                                                shape = RoundedCornerShape(4.dp)
                                            ) else Modifier
                                        )
                                        .pointerInput(
                                            clip.id, isSelected, isMulti, isDrawingMode
                                        ) {
                                            if (isDrawingMode) return@pointerInput
                                            awaitEachGesture {
                                                awaitFirstDown(requireUnconsumed = true)
                                                onGroupGestureStart()

                                                val baseX = brushTransform.x
                                                val baseY = brushTransform.y
                                                val baseScale = brushTransform.scale
                                                val baseRot = brushTransform.rotation
                                                var accumPanX = 0f
                                                var accumPanY = 0f
                                                var accumZoom = 1f
                                                var accumRot = 0f
                                                var lastDist = 0f
                                                var lastAngle = 0f
                                                var hasMulti = false

                                                if (!isSelected && !isMulti) onClipSelected(clip.id)

                                                var continueGesture = true
                                                while (continueGesture) {
                                                    val event = awaitPointerEvent()
                                                    val pressed =
                                                        event.changes.filter { it.pressed }
                                                    if (pressed.isEmpty()) {
                                                        continueGesture = false
                                                    } else {
                                                        if (pressed.size == 1) {
                                                            val ch = pressed.first()
                                                            val pan =
                                                                ch.position - ch.previousPosition
                                                            accumPanX += pan.x
                                                            accumPanY += pan.y
                                                            ch.consume()
                                                        } else if (pressed.size >= 2) {
                                                            val c1 = pressed[0]
                                                            val c2 = pressed[1]
                                                            val d = c1.position - c2.position
                                                            val dist = sqrt(d.x * d.x + d.y * d.y)
                                                            val angle = atan2(d.y, d.x)
                                                            if (hasMulti && lastDist > 1f) {
                                                                accumZoom *= dist / lastDist
                                                                accumRot += Math.toDegrees(
                                                                    normalizeAngle(angle - lastAngle)
                                                                        .toDouble()
                                                                ).toFloat()
                                                            }
                                                            lastDist = dist
                                                            lastAngle = angle
                                                            hasMulti = true
                                                            c1.consume()
                                                            c2.consume()
                                                        }
                                                        val newX =
                                                            (baseX + accumPanX / size.width * 100f)
                                                                .coerceIn(0f, 100f)
                                                        val newY =
                                                            (baseY + accumPanY / size.height * 100f)
                                                                .coerceIn(0f, 100f)
                                                        val newScale = (baseScale * accumZoom)
                                                            .coerceIn(10f, 500f)
                                                        val newRot = baseRot + accumRot

                                                        if (isMulti) {
                                                            onGroupGesture(
                                                                clip.id, newX, newY,
                                                                newScale, newRot
                                                            )
                                                        } else {
                                                            onBrushPositionChanged(
                                                                clip.id, newX, newY
                                                            )
                                                            onBrushTransformChanged(
                                                                clip.id, newScale, newRot
                                                            )
                                                        }
                                                    }
                                                }
                                                onGroupGestureEnd()
                                            }
                                        }
                                        .pointerInput(clip.id, isMulti, isDrawingMode) {
                                            if (isDrawingMode) return@pointerInput
                                            detectTapGestures {
                                                if (!isMulti) onClipSelected(clip.id)
                                            }
                                        }
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        clip.brush.strokes.forEach { stroke ->
                                            BrushEngine.drawStroke(
                                                scope = this,
                                                stroke = stroke,
                                                viewW = size.width,
                                                viewH = size.height,
                                                currentTimeMs =
                                                    (currentPosMs - clip.timelineStartMs)
                                                        .coerceAtLeast(0L)
                                            )
                                        }
                                    }
                                    if (isSelected && !isDrawingMode) {
                                        LayerTransformHandles(
                                            clipId = clip.id,
                                            scale = brushTransform.scale,
                                            rotation = brushTransform.rotation,
                                            positionX = brushTransform.x,
                                            positionY = brushTransform.y,
                                            onPositionChanged = { x, y ->
                                                onBrushPositionChanged(clip.id, x, y)
                                            },
                                            onGestureStart = onGroupGestureStart,
                                            onGestureEnd = onGroupGestureEnd,
                                            onTransformChanged = { scale, rotation ->
                                                onBrushTransformChanged(
                                                    clip.id, scale, rotation
                                                )
                                            },
                                            onDelete = { onDeleteLayer(clip.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (allOverlays.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    allOverlays.forEach { ov -> OverlayEngine.draw(this, timeSec, ov) }
                }
            }

            activeAdjustment?.let { adj ->
                if (adj.vignette > 0f) {
                    val alpha = (adj.vignette / 100f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = alpha * 0.9f)
                                    ),
                                    radius = 800f
                                )
                            )
                    )
                }
            }

            if (activeTransitionClip != null && outgoingBitmap != null) {
                val ts = activeTransitionClip.transition!!
                val progress = (
                        (currentPosMs - activeTransitionClip.timelineStartMs).toFloat() /
                                ts.durationMs.coerceAtLeast(1L)
                        ).coerceIn(0f, 1f)
                TransitionRenderer.Render(outgoingBitmap!!, ts.key, progress)
            }

            if ((isMaskPenMode || isMaskHandMode) && selectedClip != null) {
                MaskPenOverlay(
                    maskState = maskToRender ?: MaskState(type = MaskType.CUSTOM),
                    canvasW = canvasW,
                    canvasH = canvasH,
                    isHandMode = isMaskHandMode,
                    onGestureStart = onMaskGestureStart,
                    onGestureEnd = onMaskGestureEnd,
                    onTapAddPoint = onMaskPointAdd,
                    onAnchorMove = onMaskAnchorMove,
                    onHandleMove = onMaskHandleMove,
                    onTogglePoint = onMaskPointToggle,
                    onDeletePoint = onMaskPointDelete,
                    onClosePath = onClosePath,
                    onMaskMove = onMaskMove,
                    onMaskStateChanged = onMaskStateChanged
                )
            }

            if (isDrawingMode && !isMaskPenMode && !isMaskHandMode) {
                val brushClip = clips.firstOrNull {
                    it.isBrushClip &&
                            currentPosMs >= it.timelineStartMs &&
                            currentPosMs < it.timelineEndMs
                } ?: clips.lastOrNull { it.isBrushClip }

                if (brushClip != null) {
                    BrushDrawLayer(
                        brushType = activeBrushType,
                        brushColor = activeBrushColor,
                        brushWidth = activeBrushWidth,
                        brushOpacity = activeBrushOpacity,
                        clipStartMs = brushClip.timelineStartMs,
                        clipEndMs = brushClip.timelineEndMs,
                        currentPosMs = currentPosMs,
                        onStrokeComplete = onBrushStrokeComplete
                    )
                }
            }
        }

        if (sideBar > 0.5f) {
            Box(
                modifier = Modifier
                    .width(sideBar.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .background(Color.Black)
            )
            Box(
                modifier = Modifier
                    .width(sideBar.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .background(Color.Black)
            )
        }
        if (topBar > 0.5f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topBar.dp)
                    .align(Alignment.TopCenter)
                    .background(Color.Black)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topBar.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color.Black)
            )
        }

        Box(
            modifier = Modifier
                .width(canvasW.dp)
                .height(canvasH.dp)
                .align(Alignment.Center)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  COLOR MATTE RENDERER
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ColorMatteRenderer(
    style: com.moody.moodyvideoeditor.data.ColorMatteStyle,
    modifier: Modifier = Modifier
) {
    when (style.mode) {
        ColorMatteMode.SOLID -> {
            Box(modifier = modifier.background(Color(style.solidColor)))
        }

        ColorMatteMode.RAMP -> {
            BoxWithConstraints(modifier = modifier) {
                val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val rad = Math.toRadians(style.rampAngleDeg.toDouble())
                val dx = cos(rad).toFloat()
                val dy = sin(rad).toFloat()
                val halfDiag = sqrt(w * w + h * h) / 2f
                val cx = w / 2f
                val cy = h / 2f
                val start = Offset(cx - dx * halfDiag, cy - dy * halfDiag)
                val end = Offset(cx + dx * halfDiag, cy + dy * halfDiag)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(style.rampColor1),
                                    Color(style.rampColor2)
                                ),
                                start = start,
                                end = end
                            )
                        )
                )
            }
        }

        ColorMatteMode.FOUR_COLOR -> {
            BoxWithConstraints(modifier = modifier) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val W = size.width
                    val H = size.height
                    val tlR = ((style.topLeft shr 16) and 0xFF) / 255f
                    val tlG = ((style.topLeft shr 8) and 0xFF) / 255f
                    val tlB = (style.topLeft and 0xFF) / 255f
                    val trR = ((style.topRight shr 16) and 0xFF) / 255f
                    val trG = ((style.topRight shr 8) and 0xFF) / 255f
                    val trB = (style.topRight and 0xFF) / 255f
                    val blR = ((style.bottomLeft shr 16) and 0xFF) / 255f
                    val blG = ((style.bottomLeft shr 8) and 0xFF) / 255f
                    val blB = (style.bottomLeft and 0xFF) / 255f
                    val brR = ((style.bottomRight shr 16) and 0xFF) / 255f
                    val brG = ((style.bottomRight shr 8) and 0xFF) / 255f
                    val brB = (style.bottomRight and 0xFF) / 255f

                    val stepX = (W / 128f).coerceAtLeast(1f)
                    val stepY = (H / 128f).coerceAtLeast(1f)
                    var y = 0f
                    while (y < H) {
                        var x = 0f
                        while (x < W) {
                            val fx = (x / W).coerceIn(0f, 1f)
                            val fy = (y / H).coerceIn(0f, 1f)
                            val wTL = (1f - fx) * (1f - fy)
                            val wTR = fx * (1f - fy)
                            val wBL = (1f - fx) * fy
                            val wBR = fx * fy
                            val r = (tlR * wTL + trR * wTR + blR * wBL + brR * wBR)
                                .coerceIn(0f, 1f)
                            val g = (tlG * wTL + trG * wTR + blG * wBL + brG * wBR)
                                .coerceIn(0f, 1f)
                            val b = (tlB * wTL + trB * wTR + blB * wBL + brB * wBR)
                                .coerceIn(0f, 1f)
                            drawRect(
                                color = Color(r, g, b, 1f),
                                topLeft = Offset(x, y),
                                size = androidx.compose.ui.geometry.Size(stepX, stepY)
                            )
                            x += stepX
                        }
                        y += stepY
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  MASKED CLIP CONTENT WRAPPER
// ═══════════════════════════════════════════════════════════════

private fun maskAtClipTime(clip: EditorClip, currentPosMs: Long): MaskState {
    val localTimeSec = ((currentPosMs - clip.timelineStartMs).coerceAtLeast(0L)) / 1000f
    return MaskEngine.sampleAt(clip.mask, localTimeSec)
}

@Composable
private fun MaskedClipContent(
    mask: MaskState,
    content: @Composable BoxScope.() -> Unit
) {
    val shouldMask = mask.isActive && (
            mask.type != MaskType.CUSTOM ||
                    (mask.customPoints.size >= 3 && mask.customClosed)
            )

    if (!shouldMask) {
        Box(modifier = Modifier.fillMaxSize(), content = content)
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawMaskBlend(mask, size.width, size.height)
            },
        content = content
    )
}

private fun DrawScope.drawMaskBlend(mask: MaskState, w: Float, h: Float) {
    val path = buildMaskPathCompose(mask, w, h) ?: return
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        val featherPx = abs(mask.feather / 100f * minOf(w, h) / 9f)
        val alphaInt = (mask.opacity / 100f * 255).toInt().coerceIn(0, 255)

        if (featherPx > 0.5f) {
            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = android.graphics.Color.WHITE
                alpha = alphaInt
                xfermode = PorterDuffXfermode(
                    if (mask.isInverted) PorterDuff.Mode.DST_OUT
                    else PorterDuff.Mode.DST_IN
                )
                maskFilter = BlurMaskFilter(featherPx, BlurMaskFilter.Blur.NORMAL)
            }
            nativeCanvas.drawPath(path.asAndroidPath(), paint)
            paint.reset()
        } else {
            drawPath(
                path = path,
                color = Color.White.copy(alpha = alphaInt / 255f),
                blendMode = if (mask.isInverted) BlendMode.DstOut else BlendMode.DstIn
            )
        }
    }
}

private fun buildMaskPathCompose(mask: MaskState, w: Float, h: Float): Path? {
    if (mask.type == MaskType.NONE) return null
    val cx = mask.centerX * w
    val cy = mask.centerY * h
    val rad = Math.toRadians(mask.rotation.toDouble())
    val cosR = cos(rad).toFloat()
    val sinR = sin(rad).toFloat()

    fun rp(px: Float, py: Float): Pair<Float, Float> {
        if (mask.rotation == 0f) return px to py
        val dx = px - cx
        val dy = py - cy
        return (cx + dx * cosR - dy * sinR) to (cy + dx * sinR + dy * cosR)
    }

    return Path().apply {
        when (mask.type) {
            MaskType.CIRCLE -> {
                val minDimension = minOf(w, h)
                val r = (mask.radius * minDimension +
                        mask.expansion / 100f * minDimension).coerceAtLeast(0f)
                addOval(Rect(cx - r, cy - r, cx + r, cy + r))
            }

            MaskType.RECTANGLE -> {
                val expansionPx = mask.expansion / 100f * minOf(w, h)
                val hw = (mask.width * w / 2f + expansionPx).coerceAtLeast(0f)
                val hh = (mask.height * h / 2f + expansionPx).coerceAtLeast(0f)
                val c1 = rp(cx - hw, cy - hh)
                val c2 = rp(cx + hw, cy - hh)
                val c3 = rp(cx + hw, cy + hh)
                val c4 = rp(cx - hw, cy + hh)
                moveTo(c1.first, c1.second)
                lineTo(c2.first, c2.second)
                lineTo(c3.first, c3.second)
                lineTo(c4.first, c4.second)
                close()
            }

            MaskType.LINEAR -> {
                val diag = sqrt(w * w + h * h) * 1.5f
                val lineY = mask.positionY * h - mask.expansion / 100f * minOf(w, h)
                val px = -sinR
                val py = cosR
                val p1x = cx - cosR * diag
                val p1y = lineY - sinR * diag
                val p2x = cx + cosR * diag
                val p2y = lineY + sinR * diag
                val p3x = p2x + px * diag
                val p3y = p2y + py * diag
                val p4x = p1x + px * diag
                val p4y = p1y + py * diag
                moveTo(p1x, p1y)
                lineTo(p2x, p2y)
                lineTo(p3x, p3y)
                lineTo(p4x, p4y)
                close()
            }

            MaskType.HEART -> {
                val s = (
                        mask.scale * minOf(w, h) * 0.4f +
                                mask.expansion / 100f * minOf(w, h)
                        ).coerceAtLeast(0f)

                fun rp2(px: Float, py: Float) =
                    (cx + px * cosR - py * sinR) to (cy + px * sinR + py * cosR)

                val p0 = rp2(0f, 0.6f * s)
                moveTo(p0.first, p0.second)
                val c1 = rp2(-0.7f * s, 0.1f * s)
                val c2 = rp2(-1.0f * s, -0.5f * s)
                val c3 = rp2(-0.5f * s, -0.7f * s)
                cubicTo(c1.first, c1.second, c2.first, c2.second, c3.first, c3.second)
                val c4 = rp2(-0.15f * s, -0.85f * s)
                val c5 = rp2(0f, -0.55f * s)
                val c6 = rp2(0f, -0.3f * s)
                cubicTo(c4.first, c4.second, c5.first, c5.second, c6.first, c6.second)
                val c7 = rp2(0f, -0.55f * s)
                val c8 = rp2(0.15f * s, -0.85f * s)
                val c9 = rp2(0.5f * s, -0.7f * s)
                cubicTo(c7.first, c7.second, c8.first, c8.second, c9.first, c9.second)
                val c10 = rp2(1.0f * s, -0.5f * s)
                val c11 = rp2(0.7f * s, 0.1f * s)
                val c12 = rp2(0f, 0.6f * s)
                cubicTo(c10.first, c10.second, c11.first, c11.second, c12.first, c12.second)
                close()
            }

            MaskType.CUSTOM -> {
                val pts = MaskEngine.expandedPoints(
                    mask.customPoints, mask.expansion / 100f
                )
                if (pts.size >= 3 && mask.customClosed) {
                    fun rp3(px: Float, py: Float): Pair<Float, Float> {
                        val dx = px - cx
                        val dy = py - cy
                        return (cx + dx * cosR - dy * sinR) to
                                (cy + dx * sinR + dy * cosR)
                    }

                    val p0 = rp3(pts[0].x * w, pts[0].y * h)
                    moveTo(p0.first, p0.second)
                    for (i in 1 until pts.size) {
                        val prev = pts[i - 1]
                        val curr = pts[i]
                        if (prev.hasHandles || curr.hasHandles) {
                            val c1 = rp3(prev.outX * w, prev.outY * h)
                            val c2 = rp3(curr.inX * w, curr.inY * h)
                            val end = rp3(curr.x * w, curr.y * h)
                            cubicTo(
                                c1.first, c1.second, c2.first, c2.second,
                                end.first, end.second
                            )
                        } else {
                            val end = rp3(curr.x * w, curr.y * h)
                            lineTo(end.first, end.second)
                        }
                    }
                    val last = pts.last()
                    val first = pts.first()
                    if (last.hasHandles || first.hasHandles) {
                        val c1 = rp3(last.outX * w, last.outY * h)
                        val c2 = rp3(first.inX * w, first.inY * h)
                        val end = rp3(first.x * w, first.y * h)
                        cubicTo(
                            c1.first, c1.second, c2.first, c2.second,
                            end.first, end.second
                        )
                    }
                    close()
                }
            }

            MaskType.NONE -> {}
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  MASK PEN OVERLAY
// ═══════════════════════════════════════════════════════════════

@Composable
private fun MaskPenOverlay(
    maskState: MaskState, canvasW: Float, canvasH: Float,
    isHandMode: Boolean,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    onTapAddPoint: (Float, Float) -> Unit,
    onAnchorMove: (Int, Float, Float) -> Unit,
    onHandleMove: (Int, Boolean, Float, Float) -> Unit,
    onTogglePoint: (Int) -> Unit,
    onDeletePoint: (Int) -> Unit,
    onClosePath: () -> Unit,
    onMaskMove: (Float, Float) -> Unit,
    onMaskStateChanged: (MaskState) -> Unit
) {
    val density = LocalDensity.current
    val latestMaskState by rememberUpdatedState(maskState)
    val pts = maskState.customPoints
    val strokeColor = Color(maskState.strokeColor)
    val isClosed = maskState.customClosed

    var viewSizePx by remember { mutableStateOf(IntSize.Zero) }
    var selectedPointIndex by remember { mutableStateOf(-1) }
    var draggingAnchor by remember { mutableStateOf(-1) }
    var draggingHandle by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }

    val hitRadiusPx = with(density) { 22.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewSizePx = it }
            .pointerInput(pts.size, maskState.type, isClosed, isHandMode) {
                awaitEachGesture {
                    val viewW = viewSizePx.width.toFloat()
                    val viewH = viewSizePx.height.toFloat()
                    if (viewW <= 0f || viewH <= 0f) return@awaitEachGesture

                    val down = awaitFirstDown(requireUnconsumed = false)

                    if (isHandMode) {
                        val gestureMask = latestMaskState
                        down.consume()
                        onGestureStart()
                        var baseMask = gestureMask
                        var basePoint = down.position
                        var handle = maskHandleAt(
                            gestureMask, down.position.x, down.position.y,
                            viewW, viewH, hitRadiusPx
                        )
                        var latestMask = gestureMask
                        var multiBaseMask: MaskState? = null
                        var multiStartCentroid = Offset.Zero
                        var multiStartDistance = 1f
                        var multiStartAngle = 0f
                        var wasMultiTouch = false
                        var dragging = true
                        while (dragging) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                dragging = false
                            } else if (pressed.size >= 2) {
                                val first = pressed[0].position
                                val second = pressed[1].position
                                val centroid = (first + second) / 2f
                                val distance = hypot(
                                    second.x - first.x, second.y - first.y
                                ).coerceAtLeast(1f)
                                val angle = atan2(
                                    second.y - first.y, second.x - first.x
                                )
                                if (multiBaseMask == null) {
                                    multiBaseMask = latestMask
                                    multiStartCentroid = centroid
                                    multiStartDistance = distance
                                    multiStartAngle = angle
                                }
                                latestMask = transformMaskWithTwoFingers(
                                    multiBaseMask!!, multiStartCentroid, centroid,
                                    multiStartDistance, distance,
                                    multiStartAngle, angle, viewW, viewH
                                )
                                onMaskStateChanged(latestMask)
                                wasMultiTouch = true
                                pressed.forEach { it.consume() }
                            } else {
                                val change = pressed.first()
                                if (wasMultiTouch) {
                                    baseMask = latestMask
                                    basePoint = change.position
                                    handle = MaskGestureHandle.MOVE
                                    multiBaseMask = null
                                    wasMultiTouch = false
                                }
                                latestMask = transformMaskWithOneFinger(
                                    baseMask, handle, basePoint, change.position,
                                    viewW, viewH
                                )
                                onMaskStateChanged(latestMask)
                                change.consume()
                            }
                        }
                        onGestureEnd()
                        return@awaitEachGesture
                    }

                    if (!isClosed && pts.size >= 3) {
                        val first = pts[0]
                        val dFirst = sqrt(
                            (down.position.x - first.x * viewW) *
                                    (down.position.x - first.x * viewW) +
                                    (down.position.y - first.y * viewH) *
                                    (down.position.y - first.y * viewH)
                        )
                        if (dFirst < hitRadiusPx * 1.8f) {
                            onClosePath()
                            down.consume()
                            return@awaitEachGesture
                        }
                    }

                    val downX = (down.position.x / viewW).coerceIn(0f, 1f)
                    val downY = (down.position.y / viewH).coerceIn(0f, 1f)

                    var hitAnchor = -1
                    var hitHandle: Pair<Int, Boolean>? = null

                    if (selectedPointIndex in pts.indices) {
                        val sel = pts[selectedPointIndex]
                        if (sel.hasHandles) {
                            val dIn = sqrt(
                                (down.position.x - sel.inX * viewW) *
                                        (down.position.x - sel.inX * viewW) +
                                        (down.position.y - sel.inY * viewH) *
                                        (down.position.y - sel.inY * viewH)
                            )
                            val dOut = sqrt(
                                (down.position.x - sel.outX * viewW) *
                                        (down.position.x - sel.outX * viewW) +
                                        (down.position.y - sel.outY * viewH) *
                                        (down.position.y - sel.outY * viewH)
                            )
                            if (dIn < hitRadiusPx) hitHandle = selectedPointIndex to true
                            else if (dOut < hitRadiusPx) hitHandle = selectedPointIndex to false
                        }
                    }

                    if (hitHandle == null) {
                        pts.forEachIndexed { i, p ->
                            val d = sqrt(
                                (down.position.x - p.x * viewW) *
                                        (down.position.x - p.x * viewW) +
                                        (down.position.y - p.y * viewH) *
                                        (down.position.y - p.y * viewH)
                            )
                            if (d < hitRadiusPx && hitAnchor == -1) hitAnchor = i
                        }
                    }

                    if (hitHandle != null) {
                        draggingHandle = hitHandle
                        down.consume()
                    } else if (hitAnchor >= 0) {
                        selectedPointIndex = hitAnchor
                        draggingAnchor = hitAnchor
                        down.consume()
                    } else if (!isClosed) {
                        selectedPointIndex = pts.size
                        onTapAddPoint(downX, downY)
                        down.consume()
                    }

                    var continueGesture = true
                    while (continueGesture) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            draggingAnchor = -1
                            draggingHandle = null
                            continueGesture = false
                        } else {
                            pressed.forEach { ch ->
                                if (draggingAnchor >= 0) {
                                    onAnchorMove(
                                        draggingAnchor,
                                        (ch.position.x / viewW).coerceIn(0f, 1f),
                                        (ch.position.y / viewH).coerceIn(0f, 1f)
                                    )
                                    ch.consume()
                                } else if (draggingHandle != null) {
                                    val dh = draggingHandle!!
                                    val anchor = pts.getOrNull(dh.first)
                                    if (anchor != null) {
                                        onHandleMove(
                                            dh.first, dh.second,
                                            (ch.position.x / viewW) - anchor.x,
                                            (ch.position.y / viewH) - anchor.y
                                        )
                                        ch.consume()
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(pts.size) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        val viewW = viewSizePx.width.toFloat()
                        val viewH = viewSizePx.height.toFloat()
                        if (viewW > 0f && viewH > 0f) {
                            var foundIndex = -1
                            pts.forEachIndexed { i, p ->
                                val d = sqrt(
                                    (tapOffset.x - p.x * viewW) *
                                            (tapOffset.x - p.x * viewW) +
                                            (tapOffset.y - p.y * viewH) *
                                            (tapOffset.y - p.y * viewH)
                                )
                                if (d < hitRadiusPx && foundIndex == -1) foundIndex = i
                            }
                            if (foundIndex >= 0) {
                                onTogglePoint(foundIndex)
                                selectedPointIndex = foundIndex
                            }
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (!maskState.isActive) return@Canvas

            val path = buildMaskPathCompose(maskState, w, h)
            if (path != null) {
                drawPath(path, strokeColor, style = Stroke(width = 3f))
            }

            if (maskState.type == MaskType.CUSTOM) {
                pts.forEachIndexed { i, p ->
                    val cx = p.x * w
                    val cy = p.y * h
                    val isSel = i == selectedPointIndex
                    val isFirst = i == 0

                    if (isFirst && !isClosed && pts.size >= 3) {
                        drawCircle(
                            Color(0xFF60EFFF).copy(alpha = 0.4f), 22f, Offset(cx, cy)
                        )
                        drawCircle(
                            Color(0xFF60EFFF).copy(alpha = 0.8f), 16f, Offset(cx, cy),
                            style = Stroke(width = 3f)
                        )
                    }

                    drawCircle(
                        color = when {
                            isFirst && !isClosed -> Color(0xFF60EFFF)
                            isSel -> Color(0xFFFFD166)
                            else -> strokeColor
                        },
                        radius = if (isSel || (isFirst && !isClosed)) 12f else 9f,
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        Color.White,
                        if (isSel || (isFirst && !isClosed)) 12f else 9f,
                        Offset(cx, cy),
                        style = Stroke(width = 2f)
                    )
                    drawCircle(Color.Black, 3f, Offset(cx, cy))
                }
            }

            if (isHandMode) {
                val (resize, rotate, feather) = maskHandlePositions(maskState, w, h)
                val center = Offset(maskState.centerX * w, maskState.centerY * h)
                drawLine(Color.White.copy(alpha = 0.75f), center, rotate, strokeWidth = 2f)
                drawLine(Color(0xFF60EFFF), center, feather, strokeWidth = 2f)
                drawCircle(Color(0xFFFFD166), 11f, resize)
                drawCircle(Color.White, 11f, resize, style = Stroke(width = 2f))
                drawCircle(Color(0xFF60EFFF), 10f, rotate)
                drawCircle(Color.White, 10f, rotate, style = Stroke(width = 2f))
                drawCircle(Color(0xFF22C55E), 10f, feather)
                drawCircle(Color.White, 10f, feather, style = Stroke(width = 2f))
            }
        }
    }
}

private enum class MaskGestureHandle {
    MOVE, RESIZE, ROTATE, FEATHER
}

private fun maskHandlePositions(
    mask: MaskState, width: Float, height: Float
): Triple<Offset, Offset, Offset> {
    val cx = mask.centerX * width
    val cy = mask.centerY * height
    val minDimension = minOf(width, height).coerceAtLeast(1f)
    val radius = when (mask.type) {
        MaskType.CIRCLE -> mask.radius * minDimension
        MaskType.RECTANGLE -> hypot(mask.width * width / 2f, mask.height * height / 2f)
        MaskType.HEART -> mask.scale * minDimension * 0.4f
        MaskType.CUSTOM -> mask.customPoints.maxOfOrNull { point ->
            hypot((point.x - mask.centerX) * width, (point.y - mask.centerY) * height)
        } ?: minDimension * 0.2f

        MaskType.LINEAR, MaskType.NONE -> minDimension * 0.22f
    }.coerceAtLeast(24f)

    val resizeLocal = when (mask.type) {
        MaskType.CIRCLE -> Offset(radius, 0f)
        MaskType.RECTANGLE -> Offset(mask.width * width / 2f, mask.height * height / 2f)
        MaskType.HEART -> Offset(radius, 0f)
        MaskType.CUSTOM -> {
            val x = mask.customPoints.maxOfOrNull { it.x * width - cx } ?: radius
            val y = mask.customPoints.maxOfOrNull { it.y * height - cy } ?: radius
            Offset(x, y)
        }

        MaskType.LINEAR, MaskType.NONE -> Offset(radius * 0.65f, radius * 0.65f)
    }
    val resize = rotateMaskPoint(
        Offset(cx + resizeLocal.x, cy + resizeLocal.y),
        cx, cy,
        if (mask.type == MaskType.CIRCLE) 0f else mask.rotation
    )
    val rotate = rotateMaskPoint(
        Offset(cx, cy - radius - 34f), cx, cy, mask.rotation
    )
    val feather = Offset(cx + radius + 42f, cy)
    return Triple(resize, rotate, feather)
}

private fun rotateMaskPoint(
    point: Offset, centerX: Float, centerY: Float, rotationDegrees: Float
): Offset {
    val radians = Math.toRadians(rotationDegrees.toDouble())
    val cosR = cos(radians).toFloat()
    val sinR = sin(radians).toFloat()
    val dx = point.x - centerX
    val dy = point.y - centerY
    return Offset(
        centerX + dx * cosR - dy * sinR,
        centerY + dx * sinR + dy * cosR
    )
}

private fun maskHandleAt(
    mask: MaskState, x: Float, y: Float, width: Float, height: Float, hitRadius: Float
): MaskGestureHandle {
    val point = Offset(x, y)
    val (resize, rotate, feather) = maskHandlePositions(mask, width, height)
    fun near(target: Offset) = hypot(point.x - target.x, point.y - target.y) <= hitRadius * 1.5f
    return when {
        near(feather) -> MaskGestureHandle.FEATHER
        near(rotate) -> MaskGestureHandle.ROTATE
        near(resize) -> MaskGestureHandle.RESIZE
        else -> MaskGestureHandle.MOVE
    }
}

private fun transformMaskWithOneFinger(
    initial: MaskState, handle: MaskGestureHandle,
    start: Offset, current: Offset, width: Float, height: Float
): MaskState {
    val minDimension = minOf(width, height).coerceAtLeast(1f)
    val dx = (current.x - start.x) / width.coerceAtLeast(1f)
    val dy = (current.y - start.y) / height.coerceAtLeast(1f)
    val centerX = initial.centerX * width
    val centerY = initial.centerY * height
    return when (handle) {
        MaskGestureHandle.MOVE -> initial.copy(
            centerX = initial.centerX + dx,
            centerY = initial.centerY + dy,
            positionY = initial.positionY + dy,
            customPoints = initial.customPoints.map {
                it.copy(x = it.x + dx, y = it.y + dy)
            }
        )

        MaskGestureHandle.ROTATE -> {
            val startAngle = atan2(start.y - centerY, start.x - centerX)
            val currentAngle = atan2(current.y - centerY, current.x - centerX)
            val delta = Math.toDegrees((currentAngle - startAngle).toDouble()).toFloat()
            initial.copy(rotation = initial.rotation + delta)
        }

        MaskGestureHandle.FEATHER -> {
            val initialRadius = hypot(start.x - centerX, start.y - centerY)
            val currentRadius = hypot(current.x - centerX, current.y - centerY)
            initial.copy(
                feather = (initial.feather +
                        (currentRadius - initialRadius) / minDimension * 900f)
                    .coerceIn(0f, 500f)
            )
        }

        MaskGestureHandle.RESIZE -> {
            val local = rotateMaskPoint(current, centerX, centerY, -initial.rotation)
            when (initial.type) {
                MaskType.CIRCLE -> initial.copy(
                    radius = (hypot(local.x - centerX, local.y - centerY) / minDimension)
                        .coerceIn(0.01f, 2f)
                )

                MaskType.RECTANGLE -> initial.copy(
                    width = (abs(local.x - centerX) * 2f / width).coerceIn(0.01f, 3f),
                    height = (abs(local.y - centerY) * 2f / height).coerceIn(0.01f, 3f)
                )

                MaskType.HEART -> initial.copy(
                    scale = (hypot(local.x - centerX, local.y - centerY) /
                            (minDimension * 0.4f)).coerceIn(0.05f, 5f)
                )

                MaskType.CUSTOM -> {
                    val handleStart = maskHandlePositions(initial, width, height).first
                    val oldRadius = hypot(handleStart.x - centerX, handleStart.y - centerY)
                        .coerceAtLeast(1f)
                    val newRadius = hypot(local.x - centerX, local.y - centerY)
                    val factor = (newRadius / oldRadius).coerceIn(0.05f, 20f)
                    initial.copy(
                        customPoints = initial.customPoints.map { point ->
                            point.copy(
                                x = initial.centerX + (point.x - initial.centerX) * factor,
                                y = initial.centerY + (point.y - initial.centerY) * factor
                            )
                        }
                    )
                }

                MaskType.LINEAR, MaskType.NONE -> initial.copy(
                    positionY = (current.y / height).coerceIn(-1f, 2f)
                )
            }
        }
    }
}

private fun transformMaskWithTwoFingers(
    initial: MaskState, startCentroid: Offset, centroid: Offset,
    startDistance: Float, distance: Float,
    startAngle: Float, angle: Float, width: Float, height: Float
): MaskState {
    val dx = (centroid.x - startCentroid.x) / width.coerceAtLeast(1f)
    val dy = (centroid.y - startCentroid.y) / height.coerceAtLeast(1f)
    val scaleFactor = (distance / startDistance.coerceAtLeast(1f)).coerceIn(0.05f, 20f)
    val angleDelta = Math.toDegrees((angle - startAngle).toDouble()).toFloat()
    val centerX = initial.centerX + dx
    val centerY = initial.centerY + dy
    val rotatedPoints = initial.customPoints.map { point ->
        val x = initial.centerX + (point.x - initial.centerX) * scaleFactor + dx
        val y = initial.centerY + (point.y - initial.centerY) * scaleFactor + dy
        point.copy(x = x, y = y)
    }
    return initial.copy(
        centerX = centerX, centerY = centerY,
        positionY = initial.positionY + dy,
        radius = (initial.radius * scaleFactor).coerceIn(0.01f, 2f),
        width = (initial.width * scaleFactor).coerceIn(0.01f, 3f),
        height = (initial.height * scaleFactor).coerceIn(0.01f, 3f),
        scale = (initial.scale * scaleFactor).coerceIn(0.05f, 5f),
        rotation = initial.rotation + angleDelta,
        customPoints = rotatedPoints
    )
}

// ═══════════════════════════════════════════════════════════════
//  BRUSH DRAW LAYER
// ═══════════════════════════════════════════════════════════════

@Composable
private fun BrushDrawLayer(
    brushType: BrushType, brushColor: Long,
    brushWidth: Float, brushOpacity: Float,
    clipStartMs: Long, clipEndMs: Long, currentPosMs: Long,
    onStrokeComplete: (BrushStroke) -> Unit
) {
    var currentPoints by remember { mutableStateOf<List<BrushPoint>>(emptyList()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(brushType, brushColor, brushWidth, brushOpacity) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentPoints = listOf(
                            BrushPoint(
                                (offset.x / size.width).coerceIn(0f, 1f),
                                (offset.y / size.height).coerceIn(0f, 1f)
                            )
                        )
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentPoints = currentPoints + BrushPoint(
                            (change.position.x / size.width).coerceIn(0f, 1f),
                            (change.position.y / size.height).coerceIn(0f, 1f)
                        )
                    },
                    onDragEnd = {
                        if (currentPoints.isNotEmpty()) {
                            val localStart = (currentPosMs - clipStartMs).coerceAtLeast(0L)
                            val localEnd = (clipEndMs - clipStartMs)
                                .coerceAtLeast(localStart + 1)
                            onStrokeComplete(
                                BrushStroke(
                                    id = UUID.randomUUID().toString(),
                                    type = brushType, color = brushColor,
                                    width = brushWidth, opacity = brushOpacity,
                                    points = currentPoints,
                                    startMs = localStart, endMs = localEnd
                                )
                            )
                        }
                        currentPoints = emptyList()
                    },
                    onDragCancel = { currentPoints = emptyList() }
                )
            }
    ) {
        if (currentPoints.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                BrushEngine.drawStroke(
                    scope = this,
                    stroke = BrushStroke(
                        id = "temp", type = brushType, color = brushColor,
                        width = brushWidth, opacity = brushOpacity,
                        points = currentPoints,
                        startMs = 0, endMs = Long.MAX_VALUE
                    ),
                    viewW = size.width, viewH = size.height, currentTimeMs = 0
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  INTERACTIVE TEXT OVERLAY
// ═══════════════════════════════════════════════════════════════

@Composable
private fun InteractiveTextOverlay(
    clip: EditorClip,
    textState: com.moody.moodyvideoeditor.data.TextState,
    canvasW: Float, canvasH: Float,
    currentPosMs: Long,
    isSelected: Boolean, isMulti: Boolean = false,
    isDrawingMode: Boolean = false,
    onSelect: () -> Unit,
    onGroupGestureStart: () -> Unit,
    onGroupGestureEnd: () -> Unit,
    onGroupGesture: (String, Float, Float, Float, Float) -> Unit,
    onPositionChanged: (Float, Float) -> Unit,
    onTransformChanged: (Float, Float) -> Unit,
    onDeleteLayer: () -> Unit,
) {
    if (textState.content.isBlank()) return

    val localTimeSec = ((currentPosMs - clip.timelineStartMs) / 1000f).coerceAtLeast(0f)
    val sampled = TransformApplier.resolveLive(clip, localTimeSec)
    val animDur = textState.animationDuration.coerceAtLeast(0.1f)
    val progress = (localTimeSec / animDur).coerceIn(0f, 1f)

    var isDragging by remember { mutableStateOf(false) }

    val rawFrame = com.moody.moodyvideoeditor.utils.AnimationsEngine.computeFrame(
        textState.animation, progress, localTimeSec
    )
    val frame = if (isDragging) {
        rawFrame.copy(translateX = 0f, translateY = 0f)
    } else rawFrame

    val displayContent =
        if (textState.animation.equals("typewriter", ignoreCase = true)) {
            val total = textState.content.length
            val visible = (progress * total).toInt().coerceIn(0, total)
            textState.content.substring(0, visible)
        } else textState.content

    if (displayContent.isEmpty() &&
        textState.animation.equals("typewriter", ignoreCase = true)
    ) return

    val density = LocalDensity.current
    val canvasWpx = with(density) { canvasW.dp.toPx() }
    val canvasHpx = with(density) { canvasH.dp.toPx() }

    val textTypeface = TextRenderContract.androidTypefaceFor(
        textState.fontFamily,
        textState.fontWeight == "bold",
        textState.fontStyle == "italic"
    )
    val effFontSizePx = TextRenderContract.scaledFontSize(
        baseSizePx = textState.fontSize.toFloat(),
        canvasWidthPx = canvasWpx
    )
    val effLetterSpacingPx = TextScaler.letterSpacing(
        base = textState.letterSpacing,
        canvasWidthPx = canvasWpx
    )
    val effLineHeightPx = TextScaler.lineHeight(effFontSizePx, textState.lineHeight)
    val family = FontLibrary.familyFor(textState.fontFamily)
    val weight = if (textState.fontWeight == "bold") FontWeight.Bold
    else FontWeight.Normal
    val fontSty = if (textState.fontStyle == "italic")
        androidx.compose.ui.text.font.FontStyle.Italic
    else androidx.compose.ui.text.font.FontStyle.Normal

    val userStrokePx = textState.strokeWidth.coerceAtLeast(0f) *
            2f * (canvasWpx / 720f)

    val finalFontSizePx = TextRenderContract.fittedFontSizePx(
        content = displayContent,
        baseSizePx = textState.fontSize.toFloat(),
        canvasWidthPx = canvasWpx,
        maxWidthPercent = textState.maxWidth,
        typeface = textTypeface
    )

    val finalFontSizeSp = with(density) { finalFontSizePx.toSp() }
    val finalLetterSpacingSp = with(density) { effLetterSpacingPx.toSp() }
    val finalLineHeightSp = with(density) { effLineHeightPx.toSp() }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()

    val finalMeasure = remember(
        displayContent, finalFontSizeSp, finalLetterSpacingSp,
        finalLineHeightSp, family, weight, fontSty
    ) {
        measurer.measure(
            text = displayContent,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = finalFontSizeSp,
                letterSpacing = finalLetterSpacingSp,
                lineHeight = finalLineHeightSp,
                fontFamily = family,
                fontWeight = weight,
                fontStyle = fontSty
            ),
            constraints = androidx.compose.ui.unit.Constraints(
                maxWidth = Int.MAX_VALUE
            ),
            softWrap = false
        )
    }

    val textWidthDp = with(density) { finalMeasure.size.width.toDp().value }
    val textHeightDp = with(density) { finalMeasure.size.height.toDp().value }

    val solidColor = Color(textState.color)
    val gradient: Brush? = if (textState.gradientEnabled) {
        val rad = Math.toRadians(textState.gradientAngle.toDouble())
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        Brush.linearGradient(
            colors = listOf(
                Color(textState.gradientColor1),
                Color(textState.gradientColor2)
            ),
            start = Offset(-dx * 600f, -dy * 600f),
            end = Offset(dx * 600f, dy * 600f)
        )
    } else null

    val shadowStyle: androidx.compose.ui.graphics.Shadow? =
        if (textState.shadowEnabled) {
            androidx.compose.ui.graphics.Shadow(
                color = Color(textState.shadowColor),
                offset = Offset(
                    textState.shadowOffsetX * (canvasWpx / 720f),
                    textState.shadowOffsetY * (canvasWpx / 720f)
                ),
                blurRadius = textState.shadowBlur * (canvasWpx / 720f)
            )
        } else null

    val textAlignValue = when (textState.alignment) {
        "left" -> androidx.compose.ui.text.style.TextAlign.Left
        "right" -> androidx.compose.ui.text.style.TextAlign.Right
        else -> androidx.compose.ui.text.style.TextAlign.Center
    }

    val borderColor = when {
        isSelected -> Color(0xFF60EFFF)
        isMulti -> Color(0xFFFFD166)
        else -> Color.Transparent
    }

    val glowDensity = density.density
    val glowSizePx = finalFontSizePx
    val glowLetterSpacingEm = if (finalFontSizePx <= 0f) 0f
    else (effLetterSpacingPx / finalFontSizePx).coerceIn(-0.3f, 0.3f)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val posTx = (sampled.x - 50f) / 100f * canvasWpx
                    val posTy = (sampled.y - 50f) / 100f * canvasHpx
                    translationX = posTx + frame.translateX
                    translationY = posTy + frame.translateY
                    scaleX = (sampled.scale / 100f) * frame.scaleX
                    scaleY = (sampled.scale / 100f) * frame.scaleY
                    rotationZ = sampled.rotation + frame.rotationZ
                    alpha = (textState.opacity / 100f) * frame.alpha
                    transformOrigin = TransformOrigin.Center
                }
                .width((textWidthDp + 16f).coerceAtLeast(26f).dp)
                .height((textHeightDp + 16f).coerceAtLeast(26f).dp)
                .then(
                    if (isSelected || isMulti) Modifier.border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(4.dp)
                    ) else Modifier
                )
                .pointerInput(clip.id, canvasW, canvasH, isDrawingMode) {
                    if (isDrawingMode) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        isDragging = true
                        onGroupGestureStart()

                        val baseX = sampled.x
                        val baseY = sampled.y
                        val baseScale = sampled.scale
                        val baseRot = sampled.rotation
                        var accumPanX = 0f
                        var accumPanY = 0f
                        var accumZoom = 1f
                        var accumRot = 0f
                        var lastDist = 0f
                        var lastAngle = 0f
                        var hasMultiGesture = false

                        if (!isSelected && !isMulti) onSelect()

                        var continueGesture = true
                        while (continueGesture) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                isDragging = false
                                continueGesture = false
                            } else {
                                if (pressed.size == 1) {
                                    val ch = pressed.first()
                                    val pan = ch.position - ch.previousPosition
                                    val rotation = Math.toRadians(
                                        (sampled.rotation + frame.rotationZ).toDouble()
                                    )
                                    val scaleX = (sampled.scale / 100f) * frame.scaleX
                                    val scaleY = (sampled.scale / 100f) * frame.scaleY
                                    accumPanX += (
                                            pan.x * scaleX * cos(rotation) -
                                                    pan.y * scaleY * sin(rotation)
                                            ).toFloat()
                                    accumPanY += (
                                            pan.x * scaleX * sin(rotation) +
                                                    pan.y * scaleY * cos(rotation)
                                            ).toFloat()
                                    ch.consume()
                                } else if (pressed.size >= 2) {
                                    val c1 = pressed[0]
                                    val c2 = pressed[1]
                                    val d = c1.position - c2.position
                                    val dist = sqrt(d.x * d.x + d.y * d.y)
                                    val angle = atan2(d.y, d.x)
                                    if (hasMultiGesture && lastDist > 1f) {
                                        accumZoom *= dist / lastDist
                                        accumRot += Math.toDegrees(
                                            normalizeAngle(angle - lastAngle).toDouble()
                                        ).toFloat()
                                    }
                                    lastDist = dist
                                    lastAngle = angle
                                    hasMultiGesture = true
                                    c1.consume()
                                    c2.consume()
                                }

                                val rawX = baseX + accumPanX / canvasWpx * 100f
                                val rawY = baseY + accumPanY / canvasHpx * 100f
                                val newScale = (baseScale * accumZoom)
                                    .coerceIn(10f, 500f)
                                val newRot = baseRot + accumRot

                                if (isMulti) {
                                    onGroupGesture(clip.id, rawX, rawY, newScale, newRot)
                                } else {
                                    onPositionChanged(rawX, rawY)
                                    onTransformChanged(newScale, newRot)
                                }
                            }
                        }
                        onGroupGestureEnd()
                    }
                }
                .pointerInput(clip.id, isMulti, isDrawingMode) {
                    if (isDrawingMode) return@pointerInput
                    detectTapGestures { if (!isMulti) onSelect() }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (textState.glowEnabled && textState.glowRadius > 0f) {
                    val glowColorInt = textState.glowColor.toInt()
                    val glowAlign = when (textState.alignment) {
                        "left" -> Paint.Align.LEFT
                        "right" -> Paint.Align.RIGHT
                        else -> Paint.Align.CENTER
                    }
                    val textSizePx = glowSizePx
                    val letterSpacingEm = glowLetterSpacingEm
                    val glowRadiusPx = textState.glowRadius * (canvasWpx / 720f)

                    Canvas(modifier = Modifier.matchParentSize()) {
                        val nativeCanvas = drawContext.canvas.nativeCanvas
                        val layers = TextRenderContract.glowLayers()
                        layers.forEach { layer ->
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                isAntiAlias = true
                                color = glowColorInt
                                textSize = textSizePx * layer.fontScale
                                textAlign = glowAlign
                                this.typeface = textTypeface
                                this.letterSpacing = letterSpacingEm
                                alpha = (layer.alpha * 255).toInt().coerceIn(0, 255)
                                setShadowLayer(
                                    glowRadiusPx * layer.blurScale * glowDensity,
                                    0f, 0f, glowColorInt
                                )
                            }
                            val fm = paint.fontMetrics
                            val baseline = size.height / 2f -
                                    (fm.ascent + fm.descent) / 2f
                            val x = when (glowAlign) {
                                Paint.Align.LEFT -> 0f
                                Paint.Align.RIGHT -> size.width
                                else -> size.width / 2f
                            }
                            nativeCanvas.drawText(displayContent, x, baseline, paint)
                        }
                    }
                }

                if (textState.strokeEnabled && userStrokePx > 0f) {
                    Text(
                        text = displayContent,
                        color = Color(textState.strokeColor),
                        fontSize = finalFontSizeSp,
                        fontWeight = weight,
                        fontStyle = fontSty,
                        fontFamily = family,
                        textAlign = textAlignValue,
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Visible,
                        style = androidx.compose.ui.text.TextStyle(
                            drawStyle = Stroke(
                                userStrokePx,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                miter = 4f
                            ),
                            letterSpacing = finalLetterSpacingSp,
                            lineHeight = finalLineHeightSp
                        )
                    )
                }

                Text(
                    text = displayContent,
                    color = if (gradient != null) Color.Unspecified else solidColor,
                    fontSize = finalFontSizeSp,
                    fontWeight = weight,
                    fontStyle = fontSty,
                    fontFamily = family,
                    textAlign = textAlignValue,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    style = androidx.compose.ui.text.TextStyle(
                        brush = gradient,
                        shadow = shadowStyle,
                        letterSpacing = finalLetterSpacingSp,
                        lineHeight = finalLineHeightSp
                    )
                )
                if (isSelected && !isDrawingMode) {
                    LayerTransformHandles(
                        clipId = clip.id,
                        scale = sampled.scale,
                        rotation = sampled.rotation,
                        positionX = sampled.x,
                        positionY = sampled.y,
                        onPositionChanged = onPositionChanged,
                        onGestureStart = onGroupGestureStart,
                        onGestureEnd = onGroupGestureEnd,
                        onTransformChanged = onTransformChanged,
                        onDelete = onDeleteLayer,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  INTERACTIVE STICKER OVERLAY
// ═══════════════════════════════════════════════════════════════

@Composable
private fun InteractiveStickerOverlay(
    clip: EditorClip,
    stickerState: com.moody.moodyvideoeditor.data.StickerState,
    canvasW: Float, canvasH: Float,
    currentPosMs: Long,
    isSelected: Boolean, isMulti: Boolean = false,
    isDrawingMode: Boolean = false,
    onSelect: () -> Unit,
    onGroupGestureStart: () -> Unit,
    onGroupGestureEnd: () -> Unit,
    onGroupGesture: (String, Float, Float, Float, Float) -> Unit,
    onPositionChanged: (Float, Float) -> Unit,
    onTransformChanged: (Float, Float) -> Unit,
    onDeleteLayer: () -> Unit,
) {
    if (stickerState.emoji.isBlank()) return

    val localTimeSec = ((currentPosMs - clip.timelineStartMs) / 1000f).coerceAtLeast(0f)
    val sampled = TransformApplier.resolveLive(clip, localTimeSec)
    val animDur = stickerState.animationDuration.coerceAtLeast(0.1f)
    val progress = (localTimeSec / animDur).coerceIn(0f, 1f)
    val frame = try {
        com.moody.moodyvideoeditor.utils.AnimationsEngine.computeFrame(
            stickerState.animation, progress, localTimeSec
        )
    } catch (_: Throwable) {
        com.moody.moodyvideoeditor.utils.AnimationsEngine.Frame()
    }

    val density = LocalDensity.current
    val canvasWpx = with(density) { canvasW.dp.toPx() }
    val canvasHpx = with(density) { canvasH.dp.toPx() }

    val effStickerSizePx = TextScaler.fontSize(
        baseSize = 48, canvasWidthPx = canvasWpx
    )
    val clampedX = sampled.x.coerceIn(0f, 100f)
    val clampedY = sampled.y.coerceIn(0f, 100f)
    val borderColor = when {
        isSelected -> Color(0xFF60EFFF)
        isMulti -> Color(0xFFFFD166)
        else -> Color.Transparent
    }
    val effStickerSizeSp = (effStickerSizePx / density.density).sp

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val posTx = (clampedX - 50f) / 100f * canvasWpx
                    val posTy = (clampedY - 50f) / 100f * canvasHpx
                    translationX = posTx + frame.translateX
                    translationY = posTy + frame.translateY
                    scaleX = (sampled.scale / 100f) * frame.scaleX
                    scaleY = (sampled.scale / 100f) * frame.scaleY
                    rotationZ = sampled.rotation + frame.rotationZ
                    alpha = (stickerState.opacity / 100f) * frame.alpha
                    transformOrigin = TransformOrigin.Center
                }
                .then(
                    if (isSelected || isMulti) Modifier.border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(6.dp)
                    ) else Modifier
                )
                .padding(6.dp)
                .pointerInput(clip.id, isSelected, isMulti, canvasW, canvasH, isDrawingMode) {
                    if (isDrawingMode) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = true)
                        onGroupGestureStart()

                        val baseX = clampedX
                        val baseY = clampedY
                        val baseScale = sampled.scale
                        val baseRot = sampled.rotation
                        var accumPanX = 0f
                        var accumPanY = 0f
                        var accumZoom = 1f
                        var accumRot = 0f
                        var lastDist = 0f
                        var lastAngle = 0f
                        var hasMultiGesture = false

                        if (!isSelected && !isMulti) onSelect()

                        var continueGesture = true
                        while (continueGesture) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                continueGesture = false
                            } else {
                                if (pressed.size == 1) {
                                    val ch = pressed.first()
                                    val pan = ch.position - ch.previousPosition
                                    accumPanX += pan.x
                                    accumPanY += pan.y
                                    ch.consume()
                                } else if (pressed.size >= 2) {
                                    val c1 = pressed[0]
                                    val c2 = pressed[1]
                                    val d = c1.position - c2.position
                                    val dist = sqrt(d.x * d.x + d.y * d.y)
                                    val angle = atan2(d.y, d.x)
                                    if (hasMultiGesture && lastDist > 1f) {
                                        accumZoom *= dist / lastDist
                                        accumRot += Math.toDegrees(
                                            normalizeAngle(angle - lastAngle).toDouble()
                                        ).toFloat()
                                    }
                                    lastDist = dist
                                    lastAngle = angle
                                    hasMultiGesture = true
                                    c1.consume()
                                    c2.consume()
                                }
                                val rawX = baseX + accumPanX / canvasWpx * 100f
                                val rawY = baseY + accumPanY / canvasHpx * 100f
                                val cx = rawX.coerceIn(0f, 100f)
                                val cy = rawY.coerceIn(0f, 100f)
                                val newScale = (baseScale * accumZoom).coerceIn(10f, 500f)
                                val newRot = baseRot + accumRot

                                if (isMulti) {
                                    onGroupGesture(clip.id, cx, cy, newScale, newRot)
                                } else {
                                    onPositionChanged(cx, cy)
                                    onTransformChanged(newScale, newRot)
                                }
                            }
                        }
                        onGroupGestureEnd()
                    }
                }
                .pointerInput(clip.id, isMulti, isDrawingMode) {
                    if (isDrawingMode) return@pointerInput
                    detectTapGestures { if (!isMulti) onSelect() }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(text = stickerState.emoji, fontSize = effStickerSizeSp)
            if (isSelected && !isDrawingMode) {
                LayerTransformHandles(
                    clipId = clip.id,
                    scale = sampled.scale,
                    rotation = sampled.rotation,
                    positionX = sampled.x,
                    positionY = sampled.y,
                    onPositionChanged = onPositionChanged,
                    onGestureStart = onGroupGestureStart,
                    onGestureEnd = onGroupGestureEnd,
                    onTransformChanged = onTransformChanged,
                    onDelete = onDeleteLayer,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  TRANSFORM HANDLES
// ═══════════════════════════════════════════════════════════════

@Composable
private fun BoxScope.LayerTransformHandles(
    clipId: String,
    scale: Float, rotation: Float,
    widthFraction: Float = 1f, heightFraction: Float = 1f,
    positionX: Float, positionY: Float,
    onPositionChanged: (Float, Float) -> Unit,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    onTransformChanged: (Float, Float) -> Unit,
    onDelete: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth(widthFraction.coerceIn(0.01f, 1f))
            .fillMaxHeight(heightFraction.coerceIn(0.01f, 1f))
            .border(1.5.dp, Color(0xFF60EFFF), RoundedCornerShape(4.dp))
    ) {
        val density = LocalDensity.current
        val boundsWidthPx = with(density) { maxWidth.toPx() }
        val boundsHeightPx = with(density) { maxHeight.toPx() }
        val handleSizePx = with(density) { 22.dp.toPx() }

        TransformHandle(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-11).dp, y = (-11).dp),
            clipId = clipId,
            corner = TransformHandleCorner.Delete,
            boundsWidthPx = boundsWidthPx,
            boundsHeightPx = boundsHeightPx,
            handleSizePx = handleSizePx,
            scale = scale, rotation = rotation,
            positionX = positionX, positionY = positionY,
            onPositionChanged = onPositionChanged,
            onGestureStart = onGestureStart,
            onGestureEnd = onGestureEnd,
            onTransformChanged = onTransformChanged,
            onDelete = onDelete
        )
        TransformHandle(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-11).dp, y = 11.dp),
            clipId = clipId,
            corner = TransformHandleCorner.Rotate,
            boundsWidthPx = boundsWidthPx,
            boundsHeightPx = boundsHeightPx,
            handleSizePx = handleSizePx,
            scale = scale, rotation = rotation,
            positionX = positionX, positionY = positionY,
            onPositionChanged = onPositionChanged,
            onGestureStart = onGestureStart,
            onGestureEnd = onGestureEnd,
            onTransformChanged = onTransformChanged,
            onDelete = onDelete
        )
        TransformHandle(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 11.dp, y = 11.dp),
            clipId = clipId,
            corner = TransformHandleCorner.Scale,
            boundsWidthPx = boundsWidthPx,
            boundsHeightPx = boundsHeightPx,
            handleSizePx = handleSizePx,
            scale = scale, rotation = rotation,
            positionX = positionX, positionY = positionY,
            onPositionChanged = onPositionChanged,
            onGestureStart = onGestureStart,
            onGestureEnd = onGestureEnd,
            onTransformChanged = onTransformChanged,
            onDelete = onDelete
        )
    }
}

private enum class TransformHandleCorner {
    Delete, Rotate, Scale
}

@Composable
private fun TransformHandle(
    modifier: Modifier, clipId: String,
    corner: TransformHandleCorner,
    boundsWidthPx: Float, boundsHeightPx: Float, handleSizePx: Float,
    scale: Float, rotation: Float,
    positionX: Float, positionY: Float,
    onPositionChanged: (Float, Float) -> Unit,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    onTransformChanged: (Float, Float) -> Unit,
    onDelete: () -> Unit
) {
    val currentScale = rememberUpdatedState(scale)
    val currentRotation = rememberUpdatedState(rotation)
    val currentOnTransformChanged = rememberUpdatedState(onTransformChanged)
    val currentOnGestureStart = rememberUpdatedState(onGestureStart)
    val currentOnGestureEnd = rememberUpdatedState(onGestureEnd)
    val currentOnDelete = rememberUpdatedState(onDelete)

    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(
                when (corner) {
                    TransformHandleCorner.Delete -> Color(0xFFE5484D)
                    TransformHandleCorner.Rotate,
                    TransformHandleCorner.Scale -> Color(0xFF171A22)
                }
            )
            .border(1.dp, Color(0xFF60EFFF), CircleShape)
            .pointerInput(clipId, corner, boundsWidthPx, boundsHeightPx) {
                when (corner) {
                    TransformHandleCorner.Delete -> detectTapGestures {
                        currentOnDelete.value()
                    }

                    TransformHandleCorner.Rotate,
                    TransformHandleCorner.Scale -> awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        currentOnGestureStart.value()

                        val handleCenterOffsetX = if (
                            corner == TransformHandleCorner.Scale
                        ) boundsWidthPx - handleSizePx / 2f else -handleSizePx / 2f
                        val startX = handleCenterOffsetX + down.position.x -
                                boundsWidthPx / 2f
                        val startY = boundsHeightPx - handleSizePx + down.position.y -
                                boundsHeightPx / 2f
                        val startDistance = hypot(startX.toDouble(), startY.toDouble())
                            .toFloat().coerceAtLeast(1f)
                        val startAngle = atan2(startY, startX)
                        val startScale = currentScale.value
                        val startRotation = currentRotation.value
                        var previousAngle = startAngle
                        var accumulatedRotation = 0f
                        var dragX = 0f
                        var dragY = 0f

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            if (change == null || !change.pressed) break
                            val delta = change.position - change.previousPosition
                            dragX += delta.x
                            dragY += delta.y
                            change.consume()

                            val currentX = startX + dragX
                            val currentY = startY + dragY
                            val currentDistance = hypot(
                                currentX.toDouble(), currentY.toDouble()
                            ).toFloat()
                            val currentAngle = atan2(currentY, currentX)
                            accumulatedRotation += normalizeAngle(
                                currentAngle - previousAngle
                            )
                            previousAngle = currentAngle
                            if (corner == TransformHandleCorner.Scale) {
                                currentOnTransformChanged.value(
                                    (startScale * currentDistance / startDistance)
                                        .coerceIn(10f, 500f),
                                    startRotation
                                )
                            } else {
                                currentOnTransformChanged.value(
                                    startScale,
                                    startRotation + Math.toDegrees(
                                        accumulatedRotation.toDouble()
                                    ).toFloat()
                                )
                            }
                        } while (true)
                        currentOnGestureEnd.value()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when (corner) {
                TransformHandleCorner.Delete -> "×"
                TransformHandleCorner.Rotate -> "⟳"
                TransformHandleCorner.Scale -> "⤢"
            },
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun normalizeAngle(radians: Float): Float {
    val twoPi = (Math.PI * 2.0).toFloat()
    var normalized = radians
    while (normalized > Math.PI) normalized -= twoPi
    while (normalized < -Math.PI) normalized += twoPi
    return normalized
}
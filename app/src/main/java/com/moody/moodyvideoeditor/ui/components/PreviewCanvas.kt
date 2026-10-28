@file:OptIn(UnstableApi::class)

package com.moody.moodyvideoeditor.ui.components

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap

import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import com.moody.moodyvideoeditor.data.ColorFilterValues
import com.moody.moodyvideoeditor.data.ColorMatteMode
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.EffectState
import com.moody.moodyvideoeditor.data.FilterState
import com.moody.moodyvideoeditor.data.MaskAnimatableProps
import com.moody.moodyvideoeditor.data.MaskState
import com.moody.moodyvideoeditor.data.MaskType
import com.moody.moodyvideoeditor.data.OverlayState
import com.moody.moodyvideoeditor.data.SpotlightState
import com.moody.moodyvideoeditor.ui.features.SpotlightOverlay
import com.moody.moodyvideoeditor.utils.SpotlightAnimation
import com.moody.moodyvideoeditor.utils.BlurEngine
import com.moody.moodyvideoeditor.utils.ChromaKeyEngine
import com.moody.moodyvideoeditor.utils.ColorMatrixBuilder
import com.moody.moodyvideoeditor.utils.ColorWheelEngine
import com.moody.moodyvideoeditor.utils.ColorWheelPreviewFilter
import com.moody.moodyvideoeditor.utils.EffectsEngine
import com.moody.moodyvideoeditor.utils.FontLibrary
import com.moody.moodyvideoeditor.utils.ImageAdjustmentProcessor
import com.moody.moodyvideoeditor.utils.KeyframeStore
import com.moody.moodyvideoeditor.utils.MaskEngine
import com.moody.moodyvideoeditor.utils.OverlayEngine
import com.moody.moodyvideoeditor.utils.RatioHelper
import com.moody.moodyvideoeditor.utils.TextRenderContract
import com.moody.moodyvideoeditor.utils.TextScaler
import com.moody.moodyvideoeditor.utils.Transform2D
import com.moody.moodyvideoeditor.utils.TransformApplier
import com.moody.moodyvideoeditor.utils.TransitionRenderer
import com.moody.moodyvideoeditor.utils.applyTransparentCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import com.moody.moodyvideoeditor.utils.ChromaKeyHelper
import kotlinx.coroutines.delay
private const val HSL_SHARPEN_SHADER = """
uniform shader input;
uniform float2 uResolution;
uniform float uReds;
uniform float uOranges;
uniform float uYellows;
uniform float uGreens;
uniform float uCyans;
uniform float uBlues;
uniform float uPurples;
uniform float uMagentas;
uniform float uSkinTones;
uniform float uSharpen;

float3 rgb2hsv(float3 c) {
    float4 K = float4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    float4 p = mix(float4(c.bg, K.wz), float4(c.gb, K.xy), step(c.b, c.g));
    float4 q = mix(float4(p.xyw, c.r), float4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return float3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}
float hueWeight(float hue, float targetHue, float range, float sat) {
    float diff = abs(hue - targetHue);
    diff = min(diff, 1.0 - diff);
    float hueMatch = 1.0 - smoothstep(range * 0.25, range, diff);
    float satWeight = smoothstep(0.02, 0.15, sat);
    return hueMatch * satWeight;
}
half4 main(float2 fragCoord) {
    half4 pixel = input.eval(fragCoord);
    if (pixel.a < 0.01) return pixel;
    float3 rgb = float3(pixel.rgb) * 255.0;
    float3 hsv = rgb2hsv(float3(pixel.rgb));
    float h = hsv.x;
    float s = hsv.y;
    float r = rgb.r, g = rgb.g, b = rgb.b;
    if (abs(uReds) > 0.001) { float w = hueWeight(h, 0.0, 0.083, s); if (w > 0.001) { float amt = uReds * w; if (amt > 0.0) { r = r + (255.0 - r) * amt * 0.9; g = g * (1.0 - amt * 0.45); b = b * (1.0 - amt * 0.45); } else { float avg = (r + g + b) / 3.0; r = r + (avg - r) * (-amt) * 0.9; g = g + (avg - g) * (-amt) * 0.5; b = b + (avg - b) * (-amt) * 0.5; } } }
    if (abs(uOranges) > 0.001) { float w = hueWeight(h, 0.083, 0.070, s); if (w > 0.001) { float amt = uOranges * w; if (amt > 0.0) { r = r + (255.0 - r) * amt * 0.7; g = g + (180.0 - g) * amt * 0.5; b = b * (1.0 - amt * 0.5); } else { float avg = (r + g + b) / 3.0; r = r + (avg - r) * (-amt) * 0.7; g = g + (avg - g) * (-amt) * 0.7; } } }
    if (abs(uYellows) > 0.001) { float w = hueWeight(h, 0.167, 0.083, s); if (w > 0.001) { float amt = uYellows * w; if (amt > 0.0) { r = r + (255.0 - r) * amt * 0.75; g = g + (255.0 - g) * amt * 0.75; b = b * (1.0 - amt * 0.6); } else { float avg = (r + g + b) / 3.0; r = r + (avg - r) * (-amt) * 0.7; g = g + (avg - g) * (-amt) * 0.7; } } }
    if (abs(uGreens) > 0.001) { float w = hueWeight(h, 0.333, 0.125, s); if (w > 0.001) { float amt = uGreens * w; if (amt > 0.0) { g = g + (255.0 - g) * amt * 0.9; r = r * (1.0 - amt * 0.45); b = b * (1.0 - amt * 0.45); } else { float avg = (r + g + b) / 3.0; g = g + (avg - g) * (-amt) * 0.9; r = r + (avg - r) * (-amt) * 0.5; b = b + (avg - b) * (-amt) * 0.5; } } }
    if (abs(uCyans) > 0.001) { float w = hueWeight(h, 0.5, 0.083, s); if (w > 0.001) { float amt = uCyans * w; if (amt > 0.0) { g = g + (255.0 - g) * amt * 0.7; b = b + (255.0 - b) * amt * 0.7; r = r * (1.0 - amt * 0.6); } else { float avg = (r + g + b) / 3.0; g = g + (avg - g) * (-amt) * 0.7; b = b + (avg - b) * (-amt) * 0.7; } } }
    if (abs(uBlues) > 0.001) { float w = hueWeight(h, 0.667, 0.083, s); if (w > 0.001) { float amt = uBlues * w; if (amt > 0.0) { b = b + (255.0 - b) * amt * 0.9; r = r * (1.0 - amt * 0.45); g = g * (1.0 - amt * 0.45); } else { float avg = (r + g + b) / 3.0; b = b + (avg - b) * (-amt) * 0.9; r = r + (avg - r) * (-amt) * 0.5; g = g + (avg - g) * (-amt) * 0.5; } } }
    if (abs(uPurples) > 0.001) { float w = hueWeight(h, 0.778, 0.070, s); if (w > 0.001) { float amt = uPurples * w; if (amt > 0.0) { r = r + (220.0 - r) * amt * 0.7; b = b + (255.0 - b) * amt * 0.7; g = g * (1.0 - amt * 0.6); } else { float avg = (r + g + b) / 3.0; r = r + (avg - r) * (-amt) * 0.7; b = b + (avg - b) * (-amt) * 0.7; } } }
    if (abs(uMagentas) > 0.001) { float w = hueWeight(h, 0.833, 0.070, s); if (w > 0.001) { float amt = uMagentas * w; if (amt > 0.0) { r = r + (255.0 - r) * amt * 0.75; b = b + (255.0 - b) * amt * 0.75; g = g * (1.0 - amt * 0.6); } else { float avg = (r + g + b) / 3.0; r = r + (avg - r) * (-amt) * 0.7; b = b + (avg - b) * (-amt) * 0.7; } } }
    if (abs(uSkinTones) > 0.001) { float w = hueWeight(h, 0.069, 0.055, s); if (w > 0.001) { float amt = uSkinTones * w; r = clamp(r + amt * w * 30.0, 0.0, 255.0); g = clamp(g + amt * w * 15.0, 0.0, 255.0); } }
    float3 result = clamp(float3(r, g, b) / 255.0, 0.0, 1.0);
    if (uSharpen > 0.001) {
        float radius = 1.0 + uSharpen * 3.0;
        half4 l  = input.eval(fragCoord + float2(-radius, 0.0));
        half4 rr = input.eval(fragCoord + float2( radius, 0.0));
        half4 u  = input.eval(fragCoord + float2(0.0, -radius));
        half4 d  = input.eval(fragCoord + float2(0.0,  radius));
        half4 tl = input.eval(fragCoord + float2(-radius, -radius));
        half4 tr = input.eval(fragCoord + float2( radius, -radius));
        half4 bl = input.eval(fragCoord + float2(-radius,  radius));
        half4 br = input.eval(fragCoord + float2( radius,  radius));
        half4 sharpened = pixel * 9.0 - l - rr - u - d - tl - tr - bl - br;
        float strength = clamp(uSharpen * 2.0, 0.0, 3.0);
        result = mix(result, float3(sharpened.rgb), strength);
    }
    return half4(result * pixel.a, pixel.a);
}
"""

private fun blendKeyToCompose(key: String): BlendMode = when (key.lowercase()) {
    "multiply" -> BlendMode.Multiply
    "screen" -> BlendMode.Screen
    "overlay" -> BlendMode.Overlay
    "darken" -> BlendMode.Darken
    "lighten" -> BlendMode.Lighten
    "hardlight" -> BlendMode.Overlay
    "softlight" -> BlendMode.Softlight
    "colordodge" -> BlendMode.ColorDodge
    "colorburn" -> BlendMode.ColorBurn
    "vividlight" -> BlendMode.Overlay
    "linearlight" -> BlendMode.Overlay
    "pinlight" -> BlendMode.Overlay
    "difference" -> BlendMode.Difference
    "exclusion" -> BlendMode.Exclusion
    "subtract" -> BlendMode.Difference
    "divide" -> BlendMode.Difference
    "add" -> BlendMode.Plus
    "lineardodge" -> BlendMode.Plus
    "hue" -> BlendMode.Hue
    "saturation" -> BlendMode.Saturation
    "color" -> BlendMode.Color
    "luminosity" -> BlendMode.Luminosity
    else -> BlendMode.SrcOver
}

private fun blendOpacityFactor(clip: EditorClip): Float =
    if (clip.blendMode.lowercase() == "normal") 1f
    else (clip.blendOpacity / 100f).coerceIn(0f, 1f)

private fun buildClipMatrix(
    clip: EditorClip,
    globalMatrix: android.graphics.ColorMatrix,
    applyGlobal: Boolean,
    colorWheelMatrix: android.graphics.ColorMatrix? = null,
    useExactColorWheels: Boolean = false
): android.graphics.ColorMatrix? {
    val cm = android.graphics.ColorMatrix()

    val hasClipAdjustments = ColorMatrixBuilder.hasRealTimeAdjustments(clip.adjustments)
    if (hasClipAdjustments) {
        cm.postConcat(ColorMatrixBuilder.build(clip.adjustments))
    }
    val ownFilter = ColorFilterValues(
        brightness = clip.filters.brightness, contrast = clip.filters.contrast,
        saturation = clip.filters.saturation, hue = clip.filters.hue,
        grayscale = clip.filters.grayscale, sepia = clip.filters.sepia,
        invert = clip.filters.invert, blur = clip.filters.blur, opacity = clip.filters.opacity
    )
    if (EffectsEngine.hasColorEffect(ownFilter)) {
        cm.postConcat(EffectsEngine.buildColorMatrix(ownFilter))
    }
    if (applyGlobal) cm.postConcat(globalMatrix)
    if (!useExactColorWheels) {
        ColorWheelEngine.buildColorMatrix(clip.colorWheel)?.let(cm::postConcat)
        colorWheelMatrix?.let(cm::postConcat)
    }
    val hasChange = hasClipAdjustments || EffectsEngine.hasColorEffect(ownFilter) ||
            (clip.colorWheel.hasAnyChange && !useExactColorWheels) || applyGlobal ||
            (colorWheelMatrix != null && !useExactColorWheels)
    return if (hasChange) cm else null
}

private fun colorWheelStatesForClip(clip: EditorClip, activeWheels: List<EditorClip>) = buildList {
    if (clip.colorWheel.hasAnyChange) add(clip.colorWheel)
    activeWheels.filter { it.trackIndex > clip.trackIndex && it.colorWheel.hasAnyChange }
        .forEach { add(it.colorWheel) }
}

private fun colorWheelMatrixForClip(
    clip: EditorClip,
    activeWheels: List<EditorClip>
): android.graphics.ColorMatrix? {
    val applicableWheels =
        activeWheels.filter { it.trackIndex > clip.trackIndex && it.colorWheel.hasAnyChange }
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
    if (containerWidth <= 0f || containerHeight <= 0f || !contentAspectRatio.isFinite() || contentAspectRatio <= 0f) return 1f to 1f
    val containerAspectRatio = containerWidth / containerHeight
    return if (contentAspectRatio >= containerAspectRatio) 1f to (containerAspectRatio / contentAspectRatio) else (contentAspectRatio / containerAspectRatio) to 1f
}

private fun computeClipBlurRadiusDp(
    clips: List<EditorClip>,
    clip: EditorClip,
    currentPosMs: Long
): Float {
    val effectAbove =
        clips.firstOrNull { e -> e.isEffectClip && e.trackIndex > clip.trackIndex && currentPosMs >= e.timelineStartMs && currentPosMs < e.timelineEndMs && e.effectState?.blur != null }
            ?: return 0f
    val cfg = effectAbove.effectState?.blur ?: return 0f
    val localSec = ((currentPosMs - effectAbove.timelineStartMs) / 1000f).coerceAtLeast(0f)
    val durSec = (effectAbove.durationMs / 1000f).coerceAtLeast(0.1f)
    return BlurEngine.computeRadiusDp(cfg, localSec, durSec)
}

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
    previewMatte: com.moody.moodyvideoeditor.data.PreviewMatteState? = null,
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
    onGroupGesture: (String, Float, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
    onTextPositionChanged: (String, Float, Float) -> Unit = { _, _, _ -> },
    onTextTransformChanged: (String, Float, Float) -> Unit = { _, _, _ -> },
    onStickerPositionChanged: (String, Float, Float) -> Unit = { _, _, _ -> },
    onStickerTransformChanged: (String, Float, Float) -> Unit = { _, _, _ -> },
    onLayerPositionChanged: (String, Float, Float) -> Unit = { _, _, _ -> },
    onLayerTransformChanged: (String, Float, Float) -> Unit = { _, _, _ -> },
    onVisualizerPositionChanged: (String, Float, Float) -> Unit = { _, _, _ -> },
    onVisualizerTransformChanged: (String, Float, Float) -> Unit = { _, _, _ -> },
    onSpotlightChanged: (String, SpotlightState) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current

    val previewMatteClip: EditorClip? = previewMatte?.let { pm ->
        EditorClip(
            id = "__preview_matte__", uri = android.net.Uri.EMPTY, name = "Preview Matte",
            type = "matte/plain", sourceStartMs = 0L, sourceEndMs = 3_600_000L,
            timelineStartMs = -1_000_000L, trackIndex = 9999, isAudio = false,
            sourceTotalMs = Long.MAX_VALUE, matteStyle = pm.style,
            filters = FilterState(opacity = pm.opacity)
        )
    }

    val displayClips: List<EditorClip> =
        if (previewMatteClip != null) clips + previewMatteClip else clips

    val activeClips = displayClips.filter {
        !it.isAudio && !it.isAdjustmentClip && !it.isEffectClip &&
                !it.isFilterLayerClip && !it.isOverlayClip &&
                currentPosMs >= it.timelineStartMs && currentPosMs < it.timelineEndMs &&
                !hiddenVisualTracks.contains(it.trackIndex)
    }.sortedWith(compareBy({ it.trackIndex }, { it.timelineStartMs }))

    val topVideoClip = activeClips.filter { it.isVisualClip && !it.type.startsWith("image/") }
        .maxByOrNull { it.trackIndex }
    val activeVisual = activeClips.maxByOrNull { it.trackIndex }
    val videoTrackIdx = activeVisual?.trackIndex ?: 0

    val activeTransitionClip = remember(currentPosMs, clips) {
        clips.filter { c ->
            !c.isAudio && c.transition != null && c.transition.isActive &&
                    currentPosMs >= c.timelineStartMs && currentPosMs < c.timelineStartMs + c.transition.durationMs
        }.maxByOrNull { it.trackIndex }
    }

    val outgoingClip = remember(activeTransitionClip?.id, clips) {
        activeTransitionClip?.let { tc ->
            clips.filter {
                it.isVisualClip && it.id != tc.id &&
                        it.trackIndex == tc.trackIndex &&
                        it.timelineEndMs <= tc.timelineStartMs + 50L
            }.maxByOrNull { it.timelineEndMs }
        }
    }
    val latestPreviewPositionMs by rememberUpdatedState(currentPosMs)

    val transitionBitmapCache = remember { mutableMapOf<String, Bitmap>() }

    LaunchedEffect(clips.map { it.id + "_" + (it.transition?.key ?: "none") }) {
        withContext(Dispatchers.IO) {
            clips.forEach { rightClip ->
                if (rightClip.transition?.isActive == true) {
                    val outgoing = clips.filter {
                        it.isVisualClip && it.id != rightClip.id &&
                                it.trackIndex == rightClip.trackIndex &&
                                it.timelineEndMs <= rightClip.timelineStartMs + 50L
                    }.maxByOrNull { it.timelineEndMs }

                    if (outgoing != null && !transitionBitmapCache.containsKey(outgoing.id)) {
                        val bmp = try {
                            if (outgoing.type.startsWith("image/")) {
                                context.contentResolver.openInputStream(outgoing.uri)
                                    ?.use { stream ->
                                        android.graphics.BitmapFactory.decodeStream(stream)
                                    }
                            } else {
                                val retriever = android.media.MediaMetadataRetriever()
                                retriever.setDataSource(context, outgoing.uri)
                                val timeUs = (outgoing.sourceEndMs - 33).coerceAtLeast(0L) * 1000L
                                val b = retriever.getFrameAtTime(
                                    timeUs,
                                    android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                                )
                                retriever.release()
                                b
                            }
                        } catch (e: Exception) {
                            null
                        }

                        if (bmp != null) {
                            withContext(Dispatchers.Main) {
                                transitionBitmapCache[outgoing.id] = bmp
                            }
                        }
                    }
                }
            }
        }
    }
    var outgoingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(
        outgoingClip?.id,
        activeTransitionClip?.id,
        activeTransitionClip?.transition?.key,
        activeTransitionClip?.transition?.durationMs
    ) {
        val oc = outgoingClip ?: return@LaunchedEffect
        val transitionState = activeTransitionClip?.transition ?: return@LaunchedEffect
        outgoingBitmap?.takeIf { !it.isRecycled }?.recycle()
        outgoingBitmap = null

        if (oc.type.startsWith("image/")) {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(oc.uri)
                        ?.use { stream -> android.graphics.BitmapFactory.decodeStream(stream) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("TRANSITION", "Could not load outgoing image frame", e)
                    null
                }
            }
            outgoingBitmap = bitmap
            return@LaunchedEffect
        }

        val retriever = MediaMetadataRetriever()
        try {
            withContext(Dispatchers.IO) {
                retriever.setDataSource(context, oc.uri)
            }
            var lastFrameTimeMs = Long.MIN_VALUE
            while (true) {
                val nowMs = latestPreviewPositionMs
                val elapsedMs = (nowMs - activeTransitionClip.timelineStartMs)
                    .coerceIn(0L, transitionState.durationMs)
                val sourceTransitionDurationMs =
                    (transitionState.durationMs * oc.speed.coerceAtLeast(0.01f)).toLong()
                val frameTimeMs = (
                        oc.sourceEndMs - sourceTransitionDurationMs +
                                (elapsedMs * oc.speed.coerceAtLeast(0.01f)).toLong()
                        ).coerceIn(
                        oc.sourceStartMs,
                        (oc.sourceEndMs - 1L).coerceAtLeast(oc.sourceStartMs)
                    )

                if (frameTimeMs != lastFrameTimeMs) {
                    val bitmap = withContext(Dispatchers.IO) {
                        retriever.getFrameAtTime(
                            frameTimeMs * 1000L,
                            MediaMetadataRetriever.OPTION_CLOSEST
                        )
                    }
                    if (bitmap != null) {
                        outgoingBitmap?.takeIf { !it.isRecycled }?.recycle()
                        outgoingBitmap = bitmap
                    } else {
                        Log.w("TRANSITION", "No outgoing video frame at $frameTimeMs ms")
                    }
                    lastFrameTimeMs = frameTimeMs
                }
                kotlinx.coroutines.delay(50L)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("TRANSITION", "Could not render outgoing transition frames", e)
        } finally {
            retriever.release()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            outgoingBitmap?.takeIf { !it.isRecycled }?.recycle()
            transitionBitmapCache.values.forEach { if (!it.isRecycled) it.recycle() }
            transitionBitmapCache.clear()
        }
    }

    val selectedClip = clips.firstOrNull { it.id == selectedClipId }
    val maskToRender: MaskState? = if ((isMaskPenMode || isMaskHandMode) && selectedClip != null) {
        val timeSec = ((currentPosMs - selectedClip.timelineStartMs) / 1000f).coerceAtLeast(0f)
        MaskEngine.sampleAt(selectedClip.mask, timeSec)
    } else null

    val activeAdjustment = clips.filter {
        it.isAdjustmentClip && currentPosMs >= it.timelineStartMs && currentPosMs < it.timelineEndMs &&
                !hiddenVisualTracks.contains(it.trackIndex)
    }.maxByOrNull { it.trackIndex }?.adjustments

    val activeColorWheels = clips.filter {
        it.isAdjustmentClip && it.colorWheel.hasAnyChange &&
                currentPosMs >= it.timelineStartMs && currentPosMs < it.timelineEndMs &&
                !hiddenVisualTracks.contains(it.trackIndex)
    }.sortedBy { it.trackIndex }

    val activeEffects = EffectsEngine.getEffectsAbove(clips, currentPosMs, videoTrackIdx)
    val timeSec = currentPosMs / 1000f

    val motionFrames = activeEffects.mapNotNull { clip ->
        clip.effectState?.motion?.let {
            EffectsEngine.computeMotion(
                it,
                timeSec
            )
        }
    }.toMutableList()
    previewEffectState?.motion?.let { motionFrames.add(EffectsEngine.computeMotion(it, timeSec)) }
    val combinedMotion = EffectsEngine.combineMotions(motionFrames)

    val previewBlurDp = remember(previewEffectState?.blur) {
        previewEffectState?.blur?.let { cfg ->
            val i = cfg.intensity.coerceIn(0f, 100f) / 100f; (20f * i).coerceIn(0f, 25f)
        } ?: 0f
    }

    val filterList = mutableListOf<ColorFilterValues>()
    activeEffects.forEach { clip ->
        clip.effectState?.filters?.let { filterList.add(it) }
        clip.effectState?.animatedColor?.let { acCfg ->
            filterList.add(
                com.moody.moodyvideoeditor.utils.AnimatedColorEngine.compute(
                    acCfg,
                    timeSec
                )
            )
        }
    }
    previewEffectState?.filters?.let { filterList.add(it) }
    previewEffectState?.animatedColor?.let { acCfg ->
        filterList.add(
            com.moody.moodyvideoeditor.utils.AnimatedColorEngine.compute(
                acCfg,
                timeSec
            )
        )
    }

    val combinedFilter =
        if (filterList.isNotEmpty()) EffectsEngine.combineFilters(filterList) else null

    val previewOverlays = previewEffectState?.overlay?.let {
        listOf(
            OverlayState(
                type = it.type,
                intensity = it.intensity,
                color = it.color,
                color2 = it.color2,
                size = it.size,
                opacity = it.opacity,
                speed = it.speed,
                shape = it.shape,
                glowEnabled = it.glowEnabled,
                glowRadius = it.glowRadius,
                glowColor = it.glowColor
            )
        )
    } ?: emptyList()

    val allOverlays =
        EffectsEngine.collectActiveOverlays(clips, currentPosMs, videoTrackIdx).toMutableList()
            .apply { addAll(previewOverlays) }

    val activeFilterLayer = clips.filter {
        it.isFilterLayerClip && currentPosMs >= it.timelineStartMs && currentPosMs < it.timelineEndMs && !hiddenVisualTracks.contains(
            it.trackIndex
        )
    }.maxByOrNull { it.trackIndex }

    val filterLayerHasColorEffect = activeFilterLayer?.filters?.let { filters ->
        EffectsEngine.hasColorEffect(
            ColorFilterValues(
                brightness = filters.brightness,
                contrast = filters.contrast,
                saturation = filters.saturation,
                hue = filters.hue,
                grayscale = filters.grayscale,
                sepia = filters.sepia,
                invert = filters.invert,
                blur = filters.blur,
                opacity = filters.opacity
            )
        )
    } ?: false
    val previewHasColorEffect = previewFilters?.let { filters ->
        EffectsEngine.hasColorEffect(
            ColorFilterValues(
                brightness = filters.brightness,
                contrast = filters.contrast,
                saturation = filters.saturation,
                hue = filters.hue,
                grayscale = filters.grayscale,
                sepia = filters.sepia,
                invert = filters.invert,
                blur = filters.blur,
                opacity = filters.opacity
            )
        )
    } ?: false
    val hasAdjustments =
        activeAdjustment?.let { ColorMatrixBuilder.hasRealTimeAdjustments(it) } ?: false
    val hasFilters = filterList.any(EffectsEngine::hasColorEffect)
    val applyMatrix = hasAdjustments || hasFilters ||
            filterLayerHasColorEffect || previewHasColorEffect

    val combinedMatrix =
        remember(activeAdjustment, filterList, activeFilterLayer?.filters, previewFilters) {
            val cm = android.graphics.ColorMatrix()
            if (hasAdjustments && activeAdjustment != null) cm.postConcat(
                ColorMatrixBuilder.build(
                    activeAdjustment
                )
            )
            filterList.forEach { filter ->
                if (EffectsEngine.hasColorEffect(filter)) {
                    cm.postConcat(EffectsEngine.buildColorMatrix(filter))
                }
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
                if (EffectsEngine.hasColorEffect(cfv)) cm.postConcat(
                    EffectsEngine.buildColorMatrix(
                        cfv
                    )
                )
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
                if (EffectsEngine.hasColorEffect(cfv)) cm.postConcat(
                    EffectsEngine.buildColorMatrix(
                        cfv
                    )
                )
            }
            cm
        }

    val opacityAlpha = EffectsEngine.opacityAlpha(combinedFilter)

    val adjustmentEffectModifier = remember(
        activeAdjustment?.reds,
        activeAdjustment?.oranges,
        activeAdjustment?.yellows,
        activeAdjustment?.greens,
        activeAdjustment?.cyans,
        activeAdjustment?.blues,
        activeAdjustment?.purples,
        activeAdjustment?.magentas,
        activeAdjustment?.skinTones,
        activeAdjustment?.sharpen
    ) {
        val adj = activeAdjustment
        val needsShader =
            adj != null && (adj.reds != 0f || adj.oranges != 0f || adj.yellows != 0f || adj.greens != 0f || adj.cyans != 0f || adj.blues != 0f || adj.purples != 0f || adj.magentas != 0f || adj.skinTones != 0f || adj.sharpen > 0f)
        if (needsShader && Build.VERSION.SDK_INT >= 33) {
            try {
                val shader = android.graphics.RuntimeShader(HSL_SHARPEN_SHADER)
                shader.setFloatUniform("uReds", (adj!!.reds / 100f).coerceIn(-5f, 5f))
                shader.setFloatUniform("uOranges", (adj.oranges / 100f).coerceIn(-5f, 5f))
                shader.setFloatUniform("uYellows", (adj.yellows / 100f).coerceIn(-5f, 5f))
                shader.setFloatUniform("uGreens", (adj.greens / 100f).coerceIn(-5f, 5f))
                shader.setFloatUniform("uCyans", (adj.cyans / 100f).coerceIn(-5f, 5f))
                shader.setFloatUniform("uBlues", (adj.blues / 100f).coerceIn(-5f, 5f))
                shader.setFloatUniform("uPurples", (adj.purples / 100f).coerceIn(-5f, 5f))
                shader.setFloatUniform("uMagentas", (adj.magentas / 100f).coerceIn(-5f, 5f))
                shader.setFloatUniform("uSkinTones", (adj.skinTones / 100f).coerceIn(-5f, 5f))
                shader.setFloatUniform("uSharpen", (adj.sharpen / 100f).coerceIn(0f, 5f))
                Modifier.graphicsLayer {
                    shader.setFloatUniform("uResolution", size.width, size.height)
                    renderEffect =
                        android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "input")
                            .asComposeRenderEffect()

                }
            } catch (t: Throwable) {
                Modifier
            }
        } else Modifier
    }

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
            canvasW = windowW; canvasH = windowW / targetRatio
        } else {
            canvasH = windowH; canvasW = windowH * targetRatio
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
                    clip.isVisualClip && !clip.type.startsWith("image/") -> {
                        if (clip.id != topVideoClip?.id) return@forEach
                        val videoLocalSec =
                            ((currentPosMs - clip.timelineStartMs) / 1000f).coerceAtLeast(0f)
                        val videoTransform2 = TransformApplier.resolveLive(clip, videoLocalSec)
                        val videoAspect: Float = remember(clip.uri) {
                            try {
                                val retriever = MediaMetadataRetriever()
                                retriever.setDataSource(context, clip.uri)
                                val w = retriever.extractMetadata(
                                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                                )?.toFloatOrNull() ?: 0f
                                val h = retriever.extractMetadata(
                                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                                )?.toFloatOrNull() ?: 0f
                                val rot = retriever.extractMetadata(
                                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                                )?.toIntOrNull() ?: 0
                                retriever.release()
                                if (w > 0f && h > 0f) {
                                    val raw = w / h
                                    if (rot == 90 || rot == 270) 1f / raw else raw
                                } else canvasW / canvasH
                            } catch (_: Throwable) {
                                canvasW / canvasH
                            }
                        }
                        val videoBounds = fittedBoundsFractions(canvasW, canvasH, videoAspect)
                        val useExactColorWheels = Build.VERSION.SDK_INT >= 33
                        val videoWheelStates = remember(clip.colorWheel, activeColorWheels) {
                            colorWheelStatesForClip(
                                clip,
                                activeColorWheels
                            )
                        }

                        val videoWheelNative = remember(videoWheelStates) {
                            if (useExactColorWheels) ColorWheelPreviewFilter.createRenderEffect(
                                videoWheelStates
                            ) else null
                        }
                        val useExactVideoColorWheels =
                            useExactColorWheels && (videoWheelStates.isEmpty() || videoWheelNative != null)
                        val videoWheelMatrix = remember(
                            clip.id,
                            activeColorWheels,
                            useExactVideoColorWheels
                        ) {
                            if (useExactVideoColorWheels) null else colorWheelMatrixForClip(
                                clip,
                                activeColorWheels
                            )
                        }
                        val videoBlurDp =
                            remember(currentPosMs, clip.id, clip.trackIndex, previewBlurDp) {
                                maxOf(
                                    computeClipBlurRadiusDp(clips, clip, currentPosMs),
                                    previewBlurDp
                                )
                            }
                        val videoColorMatrix = buildClipMatrix(
                            clip,
                            combinedMatrix,
                            applyMatrix,
                            videoWheelMatrix,
                            useExactVideoColorWheels
                        )
                        val useNativeSurface =
                            videoColorMatrix == null &&
                                    videoWheelNative == null &&
                                    clip.adjustments.isDefault &&
                                    clip.filters.isDefault &&
                                    clip.colorWheel.isDefault &&
                                    activeAdjustment == null &&
                                    activeColorWheels.isEmpty() &&
                                    activeFilterLayer == null &&
                                    activeEffects.isEmpty() &&
                                    previewEffectState == null &&
                                    previewFilters == null &&
                                    videoBlurDp <= 0.5f &&
                                    !clip.chromaKey.isActive &&
                                    !maskAtClipTime(clip, currentPosMs).isActive &&
                                    opacityAlpha >= 1f &&
                                    clip.filters.opacity >= 100f &&
                                    clip.blendMode.equals("normal", ignoreCase = true) &&
                                    blendOpacityFactor(clip) >= 1f &&
                                    rotation == 0 &&
                                    videoTransform2.isIdentity() &&
                                    combinedMotion == EffectsEngine.MotionFrame() &&
                                    activeTransitionClip == null &&
                                    !TransformApplier.hasAnimatedTransform(clip) &&
                                    activeClips.none { it.id != clip.id && it.isVisualClip } &&
                                    activeClips.none { it.isSpotlightClip } &&
                                    allOverlays.isEmpty()

                        MaskedClipContent(mask = maskAtClipTime(clip, currentPosMs)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(adjustmentEffectModifier)
                                    .graphicsLayer {
                                        compositingStrategy =
                                            if (clip.blendMode.equals("normal", ignoreCase = true)) {
                                                CompositingStrategy.Auto
                                            } else {
                                                CompositingStrategy.Offscreen
                                            }

                                        blendMode = blendKeyToCompose(clip.blendMode)

                                        val w = size.width;
                                        val h = size.height
                                        val posTx = (videoTransform2.x - 50f) / 100f * w
                                        val posTy = (videoTransform2.y - 50f) / 100f * h
                                        val transT: Transform2D =
                                            if (activeTransitionClip != null) {
                                                val st = activeTransitionClip.transition!!
                                                val prog =
                                                    ((currentPosMs - activeTransitionClip.timelineStartMs).toFloat() / st.durationMs.coerceAtLeast(
                                                        1L
                                                    )).coerceIn(0f, 1f)
                                                TransitionRenderer.getIncomingTransform(
                                                    st.key,
                                                    prog,
                                                    w,
                                                    h
                                                )
                                            } else Transform2D()

                                        translationX =
                                            posTx + combinedMotion.tx + transT.tx
                                        translationY =
                                            posTy + combinedMotion.ty + transT.ty
                                        scaleX =
                                            (videoTransform2.scale / 100f) * combinedMotion.scale * transT.scaleX
                                        scaleY =
                                            (videoTransform2.scale / 100f) * combinedMotion.scale * transT.scaleY
                                        rotationZ =
                                            videoTransform2.rotation + combinedMotion.rotation + transT.rotZ
                                        transformOrigin = TransformOrigin(
                                            pivotFractionX = videoTransform2.anchorX / 100f,
                                            pivotFractionY = videoTransform2.anchorY / 100f
                                        )
                                        alpha =
                                            opacityAlpha * transT.alpha * blendOpacityFactor(clip)
                                        this.clip = true
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .fillMaxWidth(videoBounds.first.coerceIn(0.01f, 1f))
                                        .fillMaxHeight(videoBounds.second.coerceIn(0.01f, 1f))
                                        .applyTransparentCrop(
                                            videoTransform2.cropL,
                                            videoTransform2.cropR,
                                            videoTransform2.cropT,
                                            videoTransform2.cropB
                                        )
                                        .then(
                                            if (videoBlurDp > 0.5f) Modifier.blur(
                                                radius = videoBlurDp.dp,
                                                edgeTreatment = BlurredEdgeTreatment.Unbounded
                                            ) else Modifier
                                        )
                                ) {
                                    // 🟩 VIDEO CHROMA KEY LIVE PREVIEW
                                    if (clip.chromaKey.isActive) {
                                        var chromaProcessedBmp by remember(clip.id) { mutableStateOf<Bitmap?>(null) }
                                        var textureViewRef by remember(clip.id) { mutableStateOf<TextureView?>(null) }

                                        LaunchedEffect(
                                            clip.id,
                                            clip.chromaKey,
                                            isPlaying,
                                            if (isPlaying) null else currentPosMs
                                        ) {
                                            if (!isPlaying) chromaProcessedBmp = null
                                            var pausedCaptureAttempts = 0
                                            while (isPlaying || pausedCaptureAttempts < 20) {
                                                val tv = textureViewRef
                                                if (tv != null && tv.isAvailable && tv.width > 0) {
                                                    try {
                                                        val frame = tv.bitmap
                                                        if (frame != null) {
                                                            chromaProcessedBmp =
                                                                ChromaKeyHelper.applyChromaKey(
                                                                    frame,
                                                                    clip.chromaKey
                                                                )
                                                        }
                                                    } catch (e: Exception) {
                                                        android.util.Log.w(
                                                            "ChromaKeyPreview",
                                                            "Failed to process video frame",
                                                            e
                                                        )
                                                    }
                                                }

                                                if (!isPlaying) {
                                                    if (chromaProcessedBmp != null) break
                                                    pausedCaptureAttempts++
                                                }
                                                delay(100)
                                            }
                                        }

                                        AndroidView(
                                            factory = { ctx ->
                                                LayoutInflater.from(ctx).inflate(
                                                    com.moody.moodyvideoeditor.R.layout.view_player,
                                                    null
                                                ) as PlayerView
                                            },
                                            update = { view ->
                                                view.player = exoPlayer
                                                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                                                view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                                view.alpha = if (chromaProcessedBmp == null) 1f else 0f
                                                val sv = view.videoSurfaceView
                                                if (sv is TextureView) {
                                                    textureViewRef = sv
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        if (chromaProcessedBmp != null) {
                                            Image(
                                                bitmap = chromaProcessedBmp!!.asImageBitmap(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                    } else {
                                        if (useNativeSurface) {
                                            AndroidView(
                                                factory = { ctx ->
                                                    LayoutInflater.from(ctx).inflate(
                                                        com.moody.moodyvideoeditor.R.layout.view_player_surface,
                                                        null
                                                    ) as PlayerView
                                                },
                                                update = { view ->
                                                    view.player = exoPlayer
                                                    view.resizeMode =
                                                        AspectRatioFrameLayout.RESIZE_MODE_FILL
                                                    view.rotation = rotation.toFloat()
                                                    view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            AndroidView(
                                                factory = { ctx ->
                                                    LayoutInflater.from(ctx).inflate(
                                                        com.moody.moodyvideoeditor.R.layout.view_player,
                                                        null
                                                    ) as PlayerView
                                                },
                                                update = { view ->
                                                    view.player = exoPlayer
                                                    view.resizeMode =
                                                        AspectRatioFrameLayout.RESIZE_MODE_FILL
                                                    view.rotation = rotation.toFloat()
                                                    view.setBackgroundColor(android.graphics.Color.TRANSPARENT)

                                                    val sv = view.videoSurfaceView
                                                    if (sv is TextureView) {
                                                        if (videoColorMatrix != null) {
                                                            val paint = Paint().apply {
                                                                colorFilter =
                                                                    android.graphics.ColorMatrixColorFilter(
                                                                        videoColorMatrix
                                                                    )
                                                            }
                                                            sv.setLayerType(
                                                                View.LAYER_TYPE_HARDWARE,
                                                                paint
                                                            )
                                                        } else {
                                                            sv.setLayerType(View.LAYER_TYPE_NONE, null)
                                                        }

                                                        sv.isOpaque = videoWheelNative == null
                                                        if (Build.VERSION.SDK_INT >= 31) {
                                                            sv.setRenderEffect(videoWheelNative)
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                                if (clip.id == selectedClipId) {
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
                                                clip.id,
                                                x,
                                                y,
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
                    clip.isVisualClip && clip.type.startsWith("image/") -> {
                        val imgLocalSec =
                            ((currentPosMs - clip.timelineStartMs) / 1000f).coerceAtLeast(0f)
                        val imgTransform = TransformApplier.resolveLive(clip, imgLocalSec)
                        val isSelected = clip.id == selectedClipId
                        val isMulti = clip.id in multiSelectedIds
                        val borderColor = when {
                            isSelected -> Color(0xFF60EFFF); isMulti -> Color(0xFFFFD166); else -> Color.Transparent
                        }
                        val imageBlurDp =
                            remember(currentPosMs, clip.id, clip.trackIndex, previewBlurDp) {
                                maxOf(
                                    computeClipBlurRadiusDp(clips, clip, currentPosMs),
                                    previewBlurDp
                                )
                            }

                        val adjForImage = activeAdjustment ?: clip.adjustments
                        val needsCpuAdj =
                            ImageAdjustmentProcessor.hasSharpenOrColorChannels(adjForImage)
                        val adjKey = remember(
                            adjForImage.reds,
                            adjForImage.oranges,
                            adjForImage.yellows,
                            adjForImage.greens,
                            adjForImage.cyans,
                            adjForImage.blues,
                            adjForImage.purples,
                            adjForImage.magentas,
                            adjForImage.skinTones,
                            adjForImage.sharpen
                        ) { "${adjForImage.reds}_${adjForImage.oranges}_${adjForImage.yellows}_${adjForImage.greens}_${adjForImage.cyans}_${adjForImage.blues}_${adjForImage.purples}_${adjForImage.magentas}_${adjForImage.skinTones}_${adjForImage.sharpen}" }

                        var loadedBitmap by remember(clip.uri) {
                            mutableStateOf<android.graphics.Bitmap?>(
                                null
                            )
                        }
                        var processedImageBitmap by remember(clip.uri) {
                            mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(
                                null
                            )
                        }

                        LaunchedEffect(clip.uri) {
                            loadedBitmap = withContext(Dispatchers.IO) {
                                try {
                                    context.contentResolver.openInputStream(clip.uri)
                                        ?.use { android.graphics.BitmapFactory.decodeStream(it) }
                                } catch (_: Throwable) {
                                    null
                                }
                            }
                        }
                        LaunchedEffect(loadedBitmap, adjKey, needsCpuAdj) {
                            val src = loadedBitmap ?: return@LaunchedEffect
                            if (!needsCpuAdj) {
                                processedImageBitmap = src.asImageBitmap(); return@LaunchedEffect
                            }
                            kotlinx.coroutines.delay(70)
                            processedImageBitmap = withContext(Dispatchers.IO) {
                                ImageAdjustmentProcessor.process(
                                    src,
                                    adjForImage
                                ).asImageBitmap()
                            }
                        }
                        DisposableEffect(clip.uri) {
                            onDispose {
                                loadedBitmap?.takeIf { !it.isRecycled }?.recycle()
                            }
                        }

                        MaskedClipContent(mask = maskAtClipTime(clip, currentPosMs)) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .graphicsLayer {
                                        compositingStrategy = CompositingStrategy.Offscreen
                                        blendMode = blendKeyToCompose(clip.blendMode)

                                        val w = size.width;
                                        val h = size.height
                                        val posTx = (imgTransform.x - 50f) / 100f * w
                                        val posTy = (imgTransform.y - 50f) / 100f * h
                                        val transT: Transform2D =
                                            if (activeTransitionClip != null && activeTransitionClip.id == clip.id) {
                                                val st = activeTransitionClip.transition!!
                                                val prog =
                                                    ((currentPosMs - activeTransitionClip.timelineStartMs).toFloat() / st.durationMs.coerceAtLeast(
                                                        1L
                                                    )).coerceIn(0f, 1f)
                                                TransitionRenderer.getIncomingTransform(
                                                    st.key,
                                                    prog,
                                                    w,
                                                    h
                                                )
                                            } else Transform2D()
                                        translationX =
                                            posTx + combinedMotion.tx + transT.tx
                                        translationY =
                                            posTy + combinedMotion.ty + transT.ty
                                        scaleX =
                                            (imgTransform.scale / 100f) * combinedMotion.scale * transT.scaleX
                                        scaleY =
                                            (imgTransform.scale / 100f) * combinedMotion.scale * transT.scaleY
                                        rotationZ =
                                            imgTransform.rotation + combinedMotion.rotation + transT.rotZ
                                        transformOrigin = TransformOrigin(
                                            pivotFractionX = imgTransform.anchorX / 100f,
                                            pivotFractionY = imgTransform.anchorY / 100f
                                        )
                                        alpha =
                                            opacityAlpha * transT.alpha * blendOpacityFactor(clip)
                                        this.clip = true
                                    }
                                    .then(
                                        if (isMulti) Modifier.border(
                                            width = 1.dp,
                                            color = borderColor,
                                            shape = RoundedCornerShape(4.dp)
                                        ) else Modifier
                                    )
                                    .pointerInput(clip.id, isSelected, isMulti) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = true)
                                            onGroupGestureStart()
                                            val baseX = imgTransform.x;
                                            val baseY = imgTransform.y
                                            val baseScale = imgTransform.scale;
                                            val baseRot = imgTransform.rotation
                                            var accumPanX = 0f;
                                            var accumPanY = 0f;
                                            var accumZoom = 1f;
                                            var accumRot = 0f
                                            var lastDist = 0f;
                                            var lastAngle = 0f;
                                            var hasMulti = false
                                            if (!isSelected && !isMulti) onClipSelected(clip.id)
                                            var continueGesture = true
                                            while (continueGesture) {
                                                val event = awaitPointerEvent()
                                                val pressed = event.changes.filter { it.pressed }
                                                if (pressed.isEmpty()) {
                                                    continueGesture = false
                                                } else {
                                                    if (pressed.size == 1) {
                                                        val ch = pressed.first();
                                                        val pan = ch.position - ch.previousPosition
                                                        accumPanX += pan.x; accumPanY += pan.y; ch.consume()
                                                    } else if (pressed.size >= 2) {
                                                        val c1 = pressed[0];
                                                        val c2 = pressed[1]
                                                        val d = c1.position - c2.position
                                                        val dist =
                                                            kotlin.math.sqrt(d.x * d.x + d.y * d.y)
                                                        val angle = kotlin.math.atan2(d.y, d.x)
                                                        if (hasMulti && lastDist > 1f) {
                                                            accumZoom *= dist / lastDist
                                                            accumRot += Math.toDegrees(
                                                                normalizeAngle(angle - lastAngle).toDouble()
                                                            ).toFloat()
                                                        }
                                                        lastDist = dist; lastAngle =
                                                            angle; hasMulti = true
                                                        c1.consume(); c2.consume()
                                                    }
                                                    val newX =
                                                        (baseX + accumPanX / size.width * 100f).coerceIn(
                                                            0f,
                                                            100f
                                                        )
                                                    val newY =
                                                        (baseY + accumPanY / size.height * 100f).coerceIn(
                                                            0f,
                                                            100f
                                                        )
                                                    val newScale =
                                                        (baseScale * accumZoom).coerceIn(10f, 500f)
                                                    val newRot = baseRot + accumRot
                                                    onGroupGesture(
                                                        clip.id,
                                                        newX,
                                                        newY,
                                                        newScale,
                                                        newRot
                                                    )
                                                }
                                            }
                                            onGroupGestureEnd()
                                        }
                                    }
                                    .pointerInput(clip.id, isMulti) {
                                        detectTapGestures { if (!isMulti) onClipSelected(clip.id) }
                                    }
                            ) {
                                val imageIntrinsicSize = processedImageBitmap?.let {
                                    androidx.compose.ui.geometry.Size(
                                        it.width.toFloat(),
                                        it.height.toFloat()
                                    )
                                }
                                val imageAspect =
                                    if (imageIntrinsicSize != null && imageIntrinsicSize.width > 0f && imageIntrinsicSize.height > 0f) imageIntrinsicSize.width / imageIntrinsicSize.height else canvasW / canvasH
                                val imageBounds =
                                    fittedBoundsFractions(canvasW, canvasH, imageAspect)
                                val useExactImageColorWheels = Build.VERSION.SDK_INT >= 33
                                val imageWheelStates = remember(
                                    clip.colorWheel,
                                    activeColorWheels
                                ) { colorWheelStatesForClip(clip, activeColorWheels) }

                                val imageWheelNative = remember(imageWheelStates) {
                                    if (useExactImageColorWheels) ColorWheelPreviewFilter.createRenderEffect(
                                        imageWheelStates
                                    ) else null
                                }
                                val useExactImageWheels =
                                    useExactImageColorWheels && (imageWheelStates.isEmpty() || imageWheelNative != null)
                                // 🟩 CHROMA KEY HELPER APPLY KARO
                                var finalImageToRender = processedImageBitmap
                                if (clip.chromaKey.isActive && processedImageBitmap != null) {
                                    val bmp = processedImageBitmap!!.asAndroidBitmap()
                                    val keyedBmp = ChromaKeyHelper.applyChromaKey(bmp, clip.chromaKey)
                                    finalImageToRender = keyedBmp.asImageBitmap()
                                }

                                val imgColorFilter = remember(
                                    clip.filters,
                                    clip.adjustments,
                                    combinedMatrix,
                                    applyMatrix,
                                    activeColorWheels,
                                    clip.id,
                                    useExactImageWheels
                                ) {
                                    val cm = buildClipMatrix(
                                        clip,
                                        combinedMatrix,
                                        applyMatrix,
                                        if (useExactImageWheels) null else colorWheelMatrixForClip(
                                            clip,
                                            activeColorWheels
                                        ),
                                        useExactImageWheels
                                    )
                                    if (cm == null) null else androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                        androidx.compose.ui.graphics.ColorMatrix(cm.array)
                                    )
                                }

                                val imageWheelRenderEffect = remember(imageWheelNative) {
                                    imageWheelNative?.asComposeRenderEffect()
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .fillMaxWidth(imageBounds.first.coerceIn(0.01f, 1f))
                                        .fillMaxHeight(imageBounds.second.coerceIn(0.01f, 1f))
                                        .applyTransparentCrop(
                                            imgTransform.cropL,
                                            imgTransform.cropR,
                                            imgTransform.cropT,
                                            imgTransform.cropB
                                        )
                                        .then(
                                            if (imageBlurDp > 0.5f) Modifier.blur(
                                                radius = imageBlurDp.dp,
                                                edgeTreatment = BlurredEdgeTreatment.Unbounded
                                            ) else Modifier
                                        )
                                ) {
                                    if (finalImageToRender != null) {
                                        Image(
                                            bitmap = finalImageToRender!!,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            colorFilter = imgColorFilter,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer {
                                                    renderEffect = imageWheelRenderEffect
                                                }
                                        )
                                    } else {
                                        val imageRequest = remember(clip.uri) {
                                            ImageRequest.Builder(context).data(clip.uri)
                                                .decoderFactory(BitmapFactoryDecoder.Factory())
                                                .allowHardware(false).crossfade(false).build()
                                        }
                                        val imgPainter = rememberAsyncImagePainter(imageRequest)
                                        Image(
                                            painter = imgPainter,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            colorFilter = imgColorFilter,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer {
                                                    renderEffect = imageWheelRenderEffect
                                                }
                                        )
                                    }
                                }
                                if (isSelected) {
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
                                                clip.id,
                                                x,
                                                y,
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

                    clip.isSpotlightClip -> {
                        val localTimeSec = KeyframeStore.clipLocalTimeSeconds(
                            currentPosMs,
                            clip.timelineStartMs,
                            clip.durationMs
                        )
                        val spotlight = SpotlightAnimation.sample(
                            clip.spotlight ?: SpotlightState(),
                            clip.keyframes,
                            localTimeSec
                        )
                        SpotlightOverlay(
                            spotlight = spotlight,
                            isSelected = clip.id == selectedClipId,
                            onGestureStart = onGroupGestureStart,
                            onGestureEnd = onGroupGestureEnd,
                            onChange = { onSpotlightChanged(clip.id, it) }
                        )
                    }

                    clip.isColorMatteClip -> {
                        val localSec =
                            ((currentPosMs - clip.timelineStartMs) / 1000f).coerceAtLeast(0f)
                        val matteTransform = TransformApplier.resolveLive(clip, localSec)
                        val matteAlpha = (clip.filters.opacity / 100f).coerceIn(0f, 1f)
                        val isSel = clip.id == selectedClipId
                        val isMulti = clip.id in multiSelectedIds
                        val borderColor = when {
                            isSel -> Color(0xFF60EFFF); isMulti -> Color(0xFFFFD166); else -> Color.Transparent
                        }

                        MaskedClipContent(mask = maskAtClipTime(clip, currentPosMs)) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .graphicsLayer {
                                        compositingStrategy = CompositingStrategy.Offscreen
                                        blendMode = blendKeyToCompose(clip.blendMode)

                                        // 🟩 CHROMA KEY YAHAN APPLY KARO
                                        if (clip.chromaKey.isActive && Build.VERSION.SDK_INT >= 33) {
                                            val eff = ChromaKeyEngine.createRenderEffect(clip.chromaKey)
                                            if (eff != null) {
                                                renderEffect = eff.asComposeRenderEffect()
                                            }
                                        }

                                        val w = size.width;
                                        val h = size.height
                                        val posTx = (matteTransform.x - 50f) / 100f * w
                                        val posTy = (matteTransform.y - 50f) / 100f * h
                                        translationX = posTx + combinedMotion.tx
                                        translationY = posTy + combinedMotion.ty
                                        scaleX =
                                            (matteTransform.scale / 100f) * combinedMotion.scale
                                        scaleY =
                                            (matteTransform.scale / 100f) * combinedMotion.scale
                                        rotationZ =
                                            matteTransform.rotation + combinedMotion.rotation
                                        transformOrigin = TransformOrigin(
                                            pivotFractionX = matteTransform.anchorX / 100f,
                                            pivotFractionY = matteTransform.anchorY / 100f
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
                                    .pointerInput(clip.id, isSel, isMulti) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = true)
                                            onGroupGestureStart()
                                            val baseX = matteTransform.x;
                                            val baseY = matteTransform.y
                                            val baseScale = matteTransform.scale;
                                            val baseRot = matteTransform.rotation
                                            var accumPanX = 0f;
                                            var accumPanY = 0f;
                                            var accumZoom = 1f;
                                            var accumRot = 0f
                                            var lastDist = 0f;
                                            var lastAngle = 0f;
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
                                                        val ch = pressed.first();
                                                        val pan = ch.position - ch.previousPosition
                                                        accumPanX += pan.x; accumPanY += pan.y; ch.consume()
                                                    } else if (pressed.size >= 2) {
                                                        val c1 = pressed[0];
                                                        val c2 = pressed[1]
                                                        val d = c1.position - c2.position
                                                        val dist =
                                                            kotlin.math.sqrt(d.x * d.x + d.y * d.y)
                                                        val angle = kotlin.math.atan2(d.y, d.x)
                                                        if (hasMulti && lastDist > 1f) {
                                                            accumZoom *= dist / lastDist
                                                            accumRot += Math.toDegrees(
                                                                normalizeAngle(angle - lastAngle).toDouble()
                                                            ).toFloat()
                                                        }
                                                        lastDist = dist; lastAngle =
                                                            angle; hasMulti = true
                                                        c1.consume(); c2.consume()
                                                    }
                                                    val newX =
                                                        (baseX + accumPanX / size.width * 100f).coerceIn(
                                                            0f,
                                                            100f
                                                        )
                                                    val newY =
                                                        (baseY + accumPanY / size.height * 100f).coerceIn(
                                                            0f,
                                                            100f
                                                        )
                                                    val newScale =
                                                        (baseScale * accumZoom).coerceIn(10f, 500f)
                                                    val newRot = baseRot + accumRot
                                                    if (isMulti) onGroupGesture(
                                                        clip.id,
                                                        newX,
                                                        newY,
                                                        newScale,
                                                        newRot
                                                    )
                                                    else {
                                                        onLayerPositionChanged(
                                                            clip.id,
                                                            newX,
                                                            newY
                                                        ); onLayerTransformChanged(
                                                            clip.id,
                                                            newScale,
                                                            newRot
                                                        )
                                                    }
                                                }
                                            }
                                            onGroupGestureEnd()
                                        }
                                    }
                                    .pointerInput(clip.id, isMulti) {
                                        detectTapGestures { if (!isMulti) onClipSelected(clip.id) }
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .applyTransparentCrop(
                                            matteTransform.cropL,
                                            matteTransform.cropR,
                                            matteTransform.cropT,
                                            matteTransform.cropB
                                        )
                                ) {
                                    ColorMatteRenderer(
                                        style = clip.matteStyle,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                if (isSel) {
                                    LayerTransformHandles(
                                        clipId = clip.id,
                                        scale = matteTransform.scale,
                                        rotation = matteTransform.rotation,
                                        positionX = matteTransform.x,
                                        positionY = matteTransform.y,
                                        onPositionChanged = { x, y ->
                                            onLayerPositionChanged(
                                                clip.id,
                                                x,
                                                y
                                            )
                                        },
                                        onGestureStart = onGroupGestureStart,
                                        onGestureEnd = onGroupGestureEnd,
                                        onTransformChanged = { s, r ->
                                            onLayerTransformChanged(
                                                clip.id,
                                                s,
                                                r
                                            )
                                        },
                                        onDelete = { onDeleteLayer(clip.id) },
                                    )
                                }
                            }
                        }
                    }
                    clip.isTextClip -> {
                        val st = clip.textState ?: return@forEach
                        MaskedClipContent(mask = maskAtClipTime(clip, currentPosMs)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        compositingStrategy =
                                            CompositingStrategy.Offscreen; blendMode =
                                        blendKeyToCompose(clip.blendMode); alpha =
                                        blendOpacityFactor(clip)
                                    }) {
                                InteractiveTextOverlay(
                                    clip = clip,
                                    textState = st,
                                    canvasW = canvasW,
                                    canvasH = canvasH,
                                    currentPosMs = currentPosMs,
                                    isSelected = clip.id == selectedClipId,
                                    isMulti = clip.id in multiSelectedIds,
                                    onSelect = { onClipSelected(clip.id) },
                                    onGroupGestureStart = onGroupGestureStart,
                                    onGroupGestureEnd = onGroupGestureEnd,
                                    onGroupGesture = onGroupGesture,
                                    onPositionChanged = { x, y ->
                                        onTextPositionChanged(
                                            clip.id,
                                            x,
                                            y
                                        )
                                    },
                                    onTransformChanged = { s, r ->
                                        onTextTransformChanged(
                                            clip.id,
                                            s,
                                            r
                                        )
                                    },
                                    onDeleteLayer = { onDeleteLayer(clip.id) },
                                )
                            }
                        }
                    }

                    clip.isStickerClip -> {
                        val ss = clip.stickerState ?: return@forEach
                        MaskedClipContent(mask = maskAtClipTime(clip, currentPosMs)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        compositingStrategy =
                                            CompositingStrategy.Offscreen; blendMode =
                                        blendKeyToCompose(clip.blendMode); alpha =
                                        blendOpacityFactor(clip)
                                    }) {
                                InteractiveStickerOverlay(
                                    clip = clip,
                                    stickerState = ss,
                                    canvasW = canvasW,
                                    canvasH = canvasH,
                                    currentPosMs = currentPosMs,
                                    isSelected = clip.id == selectedClipId,
                                    isMulti = clip.id in multiSelectedIds,
                                    onSelect = { onClipSelected(clip.id) },
                                    onGroupGestureStart = onGroupGestureStart,
                                    onGroupGestureEnd = onGroupGestureEnd,
                                    onGroupGesture = onGroupGesture,
                                    onPositionChanged = { x, y ->
                                        onStickerPositionChanged(
                                            clip.id,
                                            x,
                                            y
                                        )
                                    },
                                    onTransformChanged = { s, r ->
                                        onStickerTransformChanged(
                                            clip.id,
                                            s,
                                            r
                                        )
                                    },
                                    onDeleteLayer = { onDeleteLayer(clip.id) },
                                )
                            }
                        }
                    }

                    clip.isVisualizerClip -> {
                        val vs = clip.visualizer ?: return@forEach
                        val minDimDp = minOf(canvasW, canvasH)
                        val radiusDp = vs.size * minDimDp
                        val diameterDp = radiusDp * 2f
                        val leftDp = vs.positionX * canvasW - radiusDp
                        val topDp = vs.positionY * canvasH - radiusDp
                        val isSel = clip.id == selectedClipId
                        val isMulti = clip.id in multiSelectedIds

                        Box(modifier = Modifier.fillMaxSize()) {
                            MaskedClipContent(mask = maskAtClipTime(clip, currentPosMs)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            compositingStrategy =
                                                CompositingStrategy.Offscreen; blendMode =
                                            blendKeyToCompose(clip.blendMode)
                                        }) {
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
                            Box(
                                modifier = Modifier
                                    .offset(x = leftDp.dp, y = topDp.dp)
                                    .size(diameterDp.dp)
                                    .then(
                                        if (isSel || isMulti) Modifier.border(
                                            width = if (isSel) 2.dp else 1.dp,
                                            color = if (isSel) Color(0xFF60EFFF) else Color(
                                                0xFFFFD166
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) else Modifier
                                    )
                                    .pointerInput(
                                        clip.id,
                                        isSel,
                                        isMulti,
                                        canvasW,
                                        canvasH,
                                    ) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = true)
                                            onGroupGestureStart()
                                            val baseX = vs.positionX * 100f;
                                            val baseY = vs.positionY * 100f
                                            val baseScale = vs.size / 0.32f * 100f;
                                            val baseRot = vs.rotation
                                            var accumPanX = 0f;
                                            var accumPanY = 0f;
                                            var accumZoom = 1f;
                                            var accumRot = 0f
                                            var lastDist = 0f;
                                            var lastAngle = 0f;
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
                                                        val ch = pressed.first();
                                                        val pan = ch.position - ch.previousPosition
                                                        accumPanX += pan.x; accumPanY += pan.y; ch.consume()
                                                    } else if (pressed.size >= 2) {
                                                        val c1 = pressed[0];
                                                        val c2 = pressed[1]
                                                        val d = c1.position - c2.position
                                                        val dist =
                                                            kotlin.math.sqrt(d.x * d.x + d.y * d.y)
                                                        val angle = kotlin.math.atan2(d.y, d.x)
                                                        if (hasMulti && lastDist > 1f) {
                                                            accumZoom *= dist / lastDist
                                                            accumRot += Math.toDegrees(
                                                                normalizeAngle(angle - lastAngle).toDouble()
                                                            ).toFloat()
                                                        }
                                                        lastDist = dist; lastAngle =
                                                            angle; hasMulti = true
                                                        c1.consume(); c2.consume()
                                                    }
                                                    val newX =
                                                        (baseX + accumPanX / size.width * 100f).coerceIn(
                                                            0f,
                                                            100f
                                                        )
                                                    val newY =
                                                        (baseY + accumPanY / size.height * 100f).coerceIn(
                                                            0f,
                                                            100f
                                                        )
                                                    val newScale =
                                                        (baseScale * accumZoom).coerceIn(10f, 500f)
                                                    val newRot = baseRot + accumRot
                                                    if (isMulti) onGroupGesture(
                                                        clip.id,
                                                        newX,
                                                        newY,
                                                        newScale,
                                                        newRot
                                                    )
                                                    else {
                                                        onVisualizerPositionChanged(
                                                            clip.id,
                                                            newX,
                                                            newY
                                                        ); onVisualizerTransformChanged(
                                                            clip.id,
                                                            newScale,
                                                            newRot
                                                        )
                                                    }
                                                }
                                            }
                                            onGroupGestureEnd()
                                        }
                                    }
                                    .pointerInput(clip.id, isMulti) {
                                        detectTapGestures { if (!isMulti) onClipSelected(clip.id) }
                                    }
                            ) {
                                if (isSel) {
                                    LayerTransformHandles(
                                        clipId = clip.id,
                                        scale = (vs.size / 0.32f) * 100f,
                                        rotation = vs.rotation,
                                        positionX = vs.positionX * 100f,
                                        positionY = vs.positionY * 100f,
                                        onPositionChanged = { x, y ->
                                            onVisualizerPositionChanged(
                                                clip.id,
                                                x,
                                                y
                                            )
                                        },
                                        onGestureStart = onGroupGestureStart,
                                        onGestureEnd = onGroupGestureEnd,
                                        onTransformChanged = { s, r ->
                                            onVisualizerTransformChanged(
                                                clip.id,
                                                s,
                                                r
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

            if (allOverlays.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    allOverlays.forEach { ov -> OverlayEngine.draw(this, timeSec, ov) }
                }
            }

            val activeEdgeGlows =
                clips.filter { c -> c.isEffectClip && c.trackIndex > videoTrackIdx && currentPosMs >= c.timelineStartMs && currentPosMs < c.timelineEndMs && c.effectState?.edgeGlow != null }
                    .sortedBy { it.trackIndex }.mapNotNull { it.effectState?.edgeGlow }
            val previewEdgeGlow = previewEffectState?.edgeGlow
            val allEdgeGlows =
                if (previewEdgeGlow != null) activeEdgeGlows + previewEdgeGlow else activeEdgeGlows

            if (allEdgeGlows.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    allEdgeGlows.forEach { glow ->
                        try {
                            val frame = EffectsEngine.computeEdgeGlow(glow, timeSec)
                            val intensity = (glow.intensity / 100f) * frame.intensityMul
                            val alpha = (intensity * frame.alpha).coerceIn(0f, 1f)
                            if (alpha < 0.02f) return@forEach
                            val baseColor = frame.color
                            val thickness = (glow.radius * frame.radiusMul).coerceIn(
                                4f,
                                minOf(size.width, size.height) * 0.45f
                            )
                            val ox = frame.offsetX;
                            val oy = frame.offsetY
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        baseColor.copy(
                                            alpha = alpha
                                        ), Color.Transparent
                                    ), startY = 0f, endY = thickness
                                ), topLeft = Offset(ox, oy), size = Size(size.width, thickness)
                            )
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        baseColor.copy(alpha = alpha)
                                    ), startY = size.height - thickness, endY = size.height
                                ),
                                topLeft = Offset(ox, size.height - thickness + oy),
                                size = Size(size.width, thickness)
                            )
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        baseColor.copy(
                                            alpha = alpha
                                        ), Color.Transparent
                                    ), startX = 0f, endX = thickness
                                ), topLeft = Offset(ox, oy), size = Size(thickness, size.height)
                            )
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        baseColor.copy(alpha = alpha)
                                    ), startX = size.width - thickness, endX = size.width
                                ),
                                topLeft = Offset(size.width - thickness + ox, oy),
                                size = Size(thickness, size.height)
                            )
                        } catch (_: Throwable) {
                        }
                    }
                }
            }

            val activeLightLeaks =
                clips.filter { c -> c.isEffectClip && c.trackIndex > videoTrackIdx && currentPosMs >= c.timelineStartMs && currentPosMs < c.timelineEndMs && c.effectState?.lightLeak != null }
                    .sortedBy { it.trackIndex }.mapNotNull { it.effectState?.lightLeak }
            val previewLightLeak = previewEffectState?.lightLeak
            val allLightLeaks =
                if (previewLightLeak != null) activeLightLeaks + previewLightLeak else activeLightLeaks

            if (allLightLeaks.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    allLightLeaks.forEach { leak ->
                        try {
                            val frame =
                                com.moody.moodyvideoeditor.utils.LightLeakEngine.computeFrame(
                                    leak,
                                    timeSec
                                )
                            val intensity = (leak.intensity / 100f) * frame.intensityMul
                            val alpha = (intensity * frame.alpha * 0.85f).coerceIn(0f, 1f)
                            if (alpha < 0.02f) return@forEach
                            val cx = frame.posX * size.width;
                            val cy = frame.posY * size.height
                            val maxDim = maxOf(size.width, size.height)
                            val r = frame.radius * maxDim
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        frame.color1.copy(
                                            alpha = alpha
                                        ),
                                        frame.color2.copy(alpha = alpha * 0.5f),
                                        Color.Transparent
                                    ), center = Offset(cx, cy), radius = r
                                ), radius = r, center = Offset(cx, cy), blendMode = BlendMode.Screen
                            )
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
            val activeLightning =
                clips.filter { c -> c.isEffectClip && c.trackIndex > videoTrackIdx && currentPosMs >= c.timelineStartMs && currentPosMs < c.timelineEndMs && c.effectState?.lightning != null }
                    .sortedBy { it.trackIndex }.mapNotNull { c ->
                        val cfg = c.effectState?.lightning ?: return@mapNotNull null
                        val localSec = ((currentPosMs - c.timelineStartMs) / 1000f).coerceAtLeast(0f)
                        val flash =
                            com.moody.moodyvideoeditor.utils.LightningEngine.flashAt(cfg, localSec)
                        if (flash <= 0.01f) null else Triple(cfg, flash, localSec)
                    }
            val previewLightning = previewEffectState?.lightning?.let { cfg ->
                val localSec = currentPosMs / 1000f
                val flash = com.moody.moodyvideoeditor.utils.LightningEngine.flashAt(cfg, localSec)
                if (flash <= 0.01f) null else Triple(cfg, flash, localSec)
            }
            val allLightning =
                if (previewLightning != null) activeLightning + previewLightning else activeLightning

            if (allLightning.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    allLightning.forEach { (cfg, flash, localSec) ->
                        val flashColor = Color(cfg.color)
                        drawRect(
                            color = flashColor.copy(alpha = (flash * 0.35f).coerceIn(0f, 1f)),
                            size = size,
                            blendMode = BlendMode.Screen
                        )
                        val cycleIdx =
                            kotlin.math.floor(localSec * cfg.frequency.coerceIn(0.2f, 5f)).toInt()
                        val segments =
                            com.moody.moodyvideoeditor.utils.LightningEngine.generateBolt(
                                cfg = cfg,
                                cycleIndex = cycleIdx,
                                W = size.width,
                                H = size.height
                            )
                        val trunkW = (size.width / 100f) * (1f + flash * 1.5f)
                        segments.forEach { seg ->
                            drawLine(
                                color = flashColor.copy(
                                    alpha = (seg.alpha * flash * 0.35f).coerceIn(
                                        0f,
                                        1f
                                    )
                                ),
                                start = Offset(seg.x1, seg.y1),
                                end = Offset(seg.x2, seg.y2),
                                strokeWidth = trunkW * seg.thickness * 3.2f,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                blendMode = BlendMode.Screen
                            )
                        }
                        segments.forEach { seg ->
                            drawLine(
                                color = flashColor.copy(
                                    alpha = (seg.alpha * flash * 0.75f).coerceIn(
                                        0f,
                                        1f
                                    )
                                ),
                                start = Offset(seg.x1, seg.y1),
                                end = Offset(seg.x2, seg.y2),
                                strokeWidth = trunkW * seg.thickness * 1.8f,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                blendMode = BlendMode.Screen
                            )
                        }
                        segments.forEach { seg ->
                            drawLine(
                                color = Color.White.copy(
                                    alpha = (flash * 0.95f).coerceIn(
                                        0f,
                                        1f
                                    )
                                ),
                                start = Offset(seg.x1, seg.y1),
                                end = Offset(seg.x2, seg.y2),
                                strokeWidth = trunkW * seg.thickness * 0.7f,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }
                    }
                }
            }

            val activeParticles =
                clips.filter { c -> c.isEffectClip && c.trackIndex > videoTrackIdx && currentPosMs >= c.timelineStartMs && currentPosMs < c.timelineEndMs && c.effectState?.particle != null }
                    .sortedBy { it.trackIndex }.mapNotNull { c ->
                        val cfg = c.effectState?.particle ?: return@mapNotNull null;
                        val localSec =
                            ((currentPosMs - c.timelineStartMs) / 1000f).coerceAtLeast(0f); cfg to localSec
                    }
            val previewParticle =
                previewEffectState?.particle?.let { cfg -> cfg to (currentPosMs / 1000f) }
            val allParticles =
                if (previewParticle != null) activeParticles + previewParticle else activeParticles

            if (allParticles.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    allParticles.forEach { (cfg, localSec) ->
                        try {
                            drawParticlePreview(
                                cfg = cfg,
                                localSec = localSec,
                                W = size.width,
                                H = size.height
                            )
                        } catch (_: Throwable) {
                        }
                    }
                }
            }

            val activeFire =
                clips.filter { c -> c.isEffectClip && c.trackIndex > videoTrackIdx && currentPosMs >= c.timelineStartMs && currentPosMs < c.timelineEndMs && c.effectState?.fire != null }
                    .sortedBy { it.trackIndex }.mapNotNull { c ->
                        val cfg = c.effectState?.fire ?: return@mapNotNull null;
                        val localSec =
                            ((currentPosMs - c.timelineStartMs) / 1000f).coerceAtLeast(0f); cfg to localSec
                    }
            val previewFire = previewEffectState?.fire?.let { cfg -> cfg to (currentPosMs / 1000f) }
            val allFire = if (previewFire != null) activeFire + previewFire else activeFire

            if (allFire.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    allFire.forEach { (cfg, localSec) ->
                        try {
                            drawIntoCanvas { canvas ->
                                com.moody.moodyvideoeditor.utils.FireEngine.drawOnCanvas(
                                    canvas = canvas.nativeCanvas,
                                    cfg = cfg,
                                    localTimeSec = localSec,
                                    W = size.width.toInt(),
                                    H = size.height.toInt()
                                )
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            }

            val activeDust =
                clips.filter { c -> c.isEffectClip && c.trackIndex > videoTrackIdx && currentPosMs >= c.timelineStartMs && currentPosMs < c.timelineEndMs && c.effectState?.dust != null }
                    .sortedBy { it.trackIndex }.mapNotNull { c ->
                        val cfg = c.effectState?.dust ?: return@mapNotNull null;
                        val localSec =
                            ((currentPosMs - c.timelineStartMs) / 1000f).coerceAtLeast(0f); cfg to localSec
                    }
            val previewDust = previewEffectState?.dust?.let { cfg -> cfg to (currentPosMs / 1000f) }
            val allDust = if (previewDust != null) activeDust + previewDust else activeDust

            if (allDust.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    allDust.forEach { (cfg, localSec) ->
                        try {
                            drawIntoCanvas { canvas ->
                                com.moody.moodyvideoeditor.utils.DustEngine.drawOnCanvas(
                                    canvas = canvas.nativeCanvas,
                                    cfg = cfg,
                                    localTimeSec = localSec,
                                    W = size.width.toInt(),
                                    H = size.height.toInt()
                                )
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            }

            activeAdjustment?.let { adj ->
                if (adj.vignette != 0f) {
                    val intensity = (adj.vignette / 100f).coerceIn(-1f, 1f);
                    val absIntensity = kotlin.math.abs(intensity)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val W = size.width;
                        val H = size.height;
                        val cx = W / 2f;
                        val cy = H / 2f
                        val maxR = kotlin.math.sqrt((W / 2f) * (W / 2f) + (H / 2f) * (H / 2f))
                        if (intensity < 0f) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent,
                                        0.35f to Color.Transparent,
                                        0.98f to Color.Black.copy(alpha = absIntensity * 0.75f),
                                        1.0f to Color.Black.copy(alpha = absIntensity * 0.95f)
                                    ), center = Offset(cx, cy), radius = maxR
                                ), radius = maxR, center = Offset(cx, cy)
                            )
                        } else {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent,
                                        0.35f to Color.Transparent,
                                        0.98f to Color.White.copy(alpha = absIntensity * 0.35f),
                                        1.0f to Color.White.copy(alpha = absIntensity * 0.5f)
                                    ), center = Offset(cx, cy), radius = maxR
                                ), radius = maxR, center = Offset(cx, cy)
                            )
                        }
                    }
                }
                if (adj.noise > 0f) {
                    val intensity = (adj.noise / 100f).coerceIn(0f, 1f);
                    val frameSeed = (currentPosMs / 33).toLong()
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val W = size.width;
                        val H = size.height
                        fun hashPixel(x: Int, y: Int, seed: Long): Float {
                            var h = seed; h = h * 31 + x; h = h * 31 + y; h = h xor (h ushr 13); h =
                                h * 0x5bd1e995; h = h xor (h ushr 15)
                            return ((h and 0xFFFF).toFloat() / 65535f)
                        }

                        val grainSize = 2.0f;
                        val cols = (W / grainSize).toInt().coerceAtLeast(1);
                        val rows = (H / grainSize).toInt().coerceAtLeast(1)
                        for (row in 0 until rows) {
                            for (col in 0 until cols) {
                                val rnd = hashPixel(col, row, frameSeed)
                                if (rnd > 0.45f - intensity * 0.30f) {
                                    val rnd2 = hashPixel(col + 1000, row + 1000, frameSeed)
                                    val brightness = (rnd2 * 255).toInt()
                                    val alpha = (intensity * 0.7f * (0.4f + rnd2 * 0.6f)).coerceIn(
                                        0f,
                                        0.85f
                                    )
                                    drawRect(
                                        color = Color(
                                            brightness,
                                            brightness,
                                            brightness,
                                            (alpha * 255).toInt().coerceIn(0, 255)
                                        ),
                                        topLeft = Offset(col * grainSize, row * grainSize),
                                        size = Size(grainSize, grainSize)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (activeTransitionClip != null) {
                val oc = outgoingClip
                if (oc != null) {
                    val transBitmap = outgoingBitmap ?: transitionBitmapCache[oc.id]
                    if (transBitmap != null && !transBitmap.isRecycled) {
                        val ts = activeTransitionClip.transition!!
                        val progress =
                            ((currentPosMs - activeTransitionClip.timelineStartMs).toFloat() / ts.durationMs.coerceAtLeast(
                                1L
                            )).coerceIn(0f, 1f)

                        val density = LocalDensity.current
                        val pixelW = with(density) { canvasW.dp.toPx() }
                        val pixelH = with(density) { canvasH.dp.toPx() }

                        TransitionRenderer.Render(oc, transBitmap, ts.key, progress, pixelW, pixelH)
                    }
                }
            }

            if ((isMaskPenMode || isMaskHandMode) && selectedClip != null) {
                MaskPenOverlay(
                    maskState = maskToRender ?: MaskState(type = MaskType.CUSTOM),
                    canvasW = canvasW, canvasH = canvasH, isHandMode = isMaskHandMode,
                    onGestureStart = onMaskGestureStart, onGestureEnd = onMaskGestureEnd,
                    onTapAddPoint = onMaskPointAdd, onAnchorMove = onMaskAnchorMove,
                    onHandleMove = onMaskHandleMove, onTogglePoint = onMaskPointToggle,
                    onDeletePoint = onMaskPointDelete, onClosePath = onClosePath,
                    onMaskMove = onMaskMove, onMaskStateChanged = onMaskStateChanged
                )
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
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawParticlePreview(
    cfg: com.moody.moodyvideoeditor.data.ParticleConfig, localSec: Float, W: Float, H: Float
) {
    if (localSec < 0f) return
    val cycleSec = cfg.triggerEverySec
    val cycleIdx: Int;
    val localT: Float
    if (cycleSec <= 0.01f) {
        if (localSec * 1000f > cfg.burstDurationMs + cfg.lifetimeMs) return
        cycleIdx = 0; localT = localSec
    } else {
        cycleIdx = kotlin.math.floor(localSec / cycleSec).toInt(); localT =
            localSec - cycleIdx * cycleSec
    }
    val localMs = (localT * 1000f).toLong()

    if (cfg.shockwave) {
        val waveFrac = (localMs.toFloat() / 800f).coerceIn(0f, 1f)
        if (waveFrac < 1f) {
            val ringRadius = waveFrac * W * 0.7f;
            val ringAlpha = (1f - waveFrac).coerceIn(0f, 1f);
            val c1 = Color(cfg.color1)
            drawCircle(
                color = c1.copy(alpha = ringAlpha * 0.8f),
                radius = ringRadius,
                center = Offset(cfg.triggerX * W, cfg.triggerY * H),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = (W * cfg.shockwaveThickness).coerceAtLeast(1f)
                ),
                blendMode = BlendMode.Screen
            )
        }
    }
    if (cfg.flashOnTrigger) {
        val flashFrac = (localMs / 150f).coerceIn(0f, 1f)
        if (flashFrac < 1f) {
            val c1 = Color(cfg.color1)
            drawRect(
                color = c1.copy(alpha = (1f - flashFrac) * 0.55f),
                size = androidx.compose.ui.geometry.Size(W, H),
                blendMode = BlendMode.Screen
            )
        }
    }

    val particles = com.moody.moodyvideoeditor.utils.ParticleEngine.generateParticles(cfg, cycleIdx)
    val intensityMul = (cfg.masterIntensity / 100f).coerceIn(0f, 2f)

    particles.forEach { p ->
        val age = localMs - p.birthMs
        if (age < 0L || age > cfg.lifetimeMs) return@forEach
        val st = com.moody.moodyvideoeditor.utils.ParticleEngine.computeState(cfg, p, age, W, H)
            ?: return@forEach
        val c1 = Color(cfg.color1);
        val c2 = Color(cfg.color2);
        val mix = p.colorMix
        val finalColor = Color(
            red = c1.red * (1f - mix) + c2.red * mix,
            green = c1.green * (1f - mix) + c2.green * mix,
            blue = c1.blue * (1f - mix) + c2.blue * mix,
            alpha = (st.alpha * intensityMul).coerceIn(0f, 1f)
        )
        if (cfg.glow) drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    finalColor,
                    finalColor.copy(alpha = 0f)
                ), center = Offset(st.x, st.y), radius = st.size * 2.5f
            ), radius = st.size * 2.5f, center = Offset(st.x, st.y), blendMode = BlendMode.Screen
        )
        drawCircle(color = finalColor, radius = st.size, center = Offset(st.x, st.y))
    }
}

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
                val w = constraints.maxWidth.toFloat().coerceAtLeast(1f);
                val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val rad = Math.toRadians(style.rampAngleDeg.toDouble());
                val dx = kotlin.math.cos(rad).toFloat();
                val dy = kotlin.math.sin(rad).toFloat()
                val halfDiag = kotlin.math.sqrt(w * w + h * h) / 2f;
                val cx = w / 2f;
                val cy = h / 2f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(style.rampColor1),
                                    Color(style.rampColor2)
                                ),
                                start = Offset(cx - dx * halfDiag, cy - dy * halfDiag),
                                end = Offset(cx + dx * halfDiag, cy + dy * halfDiag)
                            )
                        )
                )
            }
        }

        ColorMatteMode.FOUR_COLOR -> {
            BoxWithConstraints(modifier = modifier) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val W = size.width;
                    val H = size.height
                    val tlR = ((style.topLeft shr 16) and 0xFF) / 255f;
                    val tlG = ((style.topLeft shr 8) and 0xFF) / 255f;
                    val tlB = (style.topLeft and 0xFF) / 255f
                    val trR = ((style.topRight shr 16) and 0xFF) / 255f;
                    val trG = ((style.topRight shr 8) and 0xFF) / 255f;
                    val trB = (style.topRight and 0xFF) / 255f
                    val blR = ((style.bottomLeft shr 16) and 0xFF) / 255f;
                    val blG = ((style.bottomLeft shr 8) and 0xFF) / 255f;
                    val blB = (style.bottomLeft and 0xFF) / 255f
                    val brR = ((style.bottomRight shr 16) and 0xFF) / 255f;
                    val brG = ((style.bottomRight shr 8) and 0xFF) / 255f;
                    val brB = (style.bottomRight and 0xFF) / 255f
                    val stepX = (W / 128f).coerceAtLeast(1f);
                    val stepY = (H / 128f).coerceAtLeast(1f)
                    var y = 0f
                    while (y < H) {
                        var x = 0f
                        while (x < W) {
                            val fx = (x / W).coerceIn(0f, 1f);
                            val fy = (y / H).coerceIn(0f, 1f)
                            val wTL = (1f - fx) * (1f - fy);
                            val wTR = fx * (1f - fy);
                            val wBL = (1f - fx) * fy;
                            val wBR = fx * fy
                            val r = (tlR * wTL + trR * wTR + blR * wBL + brR * wBR).coerceIn(0f, 1f)
                            val g = (tlG * wTL + trG * wTR + blG * wBL + brG * wBR).coerceIn(0f, 1f)
                            val b = (tlB * wTL + trB * wTR + blB * wBL + brB * wBR).coerceIn(0f, 1f)
                            drawRect(
                                color = Color(r, g, b, 1f),
                                topLeft = Offset(x, y),
                                size = Size(stepX, stepY)
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

private fun maskAtClipTime(clip: EditorClip, currentPosMs: Long): MaskState {
    val localTimeSec = ((currentPosMs - clip.timelineStartMs).coerceAtLeast(0L)) / 1000f
    return MaskEngine.sampleAt(clip.mask, localTimeSec)
}

@Composable
private fun MaskedClipContent(mask: MaskState, content: @Composable BoxScope.() -> Unit) {
    val shouldMask = mask.isActive &&
            (mask.type != MaskType.CUSTOM || mask.hasCustomPath)

    if (!shouldMask) {
        Box(modifier = Modifier.fillMaxSize(), content = content)
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
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
        val featherPx = kotlin.math.abs(mask.feather / 100f * minOf(w, h) / 9f)
        val alphaInt = (mask.opacity / 100f * 255).toInt().coerceIn(0, 255)

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = android.graphics.Color.WHITE
            this.alpha = alphaInt
            if (featherPx > 0.5f) {
                maskFilter = BlurMaskFilter(
                    featherPx.coerceIn(0.5f, 150f),
                    BlurMaskFilter.Blur.NORMAL
                )
            }
        }
        val compositePaint = Paint().apply {
            xfermode = PorterDuffXfermode(
                if (mask.isInverted) PorterDuff.Mode.DST_OUT
                else PorterDuff.Mode.DST_IN
            )
        }

        val layerSaveCount = nativeCanvas.saveLayer(
            android.graphics.RectF(0f, 0f, w, h),
            compositePaint
        )
        try {
            nativeCanvas.drawPath(path.asAndroidPath(), maskPaint)
        } finally {
            nativeCanvas.restoreToCount(layerSaveCount)
            maskPaint.reset()
            compositePaint.reset()
        }
    }
}

private fun buildMaskPathCompose(mask: MaskState, w: Float, h: Float): Path? {
    if (mask.type == MaskType.NONE) return null
    val cx = mask.positionX * w;
    val cy = mask.positionY * h
    val rad = Math.toRadians(mask.rotation.toDouble());
    val cosR = kotlin.math.cos(rad).toFloat();
    val sinR = kotlin.math.sin(rad).toFloat()

    fun rp(px: Float, py: Float): Pair<Float, Float> {
        if (mask.rotation == 0f) return px to py
        val dx = px - cx;
        val dy = py - cy
        return (cx + dx * cosR - dy * sinR) to (cy + dx * sinR + dy * cosR)
    }

    return Path().apply {
        when (mask.type) {
            MaskType.CIRCLE -> {
                val minDimension = minOf(w, h)
                val r =
                    (mask.radius * minDimension + mask.expansion / 100f * minDimension).coerceAtLeast(
                        0f
                    )
                addOval(Rect(cx - r, cy - r, cx + r, cy + r))
            }

            MaskType.RECTANGLE -> {
                val expansionPx = mask.expansion / 100f * minOf(w, h)
                val hw = (mask.width * w / 2f + expansionPx).coerceAtLeast(0f)
                val hh = (mask.height * h / 2f + expansionPx).coerceAtLeast(0f)
                val cornerPx = mask.cornerRadius.coerceAtLeast(0f) * minOf(w, h)

                if (cornerPx > 0.5f) {
                    val left = cx - hw
                    val top = cy - hh
                    val right = cx + hw
                    val bottom = cy + hh
                    val r = cornerPx.coerceAtMost(minOf(hw, hh))

                    run {
                        val tlC = rp(left, top)
                        val trC = rp(right, top)
                        val brC = rp(right, bottom)
                        val blC = rp(left, bottom)

                        val tlEntry = rp(left + r, top)
                        val tlExit = rp(left, top + r)
                        val trEntry = rp(right - r, top)
                        val trExit = rp(right, top + r)
                        val brEntry = rp(right, bottom - r)
                        val brExit = rp(right - r, bottom)
                        val blEntry = rp(left + r, bottom)
                        val blExit = rp(left, bottom - r)

                        moveTo(tlEntry.first, tlEntry.second)
                        lineTo(trEntry.first, trEntry.second)
                        quadraticBezierTo(trC.first, trC.second, trExit.first, trExit.second)
                        lineTo(brEntry.first, brEntry.second)
                        quadraticBezierTo(brC.first, brC.second, brExit.first, brExit.second)
                        lineTo(blEntry.first, blEntry.second)
                        quadraticBezierTo(blC.first, blC.second, blExit.first, blExit.second)
                        lineTo(tlExit.first, tlExit.second)
                        quadraticBezierTo(tlC.first, tlC.second, tlEntry.first, tlEntry.second)
                        close()
                    }
                } else {
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
            }
            MaskType.LINEAR -> {
                val diag = kotlin.math.sqrt(w * w + h * h) * 1.5f
                val lineY = (mask.positionY + mask.linearPosition - 0.5f) * h -
                        mask.expansion / 100f * minOf(w, h)
                val px = -sinR;
                val py = cosR
                val p1x = cx - cosR * diag;
                val p1y = lineY - sinR * diag
                val p2x = cx + cosR * diag;
                val p2y = lineY + sinR * diag
                val p3x = p2x + px * diag;
                val p3y = p2y + py * diag
                val p4x = p1x + px * diag;
                val p4y = p1y + py * diag
                moveTo(p1x, p1y); lineTo(p2x, p2y); lineTo(p3x, p3y); lineTo(p4x, p4y); close()
            }

            MaskType.HEART -> {
                val s = (mask.scale * minOf(w, h) * 0.4f + mask.expansion / 100f * minOf(
                    w,
                    h
                )).coerceAtLeast(0f)

                fun rp2(px: Float, py: Float) =
                    (cx + px * cosR - py * sinR) to (cy + px * sinR + py * cosR)

                val p0 = rp2(0f, 0.6f * s); moveTo(p0.first, p0.second)
                val c1 = rp2(-0.7f * s, 0.1f * s);
                val c2 = rp2(-1.0f * s, -0.5f * s);
                val c3 = rp2(-0.5f * s, -0.7f * s)
                cubicTo(c1.first, c1.second, c2.first, c2.second, c3.first, c3.second)
                val c4 = rp2(-0.15f * s, -0.85f * s);
                val c5 = rp2(0f, -0.55f * s);
                val c6 = rp2(0f, -0.3f * s)
                cubicTo(c4.first, c4.second, c5.first, c5.second, c6.first, c6.second)
                val c7 = rp2(0f, -0.55f * s);
                val c8 = rp2(0.15f * s, -0.85f * s);
                val c9 = rp2(0.5f * s, -0.7f * s)
                cubicTo(c7.first, c7.second, c8.first, c8.second, c9.first, c9.second)
                val c10 = rp2(1.0f * s, -0.5f * s);
                val c11 = rp2(0.7f * s, 0.1f * s);
                val c12 = rp2(0f, 0.6f * s)
                cubicTo(
                    c10.first,
                    c10.second,
                    c11.first,
                    c11.second,
                    c12.first,
                    c12.second
                ); close()
            }

            MaskType.CUSTOM -> {
                val pts = MaskEngine.expandedPoints(mask.customPoints, mask.expansion / 100f)
                if (pts.size >= 2) {
                    fun rp3(px: Float, py: Float): Pair<Float, Float> {
                        val dx = px - cx
                        val dy = py - cy
                        return (cx + dx * cosR - dy * sinR) to (cy + dx * sinR + dy * cosR)
                    }

                    val roundedPath = if (
                        mask.customClosed &&
                        mask.cornerRadius * minOf(w, h) > 0.5f
                    ) {
                        MaskEngine.buildRoundedCustomPath(
                            pts = mask.customPoints,
                            w = w,
                            h = h,
                            closed = true,
                            expansion = mask.expansion / 100f,
                            rotationDeg = mask.rotation,
                            positionX = mask.positionX,
                            positionY = mask.positionY,
                            cornerRadius = mask.cornerRadius
                        )?.asComposePath()
                    } else {
                        null
                    }

                    if (roundedPath != null) {
                        addPath(roundedPath)
                    } else {
                        val p0 = rp3(pts[0].x * w, pts[0].y * h)
                        moveTo(p0.first, p0.second)
                        for (i in 1 until pts.size) {
                            val prev = pts[i - 1]
                            val curr = pts[i]
                            if (prev.hasHandles || curr.hasHandles) {
                                val c1 = rp3(prev.outX * w, prev.outY * h)
                                val c2 = rp3(curr.inX * w, curr.inY * h)
                                val end = rp3(curr.x * w, curr.y * h)
                                cubicTo(c1.first, c1.second, c2.first, c2.second, end.first, end.second)
                            } else {
                                val end = rp3(curr.x * w, curr.y * h)
                                lineTo(end.first, end.second)
                            }
                        }
                        if (mask.customClosed) {
                            val last = pts.last()
                            val first = pts.first()
                            if (last.hasHandles || first.hasHandles) {
                                val c1 = rp3(last.outX * w, last.outY * h)
                                val c2 = rp3(first.inX * w, first.inY * h)
                                val end = rp3(first.x * w, first.y * h)
                                cubicTo(c1.first, c1.second, c2.first, c2.second, end.first, end.second)
                            }
                            close()
                        }
                    }
                }
            }

            MaskType.NONE -> {}
        }
    }
}
@Composable
private fun MaskPenOverlay(
    maskState: MaskState, canvasW: Float, canvasH: Float, isHandMode: Boolean,
    onGestureStart: () -> Unit, onGestureEnd: () -> Unit, onTapAddPoint: (Float, Float) -> Unit,
    onAnchorMove: (Int, Float, Float) -> Unit, onHandleMove: (Int, Boolean, Float, Float) -> Unit,
    onTogglePoint: (Int) -> Unit, onDeletePoint: (Int) -> Unit, onClosePath: () -> Unit,
    onMaskMove: (Float, Float) -> Unit, onMaskStateChanged: (MaskState) -> Unit
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
                    val viewW = viewSizePx.width.toFloat();
                    val viewH = viewSizePx.height.toFloat()
                    if (viewW <= 0f || viewH <= 0f) return@awaitEachGesture
                    val down = awaitFirstDown(requireUnconsumed = false)

                    if (isHandMode) {
                        val gestureMask = latestMaskState; down.consume(); onGestureStart()
                        var baseMask = gestureMask;
                        var basePoint = down.position
                        var handle = maskHandleAt(
                            gestureMask,
                            down.position.x,
                            down.position.y,
                            viewW,
                            viewH,
                            hitRadiusPx
                        )
                        var latestMask = gestureMask;
                        var multiBaseMask: MaskState? = null
                        var multiStartCentroid = Offset.Zero;
                        var multiStartDistance = 1f;
                        var multiStartAngle = 0f;
                        var wasMultiTouch = false;
                        var dragging = true
                        while (dragging) {
                            val event = awaitPointerEvent();
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                dragging = false
                            } else if (pressed.size >= 2) {
                                val first = pressed[0].position;
                                val second = pressed[1].position
                                val centroid = (first + second) / 2f
                                val distance =
                                    kotlin.math.hypot(second.x - first.x, second.y - first.y)
                                        .coerceAtLeast(1f)
                                val angle =
                                    kotlin.math.atan2(second.y - first.y, second.x - first.x)
                                if (multiBaseMask == null) {
                                    multiBaseMask = latestMask; multiStartCentroid =
                                        centroid; multiStartDistance = distance; multiStartAngle =
                                        angle
                                }
                                latestMask = transformMaskWithTwoFingers(
                                    multiBaseMask!!,
                                    multiStartCentroid,
                                    centroid,
                                    multiStartDistance,
                                    distance,
                                    multiStartAngle,
                                    angle,
                                    viewW,
                                    viewH
                                )
                                onMaskStateChanged(latestMask); wasMultiTouch =
                                    true; pressed.forEach { it.consume() }
                            } else {
                                val change = pressed.first()
                                if (wasMultiTouch) {
                                    baseMask = latestMask; basePoint = change.position; handle =
                                        MaskGestureHandle.MOVE; multiBaseMask =
                                        null; wasMultiTouch = false
                                }
                                latestMask = transformMaskWithOneFinger(
                                    baseMask,
                                    handle,
                                    basePoint,
                                    change.position,
                                    viewW,
                                    viewH
                                )
                                onMaskStateChanged(latestMask); change.consume()
                            }
                        }
                        onGestureEnd(); return@awaitEachGesture
                    }

                    if (!isClosed && pts.size >= 3) {
                        val closeTarget = pts.indexOfFirst { point ->
                            kotlin.math.hypot(
                                down.position.x - point.x * viewW,
                                down.position.y - point.y * viewH
                            ) <= hitRadiusPx
                        }
                        if (closeTarget >= 0) {
                            selectedPointIndex = closeTarget
                            onClosePath()
                            down.consume()
                            return@awaitEachGesture
                        }
                    }

                    val downX = (down.position.x / viewW).coerceIn(0f, 1f);
                    val downY = (down.position.y / viewH).coerceIn(0f, 1f)
                    var hitAnchor = -1;
                    var hitHandle: Pair<Int, Boolean>? = null

                    if (selectedPointIndex in pts.indices) {
                        val sel = pts[selectedPointIndex]
                        if (sel.hasHandles) {
                            val dIn =
                                kotlin.math.sqrt((down.position.x - sel.inX * viewW) * (down.position.x - sel.inX * viewW) + (down.position.y - sel.inY * viewH) * (down.position.y - sel.inY * viewH))
                            val dOut =
                                kotlin.math.sqrt((down.position.x - sel.outX * viewW) * (down.position.x - sel.outX * viewW) + (down.position.y - sel.outY * viewH) * (down.position.y - sel.outY * viewH))
                            if (dIn < hitRadiusPx) hitHandle = selectedPointIndex to true
                            else if (dOut < hitRadiusPx) hitHandle = selectedPointIndex to false
                        }
                    }
                    if (hitHandle == null) {
                        pts.forEachIndexed { i, p ->
                            val d =
                                kotlin.math.sqrt((down.position.x - p.x * viewW) * (down.position.x - p.x * viewW) + (down.position.y - p.y * viewH) * (down.position.y - p.y * viewH))
                            if (d < hitRadiusPx && hitAnchor == -1) hitAnchor = i
                        }
                    }

                    if (hitHandle != null) {
                        draggingHandle = hitHandle; down.consume()
                    } else if (hitAnchor >= 0) {
                        selectedPointIndex = hitAnchor; draggingAnchor = hitAnchor; down.consume()
                    } else if (!isClosed) {
                        selectedPointIndex = pts.size; onTapAddPoint(downX, downY); down.consume()
                    }

                    var continueGesture = true
                    while (continueGesture) {
                        val event = awaitPointerEvent();
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            draggingAnchor = -1; draggingHandle = null; continueGesture = false
                        } else {
                            pressed.forEach { ch ->
                                if (draggingAnchor >= 0) {
                                    onAnchorMove(
                                        draggingAnchor,
                                        (ch.position.x / viewW).coerceIn(0f, 1f),
                                        (ch.position.y / viewH).coerceIn(0f, 1f)
                                    ); ch.consume()
                                } else if (draggingHandle != null) {
                                    val dh = draggingHandle!!;
                                    val anchor = pts.getOrNull(dh.first)
                                    if (anchor != null) {
                                        onHandleMove(
                                            dh.first,
                                            dh.second,
                                            (ch.position.x / viewW) - anchor.x,
                                            (ch.position.y / viewH) - anchor.y
                                        ); ch.consume()
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(pts.size) {
                detectTapGestures(onDoubleTap = { tapOffset ->
                    val viewW = viewSizePx.width.toFloat();
                    val viewH = viewSizePx.height.toFloat()
                    if (viewW > 0f && viewH > 0f) {
                        var foundIndex = -1
                        pts.forEachIndexed { i, p ->
                            val d =
                                kotlin.math.sqrt((tapOffset.x - p.x * viewW) * (tapOffset.x - p.x * viewW) + (tapOffset.y - p.y * viewH) * (tapOffset.y - p.y * viewH))
                            if (d < hitRadiusPx && foundIndex == -1) foundIndex = i
                        }
                        if (foundIndex >= 0) {
                            onTogglePoint(foundIndex); selectedPointIndex = foundIndex
                        }
                    }
                })
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width;
            val h = size.height
            if (!maskState.isActive) return@Canvas
            val path = buildMaskPathCompose(maskState, w, h)
            if (path != null) drawPath(path, strokeColor, style = Stroke(width = 3f))
            if (maskState.type == MaskType.CUSTOM) {
                pts.forEachIndexed { i, p ->
                    val cx = p.x * w;
                    val cy = p.y * h;
                    val isSel = i == selectedPointIndex;
                    val isFirst = i == 0
                    if (isFirst && !isClosed && pts.size >= 3) {
                        drawCircle(Color(0xFF60EFFF).copy(alpha = 0.4f), 22f, Offset(cx, cy))
                        drawCircle(
                            Color(0xFF60EFFF).copy(alpha = 0.8f),
                            16f,
                            Offset(cx, cy),
                            style = Stroke(width = 3f)
                        )
                    }
                    drawCircle(
                        color = when {
                            isFirst && !isClosed -> Color(0xFF60EFFF); isSel -> Color(0xFFFFD166); else -> strokeColor
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
                val center = Offset(maskState.positionX * w, maskState.positionY * h)
                drawLine(Color.White.copy(alpha = 0.75f), center, rotate, strokeWidth = 2f)
                drawLine(Color(0xFF60EFFF), center, feather, strokeWidth = 2f)
                drawCircle(Color(0xFFFFD166), 11f, resize); drawCircle(
                    Color.White,
                    11f,
                    resize,
                    style = Stroke(width = 2f)
                )
                drawCircle(Color(0xFF60EFFF), 10f, rotate); drawCircle(
                    Color.White,
                    10f,
                    rotate,
                    style = Stroke(width = 2f)
                )
                drawCircle(Color(0xFF22C55E), 10f, feather); drawCircle(
                    Color.White,
                    10f,
                    feather,
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

private enum class MaskGestureHandle { MOVE, RESIZE, ROTATE, FEATHER }

private fun maskHandlePositions(
    mask: MaskState,
    width: Float,
    height: Float
): Triple<Offset, Offset, Offset> {
    val cx = mask.positionX * width;
    val cy = mask.positionY * height
    val minDimension = minOf(width, height).coerceAtLeast(1f)
    val radius = when (mask.type) {
        MaskType.CIRCLE -> mask.radius * minDimension
        MaskType.RECTANGLE -> kotlin.math.hypot(mask.width * width / 2f, mask.height * height / 2f)
        MaskType.HEART -> mask.scale * minDimension * 0.4f
        MaskType.CUSTOM -> mask.customPoints.maxOfOrNull { point ->
            kotlin.math.hypot(
                (point.x - mask.positionX) * width,
                (point.y - mask.positionY) * height
            )
        } ?: minDimension * 0.2f

        MaskType.LINEAR, MaskType.NONE -> minDimension * 0.22f
    }.coerceAtLeast(24f)

    val resizeLocal = when (mask.type) {
        MaskType.CIRCLE -> Offset(radius, 0f)
        MaskType.RECTANGLE -> Offset(mask.width * width / 2f, mask.height * height / 2f)
        MaskType.HEART -> Offset(radius, 0f)
        MaskType.CUSTOM -> {
            val x = mask.customPoints.maxOfOrNull { it.x * width - cx } ?: radius;
            val y = mask.customPoints.maxOfOrNull { it.y * height - cy } ?: radius; Offset(x, y)
        }

        MaskType.LINEAR, MaskType.NONE -> Offset(radius * 0.65f, radius * 0.65f)
    }
    val resize = rotateMaskPoint(
        Offset(cx + resizeLocal.x, cy + resizeLocal.y),
        cx,
        cy,
        if (mask.type == MaskType.CIRCLE) 0f else mask.rotation
    )
    val rotate = rotateMaskPoint(Offset(cx, cy - radius - 34f), cx, cy, mask.rotation)
    val feather = Offset(cx + radius + 42f, cy)
    return Triple(resize, rotate, feather)
}

private fun rotateMaskPoint(
    point: Offset,
    pivotX: Float,
    pivotY: Float,
    rotationDegrees: Float
): Offset {
    val radians = Math.toRadians(rotationDegrees.toDouble());
    val cosR = kotlin.math.cos(radians).toFloat();
    val sinR = kotlin.math.sin(radians).toFloat()
    val dx = point.x - pivotX;
    val dy = point.y - pivotY
    return Offset(pivotX + dx * cosR - dy * sinR, pivotY + dx * sinR + dy * cosR)
}

private fun maskHandleAt(
    mask: MaskState,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    hitRadius: Float
): MaskGestureHandle {
    val point = Offset(x, y); val (resize, rotate, feather) = maskHandlePositions(
        mask,
        width,
        height
    )
    fun near(target: Offset) =
        kotlin.math.hypot(point.x - target.x, point.y - target.y) <= hitRadius * 1.5f
    return when {
        near(feather) -> MaskGestureHandle.FEATHER; near(rotate) -> MaskGestureHandle.ROTATE; near(
            resize
        ) -> MaskGestureHandle.RESIZE; else -> MaskGestureHandle.MOVE
    }
}

private fun transformMaskWithOneFinger(
    initial: MaskState,
    handle: MaskGestureHandle,
    start: Offset,
    current: Offset,
    width: Float,
    height: Float
): MaskState {
    val minDimension = minOf(width, height).coerceAtLeast(1f)
    val dx = (current.x - start.x) / width.coerceAtLeast(1f);
    val dy = (current.y - start.y) / height.coerceAtLeast(1f)
    val pivotX = initial.positionX * width;
    val pivotY = initial.positionY * height
    return when (handle) {
        MaskGestureHandle.MOVE -> initial.copy(
            positionX = (initial.positionX + dx).coerceIn(MaskAnimatableProps.positionRange),
            positionY = (initial.positionY + dy).coerceIn(MaskAnimatableProps.positionRange),
            customPoints = initial.customPoints.map { it.copy(x = it.x + dx, y = it.y + dy) })

        MaskGestureHandle.ROTATE -> {
            val startAngle = kotlin.math.atan2(start.y - pivotY, start.x - pivotX);
            val currentAngle = kotlin.math.atan2(current.y - pivotY, current.x - pivotX)
            val delta = Math.toDegrees((currentAngle - startAngle).toDouble()).toFloat()
            initial.copy(rotation = initial.rotation + delta)
        }

        MaskGestureHandle.FEATHER -> {
            val initialRadius = kotlin.math.hypot(start.x - pivotX, start.y - pivotY);
            val currentRadius = kotlin.math.hypot(current.x - pivotX, current.y - pivotY)
            initial.copy(
                feather = (initial.feather + (currentRadius - initialRadius) / minDimension * 900f).coerceIn(
                    0f,
                    300f
                )
            )
        }

        MaskGestureHandle.RESIZE -> {
            val local = rotateMaskPoint(current, pivotX, pivotY, -initial.rotation)
            when (initial.type) {
                MaskType.CIRCLE -> initial.copy(
                    radius = (kotlin.math.hypot(
                        local.x - pivotX,
                        local.y - pivotY
                    ) / minDimension).coerceIn(0.01f, 20f)
                )

                MaskType.RECTANGLE -> initial.copy(
                    width = (kotlin.math.abs(local.x - pivotX) * 2f / width).coerceIn(
                        0.01f,
                        20f
                    ),
                    height = (kotlin.math.abs(local.y - pivotY) * 2f / height).coerceIn(0.01f, 20f)
                )

                MaskType.HEART -> initial.copy(
                    scale = (kotlin.math.hypot(
                        local.x - pivotX,
                        local.y - pivotY
                    ) / (minDimension * 0.4f)).coerceIn(0.05f, 25f)
                )

                MaskType.CUSTOM -> {
                    val handleStart = maskHandlePositions(initial, width, height).first
                    val oldRadius =
                        kotlin.math.hypot(handleStart.x - pivotX, handleStart.y - pivotY)
                            .coerceAtLeast(1f)
                    val newRadius = kotlin.math.hypot(local.x - pivotX, local.y - pivotY)
                    val factor = (newRadius / oldRadius).coerceIn(0.05f, 20f)
                    initial.copy(customPoints = initial.customPoints.map { point ->
                        point.copy(
                            x = initial.positionX + (point.x - initial.positionX) * factor,
                            y = initial.positionY + (point.y - initial.positionY) * factor
                        )
                    })
                }

                MaskType.LINEAR, MaskType.NONE -> initial.copy(
                    positionY = current.y / height
                )
            }
        }
    }
}

private fun transformMaskWithTwoFingers(
    initial: MaskState,
    startCentroid: Offset,
    centroid: Offset,
    startDistance: Float,
    distance: Float,
    startAngle: Float,
    angle: Float,
    width: Float,
    height: Float
): MaskState {
    val dx = (centroid.x - startCentroid.x) / width.coerceAtLeast(1f);
    val dy = (centroid.y - startCentroid.y) / height.coerceAtLeast(1f)
    val scaleFactor = (distance / startDistance.coerceAtLeast(1f)).coerceIn(0.05f, 20f)
    val angleDelta = Math.toDegrees((angle - startAngle).toDouble()).toFloat()
    val positionX = (initial.positionX + dx).coerceIn(MaskAnimatableProps.positionRange);
    val positionY = (initial.positionY + dy).coerceIn(MaskAnimatableProps.positionRange)
    val rotatedPoints = initial.customPoints.map { point ->
        val x = initial.positionX + (point.x - initial.positionX) * scaleFactor + dx;
        val y = initial.positionY + (point.y - initial.positionY) * scaleFactor + dy; point.copy(
        x = x,
        y = y
    )
    }
    return initial.copy(
        positionX = positionX,
        positionY = positionY,
        radius = (initial.radius * scaleFactor).coerceIn(0.01f, 20f),
        width = (initial.width * scaleFactor).coerceIn(0.01f, 20f),
        height = (initial.height * scaleFactor).coerceIn(0.01f, 20f),
        scale = (initial.scale * scaleFactor).coerceIn(0.05f, 25f),
        rotation = initial.rotation + angleDelta,
        customPoints = rotatedPoints
    )
}

@Composable
private fun TextBackgroundRenderer(
    state: com.moody.moodyvideoeditor.data.TextState,
    textWidthDp: Float,
    textHeightDp: Float
) {
    val padX = state.backgroundPaddingX;
    val padY = state.backgroundPaddingY;
    val alpha = (state.backgroundOpacity / 100f).coerceIn(0f, 1f)
    val brush: Brush = if (state.backgroundGradientEnabled) {
        val rad = Math.toRadians(state.backgroundGradientAngle.toDouble());
        val dx = kotlin.math.cos(rad).toFloat();
        val dy = kotlin.math.sin(rad).toFloat();
        val ext = 500f
        Brush.linearGradient(
            colors = listOf(
                Color(state.backgroundColor1).copy(alpha = alpha),
                Color(state.backgroundColor2).copy(alpha = alpha)
            ), start = Offset(-dx * ext, -dy * ext), end = Offset(dx * ext, dy * ext)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(state.backgroundColor1).copy(alpha = alpha),
                Color(state.backgroundColor1).copy(alpha = alpha)
            )
        )
    }
    when (state.backgroundType) {
        "rounded" -> {
            Box(
                modifier = Modifier
                    .width((textWidthDp + padX * 2).coerceAtLeast(20f).dp)
                    .height((textHeightDp + padY * 2).coerceAtLeast(20f).dp)
                    .clip(RoundedCornerShape(state.backgroundCornerRadius.dp))
                    .background(brush)
            )
        }

        "character" -> {
            Box(
                modifier = Modifier
                    .width((textWidthDp + 4f).coerceAtLeast(20f).dp)
                    .height((textHeightDp + 4f).coerceAtLeast(20f).dp)
                    .background(brush)
            )
        }

        "outline" -> {
            Box(
                modifier = Modifier
                    .width((textWidthDp + padX * 2).coerceAtLeast(20f).dp)
                    .height((textHeightDp + padY * 2).coerceAtLeast(20f).dp)
                    .clip(RoundedCornerShape(state.backgroundCornerRadius.dp))
                    .background(Color.Transparent)
                    .border(
                        width = 3.dp,
                        brush = brush,
                        shape = RoundedCornerShape(state.backgroundCornerRadius.dp)
                    )
            )
        }
    }
}

@Composable
private fun InteractiveTextOverlay(
    clip: EditorClip,
    textState: com.moody.moodyvideoeditor.data.TextState,
    canvasW: Float,
    canvasH: Float,
    currentPosMs: Long,
    isSelected: Boolean,
    isMulti: Boolean = false,
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
    val clampedX = sampled.x.coerceIn(-50f, 150f);
    val clampedY = sampled.y.coerceIn(-50f, 150f)
    val animDur = textState.animationDuration.coerceAtLeast(0.1f);
    val progress = (localTimeSec / animDur).coerceIn(0f, 1f)
    val presetProgress = (
        localTimeSec / textState.captionPresetAnimationDuration.coerceAtLeast(0.1f)
        ).coerceIn(0f, 1f)
    var isDragging by remember { mutableStateOf(false) }
    val rawFrame = try {
        com.moody.moodyvideoeditor.utils.AnimationsEngine.computeTextFrame(
            textState.animation,
            textState.captionPresetAnimation,
            progress,
            localTimeSec,
            presetProgress
        )
    } catch (_: Throwable) {
        com.moody.moodyvideoeditor.utils.AnimationsEngine.Frame()
    }
    val frame = if (isDragging) rawFrame.copy(translateX = 0f, translateY = 0f) else rawFrame
    val currentSampled by rememberUpdatedState(sampled);
    val currentFrame by rememberUpdatedState(frame)
    val displayContent = if (textState.animation.equals("typewriter", ignoreCase = true)) {
        val total = textState.content.length;
        val visible = (progress * total).toInt().coerceIn(0, total); textState.content.substring(
            0,
            visible
        )
    } else textState.content
    if (displayContent.isEmpty() && textState.animation.equals(
            "typewriter",
            ignoreCase = true
        )
    ) return
    val density = LocalDensity.current
    val canvasWpx = with(density) { canvasW.dp.toPx() };
    val canvasHpx = with(density) { canvasH.dp.toPx() }
    val textTypeface = TextRenderContract.androidTypefaceFor(
        textState.fontFamily,
        textState.fontWeight == "bold",
        textState.fontStyle == "italic"
    )
    val refPx = minOf(canvasWpx, canvasHpx)
    val effLetterSpacingPx =
        TextScaler.letterSpacing(base = textState.letterSpacing, canvasWidthPx = refPx)
    val effLineHeightPx = TextScaler.lineHeight(
        TextRenderContract.scaledFontSize(
            textState.fontSize.toFloat(),
            canvasWpx,
            canvasHpx
        ), textState.lineHeight
    )
    val family = FontLibrary.familyFor(textState.fontFamily)
    val weight = if (textState.fontWeight == "bold") FontWeight.Bold else FontWeight.Normal
    val fontSty =
        if (textState.fontStyle == "italic") androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
    val userStrokePx = textState.strokeWidth.coerceAtLeast(0f) * 2f * (refPx / 720f)
    val finalFontSizePx = TextRenderContract.fittedFontSizePx(
        content = displayContent,
        baseSizePx = textState.fontSize.toFloat(),
        canvasWidthPx = canvasWpx,
        canvasHeightPx = canvasHpx,
        maxWidthPercent = textState.maxWidth,
        typeface = textTypeface
    )
    val finalFontSizeSp = with(density) { finalFontSizePx.toSp() };
    val finalLetterSpacingSp = with(density) { effLetterSpacingPx.toSp() };
    val finalLineHeightSp = with(density) { effLineHeightPx.toSp() }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val finalMeasure = remember(
        displayContent,
        finalFontSizeSp,
        finalLetterSpacingSp,
        finalLineHeightSp,
        family,
        weight,
        fontSty,
        canvasWpx,
        textState.maxWidth
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
                maxWidth = (canvasWpx * (textState.maxWidth / 100f)).toInt().coerceAtLeast(50)
            ),
            softWrap = true
        )
    }
    val tightMeasure = remember(
        displayContent,
        finalFontSizeSp,
        finalLetterSpacingSp,
        family,
        weight,
        fontSty
    ) {
        measurer.measure(
            text = displayContent,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = finalFontSizeSp,
                letterSpacing = finalLetterSpacingSp,
                fontFamily = family,
                fontWeight = weight,
                fontStyle = fontSty
            ),
            constraints = androidx.compose.ui.unit.Constraints(
                maxWidth = (canvasWpx * (textState.maxWidth / 100f)).toInt().coerceAtLeast(50)
            ),
            softWrap = true
        )
    }
    val textWidthDp = with(density) { finalMeasure.size.width.toDp().value };
    val textHeightDp = with(density) { finalMeasure.size.height.toDp().value };
    val textWidthConstraintDp = with(density) { tightMeasure.size.width.toDp() }
    val solidColor = Color(textState.color)
    val gradient: Brush? = if (textState.gradientEnabled) {
        Brush.linearGradient(
            colors = listOf(
                Color(
                    textState.gradientColor1
                ), Color(textState.gradientColor2)
            ),
            start = Offset(
                textState.gradientStartX.coerceIn(0f, 1f) * finalMeasure.size.width,
                textState.gradientStartY.coerceIn(0f, 1f) * finalMeasure.size.height
            ),
            end = Offset(
                textState.gradientEndX.coerceIn(0f, 1f) * finalMeasure.size.width,
                textState.gradientEndY.coerceIn(0f, 1f) * finalMeasure.size.height
            )
        )
    } else null
    val shadowStyle: androidx.compose.ui.graphics.Shadow? =
        if (textState.shadowEnabled) androidx.compose.ui.graphics.Shadow(
            color = Color(textState.shadowColor),
            offset = Offset(
                textState.shadowOffsetX * (refPx / 720f),
                textState.shadowOffsetY * (refPx / 720f)
            ),
            blurRadius = textState.shadowBlur * (refPx / 720f)
        ) else null
    val textAlignValue = when (textState.alignment) {
        "left" -> androidx.compose.ui.text.style.TextAlign.Left; "right" -> androidx.compose.ui.text.style.TextAlign.Right; else -> androidx.compose.ui.text.style.TextAlign.Center
    }
    val blurDp = with(density) { frame.blurRadiusPx.toDp() }.coerceAtLeast(0.dp).coerceAtMost(20.dp)
    val bgPadX = if (textState.backgroundEnabled) textState.backgroundPaddingX else 2f;
    val bgPadY = if (textState.backgroundEnabled) textState.backgroundPaddingY else 2f
    val selectionWidthDp = (textWidthDp + bgPadX).coerceAtLeast(20f);
    val selectionHeightDp = (textHeightDp + bgPadY).coerceAtLeast(20f)
    val outerWidthDp = selectionWidthDp;
    val outerHeightDp = selectionHeightDp
    val gradientPaddingX = (outerWidthDp - textWidthDp).coerceAtLeast(0f) / 2f
    val gradientPaddingY = (outerHeightDp - textHeightDp).coerceAtLeast(0f) / 2f
    val scaleFactor = sampled.scale / 100f;
    val handleW = (outerWidthDp * scaleFactor).dp;
    val handleH = (outerHeightDp * scaleFactor).dp

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val posTx = (clampedX - 50f) / 100f * refPx;
                    val posTy = (clampedY - 50f) / 100f * refPx
                    translationX = posTx + frame.translateX; translationY = posTy + frame.translateY
                    scaleX = (sampled.scale / 100f) * frame.scaleX; scaleY =
                    (sampled.scale / 100f) * frame.scaleY
                    rotationZ = sampled.rotation + frame.rotationZ; rotationX =
                    frame.rotationX; rotationY = frame.rotationY
                    cameraDistance = 16f * density.density; alpha =
                    (textState.opacity / 100f) * frame.alpha
                    transformOrigin = TransformOrigin.Center
                }
                .width(outerWidthDp.dp)
                .height(outerHeightDp.dp)
                .then(
                    if (blurDp > 0.dp) Modifier.blur(
                        blurDp,
                        BlurredEdgeTreatment.Unbounded
                    ) else Modifier
                )
                .pointerInput(clip.id, canvasW, canvasH) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.isConsumed) return@awaitEachGesture
                        isDragging = true; onGroupGestureStart()
                        val baseX = currentSampled.x;
                        val baseY = currentSampled.y;
                        val baseScale = currentSampled.scale;
                        val baseRot = currentSampled.rotation;
                        val baseFrame = currentFrame
                        var accumPanX = 0f;
                        var accumPanY = 0f;
                        var accumZoom = 1f;
                        var accumRot = 0f;
                        var lastDist = 0f;
                        var lastAngle = 0f;
                        var hasMultiGesture = false;
                        var hasMoved = false
                        if (!isSelected && !isMulti) onSelect()
                        var continueGesture = true
                        while (continueGesture) {
                            val event = awaitPointerEvent();
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                isDragging = false; continueGesture = false
                            } else {
                                if (pressed.size == 1) {
                                    val ch = pressed.first();
                                    val pan = ch.position - ch.previousPosition
                                    val rotation =
                                        Math.toRadians((baseRot + baseFrame.rotationZ).toDouble());
                                    val scaleX = (baseScale / 100f) * baseFrame.scaleX;
                                    val scaleY = (baseScale / 100f) * baseFrame.scaleY
                                    accumPanX += (pan.x * scaleX * kotlin.math.cos(rotation) - pan.y * scaleY * kotlin.math.sin(
                                        rotation
                                    )).toFloat()
                                    accumPanY += (pan.x * scaleX * kotlin.math.sin(rotation) + pan.y * scaleY * kotlin.math.cos(
                                        rotation
                                    )).toFloat()
                                    if (kotlin.math.abs(accumPanX) > 5f || kotlin.math.abs(accumPanY) > 5f) hasMoved =
                                        true
                                    ch.consume()
                                } else if (pressed.size >= 2) {
                                    val c1 = pressed[0];
                                    val c2 = pressed[1];
                                    val d = c1.position - c2.position
                                    val dist = kotlin.math.sqrt(d.x * d.x + d.y * d.y);
                                    val angle = kotlin.math.atan2(d.y, d.x)
                                    if (hasMultiGesture && lastDist > 1f) {
                                        accumZoom *= dist / lastDist; accumRot += Math.toDegrees(
                                            normalizeAngle(angle - lastAngle).toDouble()
                                        ).toFloat()
                                    }
                                    lastDist = dist; lastAngle = angle; hasMultiGesture =
                                        true; c1.consume(); c2.consume(); hasMoved = true
                                }
                                val rawX = baseX + accumPanX / canvasWpx * 100f;
                                val rawY = baseY + accumPanY / canvasHpx * 100f
                                val cx = rawX.coerceIn(-50f, 150f);
                                val cy = rawY.coerceIn(-50f, 150f)
                                val newScale = (baseScale * accumZoom).coerceIn(10f, 500f);
                                val newRot = baseRot + accumRot
                                if (isMulti) onGroupGesture(
                                    clip.id,
                                    cx,
                                    cy,
                                    newScale,
                                    newRot
                                ) else {
                                    onPositionChanged(cx, cy); onTransformChanged(newScale, newRot)
                                }
                            }
                        }
                        if (!hasMoved && !isMulti) onSelect()
                        onGroupGestureEnd(); isDragging = false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (textState.backgroundEnabled && textState.backgroundType != "character") {
                    TextBackgroundRenderer(
                        state = textState,
                        textWidthDp = textWidthDp,
                        textHeightDp = textHeightDp
                    )
                }
                if (textState.glowEnabled && textState.glowRadius > 0f) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        drawIntoCanvas { canvas ->
                            val nativeCanvas = canvas.nativeCanvas;
                            val glowColorInt = textState.glowColor.toInt()
                            val glowAlign = when (textState.alignment) {
                                "left" -> Paint.Align.LEFT; "right" -> Paint.Align.RIGHT; else -> Paint.Align.CENTER
                            }
                            val textSizePx = finalFontSizePx;
                            val letterSpacingEm =
                                if (finalFontSizePx <= 0f) 0f else (effLetterSpacingPx / finalFontSizePx).coerceIn(
                                    -0.3f,
                                    0.3f
                                );
                            val glowRadiusPx = textState.glowRadius * (refPx / 720f)
                            val lines = displayContent.split('\n');
                            val lineCount = lines.size;
                            val lineHeightPx = effLineHeightPx.coerceAtLeast(1f)
                            val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                textSize = textSizePx; this.typeface = textTypeface
                            };
                            val fm = measurePaint.fontMetrics
                            val totalTextH = lineHeightPx * lineCount;
                            val firstBaseline =
                                (size.height - totalTextH) / 2f + lineHeightPx / 2f - (fm.ascent + fm.descent) / 2f
                            val textW = finalMeasure.size.width.toFloat();
                            val textLeft = (size.width - textW) / 2f;
                            val textRight = (size.width + textW) / 2f
                            val x = when (glowAlign) {
                                Paint.Align.LEFT -> textLeft; Paint.Align.RIGHT -> textRight; else -> size.width / 2f
                            }
                            TextRenderContract.glowLayers().forEach { layer ->
                                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    isAntiAlias = true; color = glowColorInt; textSize =
                                    textSizePx * layer.fontScale; textAlign =
                                    glowAlign; this.typeface = textTypeface; this.letterSpacing =
                                    letterSpacingEm; alpha =
                                    (layer.alpha * 255).toInt().coerceIn(0, 255); maskFilter =
                                    android.graphics.BlurMaskFilter(
                                        (glowRadiusPx * layer.blurScale).coerceIn(
                                            1f,
                                            250f
                                        ), android.graphics.BlurMaskFilter.Blur.NORMAL
                                    )
                                }
                                lines.forEachIndexed { i, line ->
                                    val y = firstBaseline + i * lineHeightPx; nativeCanvas.drawText(
                                    line,
                                    x,
                                    y,
                                    paint
                                )
                                }
                            }
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
                        softWrap = true,
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier.width(textWidthConstraintDp),
                        style = androidx.compose.ui.text.TextStyle(
                            drawStyle = Stroke(
                                userStrokePx,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                miter = 4f
                            ), letterSpacing = finalLetterSpacingSp, lineHeight = finalLineHeightSp
                        )
                    )
                }
                val captionColorRanges =
                    com.moody.moodyvideoeditor.utils.CaptionPresetLibrary.colorRanges(
                        text = displayContent,
                        presetId = textState.captionPresetId,
                        highlightColor = textState.captionHighlightColor,
                        elapsedMs = (currentPosMs - clip.timelineStartMs).coerceAtLeast(0L),
                        durationMs = clip.durationMs,
                        preserveGradient = textState.gradientEnabled
                    )
                val annotatedBuilder = AnnotatedString.Builder(displayContent)
                if (textState.backgroundEnabled && textState.backgroundType == "character" &&
                    displayContent.isNotEmpty()
                ) {
                    annotatedBuilder.addStyle(
                        SpanStyle(
                            background = Color(textState.backgroundColor1)
                                .copy(alpha = textState.backgroundOpacity / 100f)
                        ),
                        0,
                        displayContent.length
                    )
                }
                if (!textState.gradientEnabled) {
                    captionColorRanges.forEach { range ->
                        annotatedBuilder.addStyle(
                            SpanStyle(color = Color(range.color)),
                            range.start,
                            range.end
                        )
                    }
                }
                val annotatedDisplay = annotatedBuilder.toAnnotatedString()
                Text(
                    text = annotatedDisplay,
                    color = if (gradient != null) Color.Unspecified else solidColor,
                    fontSize = finalFontSizeSp,
                    fontWeight = weight,
                    fontStyle = fontSty,
                    fontFamily = family,
                    textAlign = textAlignValue,
                    softWrap = true,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.width(textWidthConstraintDp),
                    style = androidx.compose.ui.text.TextStyle(
                        brush = gradient,
                        shadow = shadowStyle,
                        letterSpacing = finalLetterSpacingSp,
                        lineHeight = finalLineHeightSp
                    )
                )
                if (gradient != null && captionColorRanges.isNotEmpty()) {
                    val highlightBuilder = AnnotatedString.Builder(displayContent)
                    highlightBuilder.addStyle(
                        SpanStyle(color = Color.Transparent),
                        0,
                        displayContent.length
                    )
                    captionColorRanges.forEach { range ->
                        highlightBuilder.addStyle(
                            SpanStyle(color = Color(range.color)),
                            range.start,
                            range.end
                        )
                    }
                    Text(
                        text = highlightBuilder.toAnnotatedString(),
                        color = Color.Transparent,
                        fontSize = finalFontSizeSp,
                        fontWeight = weight,
                        fontStyle = fontSty,
                        fontFamily = family,
                        textAlign = textAlignValue,
                        softWrap = true,
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier.width(textWidthConstraintDp),
                        style = androidx.compose.ui.text.TextStyle(
                            letterSpacing = finalLetterSpacingSp,
                            lineHeight = finalLineHeightSp
                        )
                    )
                }
            }
        }
        if (isSelected) {
            Box(modifier = Modifier
                .graphicsLayer {
                    val posTx = (clampedX - 50f) / 100f * refPx;
                    val posTy = (clampedY - 50f) / 100f * refPx; translationX =
                    posTx + frame.translateX; translationY = posTy + frame.translateY; rotationZ =
                    sampled.rotation + frame.rotationZ
                }
                .width(handleW.coerceAtLeast(20.dp))
                .height(handleH.coerceAtLeast(20.dp))) {
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
                    onDelete = onDeleteLayer
                )
            }
        }
    }
}

@Composable
private fun InteractiveStickerOverlay(
    clip: EditorClip,
    stickerState: com.moody.moodyvideoeditor.data.StickerState,
    canvasW: Float,
    canvasH: Float,
    currentPosMs: Long,
    isSelected: Boolean,
    isMulti: Boolean = false,
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
    val clampedX = sampled.x.coerceIn(0f, 100f);
    val clampedY = sampled.y.coerceIn(0f, 100f)
    var isDragging by remember { mutableStateOf(false) }
    val animDur = stickerState.animationDuration.coerceAtLeast(0.1f);
    val progress = (localTimeSec / animDur).coerceIn(0f, 1f)
    val rawFrame = try {
        com.moody.moodyvideoeditor.utils.AnimationsEngine.computeFrame(
            stickerState.animation,
            progress,
            localTimeSec
        )
    } catch (_: Throwable) {
        com.moody.moodyvideoeditor.utils.AnimationsEngine.Frame()
    }
    val frame = if (isDragging) rawFrame.copy(translateX = 0f, translateY = 0f) else rawFrame
    var stickerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val canvasWpx = with(density) { canvasW.dp.toPx() };
    val canvasHpx = with(density) { canvasH.dp.toPx() }
    val refPx = minOf(canvasWpx, canvasHpx)
    val effStickerSizePx = TextScaler.fontSize(baseSize = 48, canvasWidthPx = refPx)
    val effStickerSizeSp = (effStickerSizePx / density.density).sp
    val blurDp = with(density) { frame.blurRadiusPx.toDp() }.coerceAtLeast(0.dp).coerceAtMost(20.dp)
    val currentSampled by rememberUpdatedState(sampled);
    val currentClampedX by rememberUpdatedState(clampedX);
    val currentClampedY by rememberUpdatedState(clampedY)
    val scaleFactor = sampled.scale / 100f;
    val wDp = with(density) { stickerSize.width.toDp() };
    val hDp = with(density) { stickerSize.height.toDp() }
    val handleW = (wDp * scaleFactor).coerceAtLeast(20.dp);
    val handleH = (hDp * scaleFactor).coerceAtLeast(20.dp)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val posTx = (clampedX - 50f) / 100f * refPx;
                    val posTy = (clampedY - 50f) / 100f * refPx
                    translationX = posTx + frame.translateX; translationY = posTy + frame.translateY
                    scaleX = (sampled.scale / 100f) * frame.scaleX; scaleY =
                    (sampled.scale / 100f) * frame.scaleY
                    rotationZ = sampled.rotation + frame.rotationZ; rotationX =
                    frame.rotationX; rotationY = frame.rotationY
                    cameraDistance = 16f * density.density; alpha =
                    (stickerState.opacity / 100f) * frame.alpha
                    transformOrigin = TransformOrigin.Center
                }
                .then(
                    if (blurDp > 0.dp) Modifier.blur(
                        blurDp,
                        BlurredEdgeTreatment.Unbounded
                    ) else Modifier
                )
                .padding(6.dp)
                .pointerInput(clip.id, isSelected, isMulti, canvasW, canvasH) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.isConsumed) return@awaitEachGesture
                        isDragging = true; onGroupGestureStart()
                        val baseX = currentClampedX;
                        val baseY = currentClampedY;
                        val baseScale = currentSampled.scale;
                        val baseRot = currentSampled.rotation
                        var accumPanX = 0f;
                        var accumPanY = 0f;
                        var accumZoom = 1f;
                        var accumRot = 0f;
                        var lastDist = 0f;
                        var lastAngle = 0f;
                        var hasMultiGesture = false;
                        var hasMoved = false
                        if (!isSelected && !isMulti) onSelect()
                        var continueGesture = true
                        while (continueGesture) {
                            val event = awaitPointerEvent();
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                continueGesture = false
                            } else {
                                if (pressed.size == 1) {
                                    val ch = pressed.first();
                                    val pan = ch.position - ch.previousPosition
                                    accumPanX += pan.x; accumPanY += pan.y
                                    if (kotlin.math.abs(accumPanX) > 5f || kotlin.math.abs(accumPanY) > 5f) hasMoved =
                                        true
                                    ch.consume()
                                } else if (pressed.size >= 2) {
                                    val c1 = pressed[0];
                                    val c2 = pressed[1];
                                    val d = c1.position - c2.position
                                    val dist = kotlin.math.sqrt(d.x * d.x + d.y * d.y);
                                    val angle = kotlin.math.atan2(d.y, d.x)
                                    if (hasMultiGesture && lastDist > 1f) {
                                        accumZoom *= dist / lastDist; accumRot += Math.toDegrees(
                                            normalizeAngle(angle - lastAngle).toDouble()
                                        ).toFloat()
                                    }
                                    lastDist = dist; lastAngle = angle; hasMultiGesture =
                                        true; c1.consume(); c2.consume(); hasMoved = true
                                }
                                val rawX = baseX + accumPanX / canvasWpx * 100f;
                                val rawY = baseY + accumPanY / canvasHpx * 100f
                                val cx = rawX.coerceIn(0f, 100f);
                                val cy = rawY.coerceIn(0f, 100f)
                                val newScale = (baseScale * accumZoom).coerceIn(10f, 500f);
                                val newRot = baseRot + accumRot
                                if (isMulti) onGroupGesture(
                                    clip.id,
                                    cx,
                                    cy,
                                    newScale,
                                    newRot
                                ) else {
                                    onPositionChanged(cx, cy); onTransformChanged(newScale, newRot)
                                }
                            }
                        }
                        if (!hasMoved && !isMulti) onSelect()
                        onGroupGestureEnd(); isDragging = false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stickerState.emoji,
                fontSize = effStickerSizeSp,
                modifier = Modifier.onSizeChanged { stickerSize = it })
        }
        if (isSelected) {
            Box(modifier = Modifier
                .graphicsLayer {
                    val posTx = (clampedX - 50f) / 100f * refPx;
                    val posTy = (clampedY - 50f) / 100f * refPx; translationX =
                    posTx + frame.translateX; translationY = posTy + frame.translateY; rotationZ =
                    sampled.rotation + frame.rotationZ
                }
                .width(handleW)
                .height(handleH)) {
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
                    onDelete = onDeleteLayer
                )
            }
        }
    }
}

@Composable
private fun BoxScope.LayerTransformHandles(
    clipId: String,
    scale: Float,
    rotation: Float,
    widthFraction: Float = 1f,
    heightFraction: Float = 1f,
    positionX: Float,
    positionY: Float,
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
            .border(1.dp, Color(0xFF60EFFF), RoundedCornerShape(4.dp))
    ) {
        val density = LocalDensity.current;
        val boundsWidthPx = with(density) { maxWidth.toPx() };
        val boundsHeightPx = with(density) { maxHeight.toPx() };
        val handleSizePx = with(density) { 14.dp.toPx() }
        TransformHandle(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-7).dp, y = (-7).dp),
            clipId = clipId,
            corner = TransformHandleCorner.Delete,
            boundsWidthPx = boundsWidthPx,
            boundsHeightPx = boundsHeightPx,
            handleSizePx = handleSizePx,
            scale = scale,
            rotation = rotation,
            positionX = positionX,
            positionY = positionY,
            onPositionChanged = onPositionChanged,
            onGestureStart = onGestureStart,
            onGestureEnd = onGestureEnd,
            onTransformChanged = onTransformChanged,
            onDelete = onDelete
        )
        TransformHandle(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-7).dp, y = 7.dp),
            clipId = clipId,
            corner = TransformHandleCorner.Rotate,
            boundsWidthPx = boundsWidthPx,
            boundsHeightPx = boundsHeightPx,
            handleSizePx = handleSizePx,
            scale = scale,
            rotation = rotation,
            positionX = positionX,
            positionY = positionY,
            onPositionChanged = onPositionChanged,
            onGestureStart = onGestureStart,
            onGestureEnd = onGestureEnd,
            onTransformChanged = onTransformChanged,
            onDelete = onDelete
        )
        TransformHandle(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 7.dp, y = 7.dp),
            clipId = clipId,
            corner = TransformHandleCorner.Scale,
            boundsWidthPx = boundsWidthPx,
            boundsHeightPx = boundsHeightPx,
            handleSizePx = handleSizePx,
            scale = scale,
            rotation = rotation,
            positionX = positionX,
            positionY = positionY,
            onPositionChanged = onPositionChanged,
            onGestureStart = onGestureStart,
            onGestureEnd = onGestureEnd,
            onTransformChanged = onTransformChanged,
            onDelete = onDelete
        )
    }
}

private enum class TransformHandleCorner { Delete, Rotate, Scale }

@Composable
private fun TransformHandle(
    modifier: Modifier,
    clipId: String,
    corner: TransformHandleCorner,
    boundsWidthPx: Float,
    boundsHeightPx: Float,
    handleSizePx: Float,
    scale: Float,
    rotation: Float,
    positionX: Float,
    positionY: Float,
    onPositionChanged: (Float, Float) -> Unit,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    onTransformChanged: (Float, Float) -> Unit,
    onDelete: () -> Unit
) {
    val currentScale = rememberUpdatedState(scale);
    val currentRotation = rememberUpdatedState(rotation);
    val currentOnTransformChanged = rememberUpdatedState(onTransformChanged)
    val currentOnGestureStart = rememberUpdatedState(onGestureStart);
    val currentOnGestureEnd = rememberUpdatedState(onGestureEnd);
    val currentOnDelete = rememberUpdatedState(onDelete)
    val currentBoundsWidth by rememberUpdatedState(boundsWidthPx);
    val currentBoundsHeight by rememberUpdatedState(boundsHeightPx)

    Box(
        modifier = modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(
                when (corner) {
                    TransformHandleCorner.Delete -> Color(0xFFE5484D); TransformHandleCorner.Rotate, TransformHandleCorner.Scale -> Color(
                    0xFF171A22
                )
                }
            )
            .border(1.dp, Color(0xFF60EFFF), CircleShape)
            .pointerInput(clipId, corner) {
                when (corner) {
                    TransformHandleCorner.Delete -> detectTapGestures { currentOnDelete.value() }
                    TransformHandleCorner.Rotate, TransformHandleCorner.Scale -> awaitEachGesture {
                        val down =
                            awaitFirstDown(requireUnconsumed = false); down.consume(); currentOnGestureStart.value()
                        val startBoundsW = currentBoundsWidth;
                        val startBoundsH = currentBoundsHeight
                        val handleCenterX =
                            if (corner == TransformHandleCorner.Scale) startBoundsW else 0f
                        val handleCenterY =
                            if (corner == TransformHandleCorner.Scale || corner == TransformHandleCorner.Rotate) startBoundsH else 0f
                        val startX =
                            handleCenterX + (down.position.x - handleSizePx / 2f) - startBoundsW / 2f
                        val startY =
                            handleCenterY + (down.position.y - handleSizePx / 2f) - startBoundsH / 2f
                        val startDistance =
                            kotlin.math.hypot(startX.toDouble(), startY.toDouble()).toFloat()
                                .coerceAtLeast(1f)
                        val startAngle = kotlin.math.atan2(startY, startX);
                        val startScale = currentScale.value;
                        val startRotation = currentRotation.value
                        var previousAngle = startAngle;
                        var accumulatedRotation = 0f;
                        var dragX = 0f;
                        var dragY = 0f
                        do {
                            val event = awaitPointerEvent();
                            val change = event.changes.firstOrNull()
                            if (change == null || !change.pressed) break
                            val delta =
                                change.position - change.previousPosition; dragX += delta.x; dragY += delta.y; change.consume()
                            val currentX = startX + dragX;
                            val currentY = startY + dragY
                            val currentDistance =
                                kotlin.math.hypot(currentX.toDouble(), currentY.toDouble())
                                    .toFloat()
                            val currentAngle = kotlin.math.atan2(currentY, currentX)
                            accumulatedRotation += normalizeAngle(currentAngle - previousAngle); previousAngle =
                                currentAngle
                            if (corner == TransformHandleCorner.Scale) currentOnTransformChanged.value(
                                (startScale * currentDistance / startDistance).coerceIn(10f, 500f),
                                startRotation
                            )
                            else currentOnTransformChanged.value(
                                startScale,
                                startRotation + Math.toDegrees(accumulatedRotation.toDouble())
                                    .toFloat()
                            )
                        } while (true)
                        currentOnGestureEnd.value()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when (corner) {
                TransformHandleCorner.Delete -> "×"; TransformHandleCorner.Rotate -> "⟳"; TransformHandleCorner.Scale -> "⤢"
            }, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold
        )
    }
}

private fun normalizeAngle(radians: Float): Float {
    val twoPi = (Math.PI * 2.0).toFloat();
    var normalized = radians
    while (normalized > Math.PI) normalized -= twoPi
    while (normalized < -Math.PI) normalized += twoPi
    return normalized
}
// ═══════════════════════════════════════════════════════════════
//  🟩 EYEDROPPER OVERLAY — v3 (Exact touch point + smaller)
// ═══════════════════════════════════════════════════════════════
@Composable
fun EyedropperOverlay(
    active: Boolean,
    onColorPicked: (Long) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!active) return

    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val window = activity?.window
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var screenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var bitmapsLoaded by remember { mutableStateOf(false) }

    var loupeX by remember { mutableFloatStateOf(-1000f) }
    var loupeY by remember { mutableFloatStateOf(-1000f) }
    var currentColor by remember { mutableStateOf<Long?>(null) }

    // ✅ Size chota kar diya (40dp)
    val loupeDiameterDp = 40.dp
    val loupeDiameterPx = with(density) { loupeDiameterDp.toPx() }

    // Window ki exact position nikalne ke liye
    var viewLocation by remember { mutableStateOf<IntArray?>(null) }

    LaunchedEffect(active, window) {
        if (!active || window == null) return@LaunchedEffect
        try {
            val decor = window.decorView
            // Pura screen capture karo (taaki touch coordinates match karein)
            val bmp = Bitmap.createBitmap(
                decor.width.coerceAtLeast(1),
                decor.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val latch = CountDownLatch(1)
                android.view.PixelCopy.request(
                    window, bmp,
                    {
                        bitmapsLoaded = true
                        latch.countDown()
                    },
                    android.os.Handler(android.os.Looper.getMainLooper())
                )
                latch.await(1, TimeUnit.SECONDS)
            } else {
                decor.isDrawingCacheEnabled = true
                decor.buildDrawingCache()
                val cacheBmp = decor.drawingCache
                if (cacheBmp != null) {
                    val canvas = android.graphics.Canvas(bmp)
                    canvas.drawBitmap(cacheBmp, 0f, 0f, null)
                }
                decor.isDrawingCacheEnabled = false
                bitmapsLoaded = true
            }
            screenBitmap = bmp
        } catch (t: Throwable) {
            Log.e("Eyedropper", "Failed to capture screen", t)
        }
    }

    DisposableEffect(active) {
        onDispose {
            screenBitmap?.takeIf { !it.isRecycled }?.recycle()
            screenBitmap = null
        }
    }

    fun updateColorAt(viewX: Float, viewY: Float) {
        val bmp = screenBitmap ?: return
        val loc = viewLocation ?: return

        // View coordinates ko Screen coordinates mein convert karo
        val screenX = viewX + loc[0]
        val screenY = viewY + loc[1]

        // Bounds check
        val px = screenX.toInt().coerceIn(0, bmp.width - 1)
        val py = screenY.toInt().coerceIn(0, bmp.height - 1)

        try {
            val c = bmp.getPixel(px, py)
            currentColor = 0xFF000000L or
                    ((android.graphics.Color.red(c).toLong() and 0xFF) shl 16) or
                    ((android.graphics.Color.green(c).toLong() and 0xFF) shl 8) or
                    (android.graphics.Color.blue(c).toLong() and 0xFF)
        } catch (e: Exception) {
            Log.e("Eyedropper", "Failed to get pixel at $px, $py", e)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                // Fallback jo har version mein chalta hai
                val pos = coordinates.localToRoot(Offset.Zero)
                viewLocation = intArrayOf(pos.x.toInt(), pos.y.toInt())
            }
            .pointerInput(active, screenBitmap, viewLocation) {
                if (!bitmapsLoaded || screenBitmap == null || viewLocation == null) return@pointerInput

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)

                    // ✅ Exact jagah set karo
                    loupeX = down.position.x
                    loupeY = down.position.y
                    updateColorAt(loupeX, loupeY)

                    var continueGesture = true
                    while (continueGesture) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }

                        if (change == null || !change.pressed) {
                            if (currentColor != null) onColorPicked(currentColor!!)
                            else onCancel()
                            continueGesture = false
                        } else {
                            // ✅ Drag karte waqt bhi exact position update karo
                            loupeX = change.position.x
                            loupeY = change.position.y
                            updateColorAt(loupeX, loupeY)
                            change.consume()
                        }
                    }
                }
            }
    ) {
        // Dim overlay (thoda halka kiya taaki background dikhe)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.15f))
        )

        // Top banner
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .background(Color(0xFF7C3AED), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "🎯 Drag to move · Tap to pick",
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
        }

        // Cancel button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(36.dp)
                .background(Color(0xFFFF3B3B), CircleShape)
                .clickable { onCancel() },
            contentAlignment = Alignment.Center
        ) {
            Text("✕", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        // Loupe circle
        val bmp = screenBitmap
        val loc = viewLocation
        if (bmp != null && !bmp.isRecycled && bitmapsLoaded && loupeX > -500f && loc != null) {

            // 1. Circle ki position set karo (touch point pe center)
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { loupeX.toDp() } - loupeDiameterDp / 2,
                        y = with(density) { loupeY.toDp() } - loupeDiameterDp / 2
                    )
                    .size(loupeDiameterDp)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(2.dp, Color.White, CircleShape)
            ) {
                // 2. Crop karo bitmap ko exact screen coordinates se
                val screenX = loupeX + loc[0]
                val screenY = loupeY + loc[1]

                val srcX = (screenX - loupeDiameterPx / 2)
                    .toInt()
                    .coerceIn(0, (bmp.width - loupeDiameterPx.toInt()).coerceAtLeast(0))
                val srcY = (screenY - loupeDiameterPx / 2)
                    .toInt()
                    .coerceIn(0, (bmp.height - loupeDiameterPx.toInt()).coerceAtLeast(0))

                val srcW = loupeDiameterPx.toInt().coerceAtMost(bmp.width - srcX)
                val srcH = loupeDiameterPx.toInt().coerceAtMost(bmp.height - srcY)

                if (srcW > 0 && srcH > 0) {
                    val cropped = Bitmap.createBitmap(bmp, srcX, srcY, srcW, srcH)
                    Image(
                        bitmap = cropped.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Center crosshair dot
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(4.dp)
                        .background(Color.White, CircleShape)
                        .border(1.dp, Color.Black, CircleShape)
                )
            }

            // Color swatch ke saath exact hex value
            val colorInt = currentColor?.toInt() ?: 0xFF000000.toInt()
            val hexLabel = "#%06X".format(currentColor ?: 0L)

            Row(
                modifier = Modifier
                    .offset(
                        x = with(density) { loupeX.toDp() } - 35.dp,
                        y = with(density) { loupeY.toDp() } + loupeDiameterDp / 2 + 8.dp
                    )
                    .width(70.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.85f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(colorInt))
                        .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    hexLabel,
                    color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
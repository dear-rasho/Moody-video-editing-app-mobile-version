package com.moody.moodyvideoeditor.ui.components

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.media.MediaMetadataRetriever
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
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
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.EffectState
import com.moody.moodyvideoeditor.data.FilterState
import com.moody.moodyvideoeditor.data.MaskState
import com.moody.moodyvideoeditor.data.MaskType
import com.moody.moodyvideoeditor.data.OverlayState
import com.moody.moodyvideoeditor.utils.BrushEngine
import com.moody.moodyvideoeditor.utils.ColorMatrixBuilder
import com.moody.moodyvideoeditor.utils.EffectsEngine
import com.moody.moodyvideoeditor.utils.FontLibrary
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ═══════════════════════════════════════════════════════════════
//  HELPER — Per-clip color matrix
// ═══════════════════════════════════════════════════════════════
private fun buildClipMatrix(
    clip: EditorClip,
    globalMatrix: android.graphics.ColorMatrix,
    applyGlobal: Boolean
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

    val hasChange =
        !clip.adjustments.isDefault ||
                clip.filters.hasAnyChange ||
                applyGlobal

    return if (hasChange) cm else null
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
    isDrawingMode: Boolean = false,
    activeBrushType: BrushType = BrushType.PEN,
    activeBrushColor: Long = 0xFFFF0000,
    activeBrushWidth: Float = 20f,
    activeBrushOpacity: Float = 1f,
    onBrushStrokeComplete: (BrushStroke) -> Unit = {},
    isMaskPenMode: Boolean = false,
    onMaskPointAdd: (Float, Float) -> Unit = { _, _ -> },
    onMaskAnchorMove: (Int, Float, Float) -> Unit = { _, _, _ -> },
    onMaskHandleMove: (Int, Boolean, Float, Float) -> Unit = { _, _, _, _ -> },
    onMaskPointToggle: (Int) -> Unit = {},
    onMaskPointDelete: (Int) -> Unit = {},
    onClosePath: () -> Unit = {},
    onClipSelected: (String) -> Unit = {},
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

    // ═══════════════════════════════════════════════════════════
    //  ACTIVE CLIPS
    // ═══════════════════════════════════════════════════════════
    val activeClips = clips
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
    val maskToRender: MaskState? = if (isMaskPenMode && selectedClip != null) {
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

    // ═══════════════════════════════════════════════════════════
    //  ROOT BOX
    // ═══════════════════════════════════════════════════════════
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
                    // ─────────── VIDEO ───────────
                    clip.isVisualClip && !clip.type.startsWith("image/") -> {
                        if (clip.id != topVideoClip?.id) return@forEach
                        val videoTransform2 = TransformApplier.resolveLive(
                            clip,
                            ((currentPosMs - clip.timelineStartMs) / 1000f)
                                .coerceAtLeast(0f)
                        )
                        MaskedClipContent(mask = clip.mask) {
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
                                                clip, combinedMatrix, applyMatrix
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
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // ─────────── IMAGE ───────────
                    clip.isVisualClip && clip.type.startsWith("image/") -> {
                        val imgLocalSec =
                            ((currentPosMs - clip.timelineStartMs) / 1000f)
                                .coerceAtLeast(0f)
                        val imgTransform =
                            TransformApplier.resolveLive(clip, imgLocalSec)

                        val isSelected = clip.id == selectedClipId
                        val isMulti = clip.id in multiSelectedIds
                        val borderColor = when {
                            isSelected -> Color(0xFF60EFFF)
                            isMulti -> Color(0xFFFFD166)
                            else -> Color.Transparent
                        }

                        MaskedClipContent(mask = clip.mask) {
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
                                            pivotFractionX =
                                                imgTransform.anchorX / 100f,
                                            pivotFractionY =
                                                imgTransform.anchorY / 100f
                                        )
                                        alpha = opacityAlpha * transT.alpha
                                        this.clip = true
                                    }
                                    .then(
                                        if (isSelected || isMulti) {
                                            Modifier.border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = borderColor,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                        } else Modifier
                                    )
                                    .pointerInput(clip.id, isSelected, isMulti) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
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
                                                        val angle = kotlin.math.atan2(d.y, d.x)
                                                        if (hasMulti && lastDist > 1f) {
                                                            accumZoom *= dist / lastDist
                                                            accumRot += Math.toDegrees(
                                                                (angle - lastAngle).toDouble()
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
                                                            clip.id,
                                                            newX,
                                                            newY,
                                                            newScale,
                                                            newRot
                                                        )
                                                    } else {
                                                        onBrushPositionChanged(clip.id, newX, newY)
                                                        onBrushTransformChanged(
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
                                val imgColorFilter = remember(
                                    clip.filters, clip.adjustments,
                                    combinedMatrix, applyMatrix
                                ) {
                                    val cm = buildClipMatrix(clip, combinedMatrix, applyMatrix)
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
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    // ─────────── 🆕 VISUALIZER (as regular layer) ───────────
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
                            // Visualizer rendered on full canvas
                            VisualizerOverlay(
                                state = vs,
                                visualizerClip = clip,
                                allClips = clips,
                                currentPosMs = currentPosMs,
                                isPlaying = isPlaying,
                                enabled = true,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Interactive gesture box matching visualizer bounds
                            Box(
                                modifier = Modifier
                                    .offset(x = leftDp.dp, y = topDp.dp)
                                    .size(diameterDp.dp)
                                    .then(
                                        if (isSel || isMulti) {
                                            Modifier.border(
                                                width = if (isSel) 2.dp else 1.dp,
                                                color = if (isSel)
                                                    Color(0xFF60EFFF)
                                                else Color(0xFFFFD166),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        } else Modifier
                                    )
                                    .pointerInput(clip.id, isSel, isMulti, canvasW, canvasH) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
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
                                                        val angle = kotlin.math.atan2(d.y, d.x)
                                                        if (hasMulti && lastDist > 1f) {
                                                            accumZoom *= dist / lastDist
                                                            accumRot += Math.toDegrees(
                                                                (angle - lastAngle).toDouble()
                                                            ).toFloat()
                                                        }
                                                        lastDist = dist
                                                        lastAngle = angle
                                                        hasMulti = true
                                                        c1.consume()
                                                        c2.consume()
                                                    }

                                                    val newX = (baseX + accumPanX /
                                                            size.width * 100f)
                                                        .coerceIn(0f, 100f)
                                                    val newY = (baseY + accumPanY /
                                                            size.height * 100f)
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
                                    .pointerInput(clip.id, isMulti) {
                                        detectTapGestures {
                                            if (!isMulti) onClipSelected(clip.id)
                                        }
                                    }
                            )
                        }
                    }
                    // ─────────── BRUSH ───────────
                    clip.isBrushClip -> {
                        if (clip.brush.strokes.isEmpty()) return@forEach
                        val localTimeSec =
                            ((currentPosMs - clip.timelineStartMs) / 1000f)
                                .coerceAtLeast(0f)
                        val brushTransform =
                            TransformApplier.resolveLive(clip, localTimeSec)

                        val isSelected = clip.id == selectedClipId
                        val isMulti = clip.id in multiSelectedIds
                        val borderColor = when {
                            isSelected -> Color(0xFF60EFFF)
                            isMulti -> Color(0xFFFFD166)
                            else -> Color.Transparent
                        }

                        MaskedClipContent(mask = clip.mask) {
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
                                        if (isSelected || isMulti) {
                                            Modifier.border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = borderColor,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                        } else Modifier
                                    )
                                    .pointerInput(clip.id, isSelected, isMulti) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
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
                                                        val angle = kotlin.math.atan2(d.y, d.x)
                                                        if (hasMulti && lastDist > 1f) {
                                                            accumZoom *= dist / lastDist
                                                            accumRot += Math.toDegrees(
                                                                (angle - lastAngle).toDouble()
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
                                                            clip.id,
                                                            newX,
                                                            newY,
                                                            newScale,
                                                            newRot
                                                        )
                                                    } else {
                                                        onBrushPositionChanged(clip.id, newX, newY)
                                                        onBrushTransformChanged(
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
                            }
                        }
                    }

                    // ─────────── TEXT ───────────
                    clip.isTextClip -> {
                        val st = clip.textState ?: return@forEach
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
                                onTextPositionChanged(clip.id, x, y)
                            },
                            onTransformChanged = { s, r ->
                                onTextTransformChanged(clip.id, s, r)
                            }
                        )
                    }

                    // ─────────── STICKER ───────────
                    clip.isStickerClip -> {
                        val ss = clip.stickerState ?: return@forEach
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
                                onStickerPositionChanged(clip.id, x, y)
                            },
                            onTransformChanged = { s, r ->
                                onStickerTransformChanged(clip.id, s, r)
                            }
                        )
                    }
                }
            }

            // ─────────── OVERLAY EFFECTS ───────────
            if (allOverlays.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    allOverlays.forEach { ov -> OverlayEngine.draw(this, timeSec, ov) }
                }
            }


            // ─────────── VIGNETTE ───────────
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

            // ─────────── TRANSITION ───────────
            if (activeTransitionClip != null && outgoingBitmap != null) {
                val ts = activeTransitionClip.transition!!
                val progress = (
                        (currentPosMs - activeTransitionClip.timelineStartMs).toFloat() /
                                ts.durationMs.coerceAtLeast(1L)
                        ).coerceIn(0f, 1f)

                TransitionRenderer.Render(outgoingBitmap!!, ts.key, progress)
            }

            // ─────────── MASK PEN ───────────
            if (isMaskPenMode && selectedClip != null) {
                MaskPenOverlay(
                    maskState = maskToRender
                        ?: MaskState(type = MaskType.CUSTOM),
                    canvasW = canvasW,
                    canvasH = canvasH,
                    onTapAddPoint = onMaskPointAdd,
                    onAnchorMove = onMaskAnchorMove,
                    onHandleMove = onMaskHandleMove,
                    onTogglePoint = onMaskPointToggle,
                    onDeletePoint = onMaskPointDelete,
                    onClosePath = onClosePath
                )
            }

            // ─────────── BRUSH DRAW MODE ───────────
            if (isDrawingMode && !isMaskPenMode) {
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

        // ─────────── SIDE BLACK BARS ───────────
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
//  MASKED CLIP CONTENT WRAPPER
// ═══════════════════════════════════════════════════════════════
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
        val featherPx = kotlin.math.abs(mask.feather / 100f * 1.5f)
        val alphaInt = (mask.opacity / 100f * 255).toInt().coerceIn(0, 255)

        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = android.graphics.Color.WHITE
            alpha = alphaInt
            xfermode = PorterDuffXfermode(
                if (mask.isInverted) PorterDuff.Mode.DST_OUT
                else PorterDuff.Mode.DST_IN
            )
            if (featherPx > 0.5f) {
                maskFilter = BlurMaskFilter(featherPx, BlurMaskFilter.Blur.NORMAL)
            }
        }
        nativeCanvas.drawPath(path.asAndroidPath(), paint)
        paint.reset()
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
                val r = mask.radius * minOf(w, h)
                addOval(Rect(cx - r, cy - r, cx + r, cy + r))
            }

            MaskType.RECTANGLE -> {
                val hw = mask.width * w / 2f
                val hh = mask.height * h / 2f
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
                val lineY = mask.positionY * h
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
                val s = mask.scale * minOf(w, h) * 0.4f
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
                val pts = mask.customPoints
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
                                c1.first, c1.second,
                                c2.first, c2.second,
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
                            c1.first, c1.second,
                            c2.first, c2.second,
                            end.first, end.second
                        )
                    } else close()
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
    onTapAddPoint: (Float, Float) -> Unit,
    onAnchorMove: (Int, Float, Float) -> Unit,
    onHandleMove: (Int, Boolean, Float, Float) -> Unit,
    onTogglePoint: (Int) -> Unit,
    onDeletePoint: (Int) -> Unit,
    onClosePath: () -> Unit
) {
    val density = LocalDensity.current
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
            .pointerInput(pts.size, maskState.type, isClosed) {
                awaitEachGesture {
                    val viewW = viewSizePx.width.toFloat()
                    val viewH = viewSizePx.height.toFloat()
                    if (viewW <= 0f || viewH <= 0f) return@awaitEachGesture

                    val down = awaitFirstDown(requireUnconsumed = false)

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
            if (pts.isEmpty()) return@Canvas

            val path = Path().apply { moveTo(pts[0].x * w, pts[0].y * h) }
            for (i in 1 until pts.size) {
                val prev = pts[i - 1]
                val curr = pts[i]
                if (prev.hasHandles || curr.hasHandles) {
                    path.cubicTo(
                        prev.outX * w, prev.outY * h,
                        curr.inX * w, curr.inY * h,
                        curr.x * w, curr.y * h
                    )
                } else {
                    path.lineTo(curr.x * w, curr.y * h)
                }
            }
            if (isClosed && pts.size >= 3) {
                val last = pts.last()
                val first = pts.first()
                if (last.hasHandles || first.hasHandles) {
                    path.cubicTo(
                        last.outX * w, last.outY * h,
                        first.inX * w, first.inY * h,
                        first.x * w, first.y * h
                    )
                } else path.close()
            }

            drawPath(path, strokeColor, style = Stroke(width = 3f))

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
    }
}

// ═══════════════════════════════════════════════════════════════
//  BRUSH DRAW LAYER
// ═══════════════════════════════════════════════════════════════
@Composable
private fun BrushDrawLayer(
    brushType: BrushType,
    brushColor: Long,
    brushWidth: Float,
    brushOpacity: Float,
    clipStartMs: Long,
    clipEndMs: Long,
    currentPosMs: Long,
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
                            val localStart = (currentPosMs - clipStartMs)
                                .coerceAtLeast(0L)
                            val localEnd = (clipEndMs - clipStartMs)
                                .coerceAtLeast(localStart + 1)
                            onStrokeComplete(
                                BrushStroke(
                                    id = UUID.randomUUID().toString(),
                                    type = brushType,
                                    color = brushColor,
                                    width = brushWidth,
                                    opacity = brushOpacity,
                                    points = currentPoints,
                                    startMs = localStart,
                                    endMs = localEnd
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
                        id = "temp",
                        type = brushType,
                        color = brushColor,
                        width = brushWidth,
                        opacity = brushOpacity,
                        points = currentPoints,
                        startMs = 0,
                        endMs = Long.MAX_VALUE
                    ),
                    viewW = size.width,
                    viewH = size.height,
                    currentTimeMs = 0
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
    onTransformChanged: (Float, Float) -> Unit
) {
    if (textState.content.isBlank()) return

    val localTimeSec = ((currentPosMs - clip.timelineStartMs) / 1000f)
        .coerceAtLeast(0f)
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

    val effFontSize = TextScaler.fontSize(textState.fontSize, canvasW)
    val effLetterSpacing = TextScaler.letterSpacing(textState.letterSpacing, canvasW)
    val effLineHeight = TextScaler.lineHeight(effFontSize, textState.lineHeight)
    val effMaxWidth = TextScaler.maxTextWidth(canvasW, textState.maxWidth)

    val userStrokeDp = textState.strokeWidth.coerceAtLeast(0f)
    val strokeWidthPx = with(density) {
        (userStrokeDp * 2f).coerceAtLeast(2f).dp.toPx()
    }

    val measurer = androidx.compose.ui.text.rememberTextMeasurer()

    val naturalMeasure = remember(displayContent, effFontSize, effLetterSpacing) {
        measurer.measure(
            text = displayContent,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = effFontSize.sp,
                letterSpacing = effLetterSpacing.sp
            ),
            constraints = androidx.compose.ui.unit.Constraints(maxWidth = Int.MAX_VALUE),
            maxLines = 1,
            softWrap = false
        )
    }

    val allowedWidthPx = with(density) { effMaxWidth.dp.toPx() }
    val finalFontSize = if (naturalMeasure.size.width > allowedWidthPx) {
        val ratio = allowedWidthPx / naturalMeasure.size.width.toFloat()
        (effFontSize * ratio).coerceAtLeast(10f)
    } else effFontSize

    val finalMeasure = remember(
        displayContent, finalFontSize, effLetterSpacing, effLineHeight
    ) {
        measurer.measure(
            text = displayContent,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = finalFontSize.sp,
                letterSpacing = effLetterSpacing.sp,
                lineHeight = effLineHeight.sp
            ),
            constraints = androidx.compose.ui.unit.Constraints(maxWidth = Int.MAX_VALUE),
            softWrap = false
        )
    }

    val textWidthDp = with(density) { finalMeasure.size.width.toDp().value }
    val textHeightDp = with(density) { finalMeasure.size.height.toDp().value }

    val (clampedX, clampedY) = TextScaler.clampPosition(
        x = sampled.x,
        y = sampled.y,
        textWidthDp = textWidthDp,
        textHeightDp = textHeightDp,
        canvasWidthDp = canvasW,
        canvasHeightDp = canvasH
    )

    val family = FontLibrary.familyFor(textState.fontFamily)
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
                offset = Offset(textState.shadowOffsetX, textState.shadowOffsetY),
                blurRadius = textState.shadowBlur.coerceAtLeast(0f)
            )
        } else null

    val textAlignValue = when (textState.alignment) {
        "left" -> androidx.compose.ui.text.style.TextAlign.Left
        "right" -> androidx.compose.ui.text.style.TextAlign.Right
        else -> androidx.compose.ui.text.style.TextAlign.Center
    }

    val weight = if (textState.fontWeight == "bold") FontWeight.Bold
    else FontWeight.Normal
    val fontSty = if (textState.fontStyle == "italic")
        androidx.compose.ui.text.font.FontStyle.Italic
    else androidx.compose.ui.text.font.FontStyle.Normal

    val borderColor = when {
        isSelected -> Color(0xFF60EFFF)
        isMulti -> Color(0xFFFFD166)
        else -> Color.Transparent
    }

    val glowDensity = density.density
    val glowSizePx = with(density) { finalFontSize.sp.toPx() }
    val glowLetterSpacingEm = if (finalFontSize <= 0f) 0f
    else (effLetterSpacing / finalFontSize).coerceIn(-0.3f, 0.3f)

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
                    alpha = (textState.opacity / 100f) * frame.alpha
                    transformOrigin = TransformOrigin.Center
                }
                .then(
                    if (isSelected || isMulti) {
                        Modifier.border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(4.dp)
                        )
                    } else Modifier
                )
                .padding(8.dp)
                .pointerInput(clip.id, isSelected, isMulti, canvasWpx, canvasHpx) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        isDragging = true
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
                                isDragging = false
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
                                    val angle = kotlin.math.atan2(d.y, d.x)

                                    if (hasMultiGesture && lastDist > 1f) {
                                        accumZoom *= dist / lastDist
                                        accumRot += Math.toDegrees(
                                            (angle - lastAngle).toDouble()
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

                                val (cx, cy) = TextScaler.clampPosition(
                                    rawX, rawY, textWidthDp, textHeightDp,
                                    canvasW, canvasH
                                )
                                val newScale = (baseScale * accumZoom)
                                    .coerceIn(10f, 500f)
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
                .pointerInput(clip.id, isMulti) {
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
                    val typeface = TextRenderContract.androidTypefaceFor(
                        family,
                        weight == FontWeight.Bold,
                        fontSty == androidx.compose.ui.text.font.FontStyle.Italic
                    )
                    val textSizePx = glowSizePx
                    val letterSpacingEm = glowLetterSpacingEm

                    Canvas(
                        modifier = Modifier.matchParentSize()
                    ) {
                        val nativeCanvas = drawContext.canvas.nativeCanvas
                        val layers = TextRenderContract.glowLayers()
                        layers.forEach { layer ->
                            val paint = Paint(
                                Paint.ANTI_ALIAS_FLAG
                            ).apply {
                                isAntiAlias = true
                                color = glowColorInt
                                textSize = textSizePx * layer.fontScale
                                textAlign = glowAlign
                                this.typeface = typeface
                                this.letterSpacing = letterSpacingEm
                                alpha = (layer.alpha * 255).toInt().coerceIn(0, 255)
                                setShadowLayer(
                                    textState.glowRadius * layer.blurScale * glowDensity,
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

                if (textState.strokeEnabled && userStrokeDp > 0f) {
                    Text(
                        text = displayContent,
                        color = Color(textState.strokeColor),
                        fontSize = finalFontSize.sp,
                        fontWeight = weight,
                        fontStyle = fontSty,
                        fontFamily = family,
                        textAlign = textAlignValue,
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Visible,
                        style = androidx.compose.ui.text.TextStyle(
                            drawStyle = Stroke(
                                strokeWidthPx,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                miter = 4f
                            ),
                            letterSpacing = effLetterSpacing.sp,
                            lineHeight = effLineHeight.sp
                        )
                    )
                }

                Text(
                    text = displayContent,
                    color = if (gradient != null) Color.Unspecified else solidColor,
                    fontSize = finalFontSize.sp,
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
                        letterSpacing = effLetterSpacing.sp,
                        lineHeight = effLineHeight.sp
                    )
                )
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
    onTransformChanged: (Float, Float) -> Unit
) {
    if (stickerState.emoji.isBlank()) return

    val localTimeSec = ((currentPosMs - clip.timelineStartMs) / 1000f)
        .coerceAtLeast(0f)
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

    val effStickerSize = TextScaler.fontSize(
        baseSize = 48,
        canvasWidthDp = canvasW
    )

    val stickerSizeDp = effStickerSize * (sampled.scale / 100f)
    val (clampedX, clampedY) = TextScaler.clampPosition(
        sampled.x, sampled.y, stickerSizeDp, stickerSizeDp, canvasW, canvasH
    )

    val density = LocalDensity.current
    val canvasWpx = with(density) { canvasW.dp.toPx() }
    val canvasHpx = with(density) { canvasH.dp.toPx() }

    val borderColor = when {
        isSelected -> Color(0xFF60EFFF)
        isMulti -> Color(0xFFFFD166)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = (clampedX - 50f) / 100f * canvasWpx +
                            frame.translateX
                    translationY = (clampedY - 50f) / 100f * canvasHpx +
                            frame.translateY
                    scaleX = (sampled.scale / 100f) * frame.scaleX
                    scaleY = (sampled.scale / 100f) * frame.scaleY
                    rotationZ = sampled.rotation + frame.rotationZ
                    alpha = (stickerState.opacity / 100f) * frame.alpha
                    transformOrigin = TransformOrigin.Center
                }
                .then(
                    if (isSelected || isMulti) {
                        Modifier.border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(6.dp)
                        )
                    } else Modifier
                )
                .padding(6.dp)
                .pointerInput(clip.id, isSelected, isMulti, canvasWpx, canvasHpx) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
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
                                    val angle = kotlin.math.atan2(d.y, d.x)

                                    if (hasMultiGesture && lastDist > 1f) {
                                        accumZoom *= dist / lastDist
                                        accumRot += Math.toDegrees(
                                            (angle - lastAngle).toDouble()
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

                                val (cx, cy) = TextScaler.clampPosition(
                                    rawX, rawY, stickerSizeDp, stickerSizeDp,
                                    canvasW, canvasH
                                )
                                val newScale = (baseScale * accumZoom)
                                    .coerceIn(10f, 500f)
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
                .pointerInput(clip.id, isMulti) {
                    detectTapGestures { if (!isMulti) onSelect() }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(text = stickerState.emoji, fontSize = effStickerSize.sp)
        }
    }
}
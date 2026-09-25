package com.moody.moodyvideoeditor.ui.components

import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.moody.moodyvideoeditor.R
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.utils.ColorMatrixBuilder
import com.moody.moodyvideoeditor.utils.EffectsEngine
import com.moody.moodyvideoeditor.utils.OverlayEngine
import com.moody.moodyvideoeditor.utils.RatioHelper
import com.moody.moodyvideoeditor.utils.Transform2D
import com.moody.moodyvideoeditor.utils.TransformApplier
import com.moody.moodyvideoeditor.utils.TransformValues
import com.moody.moodyvideoeditor.utils.TransitionRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
@Composable
fun PreviewCanvas(
    exoPlayer: ExoPlayer,
    hasVideo: Boolean,
    rotation: Int,
    aspectMode: Int,
    clips: List<EditorClip>,
    currentPosMs: Long,
    hiddenVisualTracks: Set<Int> = emptySet(),
    aspectRatioKey: String = "16:9",
    selectedClipId: String? = null,
    onClipSelected: (String) -> Unit = {},
    onTextPositionChanged: (String, Float, Float) -> Unit = { _, _, _ -> },
    onTextTransformChanged: (String, Float, Float) -> Unit = { _, _, _ -> },
    onStickerPositionChanged: (String, Float, Float) -> Unit = { _, _, _ -> },
    onStickerTransformChanged: (String, Float, Float) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current

    val activeVisual = clips
        .filter {
            it.isVisualClip &&
                    currentPosMs >= it.timelineStartMs &&
                    currentPosMs < it.timelineEndMs &&
                    !hiddenVisualTracks.contains(it.trackIndex)
        }
        .maxByOrNull { it.trackIndex }

    val videoTrackIdx = activeVisual?.trackIndex ?: 0

    val activeTransitionClip = remember(currentPosMs, clips) {
        clips.firstOrNull { c ->
            !c.isAudio &&
                    c.transition != null &&
                    c.transition.isActive &&
                    currentPosMs >= c.timelineStartMs &&
                    currentPosMs < c.timelineStartMs + c.transition.durationMs
        }
    }

    val outgoingClip = remember(activeTransitionClip?.id, clips) {
        activeTransitionClip?.let { tc ->
            clips.filter {
                it.isVisualClip &&
                        it.trackIndex == tc.trackIndex &&
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
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, oc.uri)
                val timeUs = (oc.sourceEndMs - 33).coerceAtLeast(0L) * 1000L
                val b = retriever.getFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                retriever.release()
                b
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        outgoingBitmap = bmp
    }

    DisposableEffect(Unit) {
        onDispose {
            outgoingBitmap?.takeIf { !it.isRecycled }?.recycle()
        }
    }

    val videoTransform: TransformValues = activeVisual?.let {
        val timeSec = ((currentPosMs - it.timelineStartMs) / 1000f).coerceAtLeast(0f)
        TransformApplier.resolveLive(it, timeSec)
    } ?: TransformValues()

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
    }
    val combinedMotion = EffectsEngine.combineMotions(motionFrames)

    val filterList = activeEffects.mapNotNull { it.effectState?.filters }
    val combinedFilter = if (filterList.isNotEmpty())
        EffectsEngine.combineFilters(filterList) else null

    val allOverlays = EffectsEngine.collectActiveOverlays(clips, currentPosMs, videoTrackIdx)

    val hasAdjustments = activeAdjustment?.let {
        ColorMatrixBuilder.hasRealTimeAdjustments(it)
    } ?: false
    val hasFilters = EffectsEngine.hasColorEffect(combinedFilter)
    val applyMatrix = hasAdjustments || hasFilters

    val combinedMatrix = remember(activeAdjustment, combinedFilter) {
        val cm = android.graphics.ColorMatrix()
        if (hasAdjustments && activeAdjustment != null) {
            cm.postConcat(ColorMatrixBuilder.build(activeAdjustment))
        }
        if (hasFilters && combinedFilter != null) {
            cm.postConcat(EffectsEngine.buildColorMatrix(combinedFilter))
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

        // ═══ VIDEO LAYER ═══
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasVideo && activeVisual != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val w = size.width
                            val h = size.height

                            val cropSx = 1f / (1f - videoTransform.cropL - videoTransform.cropR)
                                .coerceAtLeast(0.05f)
                            val cropSy = 1f / (1f - videoTransform.cropT - videoTransform.cropB)
                                .coerceAtLeast(0.05f)
                            val cropTx = -(videoTransform.cropL - videoTransform.cropR) / 2f * w
                            val cropTy = -(videoTransform.cropT - videoTransform.cropB) / 2f * h

                            val posTx = (videoTransform.x - 50f) / 100f * w
                            val posTy = (videoTransform.y - 50f) / 100f * h

                            val transT: Transform2D = if (activeTransitionClip != null) {
                                val st = activeTransitionClip.transition!!
                                val prog = (
                                        (currentPosMs - activeTransitionClip.timelineStartMs)
                                            .toFloat() /
                                                st.durationMs.coerceAtLeast(1L)
                                        ).coerceIn(0f, 1f)
                                TransitionRenderer.getIncomingTransform(
                                    key = st.key, p = prog, w = w, h = h
                                )
                            } else Transform2D()

                            translationX = posTx + cropTx + combinedMotion.tx + transT.tx
                            translationY = posTy + cropTy + combinedMotion.ty + transT.ty
                            scaleX = (videoTransform.scale / 100f) * cropSx *
                                    combinedMotion.scale * transT.scaleX
                            scaleY = (videoTransform.scale / 100f) * cropSy *
                                    combinedMotion.scale * transT.scaleY
                            rotationZ = videoTransform.rotation +
                                    combinedMotion.rotation + transT.rotZ
                            transformOrigin = TransformOrigin(
                                pivotFractionX = videoTransform.anchorX / 100f,
                                pivotFractionY = videoTransform.anchorY / 100f
                            )
                            alpha = opacityAlpha * transT.alpha
                            clip = true
                        }
                ) {
                    AndroidView(
                        factory = { ctx ->
                            LayoutInflater.from(ctx)
                                .inflate(R.layout.view_player, null) as PlayerView
                        },
                        update = { view ->
                            view.player = exoPlayer
                            view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            view.rotation = rotation.toFloat()

                            val surfaceView = view.videoSurfaceView
                            if (surfaceView is TextureView) {
                                if (applyMatrix) {
                                    val paint = Paint().apply {
                                        colorFilter = ColorMatrixColorFilter(combinedMatrix)
                                    }
                                    surfaceView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
                                } else {
                                    surfaceView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else if (hasVideo) {
                EmptyPreview("Move playhead onto a video clip")
            } else {
                EmptyPreview("Tap + Media below to pick a video")
            }
        }

        // ═══ CANVAS LAYER (ratio-sized, centered) ═══
        Box(
            modifier = Modifier
                .width(canvasW.dp)
                .height(canvasH.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(2.dp))
        ) {
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
                val transState = activeTransitionClip.transition!!
                val progress = (
                        (currentPosMs - activeTransitionClip.timelineStartMs).toFloat() /
                                transState.durationMs.coerceAtLeast(1L)
                        ).coerceIn(0f, 1f)

                TransitionRenderer.Render(
                    bitmap = outgoingBitmap!!,
                    transitionKey = transState.key,
                    progress = progress
                )
            }

            // ═══ TEXT ═══
            clips.filter {
                it.isTextClip &&
                        currentPosMs >= it.timelineStartMs &&
                        currentPosMs < it.timelineEndMs &&
                        !hiddenVisualTracks.contains(it.trackIndex)
            }.sortedBy { it.trackIndex }.forEach { tc ->
                val st = tc.textState ?: return@forEach
                InteractiveTextOverlay(
                    clip = tc,
                    textState = st,
                    canvasW = canvasW,
                    canvasH = canvasH,
                    currentPosMs = currentPosMs,
                    isSelected = tc.id == selectedClipId,
                    onSelect = { onClipSelected(tc.id) },
                    onPositionChanged = { x, y -> onTextPositionChanged(tc.id, x, y) },
                    onTransformChanged = { s, r -> onTextTransformChanged(tc.id, s, r) }
                )
            }

            // ═══ STICKER ═══
            clips.filter {
                it.isStickerClip &&
                        currentPosMs >= it.timelineStartMs &&
                        currentPosMs < it.timelineEndMs &&
                        !hiddenVisualTracks.contains(it.trackIndex)
            }.sortedBy { it.trackIndex }.forEach { sc ->
                val ss = sc.stickerState ?: return@forEach
                InteractiveStickerOverlay(
                    clip = sc,
                    stickerState = ss,
                    canvasW = canvasW,
                    canvasH = canvasH,
                    currentPosMs = currentPosMs,
                    isSelected = sc.id == selectedClipId,
                    onSelect = { onClipSelected(sc.id) },
                    onPositionChanged = { x, y -> onStickerPositionChanged(sc.id, x, y) },
                    onTransformChanged = { s, r -> onStickerTransformChanged(sc.id, s, r) }
                )
            }
        }

        // ═══ MASKS ═══
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
    onSelect: () -> Unit,
    onPositionChanged: (Float, Float) -> Unit,
    onTransformChanged: (Float, Float) -> Unit
) {
    if (textState.content.isBlank()) return

    val localTimeSec = ((currentPosMs - clip.timelineStartMs) / 1000f).coerceAtLeast(0f)
    val sampled = TransformApplier.resolveLive(clip, localTimeSec)
    val frame = com.moody.moodyvideoeditor.utils.AnimationsEngine.computeFrame(
        textState.animation,
        (localTimeSec / textState.animationDuration.coerceAtLeast(0.1f)).coerceIn(0f, 1f),
        localTimeSec
    )

    val family = com.moody.moodyvideoeditor.utils.FontLibrary.familyFor(textState.fontFamily)
    val solidColor = Color(textState.color)

    val gradient: Brush? = if (textState.gradientEnabled) {
        val rad = Math.toRadians(textState.gradientAngle.toDouble())
        val dx = kotlin.math.cos(rad).toFloat()
        val dy = kotlin.math.sin(rad).toFloat()
        val extent = 600f
        Brush.linearGradient(
            colors = listOf(
                Color(textState.gradientColor1),
                Color(textState.gradientColor2)
            ),
            start = Offset(-dx * extent, -dy * extent),
            end = Offset(dx * extent, dy * extent)
        )
    } else null

    val shadow = if (textState.shadowEnabled) androidx.compose.ui.graphics.Shadow(
        color = Color(textState.shadowColor),
        offset = Offset(textState.shadowOffsetX, textState.shadowOffsetY),
        blurRadius = textState.shadowBlur
    ) else null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(clip.id, isSelected) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)

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
                    var hasMulti = false

                    if (!isSelected) onSelect()

                    var continueGesture = true
                    while (continueGesture) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            continueGesture = false
                            break
                        }

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
                            val dist = kotlin.math.sqrt(d.x * d.x + d.y * d.y)
                            val angle = kotlin.math.atan2(d.y, d.x)

                            if (hasMulti && lastDist > 1f) {
                                val zoom = dist / lastDist
                                val rot = angle - lastAngle
                                accumZoom *= zoom
                                accumRot += Math.toDegrees(rot.toDouble()).toFloat()
                            }
                            lastDist = dist
                            lastAngle = angle
                            hasMulti = true
                            c1.consume()
                            c2.consume()
                        }

                        val dxPct = accumPanX / size.width * 100f
                        val dyPct = accumPanY / size.height * 100f
                        onPositionChanged(
                            (baseX + dxPct).coerceIn(0f, 100f),
                            (baseY + dyPct).coerceIn(0f, 100f)
                        )
                        onTransformChanged(
                            (baseScale * accumZoom).coerceIn(10f, 500f),
                            baseRot + accumRot
                        )
                    }
                }
            }
            .pointerInput(clip.id) {
                detectTapGestures { onSelect() }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val posTx = (sampled.x - 50f) / 100f * canvasW
                    val posTy = (sampled.y - 50f) / 100f * canvasH
                    translationX = posTx + frame.translateX
                    translationY = posTy + frame.translateY
                    scaleX = (sampled.scale / 100f) * frame.scaleX
                    scaleY = (sampled.scale / 100f) * frame.scaleY
                    rotationZ = sampled.rotation + frame.rotationZ
                    alpha = (textState.opacity / 100f) * frame.alpha
                    transformOrigin = TransformOrigin.Center
                }
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 1.5.dp,
                            color = Color(0xFF60EFFF),
                            shape = RoundedCornerShape(4.dp)
                        )
                    } else Modifier
                )
                .padding(6.dp)
        ) {
            Text(
                text = textState.content,
                color = if (gradient != null) Color.Unspecified else solidColor,
                fontSize = textState.fontSize.sp,
                fontWeight = if (textState.fontWeight == "bold") FontWeight.Bold
                else FontWeight.Normal,
                fontStyle = if (textState.fontStyle == "italic")
                    androidx.compose.ui.text.font.FontStyle.Italic
                else androidx.compose.ui.text.font.FontStyle.Normal,
                fontFamily = family,
                textAlign = when (textState.alignment) {
                    "left" -> androidx.compose.ui.text.style.TextAlign.Left
                    "right" -> androidx.compose.ui.text.style.TextAlign.Right
                    else -> androidx.compose.ui.text.style.TextAlign.Center
                },
                style = androidx.compose.ui.text.TextStyle(brush = gradient, shadow = shadow)
            )
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
    onSelect: () -> Unit,
    onPositionChanged: (Float, Float) -> Unit,
    onTransformChanged: (Float, Float) -> Unit
) {
    if (stickerState.emoji.isBlank()) return

    val localTimeSec = ((currentPosMs - clip.timelineStartMs) / 1000f).coerceAtLeast(0f)
    val sampled = TransformApplier.resolveLive(clip, localTimeSec)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(clip.id, isSelected) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)

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
                    var hasMulti = false

                    if (!isSelected) onSelect()

                    var continueGesture = true
                    while (continueGesture) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            continueGesture = false
                            break
                        }

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
                            val dist = kotlin.math.sqrt(d.x * d.x + d.y * d.y)
                            val angle = kotlin.math.atan2(d.y, d.x)

                            if (hasMulti && lastDist > 1f) {
                                val zoom = dist / lastDist
                                val rot = angle - lastAngle
                                accumZoom *= zoom
                                accumRot += Math.toDegrees(rot.toDouble()).toFloat()
                            }
                            lastDist = dist
                            lastAngle = angle
                            hasMulti = true
                            c1.consume()
                            c2.consume()
                        }

                        val dxPct = accumPanX / size.width * 100f
                        val dyPct = accumPanY / size.height * 100f
                        onPositionChanged(
                            (baseX + dxPct).coerceIn(0f, 100f),
                            (baseY + dyPct).coerceIn(0f, 100f)
                        )
                        onTransformChanged(
                            (baseScale * accumZoom).coerceIn(10f, 500f),
                            baseRot + accumRot
                        )
                    }
                }
            }
            .pointerInput(clip.id) {
                detectTapGestures { onSelect() }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val posTx = (sampled.x - 50f) / 100f * canvasW
                    val posTy = (sampled.y - 50f) / 100f * canvasH
                    translationX = posTx
                    translationY = posTy
                    scaleX = sampled.scale / 100f
                    scaleY = sampled.scale / 100f
                    rotationZ = sampled.rotation
                    transformOrigin = TransformOrigin.Center
                }
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 1.5.dp,
                            color = Color(0xFF60EFFF),
                            shape = RoundedCornerShape(6.dp)
                        )
                    } else Modifier
                )
                .padding(6.dp)
        ) {
            Text(text = stickerState.emoji, fontSize = 48.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  EMPTY PREVIEW
// ═══════════════════════════════════════════════════════════════
@Composable
private fun EmptyPreview(hint: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Movie,
            contentDescription = null,
            tint = Color(0xFF444444),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Video Preview",
            color = Color(0xFF888888),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(hint, color = Color(0xFF666666), fontSize = 11.sp)
    }
}
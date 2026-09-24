package com.moody.moodyvideoeditor.ui.components

import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
import com.moody.moodyvideoeditor.utils.TransformApplier
import com.moody.moodyvideoeditor.utils.TransformValues

@OptIn(UnstableApi::class)
@Composable
fun PreviewCanvas(
    exoPlayer: ExoPlayer,
    hasVideo: Boolean,
    rotation: Int,
    aspectMode: Int,
    clips: List<EditorClip>,
    currentPosMs: Long
) {
    val density = LocalDensity.current

    // ═══ Active visual clip ═══
    val activeVisual = clips
        .filter {
            it.isVisualClip &&
                    currentPosMs >= it.timelineStartMs &&
                    currentPosMs < it.timelineEndMs
        }
        .maxByOrNull { it.trackIndex }

    val videoTrackIdx = activeVisual?.trackIndex ?: 0

    // ═══ Sample live transform for active video ═══
    val videoTransform: TransformValues = activeVisual?.let {
        val timeSec = ((currentPosMs - it.timelineStartMs) / 1000f).coerceAtLeast(0f)
        TransformApplier.resolveLive(it, timeSec)
    } ?: TransformValues()

    // ═══ Adjustment ═══
    val activeAdjustment = clips
        .filter {
            it.isAdjustmentClip &&
                    currentPosMs >= it.timelineStartMs &&
                    currentPosMs < it.timelineEndMs
        }
        .maxByOrNull { it.trackIndex }
        ?.adjustments

    // ═══ Effects ═══
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
        if (hasFilters) {
            cm.postConcat(EffectsEngine.buildColorMatrix(combinedFilter))
        }
        cm
    }

    val opacityAlpha = EffectsEngine.opacityAlpha(combinedFilter)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (hasVideo && activeVisual != null) {
                // ═══ Apply combined transform + motion to video ═══
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val w = size.width
                            val h = size.height

                            // Crop scale & shift
                            val cropSx =
                                1f / (1f - videoTransform.cropL - videoTransform.cropR).coerceAtLeast(
                                    0.05f
                                )
                            val cropSy =
                                1f / (1f - videoTransform.cropT - videoTransform.cropB).coerceAtLeast(
                                    0.05f
                                )
                            val cropTx = -(videoTransform.cropL - videoTransform.cropR) / 2f * w
                            val cropTy = -(videoTransform.cropT - videoTransform.cropB) / 2f * h

                            // Position offset
                            val posTx = (videoTransform.x - 50f) / 100f * w
                            val posTy = (videoTransform.y - 50f) / 100f * h

                            // Motion from effects
                            val finalTx = posTx + cropTx + combinedMotion.tx
                            val finalTy = posTy + cropTy + combinedMotion.ty

                            val finalScaleX =
                                (videoTransform.scale / 100f) * cropSx * combinedMotion.scale
                            val finalScaleY =
                                (videoTransform.scale / 100f) * cropSy * combinedMotion.scale

                            val finalRot = videoTransform.rotation + combinedMotion.rotation

                            translationX = finalTx
                            translationY = finalTy
                            scaleX = finalScaleX
                            scaleY = finalScaleY
                            rotationZ = finalRot
                            transformOrigin = TransformOrigin(
                                pivotFractionX = videoTransform.anchorX / 100f,
                                pivotFractionY = videoTransform.anchorY / 100f
                            )
                            alpha = opacityAlpha
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
                            view.resizeMode = when (aspectMode) {
                                0 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
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

            // ═══ Overlays ═══
            if (allOverlays.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    allOverlays.forEach { ov ->
                        OverlayEngine.draw(this, timeSec, ov)
                    }
                }
            }

            // ═══ Vignette ═══
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

            // ═══ TEXT overlays (with live transform) ═══
            clips.filter {
                it.isTextClip &&
                        currentPosMs >= it.timelineStartMs &&
                        currentPosMs < it.timelineEndMs
            }.sortedBy { it.trackIndex }.forEach { tc ->
                val st = tc.textState ?: return@forEach
                val localTimeSec = ((currentPosMs - tc.timelineStartMs) / 1000f).coerceAtLeast(0f)
                val sampled = TransformApplier.resolveLive(tc, localTimeSec)
                TextOverlayWithTransform(
                    text = st,
                    sampled = sampled,
                    currentPosMs = currentPosMs,
                    clipStartMs = tc.timelineStartMs
                )
            }

            // ═══ STICKER overlays (with live transform) ═══
            clips.filter {
                it.isStickerClip &&
                        currentPosMs >= it.timelineStartMs &&
                        currentPosMs < it.timelineEndMs
            }.sortedBy { it.trackIndex }.forEach { sc ->
                val ss = sc.stickerState ?: return@forEach
                val localTimeSec = ((currentPosMs - sc.timelineStartMs) / 1000f).coerceAtLeast(0f)
                val sampled = TransformApplier.resolveLive(sc, localTimeSec)
                StickerOverlayWithTransform(
                    emoji = ss.emoji,
                    sampled = sampled
                )
            }
        }
    }
}

@Composable
private fun EmptyPreview(hint: String) {
    Column(
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

// ═══════════════════════════════════════════════════════════════
//  TEXT with transform
// ═══════════════════════════════════════════════════════════════
@Composable
private fun TextOverlayWithTransform(
    text: com.moody.moodyvideoeditor.data.TextState,
    sampled: TransformValues,
    currentPosMs: Long,
    clipStartMs: Long
) {
    if (text.content.isBlank()) return

    val elapsed = ((currentPosMs - clipStartMs) / 1000f).coerceAtLeast(0f)
    val animDur = text.animationDuration.coerceAtLeast(0.1f)
    val progress = (elapsed / animDur).coerceIn(0f, 1f)
    val frame = com.moody.moodyvideoeditor.utils.AnimationsEngine.computeFrame(
        text.animation, progress, elapsed
    )

    val family = com.moody.moodyvideoeditor.utils.FontLibrary.familyFor(text.fontFamily)
    val solidColor = Color(text.color)

    val gradient: Brush? = if (text.gradientEnabled) {
        val rad = Math.toRadians(text.gradientAngle.toDouble())
        val dx = kotlin.math.cos(rad).toFloat()
        val dy = kotlin.math.sin(rad).toFloat()
        val extent = 600f
        Brush.linearGradient(
            colors = listOf(Color(text.gradientColor1), Color(text.gradientColor2)),
            start = androidx.compose.ui.geometry.Offset(-dx * extent, -dy * extent),
            end = androidx.compose.ui.geometry.Offset(dx * extent, dy * extent)
        )
    } else null

    val shadow = if (text.shadowEnabled) androidx.compose.ui.graphics.Shadow(
        color = Color(text.shadowColor),
        offset = androidx.compose.ui.geometry.Offset(text.shadowOffsetX, text.shadowOffsetY),
        blurRadius = text.shadowBlur
    ) else null

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text.content,
            color = if (gradient != null) Color.Unspecified else solidColor,
            fontSize = text.fontSize.sp,
            fontWeight = if (text.fontWeight == "bold") FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (text.fontStyle == "italic")
                androidx.compose.ui.text.font.FontStyle.Italic
            else androidx.compose.ui.text.font.FontStyle.Normal,
            fontFamily = family,
            textAlign = when (text.alignment) {
                "left" -> androidx.compose.ui.text.style.TextAlign.Left
                "right" -> androidx.compose.ui.text.style.TextAlign.Right
                else -> androidx.compose.ui.text.style.TextAlign.Center
            },
            style = androidx.compose.ui.text.TextStyle(brush = gradient, shadow = shadow),
            modifier = Modifier.graphicsLayer {
                val w = size.width
                val h = size.height

                // Crop
                val cropSx = 1f / (1f - sampled.cropL - sampled.cropR).coerceAtLeast(0.05f)
                val cropSy = 1f / (1f - sampled.cropT - sampled.cropB).coerceAtLeast(0.05f)
                val cropTx = -(sampled.cropL - sampled.cropR) / 2f * w
                val cropTy = -(sampled.cropT - sampled.cropB) / 2f * h

                // Position
                val posTx = (sampled.x - 50f) / 100f * w
                val posTy = (sampled.y - 50f) / 100f * h

                translationX = posTx + cropTx + frame.translateX
                translationY = posTy + cropTy + frame.translateY
                scaleX = (sampled.scale / 100f) * cropSx * frame.scaleX
                scaleY = (sampled.scale / 100f) * cropSy * frame.scaleY
                rotationZ = sampled.rotation + frame.rotationZ
                alpha = (text.opacity / 100f) * frame.alpha
                transformOrigin = TransformOrigin(
                    pivotFractionX = sampled.anchorX / 100f,
                    pivotFractionY = sampled.anchorY / 100f
                )
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  STICKER with transform
// ═══════════════════════════════════════════════════════════════
@Composable
private fun StickerOverlayWithTransform(
    emoji: String,
    sampled: TransformValues
) {
    if (emoji.isBlank()) return

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = emoji,
            fontSize = 48.sp,
            modifier = Modifier.graphicsLayer {
                val w = size.width
                val h = size.height
                val posTx = (sampled.x - 50f) / 100f * w
                val posTy = (sampled.y - 50f) / 100f * h
                translationX = posTx
                translationY = posTy
                scaleX = sampled.scale / 100f
                scaleY = sampled.scale / 100f
                rotationZ = sampled.rotation
                transformOrigin = TransformOrigin(
                    pivotFractionX = sampled.anchorX / 100f,
                    pivotFractionY = sampled.anchorY / 100f
                )
            }
        )
    }
}
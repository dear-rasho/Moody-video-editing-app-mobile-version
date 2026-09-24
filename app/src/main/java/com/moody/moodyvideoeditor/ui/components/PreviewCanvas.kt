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
import androidx.compose.ui.graphics.graphicsLayer
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
    // ═══ 1) Active visual clip (topmost video/image) ═══
    val activeVisual = clips
        .filter {
            it.isVisualClip &&
                    currentPosMs >= it.timelineStartMs &&
                    currentPosMs < it.timelineEndMs
        }
        .maxByOrNull { it.trackIndex }

    val videoTrackIdx = activeVisual?.trackIndex ?: 0

    // ═══ 2) Active adjustment layer (topmost) ═══
    val activeAdjustment = clips
        .filter {
            it.isAdjustmentClip &&
                    currentPosMs >= it.timelineStartMs &&
                    currentPosMs < it.timelineEndMs
        }
        .maxByOrNull { it.trackIndex }
        ?.adjustments

    // ═══ 3) Effects above video track (JS hierarchy) ═══
    val activeEffects = EffectsEngine.getEffectsAbove(clips, currentPosMs, videoTrackIdx)

    // ═══ 4) Combine motions ═══
    val timeSec = currentPosMs / 1000f
    val motionFrames = activeEffects.mapNotNull { clip ->
        clip.effectState?.motion?.let { EffectsEngine.computeMotion(it, timeSec) }
    }
    val combinedMotion = EffectsEngine.combineMotions(motionFrames)

    // ═══ 5) Combine filters ═══
    val filterList = activeEffects.mapNotNull { it.effectState?.filters }
    val combinedFilter = if (filterList.isNotEmpty())
        EffectsEngine.combineFilters(filterList)
    else null

    // ═══ 6) Collect overlays (effect-layers + overlay-clips) ═══
    val allOverlays = EffectsEngine.collectActiveOverlays(clips, currentPosMs, videoTrackIdx)

    // ═══ 7) Build combined ColorMatrix (adjustments + filters) ═══
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
            // ═══ VIDEO + MOTION + FILTER ═══
            if (hasVideo && activeVisual != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = combinedMotion.tx
                            translationY = combinedMotion.ty
                            scaleX = combinedMotion.scale
                            scaleY = combinedMotion.scale
                            rotationZ = combinedMotion.rotation
                            alpha = opacityAlpha
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

            // ═══ OVERLAYS (rain, snow, fog, etc.) ═══
            if (allOverlays.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    allOverlays.forEach { ov ->
                        OverlayEngine.draw(this, timeSec, ov)
                    }
                }
            }

            // ═══ ADJUSTMENT VIGNETTE ═══
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

            // ═══ TEXT OVERLAYS ═══
            clips.filter {
                it.isTextClip &&
                        currentPosMs >= it.timelineStartMs &&
                        currentPosMs < it.timelineEndMs
            }.sortedBy { it.trackIndex }.forEach { tc ->
                tc.textState?.let { st ->
                    TextOverlay(st, currentPosMs, tc.timelineStartMs)
                }
            }

            // ═══ STICKER OVERLAYS ═══
            clips.filter {
                it.isStickerClip &&
                        currentPosMs >= it.timelineStartMs &&
                        currentPosMs < it.timelineEndMs
            }.sortedBy { it.trackIndex }.forEach { sc ->
                sc.stickerState?.let { ss ->
                    StickerOverlay(ss.emoji, ss.x, ss.y, ss.scale, ss.rotation)
                }
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
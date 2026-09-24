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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.moody.moodyvideoeditor.R
import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.data.ColorWheelState
import com.moody.moodyvideoeditor.data.FilterState
import com.moody.moodyvideoeditor.data.OverlayState
import com.moody.moodyvideoeditor.data.StickerClip
import com.moody.moodyvideoeditor.data.TextClip
import com.moody.moodyvideoeditor.data.TextState
import com.moody.moodyvideoeditor.utils.AnimationsEngine
import com.moody.moodyvideoeditor.utils.ColorMatrixBuilder
import com.moody.moodyvideoeditor.utils.FiltersEngine
import com.moody.moodyvideoeditor.utils.OverlayEngine
import com.moody.moodyvideoeditor.utils.StickerEngine
import kotlin.math.sin

@OptIn(UnstableApi::class)
@Composable
fun PreviewCanvas(
    exoPlayer: ExoPlayer,
    hasVideo: Boolean,
    rotation: Int,
    aspectMode: Int,
    textClips: List<TextClip>,
    stickerClips: List<StickerClip>,
    currentPosMs: Long,
    adjustments: AdjustmentData,
    filters: FilterState,
    colorWheel: ColorWheelState,
    overlay: OverlayState
) {
    val hasAdjustments = ColorMatrixBuilder.hasRealTimeAdjustments(adjustments)
    val hasFilters = FiltersEngine.hasRealTimeFilters(filters)
    val hasColorWheel = colorWheel.hasAnyChange
    val applyMatrix = hasAdjustments || hasFilters || hasColorWheel

    // Build combined Android ColorMatrix
    val combinedMatrix = remember(adjustments, filters, colorWheel) {
        val cm = android.graphics.ColorMatrix()
        if (hasAdjustments) cm.postConcat(ColorMatrixBuilder.build(adjustments))
        if (hasFilters) cm.postConcat(FiltersEngine.buildColorMatrix(filters))
        if (hasColorWheel) {
            val satAdj = android.graphics.ColorMatrix()
            satAdj.setSaturation(1.1f)
            cm.postConcat(satAdj)
        }
        cm
    }

    val opacityAlpha = FiltersEngine.opacityAlpha(filters)

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
            if (hasVideo) {
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
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = opacityAlpha }
                )

                if (adjustments.vignette > 0f) {
                    val alpha = (adjustments.vignette / 100f).coerceIn(0f, 1f)
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
            } else {
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
                    Text(
                        "Tap + Media below to pick a video",
                        color = Color(0xFF666666),
                        fontSize = 11.sp
                    )
                }
            }

            // Overlays
            if (overlay.isActive) {
                val overlayTime = currentPosMs / 1000f
                Canvas(modifier = Modifier.fillMaxSize()) {
                    OverlayEngine.draw(this, overlayTime, overlay)
                }
            }

            // Text
            val activeTexts = textClips.filter {
                currentPosMs >= it.startTimeMs && currentPosMs < it.endTimeMs
            }
            activeTexts.forEach { tc ->
                TextOverlay(tc.state, currentPosMs, tc.startTimeMs)
            }

            // Stickers
            val activeStickers = stickerClips.filter {
                currentPosMs >= it.startTimeMs && currentPosMs < it.endTimeMs
            }
            activeStickers.forEach { sc ->
                StickerOverlay(sc, currentPosMs)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  TEXT OVERLAY
// ═══════════════════════════════════════════════════════════════
@Composable
private fun TextOverlay(text: TextState, currentPosMs: Long, clipStartMs: Long) {
    if (text.content.isBlank()) return

    val elapsed = ((currentPosMs - clipStartMs) / 1000f).coerceAtLeast(0f)
    val animDur = text.animationDuration.coerceAtLeast(0.1f)
    val progress = (elapsed / animDur).coerceIn(0f, 1f)
    val anim = computeTextAnim(progress, elapsed, text.animation)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text.content,
            color = if (text.gradientEnabled) Color(text.gradientColor1) else Color(text.color),
            fontSize = text.fontSize.sp,
            fontWeight = if (text.fontWeight == "bold") FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (text.fontStyle == "italic") FontStyle.Italic else FontStyle.Normal,
            textAlign = when (text.alignment) {
                "left" -> TextAlign.Left
                "right" -> TextAlign.Right
                else -> TextAlign.Center
            },
            modifier = Modifier
                .offset(
                    x = ((text.positionX - 50f) * 3f).dp,
                    y = ((text.positionY - 50f) * 1.7f).dp
                )
                .graphicsLayer(
                    scaleX = (text.scale / 100f) * (anim.scale ?: 1f),
                    scaleY = (text.scale / 100f) * (anim.scale ?: 1f),
                    rotationZ = text.rotation + (anim.rot ?: 0f),
                    alpha = (text.opacity / 100f) * (anim.alpha ?: 1f),
                    translationX = anim.tx ?: 0f,
                    translationY = anim.ty ?: 0f
                )
        )
    }
}

private data class TextAnim(
    val alpha: Float? = null,
    val scale: Float? = null,
    val tx: Float? = null,
    val ty: Float? = null,
    val rot: Float? = null
)

private fun computeTextAnim(p: Float, elapsed: Float, animation: String): TextAnim {
    return when (animation) {
        "none" -> TextAnim()
        "fadeIn" -> TextAnim(alpha = p)
        "fadeUp" -> TextAnim(alpha = p, ty = (1f - p) * 24f)
        "fadeDown" -> TextAnim(alpha = p, ty = (1f - p) * -24f)
        "slideLeft" -> TextAnim(alpha = p, tx = (1f - p) * -80f)
        "slideRight" -> TextAnim(alpha = p, tx = (1f - p) * 80f)
        "slideUp" -> TextAnim(alpha = p, ty = (1f - p) * 80f)
        "slideDown" -> TextAnim(alpha = p, ty = (1f - p) * -80f)
        "popIn" -> {
            val e = AnimationsEngine.ease(p, "easeOutBack")
            TextAnim(alpha = (p * 2.5f).coerceAtMost(1f), scale = e.coerceAtLeast(0.01f))
        }

        "bounceIn" -> {
            val e = AnimationsEngine.ease(p, "easeOutBack")
            TextAnim(alpha = (p * 2.5f).coerceAtMost(1f), scale = e.coerceAtLeast(0.01f))
        }

        "zoomIn" -> TextAnim(alpha = p, scale = 0.3f + 0.7f * p)
        "zoomOut" -> TextAnim(alpha = p, scale = 2f - p)
        "pulse" -> TextAnim(scale = 1f + sin(elapsed * 3f) * 0.1f)
        "shake" -> TextAnim(tx = sin(elapsed * 40f) * 6f)
        "wave" -> TextAnim(ty = sin(elapsed * 6f) * 8f)
        "float" -> TextAnim(ty = sin(elapsed * 3f) * 8f)
        "vortexSpin" -> {
            val e = AnimationsEngine.ease(p, "easeOutBack")
            TextAnim(
                alpha = (p * 2.5f).coerceAtMost(1f),
                scale = e.coerceAtLeast(0.01f),
                rot = p * 720f
            )
        }

        "spiralIn" -> {
            val e = AnimationsEngine.ease(p, "easeOutCubic")
            TextAnim(
                alpha = e,
                scale = e.coerceAtLeast(0.01f),
                rot = (1f - p) * 720f,
                ty = (1f - e) * 100f
            )
        }

        "overshootPop" -> {
            val e = AnimationsEngine.ease(p, "easeOutBack")
            TextAnim(alpha = (p * 2.5f).coerceAtMost(1f), scale = e.coerceAtLeast(0.01f))
        }

        "elasticDrop" -> {
            val e = AnimationsEngine.ease(p, "easeOutElastic")
            TextAnim(alpha = (p * 3f).coerceAtMost(1f), ty = (1f - e) * -200f)
        }

        "cinematicZoom" -> {
            val e = AnimationsEngine.ease(p, "easeOutCubic")
            TextAnim(alpha = (p * 2f).coerceAtMost(1f), scale = 1.6f - 0.6f * e)
        }

        "blurryReveal" -> TextAnim(alpha = p, scale = 1.2f - 0.2f * p)
        else -> TextAnim(alpha = p)
    }
}

// ═══════════════════════════════════════════════════════════════
//  STICKER OVERLAY
// ═══════════════════════════════════════════════════════════════
@Composable
private fun StickerOverlay(clip: StickerClip, currentPosMs: Long) {
    val state = clip.state
    if (state.emoji.isBlank()) return

    val timeSec = ((currentPosMs - clip.startTimeMs) / 1000f).coerceAtLeast(0f)

    val sampled = if (!clip.keyframes.isEmpty) {
        StickerEngine.sample(
            kfs = clip.keyframes,
            timeSec = timeSec,
            baseX = state.x, baseY = state.y,
            baseScale = state.scale, baseRot = state.rotation,
            easePos = clip.easePosition,
            easeScale = clip.easeScale,
            easeRot = clip.easeRotation
        )
    } else {
        StickerEngine.Sampled(state.x, state.y, state.scale, state.rotation)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = state.emoji,
            fontSize = 48.sp,
            modifier = Modifier
                .offset(
                    x = ((sampled.x - 50f) * 3f).dp,
                    y = ((sampled.y - 50f) * 1.7f).dp
                )
                .graphicsLayer(
                    scaleX = sampled.scale / 100f,
                    scaleY = sampled.scale / 100f,
                    rotationZ = sampled.rotation
                )
        )
    }
}
package com.moody.moodyvideoeditor.ui.components

import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.moody.moodyvideoeditor.R
import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.utils.ColorMatrixBuilder
import kotlin.random.Random

@OptIn(UnstableApi::class)
@Composable
fun PreviewCanvas(
    exoPlayer: ExoPlayer,
    hasVideo: Boolean,
    rotation: Int,
    aspectMode: Int,
    text: String,
    textColor: Color,
    textSize: Int,
    sticker: String,
    stickerX: Float,
    stickerY: Float,
    adjustments: AdjustmentData = AdjustmentData()
) {
    val hasAdjustments = ColorMatrixBuilder.hasRealTimeAdjustments(adjustments)

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
                // ═══ VIDEO + COLOR MATRIX ═══
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
                            if (hasAdjustments) {
                                val cm = ColorMatrixBuilder.build(adjustments)
                                val paint = Paint().apply {
                                    colorFilter = ColorMatrixColorFilter(cm)
                                }
                                surfaceView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
                            } else {
                                surfaceView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // ═══ VIGNETTE OVERLAY ═══
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

                // ═══ NOISE OVERLAY ═══
                if (adjustments.noise > 0f) {
                    val noiseAlpha = (adjustments.noise / 200f).coerceIn(0f, 0.5f)
                    NoiseOverlay(alpha = noiseAlpha)
                }

                // ═══ SHARPEN (approx via slight overlay) — skip for now ═══
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
                        text = "Video Preview",
                        color = Color(0xFF888888),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap ＋ Media below to pick a video",
                        color = Color(0xFF666666),
                        fontSize = 11.sp
                    )
                }
            }

            // Text overlay
            if (text.isNotBlank()) {
                Text(
                    text = text,
                    color = textColor,
                    fontSize = textSize.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(20.dp)
                )
            }

            // Sticker overlay
            if (sticker.isNotBlank()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = sticker,
                        fontSize = 48.sp,
                        modifier = Modifier.offset(
                            x = (stickerX * 300).dp - 24.dp,
                            y = (stickerY * 160).dp - 24.dp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Noise overlay — random static dots
 */
@Composable
private fun NoiseOverlay(alpha: Float) {
    val noiseBrush = remember(alpha) {
        // Generate random dot positions
        val rand = Random(System.currentTimeMillis())
        val count = 400
        val dots = List(count) {
            Triple(
                rand.nextFloat(),
                rand.nextFloat(),
                rand.nextFloat() // opacity per dot
            )
        }
        dots
    }

    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val w = size.width
        val h = size.height
        noiseBrush.forEach { (x, y, op) ->
            drawCircle(
                color = Color.White.copy(alpha = alpha * op),
                radius = 1.5f,
                center = androidx.compose.ui.geometry.Offset(x * w, y * h)
            )
        }
    }
}
package com.moody.moodyvideoeditor.utils

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale

data class Transform2D(
    val tx: Float = 0f,
    val ty: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotZ: Float = 0f,
    val alpha: Float = 1f
)

object TransitionRenderer {

    @Composable
    fun Render(
        bitmap: android.graphics.Bitmap,
        transitionKey: String,
        progress: Float
    ) {
        val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
        val p = progress.coerceIn(0f, 1f)

        when (transitionKey) {

            "fade", "dissolve", "blur" -> {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - p }
                )
            }

            "fadeBlack", "fadeWhite" -> {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (p < 0.5f) 1f - (p * 2f) else 0f
                        }
                )
            }

            "slideLeft", "pushLeft" -> {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = -p * size.width }
                )
            }

            "slideRight", "pushRight" -> {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = p * size.width }
                )
            }

            "slideUp", "pushUp" -> {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = -p * size.height }
                )
            }

            "slideDown", "pushDown" -> {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = p * size.height }
                )
            }

            "zoomIn" -> {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1f + p * 1.5f
                            scaleY = 1f + p * 1.5f
                            alpha = 1f - p
                        }
                )
            }

            "zoomOut" -> {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1f - p * 0.7f
                            scaleY = 1f - p * 0.7f
                            alpha = 1f - p
                        }
                )
            }

            "crossZoom" -> {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1f + p * 1.2f
                            scaleY = 1f + p * 1.2f
                            alpha = 1f - p
                        }
                )
            }

            "wipeLeft" -> {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = -p * size.width
                            alpha = if (p < 0.95f) 1f else 0f
                        }
                )
            }

            "wipeRight" -> {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = p * size.width
                            alpha = if (p < 0.95f) 1f else 0f
                        }
                )
            }

            "wipeUp" -> {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = -p * size.height
                            alpha = if (p < 0.95f) 1f else 0f
                        }
                )
            }

            "wipeDown" -> {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = p * size.height
                            alpha = if (p < 0.95f) 1f else 0f
                        }
                )
            }

            "circleIn", "irisBox", "clockWipe" -> {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1f + p * 0.8f
                            scaleY = 1f + p * 0.8f
                            alpha = 1f - p
                        }
                )
            }

            "spinCW", "spinCCW", "swirl" -> {
                val rot = if (transitionKey == "spinCCW") -p * 360f else p * 360f
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = rot
                            scaleX = 1f - p * 0.5f
                            scaleY = 1f - p * 0.5f
                            alpha = 1f - p
                        }
                )
            }

            "glitch", "rgbSplit" -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = kotlin.math.sin(
                                    (p * 40f).toDouble()
                                ).toFloat() * 15f
                                alpha = 1f - p
                            }
                    )
                }
            }

            "flashWhite" -> {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = when {
                                p < 0.15f -> 1f
                                p < 0.3f -> 1f - (p - 0.15f) / 0.15f
                                else -> 0f
                            }
                        }
                )
            }

            else -> {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - p }
                )
            }
        }
    }

    fun getIncomingTransform(
        key: String,
        p: Float,
        w: Float,
        h: Float
    ): Transform2D {
        val pr = p.coerceIn(0f, 1f)
        return when (key) {

            "fade", "dissolve", "blur" -> Transform2D()

            "slideLeft", "pushLeft", "wipeLeft" ->
                Transform2D(tx = (1f - pr) * w)

            "slideRight", "pushRight", "wipeRight" ->
                Transform2D(tx = -(1f - pr) * w)

            "slideUp", "pushUp", "wipeUp" ->
                Transform2D(ty = (1f - pr) * h)

            "slideDown", "pushDown", "wipeDown" ->
                Transform2D(ty = -(1f - pr) * h)

            "zoomIn" -> Transform2D(
                scaleX = 1f + pr * 0.5f,
                scaleY = 1f + pr * 0.5f
            )

            "zoomOut" -> Transform2D(
                scaleX = 1.5f - pr * 0.5f,
                scaleY = 1.5f - pr * 0.5f
            )

            "crossZoom" -> Transform2D(
                scaleX = 0.5f + pr * 0.5f,
                scaleY = 0.5f + pr * 0.5f
            )

            "spinCW" -> Transform2D(
                rotZ = -360f * (1f - pr),
                scaleX = 0.5f + pr * 0.5f,
                scaleY = 0.5f + pr * 0.5f
            )

            "spinCCW", "swirl" -> Transform2D(
                rotZ = 360f * (1f - pr),
                scaleX = 0.5f + pr * 0.5f,
                scaleY = 0.5f + pr * 0.5f
            )

            "circleIn", "irisBox", "clockWipe" -> Transform2D(
                scaleX = 0.3f + pr * 0.7f,
                scaleY = 0.3f + pr * 0.7f
            )

            "glitch", "rgbSplit" -> Transform2D(
                tx = kotlin.math.sin((pr * 40f).toDouble()).toFloat() * 15f
            )

            "flashWhite" -> Transform2D()

            else -> Transform2D()
        }
    }
}
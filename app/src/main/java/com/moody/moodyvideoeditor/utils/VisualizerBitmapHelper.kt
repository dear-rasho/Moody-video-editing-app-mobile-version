package com.moody.moodyvideoeditor.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.moody.moodyvideoeditor.data.VisualizerState
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Canvas as ComposeCanvas

object VisualizerBitmapHelper {

    // Draw a single visualizer frame onto an Android Canvas.
    // @param relativeMs  Time in source audio (ms) — used for beat lookup
    // @param elapsedSec  Wall time for idle motion (rotation, waves)
    fun drawVisualizerFrame(
        canvas: Canvas,
        state: VisualizerState,
        relativeMs: Long,
        elapsedSec: Float,
        W: Int,
        H: Int,
        imageBitmap: Bitmap?,
        instanceKey: String = "export"
    ) {
        val overlayBmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val imageBitmapCompose = overlayBmp.asImageBitmap()
        val composeCanvas = ComposeCanvas(imageBitmapCompose)

        // Set center image (cached by URI)
        VisualizerEngine.setCenterImage(
            state.imageUri,
            imageBitmap?.asImageBitmap()
        )

        val drawScope = CanvasDrawScope()

        drawScope.draw(
            density = Density(1f, 1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = composeCanvas,
            size = ComposeSize(W.toFloat(), H.toFloat())
        ) {
            VisualizerEngine.draw(
                scope = this,
                state = state,
                relativeMs = relativeMs,
                elapsedSec = elapsedSec,
                instanceKey = instanceKey
            )
        }

        canvas.drawBitmap(overlayBmp, 0f, 0f, null)
        overlayBmp.recycle()
    }
}
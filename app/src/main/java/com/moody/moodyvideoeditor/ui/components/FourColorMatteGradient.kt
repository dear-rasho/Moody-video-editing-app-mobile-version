package com.moody.moodyvideoeditor.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.moody.moodyvideoeditor.data.ColorMatteStyle

@Composable
fun FourColorMatteGradient(
    style: ColorMatteStyle,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(
        style.topLeft, style.topRight, style.bottomLeft, style.bottomRight
    ) {
        Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, style.topLeft.toInt() or 0xFF000000.toInt())
            setPixel(1, 0, style.topRight.toInt() or 0xFF000000.toInt())
            setPixel(0, 1, style.bottomLeft.toInt() or 0xFF000000.toInt())
            setPixel(1, 1, style.bottomRight.toInt() or 0xFF000000.toInt())
        }
    }
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isDither = true
            isFilterBitmap = true
        }
    }

    Canvas(modifier = modifier) {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmap(
                bitmap,
                null,
                RectF(0f, 0f, size.width, size.height),
                paint
            )
        }
    }
}

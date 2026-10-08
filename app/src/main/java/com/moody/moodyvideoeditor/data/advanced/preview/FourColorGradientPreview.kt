package com.moody.moodyvideoeditor.advanced.preview

import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import com.moody.moodyvideoeditor.advanced.base.AdvancedEffectRenderer
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectState
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectType
import com.moody.moodyvideoeditor.data.advanced.models.GradientBlendMode
import com.moody.moodyvideoeditor.utils.KeyframeStore

class FourColorGradientRenderer : AdvancedEffectRenderer {

    override val type = AdvancedEffectType.FOUR_COLOR_GRADIENT
    override val isOverlayLayer = true

    @Composable
    override fun RenderPreview(
        clip: EditorClip,
        effect: AdvancedEffectState,
        localTimeSec: Float,
        content: @Composable () -> Unit
    ) {
        val g = effect.fourColorGradient ?: run { content(); return }

        val opacity = KeyframeStore.sample(
            g.keyframes, "globalOpacity", localTimeSec, g.globalOpacity
        ).coerceIn(0f, 100f)

        if (opacity < 0.5f) {
            content(); return
        }

        // Pre-render gradient bitmap (256x256 is enough — scaled to full size)
        val gradientBitmap = remember(g.color1, g.color2, g.color3, g.color4) {
            renderFourColorGradientBitmap(
                256, 256,
                g.color1, g.color2, g.color3, g.color4
            )
        }

        val alphaInt = ((opacity / 100f) * 255f).toInt().coerceIn(0, 255)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    // 1) Draw the layer content FIRST (text/sticker/image/etc.)
                    drawContent()

                    // 2) Draw gradient ONLY over content pixels with chosen blend
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        val paint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            isFilterBitmap = true
                            this.alpha = alphaInt
                            xfermode = PorterDuffXfermode(
                                when (g.blendMode) {
                                    // Replace content color where content exists
                                    GradientBlendMode.NORMAL ->
                                        PorterDuff.Mode.SRC_ATOP

                                    // Additive brighten — clipped to content
                                    GradientBlendMode.ADD ->
                                        PorterDuff.Mode.SRC_ATOP

                                    // Multiply with content color
                                    GradientBlendMode.MULTIPLY ->
                                        PorterDuff.Mode.MULTIPLY

                                    // Screen blend (natural clipping with content)
                                    GradientBlendMode.SCREEN ->
                                        PorterDuff.Mode.SCREEN

                                    // Overlay blend
                                    GradientBlendMode.OVERLAY ->
                                        PorterDuff.Mode.OVERLAY
                                }
                            )
                        }
                        val src = android.graphics.Rect(
                            0, 0, gradientBitmap.width, gradientBitmap.height
                        )
                        val dst = android.graphics.Rect(
                            0, 0, size.width.toInt(), size.height.toInt()
                        )
                        native.drawBitmap(gradientBitmap, src, dst, paint)
                    }
                }
        ) { content() }
    }

    override fun buildFFmpegFilters(
        clip: EditorClip,
        effect: AdvancedEffectState,
        clipDurationSec: Float
    ): List<String> {
        val g = effect.fourColorGradient ?: return emptyList()

        val alpha = (g.globalOpacity / 100f).coerceIn(0f, 1f)
        val aS = "%.4f".format(alpha)

        fun ch(c: Long, shift: Int) = ((c shr shift) and 0xFF).toInt()

        fun gradExpr(shift: Int): String {
            val tl = ch(g.color1, shift)
            val tr = ch(g.color2, shift)
            val br = ch(g.color3, shift)
            val bl = ch(g.color4, shift)
            return "(($tl*(W-X)*(H-Y)+$tr*X*(H-Y)+$bl*(W-X)*Y+$br*X*Y)/(W*H))"
        }

        val rG = gradExpr(16)
        val gG = gradExpr(8)
        val bG = gradExpr(0)

        // Apply ONLY where alpha channel of original > 0
        // a(X,Y) = original alpha, so gradient applies with alpha weighting
        fun blend(orig: String, grad: String): String = when (g.blendMode) {
            GradientBlendMode.NORMAL ->
                "($orig*(1-$aS)+$grad*$aS)"

            GradientBlendMode.ADD ->
                "min(255\\,$orig+$grad*$aS)"

            GradientBlendMode.MULTIPLY ->
                "($orig*(1-$aS)+$orig*$grad/255*$aS)"

            GradientBlendMode.SCREEN ->
                "($orig*(1-$aS)+(255-(255-$orig)*(255-$grad)/255)*$aS)"

            GradientBlendMode.OVERLAY ->
                "($orig*(1-$aS)+" +
                        "if(lt($orig\\,128)\\,2*$orig*$grad/255\\," +
                        "255-2*(255-$orig)*(255-$grad)/255)*$aS)"
        }

        // Preserve alpha → effect applies only on drawn pixels (text/image/etc.)
        return listOf(
            "format=rgba",
            "geq=" +
                    "r='${blend("r(X,Y)", rG)}':" +
                    "g='${blend("g(X,Y)", gG)}':" +
                    "b='${blend("b(X,Y)", bG)}':" +
                    "a='a(X,Y)'"
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  BITMAP RENDERER — 4-corner bilinear gradient
// ═══════════════════════════════════════════════════════════════

private fun renderFourColorGradientBitmap(
    w: Int, h: Int,
    c1: Long, c2: Long, c3: Long, c4: Long
): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(w * h)

    val tlR = ((c1 shr 16) and 0xFF).toInt()
    val tlG = ((c1 shr 8) and 0xFF).toInt()
    val tlB = (c1 and 0xFF).toInt()

    val trR = ((c2 shr 16) and 0xFF).toInt()
    val trG = ((c2 shr 8) and 0xFF).toInt()
    val trB = (c2 and 0xFF).toInt()

    val brR = ((c3 shr 16) and 0xFF).toInt()
    val brG = ((c3 shr 8) and 0xFF).toInt()
    val brB = (c3 and 0xFF).toInt()

    val blR = ((c4 shr 16) and 0xFF).toInt()
    val blG = ((c4 shr 8) and 0xFF).toInt()
    val blB = (c4 and 0xFF).toInt()

    val maxX = (w - 1).coerceAtLeast(1)
    val maxY = (h - 1).coerceAtLeast(1)

    for (y in 0 until h) {
        val fy = y.toFloat() / maxY
        val invFy = 1f - fy
        for (x in 0 until w) {
            val fx = x.toFloat() / maxX
            val invFx = 1f - fx

            val wTL = invFx * invFy
            val wTR = fx * invFy
            val wBL = invFx * fy
            val wBR = fx * fy

            val r = (tlR * wTL + trR * wTR + blR * wBL + brR * wBR)
                .toInt().coerceIn(0, 255)
            val g = (tlG * wTL + trG * wTR + blG * wBL + brG * wBR)
                .toInt().coerceIn(0, 255)
            val b = (tlB * wTL + trB * wTR + blB * wBL + brB * wBR)
                .toInt().coerceIn(0, 255)

            pixels[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    bmp.setPixels(pixels, 0, w, 0, 0, w, h)
    return bmp
}
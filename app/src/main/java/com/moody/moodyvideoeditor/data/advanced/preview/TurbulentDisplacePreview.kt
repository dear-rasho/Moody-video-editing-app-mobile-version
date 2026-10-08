package com.moody.moodyvideoeditor.advanced.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.moody.moodyvideoeditor.advanced.base.AdvancedEffectRenderer
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectState
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectType
import com.moody.moodyvideoeditor.utils.KeyframeStore
import kotlin.math.cos
import kotlin.math.sin

class TurbulentDisplaceRenderer : AdvancedEffectRenderer {

    override val type = AdvancedEffectType.TURBULENT_DISPLACE

    @Composable
    override fun RenderPreview(
        clip: EditorClip,
        effect: AdvancedEffectState,
        localTimeSec: Float,
        content: @Composable () -> Unit
    ) {
        val t = effect.turbulentDisplace ?: run { content(); return }

        val amount = KeyframeStore.sample(t.keyframes, "amount", localTimeSec, t.amount)
        val speed = KeyframeStore.sample(
            t.keyframes, "evolutionSpeed", localTimeSec, t.evolutionSpeed
        )
        val offX = KeyframeStore.sample(t.keyframes, "offsetX", localTimeSec, t.offsetX)
        val offY = KeyframeStore.sample(t.keyframes, "offsetY", localTimeSec, t.offsetY)

        if (amount < 0.1f) {
            content(); return
        }

        // Independent oscillation for X and Y — different frequencies + phase offsets
        // so both axes visibly move all the time (not alternate)
        val time = localTimeSec * speed

        // X: primary sin wave at 1.0x freq + secondary cos at 1.7x freq for richness
        val phaseX1 = time * 1.0f + offX * 6f
        val phaseX2 = time * 1.7f + offX * 8f + 2.3f
        val nx = (sin(phaseX1.toDouble()) * 0.65 +
                cos(phaseX2.toDouble()) * 0.35).toFloat()

        // Y: primary cos wave at 0.87x freq + secondary sin at 1.3x freq
        val phaseY1 = time * 0.87f + offY * 6f + 1.1f
        val phaseY2 = time * 1.3f + offY * 8f + 4.7f
        val ny = (cos(phaseY1.toDouble()) * 0.65 +
                sin(phaseY2.toDouble()) * 0.35).toFloat()

        val tx = nx * amount * 0.5f
        val ty = ny * amount * 0.5f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = tx
                    translationY = ty
                }
        ) { content() }
    }

    override fun buildFFmpegFilters(
        clip: EditorClip,
        effect: AdvancedEffectState,
        clipDurationSec: Float
    ): List<String> {
        val t = effect.turbulentDisplace ?: return emptyList()
        val amount = t.amount.coerceIn(0f, 1000f)
        if (amount < 1f) return emptyList()

        // Fractal displacement — different freq for X and Y, matching preview
        val speed = t.evolutionSpeed.coerceIn(0f, 100f)
        val offX = t.offsetX.coerceIn(0f, 1f)
        val offY = t.offsetY.coerceIn(0f, 1f)
        val amp = amount * 0.5f

        // time-based expressions
        val phaseX1 = "($speed*t+${offX * 6f})"
        val phaseX2 = "($speed*t*1.7+${offX * 8f + 2.3f})"
        val phaseY1 = "($speed*t*0.87+${offY * 6f + 1.1f})"
        val phaseY2 = "($speed*t*1.3+${offY * 8f + 4.7f})"

        val xExpr = "($amp*(sin($phaseX1)*0.65+cos($phaseX2)*0.35))"
        val yExpr = "($amp*(cos($phaseY1)*0.65+sin($phaseY2)*0.35))"

        return listOf(
            "format=rgba",
            "geq=" +
                    "r='r(X+$xExpr\\,Y+$yExpr)':" +
                    "g='g(X+$xExpr\\,Y+$yExpr)':" +
                    "b='b(X+$xExpr\\,Y+$yExpr)':" +
                    "a='a(X+$xExpr\\,Y+$yExpr)'"
        )
    }
}
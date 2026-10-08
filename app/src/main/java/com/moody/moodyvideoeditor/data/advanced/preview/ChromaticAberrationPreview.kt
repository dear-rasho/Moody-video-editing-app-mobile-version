package com.moody.moodyvideoeditor.advanced.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.moody.moodyvideoeditor.advanced.base.AdvancedEffectRenderer
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectState
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectType
import com.moody.moodyvideoeditor.utils.KeyframeStore
import kotlin.math.abs

class ChromaticAberrationRenderer : AdvancedEffectRenderer {

    override val type = AdvancedEffectType.CHROMATIC_ABERRATION

    @Composable
    override fun RenderPreview(
        clip: EditorClip,
        effect: AdvancedEffectState,
        localTimeSec: Float,
        content: @Composable () -> Unit
    ) {
        val c = effect.chromaticAberration ?: run { content(); return }

        val rx = KeyframeStore.sample(c.keyframes, "redShiftX", localTimeSec, c.redShiftX)
        val ry = KeyframeStore.sample(c.keyframes, "redShiftY", localTimeSec, c.redShiftY)
        val bx = KeyframeStore.sample(c.keyframes, "blueShiftX", localTimeSec, c.blueShiftX)
        val by = KeyframeStore.sample(c.keyframes, "blueShiftY", localTimeSec, c.blueShiftY)

        val hasShift = abs(rx) > 0.1f || abs(ry) > 0.1f || abs(bx) > 0.1f || abs(by) > 0.1f

        Box(modifier = Modifier.fillMaxSize()) {
            content()

            if (hasShift) {
                // Red channel shift overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = rx * 2f
                            translationY = ry * 2f
                            alpha = 0.35f
                            blendMode = BlendMode.Screen
                        }
                        .background(Color.Red.copy(alpha = 0.55f))
                )
                // Blue channel shift overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = -bx * 2f
                            translationY = -by * 2f
                            alpha = 0.35f
                            blendMode = BlendMode.Screen
                        }
                        .background(Color.Blue.copy(alpha = 0.55f))
                )
            }
        }
    }

    override fun buildFFmpegFilters(
        clip: EditorClip,
        effect: AdvancedEffectState,
        clipDurationSec: Float
    ): List<String> {
        val c = effect.chromaticAberration ?: return emptyList()
        val rx = c.redShiftX.coerceIn(-50f, 50f).toInt()
        val ry = c.redShiftY.coerceIn(-50f, 50f).toInt()
        val bx = c.blueShiftX.coerceIn(-50f, 50f).toInt()
        val by = c.blueShiftY.coerceIn(-50f, 50f).toInt()
        val filters = mutableListOf<String>()
        filters.add("rgbashift=rh=$rx:rv=$ry:bh=$bx:bv=$by:gh=0:gv=0")
        if (c.blurRadius > 0.5f) {
            filters.add("gblur=sigma=${"%.2f".format(c.blurRadius / 3f)}")
        }
        return filters
    }
}
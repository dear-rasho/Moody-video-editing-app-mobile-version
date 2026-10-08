package com.moody.moodyvideoeditor.advanced.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.moody.moodyvideoeditor.advanced.base.AdvancedEffectRenderer
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectState
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectType
import com.moody.moodyvideoeditor.utils.KeyframeStore

class MirrorRenderer : AdvancedEffectRenderer {

    override val type = AdvancedEffectType.MIRROR

    @Composable
    override fun RenderPreview(
        clip: EditorClip,
        effect: AdvancedEffectState,
        localTimeSec: Float,
        content: @Composable () -> Unit
    ) {
        val m = effect.mirror ?: run { content(); return }

        val cX = KeyframeStore.sample(m.keyframes, "centerX", localTimeSec, m.centerX)
        val cY = KeyframeStore.sample(m.keyframes, "centerY", localTimeSec, m.centerY)
        val angle = KeyframeStore.sample(m.keyframes, "angleDeg", localTimeSec, m.angleDeg)
        val op = KeyframeStore.sample(m.keyframes, "opacity", localTimeSec, m.opacity)

        val angleNorm = ((angle % 360f) + 360f) % 360f
        val isHorizontal = angleNorm in 45f..135f || angleNorm in 225f..315f

        // SINGLE content — apply flip transform
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (isHorizontal) scaleX = -1f else scaleY = -1f
                    alpha = (op / 100f).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(
                        pivotFractionX = cX.coerceIn(0f, 1f),
                        pivotFractionY = cY.coerceIn(0f, 1f)
                    )
                }
        ) { content() }
    }

    override fun buildFFmpegFilters(
        clip: EditorClip,
        effect: AdvancedEffectState,
        clipDurationSec: Float
    ): List<String> {
        val m = effect.mirror ?: return emptyList()
        val angleNorm = ((m.angleDeg % 360f) + 360f) % 360f
        val isHorizontal = angleNorm in 45f..135f || angleNorm in 225f..315f
        return if (isHorizontal) listOf("hflip") else listOf("vflip")
    }
}
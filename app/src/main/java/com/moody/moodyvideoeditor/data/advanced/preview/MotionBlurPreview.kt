package com.moody.moodyvideoeditor.advanced.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import com.moody.moodyvideoeditor.advanced.base.AdvancedEffectRenderer
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectState
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectType
import com.moody.moodyvideoeditor.utils.KeyframeStore

class MotionBlurRenderer : AdvancedEffectRenderer {

    override val type = AdvancedEffectType.MOTION_BLUR

    @Composable
    override fun RenderPreview(
        clip: EditorClip,
        effect: AdvancedEffectState,
        localTimeSec: Float,
        content: @Composable () -> Unit
    ) {
        val m = effect.motionBlur ?: run { content(); return }

        val shutter = KeyframeStore.sample(
            m.keyframes, "shutterAngle", localTimeSec, m.shutterAngle
        )
        val intensity = KeyframeStore.sample(
            m.keyframes, "intensity", localTimeSec, m.intensity
        )

        val blurRadius = ((shutter / 360f) * intensity * 6f).coerceIn(0f, 40f)

        if (blurRadius < 0.5f) {
            content()
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(
                        radius = blurRadius.dp,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                    )
            ) { content() }
        }
    }

    override fun buildFFmpegFilters(
        clip: EditorClip,
        effect: AdvancedEffectState,
        clipDurationSec: Float
    ): List<String> {
        val mb = effect.motionBlur ?: return emptyList()
        val samples = mb.samples.coerceIn(2f, 24f).toInt()
        val intensity = mb.intensity.coerceIn(0.1f, 2f)
        val filters = mutableListOf<String>()
        if (mb.shutterAngle > 10f) {
            filters.add("tmix=frames=$samples:weights=1")
        }
        if (intensity > 1.2f) {
            filters.add("gblur=sigma=${"%.2f".format(intensity)}")
        }
        return filters
    }
}
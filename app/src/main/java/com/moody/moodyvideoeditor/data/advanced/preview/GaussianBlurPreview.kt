package com.moody.moodyvideoeditor.advanced.preview
// ADD these
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
import com.moody.moodyvideoeditor.data.advanced.models.BlurDimension
import com.moody.moodyvideoeditor.utils.KeyframeStore

class GaussianBlurRenderer : AdvancedEffectRenderer {

    override val type = AdvancedEffectType.GAUSSIAN_BLUR

    @Composable
    override fun RenderPreview(
        clip: EditorClip,
        effect: AdvancedEffectState,
        localTimeSec: Float,
        content: @Composable () -> Unit
    ) {
        val b = effect.gaussianBlur ?: run { content(); return }

        val radius = KeyframeStore.sample(
            b.keyframes, "blurriness", localTimeSec, b.blurriness
        ).coerceIn(0f, 1000f)

        if (radius < 0.5f) {
            content(); return
        }

        val radiusX = if (b.dimension == BlurDimension.VERTICAL) 0.dp else radius.dp
        val radiusY = if (b.dimension == BlurDimension.HORIZONTAL) 0.dp else radius.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(
                    radiusX = radiusX,
                    radiusY = radiusY,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded
                )
        ) { content() }
    }

    override fun buildFFmpegFilters(
        clip: EditorClip,
        effect: AdvancedEffectState,
        clipDurationSec: Float
    ): List<String> {
        val b = effect.gaussianBlur ?: return emptyList()
        val sigma = (b.blurriness / 10f).coerceIn(0.1f, 100f)
        val s = "%.2f".format(sigma)
        return when (b.dimension) {
            BlurDimension.BOTH -> listOf("gblur=sigma=$s:steps=2")
            BlurDimension.HORIZONTAL -> listOf("gblur=sigma=$s:steps=2:planes=1")
            BlurDimension.VERTICAL -> listOf("gblur=sigma=$s:steps=2:planes=2")
        }
    }
}
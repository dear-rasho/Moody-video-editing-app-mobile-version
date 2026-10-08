package com.moody.moodyvideoeditor.advanced.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.moody.moodyvideoeditor.advanced.base.AdvancedEffectRenderer
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectState
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectType
import com.moody.moodyvideoeditor.utils.KeyframeStore

class RoundedCropRenderer : AdvancedEffectRenderer {

    override val type = AdvancedEffectType.ROUNDED_CROP

    @Composable
    override fun RenderPreview(
        clip: EditorClip,
        effect: AdvancedEffectState,
        localTimeSec: Float,
        content: @Composable () -> Unit
    ) {
        val r = effect.roundedCrop ?: run { content(); return }

        val cr = KeyframeStore.sample(r.keyframes, "cornerRadius", localTimeSec, r.cornerRadius)
            .coerceIn(0f, 500f)
        val ct = KeyframeStore.sample(r.keyframes, "cropTop", localTimeSec, r.cropTop)
            .coerceIn(0f, 0.5f)
        val cb = KeyframeStore.sample(r.keyframes, "cropBottom", localTimeSec, r.cropBottom)
            .coerceIn(0f, 0.5f)
        val cl = KeyframeStore.sample(r.keyframes, "cropLeft", localTimeSec, r.cropLeft)
            .coerceIn(0f, 0.5f)
        val crR = KeyframeStore.sample(r.keyframes, "cropRight", localTimeSec, r.cropRight)
            .coerceIn(0f, 0.5f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cr.dp))
                .padding(
                    top = (ct * 100f).dp,
                    bottom = (cb * 100f).dp,
                    start = (cl * 100f).dp,
                    end = (crR * 100f).dp
                )
        ) { content() }
    }

    override fun buildFFmpegFilters(
        clip: EditorClip,
        effect: AdvancedEffectState,
        clipDurationSec: Float
    ): List<String> {
        val r = effect.roundedCrop ?: return emptyList()
        val filters = mutableListOf<String>()

        val ct = r.cropTop.coerceIn(0f, 0.5f)
        val cb = r.cropBottom.coerceIn(0f, 0.5f)
        val cl = r.cropLeft.coerceIn(0f, 0.5f)
        val cr = r.cropRight.coerceIn(0f, 0.5f)

        if (ct > 0.001f || cb > 0.001f || cl > 0.001f || cr > 0.001f) {
            val w = "iw*${"%.4f".format(1f - cl - cr)}"
            val h = "ih*${"%.4f".format(1f - ct - cb)}"
            val x = "iw*${"%.4f".format(cl)}"
            val y = "ih*${"%.4f".format(ct)}"
            filters.add("crop=$w:$h:$x:$y")
        }

        return filters
    }
}
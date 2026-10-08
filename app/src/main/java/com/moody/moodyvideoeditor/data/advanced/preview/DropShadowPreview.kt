package com.moody.moodyvideoeditor.advanced.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.moody.moodyvideoeditor.advanced.base.AdvancedEffectRenderer
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectState
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectType
import com.moody.moodyvideoeditor.utils.KeyframeStore
import kotlin.math.cos
import kotlin.math.sin

class DropShadowRenderer : AdvancedEffectRenderer {

    override val type = AdvancedEffectType.DROP_SHADOW
    override val isBehindLayer = true

    @Composable
    override fun RenderPreview(
        clip: EditorClip,
        effect: AdvancedEffectState,
        localTimeSec: Float,
        content: @Composable () -> Unit
    ) {
        val s = effect.dropShadow ?: run { content(); return }

        val opacity = KeyframeStore.sample(s.keyframes, "opacity", localTimeSec, s.opacity)
        val distance = KeyframeStore.sample(s.keyframes, "distance", localTimeSec, s.distance)
        val angle = KeyframeStore.sample(
            s.keyframes, "directionAngle", localTimeSec, s.directionAngle
        )
        val softness = KeyframeStore.sample(
            s.keyframes, "blurSoftness", localTimeSec, s.blurSoftness
        )

        val rad = Math.toRadians(angle.toDouble())
        val dx = (cos(rad) * distance).toFloat()
        val dy = (sin(rad) * distance).toFloat()

        Box(modifier = Modifier.fillMaxSize()) {

            // ─── SHADOW LAYER ───
            // Same content, but:
            //   1. compositingStrategy = Offscreen (isolated buffer)
            //   2. drawWithContent: content draw karo, phir SrcIn blend se
            //      ONLY the content's visible pixels ko user-color se tint karo
            //   3. offset + blur
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = dx
                        translationY = dy
                        alpha = (opacity / 100f).coerceIn(0f, 1f)
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        // Draw content first
                        drawContent()
                        // Tint ONLY the drawn pixels with user's shadow color
                        drawRect(
                            color = Color(s.shadowColor),
                            blendMode = BlendMode.SrcIn
                        )
                    }
                    .blur(
                        radius = softness.dp.coerceAtLeast(0.dp),
                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                    )
            ) { content() }

            // ─── ORIGINAL CONTENT ON TOP ───
            content()
        }
    }

    override fun buildFFmpegFilters(
        clip: EditorClip,
        effect: AdvancedEffectState,
        clipDurationSec: Float
    ): List<String> {
        val s = effect.dropShadow ?: return emptyList()
        val blur = s.blurSoftness.coerceIn(0f, 100f)
        return if (blur > 0.5f) {
            listOf("gblur=sigma=${"%.2f".format(blur / 5f)}")
        } else emptyList()
    }
}
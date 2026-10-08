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
import com.moody.moodyvideoeditor.data.advanced.models.TrackMatteType

class TrackMatteRenderer : AdvancedEffectRenderer {

    override val type = AdvancedEffectType.TRACK_MATTE

    @Composable
    override fun RenderPreview(
        clip: EditorClip,
        effect: AdvancedEffectState,
        localTimeSec: Float,
        content: @Composable () -> Unit
    ) {
        val t = effect.trackMatte ?: run { content(); return }

        // Preview hint: alpha reduction indicates matte is active.
        // Real compositing happens in export (needs 2 layers).
        val alphaMul = when (t.matteType) {
            TrackMatteType.ALPHA -> 0.92f
            TrackMatteType.ALPHA_INVERTED -> 0.82f
            TrackMatteType.LUMA -> 0.88f
            TrackMatteType.LUMA_INVERTED -> 0.78f
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = alphaMul }
        ) { content() }
    }

    override fun buildFFmpegFilters(
        clip: EditorClip,
        effect: AdvancedEffectState,
        clipDurationSec: Float
    ): List<String> {
        // Handled in exportLayeredTracks
        return emptyList()
    }
}
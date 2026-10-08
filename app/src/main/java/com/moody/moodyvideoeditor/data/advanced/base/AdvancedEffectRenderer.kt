package com.moody.moodyvideoeditor.advanced.base

import androidx.compose.runtime.Composable
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectState
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectType

// ═══════════════════════════════════════════════════════════════
//  ADVANCED EFFECT RENDERER INTERFACE
// ═══════════════════════════════════════════════════════════════

interface AdvancedEffectRenderer {

    val type: AdvancedEffectType

    /** True if effect draws an overlay on top of content */
    val isOverlayLayer: Boolean get() = false

    /** True if effect wraps behind content (like shadow) */
    val isBehindLayer: Boolean get() = false

    @Composable
    fun RenderPreview(
        clip: EditorClip,
        effect: AdvancedEffectState,
        localTimeSec: Float,
        content: @Composable () -> Unit
    )

    fun buildFFmpegFilters(
        clip: EditorClip,
        effect: AdvancedEffectState,
        clipDurationSec: Float
    ): List<String>
}
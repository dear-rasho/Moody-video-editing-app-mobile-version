package com.moody.moodyvideoeditor.advanced

import com.moody.moodyvideoeditor.advanced.base.AdvancedEffectRenderer
import com.moody.moodyvideoeditor.advanced.preview.*
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectState
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectType

// ═══════════════════════════════════════════════════════════════
//  ADVANCED EFFECT REGISTRY
// ═══════════════════════════════════════════════════════════════

object AdvancedEffectRegistry {

    private val renderers: Map<AdvancedEffectType, AdvancedEffectRenderer> = mapOf(
        AdvancedEffectType.MIRROR to MirrorRenderer(),
        AdvancedEffectType.GAUSSIAN_BLUR to GaussianBlurRenderer(),
        AdvancedEffectType.ROUNDED_CROP to RoundedCropRenderer(),
        AdvancedEffectType.FOUR_COLOR_GRADIENT to FourColorGradientRenderer(),
        AdvancedEffectType.DROP_SHADOW to DropShadowRenderer(),
        AdvancedEffectType.CHROMATIC_ABERRATION to ChromaticAberrationRenderer(),
        AdvancedEffectType.ROUGHEN_EDGES to RoughenEdgesRenderer(),
        AdvancedEffectType.TURBULENT_DISPLACE to TurbulentDisplaceRenderer(),
        AdvancedEffectType.MOTION_BLUR to MotionBlurRenderer(),
        AdvancedEffectType.TRACK_MATTE to TrackMatteRenderer()
    )

    fun get(type: AdvancedEffectType): AdvancedEffectRenderer? = renderers[type]

    fun all(): Collection<AdvancedEffectRenderer> = renderers.values

    /** FFmpeg filters for ALL effects (content + overlay combined) */
    fun buildAllFilters(clip: EditorClip, clipDurationSec: Float): List<String> {
        val result = mutableListOf<String>()
        clip.advancedEffects.forEach { effect ->
            val renderer = renderers[effect.type] ?: return@forEach
            result.addAll(renderer.buildFFmpegFilters(clip, effect, clipDurationSec))
        }
        return result
    }

    /** Effects that render on top (4-color gradient etc.) */
    fun overlayEffectsFor(clip: EditorClip): List<AdvancedEffectState> =
        clip.advancedEffects.filter { renderers[it.type]?.isOverlayLayer == true }

    /** Effects that modify the clip content directly */
    fun contentEffectsFor(clip: EditorClip): List<AdvancedEffectState> =
        clip.advancedEffects.filter { renderers[it.type]?.isOverlayLayer != true }
}
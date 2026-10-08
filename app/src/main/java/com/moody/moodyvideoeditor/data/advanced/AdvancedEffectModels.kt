package com.moody.moodyvideoeditor.data.advanced

import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.data.advanced.models.ChromaticAberrationEffect
import com.moody.moodyvideoeditor.data.advanced.models.DropShadowEffect
import com.moody.moodyvideoeditor.data.advanced.models.FourColorGradientEffect
import com.moody.moodyvideoeditor.data.advanced.models.GaussianBlurEffect
import com.moody.moodyvideoeditor.data.advanced.models.MirrorEffect
import com.moody.moodyvideoeditor.data.advanced.models.MotionBlurEffect
import com.moody.moodyvideoeditor.data.advanced.models.RoughenEdgesEffect
import com.moody.moodyvideoeditor.data.advanced.models.RoundedCropEffect
import com.moody.moodyvideoeditor.data.advanced.models.TrackMatteEffect
import com.moody.moodyvideoeditor.data.advanced.models.TurbulentDisplaceEffect

// ═══════════════════════════════════════════════════════════════
//  ADVANCED EFFECTS — Container + Type enum
// ═══════════════════════════════════════════════════════════════

enum class AdvancedEffectType(
    val key: String,
    val label: String,
    val icon: String,
    val category: String
) {
    MIRROR("mirror", "Mirror", "🪞", "Transform"),
    ROUGHEN_EDGES("roughen", "Roughen Edges", "🪨", "Distort"),
    GAUSSIAN_BLUR("blur", "Gaussian Blur", "💧", "Blur"),
    ROUNDED_CROP("rcrop", "Rounded Crop", "⬜", "Crop"),
    FOUR_COLOR_GRADIENT("gradient4", "4-Color Gradient", "🌈", "Color"),
    DROP_SHADOW("shadow", "Drop Shadow", "🌑", "Shadow"),
    TURBULENT_DISPLACE("displace", "Turbulent Displace", "🌪️", "Distort"),
    CHROMATIC_ABERRATION("chroma", "Chromatic Aberration", "🌈", "Lens"),
    MOTION_BLUR("mblur", "Motion Blur", "💨", "Blur"),
    TRACK_MATTE("matte", "Track Matte", "🎭", "Composite");

    companion object {
        fun fromKey(key: String): AdvancedEffectType? =
            values().firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}

data class AdvancedEffectState(
    val type: AdvancedEffectType,
    val mirror: MirrorEffect? = null,
    val roughenEdges: RoughenEdgesEffect? = null,
    val gaussianBlur: GaussianBlurEffect? = null,
    val roundedCrop: RoundedCropEffect? = null,
    val fourColorGradient: FourColorGradientEffect? = null,
    val dropShadow: DropShadowEffect? = null,
    val turbulentDisplace: TurbulentDisplaceEffect? = null,
    val chromaticAberration: ChromaticAberrationEffect? = null,
    val motionBlur: MotionBlurEffect? = null,
    val trackMatte: TrackMatteEffect? = null
) {
    companion object {
        fun withDefaults(type: AdvancedEffectType): AdvancedEffectState = when (type) {
            AdvancedEffectType.MIRROR ->
                AdvancedEffectState(type, mirror = MirrorEffect())

            AdvancedEffectType.ROUGHEN_EDGES ->
                AdvancedEffectState(type, roughenEdges = RoughenEdgesEffect())

            AdvancedEffectType.GAUSSIAN_BLUR ->
                AdvancedEffectState(type, gaussianBlur = GaussianBlurEffect())

            AdvancedEffectType.ROUNDED_CROP ->
                AdvancedEffectState(type, roundedCrop = RoundedCropEffect())

            AdvancedEffectType.FOUR_COLOR_GRADIENT ->
                AdvancedEffectState(type, fourColorGradient = FourColorGradientEffect())

            AdvancedEffectType.DROP_SHADOW ->
                AdvancedEffectState(type, dropShadow = DropShadowEffect())

            AdvancedEffectType.TURBULENT_DISPLACE ->
                AdvancedEffectState(type, turbulentDisplace = TurbulentDisplaceEffect())

            AdvancedEffectType.CHROMATIC_ABERRATION ->
                AdvancedEffectState(type, chromaticAberration = ChromaticAberrationEffect())

            AdvancedEffectType.MOTION_BLUR ->
                AdvancedEffectState(type, motionBlur = MotionBlurEffect())

            AdvancedEffectType.TRACK_MATTE ->
                AdvancedEffectState(type, trackMatte = TrackMatteEffect())
        }
    }

    fun keyframes(): Map<String, List<Keyframe>> = when (type) {
        AdvancedEffectType.MIRROR -> mirror?.keyframes ?: emptyMap()
        AdvancedEffectType.ROUGHEN_EDGES -> roughenEdges?.keyframes ?: emptyMap()
        AdvancedEffectType.GAUSSIAN_BLUR -> gaussianBlur?.keyframes ?: emptyMap()
        AdvancedEffectType.ROUNDED_CROP -> roundedCrop?.keyframes ?: emptyMap()
        AdvancedEffectType.FOUR_COLOR_GRADIENT -> fourColorGradient?.keyframes ?: emptyMap()
        AdvancedEffectType.DROP_SHADOW -> dropShadow?.keyframes ?: emptyMap()
        AdvancedEffectType.TURBULENT_DISPLACE -> turbulentDisplace?.keyframes ?: emptyMap()
        AdvancedEffectType.CHROMATIC_ABERRATION -> chromaticAberration?.keyframes ?: emptyMap()
        AdvancedEffectType.MOTION_BLUR -> motionBlur?.keyframes ?: emptyMap()
        AdvancedEffectType.TRACK_MATTE -> trackMatte?.keyframes ?: emptyMap()
    }
}
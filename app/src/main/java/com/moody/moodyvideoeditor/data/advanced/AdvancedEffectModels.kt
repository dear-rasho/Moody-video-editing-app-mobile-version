package com.moody.moodyvideoeditor.data.advanced

import com.moody.moodyvideoeditor.data.Keyframe

// ═══════════════════════════════════════════════════════════════
//  ADVANCED EFFECTS — Data Models
//  Each effect is a standalone layer (type = "advanced/plain")
//  with one active sub-effect. All properties support keyframes.
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

// ═══════════════════════════════════════════════════════════════
//  1. MIRROR
// ═══════════════════════════════════════════════════════════════
data class MirrorEffect(
    val centerX: Float = 0.5f,        // 0.0 – 1.0
    val centerY: Float = 0.5f,        // 0.0 – 1.0
    val angleDeg: Float = 90f,        // 0° – 360°
    val opacity: Float = 100f,        // 0 – 100
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)

// ═══════════════════════════════════════════════════════════════
//  2. ROUGHEN EDGES
// ═══════════════════════════════════════════════════════════════
data class RoughenEdgesEffect(
    val borderWidth: Float = 20f,     // 0 – 500
    val edgeSharpness: Float = 1f,    // 0 – 100
    val fractalScale: Float = 100f,   // 20 – 1000
    val evolution: Float = 0f,        // 0 – 360
    val complexity: Float = 1f,       // 1 – 10
    val randomSeed: Float = 0f,       // 0 – 9999
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)

// ═══════════════════════════════════════════════════════════════
//  3. GAUSSIAN BLUR
// ═══════════════════════════════════════════════════════════════
enum class BlurDimension { BOTH, HORIZONTAL, VERTICAL }

data class GaussianBlurEffect(
    val blurriness: Float = 20f,      // 0 – 1000
    val dimension: BlurDimension = BlurDimension.BOTH,
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)

// ═══════════════════════════════════════════════════════════════
//  4. ROUNDED CROP
// ═══════════════════════════════════════════════════════════════
data class RoundedCropEffect(
    val cornerRadius: Float = 40f,    // 0 – 500
    val cropTop: Float = 0f,          // 0 – 0.5
    val cropBottom: Float = 0f,
    val cropLeft: Float = 0f,
    val cropRight: Float = 0f,
    val feathering: Float = 0f,       // 0 – 200
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)

// ═══════════════════════════════════════════════════════════════
//  5. 4-COLOR GRADIENT
// ═══════════════════════════════════════════════════════════════
enum class GradientBlendMode(val key: String) {
    NORMAL("normal"),
    ADD("add"),
    MULTIPLY("multiply"),
    SCREEN("screen"),
    OVERLAY("overlay");

    companion object {
        fun fromKey(key: String): GradientBlendMode =
            values().firstOrNull { it.key.equals(key, ignoreCase = true) }
                ?: NORMAL
    }
}

data class FourColorGradientEffect(
    val color1: Long = 0xFFFF0000L,   // TL
    val color2: Long = 0xFF00FF00L,   // TR
    val color3: Long = 0xFF0000FFL,   // BR
    val color4: Long = 0xFFFFCC00L,   // BL
    val blendMode: GradientBlendMode = GradientBlendMode.NORMAL,
    val globalOpacity: Float = 100f,  // 0 – 100
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)

// ═══════════════════════════════════════════════════════════════
//  6. DROP SHADOW
// ═══════════════════════════════════════════════════════════════
data class DropShadowEffect(
    val shadowColor: Long = 0xFF000000L,
    val opacity: Float = 50f,         // 0 – 100
    val distance: Float = 5f,         // 0 – 500
    val directionAngle: Float = 135f, // 0° – 360°
    val blurSoftness: Float = 5f,     // 0 – 100
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)

// ═══════════════════════════════════════════════════════════════
//  7. TURBULENT DISPLACE
// ═══════════════════════════════════════════════════════════════
data class TurbulentDisplaceEffect(
    val amount: Float = 50f,          // 0 – 1000
    val size: Float = 100f,           // 0 – 2000
    val offsetX: Float = 0.5f,        // 0 – 1
    val offsetY: Float = 0.5f,        // 0 – 1
    val evolutionSpeed: Float = 1f,   // 0 – 100
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)

// ═══════════════════════════════════════════════════════════════
//  8. CHROMATIC ABERRATION
// ═══════════════════════════════════════════════════════════════
data class ChromaticAberrationEffect(
    val redShiftX: Float = 0f,        // -50 – 50
    val redShiftY: Float = 0f,
    val blueShiftX: Float = 0f,
    val blueShiftY: Float = 0f,
    val blurRadius: Float = 0f,       // 0 – 50
    val falloffThreshold: Float = 0.5f, // 0 – 1
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)

// ═══════════════════════════════════════════════════════════════
//  9. MOTION BLUR
// ═══════════════════════════════════════════════════════════════
data class MotionBlurEffect(
    val shutterAngle: Float = 180f,   // 0 – 720
    val samples: Float = 16f,         // 1 – 64
    val intensity: Float = 1f,        // 0 – 2
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)

// ═══════════════════════════════════════════════════════════════
//  10. TRACK MATTE
// ═══════════════════════════════════════════════════════════════
enum class TrackMatteType(val key: String) {
    ALPHA("alpha"),
    ALPHA_INVERTED("alpha_inv"),
    LUMA("luma"),
    LUMA_INVERTED("luma_inv");

    companion object {
        fun fromKey(key: String): TrackMatteType =
            values().firstOrNull { it.key.equals(key, ignoreCase = true) } ?: ALPHA
    }
}

data class TrackMatteEffect(
    val matteType: TrackMatteType = TrackMatteType.ALPHA,
    val targetLayerId: String? = null,
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)

// ═══════════════════════════════════════════════════════════════
//  CONTAINER — Single active effect per layer
// ═══════════════════════════════════════════════════════════════
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
            AdvancedEffectType.MIRROR -> AdvancedEffectState(type, mirror = MirrorEffect())
            AdvancedEffectType.ROUGHEN_EDGES -> AdvancedEffectState(
                type,
                roughenEdges = RoughenEdgesEffect()
            )

            AdvancedEffectType.GAUSSIAN_BLUR -> AdvancedEffectState(
                type,
                gaussianBlur = GaussianBlurEffect()
            )

            AdvancedEffectType.ROUNDED_CROP -> AdvancedEffectState(
                type,
                roundedCrop = RoundedCropEffect()
            )

            AdvancedEffectType.FOUR_COLOR_GRADIENT -> AdvancedEffectState(
                type,
                fourColorGradient = FourColorGradientEffect()
            )

            AdvancedEffectType.DROP_SHADOW -> AdvancedEffectState(
                type,
                dropShadow = DropShadowEffect()
            )

            AdvancedEffectType.TURBULENT_DISPLACE -> AdvancedEffectState(
                type,
                turbulentDisplace = TurbulentDisplaceEffect()
            )

            AdvancedEffectType.CHROMATIC_ABERRATION -> AdvancedEffectState(
                type,
                chromaticAberration = ChromaticAberrationEffect()
            )

            AdvancedEffectType.MOTION_BLUR -> AdvancedEffectState(
                type,
                motionBlur = MotionBlurEffect()
            )

            AdvancedEffectType.TRACK_MATTE -> AdvancedEffectState(
                type,
                trackMatte = TrackMatteEffect()
            )
        }
    }

    // Get current keyframe map for this effect type
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
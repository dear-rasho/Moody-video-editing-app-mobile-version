package com.moody.moodyvideoeditor.data

data class EffectState(
    val kind: String = KIND_EFFECT,
    val presetKey: String = "",
    val filters: ColorFilterValues? = null,
    val animatedColor: com.moody.moodyvideoeditor.utils.AnimatedColorConfig? = null,
    val motion: MotionConfig? = null,
    val blur: com.moody.moodyvideoeditor.utils.BlurConfig? = null,
    val overlay: OverlayConfig? = null,
    val edgeGlow: EdgeGlowConfig? = null,
    val lightLeak: LightLeakConfig? = null,
    val lightning: LightningConfig? = null,
    val particle: ParticleConfig? = null,
    val fire: FireConfig? = null,
    val dust: DustConfig? = null,
    val masterIntensity: Float = 100f
) {
    companion object {
        const val KIND_EFFECT = "effect"
        const val KIND_FILTER = "filter"
        const val KIND_ADJUSTMENT = "adjustment"
        const val KIND_COLOR_WHEEL = "colorWheel"
        const val KIND_CHROMA = "chroma"
    }
}
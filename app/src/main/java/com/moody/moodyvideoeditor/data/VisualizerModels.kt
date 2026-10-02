package com.moody.moodyvideoeditor.data

enum class VisualizerPreset(
    val key: String,
    val label: String,
    val icon: String
) {
    NEON_GLOW_RING("neonGlowRing", "Neon Glow Ring", "💫"),
    FREQUENCY_SPECTRUM_RING("frequencySpectrumRing", "Spectrum Ring", "📊"),
    PARTICLE_ORBIT_RING("particleOrbitRing", "Particle Orbit", "✨"),
    LIQUID_WAVE_RING("liquidWaveRing", "Liquid Wave", "🌊"),
    DOUBLE_ORBIT_RINGS("doubleOrbitRings", "Double Orbit", "⭕"),
    DOTTED_RADIAL_WAVE("dottedRadialWave", "Dotted Radial", "🔵"),
    VINYL_RECORD_SPIN("vinylRecordSpin", "Vinyl Record", "💿"),
    AUDIO_REACTIVE_CENTER_ART("audioReactiveCenterArt", "Center Art", "🖼️"),
    BROKEN_SEGMENT_RING("brokenSegmentRing", "Broken Ring", "🔷"),
    VORTEX_TUNNEL("vortexTunnel", "Vortex Tunnel", "🌀");

    companion object {
        fun fromKey(key: String): VisualizerPreset =
            values().firstOrNull { it.key == key } ?: NEON_GLOW_RING
    }
}

data class VisualizerState(
    val preset: VisualizerPreset = VisualizerPreset.NEON_GLOW_RING,
    val linkedAudioClipId: String? = null,

    // Colors
    val color1: Long = 0xFFFFD166,
    val color2: Long = 0xFFFFA500,

    // Audio reaction
    val sensitivity: Float = 1.5f,
    val smoothing: Float = 0.65f,

    // Geometry
    val size: Float = 0.32f,
    val positionX: Float = 0.5f,
    val positionY: Float = 0.5f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val glow: Boolean = true,

    // ═══════════════════════════════════════════════════════════
    //  CENTER IMAGE
    // ═══════════════════════════════════════════════════════════
    val imageUri: String? = null,
    val showImage: Boolean = false,
    val imageScale: Float = 0.55f,
    val imageOpacity: Float = 1f,

    // Image pulse mechanics
    val imageIdleRotation: Boolean = true,
    val imageIdleSpeed: Float = 0.5f,
    val imagePulseAmount: Float = 0.15f,
    val imageBassOnly: Boolean = true,

    // ═══════════════════════════════════════════════════════════
    //  🆕 CENTER TEXT (draws inside circle)
    // ═══════════════════════════════════════════════════════════
    val showText: Boolean = false,
    val textContent: String = "🎵",
    val textState: TextState = TextState(
        content = "🎵",
        fontSize = 48,
        fontWeight = "bold",
        color = 0xFFFFFFFF,
        alignment = "center",
        letterSpacing = 0f,
        lineHeight = 1.2f
    ),
    // When both image + text are enabled, text is drawn on top of image
    val textOnTopOfImage: Boolean = true,

    // Band-driven behavior
    val bassRingBoost: Float = 1.0f,
    val midBarBoost: Float = 1.0f,
    val trebleSpikeBoost: Float = 1.0f,

    // Beat sync
    val beatTimesMs: List<Long> = emptyList(),
    val beatStrengths: List<Float> = emptyList(),
    val beatReaction: Float = 1.0f,
    val beatPulseDurationMs: Long = 260L,
    val useBeatSync: Boolean = true,

    // Lerp factor
    val lerpFactor: Float = 0.20f
) {
    val isActive: Boolean get() = opacity > 0f

    fun strengthAt(sourceMs: Long): Float {
        if (!useBeatSync || beatTimesMs.isEmpty()) return 0f
        var maxPulse = 0f
        for (i in beatTimesMs.indices) {
            val dt = sourceMs - beatTimesMs[i]
            if (dt < 0) break
            if (dt > beatPulseDurationMs) continue
            val strength = beatStrengths.getOrElse(i) { 0.5f }
            val progress = 1f - (dt.toFloat() / beatPulseDurationMs)
            val pulse = strength * progress * beatReaction
            if (pulse > maxPulse) maxPulse = pulse
        }
        return maxPulse.coerceIn(0f, 1f)
    }

    val hasBeats: Boolean get() = useBeatSync && beatTimesMs.isNotEmpty()

    /** Is anything shown inside the circle? */
    val hasCenterContent: Boolean
        get() = showImage || (showText && textContent.isNotBlank())
}
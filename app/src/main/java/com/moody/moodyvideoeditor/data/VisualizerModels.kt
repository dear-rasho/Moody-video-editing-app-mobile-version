package com.moody.moodyvideoeditor.data

enum class VisualizerPreset(
    val key: String,
    val label: String,
    val icon: String,
    val category: String
) {
    // ═══════════════════════════════════════════════════════════
    //  1️⃣ SPECTRUM (20)
    // ═══════════════════════════════════════════════════════════
    CIRCULAR_SPECTRUM("circularSpectrum", "Circular Spectrum", "🔵", "spectrum"),
    LINEAR_WAVEFORM("linearWaveform", "Linear Waveform", "〰️", "spectrum"),
    DOUBLE_SIDED_BARS("doubleSidedBars", "Double-Sided", "↕️", "spectrum"),
    RADIAL_BARS("radialBars", "Radial Bars", "🎯", "spectrum"),
    INNER_RADIAL_BARS("innerRadialBars", "Inner Radial", "🔹", "spectrum"),
    HEARTBEAT_WAVE("heartbeatWave", "Heartbeat", "💓", "spectrum"),
    SQUARE_SPECTRUM("squareSpectrum", "Square Spectrum", "⬜", "spectrum"),
    TRIANGLE_BEATS("triangleBeats", "Triangle Beats", "🔺", "spectrum"),
    HEXAGON_PULSE("hexagonPulse", "Hexagon Pulse", "⬡", "spectrum"),
    DOT_MATRIX("dotMatrix", "Dot Matrix", "🔘", "spectrum"),
    MIRRORED_LINEAR("mirroredLinear", "Mirrored Linear", "🪞", "spectrum"),
    GLOW_WAVES("glowWaves", "Glow Waves", "✨", "spectrum"),
    THICK_BARS("thickBars", "Thick Bars", "▬", "spectrum"),
    THIN_STRINGS("thinStrings", "Thin Strings", "🎸", "spectrum"),
    SINE_WAVE("sineWave", "Sine Wave", "🌊", "spectrum"),
    PERSPECTIVE_3D("perspective3d", "3D Perspective", "📐", "spectrum"),
    FREQUENCY_VOLCANO("frequencyVolcano", "Volcano", "🌋", "spectrum"),
    TORNADO_SPIRAL("tornadoSpiral", "Tornado Spiral", "🌪️", "spectrum"),
    DUAL_RING("dualRing", "Dual Ring", "💠", "spectrum"),
    STAR_BURST("starBurst", "Star Burst", "⭐", "spectrum"),

    // ═══════════════════════════════════════════════════════════
    //  2️⃣ PARTICLES (20)
    // ═══════════════════════════════════════════════════════════
    BASS_PARTICLES("bassParticles", "Bass Particles", "💥", "particles"),
    FLOATING_DUST("floatingDust", "Floating Dust", "🌫️", "particles"),
    LIQUID_DROPS("liquidDrops", "Liquid Drops", "💧", "particles"),
    FIREFLY_GLOW("fireflyGlow", "Firefly Glow", "🪰", "particles"),
    SMOKE_AURA("smokeAura", "Smoke Aura", "💨", "particles"),
    MATRIX_RAIN("matrixRain", "Matrix Rain", "💊", "particles"),
    SNOWFALL("snowfall", "Snowfall", "❄️", "particles"),
    COSMIC_NEBULA("cosmicNebula", "Cosmic Nebula", "🌌", "particles"),
    SPARK_TRAIL("sparkTrail", "Spark Trail", "🔥", "particles"),
    INK_BLEED("inkBleed", "Ink Bleed", "🖋️", "particles"),
    SAND_STORM("sandStorm", "Sand Storm", "🏜️", "particles"),
    MAGIC_DUST("magicDust", "Magic Dust", "🪄", "particles"),
    METEOR_SHOWER("meteorShower", "Meteor Shower", "☄️", "particles"),
    PLASMA_ORBS("plasmaOrbs", "Plasma Orbs", "🔮", "particles"),
    CONFETTI_POP("confettiPop", "Confetti Pop", "🎊", "particles"),
    BUBBLES_POP("bubblesPop", "Bubbles Pop", "🫧", "particles"),
    ELECTRIC_STORM("electricStorm", "Electric Storm", "⚡", "particles"),
    DISINTEGRATION("disintegration", "Disintegration", "💫", "particles"),
    GALAXY_VORTEX("galaxyVortex", "Galaxy Vortex", "🌠", "particles"),
    CYBER_GRID("cyberGrid", "Cyber Grid", "🕸️", "particles"),

    // ═══════════════════════════════════════════════════════════
    //  3️⃣ NEON / CYBER (20)
    // ═══════════════════════════════════════════════════════════
    NEON_GLOW_RING("neonGlowRing", "Neon Glow Ring", "💫", "neon"),
    RGB_GLITCH("rgbGlitch", "RGB Glitch", "🌈", "neon"),
    VAPORWAVE_GRID("vaporwaveGrid", "Vaporwave", "🌴", "neon"),
    VHS_NOISE("vhsNoise", "VHS Noise", "📼", "neon"),
    LASER_BEAM("laserBeam", "Laser Beam", "🔴", "neon"),
    DIGITAL_EQ("digitalEq", "Digital EQ", "🎚️", "neon"),
    CHROMA_PULSE("chromaPulse", "Chroma Pulse", "🎨", "neon"),
    SCANLINE_DISTORT("scanlineDistort", "Scanline", "📺", "neon"),
    TRON_WIREFRAME("tronWireframe", "Tron Grid", "🔷", "neon"),
    LED_MATRIX("ledMatrix", "LED Matrix", "🔲", "neon"),
    ARCADE_GAMEOVER("arcadeGameover", "Arcade", "🕹️", "neon"),
    LASER_TUNNEL("laserTunnel", "Laser Tunnel", "🚀", "neon"),
    NEON_TRACER("neonTracer", "Neon Tracer", "💡", "neon"),
    PIXEL_DISSOLVE("pixelDissolve", "Pixel Dissolve", "🟦", "neon"),
    ECG_GRID("ecgGrid", "ECG Grid", "📈", "neon"),
    SYNTH_SUN("synthSun", "Synthwave Sun", "🌅", "neon"),
    HOLOGRAM("hologram", "Hologram", "📽️", "neon"),
    CRT_FLICKER("crtFlicker", "CRT Flicker", "🎞️", "neon"),
    VECTOR_WAVE("vectorWave", "Vector Wave", "📊", "neon"),
    GLITCH_TWITCH("glitchTwitch", "Glitch Twitch", "📳", "neon"),

    // ═══════════════════════════════════════════════════════════
    //  4️⃣ GEOMETRIC (20)
    // ═══════════════════════════════════════════════════════════
    MINIMAL_DOTS("minimalDots", "Minimal Dots", "⚫", "geometric"),
    ROTATING_POLY("rotatingPoly", "Rotating Poly", "🔶", "geometric"),
    KALEIDOSCOPE("kaleidoscope", "Kaleidoscope", "🔯", "geometric"),
    INTERLOCKING_RINGS("interlockingRings", "Interlock Rings", "⭕", "geometric"),
    EXPANDING_SQUARES("expandingSquares", "Expanding Squares", "⬜", "geometric"),
    ORIGAMI("origami", "Origami Fold", "🦢", "geometric"),
    FRACTAL_ZOOM("fractalZoom", "Fractal Zoom", "🌀", "geometric"),
    PARALLAX_LINES("parallaxLines", "Parallax Lines", "≡", "geometric"),
    ISOMETRIC_BLOCKS("isometricBlocks", "Isometric Blocks", "🧱", "geometric"),
    SYMMETRIC_MIRROR("symmetricMirror", "Sym Mirror", "🪞", "geometric"),
    CROSSHAIR("crosshair", "Crosshair", "🎯", "geometric"),
    DNA_STRAND("dnaStrand", "DNA Strand", "🧬", "geometric"),
    CONCENTRIC_RINGS("concentricRings", "Concentric", "🎯", "geometric"),
    FLOATING_SHARDS("floatingShards", "Floating Shards", "💎", "geometric"),
    INFINITE_TUNNEL("infiniteTunnel", "Infinite Tunnel", "🚇", "geometric"),
    SHAPE_MORPH("shapeMorph", "Shape Morph", "🔵", "geometric"),
    GYROSCOPE("gyroscope", "Gyroscope", "🌐", "geometric"),
    SPLIT_DIAGONAL("splitDiagonal", "Split Diagonal", "◤", "geometric"),
    CHECKERBOARD("checkerboard", "Checkerboard", "🏁", "geometric"),
    VECTOR_RIBBON("vectorRibbon", "Vector Ribbon", "🎗️", "geometric"),

    // ═══════════════════════════════════════════════════════════
    //  5️⃣ CINEMATIC (20)
    // ═══════════════════════════════════════════════════════════
    LENS_FLARE("lensFlare", "Lens Flare", "🔆", "cinematic"),
    CAMERA_SHUTTER("cameraShutter", "Camera Shutter", "📷", "cinematic"),
    CINEMATIC_DUST("cinematicDust", "Cinematic Dust", "✨", "cinematic"),
    VIGNETTE_BREATHE("vignetteBreathe", "Vignette Breathe", "🕳️", "cinematic"),
    BLUR_DISSOLVE("blurDissolve", "Blur Dissolve", "🌫️", "cinematic"),
    SUNBEAMS("sunbeams", "Sunbeams", "🌞", "cinematic"),
    RAINDROPS("raindrops", "Raindrops", "☔", "cinematic"),
    FILM_GRAIN("filmGrain", "Film Grain", "🎞️", "cinematic"),
    LIGHT_LEAK("lightLeak", "Light Leak", "🌅", "cinematic"),
    FOGGY_AMBIANCE("foggyAmbiance", "Foggy", "🌁", "cinematic"),
    BOKEH_DRIFT("bokehDrift", "Bokeh", "🔮", "cinematic"),
    SHADOW_WAVE("shadowWave", "Shadow Wave", "👤", "cinematic"),
    WATER_RIPPLE("waterRipple", "Water Ripple", "💧", "cinematic"),
    CLOUDY_TIMELAPSE("cloudyTimelapse", "Cloudy", "☁️", "cinematic"),
    LIGHT_STREAK("lightStreak", "Light Streak", "💡", "cinematic"),
    VINTAGE_COUNTDOWN("vintageCountdown", "Countdown", "🎬", "cinematic"),
    GOLDEN_HOUR("goldenHour", "Golden Hour", "🌇", "cinematic"),
    PRISM_RAINBOW("prismRainbow", "Prism Rainbow", "🌈", "cinematic"),
    CAMERA_SHAKE("cameraShake", "Camera Shake", "📳", "cinematic"),
    HORIZON_ZOOM("horizonZoom", "Horizon Zoom", "🏞️", "cinematic");

    companion object {
        val CATEGORIES = listOf(
            "spectrum" to "🎵 Spectrum",
            "particles" to "💫 Particles",
            "neon" to "🌈 Neon",
            "geometric" to "🔷 Geometric",
            "cinematic" to "🎬 Cinematic"
        )

        // Legacy keys from old 10-preset versions → map to new
        private val LEGACY_KEY_MAP = mapOf(
            "frequencySpectrumRing" to RADIAL_BARS,
            "particleOrbitRing" to BASS_PARTICLES,
            "liquidWaveRing" to SINE_WAVE,
            "doubleOrbitRings" to DUAL_RING,
            "dottedRadialWave" to DOT_MATRIX,
            "vinylRecordSpin" to ROTATING_POLY,
            "audioReactiveCenterArt" to CONCENTRIC_RINGS,
            "brokenSegmentRing" to SPLIT_DIAGONAL,
            "vortexTunnel" to TORNADO_SPIRAL
        )

        fun fromKey(key: String): VisualizerPreset =
            values().firstOrNull { it.key == key }
                ?: LEGACY_KEY_MAP[key]
                ?: CIRCULAR_SPECTRUM

        fun byCategory(cat: String): List<VisualizerPreset> =
            values().filter { it.category == cat }
    }
}

data class VisualizerState(
    val preset: VisualizerPreset = VisualizerPreset.NEON_GLOW_RING,
    val linkedAudioClipId: String? = null,

    val color1: Long = 0xFFFFD166,
    val color2: Long = 0xFFFFA500,

    val sensitivity: Float = 1.5f,
    val smoothing: Float = 0.65f,

    val size: Float = 0.32f,
    val positionX: Float = 0.5f,
    val positionY: Float = 0.5f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val glow: Boolean = true,

    val imageUri: String? = null,
    val showImage: Boolean = false,
    val imageScale: Float = 0.55f,
    val imageOpacity: Float = 1f,

    val imageIdleRotation: Boolean = true,
    val imageIdleSpeed: Float = 0.5f,
    val imagePulseAmount: Float = 0.15f,
    val imageBassOnly: Boolean = true,

    val showText: Boolean = false,
    val textContent: String = "🎵",
    val textState: TextState = TextState(
        content = "🎵",
        fontSize = 48,
        fontWeight = "bold",
        color = 0xFFFFFFFF,
        alignment = "center"
    ),
    val textOnTopOfImage: Boolean = true,

    val bassRingBoost: Float = 1.0f,
    val midBarBoost: Float = 1.0f,
    val trebleSpikeBoost: Float = 1.0f,

    val beatTimesMs: List<Long> = emptyList(),
    val beatStrengths: List<Float> = emptyList(),
    val beatReaction: Float = 1.0f,
    val beatPulseDurationMs: Long = 260L,
    val useBeatSync: Boolean = true,

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

    val hasCenterContent: Boolean
        get() = showImage || (showText && textContent.isNotBlank())
}
package com.moody.moodyvideoeditor.data

// Mirrors js/features/effect.js PRESETS array.
// Each preset has a kind: motion | color | overlay | edge_glow | light_leak.
data class EffectPreset(
    val key: String,
    val label: String,
    val icon: String,
    val kind: EffectKind,
    val motion: MotionConfig? = null,
    val filters: ColorFilterValues? = null,
    val animatedColor: com.moody.moodyvideoeditor.utils.AnimatedColorConfig? = null,
    val overlay: OverlayConfig? = null,
    val edgeGlow: EdgeGlowConfig? = null,
    val lightLeak: LightLeakConfig? = null
)

enum class EffectKind {
    MOTION, ANIMATED_COLOR, OVERLAY, EDGE_GLOW, LIGHT_LEAK
}

// Mirrors MOTION_MAP values from beatsEngine.js
data class MotionConfig(
    val type: String,       // shake | bounce | pulse | zoomPulse | rotate | glitch
    val intensity: Float,   // 40..220
    val speed: Float        // 0.4..7.0
)

// Mirrors effect.js filter values (subset of full FilterState — only what presets use)
data class ColorFilterValues(
    val brightness: Float = 100f,
    val contrast: Float = 100f,
    val saturation: Float = 100f,
    val hue: Float = 0f,
    val grayscale: Float = 0f,
    val sepia: Float = 0f,
    val invert: Float = 0f,
    val blur: Float = 0f,
    val opacity: Float = 100f
)

// Mirrors OVERLAY presets in effect.js
data class OverlayConfig(
    val type: String,
    val intensity: Float = 100f,
    val color: Long = 0xFFFFFFFF,
    val color2: Long = 0xFFFFFFFF,
    val size: Float = 1.0f,
    val opacity: Float = 1.0f,
    val speed: Float = 1.0f,
    val shape: String = "circle",
    val glowEnabled: Boolean = false,
    val glowRadius: Float = 1.8f,
    val glowColor: Long = 0xFFFFFFFF
)

// ═══════════════════════════════════════════════════════════════
//  EDGE GLOW — Config + Animation
// ═══════════════════════════════════════════════════════════════

enum class EdgeGlowAnimation {
    STATIC,      // constant glow
    PULSE,       // rhythm pulsing
    BREATHE,     // slow in-out
    FLICKER,     // random flicker
    STROBE,      // fast on/off
    RAINBOW,     // color cycling
    WAVE,        // moving offset around edges
    SPARK,       // quick sparkle flashes
    FIRE,        // orange flame pulse
    ELECTRIC,    // blue zap with jitter
    NEON,        // sustained bright
    AURORA,      // multi-color drift
    GHOST,       // soft fading
    GLITCH       // random offsets
}

data class EdgeGlowConfig(
    val color: Long = 0xFFFFFFFF,             // primary glow color
    val color2: Long = 0xFF00E5FF,            // secondary (rainbow/aurora)
    val radius: Float = 20f,                  // 5-80 px
    val intensity: Float = 85f,               // 0-100
    val spread: Float = 6f,                   // 0-30 px
    val animation: EdgeGlowAnimation = EdgeGlowAnimation.STATIC,
    val speed: Float = 1f                     // 0.2-4.0
)

// ═══════════════════════════════════════════════════════════════
//  LIGHT LEAK — Config + Animation + Preset + State
// ═══════════════════════════════════════════════════════════════

enum class LightLeakAnimation {
    STATIC,      // constant glow
    PULSE,       // size/alpha pulse
    BREATHE,     // slow in-out
    FLICKER,     // random flicker
    DRIFT,       // slow position drift
    SWEEP        // horizontal sweep
}

data class LightLeakConfig(
    val color1: Long = 0xFFFFCC66,
    val color2: Long = 0xFFFF8800,
    val positionX: Float = 0.5f,       // 0..1
    val positionY: Float = 0.5f,       // 0..1
    val radius: Float = 0.6f,          // 0.1..2.0
    val intensity: Float = 100f,       // 0..200
    val softness: Float = 0.5f,        // 0..1
    val animation: LightLeakAnimation = LightLeakAnimation.STATIC,
    val speed: Float = 1.0f
)

data class LightLeakPreset(
    val key: String,
    val label: String,
    val icon: String,
    val config: LightLeakConfig
)

data class LightLeakState(
    val presetKey: String = "goldenHour",
    val config: LightLeakConfig = LightLeakConfig(),
    val opacity: Float = 100f
)

object EffectLibrary {


    //  MOTION EFFECTS — mirrors effect.js motion presets

    val MOTION_EFFECTS: List<EffectPreset> = listOf(
        // Shake family
        EffectPreset("shake", "Shake", "📳", EffectKind.MOTION, MotionConfig("shake", 90f, 1.2f)),
        EffectPreset("tremor", "Tremor", "💥", EffectKind.MOTION, MotionConfig("shake", 140f, 1.5f)),
        EffectPreset("quake", "Quake", "🌋", EffectKind.MOTION, MotionConfig("shake", 180f, 1.8f)),
        EffectPreset(
            "earthquake",
            "Earthquake",
            "🏚️",
            EffectKind.MOTION,
            MotionConfig("shake", 220f, 2.0f)
        ),
        EffectPreset("hit", "Hit", "👊", EffectKind.MOTION, MotionConfig("shake", 130f, 2.2f)),
        EffectPreset("impact", "Impact", "💢", EffectKind.MOTION, MotionConfig("shake", 150f, 1.6f)),
        EffectPreset("jolt", "Jolt", "⚡", EffectKind.MOTION, MotionConfig("shake", 120f, 2.5f)),
        EffectPreset("rumble", "Rumble", "🥁", EffectKind.MOTION, MotionConfig("shake", 160f, 1.3f)),
        EffectPreset(
            "vibration",
            "Vibration",
            "📱",
            EffectKind.MOTION,
            MotionConfig("shake", 60f, 3.0f)
        ),
        EffectPreset("jitter", "Jitter", "🥶", EffectKind.MOTION, MotionConfig("shake", 70f, 3.5f)),
        EffectPreset("chaos", "Chaos", "🌀", EffectKind.MOTION, MotionConfig("shake", 170f, 2.6f)),

        // Bounce family
        EffectPreset(
            "bounce",
            "Bounce",
            "🏀",
            EffectKind.MOTION,
            MotionConfig("bounce", 100f, 1.4f)
        ),
        EffectPreset("punch", "Punch", "🥊", EffectKind.MOTION, MotionConfig("bounce", 130f, 1.8f)),
        EffectPreset("kick", "Kick", "🦵", EffectKind.MOTION, MotionConfig("bounce", 140f, 2.2f)),
        EffectPreset("throb", "Throb", "💓", EffectKind.MOTION, MotionConfig("bounce", 90f, 1.0f)),
        EffectPreset("beat", "Beat", "🎵", EffectKind.MOTION, MotionConfig("bounce", 110f, 1.6f)),
        EffectPreset(
            "spring",
            "Spring",
            "🪀",
            EffectKind.MOTION,
            MotionConfig("bounce", 150f, 1.9f)
        ),
        EffectPreset(
            "elastic",
            "Elastic",
            "🪃",
            EffectKind.MOTION,
            MotionConfig("bounce", 130f, 1.5f)
        ),
        EffectPreset(
            "headbang",
            "Headbang",
            "🤘",
            EffectKind.MOTION,
            MotionConfig("bounce", 150f, 1.5f)
        ),

        // Pulse family
        EffectPreset("pulse", "Pulse", "💓", EffectKind.MOTION, MotionConfig("pulse", 100f, 1.2f)),
        EffectPreset(
            "heartbeat",
            "Heartbeat",
            "❤️",
            EffectKind.MOTION,
            MotionConfig("pulse", 130f, 0.6f)
        ),
        EffectPreset("breath", "Breathe", "🫁", EffectKind.MOTION, MotionConfig("pulse", 80f, 0.5f)),
        EffectPreset("pump", "Pump", "💪", EffectKind.MOTION, MotionConfig("pulse", 110f, 1.8f)),
        EffectPreset("thump", "Thump", "🫀", EffectKind.MOTION, MotionConfig("pulse", 140f, 0.8f)),
        EffectPreset("drum", "Drum", "🥁", EffectKind.MOTION, MotionConfig("pulse", 120f, 2.0f)),

        // Zoom pulse family
        EffectPreset(
            "zoomPulse",
            "Zoom Pulse",
            "🔍",
            EffectKind.MOTION,
            MotionConfig("zoomPulse", 100f, 1.0f)
        ),
        EffectPreset(
            "zoomHard",
            "Zoom Hard",
            "🔎",
            EffectKind.MOTION,
            MotionConfig("zoomPulse", 180f, 1.2f)
        ),
        EffectPreset(
            "zoomSoft",
            "Zoom Soft",
            "🔍",
            EffectKind.MOTION,
            MotionConfig("zoomPulse", 60f, 0.8f)
        ),
        EffectPreset(
            "push",
            "Push",
            "➡️",
            EffectKind.MOTION,
            MotionConfig("zoomPulse", 140f, 1.1f)
        ),
        EffectPreset(
            "pull",
            "Pull",
            "⬅️",
            EffectKind.MOTION,
            MotionConfig("zoomPulse", 120f, 0.9f)
        ),
        EffectPreset("rush", "Rush", "⚡", EffectKind.MOTION, MotionConfig("zoomPulse", 160f, 1.5f)),
        EffectPreset("slam", "Slam", "💥", EffectKind.MOTION, MotionConfig("zoomPulse", 200f, 1.8f)),

        // Rotate family
        EffectPreset("wobble", "Wobble", "🔄", EffectKind.MOTION, MotionConfig("rotate", 80f, 1.0f)),
        EffectPreset("swing", "Swing", "🎢", EffectKind.MOTION, MotionConfig("rotate", 100f, 1.2f)),
        EffectPreset("sway", "Sway", "🌊", EffectKind.MOTION, MotionConfig("rotate", 50f, 0.8f)),
        EffectPreset("rock", "Rock", "🪨", EffectKind.MOTION, MotionConfig("rotate", 90f, 1.0f)),
        EffectPreset("spin", "Spin", "🌪️", EffectKind.MOTION, MotionConfig("rotate", 200f, 2.0f)),
        EffectPreset("roll", "Roll", "🎳", EffectKind.MOTION, MotionConfig("rotate", 120f, 1.4f)),
        EffectPreset("whirl", "Whirl", "🌀", EffectKind.MOTION, MotionConfig("rotate", 180f, 1.8f)),
        EffectPreset("tilt", "Tilt", "📐", EffectKind.MOTION, MotionConfig("rotate", 60f, 0.7f)),

        // Glitch family
        EffectPreset(
            "glitch",
            "Glitch",
            "⚡",
            EffectKind.MOTION,
            MotionConfig("glitch", 100f, 2.0f)
        ),
        EffectPreset("noise", "Noise", "📺", EffectKind.MOTION, MotionConfig("glitch", 120f, 2.5f)),
        EffectPreset(
            "digital",
            "Digital",
            "💻",
            EffectKind.MOTION,
            MotionConfig("glitch", 100f, 3.0f)
        ),
        EffectPreset(
            "rgbSplit",
            "RGB Split",
            "🌈",
            EffectKind.MOTION,
            MotionConfig("glitch", 85f, 2.1f)
        ),
        EffectPreset(
            "pixel",
            "Pixel Glitch",
            "🟦",
            EffectKind.MOTION,
            MotionConfig("glitch", 80f, 2.8f)
        ),
        EffectPreset(
            "stutter",
            "Stutter",
            "⏸️",
            EffectKind.MOTION,
            MotionConfig("glitch", 140f, 4.0f)
        ),
        EffectPreset("tear", "Tear", "✂️", EffectKind.MOTION, MotionConfig("glitch", 110f, 3.2f)),
        EffectPreset("vhs", "VHS", "📼", EffectKind.MOTION, MotionConfig("glitch", 130f, 2.0f)),
        EffectPreset(
            "staticFx",
            "Static",
            "📻",
            EffectKind.MOTION,
            MotionConfig("glitch", 150f, 3.5f)
        ),
        EffectPreset(
            "signalLoss",
            "Signal Loss",
            "📵",
            EffectKind.MOTION,
            MotionConfig("glitch", 160f, 3.8f)
        ),

        // Flicker family
        EffectPreset(
            "flicker",
            "Flicker",
            "🕯️",
            EffectKind.MOTION,
            MotionConfig("glitch", 60f, 3.0f)
        ),
        EffectPreset("strobe", "Strobe", "💡", EffectKind.MOTION, MotionConfig("glitch", 90f, 5.0f)),
        EffectPreset(
            "flashFast",
            "Flash Fast",
            "⚡",
            EffectKind.MOTION,
            MotionConfig("glitch", 70f, 6.0f)
        ),
        EffectPreset("tv", "TV Static", "📺", EffectKind.MOTION, MotionConfig("glitch", 50f, 2.5f)),
        EffectPreset(
            "lightning",
            "Lightning",
            "🌩️",
            EffectKind.MOTION,
            MotionConfig("glitch", 120f, 4.2f)
        ),
        EffectPreset("blink", "Blink", "😉", EffectKind.MOTION, MotionConfig("glitch", 40f, 7.0f)),
        EffectPreset("spark", "Spark", "✨", EffectKind.MOTION, MotionConfig("glitch", 75f, 4.8f))
    )


    val ANIMATED_COLOR_EFFECTS: List<EffectPreset> = listOf(
        EffectPreset(
            "acHueCycle", "Hue Cycle", "🌈", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("hueCycle", 1.0f, 100f)
        ),
        EffectPreset(
            "acHueFast", "Hue Fast", "⚡", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("hueFast", 1.0f, 100f)
        ),
        EffectPreset(
            "acHueReverse", "Hue Reverse", "🔄", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("hueReverse", 1.0f, 100f)
        ),
        EffectPreset(
            "acRainbowFlow", "Rainbow Flow", "🌈", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("rainbowFlow", 1.0f, 100f)
        ),
        EffectPreset(
            "acSatPulse", "Sat Pulse", "🎨", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("satPulse", 1.0f, 100f)
        ),
        EffectPreset(
            "acSatFade", "Sat Fade", "🌫️", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("satFade", 1.0f, 100f)
        ),
        EffectPreset(
            "acBrightPulse", "Bright Pulse", "☀️", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("brightPulse", 1.0f, 100f)
        ),
        EffectPreset(
            "acBrightFlicker", "Bright Flicker", "🕯️", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("brightFlicker", 1.0f, 80f)
        ),
        EffectPreset(
            "acWarmCool", "Warm ↔ Cool", "🌡️", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("warmCool", 1.0f, 100f)
        ),
        EffectPreset(
            "acSunsetShift", "Sunset Shift", "🌅", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("sunsetShift", 1.0f, 100f)
        ),
        EffectPreset(
            "acColorStrobe", "Color Strobe", "💡", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("colorStrobe", 1.5f, 100f)
        ),
        EffectPreset(
            "acDisco", "Disco", "🕺", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("disco", 1.0f, 100f)
        ),
        EffectPreset(
            "acNeonPulse", "Neon Pulse", "💫", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("neonPulse", 1.5f, 100f)
        ),
        EffectPreset(
            "acChromaWave", "Chroma Wave", "🌊", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("chromaWave", 1.0f, 100f)
        ),
        EffectPreset(
            "acFireFlicker", "Fire Flicker", "🔥", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("fireFlicker", 2.0f, 100f)
        ),
        EffectPreset(
            "acIcePulse", "Ice Pulse", "🧊", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("icePulse", 1.0f, 100f)
        ),
        EffectPreset(
            "acCyberShift", "Cyber Shift", "🤖", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("cyberShift", 1.0f, 100f)
        ),
        EffectPreset(
            "acOceanWave", "Ocean Wave", "🌊", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("oceanWave", 0.8f, 100f)
        ),
        EffectPreset(
            "acHeartbeatColor", "Heartbeat", "❤️", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("heartbeatColor", 1.0f, 100f)
        ),
        EffectPreset(
            "acSepiaPulse", "Sepia Pulse", "📼", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("sepiaPulse", 1.0f, 100f)
        ),
        EffectPreset(
            "acInvertFlash", "Invert Flash", "◐", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("invertFlash", 1.0f, 100f)
        ),
        EffectPreset(
            "acVividBreath", "Vivid Breath", "🎆", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("vividBreath", 1.0f, 100f)
        ),
        EffectPreset(
            "acGoldShimmer", "Gold Shimmer", "✨", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("goldShimmer", 1.5f, 100f)
        ),
        EffectPreset(
            "acBloodPulse", "Blood Pulse", "🩸", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("bloodPulse", 1.0f, 100f)
        ),
        EffectPreset(
            "acPurpleHaze", "Purple Haze", "🟣", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("purpleHaze", 1.0f, 100f)
        ),
        EffectPreset(
            "acToxicGreen", "Toxic Green", "☢️", EffectKind.ANIMATED_COLOR,
            animatedColor = com.moody.moodyvideoeditor.utils.AnimatedColorConfig("toxicGreen", 1.5f, 100f)
        )
    )


    //  OVERLAY EFFECTS — mirrors effect.js overlay presets

    val OVERLAY_EFFECTS: List<EffectPreset> = listOf(
        // Particles
        EffectPreset("oRain", "Rain", "🌧️", EffectKind.OVERLAY, overlay = OverlayConfig("rain")),
        EffectPreset("oSnow", "Snow", "❄️", EffectKind.OVERLAY, overlay = OverlayConfig("snow")),
        EffectPreset("oDust", "Dust", "🌫️", EffectKind.OVERLAY, overlay = OverlayConfig("dust")),
        EffectPreset(
            "oSparks",
            "Sparks",
            "✨",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("sparks", color = 0xFFFFAA33)
        ),
        EffectPreset(
            "oEmbers",
            "Embers",
            "🔥",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("embers")
        ),
        EffectPreset("oStars", "Stars", "⭐", EffectKind.OVERLAY, overlay = OverlayConfig("stars")),
        EffectPreset(
            "oBokeh",
            "Bokeh",
            "🔮",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("bokeh", color = 0xFFFFD1FF)
        ),
        EffectPreset(
            "oFireFlies",
            "Fire Flies",
            "🪰",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("fireFlies")
        ),

        // Atmosphere
        EffectPreset(
            "oFog",
            "Fog",
            "🌁",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("fog", color = 0xFFAABBCC)
        ),
        EffectPreset("oSmoke", "Smoke", "💨", EffectKind.OVERLAY, overlay = OverlayConfig("smoke")),
        EffectPreset(
            "oHaze",
            "Haze",
            "☁️",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("haze", color = 0xFFDDEEFF)
        ),
        EffectPreset("oMist", "Mist", "🌊", EffectKind.OVERLAY, overlay = OverlayConfig("mist")),

        // Noise / Texture
        EffectPreset("oNoise", "Noise", "📡", EffectKind.OVERLAY, overlay = OverlayConfig("noise")),
        EffectPreset(
            "oFilmGrain",
            "Film Grain",
            "🎞️",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("filmGrain")
        ),
        EffectPreset(
            "oBlackNoise",
            "Black Noise",
            "⬛",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("blackNoise")
        ),
        EffectPreset(
            "oWhiteNoise",
            "White Noise",
            "⬜",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("whiteNoise")
        ),
        EffectPreset(
            "oScanlines",
            "Scanlines",
            "📺",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("scanlines")
        ),
        EffectPreset(
            "oStaticTV",
            "Static TV",
            "📻",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("staticTV")
        ),

        // Light
        EffectPreset(
            "oLightLeak",
            "Light Leak",
            "🌅",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("lightLeak")
        ),
        EffectPreset(
            "oLensFlare",
            "Lens Flare",
            "💡",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("lensFlare", color = 0xFFD0E0FF)
        ),
        EffectPreset("oBloom", "Bloom", "🌺", EffectKind.OVERLAY, overlay = OverlayConfig("bloom")),
        EffectPreset(
            "oSunburst",
            "Sunburst",
            "☀️",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("sunburst")
        ),
        EffectPreset(
            "oGodRays",
            "God Rays",
            "🌟",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("godRays")
        ),

        // Flicker
        EffectPreset(
            "oFlicker",
            "Flicker",
            "🕯️",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("flicker")
        ),
        EffectPreset(
            "oStrobe",
            "Strobe",
            "💡",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("strobe")
        ),
        EffectPreset(
            "oPulseFx",
            "Pulse FX",
            "💓",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("pulseFx")
        ),
        EffectPreset("oBlink", "Blink", "😉", EffectKind.OVERLAY, overlay = OverlayConfig("blink")),

        // Tone wash
        EffectPreset(
            "oBlueLake",
            "Blue Lake",
            "🌊",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("blueLake")
        ),
        EffectPreset(
            "oWarmWash",
            "Warm Wash",
            "🌅",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("warmWash")
        ),
        EffectPreset(
            "oCoolWash",
            "Cool Wash",
            "🧊",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("coolWash")
        ),
        EffectPreset(
            "oTealWash",
            "Teal Wash",
            "🟢",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("tealWash")
        ),
        EffectPreset(
            "oRoseWash",
            "Rose Wash",
            "🌸",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("roseWash")
        ),

        // Misc
        EffectPreset(
            "oVignette",
            "Vignette",
            "🕳️",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("vignette")
        ),
        EffectPreset(
            "oBlackBars",
            "Black Bars",
            "📺",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("blackBars")
        ),
        EffectPreset(
            "oVhsLines",
            "VHS Lines",
            "📼",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("vhsLines")
        ),
        EffectPreset(
            "oGlitchBars",
            "Glitch Bars",
            "⚡",
            EffectKind.OVERLAY,
            overlay = OverlayConfig("glitchBars")
        )
    )


    //  EDGE GLOW EFFECTS — 16 variants

    val EDGE_GLOW_EFFECTS = listOf(
        EffectPreset(
            key = "neonGlow",
            label = "Neon Glow",
            icon = "💡",
            kind = EffectKind.EDGE_GLOW,
            edgeGlow = EdgeGlowConfig(
                color = 0xFF00E5FF,
                radius = 30f,
                intensity = 100f,
                animation = EdgeGlowAnimation.STATIC,
                speed = 1.0f
            )
        ),
        EffectPreset(
            key = "rainbowPulse",
            label = "Rainbow Pulse",
            icon = "🌈",
            kind = EffectKind.EDGE_GLOW,
            edgeGlow = EdgeGlowConfig(
                color = 0xFFFF0066,
                color2 = 0xFF00FFCC,
                radius = 40f,
                intensity = 120f,
                animation = EdgeGlowAnimation.RAINBOW,
                speed = 1.5f
            )
        ),
        EffectPreset(
            "egGolden", "Golden Pulse", "🌟", EffectKind.EDGE_GLOW,
            edgeGlow = EdgeGlowConfig(
                color = 0xFFFFD700, radius = 28f,
                animation = EdgeGlowAnimation.PULSE, speed = 1.2f
            )
        ),
        EffectPreset(
            "egBreathe", "Breathe", "🌬️", EffectKind.EDGE_GLOW,
            edgeGlow = EdgeGlowConfig(
                color = 0xFF60EFFF, radius = 40f,
                animation = EdgeGlowAnimation.BREATHE, speed = 0.6f
            )
        ),
        EffectPreset(
            "egFlicker", "Candle Flicker", "🕯️", EffectKind.EDGE_GLOW,
            edgeGlow = EdgeGlowConfig(
                color = 0xFFFFAA33, radius = 25f,
                animation = EdgeGlowAnimation.FLICKER, speed = 3f
            )
        ),
        EffectPreset(
            "egStrobe", "Strobe", "💡", EffectKind.EDGE_GLOW,
            edgeGlow = EdgeGlowConfig(
                color = 0xFFFFFFFF, radius = 30f,
                intensity = 100f, animation = EdgeGlowAnimation.STROBE, speed = 4f
            )
        ),
        EffectPreset(
            "egRainbow", "Rainbow", "🌈", EffectKind.EDGE_GLOW,
            edgeGlow = EdgeGlowConfig(
                color = 0xFFFF0000, color2 = 0xFF00E5FF,
                radius = 25f, animation = EdgeGlowAnimation.RAINBOW, speed = 1.2f
            )
        ),
        EffectPreset(
            "egAurora", "Aurora", "🌌", EffectKind.EDGE_GLOW,
            edgeGlow = EdgeGlowConfig(
                color = 0xFF00FFAA, color2 = 0xFF7C3AED,
                radius = 45f, animation = EdgeGlowAnimation.AURORA, speed = 0.7f
            )
        ),
        EffectPreset(
            "egWave", "Wave", "🌊", EffectKind.EDGE_GLOW,
            edgeGlow = EdgeGlowConfig(
                color = 0xFF00E5FF, radius = 25f,
                animation = EdgeGlowAnimation.WAVE, speed = 2f
            )
        ),
        EffectPreset(
            "egSpark", "Spark", "⚡", EffectKind.EDGE_GLOW,
            edgeGlow = EdgeGlowConfig(
                color = 0xFFFFEE66, radius = 22f,
                animation = EdgeGlowAnimation.SPARK, speed = 4f
            )
        ),
        EffectPreset(
            "egFire", "Fire", "🔥", EffectKind.EDGE_GLOW,
            edgeGlow = EdgeGlowConfig(
                color = 0xFFFF6600, radius = 35f,
                intensity = 95f, animation = EdgeGlowAnimation.FIRE, speed = 2.5f
            )
        ),
        EffectPreset(
            "egElectric", "Electric", "⚡", EffectKind.EDGE_GLOW,
            edgeGlow = EdgeGlowConfig(
                color = 0xFF66CCFF, radius = 28f,
                animation = EdgeGlowAnimation.ELECTRIC, speed = 3.5f
            )
        ),
        EffectPreset(
            "egNeonCyan", "Neon Cyan", "💠", EffectKind.EDGE_GLOW,
            edgeGlow = EdgeGlowConfig(
                color = 0xFF00FFFF, radius = 25f,
                intensity = 100f, animation = EdgeGlowAnimation.NEON, speed = 1f
            )
        ),
        EffectPreset(
            "egNeonPink", "Neon Pink", "💗", EffectKind.EDGE_GLOW,
            edgeGlow = EdgeGlowConfig(
                color = 0xFFFF00AA, radius = 25f,
                intensity = 100f, animation = EdgeGlowAnimation.NEON, speed = 1f
            )
        ),
        EffectPreset(
            "egGhost", "Ghost", "👻", EffectKind.EDGE_GLOW,
            edgeGlow = EdgeGlowConfig(
                color = 0xFFCCCCFF, radius = 45f,
                intensity = 70f, animation = EdgeGlowAnimation.GHOST, speed = 1f
            )
        ),
        EffectPreset(
            "egGlitch", "Glitch", "📺", EffectKind.EDGE_GLOW,
            edgeGlow = EdgeGlowConfig(
                color = 0xFFFF0066, radius = 22f,
                animation = EdgeGlowAnimation.GLITCH, speed = 4f
            )
        )
    )


    // ═══════════════════════════════════════════════════════════
    //  🆕 LIGHT LEAK EFFECTS — built from separate LightLeaks.kt file
    //  Presets change karne ho toh sirf LightLeaks.kt edit karo
    // ═══════════════════════════════════════════════════════════

    val LIGHT_LEAK_EFFECTS: List<EffectPreset> =
        LightLeakLibrary.PRESETS.map { p ->
            EffectPreset(
                key = p.key,
                label = p.label,
                icon = p.icon,
                kind = EffectKind.LIGHT_LEAK,
                lightLeak = p.config
            )
        }


    //  ALL EFFECTS — combined

    val ALL: List<EffectPreset> = MOTION_EFFECTS + ANIMATED_COLOR_EFFECTS +
            OVERLAY_EFFECTS + EDGE_GLOW_EFFECTS + LIGHT_LEAK_EFFECTS

    fun findByKey(key: String): EffectPreset? = ALL.firstOrNull { it.key == key }
}
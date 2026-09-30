package com.moody.moodyvideoeditor.data

/**
 * Mirrors js/features/effect.js PRESETS array.
 * Each preset has a kind: motion | color | overlay.
 */
data class EffectPreset(
    val key: String,
    val label: String,
    val icon: String,
    val kind: EffectKind,
    val motion: MotionConfig? = null,
    val filters: ColorFilterValues? = null,
    val overlay: OverlayConfig? = null
)

enum class EffectKind { MOTION, COLOR, OVERLAY }

/** Mirrors MOTION_MAP values from beatsEngine.js */
data class MotionConfig(
    val type: String,       // shake | bounce | pulse | zoomPulse | rotate | glitch
    val intensity: Float,   // 40..220
    val speed: Float        // 0.4..7.0
)

/** Mirrors effect.js filter values (subset of full FilterState — only what presets use) */
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

/** Mirrors OVERLAY presets in effect.js */
data class OverlayConfig(
    val type: String,       // rain | snow | fog | lightLeak | ...
    val intensity: Float = 100f,
    val color: Long = 0xFFFFFFFF
)

object EffectLibrary {

    // ═══════════════════════════════════════════════════════════
    //  MOTION EFFECTS — mirrors effect.js motion presets
    // ═══════════════════════════════════════════════════════════
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

    // ═══════════════════════════════════════════════════════════
    //  COLOR EFFECTS — mirrors effect.js color presets
    // ═══════════════════════════════════════════════════════════
    val COLOR_EFFECTS: List<EffectPreset> = listOf(
        // Original
        EffectPreset(
            "warm",
            "Warm Glow",
            "🌅",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 108f, contrast = 105f, saturation = 115f)
        ),
        EffectPreset(
            "cool",
            "Cool Blue",
            "❄️",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 100f, contrast = 108f, saturation = 95f)
        ),
        EffectPreset(
            "vintage",
            "Vintage",
            "📼",
            EffectKind.COLOR,
            filters = ColorFilterValues(
                brightness = 98f,
                contrast = 92f,
                saturation = 80f,
                sepia = 25f
            )
        ),
        EffectPreset(
            "cinematic",
            "Cinematic",
            "🎬",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 98f, contrast = 118f, saturation = 90f)
        ),
        EffectPreset(
            "bw",
            "Black & White",
            "⚫",
            EffectKind.COLOR,
            filters = ColorFilterValues(grayscale = 100f, contrast = 110f)
        ),
        EffectPreset(
            "dreamy",
            "Dreamy",
            "💭",
            EffectKind.COLOR,
            filters = ColorFilterValues(
                brightness = 105f,
                contrast = 92f,
                saturation = 105f,
                blur = 0.6f
            )
        ),
        EffectPreset(
            "vivid",
            "Vivid",
            "🎨",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 102f, contrast = 112f, saturation = 145f)
        ),
        EffectPreset(
            "faded",
            "Faded",
            "🌫️",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 108f, contrast = 82f, saturation = 75f)
        ),
        EffectPreset(
            "dramatic",
            "Dramatic",
            "🎭",
            EffectKind.COLOR,
            filters = ColorFilterValues(contrast = 130f, saturation = 110f)
        ),
        EffectPreset(
            "negative",
            "Negative",
            "🔄",
            EffectKind.COLOR,
            filters = ColorFilterValues(invert = 100f)
        ),
        EffectPreset(
            "softGlow",
            "Soft Glow",
            "💫",
            EffectKind.COLOR,
            filters = ColorFilterValues(
                brightness = 112f,
                contrast = 95f,
                saturation = 108f,
                blur = 0.4f
            )
        ),
        EffectPreset(
            "noir",
            "Noir",
            "🖤",
            EffectKind.COLOR,
            filters = ColorFilterValues(grayscale = 100f, contrast = 135f, brightness = 92f)
        ),

        // Cinematic
        EffectPreset(
            "tealOrange",
            "Teal & Orange",
            "🟠",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 100f, contrast = 115f, saturation = 110f)
        ),
        EffectPreset(
            "hollywood",
            "Hollywood",
            "🌟",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 98f, contrast = 120f, saturation = 105f)
        ),
        EffectPreset(
            "blockbuster",
            "Blockbuster",
            "🎥",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 102f, contrast = 118f, saturation = 115f)
        ),
        EffectPreset(
            "filmLook",
            "Film Look",
            "🎞️",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 96f, contrast = 116f, saturation = 92f)
        ),
        EffectPreset(
            "drama",
            "Drama",
            "🎭",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 92f, contrast = 128f, saturation = 88f)
        ),
        EffectPreset(
            "epic",
            "Epic",
            "⚔️",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 100f, contrast = 125f, saturation = 110f)
        ),
        EffectPreset(
            "thriller",
            "Thriller",
            "🔪",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 88f, contrast = 135f, saturation = 85f)
        ),

        // Film / Vintage
        EffectPreset(
            "bleach",
            "Bleach",
            "🧴",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 105f, contrast = 140f, saturation = 55f)
        ),
        EffectPreset(
            "bleachBypass",
            "Bleach Bypass",
            "⚪",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 102f, contrast = 145f, saturation = 50f)
        ),
        EffectPreset(
            "sepiaMem",
            "Sepia Memory",
            "🟤",
            EffectKind.COLOR,
            filters = ColorFilterValues(
                brightness = 100f,
                contrast = 100f,
                saturation = 65f,
                sepia = 55f
            )
        ),
        EffectPreset(
            "sepiaDeep",
            "Sepia Deep",
            "🟫",
            EffectKind.COLOR,
            filters = ColorFilterValues(
                brightness = 95f,
                contrast = 105f,
                saturation = 60f,
                sepia = 75f
            )
        ),
        EffectPreset(
            "retro8mm",
            "Retro 8mm",
            "📽️",
            EffectKind.COLOR,
            filters = ColorFilterValues(
                brightness = 102f,
                contrast = 108f,
                saturation = 75f,
                sepia = 30f
            )
        ),
        EffectPreset(
            "kodak",
            "Kodak Film",
            "📷",
            EffectKind.COLOR,
            filters = ColorFilterValues(
                brightness = 102f,
                contrast = 110f,
                saturation = 118f,
                sepia = 8f
            )
        ),
        EffectPreset(
            "polaroid",
            "Polaroid",
            "🖼️",
            EffectKind.COLOR,
            filters = ColorFilterValues(
                brightness = 105f,
                contrast = 95f,
                saturation = 90f,
                sepia = 15f
            )
        ),
        EffectPreset(
            "oldFilm",
            "Old Film",
            "🎞️",
            EffectKind.COLOR,
            filters = ColorFilterValues(
                brightness = 95f,
                contrast = 108f,
                saturation = 70f,
                sepia = 40f
            )
        ),
        EffectPreset(
            "antique",
            "Antique",
            "🏛️",
            EffectKind.COLOR,
            filters = ColorFilterValues(
                brightness = 92f,
                contrast = 105f,
                saturation = 75f,
                sepia = 60f
            )
        ),

        // Monochrome
        EffectPreset(
            "monochrome",
            "Monochrome",
            "⬛",
            EffectKind.COLOR,
            filters = ColorFilterValues(grayscale = 100f, contrast = 120f)
        ),
        EffectPreset(
            "graySoft",
            "Gray Soft",
            "🌫️",
            EffectKind.COLOR,
            filters = ColorFilterValues(grayscale = 100f, contrast = 90f, brightness = 105f)
        ),
        EffectPreset(
            "grayHard",
            "Gray Hard",
            "⬜",
            EffectKind.COLOR,
            filters = ColorFilterValues(grayscale = 100f, contrast = 145f)
        ),
        EffectPreset(
            "inkwell",
            "Inkwell",
            "🖋️",
            EffectKind.COLOR,
            filters = ColorFilterValues(grayscale = 100f, contrast = 160f, brightness = 90f)
        ),
        EffectPreset(
            "filmNoir",
            "Film Noir",
            "🌑",
            EffectKind.COLOR,
            filters = ColorFilterValues(grayscale = 100f, contrast = 155f, brightness = 88f)
        ),

        // Neon / Cyberpunk
        EffectPreset(
            "cyberpunk",
            "Cyberpunk",
            "🤖",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 105f, contrast = 120f, saturation = 170f)
        ),
        EffectPreset(
            "vaporwave",
            "Vaporwave",
            "🌆",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 102f, contrast = 108f, saturation = 165f)
        ),
        EffectPreset(
            "synthwave",
            "Synthwave",
            "🎹",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 100f, contrast = 125f, saturation = 155f)
        ),
        EffectPreset(
            "plasma",
            "Plasma",
            "🔥",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 108f, contrast = 115f, saturation = 175f)
        ),
        EffectPreset(
            "electric",
            "Electric",
            "⚡",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 105f, contrast = 122f, saturation = 180f)
        ),
        EffectPreset(
            "techno",
            "Techno",
            "🎛️",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 100f, contrast = 130f, saturation = 165f)
        ),
        EffectPreset(
            "neonCity",
            "Neon City",
            "🌃",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 102f, contrast = 118f, saturation = 175f)
        ),
        EffectPreset(
            "retrowave",
            "Retrowave",
            "🕹️",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 100f, contrast = 112f, saturation = 160f)
        ),

        // Warm
        EffectPreset(
            "gold",
            "Gold",
            "🟡",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 108f, contrast = 108f, saturation = 130f)
        ),
        EffectPreset(
            "sunrise",
            "Sunrise",
            "🌅",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 110f, contrast = 100f, saturation = 125f)
        ),
        EffectPreset(
            "sunset",
            "Sunset",
            "🌇",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 105f, contrast = 105f, saturation = 140f)
        ),
        EffectPreset(
            "goldenHour",
            "Golden Hour",
            "⏰",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 112f, contrast = 102f, saturation = 135f)
        ),
        EffectPreset(
            "amber",
            "Amber",
            "🟠",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 105f, contrast = 105f, saturation = 125f)
        ),
        EffectPreset(
            "ember",
            "Ember",
            "🔥",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 100f, contrast = 115f, saturation = 135f)
        ),
        EffectPreset(
            "copper",
            "Copper",
            "🟤",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 102f, contrast = 108f, saturation = 128f)
        ),
        EffectPreset(
            "autumn",
            "Autumn",
            "🍂",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 100f, contrast = 105f, saturation = 140f)
        ),

        // Cool
        EffectPreset(
            "moonlight",
            "Moonlight",
            "🌙",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 95f, contrast = 110f, saturation = 90f)
        ),
        EffectPreset(
            "midnight",
            "Midnight",
            "🌌",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 85f, contrast = 120f, saturation = 95f)
        ),
        EffectPreset(
            "ice",
            "Ice",
            "🧊",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 110f, contrast = 105f, saturation = 100f)
        ),
        EffectPreset(
            "frost",
            "Frost",
            "❄️",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 108f, contrast = 108f, saturation = 95f)
        ),
        EffectPreset(
            "ocean",
            "Ocean",
            "🌊",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 100f, contrast = 110f, saturation = 120f)
        ),
        EffectPreset(
            "sky",
            "Sky",
            "☁️",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 105f, contrast = 100f, saturation = 115f)
        ),
        EffectPreset(
            "deepBlue",
            "Deep Blue",
            "🔵",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 90f, contrast = 115f, saturation = 130f)
        ),

        // Moody
        EffectPreset(
            "moody",
            "Moody",
            "🌑",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 90f, contrast = 118f, saturation = 85f)
        ),
        EffectPreset(
            "darkDrama",
            "Dark Drama",
            "🎬",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 85f, contrast = 130f, saturation = 80f)
        ),
        EffectPreset(
            "grunge",
            "Grunge",
            "🖤",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 95f, contrast = 135f, saturation = 70f)
        ),
        EffectPreset(
            "gritty",
            "Gritty",
            "⚫",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 92f, contrast = 132f, saturation = 88f)
        ),
        EffectPreset(
            "somber",
            "Somber",
            "🌧️",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 88f, contrast = 115f, saturation = 75f)
        ),

        // Special FX
        EffectPreset(
            "infrared",
            "Infrared",
            "🟥",
            EffectKind.COLOR,
            filters = ColorFilterValues(
                brightness = 105f,
                contrast = 115f,
                saturation = 160f,
                invert = 20f
            )
        ),
        EffectPreset(
            "matrix",
            "Matrix",
            "🟢",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 95f, contrast = 120f, saturation = 130f)
        ),
        EffectPreset(
            "thermal",
            "Thermal",
            "🌡️",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 108f, contrast = 125f, saturation = 180f)
        ),
        EffectPreset(
            "xray",
            "X-Ray",
            "🩻",
            EffectKind.COLOR,
            filters = ColorFilterValues(
                brightness = 100f,
                contrast = 150f,
                saturation = 10f,
                invert = 30f
            )
        ),
        EffectPreset(
            "duotone",
            "Duotone",
            "🎨",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 100f, contrast = 125f, saturation = 110f)
        ),
        EffectPreset(
            "spectrum",
            "Spectrum",
            "🌈",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 105f, contrast = 115f, saturation = 175f)
        ),
        EffectPreset(
            "hyperSat",
            "Hyper Sat",
            "🎆",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 102f, contrast = 108f, saturation = 190f)
        ),

        // Soft / Dreamy
        EffectPreset(
            "softFocus",
            "Soft Focus",
            "💫",
            EffectKind.COLOR,
            filters = ColorFilterValues(
                brightness = 108f,
                contrast = 95f,
                saturation = 108f,
                blur = 0.8f
            )
        ),
        EffectPreset(
            "pastel",
            "Pastel",
            "🌸",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 112f, contrast = 88f, saturation = 95f)
        ),
        EffectPreset(
            "creamy",
            "Creamy",
            "🍦",
            EffectKind.COLOR,
            filters = ColorFilterValues(
                brightness = 108f,
                contrast = 92f,
                saturation = 100f,
                sepia = 10f
            )
        ),
        EffectPreset(
            "haze",
            "Haze",
            "🌁",
            EffectKind.COLOR,
            filters = ColorFilterValues(
                brightness = 110f,
                contrast = 85f,
                saturation = 100f,
                blur = 0.7f
            )
        ),
        EffectPreset(
            "bloom",
            "Bloom",
            "🌺",
            EffectKind.COLOR,
            filters = ColorFilterValues(
                brightness = 118f,
                contrast = 95f,
                saturation = 115f,
                blur = 0.4f
            )
        ),
        EffectPreset(
            "ethereal",
            "Ethereal",
            "👻",
            EffectKind.COLOR,
            filters = ColorFilterValues(
                brightness = 112f,
                contrast = 90f,
                saturation = 115f,
                blur = 0.8f
            )
        ),

        // HDR
        EffectPreset(
            "hdr",
            "HDR",
            "🔆",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 105f, contrast = 135f, saturation = 125f)
        ),
        EffectPreset(
            "punchy",
            "Punchy",
            "👊",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 100f, contrast = 130f, saturation = 140f)
        ),
        EffectPreset(
            "dynamic",
            "Dynamic",
            "💥",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 105f, contrast = 128f, saturation = 130f)
        ),
        EffectPreset(
            "vividHard",
            "Vivid Hard",
            "🎨",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 102f, contrast = 120f, saturation = 170f)
        ),
        EffectPreset(
            "contrastMax",
            "Contrast Max",
            "◐",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 95f, contrast = 155f, saturation = 120f)
        ),

        // Sepia
        EffectPreset(
            "sepia",
            "Sepia",
            "🟫",
            EffectKind.COLOR,
            filters = ColorFilterValues(sepia = 100f)
        ),
        EffectPreset(
            "sepiaWarm",
            "Sepia Warm",
            "🟤",
            EffectKind.COLOR,
            filters = ColorFilterValues(sepia = 75f, brightness = 103f, contrast = 105f)
        ),
        EffectPreset(
            "brownTone",
            "Brown Tone",
            "🍫",
            EffectKind.COLOR,
            filters = ColorFilterValues(sepia = 85f, brightness = 98f, contrast = 108f)
        ),
        EffectPreset(
            "coffee",
            "Coffee",
            "☕",
            EffectKind.COLOR,
            filters = ColorFilterValues(sepia = 60f, brightness = 95f, contrast = 110f)
        ),

        // Flash / Light
        EffectPreset(
            "flashWhite",
            "Flash White",
            "⚪",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 200f, contrast = 100f, saturation = 100f)
        ),
        EffectPreset(
            "flashSoft",
            "Flash Soft",
            "🔆",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 150f, contrast = 108f, saturation = 110f)
        ),
        EffectPreset(
            "lightBurst",
            "Light Burst",
            "💡",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 175f, contrast = 105f, saturation = 120f)
        ),
        EffectPreset(
            "overexpose",
            "Overexpose",
            "☀️",
            EffectKind.COLOR,
            filters = ColorFilterValues(brightness = 165f, contrast = 95f, saturation = 105f)
        )
    )

    // ═══════════════════════════════════════════════════════════
    //  OVERLAY EFFECTS — mirrors effect.js overlay presets
    // ═══════════════════════════════════════════════════════════
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

    val ALL: List<EffectPreset> = MOTION_EFFECTS + COLOR_EFFECTS + OVERLAY_EFFECTS

    fun findByKey(key: String): EffectPreset? = ALL.firstOrNull { it.key == key }
}
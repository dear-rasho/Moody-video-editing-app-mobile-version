package com.moody.moodyvideoeditor.data

/**
 * Mirrors js/features/overlayRenderer.js — 35+ overlays, categorized.
 */
data class OverlayState(
    val type: String = "none",       // e.g. "rain", "snow", "fog"
    val intensity: Float = 100f,     // 0..200
    val color: Long = 0xFFFFFFFF     // tint / particle color
) {
    val isActive: Boolean get() = type != "none"
}

object OverlayLibrary {

    data class Preset(
        val key: String,
        val label: String,
        val icon: String,
        val defaultColor: Long = 0xFFFFFFFF,
        val defaultIntensity: Float = 100f
    )

    data class Category(
        val key: String,
        val label: String,
        val presets: List<Preset>
    )

    /**
     * Mirrors overlayRenderer.js switch cases grouped by section.
     */
    val CATEGORIES: List<Category> = listOf(
        Category(
            "particles", "Particles", listOf(
                Preset("rain", "Rain", "🌧️"),
                Preset("snow", "Snow", "❄️"),
                Preset("dust", "Dust", "🌫️"),
                Preset("sparks", "Sparks", "✨", 0xFFFFAA33),
                Preset("embers", "Embers", "🔥"),
                Preset("stars", "Stars", "⭐"),
                Preset("bokeh", "Bokeh", "🔮", 0xFFFFD1FF),
                Preset("fireFlies", "Fire Flies", "🪰")
            )
        ),

        Category(
            "atmosphere", "Atmosphere", listOf(
                Preset("fog", "Fog", "🌁", 0xFFAABBCC),
                Preset("smoke", "Smoke", "💨"),
                Preset("haze", "Haze", "☁️", 0xFFDDEEFF),
                Preset("mist", "Mist", "🌊", 0xFFFFFFFF)
            )
        ),

        Category(
            "noise", "Noise & Texture", listOf(
                Preset("noise", "Noise", "📡"),
                Preset("filmGrain", "Film Grain", "🎞️"),
                Preset("blackNoise", "Black Noise", "⬛"),
                Preset("whiteNoise", "White Noise", "⬜"),
                Preset("scanlines", "Scanlines", "📺"),
                Preset("staticTV", "Static TV", "📻")
            )
        ),

        Category(
            "light", "Light", listOf(
                Preset("lightLeak", "Light Leak", "🌅"),
                Preset("lensFlare", "Lens Flare", "💡", 0xFFD0E0FF),
                Preset("bloom", "Bloom", "🌺"),
                Preset("sunburst", "Sunburst", "☀️"),
                Preset("godRays", "God Rays", "🌟")
            )
        ),

        Category(
            "flicker", "Flicker", listOf(
                Preset("flicker", "Flicker", "🕯️"),
                Preset("strobe", "Strobe", "💡"),
                Preset("pulseFx", "Pulse FX", "💓"),
                Preset("blink", "Blink", "😉")
            )
        ),

        Category(
            "wash", "Tone Wash", listOf(
                Preset("blueLake", "Blue Lake", "🌊"),
                Preset("warmWash", "Warm Wash", "🌅"),
                Preset("coolWash", "Cool Wash", "🧊"),
                Preset("tealWash", "Teal Wash", "🟢"),
                Preset("roseWash", "Rose Wash", "🌸")
            )
        ),

        Category(
            "edges", "Edges", listOf(
                Preset("sharpenEdges", "Sharpen Edges", "🔪"),
                Preset("edgeGlow", "Edge Glow", "✨", 0xFF00FFFF)
            )
        ),

        Category(
            "misc", "Misc", listOf(
                Preset("vignette", "Vignette", "🕳️"),
                Preset("blackBars", "Black Bars", "📺"),
                Preset("vhsLines", "VHS Lines", "📼"),
                Preset("glitchBars", "Glitch Bars", "⚡")
            )
        )
    )

    val ALL_PRESETS: List<Preset> = CATEGORIES.flatMap { it.presets }

    fun findPreset(key: String): Preset? = ALL_PRESETS.firstOrNull { it.key == key }
}
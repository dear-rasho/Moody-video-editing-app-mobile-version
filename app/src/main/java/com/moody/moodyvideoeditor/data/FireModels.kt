package com.moody.moodyvideoeditor.data

// ═══════════════════════════════════════════════════════════════
//  🔥 FIRE & THERMAL
// ═══════════════════════════════════════════════════════════════

enum class FireStyle {
    FIRE_BOTTOM, FIRE_TOP, LAVA_FLOW, ASH_FALL, EMBER_RISE,
    SMOKE_DRIFT, HEAT_HAZE, FLAME_BORDER, PLASMA_FIRE, BLUE_FLAME
}

data class FireConfig(
    val style: FireStyle,

    // Intensity
    val intensity: Float = 100f,       // 0..200

    // Flame shape
    val flameHeight: Float = 0.35f,    // 0.1..1 — fraction of screen
    val waviness: Float = 0.5f,        // 0..1 — how wavy the flames are
    val density: Int = 12,             // 4..40 flame columns

    // Colors (3-stop gradient: hot → mid → cool)
    val colorHot: Long = 0xFFFFEE88,   // white/yellow base
    val colorMid: Long = 0xFFFF6600,   // orange middle
    val colorCool: Long = 0xFFAA0000,  // red top

    // Particles
    val particleCount: Int = 60,       // for embers/ash/smoke
    val particleSpeed: Float = 80f,

    // Timing
    val speed: Float = 1f,             // 0.2..5 animation speed

    val masterIntensity: Float = 100f
)

data class FirePreset(
    val key: String,
    val label: String,
    val icon: String,
    val config: FireConfig
)

object FireLibrary {

    val PRESETS: List<FirePreset> = listOf(

        FirePreset(
            "fireBottom", "Fire Bottom", "🔥",
            FireConfig(
                style = FireStyle.FIRE_BOTTOM,
                flameHeight = 0.35f, waviness = 0.55f, density = 14,
                colorHot = 0xFFFFEE88, colorMid = 0xFFFF6600,
                colorCool = 0xFFAA0000,
                particleCount = 60, particleSpeed = 90f
            )
        ),
        FirePreset(
            "fireTop", "Fire Top", "🔥",
            FireConfig(
                style = FireStyle.FIRE_TOP,
                flameHeight = 0.35f, waviness = 0.55f, density = 14,
                colorHot = 0xFFFFEE88, colorMid = 0xFFFF6600,
                colorCool = 0xFFAA0000,
                particleCount = 60, particleSpeed = 90f
            )
        ),
        FirePreset(
            "lavaFlow", "Lava Flow", "🌋",
            FireConfig(
                style = FireStyle.LAVA_FLOW,
                flameHeight = 0.45f, waviness = 0.75f, density = 18,
                colorHot = 0xFFFFCC00, colorMid = 0xFFFF3300,
                colorCool = 0xFF660000,
                particleCount = 40, particleSpeed = 50f
            )
        ),
        FirePreset(
            "ashFall", "Ash Fall", "🌫️",
            FireConfig(
                style = FireStyle.ASH_FALL,
                flameHeight = 0f, waviness = 0f, density = 0,
                colorHot = 0xFFBBBBBB, colorMid = 0xFF888888,
                colorCool = 0xFF444444,
                particleCount = 150, particleSpeed = 100f
            )
        ),
        FirePreset(
            "emberRise", "Ember Rise", "✨",
            FireConfig(
                style = FireStyle.EMBER_RISE,
                flameHeight = 0f, waviness = 0f, density = 0,
                colorHot = 0xFFFFEE88, colorMid = 0xFFFF6600,
                colorCool = 0xFFAA0000,
                particleCount = 100, particleSpeed = 70f
            )
        ),
        FirePreset(
            "smokeDrift", "Smoke Drift", "💨",
            FireConfig(
                style = FireStyle.SMOKE_DRIFT,
                flameHeight = 0f, waviness = 0f, density = 0,
                colorHot = 0xFFAAAAAA, colorMid = 0xFF666666,
                colorCool = 0xFF333333,
                particleCount = 80, particleSpeed = 40f
            )
        ),
        FirePreset(
            "heatHaze", "Heat Haze", "🌡️",
            FireConfig(
                style = FireStyle.HEAT_HAZE,
                flameHeight = 0.6f, waviness = 0.9f, density = 8,
                colorHot = 0xFFFFCC88, colorMid = 0xFFFF8844,
                colorCool = 0xFF884400,
                particleCount = 30, particleSpeed = 40f,
                intensity = 70f
            )
        ),
        FirePreset(
            "flameBorder", "Flame Border", "🔥",
            FireConfig(
                style = FireStyle.FLAME_BORDER,
                flameHeight = 0.25f, waviness = 0.65f, density = 24,
                colorHot = 0xFFFFEE88, colorMid = 0xFFFF6600,
                colorCool = 0xFFAA0000,
                particleCount = 50, particleSpeed = 80f
            )
        ),
        FirePreset(
            "plasmaFire", "Plasma Fire", "⚡",
            FireConfig(
                style = FireStyle.PLASMA_FIRE,
                flameHeight = 0.4f, waviness = 0.8f, density = 16,
                colorHot = 0xFFFF88FF, colorMid = 0xFFAA00FF,
                colorCool = 0xFF4400AA,
                particleCount = 70, particleSpeed = 100f
            )
        ),
        FirePreset(
            "blueFlame", "Blue Flame", "💙",
            FireConfig(
                style = FireStyle.BLUE_FLAME,
                flameHeight = 0.35f, waviness = 0.6f, density = 14,
                colorHot = 0xFFEEFFFF, colorMid = 0xFF00AAFF,
                colorCool = 0xFF001188,
                particleCount = 60, particleSpeed = 90f
            )
        )
    )

    fun find(key: String): FirePreset? = PRESETS.firstOrNull { it.key == key }
    fun all(): List<FirePreset> = PRESETS
}
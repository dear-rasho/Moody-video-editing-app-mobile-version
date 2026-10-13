package com.moody.moodyvideoeditor.data

// ═══════════════════════════════════════════════════════════════
//  🌀 DUST EFFECTS
// ═══════════════════════════════════════════════════════════════

enum class DustStyle {
    DUST_MOTES, DUST_STORM, SAND_STORM, CHALK_DUST, GOLDEN_DUST,
    ASH_DUST, IMPACT_DUST, FLOOR_DUST, DUST_TRAIL, COSMIC_DUST
}

data class DustConfig(
    val style: DustStyle,

    // Intensity
    val intensity: Float = 100f,        // 0..200
    val masterIntensity: Float = 100f,

    // Emission
    val count: Int = 100,               // 1..5000
    val speed: Float = 40f,             // 10..400
    val spread: Float = 360f,           // 10..360 degrees
    val direction: Float = -90f,        // -90 up, 90 down
    val gravity: Float = 0f,            // -200..200

    // Appearance
    val sizeMin: Float = 2f,            // 0.5..50
    val sizeMax: Float = 10f,           // 0.5..200
    val color1: Long = 0xFFCCAA88,
    val color2: Long = 0xFF886644,
    val glow: Boolean = false,
    val fadeOut: Boolean = true,
    val softness: Float = 1f,           // 0.3..3 — blur multiplier

    // Timing
    val lifetimeMs: Long = 2500L,       // 500..8000
    val burstDurationMs: Long = 800L,
    val triggerEverySec: Float = 0f,    // 0 = one-shot; >0 = repeat
    val triggerX: Float = 0.5f,
    val triggerY: Float = 0.5f,

    // Style-specific
    val windX: Float = 30f,             // for storms
    val floorHeight: Float = 0.25f,     // for floor dust
    val opacity: Float = 1f             // 0..1
)

data class DustPreset(
    val key: String,
    val label: String,
    val icon: String,
    val config: DustConfig
)

object DustLibrary {

    val PRESETS: List<DustPreset> = listOf(

        DustPreset(
            "dustMotes", "Dust Motes", "✦",
            DustConfig(
                style = DustStyle.DUST_MOTES,
                count = 80, speed = 25f, spread = 360f,
                sizeMin = 1.5f, sizeMax = 5f,
                color1 = 0xFFFFEECC, color2 = 0xFFCCAA88,
                gravity = -5f, lifetimeMs = 4000L,
                burstDurationMs = 3000L,
                triggerEverySec = 5f,
                glow = true, softness = 1.4f
            )
        ),
        DustPreset(
            "dustStorm", "Dust Storm", "🌪️",
            DustConfig(
                style = DustStyle.DUST_STORM,
                count = 400, speed = 180f, spread = 60f,
                direction = 0f, windX = 180f,
                sizeMin = 2f, sizeMax = 8f,
                color1 = 0xFFCC9977, color2 = 0xFF886644,
                gravity = 20f, lifetimeMs = 3000L,
                burstDurationMs = 2500L,
                triggerEverySec = 0f,
                softness = 1.6f, opacity = 0.75f
            )
        ),
        DustPreset(
            "sandStorm", "Sand Storm", "🏜️",
            DustConfig(
                style = DustStyle.SAND_STORM,
                count = 500, speed = 200f, spread = 90f,
                direction = 0f, windX = 200f,
                sizeMin = 1.5f, sizeMax = 6f,
                color1 = 0xFFFFCC88, color2 = 0xFFCC8844,
                gravity = 30f, lifetimeMs = 3500L,
                burstDurationMs = 3000L,
                softness = 1.2f, opacity = 0.85f
            )
        ),
        DustPreset(
            "chalkDust", "Chalk Dust", "🪨",
            DustConfig(
                style = DustStyle.CHALK_DUST,
                count = 200, speed = 120f, spread = 360f,
                sizeMin = 2f, sizeMax = 10f,
                color1 = 0xFFFFFFFF, color2 = 0xFFDDDDDD,
                gravity = 60f, lifetimeMs = 1800L,
                burstDurationMs = 300L,
                triggerEverySec = 2.5f,
                softness = 1.0f
            )
        ),
        DustPreset(
            "goldenDust", "Golden Dust", "✨",
            DustConfig(
                style = DustStyle.GOLDEN_DUST,
                count = 120, speed = 60f, spread = 360f,
                sizeMin = 2f, sizeMax = 7f,
                color1 = 0xFFFFDD66, color2 = 0xFFFF8800,
                gravity = -10f, lifetimeMs = 3000L,
                burstDurationMs = 2000L,
                triggerEverySec = 3f,
                glow = true, softness = 1.5f
            )
        ),
        DustPreset(
            "ashDust", "Ash Dust", "🌫️",
            DustConfig(
                style = DustStyle.ASH_DUST,
                count = 250, speed = 50f, spread = 360f,
                sizeMin = 1.5f, sizeMax = 5f,
                color1 = 0xFFAAAAAA, color2 = 0xFF555555,
                gravity = 40f, lifetimeMs = 4000L,
                burstDurationMs = 3000L,
                triggerEverySec = 4f,
                softness = 1.8f, opacity = 0.8f
            )
        ),
        DustPreset(
            "impactDust", "Impact Dust", "💥",
            DustConfig(
                style = DustStyle.IMPACT_DUST,
                count = 200, speed = 250f, spread = 180f,
                direction = -60f,
                sizeMin = 3f, sizeMax = 12f,
                color1 = 0xFFEEDDCC, color2 = 0xFF886644,
                gravity = 150f, lifetimeMs = 1500L,
                burstDurationMs = 200L,
                triggerEverySec = 0f,
                softness = 1.3f, opacity = 0.9f
            )
        ),
        DustPreset(
            "floorDust", "Floor Dust", "🌾",
            DustConfig(
                style = DustStyle.FLOOR_DUST,
                count = 150, speed = 30f, spread = 360f,
                sizeMin = 3f, sizeMax = 12f,
                color1 = 0xFFCCAA88, color2 = 0xFF886644,
                gravity = 5f, lifetimeMs = 4000L,
                burstDurationMs = 2500L,
                triggerEverySec = 3f,
                triggerY = 0.85f,
                floorHeight = 0.25f,
                softness = 1.6f, opacity = 0.7f
            )
        ),
        DustPreset(
            "dustTrail", "Dust Trail", "💨",
            DustConfig(
                style = DustStyle.DUST_TRAIL,
                count = 100, speed = 80f, spread = 40f,
                direction = 180f,
                sizeMin = 3f, sizeMax = 10f,
                color1 = 0xFFEEDDCC, color2 = 0xFF886644,
                gravity = -10f, lifetimeMs = 2000L,
                burstDurationMs = 1500L,
                triggerEverySec = 1.5f,
                softness = 1.5f, opacity = 0.75f
            )
        ),
        DustPreset(
            "cosmicDust", "Cosmic Dust", "🌌",
            DustConfig(
                style = DustStyle.COSMIC_DUST,
                count = 200, speed = 40f, spread = 360f,
                sizeMin = 1f, sizeMax = 4f,
                color1 = 0xFFAA88FF, color2 = 0xFF4488FF,
                gravity = -5f, lifetimeMs = 5000L,
                burstDurationMs = 3500L,
                triggerEverySec = 4f,
                glow = true, softness = 2f, opacity = 0.9f
            )
        )
    )

    fun find(key: String): DustPreset? = PRESETS.firstOrNull { it.key == key }
    fun all(): List<DustPreset> = PRESETS
}
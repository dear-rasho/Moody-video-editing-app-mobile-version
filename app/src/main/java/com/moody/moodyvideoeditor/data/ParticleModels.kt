package com.moody.moodyvideoeditor.data

// ═══════════════════════════════════════════════════════════════
//  PARTICLE + BLAST CONFIG
// ═══════════════════════════════════════════════════════════════

enum class ParticleStyle {
    // ✨ Particles
    SPARK_BURST, SPARK_TRAIL, CONFETTI_POP, GLITTER_FOUNTAIN,
    EMBER_RISE, STARDUST, MAGIC_DUST, BOKEH_FLOAT,
    SPARK_SHOWER, NEON_PARTICLES,
    // 💥 Blasts
    EXPLOSION, SHOCKWAVE, IMPACT_BURST, FLASH_BANG,
    NUCLEAR_BLAST, PLASMA_ORB, FIREWORK_BURST,
    ENERGY_PULSE, SMOKE_BURST, DEBRIS_BLAST
}

enum class ParticleCategory { PARTICLES, BLASTS }

data class ParticleConfig(
    val style: ParticleStyle,
    val category: ParticleCategory,

    // Emission
    val count: Int = 80,               // 1..20000 (unlimited)
    val speed: Float = 100f,           // 20..500 base speed
    val spread: Float = 360f,          // 10..360 degrees
    val direction: Float = -90f,       // -90 = up, 0 = right, 90 = down
    val gravity: Float = 0f,           // -300..300

    // Appearance
    val sizeMin: Float = 2f,           // 0.1..500 (unlimited)
    val sizeMax: Float = 8f,           // 0.1..500 (unlimited)
    val color1: Long = 0xFFFFD166,
    val color2: Long = 0xFFFF6B00,
    val glow: Boolean = true,
    val fadeOut: Boolean = true,

    // Timing
    val lifetimeMs: Long = 1200L,      // 200..5000 per particle
    val burstDurationMs: Long = 500L,  // burst window
    val triggerEverySec: Float = 0f,   // 0 = once at effect start; >0 = repeat
    val triggerX: Float = 0.5f,        // 0..1
    val triggerY: Float = 0.5f,        // 0..1

    // Special
    val emojis: String = "",           // "🎉,🎊,🎈" for emoji confetti
    val shockwave: Boolean = false,
    val shockwaveThickness: Float = 0.05f,
    val flashOnTrigger: Boolean = false,
    val masterIntensity: Float = 100f
)

// ═══════════════════════════════════════════════════════════════
//  PARTICLE PRESETS — 20 effects
// ═══════════════════════════════════════════════════════════════

data class ParticlePreset(
    val key: String,
    val label: String,
    val icon: String,
    val config: ParticleConfig
)

object ParticleLibrary {

    val PRESETS: List<ParticlePreset> = listOf(

        // ═══════ ✨ PARTICLES (10) ═══════

        ParticlePreset(
            "sparkBurst", "Spark Burst", "✨",
            ParticleConfig(
                style = ParticleStyle.SPARK_BURST,
                category = ParticleCategory.PARTICLES,
                count = 100, speed = 200f, spread = 360f,
                sizeMin = 2f, sizeMax = 6f,
                color1 = 0xFFFFEE88, color2 = 0xFFFFAA33,
                gravity = 80f, lifetimeMs = 1000L,
                burstDurationMs = 200L,
                glow = true
            )
        ),
        ParticlePreset(
            "sparkTrail", "Spark Trail", "💫",
            ParticleConfig(
                style = ParticleStyle.SPARK_TRAIL,
                category = ParticleCategory.PARTICLES,
                count = 120, speed = 60f, spread = 40f,
                direction = -90f,
                sizeMin = 1.5f, sizeMax = 4f,
                color1 = 0xFFFFEE88, color2 = 0xFFFF6633,
                gravity = -30f, lifetimeMs = 1800L,
                burstDurationMs = 1500L,
                triggerEverySec = 1.5f,
                glow = true
            )
        ),
        ParticlePreset(
            "confettiPop", "Confetti Pop", "🎊",
            ParticleConfig(
                style = ParticleStyle.CONFETTI_POP,
                category = ParticleCategory.PARTICLES,
                count = 120, speed = 180f, spread = 120f,
                direction = -90f,
                sizeMin = 4f, sizeMax = 10f,
                color1 = 0xFFFF0066, color2 = 0xFF00E5FF,
                gravity = 200f, lifetimeMs = 2500L,
                burstDurationMs = 300L,
                glow = false
            )
        ),
        ParticlePreset(
            "glitterFountain", "Glitter Fountain", "✨",
            ParticleConfig(
                style = ParticleStyle.GLITTER_FOUNTAIN,
                category = ParticleCategory.PARTICLES,
                count = 150, speed = 150f, spread = 60f,
                direction = -90f,
                sizeMin = 1.5f, sizeMax = 4f,
                color1 = 0xFFFFFFFF, color2 = 0xFFFFD166,
                gravity = 180f, lifetimeMs = 2000L,
                burstDurationMs = 1200L,
                triggerEverySec = 2f,
                glow = true
            )
        ),
        ParticlePreset(
            "emberRise", "Ember Rise", "🔥",
            ParticleConfig(
                style = ParticleStyle.EMBER_RISE,
                category = ParticleCategory.PARTICLES,
                count = 80, speed = 40f, spread = 60f,
                direction = -90f,
                sizeMin = 2f, sizeMax = 5f,
                color1 = 0xFFFFCC00, color2 = 0xFFFF3300,
                gravity = -50f, lifetimeMs = 2500L,
                burstDurationMs = 1500L,
                triggerEverySec = 2.5f,
                glow = true
            )
        ),
        ParticlePreset(
            "stardust", "Stardust", "⭐",
            ParticleConfig(
                style = ParticleStyle.STARDUST,
                category = ParticleCategory.PARTICLES,
                count = 60, speed = 30f, spread = 360f,
                sizeMin = 1f, sizeMax = 3f,
                color1 = 0xFFFFFFFF, color2 = 0xFF88DDFF,
                gravity = 0f, lifetimeMs = 3000L,
                burstDurationMs = 2000L,
                triggerEverySec = 3f,
                glow = true
            )
        ),
        ParticlePreset(
            "magicDust", "Magic Dust", "🪄",
            ParticleConfig(
                style = ParticleStyle.MAGIC_DUST,
                category = ParticleCategory.PARTICLES,
                count = 100, speed = 50f, spread = 360f,
                sizeMin = 1.5f, sizeMax = 4f,
                color1 = 0xFFB366FF, color2 = 0xFFFF66FF,
                gravity = -20f, lifetimeMs = 2200L,
                burstDurationMs = 1500L,
                triggerEverySec = 2f,
                glow = true
            )
        ),
        ParticlePreset(
            "bokehFloat", "Bokeh Float", "🔮",
            ParticleConfig(
                style = ParticleStyle.BOKEH_FLOAT,
                category = ParticleCategory.PARTICLES,
                count = 40, speed = 25f, spread = 360f,
                sizeMin = 8f, sizeMax = 20f,
                color1 = 0xFFFFD1FF, color2 = 0xFF88DDFF,
                gravity = -10f, lifetimeMs = 4000L,
                burstDurationMs = 2500L,
                triggerEverySec = 3.5f,
                glow = false
            )
        ),
        ParticlePreset(
            "sparkShower", "Spark Shower", "🌠",
            ParticleConfig(
                style = ParticleStyle.SPARK_SHOWER,
                category = ParticleCategory.PARTICLES,
                count = 120, speed = 100f, spread = 180f,
                direction = 90f,
                sizeMin = 1.5f, sizeMax = 4f,
                color1 = 0xFFFFEE88, color2 = 0xFFFF6633,
                gravity = 60f, lifetimeMs = 1800L,
                burstDurationMs = 1200L,
                triggerEverySec = 2f,
                triggerY = 0f,
                glow = true
            )
        ),
        ParticlePreset(
            "neonParticles", "Neon Particles", "💠",
            ParticleConfig(
                style = ParticleStyle.NEON_PARTICLES,
                category = ParticleCategory.PARTICLES,
                count = 90, speed = 80f, spread = 360f,
                sizeMin = 2f, sizeMax = 6f,
                color1 = 0xFF00FFFF, color2 = 0xFFFF00FF,
                gravity = 0f, lifetimeMs = 2000L,
                burstDurationMs = 1500L,
                triggerEverySec = 2f,
                glow = true
            )
        ),

        // ═══════ 💥 BLASTS (10) ═══════

        ParticlePreset(
            "explosionCenter", "Explosion", "💥",
            ParticleConfig(
                style = ParticleStyle.EXPLOSION,
                category = ParticleCategory.BLASTS,
                count = 150, speed = 250f, spread = 360f,
                sizeMin = 3f, sizeMax = 10f,
                color1 = 0xFFFFEE00, color2 = 0xFFFF3300,
                gravity = 100f, lifetimeMs = 1200L,
                burstDurationMs = 150L,
                flashOnTrigger = true,
                shockwave = true, shockwaveThickness = 0.08f,
                glow = true
            )
        ),
        ParticlePreset(
            "shockwave", "Shockwave", "💫",
            ParticleConfig(
                style = ParticleStyle.SHOCKWAVE,
                category = ParticleCategory.BLASTS,
                count = 40, speed = 400f, spread = 360f,
                sizeMin = 2f, sizeMax = 5f,
                color1 = 0xFFFFFFFF, color2 = 0xFF88DDFF,
                gravity = 0f, lifetimeMs = 800L,
                burstDurationMs = 100L,
                shockwave = true, shockwaveThickness = 0.12f,
                glow = true
            )
        ),
        ParticlePreset(
            "impactBurst", "Impact", "⚡",
            ParticleConfig(
                style = ParticleStyle.IMPACT_BURST,
                category = ParticleCategory.BLASTS,
                count = 80, speed = 300f, spread = 180f,
                direction = -90f,
                sizeMin = 2f, sizeMax = 6f,
                color1 = 0xFFFFAA33, color2 = 0xFFFF0066,
                gravity = 200f, lifetimeMs = 900L,
                burstDurationMs = 100L,
                shockwave = true, shockwaveThickness = 0.06f,
                glow = true
            )
        ),
        ParticlePreset(
            "flashBang", "Flash Bang", "📸",
            ParticleConfig(
                style = ParticleStyle.FLASH_BANG,
                category = ParticleCategory.BLASTS,
                count = 200, speed = 500f, spread = 360f,
                sizeMin = 2f, sizeMax = 8f,
                color1 = 0xFFFFFFFF, color2 = 0xFFFFEE88,
                gravity = 0f, lifetimeMs = 500L,
                burstDurationMs = 80L,
                flashOnTrigger = true,
                glow = true
            )
        ),
        ParticlePreset(
            "nuclearBlast", "Nuclear Blast", "☢️",
            ParticleConfig(
                style = ParticleStyle.NUCLEAR_BLAST,
                category = ParticleCategory.BLASTS,
                count = 300, speed = 350f, spread = 360f,
                sizeMin = 4f, sizeMax = 15f,
                color1 = 0xFFFFEE00, color2 = 0xFF882200,
                gravity = 150f, lifetimeMs = 2000L,
                burstDurationMs = 400L,
                flashOnTrigger = true,
                shockwave = true, shockwaveThickness = 0.15f,
                glow = true
            )
        ),
        ParticlePreset(
            "plasmaOrb", "Plasma Orb", "🔮",
            ParticleConfig(
                style = ParticleStyle.PLASMA_ORB,
                category = ParticleCategory.BLASTS,
                count = 100, speed = 60f, spread = 360f,
                sizeMin = 3f, sizeMax = 8f,
                color1 = 0xFF88DDFF, color2 = 0xFFCC88FF,
                gravity = -20f, lifetimeMs = 1800L,
                burstDurationMs = 800L,
                shockwave = true, shockwaveThickness = 0.05f,
                glow = true
            )
        ),
        ParticlePreset(
            "fireworkBurst", "Firework", "🎆",
            ParticleConfig(
                style = ParticleStyle.FIREWORK_BURST,
                category = ParticleCategory.BLASTS,
                count = 180, speed = 220f, spread = 360f,
                sizeMin = 2f, sizeMax = 7f,
                color1 = 0xFFFF0066, color2 = 0xFF00E5FF,
                gravity = 120f, lifetimeMs = 2000L,
                burstDurationMs = 200L,
                flashOnTrigger = true,
                glow = true
            )
        ),
        ParticlePreset(
            "energyPulse", "Energy Pulse", "⚡",
            ParticleConfig(
                style = ParticleStyle.ENERGY_PULSE,
                category = ParticleCategory.BLASTS,
                count = 60, speed = 200f, spread = 360f,
                sizeMin = 2f, sizeMax = 5f,
                color1 = 0xFF00FFFF, color2 = 0xFFFF00FF,
                gravity = 0f, lifetimeMs = 1000L,
                burstDurationMs = 200L,
                shockwave = true, shockwaveThickness = 0.10f,
                glow = true
            )
        ),
        ParticlePreset(
            "smokeBurst", "Smoke Burst", "💨",
            ParticleConfig(
                style = ParticleStyle.SMOKE_BURST,
                category = ParticleCategory.BLASTS,
                count = 100, speed = 80f, spread = 360f,
                sizeMin = 6f, sizeMax = 18f,
                color1 = 0xFFAAAAAA, color2 = 0xFF555555,
                gravity = -30f, lifetimeMs = 2500L,
                burstDurationMs = 500L,
                glow = false
            )
        ),
        ParticlePreset(
            "debrisBlast", "Debris Blast", "🪨",
            ParticleConfig(
                style = ParticleStyle.DEBRIS_BLAST,
                category = ParticleCategory.BLASTS,
                count = 120, speed = 280f, spread = 360f,
                sizeMin = 3f, sizeMax = 9f,
                color1 = 0xFFAAAAAA, color2 = 0xFF553311,
                gravity = 250f, lifetimeMs = 1800L,
                burstDurationMs = 200L,
                glow = false
            )
        )
    )

    fun find(key: String): ParticlePreset? =
        PRESETS.firstOrNull { it.key == key }

    fun all(): List<ParticlePreset> = PRESETS
}
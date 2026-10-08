package com.moody.moodyvideoeditor.data

// ═══════════════════════════════════════════════════════════════
//  LIGHT LEAKS — Presets Library
//
//  ⚠️ CLASSES (LightLeakConfig, LightLeakAnimation, etc.)
//     ab EffectModels.kt mein hain — duplicate avoid karne ke liye.
//
//  Is file mein sirf PRESETS hain.
//
//  🆕 Naya light leak add karna ho toh bas LightLeakPreset(...)
//     ko PRESETS list mein add karo. Baaki sab automatic.
// ═══════════════════════════════════════════════════════════════

object LightLeakLibrary {

    val PRESETS: List<LightLeakPreset> = listOf(

        // ─── 1 ─── Golden Hour ───────────────────────────────
        LightLeakPreset(
            key = "goldenHour",
            label = "Golden Hour",
            icon = "🌅",
            config = LightLeakConfig(
                color1 = 0xFFFFCC66,
                color2 = 0xFFFF8800,
                positionX = 0.85f,
                positionY = 0.15f,
                radius = 0.70f,
                intensity = 110f,
                softness = 0.6f,
                animation = LightLeakAnimation.BREATHE,
                speed = 1.0f
            )
        ),

        // ─── 2 ─── Cinematic Blue ────────────────────────────
        LightLeakPreset(
            key = "cinematicBlue",
            label = "Cinematic Blue",
            icon = "💙",
            config = LightLeakConfig(
                color1 = 0xFF66CCFF,
                color2 = 0xFF0055FF,
                positionX = 0.15f,
                positionY = 0.50f,
                radius = 0.55f,
                intensity = 95f,
                softness = 0.7f,
                animation = LightLeakAnimation.STATIC,
                speed = 1.0f
            )
        ),

        // ─── 3 ─── Lens Flare ────────────────────────────────
        LightLeakPreset(
            key = "lensFlare",
            label = "Lens Flare",
            icon = "✨",
            config = LightLeakConfig(
                color1 = 0xFFFFFFFF,
                color2 = 0xFFFFDD88,
                positionX = 0.50f,
                positionY = 0.40f,
                radius = 0.40f,
                intensity = 140f,
                softness = 0.3f,
                animation = LightLeakAnimation.PULSE,
                speed = 1.5f
            )
        ),

        // ─── 4 ─── Rainbow Prism ─────────────────────────────
        LightLeakPreset(
            key = "rainbowPrism",
            label = "Rainbow Prism",
            icon = "🌈",
            config = LightLeakConfig(
                color1 = 0xFFFF00FF,
                color2 = 0xFF00FFFF,
                positionX = 0.50f,
                positionY = 0.10f,
                radius = 0.85f,
                intensity = 100f,
                softness = 0.5f,
                animation = LightLeakAnimation.SWEEP,
                speed = 0.8f
            )
        ),

        // ─── 5 ─── Film Burn ─────────────────────────────────
        LightLeakPreset(
            key = "filmBurn",
            label = "Film Burn",
            icon = "🔥",
            config = LightLeakConfig(
                color1 = 0xFFFFAA00,
                color2 = 0xFFFF2200,
                positionX = 0.50f,
                positionY = 0.95f,
                radius = 0.80f,
                intensity = 130f,
                softness = 0.4f,
                animation = LightLeakAnimation.FLICKER,
                speed = 2.0f
            )
        ),

        // ─── 6 ─── Neon Pink ─────────────────────────────────
        LightLeakPreset(
            key = "neonPink",
            label = "Neon Pink",
            icon = "💗",
            config = LightLeakConfig(
                color1 = 0xFFFF88CC,
                color2 = 0xFFFF00AA,
                positionX = 0.50f,
                positionY = 0.90f,
                radius = 0.65f,
                intensity = 105f,
                softness = 0.6f,
                animation = LightLeakAnimation.BREATHE,
                speed = 1.2f
            )
        ),

        // ─── 7 ─── Sunset Flare ──────────────────────────────
        LightLeakPreset(
            key = "sunsetFlare",
            label = "Sunset Flare",
            icon = "☀️",
            config = LightLeakConfig(
                color1 = 0xFFFFDD00,
                color2 = 0xFFFF5500,
                positionX = 0.50f,
                positionY = 0.05f,
                radius = 0.75f,
                intensity = 120f,
                softness = 0.55f,
                animation = LightLeakAnimation.DRIFT,
                speed = 0.7f
            )
        ),

        // ─── 8 ─── Anamorphic ────────────────────────────────
        LightLeakPreset(
            key = "anamorphicStreak",
            label = "Anamorphic",
            icon = "🔷",
            config = LightLeakConfig(
                color1 = 0xFF00AAFF,
                color2 = 0xFF0088CC,
                positionX = 0.50f,
                positionY = 0.35f,
                radius = 1.20f,
                intensity = 90f,
                softness = 0.8f,
                animation = LightLeakAnimation.STATIC,
                speed = 1.0f
            )
        ),

        // ─── 9 ─── Soft Haze ─────────────────────────────────
        LightLeakPreset(
            key = "softHaze",
            label = "Soft Haze",
            icon = "🌫️",
            config = LightLeakConfig(
                color1 = 0xFFFFFFFF,
                color2 = 0xFFEEDDCC,
                positionX = 0.50f,
                positionY = 0.50f,
                radius = 1.50f,
                intensity = 60f,
                softness = 0.9f,
                animation = LightLeakAnimation.STATIC,
                speed = 1.0f
            )
        ),

        // ─── 10 ─── Purple Dream ─────────────────────────────
        LightLeakPreset(
            key = "purpleDream",
            label = "Purple Dream",
            icon = "💜",
            config = LightLeakConfig(
                color1 = 0xFFCC88FF,
                color2 = 0xFF8800CC,
                positionX = 0.20f,
                positionY = 0.80f,
                radius = 0.70f,
                intensity = 115f,
                softness = 0.65f,
                animation = LightLeakAnimation.PULSE,
                speed = 1.3f
            )
        )

        // ═══════════════════════════════════════════════════════
        //  🆕 NAYE LIGHT LEAKS YAHAN ADD KARO
        //  Format:
        //
        //  LightLeakPreset(
        //      key = "uniqueKey",
        //      label = "Display Name",
        //      icon = "🎨",
        //      config = LightLeakConfig(
        //          color1 = 0xFFRRGGBB,
        //          color2 = 0xFFRRGGBB,
        //          positionX = 0.5f,     // 0..1
        //          positionY = 0.5f,     // 0..1
        //          radius = 0.6f,        // 0.1..2.0
        //          intensity = 100f,     // 0..200
        //          softness = 0.5f,      // 0..1
        //          animation = LightLeakAnimation.STATIC,
        //          speed = 1.0f
        //      )
        //  ),
        //
        // ═══════════════════════════════════════════════════════
    )

    fun find(key: String): LightLeakPreset? =
        PRESETS.firstOrNull { it.key == key }

    fun all(): List<LightLeakPreset> = PRESETS
}
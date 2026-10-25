package com.moody.moodyvideoeditor.data

// ═══════════════════════════════════════════════════════════════
//  🟩 CHROMA KEY — green/blue screen removal
//  Mirrors OBS chroma-key + Premiere Ultra Key logic
// ═══════════════════════════════════════════════════════════════

data class ChromaKeyState(
    val enabled: Boolean = false,

    // Picked target color (ARGB)
    val targetColor: Long = 0xFF00FF00,   // default green, but enabled=false
    val hasColor: Boolean = false,        // user ne color pick kiya ya nahi

    // Color distance matching
    val similarity: Float = 40f,          // 0..100 — how close color must be
    val smoothness: Float = 15f,          // 0..100 — edge softness

    // Spill suppression (removes green tint from edges)
    val spillSuppression: Float = 50f,    // 0..100 — removes color cast

    // Optional: partial transparency instead of full cutout
    val opacity: Float = 100f             // 0..100 — final alpha multiplier
) {
    val isActive: Boolean
        get() = enabled && hasColor
}

object ChromaKeyPresets {

    // Common target colors
    val GREEN_SCREEN = 0xFF00B140L
    val BLUE_SCREEN  = 0xFF0047BBL
    val RED_SCREEN   = 0xFFE60000L

    // One-tap presets
    val QUICK_PRESETS = listOf(
        "Green Screen" to GREEN_SCREEN,
        "Blue Screen"  to BLUE_SCREEN,
        "Red Screen"   to RED_SCREEN
    )

    // Settings presets
    data class Preset(
        val key: String,
        val label: String,
        val icon: String,
        val similarity: Float,
        val smoothness: Float,
        val spill: Float
    )

    val SETTINGS_PRESETS = listOf(
        Preset("default", "Balanced", "⚖️", 40f, 15f, 50f),
        Preset("aggressive", "Aggressive", "💥", 65f, 8f, 80f),
        Preset("gentle", "Gentle", "🪶", 25f, 25f, 30f),
        Preset("softEdge", "Soft Edge", "🌫️", 35f, 40f, 60f),
        Preset("cleanEdge", "Clean Edge", "✨", 55f, 5f, 70f)
    )
}
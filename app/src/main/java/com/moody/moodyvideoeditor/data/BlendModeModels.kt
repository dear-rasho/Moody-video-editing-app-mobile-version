package com.moody.moodyvideoeditor.data

// ═══════════════════════════════════════════════════════════════
//  🎨 BLEND MODES — layer-wise compositing
// ═══════════════════════════════════════════════════════════════

enum class LayerBlendMode(
    val key: String,
    val label: String,
    val icon: String,
    val composeName: String,      // Compose BlendMode enum name
    val ffmpegName: String,       // FFmpeg blend filter mode
    val category: String
) {
    // ─── BASIC ───
    NORMAL("normal", "Normal", "◯", "SrcOver", "overlay", "basic"),
    MULTIPLY("multiply", "Multiply", "✕", "Multiply", "multiply", "basic"),
    SCREEN("screen", "Screen", "☀", "Screen", "screen", "basic"),
    OVERLAY("overlay", "Overlay", "◐", "Overlay", "overlay", "basic"),
    DARKEN("darken", "Darken", "🌑", "Darken", "darken", "basic"),
    LIGHTEN("lighten", "Lighten", "🌕", "Lighten", "lighten", "basic"),

    // ─── CONTRAST ───
    HARD_LIGHT("hardLight", "Hard Light", "🔆", "Overlay", "hardlight", "contrast"),
    SOFT_LIGHT("softLight", "Soft Light", "🌤", "Softlight", "softlight", "contrast"),
    COLOR_DODGE("colorDodge", "Color Dodge", "💡", "ColorDodge", "dodge", "contrast"),
    COLOR_BURN("colorBurn", "Color Burn", "🔥", "ColorBurn", "burn", "contrast"),
    VIVID_LIGHT("vividLight", "Vivid Light", "⚡", "Overlay", "vividlight", "contrast"),
    LINEAR_LIGHT("linearLight", "Linear Light", "◨", "Overlay", "linearlight", "contrast"),
    PIN_LIGHT("pinLight", "Pin Light", "📌", "Overlay", "pinlight", "contrast"),

    // ─── INVERT ───
    DIFFERENCE("difference", "Difference", "◑", "Difference", "difference", "invert"),
    EXCLUSION("exclusion", "Exclusion", "▣", "Exclusion", "exclusion", "invert"),
    SUBTRACT("subtract", "Subtract", "−", "Plus", "subtract", "invert"),
    DIVIDE("divide", "Divide", "÷", "Plus", "divide", "invert"),

    // ─── ADD ───
    ADD("add", "Add", "＋", "Plus", "addition", "add"),
    LINEAR_DODGE("linearDodge", "Linear Dodge", "⊕", "Plus", "addition", "add"),

    // ─── COLOR ───
    HUE("hue", "Hue", "🎨", "Hue", "hue", "color"),
    SATURATION("saturation", "Saturation", "🎏", "Saturation", "saturation", "color"),
    COLOR("color", "Color", "🖌", "Color", "color", "color"),
    LUMINOSITY("luminosity", "Luminosity", "💡", "Luminosity", "luminosity", "color");

    companion object {
        fun fromKey(key: String): LayerBlendMode =
            values().firstOrNull { it.key.equals(key, ignoreCase = true) }
                ?: NORMAL

        fun categories(): List<Pair<String, String>> = listOf(
            "basic" to "🎯 Basic",
            "contrast" to "🌓 Contrast",
            "invert" to "🔄 Invert",
            "add" to "➕ Add",
            "color" to "🎨 Color"
        )

        fun modesInCategory(cat: String): List<LayerBlendMode> =
            values().filter { it.category == cat }
    }
}
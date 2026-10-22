package com.moody.moodyvideoeditor.data

// Only keeping transitions that work 100% perfectly in both Preview and Export.
data class TransitionState(
    val key: String = "none",
    val durationMs: Long = 500L
) {
    val isActive: Boolean get() = key != "none"
}

data class TransitionPreset(
    val key: String,
    val label: String,
    val icon: String,
    val category: String,
    val ffmpegXfade: String = "fade"
)

object TransitionLibrary {

    val CATEGORIES = listOf(
        "fade" to "🌫️ Fade & Opacity",
        "position" to "⬅️ Position & Slide",
        "zoom" to "🔍 Zooms"
    )

    val PRESETS: List<TransitionPreset> = listOf(
        // ─── 1. FADE & OPACITY (Opacity) ───
        TransitionPreset("none", "None", "∅", "fade", ""),
        TransitionPreset("fade", "Fade", "🌫️", "fade", "fade"),
        TransitionPreset("fadeBlack", "Fade Black", "⬛", "fade", "fadeblack"),
        TransitionPreset("fadeWhite", "Fade White", "⬜", "fade", "fadewhite"),
        TransitionPreset("dissolve", "Dissolve", "✨", "fade", "dissolve"),
        TransitionPreset("blur", "Blur", "💧", "fade", "hblur"),
        TransitionPreset("blinkFade", "Blink Fade", "😉", "fade", "fadeblack"),
        TransitionPreset("softGlaze", "Soft Glaze", "🌫️", "fade", "dissolve"),
        TransitionPreset("dreamyBloom", "Dreamy Bloom", "🌸", "fade", "fadegrays"),
        TransitionPreset("glowDissolve", "Glow Dissolve", "🌟", "fade", "dissolve"),
        TransitionPreset("whiteFlash", "White Flash", "⚪", "fade", "fadewhite"),

        // ─── 2. POSITION & SLIDE (Motion) ───
        TransitionPreset("slideLeft", "Slide Left", "⬅️", "position", "slideleft"),
        TransitionPreset("slideRight", "Slide Right", "➡️", "position", "slideright"),
        TransitionPreset("slideUp", "Slide Up", "⬆️", "position", "slideup"),
        TransitionPreset("slideDown", "Slide Down", "⬇️", "position", "slidedown"),

        TransitionPreset("smoothLeft", "Smooth Left", "◀️", "position", "smoothleft"),
        TransitionPreset("smoothRight", "Smooth Right", "▶️", "position", "smoothright"),
        TransitionPreset("smoothUp", "Smooth Up", "🔼", "position", "smoothup"),
        TransitionPreset("smoothDown", "Smooth Down", "🔽", "position", "smoothdown"),

        TransitionPreset("wipeLeft", "Wipe Left", "🧹", "position", "wipeleft"),
        TransitionPreset("wipeRight", "Wipe Right", "🧹", "position", "wiperight"),
        TransitionPreset("wipeUp", "Wipe Up", "🧹", "position", "wipeup"),
        TransitionPreset("wipeDown", "Wipe Down", "🧹", "position", "wipedown"),

        TransitionPreset("diagTL", "Diagonal TL", "↖️", "position", "diagtl"),
        TransitionPreset("diagTR", "Diagonal TR", "↗️", "position", "diagtr"),
        TransitionPreset("diagBL", "Diagonal BL", "↙️", "position", "diagbl"),
        TransitionPreset("diagBR", "Diagonal BR", "↘️", "position", "diagbr"),

        // ─── 3. ZOOMS (Scale) ───
        TransitionPreset("zoomIn", "Zoom In", "🔍", "zoom", "zoomin"),
        TransitionPreset("circleOpen", "Circle Open", "⭕", "zoom", "circleopen"),
        TransitionPreset("circleClose", "Circle Close", "🛑", "zoom", "circleclose")
    )

    fun find(key: String): TransitionPreset? =
        PRESETS.firstOrNull { it.key == key }

    fun presetsInCategory(category: String): List<TransitionPreset> =
        PRESETS.filter { it.category == category }
}
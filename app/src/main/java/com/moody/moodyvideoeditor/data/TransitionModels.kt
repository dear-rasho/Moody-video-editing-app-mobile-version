package com.moody.moodyvideoeditor.data

/**
 * Mirrors js/workspace/transitionEngine.js TRANSITIONS list.
 * Transition stored on the RIGHT clip of a pair.
 */
data class TransitionState(
    val key: String = "none",
    val durationMs: Long = 500L
) {
    val isActive: Boolean get() = key != "none"
}

object TransitionLibrary {

    data class Preset(val key: String, val label: String, val icon: String)

    val PRESETS: List<Preset> = listOf(
        Preset("none", "None", "∅"),
        Preset("fade", "Fade", "◐"),
        Preset("dissolve", "Dissolve", "✨"),
        Preset("fadeBlack", "Fade Black", "⬛"),
        Preset("fadeWhite", "Fade White", "⬜"),
        Preset("blur", "Blur Blend", "💫"),

        Preset("pushLeft", "Push Left", "⬅️"),
        Preset("pushRight", "Push Right", "➡️"),
        Preset("pushUp", "Push Up", "⬆️"),
        Preset("pushDown", "Push Down", "⬇️"),

        Preset("slideLeft", "Slide Left", "◀️"),
        Preset("slideRight", "Slide Right", "▶️"),
        Preset("slideUp", "Slide Up", "🔼"),
        Preset("slideDown", "Slide Down", "🔽"),

        Preset("wipeLeft", "Wipe Left", "◁"),
        Preset("wipeRight", "Wipe Right", "▷"),
        Preset("wipeUp", "Wipe Up", "△"),
        Preset("wipeDown", "Wipe Down", "▽"),

        Preset("circleIn", "Circle In", "⭕"),
        Preset("irisBox", "Iris Box", "▢"),
        Preset("clockWipe", "Clock", "🕐"),

        Preset("zoomIn", "Zoom In", "🔍"),
        Preset("zoomOut", "Zoom Out", "🔎"),
        Preset("crossZoom", "Cross Zoom", "⊙"),

        Preset("spinCW", "Spin CW", "↻"),
        Preset("spinCCW", "Spin CCW", "↺"),
        Preset("swirl", "Swirl", "🌀"),

        Preset("rgbSplit", "RGB Split", "🌈"),
        Preset("glitch", "Glitch", "⚡"),
        Preset("flashWhite", "Flash", "💥")
    )

    fun find(key: String): Preset? = PRESETS.firstOrNull { it.key == key }
}
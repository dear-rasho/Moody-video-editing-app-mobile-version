package com.moody.moodyvideoeditor.data

/**
 * Mirrors js/features/filters.js FILTERS list.
 * 9 CSS filter primitives with individual min/max/default.
 */
data class FilterState(
    val brightness: Float = 100f,
    val contrast: Float = 100f,
    val saturation: Float = 100f,
    val hue: Float = 0f,
    val grayscale: Float = 0f,
    val sepia: Float = 0f,
    val invert: Float = 0f,
    val blur: Float = 0f,
    val opacity: Float = 100f
) {
    val isDefault: Boolean get() = this == FilterState()

    val hasAnyChange: Boolean
        get() = brightness != 100f || contrast != 100f || saturation != 100f ||
                hue != 0f || grayscale != 0f || sepia != 0f || invert != 0f ||
                blur != 0f || opacity != 100f

    /** Getter — filter key → value */
    fun get(key: String): Float = when (key) {
        "brightness" -> brightness
        "contrast" -> contrast
        "saturation" -> saturation
        "hue" -> hue
        "grayscale" -> grayscale
        "sepia" -> sepia
        "invert" -> invert
        "blur" -> blur
        "opacity" -> opacity
        else -> 0f
    }

    /** Setter — filter key → new value, returns updated state */
    fun set(key: String, value: Float): FilterState = when (key) {
        "brightness" -> copy(brightness = value)
        "contrast" -> copy(contrast = value)
        "saturation" -> copy(saturation = value)
        "hue" -> copy(hue = value)
        "grayscale" -> copy(grayscale = value)
        "sepia" -> copy(sepia = value)
        "invert" -> copy(invert = value)
        "blur" -> copy(blur = value)
        "opacity" -> copy(opacity = value)
        else -> this
    }

    /** Is this filter different from its default? */
    fun isChanged(key: String): Boolean {
        val meta = FILTERS.firstOrNull { it.key == key } ?: return false
        return get(key) != meta.default
    }

    companion object {
        /** Single source of truth for filter metadata — mirrors JS FILTERS array */
        data class Meta(
            val key: String,
            val label: String,
            val icon: String,
            val min: Float,
            val max: Float,
            val step: Float,
            val default: Float,
            val suffix: String
        )

        val FILTERS = listOf(
            Meta("brightness", "Brightness", "☀️", 0f, 200f, 1f, 100f, "%"),
            Meta("contrast", "Contrast", "◐", 0f, 200f, 1f, 100f, "%"),
            Meta("saturation", "Saturation", "🎨", 0f, 200f, 1f, 100f, "%"),
            Meta("hue", "Hue", "🌈", 0f, 360f, 1f, 0f, "°"),
            Meta("grayscale", "Grayscale", "⚪", 0f, 100f, 1f, 0f, "%"),
            Meta("sepia", "Sepia", "🟫", 0f, 100f, 1f, 0f, "%"),
            Meta("invert", "Invert", "🔄", 0f, 100f, 1f, 0f, "%"),
            Meta("blur", "Blur", "💧", 0f, 20f, 0.5f, 0f, "px"),
            Meta("opacity", "Opacity", "👁️", 0f, 100f, 1f, 100f, "%")
        )
    }
}
package com.moody.moodyvideoeditor.utils

/**
 * Aspect ratio helpers — ratio key → actual value + target dimensions.
 */
object RatioHelper {

    /** Ratio key → float value (width / height) */
    fun ratioValue(key: String): Float = when (key) {
        "9:16" -> 9f / 16f
        "1:1" -> 1f
        "4:5" -> 4f / 5f
        "3:4" -> 3f / 4f
        "21:9" -> 21f / 9f
        "16:9" -> 16f / 9f
        else -> 16f / 9f
    }

    /** Ratio key → target export dimensions (W × H) */
    fun targetDimensions(key: String): Pair<Int, Int> = when (key) {
        "9:16" -> 720 to 1280
        "1:1" -> 720 to 720
        "4:5" -> 576 to 720
        "3:4" -> 540 to 720
        "21:9" -> 1680 to 720
        "16:9" -> 1280 to 720
        else -> 1280 to 720
    }

    /** Short label for UI */
    fun shortLabel(key: String): String = when (key) {
        "16:9" -> "16:9"
        "9:16" -> "9:16"
        "1:1" -> "1:1"
        "4:5" -> "4:5"
        "3:4" -> "3:4"
        "21:9" -> "21:9"
        else -> "16:9"
    }
}
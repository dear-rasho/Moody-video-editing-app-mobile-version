package com.moody.moodyvideoeditor.data

import java.util.UUID

/**
 * Mirrors js/features/stickers.js CATEGORIES + st.sticker object.
 */
data class StickerState(
    val emoji: String = "",
    val x: Float = 50f,         // 0..100 %
    val y: Float = 50f,         // 0..100 %
    val scale: Float = 100f,    // 10..500 %
    val rotation: Float = 0f    // -180..180 deg
)

/**
 * Keyframe types for sticker animation.
 * Mirrors st.kfPosition, st.kfScale, st.kfRotation from JS.
 */
data class PositionKeyframe(val time: Float, val x: Float, val y: Float)
data class ValueKeyframe(val time: Float, val value: Float)

data class StickerKeyframes(
    val position: List<PositionKeyframe> = emptyList(),
    val scale: List<ValueKeyframe> = emptyList(),
    val rotation: List<ValueKeyframe> = emptyList()
) {
    val isEmpty: Boolean
        get() = position.isEmpty() && scale.isEmpty() && rotation.isEmpty()
}

/**
 * A placed sticker on the timeline.
 */
data class StickerClip(
    val id: String = UUID.randomUUID().toString(),
    val state: StickerState = StickerState(),
    val keyframes: StickerKeyframes = StickerKeyframes(),
    val easePosition: String = "easeInOut",
    val easeScale: String = "easeInOut",
    val easeRotation: String = "easeInOut",
    val startTimeMs: Long = 0L,
    val durationMs: Long = 3000L
) {
    val endTimeMs: Long get() = startTimeMs + durationMs
}

object StickerLibrary {

    data class Category(
        val key: String,
        val label: String,
        val icon: String,
        val emojis: List<String>
    )

    /**
     * Mirrors stickers.js CATEGORIES — 10 categories × 20 emojis.
     */
    val CATEGORIES: List<Category> = listOf(
        Category(
            "faces", "Faces", "😀", listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😍",
                "😘", "😎", "🤩", "🥳", "😜", "🤪", "😇", "🙃", "😌", "🥺"
            )
        ),
        Category(
            "hearts", "Hearts", "❤️", listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💕", "💞",
                "💓", "💗", "💖", "💘", "💝", "💟", "❣️", "💔", "💌", "🫶"
            )
        ),
        Category(
            "stars", "Stars", "⭐", listOf(
                "⭐", "🌟", "✨", "💫", "⚡", "🔥", "💥", "💢", "✴️", "✳️",
                "❇️", "🌠", "☄️", "🌌", "💫", "🌟", "✨", "⭐", "💫", "🌟"
            )
        ),
        Category(
            "arrows", "Arrows", "➡️", listOf(
                "⬅️", "➡️", "⬆️", "⬇️", "↗️", "↘️", "↙️", "↖️", "↔️", "↕️",
                "🔄", "🔃", "🔁", "🔂", "▶️", "◀️", "🔼", "🔽", "⏩", "⏪"
            )
        ),
        Category(
            "shapes", "Shapes", "🔴", listOf(
                "🔴", "🟠", "🟡", "🟢", "🔵", "🟣", "⚫", "⚪", "🟤", "🟥",
                "🟧", "🟨", "🟩", "🟦", "🟪", "⬛", "⬜", "🟫", "🔺", "🔻"
            )
        ),
        Category(
            "weather", "Weather", "☀️", listOf(
                "☀️", "🌤️", "⛅", "🌥️", "☁️", "🌦️", "🌧️", "⛈️", "🌩️", "🌨️",
                "❄️", "☃️", "⛄", "🌬️", "💨", "🌪️", "🌈", "☂️", "☔", "⚡"
            )
        ),
        Category(
            "nature", "Nature", "🌸", listOf(
                "🌸", "🌺", "🌻", "🌹", "🌷", "🌼", "💐", "🌿", "🍀", "🍁",
                "🍂", "🌱", "🌴", "🌵", "🌾", "🍄", "🌳", "🌲", "🪴", "🌷"
            )
        ),
        Category(
            "party", "Party", "🎉", listOf(
                "🎉", "🎊", "🎈", "🎁", "🎂", "🍰", "🥂", "🍾", "🎀", "🎊",
                "🎉", "🎈", "🎁", "🎂", "🍭", "🍬", "🍫", "🥳", "🎊", "🎉"
            )
        ),
        Category(
            "symbols", "Symbols", "✅", listOf(
                "✅", "❌", "❗", "❓", "⚠️", "🚫", "💯", "🔞", "🔆", "🔅",
                "♻️", "🆗", "🆕", "🆒", "🆓", "🆙", "🔝", "🔙", "🔚", "🔛"
            )
        ),
        Category(
            "animals", "Animals", "🐶", listOf(
                "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯",
                "🦁", "🐮", "🐷", "🐸", "🐵", "🐔", "🐧", "🐦", "🦄", "🐝"
            )
        )
    )

    /** Mirrors JS EASING_OPTIONS */
    data class EasingOption(val key: String, val label: String)

    val EASING_OPTIONS: List<EasingOption> = listOf(
        EasingOption("linear", "Linear"),
        EasingOption("easeIn", "Ease In"),
        EasingOption("easeOut", "Ease Out"),
        EasingOption("easeInOut", "Ease In-Out"),
        EasingOption("easeInCubic", "Cubic In"),
        EasingOption("easeOutCubic", "Cubic Out"),
        EasingOption("easeInOutCubic", "Cubic In-Out"),
        EasingOption("easeInBack", "Back In"),
        EasingOption("easeOutBack", "Back Out"),
        EasingOption("easeInOutBack", "Back In-Out"),
        EasingOption("easeOutBounce", "Bounce Out"),
        EasingOption("easeOutElastic", "Elastic Out")
    )

    fun emojiForCategory(categoryKey: String, index: Int): String? {
        val cat = CATEGORIES.firstOrNull { it.key == categoryKey } ?: return null
        return cat.emojis.getOrNull(index)
    }
}
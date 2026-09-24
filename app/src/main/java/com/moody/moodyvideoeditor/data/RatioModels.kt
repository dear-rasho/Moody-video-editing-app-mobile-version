package com.moody.moodyvideoeditor.data

data class RatioState(
    val key: String = "16:9",
    val w: Int = 16,
    val h: Int = 9
) {
    val aspect: Float get() = w.toFloat() / h.toFloat()
}

object RatioLibrary {
    val OPTIONS = listOf(
        RatioState("16:9", 16, 9),
        RatioState("9:16", 9, 16),
        RatioState("1:1", 1, 1),
        RatioState("4:5", 4, 5),
        RatioState("3:4", 3, 4),
        RatioState("21:9", 21, 9)
    )

    fun find(key: String): RatioState =
        OPTIONS.firstOrNull { it.key == key } ?: OPTIONS[0]
}
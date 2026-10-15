package com.moody.moodyvideoeditor.utils

// ═══════════════════════════════════════════════════════════════
//  CAPTION TEMPLATE — data model
//  Har template 3 layers ka hai. Har layer ek word ko style karta hai.
//  preferredRatios list se decide hota hai ke template kis aspect
//  ratio ke liye best hai.
// ═══════════════════════════════════════════════════════════════

data class CaptionLayer(
    val fontFamily: String,
    val fontSize: Int,
    val color: Long,
    val positionX: Float,
    val positionY: Float,
    val scale: Float = 100f,
    val glowEnabled: Boolean = true,
    val glowColor: Long = 0xE017D1E1,
    val glowRadius: Float = 40f,
    val strokeEnabled: Boolean = false,
    val strokeWidth: Float = 0f,
    val strokeColor: Long = 0xFF000000,
    val animation: String = "fadeIn",
    val animationDuration: Float = 0.5f,
    val startOffsetMs: Long = 0L
)

data class CaptionTemplate(
    val id: String,
    val name: String,
    val icon: String,
    val preferredRatios: Set<String>,
    val layers: List<CaptionLayer>
) {
    fun layerCount(): Int = layers.size
}

// ─── Available aspect ratios ───
object RatioKeys {
    const val LANDSCAPE_16_9 = "16:9"
    const val PORTRAIT_9_16 = "9:16"
    const val SQUARE_1_1 = "1:1"
    const val PORTRAIT_4_5 = "4:5"
    const val PORTRAIT_3_4 = "3:4"
    const val CINEMA_21_9 = "21:9"

    val ALL = setOf(
        LANDSCAPE_16_9, PORTRAIT_9_16, SQUARE_1_1,
        PORTRAIT_4_5, PORTRAIT_3_4, CINEMA_21_9
    )

    val VERTICAL = setOf(PORTRAIT_9_16, PORTRAIT_4_5, PORTRAIT_3_4)
    val HORIZONTAL = setOf(LANDSCAPE_16_9, CINEMA_21_9)
}
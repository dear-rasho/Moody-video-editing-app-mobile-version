package com.moody.moodyvideoeditor.data

import java.util.UUID

/**
 * Mirrors js/workspace/textRenderer.js textState object.
 * Each text clip has its own full state.
 */
data class TextState(
    val content: String = "",
    val fontFamily: String = "Arial",
    val fontSize: Int = 36,
    val fontWeight: String = "normal",     // "normal" | "bold"
    val fontStyle: String = "normal",      // "normal" | "italic"
    val color: Long = 0xFFFFFFFF,
    val strokeWidth: Float = 0f,
    val strokeColor: Long = 0xFF000000,
    val gradientEnabled: Boolean = false,
    val gradientColor1: Long = 0xFFFF0066,
    val gradientColor2: Long = 0xFF0066FF,
    val gradientAngle: Float = 90f,
    val shadowEnabled: Boolean = false,
    val shadowColor: Long = 0xFF000000,
    val shadowBlur: Float = 8f,
    val shadowOffsetX: Float = 2f,
    val shadowOffsetY: Float = 2f,
    val alignment: String = "center",      // left | center | right
    val positionX: Float = 50f,
    val positionY: Float = 50f,
    val anchorX: Float = 50f,
    val anchorY: Float = 50f,
    val scale: Float = 100f,
    val rotation: Float = 0f,
    val opacity: Float = 100f,
    val animation: String = "none",
    val animationDuration: Float = 0.6f
)

/**
 * Text clip on timeline — mirrors JS text clip structure.
 */
data class TextClip(
    val id: String = UUID.randomUUID().toString(),
    val state: TextState = TextState(),
    val startTimeMs: Long = 0L,
    val durationMs: Long = 3000L
) {
    val endTimeMs: Long get() = startTimeMs + durationMs
}
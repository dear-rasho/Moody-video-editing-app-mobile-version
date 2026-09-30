package com.moody.moodyvideoeditor.data

import java.util.UUID

/**
 * A segment of text with its own styling.
 * Allows mixed typography like "Rashid" → "Ras" big, "hid" small.
 * Uses character indices into the parent content string.
 */
data class TextSegment(
    val start: Int,                         // start char index (inclusive)
    val end: Int,                           // end char index (exclusive)
    val fontSize: Int? = null,              // override size (null = use parent)
    val color: Long? = null,                // override color
    val fontFamily: String? = null,         // override font
    val fontWeight: String? = null,         // "normal" | "bold"
    val fontStyle: String? = null,          // "normal" | "italic"
    val letterSpacing: Float? = null        // override letter spacing
) {
    val length: Int get() = end - start
}

/**
 * Full text state for a text clip.
 * All layout coordinates are RELATIVE (0-100%), scaled to canvas at render time.
 */
data class TextState(
    // ─── Content ───
    val content: String = "",

    // ─── Font ───
    val fontFamily: String = "Arial",
    val fontSize: Int = 36,
    val fontWeight: String = "normal",     // "normal" | "bold"
    val fontStyle: String = "normal",      // "normal" | "italic"

    // ─── Color ───
    val color: Long = 0xFFFFFFFF,

    // ─── Stroke (outline) ───
    val strokeEnabled: Boolean = false,
    val strokeWidth: Float = 0f,           // in dp
    val strokeColor: Long = 0xFF000000,

    // ─── Glow / Neon ───
    val glowEnabled: Boolean = false,
    val glowColor: Long = 0xFF4DD0E1,
    val glowRadius: Float = 25f,           // blur radius in dp

    // ─── Gradient ───
    val gradientEnabled: Boolean = false,
    val gradientColor1: Long = 0xFFFF0066,
    val gradientColor2: Long = 0xFF0066FF,
    val gradientAngle: Float = 90f,

    // ─── Shadow ───
    val shadowEnabled: Boolean = false,
    val shadowColor: Long = 0xFF000000,
    val shadowBlur: Float = 8f,
    val shadowOffsetX: Float = 2f,
    val shadowOffsetY: Float = 2f,

    // ─── Typography ───
    val alignment: String = "center",      // left | center | right
    val letterSpacing: Float = 0f,         // 🆕 char gap in sp
    val lineHeight: Float = 1.2f,          // 🆕 multiplier (1.0 = normal, 2.0 = double)
    val tracking: Float = 0f,              // 🆕 overall line stretch (advanced)

    // ─── Position (relative 0-100%) ───
    val positionX: Float = 50f,
    val positionY: Float = 50f,
    val anchorX: Float = 50f,
    val anchorY: Float = 50f,
    val maxWidth: Float = 90f,             // 🆕 wrap at this % of canvas width

    // ─── Transform ───
    val scale: Float = 100f,
    val rotation: Float = 0f,
    val opacity: Float = 100f,

    // ─── Animation ───
    val animation: String = "none",
    val animationDuration: Float = 0.6f,

    // ─── Template ───
    val templateId: String? = null,        // 🆕 which template this came from

    // ─── Segments (mixed typography) ───
    val segments: List<TextSegment> = emptyList()
) {

    // ═══════════════════════════════════════════════════════════
    //  SEGMENT HELPERS
    // ═══════════════════════════════════════════════════════════

    /** Find segment that contains a given char index */
    fun segmentAt(charIndex: Int): TextSegment? =
        segments.firstOrNull { charIndex in it.start until it.end }

    /** Add or merge a new segment */
    fun withSegment(seg: TextSegment): TextState {
        // Remove overlapping segments, then add new
        val cleaned = segments.filter { s ->
            s.end <= seg.start || s.start >= seg.end
        }
        val newSegments = (cleaned + seg).sortedBy { it.start }
        return copy(segments = newSegments)
    }

    /** Remove a specific segment by range */
    fun withoutSegment(start: Int, end: Int): TextState =
        copy(segments = segments.filter { it.start != start || it.end != end })

    /** Check if a char index falls inside any styled segment */
    fun isCharStyled(charIndex: Int): Boolean = segmentAt(charIndex) != null

    /** Total number of styled chars */
    val styledCharCount: Int
        get() = segments.sumOf { it.length }

    // ═══════════════════════════════════════════════════════════
    //  VALIDATION
    // ═══════════════════════════════════════════════════════════

    /** Clamp segments so they don't exceed content bounds */
    fun clampedSegments(): TextState {
        if (content.isEmpty()) return copy(segments = emptyList())
        val len = content.length
        val fixed = segments
            .mapNotNull { seg ->
                val s = seg.start.coerceIn(0, len)
                val e = seg.end.coerceIn(s, len)
                if (e > s) seg.copy(start = s, end = e) else null
            }
            .sortedBy { it.start }
        return copy(segments = fixed)
    }

    /** Is this state customized from default? */
    val isDefault: Boolean
        get() = this == TextState()
}

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
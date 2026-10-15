package com.moody.moodyvideoeditor.utils

import android.util.Log
import com.moody.moodyvideoeditor.data.TextState
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════
//  CAPTION STYLE ENGINE
//  Two auto-style modes:
//    1. WORDS  → one styled clip per word, at bottom-center
//    2. LINES  → grouped lines at bottom, max 7 words per line
//
//  Language-aware font selection (Urdu → Urdu fonts, etc.)
// ═══════════════════════════════════════════════════════════════

object CaptionStyleEngine {

    private const val TAG = "CaptionStyleEngine"

    // ─── Word count constraints per line ───
    private const val MAX_WORDS_PER_LINE = 7
    private const val MIN_WORDS_PER_LINE = 4

    // ─── Bottom positioning ───
    private const val Y_SINGLE_LINE = 78f
    private const val Y_LINE_START = 68f
    private const val Y_LINE_END = 88f

    // ═══════════════════════════════════════════════════════════
    //  OUTPUT
    // ═══════════════════════════════════════════════════════════

    data class StyledClip(
        val text: String,
        val fontFamily: String,
        val fontSize: Int,
        val color: Long,
        val positionX: Float,
        val positionY: Float,
        val glowEnabled: Boolean,
        val glowColor: Long,
        val glowRadius: Float,
        val strokeEnabled: Boolean,
        val strokeWidth: Float,
        val strokeColor: Long,
        val animation: String,
        val animationDuration: Float,
        val startMs: Long,
        val endMs: Long
    ) {
        fun toTextState(): TextState = TextState(
            content = text,
            fontFamily = fontFamily,
            fontSize = fontSize,
            fontWeight = "bold",
            fontStyle = "normal",
            color = color,
            glowEnabled = glowEnabled,
            glowColor = glowColor,
            glowRadius = glowRadius,
            strokeEnabled = strokeEnabled,
            strokeWidth = strokeWidth,
            strokeColor = strokeColor,
            alignment = "center",
            positionX = positionX,
            positionY = positionY,
            anchorX = 50f,
            anchorY = 50f,
            maxWidth = 95f,
            lineHeight = 1.15f,
            opacity = 100f,
            animation = animation,
            animationDuration = animationDuration
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  WORDS MODE — one clip per word
    //  Words distribute EVENLY across segment duration.
    //  No overlap — each word's window is exact.
    // ═══════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════
    //  WORDS MODE — customizable words per layer
    //  @param wordsPerLayer  1..7 — how many words in each text clip
    // ═══════════════════════════════════════════════════════════
    fun buildWordsMode(
        words: List<String>,
        segStartMs: Long,
        segEndMs: Long,
        language: String,
        wordsPerLayer: Int = 1
    ): List<StyledClip> {
        if (words.isEmpty()) return emptyList()

        val wpl = wordsPerLayer.coerceIn(1, 7)
        val chunks = words.chunked(wpl)
        val totalMs = (segEndMs - segStartMs).coerceAtLeast(100L)
        val perChunkMs = totalMs / chunks.size

        // Auto-detect language from text
        val detectedLang = if (language.equals("auto", ignoreCase = true) ||
            language.isBlank()
        ) {
            CaptionLanguage.detectLanguage(words.joinToString(" "))
        } else language

        val fonts = CaptionLanguage.fontsFor(detectedLang)
        val rng = Random(segStartMs)
        val animDur = (perChunkMs / 1000f * 0.6f).coerceIn(0.1f, 0.4f)

        Log.e(
            TAG,
            "WORDS mode: ${words.size} words → ${chunks.size} chunks " +
                    "(wpl=$wpl, perChunkMs=$perChunkMs)"
        )

        return chunks.mapIndexed { idx, chunk ->
            val clipStart = segStartMs + idx * perChunkMs
            val clipEnd = if (idx == chunks.size - 1) segEndMs
            else segStartMs + (idx + 1) * perChunkMs

            val text = chunk.joinToString(" ")
            val font = fonts[rng.nextInt(fonts.size)]
            val glow = pickRandomGlow(rng)
            val color = pickRandomColor(rng)
            val anim = pickRandomAnimation(rng)
            val fontSize = pickRandomSize(rng)

            StyledClip(
                text = text,
                fontFamily = font,
                fontSize = fontSize,
                color = color,
                positionX = 50f,
                positionY = Y_SINGLE_LINE,
                glowEnabled = true,
                glowColor = glow,
                glowRadius = 32f,
                strokeEnabled = false,
                strokeWidth = 0f,
                strokeColor = 0xFF000000,
                animation = anim,
                animationDuration = animDur,
                startMs = clipStart,
                endMs = clipEnd
            )
        }
    }
    // ═══════════════════════════════════════════════════════════
    //  LINES MODE — single text clip, auto-wrapped
    //  Max 7 words per line, min 4 words per line
    // ═══════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════
    //  LINES MODE — customizable words per line
    //  @param wordsPerLine  target words per line (3..7)
    // ═══════════════════════════════════════════════════════════

    fun buildLinesMode(
        words: List<String>,
        segStartMs: Long,
        segEndMs: Long,
        language: String,
        wordsPerLine: Int = 7
    ): List<StyledClip> {
        if (words.isEmpty()) return emptyList()

        val wpl = wordsPerLine.coerceIn(3, 7)
        val lines = groupIntoLines(words, wpl)
        Log.e(
            TAG,
            "LINES mode: ${words.size} words → ${lines.size} lines (wpl=$wpl)"
        )

        val fullText = lines.joinToString("\n") { it.joinToString(" ") }

        // Auto-detect language
        val detectedLang = if (language.equals("auto", ignoreCase = true) ||
            language.isBlank()
        ) {
            CaptionLanguage.detectLanguage(fullText)
        } else language

        val fonts = CaptionLanguage.fontsFor(detectedLang)
        val rng = Random(segStartMs)
        val font = fonts[rng.nextInt(fonts.size)]
        val glow = pickRandomGlow(rng)
        val anim = pickRandomAnimation(rng)

        return listOf(
            StyledClip(
                text = fullText,
                fontFamily = font,
                fontSize = 46,
                color = 0xFFFFFFFF,
                positionX = 50f,
                positionY = Y_SINGLE_LINE,
                glowEnabled = true,
                glowColor = glow,
                glowRadius = 28f,
                strokeEnabled = false,
                strokeWidth = 0f,
                strokeColor = 0xFF000000,
                animation = anim,
                animationDuration = 0.4f,
                startMs = segStartMs,
                endMs = segEndMs
            )
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  LINE GROUPING — custom words per line
    // ═══════════════════════════════════════════════════════════

    private fun groupIntoLines(
        words: List<String>,
        maxWordsPerLine: Int
    ): List<List<String>> {
        val total = words.size
        val wpl = maxWordsPerLine.coerceIn(3, 7)

        // Fits in a single line
        if (total <= wpl) return listOf(words)

        // How many lines needed?
        val lineCount = (total + wpl - 1) / wpl

        // Distribute evenly
        val base = total / lineCount
        val extra = total % lineCount

        val result = mutableListOf<List<String>>()
        var cursor = 0

        for (i in 0 until lineCount) {
            val count = if (i < extra) base + 1 else base
            val end = (cursor + count).coerceAtMost(total)
            result.add(words.subList(cursor, end))
            cursor = end
        }

        // Rebalance if last line too small
        if (result.size > 1 && result.last().size < 3) {
            val lastIdx = result.lastIndex
            val lastSize = result[lastIdx].size
            val needed = 3 - lastSize
            val prevLine = result[lastIdx - 1].toMutableList()

            if (prevLine.size - needed >= 3) {
                val moved = prevLine.takeLast(needed)
                val newPrev = prevLine.dropLast(needed)
                val newLast = moved + result[lastIdx]
                result[lastIdx - 1] = newPrev
                result[lastIdx] = newLast
            }
        }

        return result
    }
    // ═══════════════════════════════════════════════════════════
    //  RANDOM PICKERS
    // ═══════════════════════════════════════════════════════════

    private val GLOW_COLORS = listOf(
        0xE017D1E1L,   // Cyan
        0xE0FF4F8BL,   // Pink
        0xE0FFD166L,   // Gold
        0xE0A855F7L,   // Purple
        0xE0FF3B3BL,   // Red
        0xE0FF8C42L,   // Orange
        0xE022C55EL,   // Green
        0xE0FF00FFL,   // Magenta
        0xE060AFFFL,   // Blue
        0xE014B8A6L,   // Teal
        0xE0C4B5FDL,   // Lavender
        0xE0FF7F50L    // Coral
    )

    private val TEXT_COLORS = listOf(
        0xFFFFFFFF,    // White
        0xFFFFF5E1,    // Cream
        0xFFFFD166,    // Gold
        0xFFFFE5F0,    // Pinkish
        0xFF9BFFEC,    // Mint
        0xFFFFCCB3,    // Peach
        0xFFD9C9FF     // Lilac
    )

    private val ANIMATIONS = listOf(
        "fadeIn", "fadeUp", "slideUp", "popIn", "bounceIn",
        "blurryReveal", "characterRise", "jellyBounce",
        "overshootPop", "crossSlide", "zoomIn", "popScale"
    )

    private val FONT_SIZES = listOf(48, 52, 56, 60, 64)

    private fun pickRandomGlow(rng: Random): Long =
        GLOW_COLORS[rng.nextInt(GLOW_COLORS.size)]

    private fun pickRandomColor(rng: Random): Long =
        TEXT_COLORS[rng.nextInt(TEXT_COLORS.size)]

    private fun pickRandomAnimation(rng: Random): String =
        ANIMATIONS[rng.nextInt(ANIMATIONS.size)]

    private fun pickRandomSize(rng: Random): Int =
        FONT_SIZES[rng.nextInt(FONT_SIZES.size)]
}
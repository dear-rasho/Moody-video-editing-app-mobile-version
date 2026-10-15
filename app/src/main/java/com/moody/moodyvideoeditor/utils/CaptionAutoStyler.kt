package com.moody.moodyvideoeditor.utils

import android.util.Log

// ═══════════════════════════════════════════════════════════════
//  CAPTION AUTO STYLER v3
//  Produces MULTIPLE styled text clips (one per line) with
//  different sizes, colors, positions — like CapCut does.
//  Same duration for all → looks like one caption.
// ═══════════════════════════════════════════════════════════════

object CaptionAutoStyler {

    private const val TAG = "CaptionStyler"

    // Base size for the "biggest" line in pixels at 720p reference
    private const val BASE_FONT_SIZE = 40

    // ═══════════════════════════════════════════════════════════
    //  WORD TYPE
    // ═══════════════════════════════════════════════════════════

    enum class WordType(val score: Int) {
        FILL(10),
        NORMAL(30),
        IMPORTANT(60),
        EMPHASIS(85),
        QUESTION(70),
        EXCLAIM(95),
        NUMBER(80),
        CAPS(90)
    }

    data class WordToken(
        val text: String,
        val type: WordType,
        val score: Int,
        val position: Int
    )

    // ═══════════════════════════════════════════════════════════
    //  OUTPUT — one styled line = one text clip
    // ═══════════════════════════════════════════════════════════

    data class StyledClip(
        val text: String,
        val fontSize: Int,           // absolute pixels
        val color: Long,
        val positionY: Float,        // 0..100 (%)
        val fontFamily: String,
        val glowEnabled: Boolean,
        val glowColor: Long,
        val glowRadius: Float,
        val strokeEnabled: Boolean,
        val strokeWidth: Float,
        val strokeColor: Long,
        val animation: String,
        val animationDuration: Float,
        val role: String             // "before" | "emphasis" | "after"
    )

    // ═══════════════════════════════════════════════════════════
    //  MAIN ENTRY — returns List<StyledClip>
    // ═══════════════════════════════════════════════════════════

    fun styleLines(rawText: String): List<StyledClip> {
        if (rawText.isBlank()) return emptyList()

        val words = rawText.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()

        // 1. Classify
        val tokens = classify(words)

        // 2. Detect mood
        val mood = detectMood(tokens)

        // 3. Group into lines (text + sizeTier + role)
        val lines = groupIntoLines(tokens)

        Log.e(
            TAG,
            "Segment \"${rawText.take(40)}\" → ${lines.size} lines: " +
                    lines.joinToString(" | ") { "${it.text}(${it.sizeTier})" }
        )

        // 4. Compute positions based on line count
        val positions = computePositions(lines.size)

        // 5. Build StyledClips
        return lines.mapIndexed { idx, line ->
            val fontSize = (BASE_FONT_SIZE * line.sizeTier).toInt()
                .coerceIn(18, 220)

            // Glow / stroke scale with font size
            val glowRadius = if (!mood.glowEnabled) 0f else when {
                fontSize >= 70 -> mood.glowRadius * 1.3f
                fontSize >= 50 -> mood.glowRadius
                else -> mood.glowRadius * 0.7f
            }
            val strokeWidth = if (!mood.strokeEnabled) 0f else when {
                fontSize >= 70 -> mood.strokeWidth * 1.2f
                fontSize >= 50 -> mood.strokeWidth
                else -> mood.strokeWidth * 0.8f
            }

            val lineColor = when (line.role) {
                "emphasis" -> mood.mainColor
                else -> mood.secondaryColor
            }

            StyledClip(
                text = line.text,
                fontSize = fontSize,
                color = lineColor,
                positionY = positions[idx],
                fontFamily = mood.fontFamily,
                glowEnabled = mood.glowEnabled,
                glowColor = mood.glowColor,
                glowRadius = glowRadius,
                strokeEnabled = mood.strokeEnabled,
                strokeWidth = strokeWidth,
                strokeColor = mood.strokeColor,
                animation = mood.animation,
                animationDuration = 0.5f,
                role = line.role
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  POSITION HELPERS — vertical stacking, no overlap
    // ═══════════════════════════════════════════════════════════

    private fun computePositions(count: Int): List<Float> = when (count) {
        1 -> listOf(50f)
        2 -> listOf(40f, 62f)
        3 -> listOf(28f, 52f, 76f)
        4 -> listOf(22f, 42f, 62f, 82f)
        else -> {
            // Even spread from 20 to 80
            val step = 60f / (count - 1).coerceAtLeast(1)
            (0 until count).map { 20f + it * step }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  CLASSIFICATION
    // ═══════════════════════════════════════════════════════════

    private fun classify(words: List<String>): List<WordToken> {
        return words.mapIndexed { idx, word ->
            val type = detectType(word, idx, words.size)
            WordToken(
                text = word,
                type = type,
                score = (type.score + (word.length * 2).coerceAtMost(15))
                    .coerceIn(0, 100),
                position = idx
            )
        }
    }

    private fun detectType(word: String, position: Int, total: Int): WordType {
        val clean = word.trim().lowercase()
            .replace(Regex("[.,!?;:…\"']"), "")

        if (CaptionStyleLibrary.NUMBER_REGEX.matches(clean)) return WordType.NUMBER
        if (word.endsWith("?")) return WordType.QUESTION
        if (word.endsWith("!")) return WordType.EXCLAIM

        if (word.length >= 3 &&
            word == word.uppercase() &&
            word.any { it.isLetter() }
        ) return WordType.CAPS

        if (clean in CaptionStyleLibrary.FILL_WORDS) return WordType.FILL
        if (clean in CaptionStyleLibrary.EMPHASIS_WORDS) return WordType.EMPHASIS

        val isFirstOrLast = position == 0 || position == total - 1
        if (clean.length >= 6 || (isFirstOrLast && clean.length >= 5)) {
            return WordType.IMPORTANT
        }
        if (clean in CaptionStyleLibrary.ACTION_WORDS) return WordType.IMPORTANT

        return WordType.NORMAL
    }

    // ═══════════════════════════════════════════════════════════
    //  MOOD
    // ═══════════════════════════════════════════════════════════

    data class MoodPreset(
        val fontFamily: String,
        val mainColor: Long,
        val secondaryColor: Long,
        val glowEnabled: Boolean,
        val glowColor: Long,
        val glowRadius: Float,
        val strokeEnabled: Boolean,
        val strokeColor: Long,
        val strokeWidth: Float,
        val animation: String
    )

    private val MOOD_NEUTRAL = MoodPreset(
        fontFamily = "Arial",
        mainColor = 0xFFFFFFFF,
        secondaryColor = 0xFFDDDDDD,
        glowEnabled = false,
        glowColor = 0x00000000,
        glowRadius = 0f,
        strokeEnabled = false,
        strokeColor = 0xFF000000,
        strokeWidth = 0f,
        animation = "fadeUp"
    )

    private val MOOD_HYPE = MoodPreset(
        fontFamily = "Impact",
        mainColor = 0xFFFFFFFF,
        secondaryColor = 0xFFEEEEEE,
        glowEnabled = true,
        glowColor = 0xFFFFD166,
        glowRadius = 30f,
        strokeEnabled = true,
        strokeColor = 0xFF000000,
        strokeWidth = 2.5f,
        animation = "popIn"
    )

    private val MOOD_EMOTION = MoodPreset(
        fontFamily = "Georgia",
        mainColor = 0xFFFFE5F0,
        secondaryColor = 0xFFFFCCDD,
        glowEnabled = true,
        glowColor = 0xFFFF4F8B,
        glowRadius = 25f,
        strokeEnabled = false,
        strokeColor = 0xFF000000,
        strokeWidth = 0f,
        animation = "fadeUp"
    )

    private val MOOD_CINEMATIC = MoodPreset(
        fontFamily = "Georgia",
        mainColor = 0xFFFFFFFF,
        secondaryColor = 0xFFCCCCCC,
        glowEnabled = false,
        glowColor = 0x00000000,
        glowRadius = 0f,
        strokeEnabled = false,
        strokeColor = 0xFF000000,
        strokeWidth = 0f,
        animation = "fadeIn"
    )

    private fun detectMood(tokens: List<WordToken>): MoodPreset {
        var emphasis = 0
        var exclaim = 0
        var caps = 0
        var question = 0
        var hasEmotion = false

        val emotionSet = setOf(
            "love", "loved", "hate", "heart", "miss", "missing",
            "happy", "sad", "cry", "cried", "feel", "feeling",
            "amazing", "beautiful", "wonderful", "perfect",
            "worried", "stressed", "tired"
        )

        tokens.forEach { t ->
            when (t.type) {
                WordType.EMPHASIS -> emphasis++
                WordType.EXCLAIM -> exclaim++
                WordType.CAPS -> caps++
                WordType.QUESTION -> question++
                else -> {}
            }
            val w = t.text.lowercase().replace(Regex("[.,!?;:…\"']"), "")
            if (w in emotionSet) hasEmotion = true
        }

        val score = emphasis * 2 + exclaim * 3 + caps * 2 + question
        return when {
            exclaim >= 1 || caps >= 2 || score >= 6 -> MOOD_HYPE
            hasEmotion && score >= 2 -> MOOD_EMOTION
            question >= 1 -> MOOD_HYPE
            score >= 2 -> MOOD_CINEMATIC
            else -> MOOD_NEUTRAL
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  LINE GROUPING
    // ═══════════════════════════════════════════════════════════

    private data class StyledLine(
        val text: String,
        val sizeTier: Float,
        val role: String
    )

    private fun groupIntoLines(tokens: List<WordToken>): List<StyledLine> {
        val total = tokens.size

        // Single word
        if (total == 1) {
            return listOf(
                StyledLine(
                    tokens[0].text,
                    CaptionStyleLibrary.SIZE_HUGE,
                    "emphasis"
                )
            )
        }

        val sorted = tokens.sortedByDescending { it.score }
        val topToken = sorted.first()
        val topIdx = topToken.position
        val hasStrongEmphasis = topToken.score >= 70

        if (!hasStrongEmphasis) return splitEvenly(tokens)

        val beforeWords = tokens.subList(0, topIdx)
        val afterWords = tokens.subList(topIdx + 1, total)

        val secondTop = sorted.getOrNull(1)
        val combineSecond = secondTop != null &&
                secondTop.score >= 60 &&
                Math.abs(secondTop.position - topIdx) == 1

        val lines = mutableListOf<StyledLine>()

        // Before line (small)
        if (beforeWords.isNotEmpty()) {
            lines.add(
                StyledLine(
                    beforeWords.joinToString(" ") { it.text },
                    CaptionStyleLibrary.SIZE_SMALL,
                    "before"
                )
            )
        }

        // Emphasis line(s)
        if (combineSecond && secondTop != null) {
            val pair = if (secondTop.position > topIdx)
                listOf(topToken, secondTop)
            else listOf(secondTop, topToken)

            lines.add(
                StyledLine(
                    pair.joinToString(" ") { it.text },
                    CaptionStyleLibrary.SIZE_HUGE,
                    "emphasis"
                )
            )

            val afterFiltered = afterWords.filter { it.position != secondTop.position }
            if (afterFiltered.isNotEmpty()) {
                lines.add(
                    StyledLine(
                        afterFiltered.joinToString(" ") { it.text },
                        CaptionStyleLibrary.SIZE_SMALL,
                        "after"
                    )
                )
            }
        } else {
            lines.add(
                StyledLine(
                    topToken.text,
                    CaptionStyleLibrary.SIZE_HUGE,
                    "emphasis"
                )
            )
            if (afterWords.isNotEmpty()) {
                lines.add(
                    StyledLine(
                        afterWords.joinToString(" ") { it.text },
                        CaptionStyleLibrary.SIZE_SMALL,
                        "after"
                    )
                )
            }
        }

        return lines
    }

    private fun splitEvenly(tokens: List<WordToken>): List<StyledLine> {
        val half = (tokens.size + 1) / 2
        val first = tokens.subList(0, half).joinToString(" ") { it.text }
        val second = tokens.subList(half, tokens.size).joinToString(" ") { it.text }

        val lines = mutableListOf<StyledLine>()
        if (first.isNotBlank()) {
            lines.add(
                StyledLine(first, CaptionStyleLibrary.SIZE_LARGE, "first")
            )
        }
        if (second.isNotBlank()) {
            lines.add(
                StyledLine(second, CaptionStyleLibrary.SIZE_MEDIUM, "second")
            )
        }
        return lines
    }
}
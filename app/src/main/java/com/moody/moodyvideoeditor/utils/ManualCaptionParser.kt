package com.moody.moodyvideoeditor.utils

import android.util.Log

// ═══════════════════════════════════════════════════════════════
//  MANUAL CAPTION PARSER
//
//  Format 1 — Parenthesis timestamps (NEW):
//    (0:00) caption 1 text
//    (0:04) caption 2 text
//    (0:08) caption 3 text
//    Each (MM:SS) is the START of a caption.
//    Next (MM:SS) is the END of the previous caption.
//    Each marker starts a caption; the final caption defaults to 3 seconds.
//
//  Format 2 — Line by line:
//    0;6 hello world
//    6-12 second caption
//    0:00 → 0:06 more text
// ═══════════════════════════════════════════════════════════════

object ManualCaptionParser {

    private const val TAG = "ManualCaptionParser"

    data class ParsedCaption(
        val startMs: Long,
        val endMs: Long,
        val text: String
    ) {
        val durationMs: Long get() = (endMs - startMs).coerceAtLeast(100L)
    }

    // ═══════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════

    @Suppress("UNUSED_PARAMETER")
    fun parse(
        input: String,
        videoDurationMs: Long = 0L
    ): List<ParsedCaption> {
        if (input.isBlank()) return emptyList()

        val result = if (hasExtendedTimestampSyntax(input)) {
            Log.e(TAG, "Using extended timestamp format")
            parseExtendedFormats(input)
        } else {
            // Detect the existing (MM:SS) format before trying line-by-line ranges.
            val hasParenTimestamps = Regex("""\(\s*\d+:\d+\s*\)""").containsMatchIn(input)

            if (hasParenTimestamps) {
                Log.e(TAG, "Using parenthesis format")
                parseParenthesisFormat(input)
            } else {
                Log.e(TAG, "Using line-by-line format")
                parseLineByLine(input)
            }
        }

        val cleanedResult = result.mapNotNull { caption ->
            val cleanedText = cleanCaptionText(caption.text)
            if (cleanedText.isBlank()) null else caption.copy(text = cleanedText)
        }

        Log.e(TAG, "Parsed ${cleanedResult.size} captions")
        cleanedResult.forEach { cap ->
            Log.e(
                TAG,
                "  ${cap.startMs}ms → ${cap.endMs}ms: \"${cap.text.take(40)}\""
            )
        }

        return cleanedResult
    }

    private data class CaptionCue(
        val startMs: Long,
        val endMs: Long?,
        val text: String
    )

    private val TIME_PATTERN =
        """\d{1,3}(?:\s*[:.-]\s*\d{1,2}){1,2}(?:\s*,\s*\d{1,3})?"""
    private val RANGE_HEADER_REGEX = Regex(
        """^\s*(?:\d+\s+)?[\[(]?\s*($TIME_PATTERN)\s*[\])]?\s*(-->|->|→|–|—|\bto\b|;|-)\s*[\[(]?\s*($TIME_PATTERN)\s*[\])]?(?:\s+(.*))?\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val SINGLE_HEADER_REGEX = Regex(
        """^\s*[\[(]?\s*($TIME_PATTERN)\s*[\])]?(.*)$"""
    )
    private val STANDALONE_TIMESTAMP_REGEX = Regex(
        """^\s*[\[(]?\s*$TIME_PATTERN\s*[\])]?\s*$"""
    )
    private val NOISE_TAG_REGEX = Regex(
        """(?i)\[(?:music|applause|laughter|laughing|laughs?|sighs?|inaudible|unintelligible|noise|sound effects?)\]|\((?:music|applause|laughter|laughing|laughs?|sighs?|inaudible|unintelligible|noise|sound effects?)\)"""
    )

    private fun hasExtendedTimestampSyntax(input: String): Boolean {
        if (input.contains("-->")) return true

        return input.lineSequence().any { line ->
            val trimmed = line.trim()
            STANDALONE_TIMESTAMP_REGEX.matches(trimmed) ||
                    Regex(
                        """^\s*[\[(]?\s*\d{1,3}(?:\s*[.:,-]\s*\d{1,3}){1,3}"""
                    ).containsMatchIn(line) ||
                    Regex("""^\s*\[\s*\d+\s*:\s*\d""").containsMatchIn(line) ||
                    Regex("""^\s*\(\s*\d+\s*:\s+\d""").containsMatchIn(line) ||
                    Regex("""^\s*[\[(]\s*\d+\s*:\s*\d+\s*:\s*\d+""")
                        .containsMatchIn(line) ||
                    Regex("""[\[(]\s*\d+\s*[-.]\s*\d+""").containsMatchIn(line) ||
                    Regex("""^\d{1,3}(?::\d{1,2}){1,2}\s*[-–—]\s*\D.+$""")
                        .matches(trimmed)
        }
    }

    private fun parseExtendedFormats(input: String): List<ParsedCaption> {
        val cues = mutableListOf<CaptionCue>()
        input.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank() || line.matches(Regex("""\d+"""))) return@forEach

            val rangeMatch = RANGE_HEADER_REGEX.matchEntire(line)
            if (rangeMatch != null) {
                val start = parseExtendedTime(rangeMatch.groupValues[1])
                    ?: return@forEach
                val end = parseExtendedTime(rangeMatch.groupValues[3])
                    ?: return@forEach
                val inlineText = rangeMatch.groupValues.getOrNull(4).orEmpty().trim()
                cues += CaptionCue(start, end, inlineText)
                return@forEach
            }

            val singleMatch = SINGLE_HEADER_REGEX.matchEntire(line)
            if (singleMatch != null) {
                val start = parseExtendedTime(singleMatch.groupValues[1])
                if (start != null) {
                    var trailingText = singleMatch.groupValues[2].trim()
                    trailingText = trailingText
                        .replace(Regex("""^[\])}]+"""), "")
                        .trim()
                    trailingText = trailingText
                        .replace(Regex("""^(?:[-–—]\s*)"""), "")
                        .trim()
                    cues += CaptionCue(start, null, trailingText)
                    return@forEach
                }
            }

            if (cues.isNotEmpty()) {
                val previous = cues.last()
                cues[cues.lastIndex] = previous.copy(
                    text = listOf(previous.text, line).filter { it.isNotBlank() }.joinToString(" ")
                )
            }
        }

        return cues.mapIndexedNotNull { index, cue ->
            val nextStart = cues.getOrNull(index + 1)?.startMs
            val end = cue.endMs ?: nextStart ?: cue.startMs + 3000L
            if (end <= cue.startMs || cue.text.isBlank()) {
                null
            } else {
                ParsedCaption(cue.startMs, end, cue.text)
            }
        }
    }

    private fun parseExtendedTime(value: String): Long? {
        val normalized = value.trim().replace(Regex("""\s+"""), "")
        val pieces = normalized.split(Regex("""[:,.-]"""))
        if (pieces.any { it.isEmpty() }) return null

        return try {
            when (pieces.size) {
                2 -> {
                    val minutes = pieces[0].toLongOrNull() ?: return null
                    val seconds = pieces[1].toLongOrNull() ?: return null
                    if (seconds !in 0..59) return null
                    (minutes * 60L + seconds) * 1000L
                }
                3 -> {
                    val hours = pieces[0].toLongOrNull() ?: return null
                    val minutes = pieces[1].toLongOrNull() ?: return null
                    val seconds = pieces[2].toLongOrNull() ?: return null
                    if (minutes !in 0..59 || seconds !in 0..59) return null
                    (hours * 3600L + minutes * 60L + seconds) * 1000L
                }
                4 -> {
                    val hours = pieces[0].toLongOrNull() ?: return null
                    val minutes = pieces[1].toLongOrNull() ?: return null
                    val seconds = pieces[2].toLongOrNull() ?: return null
                    val millis = pieces[3].toLongOrNull() ?: return null
                    if (minutes !in 0..59 || seconds !in 0..59 || millis !in 0..999) return null
                    (hours * 3600L + minutes * 60L + seconds) * 1000L + millis
                }
                else -> null
            }
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun cleanCaptionText(text: String): String =
        text.replace(NOISE_TAG_REGEX, "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    // ═══════════════════════════════════════════════════════════
    //  FORMAT 1 — (MM:SS) TEXT (MM:SS) TEXT ...
    // ═══════════════════════════════════════════════════════════

    private fun parseParenthesisFormat(input: String): List<ParsedCaption> {
        val markerRegex = Regex("""\(\s*(\d+)\s*:\s*(\d+)\s*\)""")
        val matches = markerRegex.findAll(input).toList()

        if (matches.isEmpty()) return emptyList()

        // Special case: only ONE marker → treat as start of a single caption
        if (matches.size == 1) {
            val only = matches[0]
            val startMs = parseMinSec(
                only.groupValues[1],
                only.groupValues[2]
            )
            val text = input.substring(only.range.last + 1).trim()
            if (text.isBlank()) return emptyList()
            return listOf(ParsedCaption(startMs, startMs + 3000L, text))
        }

        val result = mutableListOf<ParsedCaption>()

        // For each marker except last — text is between this and next
        for (i in 0 until matches.size - 1) {
            val current = matches[i]
            val next = matches[i + 1]

            val startMs = parseMinSec(
                current.groupValues[1],
                current.groupValues[2]
            )
            val endMs = parseMinSec(
                next.groupValues[1],
                next.groupValues[2]
            )

            val textStart = current.range.last + 1
            val textEnd = next.range.first
            if (textEnd <= textStart) continue

            val text = input.substring(textStart, textEnd)
                .trim()
                .replace(Regex("\\s+"), " ")

            if (text.isBlank()) continue
            if (endMs <= startMs) continue

            result.add(ParsedCaption(startMs, endMs, text))
        }

        // Last caption — text after last marker
        val last = matches.last()
        val lastStartMs = parseMinSec(
            last.groupValues[1],
            last.groupValues[2]
        )
        val lastText = input.substring(last.range.last + 1)
            .trim()
            .replace(Regex("\\s+"), " ")

        if (lastText.isNotBlank()) {
            result.add(ParsedCaption(lastStartMs, lastStartMs + 3000L, lastText))
        }

        return result
    }

    private fun parseMinSec(mStr: String, sStr: String): Long {
        val m = mStr.toLongOrNull() ?: 0L
        val s = sStr.toLongOrNull() ?: 0L
        return (m * 60L + s) * 1000L
    }

    // ═══════════════════════════════════════════════════════════
    //  FORMAT 2 — LINE BY LINE
    // ═══════════════════════════════════════════════════════════

    private val LINE_REGEX = Regex(
        """^\s*\[?\s*([\d:.]+)\s*(?:;|-|to|–|—|→|->)\s*([\d:.]+)\s*\]?\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private fun parseLineByLine(input: String): List<ParsedCaption> {
        val lines = input.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val result = mutableListOf<ParsedCaption>()
        lines.forEach { line ->
            parseLineByLineItem(line)?.let { result.add(it) }
        }
        return result
    }

    private fun parseLineByLineItem(line: String): ParsedCaption? {
        val match = LINE_REGEX.find(line) ?: run {
            Log.w(TAG, "Skipped: \"$line\"")
            return null
        }

        val startStr = match.groupValues[1]
        val endStr = match.groupValues[2]
        val text = match.groupValues[3].trim()

        val startMs = parseTime(startStr) ?: return null
        val endMs = parseTime(endStr) ?: return null

        if (text.isBlank()) return null
        if (endMs <= startMs) return null

        return ParsedCaption(startMs, endMs, text)
    }

    private fun parseTime(str: String): Long? {
        return try {
            val clean = str.trim()
            when {
                clean.contains(":") -> {
                    val parts = clean.split(":")
                    when (parts.size) {
                        2 -> {
                            val m = parts[0].toLongOrNull() ?: return null
                            val s = parts[1].toDoubleOrNull() ?: return null
                            ((m * 60) + s).toLong() * 1000L
                        }
                        3 -> {
                            val h = parts[0].toLongOrNull() ?: return null
                            val m = parts[1].toLongOrNull() ?: return null
                            val s = parts[2].toDoubleOrNull() ?: return null
                            ((h * 3600) + (m * 60) + s).toLong() * 1000L
                        }
                        else -> null
                    }
                }
                else -> {
                    val sec = clean.toDoubleOrNull() ?: return null
                    (sec * 1000.0).toLong()
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
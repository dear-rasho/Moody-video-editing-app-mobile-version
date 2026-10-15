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
//    Last caption uses the video end time.
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

    fun parse(
        input: String,
        videoDurationMs: Long = 0L
    ): List<ParsedCaption> {
        if (input.isBlank()) return emptyList()

        // Detect (MM:SS) format
        val hasParenTimestamps = Regex("""\(\s*\d+:\d+\s*\)""").containsMatchIn(input)

        val result = if (hasParenTimestamps) {
            Log.e(TAG, "Using parenthesis format")
            parseParenthesisFormat(input, videoDurationMs)
        } else {
            Log.e(TAG, "Using line-by-line format")
            parseLineByLine(input)
        }

        Log.e(TAG, "Parsed ${result.size} captions")
        result.forEach { cap ->
            Log.e(
                TAG,
                "  ${cap.startMs}ms → ${cap.endMs}ms: \"${cap.text.take(40)}\""
            )
        }

        return result
    }

    // ═══════════════════════════════════════════════════════════
    //  FORMAT 1 — (MM:SS) TEXT (MM:SS) TEXT ...
    // ═══════════════════════════════════════════════════════════

    private fun parseParenthesisFormat(
        input: String,
        videoDurationMs: Long
    ): List<ParsedCaption> {
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
            val endMs = if (videoDurationMs > startMs) videoDurationMs
            else startMs + 3000L
            return listOf(ParsedCaption(startMs, endMs, text))
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
            val lastEndMs = if (videoDurationMs > lastStartMs + 500L) {
                videoDurationMs
            } else {
                lastStartMs + 3000L
            }
            result.add(ParsedCaption(lastStartMs, lastEndMs, lastText))
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
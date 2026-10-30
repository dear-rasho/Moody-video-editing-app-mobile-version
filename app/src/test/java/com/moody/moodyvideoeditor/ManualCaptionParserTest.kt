package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.utils.ManualCaptionParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualCaptionParserTest {
    @Test
    fun parsesSrtRangesWithMillisecondPrecision() {
        assertEquals(
            listOf(
                ManualCaptionParser.ParsedCaption(80_500L, 85_000L, "Hello there"),
                ManualCaptionParser.ParsedCaption(90_000L, 93_000L, "Bye")
            ),
            ManualCaptionParser.parse(
                "1\n00:01:20,500 --> 00:01:25,000\n[music] Hello there\n\n" +
                        "2\n00:01:30,000 --> 00:01:33,000\nBye"
            )
        )
    }

    @Test
    fun parsesSingleTimestampDashLinesAndAddsThreeSecondsToFinalCaption() {
        assertEquals(
            listOf(
                ManualCaptionParser.ParsedCaption(60_000L, 65_000L, "First"),
                ManualCaptionParser.ParsedCaption(65_000L, 68_000L, "Second")
            ),
            ManualCaptionParser.parse("00:01 - First\n1:05 - Second")
        )
    }

    @Test
    fun parsesBracketAndParenthesisTimesWithDotAndDashSeparators() {
        assertEquals(
            listOf(
                ManualCaptionParser.ParsedCaption(2_000L, 9_000L, "Hello"),
                ManualCaptionParser.ParsedCaption(9_000L, 12_000L, "World")
            ),
            ManualCaptionParser.parse("(00-02) Hello\n[ 00 : 09] [applause] World")
        )
        assertEquals(
            listOf(
                ManualCaptionParser.ParsedCaption(1_000L, 80_000L, "First"),
                ManualCaptionParser.ParsedCaption(80_000L, 83_000L, "Last")
            ),
            ManualCaptionParser.parse("0.01 - First\n00.01.20 - Last")
        )
    }

    @Test
    fun attachesTimestampOnlyTranscriptLinesAndContinuationText() {
        assertEquals(
            listOf(
                ManualCaptionParser.ParsedCaption(0L, 5_000L, "Hello world. More words."),
                ManualCaptionParser.ParsedCaption(5_000L, 8_000L, "Next caption.")
            ),
            ManualCaptionParser.parse(
                "0:00\nHello world.\nMore words.\n0:05\n[music] Next caption."
            )
        )
    }

    @Test
    fun preservesExistingLineRangeFormat() {
        assertEquals(
            listOf(
                ManualCaptionParser.ParsedCaption(0L, 6_000L, "Hello"),
                ManualCaptionParser.ParsedCaption(6_000L, 12_000L, "Again")
            ),
            ManualCaptionParser.parse("0;6 Hello\n6-12 Again")
        )
    }

    @Test
    fun keepsParenthesisFormatAndUsesThreeSecondsForItsFinalCaption() {
        assertEquals(
            listOf(
                ManualCaptionParser.ParsedCaption(0L, 4_000L, "First"),
                ManualCaptionParser.ParsedCaption(4_000L, 7_000L, "Last")
            ),
            ManualCaptionParser.parse("(0:00) First\n(0:04) Last", videoDurationMs = 60_000L)
        )
    }
}

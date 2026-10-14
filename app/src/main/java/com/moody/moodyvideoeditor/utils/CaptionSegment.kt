package com.moody.moodyvideoeditor.utils

data class CaptionSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class TranscriptionResult(
    val language: String,
    val segments: List<CaptionSegment>
) {
    val isEmpty: Boolean get() = segments.isEmpty()
}
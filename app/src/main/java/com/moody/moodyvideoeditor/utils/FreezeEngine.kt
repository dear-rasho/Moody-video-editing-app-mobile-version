package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.EditorClip
import java.util.UUID

/**
 * Mirrors js/features/freeze.js
 * Freeze = duplicate a frame at playhead for N seconds.
 * Implemented by inserting a "still" clip — actual frame grab happens in export.
 */
object FreezeEngine {

    /**
     * Creates a still-image-style clip that represents a frozen frame.
     * Uses the source URI with sourceStart == sourceEnd (1 frame).
     */
    fun makeFreezeClip(source: EditorClip, atTimeMs: Long, durationMs: Long): EditorClip {
        val sourceMid = (source.sourceStartMs + source.sourceEndMs) / 2L
        return source.copy(
            id = UUID.randomUUID().toString(),
            name = "❄️ Freeze ${durationMs / 1000}s",
            sourceStartMs = sourceMid,
            sourceEndMs = (sourceMid + 33L).coerceAtMost(source.sourceEndMs),  // ~1 frame
            timelineStartMs = atTimeMs,
            speed = 1.0f,
            linkedId = null
        )
    }
}
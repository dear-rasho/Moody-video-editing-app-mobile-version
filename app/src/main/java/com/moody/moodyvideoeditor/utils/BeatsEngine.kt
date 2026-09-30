package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.BeatsState

/**
 * Mirrors js/codebase/beatsEngine.js
 * NOTE: Full beat detection requires decoding audio samples.
 * For now: stub returns synthetic beats at 120 BPM.
 * Replace with real MediaCodec decode later.
 */
object BeatsEngine {

    /** Synthetic beat detection — 120 BPM = 500ms gap */
    fun detectSynthetic(durationMs: Long, filter: String): BeatsState {
        val gap = 500L
        val all = (0..durationMs step gap).toList()
        val filtered = applyFilter(all, filter)
        return BeatsState(
            detected = filtered.isNotEmpty(),
            count = filtered.size,
            filter = filter,
            beatTimesMs = filtered
        )
    }

    private fun applyFilter(list: List<Long>, filter: String): List<Long> {
        return when (filter) {
            "hard" -> list.filterIndexed { i, _ -> i % 4 == 0 }
            "medium" -> list.filterIndexed { i, _ -> i % 4 == 1 || i % 4 == 3 }
            "soft" -> list.filterIndexed { i, _ -> i % 4 == 2 }
            "hard,med" -> list.filterIndexed { i, _ -> i % 2 == 0 }
            "med,soft" -> list.filterIndexed { i, _ -> i % 2 == 1 }
            else -> list
        }
    }

    fun clear(): BeatsState = BeatsState()
}
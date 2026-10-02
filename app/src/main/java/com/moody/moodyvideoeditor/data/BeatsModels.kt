package com.moody.moodyvideoeditor.data

data class BeatsState(
    val detected: Boolean = false,
    val count: Int = 0,
    val filter: String = "all",
    val beatTimesMs: List<Long> = emptyList(),
    // 🆕 Parallel list to beatTimesMs — value 0..1 (0=weak, 1=strong)
    val beatStrengths: List<Float> = emptyList()
)

object BeatsLibrary {
    val FILTERS = listOf(
        "all" to "All Beats",
        "hard" to "🔴 Hard",
        "medium" to "🟡 Medium",
        "soft" to "🟢 Soft",
        "hard,med" to "🔴🟡 Hard+Med",
        "med,soft" to "🟡🟢 Med+Soft"
    )
}
package com.moody.moodyvideoeditor.data

data class BeatsState(
    val detected: Boolean = false,
    val count: Int = 0,
    val filter: String = "all",           // all|hard|medium|soft|hard,med|med,soft
    val beatTimesMs: List<Long> = emptyList()
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
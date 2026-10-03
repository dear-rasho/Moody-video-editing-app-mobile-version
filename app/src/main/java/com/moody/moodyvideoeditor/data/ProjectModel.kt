package com.moody.moodyvideoeditor.data

// Project metadata (metadata only — full state saved separately).
data class ProjectMeta(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val thumbnailPath: String?,   // absolute file path or null
    val clipCount: Int,
    val durationMs: Long
) {
    val displayDate: String
        get() {
            val diff = System.currentTimeMillis() - updatedAt
            return when {
                diff < 60_000L -> "just now"
                diff < 3600_000L -> "${diff / 60_000}m ago"
                diff < 86_400_000L -> "${diff / 3600_000}h ago"
                diff < 604_800_000L -> "${diff / 86_400_000}d ago"
                else -> "${diff / 604_800_000}w ago"
            }
        }

    val displayDuration: String
        get() {
            val totalSec = durationMs / 1000
            val m = totalSec / 60
            val s = totalSec % 60
            return if (m > 0) "%d:%02d".format(m, s) else "${s}s"
        }
}
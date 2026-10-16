package com.moody.moodyvideoeditor.data

// ═══════════════════════════════════════════════════════════════
//  SAVED TEMPLATE — user-created timeline blueprint
//  Stores a full EditorState (with placeholder URIs) + metadata.
// ═══════════════════════════════════════════════════════════════

data class SavedTemplateMeta(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val aspectRatio: String,
    val clipCount: Int,
    val durationMs: Long,
    val imageSlotCount: Int,     // count of image placeholders
    val textSlotCount: Int,      // count of text placeholders

    // Optional: full editor state for live preview
    // Null for older templates (backward-compat) or when not needed
    val editorState: EditorState? = null
) {
    val displayDuration: String
        get() {
            val totalSec = durationMs / 1000
            val m = totalSec / 60
            val s = totalSec % 60
            return if (m > 0) "%d:%02d".format(m, s) else "${s}s"
        }

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
}
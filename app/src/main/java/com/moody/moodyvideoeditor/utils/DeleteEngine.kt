package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.EditorClip

/**
 * Mirrors js/features/delete.js
 * Deletes clip + its linked partner (video ↔ audio).
 */
object DeleteEngine {

    fun collectForDeletion(target: EditorClip, allClips: List<EditorClip>): Set<String> {
        val ids = mutableSetOf(target.id)
        if (target.linkedId != null) {
            allClips.filter { it.linkedId == target.linkedId }.forEach { ids.add(it.id) }
        }
        return ids
    }

    fun applyDeletion(allClips: List<EditorClip>, idsToRemove: Set<String>): List<EditorClip> {
        return allClips.filter { it.id !in idsToRemove }
    }
}
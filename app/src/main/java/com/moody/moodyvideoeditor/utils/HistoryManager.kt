package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.EditorClip

class HistoryManager(private val maxHistory: Int = 50) {

    private val undoStack = ArrayDeque<List<EditorClip>>()
    private val redoStack = ArrayDeque<List<EditorClip>>()

    fun push(current: List<EditorClip>) {
        undoStack.addLast(current.toList())
        if (undoStack.size > maxHistory) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo(current: List<EditorClip>): List<EditorClip>? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(current.toList())
        return undoStack.removeLast()
    }

    fun redo(current: List<EditorClip>): List<EditorClip>? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(current.toList())
        return redoStack.removeLast()
    }

    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
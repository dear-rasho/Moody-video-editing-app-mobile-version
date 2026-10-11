package com.moody.moodyvideoeditor.utils

// Backward-compatible wrapper. Real content lives in PromptChunks.
object AiEditingPrompt {
    val TEXT: String
        get() = PromptChunks.fullPrompt()
}
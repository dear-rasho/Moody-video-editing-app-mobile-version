package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.BrushGradient
import com.moody.moodyvideoeditor.data.BrushType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Shared config for the brush tool.
// Lives outside of EditorScreen so Code Mode can control it too.
data class BrushConfig(
    val isDrawingMode: Boolean = false,
    val type: BrushType = BrushType.PEN,
    val color: Long = 0xFFFF0000,
    val width: Float = 20f,
    val opacity: Float = 1f,
    val gradient: BrushGradient = BrushGradient()
)

object BrushConfigHolder {
    private val _config = MutableStateFlow(BrushConfig())
    val config: StateFlow<BrushConfig> = _config.asStateFlow()

    fun update(transform: (BrushConfig) -> BrushConfig) {
        _config.value = transform(_config.value)
    }

    fun reset() {
        _config.value = BrushConfig()
    }
}
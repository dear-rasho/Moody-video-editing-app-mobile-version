package com.moody.moodyvideoeditor.utils

data class MaskSequenceData(
    val clipId: String,
    val pattern: String,
    val staticFilePath: String?,
    val fps: Int,
    val frameCount: Int,
    val clipStartSec: Double,
    val clipEndSec: Double
)

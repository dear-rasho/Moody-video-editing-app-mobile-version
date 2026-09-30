package com.moody.moodyvideoeditor.data

data class FreezeState(
    val durationMs: Long = 2000L,       // 1s / 2s / 3s
    val atTimeMs: Long = 0L
) {
    val isActive: Boolean get() = durationMs > 0L
}
package com.moody.moodyvideoeditor.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// UI state for export progress shown to the user
data class ExportUiState(
    val isExporting: Boolean = false,
    val progress: Float = 0f,
    val message: String = "",
    val phase: String = "",
    val outputUri: String? = null,
    val errorMessage: String? = null,
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false,
    val startedAtMs: Long = 0L,
    val estimatedTotalMs: Long = 0L
)

// Singleton state holder shared between ExportService and UI
// Uses StateFlow so UI can observe live updates
object ExportStateHolder {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    // Called by service to update progress
    fun update(newState: ExportUiState) {
        _state.value = newState
    }

    // Called by service to update progress with just progress and message
    fun updateProgress(progress: Float, message: String, phase: String = "") {
        _state.value = _state.value.copy(
            isExporting = true,
            progress = progress.coerceIn(0f, 1f),
            message = message,
            phase = if (phase.isNotEmpty()) phase else _state.value.phase
        )
    }

    // Called when export starts
    fun start(estimatedTotalMs: Long = 0L) {
        _state.value = ExportUiState(
            isExporting = true,
            progress = 0f,
            message = "Starting export...",
            phase = "preparing",
            startedAtMs = System.currentTimeMillis(),
            estimatedTotalMs = estimatedTotalMs
        )
    }

    // Called when export succeeds
    fun complete(outputUri: String) {
        _state.value = _state.value.copy(
            isExporting = false,
            progress = 1f,
            message = "Export complete",
            phase = "done",
            outputUri = outputUri,
            isCompleted = true,
            errorMessage = null
        )
    }

    // Called when export fails
    fun fail(error: String) {
        _state.value = _state.value.copy(
            isExporting = false,
            message = error,
            phase = "error",
            errorMessage = error,
            isCompleted = false
        )
    }

    // Called when user cancels export
    fun cancel() {
        _state.value = _state.value.copy(
            isExporting = false,
            message = "Export cancelled",
            phase = "cancelled",
            isCancelled = true
        )
    }

    // Called by UI after user dismisses the success dialog
    fun reset() {
        _state.value = ExportUiState()
    }

    // Compute rough estimated remaining time in milliseconds
    fun estimatedRemainingMs(): Long {
        val s = _state.value
        if (!s.isExporting || s.progress <= 0.01f || s.estimatedTotalMs <= 0L) {
            return 0L
        }
        val elapsed = System.currentTimeMillis() - s.startedAtMs
        val totalEstimate = (elapsed / s.progress).toLong()
        val remaining = totalEstimate - elapsed
        return remaining.coerceAtLeast(0L)
    }
}
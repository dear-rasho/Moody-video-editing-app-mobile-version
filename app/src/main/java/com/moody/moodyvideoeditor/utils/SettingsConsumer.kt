package com.moody.moodyvideoeditor.utils

import android.content.Context
import com.moody.moodyvideoeditor.data.settings.AppSettings
import com.moody.moodyvideoeditor.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════
//  SETTINGS CONSUMER — global observable settings
//
//  Purpose:
//    Any file in the app can read current settings WITHOUT needing
//    to create its own DataStore collection coroutine.
//
//  Usage:
//    // Read (synchronous, always up-to-date):
//    val fps = SettingsConsumer.defaultFps
//
//    // Observe (Compose):
//    val settings by SettingsConsumer.current.collectAsState()
//
//    // Observe (coroutine):
//    SettingsConsumer.current.collect { settings -> ... }
// ═══════════════════════════════════════════════════════════════

object SettingsConsumer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _current = MutableStateFlow(AppSettings())
    val current: StateFlow<AppSettings> = _current.asStateFlow()

    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true

        val appContext = context.applicationContext
        val repo = SettingsRepository(appContext)

        scope.launch {
            repo.settingsFlow.collect { settings ->
                _current.value = settings
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    //  CONVENIENCE ACCESSORS — sync reads, always current
    // ─────────────────────────────────────────────────────────

    // Tab 1 — Project
    val freeLayerEnabled: Boolean get() = _current.value.freeLayerEnabled
    val defaultImageDurationMs: Int get() = _current.value.defaultImageDurationMs
    val defaultExportResolution: String get() = _current.value.defaultExportResolution
    val defaultAspectRatio: String get() = _current.value.defaultAspectRatio

    // Tab 2 — Edit
    val stepFrames: Int get() = _current.value.stepFrames
    val adjustStep: Int get() = _current.value.adjustStep
    val defaultFps: Int get() = _current.value.defaultFps
    val timeCodeFormat: String get() = _current.value.timeCodeFormat
    val snapToGrid: Boolean get() = _current.value.snapToGrid
    val hapticFeedback: Boolean get() = _current.value.hapticFeedback

    // Tab 3 — Performance
    val targetLoudness: String get() = _current.value.targetLoudness
    val reduceMotion: Boolean get() = _current.value.reduceMotion
    val autoSaveEnabled: Boolean get() = _current.value.autoSaveEnabled
    val autoSaveIntervalSec: Int get() = _current.value.autoSaveIntervalSec
    val proxyModeEnabled: Boolean get() = _current.value.proxyModeEnabled
    val exportSoundEnabled: Boolean get() = _current.value.exportSoundEnabled

    // Tab 4 — Language
    val appLanguage: String get() = _current.value.appLanguage
    val appTheme: String get() = _current.value.appTheme
    val defaultExportFolderUri: String? get() = _current.value.defaultExportFolderUri
}
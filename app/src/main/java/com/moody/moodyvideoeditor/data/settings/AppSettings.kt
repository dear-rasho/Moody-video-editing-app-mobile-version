package com.moody.moodyvideoeditor.data.settings

// ═══════════════════════════════════════════════════════════════
//  IMMUTABLE SETTINGS SNAPSHOT
//  Single source of truth for the entire settings state.
//  Used as: StateFlow<AppSettings> in ViewModel
// ═══════════════════════════════════════════════════════════════

data class AppSettings(

    // ─── Tab 1: Project ───
    val freeLayerEnabled: Boolean = SettingsDefaults.FREE_LAYER_ENABLED,
    val defaultImageDurationMs: Int = SettingsDefaults.DEFAULT_IMAGE_DURATION_MS,
    val defaultExportResolution: String = SettingsDefaults.DEFAULT_EXPORT_RESOLUTION,
    val defaultAspectRatio: String = SettingsDefaults.DEFAULT_ASPECT_RATIO,

    // ─── Tab 2: Edit ───
    val stepFrames: Int = SettingsDefaults.STEP_FRAMES,
    val adjustStep: Int = SettingsDefaults.ADJUST_STEP,
    val defaultFps: Int = SettingsDefaults.DEFAULT_FPS,
    val timeCodeFormat: String = SettingsDefaults.TIME_CODE_FORMAT,
    val snapToGrid: Boolean = SettingsDefaults.SNAP_TO_GRID,
    val hapticFeedback: Boolean = SettingsDefaults.HAPTIC_FEEDBACK,

    // ─── Tab 3: Performance ───
    val targetLoudness: String = SettingsDefaults.TARGET_LOUDNESS,
    val reduceMotion: Boolean = SettingsDefaults.REDUCE_MOTION,
    val autoSaveEnabled: Boolean = SettingsDefaults.AUTO_SAVE_ENABLED,
    val autoSaveIntervalSec: Int = SettingsDefaults.AUTO_SAVE_INTERVAL_SEC,
    val proxyModeEnabled: Boolean = SettingsDefaults.PROXY_MODE_ENABLED,
    val exportSoundEnabled: Boolean = SettingsDefaults.EXPORT_SOUND_ENABLED,

    // ─── Tab 4: Language & Appearance ───
    val appLanguage: String = SettingsDefaults.APP_LANGUAGE,
    val appTheme: String = SettingsDefaults.APP_THEME,
    val defaultExportFolderUri: String? = null
) {

    // Convenience helpers
    val isDarkThemePreferred: Boolean
        get() = appTheme == "dark"

    val isSystemTheme: Boolean
        get() = appTheme == "system"
}
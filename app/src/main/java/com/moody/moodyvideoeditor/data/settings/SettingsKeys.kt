package com.moody.moodyvideoeditor.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

// ═══════════════════════════════════════════════════════════════
//  PREFERENCE KEYS — single source of truth for DataStore keys
// ═══════════════════════════════════════════════════════════════

object SettingsKeys {

    // ─── Tab 1: Project ───
    val FREE_LAYER_ENABLED = booleanPreferencesKey("free_layer_enabled")
    val DEFAULT_IMAGE_DURATION_MS = intPreferencesKey("default_image_duration_ms")
    val DEFAULT_EXPORT_RESOLUTION = stringPreferencesKey("default_export_resolution")
    val DEFAULT_ASPECT_RATIO = stringPreferencesKey("default_aspect_ratio")

    // ─── Tab 2: Edit ───
    val STEP_FRAMES = intPreferencesKey("step_frames")
    val ADJUST_STEP = intPreferencesKey("adjust_step")
    val DEFAULT_FPS = intPreferencesKey("default_fps")
    val TIME_CODE_FORMAT = stringPreferencesKey("time_code_format")
    val SNAP_TO_GRID = booleanPreferencesKey("snap_to_grid")
    val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")

    // ─── Tab 3: Performance ───
    val TARGET_LOUDNESS = stringPreferencesKey("target_loudness")
    val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
    val AUTO_SAVE_ENABLED = booleanPreferencesKey("auto_save_enabled")
    val AUTO_SAVE_INTERVAL_SEC = intPreferencesKey("auto_save_interval_sec")
    val PROXY_MODE_ENABLED = booleanPreferencesKey("proxy_mode_enabled")
    val EXPORT_SOUND_ENABLED = booleanPreferencesKey("export_sound_enabled")

    // ─── Tab 4: Language & Appearance ───
    val APP_LANGUAGE = stringPreferencesKey("app_language")
    val APP_THEME = stringPreferencesKey("app_theme")
    val DEFAULT_EXPORT_FOLDER_URI = stringPreferencesKey("default_export_folder_uri")
}

// ═══════════════════════════════════════════════════════════════
//  DEFAULT VALUES
// ═══════════════════════════════════════════════════════════════

object SettingsDefaults {
    const val FREE_LAYER_ENABLED = true
    const val DEFAULT_IMAGE_DURATION_MS = 3000
    const val DEFAULT_EXPORT_RESOLUTION = "1080p"
    const val DEFAULT_ASPECT_RATIO = "16:9"

    const val STEP_FRAMES = 10
    const val ADJUST_STEP = 10
    const val DEFAULT_FPS = 30
    const val TIME_CODE_FORMAT = "HH:MM:SS+frame"
    const val SNAP_TO_GRID = true
    const val HAPTIC_FEEDBACK = true

    const val TARGET_LOUDNESS = "Default (-23 LUFS)"
    const val REDUCE_MOTION = false
    const val AUTO_SAVE_ENABLED = true
    const val AUTO_SAVE_INTERVAL_SEC = 30
    const val PROXY_MODE_ENABLED = false
    const val EXPORT_SOUND_ENABLED = true

    const val APP_LANGUAGE = "en"
    const val APP_THEME = "system"    // system | light | dark
    const val DEFAULT_EXPORT_FOLDER_URI = ""          // empty = system default
}
package com.moody.moodyvideoeditor.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// ═══════════════════════════════════════════════════════════════
//  SETTINGS REPOSITORY
//  Single source of truth for all app settings.
//  - Exposes ONE cold Flow<AppSettings>
//  - Exposes suspend mutation functions (one per setting)
// ═══════════════════════════════════════════════════════════════

class SettingsRepository(private val context: Context) {

    // ─────────────────────────────────────────────────────────
    //  READ — cold flow, disk-backed, resilient to IO errors
    // ─────────────────────────────────────────────────────────

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())     // fallback to defaults
            } else {
                throw throwable
            }
        }
        .map { prefs ->
            AppSettings(
                // Tab 1
                freeLayerEnabled = prefs[SettingsKeys.FREE_LAYER_ENABLED]
                    ?: SettingsDefaults.FREE_LAYER_ENABLED,
                defaultImageDurationMs = prefs[SettingsKeys.DEFAULT_IMAGE_DURATION_MS]
                    ?: SettingsDefaults.DEFAULT_IMAGE_DURATION_MS,
                defaultExportResolution = prefs[SettingsKeys.DEFAULT_EXPORT_RESOLUTION]
                    ?: SettingsDefaults.DEFAULT_EXPORT_RESOLUTION,
                defaultAspectRatio = prefs[SettingsKeys.DEFAULT_ASPECT_RATIO]
                    ?: SettingsDefaults.DEFAULT_ASPECT_RATIO,

                // Tab 2
                stepFrames = prefs[SettingsKeys.STEP_FRAMES]
                    ?: SettingsDefaults.STEP_FRAMES,
                adjustStep = prefs[SettingsKeys.ADJUST_STEP]
                    ?: SettingsDefaults.ADJUST_STEP,
                defaultFps = prefs[SettingsKeys.DEFAULT_FPS]
                    ?: SettingsDefaults.DEFAULT_FPS,
                timeCodeFormat = prefs[SettingsKeys.TIME_CODE_FORMAT]
                    ?: SettingsDefaults.TIME_CODE_FORMAT,
                snapToGrid = prefs[SettingsKeys.SNAP_TO_GRID]
                    ?: SettingsDefaults.SNAP_TO_GRID,
                hapticFeedback = prefs[SettingsKeys.HAPTIC_FEEDBACK]
                    ?: SettingsDefaults.HAPTIC_FEEDBACK,

                // Tab 3
                targetLoudness = prefs[SettingsKeys.TARGET_LOUDNESS]
                    ?: SettingsDefaults.TARGET_LOUDNESS,
                reduceMotion = prefs[SettingsKeys.REDUCE_MOTION]
                    ?: SettingsDefaults.REDUCE_MOTION,
                autoSaveEnabled = prefs[SettingsKeys.AUTO_SAVE_ENABLED]
                    ?: SettingsDefaults.AUTO_SAVE_ENABLED,
                autoSaveIntervalSec = prefs[SettingsKeys.AUTO_SAVE_INTERVAL_SEC]
                    ?: SettingsDefaults.AUTO_SAVE_INTERVAL_SEC,
                proxyModeEnabled = prefs[SettingsKeys.PROXY_MODE_ENABLED]
                    ?: SettingsDefaults.PROXY_MODE_ENABLED,
                exportSoundEnabled = prefs[SettingsKeys.EXPORT_SOUND_ENABLED]
                    ?: SettingsDefaults.EXPORT_SOUND_ENABLED,

                // Tab 4
                appLanguage = prefs[SettingsKeys.APP_LANGUAGE]
                    ?: SettingsDefaults.APP_LANGUAGE,
                appTheme = prefs[SettingsKeys.APP_THEME]
                    ?: SettingsDefaults.APP_THEME,
                defaultExportFolderUri = prefs[SettingsKeys.DEFAULT_EXPORT_FOLDER_URI]
                    ?.takeIf { it.isNotBlank() }
            )
        }

    // ─────────────────────────────────────────────────────────
    //  WRITE — bulk save (used by Save button)
    //  All writes atomic; DataStore serializes concurrent edits.
    // ─────────────────────────────────────────────────────────

    suspend fun saveAll(settings: AppSettings) {
        context.settingsDataStore.edit { prefs ->
            // Tab 1
            prefs[SettingsKeys.FREE_LAYER_ENABLED] = settings.freeLayerEnabled
            prefs[SettingsKeys.DEFAULT_IMAGE_DURATION_MS] = settings.defaultImageDurationMs
            prefs[SettingsKeys.DEFAULT_EXPORT_RESOLUTION] = settings.defaultExportResolution
            prefs[SettingsKeys.DEFAULT_ASPECT_RATIO] = settings.defaultAspectRatio

            // Tab 2
            prefs[SettingsKeys.STEP_FRAMES] = settings.stepFrames
            prefs[SettingsKeys.ADJUST_STEP] = settings.adjustStep
            prefs[SettingsKeys.DEFAULT_FPS] = settings.defaultFps
            prefs[SettingsKeys.TIME_CODE_FORMAT] = settings.timeCodeFormat
            prefs[SettingsKeys.SNAP_TO_GRID] = settings.snapToGrid
            prefs[SettingsKeys.HAPTIC_FEEDBACK] = settings.hapticFeedback

            // Tab 3
            prefs[SettingsKeys.TARGET_LOUDNESS] = settings.targetLoudness
            prefs[SettingsKeys.REDUCE_MOTION] = settings.reduceMotion
            prefs[SettingsKeys.AUTO_SAVE_ENABLED] = settings.autoSaveEnabled
            prefs[SettingsKeys.AUTO_SAVE_INTERVAL_SEC] = settings.autoSaveIntervalSec
            prefs[SettingsKeys.PROXY_MODE_ENABLED] = settings.proxyModeEnabled
            prefs[SettingsKeys.EXPORT_SOUND_ENABLED] = settings.exportSoundEnabled

            // Tab 4
            prefs[SettingsKeys.APP_LANGUAGE] = settings.appLanguage
            prefs[SettingsKeys.APP_THEME] = settings.appTheme
            settings.defaultExportFolderUri?.let {
                prefs[SettingsKeys.DEFAULT_EXPORT_FOLDER_URI] = it
            } ?: run {
                prefs.remove(SettingsKeys.DEFAULT_EXPORT_FOLDER_URI)
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    //  WRITE — single-key mutators (used for immediate changes)
    //  Example: theme applied instantly outside of staged save
    // ─────────────────────────────────────────────────────────

    suspend fun setTheme(theme: String) {
        context.settingsDataStore.edit { it[SettingsKeys.APP_THEME] = theme }
    }

    suspend fun setLanguage(language: String) {
        context.settingsDataStore.edit { it[SettingsKeys.APP_LANGUAGE] = language }
    }

    // ─────────────────────────────────────────────────────────
    //  READ — synchronous one-shot (for non-Compose contexts)
    //  Should be rarely used; prefer settingsFlow
    // ─────────────────────────────────────────────────────────

    suspend fun currentSettings(): AppSettings {
        var result = AppSettings()
        settingsFlow.collect { result = it; return@collect }
        return result
    }
}
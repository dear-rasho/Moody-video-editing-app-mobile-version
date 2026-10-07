package com.moody.moodyvideoeditor.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moody.moodyvideoeditor.data.settings.AppSettings
import com.moody.moodyvideoeditor.data.settings.SettingsRepository
import com.moody.moodyvideoeditor.utils.LocaleHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SettingsRepository(app)

    // Live persisted settings from DataStore
    val persistedSettings: StateFlow<AppSettings> = repository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AppSettings()
        )

    // User-edited draft (only persisted on Save)
    private val _draftSettings = MutableStateFlow(AppSettings())
    val draftSettings: StateFlow<AppSettings> = _draftSettings.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var lastLoadedFrom: AppSettings? = null

    fun initializeDraft() {
        val current = persistedSettings.value
        lastLoadedFrom = current
        _draftSettings.value = current
        _isDirty.value = false
    }

    // ─────────────────────────────────────────────────────────
    //  DRAFT MUTATORS
    // ─────────────────────────────────────────────────────────

    fun setFreeLayerEnabled(v: Boolean) = updateDraft { copy(freeLayerEnabled = v) }
    fun setDefaultImageDurationMs(v: Int) = updateDraft { copy(defaultImageDurationMs = v) }
    fun setDefaultExportResolution(v: String) = updateDraft { copy(defaultExportResolution = v) }
    fun setDefaultAspectRatio(v: String) = updateDraft { copy(defaultAspectRatio = v) }

    fun setStepFrames(v: Int) = updateDraft { copy(stepFrames = v) }
    fun setAdjustStep(v: Int) = updateDraft { copy(adjustStep = v) }
    fun setDefaultFps(v: Int) = updateDraft { copy(defaultFps = v) }
    fun setTimeCodeFormat(v: String) = updateDraft { copy(timeCodeFormat = v) }
    fun setSnapToGrid(v: Boolean) = updateDraft { copy(snapToGrid = v) }
    fun setHapticFeedback(v: Boolean) = updateDraft { copy(hapticFeedback = v) }

    fun setTargetLoudness(v: String) = updateDraft { copy(targetLoudness = v) }
    fun setReduceMotion(v: Boolean) = updateDraft { copy(reduceMotion = v) }
    fun setAutoSaveEnabled(v: Boolean) = updateDraft { copy(autoSaveEnabled = v) }
    fun setAutoSaveIntervalSec(v: Int) = updateDraft { copy(autoSaveIntervalSec = v) }
    fun setProxyModeEnabled(v: Boolean) = updateDraft { copy(proxyModeEnabled = v) }
    fun setExportSoundEnabled(v: Boolean) = updateDraft { copy(exportSoundEnabled = v) }

    fun setAppLanguage(v: String) = updateDraft { copy(appLanguage = v) }
    fun setAppTheme(v: String) = updateDraft { copy(appTheme = v) }
    fun setDefaultExportFolderUri(v: String?) = updateDraft { copy(defaultExportFolderUri = v) }

    private inline fun updateDraft(transform: AppSettings.() -> AppSettings) {
        val newDraft = _draftSettings.value.transform()
        _draftSettings.value = newDraft
        _isDirty.value = newDraft != lastLoadedFrom
    }

    // ─────────────────────────────────────────────────────────
    //  SAVE — persists + applies language immediately
    // ─────────────────────────────────────────────────────────

    fun save() {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val draft = _draftSettings.value
                repository.saveAll(draft)
                lastLoadedFrom = draft
                _isDirty.value = false
                _errorMessage.value = null

                // Apply language immediately
                LocaleHelper.applyLanguage(draft.appLanguage)

            } catch (t: Throwable) {
                _errorMessage.value = "Failed to save: ${t.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun cancel() {
        _draftSettings.value = persistedSettings.value
        _isDirty.value = false
        _errorMessage.value = null
    }

    fun resetToDefaults() {
        updateDraft { AppSettings() }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
package com.moody.moodyvideoeditor

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.Preferences
import com.moody.moodyvideoeditor.data.settings.SettingsDefaults
import com.moody.moodyvideoeditor.data.settings.SettingsKeys
import com.moody.moodyvideoeditor.data.settings.settingsDataStore
import com.moody.moodyvideoeditor.utils.SettingsConsumer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════
//  APPLICATION CLASS
//  Runs once when app process starts.
//  1. Loads saved language from DataStore → applies to app
//  2. Starts SettingsConsumer → makes settings globally observable
// ═══════════════════════════════════════════════════════════════

class MoodyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Ensure empty LocaleList initially (system default)
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.getEmptyLocaleList()
        )

        // 2. Start global settings snapshot (must be BEFORE any UI)
        SettingsConsumer.start(this)

        // 3. Load saved language from DataStore on background thread
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs: Preferences = settingsDataStore.data.first()
                val lang = prefs[SettingsKeys.APP_LANGUAGE]
                    ?: SettingsDefaults.APP_LANGUAGE
                if (lang.isNotBlank()) {
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(lang)
                    )
                }
            } catch (_: Throwable) {
                // fail silently — system default used
            }
        }
    }
}
package com.moody.moodyvideoeditor.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// ═══════════════════════════════════════════════════════════════
//  DATASTORE SINGLETON
//  One instance per process — prevents multiple-file-access crash.
//  Physical path: /data/data/<pkg>/files/datastore/settings.preferences_pb
// ═══════════════════════════════════════════════════════════════

private const val DATASTORE_NAME = "settings"

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DATASTORE_NAME
)
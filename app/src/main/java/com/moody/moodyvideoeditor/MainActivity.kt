package com.moody.moodyvideoeditor

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.moody.moodyvideoeditor.data.settings.AppSettings
import com.moody.moodyvideoeditor.ui.theme.MoodyVideoEditorTheme
import com.moody.moodyvideoeditor.utils.SettingsConsumer

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val settings by SettingsConsumer.current.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = resolveDarkTheme(settings, systemDark)

            MoodyVideoEditorTheme(darkTheme = darkTheme) {
                AppNavigation()
            }
        }
    }

    private fun resolveDarkTheme(settings: AppSettings, systemDark: Boolean): Boolean {
        return when (settings.appTheme.lowercase()) {
            "dark" -> true
            "light" -> false
            else -> systemDark
        }
    }
}
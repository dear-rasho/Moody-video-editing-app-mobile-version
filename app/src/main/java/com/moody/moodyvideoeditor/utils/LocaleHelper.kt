package com.moody.moodyvideoeditor.utils

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

// ═══════════════════════════════════════════════════════════════
//  LOCALE HELPER — app language switching
//  Uses AppCompatDelegate (backwards-compatible to API 21)
// ═══════════════════════════════════════════════════════════════

object LocaleHelper {

    data class LanguageOption(
        val code: String,
        val labelNative: String,
        val labelEnglish: String
    )

    val SUPPORTED_LANGUAGES = listOf(
        LanguageOption("en", "English", "English"),
        LanguageOption("ur", "اردو", "Urdu"),
        LanguageOption("hi", "हिन्दी", "Hindi"),
        LanguageOption("ar", "العربية", "Arabic")
    )

    fun findByCode(code: String): LanguageOption =
        SUPPORTED_LANGUAGES.firstOrNull { it.code == code }
            ?: SUPPORTED_LANGUAGES[0]

    // Apply a language to the running app
    fun applyLanguage(languageCode: String) {
        val tag = if (languageCode.isBlank()) "en" else languageCode
        val localeList = LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    // Read the currently-applied language code
    fun currentLanguageCode(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return "en"
        return locales[0]?.language ?: "en"
    }
}
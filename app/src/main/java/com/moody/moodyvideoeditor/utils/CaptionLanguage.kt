package com.moody.moodyvideoeditor.utils

// ═══════════════════════════════════════════════════════════════
//  CAPTION LANGUAGE — user-selectable transcription language
//  Font pools are declared outside the enum to avoid
//  initialization order conflicts.
// ═══════════════════════════════════════════════════════════════

// ─── Font pools (top-level, initialized first) ───
private val ENGLISH_FONT_POOL = listOf(
    "Anton", "Bangers", "Impact", "Oswald Bold",
    "Poppins Black", "Lobster", "Damion", "Creepster",
    "Audiowide", "Carter One", "Bowlby One SC"
)

private val URDU_FONT_POOL = listOf(
    "Noto Nastaliq Urdu",
    "Noto Nastaliq Urdu Bold",
    "Noto Nastaliq Urdu Medium",
    "Gulzar",
    "AlQalam",
    "AlQalam Fahad",
    "AlQalam Abuzar",
    "Akram Unicode",
    "Gandhara Suls"
)

private val HINDI_FONT_POOL = listOf(
    "Gajraj One",
    "Jaini",
    "Matemasie",
    "Ranga",
    "Ranga Bold",
    "Sarpanch"
)

private val ARABIC_FONT_POOL = listOf(
    "Amiri",
    "Noto Kufi Arabic",
    "Reem Kufi Fun",
    "Reem Kufi Ink",
    "Almarai",
    "Fustat",
    "Qahiri",
    "IBM Plex Sans Arabic"
)

enum class CaptionLanguage(
    val code: String,
    val label: String,
    val flag: String,
    val fonts: List<String>
) {
    AUTO("auto", "Auto Detect", "🌐", ENGLISH_FONT_POOL),
    ENGLISH("en", "English", "🇬🇧", ENGLISH_FONT_POOL),
    URDU("ur", "اردو", "🇵🇰", URDU_FONT_POOL),
    HINDI("hi", "हिन्दी", "🇮🇳", HINDI_FONT_POOL),
    ARABIC("ar", "العربية", "🇸🇦", ARABIC_FONT_POOL),
    SPANISH("es", "Español", "🇪🇸", ENGLISH_FONT_POOL),
    FRENCH("fr", "Français", "🇫🇷", ENGLISH_FONT_POOL),
    GERMAN("de", "Deutsch", "🇩🇪", ENGLISH_FONT_POOL),
    RUSSIAN("ru", "Русский", "🇷🇺", ENGLISH_FONT_POOL),
    TURKISH("tr", "Türkçe", "🇹🇷", ENGLISH_FONT_POOL),
    CHINESE("zh", "中文", "🇨🇳", ENGLISH_FONT_POOL),
    JAPANESE("ja", "日本語", "🇯🇵", ENGLISH_FONT_POOL),
    KOREAN("ko", "한국어", "🇰🇷", ENGLISH_FONT_POOL);

    companion object {

        fun fromCode(code: String): CaptionLanguage =
            values().firstOrNull { it.code.equals(code, ignoreCase = true) }
                ?: AUTO

        fun fontsFor(code: String): List<String> {
            val lang = fromCode(code)
            return if (lang.fonts.isEmpty()) ENGLISH_FONT_POOL else lang.fonts
        }
        // ═══════════════════════════════════════════════════════════
        //  SCRIPT AUTO-DETECTION — for font selection
        // ═══════════════════════════════════════════════════════════

        /**
         * Detect language from text by looking at Unicode character ranges.
         * Ur / Ar / Fa all share Arabic script → use Urdu fonts.
         */
        fun detectLanguage(text: String): String {
            if (text.isBlank()) return "en"

            for (c in text) {
                val code = c.code
                when {
                    // Arabic script (Arabic, Urdu, Persian, Pashto)
                    code in 0x0600..0x06FF -> return "ur"
                    code in 0x0750..0x077F -> return "ur"
                    code in 0xFB50..0xFDFF -> return "ur"
                    code in 0xFE70..0xFEFF -> return "ur"

                    // Devanagari (Hindi, Marathi, Nepali)
                    code in 0x0900..0x097F -> return "hi"

                    // Bengali
                    code in 0x0980..0x09FF -> return "hi"

                    // Tamil
                    code in 0x0B80..0x0BFF -> return "hi"

                    // Chinese
                    code in 0x4E00..0x9FFF -> return "zh"

                    // Japanese (Hiragana + Katakana)
                    code in 0x3040..0x309F -> return "ja"
                    code in 0x30A0..0x30FF -> return "ja"

                    // Korean Hangul
                    code in 0xAC00..0xD7AF -> return "ko"
                    code in 0x1100..0x11FF -> return "ko"

                    // Cyrillic (Russian)
                    code in 0x0400..0x04FF -> return "ru"

                    // Thai
                    code in 0x0E00..0x0E7F -> return "th"
                }
            }

            // Default: Latin / English
            return "en"
        }
        // Common languages displayed as quick-pick chips
        val QUICK_PICK: List<CaptionLanguage> = listOf(
            AUTO, ENGLISH, URDU, HINDI, ARABIC,
            SPANISH, FRENCH, GERMAN, RUSSIAN,
            TURKISH, CHINESE, JAPANESE, KOREAN
        )
    }
}
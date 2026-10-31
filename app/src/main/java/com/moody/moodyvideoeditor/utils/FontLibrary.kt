package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import java.util.concurrent.ConcurrentHashMap

data class FontMeta(
    val name: String,
    val displayName: String,
    val language: String,
    val flag: String,
    val sampleText: String,
    val fileBase: String
)

object FontLibrary {

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val assetFontCache = ConcurrentHashMap<String, Typeface>()
    private val assetFontAvailability = ConcurrentHashMap<String, Boolean>()

    // ═══════════════════════════════════════════════════════════
    //  ALL 114 FONTS
    // ═══════════════════════════════════════════════════════════
    val FONT_METADATA: List<FontMeta> = listOf(

        // ═══════════ 🇸🇦 ARABIC (8) ═══════════
        FontMeta("Almarai", "Almarai", "Arabic", "🇸🇦", "العربية", "Almarai-Regular"),
        FontMeta("Amiri", "Amiri", "Arabic", "🇸🇦", "العربية", "Amiri-Regular"),
        FontMeta("Fustat", "Fustat", "Arabic", "🇸🇦", "العربية", "Fustat-Regular"),
        FontMeta("IBM Plex Sans Arabic", "IBM Plex Arabic", "Arabic", "🇸🇦", "العربية", "IBMPlexSansArabic-Regular"),
        FontMeta("Noto Kufi Arabic", "Noto Kufi Arabic", "Arabic", "🇸🇦", "العربية", "NotoKufiArabic-Regular"),
        FontMeta("Qahiri", "Qahiri", "Arabic", "🇸🇦", "العربية", "Qahiri-Regular"),
        FontMeta("Reem Kufi Fun", "Reem Kufi Fun", "Arabic", "🇸🇦", "العربية", "ReemKufiFun-Regular"),
        FontMeta("Reem Kufi Ink", "Reem Kufi Ink", "Arabic", "🇸🇦", "العربية", "ReemKufiInk-Regular"),
        // ═══════════ 🇵🇰 URDU / NASTALIQ (14) ═══════════
        FontMeta("Noto Nastaliq Urdu", "Noto Nastaliq", "Urdu", "🇵🇰", "اردو", "NotoNastaliqUrdu-Regular"),
        FontMeta("Noto Nastaliq Urdu Bold", "Noto Nastaliq Bold", "Urdu", "🇵🇰", "اردو", "NotoNastaliqUrdu-Bold"),
        FontMeta("Noto Nastaliq Urdu Medium", "Noto Nastaliq Med", "Urdu", "🇵🇰", "اردو", "NotoNastaliqUrdu-Medium"),
        FontMeta("Noto Nastaliq Urdu SemiBold", "Noto Nastaliq SB", "Urdu", "🇵🇰", "اردو", "NotoNastaliqUrdu-SemiBold"),
        FontMeta("Noto Nastaliq Urdu Variable", "Noto Nastaliq Var", "Urdu", "🇵🇰", "اردو", "NotoNastaliqUrdu-Variable"),
        FontMeta("AA Sameer Bassam", "AA Sameer Bassam", "Urdu", "🇵🇰", "اردو", "AASameerBassam-Bold"),
        FontMeta("Aadil", "Aadil", "Urdu", "🇵🇰", "اردو", "Aadil-Regular"),
        FontMeta("Akram Unicode", "Akram Unicode", "Urdu", "🇵🇰", "اردو", "AkramUnicode-Regular"),
        FontMeta("AlQalam Abuzar", "AlQalam Abuzar", "Urdu", "🇵🇰", "اردو", "AlQalamAbuzar-Regular"),
        FontMeta("AlQalam Irtaza Hassan", "AlQalam Irtaza", "Urdu", "🇵🇰", "اردو", "AlQalamIrtazaHassan-Regular"),
        FontMeta("AlQalam Fahad", "AlQalam Fahad", "Urdu", "🇵🇰", "اردو", "AlQalamFahad-Regular"),
        FontMeta("AlQalam", "AlQalam", "Urdu", "🇵🇰", "اردو", "AlQalam-Regular"),
        FontMeta("Gandhara Suls", "Gandhara Suls", "Urdu", "🇵🇰", "اردو", "GandharaSuls-Regular"),
        FontMeta("Gulzar", "Gulzar", "Urdu", "🇵🇰", "اردو", "Gulzar-Regular"),
        // ═══════════ 🇮🇳 HINDI / DEVANAGARI (6) ═══════════
        FontMeta("Gajraj One", "Gajraj One", "Hindi", "🇮🇳", "हिन्दी", "GajrajOne-Regular"),
        FontMeta("Jaini", "Jaini", "Hindi", "🇮🇳", "हिन्दी", "Jaini-Regular"),
        FontMeta("Matemasie", "Matemasie", "Hindi", "🇮🇳", "हिन्दी", "Matemasie-Regular"),
        FontMeta("Ranga", "Ranga", "Hindi", "🇮🇳", "हिन्दी", "Ranga-Regular"),
        FontMeta("Ranga Bold", "Ranga Bold", "Hindi", "🇮🇳", "हिन्दी", "Ranga-Bold"),
        FontMeta("Sarpanch", "Sarpanch", "Hindi", "🇮🇳", "हिन्दी", "Sarpanch-Regular"),

        // ═══════════ ✍️ SCRIPT / HANDWRITING (15) ═══════════
        FontMeta("Arizonia", "Arizonia", "Script", "✍️", "Cursive", "Arizonia-Regular"),
        FontMeta("Bonheur Royale", "Bonheur Royale", "Script", "✍️", "Cursive", "BonheurRoyale-Regular"),
        FontMeta("Damion", "Damion", "Script", "✍️", "Cursive", "Damion-Regular"),
        FontMeta("Edu QLD Hand", "Edu QLD Hand", "Script", "✍️", "Handwriting", "EduQLDHand-Regular"),
        FontMeta("Grand Hotel", "Grand Hotel", "Script", "✍️", "Cursive", "GrandHotel-Regular"),
        FontMeta("Grechen Fuemen", "Grechen Fuemen", "Script", "✍️", "Cursive", "GrechenFuemen-Regular"),
        FontMeta("Henny Penny", "Henny Penny", "Script", "✍️", "Cursive", "HennyPenny-Regular"),
        FontMeta("Lavishly Yours", "Lavishly Yours", "Script", "✍️", "Cursive", "LavishlyYours-Regular"),
        FontMeta("Neonderthaw", "Neonderthaw", "Script", "✍️", "Neon", "Neonderthaw-Regular"),
        FontMeta("Petit Formal Script", "Petit Formal", "Script", "✍️", "Cursive", "PetitFormalScript-Regular"),
        FontMeta("Playwrite CA Guides", "Playwrite", "Script", "✍️", "Cursive", "PlaywriteCAGuides-Regular"),
        FontMeta("Shadows Into Light", "Shadows Into Light", "Script", "✍️", "Cursive", "ShadowsIntoLight-Regular"),
        FontMeta("Stalemate", "Stalemate", "Script", "✍️", "Cursive", "Stalemate-Regular"),
        FontMeta("Story Script", "Story Script", "Script", "✍️", "Cursive", "StoryScript-Regular"),
        FontMeta("Yesteryear", "Yesteryear", "Script", "✍️", "Cursive", "Yesteryear-Regular"),

        // ═══════════ 🎨 DISPLAY — Ballet (5) ═══════════
        FontMeta("Ballet 24pt", "Ballet 24pt", "Display", "🎨", "Aa Bb", "Ballet_24pt-Regular"),
        FontMeta("Ballet 28pt", "Ballet 28pt", "Display", "🎨", "Aa Bb", "Ballet_28pt-Regular"),
        FontMeta("Ballet 48pt", "Ballet 48pt", "Display", "🎨", "Aa Bb", "Ballet_48pt-Regular"),
        FontMeta("Ballet 60pt", "Ballet 60pt", "Display", "🎨", "Aa Bb", "Ballet_60pt-Regular"),
        FontMeta("Ballet Variable", "Ballet (Variable)", "Display", "🎨", "Aa Bb", "Ballet-Regular-VariableFont_opsz"),

        // ═══════════ 🎨 DISPLAY — DynaPuff (12) ═══════════
        FontMeta("DynaPuff", "DynaPuff", "Display", "🎨", "Aa Bb", "DynaPuff-Regular"),
        FontMeta("DynaPuff Bold", "DynaPuff Bold", "Display", "🎨", "Aa Bb", "DynaPuff-Bold"),
        FontMeta("DynaPuff Medium", "DynaPuff Medium", "Display", "🎨", "Aa Bb", "DynaPuff-Medium"),
        FontMeta("DynaPuff SemiBold", "DynaPuff SemiBold", "Display", "🎨", "Aa Bb", "DynaPuff-SemiBold"),
        FontMeta("DynaPuff Condensed", "DynaPuff Cond", "Display", "🎨", "Aa Bb", "DynaPuff_Condensed-Regular"),
        FontMeta("DynaPuff Condensed Bold", "DynaPuff Cond Bold", "Display", "🎨", "Aa Bb", "DynaPuff_Condensed-Bold"),
        FontMeta("DynaPuff Condensed Medium", "DynaPuff Cond Med", "Display", "🎨", "Aa Bb", "DynaPuff_Condensed-Medium"),
        FontMeta("DynaPuff Condensed SemiBold", "DynaPuff Cond SB", "Display", "🎨", "Aa Bb", "DynaPuff_Condensed-SemiBold"),
        FontMeta("DynaPuff SemiCondensed", "DynaPuff SemiCond", "Display", "🎨", "Aa Bb", "DynaPuff_SemiCondensed-Regular"),
        FontMeta("DynaPuff SemiCondensed Bold", "DynaPuff SC Bold", "Display", "🎨", "Aa Bb", "DynaPuff_SemiCondensed-Bold"),
        FontMeta("DynaPuff SemiCondensed Medium", "DynaPuff SC Med", "Display", "🎨", "Aa Bb", "DynaPuff_SemiCondensed-Medium"),
        FontMeta("DynaPuff SemiCondensed SemiBold", "DynaPuff SC SB", "Display", "🎨", "Aa Bb", "DynaPuff_SemiCondensed-SemiBold"),

        // ═══════════ 🎨 DISPLAY — Kaluk Sans (18) ═══════════
        FontMeta("Kaluk Sans", "Kaluk Sans", "Display", "🎨", "Aa Bb", "KalukSansDemo-Regular"),
        FontMeta("Kaluk Sans Black", "Kaluk Sans Black", "Display", "🎨", "Aa Bb", "KalukSansDemo-Black"),
        FontMeta("Kaluk Sans Black Italic", "Kaluk Sans Blk It", "Display", "🎨", "Aa Bb", "KalukSansDemo-BlackItalic"),
        FontMeta("Kaluk Sans Bold", "Kaluk Sans Bold", "Display", "🎨", "Aa Bb", "KalukSansDemo-Bold"),
        FontMeta("Kaluk Sans Bold Italic", "Kaluk Sans Bold It", "Display", "🎨", "Aa Bb", "KalukSansDemo-BoldItalic"),
        FontMeta("Kaluk Sans ExtraBold", "Kaluk Sans XBold", "Display", "🎨", "Aa Bb", "KalukSansDemo-ExtraBold"),
        FontMeta("Kaluk Sans ExtraBold Italic", "Kaluk Sans XB It", "Display", "🎨", "Aa Bb", "KalukSansDemo-ExtraBoldItalic"),
        FontMeta("Kaluk Sans ExtraLight", "Kaluk Sans XLt", "Display", "🎨", "Aa Bb", "KalukSansDemo-ExtraLight"),
        FontMeta("Kaluk Sans ExtLt Italic", "Kaluk Sans XLt It", "Display", "🎨", "Aa Bb", "KalukSansDemo-ExtLtIta"),
        FontMeta("Kaluk Sans Italic", "Kaluk Sans Italic", "Display", "🎨", "Aa Bb", "KalukSansDemo-Italic"),
        FontMeta("Kaluk Sans Light", "Kaluk Sans Light", "Display", "🎨", "Aa Bb", "KalukSansDemo-Light"),
        FontMeta("Kaluk Sans Light Italic", "Kaluk Sans Lt It", "Display", "🎨", "Aa Bb", "KalukSansDemo-LightItalic"),
        FontMeta("Kaluk Sans Medium", "Kaluk Sans Medium", "Display", "🎨", "Aa Bb", "KalukSansDemo-Medium"),
        FontMeta("Kaluk Sans Medium Italic", "Kaluk Sans Med It", "Display", "🎨", "Aa Bb", "KalukSansDemo-MediumItalic"),
        FontMeta("Kaluk Sans SemiBold", "Kaluk Sans SBold", "Display", "🎨", "Aa Bb", "KalukSansDemo-SemiBold"),
        FontMeta("Kaluk Sans SemiBold Italic", "Kaluk Sans SB It", "Display", "🎨", "Aa Bb", "KalukSansDemo-SemiBoldItalic"),
        FontMeta("Kaluk Sans Thin", "Kaluk Sans Thin", "Display", "🎨", "Aa Bb", "KalukSansDemo-Thin"),
        FontMeta("Kaluk Sans Thin Italic", "Kaluk Sans Thin It", "Display", "🎨", "Aa Bb", "KalukSansDemo-ThinItalic"),

        // ═══════════ 🎨 DISPLAY — Unique (22) ═══════════
        FontMeta("Amita", "Amita", "Display", "🎨", "Aa Bb", "Amita-Regular"),
        FontMeta("Anton", "Anton", "Display", "🎨", "Aa Bb", "Anton-Regular"),
        FontMeta("Audiowide", "Audiowide", "Display", "🎨", "Aa Bb", "Audiowide-Regular"),
        FontMeta("Bangers", "Bangers", "Display", "🎨", "Aa Bb", "Bangers-Regular"),
        FontMeta("BBH Bartle", "BBH Bartle", "Display", "🎨", "Aa Bb", "BBHBartle-Regular"),
        FontMeta("Bonbon", "Bonbon", "Display", "🎨", "Aa Bb", "Bonbon-Regular"),
        FontMeta("Boogaloo", "Boogaloo", "Display", "🎨", "Aa Bb", "Boogaloo-Regular"),
        FontMeta("Caprasimo", "Caprasimo", "Display", "🎨", "Aa Bb", "Caprasimo-Regular"),
        FontMeta("Carter One", "Carter One", "Display", "🎨", "Aa Bb", "CarterOne-Regular"),
        FontMeta("Castoro Titling", "Castoro Titling", "Display", "🎨", "Aa Bb", "CastoroTitling-Regular"),
        FontMeta("Chango", "Chango", "Display", "🎨", "Aa Bb", "Chango-Regular"),
        FontMeta("Crafty Girls", "Crafty Girls", "Display", "🎨", "Aa Bb", "CraftyGirls-Regular"),
        FontMeta("Creepster", "Creepster", "Display", "🎨", "Aa Bb", "Creepster-Regular"),
        FontMeta("Erica One", "Erica One", "Display", "🎨", "Aa Bb", "EricaOne-Regular"),
        FontMeta("Felipa", "Felipa", "Display", "🎨", "Aa Bb", "Felipa-Regular"),
        FontMeta("Isometra", "Isometra", "Display", "🎨", "Aa Bb", "Isometra-Regular"),
        FontMeta("Jim Nightshade", "Jim Nightshade", "Display", "🎨", "Aa Bb", "JimNightshade-Regular"),
        FontMeta("Lobster", "Lobster", "Display", "🎨", "Aa Bb", "Lobster-Regular"),
        FontMeta("Nosifer", "Nosifer", "Display", "🎨", "Aa Bb", "Nosifer-Regular"),
        FontMeta("Oi", "Oi", "Display", "🎨", "Aa Bb", "Oi-Regular"),
        FontMeta("Oleo Script Swash Caps", "Oleo Script", "Display", "🎨", "Aa Bb", "OleoScriptSwashCaps-Regular"),
        FontMeta("Oleo Script Swash Caps Bold", "Oleo Script Bold", "Display", "🎨", "Aa Bb", "OleoScriptSwashCaps-Bold"),

        // ═══════════ 🔲 GOTHIC / BLACKLETTER (2) ═══════════
        FontMeta("Uncial Antiqua", "Uncial Antiqua", "Gothic", "🔲", "Aa Bb", "UncialAntiqua-Regular"),
        FontMeta("Unifraktur Maguntia", "Unifraktur", "Gothic", "🔲", "Aa Bb", "UnifrakturMaguntia-Regular"),

        // ═══════════ 🔤 MODERN — Oswald (7) ═══════════
        FontMeta("Oswald", "Oswald", "Modern", "🔤", "Aa Bb", "Oswald-Regular"),
        FontMeta("Oswald Bold", "Oswald Bold", "Modern", "🔤", "Aa Bb", "Oswald-Bold"),
        FontMeta("Oswald ExtraLight", "Oswald ExtraLight", "Modern", "🔤", "Aa Bb", "Oswald-ExtraLight"),
        FontMeta("Oswald Light", "Oswald Light", "Modern", "🔤", "Aa Bb", "Oswald-Light"),
        FontMeta("Oswald Medium", "Oswald Medium", "Modern", "🔤", "Aa Bb", "Oswald-Medium"),
        FontMeta("Oswald SemiBold", "Oswald SemiBold", "Modern", "🔤", "Aa Bb", "Oswald-SemiBold"),
        FontMeta("Oswald Variable", "Oswald (Variable)", "Modern", "🔤", "Aa Bb", "Oswald-VariableFont_wght"),

        // ═══════════ 🔤 MODERN — Poppins (18) ═══════════
        FontMeta("Poppins", "Poppins", "Modern", "🔤", "Aa Bb", "Poppins-Regular"),
        FontMeta("Poppins Black", "Poppins Black", "Modern", "🔤", "Aa Bb", "Poppins-Black"),
        FontMeta("Poppins Black Italic", "Poppins Blk It", "Modern", "🔤", "Aa Bb", "Poppins-BlackItalic"),
        FontMeta("Poppins Bold", "Poppins Bold", "Modern", "🔤", "Aa Bb", "Poppins-Bold"),
        FontMeta("Poppins Bold Italic", "Poppins Bold It", "Modern", "🔤", "Aa Bb", "Poppins-BoldItalic"),
        FontMeta("Poppins ExtraBold", "Poppins XBold", "Modern", "🔤", "Aa Bb", "Poppins-ExtraBold"),
        FontMeta("Poppins ExtraBold Italic", "Poppins XB It", "Modern", "🔤", "Aa Bb", "Poppins-ExtraBoldItalic"),
        FontMeta("Poppins ExtraLight", "Poppins XLt", "Modern", "🔤", "Aa Bb", "Poppins-ExtraLight"),
        FontMeta("Poppins ExtraLight Italic", "Poppins XLt It", "Modern", "🔤", "Aa Bb", "Poppins-ExtraLightItalic"),
        FontMeta("Poppins Italic", "Poppins Italic", "Modern", "🔤", "Aa Bb", "Poppins-Italic"),
        FontMeta("Poppins Light", "Poppins Light", "Modern", "🔤", "Aa Bb", "Poppins-Light"),
        FontMeta("Poppins Light Italic", "Poppins Lt It", "Modern", "🔤", "Aa Bb", "Poppins-LightItalic"),
        FontMeta("Poppins Medium", "Poppins Medium", "Modern", "🔤", "Aa Bb", "Poppins-Medium"),
        FontMeta("Poppins Medium Italic", "Poppins Med It", "Modern", "🔤", "Aa Bb", "Poppins-MediumItalic"),
        FontMeta("Poppins SemiBold", "Poppins SBold", "Modern", "🔤", "Aa Bb", "Poppins-SemiBold"),
        FontMeta("Poppins SemiBold Italic", "Poppins SB It", "Modern", "🔤", "Aa Bb", "Poppins-SemiBoldItalic"),
        FontMeta("Poppins Thin", "Poppins Thin", "Modern", "🔤", "Aa Bb", "Poppins-Thin"),
        FontMeta("Poppins Thin Italic", "Poppins Thin It", "Modern", "🔤", "Aa Bb", "Poppins-ThinItalic"),

        // ═══════════ 🔤 MODERN — Others (1) ═══════════
        FontMeta("Palanquin Dark", "Palanquin Dark", "Modern", "🔤", "Aa Bb", "PalanquinDark-Regular"),

        // ═══════════ ⚙️ SYSTEM (3) ═══════════
        FontMeta("Arial", "Arial", "System", "⚙️", "Aa Bb", ""),
        FontMeta("Georgia", "Georgia", "System", "⚙️", "Aa Bb", ""),
        FontMeta("Roboto", "Roboto", "System", "⚙️", "Aa Bb", "")
    )

    // ═══════════════════════════════════════════════════════════
    //  CATEGORIES
    // ═══════════════════════════════════════════════════════════
    val TITLE_FONT_ADDITIONS = listOf(
        "Amita",
        "Audiowide",
        "Boogaloo",
        "Bonbon",
        "Castoro Titling",
        "DynaPuff",
        "DynaPuff Condensed Bold",
        "DynaPuff SemiBold",
        "Felipa",
        "Gajraj One",
        "Henny Penny",
        "Isometra",
        "Jaini",
        "Kaluk Sans Black",
        "Kaluk Sans Bold",
        "Kaluk Sans ExtraBold",
        "Lobster",
        "Matemasie",
        "Neonderthaw",
        "Oswald",
        "Oswald Medium",
        "Oswald SemiBold",
        "Palanquin Dark",
        "Poppins Bold",
        "Poppins SemiBold",
        "Ranga",
        "Ranga Bold",
        "Sarpanch",
        "Story Script",
        "Yesteryear",
        "Oleo Script Swash Caps",
        "Grand Hotel",
        "Arizonia",
        "Ballet 60pt"
    )

    private val ORIGINAL_TITLE_FONTS = setOf(
        "Anton", "Bangers", "BBH Bartle", "Caprasimo",
        "Carter One", "Chango", "Creepster", "Erica One",
        "Nosifer", "Oi", "Oswald Bold", "Poppins Black",
        "Poppins ExtraBold", "DynaPuff Bold",
        "Uncial Antiqua", "Unifraktur Maguntia"
    )

    val FONT_CATEGORIES: Map<String, List<String>> = mapOf(
        "all" to FONT_METADATA.map { it.name },
        "english" to FONT_METADATA.filter {
            it.language in setOf("Script", "Display", "Gothic", "Modern", "System")
        }.map { it.name },
        "arabic" to FONT_METADATA.filter { it.language == "Arabic" }.map { it.name },
        "urdu" to FONT_METADATA.filter { it.language == "Urdu" }.map { it.name },
        "hindi" to FONT_METADATA.filter { it.language == "Hindi" }.map { it.name },
        "script" to FONT_METADATA.filter { it.language == "Script" }.map { it.name },
        "display" to FONT_METADATA.filter { it.language == "Display" }.map { it.name },
        "gothic" to FONT_METADATA.filter { it.language == "Gothic" }.map { it.name },
        "modern" to FONT_METADATA.filter { it.language == "Modern" }.map { it.name },
        "system" to FONT_METADATA.filter { it.language == "System" }.map { it.name },
        "music" to FONT_METADATA.filter {
            it.name in setOf(
                "Audiowide", "Kaluk Sans", "Kaluk Sans Bold",
                "Kaluk Sans Black", "Kaluk Sans ExtraBold",
                "Lobster", "Oleo Script Swash Caps",
                "Oleo Script Swash Caps Bold", "Arizonia",
                "Damion", "Grand Hotel", "Neonderthaw",
                "Yesteryear", "Uncial Antiqua"
            )
        }.map { it.name },
        "education" to FONT_METADATA.filter {
            it.name == "Arial" || it.name == "Roboto" ||
                    it.name == "Georgia" || it.name == "Oswald" ||
                    it.name == "Oswald Medium" || it.name == "Palanquin Dark" ||
                    it.name == "Kaluk Sans" || it.name == "Poppins" ||
                    it.name == "Poppins Medium" || it.name == "Poppins SemiBold"
        }.map { it.name },
        "titles" to FONT_METADATA.filter {
            it.name in ORIGINAL_TITLE_FONTS || it.name in TITLE_FONT_ADDITIONS
        }.map { it.name }
    )

    fun allFonts(): List<String> = FONT_METADATA.map { it.name }

    fun categories(): List<String> = listOf(
        "all", "english", "music", "education", "titles",
        "arabic", "urdu", "hindi", "script", "display", "gothic", "modern", "system"
    )

    fun categoryLabel(key: String): String = when (key) {
        "all" -> "🔤 All"
        "english" -> "🇬🇧 English"
        "music" -> "🎵 Music"
        "education" -> "📚 Education"
        "titles" -> "🏷️ Titles"
        "arabic" -> "🇸🇦 Arabic"
        "urdu" -> "🇵🇰 Urdu"
        "hindi" -> "🇮🇳 Hindi"
        "script" -> "✍️ Script"
        "display" -> "🎨 Display"
        "gothic" -> "🔲 Gothic"
        "modern" -> "🔤 Modern"
        "system" -> "⚙️ System"
        else -> key.replaceFirstChar { it.uppercase() }
    }

    fun metaFor(fontName: String): FontMeta? =
        FONT_METADATA.firstOrNull { it.name.equals(fontName, ignoreCase = true) }

    fun resolveFontName(input: String): String {
        val raw = input.trim()
        if (raw.isEmpty()) return "Arial"
        val key = raw.lowercase().replace(" ", "")
        FONT_CATEGORIES[key]?.let { list -> return list.firstOrNull() ?: "Arial" }
        return raw
    }

    fun loadFont(fontName: String) = Unit

    fun familyFor(fontName: String): FontFamily =
        FontFamily(typefaceFor(fontName))

    // ═══════════════════════════════════════════════════════════
    //  ASSET LOOKUP — uses fileBase directly
    // ═══════════════════════════════════════════════════════════
    fun findAssetFontPath(fontName: String): String? {
        val ctx = appContext ?: return null
        val cacheKey = fontName.lowercase()

        if (assetFontAvailability[cacheKey] == false) return null

        val meta = metaFor(fontName)
        val fileBase = meta?.fileBase

        if (fileBase.isNullOrBlank()) {
            assetFontAvailability[cacheKey] = false
            return null
        }

        val candidates = listOf(
            "fonts/$fileBase.ttf",
            "fonts/$fileBase.otf",
            "fonts/$fileBase.ttc"
        )

        for (path in candidates) {
            try {
                ctx.assets.open(path).close()
                assetFontAvailability[cacheKey] = true
                return path
            } catch (_: Throwable) {
            }
        }

        assetFontAvailability[cacheKey] = false
        return null
    }

    private fun loadAssetTypeface(fontName: String, style: Int): Typeface? {
        val ctx = appContext ?: return null
        val cacheKey = "$fontName-$style"
        assetFontCache[cacheKey]?.let { return it }

        val assetPath = findAssetFontPath(fontName) ?: return null

        return try {
            val base = Typeface.createFromAsset(ctx.assets, assetPath)
            val styled = Typeface.create(base, style)
            assetFontCache[cacheKey] = styled
            styled
        } catch (_: Throwable) {
            null
        }
    }

    fun exportAssetFontToCache(fontName: String): String? {
        val ctx = appContext ?: return null
        val assetPath = findAssetFontPath(fontName) ?: return null

        val cleanFileName = assetPath.substringAfter("fonts/")
        val outFile = java.io.File(ctx.cacheDir, "asset_font_$cleanFileName")
        if (outFile.exists() && outFile.length() > 100) {
            return outFile.absolutePath
        }
        return try {
            ctx.assets.open(assetPath).use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (outFile.exists() && outFile.length() > 100) outFile.absolutePath
            else null
        } catch (_: Throwable) {
            null
        }
    }

    private val FONT_MAP: Map<String, String> = buildMap {
        listOf(
            "arial", "helvetica", "segoe ui", "roboto", "inter",
            "verdana", "tahoma", "trebuchet ms", "calibri"
        ).forEach { put(it, "sans-serif") }
        listOf(
            "impact", "arial black", "anton", "bangers", "creepster", "oi"
        ).forEach { put(it, "sans-serif-black") }
        listOf(
            "times new roman", "georgia", "cambria", "garamond", "amiri"
        ).forEach { put(it, "serif") }
        listOf("courier new", "consolas", "monaco").forEach { put(it, "monospace") }
        listOf(
            "comic sans ms", "brush script mt", "dancing script"
        ).forEach { put(it, "cursive") }
    }

    private val SYSTEM_FAMILIES = setOf(
        "sans-serif", "sans-serif-light", "sans-serif-thin",
        "sans-serif-medium", "sans-serif-black", "sans-serif-condensed",
        "serif", "serif-monospace", "monospace", "casual", "cursive"
    )

    fun typefaceFor(
        fontName: String,
        bold: Boolean = false,
        italic: Boolean = false
    ): Typeface {
        val name = fontName.trim().ifEmpty { "Arial" }

        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }

        loadAssetTypeface(name, style)?.let { return it }

        val normalized = name.lowercase()
        val family = if (normalized in SYSTEM_FAMILIES) {
            normalized
        } else {
            FONT_MAP[normalized] ?: "sans-serif"
        }

        return Typeface.create(family, style)
    }
}
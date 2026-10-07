package com.moody.moodyvideoeditor.utils

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily

object FontLibrary {

    // ═══════════════════════════════════════════════════════════
    //  15 categories — mirrors FONT_CATEGORIES in JS
    // ═══════════════════════════════════════════════════════════
    val FONT_CATEGORIES: Map<String, List<String>> = mapOf(
        "custom" to listOf(
            "Amita", "Bangela", "Brisound", "Chopin Script", "Christmas Music",
            "Daffiys", "Eighties", "Fighter Attack", "Forceless Demo",
            "Funkora", "Funky Groove", "Gwathlyn", "Kaway", "Komika",
            "Legendary Brush", "Musiclife", "Orchard Song", "Pricedown",
            "Rengkox", "Rockybilly", "Rumburak", "Shockwave"
        ),
        "system" to listOf(
            "Arial", "Helvetica", "Segoe UI", "Roboto", "Inter",
            "Verdana", "Tahoma", "Trebuchet MS", "Calibri", "Candara",
            "Corbel", "Franklin Gothic Medium", "Lucida Grande", "Geneva",
            "Optima", "Avenir", "Futura", "Gill Sans",
            "Century Gothic", "Tw Cen MT"
        ),
        "serif" to listOf(
            "Times New Roman", "Georgia", "Cambria", "Constantia",
            "Palatino Linotype", "Book Antiqua", "Bookman Old Style",
            "Garamond", "Baskerville", "Didot", "Rockwell", "Courier New"
        ),
        "mono" to listOf(
            "Courier New", "Consolas", "Monaco", "Menlo", "Lucida Console",
            "Andale Mono", "Courier", "Inconsolata", "Source Code Pro",
            "Roboto Mono", "Fira Code", "JetBrains Mono", "Space Mono",
            "IBM Plex Mono", "Cascadia Code", "Cascadia Mono"
        ),
        "display" to listOf(
            "Shockwave", "Fighter Attack", "Pricedown", "Rockybilly",
            "Impact", "Arial Black", "Franklin Gothic Heavy", "Haettenschweiler",
            "Anton", "Bebas Neue", "Oswald", "Archivo Black",
            "Bungee", "Titan One", "Bowlby One SC", "Alfa Slab One",
            "Russo One", "Righteous", "Bungee Inline", "Bungee Shade",
            "Monoton", "Audiowide", "Orbitron"
        ),
        "handwriting" to listOf(
            "Amita", "Chopin Script", "Gwathlyn", "Legendary Brush",
            "Musiclife", "Orchard Song",
            "Comic Sans MS", "Brush Script MT", "Segoe Script", "Bradley Hand",
            "Lucida Handwriting", "Apple Chancery",
            "Dancing Script", "Pacifico", "Great Vibes", "Allura",
            "Alex Brush", "Satisfy", "Kaushan Script", "Parisienne",
            "Sacramento", "Tangerine", "Caveat", "Shadows Into Light",
            "Indie Flower", "Amatic SC", "Patrick Hand", "Kalam"
        ),
        "elegant" to listOf(
            "Amita", "Orchard Song", "Brisound",
            "Playfair Display", "Cormorant Garamond", "EB Garamond",
            "Lora", "Merriweather", "Crimson Text", "Libre Baskerville",
            "Cinzel", "Cormorant", "Spectral", "Prata", "Cardo",
            "Bodoni Moda", "Cormorant Upright", "Abril Fatface"
        ),
        "modern" to listOf(
            "Brisound", "Forceless Demo", "Rengkox", "Daffiys",
            "Poppins", "Montserrat", "Raleway", "Work Sans",
            "DM Sans", "Manrope", "Space Grotesk", "Outfit", "Sora",
            "IBM Plex Sans", "Public Sans", "Archivo", "Mulish",
            "Nunito", "Rubik", "Karla", "Lato", "Open Sans"
        ),
        "titles" to listOf(
            "Bangela", "Shockwave", "Fighter Attack", "Pricedown",
            "Poppins", "Montserrat", "Raleway", "Playfair Display",
            "Cinzel", "Bebas Neue", "Alfa Slab One", "Archivo Black",
            "Abril Fatface", "Bungee Shade", "Bungee Inline", "Russo One",
            "Anton", "Oswald", "Righteous", "Bungee"
        ),
        "music" to listOf(
            "Eighties", "Funkora", "Funky Groove", "Christmas Music",
            "Bebas Neue", "Anton", "Oswald", "Righteous", "Bungee",
            "Permanent Marker", "Caveat Brush", "Rock Salt", "Amatic SC",
            "Kalam", "Abril Fatface", "Monoton", "Titan One", "Bowlby One SC"
        ),
        "playful" to listOf(
            "Komika", "Kaway", "Rumburak", "Christmas Music",
            "Comic Sans MS", "Baloo 2", "Fredoka", "Chewy", "Luckiest Guy",
            "Bangers", "Bubblegum Sans", "Sniglet", "Grandstander",
            "Coiny", "Titan One", "Patrick Hand"
        ),
        "retro" to listOf(
            "Eighties", "Funky Groove", "Rockybilly", "Pricedown",
            "Lobster", "Righteous", "Bungee Shade", "Monoton", "Pacifico",
            "Cinzel", "Alfa Slab One", "Abril Fatface", "Bree Serif",
            "Special Elite", "Bungee Inline", "Ultra", "Bowlby One SC"
        ),
        "educational" to listOf(
            "Open Sans", "Lato", "Roboto", "Source Sans 3", "Noto Sans",
            "Inter", "Nunito", "Work Sans", "Rubik", "Karla",
            "Mulish", "Manrope", "Public Sans", "IBM Plex Sans"
        ),
        "cinematic" to listOf(
            "Cinzel", "Playfair Display", "Cormorant Garamond", "EB Garamond",
            "Prata", "Cardo", "Spectral", "Lora", "Libre Baskerville",
            "Abril Fatface", "Bodoni Moda", "Cormorant Upright"
        ),
        "minimal" to listOf(
            "Inter", "Roboto", "Open Sans", "Lato", "Work Sans",
            "DM Sans", "Manrope", "Karla", "Rubik", "IBM Plex Sans",
            "Public Sans", "Archivo"
        )
    )

    fun allFonts(): List<String> =
        FONT_CATEGORIES.values.flatten().distinct().sorted()

    fun categories(): List<String> = FONT_CATEGORIES.keys.toList()

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
    //  🆕 FONT → ANDROID SYSTEM FAMILY MAP
    //  Maps every custom font name to closest real system font
    // ═══════════════════════════════════════════════════════════

    private val FONT_MAP: Map<String, String> = buildMap {
        // ─── SANS-SERIF (Roboto) ───
        val sansList = listOf(
            "arial", "helvetica", "segoe ui", "roboto", "inter",
            "verdana", "tahoma", "trebuchet ms", "calibri", "candara",
            "corbel", "franklin gothic medium", "lucida grande", "geneva",
            "optima", "avenir", "futura", "gill sans",
            "century gothic", "tw cen mt",
            "poppins", "montserrat", "raleway", "work sans",
            "dm sans", "manrope", "space grotesk", "outfit", "sora",
            "ibm plex sans", "public sans", "archivo", "mulish",
            "nunito", "rubik", "karla", "lato", "open sans",
            "source sans 3", "noto sans", "brisound", "daffiys",
            "eighties", "funkora", "funky groove"
        )
        sansList.forEach { put(it, "sans-serif") }

        // ─── SANS-SERIF LIGHT ───
        listOf("roboto light", "segoe ui light").forEach {
            put(it, "sans-serif-light")
        }

        // ─── SANS-SERIF MEDIUM ───
        listOf("roboto medium", "segoe ui semibold").forEach {
            put(it, "sans-serif-medium")
        }

        // ─── SANS-SERIF BLACK (heavy display) ───
        listOf(
            "impact", "arial black", "franklin gothic heavy",
            "haettenschweiler", "bungee", "titan one",
            "bowlby one sc", "alfa slab one", "ultra",
            "bungee shade", "abril fatface", "archivo black"
        ).forEach { put(it, "sans-serif-black") }

        // ─── SANS-SERIF CONDENSED (narrow display) ───
        listOf(
            "anton", "bebas neue", "oswald", "russo one", "righteous",
            "bungee inline", "monoton", "audiowide", "orbitron",
            "fighter attack", "forceless demo", "komika", "pricedown",
            "rengkox", "rockybilly", "shockwave", "bangela",
            "bree serif", "special elite"
        ).forEach { put(it, "sans-serif-condensed") }

        // ─── SERIF ───
        listOf(
            "times new roman", "georgia", "cambria", "constantia",
            "palatino linotype", "book antiqua", "bookman old style",
            "garamond", "baskerville", "didot", "rockwell",
            "playfair display", "cormorant garamond", "eb garamond",
            "lora", "merriweather", "crimson text", "libre baskerville",
            "cinzel", "cormorant", "spectral", "prata", "cardo",
            "bodoni moda", "cormorant upright"
        ).forEach { put(it, "serif") }

        // ─── MONOSPACE ───
        listOf(
            "courier new", "consolas", "monaco", "menlo", "lucida console",
            "andale mono", "courier", "inconsolata", "source code pro",
            "roboto mono", "fira code", "jetbrains mono", "space mono",
            "ibm plex mono", "cascadia code", "cascadia mono"
        ).forEach { put(it, "monospace") }

        // ─── CURSIVE / SCRIPT ───
        listOf(
            "amita", "chopin script", "gwathlyn", "legendary brush",
            "musiclife", "orchard song", "kaway", "rumburak",
            "comic sans ms", "brush script mt", "segoe script",
            "bradley hand", "lucida handwriting", "apple chancery",
            "dancing script", "pacifico", "great vibes", "allura",
            "alex brush", "satisfy", "kaushan script", "parisienne",
            "sacramento", "tangerine", "caveat", "shadows into light",
            "indie flower", "amatic sc", "patrick hand", "kalam",
            "permanent marker", "caveat brush", "rock salt",
            "baloo 2", "fredoka", "chewy", "luckiest guy",
            "bangers", "bubblegum sans", "sniglet", "grandstander",
            "coiny", "lobster"
        ).forEach { put(it, "cursive") }
    }

    // ═══════════════════════════════════════════════════════════
    //  MAIN TYPE RESOLVER
    // ═══════════════════════════════════════════════════════════

    private val SYSTEM_FAMILIES = setOf(
        "sans-serif", "sans-serif-light", "sans-serif-thin",
        "sans-serif-medium", "sans-serif-black", "sans-serif-condensed",
        "sans-serif-condensed-light", "sans-serif-condensed-medium",
        "sans-serif-condensed-black", "sans-serif-smallcaps",
        "serif", "serif-monospace", "monospace", "casual", "cursive"
    )

    fun typefaceFor(
        fontName: String,
        bold: Boolean = false,
        italic: Boolean = false
    ): Typeface {
        val name = fontName.trim().ifEmpty { "Arial" }
        val normalized = name.lowercase()

        // If user already provided a system family, use it directly
        val family = if (normalized in SYSTEM_FAMILIES) {
            normalized
        } else {
            // Look up in map, fallback to sans-serif
            FONT_MAP[normalized] ?: "sans-serif"
        }

        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }

        return Typeface.create(family, style)
    }
}
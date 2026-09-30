package com.moody.moodyvideoeditor.utils

import androidx.compose.ui.text.font.FontFamily

/**
 * Mirrors js/codebase/fontLibrary.js
 * Maps font names → Compose FontFamily (via generic fallback).
 * Add .ttf files to res/font later for real custom fonts.
 */
object FontLibrary {

    // 15 categories — mirrors FONT_CATEGORIES in JS
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

    /** All fonts, deduped */
    fun allFonts(): List<String> =
        FONT_CATEGORIES.values.flatten().distinct().sorted()

    /** Category names */
    fun categories(): List<String> = FONT_CATEGORIES.keys.toList()

    /**
     * Mirrors JS resolveFontFamily().
     * If input is category name → first font of that category.
     * Else return input as-is.
     */
    fun resolveFontName(input: String): String {
        val raw = input.trim()
        if (raw.isEmpty()) return "Arial"
        val key = raw.lowercase().replace(" ", "")
        FONT_CATEGORIES[key]?.let { list -> return list.firstOrNull() ?: "Arial" }
        return raw
    }

    /**
     * Mirrors JS loadGoogleFont() — no-op here. Custom .ttf files
     * need to be added to res/font/ for real custom fonts.
     */
    fun loadFont(fontName: String) {
        // Silent — Android loads from res/font automatically.
    }

    /**
     * Maps any font name to a Compose FontFamily.
     * Uses generic Android families as fallback based on name keywords.
     */
    fun familyFor(fontName: String): FontFamily {
        val n = fontName.lowercase()
        return when {
            n.contains("mono") || n.contains("courier") || n.contains("consol")
                    || n.contains("menlo") || n.contains("monaco")
                    || n.contains("code") -> FontFamily.Monospace

            n.contains("script") || n.contains("brush") || n.contains("hand")
                    || n.contains("comic") || n.contains("cursive")
                    || n.contains("dancing") || n.contains("pacific")
                    || n.contains("vibes") || n.contains("amita")
                    || n.contains("chopin") || n.contains("musiclife")
                    || n.contains("caveat") || n.contains("allura")
                    || n.contains("satisfy") || n.contains("kaushan")
                    || n.contains("parisienne") || n.contains("sacramento")
                    || n.contains("tangerine") || n.contains("indie")
                    || n.contains("patrick") || n.contains("kalam") -> FontFamily.Cursive

            n.contains("serif") || n.contains("times") || n.contains("georgia")
                    || n.contains("garamond") || n.contains("baskerville")
                    || n.contains("playfair") || n.contains("cinzel")
                    || n.contains("bodoni") || n.contains("cormorant")
                    || n.contains("merriweather") || n.contains("lora")
                    || n.contains("crimson") || n.contains("prata")
                    || n.contains("cardo") || n.contains("spectral")
                    || n.contains("abril") -> FontFamily.Serif

            else -> FontFamily.SansSerif
        }
    }
}
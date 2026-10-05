package com.moody.moodyvideoeditor.utils

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily

// Mirrors js/codebase/fontLibrary.js
// Maps font names → Compose FontFamily (via generic fallback).
// Add .ttf files to res/font later for real custom fonts.
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

    // All fonts, deduped
    fun allFonts(): List<String> =
        FONT_CATEGORIES.values.flatten().distinct().sorted()

    // Category names
    fun categories(): List<String> = FONT_CATEGORIES.keys.toList()

    // Mirrors JS resolveFontFamily().
    // If input is category name → first font of that category.
    // Else return input as-is.
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

    fun typefaceFor(
        fontName: String,
        bold: Boolean = false,
        italic: Boolean = false
    ): Typeface {
        val name = fontName.trim().ifEmpty { "Arial" }
        val normalized = name.lowercase()
        val directTypeface = Typeface.create(name, Typeface.NORMAL)
        val isDirectFamilyAvailable = normalized.startsWith("sans-serif") ||
                normalized in setOf("serif", "monospace", "cursive") ||
                directTypeface != Typeface.DEFAULT
        val family = if (isDirectFamilyAvailable) {
            name
        } else when {
            normalized.contains("mono") || normalized.contains("courier")
                    || normalized.contains("consol") || normalized.contains("menlo")
                    || normalized.contains("monaco") || normalized.contains("code") ->
                "monospace"

            normalized.contains("script") || normalized.contains("brush")
                    || normalized.contains("hand") || normalized.contains("comic")
                    || normalized.contains("cursive") || normalized.contains("dancing")
                    || normalized.contains("pacific") || normalized.contains("vibes")
                    || normalized.contains("amita") || normalized.contains("chopin")
                    || normalized.contains("musiclife") || normalized.contains("caveat")
                    || normalized.contains("allura") || normalized.contains("satisfy")
                    || normalized.contains("kaushan") || normalized.contains("parisienne")
                    || normalized.contains("sacramento") || normalized.contains("tangerine")
                    || normalized.contains("indie") || normalized.contains("patrick")
                    || normalized.contains("kalam") -> "cursive"

            normalized.contains("serif") || normalized.contains("times")
                    || normalized.contains("georgia") || normalized.contains("garamond")
                    || normalized.contains("baskerville") || normalized.contains("playfair")
                    || normalized.contains("cinzel") || normalized.contains("bodoni")
                    || normalized.contains("cormorant") || normalized.contains("merriweather")
                    || normalized.contains("lora") || normalized.contains("crimson")
                    || normalized.contains("prata") || normalized.contains("cardo")
                    || normalized.contains("spectral") || normalized.contains("abril")
                    || normalized.contains("palatino") || normalized.contains("book")
                    || normalized.contains("didot") -> "serif"

            else -> "sans-serif"
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
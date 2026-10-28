package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.TextState

enum class CaptionPresetMode {
    WORD_HIGHLIGHT,
    CHARACTER_HIGHLIGHT,
    WORD_COLORS
}

data class CaptionPreset(
    val id: String,
    val name: String,
    val mode: CaptionPresetMode,
    val palette: List<Long>,
    val fontFamily: String,
    val fontSize: Int,
    val glowColor: Long,
    val strokeColor: Long,
    val glowEnabled: Boolean = true,
    val strokeEnabled: Boolean = true,
    val animation: String = "none",
    val glowRadius: Float = 18f
)

data class CaptionColorRange(val start: Int, val end: Int, val color: Long)

object CaptionPresetLibrary {
    private val animationPresets = listOf(
        "fadeIn" to "Soft Fade",
        "fadeUp" to "Fade Up",
        "slideUp" to "Slide Up",
        "popIn" to "Pop In",
        "bounceIn" to "Bounce In",
        "blurryReveal" to "Blurry Reveal",
        "characterRise" to "Character Rise",
        "jellyBounce" to "Jelly Bounce",
        "overshootPop" to "Overshoot Pop",
        "crossSlide" to "Cross Slide",
        "zoomIn" to "Zoom In",
        "popScale" to "Pop Scale"
    )

    private val definitions = listOf(
        Triple("Neon Cyan", CaptionPresetMode.WORD_HIGHLIGHT, listOf(0xFF00D9FF, 0xFFFFFFFF)),
        Triple("Golden Pop", CaptionPresetMode.WORD_HIGHLIGHT, listOf(0xFFFFD740, 0xFFFFFFFF)),
        Triple("Lime Pulse", CaptionPresetMode.WORD_HIGHLIGHT, listOf(0xFFB7FF2A, 0xFFFFFFFF)),
        Triple("Hot Pink", CaptionPresetMode.WORD_HIGHLIGHT, listOf(0xFFFF3D9A, 0xFFFFFFFF)),
        Triple("Electric Violet", CaptionPresetMode.WORD_HIGHLIGHT, listOf(0xFFB388FF, 0xFFFFFFFF)),
        Triple("Orange Flash", CaptionPresetMode.WORD_HIGHLIGHT, listOf(0xFFFF8A00, 0xFFFFFFFF)),
        Triple("Ice Blue", CaptionPresetMode.WORD_HIGHLIGHT, listOf(0xFF80DEEA, 0xFFFFFFFF)),
        Triple("Red Alert", CaptionPresetMode.WORD_HIGHLIGHT, listOf(0xFFFF5252, 0xFFFFFFFF)),
        Triple("Word Rainbow", CaptionPresetMode.WORD_COLORS, listOf(0xFFFF5252, 0xFFFFD740, 0xFF69F0AE, 0xFF40C4FF)),
        Triple("Candy Mix", CaptionPresetMode.WORD_COLORS, listOf(0xFFFF80AB, 0xFFB388FF, 0xFF80D8FF)),
        Triple("Tropical", CaptionPresetMode.WORD_COLORS, listOf(0xFF00E5FF, 0xFF76FF03, 0xFFFFEA00)),
        Triple("Fire Words", CaptionPresetMode.WORD_COLORS, listOf(0xFFFF1744, 0xFFFF9100, 0xFFFFEA00)),
        Triple("Pastel Flow", CaptionPresetMode.WORD_COLORS, listOf(0xFFFFAB91, 0xFFF48FB1, 0xFFCE93D8)),
        Triple("Gold & White", CaptionPresetMode.WORD_COLORS, listOf(0xFFFFD740, 0xFFFFFFFF)),
        Triple("Ocean Mix", CaptionPresetMode.WORD_COLORS, listOf(0xFF40C4FF, 0xFF18FFFF, 0xFFB2FF59)),
        Triple("Character Scan", CaptionPresetMode.CHARACTER_HIGHLIGHT, listOf(0xFF00D9FF, 0xFFFFFFFF)),
        Triple("Pink Type", CaptionPresetMode.CHARACTER_HIGHLIGHT, listOf(0xFFFF4081, 0xFFFFFFFF)),
        Triple("Gold Type", CaptionPresetMode.CHARACTER_HIGHLIGHT, listOf(0xFFFFD740, 0xFFFFFFFF)),
        Triple("Violet Type", CaptionPresetMode.CHARACTER_HIGHLIGHT, listOf(0xFFB388FF, 0xFFFFFFFF)),
        Triple("Mint Type", CaptionPresetMode.CHARACTER_HIGHLIGHT, listOf(0xFF69F0AE, 0xFFFFFFFF))
    )

    private val styleFonts = listOf(
        "Anton",
        "Bangers",
        "Impact",
        "Oswald Bold",
        "Poppins Black",
        "Lobster",
        "Damion",
        "Creepster",
        "Audiowide",
        "Carter One",
        "Bowlby One SC"
    )

    private val colorPresets = definitions.mapIndexed { index, (name, mode, colors) ->
        CaptionPreset(
            id = "caption-preset-${index + 1}",
            name = name,
            mode = mode,
            palette = colors,
            fontFamily = styleFonts[index % styleFonts.size],
            fontSize = listOf(42, 46, 50, 54)[index % 4],
            glowColor = colors.first(),
            strokeColor = 0xFF101010,
            glowEnabled = index % 5 != 3,
            strokeEnabled = index % 4 != 1,
            glowRadius = listOf(10f, 18f, 26f, 34f)[index % 4]
        )
    }

    private val animatedPresets = animationPresets.mapIndexed { index, (animation, name) ->
        colorPresets[index % colorPresets.size].copy(
            id = "caption-animation-${index + 1}",
            name = name,
            mode = CaptionPresetMode.WORD_HIGHLIGHT,
            animation = animation
        )
    }

    val presets: List<CaptionPreset> = colorPresets + animatedPresets

    fun find(id: String?): CaptionPreset? = presets.firstOrNull { it.id == id }

    fun hasMovingHighlight(id: String?): Boolean =
        find(id)?.mode?.let { it != CaptionPresetMode.WORD_COLORS } ?: false

    fun applyTo(text: String, presetId: String, highlightColor: Long): TextState {
        val preset = find(presetId) ?: return TextState(content = text)
        return TextState(
            content = text,
            fontFamily = preset.fontFamily,
            fontSize = preset.fontSize,
            fontWeight = "bold",
            color = 0xFFFFFFFF,
            glowEnabled = preset.glowEnabled,
            glowColor = preset.glowColor,
            glowRadius = preset.glowRadius,
            strokeEnabled = preset.strokeEnabled,
            strokeWidth = 2f,
            strokeColor = preset.strokeColor,
            alignment = "center",
            positionY = 78f,
            maxWidth = 95f,
            lineHeight = 1.15f,
            animation = "none",
            captionPresetId = preset.id,
            captionHighlightColor = highlightColor,
            captionPresetAnimation = preset.animation,
            captionPresetAnimationDuration = 0.45f
        )
    }

    fun colorRanges(
        text: String,
        presetId: String?,
        highlightColor: Long,
        elapsedMs: Long,
        durationMs: Long,
        preserveGradient: Boolean = false
    ): List<CaptionColorRange> {
        val preset = find(presetId) ?: return emptyList()
        if (preserveGradient && preset.mode == CaptionPresetMode.WORD_COLORS) {
            return emptyList()
        }
        if (text.isEmpty()) return emptyList()
        val words = Regex("\\S+").findAll(text).toList()
        if (words.isEmpty()) return emptyList()
        return when (preset.mode) {
            CaptionPresetMode.WORD_HIGHLIGHT -> {
                val active = activeIndex(words.size, elapsedMs, durationMs)
                val word = words[active]
                listOf(CaptionColorRange(word.range.first, word.range.last + 1, highlightColor))
            }
            CaptionPresetMode.CHARACTER_HIGHLIGHT -> {
                val characters = text.indices.filterNot { text[it].isWhitespace() }
                val active = characters[activeIndex(characters.size, elapsedMs, durationMs)]
                listOf(CaptionColorRange(active, active + 1, highlightColor))
            }
            CaptionPresetMode.WORD_COLORS -> words.mapIndexed { index, word ->
                CaptionColorRange(
                    word.range.first,
                    word.range.last + 1,
                    preset.palette[index % preset.palette.size]
                )
            }
        }
    }

    private fun activeIndex(count: Int, elapsedMs: Long, durationMs: Long): Int {
        val progress = (elapsedMs.toDouble() / durationMs.coerceAtLeast(1L)).coerceIn(0.0, 0.999999)
        return (progress * count).toInt().coerceIn(0, count - 1)
    }
}

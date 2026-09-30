package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.TextSegment
import com.moody.moodyvideoeditor.data.TextState

/**
 * Blueprint for a complete typography template.
 * Layout coordinates are RELATIVE (0-100%) — scales to any ratio.
 */
data class TypographyTemplateBlueprint(
    val templateId: String,
    val label: String,
    val icon: String,
    val description: String,
    val category: String,
    val previewRatio: String = "9:16",
    val nodes: List<TextNodeBlueprint>
)

data class TextNodeBlueprint(
    val nodeId: String,
    val defaultText: String,
    val relativeX: Float = 50f,
    val relativeY: Float = 50f,
    val fontSizePct: Float = 8f,     // % of canvas WIDTH — auto-scales with ratio
    val fontFamily: String = "Arial",
    val fontWeight: String = "normal",
    val fontStyle: String = "normal",
    val color: Long = 0xFFFFFFFF,
    val strokeEnabled: Boolean = false,
    val strokeWidth: Float = 0f,
    val strokeColor: Long = 0xFF000000,
    val glowEnabled: Boolean = false,
    val glowColor: Long = 0xFF4DD0E1,
    val glowRadius: Float = 25f,
    val letterSpacing: Float = 0f,
    val lineHeight: Float = 1.2f,
    val alignment: String = "center",
    val maxWidth: Float = 85f,
    val animation: String = "none",
    val animationDuration: Float = 0.6f,
    val timingOffsetMs: Long = 0,
    val durationMs: Long = 3000,
    val segments: List<TextSegment> = emptyList()
)

object TypographyTemplates {

    // ═══════════════════════════════════════════════════════════
    //  ALL TEMPLATES
    // ═══════════════════════════════════════════════════════════
    val ALL: List<TypographyTemplateBlueprint> = listOf(

        // ─── 1. MOTIVATIONAL ──────────────────────────────────
        TypographyTemplateBlueprint(
            templateId = "motiv",
            label = "Motivational",
            icon = "🔥",
            description = "Bold caps, high contrast, 4 stacked lines",
            category = "Popular",
            nodes = listOf(
                TextNodeBlueprint(
                    nodeId = "motiv_1",
                    defaultText = "RISE",
                    relativeY = 25f,
                    fontSizePct = 16f,
                    fontFamily = "Impact",
                    fontWeight = "bold",
                    color = 0xFFFF0066,
                    letterSpacing = 8f,
                    animation = "popIn",
                    timingOffsetMs = 0,
                    durationMs = 3000
                ),
                TextNodeBlueprint(
                    nodeId = "motiv_2",
                    defaultText = "AND",
                    relativeY = 42f,
                    fontSizePct = 8f,
                    fontFamily = "Arial",
                    color = 0xFFFFFFFF,
                    letterSpacing = 12f,
                    animation = "fadeIn",
                    timingOffsetMs = 400,
                    durationMs = 2600
                ),
                TextNodeBlueprint(
                    nodeId = "motiv_3",
                    defaultText = "GRIND",
                    relativeY = 58f,
                    fontSizePct = 16f,
                    fontFamily = "Impact",
                    fontWeight = "bold",
                    color = 0xFF00FFCC,
                    letterSpacing = 8f,
                    animation = "popIn",
                    timingOffsetMs = 800,
                    durationMs = 2200
                ),
                TextNodeBlueprint(
                    nodeId = "motiv_4",
                    defaultText = "HARDER",
                    relativeY = 75f,
                    fontSizePct = 14f,
                    fontFamily = "Impact",
                    fontWeight = "bold",
                    color = 0xFFFFCC00,
                    letterSpacing = 6f,
                    animation = "bounceIn",
                    timingOffsetMs = 1200,
                    durationMs = 1800
                )
            )
        ),

        // ─── 2. CINEMATIC ─────────────────────────────────────
        TypographyTemplateBlueprint(
            templateId = "cinematic",
            label = "Cinematic",
            icon = "🎬",
            description = "Elegant serif, wide tracking, slow fade",
            category = "Popular",
            nodes = listOf(
                TextNodeBlueprint(
                    nodeId = "cine_1",
                    defaultText = "A  STORY",
                    relativeY = 42f,
                    fontSizePct = 10f,
                    fontFamily = "Georgia",
                    fontStyle = "italic",
                    color = 0xFFFFFFFF,
                    letterSpacing = 16f,
                    animation = "fadeIn",
                    animationDuration = 1.2f,
                    timingOffsetMs = 0,
                    durationMs = 4000
                ),
                TextNodeBlueprint(
                    nodeId = "cine_2",
                    defaultText = "ABOUT  TIME",
                    relativeY = 55f,
                    fontSizePct = 7f,
                    fontFamily = "Georgia",
                    fontStyle = "italic",
                    color = 0xFFCCCCCC,
                    letterSpacing = 20f,
                    animation = "fadeIn",
                    animationDuration = 1.5f,
                    timingOffsetMs = 800,
                    durationMs = 3200
                )
            )
        ),

        // ─── 3. TIKTOK TRENDY ─────────────────────────────────
        TypographyTemplateBlueprint(
            templateId = "trendy",
            label = "TikTok Trendy",
            icon = "📱",
            description = "Mixed sizes, vibrant colors, fast animation",
            category = "Popular",
            nodes = listOf(
                TextNodeBlueprint(
                    nodeId = "trendy_1",
                    defaultText = "this",
                    relativeY = 22f,
                    fontSizePct = 7f,
                    fontFamily = "Arial",
                    fontWeight = "bold",
                    color = 0xFFFF0066,
                    animation = "slideLeft",
                    timingOffsetMs = 0,
                    durationMs = 2500
                ),
                TextNodeBlueprint(
                    nodeId = "trendy_2",
                    defaultText = "is",
                    relativeY = 35f,
                    fontSizePct = 7f,
                    fontFamily = "Arial",
                    fontWeight = "bold",
                    color = 0xFFFFCC00,
                    animation = "slideLeft",
                    timingOffsetMs = 300,
                    durationMs = 2200
                ),
                TextNodeBlueprint(
                    nodeId = "trendy_3",
                    defaultText = "SO",
                    relativeY = 50f,
                    fontSizePct = 14f,
                    fontFamily = "Impact",
                    fontWeight = "bold",
                    color = 0xFF00FFCC,
                    animation = "bounceIn",
                    timingOffsetMs = 600,
                    durationMs = 1900
                ),
                TextNodeBlueprint(
                    nodeId = "trendy_4",
                    defaultText = "GOOD",
                    relativeY = 68f,
                    fontSizePct = 18f,
                    fontFamily = "Impact",
                    fontWeight = "bold",
                    color = 0xFFFFFFFF,
                    strokeEnabled = true,
                    strokeWidth = 2f,
                    strokeColor = 0xFFFF0066,
                    animation = "popIn",
                    timingOffsetMs = 900,
                    durationMs = 1600
                ),
                TextNodeBlueprint(
                    nodeId = "trendy_5",
                    defaultText = "🔥🔥🔥",
                    relativeY = 85f,
                    fontSizePct = 6f,
                    fontFamily = "Arial",
                    color = 0xFFFFFFFF,
                    animation = "fadeIn",
                    timingOffsetMs = 1200,
                    durationMs = 1300
                )
            )
        ),

        // ─── 4. BOLD IMPACT ───────────────────────────────────
        TypographyTemplateBlueprint(
            templateId = "impact",
            label = "Bold Impact",
            icon = "💥",
            description = "Heavy stroke, tight tracking, high contrast",
            category = "Bold",
            nodes = listOf(
                TextNodeBlueprint(
                    nodeId = "impact_1",
                    defaultText = "WATCH",
                    relativeY = 35f,
                    fontSizePct = 20f,
                    fontFamily = "Arial Black",
                    fontWeight = "bold",
                    color = 0xFFFFCC00,
                    strokeEnabled = true,
                    strokeWidth = 3f,
                    strokeColor = 0xFF000000,
                    letterSpacing = 2f,
                    animation = "popIn",
                    timingOffsetMs = 0,
                    durationMs = 3000
                ),
                TextNodeBlueprint(
                    nodeId = "impact_2",
                    defaultText = "THIS",
                    relativeY = 60f,
                    fontSizePct = 20f,
                    fontFamily = "Arial Black",
                    fontWeight = "bold",
                    color = 0xFFFF0066,
                    strokeEnabled = true,
                    strokeWidth = 3f,
                    strokeColor = 0xFF000000,
                    letterSpacing = 2f,
                    animation = "popIn",
                    timingOffsetMs = 500,
                    durationMs = 2500
                )
            )
        ),

        // ─── 5. NEON GLOW ─────────────────────────────────────
        TypographyTemplateBlueprint(
            templateId = "neon",
            label = "Neon Glow",
            icon = "💡",
            description = "Cyberpunk neon effect with glow",
            category = "Effects",
            nodes = listOf(
                TextNodeBlueprint(
                    nodeId = "neon_1",
                    defaultText = "NIGHT",
                    relativeY = 38f,
                    fontSizePct = 14f,
                    fontFamily = "Arial",
                    fontWeight = "bold",
                    color = 0xFF4DD0E1,
                    glowEnabled = true,
                    glowColor = 0xFF4DD0E1,
                    glowRadius = 30f,
                    letterSpacing = 10f,
                    animation = "fadeIn",
                    animationDuration = 1.0f,
                    timingOffsetMs = 0,
                    durationMs = 3000
                ),
                TextNodeBlueprint(
                    nodeId = "neon_2",
                    defaultText = "CITY",
                    relativeY = 58f,
                    fontSizePct = 14f,
                    fontFamily = "Arial",
                    fontWeight = "bold",
                    color = 0xFFFF0066,
                    glowEnabled = true,
                    glowColor = 0xFFFF0066,
                    glowRadius = 30f,
                    letterSpacing = 10f,
                    animation = "fadeIn",
                    animationDuration = 1.0f,
                    timingOffsetMs = 600,
                    durationMs = 2400
                )
            )
        ),

        // ─── 6. MINIMAL ───────────────────────────────────────
        TypographyTemplateBlueprint(
            templateId = "minimal",
            label = "Minimal",
            icon = "◻",
            description = "Clean, thin, modern sans-serif",
            category = "Clean",
            nodes = listOf(
                TextNodeBlueprint(
                    nodeId = "min_1",
                    defaultText = "less",
                    relativeY = 45f,
                    fontSizePct = 8f,
                    fontFamily = "Arial",
                    fontWeight = "normal",
                    color = 0xFFFFFFFF,
                    letterSpacing = 4f,
                    animation = "fadeUp",
                    timingOffsetMs = 0,
                    durationMs = 3000
                ),
                TextNodeBlueprint(
                    nodeId = "min_2",
                    defaultText = "is  more",
                    relativeY = 55f,
                    fontSizePct = 8f,
                    fontFamily = "Arial",
                    fontWeight = "normal",
                    color = 0xFFAAAAAA,
                    letterSpacing = 4f,
                    animation = "fadeUp",
                    timingOffsetMs = 400,
                    durationMs = 2600
                )
            )
        ),

        // ─── 7. RETRO ─────────────────────────────────────────
        TypographyTemplateBlueprint(
            templateId = "retro",
            label = "Retro",
            icon = "📼",
            description = "80s vibe, thick outline, warm colors",
            category = "Style",
            nodes = listOf(
                TextNodeBlueprint(
                    nodeId = "retro_1",
                    defaultText = "GOOD",
                    relativeY = 38f,
                    fontSizePct = 18f,
                    fontFamily = "Impact",
                    fontWeight = "bold",
                    color = 0xFFFF8A3A,
                    strokeEnabled = true,
                    strokeWidth = 4f,
                    strokeColor = 0xFF4A1040,
                    letterSpacing = 6f,
                    animation = "slideUp",
                    timingOffsetMs = 0,
                    durationMs = 3000
                ),
                TextNodeBlueprint(
                    nodeId = "retro_2",
                    defaultText = "VIBES",
                    relativeY = 62f,
                    fontSizePct = 18f,
                    fontFamily = "Impact",
                    fontWeight = "bold",
                    color = 0xFF00D4A0,
                    strokeEnabled = true,
                    strokeWidth = 4f,
                    strokeColor = 0xFF4A1040,
                    letterSpacing = 6f,
                    animation = "slideUp",
                    timingOffsetMs = 500,
                    durationMs = 2500
                )
            )
        ),

        // ─── 8. QUOTE ─────────────────────────────────────────
        TypographyTemplateBlueprint(
            templateId = "quote",
            label = "Quote",
            icon = "❝",
            description = "Inspirational quote style with serif italic",
            category = "Clean",
            nodes = listOf(
                TextNodeBlueprint(
                    nodeId = "quote_1",
                    defaultText = "\u201CDream big,",
                    relativeY = 42f,
                    fontSizePct = 8f,
                    fontFamily = "Georgia",
                    fontStyle = "italic",
                    color = 0xFFFFFFFF,
                    lineHeight = 1.4f,
                    maxWidth = 80f,
                    animation = "fadeIn",
                    animationDuration = 1.0f,
                    timingOffsetMs = 0,
                    durationMs = 4000
                ),
                TextNodeBlueprint(
                    nodeId = "quote_2",
                    defaultText = "start small.\u201D",
                    relativeY = 58f,
                    fontSizePct = 8f,
                    fontFamily = "Georgia",
                    fontStyle = "italic",
                    color = 0xFFFFCC66,
                    lineHeight = 1.4f,
                    maxWidth = 80f,
                    animation = "fadeIn",
                    animationDuration = 1.0f,
                    timingOffsetMs = 800,
                    durationMs = 3200
                )
            )
        ),

        // ─── 9. WORDS CASCADE (auto-stack mix sizes) ──────────
        TypographyTemplateBlueprint(
            templateId = "cascade",
            label = "Cascade",
            icon = "🌊",
            description = "5 words, mixed sizes, pop-in cascade",
            category = "Bold",
            nodes = listOf(
                TextNodeBlueprint(
                    nodeId = "casc_1",
                    defaultText = "When",
                    relativeY = 15f,
                    fontSizePct = 7f,
                    fontFamily = "Arial",
                    fontWeight = "bold",
                    color = 0xFFFFFFFF,
                    animation = "popIn",
                    timingOffsetMs = 0,
                    durationMs = 3000
                ),
                TextNodeBlueprint(
                    nodeId = "casc_2",
                    defaultText = "you",
                    relativeY = 32f,
                    fontSizePct = 9f,
                    fontFamily = "Arial",
                    fontWeight = "bold",
                    color = 0xFF00FFCC,
                    animation = "popIn",
                    timingOffsetMs = 300,
                    durationMs = 2700
                ),
                TextNodeBlueprint(
                    nodeId = "casc_3",
                    defaultText = "feel",
                    relativeY = 50f,
                    fontSizePct = 11f,
                    fontFamily = "Arial",
                    fontWeight = "bold",
                    color = 0xFFFFCC00,
                    animation = "popIn",
                    timingOffsetMs = 600,
                    durationMs = 2400
                ),
                TextNodeBlueprint(
                    nodeId = "casc_4",
                    defaultText = "like",
                    relativeY = 68f,
                    fontSizePct = 13f,
                    fontFamily = "Arial",
                    fontWeight = "bold",
                    color = 0xFFFF0066,
                    animation = "popIn",
                    timingOffsetMs = 900,
                    durationMs = 2100
                ),
                TextNodeBlueprint(
                    nodeId = "casc_5",
                    defaultText = "QUITTING",
                    relativeY = 85f,
                    fontSizePct = 15f,
                    fontFamily = "Impact",
                    fontWeight = "bold",
                    color = 0xFFFFFFFF,
                    strokeEnabled = true,
                    strokeWidth = 2f,
                    strokeColor = 0xFF000000,
                    animation = "bounceIn",
                    timingOffsetMs = 1200,
                    durationMs = 1800
                )
            )
        ),

        // ─── 10. SPLIT WORDS (mixed typography showcase) ──────
        TypographyTemplateBlueprint(
            templateId = "split",
            label = "Split Words",
            icon = "⚡",
            description = "Same word, different sizes (mixed segment style)",
            category = "Effects",
            nodes = listOf(
                TextNodeBlueprint(
                    nodeId = "split_1",
                    defaultText = "FASTER",
                    relativeY = 45f,
                    fontSizePct = 14f,
                    fontFamily = "Impact",
                    fontWeight = "bold",
                    color = 0xFFFFFFFF,
                    letterSpacing = 4f,
                    animation = "popIn",
                    timingOffsetMs = 0,
                    durationMs = 3000,
                    segments = listOf(
                        // FA bigger, STER smaller — mixed typography demo
                        TextSegment(start = 0, end = 2, fontSize = 60, color = 0xFFFF0066),
                        TextSegment(start = 2, end = 6, fontSize = 36, color = 0xFF00FFCC)
                    )
                )
            )
        )
    )

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════
    fun find(id: String): TypographyTemplateBlueprint? =
        ALL.firstOrNull { it.templateId.equals(id, ignoreCase = true) }

    fun byCategory(): Map<String, List<TypographyTemplateBlueprint>> =
        ALL.groupBy { it.category }

    fun categories(): List<String> = ALL.map { it.category }.distinct()

    /**
     * Convert blueprint node → TextState.
     * fontSize is computed from fontSizePct relative to canvas width.
     */
    fun toTextState(
        node: TextNodeBlueprint,
        canvasWidthPx: Float,
        customText: String? = null,
        timeOverrideStart: Long? = null
    ): TextState {
        // fontSizePct is relative to canvas width
        // e.g., 8% of 400px canvas → 32px
        val computedSize = (node.fontSizePct / 100f * canvasWidthPx).toInt()
            .coerceIn(12, 200)

        val text = customText ?: node.defaultText

        return TextState(
            content = text,
            fontFamily = node.fontFamily,
            fontSize = computedSize,
            fontWeight = node.fontWeight,
            fontStyle = node.fontStyle,
            color = node.color,
            strokeEnabled = node.strokeEnabled,
            strokeWidth = node.strokeWidth,
            strokeColor = node.strokeColor,
            glowEnabled = node.glowEnabled,
            glowColor = node.glowColor,
            glowRadius = node.glowRadius,
            alignment = node.alignment,
            letterSpacing = node.letterSpacing,
            lineHeight = node.lineHeight,
            positionX = node.relativeX,
            positionY = node.relativeY,
            anchorX = 50f,
            anchorY = 50f,
            maxWidth = node.maxWidth,
            animation = node.animation,
            animationDuration = node.animationDuration,
            templateId = "",   // filled by caller
            segments = node.segments
        )
    }
}
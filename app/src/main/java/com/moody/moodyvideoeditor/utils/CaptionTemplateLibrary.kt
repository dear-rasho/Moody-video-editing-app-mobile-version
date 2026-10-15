package com.moody.moodyvideoeditor.utils

// ═══════════════════════════════════════════════════════════════
//  CAPTION TEMPLATE LIBRARY — 20 distinct templates
//  Same diagonal stack structure, but different:
//    → fonts, colors, glows, glow radius, animations
//    → stroke on/off, sizes
//  Ratio-aware: vertical / horizontal / all
// ═══════════════════════════════════════════════════════════════

object CaptionTemplateLibrary {

    // ═══════════════════════════════════════════════════════════
    //  COLOR PALETTES
    // ═══════════════════════════════════════════════════════════

    // Glow colors (with alpha E0 = 88% brightness)
    private const val GLOW_CYAN       = 0xE017D1E1L
    private const val GLOW_PINK       = 0xE0FF4F8BL
    private const val GLOW_GOLD       = 0xE0FFD166L
    private const val GLOW_PURPLE     = 0xE0A855F7L
    private const val GLOW_WHITE      = 0xE0FFFFFFL
    private const val GLOW_RED        = 0xE0FF3B3BL
    private const val GLOW_ORANGE     = 0xE0FF8C42L
    private const val GLOW_GREEN      = 0xE022C55EL
    private const val GLOW_BLUE       = 0xE060AFFFL // typo fix belowprivate const val GLOW_BLUE       = 0xE060AFFFL          private const val GLOW_TEAL       = 0xE014B8A6L
    private const val GLOW_LAVENDER   = 0xE0C4B5FDL
    private const val GLOW_MAGENTA    = 0xE0FF00FFL
    private const val GLOW_TEAL       = 0xE014B8A6L
    private const val GLOW_CORAL      = 0xE0FF7F50L
    private const val GLOW_MINT       = 0xE098FFB3L
    private const val GLOW_ROSE       = 0xE0FFB6C1L
    private const val GLOW_SKY        = 0xE087CEEBL
    private const val GLOW_NEON       = 0xE039FF14L

    val ALL: List<CaptionTemplate> = listOf(

        // ═══════════════════════════════════════════════════════════
        //  1. SCRIPT ELEGANCE — Cyan + Cream
        // ═══════════════════════════════════════════════════════════
        CaptionTemplate(
            id = "t_script_elegance",
            name = "Script Elegance",
            icon = "✍️",
            preferredRatios = RatioKeys.ALL,
            layers = listOf(
                CaptionLayer(
                    fontFamily = "Damion", fontSize = 64, color = 0xFFFFFFFF,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_CYAN, glowRadius = 40f,
                    animation = "blurryReveal", animationDuration = 0.6f,
                    startOffsetMs = 0L
                ),
                CaptionLayer(
                    fontFamily = "Oswald Bold", fontSize = 56, color = 0xFFFFF5E1,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_CYAN, glowRadius = 35f,
                    animation = "jellyBounce", animationDuration = 0.5f,
                    startOffsetMs = 1000L
                ),
                CaptionLayer(
                    fontFamily = "Grand Hotel", fontSize = 76, color = 0xFFFFFFFF,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_CYAN, glowRadius = 42f,
                    animation = "crossSlide", animationDuration = 0.5f,
                    startOffsetMs = 2000L
                )
            )
        ),

        // ═══════════════════════════════════════════════════════════
        //  2. CURSIVE FLOW — Purple + Rose
        // ═══════════════════════════════════════════════════════════
        CaptionTemplate(
            id = "t_cursive_flow",
            name = "Cursive Flow",
            icon = "🌊",
            preferredRatios = RatioKeys.VERTICAL + RatioKeys.SQUARE_1_1,
            layers = listOf(
                CaptionLayer(
                    fontFamily = "Arizonia", fontSize = 70, color = 0xFFFFE4F5,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_PURPLE, glowRadius = 38f,
                    animation = "fadeIn", animationDuration = 0.6f,
                    startOffsetMs = 0L
                ),
                CaptionLayer(
                    fontFamily = "Lavishly Yours", fontSize = 64, color = 0xFFFFC1E3,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_ROSE, glowRadius = 32f,
                    animation = "slideUp", animationDuration = 0.5f,
                    startOffsetMs = 1000L
                ),
                CaptionLayer(
                    fontFamily = "Bonheur Royale", fontSize = 60, color = 0xFFFFF5E1,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_PURPLE, glowRadius = 36f,
                    animation = "characterRise", animationDuration = 0.5f,
                    startOffsetMs = 2000L
                )
            )
        ),

        // ═══════════════════════════════════════════════════════════
        //  3. MODERN DISPLAY — Clean White + Gold
        // ═══════════════════════════════════════════════════════════
        CaptionTemplate(
            id = "t_modern_display",
            name = "Modern Display",
            icon = "🔤",
            preferredRatios = RatioKeys.ALL,
            layers = listOf(
                CaptionLayer(
                    fontFamily = "Poppins Black", fontSize = 58, color = 0xFFFFFFFF,
                    positionX = 0f, positionY = 0f,
                    glowEnabled = false, glowColor = GLOW_WHITE, glowRadius = 0f,
                    animation = "popIn", animationDuration = 0.5f,
                    startOffsetMs = 0L
                ),
                CaptionLayer(
                    fontFamily = "Oswald Bold", fontSize = 62, color = 0xFFFFD166,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_GOLD, glowRadius = 28f,
                    animation = "bounceIn", animationDuration = 0.5f,
                    startOffsetMs = 1000L
                ),
                CaptionLayer(
                    fontFamily = "Palanquin Dark", fontSize = 54, color = 0xFFFFFFFF,
                    positionX = 0f, positionY = 0f,
                    glowEnabled = false, glowColor = GLOW_WHITE, glowRadius = 0f,
                    animation = "slideDown", animationDuration = 0.5f,
                    startOffsetMs = 2000L
                )
            )
        ),

        // ═══════════════════════════════════════════════════════════
        //  4. BOLD IMPACT — Red + White + Stroke
        // ═══════════════════════════════════════════════════════════
        CaptionTemplate(
            id = "t_bold_impact",
            name = "Bold Impact",
            icon = "💥",
            preferredRatios = RatioKeys.VERTICAL + RatioKeys.SQUARE_1_1,
            layers = listOf(
                CaptionLayer(
                    fontFamily = "Anton", fontSize = 76, color = 0xFFFFFFFF,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_RED, glowRadius = 45f,
                    strokeEnabled = true, strokeWidth = 3f, strokeColor = 0xFF000000,
                    animation = "popScale", animationDuration = 0.5f,
                    startOffsetMs = 0L
                ),
                CaptionLayer(
                    fontFamily = "Bangers", fontSize = 70, color = 0xFFFFE5E5,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_RED, glowRadius = 50f,
                    strokeEnabled = true, strokeWidth = 2.5f, strokeColor = 0xFF000000,
                    animation = "overshootPop", animationDuration = 0.5f,
                    startOffsetMs = 1000L
                ),
                CaptionLayer(
                    fontFamily = "Impact", fontSize = 64, color = 0xFFFFFFFF,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_ORANGE, glowRadius = 40f,
                    strokeEnabled = true, strokeWidth = 3f, strokeColor = 0xFF000000,
                    animation = "stompBounce", animationDuration = 0.5f,
                    startOffsetMs = 2000L
                )
            )
        ),

        // ═══════════════════════════════════════════════════════════
        //  5. GOTHIC DARK — Purple + Dark Glow
        // ═══════════════════════════════════════════════════════════
        CaptionTemplate(
            id = "t_gothic_dark",
            name = "Gothic Dark",
            icon = "🔲",
            preferredRatios = RatioKeys.HORIZONTAL,
            layers = listOf(
                CaptionLayer(
                    fontFamily = "Unifraktur Maguntia", fontSize = 66, color = 0xFFD9C9FF,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_PURPLE, glowRadius = 55f,
                    animation = "flicker", animationDuration = 0.6f,
                    startOffsetMs = 0L
                ),
                CaptionLayer(
                    fontFamily = "Uncial Antiqua", fontSize = 58, color = 0xFFE0D0FF,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_PURPLE, glowRadius = 48f,
                    animation = "fadeIn", animationDuration = 0.6f,
                    startOffsetMs = 1000L
                ),
                CaptionLayer(
                    fontFamily = "Jim Nightshade", fontSize = 72, color = 0xFFC4B5FD,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_LAVENDER, glowRadius = 50f,
                    animation = "trailFade", animationDuration = 0.5f,
                    startOffsetMs = 2000L
                )
            )
        ),

        // ═══════════════════════════════════════════════════════════
        //  6. NEON CYBER — Cyan + Magenta dual glow
        // ═══════════════════════════════════════════════════════════
        CaptionTemplate(
            id = "t_neon_cyber",
            name = "Neon Cyber",
            icon = "💠",
            preferredRatios = RatioKeys.ALL,
            layers = listOf(
                CaptionLayer(
                    fontFamily = "Audiowide", fontSize = 60, color = 0xFFFFFFFF,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_MAGENTA, glowRadius = 45f,
                    animation = "glitch", animationDuration = 0.5f,
                    startOffsetMs = 0L
                ),
                CaptionLayer(
                    fontFamily = "Carter One", fontSize = 64, color = 0xFF9BFFEC,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_CYAN, glowRadius = 50f,
                    animation = "popIn", animationDuration = 0.5f,
                    startOffsetMs = 1000L
                ),
                CaptionLayer(
                    fontFamily = "Bangers", fontSize = 70, color = 0xFFFF9BFF,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_MAGENTA, glowRadius = 55f,
                    animation = "zoomIn", animationDuration = 0.5f,
                    startOffsetMs = 2000L
                )
            )
        ),

        // ═══════════════════════════════════════════════════════════
        //  7. VINTAGE RETRO — Warm Amber
        // ═══════════════════════════════════════════════════════════
        CaptionTemplate(
            id = "t_vintage_retro",
            name = "Vintage Retro",
            icon = "📻",
            preferredRatios = RatioKeys.ALL,
            layers = listOf(
                CaptionLayer(
                    fontFamily = "Lobster", fontSize = 66, color = 0xFFFFE5B4,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_ORANGE, glowRadius = 32f,
                    animation = "slideLeft", animationDuration = 0.5f,
                    startOffsetMs = 0L
                ),
                CaptionLayer(
                    fontFamily = "Boogaloo", fontSize = 58, color = 0xFFFFCC80,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_ORANGE, glowRadius = 28f,
                    animation = "slideRight", animationDuration = 0.5f,
                    startOffsetMs = 1000L
                ),
                CaptionLayer(
                    fontFamily = "Bonbon", fontSize = 64, color = 0xFFFFF0D5,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_GOLD, glowRadius = 36f,
                    animation = "slideUp", animationDuration = 0.5f,
                    startOffsetMs = 2000L
                )
            )
        ),

        // ═══════════════════════════════════════════════════════════
        //  8. SOFT CURSIVE — Pink Glow
        // ═══════════════════════════════════════════════════════════
        CaptionTemplate(
            id = "t_soft_cursive",
            name = "Soft Cursive",
            icon = "🌸",
            preferredRatios = RatioKeys.VERTICAL + RatioKeys.SQUARE_1_1,
            layers = listOf(
                CaptionLayer(
                    fontFamily = "Shadows Into Light", fontSize = 66, color = 0xFFFFF0F5,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_PINK, glowRadius = 35f,
                    animation = "fadeUp", animationDuration = 0.6f,
                    startOffsetMs = 0L
                ),
                CaptionLayer(
                    fontFamily = "Stalemate", fontSize = 72, color = 0xFFFFC0CB,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_ROSE, glowRadius = 42f,
                    animation = "fadeDown", animationDuration = 0.5f,
                    startOffsetMs = 1000L
                ),
                CaptionLayer(
                    fontFamily = "Yesteryear", fontSize = 68, color = 0xFFFFE4E1,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_PINK, glowRadius = 45f,
                    animation = "characterRise", animationDuration = 0.5f,
                    startOffsetMs = 2000L
                )
            )
        ),

        // ═══════════════════════════════════════════════════════════
        //  9. FANTASY MAGIC — Purple + Teal
        // ═══════════════════════════════════════════════════════════
        CaptionTemplate(
            id = "t_fantasy_magic",
            name = "Fantasy Magic",
            icon = "🪄",
            preferredRatios = RatioKeys.VERTICAL + RatioKeys.SQUARE_1_1,
            layers = listOf(
                CaptionLayer(
                    fontFamily = "Henny Penny", fontSize = 60, color = 0xFFE0F7F4,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_TEAL, glowRadius = 40f,
                    animation = "blurryReveal", animationDuration = 0.6f,
                    startOffsetMs = 0L
                ),
                CaptionLayer(
                    fontFamily = "Isometra", fontSize = 64, color = 0xFFD9C9FF,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_PURPLE, glowRadius = 45f,
                    animation = "smokeDissolve", animationDuration = 0.6f,
                    startOffsetMs = 1000L
                ),
                CaptionLayer(
                    fontFamily = "Caprasimo", fontSize = 68, color = 0xFFFFF5E1,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_TEAL, glowRadius = 48f,
                    animation = "neonGlow", animationDuration = 0.5f,
                    startOffsetMs = 2000L
                )
            )
        ),

        // ═══════════════════════════════════════════════════════════
        //  10. CLEAN MODERN — Pure White, no glow
        // ═══════════════════════════════════════════════════════════
        CaptionTemplate(
            id = "t_clean_modern",
            name = "Clean Modern",
            icon = "⚪",
            preferredRatios = RatioKeys.ALL,
            layers = listOf(
                CaptionLayer(
                    fontFamily = "Oswald Bold", fontSize = 62, color = 0xFFFFFFFF,
                    positionX = 0f, positionY = 0f,
                    glowEnabled = false, glowColor = GLOW_WHITE, glowRadius = 0f,
                    animation = "slideUp", animationDuration = 0.5f,
                    startOffsetMs = 0L
                ),
                CaptionLayer(
                    fontFamily = "Poppins SemiBold", fontSize = 58, color = 0xFFFFFFFF,
                    positionX = 0f, positionY = 0f,
                    glowEnabled = false, glowColor = GLOW_WHITE, glowRadius = 0f,
                    animation = "fadeUp", animationDuration = 0.5f,
                    startOffsetMs = 1000L
                ),
                CaptionLayer(
                    fontFamily = "Palanquin Dark", fontSize = 54, color = 0xFFEEEEEE,
                    positionX = 0f, positionY = 0f,
                    glowEnabled = false, glowColor = GLOW_WHITE, glowRadius = 0f,
                    animation = "slideDown", animationDuration = 0.5f,
                    startOffsetMs = 2000L
                )
            )
        ),

        // ═══════════════════════════════════════════════════════════
        //  11. WHIMSICAL — Mint + Coral playful
        // ═══════════════════════════════════════════════════════════
        CaptionTemplate(
            id = "t_whimsical",
            name = "Whimsical",
            icon = "🎈",
            preferredRatios = RatioKeys.VERTICAL + RatioKeys.SQUARE_1_1,
            layers = listOf(
                CaptionLayer(
                    fontFamily = "Bonbon", fontSize = 60, color = 0xFFFFF5E1,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_CORAL, glowRadius = 38f,
                    animation = "jellyBounce", animationDuration = 0.5f,
                    startOffsetMs = 0L
                ),
                CaptionLayer(
                    fontFamily = "Crafty Girls", fontSize = 64, color = 0xFFB8F2E6,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_MINT, glowRadius = 42f,
                    animation = "wobble", animationDuration = 0.5f,
                    startOffsetMs = 1000L
                ),
                CaptionLayer(
                    fontFamily = "Boogaloo", fontSize = 68, color = 0xFFFFCCB3,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_CORAL, glowRadius = 45f,
                    animation = "bounceIn", animationDuration = 0.5f,
                    startOffsetMs = 2000L
                )
            )
        ),

        // ═══════════════════════════════════════════════════════════
        //  12. CINEMATIC SERIF — Gold + Ivory
        // ═══════════════════════════════════════════════════════════
        CaptionTemplate(
            id = "t_cinematic_serif",
            name = "Cinematic Serif",
            icon = "🎬",
            preferredRatios = RatioKeys.HORIZONTAL,
            layers = listOf(
                CaptionLayer(
                    fontFamily = "Georgia", fontSize = 58, color = 0xFFF5F0DC,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_GOLD, glowRadius = 30f,
                    animation = "fadeIn", animationDuration = 0.8f,
                    startOffsetMs = 0L
                ),
                CaptionLayer(
                    fontFamily = "Amiri", fontSize = 64, color = 0xFFFFE9B3,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_GOLD, glowRadius = 35f,
                    animation = "fadeIn", animationDuration = 0.8f,
                    startOffsetMs = 1000L
                ),
                CaptionLayer(
                    fontFamily = "Castoro Titling", fontSize = 60, color = 0xFFFFF5E1,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_ORANGE, glowRadius = 32f,
                    animation = "characterRise", animationDuration = 0.6f,
                    startOffsetMs = 2000L
                )
            )
        ),

        // ═══════════════════════════════════════════════════════════
        //  13. ARTISTIC MIX — Red + Pink Bold
        // ═══════════════════════════════════════════════════════════
        CaptionTemplate(
            id = "t_artistic_mix",
            name = "Artistic Mix",
            icon = "🎨",
            preferredRatios = RatioKeys.ALL,
            layers = listOf(
                CaptionLayer(
                    fontFamily = "Amita", fontSize = 64, color = 0xFFFFFFFF,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_PINK, glowRadius = 40f,
                    animation = "fadeUp", animationDuration = 0.5f,
                    startOffsetMs = 0L
                ),
                CaptionLayer(
                    fontFamily = "Felipa", fontSize = 68, color = 0xFFFFC1CC,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_RED, glowRadius = 45f,
                    animation = "slideRight", animationDuration = 0.5f,
                    startOffsetMs = 1000L
                ),
                CaptionLayer(
                    fontFamily = "Creepster", fontSize = 72, color = 0xFFFF6B6B,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_RED, glowRadius = 55f,
                    strokeEnabled = true, strokeWidth = 2f, strokeColor = 0xFF000000,
                    animation = "overshootPop", animationDuration = 0.5f,
                    startOffsetMs = 2000L
                )
            )
        ),

        // ═══════════════════════════════════════════════════════════
        //  14. BALLET GRACE — Lavender + Sky
        // ═══════════════════════════════════════════════════════════
        CaptionTemplate(
            id = "t_ballet_grace",
            name = "Ballet Grace",
            icon = "🩰",
            preferredRatios = RatioKeys.VERTICAL + RatioKeys.SQUARE_1_1,
            layers = listOf(
                CaptionLayer(
                    fontFamily = "Ballet 28pt", fontSize = 62, color = 0xFFF0E8FF,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_LAVENDER, glowRadius = 38f,
                    animation = "fadeIn", animationDuration = 0.7f,
                    startOffsetMs = 0L
                ),
                CaptionLayer(
                    fontFamily = "Ballet Variable", fontSize = 66, color = 0xFFE0EEFF,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_SKY, glowRadius = 42f,
                    animation = "blurryReveal", animationDuration = 0.6f,
                    startOffsetMs = 1000L
                ),
                CaptionLayer(
                    fontFamily = "Neonderthaw", fontSize = 70, color = 0xFFD9C9FF,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_LAVENDER, glowRadius = 50f,
                    animation = "neonGlow", animationDuration = 0.5f,
                    startOffsetMs = 2000L
                )
            )
        ),

        // ═══════════════════════════════════════════════════════════
        //  15. PLAYFUL POP — Rainbow mix
        // ═══════════════════════════════════════════════════════════
        CaptionTemplate(
            id = "t_playful_pop",
            name = "Playful Pop",
            icon = "🎉",
            preferredRatios = RatioKeys.VERTICAL + RatioKeys.SQUARE_1_1,
            layers = listOf(
                CaptionLayer(
                    fontFamily = "BBH Bartle", fontSize = 62, color = 0xFFFFE066,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_GOLD, glowRadius = 42f,
                    animation = "popIn", animationDuration = 0.5f,
                    startOffsetMs = 0L
                ),
                CaptionLayer(
                    fontFamily = "Bonbon", fontSize = 66, color = 0xFFB8F2E6,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_MINT, glowRadius = 45f,
                    animation = "bounceIn", animationDuration = 0.5f,
                    startOffsetMs = 1000L
                ),
                CaptionLayer(
                    fontFamily = "Bonheur Royale", fontSize = 70, color = 0xFFFF9BFF,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_MAGENTA, glowRadius = 50f,
                    animation = "slideDown", animationDuration = 0.5f,
                    startOffsetMs = 2000L
                )
            )
        ),

        // ═══════════════════════════════════════════════════════════
        //  16. DIGITAL WAVES — Teal + Sky tech
        // ═══════════════════════════════════════════════════════════
        CaptionTemplate(
            id = "t_digital_waves",
            name = "Digital Waves",
            icon = "📡",
            preferredRatios = RatioKeys.ALL,
            layers = listOf(
                CaptionLayer(
                    fontFamily = "Audiowide", fontSize = 58, color = 0xFF9BFFEC,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_TEAL, glowRadius = 38f,
                    animation = "wave", animationDuration = 0.5f,
                    startOffsetMs = 0L
                ),
                CaptionLayer(
                    fontFamily = "Audiowide", fontSize = 60, color = 0xFF87CEEB,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_SKY, glowRadius = 42f,
                    animation = "glitch", animationDuration = 0.5f,
                    startOffsetMs = 1000L
                ),
                CaptionLayer(
                    fontFamily = "Isometra", fontSize = 64, color = 0xFFFFFFFF,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_TEAL, glowRadius = 48f,
                    animation = "zoomIn", animationDuration = 0.5f,
                    startOffsetMs = 2000L
                )
            )
        ),

        // ═══════════════════════════════════════════════════════════
        //  17. ELEGANT SCRIPT — Gold + Ivory
        // ═══════════════════════════════════════════════════════════
        CaptionTemplate(
            id = "t_elegant_script",
            name = "Elegant Script",
            icon = "🕊️",
            preferredRatios = RatioKeys.HORIZONTAL,
            layers = listOf(
                CaptionLayer(
                    fontFamily = "Petit Formal Script", fontSize = 62,
                    color = 0xFFFFF5E1,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_GOLD, glowRadius = 35f,
                    animation = "fadeIn", animationDuration = 0.6f,
                    startOffsetMs = 0L
                ),
                CaptionLayer(
                    fontFamily = "Damion", fontSize = 66, color = 0xFFFFE9B3,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_GOLD, glowRadius = 40f,
                    animation = "slideUp", animationDuration = 0.5f,
                    startOffsetMs = 1000L
                ),
                CaptionLayer(
                    fontFamily = "Lavishly Yours", fontSize = 70, color = 0xFFFFD166,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_ORANGE, glowRadius = 45f,
                    animation = "fadeUp", animationDuration = 0.5f,
                    startOffsetMs = 2000L
                )
            )
        ),

        // ═══════════════════════════════════════════════════════════
        //  18. HANDWRITTEN WARM — Amber + Cream
        // ═══════════════════════════════════════════════════════════
        CaptionTemplate(
            id = "t_handwritten_warm",
            name = "Handwritten Warm",
            icon = "📝",
            preferredRatios = RatioKeys.VERTICAL,
            layers = listOf(
                CaptionLayer(
                    fontFamily = "Edu QLD Hand", fontSize = 60, color = 0xFFFFF5E1,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_ORANGE, glowRadius = 30f,
                    animation = "fadeUp", animationDuration = 0.5f,
                    startOffsetMs = 0L
                ),
                CaptionLayer(
                    fontFamily = "Grechen Fuemen", fontSize = 66, color = 0xFFFFD9A0,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_ORANGE, glowRadius = 35f,
                    animation = "fadeIn", animationDuration = 0.6f,
                    startOffsetMs = 1000L
                ),
                CaptionLayer(
                    fontFamily = "Story Script", fontSize = 62, color = 0xFFFFCC80,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_GOLD, glowRadius = 40f,
                    animation = "characterRise", animationDuration = 0.5f,
                    startOffsetMs = 2000L
                )
            )
        ),

        // ═══════════════════════════════════════════════════════════
        //  19. BOLD DISPLAY — Red + Neon Green
        // ═══════════════════════════════════════════════════════════
        CaptionTemplate(
            id = "t_bold_display",
            name = "Bold Display",
            icon = "🔨",
            preferredRatios = RatioKeys.HORIZONTAL,
            layers = listOf(
                CaptionLayer(
                    fontFamily = "Erica One", fontSize = 64, color = 0xFFFFFFFF,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_RED, glowRadius = 48f,
                    animation = "stompBounce", animationDuration = 0.5f,
                    startOffsetMs = 0L
                ),
                CaptionLayer(
                    fontFamily = "Chango", fontSize = 60, color = 0xFF39FF14,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_NEON, glowRadius = 50f,
                    animation = "popScale", animationDuration = 0.5f,
                    startOffsetMs = 1000L
                ),
                CaptionLayer(
                    fontFamily = "Castoro Titling", fontSize = 64, color = 0xFFFF3B3B,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_RED, glowRadius = 55f,
                    strokeEnabled = true, strokeWidth = 2f, strokeColor = 0xFF000000,
                    animation = "bounceIn", animationDuration = 0.5f,
                    startOffsetMs = 2000L
                )
            )
        ),

        // ═══════════════════════════════════════════════════════════
        //  20. MIXED MODERN — Coral + Teal
        // ═══════════════════════════════════════════════════════════
        CaptionTemplate(
            id = "t_mixed_modern",
            name = "Mixed Modern",
            icon = "🎯",
            preferredRatios = RatioKeys.ALL,
            layers = listOf(
                CaptionLayer(
                    fontFamily = "Poppins Bold", fontSize = 62, color = 0xFFFFFFFF,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_CORAL, glowRadius = 38f,
                    animation = "popIn", animationDuration = 0.5f,
                    startOffsetMs = 0L
                ),
                CaptionLayer(
                    fontFamily = "Bangers", fontSize = 66, color = 0xFFFFCCB3,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_CORAL, glowRadius = 45f,
                    animation = "overshootPop", animationDuration = 0.5f,
                    startOffsetMs = 1000L
                ),
                CaptionLayer(
                    fontFamily = "Grand Hotel", fontSize = 70, color = 0xFF9BFFEC,
                    positionX = 0f, positionY = 0f,
                    glowColor = GLOW_TEAL, glowRadius = 50f,
                    animation = "slideLeft", animationDuration = 0.5f,
                    startOffsetMs = 2000L
                )
            )
        )
    )

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════

    fun templatesForRatio(ratio: String): List<CaptionTemplate> {
        return ALL.filter { ratio in it.preferredRatios }
    }

    fun randomForRatio(ratio: String): CaptionTemplate? {
        val available = templatesForRatio(ratio)
        return available.randomOrNull()
    }

    fun findById(id: String): CaptionTemplate? = ALL.find { it.id == id }
}
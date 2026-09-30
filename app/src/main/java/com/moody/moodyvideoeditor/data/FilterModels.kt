package com.moody.moodyvideoeditor.data

/**
 * Mirrors js/features/filters.js FILTERS list.
 * 9 CSS filter primitives with individual min/max/default.
 */
data class FilterState(
    val brightness: Float = 100f,
    val contrast: Float = 100f,
    val saturation: Float = 100f,
    val hue: Float = 0f,
    val grayscale: Float = 0f,
    val sepia: Float = 0f,
    val invert: Float = 0f,
    val blur: Float = 0f,
    val opacity: Float = 100f
) {
    val isDefault: Boolean get() = this == FilterState()

    val hasAnyChange: Boolean
        get() = brightness != 100f || contrast != 100f || saturation != 100f ||
                hue != 0f || grayscale != 0f || sepia != 0f || invert != 0f ||
                blur != 0f || opacity != 100f

    fun get(key: String): Float = when (key) {
        "brightness" -> brightness
        "contrast" -> contrast
        "saturation" -> saturation
        "hue" -> hue
        "grayscale" -> grayscale
        "sepia" -> sepia
        "invert" -> invert
        "blur" -> blur
        "opacity" -> opacity
        else -> 0f
    }

    fun set(key: String, value: Float): FilterState = when (key) {
        "brightness" -> copy(brightness = value)
        "contrast" -> copy(contrast = value)
        "saturation" -> copy(saturation = value)
        "hue" -> copy(hue = value)
        "grayscale" -> copy(grayscale = value)
        "sepia" -> copy(sepia = value)
        "invert" -> copy(invert = value)
        "blur" -> copy(blur = value)
        "opacity" -> copy(opacity = value)
        else -> this
    }

    fun isChanged(key: String): Boolean {
        val meta = FILTERS.firstOrNull { it.key == key } ?: return false
        return get(key) != meta.default
    }

    companion object {
        data class Meta(
            val key: String,
            val label: String,
            val icon: String,
            val min: Float,
            val max: Float,
            val step: Float,
            val default: Float,
            val suffix: String
        )

        val FILTERS = listOf(
            Meta("brightness", "Brightness", "☀️", 0f, 200f, 1f, 100f, "%"),
            Meta("contrast", "Contrast", "◐", 0f, 200f, 1f, 100f, "%"),
            Meta("saturation", "Saturation", "🎨", 0f, 200f, 1f, 100f, "%"),
            Meta("hue", "Hue", "🌈", 0f, 360f, 1f, 0f, "°"),
            Meta("grayscale", "Grayscale", "⚪", 0f, 100f, 1f, 0f, "%"),
            Meta("sepia", "Sepia", "🟫", 0f, 100f, 1f, 0f, "%"),
            Meta("invert", "Invert", "🔄", 0f, 100f, 1f, 0f, "%")
        )

        // ═══════════════════════════════════════════════════════════
        //  🆕 FILTER PRESETS — 135 total
        // ═══════════════════════════════════════════════════════════
        val PRESET_CATEGORIES = listOf(
            "cinematic" to "🎬 Cinematic & Movies",
            "retro" to "📸 Retro & Vintage",
            "hd" to "✨ 4K & Sharpness",
            "aesthetic" to "🍃 Aesthetic Vibe",
            "neon" to "🎨 Cyber & Neon",
            "scenery" to "🌆 Travel & Landscape",
            "portrait" to "👤 Portrait & Glow",
            "moody" to "🌫️ Atmospheric & Moody"
        )

        val PRESETS: List<FilterPreset> = listOf(
            // ═══════════ 🎬 CINEMATIC (20) ═══════════
            FilterPreset(
                "badbunny",
                "Bad Bunny",
                "🎬",
                "cinematic",
                brightness = 95f,
                contrast = 115f,
                saturation = 110f
            ),
            FilterPreset(
                "moonrise",
                "Moon Rise",
                "🌙",
                "cinematic",
                brightness = 85f,
                contrast = 110f,
                saturation = 90f
            ),
            FilterPreset(
                "oppenheimer",
                "Oppenheimer",
                "💥",
                "cinematic",
                brightness = 90f,
                contrast = 140f,
                saturation = 55f
            ),
            FilterPreset(
                "barbie",
                "Barbie AI",
                "🌸",
                "cinematic",
                brightness = 110f,
                contrast = 105f,
                saturation = 155f
            ),
            FilterPreset(
                "inception",
                "Inception",
                "🌀",
                "cinematic",
                brightness = 100f,
                contrast = 110f,
                saturation = 100f,
                blur = 0.5f
            ),
            FilterPreset(
                "serenity",
                "Serenity Blue",
                "🌊",
                "cinematic",
                brightness = 108f,
                contrast = 95f,
                saturation = 105f,
                hue = 200f
            ),
            FilterPreset(
                "dunkirk",
                "Dunkirk",
                "⚔️",
                "cinematic",
                brightness = 95f,
                contrast = 105f,
                saturation = 70f
            ),
            FilterPreset(
                "darkgrey",
                "Dark Grey",
                "🩶",
                "cinematic",
                brightness = 80f,
                contrast = 120f,
                saturation = 40f
            ),
            FilterPreset(
                "cinemetric",
                "Cine Metric",
                "🎥",
                "cinematic",
                brightness = 98f,
                contrast = 115f,
                saturation = 105f
            ),
            FilterPreset(
                "lalaland",
                "La La Land",
                "💃",
                "cinematic",
                brightness = 105f,
                contrast = 108f,
                saturation = 145f,
                sepia = 10f
            ),
            FilterPreset(
                "pulpfiction",
                "Pulp Fiction",
                "💼",
                "cinematic",
                brightness = 102f,
                contrast = 120f,
                saturation = 115f,
                sepia = 15f
            ),
            FilterPreset(
                "godfather",
                "Godfather",
                "🎩",
                "cinematic",
                brightness = 92f,
                contrast = 115f,
                saturation = 90f,
                sepia = 45f
            ),
            FilterPreset(
                "cyberpunk",
                "Cyberpunk 2077",
                "🤖",
                "cinematic",
                brightness = 95f,
                contrast = 125f,
                saturation = 175f
            ),
            FilterPreset(
                "matrix",
                "Matrix Green",
                "🟢",
                "cinematic",
                brightness = 90f,
                contrast = 115f,
                saturation = 100f,
                hue = 90f
            ),
            FilterPreset(
                "midnighttokyo",
                "Midnight Tokyo",
                "🌃",
                "cinematic",
                brightness = 80f,
                contrast = 130f,
                saturation = 95f
            ),
            FilterPreset(
                "interstellar",
                "Interstellar",
                "🚀",
                "cinematic",
                brightness = 78f,
                contrast = 120f,
                saturation = 110f,
                hue = 220f
            ),
            FilterPreset(
                "joker",
                "Joker Green",
                "🃏",
                "cinematic",
                brightness = 95f,
                contrast = 125f,
                saturation = 120f,
                hue = 60f
            ),
            FilterPreset(
                "nomadland",
                "Nomadland",
                "🏕️",
                "cinematic",
                brightness = 100f,
                contrast = 105f,
                saturation = 95f,
                sepia = 8f
            ),
            FilterPreset(
                "wesanderson",
                "Wes Anderson",
                "🎨",
                "cinematic",
                brightness = 112f,
                contrast = 88f,
                saturation = 115f
            ),
            FilterPreset(
                "filmtone",
                "Film Tone",
                "🎞️",
                "cinematic",
                brightness = 98f,
                contrast = 112f,
                saturation = 95f,
                sepia = 5f
            ),

            // ═══════════ 📸 RETRO & VINTAGE (20) ═══════════
            FilterPreset(
                "flashccd",
                "Flash CCD",
                "📷",
                "retro",
                brightness = 125f,
                contrast = 95f,
                saturation = 105f
            ),
            FilterPreset(
                "vhstape",
                "VHS Tape",
                "📼",
                "retro",
                brightness = 98f,
                contrast = 95f,
                saturation = 110f,
                blur = 0.4f
            ),
            FilterPreset(
                "camrecorder",
                "Cam Recorder",
                "📹",
                "retro",
                brightness = 115f,
                contrast = 88f,
                saturation = 75f
            ),
            FilterPreset(
                "retrocam",
                "Retro Cam",
                "🎞️",
                "retro",
                brightness = 105f,
                contrast = 95f,
                saturation = 90f,
                sepia = 20f
            ),
            FilterPreset(
                "90sfine",
                "90s Fine",
                "💿",
                "retro",
                brightness = 108f,
                contrast = 92f,
                saturation = 85f,
                sepia = 15f
            ),
            FilterPreset(
                "miamiretro",
                "Miami Retro",
                "🌴",
                "retro",
                brightness = 105f,
                contrast = 105f,
                saturation = 135f,
                hue = 320f
            ),
            FilterPreset(
                "carmel",
                "Carmel",
                "🍮",
                "retro",
                brightness = 103f,
                contrast = 100f,
                saturation = 115f,
                sepia = 25f
            ),
            FilterPreset(
                "cammelia",
                "Cammelia",
                "🌺",
                "retro",
                brightness = 102f,
                contrast = 98f,
                saturation = 85f,
                sepia = 40f
            ),
            FilterPreset(
                "oldfootage",
                "Old Footage",
                "📽️",
                "retro",
                brightness = 100f,
                contrast = 95f,
                saturation = 70f,
                sepia = 35f
            ),
            FilterPreset(
                "vintagestk",
                "Vintage Estetik",
                "🕰️",
                "retro",
                brightness = 95f,
                contrast = 105f,
                saturation = 80f,
                sepia = 30f
            ),
            FilterPreset(
                "filter90an",
                "Filter 90an",
                "📻",
                "retro",
                brightness = 100f,
                contrast = 98f,
                saturation = 78f,
                hue = 20f
            ),
            FilterPreset(
                "agedfilter",
                "Aged Filter",
                "🗞️",
                "retro",
                brightness = 102f,
                contrast = 95f,
                saturation = 75f,
                sepia = 45f
            ),
            FilterPreset(
                "oldcamera",
                "Old Camera",
                "📸",
                "retro",
                brightness = 95f,
                contrast = 105f,
                saturation = 70f,
                sepia = 30f
            ),
            FilterPreset(
                "nostalgia",
                "Nostalgic Velocity",
                "⏳",
                "retro",
                brightness = 105f,
                contrast = 108f,
                saturation = 85f,
                blur = 0.5f
            ),
            FilterPreset(
                "polaroid",
                "Polaroid Cam",
                "🖼️",
                "retro",
                brightness = 112f,
                contrast = 92f,
                saturation = 88f,
                sepia = 12f
            ),
            FilterPreset(
                "kodak",
                "Kodak Gold 200",
                "🎞️",
                "retro",
                brightness = 105f,
                contrast = 110f,
                saturation = 125f,
                sepia = 8f
            ),
            FilterPreset(
                "fuji",
                "Fuji Film 400",
                "🎞️",
                "retro",
                brightness = 108f,
                contrast = 105f,
                saturation = 100f,
                hue = 350f
            ),
            FilterPreset(
                "super8mm",
                "Super 8mm",
                "🎥",
                "retro",
                brightness = 95f,
                contrast = 105f,
                saturation = 75f,
                sepia = 25f,
                blur = 0.3f
            ),
            FilterPreset(
                "analoggrain",
                "Analog Grain",
                "📊",
                "retro",
                brightness = 100f,
                contrast = 98f,
                saturation = 95f
            ),
            FilterPreset(
                "1998cam",
                "1998 Cam",
                "📅",
                "retro",
                brightness = 110f,
                contrast = 100f,
                saturation = 105f,
                sepia = 10f
            ),

            // ═══════════ ✨ 4K & SHARPNESS (15) ═══════════
            FilterPreset(
                "clearhd",
                "Clear HD",
                "🔷",
                "hd",
                brightness = 102f,
                contrast = 115f,
                saturation = 105f
            ),
            FilterPreset(
                "hdcam2",
                "HD Cam 2",
                "📺",
                "hd",
                brightness = 100f,
                contrast = 112f,
                saturation = 108f
            ),
            FilterPreset(
                "hdupscale",
                "HD Up Scale",
                "⬆️",
                "hd",
                brightness = 100f,
                contrast = 118f,
                saturation = 110f
            ),
            FilterPreset(
                "hddark",
                "HD Dark",
                "🌑",
                "hd",
                brightness = 88f,
                contrast = 125f,
                saturation = 95f
            ),
            FilterPreset(
                "hdsunlight",
                "HD Sunlight",
                "☀️",
                "hd",
                brightness = 112f,
                contrast = 105f,
                saturation = 100f
            ),
            FilterPreset(
                "8kquality",
                "8K Quality",
                "💎",
                "hd",
                brightness = 102f,
                contrast = 122f,
                saturation = 112f
            ),
            FilterPreset(
                "quality1",
                "Quality One",
                "⭐",
                "hd",
                brightness = 100f,
                contrast = 110f,
                saturation = 108f
            ),
            FilterPreset(
                "quality2",
                "Quality Two",
                "🌟",
                "hd",
                brightness = 100f,
                contrast = 115f,
                saturation = 105f
            ),
            FilterPreset(
                "qualityrestore",
                "Quality Restore",
                "🔄",
                "hd",
                brightness = 102f,
                contrast = 112f,
                saturation = 100f
            ),
            FilterPreset(
                "lensharpen",
                "Lens Sharpen",
                "🔍",
                "hd",
                brightness = 100f,
                contrast = 118f,
                saturation = 105f
            ),
            FilterPreset(
                "vividnight",
                "Vivid Night",
                "🌉",
                "hd",
                brightness = 105f,
                contrast = 115f,
                saturation = 120f
            ),
            FilterPreset(
                "iphonehd",
                "iPhone HD",
                "📱",
                "hd",
                brightness = 105f,
                contrast = 108f,
                saturation = 110f
            ),
            FilterPreset(
                "hdrultra",
                "HDR Ultra",
                "🌈",
                "hd",
                brightness = 105f,
                contrast = 135f,
                saturation = 125f
            ),
            FilterPreset(
                "cleanslate",
                "Clean Slate",
                "🃏",
                "hd",
                brightness = 105f,
                contrast = 105f,
                saturation = 95f
            ),
            FilterPreset(
                "12kquality",
                "12K Quality",
                "🏆",
                "hd",
                brightness = 100f,
                contrast = 125f,
                saturation = 110f
            ),

            // ═══════════ 🍃 AESTHETIC (15) ═══════════
            FilterPreset(
                "coolvibes",
                "Cool Vibes",
                "❄️",
                "aesthetic",
                brightness = 105f,
                contrast = 100f,
                saturation = 95f,
                hue = 200f
            ),
            FilterPreset(
                "dreamyglaze",
                "Dreamy Glaze",
                "💭",
                "aesthetic",
                brightness = 112f,
                contrast = 90f,
                saturation = 105f,
                blur = 0.6f
            ),
            FilterPreset(
                "sunkissed",
                "Sunkissed",
                "🌞",
                "aesthetic",
                brightness = 110f,
                contrast = 102f,
                saturation = 120f,
                sepia = 15f
            ),
            FilterPreset(
                "gardenfresh",
                "Garden Fresh",
                "🌱",
                "aesthetic",
                brightness = 105f,
                contrast = 100f,
                saturation = 125f,
                hue = 30f
            ),
            FilterPreset(
                "springbloom",
                "Spring Bloom",
                "🌸",
                "aesthetic",
                brightness = 110f,
                contrast = 95f,
                saturation = 130f
            ),
            FilterPreset(
                "moonlitwood",
                "Moonlit Wood",
                "🌲",
                "aesthetic",
                brightness = 90f,
                contrast = 110f,
                saturation = 85f,
                hue = 200f
            ),
            FilterPreset(
                "moodyamb",
                "Moody Ambience",
                "🌆",
                "aesthetic",
                brightness = 88f,
                contrast = 118f,
                saturation = 90f
            ),
            FilterPreset(
                "autumnheart",
                "Autumn Heart",
                "🍁",
                "aesthetic",
                brightness = 105f,
                contrast = 105f,
                saturation = 135f,
                hue = 40f
            ),
            FilterPreset(
                "cozyglow",
                "Cozy Glow",
                "🕯️",
                "aesthetic",
                brightness = 108f,
                contrast = 95f,
                saturation = 115f,
                sepia = 20f
            ),
            FilterPreset(
                "creamsoft",
                "Creamy Soft",
                "🍦",
                "aesthetic",
                brightness = 112f,
                contrast = 85f,
                saturation = 100f
            ),
            FilterPreset(
                "aesthetictan",
                "Aesthetic Tan",
                "🟤",
                "aesthetic",
                brightness = 105f,
                contrast = 98f,
                saturation = 95f,
                sepia = 25f
            ),
            FilterPreset(
                "lattebrown",
                "Latte Brown",
                "☕",
                "aesthetic",
                brightness = 100f,
                contrast = 105f,
                saturation = 90f,
                sepia = 35f
            ),
            FilterPreset(
                "softcotton",
                "Soft Cotton",
                "☁️",
                "aesthetic",
                brightness = 115f,
                contrast = 85f,
                saturation = 100f
            ),
            FilterPreset(
                "minimalgrey",
                "Minimalist Grey",
                "🌫️",
                "aesthetic",
                brightness = 105f,
                contrast = 105f,
                saturation = 30f
            ),
            FilterPreset(
                "purematcha",
                "Pure Matcha",
                "🍵",
                "aesthetic",
                brightness = 105f,
                contrast = 98f,
                saturation = 110f,
                hue = 45f
            ),

            // ═══════════ 🎨 CYBER & NEON (15) ═══════════
            FilterPreset(
                "neonphoto",
                "Neon Photo",
                "💡",
                "neon",
                brightness = 105f,
                contrast = 125f,
                saturation = 180f
            ),
            FilterPreset(
                "negatifblue",
                "Negatif Blue",
                "🔵",
                "neon",
                brightness = 100f,
                contrast = 115f,
                saturation = 140f,
                hue = 180f
            ),
            FilterPreset(
                "envy",
                "Envy x Wasted",
                "💚",
                "neon",
                brightness = 90f,
                contrast = 130f,
                saturation = 150f,
                hue = 90f
            ),
            FilterPreset(
                "gta",
                "GTA Theme",
                "🚗",
                "neon",
                brightness = 105f,
                contrast = 115f,
                saturation = 145f
            ),
            FilterPreset(
                "swagswell",
                "Swag Swell",
                "😎",
                "neon",
                brightness = 100f,
                contrast = 118f,
                saturation = 155f
            ),
            FilterPreset(
                "synthwave",
                "Synthwave Glow",
                "🌆",
                "neon",
                brightness = 100f,
                contrast = 120f,
                saturation = 160f,
                hue = 280f
            ),
            FilterPreset(
                "laserpink",
                "Laser Pink",
                "💗",
                "neon",
                brightness = 105f,
                contrast = 115f,
                saturation = 175f,
                hue = 320f
            ),
            FilterPreset(
                "acidgreen",
                "Acid Green",
                "🧪",
                "neon",
                brightness = 102f,
                contrast = 125f,
                saturation = 165f,
                hue = 80f
            ),
            FilterPreset(
                "cyberpurple",
                "Cyber Purple",
                "💜",
                "neon",
                brightness = 95f,
                contrast = 125f,
                saturation = 150f,
                hue = 270f
            ),
            FilterPreset(
                "electricviolet",
                "Electric Violet",
                "⚡",
                "neon",
                brightness = 100f,
                contrast = 120f,
                saturation = 155f,
                hue = 260f
            ),
            FilterPreset(
                "popart",
                "Pop Art Yellow",
                "🎨",
                "neon",
                brightness = 110f,
                contrast = 125f,
                saturation = 165f,
                hue = 50f
            ),
            FilterPreset(
                "holo",
                "Holographic Glitch",
                "🔮",
                "neon",
                brightness = 105f,
                contrast = 120f,
                saturation = 170f,
                hue = 200f
            ),
            FilterPreset(
                "vaporwave",
                "Vaporwave Dream",
                "🌴",
                "neon",
                brightness = 105f,
                contrast = 105f,
                saturation = 155f,
                hue = 290f
            ),
            FilterPreset(
                "neonjungle",
                "Neon Jungle",
                "🌿",
                "neon",
                brightness = 95f,
                contrast = 125f,
                saturation = 160f,
                hue = 110f
            ),
            FilterPreset(
                "deepsea",
                "Deep Sea Diver",
                "🐋",
                "neon",
                brightness = 90f,
                contrast = 115f,
                saturation = 130f,
                hue = 180f
            ),

            // ═══════════ 🌆 TRAVEL & LANDSCAPE (15) ═══════════
            FilterPreset(
                "horizonblue",
                "Horizon Blue",
                "🌅",
                "scenery",
                brightness = 105f,
                contrast = 105f,
                saturation = 115f,
                hue = 200f
            ),
            FilterPreset(
                "forestmoss",
                "Forest Moss",
                "🌲",
                "scenery",
                brightness = 98f,
                contrast = 108f,
                saturation = 105f,
                hue = 60f
            ),
            FilterPreset(
                "desertsand",
                "Desert Sand",
                "🏜️",
                "scenery",
                brightness = 108f,
                contrast = 105f,
                saturation = 120f,
                sepia = 20f
            ),
            FilterPreset(
                "oceanbreeze",
                "Ocean Breeze",
                "🌊",
                "scenery",
                brightness = 105f,
                contrast = 108f,
                saturation = 130f,
                hue = 175f
            ),
            FilterPreset(
                "sunsetglow",
                "Sunset Glow",
                "🌇",
                "scenery",
                brightness = 108f,
                contrast = 105f,
                saturation = 135f,
                sepia = 10f
            ),
            FilterPreset(
                "goldenhour",
                "Golden Hour",
                "🌞",
                "scenery",
                brightness = 112f,
                contrast = 100f,
                saturation = 130f,
                sepia = 15f
            ),
            FilterPreset(
                "emerald",
                "Emerald Green",
                "💎",
                "scenery",
                brightness = 100f,
                contrast = 110f,
                saturation = 125f,
                hue = 80f
            ),
            FilterPreset(
                "alpinechill",
                "Alpine Chill",
                "🏔️",
                "scenery",
                brightness = 102f,
                contrast = 110f,
                saturation = 95f,
                hue = 200f
            ),
            FilterPreset(
                "safari",
                "Safari Warm",
                "🦁",
                "scenery",
                brightness = 105f,
                contrast = 102f,
                saturation = 110f,
                sepia = 25f
            ),
            FilterPreset(
                "islandbreeze",
                "Island Breeze",
                "🏝️",
                "scenery",
                brightness = 108f,
                contrast = 105f,
                saturation = 130f,
                hue = 170f
            ),
            FilterPreset(
                "mountainmist",
                "Mountain Mist",
                "⛰️",
                "scenery",
                brightness = 108f,
                contrast = 88f,
                saturation = 95f
            ),
            FilterPreset(
                "urbanexplorer",
                "Urban Explorer",
                "🏙️",
                "scenery",
                brightness = 100f,
                contrast = 115f,
                saturation = 85f
            ),
            FilterPreset(
                "citylights",
                "City Lights",
                "🌃",
                "scenery",
                brightness = 95f,
                contrast = 125f,
                saturation = 115f
            ),
            FilterPreset(
                "canyonclay",
                "Canyon Clay",
                "🏜️",
                "scenery",
                brightness = 105f,
                contrast = 108f,
                saturation = 125f,
                sepia = 20f
            ),
            FilterPreset(
                "winterfrost",
                "Winter Frost",
                "❄️",
                "scenery",
                brightness = 105f,
                contrast = 105f,
                saturation = 85f,
                hue = 200f
            ),

            // ═══════════ 👤 PORTRAIT & GLOW (20) ═══════════
            FilterPreset(
                "boldglamour",
                "Bold Glamour",
                "💋",
                "portrait",
                brightness = 108f,
                contrast = 115f,
                saturation = 115f
            ),
            FilterPreset(
                "dreamglow",
                "Dream Glow",
                "💫",
                "portrait",
                brightness = 112f,
                contrast = 95f,
                saturation = 108f,
                blur = 0.4f
            ),
            FilterPreset(
                "angel",
                "Angel Effect",
                "😇",
                "portrait",
                brightness = 115f,
                contrast = 95f,
                saturation = 105f,
                blur = 0.5f
            ),
            FilterPreset(
                "clearskin",
                "Clear Skin",
                "🧖",
                "portrait",
                brightness = 105f,
                contrast = 105f,
                saturation = 108f
            ),
            FilterPreset(
                "freshselfie",
                "Fresh Selfie",
                "🤳",
                "portrait",
                brightness = 112f,
                contrast = 102f,
                saturation = 110f
            ),
            FilterPreset(
                "naturalrad",
                "Natural Radiance",
                "✨",
                "portrait",
                brightness = 105f,
                contrast = 105f,
                saturation = 115f
            ),
            FilterPreset(
                "ivoryglow",
                "Ivory Glow",
                "🤍",
                "portrait",
                brightness = 115f,
                contrast = 95f,
                saturation = 100f
            ),
            FilterPreset(
                "porcelain",
                "Soft Porcelain",
                "🎎",
                "portrait",
                brightness = 115f,
                contrast = 90f,
                saturation = 95f
            ),
            FilterPreset(
                "bronzegoddess",
                "Bronze Goddess",
                "🏵️",
                "portrait",
                brightness = 105f,
                contrast = 105f,
                saturation = 120f,
                sepia = 15f
            ),
            FilterPreset(
                "warmhoney",
                "Warm Honey",
                "🍯",
                "portrait",
                brightness = 108f,
                contrast = 102f,
                saturation = 115f,
                sepia = 20f
            ),
            FilterPreset(
                "rosycheeks",
                "Rosy Cheeks",
                "😊",
                "portrait",
                brightness = 108f,
                contrast = 102f,
                saturation = 118f,
                hue = 350f
            ),
            FilterPreset(
                "smoothfocus",
                "Smooth Focus",
                "🎯",
                "portrait",
                brightness = 105f,
                contrast = 100f,
                saturation = 105f,
                blur = 0.3f
            ),
            FilterPreset(
                "bokeh",
                "Bokeh Filter",
                "🌸",
                "portrait",
                brightness = 110f,
                contrast = 95f,
                saturation = 105f,
                blur = 0.8f
            ),
            FilterPreset(
                "velvet",
                "Velvet Matte",
                "🎀",
                "portrait",
                brightness = 100f,
                contrast = 105f,
                saturation = 95f
            ),
            FilterPreset(
                "alabaster",
                "Alabaster Portrait",
                "🗿",
                "portrait",
                brightness = 115f,
                contrast = 105f,
                saturation = 95f
            ),
            FilterPreset(
                "sunkissedskin",
                "Sun-Kissed Skin",
                "🌞",
                "portrait",
                brightness = 110f,
                contrast = 105f,
                saturation = 120f,
                sepia = 10f
            ),
            FilterPreset(
                "goldenglowp",
                "Golden Glow",
                "🌟",
                "portrait",
                brightness = 112f,
                contrast = 108f,
                saturation = 125f,
                sepia = 15f
            ),
            FilterPreset(
                "dewy",
                "Dewy Finish",
                "💧",
                "portrait",
                brightness = 115f,
                contrast = 98f,
                saturation = 110f
            ),
            FilterPreset(
                "studiolight",
                "Studio Portrait",
                "💡",
                "portrait",
                brightness = 108f,
                contrast = 110f,
                saturation = 100f
            ),
            FilterPreset(
                "facecontour",
                "Cinematic Face",
                "🎭",
                "portrait",
                brightness = 100f,
                contrast = 125f,
                saturation = 105f
            ),

            // ═══════════ 🌫️ ATMOSPHERIC & MOODY (15) ═══════════
            FilterPreset(
                "darknoise",
                "Dark Noise",
                "📡",
                "moody",
                brightness = 85f,
                contrast = 130f,
                saturation = 70f
            ),
            FilterPreset(
                "moodyblue",
                "Moody Blue",
                "🌊",
                "moody",
                brightness = 88f,
                contrast = 118f,
                saturation = 95f,
                hue = 220f
            ),
            FilterPreset(
                "foggymorning",
                "Foggy Morning",
                "🌁",
                "moody",
                brightness = 110f,
                contrast = 85f,
                saturation = 80f
            ),
            FilterPreset(
                "rainyday",
                "Rainy Day",
                "🌧️",
                "moody",
                brightness = 95f,
                contrast = 100f,
                saturation = 60f
            ),
            FilterPreset(
                "shadowplay",
                "Shadow Play",
                "🎭",
                "moody",
                brightness = 85f,
                contrast = 145f,
                saturation = 90f
            ),
            FilterPreset(
                "gothicnoir",
                "Gothic Noir",
                "🖤",
                "moody",
                brightness = 85f,
                contrast = 140f,
                saturation = 0f
            ),
            FilterPreset(
                "coldash",
                "Cold Ash",
                "🌋",
                "moody",
                brightness = 92f,
                contrast = 115f,
                saturation = 60f
            ),
            FilterPreset(
                "distantmemory",
                "Distant Memory",
                "💭",
                "moody",
                brightness = 105f,
                contrast = 95f,
                saturation = 90f,
                blur = 0.4f
            ),
            FilterPreset(
                "melancholy",
                "Melancholy Gray",
                "😔",
                "moody",
                brightness = 98f,
                contrast = 105f,
                saturation = 35f
            ),
            FilterPreset(
                "vintagevignette",
                "Vintage Vignette",
                "🎞️",
                "moody",
                brightness = 95f,
                contrast = 110f,
                saturation = 85f,
                sepia = 20f
            ),
            FilterPreset(
                "espresso",
                "Deep Espresso",
                "☕",
                "moody",
                brightness = 90f,
                contrast = 120f,
                saturation = 85f,
                sepia = 30f
            ),
            FilterPreset(
                "overcast",
                "Overcast Mood",
                "☁️",
                "moody",
                brightness = 100f,
                contrast = 92f,
                saturation = 85f
            ),
            FilterPreset(
                "midnightshadow",
                "Midnight Shadow",
                "🌑",
                "moody",
                brightness = 75f,
                contrast = 125f,
                saturation = 80f
            ),
            FilterPreset(
                "lowkeydrama",
                "Low-Key Drama",
                "🎬",
                "moody",
                brightness = 80f,
                contrast = 135f,
                saturation = 90f
            ),
            FilterPreset(
                "ghostly",
                "Ghostly Whisper",
                "👻",
                "moody",
                brightness = 105f,
                contrast = 105f,
                saturation = 50f
            )
        )

        fun findPreset(key: String): FilterPreset? =
            PRESETS.firstOrNull { it.key == key }

        fun presetsInCategory(category: String): List<FilterPreset> =
            PRESETS.filter { it.category == category }
    }
}

/**
 * 🆕 Filter preset with pre-configured values.
 */
data class FilterPreset(
    val key: String,
    val label: String,
    val icon: String,
    val category: String,
    val brightness: Float = 100f,
    val contrast: Float = 100f,
    val saturation: Float = 100f,
    val hue: Float = 0f,
    val grayscale: Float = 0f,
    val sepia: Float = 0f,
    val invert: Float = 0f,
    val blur: Float = 0f,
    val opacity: Float = 100f
) {
    fun toFilterState(): FilterState = FilterState(
        brightness = brightness,
        contrast = contrast,
        saturation = saturation,
        hue = hue,
        grayscale = grayscale,
        sepia = sepia,
        invert = invert,
        blur = blur,
        opacity = opacity
    )
}
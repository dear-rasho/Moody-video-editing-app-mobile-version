package com.moody.moodyvideoeditor.data

// Only keeping transitions that work 100% perfectly in both Preview and Export.
data class TransitionState(
    val key: String = "none",
    val durationMs: Long = 500L
) {
    val isActive: Boolean get() = key != "none"
}

data class TransitionPreset(
    val key: String,
    val label: String,
    val icon: String,
    val category: String,
    val ffmpegXfade: String = "fade"
)

data class TransitionLightLeakStyle(
    val colorHex: String,
    val movesRight: Boolean
)

object TransitionLibrary {

    val CATEGORIES = listOf(
        "fade" to "🌫️ Fade & Opacity",
        "position" to "⬅️ Position & Slide",
        "zoom" to "🔍 Zooms",
        "shape" to "🔷 Shapes & Masks",
        "reveal" to "🎭 Covers & Reveals",
        "cinematic" to "🎞️ Cinematic",
        "3d" to "🧊 3D Moves",
        "shake" to "📳 Shakes",
        "blur" to "💨 Blur Moves",
        "lightleak" to "🌅 Light Leaks"
    )

    private val xfadeTransitions = listOf(
        Triple("fadefast", "Fade Fast", "fade"),
        Triple("fadeslow", "Fade Slow", "fade"),
        Triple("wipeleft", "Wipe Left (Xfade)", "position"),
        Triple("wiperight", "Wipe Right (Xfade)", "position"),
        Triple("wipeup", "Wipe Up (Xfade)", "position"),
        Triple("wipedown", "Wipe Down (Xfade)", "position"),
        Triple("slideleft", "Slide Left (Xfade)", "position"),
        Triple("slideright", "Slide Right (Xfade)", "position"),
        Triple("slideup", "Slide Up (Xfade)", "position"),
        Triple("slidedown", "Slide Down (Xfade)", "position"),
        Triple("circlecrop", "Circle Crop", "shape"),
        Triple("rectcrop", "Rectangle Crop", "shape"),
        Triple("distance", "Distance", "cinematic"),
        Triple("fadeblack", "Fade to Black (Xfade)", "fade"),
        Triple("fadewhite", "Fade to White (Xfade)", "fade"),
        Triple("radial", "Radial Wipe", "shape"),
        Triple("smoothleft", "Smooth Left (Xfade)", "position"),
        Triple("smoothright", "Smooth Right (Xfade)", "position"),
        Triple("smoothup", "Smooth Up (Xfade)", "position"),
        Triple("smoothdown", "Smooth Down (Xfade)", "position"),
        Triple("circleopen", "Circle Open (Xfade)", "shape"),
        Triple("circleclose", "Circle Close (Xfade)", "shape"),
        Triple("vertopen", "Vertical Open", "shape"),
        Triple("vertclose", "Vertical Close", "shape"),
        Triple("horzopen", "Horizontal Open", "shape"),
        Triple("horzclose", "Horizontal Close", "shape"),
        Triple("dissolve", "Dissolve (Xfade)", "fade"),
        Triple("pixelize", "Pixelize", "cinematic"),
        Triple("diagtl", "Diagonal Top Left (Xfade)", "position"),
        Triple("diagtr", "Diagonal Top Right (Xfade)", "position"),
        Triple("diagbl", "Diagonal Bottom Left (Xfade)", "position"),
        Triple("diagbr", "Diagonal Bottom Right (Xfade)", "position"),
        Triple("hblur", "Horizontal Blur", "cinematic"),
        Triple("wipetl", "Wipe Top Left", "position"),
        Triple("wipetr", "Wipe Top Right", "position"),
        Triple("wipebl", "Wipe Bottom Left", "position"),
        Triple("wipebr", "Wipe Bottom Right", "position"),
        Triple("fadegrays", "Fade Through Gray", "fade"),
        Triple("squeezeh", "Horizontal Squeeze", "cinematic"),
        Triple("squeezev", "Vertical Squeeze", "cinematic"),
        Triple("zoomin", "Zoom In (Xfade)", "zoom"),
        Triple("coverleft", "Cover Left", "reveal"),
        Triple("coverright", "Cover Right", "reveal"),
        Triple("coverup", "Cover Up", "reveal"),
        Triple("coverdown", "Cover Down", "reveal"),
        Triple("revealleft", "Reveal Left", "reveal"),
        Triple("revealright", "Reveal Right", "reveal"),
        Triple("revealup", "Reveal Up", "reveal"),
        Triple("revealdown", "Reveal Down", "reveal"),
        Triple("smoothleft", "Silky Left", "position"),
        Triple("smoothright", "Silky Right", "position"),
        Triple("smoothup", "Silky Up", "position"),
        Triple("smoothdown", "Silky Down", "position")
    )

    private val styledTransitions = listOf(
        Triple("cube-left", "Cube Left", "3d"),
        Triple("cube-right", "Cube Right", "3d"),
        Triple("cube-up", "Cube Up", "3d"),
        Triple("cube-down", "Cube Down", "3d"),
        Triple("flip-x", "Flip X", "3d"),
        Triple("flip-y", "Flip Y", "3d"),
        Triple("card-flip-left", "Card Flip Left", "3d"),
        Triple("card-flip-right", "Card Flip Right", "3d"),
        Triple("card-flip-up", "Card Flip Up", "3d"),
        Triple("card-flip-down", "Card Flip Down", "3d"),
        Triple("tilt-left-3d", "3D Tilt Left", "3d"),
        Triple("tilt-right-3d", "3D Tilt Right", "3d"),
        Triple("hinge-left", "Hinge Left", "3d"),
        Triple("hinge-right", "Hinge Right", "3d"),
        Triple("orbit-left", "Orbit Left", "3d"),
        Triple("orbit-right", "Orbit Right", "3d"),

        Triple("shake-x", "Shake X", "shake"),
        Triple("shake-y", "Shake Y", "shake"),
        Triple("camera-shake", "Camera Shake", "shake"),
        Triple("impact-shake", "Impact Shake", "shake"),
        Triple("micro-jitter", "Micro Jitter", "shake"),
        Triple("heavy-jitter", "Heavy Jitter", "shake"),
        Triple("roll-shake", "Roll Shake", "shake"),
        Triple("pulse-shake", "Pulse Shake", "shake"),
        Triple("whip-left", "Whip Left", "shake"),
        Triple("whip-right", "Whip Right", "shake"),
        Triple("whip-up", "Whip Up", "shake"),
        Triple("whip-down", "Whip Down", "shake"),
        Triple("snap-left", "Snap Left", "shake"),
        Triple("snap-right", "Snap Right", "shake"),
        Triple("bounce-shake", "Bounce Shake", "shake"),
        Triple("vibrate", "Vibrate", "shake"),

        Triple("blur-in", "Blur In", "blur"),
        Triple("blur-out", "Blur Out", "blur"),
        Triple("soft-blur", "Soft Blur", "blur"),
        Triple("strong-blur", "Strong Blur", "blur"),
        Triple("radial-blur", "Radial Blur", "blur"),
        Triple("zoom-blur", "Zoom Blur", "blur"),
        Triple("motion-blur-left", "Motion Blur Left", "blur"),
        Triple("motion-blur-right", "Motion Blur Right", "blur"),
        Triple("motion-blur-up", "Motion Blur Up", "blur"),
        Triple("motion-blur-down", "Motion Blur Down", "blur"),
        Triple("dream-blur", "Dream Blur", "blur"),
        Triple("focus-pull", "Focus Pull", "blur"),
        Triple("spin-blur", "Spin Blur", "blur"),
        Triple("bloom-blur", "Bloom Blur", "blur"),
        Triple("soft-focus", "Soft Focus", "blur"),
        Triple("defocus", "Defocus", "blur"),

        Triple("leak-amber", "Amber Leak", "lightleak"),
        Triple("leak-blue", "Blue Leak", "lightleak"),
        Triple("leak-pink", "Pink Leak", "lightleak"),
        Triple("leak-violet", "Violet Leak", "lightleak"),
        Triple("leak-orange", "Orange Leak", "lightleak"),
        Triple("leak-teal", "Teal Leak", "lightleak"),
        Triple("leak-rainbow", "Rainbow Leak", "lightleak"),
        Triple("flare-sweep-left", "Flare Sweep Left", "lightleak"),
        Triple("flare-sweep-right", "Flare Sweep Right", "lightleak"),
        Triple("film-burn", "Film Burn", "lightleak"),
        Triple("light-flash", "Light Flash", "lightleak"),
        Triple("prism-flare", "Prism Flare", "lightleak"),
        Triple("sun-flare", "Sun Flare", "lightleak"),
        Triple("golden-hour", "Golden Hour", "lightleak"),
        Triple("neon-leak", "Neon Leak", "lightleak"),
        Triple("soft-leak", "Soft Leak", "lightleak")
    )

    val PRESETS: List<TransitionPreset> = listOf(
        // ─── 1. FADE & OPACITY (Opacity) ───
        TransitionPreset("none", "None", "∅", "fade", ""),
        TransitionPreset("fade", "Fade", "🌫️", "fade", "fade"),
        TransitionPreset("fadeBlack", "Fade Black", "⬛", "fade", "fadeblack"),
        TransitionPreset("fadeWhite", "Fade White", "⬜", "fade", "fadewhite"),
        TransitionPreset("dissolve", "Dissolve", "✨", "fade", "dissolve"),
        TransitionPreset("blur", "Blur", "💧", "fade", "hblur"),
        TransitionPreset("blinkFade", "Blink Fade", "😉", "fade", "fadeblack"),
        TransitionPreset("softGlaze", "Soft Glaze", "🌫️", "fade", "dissolve"),
        TransitionPreset("dreamyBloom", "Dreamy Bloom", "🌸", "fade", "fadegrays"),
        TransitionPreset("glowDissolve", "Glow Dissolve", "🌟", "fade", "dissolve"),
        TransitionPreset("whiteFlash", "White Flash", "⚪", "fade", "fadewhite"),

        // ─── 2. POSITION & SLIDE (Motion) ───
        TransitionPreset("slideLeft", "Slide Left", "⬅️", "position", "slideleft"),
        TransitionPreset("slideRight", "Slide Right", "➡️", "position", "slideright"),
        TransitionPreset("slideUp", "Slide Up", "⬆️", "position", "slideup"),
        TransitionPreset("slideDown", "Slide Down", "⬇️", "position", "slidedown"),

        TransitionPreset("smoothLeft", "Smooth Left", "◀️", "position", "smoothleft"),
        TransitionPreset("smoothRight", "Smooth Right", "▶️", "position", "smoothright"),
        TransitionPreset("smoothUp", "Smooth Up", "🔼", "position", "smoothup"),
        TransitionPreset("smoothDown", "Smooth Down", "🔽", "position", "smoothdown"),

        TransitionPreset("wipeLeft", "Wipe Left", "🧹", "position", "wipeleft"),
        TransitionPreset("wipeRight", "Wipe Right", "🧹", "position", "wiperight"),
        TransitionPreset("wipeUp", "Wipe Up", "🧹", "position", "wipeup"),
        TransitionPreset("wipeDown", "Wipe Down", "🧹", "position", "wipedown"),

        TransitionPreset("diagTL", "Diagonal TL", "↖️", "position", "diagtl"),
        TransitionPreset("diagTR", "Diagonal TR", "↗️", "position", "diagtr"),
        TransitionPreset("diagBL", "Diagonal BL", "↙️", "position", "diagbl"),
        TransitionPreset("diagBR", "Diagonal BR", "↘️", "position", "diagbr"),

        // ─── 3. ZOOMS (Scale) ───
        TransitionPreset("zoomIn", "Zoom In", "🔍", "zoom", "zoomin"),
        TransitionPreset("circleOpen", "Circle Open", "⭕", "zoom", "circleopen"),
        TransitionPreset("circleClose", "Circle Close", "🛑", "zoom", "circleclose")
    ) + xfadeTransitions.mapIndexed { index, (filter, label, category) ->
        TransitionPreset(
            key = "xfade-$filter-$index",
            label = label,
            icon = "▶",
            category = category,
            ffmpegXfade = filter
        )
    } + styledTransitions.mapIndexed { index, (key, label, category) ->
        val exportFilter = when (category) {
            "3d" -> if (index % 2 == 0) "zoomin" else "smoothleft"
            "shake" -> if (index % 2 == 0) "pixelize" else "slideleft"
            "blur" -> if (key.contains("in") || key.contains("pull")) "hblur" else "dissolve"
            else -> if (key.contains("flash") || key.contains("flare")) "fadewhite" else "fadeblack"
        }
        TransitionPreset(
            key = "style-$key",
            label = label,
            icon = when (category) {
                "3d" -> "🧊"
                "shake" -> "📳"
                "blur" -> "💨"
                else -> "🌅"
            },
            category = category,
            ffmpegXfade = exportFilter
        )
    }

    fun find(key: String): TransitionPreset? =
        PRESETS.firstOrNull { it.key == key }

    fun presetsInCategory(category: String): List<TransitionPreset> =
        PRESETS.filter { it.category == category }

    fun lightLeakStyle(key: String): TransitionLightLeakStyle? {
        val preset = find(key)?.takeIf { it.category == "lightleak" } ?: return null
        val presetKey = preset.key.lowercase()
        val colorHex = when {
            "blue" in presetKey || "teal" in presetKey -> "38D9FF"
            "pink" in presetKey || "neon" in presetKey -> "FF3F9B"
            "violet" in presetKey || "prism" in presetKey -> "B36BFF"
            "orange" in presetKey || "amber" in presetKey ||
                    "golden" in presetKey || "sun" in presetKey ||
                    "burn" in presetKey -> "FFA23A"
            else -> "FFE76A"
        }
        return TransitionLightLeakStyle(
            colorHex = colorHex,
            movesRight = "right" !in presetKey
        )
    }
}
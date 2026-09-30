package com.moody.moodyvideoeditor.data

/**
 * 100+ transitions categorized, mapped to FFmpeg xfade filters.
 * Transition stored on the RIGHT clip of a pair.
 */
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

object TransitionLibrary {

    val CATEGORIES = listOf(
        "basic" to "⚡ Basic & Camera",
        "glitch" to "✨ Glitch & Digital",
        "action" to "💥 Action & Shake",
        "light" to "💫 Light & Blur",
        "matte" to "🎭 Matte & Shapes",
        "artistic" to "🎨 Artistic"
    )

    val PRESETS: List<TransitionPreset> = listOf(
        // ═══════════ BASIC & CAMERA (20) ═══════════
        TransitionPreset("none", "None", "∅", "basic", ""),
        TransitionPreset("zoomIn", "Zoom In", "🔍", "basic", "zoomin"),
        TransitionPreset("zoomOut", "Zoom Out", "🔎", "basic", "fade"),
        TransitionPreset("panLeft", "Pan Left", "⬅️", "basic", "slideleft"),
        TransitionPreset("panRight", "Pan Right", "➡️", "basic", "slideright"),
        TransitionPreset("tiltUp", "Tilt Up", "⬆️", "basic", "slideup"),
        TransitionPreset("tiltDown", "Tilt Down", "⬇️", "basic", "slidedown"),
        TransitionPreset("spinIn", "Spin In", "🌀", "basic", "circleopen"),
        TransitionPreset("spinOut", "Spin Out", "🔄", "basic", "circleclose"),
        TransitionPreset("whipPan", "Whip Pan", "💨", "basic", "hlslice"),
        TransitionPreset("pullInShake", "Pull In Shake", "🎯", "basic", "fade"),
        TransitionPreset("pushOutBounce", "Push Out Bounce", "🏀", "basic", "fade"),
        TransitionPreset("slideRight", "Right Slide", "▶️", "basic", "slideright"),
        TransitionPreset("slideLeft", "Left Slide", "◀️", "basic", "slideleft"),
        TransitionPreset("riseUp", "Up Rise", "🔼", "basic", "smoothup"),
        TransitionPreset("dropDown", "Down Drop", "🔽", "basic", "smoothdown"),
        TransitionPreset("twirlCW", "Clockwise Twirl", "↻", "basic", "circleopen"),
        TransitionPreset("twirlCCW", "Counter Twirl", "↺", "basic", "circleclose"),
        TransitionPreset("cameraRoll", "Camera Roll", "🎥", "basic", "fade"),
        TransitionPreset("diagonalSwift", "Diagonal Swift", "↗️", "basic", "diagtl"),

        // ═══════════ GLITCH & DIGITAL (15) ═══════════
        TransitionPreset("rgbShift", "RGB Shift", "🌈", "glitch", "hblur"),
        TransitionPreset("pixelateBurst", "Pixelate Burst", "🟦", "glitch", "pixelize"),
        TransitionPreset("horizontalScan", "Horizontal Scan", "📺", "glitch", "hlslice"),
        TransitionPreset("dataMoshing", "Data Moshing", "💾", "glitch", "dissolve"),
        TransitionPreset("signalLoss", "Signal Loss", "📡", "glitch", "fadegrays"),
        TransitionPreset("chromaticFlash", "Chromatic Flash", "⚡", "glitch", "fadewhite"),
        TransitionPreset("vcrDistortion", "VCR Distortion", "📼", "glitch", "hblur"),
        TransitionPreset("digitalWave", "Digital Wave", "〰️", "glitch", "vdslice"),
        TransitionPreset("matrixCode", "Matrix Code", "🟢", "glitch", "vertopen"),
        TransitionPreset("glitchBlur", "Glitch Blur", "💠", "glitch", "hblur"),
        TransitionPreset("anaglyphSlide", "Anaglyph Slide", "👓", "glitch", "slideleft"),
        TransitionPreset("bitCrushed", "Bit Crushed", "🎚️", "glitch", "pixelize"),
        TransitionPreset("interlacedCut", "Interlaced Cut", "🪟", "glitch", "hrslice"),
        TransitionPreset("noiseIntercept", "Noise Intercept", "📻", "glitch", "fadegrays"),
        TransitionPreset("staticMelt", "Static Melt", "🫠", "glitch", "smoothdown"),

        // ═══════════ ACTION & SHAKE (15) ═══════════
        TransitionPreset("velocityShake", "Velocity Shake", "⚡", "action", "fade"),
        TransitionPreset("verticalBounce", "Vertical Bounce", "🏀", "action", "slideup"),
        TransitionPreset("horizontalJiggle", "Horizontal Jiggle", "↔️", "action", "slideleft"),
        TransitionPreset("zoomImpact", "Zoom Impact", "💥", "action", "zoomin"),
        TransitionPreset("turbulentSwivel", "Turbulent Swivel", "🌀", "action", "radial"),
        TransitionPreset("flashJolt", "Flash Jolt", "⚡", "action", "fadewhite"),
        TransitionPreset("bassWave", "Bass Wave", "🔊", "action", "radial"),
        TransitionPreset("rumbleDissolve", "Rumble Dissolve", "🌋", "action", "dissolve"),
        TransitionPreset("chaosDrift", "Chaos Drift", "🎲", "action", "fade"),
        TransitionPreset("glitchShake", "Glitch Shake", "📳", "action", "hblur"),
        TransitionPreset("tremorCut", "Tremor Cut", "📳", "action", "fade"),
        TransitionPreset("impactWarp", "Impact Warp", "🐟", "action", "zoomin"),
        TransitionPreset("snapBack", "Snap Back", "🎯", "action", "zoomin"),
        TransitionPreset("wobbleSlide", "Wobble Slide", "🪼", "action", "smoothleft"),
        TransitionPreset("epicStrike", "Epic Strike", "⚔️", "action", "smoothdown"),

        // ═══════════ LIGHT & BLUR (15) ═══════════
        TransitionPreset("whiteFlash", "White Flash", "⚪", "light", "fadewhite"),
        TransitionPreset("lightLeakBurst", "Light Leak", "🌅", "light", "fadewhite"),
        TransitionPreset("glowDissolve", "Glow Dissolve", "🌟", "light", "dissolve"),
        TransitionPreset("gaussianZoom", "Gaussian Zoom", "💧", "light", "zoomin"),
        TransitionPreset("radialZoomBlur", "Radial Zoom Blur", "🎯", "light", "radial"),
        TransitionPreset("motionWipeBlur", "Motion Wipe", "💨", "light", "hblur"),
        TransitionPreset("dreamyBloom", "Dreamy Bloom", "🌸", "light", "fadegrays"),
        TransitionPreset("neonFlare", "Neon Flare", "💡", "light", "fadewhite"),
        TransitionPreset("sunbeamSweep", "Sunbeam Sweep", "☀️", "light", "slideright"),
        TransitionPreset("lensFlareCut", "Lens Flare", "🔆", "light", "fadewhite"),
        TransitionPreset("blinkFade", "Blink Fade", "😉", "light", "fadeblack"),
        TransitionPreset("softGlaze", "Soft Glaze", "🌫️", "light", "dissolve"),
        TransitionPreset("prismBlur", "Prism Blur", "🔮", "light", "hblur"),
        TransitionPreset("haloPulse", "Halo Pulse", "💫", "light", "circleopen"),
        TransitionPreset("vignetteBurn", "Vignette Burn", "🔥", "light", "fadewhite"),

        // ═══════════ MATTE & SHAPES (20) ═══════════
        TransitionPreset("circleMask", "Circle Mask", "⭕", "matte", "circleopen"),
        TransitionPreset("linearWipe", "Linear Wipe", "▬", "matte", "wipeleft"),
        TransitionPreset("mirrorSplit", "Mirror Split", "🪞", "matte", "horzopen"),
        TransitionPreset("diamondReveal", "Diamond Reveal", "💎", "matte", "diagtl"),
        TransitionPreset("heartPop", "Heart Pop", "❤️", "matte", "circleopen"),
        TransitionPreset("gridDissolve", "Grid Dissolve", "▦", "matte", "pixelize"),
        TransitionPreset("clockWipe", "Clock Wipe", "🕐", "matte", "radial"),
        TransitionPreset("starBurst", "Star Burst", "⭐", "matte", "circleopen"),
        TransitionPreset("diagonalSlice", "Diagonal Slice", "◤", "matte", "diagtr"),
        TransitionPreset("crossHatch", "Cross Hatch", "✖️", "matte", "wipetl"),
        TransitionPreset("hexagonMatrix", "Hexagon Matrix", "⬡", "matte", "pixelize"),
        TransitionPreset("venetianBlinds", "Venetian Blinds", "🪟", "matte", "vuslice"),
        TransitionPreset("boxZoom", "Box Zoom", "⬛", "matte", "rectcrop"),
        TransitionPreset("spiralWipe", "Spiral Wipe", "🌀", "matte", "circleopen"),
        TransitionPreset("mosaicSwitch", "Mosaic Switch", "🧩", "matte", "pixelize"),
        TransitionPreset("slideDoor", "Slide Door", "🚪", "matte", "coverleft"),
        TransitionPreset("triangleSweep", "Triangle Sweep", "🔺", "matte", "diagtr"),
        TransitionPreset("liquidBlob", "Liquid Blob", "💧", "matte", "circleopen"),
        TransitionPreset("zigzagWipe", "Zigzag Wipe", "〰️", "matte", "hrslice"),
        TransitionPreset("jigsawMask", "Jigsaw Mask", "🧩", "matte", "hblur"),

        // ═══════════ ARTISTIC (15) ═══════════
        TransitionPreset("inkSplash", "Ink Splash", "🖋️", "artistic", "circleopen"),
        TransitionPreset("filmBurn", "Film Burn", "🎞️", "artistic", "fadewhite"),
        TransitionPreset("paperTear", "Paper Tear", "📄", "artistic", "vdslice"),
        TransitionPreset("watercolorBleed", "Watercolor Bleed", "🎨", "artistic", "dissolve"),
        TransitionPreset("comicFlip", "Comic Book Flip", "📖", "artistic", "horzopen"),
        TransitionPreset("smokeScreen", "Smoke Screen", "💨", "artistic", "fadegrays"),
        TransitionPreset("halftoneDissolve", "Halftone Dissolve", "🔵", "artistic", "pixelize"),
        TransitionPreset("glitchPaint", "Glitch Paint", "🎨", "artistic", "pixelize"),
        TransitionPreset("pageTurn", "Page Turn", "📃", "artistic", "coverright"),
        TransitionPreset("burningPaper", "Burning Paper", "🔥", "artistic", "diagbr"),
        TransitionPreset("chalkSketch", "Chalk Sketch", "🖍️", "artistic", "fadewhite"),
        TransitionPreset("glassShatter", "Glass Shatter", "💥", "artistic", "pixelize"),
        TransitionPreset("oilPainting", "Oil Painting Blend", "🖼️", "artistic", "dissolve"),
        TransitionPreset("vintageSlide", "Vintage Slide Show", "📽️", "artistic", "fadeblack"),
        TransitionPreset("vectorShift", "Vector Shift", "▲", "artistic", "smoothleft")
    )

    fun find(key: String): TransitionPreset? =
        PRESETS.firstOrNull { it.key == key }

    fun presetsInCategory(category: String): List<TransitionPreset> =
        PRESETS.filter { it.category == category }
}
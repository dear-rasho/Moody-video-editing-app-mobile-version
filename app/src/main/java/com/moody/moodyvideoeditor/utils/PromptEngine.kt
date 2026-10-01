package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.data.EffectLibrary

enum class CmdType {
    ADJUSTMENT, FILTER, EFFECT, SPEED, TRANSFORM, TRIM,
    TRANSITION, TRANSITION_ALL, TRANSITION_AT, TRANSITION_LAYER,
    TRANSITION_LAYER_CLIPS, TRANSITION_CLIP_MAP,
    TEXT, STICKER, CHROMA, AUDIO_FX, ANIMATION,
    COLOR_WHEEL, FONT, ALIGN, ANCHOR, KEYFRAME,
    RATIO, TIGHTEN, GRAPH, CLEAR_KEYFRAMES,
    TEMPLATE,
    BRUSH_GRADIENT,
    BRUSH_TYPE,
    BRUSH_DRAW,
    BRUSH_CLEAR,
    UNKNOWN
}

data class ParsedCommand(
    val type: CmdType,
    val key: String = "",
    val value1: Float? = null,
    val value2: Float? = null,
    val stringValue: String? = null,
    val extra: String? = null,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val raw: String = ""
)

data class ParseResult(
    val commands: List<ParsedCommand>,
    val unknown: List<String>
)

object PromptEngine {

    private fun colorNameToHex(name: String): String {
        return when (name.lowercase().trim()) {
            "red" -> "ff0000"
            "orange" -> "ff6b00"
            "yellow" -> "ffcc00"
            "green" -> "00ff00"
            "cyan" -> "00e5ff"
            "blue" -> "0066ff"
            "purple" -> "7c3aed"
            "magenta", "pink" -> "ff00ff"
            "white" -> "ffffff"
            "black" -> "000000"
            else -> "ff0000"
        }
    }

    private val ADJUSTMENT_KEYS = setOf(
        "brightness", "contrast", "exposure", "whites", "blacks",
        "shadows", "highlights", "saturation", "vibrance", "clarity",
        "temperature", "tint", "noise", "sharpen", "vignette",
        "reds", "oranges", "yellows", "greens", "cyans",
        "blues", "purples", "magentas", "skintones", "skin_tones",
        "lal", "hara", "neela", "peela"
    )

    private val FILTER_KEYS = setOf(
        "grayscale", "sepia", "invert", "blur", "hue"
    )

    private val TRANSFORM_KEYS = setOf(
        "scale", "rotation", "positionx", "positiony",
        "cropl", "cropr", "cropt", "cropb"
    )

    private val ANIMATIONS = setOf(
        "none", "typewriter", "decoder", "fadein", "fadeup", "fadedown",
        "slideleft", "slideright", "slideup", "slidedown", "popin", "bouncein",
        "flicker", "cinematicblur", "flip3dx", "flip3dy", "rotate3d",
        "scribble", "glitch", "wave", "bouncewave", "pulse", "shake",
        "zoomin", "zoomout", "vortexspin", "spiralin", "tornado"
    )

    private val EFFECT_NAMES = EffectLibrary.ALL.map { it.key }.toSet()

    // ═══════════════════════════════════════════════════════════
    //  MAIN PARSE
    // ═══════════════════════════════════════════════════════════
    fun parse(input: String): ParseResult {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ParseResult(emptyList(), emptyList())

        val hasTimestamps = Regex("""\[\s*\d{1,2}:\d{2}\s*-\s*\d{1,2}:\d{2}\s*\]""")
            .containsMatchIn(trimmed)

        android.util.Log.d("PROMPT_PARSE", "Input: $trimmed")
        android.util.Log.d("PROMPT_PARSE", "hasTimestamps: $hasTimestamps")

        val result = if (hasTimestamps) parseTimestamped(trimmed)
        else parseLinear(trimmed)

        android.util.Log.d(
            "PROMPT_PARSE",
            "commands=${result.commands.size}, unknown=${result.unknown.size}"
        )
        result.commands.forEach { cmd ->
            android.util.Log.d(
                "PROMPT_PARSE",
                "  ${cmd.type}: key=${cmd.key}, v1=${cmd.value1}, sv=${cmd.stringValue}"
            )
        }
        result.unknown.forEach { u ->
            android.util.Log.d("PROMPT_PARSE", "  UNKNOWN: $u")
        }

        return result
    }

    // ═══════════════════════════════════════════════════════════
    //  TIMESTAMPED PARSE
    // ═══════════════════════════════════════════════════════════
    private fun parseTimestamped(input: String): ParseResult {
        val commands = mutableListOf<ParsedCommand>()
        val unknown = mutableListOf<String>()

        val blockRegex = Regex(
            """\[\s*(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})\s*\]\s*([^\[\n]*)""",
            RegexOption.MULTILINE
        )

        val matches = blockRegex.findAll(input).toList()
        if (matches.isEmpty()) {
            return parseLinear(input)
        }

        for (m in matches) {
            val sMin = m.groupValues[1].toIntOrNull() ?: 0
            val sSec = m.groupValues[2].toIntOrNull() ?: 0
            val eMin = m.groupValues[3].toIntOrNull() ?: 0
            val eSec = m.groupValues[4].toIntOrNull() ?: 0
            val body = m.groupValues[5].trim()

            val startMs = (sMin * 60L + sSec) * 1000L
            val endMs = (eMin * 60L + eSec) * 1000L

            if (body.isBlank()) continue

            val bodyParts = body.split(",").map { it.trim() }.filter { it.isNotBlank() }

            for (bp in bodyParts) {
                val parsed = parseOne(bp)
                if (parsed != null) {
                    commands.add(parsed.copy(startMs = startMs, endMs = endMs))
                } else {
                    unknown.add("[${sMin}:${sSec}-${eMin}:${eSec}] $bp")
                }
            }
        }

        return ParseResult(commands, unknown)
    }

    // ═══════════════════════════════════════════════════════════
    //  🆕 LINEAR PARSE — handles L1 transitions, C1 slide, ...
    // ═══════════════════════════════════════════════════════════
    private fun parseLinear(input: String): ParseResult {
        val commands = mutableListOf<ParsedCommand>()
        val unknown = mutableListOf<String>()

        val rawParts = input
            .replace("\n", ",")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        android.util.Log.d("PROMPT_PARSE", "rawParts: $rawParts")

        var i = 0
        while (i < rawParts.size) {
            val part = rawParts[i]

            // 🆕 Detect "L1 transitions" / "L2 transitions" / "L1 transition"
            val layerMatch = Regex(
                """^l(\d+)\s+transitions?$""",
                RegexOption.IGNORE_CASE
            ).find(part)

            if (layerMatch != null) {
                val layerNum = layerMatch.groupValues[1].toIntOrNull() ?: 0
                val clipPairs = mutableListOf<String>()
                i++

                // Consume following "C1 slide" / "C5 skip" / "C2 push left" entries
                while (i < rawParts.size) {
                    val cMatch = Regex(
                        """^c(\d+)(?:\s+(.+))?$""",
                        RegexOption.IGNORE_CASE
                    ).find(rawParts[i])
                    if (cMatch == null) break

                    val clipNum = cMatch.groupValues[1]
                    val transName = cMatch.groupValues[2].trim()
                        .ifBlank { "skip" }  // 🆕 bare "C1" = skip
                    clipPairs.add("$clipNum=$transName")
                    i++
                }

                if (layerNum > 0 && clipPairs.isNotEmpty()) {
                    commands.add(
                        ParsedCommand(
                            CmdType.TRANSITION_LAYER_CLIPS,
                            "layer",
                            value1 = layerNum.toFloat(),
                            stringValue = clipPairs.joinToString("|"),
                            raw = "L$layerNum transitions"
                        )
                    )
                    android.util.Log.d(
                        "PROMPT_PARSE",
                        "✅ L$layerNum transitions: $clipPairs"
                    )
                } else {
                    unknown.add(part)
                }
                continue
            }

            val parsed = parseOne(part)
            if (parsed != null) commands.add(parsed)
            else unknown.add(part)
            i++
        }

        return ParseResult(commands, unknown)
    }

    // ═══════════════════════════════════════════════════════════
    //  PARSE ONE
    // ═══════════════════════════════════════════════════════════
    private fun parseOne(text: String): ParsedCommand? {
        val lower = text.lowercase().trim()
        if (lower.isBlank()) return null

        // SPECIAL
        if (lower == "tighten track" || lower == "tighten")
            return ParsedCommand(CmdType.TIGHTEN, "tighten", raw = text)
        if (lower == "graph on")
            return ParsedCommand(CmdType.GRAPH, "on", raw = text)
        if (lower == "graph off")
            return ParsedCommand(CmdType.GRAPH, "off", raw = text)
        if (lower == "clear keyframes")
            return ParsedCommand(CmdType.CLEAR_KEYFRAMES, "clear", raw = text)

        // RATIO
        Regex("""^ratio\s+(\d+):(\d+)$""").find(lower)?.let { m ->
            return ParsedCommand(
                CmdType.RATIO,
                "${m.groupValues[1]}:${m.groupValues[2]}",
                raw = text
            )
        }

        // TEMPLATE
        if (lower.startsWith("template ")) {
            val tId = lower.substring(9).trim()
            if (tId.isNotBlank()) {
                return ParsedCommand(CmdType.TEMPLATE, tId, raw = text)
            }
        }

        // TRANSITION ALL
        Regex("""^transition\s+all\s+([a-z\s]+?)(?:\s+([\d.]+))?$""", RegexOption.IGNORE_CASE)
            .find(lower)?.let { m ->
                val key = m.groupValues[1].trim().replace(" ", "")
                val dur = m.groupValues[2].toFloatOrNull() ?: 0.5f
                return ParsedCommand(CmdType.TRANSITION_ALL, key, value1 = dur, raw = text)
            }

        // TRANSITION AT
        Regex(
            """^transition\s+at\s+([\d.]+)\s+([a-z\s]+?)(?:\s+([\d.]+))?$""",
            RegexOption.IGNORE_CASE
        ).find(lower)?.let { m ->
            val time = m.groupValues[1].toFloatOrNull() ?: 0f
            val key = m.groupValues[2].trim().replace(" ", "")
            val dur = m.groupValues[3].toFloatOrNull() ?: 0.5f
            return ParsedCommand(
                CmdType.TRANSITION_AT,
                key,
                value1 = time,
                value2 = dur,
                raw = text
            )
        }

        // TRANSITION LAYER (pattern)
        Regex("""^layer\s+(v|a)(\d+)\s+transitions\s+(.+)$""", RegexOption.IGNORE_CASE)
            .find(text)?.let { m ->
                val isAudio = m.groupValues[1].lowercase() == "a"
                val trackIdx = m.groupValues[2].toIntOrNull() ?: 0
                val pattern = m.groupValues[3].trim()
                return ParsedCommand(
                    CmdType.TRANSITION_LAYER,
                    if (isAudio) "audio" else "visual",
                    value1 = trackIdx.toFloat(),
                    stringValue = pattern,
                    raw = text
                )
            }

        // TRANSITION SINGLE
        Regex(
            """^(fade|dissolve|fadeblack|fadewhite|slide\s+left|slide\s+right|slide\s+up|slide\s+down|zoom\s+in|zoom\s+out|wipe\s+left|wipe\s+right|circleIn|blur)(?:\s+in)?(?:\s+([\d.]+))?$""",
            RegexOption.IGNORE_CASE
        ).find(lower)?.let { m ->
            val key = m.groupValues[1].replace(" ", "")
            val dur = m.groupValues[2].toFloatOrNull() ?: 0.5f
            return ParsedCommand(CmdType.TRANSITION, key, value1 = dur, raw = text)
        }

        // TEXT
        val textFull =
            Regex("""^text\s+"([^"]+)"(?:\s+(.+))?$""", RegexOption.IGNORE_CASE).find(text)
        if (textFull != null) {
            return ParsedCommand(
                CmdType.TEXT, "content",
                stringValue = textFull.groupValues[1],
                extra = textFull.groupValues[2].ifBlank { null },
                raw = text
            )
        }

        // STICKER with optional properties
        // Format: sticker 😀 animation popIn duration 0.8
        val stickerFull = Regex(
            """^sticker\s+(\S+)(?:\s+(.+))?$""",
            RegexOption.IGNORE_CASE
        ).find(text)
        if (stickerFull != null) {
            val emoji = stickerFull.groupValues[1]
            val extra = stickerFull.groupValues[2].ifBlank { null }
            return ParsedCommand(
                CmdType.STICKER, "emoji",
                stringValue = emoji,
                extra = extra,
                raw = text
            )
        }
        // BRUSH COMMANDS
        Regex(
            """^brush\s+gradient\s+(?:#([0-9a-fA-F]{6})|([a-z]+))\s+to\s+(?:#([0-9a-fA-F]{6})|([a-z]+))$""",
            RegexOption.IGNORE_CASE
        ).find(text)?.let { m ->
            val c1 = m.groupValues[1].ifBlank { colorNameToHex(m.groupValues[2]) }
            val c2 = m.groupValues[3].ifBlank { colorNameToHex(m.groupValues[4]) }
            return ParsedCommand(
                CmdType.BRUSH_GRADIENT,
                "linear",
                stringValue = "#$c1,#$c2",
                raw = text
            )
        }

        if (lower == "brush solid" || lower == "brush no gradient") {
            return ParsedCommand(CmdType.BRUSH_GRADIENT, "off", raw = text)
        }

        Regex(
            """^brush\s+(pen|marker|chalk|neon|glow|spray)(?:\s+color\s+(?:#([0-9a-fA-F]{6})|([a-z]+)))?(?:\s+width\s+([\d.]+))?$""",
            RegexOption.IGNORE_CASE
        ).find(text)?.let { m ->
            val type = m.groupValues[1].lowercase()
            val colorHex = m.groupValues[2].ifBlank { colorNameToHex(m.groupValues[3]) }
            val width = m.groupValues[4].toFloatOrNull()
            return ParsedCommand(
                CmdType.BRUSH_TYPE,
                type,
                value1 = width,
                stringValue = if (colorHex.isNotBlank()) "#$colorHex" else null,
                raw = text
            )
        }

        if (lower == "brush draw" || lower == "start drawing" || lower == "draw") {
            return ParsedCommand(CmdType.BRUSH_DRAW, "on", raw = text)
        }

        if (lower == "brush stop" || lower == "stop drawing") {
            return ParsedCommand(CmdType.BRUSH_DRAW, "off", raw = text)
        }

        if (lower == "brush clear" || lower == "clear brush") {
            return ParsedCommand(CmdType.BRUSH_CLEAR, "clear", raw = text)
        }

        // ANIMATION
        if (lower.startsWith("animation ")) {
            val anim = lower.substring(10).trim().replace(" ", "")
            if (ANIMATIONS.contains(anim))
                return ParsedCommand(CmdType.ANIMATION, anim, raw = text)
        }

        // FONT
        Regex("""^font\s+(?:"([^"]+)"|([a-z]+))(?:\s+size\s+(\d+))?$""", RegexOption.IGNORE_CASE)
            .find(text)?.let { m ->
                val name = m.groupValues[1].ifBlank { m.groupValues[2] }
                val size = m.groupValues[3].toIntOrNull()?.toFloat()
                return ParsedCommand(CmdType.FONT, name, value1 = size, raw = text)
            }

        // ALIGN
        Regex("""^align\s+(left|center|right)$""").find(lower)?.let { m ->
            return ParsedCommand(CmdType.ALIGN, m.groupValues[1], raw = text)
        }

        // ANCHOR
        Regex("""^anchor\s+(top-left|top-center|top-right|center-left|center|center-right|bottom-left|bottom-center|bottom-right)$""")
            .find(lower)
            ?.let { return ParsedCommand(CmdType.ANCHOR, it.groupValues[1], raw = text) }

        // SPEED
        Regex("""^speed\s+([\d.]+)x?$""").find(lower)?.let { m ->
            return ParsedCommand(
                CmdType.SPEED,
                "speed",
                value1 = m.groupValues[1].toFloatOrNull(),
                raw = text
            )
        }

        // TRIM
        when (lower) {
            "trim left" -> return ParsedCommand(CmdType.TRIM, "left", raw = text)
            "trim right" -> return ParsedCommand(CmdType.TRIM, "right", raw = text)
            "split" -> return ParsedCommand(CmdType.TRIM, "split", raw = text)
            "green screen" -> return ParsedCommand(CmdType.CHROMA, "#00ff00", raw = text)
        }

        // AUDIO FX
        if (lower.startsWith("audio ")) {
            val fx = lower.substring(6).trim()
            if (fx.isNotBlank()) return ParsedCommand(CmdType.AUDIO_FX, fx, raw = text)
        }

        // COLOR WHEEL
        Regex("""^(shadows|midtones|highlights)\s+([a-z]+)\s+(\d+)\s+(\d+)$""").find(lower)
            ?.let { m ->
                return ParsedCommand(
                    CmdType.COLOR_WHEEL, m.groupValues[1],
                    value1 = m.groupValues[3].toFloatOrNull(),
                    value2 = m.groupValues[4].toFloatOrNull(),
                    stringValue = m.groupValues[2],
                    raw = text
                )
            }
        Regex("""^hdr\s+(\d+)$""").find(lower)?.let { m ->
            return ParsedCommand(
                CmdType.COLOR_WHEEL,
                "hdr",
                value1 = m.groupValues[1].toFloatOrNull(),
                raw = text
            )
        }

        // EFFECT
        if (EFFECT_NAMES.contains(lower))
            return ParsedCommand(CmdType.EFFECT, lower, raw = text)

        // KEY VALUE
        Regex("""^([a-z_]+)\s+(-?[\d.]+)$""").find(lower)?.let { m ->
            val key = m.groupValues[1]
            val value = m.groupValues[2].toFloatOrNull()
            if (value != null) {
                if (ADJUSTMENT_KEYS.contains(key)) {
                    val nk = when (key) {
                        "skin_tones" -> "skintones"
                        "lal" -> "reds"
                        "hara" -> "greens"
                        "neela" -> "blues"
                        "peela" -> "yellows"
                        else -> key
                    }
                    return ParsedCommand(CmdType.ADJUSTMENT, nk, value1 = value, raw = text)
                }
                if (FILTER_KEYS.contains(key))
                    return ParsedCommand(CmdType.FILTER, key, value1 = value, raw = text)
                if (TRANSFORM_KEYS.contains(key)) {
                    val nk = when (key) {
                        "cropl" -> "cropL"
                        "cropr" -> "cropR"
                        "cropt" -> "cropT"
                        "cropb" -> "cropB"
                        else -> key
                    }
                    return ParsedCommand(CmdType.TRANSFORM, nk, value1 = value, raw = text)
                }
            }
        }

        // FILTER (no value)
        if (FILTER_KEYS.contains(lower)) {
            val def = when (lower) {
                "grayscale", "invert" -> 100f
                "sepia" -> 80f
                "blur" -> 5f
                "hue" -> 90f
                else -> 50f
            }
            return ParsedCommand(CmdType.FILTER, lower, value1 = def, raw = text)
        }

        // CHROMA
        if (lower.startsWith("chroma "))
            return ParsedCommand(CmdType.CHROMA, text.substring(7).trim(), raw = text)

        return null
    }

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════
    fun withAdjustment(adj: AdjustmentData, key: String, v: Float): AdjustmentData = when (key) {
        "brightness" -> adj.copy(brightness = v)
        "contrast" -> adj.copy(contrast = v)
        "exposure" -> adj.copy(exposure = v)
        "whites" -> adj.copy(whites = v)
        "blacks" -> adj.copy(blacks = v)
        "shadows" -> adj.copy(shadows = v)
        "highlights" -> adj.copy(highlights = v)
        "saturation" -> adj.copy(saturation = v)
        "vibrance" -> adj.copy(vibrance = v)
        "clarity" -> adj.copy(clarity = v)
        "temperature" -> adj.copy(temperature = v)
        "tint" -> adj.copy(tint = v)
        "noise" -> adj.copy(noise = v)
        "sharpen" -> adj.copy(sharpen = v)
        "vignette" -> adj.copy(vignette = v)
        "reds" -> adj.copy(reds = v)
        "oranges" -> adj.copy(oranges = v)
        "yellows" -> adj.copy(yellows = v)
        "greens" -> adj.copy(greens = v)
        "cyans" -> adj.copy(cyans = v)
        "blues" -> adj.copy(blues = v)
        "purples" -> adj.copy(purples = v)
        "magentas" -> adj.copy(magentas = v)
        "skintones" -> adj.copy(skinTones = v)
        else -> adj
    }

    fun animationKey(input: String): String {
        return when (val n = input.lowercase().replace(" ", "")) {
            "fadein" -> "fadeIn"
            "fadeup" -> "fadeUp"
            "fadedown" -> "fadeDown"
            "slideleft" -> "slideLeft"
            "slideright" -> "slideRight"
            "slideup" -> "slideUp"
            "slidedown" -> "slideDown"
            "popin" -> "popIn"
            "bouncein" -> "bounceIn"
            "cinematicblur" -> "cinematicBlur"
            "flip3dx" -> "flip3DX"
            "flip3dy" -> "flip3DY"
            "rotate3d" -> "rotate3D"
            "bouncewave" -> "bounceWave"
            "zoomin" -> "zoomIn"
            "zoomout" -> "zoomOut"
            "vortexspin" -> "vortexSpin"
            "spiralin" -> "spiralIn"
            else -> n
        }
    }
}
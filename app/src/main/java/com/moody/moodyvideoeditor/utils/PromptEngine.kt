package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.data.EffectLibrary

enum class CmdType {
    ADJUSTMENT, FILTER, EFFECT, SPEED, TRANSFORM, TRIM,
    TRANSITION, TRANSITION_ALL, TRANSITION_AT, TRANSITION_LAYER,
    TEXT, STICKER, CHROMA, AUDIO_FX, ANIMATION,
    COLOR_WHEEL, FONT, ALIGN, ANCHOR, KEYFRAME,
    RATIO, TIGHTEN, GRAPH, CLEAR_KEYFRAMES,
    TEMPLATE,   // 🆕
    UNKNOWN
}

data class ParsedCommand(
    val type: CmdType,
    val key: String = "",
    val value1: Float? = null,
    val value2: Float? = null,
    val stringValue: String? = null,
    val extra: String? = null,
    val startMs: Long? = null,   // 🆕 timestamp start
    val endMs: Long? = null,     // 🆕 timestamp end
    val raw: String = ""
)

data class ParseResult(
    val commands: List<ParsedCommand>,
    val unknown: List<String>
)

object PromptEngine {

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

        // 🆕 Check for timestamped blocks
        val hasTimestamps = Regex("""\[\s*\d{1,2}:\d{2}\s*-\s*\d{1,2}:\d{2}\s*\]""")
            .containsMatchIn(trimmed)

        return if (hasTimestamps) parseTimestamped(trimmed)
        else parseLinear(trimmed)
    }

    // ═══════════════════════════════════════════════════════════
    //  TIMESTAMPED PARSE
    // ═══════════════════════════════════════════════════════════
    private fun parseTimestamped(input: String): ParseResult {
        val commands = mutableListOf<ParsedCommand>()
        val unknown = mutableListOf<String>()

        // Block pattern: [MM:SS - MM:SS] body  (until next [ or end)
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

            // Body may have multiple comma-separated commands
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
    //  LINEAR PARSE (no timestamps)
    // ═══════════════════════════════════════════════════════════
    private fun parseLinear(input: String): ParseResult {
        val commands = mutableListOf<ParsedCommand>()
        val unknown = mutableListOf<String>()

        val rawParts = input
            .replace("\n", ",")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        for (part in rawParts) {
            val parsed = parseOne(part)
            if (parsed != null) commands.add(parsed)
            else unknown.add(part)
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
        // 🆕 TEMPLATE: template motiv / template cinematic
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
        )
            .find(lower)?.let { m ->
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

        // TRANSITION LAYER
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

        // TEXT with properties: text "Hello" size 48 color #ff0066 animation typewriter
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

        // STICKER
        if (lower.startsWith("sticker ")) {
            val emoji = text.substring(8).trim()
            if (emoji.isNotBlank())
                return ParsedCommand(CmdType.STICKER, "emoji", stringValue = emoji, raw = text)
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
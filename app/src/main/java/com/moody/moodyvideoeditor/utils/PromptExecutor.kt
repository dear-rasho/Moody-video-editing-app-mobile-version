package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.ChromaState
import com.moody.moodyvideoeditor.data.RatioLibrary
import com.moody.moodyvideoeditor.data.TextState
import com.moody.moodyvideoeditor.data.ToneValue
import com.moody.moodyvideoeditor.data.TransitionLibrary
import com.moody.moodyvideoeditor.data.TransitionState
import com.moody.moodyvideoeditor.viewmodel.EditorViewModel
import kotlin.math.abs

data class ExecuteReport(
    val applied: List<String>,
    val unknown: List<String>,
    val errors: List<String>
) {
    val successCount: Int get() = applied.size
    val unknownCount: Int get() = unknown.size
    val errorCount: Int get() = errors.size

    val isFullSuccess: Boolean
        get() = unknown.isEmpty() && errors.isEmpty() && applied.isNotEmpty()

    val statusType: String
        get() = when {
            errorCount > 0 -> "error"
            unknownCount > 0 -> "warning"
            successCount > 0 -> "success"
            else -> "warning"
        }
}

object PromptExecutor {

    private const val TAG = "PROMPT_EXEC"

    fun execute(parsed: ParseResult, viewModel: EditorViewModel): ExecuteReport {
        val applied = mutableListOf<String>()
        val errors = mutableListOf<String>()

        val state = viewModel.state.value
        val selected = state.selectedClip
        // ═══════════════════════════════════════════════════════
        //  PRIORITY 0 — TEMPLATES (before everything else)
        // ═══════════════════════════════════════════════════════
        for (cmd in parsed.commands) {
            if (cmd.type == CmdType.TEMPLATE) {
                val tmpl = TypographyTemplates.find(cmd.key)
                if (tmpl != null) {
                    viewModel.applyTemplate(
                        templateId = cmd.key,
                        startMs = viewModel.state.value.currentPosMs,
                        canvasWidthPx = 720f
                    )
                    applied.add("template ${tmpl.label} (${tmpl.nodes.size} layers)")
                } else {
                    errors.add("Template not found: ${cmd.key}")
                }
            }
        }
        // ═══════════════════════════════════════════════════════
        //  PRIORITY 1 — GLOBAL (Ratio, Tighten, Clear)
        // ═══════════════════════════════════════════════════════
        for (cmd in parsed.commands) {
            when (cmd.type) {
                CmdType.RATIO -> {
                    val ratio = RatioLibrary.find(cmd.key)
                    viewModel.updateRatio(ratio)
                    applied.add("ratio ${cmd.key}")
                }

                CmdType.TIGHTEN -> {
                    viewModel.closeGapsFromPlayhead()
                    applied.add("tighten track")
                }

                CmdType.CLEAR_KEYFRAMES -> {
                    selected?.let {
                        viewModel.resetAllTransform()
                        applied.add("clear keyframes")
                    }
                }

                else -> {}
            }
        }

        // ═══════════════════════════════════════════════════════
        //  PRIORITY 2 — TRANSITIONS
        // ═══════════════════════════════════════════════════════
        for (cmd in parsed.commands) {
            when (cmd.type) {
                CmdType.TRANSITION_ALL -> {
                    val dur = cmd.value1 ?: 0.5f
                    viewModel.applyTransitionAll(cmd.key, dur)
                    applied.add("transition all ${cmd.key} ${dur}s")
                }

                CmdType.TRANSITION_AT -> {
                    val timeSec = cmd.value1 ?: 0f
                    val dur = cmd.value2 ?: 0.5f
                    viewModel.applyTransitionAt(cmd.key, timeSec, dur)
                    applied.add("transition at ${timeSec}s ${cmd.key} ${dur}s")
                }

                CmdType.TRANSITION_LAYER -> {
                    val pattern = cmd.stringValue ?: ""
                    val isAudio = cmd.key == "audio"
                    val track = cmd.value1?.toInt() ?: 0
                    viewModel.applyLayerTransitions(pattern, track, isAudio)
                    applied.add("layer transitions: $pattern")
                }

                else -> {}
            }
        }

        // ═══════════════════════════════════════════════════════
        //  PRIORITY 3 — TEXT + STICKER (timestamp + AUTO-STACKING)
        // ═══════════════════════════════════════════════════════

        // 🆕 Group texts by [startMs-endMs] block for auto-stacking
        val textCommands = parsed.commands.filter { it.type == CmdType.TEXT }
        val groupedTexts = textCommands
            .filter { it.startMs != null && it.endMs != null }
            .groupBy { "${it.startMs}_${it.endMs}" }

        val textPositions = mutableMapOf<ParsedCommand, Pair<Float, Float>>()

        for ((_, group) in groupedTexts) {
            val count = group.size
            if (count == 0) continue

            // 🆕 Distribute Y positions: top (15%) to bottom (85%)
            val topMargin = 15f
            val bottomMargin = 85f
            val spacing = if (count > 1) (bottomMargin - topMargin) / (count - 1)
            else 50f

            group.forEachIndexed { index, cmd ->
                val y = if (count == 1) 50f
                else topMargin + index * spacing
                textPositions[cmd] = 50f to y
            }
        }

        // Now create all texts
        for (cmd in parsed.commands) {
            when (cmd.type) {
                CmdType.TEXT -> {
                    val content = cmd.stringValue ?: continue
                    var textState = TextState(content = content)
                    cmd.extra?.let { props ->
                        textState = applyTextProps(textState, props)
                    }

                    val startMs = cmd.startMs
                    val endMs = cmd.endMs

                    if (startMs != null && endMs != null) {
                        // 🆕 Apply auto-stack position if user didn't specify
                        val userSetPosition =
                            cmd.extra?.contains("position", ignoreCase = true) == true
                        if (!userSetPosition) {
                            textPositions[cmd]?.let { (x, y) ->
                                textState = textState.copy(positionX = x, positionY = y)
                            }
                        }

                        viewModel.createTextClipAtTime(
                            textState = textState,
                            startMs = startMs,
                            endMs = endMs
                        )
                        applied.add(
                            "text @${startMs / 1000}s-${endMs / 1000}s \"$content\""
                        )
                    } else {
                        viewModel.createTextClip(textState)
                        applied.add("text \"$content\"")
                    }
                }

                CmdType.STICKER -> {
                    val emoji = cmd.stringValue ?: continue
                    val startMs = cmd.startMs
                    val endMs = cmd.endMs

                    if (startMs != null && endMs != null) {
                        viewModel.createStickerAtTime(
                            emoji = emoji,
                            startMs = startMs,
                            endMs = endMs
                        )
                        applied.add("sticker @${startMs / 1000}s \"$emoji\"")
                    } else {
                        viewModel.addOrUpdateSticker(emoji)
                        applied.add("sticker $emoji")
                    }
                }

                else -> {}
            }
        }
        // 🆕 BULK MODE: if multi-selected, use bulk viewModel methods
        val hasMulti = state.multiSelectedIds.isNotEmpty()
        // ═══════════════════════════════════════════════════════
        //  PRIORITY 4 — SELECTED CLIP MUTATIONS
        // ═══════════════════════════════════════════════════════
        for (cmd in parsed.commands) {
            try {
                when (cmd.type) {
                    CmdType.ADJUSTMENT -> {
                        val target = selected ?: continue
                        val value = cmd.value1 ?: continue
                        val existing = state.clips.firstOrNull {
                            it.isAdjustmentClip &&
                                    abs(it.timelineStartMs - target.timelineStartMs) < 100
                        }
                        if (existing != null) {
                            val newAdj = PromptEngine.withAdjustment(
                                existing.adjustments, cmd.key, value
                            )
                            viewModel.updateClipAdjustment(existing.id, newAdj)
                        } else {
                            val newAdj = PromptEngine.withAdjustment(
                                target.adjustments, cmd.key, value
                            )
                            viewModel.updateSelectedAdjustment(newAdj)
                        }
                        applied.add("${cmd.key} $value")
                    }

                    CmdType.FILTER -> {
                        val target = selected ?: continue
                        val value = cmd.value1 ?: continue
                        viewModel.updateFilters(target.filters.set(cmd.key, value))
                        applied.add("${cmd.key} $value")
                    }

                    CmdType.EFFECT -> {
                        val preset = com.moody.moodyvideoeditor.data.EffectLibrary
                            .findByKey(cmd.key) ?: continue
                        viewModel.applyEffectPreset(preset.key, preset.label)
                        applied.add("effect ${preset.label}")
                    }

                    CmdType.SPEED -> {
                        val sp = cmd.value1 ?: continue
                        viewModel.setSpeed(sp)
                        applied.add("speed ${sp}x")
                    }

                    CmdType.TRANSFORM -> {
                        val value = cmd.value1 ?: continue
                        val hasMulti = state.multiSelectedIds.isNotEmpty()
                        when (cmd.key) {
                            "scale" -> viewModel.setClipScale(value / 100f)
                            "rotation" -> viewModel.setClipRotation(value)
                            "positionx" -> viewModel.setClipOffset(
                                (value - 50f) / 100f, 0f
                            )

                            "positiony" -> viewModel.setClipOffset(
                                0f, (value - 50f) / 100f
                            )

                            "cropL", "cropR", "cropT", "cropB" -> {
                                val cur = selected ?: continue
                                viewModel.setCrop(
                                    if (cmd.key == "cropL") value / 100f else cur.cropL,
                                    if (cmd.key == "cropR") value / 100f else cur.cropR,
                                    if (cmd.key == "cropT") value / 100f else cur.cropT,
                                    if (cmd.key == "cropB") value / 100f else cur.cropB
                                )
                            }
                        }
                        val label = if (hasMulti) {
                            "${cmd.key} $value → ${state.multiSelectedIds.size} clips"
                        } else "${cmd.key} $value"
                        applied.add(label)
                    }

                    CmdType.TRANSITION -> {
                        val dur = cmd.value1 ?: 0.5f
                        val preset = TransitionLibrary.find(cmd.key)
                        viewModel.updateTransition(
                            TransitionState(
                                key = preset?.key ?: cmd.key,
                                durationMs = (dur * 1000f).toLong().coerceIn(200L, 3000L)
                            )
                        )
                        applied.add("transition ${cmd.key} ${dur}s")
                    }

                    CmdType.ANIMATION -> {
                        val sel = selected ?: continue
                        if (sel.isTextClip) {
                            viewModel.setTextAnimation(PromptEngine.animationKey(cmd.key))
                            applied.add("animation ${cmd.key}")
                        }
                    }

                    CmdType.FONT -> {
                        val sel = selected ?: continue
                        if (sel.isTextClip) {
                            val st = sel.textState ?: continue
                            viewModel.updateSelectedText(
                                st.copy(
                                    fontFamily = cmd.key,
                                    fontSize = cmd.value1?.toInt() ?: st.fontSize
                                )
                            )
                            applied.add("font ${cmd.key}")
                        }
                    }

                    CmdType.ALIGN -> {
                        val sel = selected ?: continue
                        if (sel.isTextClip) {
                            val st = sel.textState ?: continue
                            viewModel.updateSelectedText(st.copy(alignment = cmd.key))
                            applied.add("align ${cmd.key}")
                        }
                    }

                    CmdType.ANCHOR -> {
                        val sel = selected ?: continue
                        if (sel.isTextClip) {
                            val st = sel.textState ?: continue
                            if (cmd.key == "coords") {
                                viewModel.updateSelectedText(
                                    st.copy(
                                        anchorX = cmd.value1 ?: 50f,
                                        anchorY = cmd.value2 ?: 50f
                                    )
                                )
                            } else {
                                val (ax, ay) = anchorCoords(cmd.key)
                                viewModel.updateSelectedText(
                                    st.copy(anchorX = ax, anchorY = ay)
                                )
                            }
                            applied.add("anchor ${cmd.key}")
                        }
                    }

                    CmdType.CHROMA -> {
                        val rest = cmd.key
                        val hexRegex = Regex("""#([0-9a-fA-F]{6})""").find(rest)
                        val colorLong = hexRegex?.let {
                            try {
                                (0xFF000000L) or it.groupValues[1].toLong(16)
                            } catch (_: Exception) {
                                null
                            }
                        } ?: 0xFF00FF00
                        viewModel.updateSelectedChroma(ChromaState(keyColor = colorLong))
                        applied.add("chroma $rest")
                    }

                    CmdType.AUDIO_FX -> {
                        viewModel.setAudioFx(cmd.key)
                        applied.add("audio ${cmd.key}")
                    }

                    CmdType.BRUSH_GRADIENT -> {
                        if (cmd.key == "off") {
                            applied.add("brush gradient off")
                        } else {
                            val parts = cmd.stringValue?.split(",") ?: emptyList()
                            if (parts.size == 2) {
                                applied.add("brush gradient ${parts[0]} → ${parts[1]}")
                            }
                        }
                    }

                    CmdType.BRUSH_TYPE -> {
                        val brushType = when (cmd.key) {
                            "pen" -> com.moody.moodyvideoeditor.data.BrushType.PEN
                            "marker" -> com.moody.moodyvideoeditor.data.BrushType.MARKER
                            "chalk" -> com.moody.moodyvideoeditor.data.BrushType.CHALK
                            "neon" -> com.moody.moodyvideoeditor.data.BrushType.NEON
                            "glow" -> com.moody.moodyvideoeditor.data.BrushType.GLOW
                            "spray" -> com.moody.moodyvideoeditor.data.BrushType.SPRAY
                            else -> com.moody.moodyvideoeditor.data.BrushType.PEN
                        }
                        applied.add("brush type: ${cmd.key}")
                    }

                    CmdType.BRUSH_DRAW -> {
                        if (cmd.key == "on") {
                            viewModel.createBrushClip()
                            applied.add("brush draw enabled")
                        } else {
                            applied.add("brush draw disabled")
                        }
                    }

                    CmdType.BRUSH_CLEAR -> {
                        val s = viewModel.state.value
                        val lastBrush = s.clips.lastOrNull { it.isBrushClip }
                        if (lastBrush != null) {
                            viewModel.clearBrushClipStrokes(lastBrush.id)
                        }
                        applied.add("brush cleared")
                    }

                    CmdType.COLOR_WHEEL -> {
                        val target = selected ?: continue
                        var cw = target.colorWheel
                        when (cmd.key) {
                            "hdr" -> cw = cw.copy(hdrWhite = cmd.value1 ?: 100f)
                            "shadows", "midtones", "highlights" -> {
                                val hue = colorToHue(cmd.stringValue ?: "red")
                                val tone = ToneValue(
                                    hue,
                                    cmd.value1 ?: 0f,
                                    cmd.value2 ?: 0f
                                )
                                cw = when (cmd.key) {
                                    "shadows" -> cw.copy(shadows = tone)
                                    "midtones" -> cw.copy(midtones = tone)
                                    else -> cw.copy(highlights = tone)
                                }
                            }
                        }
                        viewModel.updateColorWheel(cw)
                        applied.add("wheel ${cmd.key}")
                    }

                    CmdType.KEYFRAME -> {
                        applied.add("keyframe ${cmd.key}")
                    }

                    else -> {}
                }
            } catch (e: Exception) {
                errors.add("Failed: ${cmd.raw} (${e.message})")
            }
        }

        return ExecuteReport(
            applied = applied,
            unknown = parsed.unknown,
            errors = errors
        )
    }

    private fun applyTextProps(initial: TextState, props: String): TextState {
        var st = initial
        val tokens = props.split(Regex("\\s+"))
        var i = 0

        fun parseColor(str: String): Long? {
            val s = str.trim()
            if (s.startsWith("#")) {
                return try {
                    val hex = s.removePrefix("#")
                    val rgb = when (hex.length) {
                        3 -> {
                            val r = hex[0].toString().toInt(16) * 17
                            val g = hex[1].toString().toInt(16) * 17
                            val b = hex[2].toString().toInt(16) * 17
                            (r shl 16) or (g shl 8) or b
                        }

                        6 -> hex.toInt(16)
                        else -> return null
                    }
                    0xFF000000L or rgb.toLong()
                } catch (_: Exception) {
                    null
                }
            }
            return when (s.lowercase()) {
                "red" -> 0xFFFF0000L
                "orange" -> 0xFFFF6B00L
                "yellow" -> 0xFFFFCC00L
                "green" -> 0xFF00FF00L
                "cyan" -> 0xFF00E5FFL
                "blue" -> 0xFF0066FFL
                "purple" -> 0xFF7C3AEDL
                "magenta", "pink" -> 0xFFFF00FFL
                "white" -> 0xFFFFFFFFL
                "black" -> 0xFF000000L
                else -> null
            }
        }

        while (i < tokens.size) {
            val t = tokens[i].lowercase()
            when (t) {
                "size" -> {
                    val v = tokens.getOrNull(i + 1)?.toIntOrNull()
                    if (v != null) st = st.copy(fontSize = v)
                    i += 2
                }

                "color" -> {
                    val c = tokens.getOrNull(i + 1)
                    if (c != null) {
                        parseColor(c)?.let { st = st.copy(color = it) }
                    }
                    i += 2
                }

                // 🆕 TEXT COLOR RAMP (gradient)
                "gradient" -> {
                    val c1 = tokens.getOrNull(i + 1)
                    val c2 = tokens.getOrNull(i + 2)
                    // Handle both: "gradient red blue" and "gradient red to blue"
                    if (c2?.lowercase() == "to") {
                        val c2b = tokens.getOrNull(i + 3)
                        val angle = tokens.getOrNull(i + 4)?.toFloatOrNull()
                        val col1 = c1?.let { parseColor(it) }
                        val col2 = c2b?.let { parseColor(it) }
                        if (col1 != null && col2 != null) {
                            st = st.copy(
                                gradientEnabled = true,
                                gradientColor1 = col1,
                                gradientColor2 = col2,
                                gradientAngle = angle ?: st.gradientAngle
                            )
                        }
                        i += if (angle != null) 5 else 4
                    } else {
                        val angle = tokens.getOrNull(i + 3)?.toFloatOrNull()
                        val col1 = c1?.let { parseColor(it) }
                        val col2 = c2?.let { parseColor(it) }
                        if (col1 != null && col2 != null) {
                            st = st.copy(
                                gradientEnabled = true,
                                gradientColor1 = col1,
                                gradientColor2 = col2,
                                gradientAngle = angle ?: st.gradientAngle
                            )
                        }
                        i += if (angle != null) 4 else 3
                    }
                }

                "nogradient", "solid" -> {
                    st = st.copy(gradientEnabled = false)
                    i += 1
                }

                // 🆕 TEXT GLOW
                "glow" -> {
                    val c = tokens.getOrNull(i + 1)
                    val r = tokens.getOrNull(i + 2)?.toFloatOrNull()
                    val col = c?.let { parseColor(it) }
                    if (col != null) {
                        st = st.copy(
                            glowEnabled = true,
                            glowColor = col,
                            glowRadius = r ?: 25f
                        )
                        i += if (r != null) 3 else 2
                    } else if (r != null) {
                        // "glow 30" — radius only, keep old color
                        st = st.copy(glowEnabled = true, glowRadius = r)
                        i += 2
                    } else {
                        st = st.copy(glowEnabled = true)
                        i += 1
                    }
                }

                "noglow" -> {
                    st = st.copy(glowEnabled = false)
                    i += 1
                }

                // 🆕 TEXT STROKE
                "stroke" -> {
                    val w = tokens.getOrNull(i + 1)?.toFloatOrNull()
                    val c = tokens.getOrNull(i + 2)
                    val col = c?.let { parseColor(it) }
                    if (w != null) {
                        st = st.copy(
                            strokeEnabled = true,
                            strokeWidth = w,
                            strokeColor = col ?: st.strokeColor
                        )
                        i += if (col != null) 3 else 2
                    } else {
                        st = st.copy(strokeEnabled = true)
                        i += 1
                    }
                }

                "nostroke" -> {
                    st = st.copy(strokeEnabled = false)
                    i += 1
                }

                // 🆕 TEXT SHADOW
                "shadow" -> {
                    val c = tokens.getOrNull(i + 1)
                    val blur = tokens.getOrNull(i + 2)?.toFloatOrNull()
                    val x = tokens.getOrNull(i + 3)?.toFloatOrNull()
                    val y = tokens.getOrNull(i + 4)?.toFloatOrNull()
                    val col = c?.let { parseColor(it) }
                    if (col != null && blur != null) {
                        st = st.copy(
                            shadowEnabled = true,
                            shadowColor = col,
                            shadowBlur = blur,
                            shadowOffsetX = x ?: st.shadowOffsetX,
                            shadowOffsetY = y ?: st.shadowOffsetY
                        )
                        i += 5
                    } else {
                        i += 1
                    }
                }

                "noshedow", "noshadow" -> {
                    st = st.copy(shadowEnabled = false)
                    i += 1
                }

                // 🆕 TYPOGRAPHY
                "tracking" -> {
                    val v = tokens.getOrNull(i + 1)?.toFloatOrNull()
                    if (v != null) st = st.copy(letterSpacing = v)
                    i += 2
                }

                "lineheight", "leading" -> {
                    val v = tokens.getOrNull(i + 1)?.toFloatOrNull()
                    if (v != null) st = st.copy(lineHeight = v)
                    i += 2
                }

                "rotation", "rotate" -> {
                    val v = tokens.getOrNull(i + 1)?.toFloatOrNull()
                    if (v != null) st = st.copy(rotation = v)
                    i += 2
                }

                "animation", "anim" -> {
                    val a = tokens.getOrNull(i + 1)
                    if (a != null) st = st.copy(animation = PromptEngine.animationKey(a))
                    i += 2
                }

                "font" -> {
                    var fontName = ""
                    if (tokens.getOrNull(i + 1)?.startsWith("\"") == true) {
                        val sb = StringBuilder()
                        var j = i + 1
                        while (j < tokens.size) {
                            sb.append(tokens[j]).append(" ")
                            if (tokens[j].endsWith("\"")) break
                            j++
                        }
                        fontName = sb.toString().trim().trim('"')
                        i = j + 1
                    } else {
                        fontName = tokens.getOrNull(i + 1) ?: ""
                        i += 2
                    }
                    if (fontName.isNotBlank()) {
                        st = st.copy(fontFamily = fontName)
                    }
                }

                "position", "pos" -> {
                    val x = tokens.getOrNull(i + 1)?.toFloatOrNull()
                    val y = tokens.getOrNull(i + 2)?.toFloatOrNull()
                    if (x != null) st = st.copy(positionX = x.coerceIn(0f, 100f))
                    if (y != null) st = st.copy(positionY = y.coerceIn(0f, 100f))
                    i += 3
                }

                "posx", "x" -> {
                    val v = tokens.getOrNull(i + 1)?.toFloatOrNull()
                    if (v != null) st = st.copy(positionX = v.coerceIn(0f, 100f))
                    i += 2
                }

                "posy", "y" -> {
                    val v = tokens.getOrNull(i + 1)?.toFloatOrNull()
                    if (v != null) st = st.copy(positionY = v.coerceIn(0f, 100f))
                    i += 2
                }

                "anchor" -> {
                    val a = tokens.getOrNull(i + 1) ?: ""
                    val (ax, ay) = when (a.lowercase()) {
                        "top-left" -> 0f to 0f
                        "top-center" -> 50f to 0f
                        "top-right" -> 100f to 0f
                        "center-left" -> 0f to 50f
                        "center" -> 50f to 50f
                        "center-right" -> 100f to 50f
                        "bottom-left" -> 0f to 100f
                        "bottom-center" -> 50f to 100f
                        "bottom-right" -> 100f to 100f
                        else -> 50f to 50f
                    }
                    st = st.copy(anchorX = ax, anchorY = ay)
                    i += 2
                }

                "bold" -> {
                    st = st.copy(fontWeight = "bold"); i++
                }

                "italic" -> {
                    st = st.copy(fontStyle = "italic"); i++
                }

                "align" -> {
                    val a = tokens.getOrNull(i + 1)?.lowercase()
                    if (a in listOf("left", "center", "right")) {
                        st = st.copy(alignment = a!!)
                    }
                    i += 2
                }

                "opacity" -> {
                    val v = tokens.getOrNull(i + 1)?.toFloatOrNull()
                    if (v != null) st = st.copy(opacity = v.coerceIn(0f, 100f))
                    i += 2
                }

                else -> i++
            }
        }
        return st
    }

    private fun anchorCoords(key: String): Pair<Float, Float> = when (key) {
        "top-left" -> 0f to 0f
        "top-center" -> 50f to 0f
        "top-right" -> 100f to 0f
        "center-left" -> 0f to 50f
        "center" -> 50f to 50f
        "center-right" -> 100f to 50f
        "bottom-left" -> 0f to 100f
        "bottom-center" -> 50f to 100f
        "bottom-right" -> 100f to 100f
        else -> 50f to 50f
    }

    private fun colorToHue(name: String): Float = when (name.lowercase()) {
        "red" -> 0f
        "orange" -> 30f
        "yellow" -> 60f
        "green" -> 120f
        "cyan" -> 180f
        "blue" -> 240f
        "purple" -> 280f
        "magenta" -> 300f
        else -> 0f
    }
}
package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.ChromaState
import com.moody.moodyvideoeditor.data.RatioLibrary
import com.moody.moodyvideoeditor.data.TextState
import com.moody.moodyvideoeditor.data.ToneValue
import com.moody.moodyvideoeditor.data.TransitionLibrary
import com.moody.moodyvideoeditor.data.TransitionState
import com.moody.moodyvideoeditor.data.VisualizerPreset
import com.moody.moodyvideoeditor.data.VisualizerState
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
        //  PRIORITY 0 — TEMPLATES
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
        //  PRIORITY 1 — GLOBAL
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

                CmdType.TRANSITION_LAYER_CLIPS,
                CmdType.TRANSITION_CLIP_MAP -> {
                    val layerNum = cmd.value1?.toInt() ?: 0
                    val pairsStr = cmd.stringValue ?: ""
                    val clipMap = parseLayerClipPairs(pairsStr)

                    if (layerNum > 0 && clipMap.isNotEmpty()) {
                        viewModel.applyLayerClipTransitions(layerNum, clipMap)
                        val desc = clipMap.entries.joinToString(", ") {
                            "C${it.key} ${it.value}"
                        }
                        applied.add("L$layerNum transitions: $desc")
                    } else {
                        errors.add("Invalid layer clips: ${cmd.raw}")
                    }
                }

                else -> {}
            }
        }

        // ═══════════════════════════════════════════════════════
        //  PRIORITY 3 — TEXT + STICKER
        // ═══════════════════════════════════════════════════════
        val textCommands = parsed.commands.filter { it.type == CmdType.TEXT }
        val groupedTexts = textCommands
            .filter { it.startMs != null && it.endMs != null }
            .groupBy { "${it.startMs}_${it.endMs}" }

        val textPositions = mutableMapOf<ParsedCommand, Pair<Float, Float>>()

        for ((_, group) in groupedTexts) {
            val count = group.size
            if (count == 0) continue

            val topMargin = 15f
            val bottomMargin = 85f
            val spacing = if (count > 1)
                (bottomMargin - topMargin) / (count - 1)
            else 50f

            group.forEachIndexed { index, cmd ->
                val y = if (count == 1) 50f
                else topMargin + index * spacing
                textPositions[cmd] = 50f to y
            }
        }

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
                        val userSetPosition =
                            cmd.extra?.contains("position", ignoreCase = true) == true
                        if (!userSetPosition) {
                            textPositions[cmd]?.let { (x, y) ->
                                textState = textState.copy(
                                    positionX = x, positionY = y
                                )
                            }
                        }

                        viewModel.createTextClipAtTime(
                            textState = textState,
                            startMs = startMs,
                            endMs = endMs
                        )
                        applied.add(
                            "text @${startMs / 1000}s-${endMs / 1000}s " +
                                    "\"$content\""
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

                    var state2 = com.moody.moodyvideoeditor.data.StickerState(
                        emoji = emoji
                    )
                    cmd.extra?.let { props ->
                        state2 = applyStickerProps(state2, props)
                    }

                    if (startMs != null && endMs != null) {
                        viewModel.createStickerAtTime(
                            emoji = state2.emoji,
                            startMs = startMs,
                            endMs = endMs
                        )
                        val sel = viewModel.state.value.selectedClip
                        if (sel != null && sel.isStickerClip) {
                            viewModel.updateSelectedSticker(state2)
                        }
                        applied.add(
                            "sticker @${startMs / 1000}s \"${state2.emoji}\""
                        )
                    } else {
                        viewModel.addOrUpdateSticker(state2.emoji)
                        viewModel.updateSelectedSticker(state2)
                        applied.add("sticker ${state2.emoji}")
                    }
                }

                else -> {}
            }
        }

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
                                    abs(
                                        it.timelineStartMs -
                                                target.timelineStartMs
                                    ) < 100
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
                        val hasMultiT = state.multiSelectedIds.isNotEmpty()
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
                                    if (cmd.key == "cropL") value / 100f
                                    else cur.cropL,
                                    if (cmd.key == "cropR") value / 100f
                                    else cur.cropR,
                                    if (cmd.key == "cropT") value / 100f
                                    else cur.cropT,
                                    if (cmd.key == "cropB") value / 100f
                                    else cur.cropB
                                )
                            }
                        }
                        val label = if (hasMultiT) {
                            "${cmd.key} $value → " +
                                    "${state.multiSelectedIds.size} clips"
                        } else "${cmd.key} $value"
                        applied.add(label)
                    }

                    CmdType.TRANSITION -> {
                        val dur = cmd.value1 ?: 0.5f
                        val preset = TransitionLibrary.find(cmd.key)
                        viewModel.updateTransition(
                            TransitionState(
                                key = preset?.key ?: cmd.key,
                                durationMs = (dur * 1000f).toLong()
                                    .coerceIn(200L, 3000L)
                            )
                        )
                        applied.add("transition ${cmd.key} ${dur}s")
                    }

                    CmdType.ANIMATION -> {
                        val sel = selected ?: continue
                        if (sel.isTextClip) {
                            viewModel.setTextAnimation(
                                PromptEngine.animationKey(cmd.key)
                            )
                            applied.add("animation ${cmd.key}")
                        }
                    }

                    CmdType.BEAT_ANIMATION -> {
                        val beats = viewModel.state.value.beatTimesMs
                        if (beats.isEmpty()) {
                            errors.add(
                                "No beats detected. Run beat detection first."
                            )
                        } else {
                            val sel = viewModel.state.value.selectedClip
                            if (sel == null) {
                                errors.add(
                                    "Select a clip first to apply " +
                                            "beat animation"
                                )
                            } else {
                                val amount = cmd.value1 ?: 100f
                                val count = viewModel.applyBeatAnimation(
                                    type = cmd.key,
                                    amount = amount,
                                    beatTimesMs = beats
                                )
                                if (count > 0) {
                                    applied.add(
                                        "beat ${cmd.key} × $count beats " +
                                                "(amount=$amount)"
                                    )
                                } else {
                                    errors.add(
                                        "No beats within selected clip range"
                                    )
                                }
                            }
                        }
                    }

                    CmdType.FONT -> {
                        val sel = selected ?: continue
                        if (sel.isTextClip) {
                            val st = sel.textState ?: continue
                            viewModel.updateSelectedText(
                                st.copy(
                                    fontFamily = cmd.key,
                                    fontSize = cmd.value1?.toInt()
                                        ?: st.fontSize
                                )
                            )
                            applied.add("font ${cmd.key}")
                        }
                    }

                    CmdType.ALIGN -> {
                        val sel = selected ?: continue
                        if (sel.isTextClip) {
                            val st = sel.textState ?: continue
                            viewModel.updateSelectedText(
                                st.copy(alignment = cmd.key)
                            )
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
                        val hexRegex = Regex("""#([0-9a-fA-F]{6})""")
                            .find(rest)
                        val colorLong = hexRegex?.let {
                            try {
                                (0xFF000000L) or
                                        it.groupValues[1].toLong(16)
                            } catch (_: Exception) {
                                null
                            }
                        } ?: 0xFF00FF00
                        viewModel.updateSelectedChroma(
                            ChromaState(keyColor = colorLong)
                        )
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
                            val parts = cmd.stringValue?.split(",")
                                ?: emptyList()
                            if (parts.size == 2) {
                                applied.add(
                                    "brush gradient ${parts[0]} → ${parts[1]}"
                                )
                            }
                        }
                    }

                    CmdType.BRUSH_TYPE -> {
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
                                val hue = colorToHue(
                                    cmd.stringValue ?: "red"
                                )
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

                    // ═══════════════════════════════════════════
                    //  VISUALIZER
                    // ═══════════════════════════════════════════
                    CmdType.VISUALIZER -> {
                        val s2 = viewModel.state.value
                        val existingViz = s2.selectedClip
                            ?.takeIf { it.isVisualizerClip }
                            ?: s2.clips.firstOrNull { it.isVisualizerClip }

                        when (cmd.key) {
                            "add", "create", "new" -> {
                                val ok = viewModel.createVisualizerClip()
                                if (ok) applied.add("visualizer added")
                                else errors.add(
                                    "visualizer add: select an audio " +
                                            "clip first"
                                )
                            }

                            "remove", "delete" -> {
                                existingViz?.let {
                                    viewModel.removeVisualizerLayer(it.id)
                                    applied.add("visualizer removed")
                                } ?: errors.add("No visualizer to remove")
                            }

                            "preset" -> {
                                val p = resolveVisualizerPreset(
                                    cmd.stringValue ?: ""
                                )
                                if (existingViz != null && p != null) {
                                    viewModel.updateVisualizerLayer(
                                        existingViz.id,
                                        (existingViz.visualizer
                                            ?: VisualizerState())
                                            .copy(preset = p)
                                    )
                                    applied.add(
                                        "visualizer preset ${p.label}"
                                    )
                                } else {
                                    errors.add(
                                        "Unknown preset: ${cmd.stringValue}"
                                    )
                                }
                            }

                            "color1", "color2" -> {
                                val hex = cmd.stringValue?.removePrefix("#")
                                if (existingViz != null && hex != null) {
                                    val colorLong = 0xFF000000L or
                                            hex.toLong(16)
                                    val vs = existingViz.visualizer
                                        ?: VisualizerState()
                                    val updated = if (cmd.key == "color1")
                                        vs.copy(color1 = colorLong)
                                    else vs.copy(color2 = colorLong)
                                    viewModel.updateVisualizerLayer(
                                        existingViz.id, updated
                                    )
                                    applied.add(
                                        "visualizer ${cmd.key} #$hex"
                                    )
                                } else {
                                    errors.add("visualizer ${cmd.key}: failed")
                                }
                            }

                            "size" -> {
                                val pct = cmd.value1 ?: 32f
                                if (existingViz != null) {
                                    val sz = (pct / 100f)
                                        .coerceIn(0.05f, 1.5f)
                                    val vs = existingViz.visualizer
                                        ?: VisualizerState()
                                    viewModel.updateVisualizerLayer(
                                        existingViz.id, vs.copy(size = sz)
                                    )
                                    applied.add(
                                        "visualizer size ${pct.toInt()}%"
                                    )
                                } else errors.add("No visualizer")
                            }

                            "position" -> {
                                val x = (cmd.value1 ?: 50f) / 100f
                                val y = (cmd.value2 ?: 50f) / 100f
                                if (existingViz != null) {
                                    val vs = existingViz.visualizer
                                        ?: VisualizerState()
                                    viewModel.updateVisualizerLayer(
                                        existingViz.id,
                                        vs.copy(
                                            positionX = x.coerceIn(0f, 1f),
                                            positionY = y.coerceIn(0f, 1f)
                                        )
                                    )
                                    applied.add("visualizer pos")
                                } else errors.add("No visualizer")
                            }

                            "opacity" -> {
                                val o = ((cmd.value1 ?: 100f) / 100f)
                                    .coerceIn(0f, 1f)
                                if (existingViz != null) {
                                    val vs = existingViz.visualizer
                                        ?: VisualizerState()
                                    viewModel.updateVisualizerLayer(
                                        existingViz.id, vs.copy(opacity = o)
                                    )
                                    applied.add("visualizer opacity")
                                } else errors.add("No visualizer")
                            }

                            "glow" -> {
                                val on = cmd.stringValue == "on"
                                if (existingViz != null) {
                                    val vs = existingViz.visualizer
                                        ?: VisualizerState()
                                    viewModel.updateVisualizerLayer(
                                        existingViz.id, vs.copy(glow = on)
                                    )
                                    applied.add(
                                        "visualizer glow ${cmd.stringValue}"
                                    )
                                } else errors.add("No visualizer")
                            }

                            "reaction" -> {
                                val r = (cmd.value1 ?: 1f).coerceIn(0f, 2f)
                                if (existingViz != null) {
                                    val vs = existingViz.visualizer
                                        ?: VisualizerState()
                                    viewModel.updateVisualizerLayer(
                                        existingViz.id,
                                        vs.copy(beatReaction = r)
                                    )
                                    applied.add("visualizer reaction $r")
                                } else errors.add("No visualizer")
                            }

                            "text" -> {
                                val content = cmd.stringValue ?: ""
                                if (existingViz != null) {
                                    val vs = existingViz.visualizer
                                        ?: VisualizerState()
                                    val newTextState = vs.textState.copy(
                                        content = content,
                                        fontSize = cmd.value1?.toInt()
                                            ?: vs.textState.fontSize
                                    )
                                    viewModel.updateVisualizerLayer(
                                        existingViz.id,
                                        vs.copy(
                                            textState = newTextState,
                                            textContent = content,
                                            showText = true
                                        )
                                    )
                                    applied.add(
                                        "visualizer text \"$content\""
                                    )
                                } else errors.add("No visualizer")
                            }

                            "show" -> {
                                if (existingViz != null) {
                                    val vs = existingViz.visualizer
                                        ?: VisualizerState()
                                    val updated = when (cmd.stringValue) {
                                        "text" -> vs.copy(showText = true)
                                        "image" -> vs.copy(showImage = true)
                                        else -> vs
                                    }
                                    viewModel.updateVisualizerLayer(
                                        existingViz.id, updated
                                    )
                                    applied.add(
                                        "visualizer show ${cmd.stringValue}"
                                    )
                                } else errors.add("No visualizer")
                            }

                            "hide" -> {
                                if (existingViz != null) {
                                    val vs = existingViz.visualizer
                                        ?: VisualizerState()
                                    val updated = when (cmd.stringValue) {
                                        "text" -> vs.copy(showText = false)
                                        "image" -> vs.copy(showImage = false)
                                        else -> vs
                                    }
                                    viewModel.updateVisualizerLayer(
                                        existingViz.id, updated
                                    )
                                    applied.add(
                                        "visualizer hide ${cmd.stringValue}"
                                    )
                                } else errors.add("No visualizer")
                            }

                            "order" -> {
                                if (existingViz != null) {
                                    val vs = existingViz.visualizer
                                        ?: VisualizerState()
                                    val textTop =
                                        cmd.stringValue == "text-top"
                                    viewModel.updateVisualizerLayer(
                                        existingViz.id,
                                        vs.copy(textOnTopOfImage = textTop)
                                    )
                                    applied.add(
                                        "visualizer order ${cmd.stringValue}"
                                    )
                                } else errors.add("No visualizer")
                            }

                            else -> {
                                errors.add(
                                    "visualizer: unknown command " +
                                            "'${cmd.stringValue}'"
                                )
                            }
                        }
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

    // ═══════════════════════════════════════════════════════════
    //  LAYER CLIP TRANSITIONS PARSER
    // ═══════════════════════════════════════════════════════════
    private fun parseLayerClipPairs(pairs: String): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        if (pairs.isBlank()) return result

        pairs.split("|").forEach { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                val num = parts[0].toIntOrNull() ?: return@forEach
                val name = parts[1].trim()
                if (num > 0) {
                    result[num] = name.ifBlank { "skip" }
                }
            }
        }
        return result
    }

    // ═══════════════════════════════════════════════════════════
    //  VISUALIZER PRESET RESOLVER
    // ═══════════════════════════════════════════════════════════
    /**
     * Resolve a preset name from user prompt → VisualizerPreset.
     * Supports 100 preset keys + legacy aliases.
     */
    private fun resolveVisualizerPreset(name: String): VisualizerPreset? {
        val clean = name.trim().lowercase()
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
        if (clean.isBlank()) return null

        // 1) Direct key match
        VisualizerPreset.values().firstOrNull {
            it.key.lowercase() == clean
        }?.let { return it }

        // 2) Label match
        VisualizerPreset.values().firstOrNull {
            it.label.lowercase().replace(" ", "") == clean
        }?.let { return it }

        // 3) Short aliases
        val aliases = mapOf(
            // Spectrum
            "bars" to VisualizerPreset.CIRCULAR_SPECTRUM,
            "bar" to VisualizerPreset.CIRCULAR_SPECTRUM,
            "circular" to VisualizerPreset.CIRCULAR_SPECTRUM,
            "circle" to VisualizerPreset.CIRCULAR_SPECTRUM,
            "linear" to VisualizerPreset.LINEAR_WAVEFORM,
            "linearwave" to VisualizerPreset.LINEAR_WAVEFORM,
            "radial" to VisualizerPreset.RADIAL_BARS,
            "inner" to VisualizerPreset.INNER_RADIAL_BARS,
            "heartbeat" to VisualizerPreset.HEARTBEAT_WAVE,
            "square" to VisualizerPreset.SQUARE_SPECTRUM,
            "triangle" to VisualizerPreset.TRIANGLE_BEATS,
            "hexagon" to VisualizerPreset.HEXAGON_PULSE,
            "dot" to VisualizerPreset.DOT_MATRIX,
            "dots" to VisualizerPreset.DOT_MATRIX,
            "mirrored" to VisualizerPreset.MIRRORED_LINEAR,
            "glow" to VisualizerPreset.GLOW_WAVES,
            "thick" to VisualizerPreset.THICK_BARS,
            "thin" to VisualizerPreset.THIN_STRINGS,
            "sine" to VisualizerPreset.SINE_WAVE,
            "perspective" to VisualizerPreset.PERSPECTIVE_3D,
            "volcano" to VisualizerPreset.FREQUENCY_VOLCANO,
            "tornado" to VisualizerPreset.TORNADO_SPIRAL,
            "dual" to VisualizerPreset.DUAL_RING,
            "star" to VisualizerPreset.STAR_BURST,

            // Particles
            "particles" to VisualizerPreset.BASS_PARTICLES,
            "dust" to VisualizerPreset.FLOATING_DUST,
            "drops" to VisualizerPreset.LIQUID_DROPS,
            "firefly" to VisualizerPreset.FIREFLY_GLOW,
            "smoke" to VisualizerPreset.SMOKE_AURA,
            "matrix" to VisualizerPreset.MATRIX_RAIN,
            "snow" to VisualizerPreset.SNOWFALL,
            "nebula" to VisualizerPreset.COSMIC_NEBULA,
            "spark" to VisualizerPreset.SPARK_TRAIL,
            "ink" to VisualizerPreset.INK_BLEED,
            "sand" to VisualizerPreset.SAND_STORM,
            "magic" to VisualizerPreset.MAGIC_DUST,
            "meteor" to VisualizerPreset.METEOR_SHOWER,
            "plasma" to VisualizerPreset.PLASMA_ORBS,
            "confetti" to VisualizerPreset.CONFETTI_POP,
            "bubbles" to VisualizerPreset.BUBBLES_POP,
            "electric" to VisualizerPreset.ELECTRIC_STORM,
            "disintegration" to VisualizerPreset.DISINTEGRATION,
            "galaxy" to VisualizerPreset.GALAXY_VORTEX,
            "cybergrid" to VisualizerPreset.CYBER_GRID,

            // Neon
            "neon" to VisualizerPreset.NEON_GLOW_RING,
            "neonring" to VisualizerPreset.NEON_GLOW_RING,
            "rgb" to VisualizerPreset.RGB_GLITCH,
            "glitch" to VisualizerPreset.RGB_GLITCH,
            "vaporwave" to VisualizerPreset.VAPORWAVE_GRID,
            "vhs" to VisualizerPreset.VHS_NOISE,
            "laser" to VisualizerPreset.LASER_BEAM,
            "eq" to VisualizerPreset.DIGITAL_EQ,
            "chroma" to VisualizerPreset.CHROMA_PULSE,
            "scanline" to VisualizerPreset.SCANLINE_DISTORT,
            "tron" to VisualizerPreset.TRON_WIREFRAME,
            "led" to VisualizerPreset.LED_MATRIX,
            "arcade" to VisualizerPreset.ARCADE_GAMEOVER,
            "tunnel" to VisualizerPreset.LASER_TUNNEL,
            "tracer" to VisualizerPreset.NEON_TRACER,
            "pixel" to VisualizerPreset.PIXEL_DISSOLVE,
            "ecg" to VisualizerPreset.ECG_GRID,
            "synth" to VisualizerPreset.SYNTH_SUN,
            "hologram" to VisualizerPreset.HOLOGRAM,
            "crt" to VisualizerPreset.CRT_FLICKER,
            "vector" to VisualizerPreset.VECTOR_WAVE,

            // Geometric
            "minimal" to VisualizerPreset.MINIMAL_DOTS,
            "rotating" to VisualizerPreset.ROTATING_POLY,
            "kaleidoscope" to VisualizerPreset.KALEIDOSCOPE,
            "interlock" to VisualizerPreset.INTERLOCKING_RINGS,
            "expanding" to VisualizerPreset.EXPANDING_SQUARES,
            "origami" to VisualizerPreset.ORIGAMI,
            "fractal" to VisualizerPreset.FRACTAL_ZOOM,
            "parallax" to VisualizerPreset.PARALLAX_LINES,
            "isometric" to VisualizerPreset.ISOMETRIC_BLOCKS,
            "mirror" to VisualizerPreset.SYMMETRIC_MIRROR,
            "crosshair" to VisualizerPreset.CROSSHAIR,
            "dna" to VisualizerPreset.DNA_STRAND,
            "concentric" to VisualizerPreset.CONCENTRIC_RINGS,
            "shards" to VisualizerPreset.FLOATING_SHARDS,
            "infinite" to VisualizerPreset.INFINITE_TUNNEL,
            "morph" to VisualizerPreset.SHAPE_MORPH,
            "gyroscope" to VisualizerPreset.GYROSCOPE,
            "diagonal" to VisualizerPreset.SPLIT_DIAGONAL,
            "checker" to VisualizerPreset.CHECKERBOARD,
            "ribbon" to VisualizerPreset.VECTOR_RIBBON,

            // Cinematic
            "lensflare" to VisualizerPreset.LENS_FLARE,
            "shutter" to VisualizerPreset.CAMERA_SHUTTER,
            "cinematicdust" to VisualizerPreset.CINEMATIC_DUST,
            "vignette" to VisualizerPreset.VIGNETTE_BREATHE,
            "blur" to VisualizerPreset.BLUR_DISSOLVE,
            "sunbeams" to VisualizerPreset.SUNBEAMS,
            "rain" to VisualizerPreset.RAINDROPS,
            "grain" to VisualizerPreset.FILM_GRAIN,
            "lightleak" to VisualizerPreset.LIGHT_LEAK,
            "fog" to VisualizerPreset.FOGGY_AMBIANCE,
            "bokeh" to VisualizerPreset.BOKEH_DRIFT,
            "shadow" to VisualizerPreset.SHADOW_WAVE,
            "ripple" to VisualizerPreset.WATER_RIPPLE,
            "cloudy" to VisualizerPreset.CLOUDY_TIMELAPSE,
            "streak" to VisualizerPreset.LIGHT_STREAK,
            "countdown" to VisualizerPreset.VINTAGE_COUNTDOWN,
            "golden" to VisualizerPreset.GOLDEN_HOUR,
            "prism" to VisualizerPreset.PRISM_RAINBOW,
            "shake" to VisualizerPreset.CAMERA_SHAKE,
            "horizon" to VisualizerPreset.HORIZON_ZOOM,

            // Premium
            "sphere" to VisualizerPreset.AUDIO_SPHERE,
            "audioshpere" to VisualizerPreset.AUDIO_SPHERE,
            "waveformring" to VisualizerPreset.WAVEFORM_RING,
            "symmetric" to VisualizerPreset.SYMMETRIC_WAVE
        )

        aliases[clean]?.let { return it }

        // 4) Fuzzy contains
        VisualizerPreset.values().firstOrNull {
            val k = it.key.lowercase()
            val l = it.label.lowercase().replace(" ", "")
            k.contains(clean) || clean.contains(k) ||
                    l.contains(clean) || clean.contains(l)
        }?.let { return it }

        return null
    }

    // ═══════════════════════════════════════════════════════════
    //  TEXT PROPS PARSER
    // ═══════════════════════════════════════════════════════════
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

                "gradient" -> {
                    val c1 = tokens.getOrNull(i + 1)
                    val c2 = tokens.getOrNull(i + 2)
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
                    if (a != null) {
                        st = st.copy(
                            animation = PromptEngine.animationKey(a)
                        )
                    }
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
                    if (x != null) {
                        st = st.copy(positionX = x.coerceIn(0f, 100f))
                    }
                    if (y != null) {
                        st = st.copy(positionY = y.coerceIn(0f, 100f))
                    }
                    i += 3
                }

                "posx", "x" -> {
                    val v = tokens.getOrNull(i + 1)?.toFloatOrNull()
                    if (v != null) {
                        st = st.copy(positionX = v.coerceIn(0f, 100f))
                    }
                    i += 2
                }

                "posy", "y" -> {
                    val v = tokens.getOrNull(i + 1)?.toFloatOrNull()
                    if (v != null) {
                        st = st.copy(positionY = v.coerceIn(0f, 100f))
                    }
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
                    if (v != null) {
                        st = st.copy(opacity = v.coerceIn(0f, 100f))
                    }
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

    // ═══════════════════════════════════════════════════════════
    //  STICKER PROPS PARSER
    // ═══════════════════════════════════════════════════════════
    private fun applyStickerProps(
        initial: com.moody.moodyvideoeditor.data.StickerState,
        props: String
    ): com.moody.moodyvideoeditor.data.StickerState {
        var st = initial
        val tokens = props.split(Regex("\\s+"))
        var i = 0

        while (i < tokens.size) {
            when (tokens[i].lowercase()) {
                "animation", "anim" -> {
                    val a = tokens.getOrNull(i + 1)
                    if (a != null) {
                        st = st.copy(
                            animation = PromptEngine.animationKey(a)
                        )
                    }
                    i += 2
                }

                "duration", "dur" -> {
                    val v = tokens.getOrNull(i + 1)?.toFloatOrNull()
                    if (v != null) st = st.copy(animationDuration = v)
                    i += 2
                }

                "opacity" -> {
                    val v = tokens.getOrNull(i + 1)?.toFloatOrNull()
                    if (v != null) {
                        st = st.copy(opacity = v.coerceIn(0f, 100f))
                    }
                    i += 2
                }

                "position", "pos" -> {
                    val x = tokens.getOrNull(i + 1)?.toFloatOrNull()
                    val y = tokens.getOrNull(i + 2)?.toFloatOrNull()
                    if (x != null) st = st.copy(x = x.coerceIn(0f, 100f))
                    if (y != null) st = st.copy(y = y.coerceIn(0f, 100f))
                    i += 3
                }

                "scale", "size" -> {
                    val v = tokens.getOrNull(i + 1)?.toFloatOrNull()
                    if (v != null) {
                        st = st.copy(scale = v.coerceIn(10f, 500f))
                    }
                    i += 2
                }

                "rotation", "rotate" -> {
                    val v = tokens.getOrNull(i + 1)?.toFloatOrNull()
                    if (v != null) st = st.copy(rotation = v)
                    i += 2
                }

                else -> i++
            }
        }
        return st
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
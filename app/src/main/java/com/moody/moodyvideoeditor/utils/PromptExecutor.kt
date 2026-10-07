package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.BrushType
import com.moody.moodyvideoeditor.data.ChromaState
import com.moody.moodyvideoeditor.data.EffectLibrary
import com.moody.moodyvideoeditor.data.FilterState
import com.moody.moodyvideoeditor.data.RatioLibrary
import com.moody.moodyvideoeditor.data.StickerState
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

                CmdType.GRAPH -> {
                    val sel = selected
                    if (sel == null) {
                        errors.add("graph: select a clip first")
                    } else {
                        val kfs = sel.keyframes
                        val totalKfs = kfs.values.sumOf { it.size }
                        if (totalKfs == 0) {
                            applied.add("graph: no keyframes on selected clip")
                        } else {
                            val clipDurSec = sel.durationMs / 1000f
                            val sb = StringBuilder()
                            sb.append("graph: $totalKfs keyframe(s) on \"${sel.name.take(16)}\"")
                            sb.append(" (duration ${"%.2f".format(clipDurSec)}s)")
                            kfs.forEach { (prop, list) ->
                                if (list.isNotEmpty()) {
                                    val times = list.joinToString(", ") {
                                        "${"%.2f".format(it.time)}s"
                                    }
                                    sb.append("\n  • $prop: [${times}]")
                                }
                            }
                            val hasAny = KeyframeStore.hasAnyKeyframeAt(
                                kfs,
                                currentTimeSecForPrompt(state, sel)
                            )
                            sb.append("\n  • At playhead: ${if (hasAny) "keyframe exists" else "no keyframe"}")
                            applied.add(sb.toString())
                        }
                    }
                }

                CmdType.CLEAR_KEYFRAMES -> {
                    selected?.let {
                        viewModel.resetAllTransform()
                        applied.add("clear keyframes")
                    }
                }
                // 🆕 COLOR MATTE
                CmdType.COLOR_MATTE -> {
                    val colorLong = cmd.extra?.toLongOrNull()
                        ?: 0xFF0066FFL   // default blue

                    val opacity = cmd.value1 ?: 100f
                    val durationMs = cmd.value2?.toLong()
                        ?: 5000L

                    // Naya signature: style param with SOLID mode
                    val style = com.moody.moodyvideoeditor.data.ColorMatteStyle(
                        mode = com.moody.moodyvideoeditor.data.ColorMatteMode.SOLID,
                        solidColor = colorLong
                    )

                    viewModel.createColorMatte(
                        color = colorLong,
                        opacity = opacity,
                        durationMs = durationMs,
                        style = style
                    )

                    val hex = "#%06X".format(colorLong and 0xFFFFFF)
                    applied.add(
                        "color matte $hex opacity ${opacity.toInt()}% " +
                                "duration ${durationMs / 1000}s"
                    )
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
        //  PRIORITY 3 — TEXT + STICKER (smart auto-stack)
        // ═══════════════════════════════════════════════════════
        //  Rules:
        //  1. If user provided "position X Y" → RESPECT IT (no auto-stack)
        //  2. If user did NOT provide position → AUTO-STACK evenly
        //  3. Single text without position → centered (50, 50)
        //
        //  This matches manual typing behaviour when position is given,
        //  and keeps multi-text blocks readable when it's not.

        val textCommands = parsed.commands.filter { it.type == CmdType.TEXT }

        val groupedTexts = textCommands
            .filter { it.startMs != null && it.endMs != null }
            .groupBy { "${it.startMs}_${it.endMs}" }

        val autoStackPositions = mutableMapOf<ParsedCommand, Pair<Float, Float>>()

        for ((_, group) in groupedTexts) {
            if (group.isEmpty()) continue

            // Only the ones without explicit position
            val withoutPosition = group.filter { cmd ->
                !hasUserPosition(cmd)
            }

            if (withoutPosition.isEmpty()) continue

            val topMargin = 15f
            val bottomMargin = 85f
            val spacing = if (withoutPosition.size > 1)
                (bottomMargin - topMargin) / (withoutPosition.size - 1)
            else 50f

            withoutPosition.forEachIndexed { index, cmd ->
                val y = if (withoutPosition.size == 1) 50f
                else topMargin + index * spacing
                autoStackPositions[cmd] = 50f to y
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
                        // Apply auto-stack ONLY if user did not specify position
                        if (!hasUserPosition(cmd)) {
                            autoStackPositions[cmd]?.let { (x, y) ->
                                textState = textState.copy(
                                    positionX = x,
                                    positionY = y
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

                    var state2 = StickerState(
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

                    CmdType.FILTER -> run filterBlock@{
                        val target = selected
                        if (target == null) {
                            errors.add("filter: select a clip first")
                            return@filterBlock
                        }

                        // 🆕 FILTER PRESET — "preset:badbunny" form
                        if (cmd.key.startsWith("preset:")) {
                            val presetKey = cmd.key.removePrefix("preset:")
                            val preset = FilterState.findPreset(presetKey)
                            if (preset == null) {
                                errors.add("filter: preset '$presetKey' not found")
                            } else {
                                viewModel.updateFilters(preset.toFilterState())
                                applied.add("filter preset: ${preset.label}")
                            }
                        } else {
                            // Primitive filter — grayscale, sepia, invert, blur, hue
                            val value = cmd.value1
                            if (value == null) {
                                errors.add("filter: ${cmd.key} needs a value")
                                return@filterBlock
                            }
                            viewModel.updateFilters(
                                target.filters.set(cmd.key, value)
                            )
                            applied.add("${cmd.key} $value")
                        }
                    }

                    CmdType.EFFECT -> {
                        val preset = EffectLibrary
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

                    CmdType.TRANSITION -> run transitionBlock@{
                        val dur = cmd.value1 ?: 0.5f
                        val preset = TransitionLibrary.find(cmd.key)
                        if (preset == null) {
                            errors.add("transition '${cmd.key}' not found")
                            return@transitionBlock
                        }

                        val sel = selected
                        if (sel == null) {
                            errors.add("transition: select a clip first")
                            return@transitionBlock
                        }

                        // Check if there's an adjacent visual clip at the join
                        val hasAdjacent = state.clips.any { other ->
                            other.id != sel.id && other.isVisualClip &&
                                    other.trackIndex == sel.trackIndex &&
                                    abs(
                                        other.timelineEndMs - sel.timelineStartMs
                                    ) < 100L
                        }

                        viewModel.updateTransition(
                            TransitionState(
                                key = preset.key,
                                durationMs = (dur * 1000f).toLong()
                                    .coerceIn(200L, 3000L)
                            )
                        )

                        if (hasAdjacent) {
                            applied.add("transition ${preset.label} ${dur}s")
                        } else {
                            applied.add(
                                "transition ${preset.label} ${dur}s " +
                                        "(⚠️ no adjacent clip on this track)"
                            )
                        }
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

                    CmdType.AUDIO_FX -> run audioBlock@{
                        val sel = selected
                        if (sel == null) {
                            errors.add("audio: select a clip first")
                            return@audioBlock
                        }

                        val isSound = cmd.stringValue == "sound"
                        val fxKey = cmd.key.lowercase()
                        val intensity = cmd.value1

                        // ─── Handle "none" / "clear" → remove FX ───
                        if (fxKey == "none" || fxKey == "clear" || fxKey == "off") {
                            if (isSound) {
                                viewModel.setClipSoundFx(sel.id, "none")
                                viewModel.setClipSoundFxIntensity(sel.id, 100f)
                                applied.add("sound fx: removed")
                            } else {
                                viewModel.setClipAudioFx(sel.id, "none")
                                viewModel.setClipAudioFxIntensity(sel.id, 100f)
                                applied.add("audio fx: removed")
                            }
                            return@audioBlock
                        }

                        // ─── Validate FX name ───
                        val validKeys = if (isSound) {
                            AudioEngine.SOUND_FX.map { it.key }
                        } else {
                            AudioEngine.AUDIO_FX.map { it.key }
                        }

                        if (fxKey !in validKeys) {
                            errors.add(
                                "${if (isSound) "sound" else "audio"}: unknown FX '$fxKey'"
                            )
                            return@audioBlock
                        }

                        // ─── Apply to selected clip ───
                        val clampedIntensity = intensity?.coerceIn(0f, 200f)

                        if (isSound) {
                            viewModel.setClipSoundFx(sel.id, fxKey)
                            if (clampedIntensity != null) {
                                viewModel.setClipSoundFxIntensity(sel.id, clampedIntensity)
                            }
                            val label = AudioEngine.SOUND_FX
                                .firstOrNull { it.key == fxKey }?.label ?: fxKey
                            val suffix = clampedIntensity?.let { " @${it.toInt()}%" } ?: ""
                            applied.add("sound ${label}${suffix}")
                        } else {
                            viewModel.setClipAudioFx(sel.id, fxKey)
                            if (clampedIntensity != null) {
                                viewModel.setClipAudioFxIntensity(sel.id, clampedIntensity)
                            }
                            val label = AudioEngine.AUDIO_FX
                                .firstOrNull { it.key == fxKey }?.label ?: fxKey
                            val suffix = clampedIntensity?.let { " @${it.toInt()}%" } ?: ""
                            applied.add("audio ${label}${suffix}")
                        }
                    }

                    CmdType.BRUSH_GRADIENT -> {
                        if (cmd.key == "off") {
                            BrushConfigHolder.update {
                                it.copy(gradient = it.gradient.copy(enabled = false))
                            }
                            applied.add("brush gradient off")
                        } else {
                            val parts = cmd.stringValue?.split(",")
                                ?: emptyList()
                            if (parts.size >= 3) {
                                try {
                                    val color1 = 0xFF000000L or
                                            parts[0].removePrefix("#").toLong(16)
                                    val color2 = 0xFF000000L or
                                            parts[1].removePrefix("#").toLong(16)
                                    val hasMid = parts.size >= 4
                                    val color3 = if (hasMid) {
                                        0xFF000000L or
                                                parts[2].removePrefix("#").toLong(16)
                                    } else 0L
                                    val mode = parts.last()

                                    BrushConfigHolder.update { config ->
                                        config.copy(
                                            gradient = config.gradient.copy(
                                                enabled = true,
                                                color1 = color1,
                                                color2 = color2,
                                                color3 = color3,
                                                hasMid = hasMid,
                                                mode = mode
                                            )
                                        )
                                    }
                                    applied.add(
                                        "brush gradient ${parts[0]} → ${parts[1]}" +
                                                if (hasMid) " → ${parts[2]}" else ""
                                    )
                                } catch (e: Exception) {
                                    errors.add("brush gradient: invalid colors")
                                }
                            } else {
                                errors.add("brush gradient: need 2 colors")
                            }
                        }
                    }

                    CmdType.BRUSH_TYPE -> {
                        val brushType = when (cmd.key.lowercase()) {
                            "pen" -> BrushType.PEN
                            "marker" -> BrushType.MARKER
                            "chalk" -> BrushType.CHALK
                            "neon" -> BrushType.NEON
                            "glow" -> BrushType.GLOW
                            "spray" -> BrushType.SPRAY
                            else -> null
                        }
                        if (brushType == null) {
                            errors.add("brush: unknown type '${cmd.key}'")
                        } else {
                            BrushConfigHolder.update { config ->
                                var updated = config.copy(type = brushType)
                                cmd.value1?.let { w ->
                                    updated = updated.copy(
                                        width = w.coerceIn(1f, 200f)
                                    )
                                }
                                cmd.stringValue?.let { hex ->
                                    try {
                                        val colorLong =
                                            0xFF000000L or hex.removePrefix("#")
                                                .toLong(16)
                                        updated = updated.copy(color = colorLong)
                                    } catch (_: Exception) {
                                    }
                                }
                                updated
                            }
                            applied.add("brush type: ${cmd.key}")
                        }
                    }

                    CmdType.BRUSH_DRAW -> {
                        if (cmd.key == "on") {
                            // Ensure a brush clip exists
                            val hasBrush = viewModel.state.value.clips.any {
                                it.isBrushClip
                            }
                            if (!hasBrush) {
                                viewModel.createBrushClip()
                            }
                            BrushConfigHolder.update {
                                it.copy(isDrawingMode = true)
                            }
                            applied.add("brush draw enabled")
                        } else {
                            BrushConfigHolder.update {
                                it.copy(isDrawingMode = false)
                            }
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

                    CmdType.KEYFRAME -> run keyframeBlock@{
                        val sel = selected
                        if (sel == null) {
                            errors.add("keyframe: select a clip first")
                            return@keyframeBlock
                        }

                        val currentTimeSec = KeyframeStore.clipLocalTimeSeconds(
                            state.currentPosMs, sel.timelineStartMs, sel.durationMs
                        )

                        // ─── KEYFRAME ALL ───
                        if (cmd.key == "all") {
                            viewModel.toggleKeyframeAll()
                            val hasAny =
                                KeyframeStore.hasAnyKeyframeAt(sel.keyframes, currentTimeSec)
                            if (hasAny) {
                                applied.add("keyframe all @${"%.2f".format(currentTimeSec)}s (removed)")
                            } else {
                                applied.add("keyframe all @${"%.2f".format(currentTimeSec)}s (added)")
                            }
                            return@keyframeBlock
                        }

                        // ─── KEYFRAME PROP [VALUE] ───
                        val validProps = setOf(
                            "x", "y", "scale", "rotation",
                            "anchorX", "anchorY",
                            "cropL", "cropR", "cropT", "cropB"
                        )
                        if (cmd.key !in validProps) {
                            errors.add("keyframe: unknown property '${cmd.key}'")
                            return@keyframeBlock
                        }

                        val value = cmd.value1

                        if (value == null) {
                            // Toggle keyframe at playhead for that prop
                            viewModel.toggleKeyframeAtPlayhead(cmd.key)
                            val exists = KeyframeStore.hasKeyframeAt(
                                sel.keyframes, cmd.key, currentTimeSec
                            )
                            if (exists) {
                                applied.add(
                                    "keyframe ${cmd.key} removed @${
                                        "%.2f".format(
                                            currentTimeSec
                                        )
                                    }s"
                                )
                            } else {
                                applied.add(
                                    "keyframe ${cmd.key} added @${
                                        "%.2f".format(
                                            currentTimeSec
                                        )
                                    }s"
                                )
                            }
                        } else {
                            // ─── Set value AT playhead with keyframe (always creates/updates) ───

                            // Step 1: Ensure keyframe exists
                            val alreadyHasKf = KeyframeStore.hasKeyframeAt(
                                sel.keyframes, cmd.key, currentTimeSec
                            )
                            if (!alreadyHasKf) {
                                viewModel.toggleKeyframeAtPlayhead(cmd.key)
                            }

                            // Step 2: Update base + keyframe value
                            viewModel.changeTransformProperty(cmd.key, value)

                            // Step 3: Safety pass
                            val refreshed = viewModel.state.value.clips
                                .firstOrNull { it.id == sel.id }
                            if (refreshed != null) {
                                val kfValueAtTime = KeyframeStore.getKeyframes(
                                    refreshed.keyframes, cmd.key
                                ).firstOrNull { abs(it.time - currentTimeSec) < 0.05f }

                                if (kfValueAtTime != null &&
                                    abs(kfValueAtTime.value - value) > 0.01f
                                ) {
                                    viewModel.updateClipKeyframeValue(
                                        refreshed.id, cmd.key, currentTimeSec, value
                                    )
                                }
                            }

                            applied.add(
                                "keyframe ${cmd.key} = $value @${"%.2f".format(currentTimeSec)}s"
                            )
                        }
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
                                val p = resolveVisualizerPreset(cmd.stringValue ?: "")
                                when {
                                    p == null -> {
                                        errors.add("Unknown preset: ${cmd.stringValue}")
                                    }

                                    existingViz == null -> {
                                        errors.add(
                                            "visualizer: no visualizer layer — run 'visualizer add' first"
                                        )
                                    }

                                    else -> {
                                        viewModel.updateVisualizerLayer(
                                            existingViz.id,
                                            (existingViz.visualizer ?: VisualizerState())
                                                .copy(preset = p)
                                        )
                                        applied.add("visualizer preset ${p.label}")
                                    }
                                }
                            }

                            "color1", "color2" -> {
                                val hex = cmd.stringValue?.removePrefix("#")
                                val colorLong = hex?.let {
                                    try {
                                        0xFF000000L or it.toLong(16)
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                                if (existingViz != null && colorLong != null) {
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

    //  HELPERS
    private fun currentTimeSecForPrompt(
        state: com.moody.moodyvideoeditor.data.EditorState,
        clip: com.moody.moodyvideoeditor.data.EditorClip
    ): Float {
        return KeyframeStore.clipLocalTimeSeconds(
            state.currentPosMs, clip.timelineStartMs, clip.durationMs
        )
    }

    /**
     * Does this command have an explicit user position?
     * We look for "position", "pos", "posx", "posy", "x", "y" tokens.
     */
    private fun hasUserPosition(cmd: ParsedCommand): Boolean {
        val extra = cmd.extra ?: return false
        val lower = extra.lowercase()
        return lower.contains("position")
                || lower.contains("pos ")
                || lower.contains("posx")
                || lower.contains("posy")
                || Regex("""(^|\s)x\s+[\d.-]""").containsMatchIn(lower)
                || Regex("""(^|\s)y\s+[\d.-]""").containsMatchIn(lower)
    }

    //  LAYER CLIP TRANSITIONS PARSER

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
        // ═══════════════════════════════════════════════════════
        //  🆕 PRIORITY 1: Exact match by preset key (camelCase → lowercase)
        //  Handles: "neonGlowRing", "circularSpectrum", "audioSphere", etc.
        // ═══════════════════════════════════════════════════════
        VisualizerPreset.values().firstOrNull {
            it.key.lowercase() == clean
        }?.let { return it }

        // ═══════════════════════════════════════════════════════
        //  🆕 PRIORITY 2: Match by label (spaces removed)
        //  Handles: "Neon Glow Ring" → "neonglowring", "Audio Sphere" → "audiosphere"
        // ═══════════════════════════════════════════════════════
        VisualizerPreset.values().firstOrNull {
            it.label.lowercase()
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "") == clean
        }?.let { return it }

        // SPECTRUM
        val spectrumAliases = mapOf(
            "audisphere" to VisualizerPreset.AUDIO_SPHERE,
            "sphere" to VisualizerPreset.AUDIO_SPHERE,
            "waveformring" to VisualizerPreset.WAVEFORM_RING,
            "ring" to VisualizerPreset.WAVEFORM_RING,
            "symmetricwave" to VisualizerPreset.SYMMETRIC_WAVE,
            "symmetric" to VisualizerPreset.SYMMETRIC_WAVE,
            "mirrorwave" to VisualizerPreset.SYMMETRIC_WAVE,
            "circular" to VisualizerPreset.CIRCULAR_SPECTRUM,
            "circlespectrum" to VisualizerPreset.CIRCULAR_SPECTRUM,
            "linear" to VisualizerPreset.LINEAR_WAVEFORM,
            "waveform" to VisualizerPreset.LINEAR_WAVEFORM,
            "doublesided" to VisualizerPreset.DOUBLE_SIDED_BARS,
            "radial" to VisualizerPreset.RADIAL_BARS,
            "radialbars" to VisualizerPreset.RADIAL_BARS,
            "innerradial" to VisualizerPreset.INNER_RADIAL_BARS,
            "heartbeat" to VisualizerPreset.HEARTBEAT_WAVE,
            "ecgwave" to VisualizerPreset.HEARTBEAT_WAVE,
            "square" to VisualizerPreset.SQUARE_SPECTRUM,
            "triangle" to VisualizerPreset.TRIANGLE_BEATS,
            "hexagon" to VisualizerPreset.HEXAGON_PULSE,
            "dotmatrix" to VisualizerPreset.DOT_MATRIX,
            "dotsmatrix" to VisualizerPreset.DOT_MATRIX,
            "mirrored" to VisualizerPreset.MIRRORED_LINEAR,
            "glowwaves" to VisualizerPreset.GLOW_WAVES,
            "thick" to VisualizerPreset.THICK_BARS,
            "thickbars" to VisualizerPreset.THICK_BARS,
            "thin" to VisualizerPreset.THIN_STRINGS,
            "strings" to VisualizerPreset.THIN_STRINGS,
            "sine" to VisualizerPreset.SINE_WAVE,
            "3d" to VisualizerPreset.PERSPECTIVE_3D,
            "perspective" to VisualizerPreset.PERSPECTIVE_3D,
            "volcano" to VisualizerPreset.FREQUENCY_VOLCANO,
            "tornado" to VisualizerPreset.TORNADO_SPIRAL,
            "spiral" to VisualizerPreset.TORNADO_SPIRAL,
            "dualring" to VisualizerPreset.DUAL_RING,
            "star" to VisualizerPreset.STAR_BURST,
            "starburst" to VisualizerPreset.STAR_BURST
        )

        // PARTICLES
        val particleAliases = mapOf(
            "bassparticles" to VisualizerPreset.BASS_PARTICLES,
            "bass" to VisualizerPreset.BASS_PARTICLES,
            "dust" to VisualizerPreset.FLOATING_DUST,
            "floatingdust" to VisualizerPreset.FLOATING_DUST,
            "liquid" to VisualizerPreset.LIQUID_DROPS,
            "drops" to VisualizerPreset.LIQUID_DROPS,
            "firefly" to VisualizerPreset.FIREFLY_GLOW,
            "fireflies" to VisualizerPreset.FIREFLY_GLOW,
            "smoke" to VisualizerPreset.SMOKE_AURA,
            "smokeaura" to VisualizerPreset.SMOKE_AURA,
            "matrix" to VisualizerPreset.MATRIX_RAIN,
            "matrixrain" to VisualizerPreset.MATRIX_RAIN,
            "snow" to VisualizerPreset.SNOWFALL,
            "snowfall" to VisualizerPreset.SNOWFALL,
            "nebula" to VisualizerPreset.COSMIC_NEBULA,
            "cosmic" to VisualizerPreset.COSMIC_NEBULA,
            "spark" to VisualizerPreset.SPARK_TRAIL,
            "sparks" to VisualizerPreset.SPARK_TRAIL,
            "ink" to VisualizerPreset.INK_BLEED,
            "inkbleed" to VisualizerPreset.INK_BLEED,
            "sand" to VisualizerPreset.SAND_STORM,
            "sandstorm" to VisualizerPreset.SAND_STORM,
            "magic" to VisualizerPreset.MAGIC_DUST,
            "magicdust" to VisualizerPreset.MAGIC_DUST,
            "meteor" to VisualizerPreset.METEOR_SHOWER,
            "plasma" to VisualizerPreset.PLASMA_ORBS,
            "orbs" to VisualizerPreset.PLASMA_ORBS,
            "confetti" to VisualizerPreset.CONFETTI_POP,
            "bubbles" to VisualizerPreset.BUBBLES_POP,
            "electric" to VisualizerPreset.ELECTRIC_STORM,
            "storm" to VisualizerPreset.ELECTRIC_STORM,
            "disintegrate" to VisualizerPreset.DISINTEGRATION,
            "disintegration" to VisualizerPreset.DISINTEGRATION,
            "galaxy" to VisualizerPreset.GALAXY_VORTEX,
            "cybergrid" to VisualizerPreset.CYBER_GRID
        )

        // NEON
        val neonAliases = mapOf(
            "neon" to VisualizerPreset.NEON_GLOW_RING,
            "glow" to VisualizerPreset.NEON_GLOW_RING,
            "neonring" to VisualizerPreset.NEON_GLOW_RING,
            "neonglow" to VisualizerPreset.NEON_GLOW_RING,
            "rgb" to VisualizerPreset.RGB_GLITCH,
            "rgbglitch" to VisualizerPreset.RGB_GLITCH,
            "vaporwave" to VisualizerPreset.VAPORWAVE_GRID,
            "vapor" to VisualizerPreset.VAPORWAVE_GRID,
            "vhs" to VisualizerPreset.VHS_NOISE,
            "vhsnoise" to VisualizerPreset.VHS_NOISE,
            "laser" to VisualizerPreset.LASER_BEAM,
            "laserbeam" to VisualizerPreset.LASER_BEAM,
            "eq" to VisualizerPreset.DIGITAL_EQ,
            "digitaleq" to VisualizerPreset.DIGITAL_EQ,
            "chroma" to VisualizerPreset.CHROMA_PULSE,
            "chromapulse" to VisualizerPreset.CHROMA_PULSE,
            "scanline" to VisualizerPreset.SCANLINE_DISTORT,
            "tron" to VisualizerPreset.TRON_WIREFRAME,
            "tronwireframe" to VisualizerPreset.TRON_WIREFRAME,
            "led" to VisualizerPreset.LED_MATRIX,
            "ledmatrix" to VisualizerPreset.LED_MATRIX,
            "arcade" to VisualizerPreset.ARCADE_GAMEOVER,
            "lasertunnel" to VisualizerPreset.LASER_TUNNEL,
            "tracer" to VisualizerPreset.NEON_TRACER,
            "neontracer" to VisualizerPreset.NEON_TRACER,
            "pixel" to VisualizerPreset.PIXEL_DISSOLVE,
            "pixeldissolve" to VisualizerPreset.PIXEL_DISSOLVE,
            "ecg" to VisualizerPreset.ECG_GRID,
            "ecggrid" to VisualizerPreset.ECG_GRID,
            "synth" to VisualizerPreset.SYNTH_SUN,
            "synthsun" to VisualizerPreset.SYNTH_SUN,
            "hologram" to VisualizerPreset.HOLOGRAM,
            "crt" to VisualizerPreset.CRT_FLICKER,
            "crtflicker" to VisualizerPreset.CRT_FLICKER,
            "vector" to VisualizerPreset.VECTOR_WAVE,
            "vectorwave" to VisualizerPreset.VECTOR_WAVE,
            "glitch" to VisualizerPreset.GLITCH_TWITCH,
            "glitchtwitch" to VisualizerPreset.GLITCH_TWITCH
        )

        // GEOMETRIC
        val geometricAliases = mapOf(
            "minimal" to VisualizerPreset.MINIMAL_DOTS,
            "dots" to VisualizerPreset.MINIMAL_DOTS,
            "minimaldots" to VisualizerPreset.MINIMAL_DOTS,
            "poly" to VisualizerPreset.ROTATING_POLY,
            "rotatingpoly" to VisualizerPreset.ROTATING_POLY,
            "kaleidoscope" to VisualizerPreset.KALEIDOSCOPE,
            "interlocking" to VisualizerPreset.INTERLOCKING_RINGS,
            "rings" to VisualizerPreset.INTERLOCKING_RINGS,
            "expanding" to VisualizerPreset.EXPANDING_SQUARES,
            "squares" to VisualizerPreset.EXPANDING_SQUARES,
            "origami" to VisualizerPreset.ORIGAMI,
            "fractal" to VisualizerPreset.FRACTAL_ZOOM,
            "fractalzoom" to VisualizerPreset.FRACTAL_ZOOM,
            "parallax" to VisualizerPreset.PARALLAX_LINES,
            "isometric" to VisualizerPreset.ISOMETRIC_BLOCKS,
            "blocks" to VisualizerPreset.ISOMETRIC_BLOCKS,
            "mirror" to VisualizerPreset.SYMMETRIC_MIRROR,
            "symmetric" to VisualizerPreset.SYMMETRIC_MIRROR,
            "crosshair" to VisualizerPreset.CROSSHAIR,
            "target" to VisualizerPreset.CROSSHAIR,
            "dna" to VisualizerPreset.DNA_STRAND,
            "dnastrand" to VisualizerPreset.DNA_STRAND,
            "concentric" to VisualizerPreset.CONCENTRIC_RINGS,
            "shards" to VisualizerPreset.FLOATING_SHARDS,
            "infinitetunnel" to VisualizerPreset.INFINITE_TUNNEL,
            "morph" to VisualizerPreset.SHAPE_MORPH,
            "shapemorph" to VisualizerPreset.SHAPE_MORPH,
            "gyroscope" to VisualizerPreset.GYROSCOPE,
            "gyro" to VisualizerPreset.GYROSCOPE,
            "diagonal" to VisualizerPreset.SPLIT_DIAGONAL,
            "splitdiagonal" to VisualizerPreset.SPLIT_DIAGONAL,
            "checker" to VisualizerPreset.CHECKERBOARD,
            "checkerboard" to VisualizerPreset.CHECKERBOARD,
            "ribbon" to VisualizerPreset.VECTOR_RIBBON,
            "vectorribbon" to VisualizerPreset.VECTOR_RIBBON
        )

        // CINEMATIC
        val cinematicAliases = mapOf(
            "lensflare" to VisualizerPreset.LENS_FLARE,
            "flare" to VisualizerPreset.LENS_FLARE,
            "shutter" to VisualizerPreset.CAMERA_SHUTTER,
            "camerashutter" to VisualizerPreset.CAMERA_SHUTTER,
            "cinematicdust" to VisualizerPreset.CINEMATIC_DUST,
            "vignette" to VisualizerPreset.VIGNETTE_BREATHE,
            "vignettebreathe" to VisualizerPreset.VIGNETTE_BREATHE,
            "blur" to VisualizerPreset.BLUR_DISSOLVE,
            "blurdissolve" to VisualizerPreset.BLUR_DISSOLVE,
            "sunbeams" to VisualizerPreset.SUNBEAMS,
            "beams" to VisualizerPreset.SUNBEAMS,
            "rain" to VisualizerPreset.RAINDROPS,
            "raindrops" to VisualizerPreset.RAINDROPS,
            "grain" to VisualizerPreset.FILM_GRAIN,
            "filmgrain" to VisualizerPreset.FILM_GRAIN,
            "lightleak" to VisualizerPreset.LIGHT_LEAK,
            "fog" to VisualizerPreset.FOGGY_AMBIANCE,
            "foggy" to VisualizerPreset.FOGGY_AMBIANCE,
            "bokeh" to VisualizerPreset.BOKEH_DRIFT,
            "shadow" to VisualizerPreset.SHADOW_WAVE,
            "ripple" to VisualizerPreset.WATER_RIPPLE,
            "waterripple" to VisualizerPreset.WATER_RIPPLE,
            "cloudy" to VisualizerPreset.CLOUDY_TIMELAPSE,
            "lightstreak" to VisualizerPreset.LIGHT_STREAK,
            "streak" to VisualizerPreset.LIGHT_STREAK,
            "countdown" to VisualizerPreset.VINTAGE_COUNTDOWN,
            "vintage" to VisualizerPreset.VINTAGE_COUNTDOWN,
            "golden" to VisualizerPreset.GOLDEN_HOUR,
            "goldenhour" to VisualizerPreset.GOLDEN_HOUR,
            "prism" to VisualizerPreset.PRISM_RAINBOW,
            "rainbow" to VisualizerPreset.PRISM_RAINBOW,
            "shake" to VisualizerPreset.CAMERA_SHAKE,
            "camerashake" to VisualizerPreset.CAMERA_SHAKE,
            "horizon" to VisualizerPreset.HORIZON_ZOOM,
            "horizonzoom" to VisualizerPreset.HORIZON_ZOOM
        )

        val allAliases = spectrumAliases + particleAliases +
                neonAliases + geometricAliases + cinematicAliases

        allAliases[clean]?.let { return it }

        VisualizerPreset.values().firstOrNull {
            it.key.lowercase() == clean
        }?.let { return it }

        VisualizerPreset.values().firstOrNull {
            it.label.lowercase().replace(" ", "") == clean
        }?.let { return it }

        // Short aliases fallback
        val aliases = mapOf(
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
            "sphere" to VisualizerPreset.AUDIO_SPHERE,
            "audioshpere" to VisualizerPreset.AUDIO_SPHERE,
            "waveformring" to VisualizerPreset.WAVEFORM_RING,
            "symmetric" to VisualizerPreset.SYMMETRIC_WAVE
        )

        aliases[clean]?.let { return it }

        // Fuzzy contains fallback
        VisualizerPreset.values().firstOrNull {
            val k = it.key.lowercase()
            val l = it.label.lowercase().replace(" ", "")
            k.contains(clean) || clean.contains(k) ||
                    l.contains(clean) || clean.contains(l)
        }?.let { return it }

        return null
    }

    //  TEXT PROPS PARSER

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

    //  STICKER PROPS PARSER

    private fun applyStickerProps(
        initial: StickerState,
        props: String
    ): StickerState {
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

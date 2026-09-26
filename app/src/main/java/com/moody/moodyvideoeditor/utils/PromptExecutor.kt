package com.moody.moodyvideoeditor.utils

import android.util.Log
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
        //  PRIORITY 3 — TEXT + STICKER (timestamp-aware)
        // ═══════════════════════════════════════════════════════
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

                    Log.d(
                        TAG,
                        "TEXT cmd: content='$content' startMs=$startMs endMs=$endMs"
                    )

                    if (startMs != null && endMs != null) {
                        // 🆕 Timestamped → strict time-based placement
                        viewModel.createTextClipAtTime(
                            textState = textState,
                            startMs = startMs,
                            endMs = endMs
                        )
                        applied.add(
                            "text @${startMs / 1000}s-${endMs / 1000}s \"$content\""
                        )
                    } else {
                        // Manual — use playhead
                        viewModel.createTextClip(textState)
                        applied.add("text \"$content\"")
                    }
                }

                CmdType.STICKER -> {
                    val emoji = cmd.stringValue ?: continue
                    val startMs = cmd.startMs
                    val endMs = cmd.endMs

                    Log.d(
                        TAG,
                        "STICKER cmd: emoji='$emoji' startMs=$startMs endMs=$endMs"
                    )

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
                        when (cmd.key) {
                            "scale" -> viewModel.setClipScale(value / 100f)
                            "rotation" -> viewModel.setClipRotation(value)
                            "positionx" -> viewModel.setClipOffset(
                                (value - 50f) / 100f, selected?.offsetY ?: 0f
                            )

                            "positiony" -> viewModel.setClipOffset(
                                selected?.offsetX ?: 0f, (value - 50f) / 100f
                            )
                        }
                        applied.add("${cmd.key} $value")
                    }

                    CmdType.TRIM -> {
                        when (cmd.key) {
                            "left" -> viewModel.trimLeft()
                            "right" -> viewModel.trimRight()
                            "split" -> viewModel.splitCurrentClip()
                        }
                        applied.add("trim ${cmd.key}")
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
                    if (c != null && c.startsWith("#")) {
                        try {
                            val rgb = c.removePrefix("#").toLong(16)
                            st = st.copy(color = 0xFF000000L or rgb)
                        } catch (_: Exception) {
                        }
                    }
                    i += 2
                }

                "animation" -> {
                    val a = tokens.getOrNull(i + 1)
                    if (a != null) st = st.copy(animation = PromptEngine.animationKey(a))
                    i += 2
                }

                "font" -> {
                    // 🆕 Support quoted or unquoted font name
                    var fontName = ""
                    if (tokens.getOrNull(i + 1)?.startsWith("\"") == true) {
                        // quoted font — collect until closing quote
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

                "position" -> {
                    // 🆕 position X Y  (e.g. position 50 30)
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
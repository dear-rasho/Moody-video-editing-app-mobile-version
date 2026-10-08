package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectState
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectType
import com.moody.moodyvideoeditor.data.advanced.models.BlurDimension
import com.moody.moodyvideoeditor.data.advanced.models.ChromaticAberrationEffect
import com.moody.moodyvideoeditor.data.advanced.models.DropShadowEffect
import com.moody.moodyvideoeditor.data.advanced.models.FourColorGradientEffect
import com.moody.moodyvideoeditor.data.advanced.models.GaussianBlurEffect
import com.moody.moodyvideoeditor.data.advanced.models.GradientBlendMode
import com.moody.moodyvideoeditor.data.advanced.models.MirrorEffect
import com.moody.moodyvideoeditor.data.advanced.models.MotionBlurEffect
import com.moody.moodyvideoeditor.data.advanced.models.RoughenEdgesEffect
import com.moody.moodyvideoeditor.data.advanced.models.RoundedCropEffect
import com.moody.moodyvideoeditor.data.advanced.models.TrackMatteEffect
import com.moody.moodyvideoeditor.data.advanced.models.TrackMatteType
import com.moody.moodyvideoeditor.data.advanced.models.TurbulentDisplaceEffect
import com.moody.moodyvideoeditor.ui.components.ColorPickerField
import com.moody.moodyvideoeditor.ui.components.EffectPropertyRow
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.KeyframeStore

// ═══════════════════════════════════════════════════════════════
//  AUTO-KEYFRAME HELPER
//  Premiere Pro style:
//  - No keyframes on prop       → change base value
//  - Keyframes exist on prop    → add/update keyframe at playhead
//  - Toggle diamond             → add/remove keyframe at playhead
// ═══════════════════════════════════════════════════════════════

private object KfOps {
    fun hasKf(kfs: Map<String, List<Keyframe>>, prop: String, t: Float): Boolean =
        KeyframeStore.hasKeyframeAt(kfs, prop, t)

    fun hasAnyKf(kfs: Map<String, List<Keyframe>>, prop: String): Boolean =
        KeyframeStore.getKeyframes(kfs, prop).isNotEmpty()

    fun sample(kfs: Map<String, List<Keyframe>>, prop: String, t: Float, base: Float): Float =
        KeyframeStore.sample(kfs, prop, t, base)

    fun toggle(
        kfs: Map<String, List<Keyframe>>,
        prop: String,
        t: Float,
        currentValue: Float
    ): Map<String, List<Keyframe>> = if (hasKf(kfs, prop, t)) {
        KeyframeStore.removeKeyframe(kfs, prop, t)
    } else {
        KeyframeStore.setKeyframe(kfs, prop, t, currentValue)
    }

    fun autoWrite(
        kfs: Map<String, List<Keyframe>>,
        prop: String,
        t: Float,
        value: Float
    ): Map<String, List<Keyframe>> =
        KeyframeStore.autoKeyframeIfActive(kfs, prop, t, value)
}

// ═══════════════════════════════════════════════════════════════
//  MAIN PANEL
// ═══════════════════════════════════════════════════════════════

@Composable
fun AdvancedEffectsPanel(
    selectedClip: EditorClip?,
    currentTimeSec: Float,
    onAddEffect: (AdvancedEffectState) -> Unit,
    onUpdateEffect: (index: Int, AdvancedEffectState) -> Unit,
    onRemoveEffect: (index: Int) -> Unit,
    onLivePreview: (AdvancedEffectState?) -> Unit,
    onClose: () -> Unit
) {
    var selectedType by remember { mutableStateOf(AdvancedEffectType.MIRROR) }
    var editingIndex by remember { mutableIntStateOf(-1) }

    val existingEffects = selectedClip?.advancedEffects ?: emptyList()

    fun defaultFor(type: AdvancedEffectType) =
        AdvancedEffectState.withDefaults(type)

    var workingState by remember {
        mutableStateOf(defaultFor(AdvancedEffectType.MIRROR))
    }
    var lastCommittedState by remember {
        mutableStateOf(defaultFor(AdvancedEffectType.MIRROR))
    }

    LaunchedEffect(editingIndex, selectedClip?.id) {
        if (editingIndex in existingEffects.indices) {
            val fx = existingEffects[editingIndex]
            workingState = fx
            lastCommittedState = fx
            selectedType = fx.type
        } else {
            val def = defaultFor(selectedType)
            workingState = def
            lastCommittedState = def
        }
        // Clear preview when switching mode
        onLivePreview(null)
    }

    LaunchedEffect(workingState, editingIndex) {
        // No-op if nothing changed
        if (workingState == lastCommittedState) return@LaunchedEffect

        if (editingIndex >= 0) {
            // EDIT MODE: directly update the actual clip (live edit)
            onUpdateEffect(editingIndex, workingState)
            onLivePreview(null)
        } else {
            // ADD MODE: just preview (append to clip)
            onLivePreview(workingState)
        }
    }
    FeaturePanel(
        title = "✨ Advanced Effects",
        onClose = {
            onLivePreview(null)
            onClose()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ─── No clip ───
            if (selectedClip == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A0F0F))
                        .padding(12.dp)
                ) {
                    Text(
                        "⚠️ Select a clip on the timeline first",
                        color = Color(0xFFFF6B6B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                return@FeaturePanel
            }

            // ─── Target info ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F1A2A))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "🎯 ${selectedClip.name.take(28)}  ·  " +
                                "type: ${selectedClip.type}",
                        color = Color(0xFF60EFFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Playhead: ${"%.2f".format(currentTimeSec)}s " +
                                "(clip-local). Effects apply to this clip.",
                        color = Color(0xFF888888),
                        fontSize = 9.sp
                    )
                }
            }

            // ─── Applied list ───
            if (existingEffects.isNotEmpty()) {
                Text(
                    "APPLIED (${existingEffects.size})",
                    color = Color(0xFF4F9DFF),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )

                existingEffects.forEachIndexed { idx, fx ->
                    val isEditing = editingIndex == idx
                    val kfCount = fx.keyframes().values.sumOf { it.size }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isEditing) Color(0xFF2A1F4D)
                                else Color(0xFF181818)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(fx.type.icon, fontSize = 16.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                fx.type.label,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${fx.type.category} · $kfCount kf",
                                color = Color(0xFF888888),
                                fontSize = 9.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF7C3AED).copy(alpha = 0.25f))
                                .pointerInput(idx) {
                                    detectTapGestures { editingIndex = idx }
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isEditing) "✎ Editing" else "✎ Edit",
                                color = Color(0xFFA78BFA),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                                .pointerInput(idx) {
                                    detectTapGestures {
                                        onRemoveEffect(idx)
                                        if (editingIndex == idx) editingIndex = -1
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✕",
                                color = Color(0xFFFF6B6B),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }

            // ─── Back to add ───
            if (editingIndex >= 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF181818))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                editingIndex = -1
                                workingState = defaultFor(selectedType)
                            }
                        }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "← Back to Add New Effect",
                        color = Color(0xFFA78BFA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    "ADD NEW EFFECT",
                    color = Color(0xFF4F9DFF),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }

            // ─── Type selector ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AdvancedEffectType.values().forEach { type ->
                    val isActive = type == selectedType
                    Column(
                        modifier = Modifier
                            .width(72.dp)
                            .height(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isActive) Color(0xFF2A1F4D)
                                else Color(0xFF181818)
                            )
                            .pointerInput(type) {
                                detectTapGestures {
                                    selectedType = type
                                    workingState = defaultFor(type)
                                }
                            }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(type.icon, fontSize = 18.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            type.label,
                            color = if (isActive) Color.White else Color(0xFFAAAAAA),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ─── Properties ───
            AdvancedEffectPropertiesList(
                effect = workingState,
                currentTimeSec = currentTimeSec,
                onChange = { workingState = it }
            )

            Spacer(Modifier.height(4.dp))

            // ─── Actions ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (editingIndex >= 0) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF181818))
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    editingIndex = -1
                                    workingState = defaultFor(selectedType)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "✕ Cancel",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(if (editingIndex >= 0) 2f else 1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF7C3AED))
                        .pointerInput(editingIndex) {
                            detectTapGestures {
                                if (editingIndex >= 0) {
                                    onUpdateEffect(editingIndex, workingState)
                                } else {
                                    onAddEffect(workingState)
                                }
                                onLivePreview(null)
                                lastCommittedState = workingState
                                editingIndex = -1
                                workingState = defaultFor(selectedType)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (editingIndex >= 0) "✓ Update Effect"
                        else "➕ Apply to Clip",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                "💡 Diamond (◆) = keyframe at playhead. " +
                        "Slider change → auto-keyframe if keyframes exist.",
                color = Color(0xFF666666),
                fontSize = 9.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  DISPATCH
// ═══════════════════════════════════════════════════════════════

@Composable
private fun AdvancedEffectPropertiesList(
    effect: AdvancedEffectState,
    currentTimeSec: Float,
    onChange: (AdvancedEffectState) -> Unit
) {
    when (effect.type) {
        AdvancedEffectType.MIRROR -> {
            val m = effect.mirror ?: return
            MirrorProperties(m, currentTimeSec) {
                onChange(effect.copy(mirror = it))
            }
        }

        AdvancedEffectType.GAUSSIAN_BLUR -> {
            val b = effect.gaussianBlur ?: return
            GaussianBlurProperties(b, currentTimeSec) {
                onChange(effect.copy(gaussianBlur = it))
            }
        }

        AdvancedEffectType.ROUGHEN_EDGES -> {
            val r = effect.roughenEdges ?: return
            RoughenEdgesProperties(r, currentTimeSec) {
                onChange(effect.copy(roughenEdges = it))
            }
        }

        AdvancedEffectType.ROUNDED_CROP -> {
            val r = effect.roundedCrop ?: return
            RoundedCropProperties(r, currentTimeSec) {
                onChange(effect.copy(roundedCrop = it))
            }
        }

        AdvancedEffectType.FOUR_COLOR_GRADIENT -> {
            val g = effect.fourColorGradient ?: return
            FourColorGradientProperties(g, currentTimeSec) {
                onChange(effect.copy(fourColorGradient = it))
            }
        }

        AdvancedEffectType.DROP_SHADOW -> {
            val s = effect.dropShadow ?: return
            DropShadowProperties(s, currentTimeSec) {
                onChange(effect.copy(dropShadow = it))
            }
        }

        AdvancedEffectType.TURBULENT_DISPLACE -> {
            val t = effect.turbulentDisplace ?: return
            TurbulentDisplaceProperties(t, currentTimeSec) {
                onChange(effect.copy(turbulentDisplace = it))
            }
        }

        AdvancedEffectType.CHROMATIC_ABERRATION -> {
            val c = effect.chromaticAberration ?: return
            ChromaticAberrationProperties(c, currentTimeSec) {
                onChange(effect.copy(chromaticAberration = it))
            }
        }

        AdvancedEffectType.MOTION_BLUR -> {
            val m = effect.motionBlur ?: return
            MotionBlurProperties(m, currentTimeSec) {
                onChange(effect.copy(motionBlur = it))
            }
        }

        AdvancedEffectType.TRACK_MATTE -> {
            val t = effect.trackMatte ?: return
            TrackMatteProperties(t, currentTimeSec) {
                onChange(effect.copy(trackMatte = it))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Color(0xFF4F9DFF),
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
    )
}

// ═══════════════════════════════════════════════════════════════
//  1. MIRROR
// ═══════════════════════════════════════════════════════════════

@Composable
private fun MirrorProperties(
    effect: MirrorEffect,
    currentTimeSec: Float,
    onChange: (MirrorEffect) -> Unit
) {
    val kfs = effect.keyframes

    fun hasKf(p: String) = KfOps.hasKf(kfs, p, currentTimeSec)
    fun hasAny(p: String) = KfOps.hasAnyKf(kfs, p)
    fun sample(p: String, base: Float) = KfOps.sample(kfs, p, currentTimeSec, base)

    fun toggle(p: String, v: Float) {
        onChange(effect.copy(keyframes = KfOps.toggle(kfs, p, currentTimeSec, v)))
    }

    fun writeBase(p: String, v: Float, baseUpdate: (MirrorEffect) -> MirrorEffect) {
        onChange(
            if (hasAny(p)) baseUpdate(effect)
                .copy(keyframes = KfOps.autoWrite(kfs, p, currentTimeSec, v))
            else baseUpdate(effect)
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("REFLECTION CENTER")
        EffectPropertyRow(
            label = "Center X", value = sample("centerX", effect.centerX),
            range = 0f..1f, decimals = 3,
            hasKeyframe = hasKf("centerX"), hasAnyKeyframe = hasAny("centerX"),
            onValueChange = { v -> writeBase("centerX", v) { it.copy(centerX = v) } },
            onToggleKeyframe = { toggle("centerX", sample("centerX", effect.centerX)) }
        )
        EffectPropertyRow(
            label = "Center Y", value = sample("centerY", effect.centerY),
            range = 0f..1f, decimals = 3,
            hasKeyframe = hasKf("centerY"), hasAnyKeyframe = hasAny("centerY"),
            onValueChange = { v -> writeBase("centerY", v) { it.copy(centerY = v) } },
            onToggleKeyframe = { toggle("centerY", sample("centerY", effect.centerY)) }
        )
        SectionLabel("REFLECTION ANGLE")
        EffectPropertyRow(
            label = "Angle", value = sample("angleDeg", effect.angleDeg),
            range = 0f..360f, decimals = 1, unit = "°",
            hasKeyframe = hasKf("angleDeg"), hasAnyKeyframe = hasAny("angleDeg"),
            onValueChange = { v -> writeBase("angleDeg", v) { it.copy(angleDeg = v) } },
            onToggleKeyframe = { toggle("angleDeg", sample("angleDeg", effect.angleDeg)) }
        )
        EffectPropertyRow(
            label = "Opacity", value = sample("opacity", effect.opacity),
            range = 0f..100f, decimals = 1, unit = "%",
            hasKeyframe = hasKf("opacity"), hasAnyKeyframe = hasAny("opacity"),
            onValueChange = { v -> writeBase("opacity", v) { it.copy(opacity = v) } },
            onToggleKeyframe = { toggle("opacity", sample("opacity", effect.opacity)) }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  2. GAUSSIAN BLUR
// ═══════════════════════════════════════════════════════════════

@Composable
private fun GaussianBlurProperties(
    effect: GaussianBlurEffect,
    currentTimeSec: Float,
    onChange: (GaussianBlurEffect) -> Unit
) {
    val kfs = effect.keyframes
    fun hasKf(p: String) = KfOps.hasKf(kfs, p, currentTimeSec)
    fun hasAny(p: String) = KfOps.hasAnyKf(kfs, p)
    fun sample(p: String, base: Float) = KfOps.sample(kfs, p, currentTimeSec, base)
    fun toggle(p: String, v: Float) {
        onChange(effect.copy(keyframes = KfOps.toggle(kfs, p, currentTimeSec, v)))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("BLUR")
        EffectPropertyRow(
            label = "Blurriness", value = sample("blurriness", effect.blurriness),
            range = 0f..1000f, decimals = 1, unit = "px",
            hasKeyframe = hasKf("blurriness"), hasAnyKeyframe = hasAny("blurriness"),
            onValueChange = { v ->
                onChange(
                    if (hasAny("blurriness")) effect.copy(blurriness = v)
                        .copy(keyframes = KfOps.autoWrite(kfs, "blurriness", currentTimeSec, v))
                    else effect.copy(blurriness = v)
                )
            },
            onToggleKeyframe = { toggle("blurriness", sample("blurriness", effect.blurriness)) }
        )

        SectionLabel("DIMENSION")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BlurDimension.values().forEach { dim ->
                val isActive = effect.dimension == dim
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                        .pointerInput(dim) { detectTapGestures { onChange(effect.copy(dimension = dim)) } }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when (dim) {
                            BlurDimension.BOTH -> "Both"
                            BlurDimension.HORIZONTAL -> "Horizontal"
                            BlurDimension.VERTICAL -> "Vertical"
                        },
                        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  3. ROUGHEN EDGES
// ═══════════════════════════════════════════════════════════════

@Composable
private fun RoughenEdgesProperties(
    effect: RoughenEdgesEffect,
    currentTimeSec: Float,
    onChange: (RoughenEdgesEffect) -> Unit
) {
    val kfs = effect.keyframes
    fun hasKf(p: String) = KfOps.hasKf(kfs, p, currentTimeSec)
    fun hasAny(p: String) = KfOps.hasAnyKf(kfs, p)
    fun sample(p: String, base: Float) = KfOps.sample(kfs, p, currentTimeSec, base)
    fun toggle(p: String, v: Float) {
        onChange(effect.copy(keyframes = KfOps.toggle(kfs, p, currentTimeSec, v)))
    }

    @Composable
    fun Row1(
        label: String, prop: String, base: Float,
        range: ClosedFloatingPointRange<Float>, decimals: Int, unit: String = "",
        update: (RoughenEdgesEffect, Float) -> RoughenEdgesEffect
    ) {
        EffectPropertyRow(
            label = label, value = sample(prop, base), range = range,
            decimals = decimals, unit = unit,
            hasKeyframe = hasKf(prop), hasAnyKeyframe = hasAny(prop),
            onValueChange = { v ->
                val next = update(effect, v)
                onChange(
                    if (hasAny(prop))
                        next.copy(keyframes = KfOps.autoWrite(kfs, prop, currentTimeSec, v))
                    else next
                )
            },
            onToggleKeyframe = { toggle(prop, sample(prop, base)) }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("BORDER")
        Row1("Width", "borderWidth", effect.borderWidth, 0f..500f, 1, "px") { e, v ->
            e.copy(
                borderWidth = v
            )
        }
        Row1("Sharpness", "edgeSharpness", effect.edgeSharpness, 0f..100f, 1) { e, v ->
            e.copy(
                edgeSharpness = v
            )
        }
        SectionLabel("FRACTAL")
        Row1("Scale", "fractalScale", effect.fractalScale, 20f..1000f, 1) { e, v ->
            e.copy(
                fractalScale = v
            )
        }
        Row1("Evolution", "evolution", effect.evolution, 0f..360f, 1, "°") { e, v ->
            e.copy(
                evolution = v
            )
        }
        Row1(
            "Complexity",
            "complexity",
            effect.complexity,
            1f..10f,
            1
        ) { e, v -> e.copy(complexity = v) }
        Row1(
            "Seed",
            "randomSeed",
            effect.randomSeed,
            0f..9999f,
            0
        ) { e, v -> e.copy(randomSeed = v) }
    }
}

// ═══════════════════════════════════════════════════════════════
//  4. ROUNDED CROP
// ═══════════════════════════════════════════════════════════════

@Composable
private fun RoundedCropProperties(
    effect: RoundedCropEffect,
    currentTimeSec: Float,
    onChange: (RoundedCropEffect) -> Unit
) {
    val kfs = effect.keyframes
    fun hasKf(p: String) = KfOps.hasKf(kfs, p, currentTimeSec)
    fun hasAny(p: String) = KfOps.hasAnyKf(kfs, p)
    fun sample(p: String, base: Float) = KfOps.sample(kfs, p, currentTimeSec, base)
    fun toggle(p: String, v: Float) {
        onChange(effect.copy(keyframes = KfOps.toggle(kfs, p, currentTimeSec, v)))
    }

    @Composable
    fun R(
        label: String, prop: String, base: Float,
        range: ClosedFloatingPointRange<Float>, decimals: Int, unit: String = "",
        update: (RoundedCropEffect, Float) -> RoundedCropEffect
    ) {
        EffectPropertyRow(
            label = label, value = sample(prop, base), range = range,
            decimals = decimals, unit = unit,
            hasKeyframe = hasKf(prop), hasAnyKeyframe = hasAny(prop),
            onValueChange = { v ->
                val next = update(effect, v)
                onChange(
                    if (hasAny(prop))
                        next.copy(keyframes = KfOps.autoWrite(kfs, prop, currentTimeSec, v))
                    else next
                )
            },
            onToggleKeyframe = { toggle(prop, sample(prop, base)) }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("CORNER")
        R("Radius", "cornerRadius", effect.cornerRadius, 0f..500f, 1, "px") { e, v ->
            e.copy(
                cornerRadius = v
            )
        }
        SectionLabel("CROP (0.0 - 0.5)")
        R("Top", "cropTop", effect.cropTop, 0f..0.5f, 3) { e, v -> e.copy(cropTop = v) }
        R("Bottom", "cropBottom", effect.cropBottom, 0f..0.5f, 3) { e, v -> e.copy(cropBottom = v) }
        R("Left", "cropLeft", effect.cropLeft, 0f..0.5f, 3) { e, v -> e.copy(cropLeft = v) }
        R("Right", "cropRight", effect.cropRight, 0f..0.5f, 3) { e, v -> e.copy(cropRight = v) }
        SectionLabel("EDGE")
        R("Feathering", "feathering", effect.feathering, 0f..200f, 1, "px") { e, v ->
            e.copy(
                feathering = v
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  5. 4-COLOR GRADIENT
// ═══════════════════════════════════════════════════════════════

@Composable
private fun FourColorGradientProperties(
    effect: FourColorGradientEffect,
    currentTimeSec: Float,
    onChange: (FourColorGradientEffect) -> Unit
) {
    val kfs = effect.keyframes
    fun hasKf(p: String) = KfOps.hasKf(kfs, p, currentTimeSec)
    fun hasAny(p: String) = KfOps.hasAnyKf(kfs, p)
    fun sample(p: String, base: Float) = KfOps.sample(kfs, p, currentTimeSec, base)
    fun toggle(p: String, v: Float) {
        onChange(effect.copy(keyframes = KfOps.toggle(kfs, p, currentTimeSec, v)))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("CORNER COLORS")
        ColorPickerField("Top Left", effect.color1) { onChange(effect.copy(color1 = it)) }
        ColorPickerField("Top Right", effect.color2) { onChange(effect.copy(color2 = it)) }
        ColorPickerField("Bot Right", effect.color3) { onChange(effect.copy(color3 = it)) }
        ColorPickerField("Bot Left", effect.color4) { onChange(effect.copy(color4 = it)) }

        SectionLabel("BLEND MODE")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GradientBlendMode.values().forEach { mode ->
                val isActive = effect.blendMode == mode
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                        .pointerInput(mode) { detectTapGestures { onChange(effect.copy(blendMode = mode)) } }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        mode.key.replaceFirstChar { it.uppercase() },
                        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        SectionLabel("OPACITY")
        EffectPropertyRow(
            label = "Global", value = sample("globalOpacity", effect.globalOpacity),
            range = 0f..100f, decimals = 1, unit = "%",
            hasKeyframe = hasKf("globalOpacity"), hasAnyKeyframe = hasAny("globalOpacity"),
            onValueChange = { v ->
                onChange(
                    if (hasAny("globalOpacity")) effect.copy(globalOpacity = v)
                        .copy(keyframes = KfOps.autoWrite(kfs, "globalOpacity", currentTimeSec, v))
                    else effect.copy(globalOpacity = v)
                )
            },
            onToggleKeyframe = {
                toggle(
                    "globalOpacity",
                    sample("globalOpacity", effect.globalOpacity)
                )
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  6. DROP SHADOW
// ═══════════════════════════════════════════════════════════════

@Composable
private fun DropShadowProperties(
    effect: DropShadowEffect,
    currentTimeSec: Float,
    onChange: (DropShadowEffect) -> Unit
) {
    val kfs = effect.keyframes
    fun hasKf(p: String) = KfOps.hasKf(kfs, p, currentTimeSec)
    fun hasAny(p: String) = KfOps.hasAnyKf(kfs, p)
    fun sample(p: String, base: Float) = KfOps.sample(kfs, p, currentTimeSec, base)
    fun toggle(p: String, v: Float) {
        onChange(effect.copy(keyframes = KfOps.toggle(kfs, p, currentTimeSec, v)))
    }

    @Composable
    fun R(
        label: String, prop: String, base: Float, range: ClosedFloatingPointRange<Float>,
        decimals: Int, unit: String = "", update: (DropShadowEffect, Float) -> DropShadowEffect
    ) {
        EffectPropertyRow(
            label = label,
            value = sample(prop, base),
            range = range,
            decimals = decimals,
            unit = unit,
            hasKeyframe = hasKf(prop),
            hasAnyKeyframe = hasAny(prop),
            onValueChange = { v ->
                val next = update(effect, v)
                onChange(
                    if (hasAny(prop)) next.copy(
                        keyframes = KfOps.autoWrite(
                            kfs,
                            prop,
                            currentTimeSec,
                            v
                        )
                    )
                    else next
                )
            },
            onToggleKeyframe = { toggle(prop, sample(prop, base)) }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("SHADOW")
        ColorPickerField("Color", effect.shadowColor) { onChange(effect.copy(shadowColor = it)) }
        R("Opacity", "opacity", effect.opacity, 0f..100f, 1, "%") { e, v -> e.copy(opacity = v) }
        R(
            "Distance",
            "distance",
            effect.distance,
            0f..500f,
            1,
            "px"
        ) { e, v -> e.copy(distance = v) }
        R("Direction", "directionAngle", effect.directionAngle, 0f..360f, 1, "°") { e, v ->
            e.copy(
                directionAngle = v
            )
        }
        R("Softness", "blurSoftness", effect.blurSoftness, 0f..100f, 1, "px") { e, v ->
            e.copy(
                blurSoftness = v
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  7. TURBULENT DISPLACE
// ═══════════════════════════════════════════════════════════════

@Composable
private fun TurbulentDisplaceProperties(
    effect: TurbulentDisplaceEffect,
    currentTimeSec: Float,
    onChange: (TurbulentDisplaceEffect) -> Unit
) {
    val kfs = effect.keyframes
    fun hasKf(p: String) = KfOps.hasKf(kfs, p, currentTimeSec)
    fun hasAny(p: String) = KfOps.hasAnyKf(kfs, p)
    fun sample(p: String, base: Float) = KfOps.sample(kfs, p, currentTimeSec, base)
    fun toggle(p: String, v: Float) {
        onChange(effect.copy(keyframes = KfOps.toggle(kfs, p, currentTimeSec, v)))
    }

    @Composable
    fun R(
        label: String,
        prop: String,
        base: Float,
        range: ClosedFloatingPointRange<Float>,
        decimals: Int,
        unit: String = "",
        update: (TurbulentDisplaceEffect, Float) -> TurbulentDisplaceEffect
    ) {
        EffectPropertyRow(
            label = label,
            value = sample(prop, base),
            range = range,
            decimals = decimals,
            unit = unit,
            hasKeyframe = hasKf(prop),
            hasAnyKeyframe = hasAny(prop),
            onValueChange = { v ->
                val next = update(effect, v)
                onChange(
                    if (hasAny(prop)) next.copy(
                        keyframes = KfOps.autoWrite(
                            kfs,
                            prop,
                            currentTimeSec,
                            v
                        )
                    )
                    else next
                )
            },
            onToggleKeyframe = { toggle(prop, sample(prop, base)) }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("DISPLACE")
        R("Amount", "amount", effect.amount, 0f..1000f, 1) { e, v -> e.copy(amount = v) }
        R("Size", "size", effect.size, 0f..2000f, 1) { e, v -> e.copy(size = v) }
        SectionLabel("OFFSET (0.0 - 1.0)")
        R("Offset X", "offsetX", effect.offsetX, 0f..1f, 3) { e, v -> e.copy(offsetX = v) }
        R("Offset Y", "offsetY", effect.offsetY, 0f..1f, 3) { e, v -> e.copy(offsetY = v) }
        R("Speed", "evolutionSpeed", effect.evolutionSpeed, 0f..100f, 1) { e, v ->
            e.copy(
                evolutionSpeed = v
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  8. CHROMATIC ABERRATION
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ChromaticAberrationProperties(
    effect: ChromaticAberrationEffect,
    currentTimeSec: Float,
    onChange: (ChromaticAberrationEffect) -> Unit
) {
    val kfs = effect.keyframes
    fun hasKf(p: String) = KfOps.hasKf(kfs, p, currentTimeSec)
    fun hasAny(p: String) = KfOps.hasAnyKf(kfs, p)
    fun sample(p: String, base: Float) = KfOps.sample(kfs, p, currentTimeSec, base)
    fun toggle(p: String, v: Float) {
        onChange(effect.copy(keyframes = KfOps.toggle(kfs, p, currentTimeSec, v)))
    }

    @Composable
    fun R(
        label: String,
        prop: String,
        base: Float,
        range: ClosedFloatingPointRange<Float>,
        decimals: Int,
        unit: String = "",
        update: (ChromaticAberrationEffect, Float) -> ChromaticAberrationEffect
    ) {
        EffectPropertyRow(
            label = label,
            value = sample(prop, base),
            range = range,
            decimals = decimals,
            unit = unit,
            hasKeyframe = hasKf(prop),
            hasAnyKeyframe = hasAny(prop),
            onValueChange = { v ->
                val next = update(effect, v)
                onChange(
                    if (hasAny(prop)) next.copy(
                        keyframes = KfOps.autoWrite(
                            kfs,
                            prop,
                            currentTimeSec,
                            v
                        )
                    )
                    else next
                )
            },
            onToggleKeyframe = { toggle(prop, sample(prop, base)) }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("RED SHIFT")
        R(
            "Shift X",
            "redShiftX",
            effect.redShiftX,
            -50f..50f,
            1,
            "px"
        ) { e, v -> e.copy(redShiftX = v) }
        R(
            "Shift Y",
            "redShiftY",
            effect.redShiftY,
            -50f..50f,
            1,
            "px"
        ) { e, v -> e.copy(redShiftY = v) }
        SectionLabel("BLUE SHIFT")
        R("Shift X", "blueShiftX", effect.blueShiftX, -50f..50f, 1, "px") { e, v ->
            e.copy(
                blueShiftX = v
            )
        }
        R("Shift Y", "blueShiftY", effect.blueShiftY, -50f..50f, 1, "px") { e, v ->
            e.copy(
                blueShiftY = v
            )
        }
        SectionLabel("OTHER")
        R("Blur Radius", "blurRadius", effect.blurRadius, 0f..50f, 1, "px") { e, v ->
            e.copy(
                blurRadius = v
            )
        }
        R("Falloff", "falloffThreshold", effect.falloffThreshold, 0f..1f, 3) { e, v ->
            e.copy(
                falloffThreshold = v
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  9. MOTION BLUR
// ═══════════════════════════════════════════════════════════════

@Composable
private fun MotionBlurProperties(
    effect: MotionBlurEffect,
    currentTimeSec: Float,
    onChange: (MotionBlurEffect) -> Unit
) {
    val kfs = effect.keyframes
    fun hasKf(p: String) = KfOps.hasKf(kfs, p, currentTimeSec)
    fun hasAny(p: String) = KfOps.hasAnyKf(kfs, p)
    fun sample(p: String, base: Float) = KfOps.sample(kfs, p, currentTimeSec, base)
    fun toggle(p: String, v: Float) {
        onChange(effect.copy(keyframes = KfOps.toggle(kfs, p, currentTimeSec, v)))
    }

    @Composable
    fun R(
        label: String, prop: String, base: Float, range: ClosedFloatingPointRange<Float>,
        decimals: Int, unit: String = "", update: (MotionBlurEffect, Float) -> MotionBlurEffect
    ) {
        EffectPropertyRow(
            label = label,
            value = sample(prop, base),
            range = range,
            decimals = decimals,
            unit = unit,
            hasKeyframe = hasKf(prop),
            hasAnyKeyframe = hasAny(prop),
            onValueChange = { v ->
                val next = update(effect, v)
                onChange(
                    if (hasAny(prop)) next.copy(
                        keyframes = KfOps.autoWrite(
                            kfs,
                            prop,
                            currentTimeSec,
                            v
                        )
                    )
                    else next
                )
            },
            onToggleKeyframe = { toggle(prop, sample(prop, base)) }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("MOTION BLUR")
        R("Shutter", "shutterAngle", effect.shutterAngle, 0f..720f, 1, "°") { e, v ->
            e.copy(
                shutterAngle = v
            )
        }
        R("Samples", "samples", effect.samples, 1f..64f, 0) { e, v -> e.copy(samples = v) }
        R("Intensity", "intensity", effect.intensity, 0f..2f, 2) { e, v -> e.copy(intensity = v) }
    }
}

// ═══════════════════════════════════════════════════════════════
//  10. TRACK MATTE
// ═══════════════════════════════════════════════════════════════

@Composable
private fun TrackMatteProperties(
    effect: TrackMatteEffect,
    currentTimeSec: Float,
    onChange: (TrackMatteEffect) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("MATTE TYPE")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TrackMatteType.values().forEach { type ->
                val isActive = effect.matteType == type
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                        .pointerInput(type) { detectTapGestures { onChange(effect.copy(matteType = type)) } }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when (type) {
                            TrackMatteType.ALPHA -> "Alpha"
                            TrackMatteType.ALPHA_INVERTED -> "Alpha Inv"
                            TrackMatteType.LUMA -> "Luma"
                            TrackMatteType.LUMA_INVERTED -> "Luma Inv"
                        },
                        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        SectionLabel("TARGET LAYER")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF181818))
                .padding(12.dp)
        ) {
            Text(
                if (effect.targetLayerId == null)
                    "⚠️ Not set — select another clip on timeline and link here"
                else "Target: ${effect.targetLayerId.take(16)}",
                color = if (effect.targetLayerId == null) Color(0xFFFFCC00)
                else Color(0xFF60EFFF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
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
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectState
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectType
import com.moody.moodyvideoeditor.data.advanced.BlurDimension
import com.moody.moodyvideoeditor.data.advanced.ChromaticAberrationEffect
import com.moody.moodyvideoeditor.data.advanced.DropShadowEffect
import com.moody.moodyvideoeditor.data.advanced.FourColorGradientEffect
import com.moody.moodyvideoeditor.data.advanced.GaussianBlurEffect
import com.moody.moodyvideoeditor.data.advanced.GradientBlendMode
import com.moody.moodyvideoeditor.data.advanced.MirrorEffect
import com.moody.moodyvideoeditor.data.advanced.MotionBlurEffect
import com.moody.moodyvideoeditor.data.advanced.RoughenEdgesEffect
import com.moody.moodyvideoeditor.data.advanced.RoundedCropEffect
import com.moody.moodyvideoeditor.data.advanced.TrackMatteEffect
import com.moody.moodyvideoeditor.data.advanced.TrackMatteType
import com.moody.moodyvideoeditor.data.advanced.TurbulentDisplaceEffect
import com.moody.moodyvideoeditor.ui.components.ColorPickerField
import com.moody.moodyvideoeditor.ui.components.EffectPropertyRow
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.KeyframeStore

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
    var workingState by remember {
        mutableStateOf(AdvancedEffectState.withDefaults(AdvancedEffectType.MIRROR))
    }

    val existingEffects = selectedClip?.advancedEffects ?: emptyList()

    // Load effect when editing
    LaunchedEffect(editingIndex, selectedClip?.id) {
        if (editingIndex in existingEffects.indices) {
            val fx = existingEffects[editingIndex]
            workingState = fx
            selectedType = fx.type
        } else {
            workingState = AdvancedEffectState.withDefaults(selectedType)
        }
    }

    // Fire live preview on every change
    LaunchedEffect(workingState, currentTimeSec) {
        onLivePreview(workingState)
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
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ─── No clip selected ───
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
                        "🎯 Target: ${selectedClip.name.take(28)}",
                        color = Color(0xFF60EFFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Effects apply to this clip only",
                        color = Color(0xFF888888),
                        fontSize = 9.sp
                    )
                }
            }

            // ─── Applied Effects List ───
            if (existingEffects.isNotEmpty()) {
                Text(
                    "APPLIED EFFECTS (${existingEffects.size})",
                    color = Color(0xFF4F9DFF),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )

                existingEffects.forEachIndexed { idx, fx ->
                    val isEditing = editingIndex == idx
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
                                fx.type.category,
                                color = Color(0xFF888888),
                                fontSize = 9.sp
                            )
                        }
                        // Edit
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF7C3AED).copy(alpha = 0.25f))
                                .pointerInput(idx) {
                                    detectTapGestures {
                                        editingIndex = idx
                                    }
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
                        // Remove
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

            // ─── Back to Add ───
            if (editingIndex >= 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF181818))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                editingIndex = -1
                                workingState = AdvancedEffectState.withDefaults(selectedType)
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

            // ─── Type Selector ───
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
                                    workingState = AdvancedEffectState.withDefaults(type)
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

            // ─── Properties by type ───
            when (selectedType) {
                AdvancedEffectType.MIRROR -> MirrorProperties(
                    effect = workingState.mirror ?: MirrorEffect(),
                    currentTimeSec = currentTimeSec,
                    onChange = { workingState = workingState.copy(mirror = it) }
                )

                AdvancedEffectType.GAUSSIAN_BLUR -> GaussianBlurProperties(
                    effect = workingState.gaussianBlur ?: GaussianBlurEffect(),
                    currentTimeSec = currentTimeSec,
                    onChange = { workingState = workingState.copy(gaussianBlur = it) }
                )

                AdvancedEffectType.ROUGHEN_EDGES -> RoughenEdgesProperties(
                    effect = workingState.roughenEdges ?: RoughenEdgesEffect(),
                    currentTimeSec = currentTimeSec,
                    onChange = { workingState = workingState.copy(roughenEdges = it) }
                )

                AdvancedEffectType.ROUNDED_CROP -> RoundedCropProperties(
                    effect = workingState.roundedCrop ?: RoundedCropEffect(),
                    currentTimeSec = currentTimeSec,
                    onChange = { workingState = workingState.copy(roundedCrop = it) }
                )

                AdvancedEffectType.FOUR_COLOR_GRADIENT -> FourColorGradientProperties(
                    effect = workingState.fourColorGradient ?: FourColorGradientEffect(),
                    currentTimeSec = currentTimeSec,
                    onChange = { workingState = workingState.copy(fourColorGradient = it) }
                )

                AdvancedEffectType.DROP_SHADOW -> DropShadowProperties(
                    effect = workingState.dropShadow ?: DropShadowEffect(),
                    currentTimeSec = currentTimeSec,
                    onChange = { workingState = workingState.copy(dropShadow = it) }
                )

                AdvancedEffectType.TURBULENT_DISPLACE -> TurbulentDisplaceProperties(
                    effect = workingState.turbulentDisplace ?: TurbulentDisplaceEffect(),
                    currentTimeSec = currentTimeSec,
                    onChange = { workingState = workingState.copy(turbulentDisplace = it) }
                )

                AdvancedEffectType.CHROMATIC_ABERRATION -> ChromaticAberrationProperties(
                    effect = workingState.chromaticAberration
                        ?: ChromaticAberrationEffect(),
                    currentTimeSec = currentTimeSec,
                    onChange = { workingState = workingState.copy(chromaticAberration = it) }
                )

                AdvancedEffectType.MOTION_BLUR -> MotionBlurProperties(
                    effect = workingState.motionBlur ?: MotionBlurEffect(),
                    currentTimeSec = currentTimeSec,
                    onChange = { workingState = workingState.copy(motionBlur = it) }
                )

                AdvancedEffectType.TRACK_MATTE -> TrackMatteProperties(
                    effect = workingState.trackMatte ?: TrackMatteEffect(),
                    currentTimeSec = currentTimeSec,
                    onChange = { workingState = workingState.copy(trackMatte = it) }
                )
            }

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
                                    if (editingIndex in existingEffects.indices) {
                                        workingState = existingEffects[editingIndex]
                                    }
                                    editingIndex = -1
                                    workingState = AdvancedEffectState.withDefaults(selectedType)
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
                        .pointerInput(editingIndex, workingState) {
                            detectTapGestures {
                                if (editingIndex >= 0) {
                                    onUpdateEffect(editingIndex, workingState)
                                } else {
                                    onAddEffect(workingState)
                                }
                                onLivePreview(null)
                                editingIndex = -1
                                workingState = AdvancedEffectState.withDefaults(selectedType)
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
                "💡 Live preview is approximate. Export applies effects precisely.",
                color = Color(0xFF666666),
                fontSize = 9.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  REUSABLE KEYFRAME HELPERS
// ═══════════════════════════════════════════════════════════════

private inline fun <T> ToggleKf(
    keyframes: Map<String, com.moody.moodyvideoeditor.data.Keyframe>,
    prop: String,
    currentTimeSec: Float,
    currentValue: Float
): Map<String, List<com.moody.moodyvideoeditor.data.Keyframe>> {
    return emptyMap()
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
//  MIRROR PROPERTIES
// ═══════════════════════════════════════════════════════════════

@Composable
private fun MirrorProperties(
    effect: MirrorEffect,
    currentTimeSec: Float,
    onChange: (MirrorEffect) -> Unit
) {
    fun hasKf(p: String) = KeyframeStore.hasKeyframeAt(effect.keyframes, p, currentTimeSec)
    fun hasAnyKf(p: String) = KeyframeStore.getKeyframes(effect.keyframes, p).isNotEmpty()

    fun toggleKf(p: String, v: Float) {
        val newKfs = if (hasKf(p)) {
            KeyframeStore.removeKeyframe(effect.keyframes, p, currentTimeSec)
        } else {
            KeyframeStore.setKeyframe(effect.keyframes, p, currentTimeSec, v)
        }
        onChange(effect.copy(keyframes = newKfs))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("REFLECTION CENTER")
        EffectPropertyRow(
            label = "Center X", value = effect.centerX, range = 0f..1f, decimals = 3,
            hasKeyframe = hasKf("centerX"), hasAnyKeyframe = hasAnyKf("centerX"),
            onValueChange = { onChange(effect.copy(centerX = it)) },
            onToggleKeyframe = { toggleKf("centerX", effect.centerX) }
        )
        EffectPropertyRow(
            label = "Center Y", value = effect.centerY, range = 0f..1f, decimals = 3,
            hasKeyframe = hasKf("centerY"), hasAnyKeyframe = hasAnyKf("centerY"),
            onValueChange = { onChange(effect.copy(centerY = it)) },
            onToggleKeyframe = { toggleKf("centerY", effect.centerY) }
        )
        SectionLabel("REFLECTION ANGLE")
        EffectPropertyRow(
            label = "Angle", value = effect.angleDeg, range = 0f..360f, decimals = 1,
            unit = "°",
            hasKeyframe = hasKf("angleDeg"), hasAnyKeyframe = hasAnyKf("angleDeg"),
            onValueChange = { onChange(effect.copy(angleDeg = it)) },
            onToggleKeyframe = { toggleKf("angleDeg", effect.angleDeg) }
        )
        EffectPropertyRow(
            label = "Opacity", value = effect.opacity, range = 0f..100f, decimals = 1,
            unit = "%",
            hasKeyframe = hasKf("opacity"), hasAnyKeyframe = hasAnyKf("opacity"),
            onValueChange = { onChange(effect.copy(opacity = it)) },
            onToggleKeyframe = { toggleKf("opacity", effect.opacity) }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  GAUSSIAN BLUR PROPERTIES
// ═══════════════════════════════════════════════════════════════

@Composable
private fun GaussianBlurProperties(
    effect: GaussianBlurEffect,
    currentTimeSec: Float,
    onChange: (GaussianBlurEffect) -> Unit
) {
    fun hasKf(p: String) = KeyframeStore.hasKeyframeAt(effect.keyframes, p, currentTimeSec)
    fun hasAnyKf(p: String) = KeyframeStore.getKeyframes(effect.keyframes, p).isNotEmpty()

    fun toggleKf(p: String, v: Float) {
        val newKfs = if (hasKf(p)) {
            KeyframeStore.removeKeyframe(effect.keyframes, p, currentTimeSec)
        } else {
            KeyframeStore.setKeyframe(effect.keyframes, p, currentTimeSec, v)
        }
        onChange(effect.copy(keyframes = newKfs))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("BLUR")
        EffectPropertyRow(
            label = "Blurriness", value = effect.blurriness, range = 0f..1000f,
            decimals = 1, unit = "px",
            hasKeyframe = hasKf("blurriness"), hasAnyKeyframe = hasAnyKf("blurriness"),
            onValueChange = { onChange(effect.copy(blurriness = it)) },
            onToggleKeyframe = { toggleKf("blurriness", effect.blurriness) }
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
                        .background(
                            if (isActive) Color(0xFF7C3AED) else Color(0xFF181818)
                        )
                        .pointerInput(dim) {
                            detectTapGestures { onChange(effect.copy(dimension = dim)) }
                        }
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
//  ROUGHEN EDGES PROPERTIES
// ═══════════════════════════════════════════════════════════════

@Composable
private fun RoughenEdgesProperties(
    effect: RoughenEdgesEffect,
    currentTimeSec: Float,
    onChange: (RoughenEdgesEffect) -> Unit
) {
    fun hasKf(p: String) = KeyframeStore.hasKeyframeAt(effect.keyframes, p, currentTimeSec)
    fun hasAnyKf(p: String) = KeyframeStore.getKeyframes(effect.keyframes, p).isNotEmpty()

    fun toggleKf(p: String, v: Float) {
        val newKfs = if (hasKf(p)) {
            KeyframeStore.removeKeyframe(effect.keyframes, p, currentTimeSec)
        } else {
            KeyframeStore.setKeyframe(effect.keyframes, p, currentTimeSec, v)
        }
        onChange(effect.copy(keyframes = newKfs))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("BORDER")
        EffectPropertyRow(
            label = "Width", value = effect.borderWidth, range = 0f..500f, decimals = 1,
            unit = "px",
            hasKeyframe = hasKf("borderWidth"), hasAnyKeyframe = hasAnyKf("borderWidth"),
            onValueChange = { onChange(effect.copy(borderWidth = it)) },
            onToggleKeyframe = { toggleKf("borderWidth", effect.borderWidth) }
        )
        EffectPropertyRow(
            label = "Sharpness", value = effect.edgeSharpness, range = 0f..100f,
            decimals = 1,
            hasKeyframe = hasKf("edgeSharpness"),
            hasAnyKeyframe = hasAnyKf("edgeSharpness"),
            onValueChange = { onChange(effect.copy(edgeSharpness = it)) },
            onToggleKeyframe = { toggleKf("edgeSharpness", effect.edgeSharpness) }
        )

        SectionLabel("FRACTAL")
        EffectPropertyRow(
            label = "Scale", value = effect.fractalScale, range = 20f..1000f,
            decimals = 1,
            hasKeyframe = hasKf("fractalScale"),
            hasAnyKeyframe = hasAnyKf("fractalScale"),
            onValueChange = { onChange(effect.copy(fractalScale = it)) },
            onToggleKeyframe = { toggleKf("fractalScale", effect.fractalScale) }
        )
        EffectPropertyRow(
            label = "Evolution", value = effect.evolution, range = 0f..360f,
            decimals = 1, unit = "°",
            hasKeyframe = hasKf("evolution"), hasAnyKeyframe = hasAnyKf("evolution"),
            onValueChange = { onChange(effect.copy(evolution = it)) },
            onToggleKeyframe = { toggleKf("evolution", effect.evolution) }
        )
        EffectPropertyRow(
            label = "Complexity", value = effect.complexity, range = 1f..10f,
            decimals = 1,
            hasKeyframe = hasKf("complexity"), hasAnyKeyframe = hasAnyKf("complexity"),
            onValueChange = { onChange(effect.copy(complexity = it)) },
            onToggleKeyframe = { toggleKf("complexity", effect.complexity) }
        )
        EffectPropertyRow(
            label = "Seed", value = effect.randomSeed, range = 0f..9999f,
            decimals = 0,
            hasKeyframe = hasKf("randomSeed"), hasAnyKeyframe = hasAnyKf("randomSeed"),
            onValueChange = { onChange(effect.copy(randomSeed = it)) },
            onToggleKeyframe = { toggleKf("randomSeed", effect.randomSeed) }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  ROUNDED CROP PROPERTIES
// ═══════════════════════════════════════════════════════════════

@Composable
private fun RoundedCropProperties(
    effect: RoundedCropEffect,
    currentTimeSec: Float,
    onChange: (RoundedCropEffect) -> Unit
) {
    fun hasKf(p: String) = KeyframeStore.hasKeyframeAt(effect.keyframes, p, currentTimeSec)
    fun hasAnyKf(p: String) = KeyframeStore.getKeyframes(effect.keyframes, p).isNotEmpty()

    fun toggleKf(p: String, v: Float) {
        val newKfs = if (hasKf(p)) {
            KeyframeStore.removeKeyframe(effect.keyframes, p, currentTimeSec)
        } else {
            KeyframeStore.setKeyframe(effect.keyframes, p, currentTimeSec, v)
        }
        onChange(effect.copy(keyframes = newKfs))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("CORNER")
        EffectPropertyRow(
            label = "Radius", value = effect.cornerRadius, range = 0f..500f,
            decimals = 1, unit = "px",
            hasKeyframe = hasKf("cornerRadius"),
            hasAnyKeyframe = hasAnyKf("cornerRadius"),
            onValueChange = { onChange(effect.copy(cornerRadius = it)) },
            onToggleKeyframe = { toggleKf("cornerRadius", effect.cornerRadius) }
        )

        SectionLabel("CROP (0.0 - 0.5)")
        EffectPropertyRow(
            label = "Top", value = effect.cropTop, range = 0f..0.5f, decimals = 3,
            hasKeyframe = hasKf("cropTop"), hasAnyKeyframe = hasAnyKf("cropTop"),
            onValueChange = { onChange(effect.copy(cropTop = it)) },
            onToggleKeyframe = { toggleKf("cropTop", effect.cropTop) }
        )
        EffectPropertyRow(
            label = "Bottom", value = effect.cropBottom, range = 0f..0.5f, decimals = 3,
            hasKeyframe = hasKf("cropBottom"),
            hasAnyKeyframe = hasAnyKf("cropBottom"),
            onValueChange = { onChange(effect.copy(cropBottom = it)) },
            onToggleKeyframe = { toggleKf("cropBottom", effect.cropBottom) }
        )
        EffectPropertyRow(
            label = "Left", value = effect.cropLeft, range = 0f..0.5f, decimals = 3,
            hasKeyframe = hasKf("cropLeft"), hasAnyKeyframe = hasAnyKf("cropLeft"),
            onValueChange = { onChange(effect.copy(cropLeft = it)) },
            onToggleKeyframe = { toggleKf("cropLeft", effect.cropLeft) }
        )
        EffectPropertyRow(
            label = "Right", value = effect.cropRight, range = 0f..0.5f, decimals = 3,
            hasKeyframe = hasKf("cropRight"), hasAnyKeyframe = hasAnyKf("cropRight"),
            onValueChange = { onChange(effect.copy(cropRight = it)) },
            onToggleKeyframe = { toggleKf("cropRight", effect.cropRight) }
        )

        SectionLabel("EDGE")
        EffectPropertyRow(
            label = "Feathering", value = effect.feathering, range = 0f..200f,
            decimals = 1, unit = "px",
            hasKeyframe = hasKf("feathering"),
            hasAnyKeyframe = hasAnyKf("feathering"),
            onValueChange = { onChange(effect.copy(feathering = it)) },
            onToggleKeyframe = { toggleKf("feathering", effect.feathering) }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  4-COLOR GRADIENT PROPERTIES
// ═══════════════════════════════════════════════════════════════

@Composable
private fun FourColorGradientProperties(
    effect: FourColorGradientEffect,
    currentTimeSec: Float,
    onChange: (FourColorGradientEffect) -> Unit
) {
    fun hasKf(p: String) = KeyframeStore.hasKeyframeAt(effect.keyframes, p, currentTimeSec)
    fun hasAnyKf(p: String) = KeyframeStore.getKeyframes(effect.keyframes, p).isNotEmpty()

    fun toggleKf(p: String, v: Float) {
        val newKfs = if (hasKf(p)) {
            KeyframeStore.removeKeyframe(effect.keyframes, p, currentTimeSec)
        } else {
            KeyframeStore.setKeyframe(effect.keyframes, p, currentTimeSec, v)
        }
        onChange(effect.copy(keyframes = newKfs))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("CORNER COLORS")
        ColorPickerField(
            label = "Top Left", colorLong = effect.color1,
            onChange = { onChange(effect.copy(color1 = it)) }
        )
        ColorPickerField(
            label = "Top Right", colorLong = effect.color2,
            onChange = { onChange(effect.copy(color2 = it)) }
        )
        ColorPickerField(
            label = "Bot Right", colorLong = effect.color3,
            onChange = { onChange(effect.copy(color3 = it)) }
        )
        ColorPickerField(
            label = "Bot Left", colorLong = effect.color4,
            onChange = { onChange(effect.copy(color4 = it)) }
        )

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
                        .background(
                            if (isActive) Color(0xFF7C3AED) else Color(0xFF181818)
                        )
                        .pointerInput(mode) {
                            detectTapGestures { onChange(effect.copy(blendMode = mode)) }
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        mode.key.replaceFirstChar { it.uppercase() },
                        color = Color.White, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        SectionLabel("OPACITY")
        EffectPropertyRow(
            label = "Global", value = effect.globalOpacity, range = 0f..100f,
            decimals = 1, unit = "%",
            hasKeyframe = hasKf("globalOpacity"),
            hasAnyKeyframe = hasAnyKf("globalOpacity"),
            onValueChange = { onChange(effect.copy(globalOpacity = it)) },
            onToggleKeyframe = { toggleKf("globalOpacity", effect.globalOpacity) }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  DROP SHADOW PROPERTIES
// ═══════════════════════════════════════════════════════════════

@Composable
private fun DropShadowProperties(
    effect: DropShadowEffect,
    currentTimeSec: Float,
    onChange: (DropShadowEffect) -> Unit
) {
    fun hasKf(p: String) = KeyframeStore.hasKeyframeAt(effect.keyframes, p, currentTimeSec)
    fun hasAnyKf(p: String) = KeyframeStore.getKeyframes(effect.keyframes, p).isNotEmpty()

    fun toggleKf(p: String, v: Float) {
        val newKfs = if (hasKf(p)) {
            KeyframeStore.removeKeyframe(effect.keyframes, p, currentTimeSec)
        } else {
            KeyframeStore.setKeyframe(effect.keyframes, p, currentTimeSec, v)
        }
        onChange(effect.copy(keyframes = newKfs))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("SHADOW")
        ColorPickerField(
            label = "Color", colorLong = effect.shadowColor,
            onChange = { onChange(effect.copy(shadowColor = it)) }
        )
        EffectPropertyRow(
            label = "Opacity", value = effect.opacity, range = 0f..100f,
            decimals = 1, unit = "%",
            hasKeyframe = hasKf("opacity"), hasAnyKeyframe = hasAnyKf("opacity"),
            onValueChange = { onChange(effect.copy(opacity = it)) },
            onToggleKeyframe = { toggleKf("opacity", effect.opacity) }
        )
        EffectPropertyRow(
            label = "Distance", value = effect.distance, range = 0f..500f,
            decimals = 1, unit = "px",
            hasKeyframe = hasKf("distance"), hasAnyKeyframe = hasAnyKf("distance"),
            onValueChange = { onChange(effect.copy(distance = it)) },
            onToggleKeyframe = { toggleKf("distance", effect.distance) }
        )
        EffectPropertyRow(
            label = "Direction", value = effect.directionAngle, range = 0f..360f,
            decimals = 1, unit = "°",
            hasKeyframe = hasKf("directionAngle"),
            hasAnyKeyframe = hasAnyKf("directionAngle"),
            onValueChange = { onChange(effect.copy(directionAngle = it)) },
            onToggleKeyframe = { toggleKf("directionAngle", effect.directionAngle) }
        )
        EffectPropertyRow(
            label = "Softness", value = effect.blurSoftness, range = 0f..100f,
            decimals = 1, unit = "px",
            hasKeyframe = hasKf("blurSoftness"),
            hasAnyKeyframe = hasAnyKf("blurSoftness"),
            onValueChange = { onChange(effect.copy(blurSoftness = it)) },
            onToggleKeyframe = { toggleKf("blurSoftness", effect.blurSoftness) }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  TURBULENT DISPLACE PROPERTIES
// ═══════════════════════════════════════════════════════════════

@Composable
private fun TurbulentDisplaceProperties(
    effect: TurbulentDisplaceEffect,
    currentTimeSec: Float,
    onChange: (TurbulentDisplaceEffect) -> Unit
) {
    fun hasKf(p: String) = KeyframeStore.hasKeyframeAt(effect.keyframes, p, currentTimeSec)
    fun hasAnyKf(p: String) = KeyframeStore.getKeyframes(effect.keyframes, p).isNotEmpty()

    fun toggleKf(p: String, v: Float) {
        val newKfs = if (hasKf(p)) {
            KeyframeStore.removeKeyframe(effect.keyframes, p, currentTimeSec)
        } else {
            KeyframeStore.setKeyframe(effect.keyframes, p, currentTimeSec, v)
        }
        onChange(effect.copy(keyframes = newKfs))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("DISPLACE")
        EffectPropertyRow(
            label = "Amount", value = effect.amount, range = 0f..1000f,
            decimals = 1,
            hasKeyframe = hasKf("amount"), hasAnyKeyframe = hasAnyKf("amount"),
            onValueChange = { onChange(effect.copy(amount = it)) },
            onToggleKeyframe = { toggleKf("amount", effect.amount) }
        )
        EffectPropertyRow(
            label = "Size", value = effect.size, range = 0f..2000f, decimals = 1,
            hasKeyframe = hasKf("size"), hasAnyKeyframe = hasAnyKf("size"),
            onValueChange = { onChange(effect.copy(size = it)) },
            onToggleKeyframe = { toggleKf("size", effect.size) }
        )

        SectionLabel("OFFSET (0.0 - 1.0)")
        EffectPropertyRow(
            label = "Offset X", value = effect.offsetX, range = 0f..1f, decimals = 3,
            hasKeyframe = hasKf("offsetX"), hasAnyKeyframe = hasAnyKf("offsetX"),
            onValueChange = { onChange(effect.copy(offsetX = it)) },
            onToggleKeyframe = { toggleKf("offsetX", effect.offsetX) }
        )
        EffectPropertyRow(
            label = "Offset Y", value = effect.offsetY, range = 0f..1f, decimals = 3,
            hasKeyframe = hasKf("offsetY"), hasAnyKeyframe = hasAnyKf("offsetY"),
            onValueChange = { onChange(effect.copy(offsetY = it)) },
            onToggleKeyframe = { toggleKf("offsetY", effect.offsetY) }
        )
        EffectPropertyRow(
            label = "Speed", value = effect.evolutionSpeed, range = 0f..100f,
            decimals = 1,
            hasKeyframe = hasKf("evolutionSpeed"),
            hasAnyKeyframe = hasAnyKf("evolutionSpeed"),
            onValueChange = { onChange(effect.copy(evolutionSpeed = it)) },
            onToggleKeyframe = { toggleKf("evolutionSpeed", effect.evolutionSpeed) }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  CHROMATIC ABERRATION PROPERTIES
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ChromaticAberrationProperties(
    effect: ChromaticAberrationEffect,
    currentTimeSec: Float,
    onChange: (ChromaticAberrationEffect) -> Unit
) {
    fun hasKf(p: String) = KeyframeStore.hasKeyframeAt(effect.keyframes, p, currentTimeSec)
    fun hasAnyKf(p: String) = KeyframeStore.getKeyframes(effect.keyframes, p).isNotEmpty()

    fun toggleKf(p: String, v: Float) {
        val newKfs = if (hasKf(p)) {
            KeyframeStore.removeKeyframe(effect.keyframes, p, currentTimeSec)
        } else {
            KeyframeStore.setKeyframe(effect.keyframes, p, currentTimeSec, v)
        }
        onChange(effect.copy(keyframes = newKfs))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("RED SHIFT")
        EffectPropertyRow(
            label = "Shift X", value = effect.redShiftX, range = -50f..50f,
            decimals = 1, unit = "px",
            hasKeyframe = hasKf("redShiftX"), hasAnyKeyframe = hasAnyKf("redShiftX"),
            onValueChange = { onChange(effect.copy(redShiftX = it)) },
            onToggleKeyframe = { toggleKf("redShiftX", effect.redShiftX) }
        )
        EffectPropertyRow(
            label = "Shift Y", value = effect.redShiftY, range = -50f..50f,
            decimals = 1, unit = "px",
            hasKeyframe = hasKf("redShiftY"), hasAnyKeyframe = hasAnyKf("redShiftY"),
            onValueChange = { onChange(effect.copy(redShiftY = it)) },
            onToggleKeyframe = { toggleKf("redShiftY", effect.redShiftY) }
        )

        SectionLabel("BLUE SHIFT")
        EffectPropertyRow(
            label = "Shift X", value = effect.blueShiftX, range = -50f..50f,
            decimals = 1, unit = "px",
            hasKeyframe = hasKf("blueShiftX"), hasAnyKeyframe = hasAnyKf("blueShiftX"),
            onValueChange = { onChange(effect.copy(blueShiftX = it)) },
            onToggleKeyframe = { toggleKf("blueShiftX", effect.blueShiftX) }
        )
        EffectPropertyRow(
            label = "Shift Y", value = effect.blueShiftY, range = -50f..50f,
            decimals = 1, unit = "px",
            hasKeyframe = hasKf("blueShiftY"), hasAnyKeyframe = hasAnyKf("blueShiftY"),
            onValueChange = { onChange(effect.copy(blueShiftY = it)) },
            onToggleKeyframe = { toggleKf("blueShiftY", effect.blueShiftY) }
        )

        SectionLabel("OTHER")
        EffectPropertyRow(
            label = "Blur Radius", value = effect.blurRadius, range = 0f..50f,
            decimals = 1, unit = "px",
            hasKeyframe = hasKf("blurRadius"), hasAnyKeyframe = hasAnyKf("blurRadius"),
            onValueChange = { onChange(effect.copy(blurRadius = it)) },
            onToggleKeyframe = { toggleKf("blurRadius", effect.blurRadius) }
        )
        EffectPropertyRow(
            label = "Falloff", value = effect.falloffThreshold, range = 0f..1f,
            decimals = 3,
            hasKeyframe = hasKf("falloffThreshold"),
            hasAnyKeyframe = hasAnyKf("falloffThreshold"),
            onValueChange = { onChange(effect.copy(falloffThreshold = it)) },
            onToggleKeyframe = { toggleKf("falloffThreshold", effect.falloffThreshold) }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  MOTION BLUR PROPERTIES
// ═══════════════════════════════════════════════════════════════

@Composable
private fun MotionBlurProperties(
    effect: MotionBlurEffect,
    currentTimeSec: Float,
    onChange: (MotionBlurEffect) -> Unit
) {
    fun hasKf(p: String) = KeyframeStore.hasKeyframeAt(effect.keyframes, p, currentTimeSec)
    fun hasAnyKf(p: String) = KeyframeStore.getKeyframes(effect.keyframes, p).isNotEmpty()

    fun toggleKf(p: String, v: Float) {
        val newKfs = if (hasKf(p)) {
            KeyframeStore.removeKeyframe(effect.keyframes, p, currentTimeSec)
        } else {
            KeyframeStore.setKeyframe(effect.keyframes, p, currentTimeSec, v)
        }
        onChange(effect.copy(keyframes = newKfs))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("MOTION BLUR")
        EffectPropertyRow(
            label = "Shutter", value = effect.shutterAngle, range = 0f..720f,
            decimals = 1, unit = "°",
            hasKeyframe = hasKf("shutterAngle"),
            hasAnyKeyframe = hasAnyKf("shutterAngle"),
            onValueChange = { onChange(effect.copy(shutterAngle = it)) },
            onToggleKeyframe = { toggleKf("shutterAngle", effect.shutterAngle) }
        )
        EffectPropertyRow(
            label = "Samples", value = effect.samples, range = 1f..64f, decimals = 0,
            hasKeyframe = hasKf("samples"), hasAnyKeyframe = hasAnyKf("samples"),
            onValueChange = { onChange(effect.copy(samples = it)) },
            onToggleKeyframe = { toggleKf("samples", effect.samples) }
        )
        EffectPropertyRow(
            label = "Intensity", value = effect.intensity, range = 0f..2f,
            decimals = 2,
            hasKeyframe = hasKf("intensity"), hasAnyKeyframe = hasAnyKf("intensity"),
            onValueChange = { onChange(effect.copy(intensity = it)) },
            onToggleKeyframe = { toggleKf("intensity", effect.intensity) }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  TRACK MATTE PROPERTIES
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
                        .background(
                            if (isActive) Color(0xFF7C3AED) else Color(0xFF181818)
                        )
                        .pointerInput(type) {
                            detectTapGestures { onChange(effect.copy(matteType = type)) }
                        }
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
                        color = Color.White, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
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
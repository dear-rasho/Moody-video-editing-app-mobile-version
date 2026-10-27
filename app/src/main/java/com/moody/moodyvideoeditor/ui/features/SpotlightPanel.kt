package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.data.SpotlightPresets
import com.moody.moodyvideoeditor.data.SpotlightState
import com.moody.moodyvideoeditor.ui.components.KeyframeGraphDialog
import com.moody.moodyvideoeditor.ui.components.ColorPickerField
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.KeyframeStore

@Composable
fun SpotlightPanel(
    editLayerId: String?,
    initialState: SpotlightState,
    keyframeMap: Map<String, List<Keyframe>> = emptyMap(),
    currentTimeSec: Float = 0f,
    clipDurationSec: Float = 5f,
    onToggleKeyframe: (String) -> Unit = {},
    onUpdateKeyframe: (String, Float, Float, Float) -> Unit = { _, _, _, _ -> },
    onDeleteKeyframe: (String, Float) -> Unit = { _, _ -> },
    onKeyframeEditStart: () -> Unit = {},
    onKeyframeEditEnd: () -> Unit = {},
    onCreate: (SpotlightState) -> Unit,
    onUpdate: (SpotlightState) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    var spotlight by remember(editLayerId) { mutableStateOf(initialState) }
    var showGraph by remember { mutableStateOf(false) }
    val isEditMode = editLayerId != null
    if (showGraph) {
        KeyframeGraphDialog(
            clipName = "Spotlight",
            keyframeMap = keyframeMap,
            clipDurationSec = clipDurationSec,
            onUpdateKeyframe = onUpdateKeyframe,
            onDeleteKeyframe = onDeleteKeyframe,
            onKeyframeEditStart = onKeyframeEditStart,
            onKeyframeEditEnd = onKeyframeEditEnd,
            onClose = { showGraph = false }
        )
    }

    LaunchedEffect(editLayerId, initialState) {
        spotlight = initialState
    }

    fun applyChange(next: SpotlightState) {
        spotlight = next
        if (isEditMode) onUpdate(next)
    }

    FeaturePanel(
        title = if (isEditMode) "Edit Spotlight" else "2D Spotlight",
        onClose = onClose
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Drag the two handles in Preview to aim the light.",
                color = Color(0xFFAAAAAA),
                fontSize = 10.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Keyframes at ${"%.2f".format(currentTimeSec)}s",
                    color = Color(0xFFAAAAAA),
                    fontSize = 10.sp
                )
                TextButton(onClick = { showGraph = true }) {
                    Text("Open Keyframe Graph", fontSize = 10.sp)
                }
            }

            Text("Presets", color = Color(0xFF888888), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpotlightPresets.all.forEach { preset ->
                    Button(
                        onClick = { applyChange(preset.state) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF252525),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(preset.label, fontSize = 10.sp)
                    }
                }
            }

            SliderControl(
                label = "Intensity",
                value = spotlight.intensity,
                valueLabel = "%.2f×".format(spotlight.intensity),
                range = 0f..2.5f,
                keyframeProperties = listOf("spotlight.intensity"),
                keyframeMap = keyframeMap,
                currentTimeSec = currentTimeSec,
                onToggleKeyframe = onToggleKeyframe,
                onValueChange = { applyChange(spotlight.copy(intensity = it)) }
            )
            SliderControl(
                label = "Rotation",
                value = spotlight.rotationDeg,
                valueLabel = "${spotlight.rotationDeg.toInt()}°",
                range = -180f..180f,
                keyframeProperties = listOf("spotlight.rotation"),
                keyframeMap = keyframeMap,
                currentTimeSec = currentTimeSec,
                onToggleKeyframe = onToggleKeyframe,
                onValueChange = { applyChange(spotlight.copy(rotationDeg = it)) }
            )
            KeyframeControl(
                label = "Source Position",
                properties = listOf("spotlight.sourceX", "spotlight.sourceY"),
                keyframeMap = keyframeMap,
                currentTimeSec = currentTimeSec,
                onToggle = onToggleKeyframe
            )
            KeyframeControl(
                label = "Target Position",
                properties = listOf("spotlight.targetX", "spotlight.targetY"),
                keyframeMap = keyframeMap,
                currentTimeSec = currentTimeSec,
                onToggle = onToggleKeyframe
            )
            ColorPickerField(
                label = "Source",
                colorLong = spotlight.color,
                onChange = { applyChange(spotlight.copy(color = it)) }
            )
            KeyframeControl(
                label = "Source Color",
                properties = listOf(
                    "spotlight.colorR",
                    "spotlight.colorG",
                    "spotlight.colorB"
                ),
                keyframeMap = keyframeMap,
                currentTimeSec = currentTimeSec,
                onToggle = onToggleKeyframe
            )
            ColorPickerField(
                label = "Target",
                colorLong = spotlight.targetColor,
                onChange = { applyChange(spotlight.copy(targetColor = it)) }
            )
            KeyframeControl(
                label = "Target Color",
                properties = listOf(
                    "spotlight.targetColorR",
                    "spotlight.targetColorG",
                    "spotlight.targetColorB"
                ),
                keyframeMap = keyframeMap,
                currentTimeSec = currentTimeSec,
                onToggle = onToggleKeyframe
            )
            SliderControl(
                label = "Cone Radius",
                value = spotlight.radiusDeg,
                valueLabel = "${spotlight.radiusDeg.toInt()}°",
                range = 6f..80f,
                keyframeProperties = listOf("spotlight.radius"),
                keyframeMap = keyframeMap,
                currentTimeSec = currentTimeSec,
                onToggleKeyframe = onToggleKeyframe,
                onValueChange = { applyChange(spotlight.copy(radiusDeg = it)) }
            )
            SliderControl(
                label = "Edge Softness",
                value = spotlight.softness,
                valueLabel = "${(spotlight.softness * 100).toInt()}%",
                range = 0f..1f,
                keyframeProperties = listOf("spotlight.softness"),
                keyframeMap = keyframeMap,
                currentTimeSec = currentTimeSec,
                onToggleKeyframe = onToggleKeyframe,
                onValueChange = { applyChange(spotlight.copy(softness = it)) }
            )

            if (isEditMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onClose, modifier = Modifier.weight(1f)) {
                        Text("Done")
                    }
                    Button(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8F2525))
                    ) {
                        Text("Delete Layer")
                    }
                }
            } else {
                Button(
                    onClick = { onCreate(spotlight) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Spotlight Layer")
                }
            }
        }
    }
}

@Composable
private fun SliderControl(
    label: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    keyframeProperties: List<String> = emptyList(),
    keyframeMap: Map<String, List<Keyframe>> = emptyMap(),
    currentTimeSec: Float = 0f,
    onToggleKeyframe: (String) -> Unit = {},
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White, fontSize = 11.sp)
            Text(valueLabel, color = Color(0xFFAAAAAA), fontSize = 10.sp)
            keyframeProperties.forEach { property ->
                KeyframeButton(
                    active = KeyframeStore.hasKeyframeAt(keyframeMap, property, currentTimeSec),
                    onClick = { onToggleKeyframe(property) }
                )
            }
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF60EFFF),
                activeTrackColor = Color(0xFF60EFFF)
            )
        )
    }
}

@Composable
private fun KeyframeControl(
    label: String,
    properties: List<String>,
    keyframeMap: Map<String, List<Keyframe>>,
    currentTimeSec: Float,
    onToggle: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFFAAAAAA), fontSize = 10.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            properties.forEach { property ->
                val part = property.substringAfterLast('.')
                KeyframeButton(
                    active = KeyframeStore.hasKeyframeAt(keyframeMap, property, currentTimeSec),
                    onClick = { onToggle(property) },
                    label = part
                )
            }
        }
    }
}

@Composable
private fun KeyframeButton(
    active: Boolean,
    onClick: () -> Unit,
    label: String = "◆"
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 24.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 5.dp,
            vertical = 0.dp
        )
    ) {
        Text(
            text = if (label == "◆") label else "$label ${if (active) "◆" else "◇"}",
            color = if (active) Color(0xFF4F9DFF) else Color(0xFF888888),
            fontSize = 9.sp
        )
    }
}

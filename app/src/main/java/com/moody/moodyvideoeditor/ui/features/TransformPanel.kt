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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.data.KeyframeLibrary
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.KeyframeStore
import com.moody.moodyvideoeditor.utils.TransformValues
import kotlin.math.abs

/**
 * Transform panel — JS style.
 * Each property has: [◆] [Label] [−] [value input] [+]
 */
@Composable
fun TransformPanel(
    clipName: String,
    hasClipSelected: Boolean,
    base: TransformValues,
    keyframeMap: Map<String, List<Keyframe>>,
    currentTimeSec: Float,
    onPropertyChanged: (String, Float) -> Unit,
    onToggleKeyframe: (String) -> Unit,
    onSetEase: (String) -> Unit,
    onResetAll: () -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "🔲 Transform", onClose = onClose) {

        if (!hasClipSelected) {
            EmptyState()
            return@FeaturePanel
        }

        // ─── INFO BAR ────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF181818))
                .padding(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Layer:", color = Color(0xFF888888), fontSize = 10.sp)
                Text(
                    clipName, color = Color.White, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Time:", color = Color(0xFF888888), fontSize = 10.sp)
                Text(
                    String.format("%.2fs", currentTimeSec),
                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text("Tap ◆ to animate", color = Color(0xFF666666), fontSize = 9.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        // ─── PROPERTY ROWS ──────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Position
            SectionLabel("Position")
            PropRow(
                label = "X",
                propKey = "x",
                value = base.x,
                step = 1f,
                minVal = -200f,
                maxVal = 200f,
                keyframeMap = keyframeMap,
                currentTimeSec = currentTimeSec,
                onChanged = { v -> onPropertyChanged("x", v) },
                onToggleKeyframe = { onToggleKeyframe("x") }
            )
            PropRow(
                label = "Y",
                propKey = "y",
                value = base.y,
                step = 1f,
                minVal = -200f,
                maxVal = 200f,
                keyframeMap = keyframeMap,
                currentTimeSec = currentTimeSec,
                onChanged = { v -> onPropertyChanged("y", v) },
                onToggleKeyframe = { onToggleKeyframe("y") }
            )

            // Scale
            SectionLabel("Scale")
            PropRow(
                label = "Scale %",
                propKey = "scale",
                value = base.scale,
                step = 1f,
                minVal = 10f,
                maxVal = 500f,
                keyframeMap = keyframeMap,
                currentTimeSec = currentTimeSec,
                onChanged = { v -> onPropertyChanged("scale", v) },
                onToggleKeyframe = { onToggleKeyframe("scale") }
            )

            // Rotation
            SectionLabel("Rotation")
            PropRow(
                label = "Angle °",
                propKey = "rotation",
                value = base.rotation,
                step = 1f,
                minVal = -360f,
                maxVal = 360f,
                keyframeMap = keyframeMap,
                currentTimeSec = currentTimeSec,
                onChanged = { v -> onPropertyChanged("rotation", v) },
                onToggleKeyframe = { onToggleKeyframe("rotation") }
            )

            // Anchor
            SectionLabel("Anchor")
            PropRow(
                label = "Anchor X",
                propKey = "anchorX",
                value = base.anchorX,
                step = 1f,
                minVal = 0f,
                maxVal = 100f,
                keyframeMap = keyframeMap,
                currentTimeSec = currentTimeSec,
                onChanged = { v -> onPropertyChanged("anchorX", v) },
                onToggleKeyframe = { onToggleKeyframe("anchorX") }
            )
            PropRow(
                label = "Anchor Y",
                propKey = "anchorY",
                value = base.anchorY,
                step = 1f,
                minVal = 0f,
                maxVal = 100f,
                keyframeMap = keyframeMap,
                currentTimeSec = currentTimeSec,
                onChanged = { v -> onPropertyChanged("anchorY", v) },
                onToggleKeyframe = { onToggleKeyframe("anchorY") }
            )

            // Crop
            SectionLabel("Crop %")
            PropRow(
                label = "Left",
                propKey = "cropL",
                value = base.cropL * 100f,
                step = 1f,
                minVal = 0f,
                maxVal = 95f,
                keyframeMap = keyframeMap,
                currentTimeSec = currentTimeSec,
                onChanged = { v -> onPropertyChanged("cropL", v / 100f) },
                onToggleKeyframe = { onToggleKeyframe("cropL") }
            )
            PropRow(
                label = "Right",
                propKey = "cropR",
                value = base.cropR * 100f,
                step = 1f,
                minVal = 0f,
                maxVal = 95f,
                keyframeMap = keyframeMap,
                currentTimeSec = currentTimeSec,
                onChanged = { v -> onPropertyChanged("cropR", v / 100f) },
                onToggleKeyframe = { onToggleKeyframe("cropR") }
            )
            PropRow(
                label = "Top",
                propKey = "cropT",
                value = base.cropT * 100f,
                step = 1f,
                minVal = 0f,
                maxVal = 95f,
                keyframeMap = keyframeMap,
                currentTimeSec = currentTimeSec,
                onChanged = { v -> onPropertyChanged("cropT", v / 100f) },
                onToggleKeyframe = { onToggleKeyframe("cropT") }
            )
            PropRow(
                label = "Bottom",
                propKey = "cropB",
                value = base.cropB * 100f,
                step = 1f,
                minVal = 0f,
                maxVal = 95f,
                keyframeMap = keyframeMap,
                currentTimeSec = currentTimeSec,
                onChanged = { v -> onPropertyChanged("cropB", v / 100f) },
                onToggleKeyframe = { onToggleKeyframe("cropB") }
            )
        }

        // ─── EASING (if keyframe at current time) ───
        val selProps = KeyframeStore.getPropsWithKeyframeAt(keyframeMap, currentTimeSec)
        if (selProps.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            EasingPicker(
                currentEase = KeyframeStore.getEaseAtTime(keyframeMap, currentTimeSec),
                onPick = onSetEase
            )
        }

        // ─── RESET ──────────────────────────────────
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                .pointerInput(Unit) { detectTapGestures { onResetAll() } },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "↺ Reset All Transform",
                color = Color(0xFFFF6B6B), fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Section Label
// ═══════════════════════════════════════════════════════════════
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = Color(0xFF4F9DFF),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

// ═══════════════════════════════════════════════════════════════
//  Property Row — [◆] [Label] [−] [value] [+]
// ═══════════════════════════════════════════════════════════════
@Composable
private fun PropRow(
    label: String,
    propKey: String,
    value: Float,
    step: Float,
    minVal: Float,
    maxVal: Float,
    keyframeMap: Map<String, List<Keyframe>>,
    currentTimeSec: Float,
    onChanged: (Float) -> Unit,
    onToggleKeyframe: () -> Unit
) {
    val hasKfAtTime = KeyframeStore.hasKeyframeAt(keyframeMap, propKey, currentTimeSec)
    val hasAnyKf = KeyframeStore.getKeyframes(keyframeMap, propKey).isNotEmpty()

    // Local text state
    var inputText by remember(value) { mutableStateOf(formatNum(value)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF181818))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ─── Keyframe diamond ──────────────────────
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when {
                        hasKfAtTime -> Color(0xFF4F9DFF).copy(alpha = 0.25f)
                        hasAnyKf -> Color(0xFF4F9DFF).copy(alpha = 0.10f)
                        else -> Color.Transparent
                    }
                )
                .pointerInput(propKey) {
                    detectTapGestures { onToggleKeyframe() }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "◆",
                color = when {
                    hasKfAtTime -> Color(0xFF4F9DFF)
                    hasAnyKf -> Color(0xFF4F9DFF).copy(alpha = 0.55f)
                    else -> Color(0xFF555555)
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // ─── Label ────────────────────────────────
        Text(
            label,
            color = Color(0xFFCCCCCC),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.width(62.dp)
        )

        // ─── Decrease button ──────────────────────
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2A1414))
                .pointerInput(propKey) {
                    detectTapGestures {
                        val nv = (value - step).coerceIn(minVal, maxVal)
                        onChanged(nv)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text("−", color = Color(0xFFFF6B6B), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        // ─── Value input ──────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F0F0F))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = { newText ->
                    val filtered = newText.filter {
                        it.isDigit() || it == '-' || it == '.'
                    }
                    inputText = filtered
                    val parsed = filtered.toFloatOrNull()
                    if (parsed != null) {
                        val clamped = parsed.coerceIn(minVal, maxVal)
                        onChanged(clamped)
                    }
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = Color(0xFF4F9DFF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(Color(0xFF4F9DFF)),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ─── Increase button ──────────────────────
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F2A1A))
                .pointerInput(propKey) {
                    detectTapGestures {
                        val nv = (value + step).coerceIn(minVal, maxVal)
                        onChanged(nv)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Color(0xFF22C55E), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Easing Picker
// ═══════════════════════════════════════════════════════════════
@Composable
private fun EasingPicker(currentEase: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Easing (for keyframe at playhead)",
            color = Color(0xFF888888), fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyframeLibrary.EASING_OPTIONS.forEach { key ->
                val isActive = currentEase == key
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isActive) Color(0xFF4F9DFF) else Color(0xFF181818))
                        .pointerInput(key) { detectTapGestures { onPick(key) } }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        KeyframeLibrary.EASING_LABELS[key] ?: key,
                        color = if (isActive) Color.Black else Color.White,
                        fontSize = 9.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF181818))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("👆", fontSize = 24.sp)
        Text(
            "No clip selected",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Select a video, text or sticker layer first.",
            color = Color(0xFF888888), fontSize = 10.sp
        )
    }
}

private fun formatNum(v: Float): String {
    val r = Math.round(v * 10) / 10f
    return if (abs(r - r.toInt()) < 0.01f) r.toInt().toString() else r.toString()
}
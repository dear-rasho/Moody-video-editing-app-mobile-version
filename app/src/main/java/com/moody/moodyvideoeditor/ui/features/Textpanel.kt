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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.TextState
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.TextEngine

@Composable
fun TextPanel(
    currentText: TextState,
    hasTextClipSelected: Boolean,
    onTextChanged: (TextState) -> Unit,
    onCreateNew: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit
) {
    var subView by remember { mutableStateOf("options") }

    FeaturePanel(title = "📝 Text", onClose = onClose) {
        when (subView) {
            "options" -> OptionsShelf(
                hasText = hasTextClipSelected,
                onOptionSelected = { key ->
                    if (key == "addText") {
                        subView = "addText"
                    } else if (key == "removeText") {
                        onRemove()
                    } else {
                        subView = key
                    }
                }
            )

            "addText" -> AddTextSub(
                initialText = currentText.content,
                onApply = { content ->
                    if (hasTextClipSelected) {
                        onTextChanged(currentText.copy(content = content))
                    } else {
                        onCreateNew()
                        onTextChanged(currentText.copy(content = content))
                    }
                    subView = "options"
                },
                onBack = { subView = "options" }
            )

            "fonts" -> FontsSub(
                current = currentText.fontFamily,
                onSelected = { onTextChanged(currentText.copy(fontFamily = it)) },
                onBack = { subView = "options" }
            )

            "stroke" -> StrokeSub(
                width = currentText.strokeWidth,
                color = currentText.strokeColor,
                onWidthChanged = { onTextChanged(currentText.copy(strokeWidth = it)) },
                onColorChanged = { onTextChanged(currentText.copy(strokeColor = it)) },
                onBack = { subView = "options" }
            )

            "color" -> ColorSub(
                color = currentText.color,
                onChanged = {
                    onTextChanged(
                        currentText.copy(
                            color = it,
                            gradientEnabled = false
                        )
                    )
                },
                onBack = { subView = "options" }
            )

            "gradient" -> GradientSub(
                state = currentText,
                onChanged = { onTextChanged(it) },
                onBack = { subView = "options" }
            )

            "shadows" -> ShadowsSub(
                state = currentText,
                onChanged = { onTextChanged(it) },
                onBack = { subView = "options" }
            )

            "alignment" -> AlignmentSub(
                alignment = currentText.alignment,
                onChanged = { onTextChanged(currentText.copy(alignment = it)) },
                onBack = { subView = "options" }
            )

            "opacity" -> OpacitySub(
                opacity = currentText.opacity,
                onChanged = { onTextChanged(currentText.copy(opacity = it)) },
                onBack = { subView = "options" }
            )

            "animations" -> AnimationsSub(
                current = currentText.animation,
                duration = currentText.animationDuration,
                onSelected = { onTextChanged(currentText.copy(animation = it)) },
                onDurationChanged = { onTextChanged(currentText.copy(animationDuration = it)) },
                onBack = { subView = "options" }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  OPTIONS SHELF — mirrors text.js OPTIONS
// ═══════════════════════════════════════════════════════════════
@Composable
private fun OptionsShelf(hasText: Boolean, onOptionSelected: (String) -> Unit) {
    val options = listOf(
        "addText" to ("➕" to "Add Text"),
        "fonts" to ("🔤" to "Fonts"),
        "stroke" to ("✏️" to "Stroke"),
        "color" to ("🎨" to "Color"),
        "gradient" to ("🌈" to "Gradient"),
        "shadows" to ("🌑" to "Shadows"),
        "alignment" to ("↔️" to "Align"),
        "opacity" to ("👁" to "Opacity"),
        "animations" to ("✨" to "Animations"),
        "removeText" to ("🗑️" to "Remove")
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (key, pair) ->
            OptionCard(
                icon = pair.first,
                label = pair.second,
                onClick = { onOptionSelected(key) }
            )
        }
    }
}

@Composable
private fun OptionCard(icon: String, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(78.dp)
            .height(74.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF181818))
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

// ═══════════════════════════════════════════════════════════════
//  ADD TEXT — mirrors text.js renderAddText()
// ═══════════════════════════════════════════════════════════════
@Composable
private fun AddTextSub(
    initialText: String,
    onApply: (String) -> Unit,
    onBack: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BackRow("Add / Edit Text", onBack)

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Enter text…", color = Color(0xFF666666), fontSize = 12.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            singleLine = false,
            maxLines = 2,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF7C3AED),
                unfocusedBorderColor = Color(0xFF303030)
            )
        )

        ActionButton("✓ Apply", Color(0xFF7C3AED), enabled = text.isNotBlank()) {
            onApply(text)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  FONTS — mirrors text.js renderFonts()
// ═══════════════════════════════════════════════════════════════
@Composable
private fun FontsSub(current: String, onSelected: (String) -> Unit, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BackRow("Fonts (${TextEngine.FONTS.size})", onBack)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextEngine.FONTS.forEach { font ->
                val isActive = current == font
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                        .pointerInput(font) { detectTapGestures { onSelected(font) } }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        font,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  STROKE — mirrors text.js renderStroke()
// ═══════════════════════════════════════════════════════════════
@Composable
private fun StrokeSub(
    width: Float, color: Long,
    onWidthChanged: (Float) -> Unit,
    onColorChanged: (Long) -> Unit,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BackRow("Stroke", onBack)
        SliderRow("Width", width, 0f..30f) { onWidthChanged(it) }
        ColorRow("Color", color) { onColorChanged(it) }
    }
}

// ═══════════════════════════════════════════════════════════════
//  COLOR — mirrors text.js renderColor()
// ═══════════════════════════════════════════════════════════════
@Composable
private fun ColorSub(color: Long, onChanged: (Long) -> Unit, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BackRow("Solid Color", onBack)
        ColorRow("Color", color) { onChanged(it) }
    }
}

// ═══════════════════════════════════════════════════════════════
//  GRADIENT — mirrors text.js renderGradient()
// ═══════════════════════════════════════════════════════════════
@Composable
private fun GradientSub(state: TextState, onChanged: (TextState) -> Unit, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BackRow("Gradient Ramp", onBack)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Enabled",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (state.gradientEnabled) Color(0xFF7C3AED) else Color(0xFF181818))
                    .pointerInput(Unit) { detectTapGestures { onChanged(state.copy(gradientEnabled = !state.gradientEnabled)) } }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (state.gradientEnabled) "ON" else "OFF",
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        ColorRow("Color A", state.gradientColor1) {
            onChanged(state.copy(gradientColor1 = it, gradientEnabled = true))
        }
        ColorRow("Color B", state.gradientColor2) {
            onChanged(state.copy(gradientColor2 = it, gradientEnabled = true))
        }
        SliderRow(
            "Angle",
            state.gradientAngle,
            0f..360f
        ) { onChanged(state.copy(gradientAngle = it)) }
    }
}

// ═══════════════════════════════════════════════════════════════
//  SHADOWS — mirrors text.js renderShadows()
// ═══════════════════════════════════════════════════════════════
@Composable
private fun ShadowsSub(state: TextState, onChanged: (TextState) -> Unit, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BackRow("Shadow", onBack)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Enabled",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (state.shadowEnabled) Color(0xFF7C3AED) else Color(0xFF181818))
                    .pointerInput(Unit) { detectTapGestures { onChanged(state.copy(shadowEnabled = !state.shadowEnabled)) } }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (state.shadowEnabled) "ON" else "OFF",
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        ColorRow("Color", state.shadowColor) {
            onChanged(state.copy(shadowColor = it, shadowEnabled = true))
        }
        SliderRow("Blur", state.shadowBlur, 0f..40f) { onChanged(state.copy(shadowBlur = it)) }
        SliderRow(
            "Offset X",
            state.shadowOffsetX,
            -40f..40f
        ) { onChanged(state.copy(shadowOffsetX = it)) }
        SliderRow(
            "Offset Y",
            state.shadowOffsetY,
            -40f..40f
        ) { onChanged(state.copy(shadowOffsetY = it)) }
    }
}

// ═══════════════════════════════════════════════════════════════
//  ALIGNMENT — mirrors text.js renderAlignment()
// ═══════════════════════════════════════════════════════════════
@Composable
private fun AlignmentSub(alignment: String, onChanged: (String) -> Unit, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BackRow("Alignment", onBack)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "left" to "⬅ Left",
                "center" to "⬌ Center",
                "right" to "➡ Right"
            ).forEach { (key, label) ->
                val isActive = alignment == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                        .pointerInput(key) { detectTapGestures { onChanged(key) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  OPACITY — mirrors text.js renderOpacity()
// ═══════════════════════════════════════════════════════════════
@Composable
private fun OpacitySub(opacity: Float, onChanged: (Float) -> Unit, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BackRow("Opacity", onBack)
        SliderRow("Value", opacity, 0f..100f) { onChanged(it) }
    }
}

// ═══════════════════════════════════════════════════════════════
//  ANIMATIONS — mirrors text.js renderAnimations() (100+)
// ═══════════════════════════════════════════════════════════════
@Composable
private fun AnimationsSub(
    current: String,
    duration: Float,
    onSelected: (String) -> Unit,
    onDurationChanged: (Float) -> Unit,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BackRow("Animations (${TextEngine.ANIMATIONS.size})", onBack)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TextEngine.ANIMATIONS.forEach { anim ->
                val isActive = current == anim
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                        .pointerInput(anim) { detectTapGestures { onSelected(anim) } }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(anim, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        SliderRow("Duration", duration, 0.2f..3f) { onDurationChanged(it) }
    }
}

// ═══════════════════════════════════════════════════════════════
//  SHARED HELPERS
// ═══════════════════════════════════════════════════════════════
@Composable
private fun BackRow(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF181818))
                .pointerInput(Unit) { detectTapGestures { onBack() } },
            contentAlignment = Alignment.Center
        ) {
            Text("‹", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SliderRow(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            color = Color(0xFF888888),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(56.dp)
        )
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF7C3AED),
                activeTrackColor = Color(0xFF7C3AED),
                inactiveTrackColor = Color(0xFF303030)
            )
        )
        Text(
            if (range.endInclusive <= 100f && range.start >= 0f) "${value.toInt()}%"
            else String.format("%.1f", value),
            color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(48.dp)
        )
    }
}

@Composable
private fun ColorRow(label: String, colorLong: Long, onChange: (Long) -> Unit) {
    val presets = listOf(
        0xFFFFFFFF, 0xFFFFD166, 0xFFFF6B6B, 0xFF22C55E,
        0xFF60EFFF, 0xFFFF00FF, 0xFF7C3AED, 0xFF000000
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            color = Color(0xFF888888),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(56.dp)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presets.forEach { p ->
                val isActive = colorLong == p
                Box(
                    modifier = Modifier
                        .size(if (isActive) 30.dp else 26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(p))
                        .pointerInput(p) { detectTapGestures { onChange(p) } }
                )
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, bg: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .pointerInput(enabled) { if (enabled) detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
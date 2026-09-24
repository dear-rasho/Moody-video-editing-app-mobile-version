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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.TextState
import com.moody.moodyvideoeditor.ui.components.ColorPickerField
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.AnimationsEngine
import com.moody.moodyvideoeditor.utils.FontLibrary

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
                onSelect = { key ->
                    if (key == "removeText") onRemove()
                    else subView = key
                }
            )

            "addText" -> AddTextSub(
                initial = currentText.content,
                hasTextClip = hasTextClipSelected,
                onApply = { content ->
                    val newState = currentText.copy(content = content)
                    if (hasTextClipSelected) onTextChanged(newState)
                    else {
                        onCreateNew()
                        onTextChanged(newState)
                    }
                    subView = "options"
                },
                onBack = { subView = "options" }
            )

            "fonts" -> FontsSub(
                current = currentText.fontFamily,
                onPick = { onTextChanged(currentText.copy(fontFamily = it)) },
                onBack = { subView = "options" }
            )

            "stroke" -> StrokeSub(
                width = currentText.strokeWidth,
                color = currentText.strokeColor,
                onWidth = { onTextChanged(currentText.copy(strokeWidth = it)) },
                onColor = { onTextChanged(currentText.copy(strokeColor = it)) },
                onBack = { subView = "options" }
            )

            "color" -> ColorSub(
                color = currentText.color,
                onPick = {
                    onTextChanged(currentText.copy(color = it, gradientEnabled = false))
                },
                onBack = { subView = "options" }
            )

            "gradient" -> GradientSub(
                state = currentText,
                onChange = { onTextChanged(it) },
                onBack = { subView = "options" }
            )

            "shadows" -> ShadowsSub(
                state = currentText,
                onChange = { onTextChanged(it) },
                onBack = { subView = "options" }
            )

            "alignment" -> AlignmentSub(
                current = currentText.alignment,
                onPick = { onTextChanged(currentText.copy(alignment = it)) },
                onBack = { subView = "options" }
            )

            "opacity" -> OpacitySub(
                value = currentText.opacity,
                onChange = { onTextChanged(currentText.copy(opacity = it)) },
                onBack = { subView = "options" }
            )

            "animations" -> AnimationsSub(
                current = currentText.animation,
                duration = currentText.animationDuration,
                onPick = { onTextChanged(currentText.copy(animation = it)) },
                onDuration = { onTextChanged(currentText.copy(animationDuration = it)) },
                onBack = { subView = "options" }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
@Composable
private fun OptionsShelf(hasText: Boolean, onSelect: (String) -> Unit) {
    val options = listOf(
        Triple("addText", "➕", "Add"),
        Triple("fonts", "🔤", "Fonts"),
        Triple("stroke", "✏️", "Stroke"),
        Triple("color", "🎨", "Color"),
        Triple("gradient", "🌈", "Gradient"),
        Triple("shadows", "🌑", "Shadow"),
        Triple("alignment", "↔️", "Align"),
        Triple("opacity", "👁", "Opacity"),
        Triple("animations", "✨", "Anims"),
        Triple("removeText", "🗑️", "Remove")
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (key, icon, label) ->
            Column(
                modifier = Modifier
                    .width(78.dp)
                    .height(74.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF181818))
                    .pointerInput(key) { detectTapGestures { onSelect(key) } }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(icon, fontSize = 22.sp)
                Spacer(Modifier.height(4.dp))
                Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
@Composable
private fun AddTextSub(
    initial: String,
    hasTextClip: Boolean,
    onApply: (String) -> Unit,
    onBack: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BackHeader(title = if (hasTextClip) "Edit Text" else "New Text", onBack = onBack)
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (text.isNotBlank()) Color(0xFF7C3AED) else Color(0xFF3A2A5D))
                .pointerInput(text) {
                    if (text.isNotBlank()) detectTapGestures { onApply(text) }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (hasTextClip) "✓ Update" else "✓ Create Layer",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
@Composable
private fun FontsSub(current: String, onPick: (String) -> Unit, onBack: () -> Unit) {
    var activeCategory by remember { mutableStateOf("all") }

    val fontList = remember(activeCategory) {
        if (activeCategory == "all") FontLibrary.allFonts()
        else FontLibrary.FONT_CATEGORIES[activeCategory] ?: emptyList()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BackHeader("Fonts (${fontList.size})", onBack)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val cats = listOf("all" to "All") +
                    FontLibrary.categories()
                        .map { it to it.replaceFirstChar { c -> c.uppercase() } }
            cats.forEach { (key, label) ->
                val isActive = activeCategory == key
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                        .pointerInput(key) { detectTapGestures { activeCategory = key } }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            fontList.forEach { font ->
                val isActive = current == font
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF2A1F4D) else Color(0xFF181818))
                        .pointerInput(font) { detectTapGestures { onPick(font) } }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = font,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontLibrary.familyFor(font),
                        modifier = Modifier.weight(1f)
                    )
                    if (isActive) {
                        Text(
                            "✓",
                            color = Color(0xFF7C3AED),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
@Composable
private fun StrokeSub(
    width: Float, color: Long,
    onWidth: (Float) -> Unit,
    onColor: (Long) -> Unit,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BackHeader("Stroke", onBack)
        SliderRow("Width", width, 0f..20f, onWidth)
        ColorPickerField(
            label = "Color",
            colorLong = color,
            onChange = onColor
        )
    }
}

// ═══════════════════════════════════════════════════════════════
@Composable
private fun ColorSub(color: Long, onPick: (Long) -> Unit, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BackHeader("Solid Color", onBack)
        ColorPickerField(
            label = "Color",
            colorLong = color,
            onChange = onPick
        )
    }
}

// ═══════════════════════════════════════════════════════════════
@Composable
private fun GradientSub(
    state: TextState,
    onChange: (TextState) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BackHeader("Gradient Ramp", onBack)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Enabled", color = Color(0xFF888888), fontSize = 10.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (state.gradientEnabled) Color(0xFF7C3AED) else Color(0xFF181818))
                    .pointerInput(Unit) {
                        detectTapGestures {
                            onChange(state.copy(gradientEnabled = !state.gradientEnabled))
                        }
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (state.gradientEnabled) "ON" else "OFF",
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        // Preview strip showing current gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            Color(state.gradientColor1),
                            Color(state.gradientColor2)
                        )
                    )
                )
        )

        ColorPickerField(
            label = "Color A",
            colorLong = state.gradientColor1,
            onChange = { onChange(state.copy(gradientColor1 = it, gradientEnabled = true)) }
        )
        ColorPickerField(
            label = "Color B",
            colorLong = state.gradientColor2,
            onChange = { onChange(state.copy(gradientColor2 = it, gradientEnabled = true)) }
        )

        SliderRow("Angle", state.gradientAngle, 0f..360f) {
            onChange(state.copy(gradientAngle = it))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
@Composable
private fun ShadowsSub(
    state: TextState,
    onChange: (TextState) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BackHeader("Shadow", onBack)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Enabled", color = Color(0xFF888888), fontSize = 10.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (state.shadowEnabled) Color(0xFF7C3AED) else Color(0xFF181818))
                    .pointerInput(Unit) {
                        detectTapGestures {
                            onChange(state.copy(shadowEnabled = !state.shadowEnabled))
                        }
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (state.shadowEnabled) "ON" else "OFF",
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        ColorPickerField(
            label = "Color",
            colorLong = state.shadowColor,
            onChange = { onChange(state.copy(shadowColor = it, shadowEnabled = true)) }
        )
        SliderRow("Blur", state.shadowBlur, 0f..40f) { onChange(state.copy(shadowBlur = it)) }
        SliderRow(
            "Offset X",
            state.shadowOffsetX,
            -40f..40f
        ) { onChange(state.copy(shadowOffsetX = it)) }
        SliderRow(
            "Offset Y",
            state.shadowOffsetY,
            -40f..40f
        ) { onChange(state.copy(shadowOffsetY = it)) }
    }
}

// ═══════════════════════════════════════════════════════════════
@Composable
private fun AlignmentSub(current: String, onPick: (String) -> Unit, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BackHeader("Alignment", onBack)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("left" to "⬅ Left", "center" to "⬌ Center", "right" to "➡ Right")
                .forEach { (key, label) ->
                    val isActive = current == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                            .pointerInput(key) { detectTapGestures { onPick(key) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
@Composable
private fun OpacitySub(value: Float, onChange: (Float) -> Unit, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BackHeader("Opacity", onBack)
        SliderRow("Value", value, 0f..100f, onChange)
    }
}

// ═══════════════════════════════════════════════════════════════
@Composable
private fun AnimationsSub(
    current: String,
    duration: Float,
    onPick: (String) -> Unit,
    onDuration: (Float) -> Unit,
    onBack: () -> Unit
) {
    var activeCategory by remember { mutableStateOf("basic") }
    val categories = AnimationsEngine.CATEGORIES

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BackHeader("Animations (${AnimationsEngine.ALL_ANIMATIONS.size})", onBack)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { cat ->
                val isActive = activeCategory == cat.key
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                        .pointerInput(cat.key) { detectTapGestures { activeCategory = cat.key } }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${cat.label} (${cat.animations.size})",
                        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        val cat = categories.firstOrNull { it.key == activeCategory } ?: categories[0]
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 140.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            cat.animations.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.forEach { anim ->
                        val isActive = current == anim.key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                                .pointerInput(anim.key) { detectTapGestures { onPick(anim.key) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                anim.label,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        SliderRow("Duration", duration, 0.2f..3f, onDuration)
    }
}

// ═══════════════════════════════════════════════════════════════
//  SHARED
// ═══════════════════════════════════════════════════════════════
@Composable
private fun BackHeader(title: String, onBack: () -> Unit) {
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
    label: String, value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label, color = Color(0xFF888888),
            fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(60.dp)
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
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
            color = Color.White, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp)
        )
    }
}
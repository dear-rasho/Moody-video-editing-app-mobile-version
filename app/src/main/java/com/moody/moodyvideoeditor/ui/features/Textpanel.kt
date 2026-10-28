package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.moody.moodyvideoeditor.data.TextState
import com.moody.moodyvideoeditor.ui.components.ColorPickerField
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.ui.components.FontPreviewRow
import com.moody.moodyvideoeditor.utils.FontLibrary
import com.moody.moodyvideoeditor.utils.CaptionPresetLibrary

// ═══════════════════════════════════════════════════════════════
//  MAIN TEXT PANEL
// ═══════════════════════════════════════════════════════════════

@Composable
fun TextPanel(
    currentText: TextState,
    hasTextClipSelected: Boolean,
    startInEdit: Boolean = false,
    onTextChanged: (TextState) -> Unit,
    onAlignmentChanged: ((String) -> Unit)? = null,
    onDraftTextChanged: (String) -> Unit = {},
    onCreateNew: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit
) {
    var subView by remember(startInEdit) {
        mutableStateOf(if (startInEdit) "addText" else "options")
    }
    // 🆕 Scroll state ko yahan rakho taaki back aane pe reset na ho
    val optionsScrollState = rememberScrollState()
    var draftClipCreated by remember(hasTextClipSelected) {
        mutableStateOf(hasTextClipSelected)
    }

    FeaturePanel(title = "📝 Text", onClose = onClose) {
        when (subView) {
            "options" -> OptionsShelf(
                hasText = hasTextClipSelected,
                scrollState = optionsScrollState,
                onSelect = { key ->
                    if (key == "removeText") onRemove()
                    else subView = key
                }
            )

            "addText" -> AddTextSub(
                initial = currentText.content,
                hasTextClip = hasTextClipSelected,
                onDraftChanged = { content ->
                    if (!draftClipCreated) {
                        onCreateNew()
                        draftClipCreated = true
                    }
                    onDraftTextChanged(content)
                },
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

            "size" -> SliderSub(
                title = "Font Size",
                value = currentText.fontSize.toFloat(),
                range = 8f..200f,
                suffix = "pt",
                onChange = { onTextChanged(currentText.copy(fontSize = it.toInt())) },
                onBack = { subView = "options" }
            )

            "letterSpacing" -> SliderSub(
                title = "Character Gaps",
                value = currentText.letterSpacing,
                range = -5f..30f,
                suffix = "sp",
                onChange = { onTextChanged(currentText.copy(letterSpacing = it)) },
                onBack = { subView = "options" }
            )

            "lineHeight" -> SliderSub(
                title = "Line Height",
                value = currentText.lineHeight,
                range = 0.8f..10f,
                suffix = "x",
                decimals = 2,
                onChange = {
                    onTextChanged(currentText.copy(lineHeight = it.coerceIn(0.1f, 200f)))
                },
                onBack = { subView = "options" }
            )

            "tracking" -> SliderSub(
                title = "Tracking",
                value = currentText.tracking,
                range = -10f..50f,
                suffix = "",
                onChange = { onTextChanged(currentText.copy(tracking = it)) },
                onBack = { subView = "options" }
            )

            "maxWidth" -> SliderSub(
                title = "Text Width",
                value = currentText.maxWidth,
                range = 30f..100f,
                suffix = "%",
                onChange = { onTextChanged(currentText.copy(maxWidth = it)) },
                onBack = { subView = "options" }
            )

            "stroke" -> StrokeSub(
                enabled = currentText.strokeEnabled,
                width = currentText.strokeWidth,
                color = currentText.strokeColor,
                onEnabled = { onTextChanged(currentText.copy(strokeEnabled = it)) },
                onWidth = { onTextChanged(currentText.copy(strokeWidth = it)) },
                onColor = { onTextChanged(currentText.copy(strokeColor = it)) },
                onBack = { subView = "options" }
            )

            "glow" -> GlowSub(
                enabled = currentText.glowEnabled,
                color = currentText.glowColor,
                radius = currentText.glowRadius,
                onEnabled = { onTextChanged(currentText.copy(glowEnabled = it)) },
                onColor = { onTextChanged(currentText.copy(glowColor = it)) },
                onRadius = { onTextChanged(currentText.copy(glowRadius = it)) },
                onBack = { subView = "options" }
            )

            "color" -> ColorSub(
                color = currentText.color,
                onPick = { onTextChanged(currentText.copy(color = it)) },
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

            "background" -> BackgroundSub(
                state = currentText,
                onChange = { onTextChanged(it) },
                onBack = { subView = "options" }
            )

            "captionPresets" -> CaptionPresetPicker(
                selectedPresetId = currentText.captionPresetId,
                highlightColor = currentText.captionHighlightColor,
                onPresetSelected = { presetId ->
                    val styled = CaptionPresetLibrary.applyTo(
                        currentText.content,
                        presetId,
                        currentText.captionHighlightColor
                    )
                    onTextChanged(
                        currentText.copy(
                            fontFamily = styled.fontFamily,
                            fontSize = styled.fontSize,
                            fontWeight = styled.fontWeight,
                            color = styled.color,
                            glowEnabled = styled.glowEnabled,
                            glowColor = styled.glowColor,
                            glowRadius = styled.glowRadius,
                            strokeEnabled = styled.strokeEnabled,
                            strokeWidth = styled.strokeWidth,
                            strokeColor = styled.strokeColor,
                            alignment = styled.alignment,
                            positionY = styled.positionY,
                            maxWidth = styled.maxWidth,
                            lineHeight = styled.lineHeight,
                            captionPresetId = presetId,
                            captionPresetAnimation = styled.captionPresetAnimation,
                            captionPresetAnimationDuration = styled.captionPresetAnimationDuration
                        )
                    )
                },
                onHighlightColorChanged = {
                    onTextChanged(currentText.copy(captionHighlightColor = it))
                },
                onBack = { subView = "options" }
            )

            "textAnimations" -> TextAnimationsSub(
                current = currentText.animation,
                duration = currentText.animationDuration,
                onAnimationSelected = {
                    onTextChanged(currentText.copy(animation = it))
                },
                onDurationChanged = {
                    onTextChanged(currentText.copy(animationDuration = it))
                },
                onBack = { subView = "options" }
            )

            "alignment" -> AlignmentSub(
                current = currentText.alignment,
                onPick = {
                    if (onAlignmentChanged != null) onAlignmentChanged(it)
                    else onTextChanged(currentText.copy(alignment = it))
                },
                onBack = { subView = "options" }
            )

            "opacity" -> SliderSub(
                title = "Opacity",
                value = currentText.opacity,
                range = 0f..100f,
                suffix = "%",
                onChange = { onTextChanged(currentText.copy(opacity = it)) },
                onBack = { subView = "options" }
            )
        }
    }
}

@Composable
private fun TextAnimationsSub(
    current: String,
    duration: Float,
    onAnimationSelected: (String) -> Unit,
    onDurationChanged: (Float) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .verticalScrollWithScrollbar(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BackHeader("🎞️ Text Animation", onBack)
        SliderRow(
            label = "Duration",
            value = duration,
            range = 0.1f..3f,
            onChange = onDurationChanged
        )
        com.moody.moodyvideoeditor.utils.AnimationsEngine.CATEGORIES.forEach { category ->
            Text(
                category.label,
                color = Color(0xFFA78BFA),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScrollWithTwoFinger(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                category.animations.forEach { animation ->
                    val selected = current == animation.key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) Color(0xFF7C3AED) else Color(0xFF181818)
                            )
                            .clickable { onAnimationSelected(animation.key) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            animation.label,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  OPTIONS SHELF
// ═══════════════════════════════════════════════════════════════

@Composable
private fun OptionsShelf(
    hasText: Boolean,
    scrollState: androidx.compose.foundation.ScrollState,
    onSelect: (String) -> Unit
) {
    val options = listOf(
        Triple("addText", "➕", "Add"),
        Triple("fonts", "Aa", "Fonts"),
        Triple("size", "aA", "Size"),
        Triple("letterSpacing", "ab", "Spacing"),
        Triple("lineHeight", "ab", "Lines"),
        Triple("stroke", "A", "Stroke"),
        Triple("glow", "A", "Glow"),
        Triple("color", "ABC", "Color"),
        Triple("gradient", "ABC", "Gradient"),
        Triple("shadows", "A", "Shadow"),
        Triple("background", "A", "BG"),
        Triple("alignment", "🔀", "Align"),
        Triple("opacity", "A", "Opacity"),
        Triple("textAnimations", "🎞️", "Animation"),
        Triple("captionPresets", "🎤", "Presets"),
        Triple("removeText", "🗑️", "Remove")
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScrollWithTwoFinger(scrollState), // 🆕 Passed state use karo
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
                TextOptionIcon(key = key, defaultIcon = icon)
                Spacer(Modifier.height(6.dp))
                Text(
                    label,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
// 🆕 Custom Icon Renderer
@Composable
private fun TextOptionIcon(key: String, defaultIcon: String) {
    Box(
        modifier = Modifier
            .size(34.dp) // Fixed size background for all icons
            .background(Color(0xFF0F0F0F), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        when (key) {
            // ─── Fonts (Aa in Serif) ───
            "fonts" -> Text(
                "Aa",
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold
            )

            // ─── Size (a ➔ A) ───
            "size" -> Row(verticalAlignment = Alignment.Bottom) {
                Text("a", color = Color.White, fontSize = 10.sp)
                Text("➔", color = Color.Gray, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 1.dp))
                Text("A", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            // ─── Spacing (a ↔ b) ───
            "letterSpacing" -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("a", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("↔", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 1.dp))
                Text("b", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            // ─── Lines (a ↕ b) ───
            "lineHeight" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("a", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("↕", color = Color.Gray, fontSize = 8.sp)
                Text("b", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // ─── Stroke (White A with Black outline) ───
            "stroke" -> Box(contentAlignment = Alignment.Center) {
                // Black Outline
                Text(
                    "A",
                    color = Color.Black,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    style = androidx.compose.ui.text.TextStyle(
                        drawStyle = Stroke(
                            width = 6f,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )
                )
                // White Fill
                Text(
                    "A",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // ─── Glow (Blue Blur) ───
            "glow" -> Text(
                "A",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color(0xFF00E5FF),
                        blurRadius = 8f
                    )
                )
            )

            // ─── Color (Multi-color letters) ───
            "color" -> {
                val text = androidx.compose.ui.text.buildAnnotatedString {
                    withStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFFF0066))) { append("A") }
                    withStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFF00E5FF))) { append("B") }
                    withStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFFFCC00))) { append("C") }
                }
                Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            // ─── Gradient (Pink to Blue Gradient) ───
            "gradient" -> Text(
                "ABC",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFFFF0066), Color(0xFF00E5FF))
                    )
                )
            )

            // ─── Shadow (Dark drop shadow) ───
            "shadows" -> Text(
                "A",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black,
                        offset = Offset(3f, 3f),
                        blurRadius = 4f
                    )
                )
            )

            // ─── Background (Word with colored box) ───
            "background" -> Box(
                modifier = Modifier
                    .background(Color(0xFFFF0066), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text("A", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            // ─── Opacity (Full vs Half) ───
            "opacity" -> Row {
                Text("A", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("A", color = Color.White.copy(alpha = 0.3f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            // ─── Default icons ───
            else -> Text(defaultIcon, fontSize = 18.sp)
        }
    }
}
// ═══════════════════════════════════════════════════════════════
//  ADD TEXT SUB
// ═══════════════════════════════════════════════════════════════

@Composable
private fun AddTextSub(
    initial: String,
    hasTextClip: Boolean,
    onDraftChanged: (String) -> Unit,
    onApply: (String) -> Unit,
    onBack: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BackHeader(title = if (hasTextClip) "Edit Text" else "New Text", onBack = onBack)
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onDraftChanged(it)
            },
            placeholder = {
                Text("Enter text…", color = Color(0xFF666666), fontSize = 12.sp)
            },
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
                .background(
                    if (text.isNotBlank()) Color(0xFF7C3AED) else Color(0xFF3A2A5D)
                )
                .pointerInput(text) {
                    if (text.isNotBlank()) detectTapGestures { onApply(text) }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (hasTextClip) "✓ Update" else "✓ Create Layer",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  FONTS SUB
// ═══════════════════════════════════════════════════════════════

@Composable
private fun FontsSub(current: String, onPick: (String) -> Unit, onBack: () -> Unit) {
    var activeCategory by remember { mutableStateOf("english") }

    val fontList = remember(activeCategory) {
        FontLibrary.FONT_CATEGORIES[activeCategory] ?: emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .verticalScrollWithScrollbar(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BackHeader("Fonts (${FontLibrary.allFonts().size})", onBack)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScrollWithTwoFinger(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FontLibrary.categories().forEach { key ->
                val label = FontLibrary.categoryLabel(key)
                val isActive = activeCategory == key

                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isActive) Color(0xFF7C3AED) else Color(0xFF181818)
                        )
                        .pointerInput(key) {
                            detectTapGestures { activeCategory = key }
                        }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScrollWithScrollbar(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            fontList.forEach { font ->
                FontPreviewRow(
                    fontName = font,
                    isActive = current == font,
                    onPick = { onPick(font) }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  STROKE SUB
// ═══════════════════════════════════════════════════════════════

@Composable
private fun StrokeSub(
    enabled: Boolean,
    width: Float,
    color: Long,
    onEnabled: (Boolean) -> Unit,
    onWidth: (Float) -> Unit,
    onColor: (Long) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScrollWithScrollbar(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BackHeader("Stroke / Outline", onBack)
        ToggleRow("Enabled", enabled, onEnabled)
        SliderRow("Width", width, 0f..20f, onWidth)
        ColorPickerField(
            label = "Color",
            colorLong = color,
            onChange = onColor
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  GLOW SUB
// ═══════════════════════════════════════════════════════════════

@Composable
private fun GlowSub(
    enabled: Boolean,
    color: Long,
    radius: Float,
    onEnabled: (Boolean) -> Unit,
    onColor: (Long) -> Unit,
    onRadius: (Float) -> Unit,
    onBack: () -> Unit
) {
    val latestOnEnabled by rememberUpdatedState(onEnabled)
    val latestOnColor by rememberUpdatedState(onColor)
    val latestOnRadius by rememberUpdatedState(onRadius)

    var radiusText by remember { mutableStateOf(String.format("%.0f", radius)) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(radius, isFocused) {
        if (!isFocused) radiusText = String.format("%.0f", radius)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScrollWithScrollbar(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BackHeader("Glow / Neon", onBack)
        ToggleRow("Enabled", enabled) { latestOnEnabled(it) }

        Text(
            "Radius — drag slider or type exact value (max 500)",
            color = Color(0xFF888888),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Radius",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(60.dp)
            )

            Slider(
                value = radius.coerceIn(0f, 500f),
                onValueChange = {
                    latestOnRadius(it)
                    radiusText = String.format("%.0f", it)
                },
                valueRange = 0f..500f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF7C3AED),
                    activeTrackColor = Color(0xFF7C3AED),
                    inactiveTrackColor = Color(0xFF303030)
                )
            )

            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF181818))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = radiusText,
                    onValueChange = { input ->
                        val filtered = input.filter {
                            it.isDigit() || it == '.' || it == '-'
                        }
                        radiusText = filtered
                        filtered.toFloatOrNull()?.takeIf { it.isFinite() }?.let {
                            latestOnRadius(it.coerceIn(0f, 500f))
                        }
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(Color(0xFF7C3AED)),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { state ->
                            isFocused = state.isFocused
                            if (!state.isFocused) {
                                radiusText.toFloatOrNull()
                                    ?.takeIf { it.isFinite() }
                                    ?.let { latestOnRadius(it.coerceIn(0f, 500f)) }
                            }
                        }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                15f to "Soft",
                40f to "Normal",
                100f to "Strong",
                250f to "Extreme"
            ).forEach { (presetVal, presetLabel) ->
                val isActive = kotlin.math.abs(radius - presetVal) < 8f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isActive) Color(0xFF7C3AED)
                            else Color(0xFF181818)
                        )
                        .clickable {
                            latestOnRadius(presetVal)
                            radiusText = String.format("%.0f", presetVal)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        presetLabel,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        ColorPickerField(
            label = "Color",
            colorLong = color,
            onChange = { latestOnColor(it) }
        )

        Text(
            "💡 Radius 0-500. Higher = bigger bloom. Try Extreme (250) for epic neon.",
            color = Color(0xFF666666),
            fontSize = 9.sp,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  COLOR SUB
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
//  GRADIENT SUB
// ═══════════════════════════════════════════════════════════════

@Composable
private fun GradientSub(
    state: TextState,
    onChange: (TextState) -> Unit,
    onBack: () -> Unit
) {
    val latestState by rememberUpdatedState(state)
    val latestOnChange by rememberUpdatedState(onChange)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScrollWithScrollbar(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BackHeader("Gradient Ramp", onBack)

        ToggleRow("Enabled", state.gradientEnabled) {
            latestOnChange(latestState.copy(gradientEnabled = it))
        }

        GradientRampControl(
            startX = state.gradientStartX,
            startY = state.gradientStartY,
            endX = state.gradientEndX,
            endY = state.gradientEndY,
            color1 = Color(state.gradientColor1),
            color2 = Color(state.gradientColor2),
            onStartChange = { x, y ->
                latestOnChange(latestState.copy(gradientStartX = x, gradientStartY = y))
            },
            onEndChange = { x, y ->
                latestOnChange(latestState.copy(gradientEndX = x, gradientEndY = y))
            }
        )

        key("gradA") {
            ColorPickerField(
                label = "Color A",
                colorLong = state.gradientColor1,
                onChange = { newColor ->
                    latestOnChange(
                        latestState.copy(
                            gradientColor1 = newColor,
                            gradientEnabled = true
                        )
                    )
                }
            )
        }
        key("gradB") {
            ColorPickerField(
                label = "Color B",
                colorLong = state.gradientColor2,
                onChange = { newColor ->
                    latestOnChange(
                        latestState.copy(
                            gradientColor2 = newColor,
                            gradientEnabled = true
                        )
                    )
                }
            )
        }

    }
}

@Composable
private fun GradientRampControl(
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    color1: Color,
    color2: Color,
    onStartChange: (Float, Float) -> Unit,
    onEndChange: (Float, Float) -> Unit
) {
    var activeHandle by remember { mutableStateOf(0) }
    val latestStartX by rememberUpdatedState(startX)
    val latestStartY by rememberUpdatedState(startY)
    val latestEndX by rememberUpdatedState(endX)
    val latestEndY by rememberUpdatedState(endY)
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .height(88.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF181818))
                .pointerInput(Unit) {
                    fun updateGradientHandle(position: Offset) {
                        var x = (position.x / size.width).coerceIn(0f, 1f)
                        val y = (position.y / size.height).coerceIn(0f, 1f)
                        val otherX = if (activeHandle == 0) latestEndX else latestStartX
                        val otherY = if (activeHandle == 0) latestEndY else latestStartY
                        if (kotlin.math.abs(x - otherX) < 0.01f && kotlin.math.abs(y - otherY) < 0.01f) {
                            x = if (otherX <= 0.98f) otherX + 0.02f else otherX - 0.02f
                        }
                        if (activeHandle == 0) onStartChange(x, y) else onEndChange(x, y)
                    }

                    detectDragGestures(
                        onDragStart = { position ->
                            val start = Offset(latestStartX * size.width, latestStartY * size.height)
                            val end = Offset(latestEndX * size.width, latestEndY * size.height)
                            activeHandle = if ((position - start).getDistance() <= (position - end).getDistance()) 0 else 1
                            updateGradientHandle(position)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            updateGradientHandle(change.position)
                        }
                    )
                }
        ) {
            val start = Offset(startX * size.width, startY * size.height)
            val end = Offset(endX * size.width, endY * size.height)
            drawRect(Brush.linearGradient(listOf(color1, color2), start = start, end = end))
            drawLine(Color.White.copy(alpha = 0.75f), start, end, strokeWidth = 2.dp.toPx())
            drawCircle(Color(0xFFFF4F9A), radius = 9.dp.toPx(), center = start)
            drawCircle(Color.White, radius = 9.dp.toPx(), center = start, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            drawCircle(Color(0xFF54B6FF), radius = 9.dp.toPx(), center = end)
            drawCircle(Color.White, radius = 9.dp.toPx(), center = end, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  SHADOWS SUB
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
            .verticalScrollWithScrollbar(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BackHeader("Shadow", onBack)

        ToggleRow("Enabled", state.shadowEnabled) {
            onChange(state.copy(shadowEnabled = it))
        }

        ColorPickerField(
            label = "Color",
            colorLong = state.shadowColor,
            onChange = { onChange(state.copy(shadowColor = it, shadowEnabled = true)) }
        )
        SliderRow("Blur", state.shadowBlur, 0f..40f) {
            onChange(state.copy(shadowBlur = it))
        }
        SliderRow("Offset X", state.shadowOffsetX, -40f..40f) {
            onChange(state.copy(shadowOffsetX = it))
        }
        SliderRow("Offset Y", state.shadowOffsetY, -40f..40f) {
            onChange(state.copy(shadowOffsetY = it))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  ALIGNMENT SUB
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
                            .background(
                                if (isActive) Color(0xFF7C3AED) else Color(0xFF181818)
                            )
                            .pointerInput(key) {
                                detectTapGestures { onPick(key) }
                            },
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
//  🆕 BACKGROUND SUB — 5 types + solid/gradient
// ═══════════════════════════════════════════════════════════════

@Composable
private fun BackgroundSub(
    state: TextState,
    onChange: (TextState) -> Unit,
    onBack: () -> Unit
) {
    val latest by rememberUpdatedState(state)
    val latestOnChange by rememberUpdatedState(onChange)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 340.dp)
            .verticalScrollWithScrollbar(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BackHeader("🟦 Text Background", onBack)

        ToggleRow("Enable Background", state.backgroundEnabled) {
            latestOnChange(latest.copy(backgroundEnabled = it))
        }

        if (!state.backgroundEnabled) {
            Text(
                "Enable to see background styles",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
            return@Column
        }

        Text(
            "Style",
            color = Color(0xFF888888), fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScrollWithTwoFinger(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "rounded" to "▢ Rounded",
                "character" to "🅰️ Char",
                "outline" to "◯ Outline"
            ).forEach { (key, label) ->
                val active = state.backgroundType == key
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (active) Color(0xFF7C3AED) else Color(0xFF181818)
                        )
                        .pointerInput(key) {
                            detectTapGestures {
                                latestOnChange(latest.copy(backgroundType = key))
                            }
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label, color = Color.White, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Fill",
                color = Color(0xFF888888), fontSize = 10.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp)
            )
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (!state.backgroundGradientEnabled) Color(0xFF7C3AED)
                        else Color(0xFF181818)
                    )
                    .pointerInput(Unit) {
                        detectTapGestures {
                            latestOnChange(
                                latest.copy(backgroundGradientEnabled = false)
                            )
                        }
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Solid", color = Color.White, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (state.backgroundGradientEnabled) Color(0xFF7C3AED)
                        else Color(0xFF181818)
                    )
                    .pointerInput(Unit) {
                        detectTapGestures {
                            latestOnChange(
                                latest.copy(backgroundGradientEnabled = true)
                            )
                        }
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Gradient", color = Color.White, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        ColorPickerField(
            label = if (state.backgroundGradientEnabled) "Color A" else "Color",
            colorLong = state.backgroundColor1,
            onChange = { latestOnChange(latest.copy(backgroundColor1 = it)) }
        )

        if (state.backgroundGradientEnabled) {
            ColorPickerField(
                label = "Color B",
                colorLong = state.backgroundColor2,
                onChange = { latestOnChange(latest.copy(backgroundColor2 = it)) }
            )

            SliderRow(
                "Angle",
                state.backgroundGradientAngle,
                0f..360f
            ) { latestOnChange(latest.copy(backgroundGradientAngle = it)) }
        }

        SliderRow("Pad X", state.backgroundPaddingX, 0f..60f) {
            latestOnChange(latest.copy(backgroundPaddingX = it))
        }
        SliderRow("Pad Y", state.backgroundPaddingY, 0f..60f) {
            latestOnChange(latest.copy(backgroundPaddingY = it))
        }

        if (state.backgroundType in listOf("rounded", "outline")) {
            SliderRow("Corner", state.backgroundCornerRadius, 0f..50f) {
                latestOnChange(latest.copy(backgroundCornerRadius = it))
            }
        }
        SliderRow("Opacity", state.backgroundOpacity, 0f..100f) {
            latestOnChange(latest.copy(backgroundOpacity = it))
        }

        Text(
            "Quick Colors",
            color = Color(0xFF888888), fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScrollWithTwoFinger(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                0xFF000000, 0xFFFFFFFF, 0xFFFF0066, 0xFF00E5FF,
                0xFFFFCC00, 0xFF22C55E, 0xFF7C3AED, 0xFFFF6B00
            ).forEach { c ->
                val active = state.backgroundColor1 == c
                Box(
                    modifier = Modifier
                        .size(if (active) 34.dp else 30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(c))
                        .then(
                            if (active) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = Color.White,
                                    shape = RoundedCornerShape(8.dp)
                                )
                            } else Modifier
                        )
                        .pointerInput(c) {
                            detectTapGestures {
                                latestOnChange(latest.copy(backgroundColor1 = c))
                            }
                        }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  SHARED COMPONENTS
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SliderSub(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    decimals: Int = 0,
    onChange: (Float) -> Unit,
    onBack: () -> Unit
) {
    val formattedValue = if (decimals > 0) {
        String.format("%.${decimals}f", value)
    } else {
        value.toInt().toString()
    }
    var textValue by remember { mutableStateOf(formattedValue) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(formattedValue, isFocused) {
        if (!isFocused) textValue = formattedValue
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BackHeader(title, onBack)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
            Row(
                modifier = Modifier
                    .width(100.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF181818))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                BasicTextField(
                    value = textValue,
                    onValueChange = { input ->
                        val filtered = input.filter {
                            it.isDigit() || it == '-' || it == '.' || it == '+'
                        }
                        textValue = filtered
                        filtered.toFloatOrNull()?.takeIf { it.isFinite() }?.let(onChange)
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    ),
                    cursorBrush = SolidColor(Color(0xFFFFD166)),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focusState ->
                            isFocused = focusState.isFocused
                            if (!focusState.isFocused) {
                                textValue.toFloatOrNull()
                                    ?.takeIf { it.isFinite() }
                                    ?.let(onChange)
                            }
                        }
                )
                Text(
                    suffix,
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        Text(
            "Range ${range.start.toInt()} – ${range.endInclusive.toInt()}$suffix",
            color = Color(0xFF666666),
            fontSize = 9.sp,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = Color(0xFF888888),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (checked) Color(0xFF7C3AED) else Color(0xFF181818)
                )
                .pointerInput(checked) {
                    detectTapGestures { onChange(!checked) }
                }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (checked) "ON" else "OFF",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BackHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF181818))
                .pointerInput(Unit) {
                    detectTapGestures { onBack() }
                },
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
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    var textValue by remember { mutableStateOf(String.format("%.1f", value)) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(value, isFocused) {
        if (!isFocused) textValue = String.format("%.1f", value)
    }

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
        Box(
            modifier = Modifier
                .width(68.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF181818))
                .padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = textValue,
                onValueChange = { input ->
                    val filtered = input.filter {
                        it.isDigit() || it == '-' || it == '.' || it == '+'
                    }
                    textValue = filtered
                    filtered.toFloatOrNull()?.takeIf { it.isFinite() }?.let(onChange)
                },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(Color(0xFFFFD166)),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        if (!focusState.isFocused) {
                            textValue.toFloatOrNull()
                                ?.takeIf { it.isFinite() }
                                ?.let(onChange)
                        }
                    }
            )
        }
    }
}

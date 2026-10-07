package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════
//  EFFECT PROPERTY ROW
//  Slider + Custom input + Keyframe button
//  User can type any exact value in the input field.
// ═══════════════════════════════════════════════════════════════

@Composable
fun EffectPropertyRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    decimals: Int = 3,
    unit: String = "",
    hasKeyframe: Boolean = false,
    hasAnyKeyframe: Boolean = false,
    onValueChange: (Float) -> Unit,
    onToggleKeyframe: () -> Unit
) {
    fun format(v: Float): String = if (decimals == 0) {
        v.toInt().toString()
    } else {
        "%.${decimals}f".format(v)
    }

    var textValue by remember { mutableStateOf(format(value)) }
    var isFocused by remember { mutableStateOf(false) }

    // Sync when value changes externally (slider drag)
    LaunchedEffect(value, isFocused) {
        if (!isFocused) {
            textValue = format(value)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ─── ◆ Keyframe Button ───
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(
                    when {
                        hasKeyframe -> Color(0xFF4F9DFF).copy(alpha = 0.25f)
                        hasAnyKeyframe -> Color(0xFF4F9DFF).copy(alpha = 0.10f)
                        else -> Color.Transparent
                    }
                )
                .pointerInput(label) {
                    detectTapGestures { onToggleKeyframe() }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "◆",
                color = when {
                    hasKeyframe -> Color(0xFF4F9DFF)
                    hasAnyKeyframe -> Color.White
                    else -> Color.White.copy(alpha = 0.65f)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // ─── Label ───
        Text(
            label,
            color = Color(0xFFCCCCCC),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.width(62.dp)
        )

        // ─── Slider ───
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = {
                onValueChange(it)
                textValue = format(it)
            },
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF7C3AED),
                activeTrackColor = Color(0xFF7C3AED),
                inactiveTrackColor = Color(0xFF303030)
            )
        )

        // ─── Custom Input ───
        Box(
            modifier = Modifier
                .width(70.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF0F0F0F))
                .border(
                    if (isFocused) 1.dp else 0.dp,
                    if (isFocused) Color(0xFF7C3AED) else Color.Transparent,
                    RoundedCornerShape(5.dp)
                )
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = textValue,
                onValueChange = { newText ->
                    val filtered = newText.filter {
                        it.isDigit() || it == '.' || it == '-'
                    }
                    textValue = filtered

                    filtered.toFloatOrNull()?.let { parsed ->
                        if (parsed.isFinite()) {
                            val clamped = parsed.coerceIn(
                                range.start, range.endInclusive
                            )
                            onValueChange(clamped)
                        }
                    }
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = Color(0xFF4F9DFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(Color(0xFF4F9DFF)),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (decimals == 0)
                        KeyboardType.Number
                    else KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val parsed = textValue.toFloatOrNull() ?: value
                        val clamped = parsed.coerceIn(
                            range.start, range.endInclusive
                        )
                        textValue = format(clamped)
                        onValueChange(clamped)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        isFocused = state.isFocused
                        if (!state.isFocused) {
                            val parsed = textValue.toFloatOrNull()
                            if (parsed != null && parsed.isFinite()) {
                                val clamped = parsed.coerceIn(
                                    range.start, range.endInclusive
                                )
                                textValue = format(clamped)
                                onValueChange(clamped)
                            } else {
                                textValue = format(value)
                            }
                        }
                    }
            )
        }

        // ─── Unit ───
        if (unit.isNotBlank()) {
            Text(
                unit,
                color = Color(0xFF888888),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(14.dp)
            )
        } else {
            Box(modifier = Modifier.width(14.dp))
        }
    }
}
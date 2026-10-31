package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.ui.components.ColorPickerField
import com.moody.moodyvideoeditor.utils.AnimationsEngine
import com.moody.moodyvideoeditor.utils.CaptionPresetLibrary
import com.moody.moodyvideoeditor.utils.FontLibrary

@Composable
fun CaptionPresetPicker(
    selectedPresetId: String?,
    highlightColor: Long,
    onPresetSelected: (String) -> Unit,
    onHighlightColorChanged: (Long) -> Unit,
    selectedTemplateId: String? = null,
    onTemplateSelected: (String) -> Unit = {},
    includeTextStyles: Boolean = false,
    includeTypographyAnimations: Boolean = false,
    onTypographyAnimationSelected: (String) -> Unit = {},
    title: String = "Choose a caption preset",
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .verticalScrollWithScrollbar(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val previewTransition = rememberInfiniteTransition(label = "captionPresetPreview")
        val previewClock by previewTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "captionPresetPreviewClock"
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                title,
                color = Color(0xFFAAAAAA),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 10.dp)
            )
            TextButton(onClick = onBack) { Text("Done") }
        }
        CaptionPresetLibrary.presets.chunked(2).forEachIndexed { rowIndex, rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowPresets.forEachIndexed { columnIndex, preset ->
                    val active = preset.id == selectedPresetId
                    val presetIndex = rowIndex * 2 + columnIndex
                    val animationElapsed =
                        ((previewClock + presetIndex * 0.13f) % 0.9f + 0.12f) % 0.9f
                    val animationProgress = (animationElapsed / 0.45f).coerceIn(0f, 1f)
                    val sample = CaptionPresetLibrary.visibleText(
                        "Moody Editing App",
                        preset.animation,
                        animationProgress
                    )
                    val preview = buildAnnotatedString {
                        append(sample)
                        CaptionPresetLibrary.colorRanges(
                            text = sample,
                            presetId = preset.id,
                            highlightColor = highlightColor,
                            elapsedMs = (animationElapsed * 1000f).toLong(),
                            durationMs = 900L
                        ).forEach { range ->
                            addStyle(
                                SpanStyle(color = Color(range.color)),
                                range.start,
                                range.end
                            )
                        }
                    }
                    val frame = AnimationsEngine.computeFrame(
                        animation = preset.animation,
                        progress = animationProgress,
                        elapsedSec = animationElapsed
                    )
                    val previewModifier = Modifier
                        .blur(frame.blurRadiusPx.coerceIn(0f, 20f).dp)
                        .graphicsLayer {
                            translationX = frame.translateX
                            translationY = frame.translateY
                            scaleX = frame.scaleX
                            scaleY = frame.scaleY
                            rotationZ = frame.rotationZ
                            rotationX = frame.rotationX
                            rotationY = frame.rotationY
                            alpha = frame.alpha
                            cameraDistance = 16f * density
                        }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) Color(0xFF39205F) else Color(0xFF181818))
                            .clickable { onPresetSelected(preset.id) }
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = preview,
                            color = Color.White,
                            fontSize = (preset.fontSize / 4f).coerceIn(10f, 14f).sp,
                            fontFamily = FontLibrary.familyFor(preset.fontFamily),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            style = TextStyle(
                                shadow = if (preset.glowEnabled) {
                                    Shadow(
                                        color = Color(preset.glowColor),
                                        offset = Offset.Zero,
                                        blurRadius = preset.glowRadius * 0.35f
                                    )
                                } else {
                                    null
                                }
                            ),
                            modifier = previewModifier
                        )
                        Text(
                            "${preset.name} · ${preset.fontFamily}" +
                                    if (preset.glowEnabled) " · Glow" else "",
                            color = if (active) Color(0xFFC4A7FF) else Color(0xFFAAAAAA),
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                    }
                }
                if (rowPresets.size == 1) {
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
            }
        }
        if (CaptionPresetLibrary.find(selectedPresetId)?.mode ==
            com.moody.moodyvideoeditor.utils.CaptionPresetMode.WORD_HIGHLIGHT ||
            CaptionPresetLibrary.find(selectedPresetId)?.mode ==
            com.moody.moodyvideoeditor.utils.CaptionPresetMode.CHARACTER_HIGHLIGHT
        ) {
            ColorPickerField(
                label = "Active color",
                colorLong = highlightColor,
                onChange = onHighlightColorChanged
            )
        }
        if (includeTextStyles) {
            TextStyleTemplatePicker(
                selectedTemplateId = selectedTemplateId,
                onTemplateSelected = onTemplateSelected,
                onBack = {},
                embedded = true
            )
        }
        if (includeTypographyAnimations) {
            TypographyAnimationPresetPicker(onPresetSelected = onTypographyAnimationSelected)
        }
    }
}

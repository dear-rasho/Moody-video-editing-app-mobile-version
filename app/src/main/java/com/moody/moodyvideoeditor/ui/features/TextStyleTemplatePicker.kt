package com.moody.moodyvideoeditor.ui.features

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.utils.AnimationsEngine
import com.moody.moodyvideoeditor.utils.FontLibrary
import com.moody.moodyvideoeditor.utils.TextStyleTemplateLibrary

@Composable
fun TextStyleTemplatePicker(
    selectedTemplateId: String?,
    onTemplateSelected: (String) -> Unit,
    onBack: () -> Unit,
    embedded: Boolean = false
) {
    val previewTransition = rememberInfiniteTransition(label = "textStyleTemplatePreview")
    val elapsed by previewTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "textStyleTemplateClock"
    )

    val columnModifier = if (embedded) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .verticalScrollWithScrollbar(rememberScrollState())
    }

    Column(
        modifier = columnModifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (embedded) {
            Text(
                "Text styles",
                color = Color(0xFFA78BFA),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Text style templates",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Glass, motion and 3D looks",
                        color = Color(0xFFAAAAAA),
                        fontSize = 10.sp
                    )
                }
                TextButton(onClick = onBack) { Text("Done") }
            }
        }

        TextStyleTemplateLibrary.templates.chunked(2).forEach { rowTemplates ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowTemplates.forEach { template ->
                    val style = template.style
                    val duration = style.animationDuration.coerceAtLeast(0.1f)
                    val progress = (elapsed / duration).coerceIn(0f, 1f)
                    val frame = AnimationsEngine.computeFrame(
                        style.animation,
                        progress,
                        elapsed
                    )
                    FontLibrary.loadFont(style.fontFamily)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (template.id == selectedTemplateId) {
                                    Color(0xFF39205F)
                                } else {
                                    Color(0xFF17191F)
                                }
                            )
                            .clickable { onTemplateSelected(template.id) }
                            .padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF27354C), Color(0xFF11141C))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val shadow = when {
                                style.glowEnabled -> Shadow(
                                    color = Color(style.glowColor),
                                    blurRadius = (style.glowRadius * 0.4f).coerceIn(0f, 18f)
                                )
                                style.shadowEnabled -> Shadow(
                                    color = Color(style.shadowColor),
                                    blurRadius = style.shadowBlur.coerceIn(0f, 12f)
                                )
                                else -> null
                            }
                            Box(
                                modifier = Modifier
                                    .blur(frame.blurRadiusPx.coerceIn(0f, 12f).dp)
                                    .graphicsLayer {
                                        translationX = frame.translateX
                                        translationY = frame.translateY
                                        scaleX = frame.scaleX
                                        scaleY = frame.scaleY
                                        rotationZ = frame.rotationZ
                                        rotationX = frame.rotationX
                                        rotationY = frame.rotationY
                                        cameraDistance = 16f * density
                                        alpha = frame.alpha
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (style.strokeEnabled) {
                                    Text(
                                        text = "Your Text",
                                        color = Color(style.strokeColor),
                                        fontSize = (style.fontSize / 3.6f).coerceIn(10f, 19f).sp,
                                        fontFamily = FontLibrary.familyFor(style.fontFamily),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        style = TextStyle(
                                            drawStyle = Stroke(width = style.strokeWidth * 1.8f),
                                            shadow = shadow
                                        )
                                    )
                                }
                                Text(
                                    text = "Your Text",
                                    color = if (style.gradientEnabled) Color.Unspecified else Color(style.color),
                                    fontSize = (style.fontSize / 3.6f).coerceIn(10f, 19f).sp,
                                    fontFamily = FontLibrary.familyFor(style.fontFamily),
                                    fontWeight = if (style.fontWeight == "bold") FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    style = TextStyle(
                                        brush = if (style.gradientEnabled) {
                                            Brush.linearGradient(
                                                listOf(
                                                    Color(style.gradientColor1),
                                                    Color(style.gradientColor2)
                                                )
                                            )
                                        } else null,
                                        shadow = shadow
                                    )
                                )
                            }
                        }
                        Text(
                            "${template.icon} ${template.name}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            template.description,
                            color = Color(0xFFAAAAAA),
                            fontSize = 9.sp,
                            maxLines = 2,
                            minLines = 2
                        )
                    }
                }
                if (rowTemplates.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

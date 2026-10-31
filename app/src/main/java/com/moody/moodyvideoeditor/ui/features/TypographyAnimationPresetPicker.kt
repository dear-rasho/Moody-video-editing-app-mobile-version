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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.utils.TypographyAnimationPreset
import com.moody.moodyvideoeditor.utils.TypographyAnimationPresetLibrary

@Composable
fun TypographyAnimationPresetPicker(
    onPresetSelected: (String) -> Unit
) {
    val transition = rememberInfiniteTransition(label = "typographyPresetPreview")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "typographyPresetProgress"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            "Spacing & size animations",
            color = Color(0xFFA78BFA),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        TypographyAnimationPresetLibrary.presets.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { preset ->
                    TypographyPresetCard(
                        preset = preset,
                        progress = progress,
                        onClick = { onPresetSelected(preset.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TypographyPresetCard(
    preset: TypographyAnimationPreset,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sizeScale = preset.sizeScale?.let {
        TypographyAnimationPresetLibrary.valueAt(it, progress)
    } ?: 1f
    val spacing = preset.letterSpacing?.let {
        TypographyAnimationPresetLibrary.valueAt(it, progress)
    } ?: 0f

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF181818))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Moody",
            color = Color.White,
            fontSize = (15f * sizeScale).coerceIn(8f, 20f).sp,
            letterSpacing = (spacing / 24f).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            preset.name,
            color = Color(0xFFC4A7FF),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            preset.description,
            color = Color(0xFFAAAAAA),
            fontSize = 8.sp,
            maxLines = 2,
            lineHeight = 10.sp
        )
    }
}

package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.utils.KeyframeStore

data class EasingPreset(
    val key: String,
    val label: String,
    val desc: String
)

object EasingPresets {
    val ALL = listOf(
        EasingPreset("linear", "Linear", "Constant speed"),
        EasingPreset("easeInOut", "Easy Ease", "Smooth start+end"),
        EasingPreset("easeIn", "Ease In", "Slow start"),
        EasingPreset("easeOut", "Ease Out", "Slow stop"),
        EasingPreset("sineInOut", "Ease InOut Sine", "Gentle natural"),
        EasingPreset("quadInOut", "Ease InOut Quad", "Natural lift"),
        EasingPreset("cubicInOut", "Ease InOut Cubic", "Cinematic"),
        EasingPreset("quartInOut", "Ease InOut Quart", "Slow → snap"),
        EasingPreset("quintInOut", "Ease InOut Quint", "Snappy modern"),
        EasingPreset("expoInOut", "Ease InOut Expo", "Extreme sharp"),
        EasingPreset("circInOut", "Ease InOut Circ", "Circle arc"),
        EasingPreset("backInOut", "Overshoot / Back", "Goes past target"),
        EasingPreset("elasticOut", "Elastic", "Rubber band"),
        EasingPreset("bounceOut", "Bounce", "Gravity bounce")
    )
}

@Composable
fun EasingGraphPicker(
    currentEase: String,
    onPick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Easing Graphs",
                color = Color(0xFF888888),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text("← swipe →", color = Color(0xFF555555), fontSize = 8.sp)
        }

        // Single horizontal-scroll row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EasingPresets.ALL.forEach { preset ->
                EasingCard(
                    preset = preset,
                    isActive = currentEase == preset.key,
                    onClick = { onPick(preset.key) }
                )
            }
        }
    }
}

@Composable
private fun EasingCard(
    preset: EasingPreset,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(84.dp)
            .height(76.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Color(0xFF2A1F4D) else Color(0xFF181818))
            .pointerInput(preset.key) { detectTapGestures { onClick() } }
            .padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF0F0F0F))
        ) {
            Canvas(modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)) {
                val w = size.width
                val h = size.height
                val pad = 4f
                val usableW = w - pad * 2
                val usableH = h - pad * 2

                drawLine(
                    color = Color(0xFF222222),
                    start = Offset(pad, h / 2f),
                    end = Offset(w - pad, h / 2f),
                    strokeWidth = 0.8f
                )

                val path = Path()
                val samples = 40
                for (i in 0..samples) {
                    val t = i / samples.toFloat()
                    val v = KeyframeStore.easeFn(t, preset.key)
                    val x = pad + t * usableW
                    val vClamped = v.coerceIn(-0.15f, 1.15f)
                    val y = pad + usableH * (1f - (vClamped + 0.15f) / 1.30f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = if (isActive) Color(0xFF4F9DFF) else Color(0xFFAAAAAA),
                    style = Stroke(width = 2f)
                )

                drawCircle(
                    color = Color(0xFF666666), radius = 1.8f,
                    center = Offset(pad, pad + usableH * (1f - 0.15f / 1.30f))
                )
                drawCircle(
                    color = Color(0xFF666666), radius = 1.8f,
                    center = Offset(w - pad, pad + usableH * (1f - 1.15f / 1.30f))
                )
            }
        }

        Text(
            preset.label,
            color = if (isActive) Color(0xFF4F9DFF) else Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            preset.desc,
            color = Color(0xFF777777),
            fontSize = 7.sp,
            maxLines = 1
        )
    }
}
package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.LayerShadowState
import com.moody.moodyvideoeditor.ui.components.ColorPickerField
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

@Composable
fun LayerShadowPanel(
    layerId: String?,
    layerName: String?,
    initialState: LayerShadowState,
    onUpdate: (LayerShadowState) -> Unit,
    onClose: () -> Unit
) {
    var settings by remember(layerId) { mutableStateOf(initialState) }
    LaunchedEffect(layerId, initialState) {
        settings = initialState
    }

    fun apply(next: LayerShadowState) {
        settings = next
        onUpdate(next)
    }

    FeaturePanel(title = "Shadow", onClose = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScrollWithScrollbar(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                layerName?.let { "Apply to: $it" } ?: "Select a visual layer first.",
                color = Color(0xFFBBBBBB),
                fontSize = 11.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled", color = Color.White, modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.enabled,
                    enabled = layerId != null,
                    onCheckedChange = { apply(settings.copy(enabled = it)) }
                )
            }
            ColorPickerField(
                label = "Color",
                colorLong = settings.color,
                onChange = { apply(settings.copy(color = it, enabled = true)) }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    0xFF000000,
                    0xFF171717,
                    0xFF27211E,
                    0xFF151B26,
                    0xFF211629
                ).forEach { color ->
                    val selected = settings.color == color
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(color.toInt()))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) Color(0xFF60EFFF) else Color(0xFF666666),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                apply(settings.copy(color = color, enabled = true))
                            }
                    )
                }
            }
            ShadowSlider(
                label = "Blur",
                value = settings.blur,
                range = 0f..40f,
                enabled = layerId != null,
                onChange = { apply(settings.copy(blur = it)) }
            )
            ShadowSlider(
                label = "Distance",
                value = settings.distance,
                range = 0f..80f,
                enabled = layerId != null,
                onChange = { apply(settings.copy(distance = it)) }
            )
            ShadowSlider(
                label = "Angle",
                value = settings.angle,
                range = 0f..360f,
                enabled = layerId != null,
                onChange = { apply(settings.copy(angle = it)) }
            )
        }
    }
}

@Composable
private fun ShadowSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.White, modifier = Modifier.weight(1f), fontSize = 11.sp)
            Text("%.1f".format(value), color = Color.LightGray, fontSize = 10.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF60EFFF),
                activeTrackColor = Color(0xFF60EFFF)
            )
        )
    }
}

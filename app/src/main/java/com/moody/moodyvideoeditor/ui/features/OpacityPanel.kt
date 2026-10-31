package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.KeyframeLibrary
import com.moody.moodyvideoeditor.data.KeyframeMap
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.KeyframeStore

@Composable
fun OpacityPanel(
    layerName: String,
    opacity: Float,
    keyframes: KeyframeMap,
    timeSec: Float,
    hasLayerSelected: Boolean,
    onOpacityChanged: (Float) -> Unit,
    onToggleKeyframe: (String) -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "Layer Opacity", onClose = onClose) {
        if (!hasLayerSelected) {
            Text(
                "Select a visual layer to control its opacity.",
                color = Color(0xFF999999),
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp)
            )
            return@FeaturePanel
        }

        val property = KeyframeLibrary.LAYER_OPACITY
        val hasKeyframe = KeyframeStore.hasKeyframeAt(keyframes, property, timeSec)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(layerName, color = Color(0xFFAAAAAA), fontSize = 11.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Opacity",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (hasKeyframe) "◆" else "◇",
                    color = if (hasKeyframe) Color(0xFF60EFFF) else Color(0xFFAAAAAA),
                    fontSize = 20.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onToggleKeyframe(property) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Text(
                    "${opacity.toInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Slider(
                value = opacity.coerceIn(0f, 100f),
                onValueChange = { onOpacityChanged(it.coerceIn(0f, 100f)) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF60EFFF),
                    activeTrackColor = Color(0xFF60EFFF)
                )
            )
            Text(
                "Keyframes animate opacity across this layer.",
                color = Color(0xFF777777),
                fontSize = 10.sp
            )
        }
    }
}

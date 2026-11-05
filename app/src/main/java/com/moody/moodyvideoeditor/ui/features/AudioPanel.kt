package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.AudioAdjustmentSettings
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

@Composable
fun AudioPanel(
    settings: AudioAdjustmentSettings,
    trackIndex: Int,
    onSettingsChanged: (AudioAdjustmentSettings) -> Unit,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit,
    onAddLayer: () -> Unit,
    onClose: () -> Unit
) {
    val current = settings.clamped()
    FeaturePanel(title = "🎚️ Audio Settings", onClose = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                "Affects audio tracks A1-A$trackIndex above this layer.",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 5.dp)
            )

            AudioSlider("Volume", current.volume, onEditStart, onEditEnd) {
                onSettingsChanged(current.copy(volume = it))
            }
            AudioSlider("Bass", current.bass, onEditStart, onEditEnd) {
                onSettingsChanged(current.copy(bass = it))
            }
            AudioSlider("Mid EQ", current.mids, onEditStart, onEditEnd) {
                onSettingsChanged(current.copy(mids = it))
            }
            AudioSlider("Treble", current.treble, onEditStart, onEditEnd) {
                onSettingsChanged(current.copy(treble = it))
            }
            AudioSlider("Vocal presence", current.vocals, onEditStart, onEditEnd) {
                onSettingsChanged(current.copy(vocals = it))
            }
            AudioSlider("Echo", current.echo, onEditStart, onEditEnd) {
                onSettingsChanged(current.copy(echo = it))
            }
            AudioSlider("Reverb", current.reverb, onEditStart, onEditEnd) {
                onSettingsChanged(current.copy(reverb = it))
            }
            AudioSlider("Studio reverb", current.studioReverb, onEditStart, onEditEnd) {
                onSettingsChanged(current.copy(studioReverb = it))
            }
            AudioSlider("Pitch (50 = normal)", current.pitch, onEditStart, onEditEnd) {
                onSettingsChanged(current.copy(pitch = it))
            }
            AudioSlider("Loudness", current.loudness, onEditStart, onEditEnd) {
                onSettingsChanged(current.copy(loudness = it))
            }
            AudioSlider("Vocal enhancement", current.vocalEnhancement, onEditStart, onEditEnd) {
                onSettingsChanged(current.copy(vocalEnhancement = it))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF241842))
                    .pointerInput(Unit) { detectTapGestures { onAddLayer() } },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "＋ Add another Audio Settings layer",
                    color = Color(0xFFC4B5FD),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AudioSlider(
    label: String,
    value: Float,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit,
    onValueChanged: (Float) -> Unit
) {
    var editing by remember(label) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            label,
            color = Color(0xFFBBBBBB),
            fontSize = 10.sp,
            modifier = Modifier.weight(1.05f)
        )
        Slider(
            value = value.coerceIn(0f, 100f),
            onValueChange = {
                if (!editing) {
                    editing = true
                    onEditStart()
                }
                onValueChanged(it)
            },
            onValueChangeFinished = {
                if (editing) {
                    editing = false
                    onEditEnd()
                }
            },
            valueRange = 0f..100f,
            modifier = Modifier.weight(2.2f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF7C3AED),
                activeTrackColor = Color(0xFF7C3AED),
                inactiveTrackColor = Color(0xFF303030)
            )
        )
        Text(
            "${value.toInt()}%",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.45f)
        )
    }
}

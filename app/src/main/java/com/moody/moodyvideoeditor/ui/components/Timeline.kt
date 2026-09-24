package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.EditorState

@Composable
fun Timeline(
    state: EditorState,
    onClipTapped: (EditorClip) -> Unit
) {
    val totalMs = state.totalDurationMs.coerceAtLeast(1L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            "Timeline · Tap clip to select",
            color = Color(0xFF888888),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // ═══ VISUAL LAYERS (V1, V2, V3) — shown top-down
        for (i in (state.visualLayerCount - 1) downTo 0) {
            TrackRow(
                label = "V${i + 1}",
                clips = state.clipsOf(i, isAudio = false),
                totalMs = totalMs,
                selectedClipId = state.selectedClipId,
                isSelectedLayer = state.selectedTrackIndex == i && !state.selectedIsAudio,
                onClipTapped = onClipTapped
            )
        }

        // ═══ AUDIO LAYERS (A1, A2)
        for (i in 0 until state.audioLayerCount) {
            TrackRow(
                label = "A${i + 1}",
                clips = state.clipsOf(i, isAudio = true),
                totalMs = totalMs,
                selectedClipId = state.selectedClipId,
                isSelectedLayer = state.selectedTrackIndex == i && state.selectedIsAudio,
                onClipTapped = onClipTapped,
                isAudio = true
            )
        }
    }
}

@Composable
private fun TrackRow(
    label: String,
    clips: List<EditorClip>,
    totalMs: Long,
    selectedClipId: String?,
    isSelectedLayer: Boolean,
    onClipTapped: (EditorClip) -> Unit,
    isAudio: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isAudio) 26.dp else 32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track label
        Box(
            modifier = Modifier
                .width(34.dp)
                .fillMaxHeight()
                .padding(end = 2.dp)
                .background(
                    if (isSelectedLayer) Color(0xFF7C3AED).copy(alpha = 0.3f)
                    else Color(0xFF181818),
                    RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = if (isSelectedLayer) Color(0xFF7C3AED) else Color(0xFF888888),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Track content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1A1A1A))
        ) {
            ClipTrack(
                clips = clips,
                totalMs = totalMs,
                selectedClipId = selectedClipId,
                onClipTapped = onClipTapped,
                isAudio = isAudio
            )
        }
    }
}

@Composable
private fun ClipTrack(
    clips: List<EditorClip>,
    totalMs: Long,
    selectedClipId: String?,
    onClipTapped: (EditorClip) -> Unit,
    isAudio: Boolean
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width }
    ) {
        if (widthPx > 0 && totalMs > 0) {
            clips.forEach { clip ->
                val startFrac = clip.timelineStartMs.toFloat() / totalMs
                val endFrac = clip.timelineEndMs.toFloat() / totalMs
                val startPx = startFrac * widthPx
                val widthPxClip = ((endFrac - startFrac) * widthPx).coerceAtLeast(8f)

                val isSelected = clip.id == selectedClipId
                val color = when {
                    isSelected -> Color(0xFFFFD166)
                    isAudio -> Color(0xFF10B981)
                    else -> Color(0xFF7C3AED)
                }

                Box(
                    modifier = Modifier
                        .offset(x = with(density) { startPx.toDp() })
                        .width(with(density) { widthPxClip.toDp() })
                        .fillMaxHeight()
                        .padding(1.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                        .pointerInput(clip.id) {
                            detectTapGestures { onClipTapped(clip) }
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = clip.name.take(10),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 3.dp),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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

@Composable
fun Timeline(
    clips: List<EditorClip>,
    currentIndex: Int,
    currentPosMs: Long,
    onClipTapped: (Int, Long) -> Unit
) {
    val totalMs = clips.maxOfOrNull { it.timelineEndMs } ?: 1L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Timeline",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            if (clips.isNotEmpty()) {
                Text(
                    text = "${clips.size} clip${if (clips.size != 1) "s" else ""}",
                    color = Color(0xFF666666),
                    fontSize = 9.sp
                )
            }
        }

        // V1 Track
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackLabel("V1")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1A1A1A))
            ) {
                ClipTrack(
                    clips = clips,
                    currentIndex = currentIndex,
                    currentPosMs = currentPosMs,
                    totalMs = totalMs,
                    onClipTapped = onClipTapped
                )
            }
        }

        // A1 Track (static)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackLabel("A1")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun TrackLabel(text: String) {
    Box(
        modifier = Modifier
            .width(28.dp)
            .fillMaxHeight()
            .padding(end = 4.dp)
            .background(Color(0xFF181818), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFF888888),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ClipTrack(
    clips: List<EditorClip>,
    currentIndex: Int,
    currentPosMs: Long,
    totalMs: Long,
    onClipTapped: (Int, Long) -> Unit
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width }
    ) {
        if (widthPx > 0 && totalMs > 0) {
            clips.forEachIndexed { index, clip ->
                val startFrac = clip.timelineStartMs.toFloat() / totalMs
                val endFrac = clip.timelineEndMs.toFloat() / totalMs
                val startPx = startFrac * widthPx
                val widthPxClip = ((endFrac - startFrac) * widthPx).coerceAtLeast(6f)

                val isActive = index == currentIndex
                val color = if (isActive) Color(0xFF7C3AED) else Color(0xFF5B21B6)

                Box(
                    modifier = Modifier
                        .offset(x = with(density) { startPx.toDp() })
                        .width(with(density) { widthPxClip.toDp() })
                        .fillMaxHeight()
                        .padding(2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                        .pointerInput(index, clip.id) {
                            detectTapGestures { offset ->
                                val frac = (offset.x / widthPxClip).coerceIn(0f, 1f)
                                val posMs = (frac * clip.durationMs).toLong()
                                onClipTapped(index, posMs)
                            }
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "${index + 1}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            val active = clips.getOrNull(currentIndex)
            if (active != null && active.durationMs > 0) {
                val sF = active.timelineStartMs.toFloat() / totalMs
                val dF = active.durationMs.toFloat() / totalMs
                val pF = (currentPosMs.toFloat() / active.durationMs).coerceIn(0f, 1f)
                val playPx = (sF + dF * pF) * widthPx

                Box(
                    modifier = Modifier
                        .offset(x = with(density) { playPx.toDp() } - 1.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Color.Red)
                )
            }
        }
    }
}
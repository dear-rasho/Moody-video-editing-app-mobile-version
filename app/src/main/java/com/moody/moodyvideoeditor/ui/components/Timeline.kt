package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.zIndex
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.EditorState
import kotlin.math.roundToInt

private val TRACK_LABEL_WIDTH = 40.dp
private val RULER_HEIGHT = 24.dp
private val VISUAL_TRACK_HEIGHT = 40.dp
private val AUDIO_TRACK_HEIGHT = 32.dp
private const val HANDLE_GRAB_WIDTH_DP = 20

@Composable
fun Timeline(
    state: EditorState,
    onClipTapped: (EditorClip) -> Unit,
    onTrackTapped: (trackIndex: Int, isAudio: Boolean) -> Unit,
    onTrimLeft: (Long) -> Unit,
    onTrimRight: (Long) -> Unit,
    onTrimCommit: () -> Unit,
    onSeek: (Long) -> Unit,
    onMoveClip: (clipId: String, trackIndex: Int, isAudio: Boolean, timelineMs: Long) -> Unit = { _, _, _, _ -> },
    onDragEnd: () -> Unit = {}
) {
    val totalMs = state.totalDurationMs.coerceAtLeast(10000L)
    var trackAreaWidthPx by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(4.dp)
    ) {
        // ═══ RULER (click = seek) ═══
        RulerRow(
            totalMs = totalMs,
            trackAreaWidthPx = trackAreaWidthPx,
            onSeek = onSeek
        )

        // ═══ TRACKS + PLAYHEAD ═══
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Visual: V3, V2, V1
                for (i in (state.visualLayerCount - 1) downTo 0) {
                    VisualTrackRow(
                        trackIndex = i,
                        clips = state.clipsOf(i, false),
                        totalMs = totalMs,
                        selectedClipId = state.selectedClipId,
                        selectedTrackIndex = state.selectedTrackIndex,
                        selectedIsAudio = state.selectedIsAudio,
                        onClipTapped = onClipTapped,
                        onTrackTapped = onTrackTapped,
                        onTrimLeft = onTrimLeft,
                        onTrimRight = onTrimRight,
                        onTrimCommit = onTrimCommit,
                        onSeek = onSeek,
                        onMoveClip = onMoveClip,
                        onDragEnd = onDragEnd,
                        onTrackAreaSized = { w -> if (i == 0) trackAreaWidthPx = w },
                        visualLayerCount = state.visualLayerCount,
                        audioLayerCount = state.audioLayerCount
                    )
                }

                // Audio: A1, A2...
                for (i in 0 until state.audioLayerCount) {
                    AudioTrackRow(
                        trackIndex = i,
                        clips = state.clipsOf(i, true),
                        totalMs = totalMs,
                        selectedClipId = state.selectedClipId,
                        selectedTrackIndex = state.selectedTrackIndex,
                        selectedIsAudio = state.selectedIsAudio,
                        onClipTapped = onClipTapped,
                        onTrackTapped = onTrackTapped,
                        onTrimLeft = onTrimLeft,
                        onTrimRight = onTrimRight,
                        onTrimCommit = onTrimCommit,
                        onSeek = onSeek,
                        onMoveClip = onMoveClip,
                        onDragEnd = onDragEnd,
                        visualLayerCount = state.visualLayerCount,
                        audioLayerCount = state.audioLayerCount
                    )
                }
            }

            // ═══ PLAYHEAD (separate component) ═══
            TimelinePlayhead(
                currentTimeMs = state.currentPosMs,
                totalMs = totalMs,
                leftOffset = TRACK_LABEL_WIDTH,
                trackAreaWidthPx = trackAreaWidthPx,
                onSeek = onSeek
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  RULER — click to seek
// ═══════════════════════════════════════════════════════════════
@Composable
private fun RulerRow(
    totalMs: Long,
    trackAreaWidthPx: Int,
    onSeek: (Long) -> Unit
) {
    val density = LocalDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RULER_HEIGHT),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(TRACK_LABEL_WIDTH)
                .fillMaxHeight()
                .background(Color(0xFF181818), RoundedCornerShape(3.dp))
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 2.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF181818))
                .onSizeChanged {
                    // Track ruler width matches track area (approximately)
                    // We use the track content width, not ruler width, for accuracy
                }
                .pointerInput(totalMs, trackAreaWidthPx) {
                    detectTapGestures { offset ->
                        if (trackAreaWidthPx > 0 && totalMs > 0) {
                            val pxPerMs = trackAreaWidthPx.toFloat() / totalMs.toFloat()
                            val timeMs = (offset.x / pxPerMs).toLong()
                            onSeek(timeMs.coerceIn(0L, totalMs))
                        }
                    }
                }
        ) {
            if (trackAreaWidthPx > 0 && totalMs > 0) {
                val marksCount = 6
                for (i in 0..marksCount) {
                    val frac = i.toFloat() / marksCount
                    val timeMs = (frac * totalMs).toLong()
                    val xDp = with(density) { (frac * trackAreaWidthPx).toDp() }
                    Box(
                        modifier = Modifier
                            .offset(x = xDp)
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF444444))
                    )
                    Text(
                        text = formatTime(timeMs),
                        color = Color(0xFF888888),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(x = xDp + 3.dp, y = 4.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  VISUAL TRACK
// ═══════════════════════════════════════════════════════════════
@Composable
private fun VisualTrackRow(
    trackIndex: Int,
    clips: List<EditorClip>,
    totalMs: Long,
    selectedClipId: String?,
    selectedTrackIndex: Int,
    selectedIsAudio: Boolean,
    onClipTapped: (EditorClip) -> Unit,
    onTrackTapped: (Int, Boolean) -> Unit,
    onTrimLeft: (Long) -> Unit,
    onTrimRight: (Long) -> Unit,
    onTrimCommit: () -> Unit,
    onSeek: (Long) -> Unit,
    onMoveClip: (String, Int, Boolean, Long) -> Unit,
    onDragEnd: () -> Unit,
    onTrackAreaSized: (Int) -> Unit,
    visualLayerCount: Int,
    audioLayerCount: Int
) {
    val isSelectedLayer = selectedTrackIndex == trackIndex && !selectedIsAudio

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(VISUAL_TRACK_HEIGHT),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(TRACK_LABEL_WIDTH)
                .fillMaxHeight()
                .padding(vertical = 2.dp, horizontal = 2.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (isSelectedLayer) Color(0xFF7C3AED) else Color(0xFF1F1F1F))
                .pointerInput(trackIndex) {
                    detectTapGestures { onTrackTapped(trackIndex, false) }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "V${trackIndex + 1}",
                color = if (isSelectedLayer) Color.White else Color(0xFFAAAAAA),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        TrackContent(
            clips = clips,
            totalMs = totalMs,
            selectedClipId = selectedClipId,
            isAudio = false,
            trackIndex = trackIndex,
            onClipTapped = onClipTapped,
            onTrimLeft = onTrimLeft,
            onTrimRight = onTrimRight,
            onTrimCommit = onTrimCommit,
            onSeek = onSeek,
            onMoveClip = onMoveClip,
            onDragEnd = onDragEnd,
            onSized = if (trackIndex == 0) onTrackAreaSized else null,
            visualLayerCount = visualLayerCount,
            audioLayerCount = audioLayerCount
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  AUDIO TRACK
// ═══════════════════════════════════════════════════════════════
@Composable
private fun AudioTrackRow(
    trackIndex: Int,
    clips: List<EditorClip>,
    totalMs: Long,
    selectedClipId: String?,
    selectedTrackIndex: Int,
    selectedIsAudio: Boolean,
    onClipTapped: (EditorClip) -> Unit,
    onTrackTapped: (Int, Boolean) -> Unit,
    onTrimLeft: (Long) -> Unit,
    onTrimRight: (Long) -> Unit,
    onTrimCommit: () -> Unit,
    onSeek: (Long) -> Unit,
    onMoveClip: (String, Int, Boolean, Long) -> Unit,
    onDragEnd: () -> Unit,
    visualLayerCount: Int,
    audioLayerCount: Int
) {
    val isSelectedLayer = selectedTrackIndex == trackIndex && selectedIsAudio

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AUDIO_TRACK_HEIGHT),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(TRACK_LABEL_WIDTH)
                .fillMaxHeight()
                .padding(vertical = 2.dp, horizontal = 2.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (isSelectedLayer) Color(0xFF10B981) else Color(0xFF1F1F1F))
                .pointerInput(trackIndex) {
                    detectTapGestures { onTrackTapped(trackIndex, true) }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "A${trackIndex + 1}",
                color = if (isSelectedLayer) Color.White else Color(0xFFAAAAAA),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        TrackContent(
            clips = clips,
            totalMs = totalMs,
            selectedClipId = selectedClipId,
            isAudio = true,
            trackIndex = trackIndex,
            onClipTapped = onClipTapped,
            onTrimLeft = onTrimLeft,
            onTrimRight = onTrimRight,
            onTrimCommit = onTrimCommit,
            onSeek = onSeek,
            onMoveClip = onMoveClip,
            onDragEnd = onDragEnd,
            onSized = null,
            visualLayerCount = visualLayerCount,
            audioLayerCount = audioLayerCount
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  TRACK CONTENT — clips + trim + drag + tap-to-seek
// ═══════════════════════════════════════════════════════════════
@Composable
private fun RowScope.TrackContent(
    clips: List<EditorClip>,
    totalMs: Long,
    selectedClipId: String?,
    isAudio: Boolean,
    trackIndex: Int,
    onClipTapped: (EditorClip) -> Unit,
    onTrimLeft: (Long) -> Unit,
    onTrimRight: (Long) -> Unit,
    onTrimCommit: () -> Unit,
    onSeek: (Long) -> Unit,
    onMoveClip: (String, Int, Boolean, Long) -> Unit,
    onDragEnd: () -> Unit,
    onSized: ((Int) -> Unit)?,
    visualLayerCount: Int,
    audioLayerCount: Int
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(start = 2.dp, top = 2.dp, bottom = 2.dp, end = 2.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF1A1A1A))
            .onSizeChanged {
                widthPx = it.width
                onSized?.invoke(it.width)
            }
            // Tap to seek (only on empty area)
            .pointerInput(clips, totalMs, widthPx) {
                detectTapGestures { offset ->
                    if (widthPx > 0 && totalMs > 0) {
                        val pxPerMs = widthPx.toFloat() / totalMs.toFloat()
                        val timeMs = (offset.x / pxPerMs).toLong()
                        val hitClip = clips.any {
                            timeMs >= it.timelineStartMs && timeMs < it.timelineEndMs
                        }
                        if (!hitClip) {
                            onSeek(timeMs.coerceIn(0L, totalMs))
                        }
                    }
                }
            }
    ) {
        if (widthPx <= 0 || totalMs <= 0) return@Box

        val pxPerMs = widthPx.toFloat() / totalMs.toFloat()
        val stepPx = with(density) { 30.dp.toPx() }
        val handleWidthPx = with(density) { HANDLE_GRAB_WIDTH_DP.dp.toPx() }

        clips.forEach { clip ->
            val isSelected = clip.id == selectedClipId
            val startPx = clip.timelineStartMs.toFloat() / totalMs * widthPx
            val endPx = clip.timelineEndMs.toFloat() / totalMs * widthPx
            val widthPxClip = (endPx - startPx).coerceAtLeast(20f)

            val barColor = if (isAudio) Color(0xFF10B981) else Color(0xFF7C3AED)

            // ═══ CLIP BAR ═══
            Box(
                modifier = Modifier
                    .offset(x = with(density) { startPx.toDp() })
                    .width(with(density) { widthPxClip.toDp() })
                    .fillMaxHeight()
                    .zIndex(1f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isSelected) Color(0xFFFFD166) else barColor)
                    // MOVE drag (whole clip)
                    .pointerInput(clip.id, widthPx, totalMs) {
                        var accumX = 0f
                        var accumY = 0f
                        var startTimeMs = 0L
                        var startTrack = 0
                        var startIsAudio = false

                        detectDragGestures(
                            onDragStart = { offset ->
                                accumX = 0f
                                accumY = 0f
                                startTimeMs = clip.timelineStartMs
                                startTrack = clip.trackIndex
                                startIsAudio = clip.isAudio
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                accumX += drag.x
                                accumY += drag.y

                                val dMs = (accumX / pxPerMs).toLong()
                                val newTime = (startTimeMs + dMs).coerceAtLeast(0L)

                                // Vertical: positive accumY = down
                                val steps = (accumY / stepPx).roundToInt()
                                var newTrack = startTrack
                                val newAudio = startIsAudio

                                if (!startIsAudio) {
                                    newTrack =
                                        (startTrack - steps).coerceIn(0, visualLayerCount - 1)
                                } else {
                                    newTrack = (startTrack + steps).coerceIn(0, audioLayerCount - 1)
                                }

                                onMoveClip(clip.id, newTrack, newAudio, newTime)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    }
                    // Tap to select
                    .pointerInput(clip.id) {
                        detectTapGestures { onClipTapped(clip) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = clip.name.take(12),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                // ═══ TRIM HANDLES (children of clip) ═══
                if (isSelected) {
                    // LEFT
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(with(density) { (handleWidthPx).toDp() })
                            .fillMaxHeight()
                            .zIndex(5f)
                            .pointerInput(clip.id + "-L", widthPx, totalMs) {
                                var accumX = 0f
                                var startMs = 0L
                                var endMs = 0L

                                detectDragGestures(
                                    onDragStart = {
                                        accumX = 0f
                                        startMs = clip.sourceStartMs
                                        endMs = clip.sourceEndMs
                                    },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        accumX += drag.x
                                        val dMs = (accumX / pxPerMs).toLong()
                                        val newStart = (startMs + dMs)
                                            .coerceIn(0L, endMs - EditorClip.MIN_DURATION_MS)
                                        onTrimLeft(newStart)
                                    },
                                    onDragEnd = { onTrimCommit() },
                                    onDragCancel = { onTrimCommit() }
                                )
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF22C55E))
                        )
                    }

                    // RIGHT
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(with(density) { (handleWidthPx).toDp() })
                            .fillMaxHeight()
                            .zIndex(5f)
                            .pointerInput(clip.id + "-R", widthPx, totalMs) {
                                var accumX = 0f
                                var startMs = 0L
                                var endMs = 0L

                                detectDragGestures(
                                    onDragStart = {
                                        accumX = 0f
                                        startMs = clip.sourceStartMs
                                        endMs = clip.sourceEndMs
                                    },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        accumX += drag.x
                                        val dMs = (accumX / pxPerMs).toLong()
                                        val maxEnd = if (clip.sourceTotalMs != Long.MAX_VALUE)
                                            clip.sourceTotalMs else Long.MAX_VALUE
                                        val newEnd = (endMs + dMs)
                                            .coerceIn(startMs + EditorClip.MIN_DURATION_MS, maxEnd)
                                        onTrimRight(newEnd)
                                    },
                                    onDragEnd = { onTrimCommit() },
                                    onDragCancel = { onTrimCommit() }
                                )
                            },
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF22C55E))
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}
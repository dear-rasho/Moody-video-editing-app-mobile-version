package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.EditorState
import kotlin.math.roundToInt

private val TRACK_LABEL_WIDTH = 44.dp
private val RULER_HEIGHT = 26.dp
private val VISUAL_TRACK_HEIGHT = 42.dp
private val AUDIO_TRACK_HEIGHT = 34.dp
private const val DP_PER_SECOND = 12f

@Composable
fun Timeline(
    state: EditorState,
    onClipTapped: (EditorClip) -> Unit,
    onTrackTapped: (Int, Boolean) -> Unit,
    onTrimLeft: (Long) -> Unit,
    onTrimRight: (Long) -> Unit,
    onTrimCommit: () -> Unit,
    onSeek: (Long) -> Unit,
    onMoveClip: (String, Int, Boolean, Long) -> Unit = { _, _, _, _ -> },
    onDragEnd: () -> Unit = {}
) {
    val totalMs = state.totalDurationMs.coerceAtLeast(10000L)
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val density = LocalDensity.current

    val contentWidthDp: Dp = with(density) {
        ((totalMs / 1000f) * DP_PER_SECOND).dp
    }
    val contentWidthPx = with(density) { contentWidthDp.toPx() }
    val labelWidthPx = with(density) { TRACK_LABEL_WIDTH.toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        // ═══ RULER (sticky top) ═══
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
                    .background(Color(0xFF181818))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF181818))
                    .horizontalScroll(hScroll)
            ) {
                Box(
                    modifier = Modifier
                        .width(contentWidthDp)
                        .fillMaxHeight()
                        .pointerInput(totalMs, contentWidthDp) {
                            detectTapGestures { offset ->
                                if (contentWidthPx <= 0f) return@detectTapGestures
                                val pxPerMs = contentWidthPx / totalMs.toFloat()
                                onSeek((offset.x / pxPerMs).toLong().coerceIn(0L, totalMs))
                            }
                        }
                ) {
                    val marks = 12
                    for (i in 0..marks) {
                        val frac = i.toFloat() / marks
                        val timeMs = (frac * totalMs).toLong()
                        val xDp = contentWidthDp * frac
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
                            modifier = Modifier.offset(x = xDp + 3.dp, y = 5.dp)
                        )
                    }
                }
            }
        }

        // ═══ TRACKS + PLAYHEAD OVERLAY ═══
        Box(modifier = Modifier
            .fillMaxWidth()
            .weight(1f)) {

            // Tracks column (vertically scrollable)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(vScroll)
            ) {
                for (i in (state.visualLayerCount - 1) downTo 0) {
                    VisualTrackRow(
                        trackIndex = i,
                        clips = state.clipsOf(i, false),
                        totalMs = totalMs,
                        contentWidthDp = contentWidthDp,
                        hScroll = hScroll,
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
                for (i in 0 until state.audioLayerCount) {
                    AudioTrackRow(
                        trackIndex = i,
                        clips = state.clipsOf(i, true),
                        totalMs = totalMs,
                        contentWidthDp = contentWidthDp,
                        hScroll = hScroll,
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
                Box(Modifier.height(40.dp))
            }

            // ═══ PLAYHEAD — red vertical line ═══
            val playheadPx = if (totalMs > 0) {
                (state.currentPosMs.toFloat() / totalMs.toFloat()) * contentWidthPx
            } else 0f

            val screenX = labelWidthPx + playheadPx - hScroll.value.toFloat()

            // Only show if within visible track area
            if (screenX >= labelWidthPx - 4f) {
                // Vertical line
                Box(
                    modifier = Modifier
                        .offset(x = with(density) { screenX.toDp() })
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Color(0xFFFF3B3B))
                        .zIndex(100f)
                )
                // Top knob
                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { (screenX - 8).toDp() },
                            y = 0.dp
                        )
                        .width(18.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                        .background(Color(0xFFFF3B3B))
                        .zIndex(101f)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  VISUAL TRACK ROW
// ═══════════════════════════════════════════════════════════════
@Composable
private fun VisualTrackRow(
    trackIndex: Int,
    clips: List<EditorClip>,
    totalMs: Long,
    contentWidthDp: Dp,
    hScroll: ScrollState,
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
                .padding(2.dp)
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
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF1A1A1A))
                .horizontalScroll(hScroll)
        ) {
            TrackContent(
                clips = clips, totalMs = totalMs, contentWidthDp = contentWidthDp,
                selectedClipId = selectedClipId, isAudio = false, trackIndex = trackIndex,
                onClipTapped = onClipTapped,
                onTrimLeft = onTrimLeft, onTrimRight = onTrimRight, onTrimCommit = onTrimCommit,
                onSeek = onSeek, onMoveClip = onMoveClip, onDragEnd = onDragEnd,
                visualLayerCount = visualLayerCount, audioLayerCount = audioLayerCount
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  AUDIO TRACK ROW
// ═══════════════════════════════════════════════════════════════
@Composable
private fun AudioTrackRow(
    trackIndex: Int,
    clips: List<EditorClip>,
    totalMs: Long,
    contentWidthDp: Dp,
    hScroll: ScrollState,
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
                .padding(2.dp)
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
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF1A1A1A))
                .horizontalScroll(hScroll)
        ) {
            TrackContent(
                clips = clips, totalMs = totalMs, contentWidthDp = contentWidthDp,
                selectedClipId = selectedClipId, isAudio = true, trackIndex = trackIndex,
                onClipTapped = onClipTapped,
                onTrimLeft = onTrimLeft, onTrimRight = onTrimRight, onTrimCommit = onTrimCommit,
                onSeek = onSeek, onMoveClip = onMoveClip, onDragEnd = onDragEnd,
                visualLayerCount = visualLayerCount, audioLayerCount = audioLayerCount
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  TRACK CONTENT
// ═══════════════════════════════════════════════════════════════
@Composable
private fun TrackContent(
    clips: List<EditorClip>,
    totalMs: Long,
    contentWidthDp: Dp,
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
    visualLayerCount: Int,
    audioLayerCount: Int
) {
    val density = LocalDensity.current
    val contentWidthPx = with(density) { contentWidthDp.toPx() }

    Box(
        modifier = Modifier
            .width(contentWidthDp)
            .fillMaxHeight()
            .pointerInput(clips, totalMs, contentWidthPx) {
                detectTapGestures { offset ->
                    if (contentWidthPx <= 0f || totalMs <= 0L) return@detectTapGestures
                    val pxPerMs = contentWidthPx / totalMs.toFloat()
                    val timeMs = (offset.x / pxPerMs).toLong()
                    val hitClip =
                        clips.any { timeMs >= it.timelineStartMs && timeMs < it.timelineEndMs }
                    if (!hitClip) onSeek(timeMs.coerceIn(0L, totalMs))
                }
            }
    ) {
        if (contentWidthPx <= 0f || totalMs <= 0L) return@Box

        val pxPerMs = contentWidthPx / totalMs.toFloat()
        val stepPx = with(density) { 30.dp.toPx() }
        val handleWidthPx = with(density) { 20.dp.toPx() }

        clips.forEach { clip ->
            val isSelected = clip.id == selectedClipId
            val startPx = clip.timelineStartMs.toFloat() / totalMs * contentWidthPx
            val endPx = clip.timelineEndMs.toFloat() / totalMs * contentWidthPx
            val clipWidthPx = (endPx - startPx).coerceAtLeast(20f)

            val barColor = when {
                clip.isAudio -> Color(0xFF10B981)
                clip.isTextClip -> Color(0xFFEC4899)
                clip.isStickerClip -> Color(0xFFF59E0B)
                clip.isEffectClip -> Color(0xFFA855F7)
                clip.isAdjustmentClip -> Color(0xFF06B6D4)
                clip.isOverlayClip -> Color(0xFF3B82F6)
                clip.isChromaClip -> Color(0xFF22C55E)
                else -> Color(0xFF7C3AED)
            }

            Box(
                modifier = Modifier
                    .offset(x = with(density) { startPx.toDp() })
                    .width(with(density) { clipWidthPx.toDp() })
                    .fillMaxHeight()
                    .zIndex(1f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isSelected) Color(0xFFFFD166) else barColor)
                    .pointerInput(clip.id, contentWidthPx, totalMs) {
                        var accumX = 0f
                        var accumY = 0f
                        var startTimeMs = 0L
                        var startTrack = 0
                        var startIsAudio = false

                        detectDragGestures(
                            onDragStart = {
                                accumX = 0f; accumY = 0f
                                startTimeMs = clip.timelineStartMs
                                startTrack = clip.trackIndex
                                startIsAudio = clip.isAudio
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                accumX += drag.x; accumY += drag.y
                                val dMs = (accumX / pxPerMs).toLong()
                                val newTime = (startTimeMs + dMs).coerceAtLeast(0L)
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
                    .pointerInput(clip.id) {
                        detectTapGestures { onClipTapped(clip) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = clip.name.take(14),
                    color = Color.White, fontSize = 9.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(with(density) { handleWidthPx.toDp() })
                            .fillMaxHeight()
                            .zIndex(5f)
                            .pointerInput(clip.id + "-L", contentWidthPx, totalMs) {
                                var accumX = 0f
                                var startMs = 0L
                                var endMs = 0L
                                detectDragGestures(
                                    onDragStart = {
                                        accumX = 0f; startMs = clip.sourceStartMs; endMs =
                                        clip.sourceEndMs
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
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(with(density) { handleWidthPx.toDp() })
                            .fillMaxHeight()
                            .zIndex(5f)
                            .pointerInput(clip.id + "-R", contentWidthPx, totalMs) {
                                var accumX = 0f
                                var startMs = 0L
                                var endMs = 0L
                                detectDragGestures(
                                    onDragStart = {
                                        accumX = 0f; startMs = clip.sourceStartMs; endMs =
                                        clip.sourceEndMs
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
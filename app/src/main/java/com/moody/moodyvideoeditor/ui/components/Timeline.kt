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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.EditorState
import kotlin.math.abs
import kotlin.math.roundToInt

private val TRACK_LABEL_WIDTH = 32.dp
private val RULER_HEIGHT = 22.dp
private val VISUAL_TRACK_HEIGHT = 34.dp
private val AUDIO_TRACK_HEIGHT = 28.dp
private const val DP_PER_SECOND = 20f
private const val SNAP_THRESHOLD_MS = 100L

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

    var viewportWidthPx by remember { mutableFloatStateOf(0f) }
    var lastUserScrollMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(hScroll.isScrollInProgress) {
        if (hScroll.isScrollInProgress) {
            lastUserScrollMs = System.currentTimeMillis()
        }
    }

    LaunchedEffect(state.currentPosMs, state.isPlaying, viewportWidthPx) {
        if (viewportWidthPx <= 0f) return@LaunchedEffect
        val userScrolledRecently =
            (System.currentTimeMillis() - lastUserScrollMs) <
                    TimelinePlayheadController.USER_SCROLL_GRACE_MS
        val target = TimelinePlayheadController.autoScrollTarget(
            playheadContentPx = TimelinePlayheadController.playheadContentPx(
                state.currentPosMs, totalMs, contentWidthPx
            ),
            labelWidthPx = labelWidthPx,
            viewportWidthPx = viewportWidthPx,
            contentWidthPx = contentWidthPx,
            currentScroll = hScroll.value,
            isPlaying = state.isPlaying,
            userScrolledRecently = userScrolledRecently
        )
        if (target != hScroll.value) hScroll.scrollTo(target)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .onSizeChanged { viewportWidthPx = it.width.toFloat() }
    ) {
        // ═══ RULER ═══
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
                    .background(Color(0xFF0A0A0A))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF0A0A0A))
                    .horizontalScroll(hScroll)
            ) {
                Box(
                    modifier = Modifier
                        .width(contentWidthDp)
                        .fillMaxHeight()
                        .pointerInput(totalMs, contentWidthPx) {
                            detectTapGestures { offset ->
                                onSeek(
                                    TimelinePlayheadController.seekTimeFromClick(
                                        offset.x, contentWidthPx, totalMs
                                    )
                                )
                            }
                        }
                ) {
                    val marksCount = 12
                    for (i in 0..marksCount) {
                        val frac = i.toFloat() / marksCount
                        val timeMs = (frac * totalMs).toLong()
                        val xDp = contentWidthDp * frac
                        Box(
                            modifier = Modifier
                                .offset(x = xDp)
                                .width(1.dp)
                                .height(6.dp)
                                .background(Color(0xFF444444))
                                .align(Alignment.BottomStart)
                        )
                        Text(
                            text = formatRulerTime(timeMs),
                            color = Color(0xFF888888),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset(x = xDp + 3.dp, y = 2.dp)
                        )
                    }
                }
            }
        }

        // ═══ TRACKS + PLAYHEAD ═══
        Box(modifier = Modifier
            .fillMaxWidth()
            .weight(1f)) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(vScroll)
            ) {
                for (i in (state.visualLayerCount - 1) downTo 0) {
                    VisualTrackRow(
                        trackIndex = i,
                        clips = state.clipsOf(i, false),
                        allClips = state.clips,
                        totalMs = totalMs,
                        contentWidthDp = contentWidthDp,
                        contentWidthPx = contentWidthPx,
                        hScroll = hScroll,
                        selectedClipId = state.selectedClipId,
                        multiSelectedIds = state.multiSelectedIds,
                        selectedTrackIndex = state.selectedTrackIndex,
                        selectedIsAudio = state.selectedIsAudio,
                        currentPosMs = state.currentPosMs,
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
                        allClips = state.clips,
                        totalMs = totalMs,
                        contentWidthDp = contentWidthDp,
                        contentWidthPx = contentWidthPx,
                        hScroll = hScroll,
                        selectedClipId = state.selectedClipId,
                        multiSelectedIds = state.multiSelectedIds,
                        selectedTrackIndex = state.selectedTrackIndex,
                        selectedIsAudio = state.selectedIsAudio,
                        currentPosMs = state.currentPosMs,
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

            TimelinePlayhead(
                currentPosMs = state.currentPosMs,
                totalMs = totalMs,
                contentWidthPx = contentWidthPx,
                labelWidthPx = labelWidthPx,
                hScrollValue = hScroll.value,
                viewportWidthPx = viewportWidthPx
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
@Composable
private fun VisualTrackRow(
    trackIndex: Int,
    clips: List<EditorClip>,
    allClips: List<EditorClip>,
    totalMs: Long,
    contentWidthDp: Dp,
    contentWidthPx: Float,
    hScroll: ScrollState,
    selectedClipId: String?,
    multiSelectedIds: Set<String>,
    selectedTrackIndex: Int,
    selectedIsAudio: Boolean,
    currentPosMs: Long,
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
                .pointerInput(trackIndex) {
                    detectTapGestures { onTrackTapped(trackIndex, false) }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "V${trackIndex + 1}",
                color = if (isSelectedLayer) Color(0xFF7C3AED) else Color(0xFF888888),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF141414))
                .horizontalScroll(hScroll)
        ) {
            TrackContent(
                clips = clips, allClips = allClips, totalMs = totalMs,
                contentWidthDp = contentWidthDp, contentWidthPx = contentWidthPx,
                selectedClipId = selectedClipId,
                multiSelectedIds = multiSelectedIds,
                isAudio = false, trackIndex = trackIndex,
                currentPosMs = currentPosMs,
                onClipTapped = onClipTapped,
                onTrimLeft = onTrimLeft, onTrimRight = onTrimRight,
                onTrimCommit = onTrimCommit,
                onSeek = onSeek, onMoveClip = onMoveClip, onDragEnd = onDragEnd,
                visualLayerCount = visualLayerCount,
                audioLayerCount = audioLayerCount
            )
        }
    }
}

@Composable
private fun AudioTrackRow(
    trackIndex: Int,
    clips: List<EditorClip>,
    allClips: List<EditorClip>,
    totalMs: Long,
    contentWidthDp: Dp,
    contentWidthPx: Float,
    hScroll: ScrollState,
    selectedClipId: String?,
    multiSelectedIds: Set<String>,
    selectedTrackIndex: Int,
    selectedIsAudio: Boolean,
    currentPosMs: Long,
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
                .pointerInput(trackIndex) {
                    detectTapGestures { onTrackTapped(trackIndex, true) }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "A${trackIndex + 1}",
                color = if (isSelectedLayer) Color(0xFF10B981) else Color(0xFF888888),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF141414))
                .horizontalScroll(hScroll)
        ) {
            TrackContent(
                clips = clips, allClips = allClips, totalMs = totalMs,
                contentWidthDp = contentWidthDp, contentWidthPx = contentWidthPx,
                selectedClipId = selectedClipId,
                multiSelectedIds = multiSelectedIds,
                isAudio = true, trackIndex = trackIndex,
                currentPosMs = currentPosMs,
                onClipTapped = onClipTapped,
                onTrimLeft = onTrimLeft, onTrimRight = onTrimRight,
                onTrimCommit = onTrimCommit,
                onSeek = onSeek, onMoveClip = onMoveClip, onDragEnd = onDragEnd,
                visualLayerCount = visualLayerCount,
                audioLayerCount = audioLayerCount
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
@Composable
private fun TrackContent(
    clips: List<EditorClip>,
    allClips: List<EditorClip>,
    totalMs: Long,
    contentWidthDp: Dp,
    contentWidthPx: Float,
    selectedClipId: String?,
    multiSelectedIds: Set<String>,
    isAudio: Boolean,
    trackIndex: Int,
    currentPosMs: Long,
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

    // 🆕 Snap guide state — green vertical line during trim
    var snapGuideX by remember { mutableFloatStateOf(-1f) }

    Box(
        modifier = Modifier
            .width(contentWidthDp)
            .fillMaxHeight()
            .pointerInput(clips, totalMs, contentWidthPx) {
                detectTapGestures { offset ->
                    if (contentWidthPx <= 0f || totalMs <= 0L) return@detectTapGestures
                    val pxPerMs = contentWidthPx / totalMs.toFloat()
                    val timeMs = (offset.x / pxPerMs).toLong()
                    val hitClip = clips.any {
                        timeMs >= it.timelineStartMs && timeMs < it.timelineEndMs
                    }
                    if (!hitClip) {
                        onSeek(
                            TimelinePlayheadController.seekTimeFromClick(
                                offset.x, contentWidthPx, totalMs
                            )
                        )
                    }
                }
            }
    ) {
        if (contentWidthPx <= 0f || totalMs <= 0L) return@Box

        val pxPerMs = contentWidthPx / totalMs.toFloat()
        val stepPx = with(density) { 30.dp.toPx() }
        val handleWidthPx = with(density) { 18.dp.toPx() }

        clips.forEach { clip ->
            val isSelected = clip.id == selectedClipId
            val isMulti = multiSelectedIds.contains(clip.id)
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
                else -> Color(0xFF2563EB)
            }

            Box(
                modifier = Modifier
                    .offset(x = with(density) { startPx.toDp() })
                    .width(with(density) { clipWidthPx.toDp() })
                    .fillMaxHeight()
                    .padding(vertical = 2.dp)
                    .zIndex(if (isSelected) 10f else if (isMulti) 9f else 1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            isSelected -> Color(0xFFFFD166)
                            isMulti -> Color(0xFF4F9DFF)
                            else -> barColor
                        }
                    )
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
                                    newTrack = (startTrack - steps)
                                        .coerceIn(0, visualLayerCount - 1)
                                } else {
                                    newTrack = (startTrack + steps)
                                        .coerceIn(0, audioLayerCount - 1)
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
                    text = clip.name.take(20),
                    color = Color.White, fontSize = 9.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1
                )

                if (isSelected) {
                    // LEFT handle — GREEN
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

                                        // 🆕 Snap detection
                                        val newStartTimelineMs =
                                            clip.timelineStartMs + (newStart - clip.sourceStartMs)
                                        var snapTarget: Long? = null
                                        allClips.filter { it.id != clip.id }.forEach { other ->
                                            val oS = other.timelineStartMs
                                            val oE = other.timelineEndMs
                                            if (abs(newStartTimelineMs - oS) < SNAP_THRESHOLD_MS) {
                                                snapTarget = oS
                                            } else if (abs(newStartTimelineMs - oE) < SNAP_THRESHOLD_MS) {
                                                snapTarget = oE
                                            }
                                        }
                                        if (abs(newStartTimelineMs - currentPosMs) < SNAP_THRESHOLD_MS) {
                                            snapTarget = currentPosMs
                                        }

                                        if (snapTarget != null) {
                                            val deltaMs = snapTarget!! - clip.timelineStartMs
                                            val finalSourceStart = clip.sourceStartMs + deltaMs
                                            onTrimLeft(finalSourceStart.coerceAtLeast(0L))
                                            snapGuideX = (snapTarget!!.toFloat() /
                                                    totalMs.toFloat()) * contentWidthPx
                                        } else {
                                            onTrimLeft(newStart)
                                            snapGuideX = -1f
                                        }
                                    },
                                    onDragEnd = {
                                        snapGuideX = -1f
                                        onTrimCommit()
                                    },
                                    onDragCancel = {
                                        snapGuideX = -1f
                                        onTrimCommit()
                                    }
                                )
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF22C55E))
                        )
                    }

                    // RIGHT handle — GREEN
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

                                        // 🆕 Snap detection
                                        val newEndTimelineMs =
                                            clip.timelineStartMs + (newEnd - clip.sourceStartMs)
                                        var snapTarget: Long? = null
                                        allClips.filter { it.id != clip.id }.forEach { other ->
                                            val oS = other.timelineStartMs
                                            val oE = other.timelineEndMs
                                            if (abs(newEndTimelineMs - oS) < SNAP_THRESHOLD_MS) {
                                                snapTarget = oS
                                            } else if (abs(newEndTimelineMs - oE) < SNAP_THRESHOLD_MS) {
                                                snapTarget = oE
                                            }
                                        }
                                        if (abs(newEndTimelineMs - currentPosMs) < SNAP_THRESHOLD_MS) {
                                            snapTarget = currentPosMs
                                        }

                                        if (snapTarget != null) {
                                            val deltaMs = snapTarget!! - clip.timelineStartMs
                                            val finalSourceEnd = clip.sourceStartMs + deltaMs
                                            onTrimRight(finalSourceEnd.coerceAtMost(maxEnd))
                                            snapGuideX = (snapTarget!!.toFloat() /
                                                    totalMs.toFloat()) * contentWidthPx
                                        } else {
                                            onTrimRight(newEnd)
                                            snapGuideX = -1f
                                        }
                                    },
                                    onDragEnd = {
                                        snapGuideX = -1f
                                        onTrimCommit()
                                    },
                                    onDragCancel = {
                                        snapGuideX = -1f
                                        onTrimCommit()
                                    }
                                )
                            },
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF22C55E))
                        )
                    }
                }
            }

            // Keyframe markers
            KeyframeMarkerOverlay(
                clip = clip,
                clipStartPx = startPx,
                clipWidthPx = clipWidthPx,
                totalMs = totalMs,
                currentPosMs = currentPosMs,
                onSeekToKeyframe = { tSec ->
                    val timeMs = clip.timelineStartMs + (tSec * 1000f).toLong()
                    onSeek(timeMs)
                }
            )
        }

        // 🆕 GREEN SNAP GUIDE LINE
        if (snapGuideX >= 0f) {
            Box(
                modifier = Modifier
                    .offset(x = with(density) { snapGuideX.toDp() })
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF22C55E))
                    .zIndex(50f)
            )
        }
    }
}

private fun formatRulerTime(ms: Long): String {
    val totalSec = ms / 1000
    if (totalSec < 60) return "${totalSec}s"
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
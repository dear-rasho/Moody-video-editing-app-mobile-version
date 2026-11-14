package com.moody.moodyvideoeditor.ui.components

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.runtime.remember
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.EditorState
import com.moody.moodyvideoeditor.utils.TimelineRuler
import com.moody.moodyvideoeditor.utils.TimelineZoom
import com.moody.moodyvideoeditor.utils.WaveformEngine
import com.moody.moodyvideoeditor.utils.SpeedEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt


// ═══════════════════════════════════════════════════════════════
//  CONSTANTS
// ═══════════════════════════════════════════════════════════════

private val RULER_HEIGHT = 20.dp
private val SCROLLBAR_HEIGHT = 8.dp
private val VISUAL_TRACK_HEIGHT = 36.dp
private val AUDIO_TRACK_HEIGHT = 32.dp
private const val DP_PER_SECOND = 20f

private const val SNAP_ENTER_PX = 14f
private const val SNAP_RELEASE_PX = 28f
private const val SNAP_MIN_GAP_MS = 50L

private const val LONG_PRESS_MS = 1_300L

private val GHOST_TRACK_HEIGHT = 48.dp


// ═══════════════════════════════════════════════════════════════
//  DATA CLASSES
// ═══════════════════════════════════════════════════════════════

private data class SnapTarget(val timeMs: Long, val label: String)

private data class DragVisual(
    val active: Boolean = false,
    val clipId: String? = null,
    val isAudio: Boolean = false,
    val targetTrack: Int = -1,
    val needsNewLayer: Boolean = false,
    val sourceTrack: Int = -1,
    val targetTimeMs: Long = 0L
)


// ═══════════════════════════════════════════════════════════════
//  MAIN TIMELINE COMPOSABLE
// ═══════════════════════════════════════════════════════════════

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
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onTransitionDelete: (String) -> Unit = {},
    onTransitionDurationChange: (String, Long) -> Unit = { _, _ -> },
    onReplaceRequested: (String) -> Unit = {},
    onEmptyAreaTapped: () -> Unit = {}   // 🆕 CHANGE #1: khali jagah tap
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val currentPosMsState = rememberUpdatedState(state.currentPosMs)

    var viewportWidthPx by remember { mutableFloatStateOf(0f) }
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    var lastUserScrollMs by remember { mutableLongStateOf(0L) }
    var pendingProgrammaticScroll by remember { mutableIntStateOf(-1) }

    var dragVisual by remember { mutableStateOf(DragVisual()) }

    var selectedTransitionClipId by remember { mutableStateOf<String?>(null) }

    val slider = state.timelineZoom.coerceIn(
        TimelineZoom.SLIDER_MIN, TimelineZoom.SLIDER_MAX
    )
    val viewportContentWidthDp = if (viewportWidthPx > 0f) {
        with(density) {
            viewportWidthPx.coerceAtLeast(100f).toDp().value
        }
    } else {
        360f
    }

// 🆕 Use locked timeline duration
    val totalMs = remember(state.clips, state.timelineLockedMs) {
        state.timelineDurationMs
    }
    val totalSec = totalMs / 1000f

    val pps = TimelineZoom.ppsForSlider(slider)
    val contentWidthDp: Dp =
        TimelineZoom.safeContentWidthDp(totalSec * pps, density.density).dp
    val contentWidthPx = with(density) { contentWidthDp.toPx() }
    val playheadAnchorDp =
        (viewportContentWidthDp * TimelinePlayheadController.PLAYHEAD_ANCHOR_FRACTION).dp
    val timelineTrailingPaddingDp =
        (viewportContentWidthDp - playheadAnchorDp.value).coerceAtLeast(0f).dp
    val visualTracks = remember(state.clips, state.visualLayerCount) {
        state.timelineVisualList()
    }
    val audioTracks = remember(state.clips, state.audioLayerCount) {
        state.timelineAudioList()
    }

    LaunchedEffect(hScroll.interactionSource) {
        hScroll.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                pendingProgrammaticScroll = -1
                lastUserScrollMs = System.currentTimeMillis()
            }
        }
    }

    LaunchedEffect(hScroll.value, lastUserScrollMs, state.currentPosMs, totalMs, contentWidthPx, dragVisual.active) {
        if (dragVisual.active) return@LaunchedEffect
        if (hScroll.value == pendingProgrammaticScroll) {
            pendingProgrammaticScroll = -1
            return@LaunchedEffect
        }
        val userScrolledRecently =
            System.currentTimeMillis() - lastUserScrollMs <
                    TimelinePlayheadController.USER_SCROLL_GRACE_MS
        if (!userScrolledRecently) return@LaunchedEffect
        val seekTimeMs = TimelinePlayheadController.timeForScroll(
            hScroll.value, totalMs, contentWidthPx
        )
        if (abs(seekTimeMs - state.currentPosMs) > 1L) onSeek(seekTimeMs)
    }

    LaunchedEffect(
        state.currentPosMs,
        state.isPlaying,
        viewportWidthPx,
        totalMs,
        contentWidthPx,
        dragVisual.active
    ) {
        if (viewportWidthPx <= 0f) return@LaunchedEffect
        if (dragVisual.active) return@LaunchedEffect
        val target = TimelinePlayheadController.scrollForTime(
            state.currentPosMs, totalMs, contentWidthPx
        )
        if (target != hScroll.value) {
            pendingProgrammaticScroll = target
            hScroll.scrollTo(target)
        }
    }

    LaunchedEffect(
        dragVisual.active,
        dragVisual.targetTrack,
        dragVisual.isAudio,
        dragVisual.needsNewLayer,
        dragVisual.targetTimeMs,
        viewportWidthPx,
        viewportHeightPx
    ) {
        if (!dragVisual.active) return@LaunchedEffect
        if (viewportHeightPx <= 0f || viewportWidthPx <= 0f) return@LaunchedEffect

        val trackHeightPx = with(density) {
            (if (dragVisual.isAudio) AUDIO_TRACK_HEIGHT else VISUAL_TRACK_HEIGHT)
                .toPx()
        }
        val ghostHeightPx = with(density) {
            GHOST_TRACK_HEIGHT.toPx()
        }

        val totalVisual = state.visualLayerCount
        val totalAudio = state.audioLayerCount

        val targetTrackY: Float = if (!dragVisual.isAudio) {
            val topVisualIndex = totalVisual - 1
            val target = dragVisual.targetTrack.coerceIn(0, topVisualIndex)
            (topVisualIndex - target) * trackHeightPx
        } else {
            val visualBlockHeight = totalVisual * trackHeightPx
            val ghostBlockHeight = if (dragVisual.needsNewLayer) ghostHeightPx else 0f
            val target = dragVisual.targetTrack.coerceIn(0, totalAudio)
            visualBlockHeight + ghostBlockHeight + target * trackHeightPx
        }

        val viewportTop = vScroll.value.toFloat()
        val viewportBottom = viewportTop + viewportHeightPx

        val topMargin = viewportHeightPx * 0.20f
        val bottomMargin = viewportHeightPx * 0.20f

        val targetCenterY = targetTrackY + trackHeightPx / 2f

        val scrollTarget: Int? = when {
            targetCenterY < viewportTop + topMargin -> {
                (targetCenterY - topMargin).toInt().coerceAtLeast(0)
            }

            targetCenterY > viewportBottom - bottomMargin -> {
                (targetCenterY - viewportHeightPx + bottomMargin).toInt()
            }

            else -> null
        }

        if (scrollTarget != null) {
            val clamped = scrollTarget.coerceIn(0, vScroll.maxValue)
            if (clamped != vScroll.value) {
                vScroll.animateScrollTo(clamped)
            }
        }

        if (totalMs > 0L && contentWidthPx > 0f) {
            val timelineViewportWidth = viewportWidthPx
            val targetContentX =
                dragVisual.targetTimeMs.coerceAtLeast(0L).toFloat() /
                        totalMs.toFloat() * contentWidthPx
            val leadingPaddingPx = with(density) { playheadAnchorDp.toPx() }
            val targetViewportX = leadingPaddingPx + targetContentX - hScroll.value
            val edgeMargin = with(density) { 48.dp.toPx() }
            val scrollDelta = when {
                targetViewportX < edgeMargin && hScroll.value > 0 -> -24f
                targetViewportX > timelineViewportWidth - edgeMargin &&
                        hScroll.value < hScroll.maxValue -> 24f
                else -> 0f
            }
            if (scrollDelta != 0f) {
                hScroll.scrollTo((hScroll.value + scrollDelta.toInt()).coerceIn(0, hScroll.maxValue))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .onSizeChanged { viewportWidthPx = it.width.toFloat() }
    ) {
        // ─── RULER ───
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RULER_HEIGHT),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0A0A))
                    .horizontalScroll(hScroll)
            ) {
                Row(Modifier.fillMaxHeight()) {
                    Spacer(Modifier.width(playheadAnchorDp))
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
                        val stepSec = TimelineRuler.pickStepSec(
                            zoom = (pps / DP_PER_SECOND).coerceAtLeast(0.01f)
                        )
                        val marks = remember(totalMs, stepSec) {
                            TimelineRuler.marks(totalMs, stepSec)
                        }

                        marks.forEach { timeMs ->
                            val frac = timeMs.toFloat() / totalMs.toFloat()
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
                                text = TimelineRuler.formatLabel(timeMs, stepSec),
                                color = Color(0xFF888888),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.offset(x = xDp + 3.dp, y = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(timelineTrailingPaddingDp))
                }
            }
        }

        // ─── TRACKS ───
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { viewportHeightPx = it.height.toFloat() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(vScroll)
            ) {
                val topVisualIndex = state.visualLayerCount - 1

                for (i in topVisualIndex downTo 0) {
                    if (dragVisual.active && !dragVisual.isAudio &&
                        dragVisual.needsNewLayer &&
                        dragVisual.targetTrack == topVisualIndex + 1 &&
                        i == topVisualIndex
                    ) {
                        GhostTrackDrop(
                            contentWidthDp = contentWidthDp,
                            leadingPaddingDp = playheadAnchorDp,
                            trailingPaddingDp = timelineTrailingPaddingDp,
                            hScroll = hScroll
                        )
                    }

                    VisualTrackRow(
                        trackIndex = i,
                        clips = visualTracks.getOrElse(i) { emptyList() },
                        allClips = state.clips,
                        totalMs = totalMs,
                        contentWidthDp = contentWidthDp,
                        contentWidthPx = contentWidthPx,
                        leadingPaddingDp = playheadAnchorDp,
                        trailingPaddingDp = timelineTrailingPaddingDp,
                        hScroll = hScroll,
                        selectedClipId = state.selectedClipId,
                        multiSelectedIds = state.multiSelectedIds,
                        currentPosMs = currentPosMsState,
                        onClipTapped = onClipTapped,
                        onTrackTapped = onTrackTapped,
                        onTrimLeft = onTrimLeft,
                        onTrimRight = onTrimRight,
                        onTrimCommit = onTrimCommit,
                        onSeek = onSeek,
                        onMoveClip = onMoveClip,
                        onDragStart = {
                            lastUserScrollMs = 0L
                            onDragStart()
                        },
                        onDragEnd = {
                            dragVisual = DragVisual()
                            onDragEnd()
                        },
                        onDragCancel = {
                            dragVisual = DragVisual()
                            onDragCancel()
                        },
                        onDragVisualUpdate = { updated -> dragVisual = updated },
                        dragVisual = dragVisual,
                        visualLayerCount = state.visualLayerCount,
                        audioLayerCount = state.audioLayerCount,
                        onTransitionDelete = onTransitionDelete,
                        onTransitionDurationChange = onTransitionDurationChange,
                        selectedTransitionClipId = selectedTransitionClipId,
                        onTransitionTapped = { id ->
                            selectedTransitionClipId =
                                if (selectedTransitionClipId == id) null else id
                        },
                        onReplaceRequested = onReplaceRequested,
                        onEmptyAreaTapped = onEmptyAreaTapped,   // 🆕 pass through
                        haptic = haptic
                    )
                }

                for (i in 0 until state.audioLayerCount) {
                    if (dragVisual.active && dragVisual.isAudio &&
                        dragVisual.needsNewLayer &&
                        dragVisual.targetTrack == state.audioLayerCount &&
                        i == state.audioLayerCount - 1
                    ) {
                        GhostTrackDrop(
                            contentWidthDp = contentWidthDp,
                            leadingPaddingDp = playheadAnchorDp,
                            trailingPaddingDp = timelineTrailingPaddingDp,
                            hScroll = hScroll
                        )
                    }

                    AudioTrackRow(
                        trackIndex = i,
                        clips = audioTracks.getOrElse(i) { emptyList() },
                        allClips = state.clips,
                        totalMs = totalMs,
                        contentWidthDp = contentWidthDp,
                        contentWidthPx = contentWidthPx,
                        leadingPaddingDp = playheadAnchorDp,
                        trailingPaddingDp = timelineTrailingPaddingDp,
                        hScroll = hScroll,
                        selectedClipId = state.selectedClipId,
                        multiSelectedIds = state.multiSelectedIds,
                        currentPosMs = currentPosMsState,
                        onClipTapped = onClipTapped,
                        onTrackTapped = onTrackTapped,
                        onTrimLeft = onTrimLeft,
                        onTrimRight = onTrimRight,
                        onTrimCommit = onTrimCommit,
                        onSeek = onSeek,
                        onMoveClip = onMoveClip,
                        onDragStart = {
                            lastUserScrollMs = 0L
                            onDragStart()
                        },
                        onDragEnd = {
                            dragVisual = DragVisual()
                            onDragEnd()
                        },
                        onDragCancel = {
                            dragVisual = DragVisual()
                            onDragCancel()
                        },
                        onDragVisualUpdate = { updated -> dragVisual = updated },
                        dragVisual = dragVisual,
                        visualLayerCount = state.visualLayerCount,
                        audioLayerCount = state.audioLayerCount,
                        onTransitionDelete = onTransitionDelete,
                        onTransitionDurationChange = onTransitionDurationChange,
                        selectedTransitionClipId = selectedTransitionClipId,
                        onTransitionTapped = { id ->
                            selectedTransitionClipId =
                                if (selectedTransitionClipId == id) null else id
                        },
                        onReplaceRequested = onReplaceRequested,
                        onEmptyAreaTapped = onEmptyAreaTapped,   // 🆕 pass through
                        haptic = haptic
                    )
                }

                Box(Modifier.height(40.dp))
            }

            TimelinePlayhead(viewportWidthPx = viewportWidthPx)
        }

        TimelineScrollbar(
            hScroll = hScroll,
            contentWidthPx = contentWidthPx,
            viewportWidthPx = viewportWidthPx,
            onUserScroll = {
                pendingProgrammaticScroll = -1
                lastUserScrollMs = System.currentTimeMillis()
            }
        )
    }
}


// ═══════════════════════════════════════════════════════════════
//  VISUAL TRACK ROW
// ═══════════════════════════════════════════════════════════════

@Composable
private fun VisualTrackRow(
    trackIndex: Int,
    clips: List<EditorClip>,
    allClips: List<EditorClip>,
    totalMs: Long,
    contentWidthDp: Dp,
    contentWidthPx: Float,
    leadingPaddingDp: Dp,
    trailingPaddingDp: Dp,
    hScroll: ScrollState,
    selectedClipId: String?,
    multiSelectedIds: Set<String>,
    currentPosMs: State<Long>,
    onClipTapped: (EditorClip) -> Unit,
    onTrackTapped: (Int, Boolean) -> Unit,
    onTrimLeft: (Long) -> Unit,
    onTrimRight: (Long) -> Unit,
    onTrimCommit: () -> Unit,
    onSeek: (Long) -> Unit,
    onMoveClip: (String, Int, Boolean, Long) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDragVisualUpdate: (DragVisual) -> Unit,
    dragVisual: DragVisual,
    visualLayerCount: Int,
    audioLayerCount: Int,
    onTransitionDelete: (String) -> Unit,
    onTransitionDurationChange: (String, Long) -> Unit,
    selectedTransitionClipId: String?,
    onTransitionTapped: (String) -> Unit,
    onReplaceRequested: (String) -> Unit,
    onEmptyAreaTapped: () -> Unit,
    haptic: HapticFeedback
) {
    val isDragTarget = dragVisual.active && !dragVisual.isAudio &&
            dragVisual.targetTrack == trackIndex

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(VISUAL_TRACK_HEIGHT)
            .background(Color(0xFF141414))
            .horizontalScroll(hScroll)
            .then(
                if (isDragTarget && !dragVisual.needsNewLayer) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = Color(0xFF22C55E).copy(alpha = 0.8f)
                    )
                } else Modifier
            )
    ) {
        Row(Modifier.fillMaxHeight()) {
            Spacer(Modifier.width(leadingPaddingDp))
            TrackContent(
                clips = clips,
                allClips = allClips,
                totalMs = totalMs,
                contentWidthDp = contentWidthDp,
                contentWidthPx = contentWidthPx,
                selectedClipId = selectedClipId,
                multiSelectedIds = multiSelectedIds,
                isAudio = false,
                trackIndex = trackIndex,
                currentPosMs = currentPosMs,
                onClipTapped = onClipTapped,
                onTrimLeft = onTrimLeft,
                onTrimRight = onTrimRight,
                onTrimCommit = onTrimCommit,
                onSeek = onSeek,
                onMoveClip = onMoveClip,
                onDragStart = onDragStart,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
                onDragVisualUpdate = onDragVisualUpdate,
                dragVisual = dragVisual,
                hScroll = hScroll,
                onTransitionDelete = onTransitionDelete,
                onTransitionDurationChange = onTransitionDurationChange,
                selectedTransitionClipId = selectedTransitionClipId,
                onTransitionTapped = onTransitionTapped,
                visualLayerCount = visualLayerCount,
                audioLayerCount = audioLayerCount,
                haptic = haptic,
                onReplaceRequested = onReplaceRequested,
                onEmptyAreaTapped = onEmptyAreaTapped,
                onTrackTapped = onTrackTapped
            )
            Spacer(Modifier.width(trailingPaddingDp))
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
    allClips: List<EditorClip>,
    totalMs: Long,
    contentWidthDp: Dp,
    contentWidthPx: Float,
    leadingPaddingDp: Dp,
    trailingPaddingDp: Dp,
    hScroll: ScrollState,
    selectedClipId: String?,
    multiSelectedIds: Set<String>,
    currentPosMs: State<Long>,
    onClipTapped: (EditorClip) -> Unit,
    onTrackTapped: (Int, Boolean) -> Unit,
    onTrimLeft: (Long) -> Unit,
    onTrimRight: (Long) -> Unit,
    onTrimCommit: () -> Unit,
    onSeek: (Long) -> Unit,
    onMoveClip: (String, Int, Boolean, Long) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDragVisualUpdate: (DragVisual) -> Unit,
    dragVisual: DragVisual,
    visualLayerCount: Int,
    audioLayerCount: Int,
    onTransitionDelete: (String) -> Unit,
    onTransitionDurationChange: (String, Long) -> Unit,
    selectedTransitionClipId: String?,
    onTransitionTapped: (String) -> Unit,
    onReplaceRequested: (String) -> Unit,
    onEmptyAreaTapped: () -> Unit,
    haptic: HapticFeedback
) {
    val isDragTarget = dragVisual.active && dragVisual.isAudio &&
            dragVisual.targetTrack == trackIndex

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AUDIO_TRACK_HEIGHT)
            .background(Color(0xFF141414))
            .horizontalScroll(hScroll)
            .then(
                if (isDragTarget && !dragVisual.needsNewLayer) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = Color(0xFF22C55E).copy(alpha = 0.8f)
                    )
                } else Modifier
            )
    ) {
        Row(Modifier.fillMaxHeight()) {
            Spacer(Modifier.width(leadingPaddingDp))
            TrackContent(
                clips = clips,
                allClips = allClips,
                totalMs = totalMs,
                contentWidthDp = contentWidthDp,
                contentWidthPx = contentWidthPx,
                selectedClipId = selectedClipId,
                multiSelectedIds = multiSelectedIds,
                isAudio = true,
                trackIndex = trackIndex,
                currentPosMs = currentPosMs,
                onClipTapped = onClipTapped,
                onTrimLeft = onTrimLeft,
                onTrimRight = onTrimRight,
                onTrimCommit = onTrimCommit,
                onSeek = onSeek,
                onMoveClip = onMoveClip,
                onDragStart = onDragStart,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
                onDragVisualUpdate = onDragVisualUpdate,
                dragVisual = dragVisual,
                hScroll = hScroll,
                onTransitionDelete = onTransitionDelete,
                onTransitionDurationChange = onTransitionDurationChange,
                selectedTransitionClipId = selectedTransitionClipId,
                onTransitionTapped = onTransitionTapped,
                visualLayerCount = visualLayerCount,
                audioLayerCount = audioLayerCount,
                haptic = haptic,
                onReplaceRequested = onReplaceRequested,
                onEmptyAreaTapped = onEmptyAreaTapped,
                onTrackTapped = onTrackTapped
            )
            Spacer(Modifier.width(trailingPaddingDp))
        }
    }
}


// ═══════════════════════════════════════════════════════════════
//  GHOST TRACK DROP ZONE
// ═══════════════════════════════════════════════════════════════

@Composable
private fun GhostTrackDrop(
    contentWidthDp: Dp,
    leadingPaddingDp: Dp,
    trailingPaddingDp: Dp,
    hScroll: ScrollState
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(GHOST_TRACK_HEIGHT)
            .background(Color(0xFF0F1A0F))
            .border(
                width = 1.dp,
                color = Color(0xFF22C55E).copy(alpha = 0.5f)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F1A0F))
                .horizontalScroll(hScroll)
        ) {
            Row(Modifier.fillMaxHeight()) {
                Spacer(Modifier.width(leadingPaddingDp))
                Box(
                    modifier = Modifier
                        .width(contentWidthDp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Drop here to create new layer",
                        color = Color(0xFF22C55E).copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(trailingPaddingDp))
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════
//  TRACK CONTENT
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
    currentPosMs: State<Long>,
    onClipTapped: (EditorClip) -> Unit,
    onTrimLeft: (Long) -> Unit,
    onTrimRight: (Long) -> Unit,
    onTrimCommit: () -> Unit,
    onSeek: (Long) -> Unit,
    onMoveClip: (String, Int, Boolean, Long) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDragVisualUpdate: (DragVisual) -> Unit,
    dragVisual: DragVisual,
    hScroll: ScrollState,
    onTransitionDelete: (String) -> Unit,
    onTransitionDurationChange: (String, Long) -> Unit,
    selectedTransitionClipId: String?,
    onTransitionTapped: (String) -> Unit,
    visualLayerCount: Int,
    audioLayerCount: Int,
    haptic: HapticFeedback,
    onReplaceRequested: (String) -> Unit = {},
    onEmptyAreaTapped: () -> Unit = {},
    onTrackTapped: (Int, Boolean) -> Unit
) {
    val density = LocalDensity.current

    var snapGuideX by remember { mutableFloatStateOf(-1f) }
    var snapLabel by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .width(contentWidthDp)
            .fillMaxHeight()
            .pointerInput(clips, totalMs, contentWidthPx) {
                detectTapGestures { offset ->
                    if (contentWidthPx <= 0f || totalMs <= 0L) return@detectTapGestures
                    val tappedClip = clips.firstOrNull { clip ->
                        val startPx = clip.timelineStartMs.toFloat() / totalMs * contentWidthPx
                        val endPx = clip.timelineEndMs.toFloat() / totalMs * contentWidthPx
                        offset.x in startPx..endPx
                    }
                    if (tappedClip == null) {
                        onEmptyAreaTapped()
                        onTrackTapped(trackIndex, isAudio)
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
        val stepPx = with(density) {
            (if (isAudio) AUDIO_TRACK_HEIGHT else VISUAL_TRACK_HEIGHT).toPx()
        }

        val enterMs = (SNAP_ENTER_PX / pxPerMs).toLong().coerceAtLeast(SNAP_MIN_GAP_MS)
        val releaseMs = (SNAP_RELEASE_PX / pxPerMs).toLong().coerceAtLeast(enterMs * 2)
        val selectedClip = allClips.firstOrNull { it.id == selectedClipId }

        clips.forEach { clip ->
            ClipCard(
                clip = clip,
                allClips = allClips,
                totalMs = totalMs,
                contentWidthPx = contentWidthPx,
                pxPerMs = pxPerMs,
                stepPx = stepPx,
                currentPosMs = currentPosMs,
                isSelected = clip.id == selectedClipId ||
                        (multiSelectedIds.contains(clip.id) &&
                                selectedClip?.linkedId != null &&
                                clip.linkedId == selectedClip.linkedId) ||
                        (multiSelectedIds.contains(clip.id) &&
                                selectedClip?.linkGroupId != null &&
                                clip.linkGroupId == selectedClip.linkGroupId),
                isMulti = multiSelectedIds.contains(clip.id) &&
                        clip.id != selectedClipId &&
                        !(selectedClip?.linkedId != null &&
                                clip.linkedId == selectedClip.linkedId) &&
                        !(selectedClip?.linkGroupId != null &&
                                clip.linkGroupId == selectedClip.linkGroupId),
                isDragging = dragVisual.active && dragVisual.clipId == clip.id,
                isAudio = isAudio,
                visualLayerCount = visualLayerCount,
                audioLayerCount = audioLayerCount,
                enterMs = enterMs,
                releaseMs = releaseMs,
                onClipTapped = onClipTapped,
                onTrimLeft = onTrimLeft,
                onTrimRight = onTrimRight,
                onTrimCommit = onTrimCommit,
                onSeek = onSeek,
                onMoveClip = onMoveClip,
                onDragStart = onDragStart,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
                onDragVisualUpdate = onDragVisualUpdate,
                hScroll = hScroll,
                onSnapGuideUpdate = { x, label ->
                    snapGuideX = x
                    snapLabel = label
                },
                onReplaceRequested = onReplaceRequested,
                haptic = haptic
            )
        }

        clips.forEach { clip ->
            val trans = clip.transition
            if (clip.isVisualClip && trans != null && trans.isActive) {
                val hasAdjacentBefore = allClips.any { other ->
                    other.id != clip.id && other.isVisualClip &&
                            abs(other.timelineEndMs - clip.timelineStartMs) < 50L
                }
                if (hasAdjacentBefore) {
                    val junctionPx = clip.timelineStartMs.toFloat() /
                            totalMs * contentWidthPx
                    val isTransSelected = selectedTransitionClipId == clip.id

                    Box(
                        modifier = Modifier
                            .offset(
                                x = with(density) { (junctionPx - 14f).toDp() }
                            )
                            .width(28.dp)
                            .height(20.dp)
                            .zIndex(25f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isTransSelected) Color(0xFFFF3B3B)
                                else Color(0xFFA855F7)
                            )
                            .border(
                                width = 1.5.dp,
                                color = Color.White.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .pointerInput(clip.id) {
                                detectTapGestures { onTransitionTapped(clip.id) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "⇄",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isTransSelected) {
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = with(density) { (junctionPx + 12f).toDp() },
                                    y = (-2).dp
                                )
                                .size(16.dp)
                                .zIndex(26f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFF3B3B))
                                .border(
                                    width = 1.dp,
                                    color = Color.White,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .pointerInput(clip.id) {
                                    detectTapGestures {
                                        onTransitionDelete(clip.id)
                                        onTransitionTapped(clip.id)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✕",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "%.2fs".format(trans.durationMs / 1000f),
                        color = Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .offset(
                                x = with(density) { (junctionPx - 16f).toDp() },
                                y = 22.dp
                            )
                            .zIndex(25f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        if (snapGuideX >= 0f) {
            Box(
                modifier = Modifier
                    .offset(x = with(density) { (snapGuideX - 4f).toDp() })
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(Color(0xFFFFD166).copy(alpha = 0.35f))
                    .zIndex(58f)
            )
            Box(
                modifier = Modifier
                    .offset(x = with(density) { (snapGuideX - 1.5f).toDp() })
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(Color(0xFFFFD166))
                    .zIndex(60f)
            )
            snapLabel?.let { label ->
                Text(
                    text = "🔗 $label",
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .offset(
                            x = with(density) { (snapGuideX + 6).toDp() },
                            y = 4.dp
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFFD166))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .zIndex(61f)
                )
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════
//  CLIP CARD
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ClipCard(
    clip: EditorClip,
    allClips: List<EditorClip>,
    totalMs: Long,
    contentWidthPx: Float,
    pxPerMs: Float,
    stepPx: Float,
    currentPosMs: State<Long>,
    isSelected: Boolean,
    isMulti: Boolean,
    isDragging: Boolean,
    isAudio: Boolean,
    visualLayerCount: Int,
    audioLayerCount: Int,
    enterMs: Long,
    releaseMs: Long,
    onClipTapped: (EditorClip) -> Unit,
    onTrimLeft: (Long) -> Unit,
    onTrimRight: (Long) -> Unit,
    onTrimCommit: () -> Unit,
    onSeek: (Long) -> Unit,
    onMoveClip: (String, Int, Boolean, Long) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDragVisualUpdate: (DragVisual) -> Unit,
    hScroll: ScrollState,
    onSnapGuideUpdate: (Float, String?) -> Unit,
    onReplaceRequested: (String) -> Unit = {},
    haptic: HapticFeedback
) {
    val density = LocalDensity.current
    val latestClipState = rememberUpdatedState(clip)
    val latestAllClipsState = rememberUpdatedState(allClips)
    val latestCurrentPosMs = currentPosMs


    val barColor = clipColor(clip)

    var accumDragX by remember { mutableFloatStateOf(0f) }
    var accumDragY by remember { mutableFloatStateOf(0f) }
    var dragStartTimeMs by remember { mutableLongStateOf(0L) }
    var dragStartTrack by remember { mutableIntStateOf(0) }
    var dragTargetTimeMs by remember { mutableLongStateOf(0L) }
    var dragTargetTrack by remember { mutableIntStateOf(0) }
    var clipLifted by remember { mutableStateOf(false) }

    // startPx ab live position se calculate hoga
    val startPx = (dragTargetTimeMs.takeIf { clipLifted } ?: clip.timelineStartMs).toFloat() / totalMs * contentWidthPx
    val endPx = startPx + (clip.durationMs.toFloat() / totalMs * contentWidthPx)
    val clipWidthPx = (endPx - startPx).coerceAtLeast(20f)

    Box(
        modifier = Modifier
            .offset(x = with(density) { startPx.toDp() })
            .width(with(density) { clipWidthPx.toDp() })
            // ...
            .fillMaxHeight()
            .padding(vertical = 2.dp)
            .zIndex(
                if (isDragging || clipLifted) 30f
                else if (isSelected) 10f
                else if (isMulti) 9f
                else 1f
            )
            .graphicsLayer {
                if (isDragging || clipLifted) {
                    translationX = if (clipLifted) 0f else accumDragX
                    translationY = accumDragY
                    alpha = 0.85f
                    scaleX = 1.05f
                    scaleY = 1.05f
                }
            }
            .clip(RoundedCornerShape(6.dp))
            .background(if (isMulti) Color(0xFF4F9DFF) else barColor)
            .then(
                if (isSelected && !isDragging && !clipLifted) {
                    Modifier.border(
                        width = 2.dp,
                        color = Color.White,
                        shape = RoundedCornerShape(6.dp)
                    )
                } else if (isDragging || clipLifted) {
                    Modifier.border(
                        width = 2.dp,
                        color = Color(0xFF60EFFF),
                        shape = RoundedCornerShape(6.dp)
                    )
                } else Modifier
            )
            .graphicsLayer { alpha = if (isDragging || clipLifted) 0.85f else 1f }
            .pointerInput(clip.id, contentWidthPx, totalMs, pxPerMs, stepPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val dragClip = latestClipState.value
                    accumDragX = 0f
                    accumDragY = 0f
                    dragStartTimeMs = dragClip.timelineStartMs
                    dragStartTrack = dragClip.trackIndex
                    dragTargetTimeMs = dragClip.timelineStartMs
                    dragTargetTrack = dragClip.trackIndex
                    clipLifted = false

                    var totalMove = 0f
                    val trimHandleHitWidthPx = with(density) { 16.dp.toPx() }
                    val hasDistinctTrimHandles = size.width > trimHandleHitWidthPx * 2
                    if (hasDistinctTrimHandles &&
                        (down.position.x <= trimHandleHitWidthPx ||
                                down.position.x >= size.width - trimHandleHitWidthPx)
                    ) {
                        return@awaitEachGesture
                    }
                    val longPressSuccess = withTimeoutOrNull(LONG_PRESS_MS) {
                        var waitingForLongPress = true
                        while (waitingForLongPress) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: return@withTimeoutOrNull false
                            if (!change.pressed || change.isConsumed) {
                                return@withTimeoutOrNull false
                            }
                            val delta = change.position - down.position
                            totalMove = kotlin.math.sqrt(
                                delta.x * delta.x + delta.y * delta.y
                            )
                            waitingForLongPress =
                                change.pressed && !change.isConsumed && totalMove <= 20f
                            if (!waitingForLongPress) {
                                return@withTimeoutOrNull false
                            }
                        }
                        false
                    } ?: true

                    if (!longPressSuccess) {
                        if (totalMove < 12f) {
                            onClipTapped(dragClip)
                            onSeek(
                                TimelinePlayheadController.timeForClipTap(
                                    clipStartMs = dragClip.timelineStartMs,
                                    clipDurationMs = dragClip.durationMs,
                                    localX = down.position.x,
                                    pixelsPerMs = pxPerMs,
                                    totalMs = totalMs
                                )
                            )
                        }
                        return@awaitEachGesture
                    }

                    if (com.moody.moodyvideoeditor.utils.SettingsConsumer.hapticFeedback) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    down.consume()
                    clipLifted = true
                    onDragStart()

                    try {
                    var continueDrag = true
                    while (continueDrag) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            continueDrag = false
                            break
                        }

                        val ch = pressed.firstOrNull { it.id == down.id } ?: pressed.first()

                        val panX = ch.position.x - down.position.x
                        val panY = ch.position.y - down.position.y
                        val minPanX = -dragStartTimeMs.toFloat() * pxPerMs
                        val boundedPanX = panX.coerceAtLeast(minPanX)
                        accumDragX = boundedPanX
                        accumDragY = panY

                        val virtualStartMs = (dragStartTimeMs +
                                (boundedPanX / pxPerMs).toLong()).coerceAtLeast(0L)
                        val virtualEndMs = virtualStartMs + dragClip.durationMs

                        val targets = mutableListOf<SnapTarget>()
                        latestAllClipsState.value.filter { it.id != dragClip.id }.forEach { other ->
                            if (other.isAudio != dragClip.isAudio) return@forEach
                            targets.add(
                                SnapTarget(
                                    other.timelineStartMs,
                                    "Start of ${other.name.take(14)}"
                                )
                            )
                            targets.add(
                                SnapTarget(
                                    other.timelineEndMs,
                                    "End of ${other.name.take(14)}"
                                )
                            )
                        }
                        targets.add(SnapTarget(latestCurrentPosMs.value, "Playhead"))

                        val snapEnabled =
                            com.moody.moodyvideoeditor.utils.SettingsConsumer.snapToGrid

                        var bestTarget: SnapTarget? = null
                        var bestDist = enterMs
                        var snapAtEnd = false

                        if (snapEnabled) {
                            targets.forEach { t ->
                                val dStart = abs(t.timeMs - virtualStartMs)
                                if (dStart < bestDist) {
                                    bestDist = dStart
                                    bestTarget = t
                                    snapAtEnd = false
                                }
                                val dEnd = abs(t.timeMs - virtualEndMs)
                                if (dEnd < bestDist) {
                                    bestDist = dEnd
                                    bestTarget = t
                                    snapAtEnd = true
                                }
                            }
                        }

                        val captured = bestTarget
                        val finalTimeMs: Long
                        if (captured != null) {
                            finalTimeMs = if (snapAtEnd) {
                                (captured.timeMs - dragClip.durationMs).coerceAtLeast(0L)
                            } else {
                                captured.timeMs
                            }
                            val guideX = (captured.timeMs.toFloat() /
                                    totalMs.toFloat()) * contentWidthPx
                            onSnapGuideUpdate(guideX, "Snap: ${captured.label}")
                        } else {
                            finalTimeMs = virtualStartMs
                            onSnapGuideUpdate(-1f, null)
                        }
                        dragTargetTimeMs = finalTimeMs

                        val steps = (panY / stepPx).roundToInt()
                        val newTrack = if (dragClip.isAudio) {
                            (dragStartTrack + steps).coerceAtLeast(0)
                        } else {
                            (dragStartTrack - steps).coerceAtLeast(0)
                        }
                        dragTargetTrack = newTrack

                        val maxTrack = if (dragClip.isAudio) audioLayerCount
                        else visualLayerCount
                        val needsNew = newTrack >= maxTrack

                        onDragVisualUpdate(
                            DragVisual(
                                active = true,
                                clipId = dragClip.id,
                                isAudio = dragClip.isAudio,
                                targetTrack = newTrack.coerceAtMost(maxTrack),
                                needsNewLayer = needsNew,
                                sourceTrack = dragStartTrack,
                                targetTimeMs = finalTimeMs
                            )
                        )

                        ch.consume()
                    }

                    clipLifted = false
                    onSnapGuideUpdate(-1f, null)

                    if (dragTargetTrack != dragClip.trackIndex ||
                        dragTargetTimeMs != dragClip.timelineStartMs
                    ) {
                        onMoveClip(
                            dragClip.id,
                            dragTargetTrack,
                            dragClip.isAudio,
                            dragTargetTimeMs
                        )
                    }

                    accumDragX = 0f
                    accumDragY = 0f
                    onDragEnd()
                    } catch (cancelled: CancellationException) {
                        clipLifted = false
                        accumDragX = 0f
                        accumDragY = 0f
                        onSnapGuideUpdate(-1f, null)
                        onDragCancel()
                        throw cancelled
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (clip.isAudio && clip.uri != Uri.EMPTY &&
            !clip.type.endsWith("/plain")
        ) {
            AudioWaveformBackground(
                uri = clip.uri,
                sourceStartMs = clip.sourceStartMs,
                sourceEndMs = clip.sourceEndMs,
                sourceTotalMs = clip.sourceTotalMs,
                volume = clip.volume,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
            )
        }

        Text(
            text = clip.name.take(20),
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.zIndex(10f)
        )

        // 🆕 REPLACE ICON — top-right corner
        if (!isDragging && !clipLifted && clip.isReplaceable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 2.dp, top = 2.dp)
                    .size(16.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF4F9DFF).copy(alpha = 0.9f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onReplaceRequested(clip.id)
                    }
                    .zIndex(20f),
                contentAlignment = Alignment.Center
            ) {
                Text("🔄", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (isSelected && !isDragging && !clipLifted) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(24.dp)
                    .fillMaxHeight()
                    .zIndex(5f)
            ) {
                TrimHandleLeft(
                    clip = clip,
                    totalMs = totalMs,
                    contentWidthPx = contentWidthPx,
                    currentPosMs = currentPosMs,
                    allClips = allClips,
                    pxPerMs = pxPerMs,
                    enterMs = enterMs,
                    releaseMs = releaseMs,
                    onTrimLeft = onTrimLeft,
                    onTrimCommit = onTrimCommit,
                    hScroll = hScroll,
                    handleContentStartPx = startPx,
                    onSnapGuideUpdate = onSnapGuideUpdate
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(24.dp)
                    .fillMaxHeight()
                    .zIndex(5f)
            ) {
                TrimHandleRight(
                    clip = clip,
                    totalMs = totalMs,
                    contentWidthPx = contentWidthPx,
                    currentPosMs = currentPosMs,
                    allClips = allClips,
                    pxPerMs = pxPerMs,
                    enterMs = enterMs,
                    releaseMs = releaseMs,
                    onTrimRight = onTrimRight,
                    onTrimCommit = onTrimCommit,
                    hScroll = hScroll,
                    handleContentStartPx = startPx + clipWidthPx - with(density) { 24.dp.toPx() },
                    onSnapGuideUpdate = onSnapGuideUpdate
                )
            }
        }

        KeyframeMarkerOverlay(
            clip = clip,
            clipWidthPx = clipWidthPx,
            currentPosMs = currentPosMs,
            onSeekToKeyframe = { tSec ->
                val timeMs = clip.timelineStartMs + (tSec * 1000f).toLong()
                onClipTapped(clip)
                onSeek(timeMs)
            }
        )
    }
}


// ═══════════════════════════════════════════════════════════════
//  TRIM HANDLES
// ═══════════════════════════════════════════════════════════════

@Composable
private fun TrimHandleLeft(
    clip: EditorClip,
    totalMs: Long,
    contentWidthPx: Float,
    currentPosMs: State<Long>,
    allClips: List<EditorClip>,
    pxPerMs: Float,
    enterMs: Long,
    releaseMs: Long,
    onTrimLeft: (Long) -> Unit,
    onTrimCommit: () -> Unit,
    hScroll: ScrollState,
    handleContentStartPx: Float,
    onSnapGuideUpdate: (Float, String?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val latestClip = rememberUpdatedState(clip)
    val latestAllClips = rememberUpdatedState(allClips)
    val latestCurrentPosMs = currentPosMs
    val latestPxPerMs = rememberUpdatedState(pxPerMs)
    val latestTotalMs = rememberUpdatedState(totalMs)
    val latestContentWidthPx = rememberUpdatedState(contentWidthPx)
    val latestHandleContentStartPx = rememberUpdatedState(handleContentStartPx)
    val latestOnTrimLeft = rememberUpdatedState(onTrimLeft)
    val latestOnTrimCommit = rememberUpdatedState(onTrimCommit)
    val latestOnSnapGuideUpdate = rememberUpdatedState(onSnapGuideUpdate)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(clip.id + "-L") {
                var accumX = 0f
                var startMs = 0L
                var endMs = 0L
                var dragClip = clip
                var activeTarget: SnapTarget? = null
                var pointerScreenX = 0f
                var autoScrollDirection = 0
                var autoScrollJob: Job? = null

                fun updateAutoScroll(change: androidx.compose.ui.input.pointer.PointerInputChange) {
                    pointerScreenX =
                        latestHandleContentStartPx.value + change.position.x - hScroll.value
                    val edgeSize = 40f
                    autoScrollDirection = when {
                        pointerScreenX < edgeSize -> -1
                        pointerScreenX > hScroll.viewportSize - edgeSize -> 1
                        else -> 0
                    }
                    if (autoScrollDirection == 0) {
                        autoScrollJob?.cancel()
                        autoScrollJob = null
                    } else if (autoScrollJob?.isActive != true) {
                        autoScrollJob = coroutineScope.launch {
                            while (isActive && autoScrollDirection != 0) {
                                val scrolled = hScroll.dispatchRawDelta(autoScrollDirection * 12f)
                                if (abs(scrolled) < 0.5f) break
                                accumX += scrolled
                                val deltaMs = (accumX / latestPxPerMs.value).toLong()
                                val timelineOffset = deltaMs.coerceIn(
                                    0L,
                                    (dragClip.durationMs - EditorClip.MIN_DURATION_MS)
                                        .coerceAtLeast(0L)
                                )
                                val trimmedStart = SpeedEngine.sourcePositionAt(
                                    dragClip,
                                    timelineOffset
                                ).coerceIn(startMs, endMs - EditorClip.MIN_DURATION_MS)
                                latestOnTrimLeft.value(trimmedStart)
                                activeTarget = null
                                latestOnSnapGuideUpdate.value(-1f, null)
                                delay(16)
                            }
                        }
                    }
                }

                detectDragGestures(
                    onDragStart = { _: Offset ->
                        accumX = 0f
                        dragClip = latestClip.value
                        startMs = dragClip.sourceStartMs
                        endMs = dragClip.sourceEndMs
                        activeTarget = null
                        autoScrollDirection = 0
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        accumX += drag.x
                        val currentClip = dragClip
                        val dMs = (accumX / latestPxPerMs.value).toLong()
                        val maxTimelineOffset =
                            (currentClip.durationMs - EditorClip.MIN_DURATION_MS)
                                .coerceAtLeast(0L)
                        val newStartOffset = dMs.coerceIn(0L, maxTimelineOffset)
                        val newStart = SpeedEngine.sourcePositionAt(currentClip, newStartOffset)
                            .coerceIn(startMs, endMs - EditorClip.MIN_DURATION_MS)
                        val newStartTimelineMs = currentClip.timelineStartMs + newStartOffset

                        val targets = mutableListOf<SnapTarget>()
                        latestAllClips.value.filter { it.id != currentClip.id }.forEach { other ->
                            targets.add(
                                SnapTarget(
                                    other.timelineStartMs,
                                    "Start of ${other.name.take(14)}"
                                )
                            )
                            targets.add(
                                SnapTarget(
                                    other.timelineEndMs,
                                    "End of ${other.name.take(14)}"
                                )
                            )
                        }
                        targets.add(SnapTarget(latestCurrentPosMs.value, "Playhead"))

                        var bestTarget: SnapTarget? = null
                        var bestDist = enterMs
                        targets.forEach { t ->
                            val d = abs(t.timeMs - newStartTimelineMs)
                            val current = activeTarget
                            if (current != null &&
                                current.timeMs == t.timeMs &&
                                d <= releaseMs
                            ) {
                                bestTarget = t
                                bestDist = d
                                return@forEach
                            }
                            if (d < bestDist) {
                                bestDist = d
                                bestTarget = t
                            }
                        }

                        val captured = bestTarget
                        if (captured != null) {
                            activeTarget = captured
                            val targetOffset = (captured.timeMs - currentClip.timelineStartMs)
                                .coerceIn(0L, maxTimelineOffset)
                            val finalSourceStart = SpeedEngine.sourcePositionAt(
                                currentClip,
                                targetOffset
                            ).coerceIn(startMs, endMs - EditorClip.MIN_DURATION_MS)
                            latestOnTrimLeft.value(finalSourceStart)
                            val guideX = (captured.timeMs.toFloat() /
                                    latestTotalMs.value.toFloat()) * latestContentWidthPx.value
                            latestOnSnapGuideUpdate.value(guideX, "Snap: ${captured.label}")
                        } else {
                            activeTarget = null
                            latestOnTrimLeft.value(newStart)
                            latestOnSnapGuideUpdate.value(-1f, null)
                        }
                        updateAutoScroll(change)
                    },
                    onDragEnd = {
                        autoScrollJob?.cancel()
                        autoScrollJob = null
                        latestOnSnapGuideUpdate.value(-1f, null)
                        activeTarget = null
                        latestOnTrimCommit.value()
                    },
                    onDragCancel = {
                        autoScrollJob?.cancel()
                        autoScrollJob = null
                        latestOnSnapGuideUpdate.value(-1f, null)
                        activeTarget = null
                        latestOnTrimCommit.value()
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .fillMaxHeight()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFFFD166))
        )
    }
}

private fun expandClipForRightTrim(
    clip: EditorClip,
    requestedTimelineOffsetMs: Long
): EditorClip {
    val sourceEnd = if (clip.sourceTotalMs != Long.MAX_VALUE) {
        maxOf(clip.sourceEndMs, clip.sourceTotalMs)
    } else {
        val extraTimelineMs =
            (requestedTimelineOffsetMs - clip.durationMs).coerceAtLeast(0L)
        val requiredSourceEnd = clip.sourceEndMs.toDouble() +
                extraTimelineMs * SpeedEngine.MAX_SPEED + 1_000.0
        requiredSourceEnd.coerceAtMost(Long.MAX_VALUE / 2.0).toLong()
    }
    return clip.copy(sourceEndMs = sourceEnd)
}

@Composable
private fun TrimHandleRight(
    clip: EditorClip,
    totalMs: Long,
    contentWidthPx: Float,
    currentPosMs: State<Long>,
    allClips: List<EditorClip>,
    pxPerMs: Float,
    enterMs: Long,
    releaseMs: Long,
    onTrimRight: (Long) -> Unit,
    onTrimCommit: () -> Unit,
    hScroll: ScrollState,
    handleContentStartPx: Float,
    onSnapGuideUpdate: (Float, String?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val latestClip = rememberUpdatedState(clip)
    val latestAllClips = rememberUpdatedState(allClips)
    val latestCurrentPosMs = currentPosMs
    val latestPxPerMs = rememberUpdatedState(pxPerMs)
    val latestTotalMs = rememberUpdatedState(totalMs)
    val latestContentWidthPx = rememberUpdatedState(contentWidthPx)
    val latestHandleContentStartPx = rememberUpdatedState(handleContentStartPx)
    val latestOnTrimRight = rememberUpdatedState(onTrimRight)
    val latestOnTrimCommit = rememberUpdatedState(onTrimCommit)
    val latestOnSnapGuideUpdate = rememberUpdatedState(onSnapGuideUpdate)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(clip.id + "-R") {
                var accumX = 0f
                var startMs = 0L
                var endMs = 0L
                var dragClip = clip
                var activeTarget: SnapTarget? = null
                var autoScrollDirection = 0
                var autoScrollJob: Job? = null

                fun updateAutoScroll(change: androidx.compose.ui.input.pointer.PointerInputChange) {
                    val pointerScreenX =
                        latestHandleContentStartPx.value + change.position.x - hScroll.value
                    val edgeSize = 40f
                    autoScrollDirection = when {
                        pointerScreenX < edgeSize -> -1
                        pointerScreenX > hScroll.viewportSize - edgeSize -> 1
                        else -> 0
                    }
                    if (autoScrollDirection == 0) {
                        autoScrollJob?.cancel()
                        autoScrollJob = null
                    } else if (autoScrollJob?.isActive != true) {
                        autoScrollJob = coroutineScope.launch {
                            while (isActive && autoScrollDirection != 0) {
                                val scrolled = hScroll.dispatchRawDelta(autoScrollDirection * 12f)
                                if (abs(scrolled) < 0.5f) break
                                accumX += scrolled
                                val deltaMs = (accumX / latestPxPerMs.value).toLong()
                                val requestedOffset = (dragClip.durationMs + deltaMs)
                                    .coerceAtLeast(EditorClip.MIN_DURATION_MS)
                                val expandedClip =
                                    expandClipForRightTrim(dragClip, requestedOffset)
                                val maxEnd = expandedClip.sourceEndMs
                                val timelineOffset = requestedOffset.coerceIn(
                                    EditorClip.MIN_DURATION_MS,
                                    expandedClip.durationMs
                                )
                                val trimmedEnd = SpeedEngine.sourcePositionAt(
                                    expandedClip,
                                    timelineOffset
                                ).coerceIn(startMs + EditorClip.MIN_DURATION_MS, maxEnd)
                                latestOnTrimRight.value(trimmedEnd)
                                activeTarget = null
                                latestOnSnapGuideUpdate.value(-1f, null)
                                delay(16)
                            }
                        }
                    }
                }

                detectDragGestures(
                    onDragStart = { _: Offset ->
                        accumX = 0f
                        dragClip = latestClip.value
                        startMs = dragClip.sourceStartMs
                        endMs = dragClip.sourceEndMs
                        activeTarget = null
                        autoScrollDirection = 0
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        accumX += drag.x
                        val currentClip = dragClip
                        val dMs = (accumX / latestPxPerMs.value).toLong()
                        val requestedOffset = (currentClip.durationMs + dMs)
                            .coerceAtLeast(EditorClip.MIN_DURATION_MS)
                        val expandedClip =
                            expandClipForRightTrim(currentClip, requestedOffset)
                        val maxEnd = expandedClip.sourceEndMs
                        val maxTimelineOffset = expandedClip.durationMs
                        val newEndOffset = requestedOffset.coerceIn(
                            EditorClip.MIN_DURATION_MS,
                            maxTimelineOffset
                        )
                        val newEnd = SpeedEngine.sourcePositionAt(expandedClip, newEndOffset)
                            .coerceIn(startMs + EditorClip.MIN_DURATION_MS, maxEnd)
                        val newEndTimelineMs = currentClip.timelineStartMs + newEndOffset

                        val targets = mutableListOf<SnapTarget>()
                        latestAllClips.value.filter { it.id != currentClip.id }.forEach { other ->
                            targets.add(
                                SnapTarget(
                                    other.timelineStartMs,
                                    "Start of ${other.name.take(14)}"
                                )
                            )
                            targets.add(
                                SnapTarget(
                                    other.timelineEndMs,
                                    "End of ${other.name.take(14)}"
                                )
                            )
                        }
                        targets.add(SnapTarget(latestCurrentPosMs.value, "Playhead"))

                        var bestTarget: SnapTarget? = null
                        var bestDist = enterMs
                        targets.forEach { t ->
                            val d = abs(t.timeMs - newEndTimelineMs)
                            val current = activeTarget
                            if (current != null &&
                                current.timeMs == t.timeMs &&
                                d <= releaseMs
                            ) {
                                bestTarget = t
                                bestDist = d
                                return@forEach
                            }
                            if (d < bestDist) {
                                bestDist = d
                                bestTarget = t
                            }
                        }

                        val captured = bestTarget
                        if (captured != null) {
                            activeTarget = captured
                            val targetOffset =
                                (captured.timeMs - currentClip.timelineStartMs).coerceIn(
                                    EditorClip.MIN_DURATION_MS,
                                    maxTimelineOffset
                                )
                            val finalSourceEnd =
                                SpeedEngine.sourcePositionAt(expandedClip, targetOffset)
                            latestOnTrimRight.value(
                                finalSourceEnd.coerceIn(
                                    startMs + EditorClip.MIN_DURATION_MS,
                                    maxEnd
                                )
                            )
                            val guideX = (captured.timeMs.toFloat() /
                                    latestTotalMs.value.toFloat()) * latestContentWidthPx.value
                            latestOnSnapGuideUpdate.value(guideX, "Snap: ${captured.label}")
                        } else {
                            activeTarget = null
                            latestOnTrimRight.value(newEnd)
                            latestOnSnapGuideUpdate.value(-1f, null)
                        }
                        updateAutoScroll(change)
                    },
                    onDragEnd = {
                        autoScrollJob?.cancel()
                        autoScrollJob = null
                        latestOnSnapGuideUpdate.value(-1f, null)
                        activeTarget = null
                        latestOnTrimCommit.value()
                    },
                    onDragCancel = {
                        autoScrollJob?.cancel()
                        autoScrollJob = null
                        latestOnSnapGuideUpdate.value(-1f, null)
                        activeTarget = null
                        latestOnTrimCommit.value()
                    }
                )
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .fillMaxHeight()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFFFD166))
        )
    }
}


// ═══════════════════════════════════════════════════════════════
//  TIMELINE SCROLLBAR
// ═══════════════════════════════════════════════════════════════

@Composable
private fun TimelineScrollbar(
    hScroll: ScrollState,
    contentWidthPx: Float,
    viewportWidthPx: Float,
    onUserScroll: () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val contentVisibleWidthPx = viewportWidthPx.coerceAtLeast(1f)
    val maxScroll = contentWidthPx.coerceAtLeast(0f)

    // 🆕 AGAR SCROLL KARNE KI ZAROORAT NAHI HAI (maxScroll == 0)
    if (maxScroll <= 1f) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SCROLLBAR_HEIGHT)
                .background(Color(0xFF0A0A0A))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF1A1A1A)) // Track color
            ) {
                // 🆕 Full width thumb (kyunki sab kuch fit hai)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF4F9DFF).copy(alpha = 0.5f)) // Halka sa blue
                )
            }
        }
        return
    }

    // 🆕 NORMAL SCROLLBAR (jab scroll ki zaroorat ho)
    val trackWidthDp = with(density) { contentVisibleWidthPx.toDp() }
    val thumbFraction = (contentVisibleWidthPx / (contentWidthPx + contentVisibleWidthPx))
        .coerceIn(0.05f, 1f)
    val thumbWidthDp = trackWidthDp * thumbFraction

    val scrollFraction = (hScroll.value.toFloat() / maxScroll).coerceIn(0f, 1f)
    val maxThumbOffsetDp = trackWidthDp - thumbWidthDp
    val thumbOffsetDp = maxThumbOffsetDp * scrollFraction

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SCROLLBAR_HEIGHT)
            .background(Color(0xFF0A0A0A))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF1A1A1A))
                .pointerInput(maxScroll, contentVisibleWidthPx) {
                    detectTapGestures { offset ->
                        val tapFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val targetScroll = (tapFraction * maxScroll).toInt()
                        onUserScroll()
                        scope.launch {
                            hScroll.scrollTo(targetScroll)
                        }
                    }
                }
                .pointerInput(maxScroll, contentVisibleWidthPx, thumbWidthDp) {
                    var accumulatedDrag = 0f
                    var startingScroll = 0
                    detectDragGestures(
                        onDragStart = { _ ->
                            accumulatedDrag = 0f
                            startingScroll = hScroll.value
                            onUserScroll()
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            accumulatedDrag += drag.x
                            val trackWidthPxLocal = size.width.toFloat()
                            val thumbWidthPxLocal = with(density) {
                                thumbWidthDp.toPx()
                            }
                            val maxThumbOffsetPxLocal =
                                (trackWidthPxLocal - thumbWidthPxLocal)
                                    .coerceAtLeast(1f)
                            val scrollDelta =
                                (accumulatedDrag / maxThumbOffsetPxLocal) * maxScroll
                            val newScroll = (startingScroll + scrollDelta)
                                .coerceIn(0f, maxScroll)
                            onUserScroll()
                            scope.launch {
                                hScroll.scrollTo(newScroll.toInt())
                            }
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffsetDp)
                    .width(thumbWidthDp.coerceAtLeast(20.dp))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF7C3AED))
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  AUDIO WAVEFORM BACKGROUND
// ═══════════════════════════════════════════════════════════════

@Composable
private fun AudioWaveformBackground(
    uri: Uri,
    sourceStartMs: Long,
    sourceEndMs: Long,
    sourceTotalMs: Long,
    volume: Float,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var waveform by remember(uri) { mutableStateOf<FloatArray?>(null) }

    LaunchedEffect(uri) {
        waveform = withContext(Dispatchers.IO) {
            WaveformEngine.loadWaveform(context, uri)
        }
    }

    val waveformBars = remember(waveform, sourceStartMs, sourceEndMs, sourceTotalMs, volume) {
        val samples = waveform ?: return@remember FloatArray(0)
        if (samples.isEmpty()) return@remember FloatArray(0)

        val totalMs = if (sourceTotalMs != Long.MAX_VALUE && sourceTotalMs > 0L) {
            sourceTotalMs
        } else {
            sourceEndMs.coerceAtLeast(1L)
        }
        val startIdx = ((sourceStartMs.toDouble() / totalMs) * samples.size)
            .toInt().coerceIn(0, samples.lastIndex)
        val endIdx = ((sourceEndMs.toDouble() / totalMs) * samples.size)
            .toInt().coerceIn(startIdx + 1, samples.size)
        val sampleCount = endIdx - startIdx
        val bucketCount = sampleCount.coerceAtMost(2_048)

        FloatArray(bucketCount) { bucket ->
            val bucketStart = startIdx + bucket * sampleCount / bucketCount
            val bucketEnd = startIdx + (bucket + 1) * sampleCount / bucketCount
            var peak = 0f
            for (index in bucketStart until bucketEnd) {
                peak = maxOf(peak, samples[index])
            }
            peak * volume.coerceIn(0f, 1f)
        }
    }

    Canvas(modifier = modifier) {
        if (waveformBars.isEmpty()) return@Canvas

        val w = size.width
        val h = size.height
        val midY = h / 2f
        val maxAmp = h * 0.42f

        val pxPerSample = w / waveformBars.size.toFloat()
        val waveformPath = Path()
        waveformBars.forEachIndexed { index, sample ->
            val amp = sample.coerceIn(0f, 1f)
            val barH = amp * maxAmp
            val x = (index + 0.5f) * pxPerSample
            waveformPath.moveTo(x, midY - barH)
            waveformPath.lineTo(x, midY + barH)
        }

        drawPath(
            path = waveformPath,
            color = Color(0xFFFFFFFF).copy(alpha = 0.55f),
            style = Stroke(width = pxPerSample.coerceIn(1f, 2f), cap = StrokeCap.Butt)
        )
        drawLine(
            color = Color.White.copy(alpha = 0.3f),
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1f
        )
    }
}


// ═══════════════════════════════════════════════════════════════
//  CLIP COLOR HELPER
// ═══════════════════════════════════════════════════════════════

private fun clipColor(clip: EditorClip): Color = when {
    clip.isColorMatteClip -> {
        // 🆕 Representative color based on matte mode
        val rawColor = when (clip.matteStyle.mode) {
            com.moody.moodyvideoeditor.data.ColorMatteMode.SOLID ->
                clip.matteStyle.solidColor

            com.moody.moodyvideoeditor.data.ColorMatteMode.RAMP ->
                clip.matteStyle.rampColor1

            com.moody.moodyvideoeditor.data.ColorMatteMode.FOUR_COLOR ->
                clip.matteStyle.topLeft
        }

        val r = ((rawColor shr 16) and 0xFF).toInt()
        val g = ((rawColor shr 8) and 0xFF).toInt()
        val b = (rawColor and 0xFF).toInt()
        val luminance = (0.299f * r + 0.587f * g + 0.114f * b) / 255f

        // 🆕 DEBUG — build chalao aur logcat mein dekho ye line
        android.util.Log.d(
            "MATTE_COLOR",
            "mode=${clip.matteStyle.mode} " +
                    "raw=0x${rawColor.toString(16)} " +
                    "lum=$luminance " +
                    "→ ${if (luminance < 0.35f) "YELLOW" else "COLOR"}"
        )

        // Dark → bright yellow (visible on dark timeline)
        if (luminance < 0.35f) Color(0xFFFFC107)
        else Color(r, g, b).copy(alpha = 0.9f)
    }

    clip.isSpotlightClip -> Color(0xFFFFD35A)
    clip.isVisualizerClip -> Color(0xFF16D9FF)
    clip.isAudioFxClip -> Color(0xFFA855F7)
    clip.isSoundFxClip -> Color(0xFF3B82F6)
    clip.isFilterLayerClip -> Color(0xFFEC4899)
    clip.isAudio -> Color(0xFF10B981)
    clip.isTextClip -> Color(0xFFEC4899)
    clip.isEffectClip -> Color(0xFFA855F7)
    clip.isAdjustmentClip -> Color(0xFF06B6D4)
    clip.isOverlayClip -> Color(0xFF3B82F6)

    else -> Color(0xFF2563EB)
}
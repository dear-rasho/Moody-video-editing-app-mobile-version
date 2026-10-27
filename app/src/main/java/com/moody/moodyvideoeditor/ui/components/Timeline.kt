package com.moody.moodyvideoeditor.ui.components

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt


// ═══════════════════════════════════════════════════════════════
//  CONSTANTS
// ═══════════════════════════════════════════════════════════════

private val TRACK_LABEL_WIDTH = 54.dp
private val RULER_HEIGHT = 20.dp
private val SCROLLBAR_HEIGHT = 8.dp
private val VISUAL_TRACK_HEIGHT = 36.dp
private val AUDIO_TRACK_HEIGHT = 32.dp
private const val DP_PER_SECOND = 20f

private const val SNAP_ENTER_PX = 14f
private const val SNAP_RELEASE_PX = 28f
private const val SNAP_MIN_GAP_MS = 50L

private const val LONG_PRESS_MS = 1_300L

private const val MIN_PX_PER_SEC = 4f
private const val MAX_PX_PER_SEC = 220f

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
    onToggleVisualVisibility: (Int) -> Unit = {},
    onToggleAudioMute: (Int) -> Unit = {},
    onSwapTracks: (Int, Int, Boolean) -> Unit = { _, _, _ -> },
    onTransitionDelete: (String) -> Unit = {},
    onTransitionDurationChange: (String, Long) -> Unit = { _, _ -> },
    onTimelineZoomChange: (Float) -> Unit = {},
    onReplaceRequested: (String) -> Unit = {},
    onEmptyAreaTapped: () -> Unit = {}   // 🆕 CHANGE #1: khali jagah tap
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val labelWidthPx = with(density) { TRACK_LABEL_WIDTH.toPx() }

    var viewportWidthPx by remember { mutableFloatStateOf(0f) }
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    var lastUserScrollMs by remember { mutableLongStateOf(0L) }

    var dragVisual by remember { mutableStateOf(DragVisual()) }

    var selectedTransitionClipId by remember { mutableStateOf<String?>(null) }

    var draggedLabelFrom by remember { mutableIntStateOf(-1) }
    var draggedLabelTarget by remember { mutableIntStateOf(-1) }
    var draggedLabelIsAudio by remember { mutableStateOf(false) }

    val slider = state.timelineZoom.coerceIn(
        TimelineZoom.SLIDER_MIN, TimelineZoom.SLIDER_MAX
    )
    val viewportContentWidthDp = if (viewportWidthPx > 0f) {
        with(density) {
            (viewportWidthPx - labelWidthPx).coerceAtLeast(100f).toDp().value
        }
    } else {
        360f
    }

// 🆕 Use locked timeline duration
    val totalMs = state.timelineDurationMs
    val totalSec = totalMs / 1000f

    val pps = TimelineZoom.ppsForSlider(slider, totalSec, viewportContentWidthDp)
    val contentWidthDp: Dp = (totalSec * pps).dp
    val contentWidthPx = with(density) { contentWidthDp.toPx() }

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
        if (viewportHeightPx <= 0f || viewportWidthPx <= labelWidthPx) return@LaunchedEffect

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
            val timelineViewportWidth = viewportWidthPx - labelWidthPx
            val targetContentX =
                dragVisual.targetTimeMs.coerceAtLeast(0L).toFloat() /
                        totalMs.toFloat() * contentWidthPx
            val targetViewportX = targetContentX - hScroll.value
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
                    val stepSec = TimelineRuler.pickStepSec(
                        zoom = (pps / DP_PER_SECOND).coerceAtLeast(0.01f)
                    )
                    val marks = TimelineRuler.marks(totalMs, stepSec)

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
            }
        }

        // ─── TRACKS ───
        val hasAnySelection = state.selectedClipId != null ||
                state.multiSelectedIds.isNotEmpty()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { viewportHeightPx = it.height.toFloat() }
                .pointerInput(hasAnySelection, pps) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)

                        var twoFingerActive = false
                        var initialDistance = 0f
                        var accumulatedHScroll = hScroll.value
                        var accumulatedVScroll = vScroll.value
                        var initialPps = pps

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                break
                            }

                            if (pressed.size >= 2) {
                                twoFingerActive = true

                                val c1 = pressed[0].position
                                val c2 = pressed[1].position
                                val dx = c1.x - c2.x
                                val dy = c1.y - c2.y
                                val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                                if (initialDistance == 0f) {
                                    initialDistance = distance
                                    initialPps = pps
                                } else {
                                    val ratio = distance / initialDistance
                                    val newPps = (initialPps * ratio)
                                        .coerceIn(MIN_PX_PER_SEC, MAX_PX_PER_SEC)

                                    val newSlider = ppsToSlider(
                                        newPps, totalSec, viewportContentWidthDp
                                    )

                                    onTimelineZoomChange(newSlider)
                                }

                                var sumX = 0f
                                var sumY = 0f
                                pressed.forEach { change ->
                                    val pan = change.position - change.previousPosition
                                    sumX += pan.x
                                    sumY += pan.y
                                    change.consume()
                                }
                                val avgX = sumX / pressed.size
                                val avgY = sumY / pressed.size

                                accumulatedHScroll = (accumulatedHScroll - avgX.toInt())
                                    .coerceIn(0, hScroll.maxValue)
                                accumulatedVScroll = (accumulatedVScroll - avgY.toInt())
                                    .coerceIn(0, vScroll.maxValue)

                                coroutineScope.launch {
                                    hScroll.scrollTo(accumulatedHScroll)
                                    vScroll.scrollTo(accumulatedVScroll)
                                }
                            } else if (twoFingerActive) {
                                pressed.forEach { it.consume() }
                            } else {
                                firstDown.consume()
                            }
                        }
                    }
                }
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
                            trackIndex = topVisualIndex + 1,
                            isAudio = false,
                            contentWidthDp = contentWidthDp,
                            hScroll = hScroll
                        )
                    }

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
                        onDragStart = onDragStart,
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
                        isHidden = state.hiddenVisualTracks.contains(i),
                        onToggleVisibility = onToggleVisualVisibility,
                        isLabelDragging = draggedLabelFrom == i && !draggedLabelIsAudio,
                        isLabelTarget = draggedLabelTarget == i &&
                                !draggedLabelIsAudio &&
                                draggedLabelFrom != i,
                        onLabelDragStart = { from ->
                            draggedLabelFrom = from
                            draggedLabelTarget = from
                            draggedLabelIsAudio = false
                        },
                        onLabelDragUpdate = { target -> draggedLabelTarget = target },
                        onLabelDragEnd = {
                            if (draggedLabelFrom >= 0 &&
                                draggedLabelTarget >= 0 &&
                                draggedLabelFrom != draggedLabelTarget
                            ) {
                                onSwapTracks(
                                    draggedLabelFrom,
                                    draggedLabelTarget,
                                    false
                                )
                            }
                            draggedLabelFrom = -1
                            draggedLabelTarget = -1
                        },
                        onLabelDragCancel = {
                            draggedLabelFrom = -1
                            draggedLabelTarget = -1
                        },
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
                            trackIndex = state.audioLayerCount,
                            isAudio = true,
                            contentWidthDp = contentWidthDp,
                            hScroll = hScroll
                        )
                    }

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
                        onDragStart = onDragStart,
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
                        isMuted = state.mutedAudioTracks.contains(i),
                        onToggleMute = onToggleAudioMute,
                        isLabelDragging = draggedLabelFrom == i && draggedLabelIsAudio,
                        isLabelTarget = draggedLabelTarget == i &&
                                draggedLabelIsAudio &&
                                draggedLabelFrom != i,
                        onLabelDragStart = { from ->
                            draggedLabelFrom = from
                            draggedLabelTarget = from
                            draggedLabelIsAudio = true
                        },
                        onLabelDragUpdate = { target -> draggedLabelTarget = target },
                        onLabelDragEnd = {
                            if (draggedLabelFrom >= 0 &&
                                draggedLabelTarget >= 0 &&
                                draggedLabelFrom != draggedLabelTarget
                            ) {
                                onSwapTracks(
                                    draggedLabelFrom,
                                    draggedLabelTarget,
                                    true
                                )
                            }
                            draggedLabelFrom = -1
                            draggedLabelTarget = -1
                        },
                        onLabelDragCancel = {
                            draggedLabelFrom = -1
                            draggedLabelTarget = -1
                        },
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

            TimelinePlayhead(
                currentPosMs = state.currentPosMs,
                totalMs = totalMs,
                contentWidthPx = contentWidthPx,
                labelWidthPx = labelWidthPx,
                hScrollValue = hScroll.value,
                viewportWidthPx = viewportWidthPx
            )
        }

        TimelineScrollbar(
            hScroll = hScroll,
            contentWidthPx = contentWidthPx,
            viewportWidthPx = viewportWidthPx,
            labelWidthPx = labelWidthPx
        )
    }
}


// ═══════════════════════════════════════════════════════════════
//  PINCH-ZOOM MATH
// ═══════════════════════════════════════════════════════════════

private fun ppsToSlider(
    pps: Float,
    totalSec: Float,
    viewportContentWidthDp: Float
): Float {
    if (totalSec <= 0f || viewportContentWidthDp <= 0f) return 0f
    val fitPps = viewportContentWidthDp / totalSec
    val maxPps = 100f
    if (maxPps <= fitPps) return 0f

    val minLog = kotlin.math.ln(fitPps.toDouble())
    val maxLog = kotlin.math.ln(maxPps.toDouble())
    val targetLog = kotlin.math.ln(pps.coerceAtLeast(fitPps).toDouble())
    val fraction = (targetLog - minLog) / (maxLog - minLog)
    return (fraction * 100f).toFloat().coerceIn(0f, 100f)
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
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDragVisualUpdate: (DragVisual) -> Unit,
    dragVisual: DragVisual,
    visualLayerCount: Int,
    audioLayerCount: Int,
    isHidden: Boolean,
    onToggleVisibility: (Int) -> Unit,
    isLabelDragging: Boolean,
    isLabelTarget: Boolean,
    onLabelDragStart: (Int) -> Unit,
    onLabelDragUpdate: (Int) -> Unit,
    onLabelDragEnd: () -> Unit,
    onLabelDragCancel: () -> Unit,
    onTransitionDelete: (String) -> Unit,
    onTransitionDurationChange: (String, Long) -> Unit,
    selectedTransitionClipId: String?,
    onTransitionTapped: (String) -> Unit,
    onReplaceRequested: (String) -> Unit,
    onEmptyAreaTapped: () -> Unit,
    haptic: HapticFeedback
) {
    val density = LocalDensity.current
    val isSelectedLayer = selectedTrackIndex == trackIndex && !selectedIsAudio
    val isDragTarget = dragVisual.active && !dragVisual.isAudio &&
            dragVisual.targetTrack == trackIndex

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(VISUAL_TRACK_HEIGHT)
            .then(
                if (isDragTarget && !dragVisual.needsNewLayer) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = Color(0xFF22C55E).copy(alpha = 0.8f)
                    )
                } else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .width(TRACK_LABEL_WIDTH)
                .fillMaxHeight()
                .padding(horizontal = 2.dp)
                .background(
                    when {
                        isLabelDragging -> Color(0xFF4F9DFF).copy(alpha = 0.3f)
                        isLabelTarget -> Color(0xFF22C55E).copy(alpha = 0.3f)
                        else -> Color.Transparent
                    }
                )
                .pointerInput(trackIndex, visualLayerCount, isSelectedLayer) {
                    if (!isSelectedLayer) return@pointerInput

                    var accumulatedY = 0f
                    val stepPxLocal = with(density) { VISUAL_TRACK_HEIGHT.toPx() }
                    detectVerticalDragGestures(
                        onDragStart = {
                            accumulatedY = 0f
                            onLabelDragStart(trackIndex)
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedY += dragAmount
                            val steps = (accumulatedY / stepPxLocal).roundToInt()
                            val target = (trackIndex - steps)
                                .coerceIn(0, visualLayerCount - 1)
                            onLabelDragUpdate(target)
                        },
                        onDragEnd = { onLabelDragEnd() },
                        onDragCancel = { onLabelDragCancel() }
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                "V${trackIndex + 1}",
                color = when {
                    isLabelDragging -> Color.White
                    isLabelTarget -> Color.White
                    isHidden -> Color(0xFF555555)
                    isSelectedLayer -> Color(0xFF7C3AED)
                    else -> Color(0xFF888888)
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(end = 2.dp)
                    .pointerInput(trackIndex) {
                        detectTapGestures { onTrackTapped(trackIndex, false) }
                    }
            )
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .pointerInput(trackIndex) {
                        detectTapGestures { onToggleVisibility(trackIndex) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isHidden) "🚫" else "👁",
                    fontSize = 10.sp,
                    color = if (isHidden) Color(0xFF555555) else Color(0xFFAAAAAA)
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF141414))
                .horizontalScroll(hScroll)
        ) {
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
                onTransitionDelete = onTransitionDelete,
                onTransitionDurationChange = onTransitionDurationChange,
                selectedTransitionClipId = selectedTransitionClipId,
                onTransitionTapped = onTransitionTapped,
                visualLayerCount = visualLayerCount,
                audioLayerCount = audioLayerCount,
                trackHidden = isHidden,
                haptic = haptic,
                onReplaceRequested = onReplaceRequested,
                onEmptyAreaTapped = onEmptyAreaTapped
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
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDragVisualUpdate: (DragVisual) -> Unit,
    dragVisual: DragVisual,
    visualLayerCount: Int,
    audioLayerCount: Int,
    isMuted: Boolean,
    onToggleMute: (Int) -> Unit,
    isLabelDragging: Boolean,
    isLabelTarget: Boolean,
    onLabelDragStart: (Int) -> Unit,
    onLabelDragUpdate: (Int) -> Unit,
    onLabelDragEnd: () -> Unit,
    onLabelDragCancel: () -> Unit,
    onTransitionDelete: (String) -> Unit,
    onTransitionDurationChange: (String, Long) -> Unit,
    selectedTransitionClipId: String?,
    onTransitionTapped: (String) -> Unit,
    onReplaceRequested: (String) -> Unit,
    onEmptyAreaTapped: () -> Unit,
    haptic: HapticFeedback
) {
    val density = LocalDensity.current
    val isSelectedLayer = selectedTrackIndex == trackIndex && selectedIsAudio
    val isDragTarget = dragVisual.active && dragVisual.isAudio &&
            dragVisual.targetTrack == trackIndex

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AUDIO_TRACK_HEIGHT)
            .then(
                if (isDragTarget && !dragVisual.needsNewLayer) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = Color(0xFF22C55E).copy(alpha = 0.8f)
                    )
                } else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .width(TRACK_LABEL_WIDTH)
                .fillMaxHeight()
                .padding(horizontal = 2.dp)
                .background(
                    when {
                        isLabelDragging -> Color(0xFF4F9DFF).copy(alpha = 0.3f)
                        isLabelTarget -> Color(0xFF22C55E).copy(alpha = 0.3f)
                        else -> Color.Transparent
                    }
                )
                .pointerInput(trackIndex, audioLayerCount, isSelectedLayer) {
                    if (!isSelectedLayer) return@pointerInput

                    var accumulatedY = 0f
                    val stepPxLocal = with(density) { AUDIO_TRACK_HEIGHT.toPx() }
                    detectVerticalDragGestures(
                        onDragStart = {
                            accumulatedY = 0f
                            onLabelDragStart(trackIndex)
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedY += dragAmount
                            val steps = (accumulatedY / stepPxLocal).roundToInt()
                            val target = (trackIndex + steps)
                                .coerceIn(0, audioLayerCount - 1)
                            onLabelDragUpdate(target)
                        },
                        onDragEnd = { onLabelDragEnd() },
                        onDragCancel = { onLabelDragCancel() }
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                "A${trackIndex + 1}",
                color = when {
                    isLabelDragging -> Color.White
                    isLabelTarget -> Color.White
                    isMuted -> Color(0xFF555555)
                    isSelectedLayer -> Color(0xFF10B981)
                    else -> Color(0xFF888888)
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(end = 2.dp)
                    .pointerInput(trackIndex) {
                        detectTapGestures { onTrackTapped(trackIndex, true) }
                    }
            )
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .pointerInput(trackIndex) {
                        detectTapGestures { onToggleMute(trackIndex) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isMuted) "🔇" else "🔊",
                    fontSize = 10.sp,
                    color = if (isMuted) Color(0xFF555555) else Color(0xFFAAAAAA)
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF141414))
                .horizontalScroll(hScroll)
        ) {
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
                onTransitionDelete = onTransitionDelete,
                onTransitionDurationChange = onTransitionDurationChange,
                selectedTransitionClipId = selectedTransitionClipId,
                onTransitionTapped = onTransitionTapped,
                visualLayerCount = visualLayerCount,
                audioLayerCount = audioLayerCount,
                trackHidden = isMuted,
                haptic = haptic,
                onReplaceRequested = onReplaceRequested,
                onEmptyAreaTapped = onEmptyAreaTapped
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════
//  GHOST TRACK DROP ZONE
// ═══════════════════════════════════════════════════════════════

@Composable
private fun GhostTrackDrop(
    trackIndex: Int,
    isAudio: Boolean,
    contentWidthDp: Dp,
    hScroll: ScrollState
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(GHOST_TRACK_HEIGHT)
            .background(Color(0xFF0F1A0F))
            .border(
                width = 1.dp,
                color = Color(0xFF22C55E).copy(alpha = 0.5f)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(TRACK_LABEL_WIDTH)
                .fillMaxHeight()
                .background(Color(0xFF0F1A0F)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isAudio) "A${trackIndex + 1} +" else "V${trackIndex + 1} +",
                color = Color(0xFF22C55E),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF0F1A0F))
                .horizontalScroll(hScroll)
        ) {
            Box(
                modifier = Modifier
                    .width(contentWidthDp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "⬇ Drop here to create new layer",
                    color = Color(0xFF22C55E).copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
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
    currentPosMs: Long,
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
    onTransitionDelete: (String) -> Unit,
    onTransitionDurationChange: (String, Long) -> Unit,
    selectedTransitionClipId: String?,
    onTransitionTapped: (String) -> Unit,
    visualLayerCount: Int,
    audioLayerCount: Int,
    trackHidden: Boolean,
    haptic: HapticFeedback,
    onReplaceRequested: (String) -> Unit = {},
    onEmptyAreaTapped: () -> Unit = {}
) {
    val density = LocalDensity.current

    var snapGuideX by remember { mutableFloatStateOf(-1f) }
    var snapLabel by remember { mutableStateOf<String?>(null) }

    val trackAlpha = if (trackHidden) 0.3f else 1f

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
                        // 🆕 CHANGE #1: Khali jagah tap → unselect all
                        onEmptyAreaTapped()
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

        clips.forEach { clip ->
            ClipCard(
                clip = clip,
                allClips = allClips,
                totalMs = totalMs,
                contentWidthPx = contentWidthPx,
                pxPerMs = pxPerMs,
                stepPx = stepPx,
                currentPosMs = currentPosMs,
                isSelected = clip.id == selectedClipId,
                isMulti = multiSelectedIds.contains(clip.id),
                isDragging = dragVisual.active && dragVisual.clipId == clip.id,
                isAudio = isAudio,
                trackAlpha = trackAlpha,
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
    currentPosMs: Long,
    isSelected: Boolean,
    isMulti: Boolean,
    isDragging: Boolean,
    isAudio: Boolean,
    trackAlpha: Float,
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
    onSnapGuideUpdate: (Float, String?) -> Unit,
    onReplaceRequested: (String) -> Unit = {},
    haptic: HapticFeedback
) {
    val density = LocalDensity.current
    val latestClipState = rememberUpdatedState(clip)
    val latestAllClipsState = rememberUpdatedState(allClips)
    val latestCurrentPosMs = rememberUpdatedState(currentPosMs)


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
            .graphicsLayer { alpha = if (isDragging || clipLifted) 0.85f else trackAlpha }
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
                    if (down.position.x <= trimHandleHitWidthPx ||
                        down.position.x >= size.width - trimHandleHitWidthPx
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
    currentPosMs: Long,
    allClips: List<EditorClip>,
    pxPerMs: Float,
    enterMs: Long,
    releaseMs: Long,
    onTrimLeft: (Long) -> Unit,
    onTrimCommit: () -> Unit,
    onSnapGuideUpdate: (Float, String?) -> Unit
) {
    val latestClip = rememberUpdatedState(clip)
    val latestAllClips = rememberUpdatedState(allClips)
    val latestCurrentPosMs = rememberUpdatedState(currentPosMs)
    val latestPxPerMs = rememberUpdatedState(pxPerMs)
    val latestTotalMs = rememberUpdatedState(totalMs)
    val latestContentWidthPx = rememberUpdatedState(contentWidthPx)
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
                detectDragGestures(
                    onDragStart = { _: Offset ->
                        accumX = 0f
                        dragClip = latestClip.value
                        startMs = dragClip.sourceStartMs
                        endMs = dragClip.sourceEndMs
                        activeTarget = null
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        accumX += drag.x
                        val currentClip = dragClip
                        val speed = currentClip.speed.coerceAtLeast(0.01f)
                        val dMs = (accumX / latestPxPerMs.value).toLong()
                        val newStart = (startMs + (dMs * speed).toLong())
                            .coerceIn(0L, endMs - EditorClip.MIN_DURATION_MS)

                        val newStartTimelineMs = currentClip.timelineStartMs +
                                ((newStart - currentClip.sourceStartMs) / speed).toLong()

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
                            val deltaMs = captured.timeMs - currentClip.timelineStartMs
                            val finalSourceStart =
                                currentClip.sourceStartMs + (deltaMs * speed).toLong()
                            latestOnTrimLeft.value(finalSourceStart.coerceAtLeast(0L))
                            val guideX = (captured.timeMs.toFloat() /
                                    latestTotalMs.value.toFloat()) * latestContentWidthPx.value
                            latestOnSnapGuideUpdate.value(guideX, "Snap: ${captured.label}")
                        } else {
                            activeTarget = null
                            latestOnTrimLeft.value(newStart)
                            latestOnSnapGuideUpdate.value(-1f, null)
                        }
                    },
                    onDragEnd = {
                        latestOnSnapGuideUpdate.value(-1f, null)
                        activeTarget = null
                        latestOnTrimCommit.value()
                    },
                    onDragCancel = {
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

@Composable
private fun TrimHandleRight(
    clip: EditorClip,
    totalMs: Long,
    contentWidthPx: Float,
    currentPosMs: Long,
    allClips: List<EditorClip>,
    pxPerMs: Float,
    enterMs: Long,
    releaseMs: Long,
    onTrimRight: (Long) -> Unit,
    onTrimCommit: () -> Unit,
    onSnapGuideUpdate: (Float, String?) -> Unit
) {
    val latestClip = rememberUpdatedState(clip)
    val latestAllClips = rememberUpdatedState(allClips)
    val latestCurrentPosMs = rememberUpdatedState(currentPosMs)
    val latestPxPerMs = rememberUpdatedState(pxPerMs)
    val latestTotalMs = rememberUpdatedState(totalMs)
    val latestContentWidthPx = rememberUpdatedState(contentWidthPx)
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

                detectDragGestures(
                    onDragStart = { _: Offset ->
                        accumX = 0f
                        dragClip = latestClip.value
                        startMs = dragClip.sourceStartMs
                        endMs = dragClip.sourceEndMs
                        activeTarget = null
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        accumX += drag.x
                        val currentClip = dragClip
                        val speed = currentClip.speed.coerceAtLeast(0.01f)
                        val dMs = (accumX / latestPxPerMs.value).toLong()
                        val maxEnd = if (currentClip.sourceTotalMs != Long.MAX_VALUE)
                            currentClip.sourceTotalMs else Long.MAX_VALUE
                        val newEnd = (endMs + (dMs * speed).toLong())
                            .coerceIn(
                                startMs + EditorClip.MIN_DURATION_MS,
                                maxEnd
                            )

                        val newEndTimelineMs = currentClip.timelineStartMs +
                                ((newEnd - currentClip.sourceStartMs) / speed).toLong()

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
                            val deltaMs = captured.timeMs - currentClip.timelineStartMs
                            val finalSourceEnd =
                                currentClip.sourceStartMs + (deltaMs * speed).toLong()
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
                    },
                    onDragEnd = {
                        latestOnSnapGuideUpdate.value(-1f, null)
                        activeTarget = null
                        latestOnTrimCommit.value()
                    },
                    onDragCancel = {
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
    labelWidthPx: Float
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val contentVisibleWidthPx = (viewportWidthPx - labelWidthPx).coerceAtLeast(1f)
    val maxScroll = (contentWidthPx - contentVisibleWidthPx).coerceAtLeast(0f)

    // 🆕 AGAR SCROLL KARNE KI ZAROORAT NAHI HAI (maxScroll == 0)
    if (maxScroll <= 1f) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SCROLLBAR_HEIGHT)
                .background(Color(0xFF0A0A0A))
        ) {
            Box(modifier = Modifier.width(TRACK_LABEL_WIDTH).fillMaxHeight())
            Box(
                modifier = Modifier
                    .weight(1f)
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
    val thumbFraction = (contentVisibleWidthPx / contentWidthPx)
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
                .width(TRACK_LABEL_WIDTH)
                .fillMaxHeight()
                .background(Color(0xFF0A0A0A))
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF1A1A1A))
                .pointerInput(maxScroll, contentVisibleWidthPx) {
                    detectTapGestures { offset ->
                        val tapFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val targetScroll = (tapFraction * maxScroll).toInt()
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
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var waveform by remember(uri) { mutableStateOf<FloatArray?>(null) }

    LaunchedEffect(uri) {
        waveform = withContext(Dispatchers.IO) {
            WaveformEngine.loadWaveform(context, uri)
        }
    }

    Canvas(modifier = modifier) {
        val wf = waveform ?: return@Canvas
        if (wf.isEmpty()) return@Canvas

        val w = size.width
        val h = size.height
        val midY = h / 2f
        val maxAmp = h * 0.42f

        val totalMs = if (sourceTotalMs != Long.MAX_VALUE && sourceTotalMs > 0L)
            sourceTotalMs
        else (sourceEndMs).coerceAtLeast(1L)

        val startFrac = (sourceStartMs.toFloat() / totalMs.toFloat())
            .coerceIn(0f, 1f)
        val endFrac = (sourceEndMs.toFloat() / totalMs.toFloat())
            .coerceIn(0f, 1f)

        val startIdx = (startFrac * wf.size).toInt().coerceIn(0, wf.size - 1)
        val endIdx = (endFrac * wf.size).toInt().coerceIn(startIdx + 1, wf.size)

        val samplesToDraw = (endIdx - startIdx).coerceAtLeast(1)
        val pxPerSample = w / samplesToDraw.toFloat()

        var x = 0f
        for (i in startIdx until endIdx) {
            val amp = wf[i].coerceIn(0f, 1f)
            val barH = amp * maxAmp

            drawLine(
                color = Color(0xFFFFFFFF).copy(alpha = 0.55f),
                start = Offset(x, midY - barH),
                end = Offset(x, midY),
                strokeWidth = pxPerSample.coerceIn(1f, 2f),
                cap = StrokeCap.Butt
            )
            drawLine(
                color = Color(0xFFFFFFFF).copy(alpha = 0.55f),
                start = Offset(x, midY),
                end = Offset(x, midY + barH),
                strokeWidth = pxPerSample.coerceIn(1f, 2f),
                cap = StrokeCap.Butt
            )

            x += pxPerSample
        }

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

    clip.isVisualizerClip -> Color(0xFFFFD166)
    clip.isSpotlightClip -> Color(0xFFFFD35A)
    clip.isAudioFxClip -> Color(0xFFA855F7)
    clip.isSoundFxClip -> Color(0xFF3B82F6)
    clip.isFilterLayerClip -> Color(0xFFEC4899)
    clip.isAudio -> Color(0xFF10B981)
    clip.isTextClip -> Color(0xFFEC4899)
    clip.isStickerClip -> Color(0xFFF59E0B)
    clip.isEffectClip -> Color(0xFFA855F7)
    clip.isAdjustmentClip -> Color(0xFF06B6D4)
    clip.isOverlayClip -> Color(0xFF3B82F6)

    else -> Color(0xFF2563EB)
}
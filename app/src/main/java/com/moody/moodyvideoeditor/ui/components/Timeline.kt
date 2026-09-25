package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.moody.moodyvideoeditor.utils.TimelineRuler
import com.moody.moodyvideoeditor.utils.TimelineZoom
import kotlin.math.abs
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════
//  CONSTANTS
// ═══════════════════════════════════════════════════════════════
private val TRACK_LABEL_WIDTH = 54.dp
private val RULER_HEIGHT = 22.dp
private val VISUAL_TRACK_HEIGHT = 34.dp
private val AUDIO_TRACK_HEIGHT = 28.dp
private const val DP_PER_SECOND = 20f

private const val SNAP_ENTER_PX = 14f
private const val SNAP_RELEASE_PX = 28f
private const val SNAP_MIN_GAP_MS = 50L

private data class SnapTarget(val timeMs: Long, val label: String)

private data class DragVisual(
    val active: Boolean = false,
    val clipId: String? = null,
    val isAudio: Boolean = false,
    val targetTrack: Int = -1,
    val needsNewLayer: Boolean = false,
    val sourceTrack: Int = -1
)

// ═══════════════════════════════════════════════════════════════
//  MAIN TIMELINE
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
    onTransitionDurationChange: (String, Long) -> Unit = { _, _ -> }
) {
    val density = LocalDensity.current
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    val labelWidthPx = with(density) { TRACK_LABEL_WIDTH.toPx() }

    var viewportWidthPx by remember { mutableFloatStateOf(0f) }
    var lastUserScrollMs by remember { mutableLongStateOf(0L) }

    var dragVisual by remember { mutableStateOf(DragVisual()) }

    // 🆕 Transition selection
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

    val actualMs = state.totalDurationMs
    val playheadBuffer = state.currentPosMs + 10_000L
    val hasClips = state.clips.isNotEmpty()

    val totalMs = if (hasClips) {
        maxOf(actualMs, playheadBuffer, 1000L)
    } else {
        TimelineZoom.MIN_TIMELINE_MS
    }
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

        // ═══ TRACKS + PLAYHEAD ═══
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(vScroll)
            ) {
                val topVisualIndex = state.visualLayerCount - 1

                for (i in topVisualIndex downTo 0) {
                    if (dragVisual.active && !dragVisual.isAudio &&
                        dragVisual.targetTrack == i &&
                        dragVisual.needsNewLayer && i == topVisualIndex
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color(0xFF22C55E))
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
                        }
                    )

                    if (dragVisual.active && !dragVisual.isAudio &&
                        dragVisual.targetTrack == i - 1 &&
                        dragVisual.needsNewLayer && i > 0
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color(0xFF22C55E).copy(alpha = 0.8f))
                        )
                    }
                }

                // Audio tracks
                for (i in 0 until state.audioLayerCount) {
                    if (dragVisual.active && dragVisual.isAudio &&
                        dragVisual.targetTrack == i &&
                        dragVisual.needsNewLayer && i == state.audioLayerCount - 1
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color(0xFF22C55E))
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
                        }
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
    isLabelDragging: Boolean = false,
    isLabelTarget: Boolean = false,
    onLabelDragStart: (Int) -> Unit = {},
    onLabelDragUpdate: (Int) -> Unit = {},
    onLabelDragEnd: () -> Unit = {},
    onLabelDragCancel: () -> Unit = {},
    onTransitionDelete: (String) -> Unit = {},
    onTransitionDurationChange: (String, Long) -> Unit = { _, _ -> },
    selectedTransitionClipId: String? = null,
    onTransitionTapped: (String) -> Unit = {}
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
                        width = 1.dp,
                        color = Color(0xFF22C55E).copy(alpha = 0.7f)
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
                .pointerInput(trackIndex, visualLayerCount) {
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
                fontSize = 9.sp,
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
                trackHidden = isHidden
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
    isLabelDragging: Boolean = false,
    isLabelTarget: Boolean = false,
    onLabelDragStart: (Int) -> Unit = {},
    onLabelDragUpdate: (Int) -> Unit = {},
    onLabelDragEnd: () -> Unit = {},
    onLabelDragCancel: () -> Unit = {},
    onTransitionDelete: (String) -> Unit = {},
    onTransitionDurationChange: (String, Long) -> Unit = { _, _ -> },
    selectedTransitionClipId: String? = null,
    onTransitionTapped: (String) -> Unit = {}
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
                        width = 1.dp,
                        color = Color(0xFF22C55E).copy(alpha = 0.7f)
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
                .pointerInput(trackIndex, audioLayerCount) {
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
                fontSize = 9.sp,
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
                trackHidden = isMuted
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
    onTransitionDelete: (String) -> Unit = {},
    onTransitionDurationChange: (String, Long) -> Unit = { _, _ -> },
    selectedTransitionClipId: String? = null,
    onTransitionTapped: (String) -> Unit = {},
    visualLayerCount: Int,
    audioLayerCount: Int,
    trackHidden: Boolean = false
) {
    val density = LocalDensity.current

    var snapGuideX by remember { mutableFloatStateOf(-1f) }
    var snapLabel by remember { mutableStateOf<String?>(null) }

    var draggingClipId by remember { mutableStateOf<String?>(null) }
    var dragDeltaX by remember { mutableFloatStateOf(0f) }
    var dragDeltaY by remember { mutableFloatStateOf(0f) }
    var dragOriginalStartMs by remember { mutableLongStateOf(0L) }
    var dragTargetTrack by remember { mutableIntStateOf(0) }
    var dragTargetTimeMs by remember { mutableLongStateOf(0L) }

    val trackAlpha = if (trackHidden) 0.3f else 1f

    Box(
        modifier = Modifier
            .width(contentWidthDp)
            .fillMaxHeight()
            .pointerInput(clips, totalMs, contentWidthPx) {
                detectTapGestures { offset ->
                    if (contentWidthPx <= 0f || totalMs <= 0L) return@detectTapGestures
                    onSeek(
                        TimelinePlayheadController.seekTimeFromClick(
                            offset.x, contentWidthPx, totalMs
                        )
                    )
                }
            }
    ) {
        if (contentWidthPx <= 0f || totalMs <= 0L) return@Box

        val pxPerMs = contentWidthPx / totalMs.toFloat()
        val stepPx = with(density) { VISUAL_TRACK_HEIGHT.toPx() }
        val handleWidthPx = with(density) { 24.dp.toPx() }

        val enterMs = (SNAP_ENTER_PX / pxPerMs).toLong().coerceAtLeast(SNAP_MIN_GAP_MS)
        val releaseMs = (SNAP_RELEASE_PX / pxPerMs).toLong().coerceAtLeast(enterMs * 2)

        // Ghost
        draggingClipId?.let { dragId ->
            val ghost = clips.firstOrNull { it.id == dragId }
            if (ghost != null) {
                val ghostStartPx = dragOriginalStartMs.toFloat() / totalMs * contentWidthPx
                val ghostEndMs = dragOriginalStartMs + ghost.durationMs
                val ghostEndPx = ghostEndMs.toFloat() / totalMs * contentWidthPx
                val ghostWidthPx = (ghostEndPx - ghostStartPx).coerceAtLeast(20f)
                val ghostColor = when {
                    ghost.isAudio -> Color(0xFF10B981)
                    ghost.isTextClip -> Color(0xFFEC4899)
                    ghost.isStickerClip -> Color(0xFFF59E0B)
                    ghost.isEffectClip -> Color(0xFFA855F7)
                    ghost.isAdjustmentClip -> Color(0xFF06B6D4)
                    ghost.isOverlayClip -> Color(0xFF3B82F6)
                    ghost.isChromaClip -> Color(0xFF22C55E)
                    else -> Color(0xFF2563EB)
                }
                Box(
                    modifier = Modifier
                        .offset(x = with(density) { ghostStartPx.toDp() })
                        .width(with(density) { ghostWidthPx.toDp() })
                        .fillMaxHeight()
                        .padding(vertical = 2.dp)
                        .zIndex(2f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ghostColor.copy(alpha = 0.12f))
                        .border(
                            width = 1.dp,
                            color = ghostColor.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(4.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↺",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        clips.forEach { clip ->
            val isSelected = clip.id == selectedClipId
            val isMulti = multiSelectedIds.contains(clip.id)
            val isDragging = clip.id == draggingClipId

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
                    .zIndex(
                        if (isDragging) 30f
                        else if (isSelected) 10f
                        else if (isMulti) 9f
                        else 1f
                    )
                    .graphicsLayer {
                        if (isDragging) {
                            translationX = dragDeltaX
                            translationY = dragDeltaY
                            alpha = 0.92f
                            scaleX = 1.03f
                            scaleY = 1.03f
                        }
                    }
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            isMulti -> Color(0xFF4F9DFF)
                            else -> barColor
                        }
                    )
                    .then(
                        if (isSelected && !isDragging) {
                            Modifier.border(
                                width = 2.dp,
                                color = Color.White,
                                shape = RoundedCornerShape(4.dp)
                            )
                        } else if (isDragging) {
                            Modifier.border(
                                width = 2.dp,
                                color = Color(0xFF60EFFF),
                                shape = RoundedCornerShape(4.dp)
                            )
                        } else Modifier
                    )
                    .graphicsLayer { alpha = trackAlpha }
                    .pointerInput(clip.id, contentWidthPx, totalMs) {
                        var accumX = 0f
                        var accumY = 0f
                        var startTimeMs = 0L
                        var startTrack = 0

                        detectDragGestures(
                            onDragStart = {
                                accumX = 0f
                                accumY = 0f
                                startTimeMs = clip.timelineStartMs
                                startTrack = clip.trackIndex

                                draggingClipId = clip.id
                                dragDeltaX = 0f
                                dragDeltaY = 0f
                                dragOriginalStartMs = clip.timelineStartMs
                                dragTargetTrack = clip.trackIndex
                                dragTargetTimeMs = clip.timelineStartMs
                                onDragStart()
                            },
                            onDrag = { change, drag ->
                                change.consume()

                                accumX += drag.x
                                accumY += drag.y
                                dragDeltaX = accumX
                                dragDeltaY = accumY

                                val virtualStartMs = (startTimeMs +
                                        (accumX / pxPerMs).toLong())
                                    .coerceAtLeast(0L)
                                val virtualEndMs = virtualStartMs + clip.durationMs

                                val targets = mutableListOf<SnapTarget>()
                                allClips.filter { it.id != clip.id }.forEach { other ->
                                    if (other.isAudio != clip.isAudio) return@forEach
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
                                targets.add(SnapTarget(currentPosMs, "Playhead"))

                                var bestTarget: SnapTarget? = null
                                var bestDist = enterMs
                                var snapAtEnd = false
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

                                val finalTimeMs: Long
                                if (bestTarget != null) {
                                    finalTimeMs = if (snapAtEnd) {
                                        (bestTarget!!.timeMs - clip.durationMs)
                                            .coerceAtLeast(0L)
                                    } else {
                                        bestTarget!!.timeMs
                                    }
                                    snapGuideX = (bestTarget!!.timeMs.toFloat() /
                                            totalMs.toFloat()) * contentWidthPx
                                    snapLabel = "Snap: ${bestTarget!!.label}"
                                } else {
                                    finalTimeMs = virtualStartMs
                                    snapGuideX = -1f
                                    snapLabel = null
                                }
                                dragTargetTimeMs = finalTimeMs

                                val steps = (accumY / stepPx).roundToInt()
                                val newTrack = if (clip.isAudio) {
                                    (startTrack + steps).coerceAtLeast(0)
                                } else {
                                    (startTrack - steps).coerceAtLeast(0)
                                }
                                dragTargetTrack = newTrack

                                val maxTrack = if (clip.isAudio) audioLayerCount - 1
                                else visualLayerCount - 1
                                val needsNew = newTrack > maxTrack

                                onDragVisualUpdate(
                                    DragVisual(
                                        active = true,
                                        clipId = clip.id,
                                        isAudio = clip.isAudio,
                                        targetTrack = newTrack.coerceAtMost(maxTrack),
                                        needsNewLayer = needsNew,
                                        sourceTrack = startTrack
                                    )
                                )
                            },
                            onDragEnd = {
                                if (dragTargetTrack != clip.trackIndex ||
                                    dragTargetTimeMs != clip.timelineStartMs
                                ) {
                                    onMoveClip(
                                        clip.id,
                                        dragTargetTrack,
                                        clip.isAudio,
                                        dragTargetTimeMs
                                    )
                                }
                                draggingClipId = null
                                dragDeltaX = 0f
                                dragDeltaY = 0f
                                snapGuideX = -1f
                                snapLabel = null
                                onDragEnd()
                            },
                            onDragCancel = {
                                draggingClipId = null
                                dragDeltaX = 0f
                                dragDeltaY = 0f
                                snapGuideX = -1f
                                snapLabel = null
                                onDragCancel()
                            }
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

                if (isSelected && !isDragging) {
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
                                var activeTarget: SnapTarget? = null

                                detectDragGestures(
                                    onDragStart = {
                                        accumX = 0f
                                        startMs = clip.sourceStartMs
                                        endMs = clip.sourceEndMs
                                        activeTarget = null
                                    },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        accumX += drag.x
                                        val dMs = (accumX / pxPerMs).toLong()
                                        val newStart = (startMs + dMs)
                                            .coerceIn(0L, endMs - EditorClip.MIN_DURATION_MS)

                                        val newStartTimelineMs =
                                            clip.timelineStartMs + (newStart - clip.sourceStartMs)

                                        val targets = mutableListOf<SnapTarget>()
                                        allClips.filter { it.id != clip.id }.forEach { other ->
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
                                        targets.add(SnapTarget(currentPosMs, "Playhead"))

                                        var bestTarget: SnapTarget? = null
                                        var bestDist = enterMs
                                        targets.forEach { t ->
                                            val d = abs(t.timeMs - newStartTimelineMs)
                                            if (activeTarget != null &&
                                                activeTarget!!.timeMs == t.timeMs &&
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

                                        if (bestTarget != null) {
                                            activeTarget = bestTarget
                                            val deltaMs = bestTarget!!.timeMs - clip.timelineStartMs
                                            val finalSourceStart = clip.sourceStartMs + deltaMs
                                            onTrimLeft(finalSourceStart.coerceAtLeast(0L))
                                            snapGuideX = (bestTarget!!.timeMs.toFloat() /
                                                    totalMs.toFloat()) * contentWidthPx
                                            snapLabel = "Snap: ${bestTarget!!.label}"
                                        } else {
                                            activeTarget = null
                                            onTrimLeft(newStart)
                                            snapGuideX = -1f
                                            snapLabel = null
                                        }
                                    },
                                    onDragEnd = {
                                        snapGuideX = -1f
                                        snapLabel = null
                                        activeTarget = null
                                        onTrimCommit()
                                    },
                                    onDragCancel = {
                                        snapGuideX = -1f
                                        snapLabel = null
                                        activeTarget = null
                                        onTrimCommit()
                                    }
                                )
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White)
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
                                var activeTarget: SnapTarget? = null

                                detectDragGestures(
                                    onDragStart = {
                                        accumX = 0f
                                        startMs = clip.sourceStartMs
                                        endMs = clip.sourceEndMs
                                        activeTarget = null
                                    },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        accumX += drag.x
                                        val dMs = (accumX / pxPerMs).toLong()
                                        val maxEnd = if (clip.sourceTotalMs != Long.MAX_VALUE)
                                            clip.sourceTotalMs else Long.MAX_VALUE
                                        val newEnd = (endMs + dMs)
                                            .coerceIn(
                                                startMs + EditorClip.MIN_DURATION_MS,
                                                maxEnd
                                            )

                                        val newEndTimelineMs =
                                            clip.timelineStartMs + (newEnd - clip.sourceStartMs)

                                        val targets = mutableListOf<SnapTarget>()
                                        allClips.filter { it.id != clip.id }.forEach { other ->
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
                                        targets.add(SnapTarget(currentPosMs, "Playhead"))

                                        var bestTarget: SnapTarget? = null
                                        var bestDist = enterMs
                                        targets.forEach { t ->
                                            val d = abs(t.timeMs - newEndTimelineMs)
                                            if (activeTarget != null &&
                                                activeTarget!!.timeMs == t.timeMs &&
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

                                        if (bestTarget != null) {
                                            activeTarget = bestTarget
                                            val deltaMs = bestTarget!!.timeMs - clip.timelineStartMs
                                            val finalSourceEnd = clip.sourceStartMs + deltaMs
                                            onTrimRight(finalSourceEnd.coerceAtMost(maxEnd))
                                            snapGuideX = (bestTarget!!.timeMs.toFloat() /
                                                    totalMs.toFloat()) * contentWidthPx
                                            snapLabel = "Snap: ${bestTarget!!.label}"
                                        } else {
                                            activeTarget = null
                                            onTrimRight(newEnd)
                                            snapGuideX = -1f
                                            snapLabel = null
                                        }
                                    },
                                    onDragEnd = {
                                        snapGuideX = -1f
                                        snapLabel = null
                                        activeTarget = null
                                        onTrimCommit()
                                    },
                                    onDragCancel = {
                                        snapGuideX = -1f
                                        snapLabel = null
                                        activeTarget = null
                                        onTrimCommit()
                                    }
                                )
                            },
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White)
                        )
                    }
                }
            }

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

        // ═══════════════════════════════════════════════════════
        //  🆕 TRANSITION MARKERS
        // ═══════════════════════════════════════════════════════
        clips.forEach { clip ->
            val trans = clip.transition
            if (trans != null && trans.isActive) {
                val hasAdjacentBefore = allClips.any { other ->
                    other.id != clip.id &&
                            other.isAudio == clip.isAudio &&
                            abs(other.timelineEndMs - clip.timelineStartMs) < 50L
                }
                if (hasAdjacentBefore) {
                    val junctionPx = clip.timelineStartMs.toFloat() /
                            totalMs * contentWidthPx
                    val isTransSelected = selectedTransitionClipId == clip.id

                    Box(
                        modifier = Modifier
                            .offset(x = with(density) { (junctionPx - 14f).toDp() })
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
            // Outer glow (yellow thick)
            Box(
                modifier = Modifier
                    .offset(x = with(density) { (snapGuideX - 4f).toDp() })
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(Color(0xFFFFD166).copy(alpha = 0.35f))
                    .zIndex(58f)
            )
            // Inner bright line
            Box(
                modifier = Modifier
                    .offset(x = with(density) { (snapGuideX - 1.5f).toDp() })
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(Color(0xFFFFD166))
                    .zIndex(60f)
            )
            // Label
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
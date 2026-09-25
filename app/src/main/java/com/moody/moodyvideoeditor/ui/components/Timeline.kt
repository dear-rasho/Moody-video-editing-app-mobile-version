package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
    onDragEnd: () -> Unit = {},
    onToggleVisualVisibility: (Int) -> Unit = {},
    onToggleAudioMute: (Int) -> Unit = {}
) {
    val density = LocalDensity.current
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    val labelWidthPx = with(density) { TRACK_LABEL_WIDTH.toPx() }

    var viewportWidthPx by remember { mutableFloatStateOf(0f) }
    var lastUserScrollMs by remember { mutableLongStateOf(0L) }

    // Smart zoom — slider 0 = Fit
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

    // 🆕 totalMs = actual clips duration (no 40 min minimum when clips exist)
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
                        audioLayerCount = state.audioLayerCount,
                        isHidden = state.hiddenVisualTracks.contains(i),
                        onToggleVisibility = onToggleVisualVisibility
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
                        audioLayerCount = state.audioLayerCount,
                        isMuted = state.mutedAudioTracks.contains(i),
                        onToggleMute = onToggleAudioMute
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
    onDragEnd: () -> Unit,
    visualLayerCount: Int,
    audioLayerCount: Int,
    isHidden: Boolean,
    onToggleVisibility: (Int) -> Unit
) {
    val isSelectedLayer = selectedTrackIndex == trackIndex && !selectedIsAudio

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(VISUAL_TRACK_HEIGHT),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label + 👁 side-by-side
        Row(
            modifier = Modifier
                .width(TRACK_LABEL_WIDTH)
                .fillMaxHeight()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                "V${trackIndex + 1}",
                color = when {
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
                onDragEnd = onDragEnd,
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
    onDragEnd: () -> Unit,
    visualLayerCount: Int,
    audioLayerCount: Int,
    isMuted: Boolean,
    onToggleMute: (Int) -> Unit
) {
    val isSelectedLayer = selectedTrackIndex == trackIndex && selectedIsAudio

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AUDIO_TRACK_HEIGHT),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .width(TRACK_LABEL_WIDTH)
                .fillMaxHeight()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                "A${trackIndex + 1}",
                color = when {
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
                onDragEnd = onDragEnd,
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
    onDragEnd: () -> Unit,
    visualLayerCount: Int,
    audioLayerCount: Int,
    trackHidden: Boolean = false
) {
    val density = LocalDensity.current

    var snapGuideX by remember { mutableFloatStateOf(-1f) }
    var snapLabel by remember { mutableStateOf<String?>(null) }

    // 🆕 Phase 2.5 — smooth drag local state
    var draggingClipId by remember { mutableStateOf<String?>(null) }
    var dragDeltaX by remember { mutableFloatStateOf(0f) }
    var dragDeltaY by remember { mutableFloatStateOf(0f) }
    var dragTargetTrack by remember { mutableIntStateOf(trackIndex) }
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
        val stepPx = with(density) { 30.dp.toPx() }
        val handleWidthPx = with(density) { 24.dp.toPx() }   // 🆕 18 → 24

        val enterMs = (SNAP_ENTER_PX / pxPerMs).toLong().coerceAtLeast(SNAP_MIN_GAP_MS)
        val releaseMs = (SNAP_RELEASE_PX / pxPerMs).toLong().coerceAtLeast(enterMs * 2)

        // ═══════════════════════════════════════════════════════
        //  👻 GHOST — semi-transparent copy at original position
        // ═══════════════════════════════════════════════════════
        draggingClipId?.let { dragId ->
            val ghost = clips.firstOrNull { it.id == dragId }
            if (ghost != null) {
                val ghostStartPx = ghost.timelineStartMs.toFloat() / totalMs * contentWidthPx
                val ghostEndPx = ghost.timelineEndMs.toFloat() / totalMs * contentWidthPx
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
                        .background(ghostColor.copy(alpha = 0.15f))
                        .border(
                            width = 1.5.dp,
                            color = ghostColor.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↺ ${ghost.name.take(16)}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  CLIPS
        // ═══════════════════════════════════════════════════════
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
                        // 🆕 Phase 2.5 — smooth local drag offset
                        if (isDragging) {
                            translationX = dragDeltaX
                            translationY = dragDeltaY
                            alpha = 0.85f
                            scaleX = 1.03f
                            scaleY = 1.03f
                        }
                    }
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            isMulti -> Color(0xFF4F9DFF)
                            else -> barColor   // 🆕 selected bhi normal color
                        }
                    )
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                width = 2.dp,
                                color = Color.White,
                                shape = RoundedCornerShape(4.dp)
                            )
                        } else Modifier
                    )
                    .graphicsLayer { alpha = trackAlpha }
                    .pointerInput(clip.id, contentWidthPx, totalMs) {
                        var accumulatedX = 0f
                        var accumulatedY = 0f
                        var startTimeMs = 0L
                        var startTrack = 0

                        detectDragGestures(
                            onDragStart = {
                                accumulatedX = 0f
                                accumulatedY = 0f
                                startTimeMs = clip.timelineStartMs
                                startTrack = clip.trackIndex

                                // 🆕 Start local drag
                                draggingClipId = clip.id
                                dragDeltaX = 0f
                                dragDeltaY = 0f
                                dragTargetTrack = startTrack
                                dragTargetTimeMs = startTimeMs
                            },
                            onDrag = { change, drag ->
                                change.consume()

                                // 🆕 Smooth local update
                                accumulatedX += drag.x
                                accumulatedY += drag.y
                                dragDeltaX = accumulatedX
                                dragDeltaY = accumulatedY

                                // Virtual position
                                val virtualStartMs = (startTimeMs +
                                        (accumulatedX / pxPerMs).toLong())
                                    .coerceAtLeast(0L)

                                // Snap detection — horizontal
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
                                targets.forEach { t ->
                                    // Start edge snap
                                    val dStart = abs(t.timeMs - virtualStartMs)
                                    if (dStart < bestDist) {
                                        bestDist = dStart
                                        bestTarget = t
                                    }
                                    // End edge snap
                                    val dEnd = abs(t.timeMs - virtualEndMs)
                                    if (dEnd < bestDist) {
                                        bestDist = dEnd
                                        bestTarget = t
                                    }
                                }

                                val finalTimeMs: Long
                                if (bestTarget != null) {
                                    // Check if start or end is closer
                                    val dStart = abs(bestTarget!!.timeMs - virtualStartMs)
                                    val dEnd = abs(bestTarget!!.timeMs - virtualEndMs)
                                    finalTimeMs = if (dStart <= dEnd) {
                                        bestTarget!!.timeMs
                                    } else {
                                        bestTarget!!.timeMs - clip.durationMs
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

                                // Vertical track calculation
                                val steps = (accumulatedY / stepPx).roundToInt()
                                var newTrack = if (clip.isAudio) {
                                    (startTrack + steps).coerceAtLeast(0)
                                } else {
                                    (startTrack - steps).coerceAtLeast(0)
                                }
                                dragTargetTrack = newTrack
                            },
                            onDragEnd = {
                                // 🆕 Commit on end only — smooth
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
                                onDragEnd()
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

                // Trim handles (only when selected AND not dragging)
                if (isSelected && !isDragging) {
                    // LEFT handle
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
                                .background(Color.White)   // 🆕 White handle
                        )
                    }

                    // RIGHT handle
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

        // ═══════════════════════════════════════════════════════
        //  🆕 CYAN SNAP LINE (vertical through track)
        // ═══════════════════════════════════════════════════════
        if (snapGuideX >= 0f) {
            Box(
                modifier = Modifier
                    .offset(x = with(density) { (snapGuideX - 1f).toDp() })
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF60EFFF))
                    .zIndex(60f)
            )
            snapLabel?.let { label ->
                Text(
                    text = label,
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .offset(
                            x = with(density) { (snapGuideX + 4).toDp() },
                            y = 4.dp
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF60EFFF))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .zIndex(61f)
                )
            }
        }
    }
}
package com.moody.moodyvideoeditor.ui.features

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.BeatsLibrary
import com.moody.moodyvideoeditor.data.BeatsState
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.BeatsEngine
import com.moody.moodyvideoeditor.viewmodel.EditorViewModel
import kotlinx.coroutines.launch

@Composable
fun BeatsPanel(
    state: BeatsState,
    viewModel: EditorViewModel,
    onDetect: (String) -> Unit = {},
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var detecting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var copiedMode by remember { mutableStateOf<String?>(null) }
    var showFullList by remember { mutableStateOf(false) }

    FeaturePanel(
        title = "🥁 Beats" + if (state.detected) " · ${state.count}" else "",
        onClose = onClose
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ═══ FILTER CHIPS ═══
            Text(
                "Beat Range Filter",
                color = Color(0xFF888888), fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BeatsLibrary.FILTERS.forEach { (key, label) ->
                    val isActive = state.filter == key
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isActive) Color(0xFF7C3AED)
                                else Color(0xFF181818)
                            )
                            .pointerInput(key, detecting) {
                                if (!detecting) {
                                    detectTapGestures {
                                        coroutineScope.launch {
                                            detecting = true
                                            progress = 0f
                                            errorMessage = null
                                            copiedMode = null

                                            val sourceClip =
                                                viewModel.state.value.selectedClip
                                                    ?: viewModel.state.value.clips
                                                        .firstOrNull {
                                                            it.isAudio &&
                                                                    it.uri != Uri.EMPTY
                                                        }
                                                    ?: viewModel.state.value.clips
                                                        .firstOrNull {
                                                            it.isVisualClip &&
                                                                    !it.type.startsWith(
                                                                        "image/"
                                                                    )
                                                        }

                                            val beats = try {
                                                if (sourceClip != null &&
                                                    sourceClip.uri != Uri.EMPTY
                                                ) {
                                                    BeatsEngine.detect(
                                                        context = context,
                                                        uri = sourceClip.uri,
                                                        filter = key,
                                                        onProgress = {
                                                            progress = it
                                                        }
                                                    )
                                                } else {
                                                    errorMessage =
                                                        "No audio source — synthetic mode"
                                                    BeatsEngine.detectSynthetic(
                                                        viewModel.state.value.totalDurationMs,
                                                        key
                                                    )
                                                }
                                            } catch (e: Throwable) {
                                                errorMessage =
                                                    "Failed: ${e.message}"
                                                BeatsEngine.detectSynthetic(
                                                    viewModel.state.value.totalDurationMs,
                                                    key
                                                )
                                            }

                                            viewModel.updateBeats(beats)
                                            onDetect(key)
                                            detecting = false
                                            progress = 0f
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ═══ DETECTING PROGRESS ═══
            if (detecting) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F1A2A))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "🎵 Analyzing audio… ${(progress * 100).toInt()}%",
                        color = Color(0xFF60EFFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF1A1A1A))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .height(4.dp)
                                .background(Color(0xFF60EFFF))
                        )
                    }
                }
            }

            // ═══ WARNING ═══
            if (!detecting && errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF2A1A0F))
                        .padding(8.dp)
                ) {
                    Text(
                        "⚠️ $errorMessage",
                        color = Color(0xFFF59E0B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ═══ SPEECH DETECTED ═══
            if (!detecting && !state.detected && state.filter != "all") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A0F0F))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "❌ No musical beats detected",
                            color = Color(0xFFFF6B6B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Speech/podcast detected. Try music audio for beats.",
                            color = Color(0xFF888888),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // ═══ RESULT ═══
            if (!detecting && state.detected) {
                // Stats
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F2A1A))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "✅ ${state.count} beats detected",
                        color = Color(0xFF22C55E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "First: ${state.beatTimesMs.firstOrNull() ?: 0}ms  ·  " +
                                "Last: ${state.beatTimesMs.lastOrNull() ?: 0}ms",
                        color = Color(0xFF888888),
                        fontSize = 10.sp
                    )
                    if (state.beatTimesMs.isNotEmpty()) {
                        Text(
                            "Avg gap: ${avgGap(state.beatTimesMs)}ms  ·  " +
                                    "≈ ${avgBpm(state.beatTimesMs)} BPM",
                            color = Color(0xFF60EFFF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 🆕 TOGGLE FULL LIST
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF181818))
                        .pointerInput(showFullList) {
                            detectTapGestures {
                                showFullList = !showFullList
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (showFullList) "▲ Hide Beat List"
                        else "▼ Show All Beats (${state.count})",
                        color = Color(0xFFA78BFA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 🆕 FULL BEAT LIST — scrollable, persistent
                if (showFullList) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F0F0F)),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(
                            items = state.beatTimesMs,
                            key = { idx, _ -> idx }
                        ) { idx, beatMs ->
                            BeatRowItem(
                                index = idx + 1,
                                beatMs = beatMs,
                                gapMs = if (idx > 0) {
                                    beatMs - state.beatTimesMs[idx - 1]
                                } else 0L,
                                onClick = {
                                    viewModel.setCurrentPos(beatMs)
                                }
                            )
                        }
                    }

                    Text(
                        "💡 Tap any beat to seek | Long data — scroll inside",
                        color = Color(0xFF666666),
                        fontSize = 9.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // ═══ COPY OPTIONS ═══
                Text(
                    "📋 Copy Beat Data (persistent)",
                    color = Color(0xFF888888), fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )

                CopyRow(
                    label = "Milliseconds",
                    value = state.beatTimesMs.joinToString(", ") { "$it" },
                    preview = state.beatTimesMs.take(4)
                        .joinToString(", ") { "$it" } +
                            if (state.beatTimesMs.size > 4) ", …" else "",
                    copiedMode = copiedMode,
                    mode = "ms",
                    onCopy = { text, mode ->
                        copyToClipboard(context, "Beat Times (ms)", text)
                        copiedMode = mode
                    }
                )

                CopyRow(
                    label = "Seconds (AI ready)",
                    value = state.beatTimesMs.joinToString(", ") {
                        "%.2f".format(it / 1000f)
                    },
                    preview = state.beatTimesMs.take(4)
                        .joinToString(", ") { "%.2f".format(it / 1000f) } +
                            if (state.beatTimesMs.size > 4) ", …" else "",
                    copiedMode = copiedMode,
                    mode = "sec",
                    onCopy = { text, mode ->
                        copyToClipboard(context, "Beat Times (sec)", text)
                        copiedMode = mode
                    }
                )

                CopyRow(
                    label = "Timestamps [MM:SS.ms]",
                    value = state.beatTimesMs.joinToString(", ") { ms ->
                        val totalSec = ms / 1000.0
                        val m = (totalSec / 60).toInt()
                        val s = totalSec - m * 60
                        "[%d:%05.2f]".format(m, s)
                    },
                    preview = state.beatTimesMs.take(3)
                        .joinToString(", ") { ms ->
                            val totalSec = ms / 1000.0
                            val m = (totalSec / 60).toInt()
                            val s = totalSec - m * 60
                            "[%d:%05.2f]".format(m, s)
                        } + if (state.beatTimesMs.size > 3) ", …" else "",
                    copiedMode = copiedMode,
                    mode = "ts",
                    onCopy = { text, mode ->
                        copyToClipboard(context, "Beat Timestamps", text)
                        copiedMode = mode
                    }
                )

                CopyRow(
                    label = "AI Prompt (Full)",
                    value = buildAiPromptFormat(state.beatTimesMs),
                    preview = "count: ${state.count}, bpm: ${avgBpm(state.beatTimesMs)}, beats: [...]",
                    copiedMode = copiedMode,
                    mode = "ai",
                    onCopy = { text, mode ->
                        copyToClipboard(context, "AI Beat Prompt", text)
                        copiedMode = mode
                    }
                )

                // Clear button
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                        .pointerInput(Unit) {
                            detectTapGestures { onClear() }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🗑 Clear Beats",
                        color = Color(0xFFFF6B6B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (!detecting && !state.detected && state.filter == "all") {
                Text(
                    "Tap a filter to detect beats",
                    color = Color(0xFF666666), fontSize = 10.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  BEAT ROW ITEM
// ═══════════════════════════════════════════════════════════════
@Composable
private fun BeatRowItem(
    index: Int,
    beatMs: Long,
    gapMs: Long,
    onClick: () -> Unit
) {
    val totalSec = beatMs / 1000.0
    val m = (totalSec / 60).toInt()
    val s = totalSec - m * 60
    val timeStr = "[%d:%05.2f]".format(m, s)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF1A1A1A))
            .pointerInput(beatMs) {
                detectTapGestures { onClick() }
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "#%d".format(index),
            color = Color(0xFFFFD166),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(36.dp)
        )
        Text(
            timeStr,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(72.dp)
        )
        Text(
            "${beatMs}ms",
            color = Color(0xFF888888),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(72.dp)
        )
        if (gapMs > 0) {
            Text(
                "+${gapMs}ms",
                color = Color(0xFF60EFFF),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  COPY ROW
// ═══════════════════════════════════════════════════════════════
@Composable
private fun CopyRow(
    label: String,
    value: String,
    preview: String,
    copiedMode: String?,
    mode: String,
    onCopy: (String, String) -> Unit
) {
    val isCopied = copiedMode == mode

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF181818))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                preview,
                color = Color(0xFF666666),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }

        Box(
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isCopied) Color(0xFF22C55E)
                    else Color(0xFF7C3AED).copy(alpha = 0.25f)
                )
                .pointerInput(value, mode) {
                    detectTapGestures { onCopy(value, mode) }
                }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isCopied) "✓ Copied" else "📋 Copy",
                color = if (isCopied) Color.White else Color(0xFF7C3AED),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  HELPERS
// ═══════════════════════════════════════════════════════════════
private fun copyToClipboard(context: Context, label: String, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    } catch (_: Throwable) {
    }
}

private fun avgGap(times: List<Long>): Long {
    if (times.size < 2) return 0L
    var sum = 0L
    for (i in 1 until times.size) {
        sum += (times[i] - times[i - 1])
    }
    return sum / (times.size - 1)
}

private fun avgBpm(times: List<Long>): Int {
    val gap = avgGap(times)
    if (gap <= 0L) return 0
    return (60000 / gap).toInt()
}

private fun buildAiPromptFormat(times: List<Long>): String {
    if (times.isEmpty()) return ""

    val count = times.size
    val bpm = avgBpm(times)
    val gapMs = avgGap(times)

    val sb = StringBuilder()
    sb.append("# Beat Detection Result\n")
    sb.append("count: $count\n")
    sb.append("bpm: $bpm\n")
    sb.append("avg_gap_ms: $gapMs\n")
    sb.append("beats_ms: [")
    sb.append(times.joinToString(", "))
    sb.append("]\n")
    sb.append("beats_sec: [")
    sb.append(times.joinToString(", ") { "%.3f".format(it / 1000f) })
    sb.append("]\n")
    sb.append("\n# Usage in Moody Code Mode:\n")
    sb.append("# beat pulse 120\n")
    sb.append("# beat bounce 130\n")
    sb.append("# beat shake 150\n")

    return sb.toString()
}
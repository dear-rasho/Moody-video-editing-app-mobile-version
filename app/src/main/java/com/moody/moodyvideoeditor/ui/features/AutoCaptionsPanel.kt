package com.moody.moodyvideoeditor.ui.features

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.CaptionGenerator
import com.moody.moodyvideoeditor.utils.CaptionSegment
import com.moody.moodyvideoeditor.utils.WhisperModelManager
import kotlinx.coroutines.delay

@Composable
fun AutoCaptionsPanel(
    isProcessing: Boolean,
    progress: Float,
    statusMessage: String,
    downloadedModels: Set<String>,
    hasClipSelected: Boolean,
    generatedCaptions: List<CaptionSegment>,
    detectedLanguage: String,
    onDownloadModel: (String) -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteModel: (String) -> Unit,
    onGenerateCaptions: (language: String, modelFile: String, grouping: CaptionGenerator.GroupingMode) -> Unit,
    onCancel: () -> Unit,
    onClearCaptions: () -> Unit = {},
    onClose: () -> Unit
) {
    var selectedLanguage by remember { mutableStateOf("auto") }
    var selectedGrouping by remember {
        mutableStateOf(CaptionGenerator.GroupingMode.PHRASE)
    }
    // 🆕 Default = Fast (Tiny) for best speed
    var selectedModel by remember { mutableStateOf(WhisperModelManager.MODEL_TINY) }
    val context = LocalContext.current
    var copiedAll by remember { mutableStateOf(false) }
    var copiedItemIdx by remember { mutableStateOf(-1) }

    FeaturePanel(title = "📝 Auto Captions", onClose = onClose) {

        if (!hasClipSelected) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF181818))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("👆", fontSize = 24.sp)
                Text(
                    "Select a video clip",
                    color = Color.White, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Tap a clip on the timeline first.",
                    color = Color(0xFF888888), fontSize = 10.sp
                )
            }
            return@FeaturePanel
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 550.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // Info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1F1438))
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        "🎙️ Offline Speech-to-Text",
                        color = Color(0xFFA78BFA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Runs on-device. No internet after download.",
                        color = Color(0xFF888888),
                        fontSize = 10.sp
                    )
                }
            }

            // Progress
            if (isProcessing) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F1A2A))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        statusMessage.ifBlank { "Processing…" },
                        color = Color(0xFF60EFFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF1A1A1A))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .height(6.dp)
                                .background(Color(0xFF60EFFF))
                        )
                    }
                    Text(
                        "${(progress * 100).toInt()}%",
                        color = Color(0xFF888888),
                        fontSize = 10.sp
                    )
                }
            }

            // Error
            if (!isProcessing && statusMessage.isNotBlank() &&
                statusMessage.startsWith("❌")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A0F0F))
                        .padding(10.dp)
                ) {
                    Text(
                        statusMessage,
                        color = Color(0xFFFF6B6B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Generated list
            if (generatedCaptions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "✅ ${generatedCaptions.size} Captions (${detectedLanguage.uppercase()})",
                        color = Color(0xFF22C55E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (copiedAll) Color(0xFF22C55E)
                                else Color(0xFF7C3AED).copy(alpha = 0.25f)
                            )
                            .pointerInput(generatedCaptions.size) {
                                detectTapGestures {
                                    copyToClipboard(
                                        context,
                                        buildSrtText(generatedCaptions),
                                        "Moody Captions"
                                    )
                                    copiedAll = true
                                }
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (copiedAll) "✓ Copied SRT" else "📋 Copy SRT",
                            color = if (copiedAll) Color.White
                            else Color(0xFFA78BFA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F0F0F))
                        .verticalScroll(rememberScrollState())
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    generatedCaptions.forEachIndexed { idx, seg ->
                        val isCopied = copiedItemIdx == idx
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF181818))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    formatTime(seg.startMs) + " → " +
                                            formatTime(seg.endMs),
                                    color = Color(0xFF60EFFF),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    seg.text,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    maxLines = 3
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(
                                        if (isCopied) Color(0xFF22C55E)
                                        else Color(0xFF7C3AED).copy(alpha = 0.25f)
                                    )
                                    .pointerInput(idx) {
                                        detectTapGestures {
                                            copyToClipboard(
                                                context,
                                                formatItem(seg),
                                                "Moody Caption"
                                            )
                                            copiedItemIdx = idx
                                        }
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (isCopied) "✓" else "📋",
                                    color = if (isCopied) Color.White
                                    else Color(0xFFA78BFA),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                copiedItemIdx = -1
                                copiedAll = false
                                onClearCaptions()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🗑 Clear caption list",
                        color = Color(0xFFFF6B6B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                LaunchedEffect(copiedAll) {
                    if (copiedAll) { delay(1500); copiedAll = false }
                }
                LaunchedEffect(copiedItemIdx) {
                    if (copiedItemIdx >= 0) { delay(1500); copiedItemIdx = -1 }
                }
            }

            // Main action
            if (!isProcessing && generatedCaptions.isEmpty()) {

                // Language
                Text(
                    "Language",
                    color = Color(0xFF888888),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "auto" to "🌐", "en" to "🇬🇧", "hi" to "🇮🇳",
                        "ur" to "🇵🇰", "zh" to "🇨🇳", "ar" to "🇸🇦"
                    ).forEach { (code, flag) ->
                        val isActive = selectedLanguage == code
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isActive) Color(0xFF7C3AED)
                                    else Color(0xFF181818)
                                )
                                .pointerInput(code) {
                                    detectTapGestures { selectedLanguage = code }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(flag, fontSize = 14.sp)
                        }
                    }
                }

                // Caption Style
                Text(
                    "Caption Style",
                    color = Color(0xFF888888),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CaptionGenerator.GroupingMode.values().forEach { mode ->
                        val isActive = selectedGrouping == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isActive) Color(0xFF7C3AED)
                                    else Color(0xFF181818)
                                )
                                .pointerInput(mode.key) {
                                    detectTapGestures { selectedGrouping = mode }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(mode.icon, fontSize = 14.sp)
                                Text(
                                    mode.label.split(" ").first(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Model picker
                Text(
                    "Model (Speed vs Accuracy)",
                    color = Color(0xFF888888),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )

                WhisperModelManager.MODELS.forEach { model ->
                    val downloaded = model.fileName in downloadedModels
                    val isSelected = selectedModel == model.fileName

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) Color(0xFF2A1F4D)
                                else Color(0xFF181818)
                            )
                            .then(
                                if (isSelected) Modifier.border(
                                    1.dp, Color(0xFF7C3AED),
                                    RoundedCornerShape(8.dp)
                                ) else Modifier
                            )
                            .pointerInput(model.fileName) {
                                detectTapGestures { selectedModel = model.fileName }
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .height(20.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (downloaded) Color(0xFF22C55E)
                                    else if (isSelected) Color(0xFF7C3AED)
                                    else Color(0xFF2A2A2A)
                                )
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (downloaded) "✓ Ready" else "Not Downloaded",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${model.label} · ${model.sizeBytes / 1024 / 1024} MB",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${model.speedLabel} · ${model.qualityLabel}",
                                color = Color(0xFF888888),
                                fontSize = 9.sp
                            )
                        }

                        if (downloaded) {
                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                                    .pointerInput(model.fileName) {
                                        detectTapGestures { onDeleteModel(model.fileName) }
                                    }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🗑", color = Color(0xFFFF6B6B), fontSize = 10.sp)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF4F9DFF).copy(alpha = 0.2f))
                                    .pointerInput(model.fileName) {
                                        detectTapGestures { onDownloadModel(model.fileName) }
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "⬇",
                                    color = Color(0xFF4F9DFF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Generate button
                val selectedModelReady = selectedModel in downloadedModels

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selectedModelReady) Color(0xFF7C3AED)
                            else Color(0xFF2A2A2A)
                        )
                        .pointerInput(selectedModelReady) {
                            detectTapGestures {
                                if (selectedModelReady) {
                                    onGenerateCaptions(
                                        selectedLanguage,
                                        selectedModel,
                                        selectedGrouping
                                    )
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (selectedModelReady) "✨ Generate Captions"
                        else "⬇ Download selected model first",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "💡 Fast = 10x quicker, Basic accuracy. Balanced = good tradeoff.",
                    color = Color(0xFF666666),
                    fontSize = 9.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF6B6B).copy(alpha = 0.2f))
                        .pointerInput(Unit) {
                            detectTapGestures { onCancel() }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "✕ Cancel",
                        color = Color(0xFFFF6B6B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000.0
    val m = (totalSec / 60).toInt()
    val s = totalSec - m * 60
    return "%d:%05.2f".format(m, s)
}

private fun formatItem(seg: CaptionSegment): String =
    "[${formatTime(seg.startMs)} - ${formatTime(seg.endMs)}] ${seg.text}"

private fun buildSrtText(segments: List<CaptionSegment>): String {
    val sb = StringBuilder()
    segments.forEachIndexed { idx, seg ->
        sb.append(idx + 1).append("\n")
        sb.append(srtTime(seg.startMs))
            .append(" --> ")
            .append(srtTime(seg.endMs))
            .append("\n")
        sb.append(seg.text).append("\n\n")
    }
    return sb.toString().trim()
}

private fun srtTime(ms: Long): String {
    val h = (ms / 3_600_000)
    val m = (ms % 3_600_000) / 60_000
    val s = (ms % 60_000) / 1000
    val msec = ms % 1000
    return "%02d:%02d:%02d,%03d".format(h, m, s, msec)
}

private fun copyToClipboard(context: Context, text: String, label: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    } catch (_: Throwable) {
    }
}
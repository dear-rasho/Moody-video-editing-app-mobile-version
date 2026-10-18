package com.moody.moodyvideoeditor.ui.features

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.moody.moodyvideoeditor.utils.CaptionLanguage
import com.moody.moodyvideoeditor.utils.CaptionSegment
import com.moody.moodyvideoeditor.utils.WhisperModelManager
import kotlinx.coroutines.delay

@Composable
fun AutoCaptionsPanel(
    isProcessing: Boolean,
    progress: Float,
    statusMessage: String,
    hasClipSelected: Boolean,
    generatedCaptions: List<CaptionSegment>,
    detectedLanguage: String,
    onDownloadModel: (String) -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteModel: (String) -> Unit,
    onGenerateCaptions: (
        grouping: CaptionGenerator.GroupingMode,
        autoStyle: Boolean,
        language: String,
        modelFile: String,
        wordsPerLayer: Int,
        wordsPerLine: Int
    ) -> Unit,
    onCancel: () -> Unit,
    onClearCaptions: () -> Unit = {},
    onClose: () -> Unit
) {
    val context = LocalContext.current

    var selectedGrouping by remember {
        mutableStateOf(CaptionGenerator.GroupingMode.WORDS)
    }
    var autoStyle by remember { mutableStateOf(CaptionGenerator.autoStyleEnabled) }
    var selectedLanguage by remember { mutableStateOf(CaptionLanguage.AUTO) }
    var selectedModel by remember { mutableStateOf(WhisperModelManager.MODEL_BASE) }

    // ✅ Default 2 words per layer (user can change 1-7)
    var wordsPerLayer by remember { mutableIntStateOf(2) }
    // ✅ Default 1 line (user can change 1-5)
    var wordsPerLine by remember { mutableIntStateOf(1) }

    var copiedAll by remember { mutableStateOf(false) }
    var copiedItemIdx by remember { mutableStateOf(-1) }

    var downloadTick by remember { mutableStateOf(0) }

    val baseReady = remember(downloadTick) { WhisperModelManager.isBaseReady(context) }

    val currentModelReady = remember(selectedModel, downloadTick) {
        WhisperModelManager.isModelDownloaded(context, selectedModel)
    }

    FeaturePanel(title = "🎙️ Auto Captions", onClose = onClose) {

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
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ─── Info banner ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1F1438))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "🎙️ Offline Speech-to-Text",
                        color = Color(0xFFA78BFA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Whisper AI · Supports 90+ languages",
                        color = Color(0xFF888888),
                        fontSize = 10.sp
                    )
                    Text(
                        "⬇️ Download once · Works fully offline · No internet needed after",
                        color = Color(0xFF22C55E),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ─── Progress bar ───
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

            // ─── Error banner ───
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

            // ═══════════════════════════════════════════════════════
            //  GENERATED LIST
            // ═══════════════════════════════════════════════════════
            if (generatedCaptions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "✅ ${generatedCaptions.size} captions" +
                                if (detectedLanguage.isNotBlank())
                                    " (${detectedLanguage.uppercase()})"
                                else "",
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
                            if (copiedAll) "✓ SRT" else "📋 SRT",
                            color = if (copiedAll) Color.White else Color(0xFFA78BFA),
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

            // ═══════════════════════════════════════════════════════
            //  MAIN ACTION
            // ═══════════════════════════════════════════════════════
            if (!isProcessing && generatedCaptions.isEmpty()) {

                // ─── Language picker ───
                Text(
                    "Transcription Language",
                    color = Color(0xFF888888),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CaptionLanguage.QUICK_PICK.forEach { lang ->
                        val isActive = selectedLanguage == lang
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isActive) Color(0xFF7C3AED) else Color(0xFF181818)
                                )
                                .pointerInput(lang.code) {
                                    detectTapGestures { selectedLanguage = lang }
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(lang.flag, fontSize = 12.sp)
                                Text(
                                    lang.label,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // ─── Warning: model not downloaded ───
                if (!baseReady) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2A1A0F))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "⬇️ Download Required",
                                color = Color(0xFFFFAA33),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Multilingual Base model (142 MB) is required for transcription.\n" +
                                        "Download once — works offline forever.",
                                color = Color(0xFFAAAAAA),
                                fontSize = 9.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }

                // ═══════════════════════════════════════════════════
                //  MODEL PICKER (single model — Base)
                // ═══════════════════════════════════════════════════
                Text(
                    "Model",
                    color = Color(0xFF888888),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )

                WhisperModelManager.MODELS.forEach { model ->
                    val isDownloaded = remember(downloadTick) {
                        WhisperModelManager.isModelDownloaded(context, model.fileName)
                    }
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
                                detectTapGestures {
                                    if (isDownloaded) selectedModel = model.fileName
                                }
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .height(22.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    if (isDownloaded) Color(0xFF22C55E)
                                    else Color(0xFF4F9DFF).copy(alpha = 0.25f)
                                )
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isDownloaded) "✓ Ready" else "Not Downloaded",
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
                                model.bestFor,
                                color = if (isSelected) Color(0xFFA78BFA)
                                else Color(0xFF888888),
                                fontSize = 9.sp
                            )
                        }

                        if (isDownloaded) {
                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                                    .pointerInput(model.fileName) {
                                        detectTapGestures {
                                            onDeleteModel(model.fileName)
                                            downloadTick++
                                        }
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
                                        detectTapGestures {
                                            onDownloadModel(model.fileName)
                                        }
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "⬇ Download",
                                    color = Color(0xFF4F9DFF),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // ─── AI Auto-Style toggle ───
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1F1438))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🤖", fontSize = 18.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "AI Auto-Style",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (autoStyle) "Random fonts, glows, animations"
                            else "Simple plain captions",
                            color = Color(0xFF888888),
                            fontSize = 9.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (autoStyle) Color(0xFF7C3AED)
                                else Color(0xFF181818)
                            )
                            .pointerInput(autoStyle) {
                                detectTapGestures {
                                    autoStyle = !autoStyle
                                    CaptionGenerator.autoStyleEnabled = autoStyle
                                }
                            }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (autoStyle) "ON" else "OFF",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // ─── Style: Words / Lines ───
                Text(
                    "Caption Style",
                    color = Color(0xFF888888),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CaptionGenerator.GroupingMode.values().forEach { mode ->
                        val isActive = selectedGrouping == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(8.dp))
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
                                Text(mode.icon, fontSize = 18.sp)
                                Text(
                                    mode.label,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // ═══════════════════════════════════════════════════
                //  WORDS PER LAYER — only for WORDS mode
                //  Default = 2 (user can change 1-7)
                // ═══════════════════════════════════════════════════
                if (selectedGrouping == CaptionGenerator.GroupingMode.WORDS) {
                    Text(
                        "Words per layer: $wordsPerLayer",
                        color = Color(0xFF888888),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (n in 1..7) {
                            val isActive = wordsPerLayer == n
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isActive) Color(0xFF7C3AED)
                                        else Color(0xFF181818)
                                    )
                                    .pointerInput(n) {
                                        detectTapGestures { wordsPerLayer = n }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$n",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        "1 = one word per caption · 7 = seven words per caption",
                        color = Color(0xFF666666),
                        fontSize = 9.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // ═══════════════════════════════════════════════════
                //  WORDS PER LINE — only for LINES mode
                //  Default = 1 (user can change 1-5)
                // ═══════════════════════════════════════════════════
                if (selectedGrouping == CaptionGenerator.GroupingMode.LINES) {
                    Text(
                        "Words per line: $wordsPerLine",
                        color = Color(0xFF888888),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (n in 1..5) {
                            val isActive = wordsPerLine == n
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isActive) Color(0xFF7C3AED)
                                        else Color(0xFF181818)
                                    )
                                    .pointerInput(n) {
                                        detectTapGestures { wordsPerLine = n }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$n",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        "1 = single line · 5 = up to five lines",
                        color = Color(0xFF666666),
                        fontSize = 9.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // ─── Generate button ───
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (currentModelReady) Color(0xFF7C3AED)
                            else Color(0xFF2A2A2A)
                        )
                        .pointerInput(
                            currentModelReady, selectedGrouping,
                            autoStyle, selectedLanguage, selectedModel,
                            wordsPerLayer
                        ) {
                            detectTapGestures {
                                if (currentModelReady) {
                                    onGenerateCaptions(
                                        selectedGrouping,
                                        autoStyle,
                                        selectedLanguage.code,
                                        selectedModel,
                                        wordsPerLayer,
                                        wordsPerLine
                                    )
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (currentModelReady)
                            "🎙️ Transcribe from Audio"
                        else
                            "⬇ Download model first",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "💡 Tip: For manual text captions, use ✍️ Manual Cap",
                    color = Color(0xFF666666),
                    fontSize = 9.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // ─── Cancel button ───
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

// ═══════════════════════════════════════════════════════════════
//  HELPERS
// ═══════════════════════════════════════════════════════════════

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
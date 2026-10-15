package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.CaptionGenerator
import kotlinx.coroutines.delay

@Composable
fun ManualCaptionsPanel(
    statusMessage: String,
    onAddManual: (
        input: String,
        grouping: CaptionGenerator.GroupingMode,
        autoStyle: Boolean,
        wordsPerLayer: Int,
        wordsPerLine: Int
    ) -> Unit,
    onClearCaptions: () -> Unit,
    onClose: () -> Unit
) {
    var manualText by remember { mutableStateOf("") }
    var selectedGrouping by remember {
        mutableStateOf(CaptionGenerator.GroupingMode.WORDS)
    }
    var autoStyle by remember { mutableStateOf(true) }
    var wordsPerLayer by remember { mutableIntStateOf(1) }
    var wordsPerLine by remember { mutableIntStateOf(7) }
    var localStatus by remember { mutableStateOf("") }

    LaunchedEffect(statusMessage) {
        if (statusMessage.isNotBlank()) localStatus = statusMessage
    }

    LaunchedEffect(localStatus) {
        if (localStatus.startsWith("✅")) {
            delay(1500)
            localStatus = ""
        }
    }

    FeaturePanel(title = "✍️ Manual Captions", onClose = onClose) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ─── Status banner ───
            if (localStatus.isNotBlank()) {
                val isError = localStatus.startsWith("❌")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isError) Color(0xFF2A0F0F) else Color(0xFF0F2A1A)
                        )
                        .padding(10.dp)
                ) {
                    Text(
                        localStatus,
                        color = if (isError) Color(0xFFFF6B6B)
                        else Color(0xFF22C55E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ─── Info banner ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1F1438))
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        "✍️ Paste Your Captions",
                        color = Color(0xFFA78BFA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "No clip selection needed · Fonts auto-detected",
                        color = Color(0xFF888888),
                        fontSize = 10.sp
                    )
                }
            }

            // ─── Format hint ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F1A2A))
                    .padding(8.dp)
            ) {
                Text(
                    "Format:  (MM:SS) text — one per line\n" +
                            "Example:\n" +
                            "(0:00) hello world how are you\n" +
                            "(0:04) next caption here\n" +
                            "(0:08) final message",
                    color = Color(0xFF60EFFF),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 13.sp
                )
            }

            // ─── Text area ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0A0A0A))
                    .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(8.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 130.dp, max = 240.dp)
                        .padding(12.dp)
                ) {
                    if (manualText.isBlank()) {
                        Text(
                            "(0:00) hello friend how are you\n" +
                                    "(0:04) welcome back everyone\n" +
                                    "(0:08) thanks for watching",
                            color = Color(0xFF444444),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                    BasicTextField(
                        value = manualText,
                        onValueChange = { manualText = it },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        ),
                        cursorBrush = SolidColor(Color(0xFF7C3AED)),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ═══════════════════════════════════════════════════════
            //  AI AUTO-STYLE TOGGLE
            // ═══════════════════════════════════════════════════════
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
                        color = Color.White, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (autoStyle) "Random fonts, glows, animations"
                        else "Simple plain captions",
                        color = Color(0xFF888888), fontSize = 9.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (autoStyle) Color(0xFF7C3AED) else Color(0xFF181818)
                        )
                        .clickable {
                            autoStyle = !autoStyle
                            CaptionGenerator.autoStyleEnabled = autoStyle
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (autoStyle) "ON" else "OFF",
                        color = Color.White, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ═══════════════════════════════════════════════════════
            //  CAPTION STYLE — Words / Lines
            // ═══════════════════════════════════════════════════════
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
                            .clickable { selectedGrouping = mode },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(mode.icon, fontSize = 18.sp)
                            Text(
                                mode.label,
                                color = Color.White, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════
            //  WORDS MODE — Words per layer (1-7)
            // ═══════════════════════════════════════════════════════
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
                                .height(38.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isActive) Color(0xFF7C3AED)
                                    else Color(0xFF181818)
                                )
                                .clickable { wordsPerLayer = n },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$n",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    "Selected: $wordsPerLayer word(s) per layer",
                    color = Color(0xFF666666),
                    fontSize = 9.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // ═══════════════════════════════════════════════════════
            //  LINES MODE — Words per line (3-7)
            // ═══════════════════════════════════════════════════════
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
                    for (n in 3..7) {
                        val isActive = wordsPerLine == n
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isActive) Color(0xFF7C3AED)
                                    else Color(0xFF181818)
                                )
                                .clickable { wordsPerLine = n },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$n",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    "Selected: $wordsPerLine word(s) per line",
                    color = Color(0xFF666666),
                    fontSize = 9.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // ═══════════════════════════════════════════════════════
            //  GENERATE BUTTON
            // ═══════════════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (manualText.isNotBlank()) Color(0xFF22C55E)
                        else Color(0xFF2A2A2A)
                    )
                    .clickable(enabled = manualText.isNotBlank()) {
                        onAddManual(
                            manualText,
                            selectedGrouping,
                            autoStyle,
                            wordsPerLayer,
                            wordsPerLine
                        )
                        manualText = ""
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "✨ Generate Captions",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // ─── Clear button ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                    .clickable {
                        manualText = ""
                        localStatus = ""
                        onClearCaptions()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🗑 Clear & Reset",
                    color = Color(0xFFFF6B6B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
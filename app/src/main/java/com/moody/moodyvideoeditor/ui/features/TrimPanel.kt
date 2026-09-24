package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

/**
 * Mirrors js/features/trim.js
 */
@Composable
fun TrimPanel(
    clipName: String,
    clipStartMs: Long,
    clipEndMs: Long,
    playheadMs: Long,
    sourceInMs: Long,
    sourceOutMs: Long,
    hasClipSelected: Boolean,
    playheadInsideClip: Boolean,
    onClose: () -> Unit,
    onTrimLeft: () -> Unit,
    onTrimRight: () -> Unit,
    onSplit: () -> Unit
) {
    FeaturePanel(title = "✂️ Trim", onClose = onClose) {

        if (!hasClipSelected) {
            EmptyState("👆", "No clip selected", "Tap a clip on the timeline first.")
            return@FeaturePanel
        }

        // ─── INFO BOX ─────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF181818))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            InfoRow("Clip:", clipName)
            InfoRow(
                "Range:",
                "${fmt(clipStartMs)} → ${fmt(clipEndMs)}  (${fmt(clipEndMs - clipStartMs)})"
            )
            InfoRow("Source:", "${fmt(sourceInMs)} → ${fmt(sourceOutMs)}")
            InfoRow("Playhead:", fmt(playheadMs))
        }

        if (!playheadInsideClip) {
            Spacer(Modifier.height(6.dp))
            EmptyState(
                icon = "🎯",
                title = "Playhead clip ke bahar hai",
                text = "Playhead ${fmt(playheadMs)} pe hai, clip ${fmt(clipStartMs)}–${fmt(clipEndMs)} me hai. Playhead andar move karo."
            )
            return@FeaturePanel
        }

        // ─── PREVIEW BOX ──────────────────────────────
        val cutLeft = playheadMs - clipStartMs
        val cutRight = clipEndMs - playheadMs
        val totalDur = clipEndMs - clipStartMs

        Spacer(Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F1A2E))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "📊 Preview",
                color = Color(0xFF4F9DFF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            PreviewRow("Trim Left →", totalDur, cutRight)
            PreviewRow("Trim Right →", totalDur, cutLeft)
            Text(
                "Trim L: ${fmt(cutLeft)} katega  |  Trim R: ${fmt(cutRight)} katega",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // ─── ACTION CARDS ─────────────────────────────
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionCard(
                icon = "✂️",
                label = "Split",
                desc = "Beech se todo",
                modifier = Modifier.weight(1f),
                onClick = onSplit
            )
            ActionCard(
                icon = "⬅️",
                label = "Trim Left",
                desc = "${fmt(cutLeft)} hatao",
                modifier = Modifier.weight(1f),
                onClick = onTrimLeft
            )
            ActionCard(
                icon = "➡️",
                label = "Trim Right",
                desc = "${fmt(cutRight)} hatao",
                modifier = Modifier.weight(1f),
                danger = true,
                onClick = onTrimRight
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF888888), fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PreviewRow(label: String, beforeMs: Long, afterMs: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, color = Color(0xFFAAAAAA), fontSize = 10.sp, modifier = Modifier.weight(1f))
        Text(fmt(beforeMs), color = Color(0xFF666666), fontSize = 10.sp)
        Text("→", color = Color(0xFF4F9DFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(
            fmt(afterMs),
            color = Color(0xFF22C55E),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EmptyState(icon: String, title: String, text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF181818))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(icon, fontSize = 24.sp)
        Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text, color = Color(0xFF888888), fontSize = 10.sp)
    }
}

@Composable
private fun ActionCard(
    icon: String,
    label: String,
    desc: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val bg = if (danger) Color(0xFF2A1414) else Color(0xFF181818)

    Column(
        modifier = modifier
            .height(88.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(desc, color = Color(0xFF888888), fontSize = 9.sp)
    }
}

private fun fmt(ms: Long): String {
    val totalSec = (ms / 1000.0)
    val min = (totalSec / 60).toInt()
    val sec = totalSec - min * 60
    return if (min > 0) "%d:%05.2f".format(min, sec) else "%.2fs".format(sec)
}
package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.EditorState

/**
 * Mini preview of a saved template.
 * Shows first N text layers rendered with their actual styling.
 * Falls back to clip counts if no text layers exist.
 */
@Composable
fun SavedTemplatePreview(
    state: EditorState,
    modifier: Modifier = Modifier
) {
    val textClips = remember(state.clips) {
        state.clips
            .filter { it.isTextClip && it.textState != null }
            .sortedBy { it.timelineStartMs }
            .take(3)
    }

    val imageCount = remember(state.clips) {
        state.clips.count { it.isVisualClip && it.type.startsWith("image/") }
    }
    val videoCount = remember(state.clips) {
        state.clips.count { it.isVisualClip && it.type.startsWith("video/") }
    }
    val totalClips = state.clips.size

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(0.dp))
            .background(Color(0xFF0F0F0F))
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (imageCount > 0) {
                MiniBadge("🖼️ $imageCount", Color(0xFF4F9DFF))
            }
            if (videoCount > 0) {
                MiniBadge("🎬 $videoCount", Color(0xFF7C3AED))
            }
        }

        if (textClips.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                textClips.forEach { clip ->
                    val ts = clip.textState ?: return@forEach
                    val sampleText = ts.content
                        .replace("\n", " ")
                        .take(15)
                        .ifBlank { "..." }

                    Text(
                        text = sampleText,
                        color = Color(ts.color),
                        fontSize = 8.sp,
                        fontWeight = if (ts.fontWeight == "bold")
                            FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            Text(
                "No text",
                color = Color(0xFF555555),
                fontSize = 8.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                "$totalClips clips",
                color = Color.White,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MiniBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.4f))
            .padding(horizontal = 3.dp, vertical = 1.dp)
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
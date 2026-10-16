package com.moody.moodyvideoeditor.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.moody.moodyvideoeditor.data.SavedTemplateMeta
import com.moody.moodyvideoeditor.ui.components.SavedTemplatePreview

@Composable
fun SavedTemplatesShelf(
    templates: List<SavedTemplateMeta>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    if (templates.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF181818)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("✍️", fontSize = 26.sp)
                Text(
                    "No saved templates yet",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "In editor → \"Save as Template\"",
                    color = Color(0xFF888888),
                    fontSize = 10.sp
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        templates.forEach { t ->
            TemplateCard(
                template = t,
                onClick = { onOpen(t.id) },
                onDelete = { onDelete(t.id) }
            )
        }
        Box(modifier = Modifier.width(4.dp))
    }
}

@Composable
private fun TemplateCard(
    template: SavedTemplateMeta,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF181818))
            .clickable { onClick() }
    ) {
        Column {
            // ─── Thumbnail area ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(Color(0xFF1F1438)),
                contentAlignment = Alignment.Center
            ) {
                // 🆕 Live preview (if editorState is available)
                val state = template.editorState
                if (state != null) {
                    SavedTemplatePreview(
                        state = state,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                    )
                } else {
                    // Fallback: icon + slot info
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("🎞️", fontSize = 26.sp)
                        Text(
                            "${template.imageSlotCount}🖼️ · ${template.textSlotCount}📝",
                            color = Color(0xFFA78BFA),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Duration badge (bottom-right)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        template.displayDuration,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Ratio badge (top-left)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        template.aspectRatio,
                        color = Color(0xFF60EFFF),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ─── Info area ───
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    template.name,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${template.clipCount} layers · ${template.displayDate}",
                    color = Color(0xFF888888),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // ─── Delete button (top-right) ───
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A1414))
                .clickable { onDelete() }
                .zIndex(10f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "✕",
                color = Color(0xFFFF6B6B),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
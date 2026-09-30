package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.FilterState
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

@Composable
fun FiltersPanel(
    current: FilterState,
    hasClipSelected: Boolean,
    onFilterChanged: (FilterState) -> Unit,
    onResetAll: () -> Unit,
    onClose: () -> Unit
) {
    var activeFilterKey by remember { mutableStateOf<String?>(null) }

    FeaturePanel(title = "🎨 Filters", onClose = onClose) {
        if (!hasClipSelected) {
            EmptyState(
                icon = "👆",
                title = "No clip selected",
                text = "Pehle timeline pe ek clip select karo."
            )
            return@FeaturePanel
        }

        val activeKey = activeFilterKey
        if (activeKey == null) {
            ListView(
                state = current,
                onFilterTap = { activeFilterKey = it },
                onResetAll = onResetAll
            )
        } else {
            val meta =
                FilterState.FILTERS.firstOrNull { it.key == activeKey } ?: return@FeaturePanel
            SliderView(
                meta = meta,
                value = current.get(activeKey),
                isChanged = current.isChanged(activeKey),
                onValueChanged = { newVal ->
                    onFilterChanged(current.set(activeKey, newVal))
                },
                onReset = {
                    onFilterChanged(current.set(activeKey, meta.default))
                },
                onBack = { activeFilterKey = null }
            )
        }
    }
}

@Composable
private fun ListView(
    state: FilterState,
    onFilterTap: (String) -> Unit,
    onResetAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val filters = FilterState.FILTERS
        val rows = filters.chunked(3)

        rows.forEach { rowFilters ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowFilters.forEach { meta ->
                    FilterGridItem(
                        meta = meta,
                        value = state.get(meta.key),
                        isChanged = state.isChanged(meta.key),
                        modifier = Modifier.weight(1f),
                        onClick = { onFilterTap(meta.key) }
                    )
                }
                repeat(3 - rowFilters.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        if (state.hasAnyChange) {
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                    .pointerInput(Unit) { detectTapGestures { onResetAll() } },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "↺ Reset All Filters",
                    color = Color(0xFFFF6B6B),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FilterGridItem(
    meta: FilterState.Companion.Meta,
    value: Float,
    isChanged: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (isChanged) Color(0xFF2A1F4D) else Color(0xFF181818)
    val iconBg = if (isChanged) Color(0xFF7C3AED) else Color(0xFF222222)

    Column(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .pointerInput(meta.key) { detectTapGestures { onClick() } }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(meta.icon, fontSize = 15.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            meta.label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        if (isChanged) {
            Text(
                "${value.toInt()}${meta.suffix}",
                color = Color(0xFF7C3AED),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SliderView(
    meta: FilterState.Companion.Meta,
    value: Float,
    isChanged: Boolean,
    onValueChanged: (Float) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF181818))
                    .pointerInput(Unit) { detectTapGestures { onBack() } },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "${meta.icon}  ${meta.label}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF181818))
                    .pointerInput(Unit) { detectTapGestures { onReset() } }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("↺", color = Color(0xFF888888), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Slider(
                value = value.coerceIn(meta.min, meta.max),
                onValueChange = {
                    val snapped = if (meta.step >= 1f) kotlin.math.round(it) else it
                    onValueChanged(snapped)
                },
                valueRange = meta.min..meta.max,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF7C3AED),
                    activeTrackColor = Color(0xFF7C3AED),
                    inactiveTrackColor = Color(0xFF303030)
                )
            )

            Box(
                modifier = Modifier
                    .width(70.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF181818)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (meta.step < 1f) String.format("%.1f%s", value, meta.suffix)
                    else "${value.toInt()}${meta.suffix}",
                    color = if (isChanged) Color(0xFF7C3AED) else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            "Range ${meta.min.toInt()}–${meta.max.toInt()}${meta.suffix}  •  Default ${meta.default.toInt()}${meta.suffix}",
            color = Color(0xFF666666),
            fontSize = 10.sp,
            modifier = Modifier.padding(start = 4.dp)
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
package com.moody.moodyvideoeditor.ui.features

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

data class FilterOption(
    val key: String,
    val name: String
)

@Composable
fun FiltersPanel(
    currentFilter: String,
    onFilterSelected: (String) -> Unit,
    onClose: () -> Unit
) {
    val filters = listOf(
        FilterOption("none", "None"),
        FilterOption("bw", "B&W"),
        FilterOption("sepia", "Sepia"),
        FilterOption("vivid", "Vivid"),
        FilterOption("warm", "Warm"),
        FilterOption("cool", "Cool"),
        FilterOption("vintage", "Vintage"),
        FilterOption("cinematic", "Cinematic"),
        FilterOption("noir", "Noir"),
        FilterOption("dramatic", "Dramatic"),
        FilterOption("faded", "Faded"),
        FilterOption("dreamy", "Dreamy")
    )

    FeaturePanel(title = "🎨 Filters", onClose = onClose) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { filter ->
                val isActive = currentFilter == filter.key
                Column(
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                        .pointerInput(filter.key) {
                            detectTapGestures { onFilterSelected(filter.key) }
                        }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF222222))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = filter.name,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
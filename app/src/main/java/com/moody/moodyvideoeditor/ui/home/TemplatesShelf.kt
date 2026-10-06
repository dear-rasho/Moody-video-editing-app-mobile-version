package com.moody.moodyvideoeditor.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.templates.HomeTemplateCatalog
import com.moody.moodyvideoeditor.data.templates.HomeTemplatePreset

@Composable
fun TemplatesShelf(onTemplateSelected: (HomeTemplatePreset) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HomeTemplateCatalog.all.forEach { template ->
            Box(
                modifier = Modifier
                    .width(178.dp)
                    .height(142.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF181818)),
                contentAlignment = Alignment.BottomStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(142.dp)
                        .background(Color(template.accentColor).copy(alpha = 0.12f))
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTemplateSelected(template) }
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(template.icon, fontSize = 23.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            template.category.uppercase(),
                            color = Color(template.accentColor),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(
                        template.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        template.description,
                        color = Color(0xFFAAAAAA),
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "USE TEMPLATE  →",
                        color = Color(template.accentColor),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
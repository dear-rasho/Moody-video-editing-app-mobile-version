package com.moody.moodyvideoeditor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.ui.home.CodeBaseShelf
import com.moody.moodyvideoeditor.ui.home.RecentProjects
import com.moody.moodyvideoeditor.ui.home.TemplatesShelf

@Composable
fun DashboardScreen(
    onNewProject: () -> Unit,
    onCodeEditor: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "OFFLINE VIDEO EDITOR",
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Projects",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = onNewProject,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF7C3AED), RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "New Project",
                    tint = Color.White
                )
            }
        }

        // RECENT PROJECTS
        SectionTitle("Recent Projects")
        RecentProjects()

        // TEMPLATES
        SectionTitle("Templates")
        TemplatesShelf()

        // CODE BASE EDITING
        SectionTitle("Code Base Editing")
        CodeBaseShelf(onOpen = { onCodeEditor() })
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
    )
}
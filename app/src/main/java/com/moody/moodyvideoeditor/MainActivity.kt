package com.moody.moodyvideoeditor

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.moody.moodyvideoeditor.data.Clip
import com.moody.moodyvideoeditor.data.EditorViewModel
import com.moody.moodyvideoeditor.utils.VideoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MoodyEditorApp()
            }
        }
    }
}

@Composable
fun MoodyEditorApp(viewModel: EditorViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    // Pehla clip = state.timeline.visualTracks[0][0] (agar hai)
    val currentClip = remember(state.timeline.visualTracks) {
        state.timeline.visualTracks.getOrNull(0)?.firstOrNull()
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { pendingUri = it }
    }

    LaunchedEffect(pendingUri) {
        pendingUri?.let { uri ->
            isLoading = true
            try {
                val clip = withContext(Dispatchers.IO) {
                    val name = VideoUtils.getFileName(context, uri)
                    val duration = VideoUtils.getVideoDuration(context, uri)
                    Clip(
                        uri = uri.toString(),
                        type = "video/",
                        name = name,
                        startTimeMs = 0,
                        durationMs = duration,
                        sourceTotalMs = duration
                    )
                }
                // Pehli clip ko V1 mein add karein (agar khali hai)
                if (state.timeline.visualTracks[0].isEmpty()) {
                    viewModel.addVisualClip(0, clip)
                } else {
                    // Warna replace karein
                    viewModel.deleteClip(state.timeline.visualTracks[0][0].id)
                    viewModel.addVisualClip(0, clip)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
                pendingUri = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "🎬 Moody Video Editor",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        // ─── VIDEO PREVIEW ────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> CircularProgressIndicator(color = Color(0xFF7C3AED))
                currentClip != null -> VideoPlayer(uri = Uri.parse(currentClip.uri))
                else -> EmptyPreview()
            }
        }

        // ─── VIDEO INFO ───────────────────────────────────────
        if (currentClip != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Movie,
                            contentDescription = null,
                            tint = Color(0xFF7C3AED),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = currentClip.name,
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = "Duration: ${VideoUtils.formatDuration(currentClip.durationMs)}",
                        color = Color(0xFF888888),
                        fontSize = 10.sp
                    )
                }
            }
        }

        // ─── TIMELINE PREVIEW (Simple) ────────────────────────
        TimelinePreview(viewModel = viewModel)

        // ─── PICK BUTTON ──────────────────────────────────────
        Button(
            onClick = { picker.launch("video/*") },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = if (currentClip == null) Icons.Filled.Add else Icons.Filled.Refresh,
                contentDescription = null,
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (currentClip == null) "Pick Video" else "Change Video",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  TIMELINE PREVIEW (Basic — full UI baad mein)
// ═══════════════════════════════════════════════════════════════
@Composable
fun TimelinePreview(viewModel: EditorViewModel) {
    val state by viewModel.state.collectAsState()
    val totalDur = state.timeline.totalDurationMs().coerceAtLeast(1L)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Timeline",
            color = Color(0xFF888888),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )

        // Visual tracks
        state.timeline.visualTracks.forEachIndexed { trackIdx, clips ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "V${trackIdx + 1}",
                    color = Color(0xFF666666),
                    fontSize = 9.sp,
                    modifier = Modifier.width(24.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                ) {
                    clips.forEach { clip ->
                        val leftFrac = clip.startTimeMs.toFloat() / totalDur
                        val widthFrac = clip.durationMs.toFloat() / totalDur
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = widthFrac.coerceIn(0.01f, 1f))
                                .offset(x = 0.dp)
                                .background(Color(0xFF7C3AED), RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }

        // Audio tracks
        state.timeline.audioTracks.forEachIndexed { trackIdx, clips ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "A${trackIdx + 1}",
                    color = Color(0xFF666666),
                    fontSize = 9.sp,
                    modifier = Modifier.width(24.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
fun EmptyPreview() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Movie,
            contentDescription = null,
            tint = Color(0xFF444444),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No video selected",
            color = Color(0xFF888888),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tap below to pick a video",
            color = Color(0xFF666666),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(uri: Uri) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = false }
    }
    LaunchedEffect(uri) {
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
    }
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
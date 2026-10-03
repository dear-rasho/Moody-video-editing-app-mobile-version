package com.moody.moodyvideoeditor.ui.features

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.service.ExportUiState
import kotlinx.coroutines.delay

@Composable
fun ExportOverlay(
    state: ExportUiState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Block all touch behind the overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .pointerInput(Unit) {}
    ) {
        when {
            // Success state — show completion dialog
            state.isCompleted && state.outputUri != null -> {
                CompletionDialog(
                    outputUri = state.outputUri,
                    onOpen = { uri ->
                        openExportedFile(context, uri)
                        onDismiss()
                    },
                    onShare = { uri ->
                        shareExportedFile(context, uri)
                        onDismiss()
                    },
                    onOk = { onDismiss() }
                )
            }

            // Cancelled state — auto dismiss after 1.5s
            state.isCancelled -> {
                CancelledDialog(onDismiss = { onDismiss() })
            }

            // Error state — show error dialog
            state.errorMessage != null -> {
                ErrorDialog(
                    message = state.errorMessage,
                    onDismiss = { onDismiss() }
                )
            }

            // Exporting in progress
            state.isExporting -> {
                ExportingDialog(
                    state = state,
                    onCancel = { onCancel() }
                )
            }
        }
    }
}


// Exporting progress dialog
@Composable
private fun ExportingDialog(
    state: ExportUiState,
    onCancel: () -> Unit
) {
    // Animated rotation for spinner
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Live remaining time calculation
    var remainingSec by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.progress, state.startedAtMs) {
        while (state.isExporting) {
            val s = state
            if (s.progress > 0.01f && s.startedAtMs > 0L) {
                val elapsed = System.currentTimeMillis() - s.startedAtMs
                val totalEstimate = (elapsed / s.progress).toLong()
                val remaining = (totalEstimate - elapsed).coerceAtLeast(0L)
                remainingSec = remaining / 1000
            }
            delay(500)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1A1A))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Rotating spinner
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .rotate(rotation),
                contentAlignment = Alignment.Center
            ) {
                // Simple ring shape using borders
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFF7C3AED),
                                    Color(0xFF60EFFF),
                                    Color(0xFF7C3AED),
                                    Color.Transparent,
                                    Color(0xFF7C3AED)
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1A1A1A))
                )
            }

            Text(
                text = "Exporting...",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF0A0A0A))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(state.progress.coerceIn(0f, 1f))
                        .height(8.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF7C3AED),
                                    Color(0xFF60EFFF)
                                )
                            )
                        )
                )
            }

            // Percentage
            Text(
                text = "${(state.progress * 100).toInt()}%",
                color = Color(0xFF7C3AED),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            // Phase message
            if (state.message.isNotBlank()) {
                Text(
                    text = state.message,
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Remaining time
            if (remainingSec > 0) {
                Text(
                    text = "About $remainingSec seconds remaining",
                    color = Color(0xFF666666),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Info message
            Text(
                text = "Keep the app open for best results. Export will continue if you minimize.",
                color = Color(0xFF555555),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Cancel button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2A2A2A))
                    .pointerInput(Unit) {
                        detectTapGestures { onCancel() }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Cancel Export",
                    color = Color(0xFFFF6B6B),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


// Success dialog
@Composable
private fun CompletionDialog(
    outputUri: String,
    onOpen: (String) -> Unit,
    onShare: (String) -> Unit,
    onOk: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1A1A))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", fontSize = 32.sp, color = Color(0xFF22C55E))
            }

            Text(
                text = "Export Complete",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Your video has been saved to the gallery.",
                color = Color(0xFFAAAAAA),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Open button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF7C3AED))
                        .pointerInput(outputUri) {
                            detectTapGestures { onOpen(outputUri) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Open",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Share button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2A2A2A))
                        .pointerInput(outputUri) {
                            detectTapGestures { onShare(outputUri) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Share",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // OK button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { onOk() }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "OK",
                    color = Color(0xFF888888),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


// Cancelled dialog
@Composable
private fun CancelledDialog(onDismiss: () -> Unit) {
    // Auto dismiss after 1.5 seconds
    LaunchedEffect(Unit) {
        delay(1500)
        onDismiss()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1A1A))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("⚠️", fontSize = 40.sp)
            Text(
                text = "Export Cancelled",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Your export has been cancelled.",
                color = Color(0xFF888888),
                fontSize = 12.sp
            )
        }
    }
}


// Error dialog
@Composable
private fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1A1A))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF6B6B).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text("✕", fontSize = 32.sp, color = Color(0xFFFF6B6B))
            }

            Text(
                text = "Export Failed",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF2A2A2A))
            )

            Text(
                text = message,
                color = Color(0xFFAAAAAA),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF7C3AED))
                    .pointerInput(Unit) {
                        detectTapGestures { onDismiss() }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "OK",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


// Helper: opens the exported file with system viewer
private fun openExportedFile(context: Context, uriString: String) {
    try {
        val uri = Uri.parse(uriString)
        val mimeType = getMimeTypeForUri(context, uri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (t: Throwable) {
        android.util.Log.e("ExportOverlay", "openExportedFile failed", t)
    }
}


// Helper: shares the exported file
private fun shareExportedFile(context: Context, uriString: String) {
    try {
        val uri = Uri.parse(uriString)
        val mimeType = getMimeTypeForUri(context, uri)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share via"))
    } catch (t: Throwable) {
        android.util.Log.e("ExportOverlay", "shareExportedFile failed", t)
    }
}


// Helper: determine MIME type from URI
private fun getMimeTypeForUri(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.getType(uri) ?: guessMimeFromExtension(uri)
    } catch (t: Throwable) {
        guessMimeFromExtension(uri)
    }
}


// Helper: guess MIME from file extension
private fun guessMimeFromExtension(uri: Uri): String {
    val path = uri.lastPathSegment?.lowercase() ?: ""
    return when {
        path.endsWith(".mp4") -> "video/mp4"
        path.endsWith(".mov") -> "video/quicktime"
        path.endsWith(".mp3") -> "audio/mpeg"
        path.endsWith(".m4a") -> "audio/mp4"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
        else -> "*/*"
    }
}
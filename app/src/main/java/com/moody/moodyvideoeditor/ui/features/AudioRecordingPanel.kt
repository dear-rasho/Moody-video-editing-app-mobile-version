package com.moody.moodyvideoeditor.ui.features

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun AudioRecordingPanel(
    onRecorded: (uri: Uri, name: String, durationMs: Long) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingStartedAt by remember { mutableLongStateOf(0L) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var hasMicrophonePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicrophonePermission = granted
        statusMessage = if (granted) null else "Microphone permission is required to record audio."
    }

    fun startRecording() {
        var newRecorder: MediaRecorder? = null
        var output: File? = null
        try {
            val directory = File(context.filesDir, "recordings")
            if (!directory.exists() && !directory.mkdirs()) {
                error("Could not create the recordings folder.")
            }
            val outputFile = File(directory, "voice_${System.currentTimeMillis()}.m4a")
            output = outputFile
            recordingFile = outputFile
            val activeRecorder = createRecorder(context)
            newRecorder = activeRecorder
            recorder = activeRecorder
            activeRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            activeRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            activeRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            activeRecorder.setAudioChannels(1)
            activeRecorder.setAudioSamplingRate(44_100)
            activeRecorder.setAudioEncodingBitRate(128_000)
            activeRecorder.setOutputFile(outputFile.absolutePath)
            activeRecorder.prepare()
            activeRecorder.start()
            recordingStartedAt = SystemClock.elapsedRealtime()
            elapsedMs = 0L
            statusMessage = null
            isRecording = true
        } catch (error: Exception) {
            newRecorder?.release()
            recorder = null
            output?.delete()
            recordingFile = null
            statusMessage = "Could not start recording: ${error.message ?: "unknown error"}"
        }
    }

    fun finishRecording() {
        val activeRecorder = recorder ?: return
        val output = recordingFile
        val duration = (SystemClock.elapsedRealtime() - recordingStartedAt).coerceAtLeast(0L)
        isRecording = false
        recorder = null
        recordingFile = null

        try {
            activeRecorder.stop()
            output?.takeIf { it.isFile && it.length() > 0L }?.let { file ->
                val name = "Voice Recording ${System.currentTimeMillis()}.m4a"
                onRecorded(Uri.fromFile(file), name, duration.coerceAtLeast(1_000L))
                onClose()
            } ?: run {
                output?.delete()
                statusMessage = "The recording was empty. Try recording again."
            }
        } catch (error: Exception) {
            output?.delete()
            statusMessage = "Could not finish recording: ${error.message ?: "try a longer recording"}"
        } finally {
            activeRecorder.release()
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            elapsedMs = SystemClock.elapsedRealtime() - recordingStartedAt
            delay(200L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder?.let { activeRecorder ->
                try {
                    activeRecorder.stop()
                } catch (_: RuntimeException) {
                } finally {
                    activeRecorder.release()
                    recordingFile?.delete()
                }
            }
        }
    }

    FeaturePanel(title = "🎙️ Record Audio", onClose = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (isRecording) "Recording from microphone" else "Record a voice clip",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                formatRecordingTime(elapsedMs),
                color = if (isRecording) Color(0xFFFF6B6B) else Color(0xFFBBBBBB),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isRecording) {
                    ActionButton(
                        label = "■ Stop & Add to Audio Layer",
                        color = Color(0xFFB91C1C),
                        modifier = Modifier.weight(1f),
                        onClick = ::finishRecording
                    )
                } else {
                    ActionButton(
                        label = "🎙️ Start Recording",
                        color = Color(0xFF7C3AED),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (hasMicrophonePermission) {
                            startRecording()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
            }

            Text(
                statusMessage ?: "Your recording will be added at the current playhead.",
                color = if (statusMessage != null) Color(0xFFFF6B6B) else Color(0xFF888888),
                fontSize = 10.sp
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun createRecorder(context: Context): MediaRecorder =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()

@Composable
private fun ActionButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatRecordingTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000L
    return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

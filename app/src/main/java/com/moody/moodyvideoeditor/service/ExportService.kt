package com.moody.moodyvideoeditor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.moody.moodyvideoeditor.MainActivity
import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.utils.VideoExporter
import com.moody.moodyvideoeditor.viewmodel.ExportClipsHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ExportService : Service() {

    companion object {
        private const val TAG = "ExportService"
        private const val CHANNEL_ID = "moody_export_channel"
        private const val NOTIFICATION_ID = 8801

        const val ACTION_START = "com.moody.moodyvideoeditor.START_EXPORT"
        const val ACTION_CANCEL = "com.moody.moodyvideoeditor.CANCEL_EXPORT"

        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_MODE = "mode"
        const val EXTRA_RESOLUTION = "resolution"
        const val EXTRA_FPS = "fps"
        const val EXTRA_BITRATE = "bitrate"
        const val EXTRA_VIDEO_FORMAT = "video_format"
        const val EXTRA_AUDIO_FORMAT = "audio_format"
        const val EXTRA_AUDIO_BITRATE = "audio_bitrate"
        const val EXTRA_IMAGE_FORMAT = "image_format"
        const val EXTRA_JPEG_QUALITY = "jpeg_quality"
        const val EXTRA_ASPECT_RATIO = "aspect_ratio"
        const val EXTRA_OUTPUT_WIDTH = "output_width"
        const val EXTRA_OUTPUT_HEIGHT = "output_height"
        const val EXTRA_FOLDER_URI = "folder_uri"
        const val EXTRA_START_MS = "start_ms"
        const val EXTRA_END_MS = "end_ms"
        const val EXTRA_TOTAL_DURATION_MS = "total_duration_ms"

        fun buildStartIntent(
            context: Context,
            config: ExportServiceConfig
        ): Intent {
            return Intent(context, ExportService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FILE_NAME, config.fileName)
                putExtra(EXTRA_MODE, config.mode)
                putExtra(EXTRA_RESOLUTION, config.resolution)
                putExtra(EXTRA_FPS, config.fps)
                putExtra(EXTRA_BITRATE, config.bitrateKbps)
                putExtra(EXTRA_VIDEO_FORMAT, config.videoFormat)
                putExtra(EXTRA_AUDIO_FORMAT, config.audioFormat)
                putExtra(EXTRA_AUDIO_BITRATE, config.audioBitrateKbps)
                putExtra(EXTRA_IMAGE_FORMAT, config.imageFormat)
                putExtra(EXTRA_JPEG_QUALITY, config.jpegQuality)
                putExtra(EXTRA_ASPECT_RATIO, config.aspectRatio)
                putExtra(EXTRA_OUTPUT_WIDTH, config.outputWidth)
                putExtra(EXTRA_OUTPUT_HEIGHT, config.outputHeight)
                putExtra(EXTRA_FOLDER_URI, config.folderUri)
                putExtra(EXTRA_START_MS, config.startMs)
                putExtra(EXTRA_END_MS, config.endMs)
                putExtra(EXTRA_TOTAL_DURATION_MS, config.totalDurationMs)
            }
        }

        fun buildCancelIntent(context: Context): Intent {
            return Intent(context, ExportService::class.java).apply {
                action = ACTION_CANCEL
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var exportJob: Job? = null
    private var activeExporter: VideoExporter? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotificationUpdate = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_START -> handleStart(intent)
            ACTION_CANCEL -> handleCancel()
            else -> stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        if (exportJob?.isActive == true) {
            Log.w(TAG, "Export already running, ignoring new start")
            return
        }

        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "MoodyExport"
        val mode = intent.getStringExtra(EXTRA_MODE) ?: "video"
        val resolution = intent.getStringExtra(EXTRA_RESOLUTION) ?: "720p"
        val fps = intent.getIntExtra(EXTRA_FPS, 30)
        val bitrate = intent.getIntExtra(EXTRA_BITRATE, 8000)
        val videoFormat = intent.getStringExtra(EXTRA_VIDEO_FORMAT) ?: "mp4"
        val audioFormat = intent.getStringExtra(EXTRA_AUDIO_FORMAT) ?: "mp3"
        val audioBitrate = intent.getIntExtra(EXTRA_AUDIO_BITRATE, 192)
        val imageFormat = intent.getStringExtra(EXTRA_IMAGE_FORMAT) ?: "png"
        val jpegQuality = intent.getIntExtra(EXTRA_JPEG_QUALITY, 90)
        val aspectRatio = intent.getStringExtra(EXTRA_ASPECT_RATIO) ?: "16:9"
        val outputWidth = intent.getIntExtra(EXTRA_OUTPUT_WIDTH, 0)
        val outputHeight = intent.getIntExtra(EXTRA_OUTPUT_HEIGHT, 0)
        val folderUri = intent.getStringExtra(EXTRA_FOLDER_URI)
        val startMs = intent.getLongExtra(EXTRA_START_MS, 0L)
        val endMs = intent.getLongExtra(EXTRA_END_MS, 0L)
        val totalDurMs = intent.getLongExtra(EXTRA_TOTAL_DURATION_MS, 0L)

        startForegroundWithNotification("Starting export...", 0)
        acquireWakeLock()

        val effectiveDur = if (endMs > startMs) (endMs - startMs) else totalDurMs
        val estimatedTotalMs = estimateTotalMs(effectiveDur, mode)

        ExportStateHolder.start(estimatedTotalMs)

        val clips = ExportClipsHolder.clips
        Log.e(TAG, "Service received ${clips.size} clips from holder")

        if (clips.isEmpty()) {
            Log.e(TAG, "No clips in ExportClipsHolder")
            ExportStateHolder.fail("No clips to export")
            stopSelfSafe()
            return
        }

        exportJob = serviceScope.launch {
            try {
                runExport(
                    clips = clips,
                    fileName = fileName,
                    mode = mode,
                    resolution = resolution,
                    fps = fps,
                    bitrateKbps = bitrate,
                    videoFormat = videoFormat,
                    audioFormat = audioFormat,
                    audioBitrateKbps = audioBitrate,
                    imageFormat = imageFormat,
                    jpegQuality = jpegQuality,
                    aspectRatio = aspectRatio,
                    outputWidth = outputWidth,
                    outputHeight = outputHeight,
                    folderUri = folderUri,
                    startMs = startMs,
                    endMs = endMs
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Export crashed", t)
                ExportStateHolder.fail("Export failed: ${t.message}")
                stopSelfSafe()
            }
        }
    }

    private suspend fun runExport(
        clips: List<EditorClip>,
        fileName: String,
        mode: String,
        resolution: String,
        fps: Int,
        bitrateKbps: Int,
        videoFormat: String,
        audioFormat: String,
        audioBitrateKbps: Int,
        imageFormat: String,
        jpegQuality: Int,
        aspectRatio: String,
        outputWidth: Int,
        outputHeight: Int,
        folderUri: String?,
        startMs: Long,
        endMs: Long
    ) {
        Log.i(
            TAG,
            "Starting export: mode=$mode, ratio=$aspectRatio, " +
                    "output=${outputWidth}x$outputHeight, clips=${clips.size}"
        )

        val exporter = VideoExporter(
            context = applicationContext,
            onProgress = { p ->
                val phase = phaseForProgress(p, mode)
                val msg = messageForProgress(p, mode)
                ExportStateHolder.updateProgress(p, msg, phase)

                val now = System.currentTimeMillis()
                if (now - lastNotificationUpdate > 800) {
                    lastNotificationUpdate = now
                    updateNotification(msg, (p * 100).toInt())
                }
            },
            onSuccess = { uri ->
                ExportStateHolder.complete(uri.toString())
                showCompletionNotification(uri)
                ExportClipsHolder.clear()
                stopSelfSafe()
            },
            onError = { err ->
                ExportStateHolder.fail(err)
                ExportClipsHolder.clear()
                stopSelfSafe()
            },
            onCancelled = {
                ExportStateHolder.cancel()
                ExportClipsHolder.clear()
                stopSelfSafe()
            }
        )

        activeExporter = exporter

        exporter.export(
            clips = clips,
            fileName = fileName,
            adjustments = AdjustmentData(),
            aspectRatio = aspectRatio,
            outputWidth = outputWidth,
            outputHeight = outputHeight,
            resolution = resolution,
            fps = fps,
            bitrateKbps = bitrateKbps,
            format = videoFormat,
            customFolderUri = folderUri,
            customStartMs = startMs,
            customEndMs = endMs,
            exportMode = mode,
            audioFormat = audioFormat,
            audioBitrateKbps = audioBitrateKbps,
            imageFormat = imageFormat,
            jpegQuality = jpegQuality
        )
    }

    private fun handleCancel() {
        Log.d(TAG, "Cancel requested")
        try {
            activeExporter?.cancel()
        } catch (t: Throwable) {
            Log.e(TAG, "Cancel failed", t)
        }
        exportJob?.cancel()
        ExportStateHolder.cancel()
        stopSelfSafe()
    }

    private fun phaseForProgress(p: Float, mode: String): String {
        return when (mode) {
            "audio" -> when {
                p < 0.05f -> "preparing"
                p < 0.95f -> "encoding"
                else -> "finalizing"
            }

            "image" -> when {
                p < 0.05f -> "preparing"
                p < 0.50f -> "rendering"
                p < 0.99f -> "encoding"
                else -> "finalizing"
            }

            else -> when {
                p < 0.05f -> "preparing"
                p < 0.20f -> "rendering"
                p < 0.40f -> "rendering"
                p < 0.99f -> "encoding"
                else -> "finalizing"
            }
        }
    }

    private fun messageForProgress(p: Float, mode: String): String {
        return when (mode) {
            "audio" -> when {
                p < 0.05f -> "Preparing audio..."
                p < 0.95f -> "Encoding audio ${(p * 100).toInt()}%"
                else -> "Finalizing..."
            }

            "image" -> when {
                p < 0.05f -> "Preparing frames..."
                p < 0.50f -> "Rendering text ${(p * 200).toInt()}%"
                p < 0.99f -> "Encoding images ${((p - 0.5f) * 200).toInt()}%"
                else -> "Finalizing..."
            }

            else -> when {
                p < 0.05f -> "Preparing..."
                p < 0.20f -> "Rendering text ${((p - 0.05f) / 0.15f * 100).toInt()}%"
                p < 0.40f -> "Rendering visualizer ${((p - 0.20f) / 0.20f * 100).toInt()}%"
                p < 0.99f -> "Encoding video ${((p - 0.40f) / 0.60f * 100).toInt()}%"
                else -> "Finalizing..."
            }
        }
    }

    private fun estimateTotalMs(durationMs: Long, mode: String): Long {
        return when (mode) {
            "audio" -> (durationMs * 0.5).toLong().coerceAtLeast(3000L)
            "image" -> (durationMs * 3.0).toLong().coerceAtLeast(5000L)
            else -> (durationMs * 1.5).toLong().coerceAtLeast(5000L)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Video Export",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress while exporting video"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification(message: String, progress: Int) {
        val notification = buildNotification(message, progress, false)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed", t)
        }
    }

    private fun updateNotification(message: String, progress: Int) {
        try {
            val notification = buildNotification(message, progress, false)
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIFICATION_ID, notification)
        } catch (t: Throwable) {
            Log.e(TAG, "updateNotification failed", t)
        }
    }

    private fun showCompletionNotification(uri: Uri) {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Export complete")
                .setContentText("Saved to gallery")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(false)
                .build()

            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIFICATION_ID + 1, notification)
        } catch (t: Throwable) {
            Log.e(TAG, "showCompletionNotification failed", t)
        }
    }

    private fun buildNotification(
        message: String,
        progress: Int,
        indeterminate: Boolean
    ): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = buildCancelIntent(this)
        val cancelPending = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Exporting")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pending)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancelPending
            )
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "MoodyExport::WakeLock"
                )
                wakeLock?.setReferenceCounted(false)
                wakeLock?.acquire(30 * 60 * 1000L)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "acquireWakeLock failed", t)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null
        } catch (t: Throwable) {
            Log.e(TAG, "releaseWakeLock failed", t)
        }
    }

    private fun stopSelfSafe() {
        try {
            serviceScope.launch {
                kotlinx.coroutines.delay(1000)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "stopForeground failed", t)
                }
                stopSelf()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "stopSelfSafe failed", t)
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        try {
            activeExporter?.cancel()
        } catch (_: Throwable) {
        }
        serviceScope.cancel()
        super.onDestroy()
    }
}


data class ExportServiceConfig(
    val fileName: String,
    val mode: String,
    val resolution: String,
    val fps: Int,
    val bitrateKbps: Int,
    val videoFormat: String,
    val audioFormat: String,
    val audioBitrateKbps: Int,
    val imageFormat: String,
    val jpegQuality: Int,
    val aspectRatio: String,
    val outputWidth: Int,
    val outputHeight: Int,
    val folderUri: String?,
    val startMs: Long,
    val endMs: Long,
    val totalDurationMs: Long,
    val clipsJson: String
)
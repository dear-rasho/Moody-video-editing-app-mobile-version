package com.moody.moodyvideoeditor.utils

import android.media.MediaCodecList
import android.os.Build
import android.util.Log

internal object VideoEncoderPolicy {
    private const val TAG = "VideoEncoderPolicy"
    private const val HARDWARE_ENCODER = "h264_mediacodec"
    private const val SOFTWARE_ENCODER = "libx264"

    fun codecArguments(width: Int, height: Int, fps: Int): List<String> {
        val hardwareEncoder = findHardwareAvcEncoder(width, height, fps)
        return if (hardwareEncoder != null) {
            Log.i(
                "EXPORT_ENCODER",
                "Using Android H.264 hardware encoder ($hardwareEncoder) " +
                        "via h264_mediacodec for ${width}x$height@$fps"
            )
            listOf("-c:v", HARDWARE_ENCODER)
        } else {
            Log.i(
                "EXPORT_ENCODER",
                "No compatible Android H.264 hardware encoder for " +
                        "${width}x$height@$fps; using libx264 ultrafast"
            )
            listOf(
                "-c:v", SOFTWARE_ENCODER,
                "-preset", "ultrafast",
                "-tune", "fastdecode"
            )
        }
    }

    fun softwareFallbackArguments(arguments: List<String>): List<String>? {
        val codecIndex = arguments.indexOf("-c:v")
        if (codecIndex < 0 || arguments.getOrNull(codecIndex + 1) != HARDWARE_ENCODER) {
            return null
        }

        val fallback = arguments.toMutableList()
        fallback[codecIndex + 1] = SOFTWARE_ENCODER
        fallback.addAll(codecIndex + 2, listOf("-preset", "ultrafast", "-tune", "fastdecode"))

        val bitrateIndex = fallback.indexOf("-b:v")
        val threads = listOf("-threads", "0")
        if (bitrateIndex >= 0) fallback.addAll(bitrateIndex + 2, threads)
        else fallback.addAll(fallback.size - 1, threads)

        return fallback
    }

    fun shouldFallbackToSoftware(logs: String): Boolean {
        val message = logs.lowercase()
        return "mediacodec" in message ||
                "h264_mediacodec" in message ||
                "error while opening encoder" in message ||
                "failed to configure encoder" in message
    }

    private fun findHardwareAvcEncoder(width: Int, height: Int, fps: Int): String? {
        return try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull { codec ->
                if (!codec.isEncoder ||
                    codec.supportedTypes.none { it.equals("video/avc", ignoreCase = true) }
                ) {
                    false
                } else {
                    val isHardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        codec.isHardwareAccelerated && !codec.isSoftwareOnly
                    } else {
                        val name = codec.name.lowercase()
                        !name.startsWith("omx.google.") &&
                                !name.startsWith("c2.android.") &&
                                !name.contains(".sw.")
                    }
                    val videoCapabilities =
                        codec.getCapabilitiesForType("video/avc").videoCapabilities
                    isHardware && videoCapabilities?.areSizeAndRateSupported(
                        width,
                        height,
                        fps.toDouble()
                    ) == true
                }
            }?.name
        } catch (error: Exception) {
            Log.w(TAG, "Hardware H.264 encoder unavailable; using software encoding", error)
            null
        }
    }
}
